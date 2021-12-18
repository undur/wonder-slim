/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSSelector;

import er.extensions.components.ERXStatelessComponent;

/**
 * Diverse collection of utility methods for handling everything from EOF to
 * foundation. In the future this class will most likely be split into more
 * meaning full groups of utility methods.
 */
public class ERXUtilities {

	private static final Logger log = LoggerFactory.getLogger(ERXUtilities.class);

	@Deprecated
	public static final Class[] NotificationClassArray = { com.webobjects.foundation.NSNotification.class };

	/**
	 * Generates a string representation of the current stacktrace.
	 *
	 * @return current stacktrace.
	 */
	public static String stackTrace() {
		String result = null;
		try {
			throw new Throwable();
		}
		catch (Throwable t) {
			result = ERXUtilities.stackTrace(t);
		}

		String separator = System.getProperties().getProperty("line.separator");

		// Chop off the 1st line, "java.lang.Throwable"
		//
		int offset = result.indexOf(separator);
		result = result.substring(offset + 1);

		// Chop off the lines at the start that refer to ERXUtilities
		//
		offset = result.indexOf(separator);
		while (result.substring(0, offset).indexOf("ERXUtilities.java") >= 0) {
			result = result.substring(offset + 1);
			offset = result.indexOf(separator);
		}
		return separator + result;
	}

	/**
	 * Converts a throwable's stacktrace into a string representation.
	 * 
	 * @param t
	 *            throwable to print to a string
	 * @return string representation of stacktrace
	 */
	public static String stackTrace(Throwable t) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
		PrintStream printStream = new PrintStream(baos);
		t.printStackTrace(printStream);
		return baos.toString();
	}

	/**
	 * Useful interface for binding objects to WOComponent bindings where you
	 * want to delay the evaluation of the boolean operation until
	 * <code>valueForBinding</code> is actually called. See
	 * {@link ERXStatelessComponent} for examples.
	 */
	public static interface BooleanOperation {
		public boolean value();
	}

	/**
	 * Useful interface for binding objects to WOComponent bindings where you
	 * want to delay the evaluation of the operation until
	 * <code>valueForBinding</code> is actually called. See
	 * {@link ERXStatelessComponent} for examples.
	 */
	public static interface Operation {
		public Object value();
	}

	/**
	 * Utility that returns a selector you can use with the
	 * NSNotificationCenter.
	 * 
	 * @param methodName
	 * @return A selector suitable for firing a notification
	 */
	public static NSSelector<Void> notificationSelector(String methodName) {
		return new NSSelector<Void>(methodName, ERXUtilities.NotificationClassArray);
	}

	/**
	 * Reads a file in from the file system for the given set of languages and then
	 * parses the file as if it were a property list, using the specified encoding.
	 *
	 * @param fileName name of the file
	 * @param aFrameWorkName name of the framework, <code>null</code> or 'app' for the application bundle.
	 * @param languageList language list search order
	 * @param encoding the encoding used with <code>fileName</code>
	 * @return de-serialized object from the plist formatted file specified.
	 */
	@Deprecated
	public static Object readPropertyListFromFileInFramework(String fileName, String aFrameWorkName, NSArray<String> languageList, String encoding) {
		Object result = null;

		try( InputStream stream = WOApplication.application().resourceManager().inputStreamForResourceNamed(fileName, aFrameWorkName, languageList)) {
			if (stream != null) {
				String stringFromFile = new String(stream.readAllBytes(), encoding);
				result = NSPropertyListSerialization.propertyListFromString(stringFromFile);
			}
		}
		catch (IOException ioe) {
			log.error("ConfigurationManager: Error reading file <{}> from framework {}", fileName, aFrameWorkName);
		}

		return result;
	}

	/**
	 * Creates an NSDictionary from a resource associated with a given bundle that is in property list format.
	 * 
	 * @param name name of the file or resource.
	 * @param bundle NSBundle to which the resource belongs.
	 * @return NSDictionary de-serialized from the property list.
	 */
	@SuppressWarnings("unchecked")
	public static NSDictionary dictionaryFromPropertyList(String name, NSBundle bundle) {
		String string = ERXUtilities.stringFromResource(name, "plist", bundle);
		return (NSDictionary<?, ?>) NSPropertyListSerialization.propertyListFromString(string);
	}

	/**
	 * Retrieves a given string for a given name, extension and bundle.
	 * 
	 * @param name of the resource
	 * @param extension of the resource, example: txt or rtf
	 * @param bundle to look for the resource in
	 * @return string of the given file specified in the bundle
	 */
	public static String stringFromResource(String name, String extension, NSBundle bundle) {
		String path = null;
	
		if (bundle == null) {
			bundle = NSBundle.mainBundle();
		}
	
		path = bundle.resourcePathForLocalizedResourceNamed(name + (extension == null || extension.length() == 0 ? "" : "." + extension), null);
	
		if (path != null) {
			try( InputStream stream = bundle.inputStreamForResourcePath(path)) {
				byte bytes[] = stream.readAllBytes();
				return new String(bytes);
			}
			catch (IOException e) {
				log.warn("IOException when stringFromResource({}.{} in bundle {}", name, extension, bundle.name());
			}
		}
	
		return null;
	}
}
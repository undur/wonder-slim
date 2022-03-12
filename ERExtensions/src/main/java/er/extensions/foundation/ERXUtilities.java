/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSSelector;

import er.extensions.appserver.ERXWOContext;
import er.extensions.components.ERXStatelessComponent;

public class ERXUtilities {

	private static final Logger log = LoggerFactory.getLogger(ERXUtilities.class);

	public static final Class[] NotificationClassArray = { com.webobjects.foundation.NSNotification.class };

	/**
	 * Useful interface for binding objects to WOComponent bindings where you want to delay the evaluation of the boolean operation until
	 * <code>valueForBinding</code> is actually called. See {@link ERXStatelessComponent} for examples.
	 */
	public static interface BooleanOperation {
		public boolean value();
	}

	/**
	 * Useful interface for binding objects to WOComponent bindings where you want to delay the evaluation of the operation until
	 * <code>valueForBinding</code> is actually called. See {@link ERXStatelessComponent} for examples.
	 */
	public static interface Operation {
		public Object value();
	}

	/**
	 * Utility that returns a selector you can use with the NSNotificationCenter.
	 * 
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
	

	/**
	 * Puts together a dictionary with a bunch of useful information relative to
	 * the current state when the exception occurred. Potentially added information:
	 * 
	 * <ol>
	 * <li>the current page name</li>
	 * <li>the current component</li>
	 * <li>the complete hierarchy of nested components</li>
	 * <li>the requested uri</li>
	 * <li>the D2W page configuration</li>
	 * <li>the previous page list (from the WOStatisticsStore)</li>
	 * </ol>
	 * Also, in case the top-level exception was a EOGeneralAdaptorException,
	 * then you also get the failed ops and the sql exception.
	 * 
	 * @param e exception
	 * @param context the current context
	 * @return dictionary containing extra information for the current context.
	 */
	public static NSMutableDictionary extraInformationForExceptionInContext(Exception e, WOContext context) {
		NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		extraInfo.addEntriesFromDictionary(informationForContext(context));
		extraInfo.addEntriesFromDictionary(informationForBundles());
		return extraInfo;
	}

	private static NSMutableDictionary<String, Object> informationForBundles() {
		NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		NSMutableDictionary<String, Object> bundleVersions = new NSMutableDictionary<String, Object>();
		for (Enumeration bundles = NSBundle._allBundlesReally().objectEnumerator(); bundles.hasMoreElements();) {
			NSBundle bundle = (NSBundle) bundles.nextElement();
			String version = versionStringForFrameworkNamed(bundle.name());
			if (version == null) {
				version = "No version provided";
			}
			bundleVersions.setObjectForKey(version, bundle.name());
		}
		extraInfo.setObjectForKey(bundleVersions, "Bundles");
		return extraInfo;
	}

	private static NSMutableDictionary<String, Object> informationForContext(WOContext context) {
		NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		if (context != null) {
			if (context.page() != null) {
				extraInfo.setObjectForKey(context.page().name(), "CurrentPage");
				if (context.component() != null) {
					extraInfo.setObjectForKey(context.component().name(), "CurrentComponent");
					if (context.component().parent() != null) {
						extraInfo.setObjectForKey(ERXWOContext.componentPath(context), "CurrentComponentHierarchy");
					}
				}
				// If this is a D2W component, get its D2W-related information
				// from ERDirectToWeb.
				NSSelector d2wSelector = new NSSelector("d2wContext");
				if (d2wSelector.implementedByObject(context.page())) {
					try {
						Class erDirectToWebClazz = Class.forName("er.directtoweb.ERDirectToWeb");
						NSSelector infoSelector = new NSSelector("informationForContext", new Class[] { WOContext.class });
						NSDictionary d2wExtraInfo = (NSDictionary) infoSelector.invoke(erDirectToWebClazz, context);
						extraInfo.addEntriesFromDictionary(d2wExtraInfo);
					}
					catch (Exception e) {
					}
				}
			}
			if (context.request() != null) {
				extraInfo.setObjectForKey(context.request().uri(), "URL");
				if (context.request().headers() != null) {
					NSMutableDictionary<String, Object> headers = new NSMutableDictionary<>();
					for (Object key : context.request().headerKeys()) {
						String value = context.request().headerForKey(key);
						if (value != null) {
							headers.setObjectForKey(value, key.toString());
						}
					}
					extraInfo.setObjectForKey(headers, "Headers");
				}
			}
			if (context.hasSession()) {
				if (context.session().statistics() != null) {
					extraInfo.setObjectForKey(context.session().statistics(), "PreviousPageList");
				}
				extraInfo.setObjectForKey(context.session(), "Session");
			}
		}
		return extraInfo;
	}

	/**
	 * @return true if [string] is null or empty
	 */
	public static boolean stringIsNullOrEmpty( String string ) {
		return string == null || string.isEmpty();
	}
	
    /** 
     * Returns the version string of the given framework.
     * It checks <code>CFBundleShortVersionString</code> property 
     * in the <code>info.plist</code> resource and returns 
     * a trimmed version of the value.
     * 
     * @param frameworkName name
     * @return version number as string; can be null-string when the framework is not found or the framework doesn't have the value of <code>CFBundleShortVersionString</code> in its <code>info.plist</code> resource.
     * @see #webObjectsVersion()
     */ 
	private static String versionStringForFrameworkNamed(String frameworkName) {
        return valueFromPlistBundleWithKey(NSBundle.bundleForName(frameworkName), "Info.plist", "CFBundleShortVersionString");
    }

    /**
     * Returns the key in an plist of the given framework.
     * 
     * @param bundle bundle name
     * @param plist plist Filename
     * @param key key
     * @return Result
     */
	private static String valueFromPlistBundleWithKey(NSBundle bundle, String plist, String key) {
    	if (bundle == null)
    		return "";

    	String dictString = new String(bundle.bytesForResourcePath(plist));
    	NSDictionary versionDictionary = NSPropertyListSerialization.dictionaryForString(dictString);

    	String versionString = (String) versionDictionary.objectForKey(key);
    	return versionString == null  ?  ""  :  versionString.trim(); // trim() removes the line ending char
    }
}
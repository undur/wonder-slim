/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import com.webobjects.foundation.NSSelector;

import er.extensions.components.ERXStatelessComponent;

/**
 * Diverse collection of utility methods for handling everything from EOF to
 * foundation. In the future this class will most likely be split into more
 * meaning full groups of utility methods.
 */
public class ERXUtilities {

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
}
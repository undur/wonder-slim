/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSSelector;

public class ERXUtilities {

	private static final Class[] NotificationClassArray = { com.webobjects.foundation.NSNotification.class };

	/**
	 * @return A selector suitable for firing a notification
	 */
	public static NSSelector<Void> notificationSelector(String methodName) {
		return new NSSelector<>(methodName, ERXUtilities.NotificationClassArray);
	}

	/**
	 * Read and parse a string plist resource
	 *
	 * @param filename name of the file
	 * @param frameworkName name of framework containing resource, <code>null</code> or 'app' for the application bundle
	 * @param languages language list search order
	 * @param encoding string encoding of the resource
	 * 
	 * @return de-serialized plist from the resource
	 */
	public static Object readPListFromBundleResource(final String filename, final String frameworkName, final NSArray<String> languages, Charset charset) {
		final String plistString = readStringFromBundleResource( filename, frameworkName, languages, charset);
		return NSPropertyListSerialization.propertyListFromString(plistString);
	}

	/**
	 * Read a string resource 
	 *
	 * @param filename name of the file
	 * @param frameworkName name of framework containing resource, <code>null</code> or 'app' for the application bundle
	 * @param languages language list search order
	 * @param charset string Charset of the resource
	 * 
	 * @return string content of the resource
	 */
	public static String readStringFromBundleResource(final String filename, final String frameworkName, final NSArray<String> languages, Charset charset) {
		try( final InputStream stream = WOApplication.application().resourceManager().inputStreamForResourceNamed(filename, frameworkName, languages)) {
			
			if( stream == null ) {
				return null;
			}
			
			return new String(stream.readAllBytes(), charset);
		}
		catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	/**
	 * @return true if [string] is null or empty
	 */
	public static boolean stringIsNullOrEmpty( String string ) {
		return string == null || string.isEmpty();
	}

	/**
	 * Wrapper class for everything related to that single method for obtaining a private field/method value
	 * 
	 * FIXME: Delete and replace with a couple of methods // Hugi 2025-10-16
	 */
	public static class ERXPrivateer {

		public static Object privateValueForKey(Object target, String key) {
			final Field field = accessibleFieldForKey(target, key);

			try {
				if (field != null) {
					return field.get(target);
				}

				final Method method = accessibleMethodForKey(target, key);

				if (method != null) {
					return method.invoke(target, (Object[]) null);
				}

				throw new NSKeyValueCoding.UnknownKeyException("Key " + key + " not found", target, key);
			}
			catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
		}
		
		private static Field accessibleFieldForKey(Object target, String key) {
			final Field f = fieldForKey(target, key);

			if (f != null) {
				f.setAccessible(true);
			}

			return f;
		}
		
		private static Method accessibleMethodForKey(Object target, String key) {
			final Method f = methodForKey(target, key);

			if (f != null) {
				f.setAccessible(true);
			}

			return f;
		}
		
		private static Field fieldForKey(Object target, String key) {
			Field result = null;
			Class c = target.getClass();

			while (c != null) {
				try {
					result = c.getDeclaredField(key);

					if (result != null) {
						return result;
					}
				}
				catch (SecurityException e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
				catch (NoSuchFieldException e) {
					c = c.getSuperclass();
				}
			}

			return null;
		}
		
		private static Method methodForKey(Object target, String key) {
			Method result = null;
			Class c = target.getClass();

			while (c != null) {
				try {
					result = c.getDeclaredMethod(key);

					if (result != null) {
						return result;
					}
				}
				catch (SecurityException e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
				catch (NoSuchMethodException e) {
					c = c.getSuperclass();
				}
			}

			return null;
		}
	}
}
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
import java.util.Enumeration;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSSelector;

import er.extensions.appserver.ERXWOContext;

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
	 * @return de-serialized plist from the resource
	 */
	public static Object readPListFromBundleResource(final String filename, final String frameworkName, final NSArray<String> languages, Charset charset) {
		return NSPropertyListSerialization.propertyListFromString(readStringFromBundleResource( filename, frameworkName, languages, charset));
	}

	/**
	 * Read a string resource 
	 *
	 * @param filename name of the file
	 * @param frameworkName name of framework containing resource, <code>null</code> or 'app' for the application bundle
	 * @param languages language list search order
	 * @param charset string Charset of the resource
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
	 * @param context the current context
	 * @return dictionary containing extra information for the current context.
	 */
	public static NSMutableDictionary extraInformationForExceptionInContext(WOContext context) {
		final NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		extraInfo.addEntriesFromDictionary(informationForContext(context));
		extraInfo.addEntriesFromDictionary(informationForBundles());
		return extraInfo;
	}

	private static NSMutableDictionary<String, Object> informationForBundles() {
		final NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		final NSMutableDictionary<String, Object> bundleVersions = new NSMutableDictionary<String, Object>();

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
		final NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		
		if (context != null) {
			if (context.page() != null) {
				extraInfo.setObjectForKey(context.page().name(), "CurrentPage");
				if (context.component() != null) {
					extraInfo.setObjectForKey(context.component().name(), "CurrentComponent");
					if (context.component().parent() != null) {
						extraInfo.setObjectForKey(ERXWOContext.componentPath(context), "CurrentComponentHierarchy");
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
        NSBundle bundle = NSBundle.bundleForName(frameworkName);

    	if (bundle == null) {
    		return "";
    	}

    	final String dictString = new String(bundle.bytesForResourcePath("Info.plist"));
    	final NSDictionary versionDictionary = NSPropertyListSerialization.dictionaryForString(dictString);
    	final String versionString = (String) versionDictionary.objectForKey("CFBundleShortVersionString");
    	return versionString == null  ?  ""  :  versionString.trim(); // trim() removes the line ending char
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
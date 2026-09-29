/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.math.BigDecimal;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSProperties;

/**
 * Typed reading of the application's properties: a property's value as a string, boolean, number, array or enum, with
 * or without a default. The properties themselves are composed, reloaded and watched by {@link ERXConfigurationManager}.
 *
 * The string, boolean, int, long and array readers are bridges to NSProperties' (ERFoundation's), which read and convert
 * values identically, as ERXPropertiesParityTest shows; code can use either. Values are read and converted on each call,
 * not cached, so a value is always the one in effect. NSProperties' own cache ({@code NSProperties.cacheEnabled}, off by
 * default) must stay off: with it on, reading a key as one type and then another gives wrong values or exceptions.
 */
public class ERXProperties {

	private ERXProperties() {}

	/**
	 * @return The property's value as an array (property list format: {@code (a, b)}), null if it has none
	 */
	public static NSArray<String> arrayForKey( final String propertyName ) {
		return NSProperties.arrayForKey( propertyName );
	}

	/**
	 * @return The property's value as an array (property list format: {@code (a, b)}), the default if it has none
	 */
	public static NSArray<String> arrayForKeyWithDefault( final String propertyName, final NSArray<String> defaultValue ) {
		return NSProperties.arrayForKeyWithDefault( propertyName, defaultValue );
	}

	/**
	 * @return The property's value as a boolean ({@code true}/{@code yes}/{@code y} or a non-zero number), false if it has none
	 */
	public static boolean booleanForKey( final String propertyName ) {
		return NSProperties.booleanForKey( propertyName );
	}

	/**
	 * @return The property's value as a boolean ({@code true}/{@code yes}/{@code y} or a non-zero number), the default if it has none
	 */
	public static boolean booleanForKeyWithDefault( final String propertyName, final boolean defaultValue ) {
		return NSProperties.booleanForKeyWithDefault( propertyName, defaultValue );
	}

	/**
	 * @return The property's value as an int, 0 if it has none
	 */
	public static int intForKey( final String propertyName ) {
		return NSProperties.intForKey( propertyName );
	}

	/**
	 * @return The property's value as an int, the default if it has none
	 */
	public static int intForKeyWithDefault( final String propertyName, final int defaultValue ) {
		return NSProperties.intForKeyWithDefault( propertyName, defaultValue );
	}

	/**
	 * @return The property's value as a long, the default if it has none
	 */
	public static long longForKeyWithDefault( final String propertyName, final long defaultValue ) {
		return NSProperties.longForKeyWithDefault( propertyName, defaultValue );
	}

	/**
	 * @return The property's value as a BigDecimal, the default if it has none
	 */
	public static BigDecimal bigDecimalForKeyWithDefault( final String propertyName, final BigDecimal defaultValue ) {
		return ERXValueUtilities.bigDecimalValueWithDefault( NSProperties.getProperty( propertyName ), defaultValue );
	}

	/**
	 * @return The property's value, null if it has none
	 */
	public static String stringForKey( final String propertyName ) {
		return NSProperties.stringForKey( propertyName );
	}

	/**
	 * @return The property's value, the default if it has none
	 */
	public static String stringForKeyWithDefault( final String propertyName, final String defaultValue ) {
		return NSProperties.stringForKeyWithDefault( propertyName, defaultValue );
	}

	/**
	 * @return The constant of the given enum named by the property's value, ignoring case, null if it has none or none matches
	 */
	public static <T extends Enum> T enumValueForKey( final Class<T> enumClass, final String propertyName ) {
		final String stringValue = stringForKey( propertyName );

		if( stringValue != null ) {
			for( final T enumValue : enumClass.getEnumConstants() ) {
				if( enumValue.name().equalsIgnoreCase( stringValue ) ) {
					return enumValue;
				}
			}
		}

		return null;
	}

	/**
	 * Clears NSProperties' cache (if it's caching) after the configuration changed the system properties
	 */
	static void systemPropertiesChanged() {
		NSNotificationCenter.defaultCenter().postNotification( NSProperties.PropertiesDidChange, null, null );
	}
}

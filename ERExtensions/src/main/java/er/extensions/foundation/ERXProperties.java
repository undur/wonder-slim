/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSProperties;

/**
 * Typed reading of the application's properties: a property's value as a string, boolean, number, array or enum, with
 * or without a default, converted once and cached. The properties themselves are composed, reloaded and watched by
 * {@link ERXConfigurationManager}, which clears this cache whenever it changes them.
 */
public class ERXProperties {

    private static String UndefinedMarker = "-undefined-";

    /**
     * All methods on ERXProperties are static, no instances allowed.
     */
    private ERXProperties() {}

    /** 
    * Internal cache of type converted values to avoid reconverting attributes that are asked for frequently 
    */
    private static Map<String, Object> _cache = new ConcurrentHashMap<>();

    /**
     * Cover method for returning an NSArray for a given system property.
     * 
     * @param s system property
     * @return array de-serialized from the string in the system properties
     */
	public static NSArray<String> arrayForKey(String s) {
        return arrayForKeyWithDefault(s, null);
    }

    /**
     * Cover method for returning an NSArray for a
     * given system property and set a default value if not given.
     * 
     * @param s system property
     * @param defaultValue default value
     * @return array de-serialized from the string in the system properties or default value
     */
	public static NSArray<String> arrayForKeyWithDefault(final String propertyName, final NSArray<String> defaultValue) {
		NSArray<String> value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof NSArray) {
			value = (NSArray) cachedValue;
		} else {
			value = ERXValueUtilities.arrayValueWithDefault(NSProperties.getProperty(propertyName), null);
			_cache.put(propertyName, value == null ? UndefinedMarker : value);
			if (value == null) {
				value = defaultValue;
			}
		}
		return value;
    }
    
    /**
     * 	Cover method for returning a boolean for a
     * 	given system property. This method uses the
     * 	method <code>booleanValue</code> from
     * 	{@link ERXUtilities}.
     * 
     * @param propertyName system property
     * 
     * @return boolean value of the string in the system properties.
     */
	public static boolean booleanForKey(String propertyName) {
        return booleanForKeyWithDefault(propertyName, false);
    }

    /**
     * Cover method for returning a boolean for a
     * given system property or a default value. This method uses the
     * method <code>booleanValue</code> from
     * {@link ERXUtilities}.
     * 
     * @param s system property
     * @param defaultValue default value
     * @return boolean value of the string in the system properties.
     */
	public static boolean booleanForKeyWithDefault(final String propertyName, final boolean defaultValue) {
        boolean value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Boolean) {
			value = ((Boolean) cachedValue).booleanValue();
		} else {
			Boolean objValue = ERXValueUtilities.BooleanValueWithDefault(NSProperties.getProperty(propertyName), null);
			_cache.put(propertyName, objValue == null ? UndefinedMarker : objValue);
			if (objValue == null) {
				value = defaultValue;
			} else {
				value = objValue.booleanValue();
			}
		}
		return value;
    }

    /**
     * Cover method for returning an int for a given system property.
     * 
     * @param s system property
     * @return int value of the system property or 0
     */
	public static int intForKey(String s) {
        return intForKeyWithDefault(s, 0);
    }

    /**
     * Cover method for returning an int for a
     * given system property with a default value.
     * 
     * @param s system property
     * @param defaultValue default value
     * @return int value of the system property or the default value
     */
	public static int intForKeyWithDefault(final String propertyName, final int defaultValue) {
		int value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Integer) {
			value = ((Integer) cachedValue).intValue();
		} else {
			Integer objValue = ERXValueUtilities.IntegerValueWithDefault(NSProperties.getProperty(propertyName), null);
			_cache.put(propertyName, objValue == null ? UndefinedMarker : objValue);
			if (objValue == null) {
				value = defaultValue;
			} else {
				value = objValue.intValue();
			}
		}
		return value;
    }

    /**
     * Cover method for returning a BigDecimal for a
     * given system property or a default value. This method uses the
     * method <code>bigDecimalValueWithDefault</code> from
     * {@link ERXValueUtilities}.
     * 
     * @param s system property
     * @param defaultValue default value
     * @return BigDecimal value of the string in the system properties. Scale is controlled by the string, ie "4.400" will have a scale of 3.
     */
	public static BigDecimal bigDecimalForKeyWithDefault(String propertyName, BigDecimal defaultValue) {
        Object value = _cache.get(propertyName);
        if (UndefinedMarker.equals(value)) {
            return defaultValue;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal)value;
        }
        
        String propertyValue = NSProperties.getProperty(propertyName);
        final BigDecimal bigDecimal = ERXValueUtilities.bigDecimalValueWithDefault(propertyValue, defaultValue);
        _cache.put(propertyName, propertyValue == null ? UndefinedMarker : bigDecimal);
        return bigDecimal;
    }

    /**
     * Cover method for returning a long for a
     * given system property with a default value.
     * 
     * @param s system property
     * @param defaultValue default value
     * @return long value of the system property or the default value
     */
	public static long longForKeyWithDefault(final String propertyName, final long defaultValue) {
		long value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Long) {
			value = ((Long) cachedValue).longValue();
		} else {
			Long objValue = ERXValueUtilities.LongValueWithDefault(NSProperties.getProperty(propertyName), null);
			_cache.put(propertyName, objValue == null ? UndefinedMarker : objValue);
			if (objValue == null) {
				value = defaultValue;
			} else {
				value = objValue.longValue();
			}
		}
		return value;
    }
    
    /**
     * Returning an string for a given system 
     * property. This is a cover method of 
     * {@link java.lang.System#getProperty}
     * 
     * @param s system property
     * @return string value of the system property or null
     */
	public static String stringForKey(String s) {
        return stringForKeyWithDefault(s, null);
    }

    /**
     * Returning an string for a given system
     * property. This is a cover method of
     * {@link java.lang.System#getProperty}
     * 
     * @param s system property
     * @param defaultValue default value
     * @return string value of the system property or null
     */
	public static String stringForKeyWithDefault(final String propertyName, final String defaultValue) {
        final String propertyValue = NSProperties.getProperty(propertyName);
        final String stringValue = propertyValue == null ? defaultValue : propertyValue;
        return stringValue == UndefinedMarker ? null : stringValue;
    }

    /**
     * Returns an enum value for a given enum class and system property. If the property is not
     * set or matches no enum constant, <code>null</code> will be returned. The search for the
     * enum value is case insensitive, i.e. a property value "foo" will match the enum constant
     * <code>FOO</code>.
     * 
     * @param enumClass the enum class
     * @param key the property key
     * @return the enum value
     */
    public static <T extends Enum> T enumValueForKey(Class<T> enumClass, String key) {
    	return enumValueForKeyWithDefault(enumClass, key, null);
    }

    /**
     * Returns an enum value for a given enum class and system property. If the property is not
     * set or matches no enum constant, the specified default value will be returned. The
     * search for the enum value is case insensitive, i.e. a property value "foo" will match
     * the enum constant <code>FOO</code>.
     * 
     * @param enumClass the enum class
     * @param key the property key
     * @param defaultValue the default value
     * @return the enum value
     */
    private static <T extends Enum> T enumValueForKeyWithDefault(Class<T> enumClass, String key, T defaultValue) {
    	T result = defaultValue;
    	String stringValue = stringForKey(key);
    	if (stringValue != null) {
    		for (T enumValue : enumClass.getEnumConstants()) {
    			if (enumValue.name().equalsIgnoreCase(stringValue)) {
    				result = enumValue;
    				break;
    			}
    		}
    	}
    	return result;
    }

    /**
     * Clears the cached values, and NSProperties', after the configuration changed the system properties
     */
    static void systemPropertiesChanged() {
        _cache.clear();
        // NSProperties (ERFoundation's) caches property values and clears the cache on this notification; without it,
        // properties read through NSProperties would keep their old values
        NSNotificationCenter.defaultCenter().postNotification(NSProperties.PropertiesDidChange, null, null);
    }
}

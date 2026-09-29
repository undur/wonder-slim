/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSProperties;

public class ERXProperties {

    private static String UndefinedMarker = "-undefined-";

    private static final Logger log = LoggerFactory.getLogger(ERXProperties.class);

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
     * Copies all properties from source to dest. 
     * 
     * @param source properties copied from
     * @param dest properties copied to
     */
    public static void transferPropertiesFromSourceToDest(Properties source, Properties dest) {
        if (source != null) {
            dest.putAll(source);
            if (dest == System.getProperties()) {
                systemPropertiesChanged();
            }
        }
    }
    
    /**
     * Gets the properties for a given file.
     * 
     * @param file the properties file
     * @return properties from the given file
     * @throws java.io.IOException if the file is not found or cannot be read
     */
	static Properties propertiesFromFile(File file) throws java.io.IOException {
        if (file == null)
            throw new IllegalStateException("Attempting to get properties for a null file!");
        ERXProperties._Properties prop = new ERXProperties._Properties();
        prop.load(file);
        return prop;
    }
    
    /**
     * Sets and returns properties object with the values from  the given command line arguments string array. 
     * 
     * @param argv string array typically provided by the command line arguments
     * @return properties object with the values from the argv
     */
	public static Properties propertiesFromArgv(String[] argv) {
    	ERXProperties._Properties properties = new ERXProperties._Properties();
        NSDictionary argvDict = NSProperties.valuesFromArgv(argv);
        Enumeration e = argvDict.allKeys().objectEnumerator();
        while (e.hasMoreElements()) {
            Object key = e.nextElement();
            properties.put(key, argvDict.objectForKey(key));
        }
        return properties;
    }


	/**
	 * @return The keys a properties file, a JVM option or an argument set, see {@link ERXConfigurationManager#keys()}. The rest
	 *         of what is in effect are WebObjects and JVM defaults.
	 */
	public static java.util.Set<String> explicitlySetKeys() {
		final ERXConfigurationManager configuration = ERXConfigurationManager.current();
		return configuration == null ? java.util.Set.of() : configuration.keys();
	}

	/**
	 * @return true if the key names something that must not be written to a log: passwords, API keys,
	 *         tokens, secrets and credentials of any spelling
	 */
	public static boolean isSecretKey(String key) {
		return key != null && SECRET_KEY_PATTERN.matcher(key).find();
	}

	/**
	 * @return The value as it may be shown or logged: masked entirely if the key names a secret (see
	 *         {@link #isSecretKey(String)}), otherwise with the value of every secret property masked wherever it
	 *         appears in it. The JVM's own record of the command line ({@code sun.java.command}) carries the
	 *         application's arguments, passwords included.
	 */
	public static String maskedValue(final String key, final String value) {
		if (value == null) {
			return null;
		}

		if (isSecretKey(key)) {
			return MASK;
		}

		String result = value;

		for (final String otherKey : System.getProperties().stringPropertyNames()) {
			if (isSecretKey(otherKey)) {
				final String secret = System.getProperty(otherKey);

				// Very short values would mask ordinary text, and aren't worth hiding anyway
				if (secret != null && secret.length() >= 4) {
					result = result.replace(secret, MASK);
				}
			}
		}

		return result;
	}

	private static final String MASK = "********";

	private static final java.util.regex.Pattern SECRET_KEY_PATTERN = java.util.regex.Pattern.compile("(?i)(password|passwd|secret|api[._-]?key|access[._-]?key|private[._-]?key|token|credential)");


    /**
     * Returns all of the properties in the system mapped to their evaluated values, sorted by key.
     * 
     * @param properties
     * @param protectValues if <code>true</code>, keys with the word "password" in them will have their values removed 
     * @return all of the properties in the system mapped to their evaluated values, sorted by key
     */
    private static Map<String, String> propertiesMap(Properties properties, boolean protectValues) {
    	Map<String, String> props = new TreeMap<>();
    	for (Enumeration e = properties.keys(); e.hasMoreElements();) {
    		String key = (String) e.nextElement();
    		final String value = String.valueOf(properties.getProperty(key));
    		props.put(key, protectValues ? maskedValue(key, value) : value);
    	}
    	return props;
    }
    
    /**
     * Returns a string suitable for logging.
     * 
     * @param properties
     * @return string for logging
     */
    public static String logString(Properties properties) {
    	StringBuilder message = new StringBuilder();
        for (Map.Entry<String, String> entry : propertiesMap(properties, true).entrySet()) {
        	message.append("  " + entry.getKey() + "=" + entry.getValue() + "\n");
        }
        return message.toString();
    }
    

    static void systemPropertiesChanged() {
        _cache.clear();
        // NSProperties (ERFoundation's) caches property values and clears the cache on this notification; without it,
        // properties read through NSProperties would keep their old values
        NSNotificationCenter.defaultCenter().postNotification(NSProperties.PropertiesDidChange, null, null);
    }

	/**
	 * _Properties is a subclass of Properties that provides support for including other
	 * Properties files on the fly.  If you create a property named .includeProps, the value
	 * will be interpreted as a file to load.  If the path is absolute, it will just load it
	 * directly.  If it's relative, the path will be loaded relative to the current user's
	 * home directory.  Multiple .includeProps can be included in a Properties file and they
	 * will be loaded in the order they appear within the file.
	 */
	private static class _Properties extends Properties {

		private static final Logger log = LoggerFactory.getLogger(ERXProperties.class);

		public static final String IncludePropsKey = ".includeProps";
		
		private Stack<File> _files = new Stack<>();
		
		@Override
		public synchronized Object put(Object key, Object value) {
			if (_Properties.IncludePropsKey.equals(key)) {
				String propsFileName = (String)value;
                File propsFile = new File(propsFileName);
                if (!propsFile.isAbsolute()) {
                    // if we don't have any context for a relative (non-absolute) props file,
                    // we presume that it's relative to the user's home directory
    				File cwd = null;
    				if (_files.size() > 0) {
    					cwd = _files.peek();
    				}
    				else {
    					cwd = new File(System.getProperty("user.home"));
                	}
                    propsFile = new File(cwd, propsFileName);
                }

                // Detect mutually recursing props files by tracking what we've already loaded:
                String existingIncludeProps = getProperty(_Properties.IncludePropsKey);
                if (existingIncludeProps == null) {
                	existingIncludeProps = "";
                }
                if (existingIncludeProps.indexOf(propsFile.getPath()) > -1) {
                    log.error("_Properties.load(): recursive includeProps detected! {} in {}", propsFile, existingIncludeProps);
                    log.error("_Properties.load() cannot proceed - QUITTING!");
                    System.exit(1);
                }
                if (existingIncludeProps.length() > 0) {
                	existingIncludeProps += ", ";
                }
                existingIncludeProps += propsFile;
                super.put(_Properties.IncludePropsKey, existingIncludeProps);

                try {
                    log.info("_Properties.load(): Including props file: {}", propsFile);
					load(propsFile);
				} catch (IOException e) {
					throw new RuntimeException("Failed to load the property file '" + value + "'.", e);
				}
				return null;
			}
			return super.put(key, value);
		}

		public synchronized void load(File propsFile) throws IOException {
			_files.push(propsFile.getParentFile());
			try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(propsFile))) {
	            load(is);
			}
			finally {
				_files.pop();
			}
		}
	}
    
}
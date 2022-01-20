/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;

import er.extensions.appserver.ERXApplication;

public class ERXProperties extends Properties implements NSKeyValueCoding {

	/**
	 * Do I need to update serialVersionUID?
	 * See section 5.6 <cite>Type Changes Affecting Serialization</cite> on page 51 of the 
	 * <a href="http://java.sun.com/j2se/1.4/pdf/serial-spec.pdf">Java Object Serialization Spec</a>
	 */
	private static final long serialVersionUID = 1L;

    /** default string */
    public static final String DefaultString = "Default";
    
    private static String UndefinedMarker = "-undefined-";

    private static final Logger log = LoggerFactory.getLogger(ERXProperties.class);
    private static final Logger configLog = LoggerFactory.getLogger(ERXConfigurationManager.class);

    private static final Map<String, String> AppSpecificPropertyNames = new HashMap<>(128);

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
     * Converts the standard propertyName into one with a .&lt;AppName&gt; on the end, if the property is defined with
     * that suffix.  If not, then this caches the standard propertyName.  A cache is maintained to avoid concatenating
     * strings frequently, but may be overkill since most usage of this system doesn't involve frequent access.
     */
	private static String getApplicationSpecificPropertyName(final String propertyName) {
    	return propertyName;
    }

    /**
     * Cover method for returning an NSArray for a
     * given system property and set a default value if not given.
     * 
     * @param s system property
     * @param defaultValue default value
     * @return array de-serialized from the string in the system properties or default value
     */
	public static NSArray<String> arrayForKeyWithDefault(final String s, final NSArray<String> defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);
		NSArray<String> value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof NSArray) {
			value = (NSArray) cachedValue;
		} else {
			value = ERXValueUtilities.arrayValueWithDefault(ERXSystem.getProperty(propertyName), null);
			_cache.put(s, value == null ? UndefinedMarker : value);
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
     * @param s system property
     * 
     * @return boolean value of the string in the system properties.
     */
	public static boolean booleanForKey(String s) {
        return booleanForKeyWithDefault(s, false);
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
	public static boolean booleanForKeyWithDefault(final String s, final boolean defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);
        boolean value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Boolean) {
			value = ((Boolean) cachedValue).booleanValue();
		} else {
			Boolean objValue = ERXValueUtilities.BooleanValueWithDefault(ERXSystem.getProperty(propertyName), null);
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
	public static int intForKeyWithDefault(final String s, final int defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);
		int value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Integer) {
			value = ((Integer) cachedValue).intValue();
		} else {
			Integer objValue = ERXValueUtilities.IntegerValueWithDefault(ERXSystem.getProperty(propertyName), null);
			_cache.put(s, objValue == null ? UndefinedMarker : objValue);
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
	public static BigDecimal bigDecimalForKeyWithDefault(String s, BigDecimal defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);

        Object value = _cache.get(propertyName);
        if (UndefinedMarker.equals(value)) {
            return defaultValue;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal)value;
        }
        
        String propertyValue = ERXSystem.getProperty(propertyName);
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
	public static long longForKeyWithDefault(final String s, final long defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);
		long value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Long) {
			value = ((Long) cachedValue).longValue();
		} else {
			Long objValue = ERXValueUtilities.LongValueWithDefault(ERXSystem.getProperty(propertyName), null);
			_cache.put(s, objValue == null ? UndefinedMarker : objValue);
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
	public static String stringForKeyWithDefault(final String s, final String defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);
        final String propertyValue = ERXSystem.getProperty(propertyName);
        final String stringValue = propertyValue == null ? defaultValue : propertyValue;
        return stringValue == UndefinedMarker ? null : stringValue;
    }
    
    /**
     * Returns an array of strings separated with the given separator string.
     * 
     * @param key the key to lookup
     * @param separator the separator (",")
     * @return the array of strings or NSArray.EmptyArray if not found
     */
    @SuppressWarnings({ "unchecked" })
    public static NSArray<String> componentsSeparatedByString(String key, String separator) {
    	return ERXProperties.componentsSeparatedByStringWithDefault(key, separator, NSArray.EmptyArray);
    }

    /**
     * Returns an array of strings separated with the given separator string.
     * 
     * @param key the key to lookup
     * @param separator the separator (",")
     * @param defaultValue the default array to return if there is no value
     * @return the array of strings
     */
    @SuppressWarnings({ "unchecked" })
	public static NSArray<String> componentsSeparatedByStringWithDefault(String key, String separator, NSArray<String> defaultValue) {
    	NSArray<String> array;
    	String str = stringForKeyWithDefault(key, null);
    	if (str == null) {
    		array = defaultValue;
    	}
    	else {
    		array = NSArray.componentsSeparatedByString(str, separator);
    	}
    	return array;
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
    public static <T extends Enum> T enumValueForKeyWithDefault(Class<T> enumClass, String key, T defaultValue) {
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
     * Sets an array in the System properties for a particular key.
     * 
     * @param array to be set in the System properties
     * @param key to be used to get the value
     */
    public static void setArrayForKey(NSArray array, String key) {
        setStringForKey(NSPropertyListSerialization.stringFromPropertyList(array), key);
    }

    /**
     * Sets a string in the System properties for another string.
     * 
     * @param string to be set in the System properties
     * @param key to be used to get the value
     */
    // DELETEME: Really not needed anymore -- MS: Why?  We need the cache clearing.
    public static void setStringForKey(String string, String key) {
        System.setProperty(key, string);
        _cache.remove(key);
    }

    public static void removeKey(String key) {
    	System.getProperties().remove(key);
    	_cache.remove(key);
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
     * Reads a Java properties file at the given path 
     * and returns a {@link java.util.Properties Properties} object 
     * as the result. If the file does not exist, returns 
     * an empty properties object. 
     * 
     * @param path file path to the properties file
     * @return properties object with the values from the file specified.
     */
    // FIXME: This shouldn't eat the exception
	public static Properties propertiesFromPath(String path) {
    	ERXProperties._Properties prop = new ERXProperties._Properties();

        if (path == null  ||  path.length() == 0) {
            log.warn("Attempting to read property file for null file path");
            return prop;
        }

        File file = new File(path);
        if (! file.exists()  ||  ! file.isFile()  ||  ! file.canRead()) {
            log.warn("File '{}' doesn't exist or can't be read.", path);
            return prop;
        }

        try {
        	prop.load(file);
            log.debug("Loaded configuration file at path: {}", path);
        } catch (IOException e) {
            log.error("Unable to initialize properties from file '{}'", path, e);
        }
        return prop;
    }

    /**
     * Gets the properties for a given file.
     * 
     * @param file the properties file
     * @return properties from the given file
     * @throws java.io.IOException if the file is not found or cannot be read
     */
	public static Properties propertiesFromFile(File file) throws java.io.IOException {
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
     * Returns an array of paths to the <code>Properties</code> and 
     * <code>WebObjects.properties</code> files contained in the 
     * application/framework bundles and home directory. 
     * <p>
     * If ProjectBuilder (for Mac OS X) has the project opened, 
     * it will attempt to get the path to the one in the project 
     * directory instead of the one in the bundle. 
     * <p>
     * This opened project detection feature is pretty fragile and 
     * will change between versions of the dev-tools.
     * 
     * @return paths to Properties files
     */
	public static NSArray pathsForUserAndBundleProperties() {
        return pathsForUserAndBundleProperties(false);
    }

    private static void addIfPresent(String info, String path, NSMutableArray<String> propertiesPaths, NSMutableArray<String> projectsInfo) {
    	if(path != null && path.length() > 0) {
    		path = getActualPath(path);
    		if(propertiesPaths.containsObject(path)) {
    			log.error("Path was already included: {}", path);
    		}
    		projectsInfo.addObject("  " + info +" -> " + path);
    		propertiesPaths.addObject(path);
    	}
    }
    
    public static NSArray<String> pathsForUserAndBundleProperties(boolean reportLoggingEnabled) {
        NSMutableArray<String> propertiesPaths = new NSMutableArray();
        NSMutableArray<String> projectsInfo = new NSMutableArray();

        /*  Properties for frameworks */
        NSArray frameworkNames = (NSArray) NSBundle.frameworkBundles().valueForKey("name");
        Enumeration e = frameworkNames.reverseObjectEnumerator();
        while (e.hasMoreElements()) {
        	String frameworkName = (String) e.nextElement();

        	String propertyPath = pathForResourceNamed("Properties", frameworkName, null);
        	addIfPresent(frameworkName + ".framework", propertyPath, propertiesPaths, projectsInfo);

        	/** Properties.dev -- per-Framework-dev properties 
        	 * This adds support for Properties.dev in your Frameworks new load order will be
        	 */
        	String devPropertiesPath = ERXApplication.isDevelopmentModeSafe() ? ERXProperties.variantPropertiesInBundle("dev", frameworkName) : null;
        	addIfPresent(frameworkName + ".framework.dev", devPropertiesPath, propertiesPaths, projectsInfo);
        	
        	/** Properties.<userName> -- per-Framework-per-User properties */
        	String userPropertiesPath = ERXProperties.variantPropertiesInBundle(ERXSystem.getProperty("user.name"), frameworkName);
        	addIfPresent(frameworkName + ".framework.user", userPropertiesPath, propertiesPaths, projectsInfo);
        }

		NSBundle mainBundle = NSBundle.mainBundle();
		
		if( mainBundle != null ) {
	        String mainBundleName = mainBundle.name();
	
	        String appPath = pathForResourceNamed("Properties", "app", null);
	    	addIfPresent(mainBundleName + ".app", appPath, propertiesPaths, projectsInfo);
		}

		/*  WebObjects.properties in the user home directory */
		String userHome = ERXSystem.getProperty("user.home");
		if (userHome != null && userHome.length() > 0) {
			File file = new File(userHome, "WebObjects.properties");
			if (file.exists() && file.isFile() && file.canRead()) {
				try {
					String userHomePath = file.getCanonicalPath();
			    	addIfPresent("{$user.home}/WebObjects.properties", userHomePath, propertiesPaths, projectsInfo);
				}
				catch (java.io.IOException ex) {
					log.error("Failed to load the configuration file '{}'.", file, ex);
				}
			}
        }

		/*  Optional properties files */
		if (optionalConfigurationFiles() != null && optionalConfigurationFiles().count() > 0) {
			for (Enumeration configEnumerator = optionalConfigurationFiles().objectEnumerator(); configEnumerator.hasMoreElements();) {
				String configFile = (String) configEnumerator.nextElement();
				File file = new File(configFile);
				if (file.exists() && file.isFile() && file.canRead()) {
					try {
						String optionalPath = file.getCanonicalPath();
				    	addIfPresent("Optional Configuration", optionalPath, propertiesPaths, projectsInfo);
					}
					catch (java.io.IOException ex) {
						log.error("Failed to load configuration file '{}'.", file, ex);
					}
				}
				else {
					log.error("The optional configuration file '{}' either does not exist or could not be read.", file);
				}
			}
		}

		optionalPropertiesLoader(ERXSystem.getProperty("user.name"), propertiesPaths, projectsInfo);
		
        /** /etc/WebObjects/AppName/Properties -- per-Application-per-Machine properties */
        String applicationMachinePropertiesPath = ERXProperties.applicationMachinePropertiesPath("Properties");
    	addIfPresent("Application-Machine Properties", applicationMachinePropertiesPath, propertiesPaths, projectsInfo);

        /** Properties.dev -- per-Application-dev properties */
        String applicationDeveloperPropertiesPath = ERXProperties.applicationDeveloperProperties();
    	addIfPresent("Application-Developer Properties", applicationDeveloperPropertiesPath, propertiesPaths, projectsInfo);

        /** Properties.<userName> -- per-Application-per-User properties */
        String applicationUserPropertiesPath = ERXProperties.applicationUserProperties();
    	addIfPresent("Application-User Properties", applicationUserPropertiesPath, propertiesPaths, projectsInfo);

        /*  Report the result */
		if (reportLoggingEnabled && projectsInfo.count() > 0 && log.isInfoEnabled()) {
			StringBuilder message = new StringBuilder();
			message.append("\n\n").append("ERXProperties has found the following Properties files: \n");
			message.append(projectsInfo.componentsJoinedByString("\n"));
			message.append('\n');
			message.append("ERXProperties currently has the following properties:\n");
			message.append(ERXProperties.logString(ERXSystem.getProperties()));
			// ERXLogger.configureLoggingWithSystemProperties();
			log.info(message.toString());
		}

    	return propertiesPaths.immutableClone();
    }

    /** 
     * 	Making it possible to use Properties File in the Application more
     * 	powerful, specially for newcomers.
     * 	For every Framework it will try to call also following 
     * 		Properties.[Framework] and Properties.[Framework].[Username]
     * 	Also there is a Propertie for
     * 		Properties.log4j, Properties.log4j.[Username] for logging
     * 		Properties.database, Properties.database.[Username] for database infos
     * 		Properties.multilanguage, Properties.multilanguage.[Username] for Encoding
     * 		Properties.migration, Properties.migration.[Username] for Migration
     * 
     * @param userName Username
     * @param propertiesPaths Properites Path {@link ERXProperties#pathsForUserAndBundleProperties}
     * @param projectsInfo Project Info {@link ERXProperties#pathsForUserAndBundleProperties}
     */
    private static void optionalPropertiesLoader(String userName, NSMutableArray<String> propertiesPaths, NSMutableArray<String> projectsInfo) {
    	if(!ERXProperties.booleanForKeyWithDefault("er.extensions.ERXProperties.loadOptionalProperties", true)){
    		return;
    	}
    	
    	/** Properties.log4j.<userName> -- per-Application-per-User properties */
        String logPropertiesPath;
        logPropertiesPath = ERXProperties.variantPropertiesInBundle("log4j", "app");
        if(logPropertiesPath != null) {
        	addIfPresent("Application-User Log4j Properties", logPropertiesPath, propertiesPaths, projectsInfo);
        }
        logPropertiesPath = ERXProperties.variantPropertiesInBundle("log4j." + userName, "app");
        if(logPropertiesPath != null) {
        	addIfPresent("Application-User Log4j Properties", logPropertiesPath, propertiesPaths, projectsInfo);
        }

        /** Properties.database.<userName> -- per-Application-per-User properties */
        String databasePropertiesPath;
        databasePropertiesPath = ERXProperties.variantPropertiesInBundle("database", "app");
        if(databasePropertiesPath != null) {
        	addIfPresent("Application-User Database Properties", databasePropertiesPath, propertiesPaths, projectsInfo);
        }
        databasePropertiesPath = ERXProperties.variantPropertiesInBundle("database." + userName, "app");
        if(databasePropertiesPath != null) {
        	addIfPresent("Application-User Database Properties", databasePropertiesPath, propertiesPaths, projectsInfo);
        }
   	
        /** Properties.multilanguage.<userName> -- per-Application-per-User properties */
        String multilanguagePath;
        multilanguagePath = ERXProperties.variantPropertiesInBundle("multilanguage", "app");
        if(multilanguagePath != null) {
        	addIfPresent("Application-User Multilanguage Properties", multilanguagePath, propertiesPaths, projectsInfo);
        }
        multilanguagePath = ERXProperties.variantPropertiesInBundle("multilanguage." + userName, "app");
        if(multilanguagePath != null) {
        	addIfPresent("Application-User Multilanguage Properties", multilanguagePath, propertiesPaths, projectsInfo);
        }
    	
        /** Properties.migration -- per-Application properties */
        String migrationPath;
        migrationPath = ERXProperties.variantPropertiesInBundle("migration", "app");
        if(migrationPath != null) {
        	addIfPresent("Application-User Migration Properties", migrationPath, propertiesPaths, projectsInfo);
        }
        migrationPath = ERXProperties.variantPropertiesInBundle("migration." + userName, "app");
        if(migrationPath != null) {
        	addIfPresent("Application-User Migration Properties", migrationPath, propertiesPaths, projectsInfo);
        }
    	
        /** Properties.<frameworkName>.<userName> -- per-Application-per-User properties */
        @SuppressWarnings("unchecked")
        NSArray<String> frameworkNames = (NSArray<String>) NSBundle.frameworkBundles().valueForKey("name");
        Enumeration<String> e = frameworkNames.reverseObjectEnumerator();
        while (e.hasMoreElements()) {
          String frameworkName = e.nextElement();
          String userPropertiesPath;
          userPropertiesPath = ERXProperties.variantPropertiesInBundle(frameworkName, "app");
          if(userPropertiesPath != null) {
        	  addIfPresent(frameworkName + ".framework.common", userPropertiesPath, propertiesPaths, projectsInfo);
          }
          userPropertiesPath = ERXProperties.variantPropertiesInBundle(frameworkName + "." + userName, "app");
          if(userPropertiesPath != null) {
        	  addIfPresent(frameworkName + ".framework.user", userPropertiesPath, propertiesPaths, projectsInfo);
          }
        }
    }

    /**
     * Apply the current configuration to the supplied properties.
     * 
     * @param source
     * @param commandLine
     * @return the applied properties
     */
    public static Properties applyConfiguration(Properties source, Properties commandLine) {

    	Properties dest = source != null ? (Properties) source.clone() : new Properties();
    	NSArray additionalConfigurationFiles = ERXProperties.pathsForUserAndBundleProperties(false);

    	if (additionalConfigurationFiles.count() > 0) {
    		for (Enumeration configEnumerator = additionalConfigurationFiles.objectEnumerator(); configEnumerator.hasMoreElements();) {
    			String configFile = (String)configEnumerator.nextElement();
    			File file = new File(configFile);
    			if (file.exists() && file.isFile() && file.canRead()) {
    				try {
    					Properties props = ERXProperties.propertiesFromFile(file);
    					if(log.isDebugEnabled()) {
    						log.debug("Loaded: {}\n{}", file, ERXProperties.logString(props));
    					}
    					ERXProperties.transferPropertiesFromSourceToDest(props, dest);
    				} catch (java.io.IOException ex) {
    					log.error("Unable to load optional configuration file: {}", configFile, ex);
    				}
    			}
    			else {
    				configLog.error("The optional configuration file '{}' either does not exist or cannot be read.", file);
    			}
    		}
    	}

    	if(commandLine != null) {
    		ERXProperties.transferPropertiesFromSourceToDest(commandLine, dest);
    	}
		return dest;
    	
    }

    /**
     * Returns all of the properties in the system mapped to their evaluated values, sorted by key.
     * 
     * @param protectValues if true, keys with the word "password" in them will have their values removed
     * @return all of the properties in the system mapped to their evaluated values, sorted by key
     */
	public static Map<String, String> allPropertiesMap(boolean protectValues) {
    	return propertiesMap(ERXSystem.getProperties(), protectValues);
    }

    /**
     * Returns all of the properties in the system mapped to their evaluated values, sorted by key.
     * 
     * @param properties
     * @param protectValues if <code>true</code>, keys with the word "password" in them will have their values removed 
     * @return all of the properties in the system mapped to their evaluated values, sorted by key
     */
    public static Map<String, String> propertiesMap(Properties properties, boolean protectValues) {
    	Map<String, String> props = new TreeMap<>();
    	for (Enumeration e = properties.keys(); e.hasMoreElements();) {
    		String key = (String) e.nextElement();
    		if (protectValues && key.toLowerCase().contains("password")) {
    			props.put(key, "<deleted for log>");
    		}
    		else {
    			props.put(key, String.valueOf(properties.getProperty(key)));
    		}
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
    
    public static class Property {
    	public String key, value;
    	public Property(String key, String value) {
    		this.key = key;
    		this.value = value;
    	}
    	@Override
    	public String toString() {
    		return key + " = " + value;
    	}
    }

    public static NSArray<Property> allProperties() {
    	NSMutableArray<Property> props = new NSMutableArray<>();
    	for (Enumeration e = ERXSystem.getProperties().keys(); e.hasMoreElements();) {
    		String key = (String) e.nextElement();
    		String object = "" + ERXSystem.getProperty(key);
    		props.addObject(new Property(key, object));
    	}
    	return (NSArray) props.valueForKey("@sortAsc.key");
     }

    /** 
     * Returns the full path to the Properties file under the 
     * given project path. At the current implementation, 
     * it looks for /Properties and /Resources/Properties. 
     * If the Properties file doesn't exist, returns null.  
     * 
     * @param projectPath string to the project root directory
     * @return the path to the Properties file if it exists
     */
	public static String pathForPropertiesUnderProjectPath(String projectPath) {
        String path = null; 
        final List<String> supportedPropertiesPaths = Arrays.asList( "/Properties", "/Resources/Properties");

        for( String s : supportedPropertiesPaths ) {
            File file = new File(projectPath + s);
            if (file.exists()  &&  file.isFile()  &&  file.canRead()) {
                try {
                    path = file.getCanonicalPath();
                } catch (IOException ex) {
                    log.error("Could not get canonical path from {}", file, ex);
                }
                break;
            }
        }
        return path;
    }
    
    /**
     * Returns the application-specific user properties.
     * 
     * @return application-specific user properties
     */
	public static String applicationDeveloperProperties() {
    	String applicationDeveloperPropertiesPath = null;
    	if (ERXApplication.isDevelopmentModeSafe()) {
	        String devName = ERXSystem.getProperty("er.extensions.ERXProperties.devPropertiesName", "dev");
	        applicationDeveloperPropertiesPath = variantPropertiesInBundle(devName, "app");
    	}
        return applicationDeveloperPropertiesPath;
    }
    
    /**
     * Returns the application-specific variant properties for the given bundle.
     * 
     * @param userName 
     * @param bundleName 
     * @return the application-specific variant properties for the given bundle.
     */
    public static String variantPropertiesInBundle(String userName, String bundleName) {
    	String applicationUserPropertiesPath = null;
        if (userName != null  &&  userName.length() > 0) { 
        	String resourceApplicationUserPropertiesPath = pathForResourceNamed("Properties." + userName, bundleName, null);
            if (resourceApplicationUserPropertiesPath != null) {
            	applicationUserPropertiesPath = ERXProperties.getActualPath(resourceApplicationUserPropertiesPath);
            }
        }
        return applicationUserPropertiesPath;
    }

    /**
     * Returns the application-specific user properties.
     * 
     * @return the application-specific user properties
     */
	public static String applicationUserProperties() {
    	return variantPropertiesInBundle(ERXSystem.getProperty("user.name"), "app");
    }
    
    /**
     * Returns the path to the application-specific system-wide file "fileName".  By default this path is /etc/WebObjects, 
     * and the application name will be appended.  For instance, if you are asking for the MyApp Properties file for the
     * system, it would go in /etc/WebObjects/MyApp/Properties.
     * 
     * @param fileName the Filename
     * @return the path, or null if the path does not exist
     */
	public static String applicationMachinePropertiesPath(String fileName) {
    	String applicationMachinePropertiesPath = null;
    	String machinePropertiesPath = ERXSystem.getProperty("er.extensions.ERXProperties.machinePropertiesPath", "/etc/WebObjects");
    	WOApplication application = WOApplication.application();
    	String applicationName;
    	if (application != null) {
    		applicationName = application.name();
    	}
    	else {
    		applicationName = ERXSystem.getProperty("WOApplicationName");
    		if (applicationName == null) {
    			NSBundle mainBundle = NSBundle.mainBundle();
    			if (mainBundle != null) {
    				applicationName = mainBundle.name();
    			}
    			if (applicationName == null) {
    				applicationName = "Unknown";
    			}
    		}
    	}
    	File applicationPropertiesFile = new File(machinePropertiesPath + File.separator + fileName);
    	if (!applicationPropertiesFile.exists()) {
    		applicationPropertiesFile = new File(machinePropertiesPath + File.separator + applicationName + File.separator + fileName);
    	}
    	if (applicationPropertiesFile.exists()) {
    		try {
    			applicationMachinePropertiesPath = applicationPropertiesFile.getCanonicalPath();
    		}
    		catch (IOException e) {
    			log.error("Failed to load machine Properties file '{}'.", fileName, e);
    		}
    	}
    	return applicationMachinePropertiesPath;
    }

    /**
     * Gets an array of optionally defined configuration files.  For each file, if it does not
     * exist as an absolute path, ERXProperties will attempt to resolve it as an application resource
     * and use that instead.
     * 
     * @return array of configuration file names
     */
	public static NSArray optionalConfigurationFiles() {
    	NSArray immutableOptionalConfigurationFiles = arrayForKey("er.extensions.ERXProperties.OptionalConfigurationFiles");
    	NSMutableArray optionalConfigurationFiles = null;
    	if (immutableOptionalConfigurationFiles != null) {
    		optionalConfigurationFiles = immutableOptionalConfigurationFiles.mutableClone();
	    	for (int i = 0; i < optionalConfigurationFiles.count(); i ++) {
	    		String optionalConfigurationFile = (String)optionalConfigurationFiles.objectAtIndex(i);
	    		if (!new File(optionalConfigurationFile).exists()) {
		        	String resourcePropertiesPath = pathForResourceNamed(optionalConfigurationFile, "app", null);
		        	if (resourcePropertiesPath != null) {
		            	optionalConfigurationFiles.replaceObjectAtIndex(ERXProperties.getActualPath(resourcePropertiesPath), i);
		        	}
	    		}
	    	}
    	}
    	return optionalConfigurationFiles;
    }
    
    /**
     * Returns actual full path to the given file system path  
     * that could contain symbolic links. For example: 
     * /Resources will be converted to /Versions/A/Resources
     * when /Resources is a symbolic link.
     * 
     * @param path path string to a resource that could contain symbolic links
     * @return actual path to the resource
     */
	public static String getActualPath(String path) {
        String actualPath = null;
        File file = new File(path);
        try {
            actualPath = file.getCanonicalPath();
        } catch (Exception ex) {
            log.warn("The file at {} does not seem to exist.", path , ex);
        }
        return actualPath;
    }

    public static void systemPropertiesChanged() {
        synchronized (AppSpecificPropertyNames) {
            AppSpecificPropertyNames.clear();
        }
        _cache.clear();
        // MS: Leave for future WO support ...
        NSNotificationCenter.defaultCenter().postNotification("PropertiesDidChange", null, null);
    }

    /** caches the application name that is appended to the key for lookup */
    protected String applicationNameForAppending;

    /**
     * Caches the application name for appending to the key.
     * Note that for a period when the application is starting up
     * application() will be null and name() will be null.
     * <p>
     * Note: this is redundant with the scheme checked in on March 21, 2005 by clloyd (ben holt did checkin).
     * This scheme requires the user to swizzle the existing properties file with a new one of this type.
     * 
     * @return application name used for appending, for example ".ERMailer"
     */
	protected String applicationNameForAppending() {
        if (applicationNameForAppending == null) {
            applicationNameForAppending = WOApplication.application() != null ? WOApplication.application().name() : null;
            if (applicationNameForAppending != null) {
                applicationNameForAppending = "." + applicationNameForAppending;
            }
        }
        return applicationNameForAppending;
    }

    /**
     * Overriding the default getProperty method to first check:
     * key.&lt;ApplicationName&gt; before checking for key. If nothing
     * is found then key.Default is checked.
     * 
     * @param key to check
     * @return property value
     */
	@Override
    public String getProperty(String key) {
        String property = null;
        String application = applicationNameForAppending();
        if (application != null) {
            property = super.getProperty(key + application);
        }
        if (property == null) {
            property = super.getProperty(key);
            if (property == null) {
                property = super.getProperty(key + DefaultString);
            }
            // We go ahead and set the value to increase the lookup the next time the
            // property is accessed.
            if (property != null && application != null) {
                setProperty(key + application, property);
            }
        }
        return property;
    }

    /**
     * Returns the properties as a String in Property file format. Useful when you use them 
     * as custom value types, you would set this as the conversion method name.
     * 
     * @return Returns the properties as a String in Property file format
     * @throws IOException
     */
    // TODO The result isn't a Object it is a String
	public Object toExternalForm() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        store(os, null);
        return new String(os.toByteArray());
    }
    
    /**
     * Load the properties from a String in Property file format. Useful when you use them 
     * as custom value types, you would set this as the factory method name.
     */
	public static ERXProperties fromExternalForm(String string) {
        ERXProperties result = new ERXProperties();
        try {
			result.load(new ByteArrayInputStream(string.getBytes()));
		}
		catch (IOException e) {
			// AK: shouldn't ever happen...
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
        return result;
    }

	@Override
    public void takeValueForKey(Object anObject, String aKey) {
         setProperty(aKey, (anObject != null ? anObject.toString() : null));
    }

    @Override
    public Object valueForKey(String aKey) {
         return getProperty(aKey);
    }

	/**
	 * Stores the mapping between operator keys and operators
	 */
	private static final NSMutableDictionary<String, ERXProperties.Operator> operators = new NSMutableDictionary<>();

	/**
	 * Registers a property operator for a particular key.
	 * 
     * @param operator the operator to register
     * @param key the key name of the operator
	 */
	public static void setOperatorForKey(ERXProperties.Operator operator, String key) {
		ERXProperties.operators.setObjectForKey(operator, key);
	}

	/**
	 * Property operators work like array operators. In your properties, you can
	 * define keys like:
	 * <pre><code>er.extensions.akey.@someOperatorKey.aparameter=somevalue</code></pre>
	 * Which will be processed by the someOperatorKey operator. Because
	 * properties get handled very early in the startup process, you should
	 * register operators somewhere like a static block in your Application
	 * class. For instance, if you wanted to register the forInstance operator,
	 * you might put the following your Application class:
	 * <pre><code>
	 * static {
	 *   ERXProperties.setOperatorForKey(new ERXProperties.InRangeOperator(100), ERXProperties.InRangeOperator.ForInstanceKey);
	 * }
	 * </code></pre>
	 * It's important to note that property operators evaluate at load time, not
	 * access time, so the compute function should not depend on any runtime
	 * state to execute. Additionally, access to other properties inside the
	 * compute method should be very carefully considered because it's possible
	 * that the operators are evaluated before all of the properties in the
	 * system are loaded.
	 */
	public static interface Operator {

		/**
		 * Performs some computation on the key, value, and parameters and
		 * returns a dictionary of new properties. If this method returns null,
		 * the original key and value will be used. If any other dictionary is
		 * returned, the properties in the dictionary will be copied into the
		 * destination properties.
		 * 
		 * @param key the key ("er.extensions.akey" in "er.extensions.akey.@someOperatorKey.aparameter=somevalue")
		 * @param value ("somevalue" in "er.extensions.akey.@someOperatorKey.aparameter=somevalue")
		 * @param parameters ("aparameter" in "er.extensions.akey.@someOperatorKey.aparameter=somevalue")
		 * @return a dictionary of properties (or null to use the original key and value)
		 */
		public NSDictionary<String, String> compute(String key, String value, String parameters);
	}

	/**
	 * InRangeOperator provides support for defining properties that only
	 * get set if a value falls within a specific range of values.
	 * <p>
	 * An example of this is instance-number-based properties, where you want to 
	 * only set a specific value if the instance number of the application falls
	 * within a certain value. In this example, because instance number is 
	 * something that is associated with a request rather than the application 
	 * itself, it is up to the class registering this operator to specify which 
	 * instance number this application is (via, for instance, a custom system property).
	 * <p>
	 * InRangeOperator supports specifying keys like:
	 * <pre><code>er.extensions.akey.@forInstance.50=avalue</code></pre>
	 * which would set the value of "er.extensions.akey" to "avalue" if this
	 * instance is 50.
	 * <pre><code>er.extensions.akey.@forInstance.60,70=avalue</code></pre>
	 * which would set the value of "er.extensions.akey" to "avalue" if this
	 * instance is 60 or 70.
	 * <pre><code>er.extensions.akey.@forInstance.100-300=avalue</code></pre>
	 * which would set the value of "er.extensions.akey" to "avalue" if this
	 * instance is between 100 and 300 (inclusive).
	 * <pre><code>er.extensions.akey.@forInstance.20-30,500=avalue</code></pre>
	 * which would set the value of "er.extensions.akey" to "avalue" if this
	 * instance is between 20 and 30 (inclusive), or if the instance is 50.
	 * <p>
	 * If there are multiple inRange operators that match for the same key,
	 * the last property (when sorted alphabetically by key name) will win. As a
	 * result, it's important to not define overlapping ranges, or you
	 * may get unexpected results.
	 */
	public static class InRangeOperator implements ERXProperties.Operator {

		/**
		 * The default key name of the ForInstance variant of the InRange operator.
		 */
		public static final String ForInstanceKey = "forInstance";

		private int _instanceNumber;

		/**
		 * Constructs a new InRangeOperator.
		 * 
	     * @param value the instance number of this application
		 */
		public InRangeOperator(int value) {
			_instanceNumber = value;
		}

		public NSDictionary<String, String> compute(String key, String value, String parameters) {
			NSDictionary computedProperties = null;
			if (parameters != null && parameters.length() > 0) {
				if (isValueInRange(_instanceNumber, parameters)) {
					computedProperties = new NSDictionary(value, key);
				}
				else {
					computedProperties = NSDictionary.EmptyDictionary;
				}
			}
			return computedProperties;
		}
		
		/**
		 * Returns whether the given value falls in a range defined by the given
		 * string, which is in the format "1-5,100,500,800-1000".
		 * 
		 * @param value the value to check for
		 * @param rangeString the range string to parse
		 * @return whether or not the value falls within the given ranges
		 */
		private static boolean isValueInRange(int value, String rangeString) {
			boolean rangeMatches = false;
			if (rangeString != null && rangeString.length() > 0) {
				String[] ranges = rangeString.split(",");
				for (String range : ranges) {
					range = range.trim();
					int dashIndex = range.indexOf('-');
					if (dashIndex == -1) {
						int singleValue = Integer.parseInt(range);
						if (value == singleValue) {
							rangeMatches = true;
							break;
						}
					}
					else {
						int lowValue = Integer.parseInt(range.substring(0, dashIndex).trim());
						int highValue = Integer.parseInt(range.substring(dashIndex + 1).trim());
						if (value >= lowValue && value <= highValue) {
							rangeMatches = true;
							break;
						}
					}
				}
			}
			return rangeMatches;
		}
	}

	/**
	 * For each property in originalProperties, process the keys and values with
	 * the registered property operators and stores the converted value into
	 * destinationProperties.
	 * 
	 * @param originalProperties the properties to convert
	 * @param destinationProperties the properties to copy into
	 */
	public static void evaluatePropertyOperators(Properties originalProperties, Properties destinationProperties) {
		NSArray<String> operatorKeys = ERXProperties.operators.allKeys();
		for (Object keyObj : new TreeSet<>(originalProperties.keySet())) {
			String key = (String) keyObj;
			if (key != null && key.length() > 0) {
				String value = originalProperties.getProperty(key);
				if (operatorKeys.count() > 0 && key.indexOf(".@") != -1) {
					ERXProperties.Operator operator = null;
					NSDictionary<String, String> computedProperties = null;
					for (String operatorKey : operatorKeys) {
						String operatorKeyWithAt = ".@" + operatorKey;
						if (key.endsWith(operatorKeyWithAt)) {
							operator = ERXProperties.operators.objectForKey(operatorKey);
							computedProperties = operator.compute(key.substring(0, key.length() - operatorKeyWithAt.length()), value, null);
							break;
						}
						int keyIndex = key.indexOf(operatorKeyWithAt + ".");
						if (keyIndex != -1) {
							operator = ERXProperties.operators.objectForKey(operatorKey);
							computedProperties = operator.compute(key.substring(0, keyIndex), value, key.substring(keyIndex + operatorKeyWithAt.length() + 1));
							break;
						}
					}

					if (computedProperties == null) {
						destinationProperties.put(key, value);
					}
					else {
						originalProperties.remove(key);
						
						// If the key exists in the System properties' defaults with a different value, we must reinsert
						// the property so it doesn't get overwritten with the default value when we evaluate again.
						// This happens because ERXConfigurationManager processes the properties after a configuration
						// change in multiple passes and each calls this method.
						if (System.getProperty(key) != null && !System.getProperty(key).equals(value)) {
							originalProperties.put(key, value);
						}
						
						for (String computedKey : computedProperties.allKeys()) {
							destinationProperties.put(computedKey, computedProperties.objectForKey(computedKey));
						}
					}
				}
				else {
					destinationProperties.put(key, value);
				}
			}
		}
	}

	/**
	 * For every application specific property, this method generates a similar property without
	 * the application name in the property key. The original property is not removed.
	 * <p>
	 * Ex: if current application is MyApp, for a property foo.bar.MyApp=true a new property
	 * foo.bar=true is generated.
	 * 
	 * @param properties Properties to update
	 */
// xxxxxxxxxxxxxxxxxxx
// This is more complex than it needs to be. Can just use endsWith....
//
	public static void flattenPropertyNames(Properties properties) {
	    
	    WOApplication application = WOApplication.application();
	    if (application == null) {
	        return;
	    }
	    String applicationName = application.name();
	    for (Object keyObj : new TreeSet<>(properties.keySet())) {
	        String key = (String) keyObj;
	        if (key != null && key.length() > 0) {
	            String value = properties.getProperty(key);
	            int lastDotPosition = key.lastIndexOf(".");
	            if (lastDotPosition != -1) {
	                String lastElement = key.substring(lastDotPosition + 1);
	                if (lastElement.equals(applicationName)) {
	                    properties.put(key.substring(0, lastDotPosition), value);
	                }
	            }
	        }
	    }
	}

	/**
	 * _Properties is a subclass of Properties that provides support for including other
	 * Properties files on the fly.  If you create a property named .includeProps, the value
	 * will be interpreted as a file to load.  If the path is absolute, it will just load it
	 * directly.  If it's relative, the path will be loaded relative to the current user's
	 * home directory.  Multiple .includeProps can be included in a Properties file and they
	 * will be loaded in the order they appear within the file.
	 */
	public static class _Properties extends Properties {

		private static final Logger log = LoggerFactory.getLogger(ERXProperties.class);

		/**
		 * Do I need to update serialVersionUID?
		 * See section 5.6 <cite>Type Changes Affecting Serialization</cite> on page 51 of the 
		 * <a href="http://java.sun.com/j2se/1.4/pdf/serial-spec.pdf">Java Object Serialization Spec</a>
		 */
		private static final long serialVersionUID = 1L;

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
    
	/**
	 * Determines the path of the specified Resource. This is done to get a
	 * single entry point due to the deprecation of pathForResourceNamed
	 * 
	 * @param fileName name of the file
	 * @param frameworkName name of the framework, <code>null</code> or "app" for the application bundle
	 * @param languages array of languages to get localized resource or <code>null</code>
	 * @return the absolutePath method off of the file object
	 */
	private static String pathForResourceNamed(String fileName, String frameworkName, NSArray<String> languages) {
		String path = null;
		NSBundle bundle = "app".equals(frameworkName) ? NSBundle.mainBundle() : NSBundle.bundleForName(frameworkName);
		if (bundle != null && bundle.isJar()) {
			// FIXME: Changed log level to debug
			// This was emitting at every application startup, seemingly without purpose.
			// Since property loading seems to work fine anyway, I turned it to debug
			// and we're going to have to have a look at property loading in general later.
			log.debug("Can't get path when run as jar: {} - {}", frameworkName, fileName);
		}
		else {
			WOApplication application = WOApplication.application();
			if (application != null) {
				URL url = application.resourceManager().pathURLForResourceNamed(fileName, frameworkName, languages);
				if (url != null) {
					path = url.getFile();
				}
			}
			else if (bundle != null) {
				URL url = bundle.pathURLForResourcePath(fileName);
				if (url != null) {
					path = url.getFile();
				}
			}
		}
		return path;
	}
}
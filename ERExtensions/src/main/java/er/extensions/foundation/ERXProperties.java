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
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
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
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSProperties;

import er.extensions.appserver.ERXApplication;

public class ERXProperties {

	/**
	 * Do I need to update serialVersionUID?
	 * See section 5.6 <cite>Type Changes Affecting Serialization</cite> on page 51 of the 
	 * <a href="http://java.sun.com/j2se/1.4/pdf/serial-spec.pdf">Java Object Serialization Spec</a>
	 */
	private static final long serialVersionUID = 1L;

    private static String UndefinedMarker = "-undefined-";

    private static final Logger log = LoggerFactory.getLogger(ERXProperties.class);
    private static final Logger configLog = LoggerFactory.getLogger(ERXConfigurationManager.class);

    private static final Map<String, String> AppSpecificPropertyNames = new HashMap<>(128);

    /**
     * FIXME: This constructor was onl yadded as an experiment, to ensure that no instances are created, so I can eliminate the KVC implementation // Hugi 2022-01-20
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
			value = ERXValueUtilities.arrayValueWithDefault(NSProperties.getProperty(propertyName), null);
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
	public static int intForKeyWithDefault(final String s, final int defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);
		int value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Integer) {
			value = ((Integer) cachedValue).intValue();
		} else {
			Integer objValue = ERXValueUtilities.IntegerValueWithDefault(NSProperties.getProperty(propertyName), null);
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
	public static long longForKeyWithDefault(final String s, final long defaultValue) {
        final String propertyName = getApplicationSpecificPropertyName(s);
		long value;
		Object cachedValue = _cache.get(propertyName);
		if (UndefinedMarker.equals(cachedValue)) {
			value = defaultValue;
		} else if (cachedValue instanceof Long) {
			value = ((Long) cachedValue).longValue();
		} else {
			Long objValue = ERXValueUtilities.LongValueWithDefault(NSProperties.getProperty(propertyName), null);
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
	private static Properties propertiesFromFile(File file) throws java.io.IOException {
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
        	String userPropertiesPath = ERXProperties.variantPropertiesInBundle(NSProperties.getProperty("user.name"), frameworkName);
        	addIfPresent(frameworkName + ".framework.user", userPropertiesPath, propertiesPaths, projectsInfo);
        }

		NSBundle mainBundle = NSBundle.mainBundle();
		
		if( mainBundle != null ) {
	        String mainBundleName = mainBundle.name();
	
	        String appPath = pathForResourceNamed("Properties", "app", null);
	    	addIfPresent(mainBundleName + ".app", appPath, propertiesPaths, projectsInfo);
		}

		/*  WebObjects.properties in the user home directory */
		String userHome = NSProperties.getProperty("user.home");
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

		optionalPropertiesLoader(NSProperties.getProperty("user.name"), propertiesPaths, projectsInfo);
		
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
			message.append(ERXProperties.logString(NSProperties._getProperties()));
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
     * @param properties
     * @param protectValues if <code>true</code>, keys with the word "password" in them will have their values removed 
     * @return all of the properties in the system mapped to their evaluated values, sorted by key
     */
    private static Map<String, String> propertiesMap(Properties properties, boolean protectValues) {
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
    
    /**
     * Returns the application-specific user properties.
     * 
     * @return application-specific user properties
     */
	private static String applicationDeveloperProperties() {
    	String applicationDeveloperPropertiesPath = null;
    	if (ERXApplication.isDevelopmentModeSafe()) {
	        String devName = NSProperties.getProperty("er.extensions.ERXProperties.devPropertiesName", "dev");
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
    private static String variantPropertiesInBundle(String userName, String bundleName) {
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
     * @return The application-specific user properties
     */
	private static String applicationUserProperties() {
    	return variantPropertiesInBundle(NSProperties.getProperty("user.name"), "app");
    }
    
    /**
     * Returns the path to the application-specific system-wide file "fileName".  By default this path is /etc/WebObjects, 
     * and the application name will be appended.  For instance, if you are asking for the MyApp Properties file for the
     * system, it would go in /etc/WebObjects/MyApp/Properties.
     * 
     * @param fileName the Filename
     * @return the path, or null if the path does not exist
     */
	private static String applicationMachinePropertiesPath(String fileName) {
    	String applicationMachinePropertiesPath = null;
    	String machinePropertiesPath = NSProperties.getProperty("er.extensions.ERXProperties.machinePropertiesPath", "/etc/WebObjects");
    	WOApplication application = WOApplication.application();
    	String applicationName;
    	if (application != null) {
    		applicationName = application.name();
    	}
    	else {
    		applicationName = NSProperties.getProperty("WOApplicationName");
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
	private static NSArray optionalConfigurationFiles() {
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
	private static String getActualPath(String path) {
        String actualPath = null;
        File file = new File(path);
        try {
            actualPath = file.getCanonicalPath();
        } catch (Exception ex) {
            log.warn("The file at {} does not seem to exist.", path , ex);
        }
        return actualPath;
    }

    private static void systemPropertiesChanged() {
        synchronized (AppSpecificPropertyNames) {
            AppSpecificPropertyNames.clear();
        }
        _cache.clear();
        // MS: Leave for future WO support ...
        NSNotificationCenter.defaultCenter().postNotification(NSProperties.PropertiesDidChange, null, null);
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
	private static class _Properties extends Properties {

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
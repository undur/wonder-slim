/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSProperties;

import er.extensions.ERXExtensions;
import er.extensions.ERXLoggingSupport;

/**
 * Handles rapid turnaround for system configuration
 * 
 * <h3>Placing configuration parameters</h3>
 * You can provide the system configuration by the following ways:
 * <p>
 * Note: This is the standard way for WebObjects 5.x applications.
 * <ul>
 * <li><code>Properties</code> file under the Resources group of the application
 * and framework project. It's a {@link java.util.Properties} file and Project
 * Wonder's standard project templates include it. (The templates won't be
 * available on some platforms at this moment.)</li>
 * 
 * <li><code>WebObjects.properties</code> under the user home directory; same
 * format to Properties.
 * <p>
 * Note that the user home directory depends on the user who launch the
 * application. They may change between the development and deployment
 * time.</li>
 * 
 * <li>Command line arguments
 * <p>
 * For example: <code>-WOCachingEnabled false -com.webobjects.pid $$</code><br>
 * Don't forget to put a dash "-" before the key.</li>
 * </ul>
 * <h3>Loading order of the configuration parameters</h3> When the application
 * launches, configuration parameters will be loaded by the following order.
 * ERXConfigurationManager tries to reload them by the exactly same order when
 * one of those configuration files changes.
 * <ol>
 * <li>Properties in frameworks that the application links to</li>
 * <li>Properties in the application</li>
 * <li>WebObjects.properties under the home directory</li>
 * <li>Command line arguments</li>
 * </ol>
 * If there is a conflicting parameter between the files and arguments, the
 * latter one overrides the earlier one.
 * <p>
 * Note that the order between frameworks does not seems to be specified. You
 * should not put conflicting parameters between framework Properties files. On
 * the other hand, the application Properties should be always loaded after all
 * framework Properties are loaded. You can safely override parameters on the
 * frameworks from the applications Properties.
 * 
 * @property er.extensions.ERXConfigurationManager.PropertiesTouchFile if this
 *           property is set to a file name, the application will register for
 *           notifications of changes to that file and when that file is
 *           touched, the application will re-load properties.
 */

public class ERXConfigurationManager {

	private static final Logger log = LoggerFactory.getLogger(ERXConfigurationManager.class);

	/**
	 * Notification posted when the configuration is updated. The Java system properties is the part of the configuration.
	 */
	public static final String ConfigurationDidChangeNotification = "ConfigurationDidChangeNotification";

	/**
	 * Configuration manager singleton
	 */
	private static ERXConfigurationManager defaultManager = null;

	private String[] _commandLineArguments;
	private NSArray<String> _monitoredProperties;
	private Properties _defaultProperties;
	private Properties _commandLineArgumentProperties;
	private boolean _isInitialized = false;
	private boolean _isRapidTurnAroundInitialized = false;

	/**
	 * Holds the hostName
	 */
	private String _hostName;

	/**
	 * Private constructor to prevent instantiation from outside the class
	 */
	private ERXConfigurationManager() {}

	/**
	 * If set, touching this path will be used to signal a change to properties files.
	 */
	private static String propertiesTouchFile() {
		return ERXProperties.stringForKey("er.extensions.ERXConfigurationManager.PropertiesTouchFile");
	}

	/**
	 * Returns the single instance of this class
	 * 
	 * @return the configuration manager
	 */
	public static ERXConfigurationManager defaultManager() {
		if (defaultManager == null) {
			defaultManager = new ERXConfigurationManager();
		}

		return defaultManager;
	}

	/**
	 * Returns the command line arguments.
	 * {@link er.extensions.appserver.ERXApplication#main(String[], Class)} sets this value.
	 * 
	 * @return the command line arguments as a String[]
	 * @see #setCommandLineArguments
	 */
	public String[] commandLineArguments() {
		return _commandLineArguments;
	}

	/**
	 * Returns the command line arguments as Properties.
	 * {@link er.extensions.appserver.ERXApplication#main(String[], Class)} sets this value.
	 * 
	 * @return the command line arguments as a String[]
	 * @see #setCommandLineArguments(String[])
	 */
	public Properties commandLineArgumentProperties() {
		return (Properties) _commandLineArgumentProperties.clone();
	}

	/**
	 * Returns the command line arguments as Properties.
	 * {@link er.extensions.appserver.ERXApplication#main(String[], Class)} sets this value.
	 * 
	 * @return the command line arguments as a String[]
	 * @see #setCommandLineArguments(String[])
	 */
	public Properties defaultProperties() {
		return (Properties) _defaultProperties.clone();
	}

	/**
	 * Sets the command line arguments.
	 * {@link er.extensions.appserver.ERXApplication#main(String[], Class)} will
	 * call this method when the application starts up.
	 * 
	 * @see #commandLineArguments()
	 */
	public void setCommandLineArguments(String[] newCommandLineArguments) {
		_commandLineArguments = newCommandLineArguments;
		_defaultProperties = (Properties) NSProperties._getProperties().clone();
		_commandLineArgumentProperties = ERXProperties.propertiesFromArgv(_commandLineArguments);
	}

	/**
	 * Initializes the configuration manager. The framework principal
	 * {@link ERXExtensions} calls this method when the ERExtensions framework is loaded.
	 */
	public void initialize() {
		if (!_isInitialized) {
			_isInitialized = true;
			loadConfiguration();
		}
	}

	private NSArray<String> monitoredProperties() {
		if (_monitoredProperties == null) {
			_monitoredProperties = ERXProperties.pathsForUserAndBundleProperties();
		}

		return _monitoredProperties;
	}

	/**
	 * Sets up the system for rapid turnaround mode. It will watch the changes
	 * on Properties files in application and framework bundles and
	 * WebObjects.properties under the home directory. Rapid turnaround mode
	 * will only be enabled if there are such files available and system has
	 * WOCaching disabled.
	 */
	public void configureRapidTurnAround() {

		if (_isRapidTurnAroundInitialized) {
			return;
		}

		_isRapidTurnAroundInitialized = true;

		if (WOApplication.application() != null && WOApplication.application().isCachingEnabled()) {
			// In the original Wonder, this logged as "info". For some reason, I feel compelled to mention this before changing it
			log.debug("WOCachingEnabled is true. Disabling the rapid turnaround for Properties files");
			registerPropertiesTouchFiles();
			return;
		}

		for (String path : monitoredProperties()) {
			registerForFileNotification(path, "updateSystemProperties");
		}
	}

	private void registerPropertiesTouchFiles() {
		String propertiesTouchFile = propertiesTouchFile();

		if (propertiesTouchFile != null) {
			String appNamePlaceHolder = "/{AppName}/";
			int appNamePlaceHolderIndex = propertiesTouchFile.lastIndexOf(appNamePlaceHolder);
			if (appNamePlaceHolderIndex == -1) {
				registerForFileNotification(propertiesTouchFile, "updateAllSystemProperties");
			}
			else {
				if (WOApplication.application() != null) {
					StringBuilder appSpecificTouchFile = new StringBuilder();

					appSpecificTouchFile.append(propertiesTouchFile.substring(0, appNamePlaceHolderIndex + 1));
					appSpecificTouchFile.append(WOApplication.application().name());
					appSpecificTouchFile.append(propertiesTouchFile.substring(appNamePlaceHolderIndex + appNamePlaceHolder.length() - 1));

					registerForFileNotification(appSpecificTouchFile.toString(), "updateAllSystemProperties");
				}

				StringBuilder globalTouchFile = new StringBuilder();

				globalTouchFile.append(propertiesTouchFile.substring(0, appNamePlaceHolderIndex + 1));
				globalTouchFile.append(propertiesTouchFile.substring(appNamePlaceHolderIndex + appNamePlaceHolder.length()));

				registerForFileNotification(globalTouchFile.toString(), "updateAllSystemProperties");
			}
		}
	}

	private void registerForFileNotification(String path, String callbackMethod) {
		try {
			ERXFileNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector(callbackMethod), path);
			log.debug("Registered: {}", path);
		}
		catch (Exception ex) {
			log.error("An exception occured while registering the observer for the " + "logging configuration file: {} {}", ex.getClass().getName(), ex.getMessage(), ex);
		}
	}

	/**
	 * This will overlay the current system config files. It will then re-load the command line args.
	 */
	public void loadConfiguration() {
		Properties systemProperties = System.getProperties();
		systemProperties = applyConfiguration(systemProperties);

		ERXProperties.transferPropertiesFromSourceToDest(systemProperties, System.getProperties());

		ERXLoggingSupport.configureLoggingWithSystemProperties();
	}

	/**
	 * This will overlay the current system config files. It will then re-load the command line args.
	 */
	public Properties applyConfiguration(Properties systemProperties) {
		return ERXProperties.applyConfiguration(systemProperties, commandLineArgumentProperties());
	}

	/**
	 * Updates the configuration from the current configuration and posts
	 * {@link #ConfigurationDidChangeNotification}. It also calls
	 * configureLoggingWithSystemProperties()
	 * to reconfigure the logging system.
	 * <p>
	 * The configuration files: Properties and WebObjects.properties files are
	 * reloaded to the Java system properties by the same order to the when the
	 * system starts up. Then the command line arguments will be applied to the
	 * properties again so that the configuration will be consistent during the
	 * application lifespan.
	 * <p>
	 * This method is called when rapid turnaround is enabled and one of the
	 * configuration files changes.
	 * 
	 * @param n NSNotification object for the event (null means load all files)
	 */
	public synchronized void updateSystemProperties(NSNotification n) {
		loadConfiguration();
	}

	public synchronized void updateAllSystemProperties(NSNotification notification) {
		loadConfiguration();
	}

	/**
	 * Gets the default host name for the current local host.
	 * 
	 * @return host name or UnknownHost if the host is unknown.
	 */
	public String hostName() {
		if (_hostName == null) {
			try {
				_hostName = java.net.InetAddress.getLocalHost().getHostName();
			}
			catch (java.net.UnknownHostException ehe) {
				log.warn("Caught unknown host exception.", ehe);
				_hostName = "UnknownHost";
			}
		}
		return _hostName;
	}
}
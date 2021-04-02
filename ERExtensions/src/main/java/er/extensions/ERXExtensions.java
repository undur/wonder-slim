/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSSelector;
import com.webobjects.foundation._NSStringUtilities;

import er.extensions.appserver.ERXApplication;
import er.extensions.foundation.ERXArrayUtilities;
import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXMutableURL;
import er.extensions.foundation.ERXPatcher;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXRuntimeUtilities;
import er.extensions.foundation.ERXStringUtilities;
import er.extensions.foundation.ERXSystem;
import er.extensions.foundation.ERXValueUtilities;
import er.extensions.localization.ERXLocalizer;
import er.extensions.logging.ERXLogger;
import er.extensions.validation.ERXValidationFactory;
import x.FIXMEException;

/**
 * Principal class of the ERExtensions framework. This class will be loaded at
 * runtime when the ERExtensions bundle is loaded (even before the Application
 * constructor is called) This class has a boat-load of stuff in it that will
 * hopefully be finding better homes in the future. This class serves as the
 * initialization point of this framework, look in the static initializer to see
 * all the stuff that is initially setup when this class is loaded. This class
 * also has a boat load of string, array and EOF utilities as well as the
 * factory methods for creating editing contexts with the default delegates set.
 */
public class ERXExtensions extends ERXFrameworkPrincipal {

	private static boolean _initialized;

	/**
	 * Configures the framework. All the bits and pieces that need to be
	 * configured are configured, those that need to happen later are delayed by
	 * registering an observer for notifications that are posted when the
	 * application is finished launching. This public observer is used to
	 * perform basic functions in response to notifications. Specifically it
	 * handles configuring the adaptor context so that SQL debugging can be
	 * enabled and disabled on the fly through the log4j system. Handling
	 * cleanup issues when sessions timeout, i.e. releasing all references to
	 * editing contexts created for that session. Handling call all of the
	 * <code>did*</code> methods on {@link ERXGenericRecord} subclasses after an
	 * editing context has been saved. This delegate is also responsible for
	 * configuring {@link ERXValidationFactory}. This delegate is configured
	 * when this framework is loaded.
	 */
	@Override
	protected void initialize() {
		NSNotificationCenter.defaultCenter().addObserver(this,
				new NSSelector("bundleDidLoad", ERXConstant.NotificationClassArray),
				ERXApplication.AllBundlesLoadedNotification,
				null);
	}

	public void bundleDidLoad(NSNotification n) {

		if (_initialized) {
			return;
		}

		_initialized = true;

		try {
			// This will load any optional configuration files,
			// ensures that WOOutputPath's was processed with this @@
			// variable substitution. WOApplication uses WOOutputPath in
			// its constructor so we need to modify it before calling
			// the constructor.
			ERXConfigurationManager.defaultManager().initialize();
			ERXSystem.updateProperties();

			// AK: enable this when we're ready
			// WOEncodingDetector.sharedInstance().setFallbackEncoding(CharEncoding.UTF_8);

			// GN: configure logging with optional custom subclass of ERXLogger
			String className = ERXProperties.stringForKey("er.extensions.erxloggerclass");
			if (className != null) {
				Class loggerClass = Class.forName(className);
				Method method = loggerClass.getDeclaredMethod(ERXLogger.CONFIGURE_LOGGING_WITH_SYSTEM_PROPERTIES, (Class[]) null);
				method.invoke(loggerClass, (Object[]) null);
			}
			else {
				// default behaviour:
				ERXLogger.configureLoggingWithSystemProperties();
			}
		}
		catch (Exception e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}

	/**
	 * This method is called when the application has finished launching. Here
	 * is where log4j is configured for rapid turn around and the validation
	 * template system is configured.
	 */
	@Override
	public void finishInitialization() {
		// AK: we now setup the properties three times. At startup, in ERX.init
		// and here. Note that this sucks beyond belief, as this will produce
		// unforeseen results in several cases, but it's the only way to set up
		// all parts of the property handling. The first install only loads
		// plain
		// and user props the second has no good way to set up the main bundle
		// and this one
		// comes too late for static inits
		ERXConfigurationManager.defaultManager().loadConfiguration();

		ERXProperties.populateSystemProperties();

		ERXConfigurationManager.defaultManager().configureRapidTurnAround();
		ERXLocalizer.initialize();
		ERXValidationFactory.defaultFactory().configureFactory();
		// update configuration with system properties that might depend
		// on others like
		// log4j.appender.SQL.File=@@loggingBasePath@@/@@port@@.sql
		// loggingBasePath=/var/log/@@name@@
		// name and port are resolved via WOApplication.application()
		// ERXLogger.configureLoggingWithSystemProperties();

		ERXProperties.pathsForUserAndBundleProperties(true);
	}

	/**
	 * Initializes your WOApplication programmatically (for use in test cases
	 * and main methods) with the assumption that the current directory is your
	 * main bundle URL.
	 * 
	 * @param applicationSubclass
	 *            your Application subclass
	 * @param args
	 *            the commandline arguments for your application
	 */
	public static void initApp(Class applicationSubclass, String[] args) {
		try {
			File woaFolder = new File(".").getCanonicalFile();
			if (!woaFolder.getName().endsWith(".woa")) {
				if (new File(woaFolder, ".project").exists()) {
					File buildFolder = new File(new File(woaFolder, "build"), woaFolder.getName() + ".woa");
					if (buildFolder.exists()) {
						woaFolder = buildFolder;
					}
					else {
						File distFolder = new File(new File(woaFolder, "dist"), woaFolder.getName() + ".woa");
						if (distFolder.exists()) {
							woaFolder = distFolder;
						}
						else {
							// Bundle-less builds. Yay!
							// throw new IllegalArgumentException("You must run
							// your application from a .woa folder to call this
							// method.");
						}
					}
				}
			}
			ERXExtensions.initApp(null, woaFolder.toURI().toURL(), applicationSubclass, args);
		}
		catch (IOException e) {
			throw new NSForwardException(e);
		}
	}

	/**
	 * Initializes your WOApplication programmatically (for use in test cases
	 * and main methods).
	 * 
	 * @param mainBundleName
	 *            the name of your main bundle
	 * @param applicationSubclass
	 *            your Application subclass
	 * @param args
	 *            the commandline arguments for your application
	 */
	public static void initApp(String mainBundleName, Class applicationSubclass, String[] args) {
		ERXExtensions.initApp(mainBundleName, null, applicationSubclass, args);
	}

	private static boolean _appInitialized = false;

	/**
	 * Initializes your WOApplication programmatically (for use in test cases
	 * and main methods).
	 * 
	 * @param mainBundleName
	 *            the name of your main bundle (or null to use mainBundleURL)
	 * @param mainBundleURL
	 *            the URL to your main bundle (ignored if mainBundleName is set)
	 * @param applicationSubclass
	 *            your Application subclass
	 * @param args
	 *            the commandline arguments for your application
	 */
	public static void initApp(String mainBundleName, URL mainBundleURL, Class applicationSubclass, String[] args) {
		if (_appInitialized) {
			return;
		}
		try {
			ERXApplication.setup(args);
			if (mainBundleURL != null) {
				System.setProperty("webobjects.user.dir", new File(mainBundleURL.getFile()).getCanonicalPath());
			}
			// Odds are you are only using this method for test cases and
			// development mode
			System.setProperty("er.extensions.ERXApplication.developmentMode", "true");
			ERXApplication.primeApplication(mainBundleName, mainBundleURL, applicationSubclass.getName());
			// NSNotificationCenter.defaultCenter().postNotification(new
			// NSNotification(ERXApplication.ApplicationDidCreateNotification,
			// WOApplication.application()));
		}
		catch (IOException e) {
			throw new NSForwardException(e);
		}
		_appInitialized = true;
	}
}
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
import com.webobjects.eoaccess.EOAttribute;
import com.webobjects.eoaccess.EODatabase;
import com.webobjects.eoaccess.EODatabaseContext;
import com.webobjects.eoaccess.EOEntity;
import com.webobjects.eoaccess.EOModelGroup;
import com.webobjects.eoaccess.EOQualifierSQLGeneration;
import com.webobjects.eoaccess.EOQualifierSQLGeneration.Support;
import com.webobjects.eoaccess.EORelationship;
import com.webobjects.eoaccess.EOSQLExpression;
import com.webobjects.eoaccess.EOUtilities;
import com.webobjects.eocontrol.EOEnterpriseObject;
import com.webobjects.eocontrol.EOFetchSpecification;
import com.webobjects.eocontrol.EOKeyValueQualifier;
import com.webobjects.eocontrol.EOQualifier;
import com.webobjects.eocontrol.EOQualifierVariable;
import com.webobjects.eocontrol.EOSharedEditingContext;
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
import er.extensions.eof.ERXConstant;
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

/**
 * Principal class of the ERExtensions framework. This class
 * will be loaded at runtime when the ERExtensions bundle is
 * loaded (even before the Application constructor is called)
 * This class has a boat-load of stuff in it that will hopefully
 * be finding better homes in the future. This class serves as
 * the initialization point of this framework, look in the static
 * initializer to see all the stuff that is initially setup when
 * this class is loaded. This class also has a boat load of
 * string, array and EOF utilities as well as the factory methods
 * for creating editing contexts with the default delegates set.
 */
public class ERXExtensions extends ERXFrameworkPrincipal {
    
    /** Notification name, posted before object will change in an editing context */
    public final static String objectsWillChangeInEditingContext= "ObjectsWillChangeInEditingContext";

    /** Notification name, posted before EOAdaptor debug logging will change its setting. */
    public final static String eoAdaptorLoggingWillChangeNotification = "EOAdaptorLoggingWillChange";
    
    /** logging support */
    private static Logger _log;
    
    private static boolean _initialized;

    public ERXExtensions() {
    }
   
   /**
     * Configures the framework. All the bits and pieces that need
     * to be configured are configured, those that need to happen
     * later are delayed by registering an observer for notifications
     * that are posted when the application is finished launching.
     * This public observer is used to perform basic functions in
     * response to notifications. Specifically it handles
     * configuring the adaptor context so that SQL debugging can
     * be enabled and disabled on the fly through the log4j system.
     * Handling cleanup issues when sessions timeout, i.e. releasing
     * all references to editing contexts created for that session.
     * Handling call all of the <code>did*</code> methods on
     * {@link ERXGenericRecord} subclasses after an editing context
     * has been saved. This delegate is also responsible for configuring
     * {@link ERXValidationFactory}.
     * This delegate is configured when this framework is loaded.
     */
    @Override
    protected void initialize() {
    	NSNotificationCenter.defaultCenter().addObserver(this,
    			new NSSelector("bundleDidLoad", ERXConstant.NotificationClassArray),
    			ERXApplication.AllBundlesLoadedNotification,
    			null);
    }

    public void bundleDidLoad(NSNotification n) {
    	if(_initialized) return;
    	_initialized = true;
    	
    	try {
    		// This will load any optional configuration files, 
    		// ensures that WOOutputPath's was processed with this @@
    		// variable substitution. WOApplication uses WOOutputPath in
    		// its constructor so we need to modify it before calling
    		// the constructor.
    		ERXConfigurationManager.defaultManager().initialize();
        	EOModelGroup.setClassDelegate(this);
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
        	
            ERXArrayUtilities.initialize();
            
			ERXExtensions.configureAdaptorContextRapidTurnAround(this);
    		
    	} catch (Exception e) {
    		throw NSForwardException._runtimeExceptionForThrowable(e);
    	}
    }

    /**
     * This method is called when the application has finished
     * launching. Here is where log4j is configured for rapid
     * turn around and the validation template system is configured.
     */
    @Override
    public void finishInitialization() {
        // AK: we now setup the properties three times. At startup, in ERX.init
		// and here. Note that this sucks beyond belief, as this will produce
		// unforeseen results in several cases, but it's the only way to set up
		// all parts of the property handling. The first install only loads plain
		// and user props the second has no good way to set up the main bundle and this one
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
        
        _log = Logger.getLogger(ERXExtensions.class);
		ERXProperties.pathsForUserAndBundleProperties(true);
    }

    @Override
    public void didFinishInitialization() {
        super.didFinishInitialization();
    }

    /**
     * This method is called every time the configuration file
     * is changed. This allows for turning SQL debugging on and
     * off at runtime.
     * @param n notification posted when the configuration file
     * 	changes.
     */
    public void configureAdaptorContext(NSNotification n) {
        ERXExtensions.configureAdaptorContext();
    }

    /** logging support for the adaptor channel */
    public static Logger adaptorLogger;

    /** logging support for shared object loading */
    public static Logger sharedEOadaptorLogger;

    /** flag to indicate if adaptor channel logging is enabled */
    private static Boolean adaptorEnabled;

    /** 
     * flag to indicate if rapid turn around is enabled for the
     * adaptor channel logging. 
     */
    private static boolean _isConfigureAdaptorContextRapidTurnAround = false;

    /**
     * Configures the passed in observer to register a call back 
     * when the configuration file is changed. This allows one to 
     * change a logger's setting and have that changed value change
     * the NSLog setting to log the generated SQL. This method is
     * called as part of the framework initialization process.
     * @param anObserver object to register the call back with.
     */
    // FIXME: This shouldn't be enabled when the application is in production.
    // FIXME: Now that all of the logging has been centralized, we should just be able
    //		to do something like this, but much more generic, i.e. have a mapping
    //		between logger names and NSLog groups, for example
    //		com.webobjects.logging.DebugGroupSQLGeneration we should
    //		be able to get the last part of the logger name and look up that log group and turn 
    public static void configureAdaptorContextRapidTurnAround(Object anObserver) {
        if (!_isConfigureAdaptorContextRapidTurnAround) {
            // This allows enabling from the log4j system.
            adaptorLogger = Logger.getLogger("er.transaction.adaptor.EOAdaptorDebugEnabled");
            
            sharedEOadaptorLogger = Logger.getLogger("er.transaction.adaptor.EOSharedEOAdaptorDebugEnabled");
            if ((adaptorLogger.isDebugEnabled() 
            		&& !NSLog.debugLoggingAllowedForGroups(NSLog.DebugGroupSQLGeneration|NSLog.DebugGroupDatabaseAccess))
            		|| ERXProperties.booleanForKey("EOAdaptorDebugEnabled")) {
                NSLog.allowDebugLoggingForGroups(NSLog.DebugGroupSQLGeneration|NSLog.DebugGroupDatabaseAccess);
                NSLog.setAllowedDebugLevel(NSLog.DebugLevelInformational);
            }
            adaptorEnabled = NSLog.debugLoggingAllowedForGroups(NSLog.DebugGroupSQLGeneration|NSLog.DebugGroupDatabaseAccess) ? Boolean.TRUE : Boolean.FALSE;
                                          // Allows rapid turn-around of adaptor debugging.
            NSNotificationCenter.defaultCenter().addObserver(anObserver,
                                                             new NSSelector("configureAdaptorContext", ERXConstant.NotificationClassArray),
                                                             ERXConfigurationManager.ConfigurationDidChangeNotification,
                                                             null);
            _isConfigureAdaptorContextRapidTurnAround = true;
        }
    }

    /**
     * This method is called by the delegate when the configuration
     * file is changed. It's sole purpose is to map a logging logger
     * to a debug group. Hopefully in the future we will have a more
     * generic solution.
     */
    public static void configureAdaptorContext() {
        Boolean targetState = null;
        if (adaptorLogger != null) {
	        if (adaptorLogger.isDebugEnabled() && !adaptorEnabled.booleanValue()) {
	            targetState = Boolean.TRUE;
	        } else if (!adaptorLogger.isDebugEnabled() && adaptorEnabled.booleanValue()) {
	            targetState = Boolean.FALSE;
	        }
	        if (targetState != null) {
	        	setAdaptorLogging(targetState.booleanValue());
	        }
        }
    }

    /**
     * Returns the current state of EOAdaptor logging.
     */
	public static boolean adaptorLogging() {
		return NSLog.debugLoggingAllowedForGroups(NSLog.DebugGroupSQLGeneration|NSLog.DebugGroupDatabaseAccess);
	}

    /**
     * Turn EOAdaptor logging on and off.
     * @param onOff
     */
    public static void setAdaptorLogging(boolean onOff) {
    	Boolean targetState = Boolean.valueOf(onOff);
    	if (NSLog.debugLoggingAllowedForGroups(NSLog.DebugGroupSQLGeneration|NSLog.DebugGroupDatabaseAccess) != targetState.booleanValue()) {
			// Post a notification to give us a hook to perform other operations necessary to get logging going, e.g. change Logger settings, etc.
			NSNotificationCenter.defaultCenter().postNotification(new NSNotification(eoAdaptorLoggingWillChangeNotification, targetState));
    		if (targetState.booleanValue()) {
    			NSLog.allowDebugLoggingForGroups(NSLog.DebugGroupSQLGeneration|NSLog.DebugGroupDatabaseAccess);
    		} else {
    			NSLog.refuseDebugLoggingForGroups(NSLog.DebugGroupSQLGeneration|NSLog.DebugGroupDatabaseAccess);
    		}
    	}
    	if (adaptorLogger != null) {
	    	if (targetState.booleanValue()) {
	    		adaptorLogger.info("Adaptor debug on");
	    	} else {
	    		adaptorLogger.info("Adaptor debug off");
	    	}
    	}	
    	adaptorEnabled = targetState;
   }

    /**
     * Forces the garbage collector to run. The
     * max loop parameter determines the maximum
     * number of times to run the garbage collector
     * if the memory footprint is still going down.
     * In normal cases you would just need to call
     * this method with the parameter 1. If called
     * with the parameter 0 the garbage collector
     * will continue to run until no more free memory
     * is available to collect.
     * <p>
     * Note: This can be a very costly operation and
     * should only be used in extreme circumstances.
     * @param maxLoop maximum times to run the garbage
     *		collector. Passing in 0 will cause the
     *		collector to run until all free objects
     *		have been collected.
     */
    public static void forceGC(int maxLoop) {
        if (_log.isDebugEnabled()) _log.debug("Forcing full Garbage Collection");
        Runtime runtime = Runtime.getRuntime();
        long isFree = runtime.freeMemory();
        long wasFree;
        int i=0;
        do {
            wasFree = isFree;
            runtime.gc();
            isFree = runtime.freeMemory();
            i++;
        } while (isFree > wasFree && (maxLoop<=0 || i<maxLoop) );
        runtime.runFinalization(); //TODO: should this be inside the loop?
    }

    /**
     * This method can be used with Direct Action URLs to make sure
     * that the browser will reload the page. This is done by
     * adding the parameter [? | &amp;]r=random_number to the end of the
     * url.
     * @param daURL a url to add the randomization to.
     * @return url with the addition of the randomization key
     */
    // FIXME: Should check to make sure that the key 'r' isn't already present in the url.
    public static String randomizeDirectActionURL(String daURL) {
	    int r=ThreadLocalRandom.current().nextInt();
	    char c=daURL.indexOf('?')==-1 ? '?' : '&';
	    return  daURL+c+"r="+r;
    }
    /**
     * This method can be used with Direct Action URLs to make sure
     * that the browser will reload the page. This is done by
     * adding the parameter [? | &amp;]r=random_number to the end of the
     * url.
     * @param daURL a url to add the randomization to.
     */
    // FIXME: Should check to make sure that the key 'r' isn't already present in the url.
    public static void addRandomizeDirectActionURL(StringBuffer daURL) {
	    int r=ThreadLocalRandom.current().nextInt();
	    char c='?';
	    for (int i=0; i<daURL.length(); i++) {
		if (daURL.charAt(i)=='?') {
		    c='&'; break;
		}
	    }
	    daURL.append(c);
	    daURL.append("r=");
	    daURL.append(r);
    }
    
    /**
     * Adds the session ID for a given session to a given URL.
     * 
     * @param urlString
     *            URL string to add session ID form value to
     * @param session
     *            session object
     * @return URL with the addition of session ID form value
     */
    public static String addSessionIdFormValue(String urlString, WOSession session) {
    	if (urlString == null || session == null) {
    		_log.warn("not adding session ID: url=" + (urlString != null ? urlString : "<null>") + " session=" + (session != null ? session : "<null>"));
    		return urlString;
    	}
    	String sessionIdKey = WOApplication.application().sessionIdKey();
    	try {
			ERXMutableURL url = new ERXMutableURL(urlString);
			if (!url.containsQueryParameter(sessionIdKey)) {
				url.setQueryParameter(sessionIdKey, session.sessionID());
			}
			return url.toExternalForm();
		}
		catch (MalformedURLException e) {
			_log.error("invalid URL string: " + urlString, e);
		}
    	
    	return urlString;
    }

    /**
     * Uses the <code>setObjectForKey</code> method of the {@link WOSession}
     * class to push a Boolean object onto the session for a given key.
     * Note this is not using key value coding, meaning you don't need
     * to have a boolean instance variable corresponding to the given
     * key on your session object. This flag can be retrieved using
     * the method <code>booleanFlagOnSessionForKeyWithDefault</code>.
     * @param s session object on which to set the boolean flag 
     * @param key to be used in the session's dictionary
     * @param newValue boolean value to be set on the session
     */
    public static void setBooleanFlagOnSessionForKey(WOSession s,
                                                     String key,
                                                     boolean newValue) {
        s.setObjectForKey(newValue ? Boolean.TRUE : Boolean.FALSE, key);
    }

    /**
     * Retrieves a value from the session's dictionary and evaluates
     * that object using the <code>booleanValue</code> method of
     * {@link ERXValueUtilities}. If there is no object corresponding
     * to the key passed in, then the default value is returned. The
     * usual way in which boolean values are set on the session object
     * is by using the method <code>setBooleanFlagOnSessionForKey</code>
     * in this class.
     * @param s session object to retrieve the boolean flag from
     * @param key that the boolean is stored under
     * @param defaultValue value to be returned if the object in the
     *		dictionary is null
     * @return boolean value of the object stored in the session's dictionary
     *		for the given key.
     */
    public static boolean booleanFlagOnSessionForKeyWithDefault(WOSession s,
                                                                String key,
                                                                boolean defaultValue) {
        return s.objectForKey(key) != null ? ERXValueUtilities.booleanValue(s.objectForKey(key)) : defaultValue;
    }

    /**
     * Constructs a unique key based on a context.
     * A method used by the preferences mechanism from ERDirectToWeb which
     * needs to be here because it is shared by ERDirectToWeb and ERCoreBusinessLogic.
     * 
     * @param key preference key
     * @param context most likely a d2wContext object
     * @return a unique preference key for storing and retrieving preferences
     */
    // FIXME: Needs to find a better home.
    public static String userPreferencesKeyFromContext(String key, NSKeyValueCoding context) {
        StringBuilder result = new StringBuilder(key);
        result.append('.');
        String pc=(String)context.valueForKey("pageConfiguration");
        if (pc==null || pc.length()==0) {
            String en="_All_";
            EOEntity e=(EOEntity)context.valueForKey("entity");
            if (e!=null) en=e.name();
            result.append("__");
            result.append(context.valueForKey("task"));
            result.append('_');
            result.append(en);
        } else {
            result.append(pc);
        }
        return result.toString();
    }

    /**
     * Frees all of the resources associated with a given
     * process and then destroys the process.
     * @param p process to destroy
     * @deprecated use {@link ERXRuntimeUtilities#freeProcessResources(Process)} instead
     */
    @Deprecated
    public static void freeProcessResources(Process p) {
        if (p!=null) {
            try {
                if (p.getInputStream()!=null) p.getInputStream().close();
                if (p.getOutputStream()!=null) p.getOutputStream().close();
                if (p.getErrorStream()!=null) p.getErrorStream().close();
                p.destroy();
            } catch (IOException e) {}
        }
    }
    
    /**
     * Determines if a given object implements a method given
     * the name and the array of input parameters.
     * Note that this doesn't quite check the method signature
     * since the method return type is not checked.
     *
     * @param object to determine if it implements a method
     * @param methodName name of the method
     * @param parameters array of parameters
     * @return if the object implements a method with the given name
     * 		and class parameters
     */
    public static boolean objectImplementsMethod(Object object, String methodName, Class[] parameters) {
        boolean implementsMethod = false;
        for (Enumeration e = (new NSArray(object.getClass().getMethods())).objectEnumerator(); e.hasMoreElements();) {
            Method m = (Method)e.nextElement();
            if (m.getName().equals(methodName) && Arrays.equals(m.getParameterTypes(), parameters)) {
                implementsMethod = true; break;
            }
        }
        return implementsMethod;
    }

    /**
     * Initializes your WOApplication programmatically (for use in test cases and main methods) with
     * the assumption that the current directory is your main bundle URL.
     * 
     * @param applicationSubclass your Application subclass
     * @param args the commandline arguments for your application
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
	    					//Bundle-less builds. Yay!
	    		    		//throw new IllegalArgumentException("You must run your application from a .woa folder to call this method.");
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
     * Initializes your WOApplication programmatically (for use in test cases and main methods).
     * 
     * @param mainBundleName the name of your main bundle
     * @param applicationSubclass your Application subclass
     * @param args the commandline arguments for your application
     */
    public static void initApp(String mainBundleName, Class applicationSubclass, String[] args) {
    	ERXExtensions.initApp(mainBundleName, null, applicationSubclass, args);
    }
    
    private static boolean _appInitialized = false;
    /**
     * Initializes your WOApplication programmatically (for use in test cases and main methods).
     * 
     * @param mainBundleName the name of your main bundle (or null to use mainBundleURL)
     * @param mainBundleURL the URL to your main bundle (ignored if mainBundleName is set)
     * @param applicationSubclass your Application subclass
     * @param args the commandline arguments for your application
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
	        // Odds are you are only using this method for test cases and development mode
	        System.setProperty("er.extensions.ERXApplication.developmentMode", "true");
	        ERXApplication.primeApplication(mainBundleName, mainBundleURL, applicationSubclass.getName());
	        //NSNotificationCenter.defaultCenter().postNotification(new NSNotification(ERXApplication.ApplicationDidCreateNotification, WOApplication.application()));
		}
		catch (IOException e) {
			throw new NSForwardException(e);
		}
    	_appInitialized = true;
    }
    
    /**
     * Initializes Wonder EOF programmatically (for use in test cases and main methods).  You do
     * not need to call this method if you already called initApp.  This is lighter-weight than 
     * initApp, and tries to just get enough configured to make EOF work properly.  This method
     * assumes you are running your app from a .woa folder.
     * 
     * <p>This is equivalent to calling <code>initEOF(new File("."), args)</code>.</p>
     * 
     * @param args the commandline arguments for your application
     * @throws IllegalArgumentException if the current dir or mainBundleFolder is not a *.woa bundle.
     */
    public static void initEOF(final String[] args) {
    	ERXExtensions.initEOF(new File("."), args);
    }
    /**
     * Initializes Wonder EOF programmatically (for use in test cases and main methods).  You do
     * not need to call this method if you already called initApp.  This is lighter-weight than 
     * initApp, and tries to just get enough configured to make EOF work properly.
     * 
     * <p>This is equivalent to calling <code>initEOF(mainBundleFolder, args, true, true, true)</code>.</p>
     * 
     * @param mainBundleFolder the folder of your main bundle
     * @param args the commandline arguments for your application
     * @throws IllegalArgumentException if the current dir or mainBundleFolder is not a *.woa bundle.
     */
    public static void initEOF(final File mainBundleFolder, final String[] args) {
    	initEOF(mainBundleFolder, args, true, true, true);
    }
    /**
     * <p>
     * Initializes Wonder EOF programmatically (for use in test cases and main methods).  You do
     * not need to call this method if you already called initApp.  This is lighter-weight than 
     * initApp, and tries to just get enough configured to make EOF work properly. This method is also,
     * unlike {@link #initEOF(String[])} or {@link #initEOF(File, String[])}, not so restrictive as to
     * require the name of the bundle be a <code>*.woa</code>, and nor does it require <code>*.framework</code>
     * as the name of the bundle.
     * </p>
     * <p>
     * It can therefore be useful for, and used with, folder, jar or war
     * bundles -- whichever bundle is referenced by <code>mainBundleURI</code> -- so long as it is
     * bundle-like in content rather than by name. For NSBundle, this usually just requires a Resources folder
     * within the bundle.
     * </p>
     * <p>
     * For example, if you're build tool compiles sources and puts Resources under <code>target/classes</code>
     * you can call this method via <code>ERXExtensions.initEOF(new File(projectDir, "target/classes"), args, true)</code>.
     * </p>
     * <p><b>Note 1:</b>
     *  this will set the system property <code>webobjects.user.dir</code> to the canonical path of the 
     * given bundle uri if, and only if, the bundle uri points to a directory and is schema is <code>file</code>.
     * </p>
     * <p><b>Note 2:</b>
     *  this will set NSBundle's mainBundle to the referenced bundle loaded via
     *  {@link er.extensions.foundation.ERXRuntimeUtilities#loadBundleIfNeeded(File)} if found.
     * </p>
     * 
     * <p>This is equivalent to calling <code>initEOF(mainBundleFolder, args, assertsBundleExists, false, true)</code>.</p>
     * 
     * @param mainBundleFile the archive file or directory of your main bundle
     * @param args the commandline arguments for your application
     * @param assertsBundleExists ensures that the bundle exists and is loaded
     * @throws NSForwardException if the given bundle doesn't satisfy the given assertions or
     *  		ERXRuntimeUtilities.loadBundleIfNeeded or ERXApplication.setup fails.
     * @see #initEOF(File, String[], boolean, boolean, boolean)
     */
    public static void initEOF(final File mainBundleFile, final String[] args, boolean assertsBundleExists) {
    	initEOF(mainBundleFile, args, assertsBundleExists, false, true);
    }
    private static boolean _eofInitialized = false;
    private static final Lock _eofInitializeLock = new ReentrantLock();
    /**
     * <p>
     * Initializes Wonder EOF programmatically (for use in test cases and main methods).  You do
     * not need to call this method if you already called initApp.  This is lighter-weight than 
     * initApp, and tries to just get enough configured to make EOF work properly. This method is also,
     * unlike {@link #initEOF(String[])} or {@link #initEOF(File, String[])}, not so restrictive as to
     * require the name of the bundle be a <code>*.woa</code>, and nor does it require <code>*.framework</code>
     * as the name of the bundle.
     * </p>
     * <p>
     * It can therefore be useful for, and used with, folder, jar or war
     * bundles -- whichever bundle is referenced by <code>mainBundleURI</code> -- so long as it is
     * bundle-like in content rather than by name. For NSBundle, this usually just requires a Resources folder
     * within the bundle.
     * </p>
     * <p>
     * For example, if you're build tool compiles sources and puts Resources under <code>target/classes</code>
     * you can call this method via <code>ERXExtensions.initEOF(new File(projectDir, "target/classes").toURI(), args)</code>.
     * </p>
     * <p><b>Note 1:</b>
     *  this will set the system property <code>webobjects.user.dir</code> to the canonical path of the 
     * given bundle uri if, and only if, the bundle uri points to a directory and is schema is <code>file</code>.
     * </p>
     * <p><b>Note 2:</b>
     *  this will set NSBundle's mainBundle to the referenced bundle loaded via
     *  {@link er.extensions.foundation.ERXRuntimeUtilities#loadBundleIfNeeded(File)} if found.
     * </p>
     * 
     * @param mainBundleFile the archive file or directory of your main bundle
     * @param args the commandline arguments for your application
     * @param assertsBundleExists ensures that the bundle exists and is loaded
     * @param assertsBundleIsWOApplicationFolder ensures that the bundle referenced by mainBundleFile, or the current dir if fallbackToUserDirAsBundle is true, is a <code>*.woa</code> bundle folder.
     * @param fallbackToUserDirAsBundle falls back to current dir if the mainBundleFile does not exist
     * @throws NSForwardException if the given bundle doesn't satisfy the given assertions or
     *  		ERXRuntimeUtilities.loadBundleIfNeeded or ERXApplication.setup fails.
     * @see er.extensions.foundation.ERXRuntimeUtilities#loadBundleIfNeeded(File)
     * @see NSBundle#_setMainBundle(NSBundle)
     * @see er.extensions.appserver.ERXApplication#setup(String[])
     * @see #bundleDidLoad(NSNotification)
     */
    public static void initEOF(final File mainBundleFile, String[] args, boolean assertsBundleExists, boolean assertsBundleIsWOApplicationFolder, boolean fallbackToUserDirAsBundle) {
    	_eofInitializeLock.lock();
    	try {
	    	if (!_eofInitialized) {
	    		try {
	    			File bundleFile = mainBundleFile;
	    			
	    			if (assertsBundleIsWOApplicationFolder) {
	    				if (!bundleFile.exists() || !bundleFile.getName().endsWith(".woa")) {
	    					bundleFile = new File(".").getCanonicalFile();
	    					if (!bundleFile.exists() || !bundleFile.getName().endsWith(".woa")) {
	    						throw new IllegalArgumentException("Assertion failure. You must run your application from the .woa folder to call this method.");
	    					}
	    				}
	    			}
	    			
	    			if (assertsBundleExists) {
	    				if (bundleFile == null || !bundleFile.exists()) {
	    					if (fallbackToUserDirAsBundle) {
	    						bundleFile = new File(".").getCanonicalFile();
	    					} else {
	    						throw new IllegalArgumentException("Assertion failure. The main bundle is required to exist to call this method.");
	    					}
	    				}
	    			}
	    			
	    			if (bundleFile != null && bundleFile.isDirectory()) {
	    				System.setProperty("webobjects.user.dir", bundleFile.getCanonicalPath());
	    			}
	    			
	    			NSBundle mainBundle = null;
	    			try {
	    				mainBundle = ERXRuntimeUtilities.loadBundleIfNeeded(bundleFile);
	    				if (mainBundle == null) {
							throw new IllegalArgumentException("The main bundle failed to load.");
						}
	    				NSBundle._setMainBundle(mainBundle);
						NSLog.debug.appendln("initEOF setting main bundle to " + mainBundle);
	    			}
	    			catch (Exception e) {
	    				if (assertsBundleExists) {
	    					throw e;
	    				}
	    			}
									
					ERXApplication.setup(args);
					ERXFrameworkPrincipal.sharedInstance(ERXExtensions.class).bundleDidLoad(null);
				}
				catch (Exception e) {
					throw new NSForwardException(e);
				}
		    	_eofInitialized = true;
	    	}
    	} finally {
    		_eofInitializeLock.unlock();
    	}
    }
}

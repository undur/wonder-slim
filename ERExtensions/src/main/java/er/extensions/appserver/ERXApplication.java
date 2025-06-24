/*

 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOAction;
import com.webobjects.appserver.WOAdaptor;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOCookie;
import com.webobjects.appserver.WOCookie.SameSite;
import com.webobjects.appserver.WOMessage;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOTimer;
import com.webobjects.appserver._private.WOComponentDefinition;
import com.webobjects.appserver._private.WODeployedBundle;
import com.webobjects.appserver._private.WOProperties;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSMutableSet;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSTimestamp;

import er.extensions.ERXExtensions;
import er.extensions.ERXFrameworkPrincipal;
import er.extensions.ERXLoggingSupport;
import er.extensions.ERXMonitorServer;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.bettertemplates.ERXBetterTemplates;
import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXExceptionUtilities;
import er.extensions.foundation.ERXPatcher;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;
import er.extensions.foundation.ERXUtilities;
import er.extensions.statistics.ERXStats;

public abstract class ERXApplication extends ERXAjaxApplication {

	private static final Logger log = LoggerFactory.getLogger(ERXApplication.class);
	private static final Logger requestHandlingLog = LoggerFactory.getLogger("er.extensions.ERXApplication.RequestHandling");
	private static final Logger statsLog = LoggerFactory.getLogger("er.extensions.ERXApplication.Statistics");

	/**
	 * Notification to get posted when terminate() is called.
	 */
	public static final String ApplicationWillTerminateNotification = "ApplicationWillTerminateNotification";

	/**
	 * Notification to post when all bundles were loaded but before their principal was called
	 * 
	 * FIXME: This notification was posted by the (ERX)Loader. We need to implement at least that part again if we want to keep it // Hugi 2025-06-22 
	 */
	public static final String AllBundlesLoadedNotification = "NSBundleAllBundlesLoaded";

	/**
	 * Notification to post when all bundles were loaded but before their principal was called
	 */
	public static final String ApplicationDidCreateNotification = "NSApplicationDidCreateNotification";

	/**
	 * Notification to post when all application initialization processes are complete
	 */
	public static final String ApplicationDidFinishInitializationNotification = "NSApplicationDidFinishInitializationNotification";
	
	/**
	 * The path rewriting pattern to match (@see _rewriteURL)
	 */
	private String _replaceApplicationPathPattern;

	/**
	 * The path rewriting replacement to apply to the matched pattern (@see _rewriteURL)
	 */
	private String _replaceApplicationPathReplace;

	/**
	 * The SSL host used by this application.
	 */
	private String _sslHost;

	/**
	 * The SSL port used by this application.
	 */
	private Integer _sslPort;

	/**
	 * Tracks whether or not _addAdditionalAdaptors has been called yet.
	 */
	private boolean _initializedAdaptors = false;

	/**
	 * To support load balancing with mod_proxy
	 */
	private String _proxyBalancerRoute = null;

	/**
	 * To support load balancing with mod_proxy
	 */
	private String _proxyBalancerCookieName = null;

	/**
	 * To support load balancing with mod_proxy
	 */
	private String _proxyBalancerCookiePath = null;

	/**
	 * Host name used for URL generation when no request is present (for example, in background tasks)
	 */
	private String _publicHost;

	/**
	 * Watches the state of the application's memory heap and handles low memory situations
	 */
	private final ERXLowMemoryHandler _lowMemoryHandler;

	/**
	 * The time taken from invoking main, until the end of the application constructor
	 */
	private static long _startupTimeInMilliseconds = System.currentTimeMillis();

	/**
	 * Keeps track of whether didFinishLaunching has been invoked. We use this to keep track of whether we can declare variables that are dependent on configuration as constant (as this is written, only applies to isDevelopmentMode)
	 */
	private static boolean didFinishLaunchingInvoked = false;

	/**
	 * Indicates if ERXApplication.main() has been invoked (so we can check that application actually did so)
	 */
	private static boolean wasERXApplicationMainInvoked = false;

	/**
	 * Keeps track of whether the application is running in development mode. Set in didFinishLaunching and used after that, since we assume this value will never change after the application has been initialized
	 */
	private static boolean isDevelopmentModeCached;

	/**
	 * Application entry point.
	 */
	public static void main(String argv[], Class applicationClass) {
		wasERXApplicationMainInvoked = true;
		useProjectBundleIfDeveloping();
		disablePBXProjectWatcher();
		setup(argv);
		WOApplication.main(argv, applicationClass);
	}

	/**
	 * If a build.properties file exists in the current working directory, we're probably doing development. So let's tell the framework by setting NSProjectBundleEnabled=true
	 */
	private static void useProjectBundleIfDeveloping() {
		if( Files.exists(Paths.get("build.properties")) ) {
			System.setProperty("NSProjectBundleEnabled", "true");
			System.out.print( """
								===================================================================================================
								== build.properties found. Assuming we're doing development. Setting NSProjectBundleEnabled=true ==
								===================================================================================================
								""");
		}
	}

	/**
	 * Called prior to actually initializing the app. Defines framework load order, class path order, checks patches etc.
	 */
	public static void setup(String[] argv) {
		ERXConfigurationManager.defaultManager().setCommandLineArguments(argv);
		ERXFrameworkPrincipal.setUpFrameworkPrincipalClass(ERXExtensions.class);

		if( ERXProperties.booleanForKeyWithDefault( "er.extensions.ERXApplication.enableERXShutdownHook", true ) ) {
			ERXShutdownHook.useMe();
			ERXShutdownHook.initERXShutdownHook();
		}
	}

	public ERXApplication() {

		// FIXME: Figure out why this is getting initialized here and document it // Hugi 2025-06-07
		ERXStats.initStatisticsIfNecessary();
		
		// WOFrameworksBaseURL and WOApplicationBaseURL properties are broken in 5.4. This is the workaround.
		frameworksBaseURL();
		applicationBaseURL();

		if (System.getProperty("WOFrameworksBaseURL") != null) {
			setFrameworksBaseURL(System.getProperty("WOFrameworksBaseURL"));
		}

		if (System.getProperty("WOApplicationBaseURL") != null) {
			setApplicationBaseURL(System.getProperty("WOApplicationBaseURL"));
		}

		// FIXME: We need to validate the entire setup of logging at some point // Hugi 2025-06-07
		ERXLoggingSupport.reInitConsoleAppenders();

		checkEnvironment();

		if( useBetterTemplates() ) {
			ERXBetterTemplates.configureWOForBetterTemplates();
			log.info( "Better templates are active" );
		}
		else {
			log.info( "Better templates are inactive" );
		}

		didCreateApplication();
		NSNotificationCenter.defaultCenter().postNotification(new NSNotification(ApplicationDidCreateNotification, this));

		installPatches();

		_lowMemoryHandler = new ERXLowMemoryHandler();

		registerRequestHandler(new ERXComponentRequestHandler(), componentRequestHandlerKey());
		registerRequestHandler(new ERXDirectActionRequestHandler(), directActionRequestHandlerKey());
		registerRequestHandler(new ERXDirectActionRequestHandler(ERXDirectAction.class.getName(), "stats", false), "erxadm");

		if (_rapidTurnaroundActiveForAnyProject() && isDirectConnectEnabled()) {
			registerRequestHandler(new ERXStaticResourceRequestHandler(), "_wr_");
		}

		final String defaultEncoding = System.getProperty("er.extensions.ERXApplication.DefaultEncoding");

		if (defaultEncoding != null) {
			setDefaultEncoding(defaultEncoding);
		}

		// Configure the WOStatistics CLFF logging since it can't be controlled by a property, grrr.
		configureStatisticsLogging();

		_replaceApplicationPathPattern = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.pattern");
		if (_replaceApplicationPathPattern != null && _replaceApplicationPathPattern.length() == 0) {
			_replaceApplicationPathPattern = null;
		}
		_replaceApplicationPathReplace = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.replace");

		if (_replaceApplicationPathPattern == null && rewriteDirectConnectURL()) {
			_replaceApplicationPathPattern = "/cgi-bin/WebObjects/" + name() + applicationExtension();
			if (_replaceApplicationPathReplace == null) {
				_replaceApplicationPathReplace = "";
			}
		}

		_publicHost = ERXProperties.stringForKeyWithDefault("er.extensions.ERXApplication.publicHost", host());
		
		startMonitorServer();

		NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("finishInitialization"), WOApplication.ApplicationWillFinishLaunchingNotification, null);
		NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("didFinishLaunching"), WOApplication.ApplicationDidFinishLaunchingNotification, null);
		NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("addBalancerRouteCookieByNotification"), WORequestHandler.DidHandleRequestNotification, null);
	}

	/**
	 * Run some environment validation. If any of those checks fail, we log the error and exit.
	 */
	private void checkEnvironment() {
		try {
			checkERXApplicationMainInvoked();
			checkMainBundleIsNotJavaFoundation();
			checkClasspathValidity();
		}
		catch (Exception e) {
			System.out.println( """

					==============================================================================================
					== %s
					==============================================================================================

					""".formatted(e.getMessage()));
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Ensure ERXApplication.main() was invoked when running the application (as opposed to WOApplication.main()) 
	 */
	private void checkERXApplicationMainInvoked() throws Exception {
		if (!isDeployedAsServlet() && !wasERXApplicationMainInvoked ) {
			throw new IllegalStateException(
					"""
					
					==============================================================================================
					It seems that your application class %s did not call %s.main(argv[], applicationClass) method.
					Please modify your Application.java as the followings so that %s can provide its rapid turnaround feature completely.
					
					public static void main(String argv[]) {
						ERXApplication.main(argv, Application.class);
					}
					==============================================================================================

					""".formatted(
					application().getClass().getName(),
					ERXApplication.class.getName(),
					ERXConfigurationManager.class.getName()));
		}
	}

	/**
	 * Ensure the main bundle's name isn't JavaFoundation, since if it is, something is seriously wrong
	 */
	private static void checkMainBundleIsNotJavaFoundation() throws Exception {
		if ("JavaFoundation".equals(NSBundle.mainBundle().name())) {
			throw new IllegalStateException("Your main bundle is \"JavaFoundation\". Are you sure ERExtensions is the first <dependency> in your pom? And if you're developing; are you sure your working directory is your application's project?");
		}
	}

	/**
	 * Ensure ERFoundation, ERWebObjects and ERExtensions are earlier on the classpath than JavaFoundation and JavaWebObjects.
	 * These libraries contain "patch classes" that override classes from the WO frameworks.
	 */
	private static void checkClasspathValidity() throws Exception {
		final String[] classpathElements = System.getProperty("java.class.path").split(File.pathSeparator);
		
		boolean foundERFoundation = false;
		boolean foundERWebObjects = false;
		boolean foundERExtensions = false;
		
		for (String cpe : classpathElements) {
			final String cpeLowercase = cpe.toLowerCase();

			if( cpeLowercase.contains("erfoundation") ) {
				foundERFoundation = true;
			}

			if( cpeLowercase.contains("erwebobjects") ) {
				foundERWebObjects = true;
			}

			if( cpeLowercase.contains("erextensions") ) {
				foundERExtensions = true;
			}
			
			if( cpeLowercase.contains("javawebobjects") || cpeLowercase.contains("javafoundation") ) {
				if( !foundERFoundation || !foundERWebObjects || !foundERExtensions ) {
					throw new IllegalStateException("Whoops. ERFoundation, ERWebObjects and ERExtensions must appear earlier on the classpath than JavaFoundation and JavaWebObjects. The best way to ensure this is to make ERExtensions the first <dependency> in your pom file");
				}
			}
		}
	}

	/**
	 * Indicates if we want to enable the better (old WOOgnl) template parser
	 * 
	 * @return true by default. Override to change.
	 */
	protected boolean useBetterTemplates() {
		return true;
	}

	private void startMonitorServer() {
		// We'll only start up the monitor server if a password is set for it
		final String monitorServerPassword = ERXProperties.stringForKey( "WOMonitorServicePassword" );

		if( monitorServerPassword != null ) {
			try {
				// FIXME: This method of obtaining a port for the monitor service absolutely sucks
				final int monitorServerPort = port().intValue() + 10000;
				ERXMonitorServer.start( monitorServerPort );
			}
			catch( IOException e ) {
				log.error( "Failed to start up the monitor service", e );
			}
		}		
	}

	/**
	 * Installs several bugfixes and enhancements to WODynamicElements.
	 */
	protected void installPatches() {
		ERXPatcher.installPatches();

		if (contextClassName().equals("WOContext")) {
			setContextClassName(ERXWOContext.class.getName());
		}
	}

	/**
	 * Adds support for automatic application cycling. Applications can be configured to cycle in two ways:
	 * 
	 * The first way is by setting the System property <b>ERTimeToLive</b> to the number of seconds (+ a random interval of 10 minutes) that the
	 * application should be up before terminating. Note that when the application's time to live is up it will quit calling the method <code>killInstance</code>.
	 * 
	 * The second way is by setting the System property <b>ERTimeToDie</b> to the time in seconds after midnight when the app should be starting to refuse new sessions.
	 * In this case when the application starts to refuse new sessions it will also register a kill timer that will terminate the application between 0 minutes and 1:00 minutes.
	 */
	@Override
	public void run() {
		try {
			int timeToLive = ERXProperties.intForKey("ERTimeToLive");
			if (timeToLive > 0) {
				log.info("Instance will live " + timeToLive + " seconds.");
				NSLog.out.appendln("Instance will live " + timeToLive + " seconds.");
				// add a fudge factor of around 10 minutes
				timeToLive += Math.random() * 600;
				NSTimestamp exitDate = (new NSTimestamp()).timestampByAddingGregorianUnits(0, 0, 0, 0, 0, timeToLive);
				WOTimer t = new WOTimer(exitDate, 0, this, "killInstance", null, null, false);
				t.schedule();
			}
			int timeToDie = ERXProperties.intForKey("ERTimeToDie");
			if (timeToDie > 0) {
				log.info("Instance will not live past " + timeToDie + ":00.");
				NSLog.out.appendln("Instance will not live past " + timeToDie + ":00.");

				LocalDateTime now = LocalDateTime.now();

				int s = (timeToDie - now.getHour()) * 3600 - now.getMinute() * 60;

				if (s < 0) {
					s += 24 * 3600; // how many seconds to the deadline
				}

				// deliberately randomize this so that not all instances restart at the same time adding up to 1 hour
				s += (Math.random() * 3600);

				NSTimestamp stopDate = new NSTimestamp().timestampByAddingGregorianUnits(0, 0, 0, 0, 0, s);

				WOTimer t = new WOTimer(stopDate, 0, this, "startRefusingSessions", null, null, false);
				t.schedule();
			}
			super.run();
		}
		catch (RuntimeException t) {
			if (ERXApplication._wasMainInvoked) {
				ERXApplication.log.error(name() + " failed to start.", t);
			}
			throw t;
		}
	}

	@Override
	public WOResourceManager createResourceManager() {
		return new ERXResourceManager();
	}

	/**
	 * When a context is created we push it into thread local storage. This handles the case for direct actions.
	 */
	@Override
	public WOContext createContextForRequest(WORequest request) {
		final WOContext context = super.createContextForRequest(request);

		// We only want to push in the context the first time it is created, i.e we don't want to lose the current context when we create a context for an error page.
		if (ERXWOContext.currentContext() == null) {
			ERXWOContext.setCurrentContext(context);
		}

		return context;
	}

	@Override
	public ERXRequest createRequest(String method, String url, String httpVersion, Map<String, ? extends List<String>> headers, NSData content, Map<String, Object> info) {

		// Workaround for #3428067 (Apache Server Side Include module will feed "INCLUDED" as the HTTP version, which causes a request object not to be created by an exception.
		if (httpVersion == null || httpVersion.startsWith("INCLUDED")) {
			httpVersion = "HTTP/1.0";
		}

		if (rewriteDirectConnectURL()) {
			url = adaptorPath() + name() + applicationExtension() + url;
		}

		return new ERXRequest(method, url, httpVersion, headers, content, info);
	}

	/**
	 * Determines if an application is deployed as servlet (contextClassName() is set WOServletContext or ERXWOServletContext)
	 * 
	 * @return true if the application is deployed as servlet.
	 */
	public boolean isDeployedAsServlet() {
		return contextClassName().contains("Servlet");
	}

	/**
	 * Called, for example, when refuse new sessions is enabled and the request contains an expired session.
	 * If mod_rewrite is being used we don't want the adaptor prefix being part of the redirect.
	 * 
	 * @see com.webobjects.appserver.WOApplication#_newLocationForRequest(com.webobjects.appserver.WORequest)
	 */
	@Override
	public String _newLocationForRequest(WORequest aRequest) {
		return _rewriteURL(super._newLocationForRequest(aRequest));
	}

	/**
	 * Configures the statistics logging for a given application.
	 * By default will log to a file &lt;base log directory&gt;/&lt;WOApp Name&gt;-&lt;host&gt;-&lt;port&gt;.log if the base log path is defined.
	 * The base log path is defined by the property <code>er.extensions.ERXApplication.StatisticsBaseLogPath</code>.
	 * The default log rotation frequency is 24 hours, but can be changed by setting in milliseconds the property <code>er.extensions.ERXApplication.StatisticsLogRotationFrequency</code>
	 */
	public void configureStatisticsLogging() {
		String statisticsBasePath = System.getProperty("er.extensions.ERXApplication.StatisticsBaseLogPath");
		if (statisticsBasePath != null) {
			// Defaults to a single day
			int rotationFrequency = ERXProperties.intForKeyWithDefault("er.extensions.ERXApplication.StatisticsLogRotationFrequency", 24 * 60 * 60 * 1000);
			String logPath = statisticsBasePath + File.separator + name() + "-" + ERXConfigurationManager.defaultManager().hostName() + "-" + port() + ".log";
			if (log.isDebugEnabled()) {
				log.debug("Configured statistics logging to file path \"" + logPath + "\" with rotation frequency: " + rotationFrequency);
			}
			statisticsStore().setLogFile(logPath, rotationFrequency);
		}
	}

	/**
	 * Notification method called when the application posts the notification {@link WOApplication#ApplicationWillFinishLaunchingNotification}.
	 * This method calls subclasses' {@link #finishInitialization} method.
	 * 
	 * @param n notification posted after WOApplication has been constructed, but before the application is ready for accepting requests.
	 */
	public final void finishInitialization(NSNotification n) {
		finishInitialization();
		NSNotificationCenter.defaultCenter().postNotification(new NSNotification(ERXApplication.ApplicationDidFinishInitializationNotification, this));
	}

	/**
	 * Notification method called when the application posts the notification {@link WOApplication#ApplicationDidFinishLaunchingNotification}.
	 * This method calls subclasse's {@link #didFinishLaunching} method.
	 * 
	 * @param n notification posted after WOApplication has finished launching and is ready for accepting requests.
	 */
	public final void didFinishLaunching(NSNotification n) {
		didFinishLaunching();
		ERXStats.logStatisticsForOperation(statsLog, "sum");

		// FIXME: Is this being handled by ERXStats? Check out. 
		_startupTimeInMilliseconds = System.currentTimeMillis() - _startupTimeInMilliseconds;
		log.info( String.format( "Startup time %s ms: ", _startupTimeInMilliseconds ) );

		System.out.println( "============= LOADED BUNDLES START =============" );
		System.out.println( String.format( "%-22s : %-65s : %s", "-- Name --", "-- Bundle class --", "-- isJar --" ) );
		for( NSBundle nsBundle : NSBundle._allBundlesReally() ) {
			System.out.println( String.format( "%-22s : %-65s : %s", nsBundle.name(), nsBundle.getClass().getName(), nsBundle.isJar() ) );
		}
		System.out.println( "============= LOADED BUNDLES END ===============" );
		
		didFinishLaunchingInvoked = true;
		isDevelopmentModeCached = checkIsDevelopmentMode();
	}

	/**
	 * Called when the application posts {@link WOApplication#ApplicationWillFinishLaunchingNotification}.
	 * Override this to perform application initialization.
	 */
	public void finishInitialization() {}

	protected void didCreateApplication() {}

	/**
	 * Called when the application posts {@link WOApplication#ApplicationDidFinishLaunchingNotification}.
	 * Override this to perform application specific tasks after the application has been initialized.
	 * This is a good spot to perform batch application tasks.
	 */
	public void didFinishLaunching() {}

	/**
	 * @return The <code>WOApplication.application()</code> cast as an ERXApplication
	 */
	public static ERXApplication erxApplication() {
		return (ERXApplication) WOApplication.application();
	}

	/**
	 * Used to instantiate a WOComponent when no context is available, typically outside of a session
	 * 
	 * @param pageName The name of the WOComponent to instantiate
	 * @return created WOComponent with the given name
	 */
	public static WOComponent instantiatePage(String pageName) {
		return application().pageWithName(pageName, ERXWOContext.newContext());
	}

	/**
	 * Stops the application from handling any new requests. Will still handle requests from existing sessions.
	 */
	public void startRefusingSessions() {
		log.info("Refusing new sessions");
		NSLog.out.appendln("Refusing new sessions");
		refuseNewSessions(true);
	}

	/**
	 * Bugfix for WO component loading. It fixes:
	 * 
	 * <ul>
	 * <li>when isCachingEnabled is ON, and you have a new browser language that
	 * hasn't been seen so far, the component gets re-read from the disk, which
	 * can wreak havoc if you overwrite your html/wod with a new version.
	 * <li>when caching enabled is OFF, and you make a change, you only see the
	 * change in the first browser that touches the page. You need to re-save if
	 * you want it seen in the second one.
	 * </ul>
	 * 
	 * You need to set <code>er.extensions.ERXApplication.fixCachingEnabled=false</code> if you don't want it to load.
	 * 
	 * @author ak
	 */
	@Override
	public WOComponentDefinition _componentDefinition(String s, NSArray nsarray) {

		if (ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.fixCachingEnabled", true)) {
			// _expectedLanguages already contains all the languages in all projects,
			// so there is no need to check for the ones that come in...
			return super._componentDefinition(s, (nsarray != null ? nsarray.arrayByAddingObjectsFromArray(_expectedLanguages()) : _expectedLanguages()));
		}

		return super._componentDefinition(s, nsarray);
	}

	/**
	 * Override and return false if you do not want sessions to be refused when memory is starved.
	 * 
	 * @return whether or not sessions should be refused on starved memory
	 */
	protected boolean refuseSessionsOnStarvedMemory() {
		return true;
	}

	/**
	 * Overridden to return the super value OR true if the app is memory starved.
	 */
	@Override
	public boolean isRefusingNewSessions() {
		return super.isRefusingNewSessions() || (refuseSessionsOnStarvedMemory() && _lowMemoryHandler.isMemoryStarved());
	}

	/**
	 * Overridden to fix that direct connect apps can't refuse new sessions.
	 */
	@Override
	public synchronized void refuseNewSessions(boolean value) {
		boolean success = false;

		try {
			Field f = WOApplication.class.getDeclaredField("_refusingNewClients");
			f.setAccessible(true);
			f.set(this, value);
			success = true;
		}
		catch (SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			log.error("Failed to do some stupid reflection shit", e);
		}

		if (!success) {
			super.refuseNewSessions(value);
		}

		// #81712. App will terminate immediately if the right conditions are met.
		if (value && (activeSessionsCount() <= minimumActiveSessionsCount())) {
			log.info("Refusing new clients and below min active session threshold, about to terminate...");
			terminate();
		}

		resetKillTimer(isRefusingNewSessions());
	}

	protected WOTimer _killTimer;

	/**
	 * Sets the kill timer.
	 */
	private void resetKillTimer(boolean install) {
		// we assume that we changed our mind about killing the instance.
		if (_killTimer != null) {
			_killTimer.invalidate();
			_killTimer = null;
		}
		if (install) {
			int timeToKill = ERXProperties.intForKey("ERTimeToKill");
			if (timeToKill > 0) {
				log.warn("Registering kill timer in " + timeToKill + "seconds");
				NSTimestamp exitDate = (new NSTimestamp()).timestampByAddingGregorianUnits(0, 0, 0, 0, 0, timeToKill);
				_killTimer = new WOTimer(exitDate, 0, this, "killInstance", null, null, false);
				_killTimer.schedule();
			}
		}
	}

	/**
	 * Killing the instance will log a 'Forcing exit' message and then call <code>System.exit(1)</code>
	 */
	public void killInstance() {
		log.info("Forcing exit");
		NSLog.out.appendln("Forcing exit");
		System.exit(1);
	}

	/**
	 * Cached computed name
	 */
	private String _cachedApplicationName;

	/**
	 * Adds the ability to completely change the applications name by setting the System property <b>ERApplicationName</b>.
	 * Will also append the <code>nameSuffix</code> if one is set.
	 * 
	 * @return the computed name of the application.
	 */
	@Override
	public String name() {
		if (_cachedApplicationName == null) {
			synchronized (this) {
				_cachedApplicationName = System.getProperty("ERApplicationName");

				if (_cachedApplicationName == null) {
					_cachedApplicationName = super.name();
				}

				if (_cachedApplicationName != null) {
					String suffix = nameSuffix();

					if (suffix != null && suffix.length() > 0) {
						_cachedApplicationName += suffix;
					}
				}
			}
		}

		return _cachedApplicationName;
	}

	/**
	 * The name suffix is appended to the current name of the application. This adds the ability to add
	 * a useful suffix to differentiate between different sets of applications on the same machine.
	 * 
	 * The name suffix is set via the System property <b>ERApplicationNameSuffix</b>.
	 * For example if the name of an application is Buyer and you want to have a training instance appear with the name
	 * BuyerTraining then you would set the ERApplicationNameSuffix to Training.
	 * 
	 * @return the System property <b>ERApplicationNameSuffix</b> or <code>""</code>
	 */
	public String nameSuffix() {
		return ERXProperties.stringForKeyWithDefault("ERApplicationNameSuffix", "");
	}

	/**
	 * @return the name of the application executable (from WOApplication.name())
	 */
	public String rawName() {
		return super.name();
	}

	/**
	 * Workaround for WO 5.2 DirectAction lock-ups. As the super-implementation is empty,
	 * it is fairly safe to override here to call the normal exception handling earlier than usual.
	 * 
	 * FIXME: Since this is a fix for WO 5.2, do we still need it in 5.4.3?
	 */
	@Override
	public WOResponse handleActionRequestError(WORequest aRequest, Exception exception, String reason, WORequestHandler aHandler, String actionClassName, String actionName, Class actionClass, WOAction actionInstance) {
		WOContext context = actionInstance != null ? actionInstance.context() : null;

		boolean didCreateContext = false;

		if (context == null) {
			// AK: we provide the "handleException" with not much enough info to output a reasonable error message
			context = createContextForRequest(aRequest);
			didCreateContext = true;
		}

		final WOResponse response = handleException(exception, context);

		// CH: If we have created a context, then the request handler won't know about it and can't put the components from
		// handleException(exception, context) to sleep nor check-in any session that may have been checked out or created (e.g. from a component action URL.
		//
		// I'm not sure if the reasoning below was valid, or of the real cause of this deadlocking was creating the context
		// above and then creating / checking out a session during handleException(exception, context). In any case, a zombie
		// session was getting created with WO 5.4.3 and this does NOT happen with a pure WO application making the code above
		// a prime suspect. I am leaving the code below in so that if it does something for prior versions, that will still work.
		if (didCreateContext) {
			context._putAwakeComponentsToSleep();
			saveSessionForContext(context);
		}

		// AK: bugfix for #4186886 (Session store deadlock with DAs). The bug occurs in 5.2.3, I'm not sure about other versions.
		// It may create other problems, but this one is very severe to begin with. The crux of the matter is that for certain exceptions, the DA request handler
		// does not check sessions back in which leads to a deadlock in the session store when the session is accessed again.
		else if (context.hasSession() && ("InstantiationError".equals(reason) || "InvocationError".equals(reason))) {
			context._putAwakeComponentsToSleep();
			saveSessionForContext(context);
		}

		return response;
	}

	/**
	 * Overridden to log extra information about state when exception is handled.
	 */
	@Override
	public WOResponse handleException(Exception exception, WOContext context) {
		// We first want to test if we ran out of memory. If so we need to quit ASAP.
		handlePotentiallyFatalException(exception);

		// Generate a unique exception ID for display in logs/exception page
		final UUID exceptionID = UUID.randomUUID();
		
		// Store the error's id in the context thread's dictionary (so it can be used for display in other locations, notably the exception page)
		// CHECKME: This is not optimal. Ideally, we'd add the id to the exception's "metadata" generated below. But it will do for now // Hugi 2024-02-28
		ERXThreadStorage.takeValueForKey( exceptionID.toString(), "exceptionID" );
		
		// Not a fatal exception, business as usual.
		final NSDictionary extraInfo = ERXUtilities.extraInformationForExceptionInContext(exception, context);

		log.error("Exception caught: " + exception.getMessage() + "\nexceptionID: " + exceptionID + "\nExtra info: " + NSPropertyListSerialization.stringFromPropertyList(extraInfo) + "\n", exception);

		WOResponse response = super.handleException(exception, context);
		response.setStatus(500);
		return response;
	}

	/**
	 * Handles the potentially fatal OutOfMemoryError by quitting the application ASAP.
	 * Broken out into a separate method to make custom error handling easier, ie. generating your own error pages in production, etc.
	 * 
	 * @param exception to check if it is a fatal exception.
	 */
	public void handlePotentiallyFatalException(Exception exception) {
		Throwable throwable = ERXExceptionUtilities.originalThrowable(exception);

		if( _lowMemoryHandler.shouldQuit( throwable ) ) {
			Runtime.getRuntime().exit(1);
		}
	}

	public WOResponse dispatchRequest(WORequest request) {
		WOResponse response;

		if (requestHandlingLog.isDebugEnabled()) {
			requestHandlingLog.debug("{}", request);
		}

		try {
			ERXStats.initStatisticsIfNecessary();
			_lowMemoryHandler.checkMemory();
			response = super.dispatchRequest(request);
		}
		finally {
			ERXStats.logStatisticsForOperation(statsLog, "key");
			ERXThreadStorage.reset();
		}

		if (requestHandlingLog.isDebugEnabled()) {
			requestHandlingLog.debug("Returning, encoding: " + response.contentEncoding() + " response: " + response);
		}

		if( ERXResponseCompression.responseCompressionEnabled() ) {
			if( ERXResponseCompression.shouldCompress( request, response ) ) {
				response = ERXResponseCompression.compressResponse( response );
			}
		}

		return response;
	}

	protected NSMutableArray<String> _streamingRequestHandlerKeys = new NSMutableArray<>(streamActionRequestHandlerKey());

	public void registerStreamingRequestHandlerKey(String s) {
		if (!_streamingRequestHandlerKeys.containsObject(s)) {
			_streamingRequestHandlerKeys.addObject(s);
		}
	}

	public boolean isStreamingRequestHandlerKey(String s) {
		return _streamingRequestHandlerKeys.containsObject(s);
	}

	private static boolean checkIsDevelopmentMode() {
		boolean developmentMode = false;
		if (ERXProperties.stringForKey("er.extensions.ERXApplication.developmentMode") != null) {
			developmentMode = ERXProperties.booleanForKey("er.extensions.ERXApplication.developmentMode");
		}
		else {
			String ide = ERXProperties.stringForKey("WOIDE");
			
			if ("WOLips".equals(ide) || "Xcode".equals(ide)) {
				developmentMode = true;
			}
			
			if (!developmentMode) {
				developmentMode = ERXProperties.booleanForKey("NSProjectBundleEnabled");
			}
		}
		
		return developmentMode;
	}

	/**
	 * @return whether or not the current application is in development mode
	 */
	public static boolean isDevelopmentModeSafe() {

		if( didFinishLaunchingInvoked ) {
			return isDevelopmentModeCached;
		}

		return checkIsDevelopmentMode();
	}

	/**
	 * @return whether or not the current application is in development mode
	 */
	public boolean isDevelopmentMode() {
		return isDevelopmentModeSafe();
	}


	/**
	 * This method is called by ERXWOContext and provides the application a hook to rewrite generated URLs.
	 * 
	 * You can also set "er.extensions.replaceApplicationPath.pattern" to the pattern
	 * to match and "er.extensions.replaceApplicationPath.replace" to the value to replace it with.
	 * 
	 * For example, in Properties: <code>
	 * er.extensions.ERXApplication.replaceApplicationPath.pattern=/cgi-bin/WebObjects/YourApp.woa
	 * er.extensions.ERXApplication.replaceApplicationPath.replace=/yourapp
	 * </code>
	 * 
	 * and in Apache 2.2: <code>
	 * RewriteRule ^/yourapp(.*)$ /cgi-bin/WebObjects/YourApp.woa$1 [PT,L]
	 * </code>
	 * 
	 * or Apache 1.3: <code>
	 * RewriteRule ^/yourapp(.*)$ /cgi-bin/WebObjects/YourApp.woa$1 [P,L]
	 * </code>
	 *
	 * @param url the URL to rewrite
	 * @return the rewritten URL
	 */
	public String _rewriteURL(String url) {
		String processedURL = url;
		if (url != null && _replaceApplicationPathPattern != null && _replaceApplicationPathReplace != null) {
			processedURL = processedURL.replaceFirst(_replaceApplicationPathPattern, _replaceApplicationPathReplace);
		}
		return processedURL;
	}

	/**
	 * This method is called by ERXResourceManager and provides the application a hook to rewrite generated URLs for resources.
	 *
	 * @param url the URL to rewrite
	 * @param bundle the bundle the resource is located in
	 * @return the rewritten URL
	 */
	public String _rewriteResourceURL(String url, WODeployedBundle bundle) {
		return url;
	}

	/**
	 * @return whether or not to rewrite direct connect URLs
	 */
	public boolean rewriteDirectConnectURL() {
		return isDirectConnectEnabled() && !isCachingEnabled() && isDevelopmentMode() && ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.rewriteDirectConnect", false);
	}

	/**
	 * @return The directConnecURL, optionally rewritten.
	 */
	@Override
	public String directConnectURL() {
		String directConnectURL = super.directConnectURL();
		if (rewriteDirectConnectURL()) {
			directConnectURL = _rewriteURL(directConnectURL);
		}
		return directConnectURL;
	}

	/**
	 * Set the application's default encodings
	 */
	public void setDefaultEncoding(String encodingName) {
		log.debug("Setting default encoding to '{}'", encodingName);
		WOMessage.setDefaultEncoding(encodingName);
		WOMessage.setDefaultURLEncoding(encodingName);
	}

	/**
	 * @return A page constructed from the given component class
	 */
	@SuppressWarnings("unchecked")
	public <T extends WOComponent> T pageWithName(Class<T> componentClass, WOContext context) {
		return (T) pageWithName(componentClass.getName(), context);
	}

	/**
	 * @return A page constructed from the given component class
	 * 
	 * Renders in the context provided by ERXWOContext.currentContext()
	 */
	@SuppressWarnings("unchecked")
	public <T extends WOComponent> T pageWithName(Class<T> componentClass) {
		return (T) pageWithName(componentClass.getName(), ERXWOContext.currentContext());
	}

	/**
	 * Returns whether or not DirectConnect SSL should be enabled. If you set
	 * this, please review the DirectConnect SSL section of the ERExtensions
	 * sample Properties file to learn more about how to properly configure it.
	 * 
	 * @return whether or not DirectConnect SSL should be enabled
	 * @property er.extensions.ERXApplication.ssl.enabled
	 */
	public boolean sslEnabled() {
		return ERXProperties.booleanForKey("er.extensions.ERXApplication.ssl.enabled");
	}

	/**
	 * Returns the host name that will be used to bind the SSL socket to (defaults to host()).
	 * 
	 * @return the SSL socket host
	 * @property er.extensions.ERXApplication.ssl.host
	 */
	public String sslHost() {
		String sslHost = _sslHost;
		if (sslHost == null) {
			sslHost = ERXProperties.stringForKeyWithDefault("er.extensions.ERXApplication.ssl.host", host());
		}
		return sslHost;
	}

	/**
	 * Sets an SSL host override.
	 * 
	 * @param sslHost an SSL host override
	 */
	public void _setSslHost(String sslHost) {
		_sslHost = sslHost;
	}

	/**
	 * Returns the SSL port that will be used for DirectConnect SSL (defaults to 443). A value of 0 will cause WO to autogenerate an SSL port number.
	 * 
	 * @return the SSL port that will be used for DirectConnect SSL
	 * @property er.extensions.ERXApplication.ssl.port
	 */
	public int sslPort() {
		int sslPort;
		if (_sslPort != null) {
			sslPort = _sslPort.intValue();
		}
		else {
			sslPort = ERXProperties.intForKeyWithDefault("er.extensions.ERXApplication.ssl.port", 443);
		}
		return sslPort;
	}

	/**
	 * Sets an SSL port override (called back by the ERXSecureAdaptor)
	 * 
	 * @param sslPort an ssl port override
	 */
	public void _setSslPort(int sslPort) {
		_sslPort = sslPort;
	}

	/**
	 * Injects additional adaptors into the WOAdditionalAdaptors setting.
	 * Subclasses can extend this method, but should call super._addAdditionalAdaptors.
	 * 
	 * @param additionalAdaptors the mutable adaptors array
	 */
	protected void _addAdditionalAdaptors(NSMutableArray<NSDictionary<String, Object>> additionalAdaptors) {
		if (sslEnabled()) {
			boolean sslAdaptorConfigured = false;
			for (NSDictionary<String, Object> adaptor : additionalAdaptors) {
				if (ERXSecureDefaultAdaptor.class.getName().equals(adaptor.objectForKey(WOProperties._AdaptorKey))) {
					sslAdaptorConfigured = true;
				}
			}
			ERXSecureDefaultAdaptor.checkSSLConfig();
			if (!sslAdaptorConfigured) {
				NSMutableDictionary<String, Object> sslAdaptor = new NSMutableDictionary<>();
				sslAdaptor.setObjectForKey(ERXSecureDefaultAdaptor.class.getName(), WOProperties._AdaptorKey);
				String sslHost = sslHost();
				if (sslHost != null) {
					sslAdaptor.setObjectForKey(sslHost, WOProperties._HostKey);
				}
				sslAdaptor.setObjectForKey(Integer.valueOf(sslPort()), WOProperties._PortKey);
				additionalAdaptors.addObject(sslAdaptor);
			}
		}
	}

	/**
	 * Returns the additionalAdaptors, but calls _addAdditionalAdaptors to give
	 * the runtime an opportunity to programmatically force adaptors into the list.
	 */
	@Override
	@SuppressWarnings("deprecation")
	public NSArray<NSDictionary<String, Object>> additionalAdaptors() {
		NSArray<NSDictionary<String, Object>> additionalAdaptors = super.additionalAdaptors();
		if (!_initializedAdaptors) {
			NSMutableArray<NSDictionary<String, Object>> mutableAdditionalAdaptors = additionalAdaptors.mutableClone();
			_addAdditionalAdaptors(mutableAdditionalAdaptors);
			_initializedAdaptors = true;
			additionalAdaptors = mutableAdditionalAdaptors;
			setAdditionalAdaptors(mutableAdditionalAdaptors);
		}
		return additionalAdaptors;
	}

	/**
	 * Overridden to check for (and optionally kill) an existing running instance on the same port
	 */
	@Override
	public WOAdaptor adaptorWithName(String aClassName, NSDictionary<String, Object> anArgsDictionary) {
		try {
			return super.adaptorWithName(aClassName, anArgsDictionary);
		}
		catch (NSForwardException e) {
			Throwable rootCause = ERXExceptionUtilities.getMeaningfulThrowable(e);
			if ((rootCause instanceof BindException) && ERXDevelopmentInstanceStopper.stopPreviousDevInstance()) {
				return super.adaptorWithName(aClassName, anArgsDictionary);
			}
			throw e;
		}
	}

	private static void _debugValueForDeclarationNamed(WOComponent component, String verb, String aDeclarationName, String aDeclarationType, String aBindingName, String anAssociationDescription, Object aValue) {
		if (aValue instanceof String) {
			StringBuilder stringbuffer = new StringBuilder(((String) aValue).length() + 2);
			stringbuffer.append('"');
			stringbuffer.append(aValue);
			stringbuffer.append('"');
			aValue = stringbuffer;
		}
		if (aDeclarationName.startsWith("_")) {
			aDeclarationName = "[inline]";
		}

		StringBuilder sb = new StringBuilder();

		String lastComponentName = component.name().replaceFirst(".*\\.", "");
		sb.append(lastComponentName);

		sb.append(verb);

		if (!aDeclarationName.startsWith("_")) {
			sb.append(aDeclarationName);
			sb.append(':');
		}
		sb.append(aDeclarationType);

		sb.append(" { ");
		sb.append(aBindingName);
		sb.append('=');

		String valueStr = aValue != null ? aValue.toString() : "null";
		if (anAssociationDescription.startsWith("class ")) {
			sb.append(valueStr);
			sb.append("; }");
		}
		else {
			sb.append(anAssociationDescription);
			sb.append("; } value ");
			sb.append(valueStr);
		}

		NSLog.debug.appendln(sb.toString());
	}

	/**
	 * The set of component names that have binding debug enabled
	 */
	private NSMutableSet<String> _debugComponents = new NSMutableSet<>();

	/**
	 * Little bit better binding debug output than the original.
	 */
	@Override
	public void logTakeValueForDeclarationNamed(String aDeclarationName, String aDeclarationType, String aBindingName, String anAssociationDescription, Object aValue) {
		WOComponent component = ERXWOContext.currentContext().component();
		if (component.parent() != null) {
			component = component.parent();
		}
		_debugValueForDeclarationNamed(component, " ==> ", aDeclarationName, aDeclarationType, aBindingName, anAssociationDescription, aValue);
	}

	/**
	 * Little bit better binding debug output than the original.
	 */
	@Override
	public void logSetValueForDeclarationNamed(String aDeclarationName, String aDeclarationType, String aBindingName, String anAssociationDescription, Object aValue) {
		WOComponent component = ERXWOContext.currentContext().component();
		if (component.parent() != null) {
			component = component.parent();
		}
		_debugValueForDeclarationNamed(component, " <== ", aDeclarationName, aDeclarationType, aBindingName, anAssociationDescription, aValue);
	}

	/**
	 * Turns on/off binding debugging for the given component. Binding debugging
	 * requires using the BetterTemplates parser and setting bettertemplates.debugSupport=true.
	 * 
	 * @param debugEnabled whether or not to enable debugging
	 * @param componentName the component name to enable debugging for
	 */
	public void setDebugEnabledForComponent(boolean debugEnabled, String componentName) {
		if (debugEnabled) {
			_debugComponents.addObject(componentName);
		}
		else {
			_debugComponents.removeObject(componentName);
		}
	}

	/**
	 * Returns whether or not binding debugging is enabled for the given component
	 * 
	 * @param componentName the component name
	 * @return whether or not binding debugging is enabled for the given component
	 */
	public boolean debugEnabledForComponent(String componentName) {
		return _debugComponents.containsObject(componentName);
	}

	/**
	 * Turns off binding debugging for all components.
	 */
	public void clearDebugEnabledForAllComponents() {
		_debugComponents.removeAllObjects();
	}

	/**
	 * Sends out a ApplicationWillTerminateNotification before actually starting to terminate.
	 */
	@Override
	public void terminate() {
		NSNotificationCenter.defaultCenter().postNotification(ApplicationWillTerminateNotification, this);
		super.terminate();
	}

	/**
	 * Empty array for adaptorExtensions
	 */
	private static String[] EMPTY_STRING_ARRAY = {};

	/**
	 * Override default implementation WHICH returns {".dll", ".exe"} and therefore prohibits IIS as WebServer.
	 */
	@Override
	public String[] adaptorExtensions() {
		return EMPTY_STRING_ARRAY;
	}

	public void addBalancerRouteCookieByNotification(NSNotification notification) {
		if (notification.object() instanceof WOContext) {
			addBalancerRouteCookie((WOContext) notification.object());
		}
	}

	public void addBalancerRouteCookie(WOContext context) {
		if (context != null && context.request() != null && context.response() != null) {
			if (_proxyBalancerRoute == null) {
				_proxyBalancerRoute = (name() + "_" + port().toString()).toLowerCase();
				_proxyBalancerRoute = "." + _proxyBalancerRoute.replace('.', '_');
			}
			if (_proxyBalancerCookieName == null) {
				_proxyBalancerCookieName = ("routeid_" + name()).toLowerCase();
				_proxyBalancerCookieName = _proxyBalancerCookieName.replace('.', '_');
			}
			if (_proxyBalancerCookiePath == null) {
				_proxyBalancerCookiePath = (System.getProperty("FixCookiePath") != null) ? System.getProperty("FixCookiePath") : "/";
			}
			WOCookie cookie = new WOCookie(_proxyBalancerCookieName, _proxyBalancerRoute, _proxyBalancerCookiePath, null, -1, context.request().isSecure(), true);
			cookie.setExpires(null);
			cookie.setSameSite(SameSite.LAX);
			context.response().addCookie(cookie);
		}
	}

	/**
	 * @return Host name used for URL generation when no request is present (for example, in background tasks)
	 */
	public String publicHost() {
		return _publicHost;
	}
	
	/**
	 * On every startup of a WOApplication _PBXProjectWatcher._sendXMLToPB() gets invoked, in an attempt to communicate with ProjectBuilder, an IDE which no longer exists.
	 * Disabling this request shaves about a second of application startup time.
	 */
	private static void disablePBXProjectWatcher() {
		try {
			Field field = com.webobjects._ideservices._PBXProjectWatcher.class.getDeclaredField("_communicationDisabled");
			field.setAccessible(true);
			field.set(null, true);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
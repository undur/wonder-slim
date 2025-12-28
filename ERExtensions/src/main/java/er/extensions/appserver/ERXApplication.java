/*

 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver;

import java.io.File;
import java.lang.reflect.Field;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOAction;
import com.webobjects.appserver.WOAdaptor;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOMessage;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOTimer;
import com.webobjects.appserver._private.WOComponentDefinition;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSTimestamp;
import com.webobjects.woextensions.error.WOExceptionPage;

import er.extensions.ERXExtensions;
import er.extensions.ERXFrameworkPrincipal;
import er.extensions.ERXKVCReflectionHack;
import er.extensions.ERXLoggingSupport;
import er.extensions.ERXMonitorServer;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXExceptionUtilities;
import er.extensions.foundation.ERXPatcher;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;
import er.extensions.resources.ERXAppBasedResourceManager;
import er.extensions.resources.ERXAppBasedResourceRequestHandler;
import er.extensions.resources.ERXResourceManagerBase;
import er.extensions.statistics.ERXStats;
import parsley.Parsley;

/**
 * FIXME: Application/plugin initialization phases // Hugi 2025-10-29
 * 
 * 1. main() :: Collect all ERXPlugin classes
 * 2. main() :: Gather and read Properties from each (in .requires() order)
 * 
 * --- At this point all "raw" properties are loaded so the plugins are ready for real "initializaiton"
 * 
 * 3. main() / ERXPlugin.init() - Construct an instance of each ERXPlugin class and run initialization logic (in .requires() order)
 * 
 * 4. ERXApplication() / ERXPlugin.afterApplicationConstruction() 
 * 5. ?? / ERXApplciation.afterApplicationLaunch()
 */

public abstract class ERXApplication extends ERXAjaxApplication {

	private static final Logger log = LoggerFactory.getLogger(ERXApplication.class);
	private static final Logger requestHandlingLog = LoggerFactory.getLogger("er.extensions.ERXApplication.RequestHandling");
	private static final Logger statsLog = LoggerFactory.getLogger("er.extensions.ERXApplication.Statistics");

	/**
	 * Indicates whether the application is running in development mode
	 */
	private static final boolean _isDevelopmentMode = checkDevelopmentModeEnablingProjectBundle();

	/**
	 * Host name used for URL generation when no request is present (for example, in background tasks)
	 */
	private final String _publicHost;

	/**
	 * Configuration for URL rewriting
	 */
	private final ERXURLRewriter _urlRewriter;

	/**
	 * To support load balancing with mod_proxy
	 */
	private final ERXProxyBalancerConfig _proxyBalancerConfig;

	/**
	 * Watches the state of the application's memory heap and handles low memory situations
	 */
	private final ERXLowMemoryHandler _lowMemoryHandler;

	/**
	 * Keeps track of exceptions logged by handleException()
	 */
	private final ERXExceptionManager _exceptionManager;

	/**
	 * Indicates if ERXApplication.main() has been invoked (so we can check that application actually did so)
	 */
	private static boolean _wasERXApplicationMainInvoked = false;

	/**
	 * Registered streaming request handler keys
	 */
	private final Set<String> _streamingRequestHandlerKeys = new HashSet<>(Set.of(streamActionRequestHandlerKey()));

	/**
	 * Application entry point
	 */
	public static void main(String[] argv, Class applicationClass) {
		_wasERXApplicationMainInvoked = true;

		ERXKVCReflectionHack.enable();
		ERXConfigurationManager.defaultManager().setCommandLineArguments(argv);
		ERXFrameworkPrincipal.setUpFrameworkPrincipalClass(ERXExtensions.class);
		ERXShutdownHook.initERXShutdownHookIfEnabled();

		WOApplication.main(argv, applicationClass);
	}

	public ERXApplication() {

		// FIXME: We need to validate the entire setup of logging at some point // Hugi 2025-06-07
		ERXLoggingSupport.reInitConsoleAppenders();

		// Register and initialize the parsley template parser
		Parsley.register();
		Parsley.showInlineRenderingErrors( isDevelopmentModeSafe() );

		// FIXME: Figure out why this is getting initialized here and document it // Hugi 2025-06-07
		ERXStats.initStatisticsIfNecessary();
		
		fixBaseURLs();

		checkEnvironment();

		ERXPatcher.installPatches();
		setContextClassName(ERXWOContext.class.getName());

		_lowMemoryHandler = new ERXLowMemoryHandler();
		_exceptionManager = new ERXExceptionManager();

		registerRequestHandler(new ERXComponentRequestHandler(), componentRequestHandlerKey());
		registerRequestHandler(new ERXDirectActionRequestHandler(), directActionRequestHandlerKey());
		registerRequestHandler( new ERXAppBasedResourceRequestHandler(), ERXAppBasedResourceRequestHandler.KEY );			

		final String defaultEncoding = System.getProperty("er.extensions.ERXApplication.DefaultEncoding");

		if (defaultEncoding != null) {
			setDefaultEncoding(defaultEncoding);
		}

		// Configure the WOStatistics CLFF logging since it can't be controlled by a property, grrr.
		configureStatisticsLogging();

		_urlRewriter = new ERXURLRewriter(this);

		_publicHost = ERXProperties.stringForKeyWithDefault("er.extensions.ERXApplication.publicHost", host());

		ERXMonitorServer.start();

		activateScheduleOfLifeAndDeath();

		// FIXME: Quick fix for our resource manager's initialization issue. Fix // Hugi 2025-10-06
		if( resourceManager() instanceof ERXResourceManagerBase rmb ) {
			rmb.loadAdditionalContentTypes(); 
		}
		
		_proxyBalancerConfig = new ERXProxyBalancerConfig(name(), port());

		ERXNotification.DidHandleRequestNotification.addObserver(_proxyBalancerConfig::addBalancerRouteCookieByNotification);

		// Adding notification hooks for the application's launch lifecycle
		ERXNotification.ApplicationWillFinishLaunchingNotification.addObserver(this::finishInitialization);
		ERXNotification.ApplicationDidFinishLaunchingNotification.addObserver(this::didFinishLaunching);
		
		ERXNotification.ApplicationDidCreateNotification.postNotification(this);
	}

	/**
	 * Workaround for broken 'WOFrameworksBaseURL' and 'WOApplicationBaseURL' properties in 5.4.
	 * Discussion of the fix can be seen in a webobjects-dev thread from Ricardo on 2009-03-14:
	 * https://lists.apple.com/archives/webobjects-dev/2009/Mar/msg00477.html
	 * 
	 * As of 2025-08-30 I haven't validated whether this is still required.
	 * But since the mail is written after WO's last release, I assume it is // Hugi
	 */
	private void fixBaseURLs() {
		frameworksBaseURL();
		applicationBaseURL();

		if (System.getProperty("WOFrameworksBaseURL") != null) {
			setFrameworksBaseURL(System.getProperty("WOFrameworksBaseURL"));
		}

		if (System.getProperty("WOApplicationBaseURL") != null) {
			setApplicationBaseURL(System.getProperty("WOApplicationBaseURL"));
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
	public void activateScheduleOfLifeAndDeath() {
		int timeToLive = ERXProperties.intForKey("ERTimeToLive");

		if (timeToLive > 0) {
			log.info("Instance will live " + timeToLive + " seconds.");

			// Adds a fudge factor of up to 10 minutes
			timeToLive += Math.random() * 600;

			CompletableFuture.delayedExecutor(timeToLive, TimeUnit.SECONDS ).execute(this::killInstance);
		}

		int timeToDie = ERXProperties.intForKey("ERTimeToDie");

		if (timeToDie > 0) {
			log.info("Instance will not live past " + timeToDie + ":00.");

			final LocalDateTime now = LocalDateTime.now();

			int s = (timeToDie - now.getHour()) * 3600 - now.getMinute() * 60;

			if (s < 0) {
				s += 24 * 3600; // how many seconds to the deadline
			}

			// Randomize so not all instances restart at the same time adding up to 1 hour
			s += (Math.random() * 3600);

			CompletableFuture.delayedExecutor(s, TimeUnit.SECONDS ).execute(this::startRefusingSessions);
		}
	}

	@Override
	public WOResourceManager createResourceManager() {
		return new ERXAppBasedResourceManager();
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

		if (shouldRewriteDirectConnectURL()) {
			url = adaptorPath() + name() + applicationExtension() + url;
		}

		return new ERXRequest(method, url, httpVersion, headers, content, info);
	}

	/**
	 * Called, for example, when refuse new sessions is enabled and the request contains an expired session.
	 * If mod_rewrite is being used we don't want the adaptor prefix being part of the redirect.
	 */
	@Override
	public String _newLocationForRequest(WORequest aRequest) {
		return urlRewriter().rewriteURL(super._newLocationForRequest(aRequest));
	}

	/**
	 * Configures the statistics logging for a given application.
	 * By default will log to a file &lt;base log directory&gt;/&lt;WOApp Name&gt;-&lt;host&gt;-&lt;port&gt;.log if the base log path is defined.
	 * The base log path is defined by the property <code>er.extensions.ERXApplication.StatisticsBaseLogPath</code>.
	 * The default log rotation frequency is 24 hours, but can be changed by setting in milliseconds the property <code>er.extensions.ERXApplication.StatisticsLogRotationFrequency</code>
	 */
	public void configureStatisticsLogging() {
		final String statisticsBasePath = System.getProperty("er.extensions.ERXApplication.StatisticsBaseLogPath");

		if (statisticsBasePath != null) {
			// Defaults to a single day
			final int rotationFrequency = ERXProperties.intForKeyWithDefault("er.extensions.ERXApplication.StatisticsLogRotationFrequency", 24 * 60 * 60 * 1000);
			final String logPath = statisticsBasePath + File.separator + name() + "-" + ERXConfigurationManager.defaultManager().hostName() + "-" + port() + ".log";

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
		ERXNotification.ApplicationDidFinishInitializationNotification.postNotification(this);
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

		// Time since the actual JVM process was started (when the process began, before any class initialization including main() or an app’s static initialization) 
		final long elapsedMSSinceJVMStartup = System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();

		log.info( String.format( "Startup time: %s ms", elapsedMSSinceJVMStartup ) );

		System.out.println( "============= LOADED BUNDLES START =============" );
		System.out.println( String.format( "%-22s : %-65s : %s", "-- Name --", "-- Bundle class --", "-- isJar --" ) );
		for( NSBundle nsBundle : NSBundle._allBundlesReally() ) {
			System.out.println( String.format( "%-22s : %-65s : %s", nsBundle.name(), nsBundle.getClass().getName(), nsBundle.isJar() ) );
		}
		System.out.println( "============= LOADED BUNDLES END ===============" );
	}

	/**
	 * Called when the application posts {@link WOApplication#ApplicationWillFinishLaunchingNotification}.
	 * Override this to perform application initialization.
	 */
	public void finishInitialization() {}

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
	 * Stops the application from handling any new requests. Will still handle requests from existing sessions.
	 */
	public void startRefusingSessions() {
		log.info("Refusing new sessions");
		NSLog.out.appendln("Refusing new sessions");
		refuseNewSessions(true);
	}

	/**
	 * Override to return false if you do not want sessions to be refused when memory is starved.
	 */
	protected boolean refuseSessionsOnStarvedMemory() {
		return true;
	}

	/**
	 * Overridden to add a check for memory starvation
	 */
	@Override
	public boolean isRefusingNewSessions() {
		return super.isRefusingNewSessions() || (refuseSessionsOnStarvedMemory() && _lowMemoryHandler.isMemoryStarved());
	}

	/**
	 * Overridden to fix that direct connect apps can't refuse new sessions.
	 */
	@Override
	public synchronized void refuseNewSessions(boolean shouldRefuseNewSessions) {
		boolean success = false;

		try {
			Field f = WOApplication.class.getDeclaredField("_refusingNewClients");
			f.setAccessible(true);
			f.set(this, shouldRefuseNewSessions);
			success = true;
		}
		catch (SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			log.error("Failed to do some stupid reflection shit", e);
		}

		if (!success) {
			super.refuseNewSessions(shouldRefuseNewSessions);
		}

		// #81712. App will terminate immediately if the right conditions are met.
		if (shouldRefuseNewSessions && (activeSessionsCount() <= minimumActiveSessionsCount())) {
			log.info("Refusing new clients and below min active session threshold, about to terminate...");
			terminate();
		}

		resetKillTimer(isRefusingNewSessions());
	}

	private WOTimer _killTimer;

	/**
	 * Sets the kill timer.
	 */
	private void resetKillTimer(boolean isRefusingNewSessions) {
		// we assume that we changed our mind about killing the instance.
		if (_killTimer != null) {
			_killTimer.invalidate();
			_killTimer = null;
		}

		if (isRefusingNewSessions) {
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
	 * Sends out a ApplicationWillTerminateNotification before actually starting to terminate.
	 */
	@Override
	public void terminate() {
		ERXNotification.ApplicationWillTerminateNotification.postNotification(this);
		super.terminate();
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
	public WOComponentDefinition _componentDefinition(final String componentName, NSArray languages) {

		final boolean fixCachingEnabled = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.fixCachingEnabled", true);

		if (fixCachingEnabled) {
			// _expectedLanguages already contains all the languages in all projects, so there is no need to check for the ones that come in...
			languages = languages != null ? languages.arrayByAddingObjectsFromArray(_expectedLanguages()) : _expectedLanguages();
		}

		return super._componentDefinition(componentName, languages);
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
	 * Overridden to:
	 * 
	 * - Check for (and handle) OutOfMemoryError
	 * - Set an Exception ID that's displayed to the user and logged, making exceptions easier to handle) 
	 * - Log some more information about state before passing the exception to WOApplication to handle
	 * - Set the status of an error response to 500 (WO itself returns 200 which isn't great)  
	 */
	@Override
	public WOResponse handleException(Exception exception, WOContext context) {

		// Get the original throwable
		final Throwable originalThrowable = ERXExceptionUtilities.originalThrowable(exception);

		// Check if we ran out of memory. If so we need to quit ASAP.
		if( _lowMemoryHandler.shouldQuit( originalThrowable ) ) {
			Runtime.getRuntime().exit(1);
		}

		// Generate a unique exception ID for display in logs/exception page
		final String exceptionID = UUID.randomUUID().toString();

		// Store the exception ID with the current thread for display in the exception page
		WOExceptionPage.setExceptionID(exceptionID);

		// Not a fatal exception, business as usual.
		final NSDictionary extraInfo = ERXExceptionManager.Util.extraInformationForExceptionInContext(context);
		final String extraInfoString = NSPropertyListSerialization.stringFromPropertyList(extraInfo);

		log.error( "Exception caught: %s\nexceptionID: %s\nExtra info: %s\n".formatted( originalThrowable.getMessage(), exceptionID, extraInfoString ), exception );
		
		_exceptionManager.log(originalThrowable, LocalDateTime.now(), exceptionID, extraInfo);

		final WOResponse response = super.handleException(exception, context);
		response.setStatus(500);
		return response;
	}

	public ERXExceptionManager exceptionManager() {
		return _exceptionManager;
	}

	public WOResponse dispatchRequest(WORequest request) {
		final WOResponse response;

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
				ERXResponseCompression.compressResponse( response );
			}
		}

		return response;
	}

	public void registerStreamingRequestHandlerKey(String key) {
		_streamingRequestHandlerKeys.add(key);
	}

	public boolean isStreamingRequestHandlerKey(String key) {
		return _streamingRequestHandlerKeys.contains(key);
	}

	/**
	 * @return whether or not the current application is in development mode
	 */
	public static boolean isDevelopmentModeSafe() {
		return _isDevelopmentMode;
	}

	/**
	 * @return whether or not the current application is in development mode
	 */
	public boolean isDevelopmentMode() {
		return _isDevelopmentMode;
	}

	/**
	 * @return The URL rewriter
	 */
	public ERXURLRewriter urlRewriter() {
		return _urlRewriter;
	}

	/**
	 * @return whether or not to rewrite direct connect URLs
	 */
	public boolean shouldRewriteDirectConnectURL() {
		return isDirectConnectEnabled() && !isCachingEnabled() && isDevelopmentMode() && ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.rewriteDirectConnect", false);
	}

	/**
	 * @return The directConnecURL, optionally rewritten.
	 */
	@Override
	public String directConnectURL() {
		final String url = super.directConnectURL();

		if (shouldRewriteDirectConnectURL()) {
			return urlRewriter().rewriteURL(url);
		}

		return url;
	}

	/**
	 * Set the application's default encodings
	 */
	public void setDefaultEncoding(String encodingName) {
		WOMessage.setDefaultEncoding(encodingName);
		WOMessage.setDefaultURLEncoding(encodingName);
	}

	/**
	 * @return A page constructed from the given component class
	 */
	public <T extends WOComponent> T pageWithName(Class<T> componentClass, WOContext context) {
		return (T) pageWithName(componentClass.getName(), context);
	}

	/**
	 * @return A page constructed from the given component class in ERXWOContext.currentContext()
	 */
	public <T extends WOComponent> T pageWithName(Class<T> componentClass) {
		return pageWithName(componentClass, ERXWOContext.currentContext());
	}

	/**
	 * Overridden to check for (and optionally kill) an existing running instance on the same port
	 */
	@Override
	public WOAdaptor adaptorWithName(String adaptorClassName, NSDictionary<String, Object> args) {
		try {
			return super.adaptorWithName(adaptorClassName, args);
		}
		catch (Exception e) {
			final Throwable rootCause = ERXExceptionUtilities.getMeaningfulThrowable(e);

			if (rootCause instanceof BindException && ERXDevelopmentInstanceStopper.stopPreviousDevInstance()) {
				return super.adaptorWithName(adaptorClassName, args);
			}

			throw e;
		}
	}

	/**
	 * Empty array for adaptorExtensions
	 */
	private static final String[] EMPTY_STRING_ARRAY = {};

	/**
	 * Override default implementation WHICH returns {".dll", ".exe"} and therefore prohibits IIS as WebServer.
	 */
	@Override
	public String[] adaptorExtensions() {
		return EMPTY_STRING_ARRAY;
	}

	/**
	 * @return Host name used for URL generation when no request is present (for example, in background tasks)
	 */
	public String publicHost() {
		return _publicHost;
	}
		
	/**
	 * If a build.properties file exists in the current working directory, we're probably doing development. So let's tell the framework by setting NSProjectBundleEnabled=true
	 */
	private static boolean checkDevelopmentModeEnablingProjectBundle() {

		final boolean buildPropertiesExists = Files.exists(Path.of("build.properties"));

		if( buildPropertiesExists ) {
			System.setProperty("NSProjectBundleEnabled", "true");
			logImportantMessage( "build.properties found. Setting development mode. Setting NSProjectBundleEnabled=true" );
		}
		else {
			logImportantMessage( "No build.properties found. Assuming we're in production" );
		}

		return buildPropertiesExists;
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
			logImportantMessage(e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Ensure ERXApplication.main() was invoked when running the application (as opposed to WOApplication.main()) 
	 */
	private void checkERXApplicationMainInvoked() throws Exception {
		if (!_wasERXApplicationMainInvoked ) {
			throw new IllegalStateException( "Your application's main() did not invoke ERXApplication.main() as it should. Did you accidentally invoke WOApplication.main()?" );
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
	 * Log a message that becomes a little more important looking in our logs 
	 */
	private static void logImportantMessage( String message ) {
		IO.println( "=".repeat(message.length() + 6));
		IO.println( "== " + message + " ==" );
		IO.println( "=".repeat(message.length() + 6));
	}
}
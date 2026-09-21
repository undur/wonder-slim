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
import java.util.Collection;
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
import com.webobjects.appserver.WOSession;
import com.webobjects.appserver.WOTimer;
import com.webobjects.appserver._private.WOComponentDefinition;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation._NSUtilities;
import com.webobjects.woextensions.error.WOExceptionPage;

import er.extensions.ERXExtensions;
import er.extensions.ERXFrameworkPrincipal;
import er.extensions.ERXKVCReflectionHack;
import er.extensions.ERXLoggingSupport;
import er.extensions.ERXMonitorServer;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.dev.ERXConsoleCapture;
import er.extensions.dev.ERXConsoleLogRequestHandler;
import er.extensions.dev.ERXDevServerRegistration;
import er.extensions.dev.ERXDevelopmentInstanceStopper;
import er.extensions.dev.ERXEvalRequestHandler;
import er.extensions.dev.ERXRuntimeProblemsRequestHandler;
import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXExceptionUtilities;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;
import er.extensions.resources.ERXAppBasedResourceManager;
import er.extensions.resources.ERXAppBasedResourceRequestHandler;
import er.extensions.resources.ERXResourceManagerBase;
import er.extensions.routes.RouteAction;
import er.extensions.routes.RouteRequestHandler;
import er.extensions.routes.RouteTable;
import er.extensions.statistics.ERXStats;
import parsley.ParsleyConfiguration;

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

	/**
	 * Short URLs: request handler keys as top-level routes. See {@link #shortURLs()}.
	 */
	private final boolean _shortURLs;

	/**
	 * To support load balancing with mod_proxy
	 */
	private final ERXProxyBalancerConfig _proxyBalancerConfig;

	/**
	 * Watches the state of the application's memory heap and handles low memory situations
	 */
	private final ERXLowMemoryHandler _lowMemoryHandler;

	/**
	 * Sheds page cache weight under genuine memory pressure (null when disabled by property)
	 */
	private final ERXPageCachePressureValve _pageCachePressureValve;

	/**
	 * Keeps track of exceptions logged by handleException()
	 */
	private final ERXExceptionManager _exceptionManager;

	/**
	 * Indicates if ERXApplication.main() has been invoked (so we can check that application actually did so)
	 */
	private static boolean _wasERXApplicationMainInvoked = false;

	/**
	 * Request handler keys whose requests may carry a large body that the handler wants to read as a stream
	 * (WORequest.contentInputStream()) rather than have materialized in memory.
	 *
	 * Why this exists: WORequest hands out the content stream only as long as no form value has been read
	 * (its _formValuesUsed guard — form parsing may consume the body). But form values get read before any
	 * request handler or action sees the request: WOContext's constructor calls request.sessionID() (via
	 * _synchronizeForDistribution), and the session id lookup goes through form values, which for a
	 * URL-encoded body means contentString() — the entire body pulled into memory, and the stream gone.
	 * ERXRequest._getSessionIDFromValuesOrCookie() consults this set and, for a registered key, looks the
	 * session id up in cookies only, leaving the body untouched. WO's own streaming key ("ws",
	 * streamActionRequestHandlerKey()) is registered by default; register any handler of your own whose
	 * actions stream their request body. Note that WODirectActionRequestHandler additionally sniffs the
	 * WOSubmitAction form value to resolve the action path — setAllowsContentInputStream(true) on the
	 * handler turns that off; both are needed for a streaming direct action.
	 *
	 * (Found the hard way while making wotaskd's deploy endpoint stream a 40 MB archive, 2026-09-02.)
	 */
	private final Set<String> _streamingRequestHandlerKeys = new HashSet<>(Set.of(streamActionRequestHandlerKey()));

	/**
	 * Application entry point
	 */
	public static void main(String[] argv, Class applicationClass) {
		_wasERXApplicationMainInvoked = true;

		// A console appender from the very first line, so nothing logged during WO's and our own
		// initialization is dropped (log4j's "No appenders could be found" - and, worse, silently lost
		// constructor-time output). The real configuration from the Properties cascade replaces it
		// once the bundles have loaded (ERXExtensions.bundleDidLoad -> configureLoggingWithSystemProperties).
		ERXLoggingSupport.configureDefaultLogging();

		ERXKVCReflectionHack.enable();
		ERXConfigurationManager.defaultManager().setCommandLineArguments(argv);
		ERXFrameworkPrincipal.setUpFrameworkPrincipalClass(ERXExtensions.class);
		ERXShutdownHook.initERXShutdownHookIfEnabled();

		// WO's own debug chatter - WOProperties.printWODefaults() dumping every WO default as
		// "[date] <main> WOxxx=yyy", "Application project found", "Cannot use rapid turnaround" and
		// friends - is emitted through NSLog.debug at the Informational level during WOApplication's
		// own initialization, before our logging is configured. The properties report and the startup
		// banner cover what matters from it. Setting the level here would not stick: WO re-derives it
		// from NSDebugLevel / WODebuggingEnabled inside _initWOApp, just before the dump. So the debug
		// logger installed here clamps whatever level WO later sets on it; ERXLogger carries the
		// clamped level over to the log4j bridge when logging is configured. er.extensions.NSLog.debugLevel
		// (0-3, the NSLog.DebugLevel* values; a -D system property, since this runs before WO reads its
		// own arguments) raises the cap when WO's debug output is wanted.
		final int nsLogDebugCap = Integer.getInteger("er.extensions.NSLog.debugLevel", NSLog.DebugLevelCritical);

		NSLog.setDebug(new NSLog.PrintStreamLogger(System.out) {
			@Override
			public void setAllowedDebugLevel(int level) {
				super.setAllowedDebugLevel(Math.min(level, nsLogDebugCap));
			}
		});
		NSLog.debug.setAllowedDebugLevel(nsLogDebugCap);

		WOApplication.main(argv, applicationClass);
	}

	public ERXApplication() {

		// FIXME: We need to validate the entire setup of logging at some point // Hugi 2025-06-07
		ERXLoggingSupport.reInitConsoleAppenders();

		// Register and initialize the parsley template parser, with development features
		// (inline errors, the controls strip) on in development mode and off in production.
		final ParsleyConfiguration.Builder parsleyConfiguration = isDevelopmentModeSafe()
				? ParsleyConfiguration.defaultDevConfiguration()
				: ParsleyConfiguration.defaultProductionConfiguration();

		parsleyConfiguration.register();

		// FIXME: Figure out why this is getting initialized here and document it // Hugi 2025-06-07
		ERXStats.initStatisticsIfNecessary();

		// RouteAction is a very generic name for a direct action class, so we register it explicitly to prevent problems
		_NSUtilities.setClassForName( RouteAction.class, "RouteAction" );

		fixBaseURLs();

		checkEnvironment();

		setContextClassName(ERXWOContext.class.getName());

		_lowMemoryHandler = new ERXLowMemoryHandler();
		_pageCachePressureValve = ERXPageCachePressureValve.installIfEnabled();
		_exceptionManager = new ERXExceptionManager();

		final ERXAppBasedResourceRequestHandler resourceRequestHandler = new ERXAppBasedResourceRequestHandler();

		if( ERXAppBasedResourceManager.USE_NEW_URLS ) {
			RouteTable.defaultRouteTable().map(ERXAppBasedResourceManager.URL_ROUTE_PREFIX + "*", ri -> resourceRequestHandler.handleRequest( ri.request() ) );
		}

		// The rewritten component-action handler is the default; the patched-stock handler remains
		// available as an opt-out escape hatch while the rewrite earns trust in production. See
		// ERXComponentActionRequestHandler's javadoc.
		if( ERXProperties.booleanForKeyWithDefault( "er.extensions.ERXComponentActionRequestHandler.enabled", true ) ) {
			registerRequestHandler(new ERXComponentActionRequestHandler(), componentRequestHandlerKey());
		}
		else {
			log.info( "Using the legacy ERXComponentRequestHandler for component actions (er.extensions.ERXComponentActionRequestHandler.enabled=false)" );
			registerRequestHandler(new ERXComponentRequestHandler(), componentRequestHandlerKey());
		}
		registerRequestHandler(new ERXDirectActionRequestHandler(), directActionRequestHandlerKey());
		registerRequestHandler( resourceRequestHandler, ERXAppBasedResourceRequestHandler.KEY );

		// Development only: capture recent log output and expose it for reading over HTTP
		// (.../App.woa/log), so external tooling can read the app's logs instead of a
		// human copying the IDE console.
		//
		// Capture attaches a bounded in-memory appender to the logging backend's root
		// logger; it does NOT touch System.out/System.err, deliberately — in this stack
		// NSLog bridges the streams INTO log4j and log4j's ConsoleAppender writes back OUT
		// to System.out, so teeing the streams would sit inside a feedback loop. Capturing
		// at the appender — the single point where all logging converges — sidesteps that.
		// It runs after reInitConsoleAppenders() (above) so the root logger is configured.
		if( isDevelopmentModeSafe() ) {
			ERXConsoleCapture.install();
			registerRequestHandler( new ERXConsoleLogRequestHandler(), ERXConsoleLogRequestHandler.KEY );
			// Dev endpoints shared (in shape and behavior) with ng-objects, backed by ng-core:
			// evaluate a snippet in the running JVM, and read back the runtime problems the app
			// rendered into its pages. See the ng-objects /ng/dev/eval and /ng/dev/problems routes.
			registerRequestHandler( new ERXEvalRequestHandler(), ERXEvalRequestHandler.KEY );
			registerRequestHandler( new ERXRuntimeProblemsRequestHandler(), ERXRuntimeProblemsRequestHandler.KEY );
		}

		// Routes: createRequest() canonicalizes every URL that is not a handler URL to /route/<path>, which is how routes
		// get here. The same handler is the default request handler as well, so that a request which somehow arrives
		// uncanonicalized with an unknown handler key gets the route table's answer rather than WO's component request
		// handler. See ERXShortURLs.canonicalize and RouteRequestHandler.
		final RouteRequestHandler routeRequestHandler = new RouteRequestHandler();
		registerRequestHandler( routeRequestHandler, ERXShortURLs.ROUTE_KEY );
		setDefaultRequestHandler( routeRequestHandler );

		final String defaultEncoding = System.getProperty("er.extensions.ERXApplication.DefaultEncoding");

		if (defaultEncoding != null) {
			setDefaultEncoding(defaultEncoding);
		}

		// Configure the WOStatistics CLFF logging since it can't be controlled by a property, grrr.
		configureStatisticsLogging();

		refuseObsoleteURLRewriterProperties();
		_shortURLs = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.shortURLs", true);

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

		// Every inbound URL is turned into the canonical WO URL for it here, before the request exists: handler-key
		// URLs get the application prefix, everything else becomes /route/<path> under the same prefix. WO then parses
		// a well-formed URL every time, whatever shape the front end delivered (freestyle, adaptor prefix, instance
		// number, or already marked as a route). See ERXShortURLs.canonicalize.
		// The handler keys are read per request rather than cached: handlers may be registered after construction, and the array is small.
		@SuppressWarnings("unchecked")
		final Collection<String> handlerKeys = registeredRequestHandlerKeys();

		url = ERXShortURLs.canonicalize(url, adaptorPath(), name(), applicationExtension(), handlerKeys, RouteTable.defaultRouteTable()::hasRouteFor);

		return new ERXRequest(method, url, httpVersion, headers, content, info);
	}

    /**
     * @returns Request handler used to handle the given request.
     * 
     * Overridden to disable WOStaticResourceRequestHandler being returned for URLs ending with resource-suffixes.
     */
	@Override
    public WORequestHandler handlerForRequest(WORequest request) {
        WORequestHandler requestHandler = requestHandlerForKey(request.requestHandlerKey());
        return requestHandler != null ? requestHandler : defaultRequestHandler();
    }

	/**
	 * The location WO redirects to when it won't serve a request itself — for
	 * example when refusing new sessions and the request carries an expired
	 * session. WOApplication builds it from the request's adaptor prefix and
	 * application name (no extension), i.e. in long form, so it is shortened
	 * and rewritten like every generated URL: otherwise a front end that only
	 * knows the short (or rewritten) form would receive a redirect it can't
	 * route. The prefix removed is the request's own, for the reason given at
	 * {@link ERXShortURLs#applicationPrefix}.
	 */
	@Override
	public String _newLocationForRequest(WORequest aRequest) {
		String location = super._newLocationForRequest(aRequest);

		if (shortURLs() && aRequest != null) {
			location = ERXShortURLs.shorten(location, ERXShortURLs.applicationPrefix(aRequest.adaptorPrefix(), aRequest.applicationName(), ""));
		}

		return location;
	}

	/**
	 * Whether the application accepts and generates short URLs — a request
	 * handler key as the first path segment, no adaptor prefix
	 * ({@code /wa/…} for {@code /cgi-bin/WebObjects/App.woa/wa/…}). The long
	 * form keeps working either way; explicit routes take precedence over the
	 * shortcut. Property: {@code er.extensions.ERXApplication.shortURLs},
	 * default true — the clean form is the default, an application that
	 * must keep generating long URLs opts out. See {@link ERXShortURLs}.
	 *
	 * Why a property and not a front-end rewrite rule: the point is the same
	 * URLs in development and in deployment, so a page's links work whether
	 * the app is hit directly or through a proxy, with nothing to configure
	 * per app on the front end. The property is read in the constructor, next
	 * to the URL rewriter, once the application's properties are loaded.
	 */
	public boolean shortURLs() {
		return _shortURLs;
	}

	/**
	 * @return The prefix every long-form URL of this application starts with,
	 *         {@code /cgi-bin/WebObjects/App.woa} by default: the adaptor path
	 *         (which carries no trailing slash), the application name and
	 *         extension
	 */
	public String applicationURLPrefix() {
		return adaptorPath() + "/" + name() + applicationExtension();
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

		// Logged post-launch so it lands after the configured logging is in place and near the
		// startup banner, where a misconfiguration is actually read.
		warnIfWODisplayExceptionPagesDisabled();

		// Development only: announce our port to the Eclipse dev server so external
		// tooling/agents can discover where this app runs by name (…/apps) rather than
		// guessing. Best-effort and on a background thread — a missing dev server never
		// affects startup. Done here, not in the constructor, because the listening port
		// isn't reliably bound until launch has finished.
		if( isDevelopmentModeSafe() ) {
			ERXDevServerRegistration.registerAtStartup();
		}

		ERXStats.logStatisticsForOperation(statsLog, "sum");

		printStartupInfo();
	}

	/**
	 * Print some useful configuration info at app startup
	 */
	private void printStartupInfo() {
		// Time since the actual JVM process was started (when the process began, before any class initialization including main() or an app’s static initialization) 
		final long elapsedMSSinceJVMStartup = System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();

		log.info( String.format( "Startup time: %s ms", elapsedMSSinceJVMStartup ) );

		System.out.println( "================ LOADED BUNDLES ================" );
		System.out.println( String.format( "%-22s : %-65s : %s", "-- Name --", "-- Bundle class --", "-- isJar --" ) );

		for( NSBundle nsBundle : NSBundle._allBundlesReally() ) {
			System.out.println( String.format( "%-22s : %-65s : %s", nsBundle.name(), nsBundle.getClass().getName(), nsBundle.isJar() ) );
		}

		// The page cache and how it's bounded. Surfaced at launch to make the configuration
		// visible during the page-cache migration; may be removed once that has settled.
		System.out.println();
		System.out.println( "============= CACHE CONFIGURATION ==============" );
		System.out.println( String.format( "%-37s : %s", "unified page cache (instances)", pageCacheSize() ) );
		System.out.println( String.format( "%-37s : %s (obsolete: savePageInPermanentCache throws)", "WO permanent page cache", permanentPageCacheSize() ) );
		System.out.println( String.format( "%-37s : %s (unused: unified cache handles all restores)", "WO page fragment cache", pageFragmentCacheSize() ) );
		System.out.println( String.format( "%-37s : %s", "memory pressure valve", _pageCachePressureValve != null ? _pageCachePressureValve.bannerDescription() : "disabled" ) );

		// Last, and in every mode, because it's what you reach for first in a log: the name this
		// application answers to and where it can be reached. The name is what WOApplication.name()
		// resolved - the -WOApplicationName wotaskd passes for a deployed instance, else the bundle's
		// name - and the bundle name is shown alongside whenever the two differ, since URL generation
		// fills in name() for requests that carry no application name and a mismatch is otherwise
		// invisible until a URL fails to route.
		System.out.println();
		System.out.println( "================= APPLICATION ==================" );

		final String bundleName = NSBundle.mainBundle() != null ? NSBundle.mainBundle().name() : null;

		if( bundleName == null || bundleName.equals( name() ) ) {
			System.out.println( String.format( "%-15s : %s", "name", name() ) );
		}
		else {
			System.out.println( String.format( "%-15s : %s (deployed name; the bundle is %s)", "name", name(), bundleName ) );
		}

		System.out.println( String.format( "%-15s : %s", "pid", ProcessHandle.current().pid() ) );

		if( isDirectConnectEnabled() ) {
			// One URL per line so each is conveniently double clickable. The host is forced to
			// "localhost" because directConnectURL() typically resolves to the machine's mDNS name
			// (e.g. my-macbook.local), which is awkward to connect to from the same box (firewall
			// prompts, mDNS round-trips). WOApplication.port() can be -1 here (it's only set when
			// -WOPort was passed); the bound port lives on the adaptor itself, which is how WO's own
			// directConnectURL() sources it (see WOApplication.directConnectURLForAdaptor).
			final int port = defaultAdaptor().port();
			System.out.println( String.format( "%-15s : http://localhost:%s", "direct connect", port ) );

			// The jetty adaptor binds all interfaces (its connector sets no host), so the app is
			// just as reachable from other devices on the network - a phone on the same Wi-Fi, say.
			// Print those URLs too: IPv4 on interfaces that are up, loopback and link-local excluded
			// (neither is usefully clickable from another device). Address enumeration is a
			// convenience and must never disturb startup.
			try {
				final var interfaces = java.net.NetworkInterface.getNetworkInterfaces();

				while( interfaces.hasMoreElements() ) {
					final var networkInterface = interfaces.nextElement();

					if( !networkInterface.isUp() || networkInterface.isLoopback() ) {
						continue;
					}

					final var addresses = networkInterface.getInetAddresses();

					while( addresses.hasMoreElements() ) {
						final var address = addresses.nextElement();

						if( address instanceof java.net.Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress() ) {
							System.out.println( String.format( "%-15s : http://%s:%s", "", address.getHostAddress(), port ) );
						}
					}
				}
			}
			catch( Exception e ) {
				log.debug( "Could not enumerate network interfaces for the startup banner", e );
			}
		}

		System.out.println( "================================================" );
	}

	/**
	 * Overridden to count page-restore attempts against expired sessions. A component-action or
	 * ajax request whose session ID no longer resolves is a page restore the cache never got to
	 * see - the session timeout broke it upstream - so without this, the reuse statistics
	 * undercount exactly the misses the timeout causes. Counted HERE rather than in
	 * handleSessionRestorationErrorInContext because applications legitimately override that
	 * method for their own expiry UX (and rarely call super); the failed restore itself is the
	 * semantic event, and this is its single choke point. Only page-restoring request handlers
	 * count: a direct action arriving with a dead cookie session recovers invisibly and loses no
	 * page. ("ajax" is AjaxSlim's handler key - referenced literally since the dependency points
	 * the other way; it is the de-facto constant of the wonder lineage.)
	 */
	@Override
	public WOSession restoreSessionWithID( String sessionID, WOContext context ) {
		final WOSession session = super.restoreSessionWithID( sessionID, context );

		if( session == null && sessionID != null && context != null && context.request() != null ) {
			final String handlerKey = context.request().requestHandlerKey();

			if( componentRequestHandlerKey().equals( handlerKey ) || "ajax".equals( handlerKey ) ) {
				er.extensions.appserver.cachemonitor.PageCacheReuseStats.recordExpiredSessionAttempt();
			}
		}

		return session;
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
	 * Route action request errors through our exception handling.
	 *
	 * WOApplication.handleActionRequestError() is an empty extension point (it returns null) that the action
	 * request handlers call when an action throws. We answer it with handleException(), so a thrown direct
	 * action - or a routed action, since our default request handler is action-based - gets the same treatment
	 * as any other exception: exception ID, logging, ERXExceptionManager recording, the OOM check and a 500
	 * exception page. Direct actions are the bulk of most applications' requests, so this path matters.
	 *
	 * WO 5.4.3 would in fact handle a null return from here on its own - _handleRequest falls back to
	 * generateErrorResponse(), which calls handleException() too - BUT only when WODisplayExceptionPages is
	 * true. Answering the hook ourselves makes that gauntlet moot: our error handling runs regardless of the
	 * property, which is why disabling that property to hide stack traces from end users doesn't silently turn
	 * off error handling here (it never did in this lineage). To control what users see, override
	 * handleException() and return a friendly page in production. An explicit WODisplayExceptionPages=false
	 * draws a startup warning (see warnIfWODisplayExceptionPagesDisabled()) precisely because it no longer
	 * does the thing its name implies.
	 *
	 * The cleanup block turns on WHO created the context, which is the whole point of it:
	 *
	 * - Normally the action instance exists and hands us its own context (contextWasMissing == false). That
	 *   context belongs to WO's action request handler, and 5.4.3's _handleRequest sleeps its components and
	 *   checks its session back in inside finally blocks on every path, error paths included. So we must do
	 *   nothing - touching it here would be a double check-in. This is also why the old
	 *   InstantiationError/InvocationError special-casing is gone: it only duplicated that handler cleanup.
	 *
	 * - But when there is no action instance (contextWasMissing == true) there is no context to inherit, so we
	 *   create one for handleException() to render into. That context is a local of ours - it never enters the
	 *   request handler's frame, so the handler's finally blocks never see it and can't sleep its components or
	 *   check its session back in. We are the only holder, so we do that cleanup ourselves. Leaving it dangling
	 *   is exactly what used to strand awake components and (when the request carried a session) leak a session.
	 *
	 * contextWasMissing == true means actionInstance == null: the action class couldn't be found or couldn't be
	 * instantiated (the InstantiationError / ClassNotFound family), NOT an exception thrown from a running
	 * action. In that family saveSessionForContext() is often a near-no-op because a request that failed to
	 * instantiate an action usually carries no session - but "usually" is not "always" (a direct action URL can
	 * carry a session ID and still fail to instantiate), and the component-sleep half runs regardless, so both
	 * lines are load-bearing in precisely the corner that reaches them.
	 */
	@Override
	public WOResponse handleActionRequestError(WORequest aRequest, Exception exception, String reason, WORequestHandler aHandler, String actionClassName, String actionName, Class actionClass, WOAction actionInstance) {
		WOContext context = actionInstance != null ? actionInstance.context() : null;

		final boolean contextWasMissing = context == null;

		if (contextWasMissing) {
			context = createContextForRequest(aRequest);
		}

		final WOResponse response = handleException(exception, context);

		// Only clean up a context WE created; one handed to us by the action belongs to WO's request handler,
		// which checks it back in itself (see the method javadoc).
		if (contextWasMissing) {
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

		// Capture the component hierarchy that was rendering when the exception
		// was thrown, while the originating context is still live. The exception
		// page renders in a fresh request cycle where this context is gone, so
		// we snapshot it here and carry it across via thread storage. The
		// exception is passed too: if it carries a Parsley source location, the
		// failing frame is resolved to a template file + line.
		//
		// We pass the ORIGINAL exception here, not originalThrowable. Parsley
		// attaches its source/binding markers (ParsleySourceLocation /
		// ParsleyBindingLocation) as *suppressed* throwables on the outer wrapper
		// frames (e.g. the NSForwardException produced when a component constructor
		// throws). originalThrowable has been unwrapped to the root cause via
		// getCause(), which discards those outer frames and their suppressed
		// markers — so handing it the root would lose the location. setContextSnapshot
		// walks the cause chain itself (and only reads the throwable for location/
		// binding/phase detection, never for the displayed message or type), so giving
		// it the un-unwrapped exception strictly widens what it can find without
		// affecting anything shown on the page.
		WOExceptionPage.setContextSnapshot(exception, context);

		// Not a fatal exception, business as usual.
		final NSDictionary extraInfo = ERXExceptionManager.Util.extraInformationForExceptionInContext(context);
		final String extraInfoString = ERXExceptionManager.Util.formatExtraInfo(extraInfo);

		log.error( "Exception caught: %s\nexceptionID: %s\nExtra info:\n%s\n".formatted( originalThrowable.getMessage(), exceptionID, extraInfoString ), exception );
		
		_exceptionManager.log(originalThrowable, LocalDateTime.now(), exceptionID, extraInfo);

		final WOResponse response = super.handleException(exception, context);
		response.setStatus(500);
		return response;
	}

	/**
	 * Overridden to give the page-restoration error response a real error status instead of WO's
	 * default 200.
	 * <p>
	 * When a request's context can no longer be restored (the page has aged out of the caches - the
	 * classic "backtracked too far"), WO renders an error page but ships it with a 200 OK. That is fine
	 * for a full-page browser backtrack, but it is actively harmful for an Ajax request: the client
	 * sees a successful response and morphs the error-page HTML straight into the target container. A
	 * non-2xx status is the signal the Ajax client needs to surface the failure to the user (show a
	 * "reload" notice) instead of injecting the error page into the DOM - so we set 500 here, matching
	 * {@link #handleException}. The body is unchanged, so a normal browser backtrack still shows the
	 * same error page; only the status differs.
	 */
	@Override
	public WOResponse handlePageRestorationErrorInContext(WOContext context) {
		final WOResponse response = super.handlePageRestorationErrorInContext(context);
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

	/**
	 * Register a request handler key whose requests should keep their body available as a stream — see
	 * _streamingRequestHandlerKeys for the why.
	 */
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
	 * ERXURLRewriter (a regular expression applied to every generated URL) is gone. It rewrote in one direction only and
	 * never saw the long form it was written to match once short URLs were on. Configuration that still asks for it
	 * stops the launch, rather than being silently ignored.
	 */
	private static void refuseObsoleteURLRewriterProperties() {
		for( final String key : List.of( "er.extensions.ERXApplication.replaceApplicationPath.pattern", "er.extensions.ERXApplication.replaceApplicationPath.replace" ) ) {
			final String value = ERXProperties.stringForKey( key );

			if( value != null && !value.isEmpty() ) {
				throw new IllegalStateException( "The property '" + key + "' is set, but URL rewriting by pattern has been removed. Short URLs (er.extensions.ERXApplication.shortURLs, on by default) remove the adaptor prefix from generated URLs and accept them inbound; remove the replaceApplicationPath properties. Serving an application beneath a path of its own is not supported at present." );
			}
		}
	}
	/**
	 * @return The direct-connect URL — the application's own front door, so
	 *         shortened with short URLs on but never passed through the URL
	 *         rewriter, whose pattern describes a front end's mapping
	 */
	@Override
	public String directConnectURL() {
		final String url = super.directConnectURL();
		return shortURLs() ? ERXShortURLs.shorten(url, applicationURLPrefix()) : url;
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
	 * Warn if someone set WODisplayExceptionPages=false expecting it to hide stack traces from end users.
	 *
	 * In stock WO 5.4.3 that property gates whether action request errors reach handleException() at all
	 * (via WODirectActionRequestHandler.generateErrorResponse()) - so disabling it doesn't merely hide the
	 * exception page, it drops error handling for action requests entirely. In slim it does neither: our
	 * handleActionRequestError() routes those errors to handleException() ourselves, regardless of the
	 * property. So the property is effectively inert here, and setting it false to control what users see
	 * won't have that effect. We don't touch the property or fail startup over it (it's a stock WO knob, not
	 * ours to enforce) - just point out that it isn't doing what its owner likely intends, and where the
	 * intent belongs instead.
	 */
	private static void warnIfWODisplayExceptionPagesDisabled() {
		final String value = NSProperties.getProperty("WODisplayExceptionPages");

		if (value != null && !NSPropertyListSerialization.booleanForString(value)) {
			log.warn("WODisplayExceptionPages is set to '{}', but slim routes action request errors through handleException() regardless, so this property has no effect here. To hide stack traces from end users, override handleException() to return a friendly error page in production.", value);
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
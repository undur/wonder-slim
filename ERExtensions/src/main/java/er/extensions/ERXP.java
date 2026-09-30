package er.extensions;

import er.extensions.foundation.ERXProperties;

/**
 * The configuration parameters ERExtensions reads: one constant per property key, documenting what the property does
 * and its default, grouped by the class that reads it. Code reads a property through its constant
 * ({@code ERXProperties.booleanForKey( ERXP.SHORT_URLS.id() )}), never through a key written out as a string.
 * 
 *  FIXME: Properties should be typed (i.e. a property should know which type it will return and offer suitably typed retrieval methods) // Hugi 2025-10-25
 *  FIXME: Libraries/Frameworks/Apps should be able to declare their own properties // Hugi 2025-10-25
 *  FIXME: Eliminate direct usage of ERXPRoperties in code // Hugi 2025-10-25
 */

public enum ERXP {

	// ERXApplication
	/**
	 * The application's default encoding, applied with {@code setDefaultEncoding()} at startup. Unset: WO's default.
	 */
	DEFAULT_ENCODING( "er.extensions.ERXApplication.DefaultEncoding" ),

	/**
	 * The public host name used for complete URLs generated without a request, such as links in mails sent from background tasks. Defaults to {@code WOHost}.
	 */
	PUBLIC_HOST( "er.extensions.ERXApplication.publicHost" ),

	/**
	 * If true, the request behind a context created without a request ({@code ERXWOContext.newContext()}) is marked as https, so complete URLs it generates use https. Defaults to false.
	 */
	PUBLIC_HOST_IS_SECURE( "er.extensions.ERXApplication.publicHostIsSecure" ),

	/**
	 * A directory for the statistics store's log file, named {@code <app>-<host>-<port>.log}. Unset: no log file.
	 */
	STATISTICS_BASE_LOG_PATH( "er.extensions.ERXApplication.StatisticsBaseLogPath" ),

	/**
	 * How often the statistics log is rotated, in milliseconds. Defaults to 24 hours.
	 */
	STATISTICS_LOG_ROTATION_FREQUENCY( "er.extensions.ERXApplication.StatisticsLogRotationFrequency" ),

	/**
	 * If true, component definitions are looked up with all of the application's languages, so WO doesn't re-read a template from disk for a language it hasn't seen yet (with caching on), and a changed template shows up in every browser (with caching off). Defaults to true.
	 */
	FIX_CACHING_ENABLED( "er.extensions.ERXApplication.fixCachingEnabled" ),

	/**
	 * The highest NSLog debug level (0-3, the {@code NSLog.DebugLevel*} values) let through, capping WO's own debug output at startup. Must be a {@code -D} JVM argument, since it's read before WO reads its arguments. Defaults to {@code NSLog.DebugLevelCritical}.
	 */
	NSLOG_DEBUG_LEVEL( "er.extensions.NSLog.debugLevel" ),

	/**
	 * If true, all of WebObjects' NSLog debug output goes to slf4j, and slf4j's configuration alone decides what's logged, rather than NSLog's debug level (see {@code ERXNSLogBridge}). The key names the bridge's former, log4j-specific class. Defaults to false.
	 */
	NSLOG_IGNORE_SETTINGS( "er.extensions.ERXNSLogLog4jBridge.ignoreNSLogSettings" ),

	// ERXLoggingConfiguration
	/**
	 * A prefix, not a key: {@code er.logging.level.<logger>} sets a logger's level ({@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR}, {@code OFF}) with any logging backend; {@code er.logging.level.root} the root logger's. The backend's own configuration wins where it names the same logger; set on the running instance, it wins over everything. ERExtensions' Properties sets the root to INFO.
	 */
	LOGGING_LEVEL_PREFIX( "er.logging.level." ),

	/**
	 * The layout of the console output a logging backend sets up when its own configuration sets up none, in conversions both backends understand ({@code %d{...} %p %c %m %t %F %L %n}). ERExtensions' Properties sets {@code %d{MMM dd HH:mm:ss} %-5p %c - %m%n}.
	 */
	LOGGING_PATTERN( "er.logging.pattern" ),

	/**
	 * Whether a logged throwable that only wraps another (an {@code NSForwardException}, an {@code InvocationTargetException}) is printed as the throwable it wraps, with any logging backend. Default true.
	 */
	LOGGING_STACK_TRACE_UNWRAP( "er.logging.stackTrace.unwrap" ),

	/**
	 * Whether only the innermost cause of a logged throwable is printed, instead of the whole chain of causes, with any logging backend. Default false.
	 */
	LOGGING_STACK_TRACE_INNERMOST_ONLY( "er.logging.stackTrace.innermostOnly" ),

	/**
	 * Whether runs of stack frames that are plumbing (reflection, the element tree WebObjects walks, the server's threads) are each collapsed into one line in logged stack traces, with any logging backend. Default false.
	 */
	LOGGING_STACK_TRACE_SKIP_FRAMES( "er.logging.stackTrace.skipFrames" ),

	/**
	 * The frames er.logging.stackTrace.skipFrames skips, replacing its built-in list: regular expressions, comma separated, matched against {@code class.method}.
	 */
	LOGGING_STACK_TRACE_SKIP_PATTERNS( "er.logging.stackTrace.skipPatterns" ),

	/**
	 * Seconds the instance lives before it's stopped, plus a random interval of up to 10 minutes. Unset or 0: no limit.
	 */
	TIME_TO_LIVE( "ERTimeToLive" ),

	/**
	 * An hour of the day (0-23) after which the instance starts refusing new sessions, randomised by up to an hour so instances don't all restart together. Unset or 0: never.
	 */
	TIME_TO_DIE( "ERTimeToDie" ),

	/**
	 * Seconds after the instance starts refusing new sessions that it's stopped. Unset or 0: it's never stopped for that reason.
	 */
	TIME_TO_KILL( "ERTimeToKill" ),

	// ERXRoutingApplication
	/**
	 * If true, generated URLs leave out the adaptor prefix ({@code /cgi-bin/WebObjects/App.woa}), and URLs without it are accepted. Defaults to true.
	 */
	SHORT_URLS( "er.extensions.ERXApplication.shortURLs" ),

	// ERXAjaxApplication
	/**
	 * If true, an Ajax action may return the context's page as its result. A debugging aid. Defaults to false.
	 */
	ALLOW_CONTEXT_PAGE_RESPONSE( "er.extensions.ERXAjaxApplication.allowContextPageResponse" ),

	// ERXDevelopmentInstanceStopper
	/**
	 * If true, starting a development instance doesn't stop one already running on the same port. Defaults to false.
	 */
	ALLOW_MULTIPLE_DEV_INSTANCES( "er.extensions.ERXApplication.allowMultipleDevInstances" ),

	// ERXShutdownHook
	/**
	 * If true, ERXShutdownHook is installed so registered hooks run when the JVM shuts down. Must be a {@code -D} JVM argument. Defaults to true.
	 */
	ENABLE_SHUTDOWN_HOOK( "er.extensions.ERXApplication.enableERXShutdownHook" ),

	// ERXLowMemoryHandler
	/**
	 * If true, the instance quits when it hits an OutOfMemoryError. Defaults to true.
	 */
	EXIT_ON_OUT_OF_MEMORY_ERROR( "er.extensions.AppShouldExitOnOutOfMemoryError" ),

	/**
	 * Bytes of memory reserved at startup and released on an OutOfMemoryError, so the application can still react. Defaults to 0, no reserve.
	 */
	LOW_MEMORY_BUFFER_SIZE( "er.extensions.ERXApplication.lowMemBufferSize" ),

	/**
	 * The memory level at which a LowMemory notification is posted: used memory as a fraction of the maximum (0.80), or megabytes of memory left (200). A warning level below {@link #MEMORY_STARVED_THRESHOLD}. Unset: no check.
	 */
	MEMORY_LOW_THRESHOLD( "er.extensions.ERXApplication.memoryLowThreshold" ),

	/**
	 * The memory level at which the instance starts refusing new sessions until memory is available again, and a StarvedMemory notification is posted: used memory as a fraction of the maximum (0.90), or megabytes of memory left (100). Unset: no check.
	 */
	MEMORY_STARVED_THRESHOLD( "er.extensions.ERXApplication.memoryStarvedThreshold" ),

	// ERXPageCachePressureValve
	/**
	 * If true, sessions' page caches are trimmed when old-generation memory stays full after garbage collection. Defaults to true.
	 */
	PAGE_CACHE_PRESSURE_VALVE_ENABLED( "er.extensions.ERXPageCachePressureValve.enabled" ),

	/**
	 * The old-generation fill, as a fraction of its maximum, above which page caches are trimmed to {@link #PAGE_CACHE_PRESSURE_VALVE_TRIM_TO_FRACTION}. Defaults to 0.85.
	 */
	PAGE_CACHE_PRESSURE_VALVE_THRESHOLD( "er.extensions.ERXPageCachePressureValve.threshold" ),

	/**
	 * The old-generation fill above which page caches are trimmed to {@link #PAGE_CACHE_PRESSURE_VALVE_AGGRESSIVE_TRIM_TO_FRACTION}. Defaults to 0.90.
	 */
	PAGE_CACHE_PRESSURE_VALVE_AGGRESSIVE_THRESHOLD( "er.extensions.ERXPageCachePressureValve.aggressiveThreshold" ),

	/**
	 * The fraction of {@code WOPageCacheSize} each session's page cache is trimmed to above the threshold. Defaults to 0.50.
	 */
	PAGE_CACHE_PRESSURE_VALVE_TRIM_TO_FRACTION( "er.extensions.ERXPageCachePressureValve.trimToFraction" ),

	/**
	 * The fraction of {@code WOPageCacheSize} each session's page cache is trimmed to above the aggressive threshold. Defaults to 0.25.
	 */
	PAGE_CACHE_PRESSURE_VALVE_AGGRESSIVE_TRIM_TO_FRACTION( "er.extensions.ERXPageCachePressureValve.aggressiveTrimToFraction" ),

	// ERXAjaxSession
	/**
	 * If true, the page cache's size and structure is logged every time a page is stored. Defaults to false.
	 */
	LOG_PAGE_CACHE( "er.extensions.appserver.ajax.ERXAjaxSession.logPageCache" ),

	/**
	 * If true, the session keeps a dictionary of information per cached page ({@code pageInfoDictionary()}), removed along with the page. Defaults to false.
	 */
	STORES_PAGE_INFO( "er.extensions.appserver.ajax.ERXAjaxSession.storesPageInfo" ),

	// ERXSession
	/**
	 * If true, session and instance cookies are marked Secure. Defaults to false.
	 */
	USE_SECURE_SESSION_COOKIES( "er.extensions.ERXSession.useSecureSessionCookies" ),

	/**
	 * If true, session and instance cookies are marked HttpOnly. Defaults to false.
	 */
	USE_HTTP_ONLY_SESSION_COOKIES( "er.extensions.ERXSession.useHttpOnlySessionCookies" ),

	/**
	 * The SameSite policy of session and instance cookies: NORMAL, LAX or STRICT. Unset: no SameSite attribute.
	 */
	SESSION_COOKIES_SAME_SITE( "er.extensions.ERXSession.cookies.SameSite" ),

	// ERXRequest
	/**
	 * If true, https URL generation is disabled and https URLs become http. For development without TLS. Defaults to false.
	 */
	SECURE_DISABLED( "er.extensions.ERXRequest.secureDisabled" ),

	/**
	 * The value of the forwarded-protocol header that marks a request as secure. Defaults to {@code https}.
	 */
	X_FORWARDED_PROTO_FOR_SSL( "er.extensions.appserver.ERXRequest.xForwardedProtoForSsl" ),

	/**
	 * The header carrying the protocol a front end received the request with. Defaults to {@code x-forwarded-proto}.
	 */
	X_FORWARDED_PROTO_HEADER_KEY_FOR_SSL( "er.extensions.appserver.ERXRequest.xForwardedProtoHeaderKeyForSsl" ),

	// ERXResponseCompression
	/**
	 * If true, responses are gzip-compressed for clients that accept it. Defaults to false.
	 *
	 * FIXME: Rename the property to reflect ERXResponseCompression, the class that reads it // Hugi 2021-05-24
	 */
	RESPONSE_COMPRESSION_ENABLED( "er.extensions.ERXApplication.responseCompressionEnabled" ),

	/**
	 * The content types compressed in addition to every {@code text/*} type, as an array. Parameters such as {@code charset} and case are ignored when matching. Defaults to {@code (application/javascript, application/json, application/xml, image/svg+xml)}.
	 *
	 * FIXME: Rename the property to reflect ERXResponseCompression, the class that reads it // Hugi 2021-05-24
	 */
	RESPONSE_COMPRESSION_TYPES( "er.extensions.ERXApplication.responseCompressionTypes" ),

	// ERXResponseRewriter
	/**
	 * If true, script files added to the head during an Ajax response are loaded on demand in the browser. Defaults to true.
	 */
	LOAD_ON_DEMAND( "er.extensions.loadOnDemand" ),

	/**
	 * If true, on-demand loading also applies to Ajax page replacements. Defaults to false.
	 */
	LOAD_ON_DEMAND_DURING_REPLACE( "er.extensions.loadOnDemandDuringReplace" ),

	/**
	 * If true, {@code type="text/javascript"} is added to injected script tags, as HTML4 and XHTML require. Defaults to false.
	 */
	JAVASCRIPT_TYPE_ATTRIBUTE( "er.extensions.ERXResponseRewriter.javascriptTypeAttribute" ),

	/**
	 * If true, resources added to the head of a non-secure request are loaded with complete https URLs. Defaults to false.
	 */
	SECURE_RESOURCES( "er.ajax.secureResources" ),

	/**
	 * The tag head resources are inserted in front of. Defaults to {@code </head>}.
	 */
	HTML_CLOSE_HEAD( "er.ajax.AJComponent.htmlCloseHead" ),

	/**
	 * A prefix, not a complete key: {@code er.extensions.ERXResponseRewriter.resource.<framework>.<fileName>=<Framework>.<fileName>} replaces a resource added to the head with another, such as a minified or combined script.
	 */
	RESOURCE_REPLACEMENT( "er.extensions.ERXResponseRewriter.resource." ),

	// ERXWOForm
	/**
	 * The default of the {@code multipleSubmit} binding for all forms. Defaults to false.
	 */
	FORM_MULTIPLE_SUBMIT_DEFAULT( "er.extensions.ERXWOForm.multipleSubmitDefault" ),

	/**
	 * The default of the {@code addDefaultSubmitButton} binding for all forms. Defaults to false.
	 */
	FORM_ADD_DEFAULT_SUBMIT_BUTTON_DEFAULT( "er.extensions.ERXWOForm.addDefaultSubmitButtonDefault" ),

	/**
	 * If true, forms render an {@code id} attribute instead of {@code name}. Defaults to false.
	 */
	FORM_USE_ID_INSTEAD_OF_NAME_TAG( "er.extensions.ERXWOForm.useIdInsteadOfNameTag" ),

	// ERXWORepetition
	/**
	 * The default of the {@code checkHashCodes} binding: add items' hash codes to element IDs, so an action finds its item even if the list changed. Defaults to {@link #REPETITION_CHECK_HASH_CODES_BY_CLASS_NAME}.
	 */
	REPETITION_CHECK_HASH_CODES( "er.extensions.ERXWORepetition.checkHashCodes" ),

	/**
	 * The same setting under the class's former full name (from before it moved to {@code er.extensions.components.replacements}), read when {@link #REPETITION_CHECK_HASH_CODES} isn't set. Defaults to false.
	 */
	REPETITION_CHECK_HASH_CODES_BY_CLASS_NAME( "er.extensions.components.ERXWORepetition.checkHashCodes" ),

	/**
	 * The default of the {@code raiseOnUnmatchedObject} binding: throw when an action's item isn't found, rather than using the wrong one. Defaults to {@link #REPETITION_RAISE_ON_UNMATCHED_OBJECT_BY_CLASS_NAME}.
	 */
	REPETITION_RAISE_ON_UNMATCHED_OBJECT( "er.extensions.ERXWORepetition.raiseOnUnmatchedObject" ),

	/**
	 * The same setting under the class's former full name (from before it moved to {@code er.extensions.components.replacements}), read when {@link #REPETITION_RAISE_ON_UNMATCHED_OBJECT} isn't set. Defaults to false.
	 */
	REPETITION_RAISE_ON_UNMATCHED_OBJECT_BY_CLASS_NAME( "er.extensions.components.ERXWORepetition.raiseOnUnmatchedObject" ),

	// ERXWOHyperlink
	/**
	 * If true, hyperlinks with an action bound get {@code rel="nofollow"}. Defaults to false.
	 */
	HYPERLINK_DEFAULT_NO_FOLLOW( "er.extensions.ERXHyperlink.defaultNoFollow" ),

	// ERXNumberFormatter
	/**
	 * Characters removed from input before a number is parsed. Defaults to {@code %$}.
	 */
	NUMBER_FORMATTER_IGNORED_CHARS( "er.extensions.ERXNumberFormatter.ignoredChars" ),

	// ERXStats
	/**
	 * If true, ERXStats collects statistics for each request. Defaults to false.
	 */
	STATS_ENABLED( "er.extensions.erxStats.enabled" ),

	/**
	 * If true, ERXStats also collects stack traces. Defaults to false.
	 */
	STATS_TRACE_COLLECTING_ENABLED( "er.extensions.erxStats.traceCollectingEnabled" ),

	/**
	 * The number of requests' statistics kept. Defaults to 1000.
	 */
	STATS_MAX( "er.extensions.erxStats.max" ),

	// ERXStatisticsStore
	/**
	 * The request duration, in milliseconds, above which a slow request is logged as a warning. Defaults to 2000.
	 */
	STATISTICS_STORE_WARN_MILLIS( "er.extensions.ERXStatisticsStore.milliSeconds.warn" ),

	/**
	 * The request duration above which a slow request is logged as an error. Defaults to 10 seconds.
	 */
	STATISTICS_STORE_ERROR_MILLIS( "er.extensions.ERXStatisticsStore.milliSeconds.error" ),

	/**
	 * The request duration above which a slow request is logged as fatal. Defaults to 5 minutes.
	 */
	STATISTICS_STORE_FATAL_MILLIS( "er.extensions.ERXStatisticsStore.milliSeconds.fatal" ),

	// ERXConfigurationManager
	/**
	 * A file that, when touched, makes the application reload its properties. Unset: no reloading.
	 */
	PROPERTIES_TOUCH_FILE( "er.extensions.ERXConfigurationManager.PropertiesTouchFile" ),

	// ERXProperties
	/**
	 * If true, the application's optional Properties files are read too: {@code Properties.log4j}, {@code .database}, {@code .multilanguage} and {@code .migration}, and {@code Properties.<Framework>} for every framework, each with a {@code .<user>} variant. That's two lookups per framework at every composition of the configuration. Defaults to false.
	 */
	LOAD_OPTIONAL_PROPERTIES( "er.extensions.ERXProperties.loadOptionalProperties" ),

	/**
	 * More Properties files to load, as an array of absolute paths or application resource names.
	 */
	OPTIONAL_CONFIGURATION_FILES( "er.extensions.ERXProperties.OptionalConfigurationFiles" ),

	/**
	 * In development mode, the variant of the application's Properties loaded as developer properties ({@code Properties.<name>}). Defaults to {@code dev}.
	 */
	DEV_PROPERTIES_NAME( "er.extensions.ERXProperties.devPropertiesName" ),

	/**
	 * The directory of machine-wide Properties files, which are looked up as {@code <path>/<AppName>/Properties}. Defaults to {@code /etc/WebObjects}.
	 */
	MACHINE_PROPERTIES_PATH( "er.extensions.ERXProperties.machinePropertiesPath" ),

	// WOHostUtilities
	/**
	 * The addresses treated as the local host, which may make administrative requests (such as wotaskd's), as an array. Unset: all of the server's own addresses.
	 */
	LOCALHOST_IPS( "er.extensions.WOHostUtilities.localhostips" ),

	// WOExceptionPage and ERXDevServerRegistration
	/**
	 * The port of the WOLips or Parslips dev server on localhost, used to open source files from the exception page and to register the application in development. Defaults to 9485.
	 */
	WOLIPS_PORT( "wolips.port" ),

	/**
	 * The WOLips dev server's password, required for opening source files from the exception page.
	 */
	WOLIPS_PASSWORD( "wolips.password" );

	private String _id;

	private ERXP( String id ) {
		_id = id;
	}
	
	/**
	 * @return The Actual name of the property (used in Property files)
	 */
	public String id() {
		return _id;
	}
	
	public String stringValue() {
		return ERXProperties.stringForKey(_id);
	}
}
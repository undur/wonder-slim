# Logging in wonder-slim

Status: reworked in #44. This document records how logging works and the traps it holds. It is
descriptive, not aspirational.

## The stack, as it stands

- The framework logs through **slf4j** (`org.slf4j.Logger`), as does application code.
- A **backend** does the logging behind slf4j, as an `ERXLoggingBackend` found through
  `ServiceLoader` (`META-INF/services/er.extensions.logging.ERXLoggingBackend`); at most one may be on
  the classpath. `ERXLoggingSupport` is the framework's entry point to it, so ERExtensions
  carries no compile dependency on a backend. Two exist:
  - **`ERLoggingReload4j`**: log4j 1.x via reload4j (`ERXReload4jLoggingBackend`). The default.
  - **`ERLoggingLogback`**: logback (`ERXLogbackLoggingBackend`).
- **WebObjects needs no log4j API.** Its form value encoder (`WOCGIFormValues$Encoder`, encoding
  a direct action's query parameters) calls `org.apache.log4j.Logger`, and `ERXApplication.main()`
  rewrites it to call slf4j before WebObjects first uses it (`ERXLog4jRewrite`). An application
  whose own code or libraries call the log4j API brings one: `log4j-over-slf4j` with logback
  (it hands the calls to slf4j), or nothing more with reload4j, which is one. The two can't be on
  the classpath together.
- WebObjects' own `NSLog` output is redirected **into** slf4j, and so the backend, by
  `ERXNSLogBridge` (installed first thing in `ERXApplication.main()`), and log4j's
  `ConsoleAppender` writes back **out** to `System.out`. So `NSLog` → slf4j → log4j → `System.out`
  is a loop that the configuration code has to be careful not to feed twice — this is why,
  for example, `ERXConsoleCapture` attaches at the appender rather than teeing the streams.

## Configuring logging

In layers, lowest first; a later layer wins where two name the same logger
(`ERXLoggingConfiguration`):

1. **Project Wonder style log4j levels** (`log4j.logger.X`, `log4j.rootLogger`,
   `log4j.rootCategory`), for a backend other than reload4j, which reads `log4j.*` itself.
   The rest of log4j's configuration (appenders, layouts) can't be translated; logback names
   those keys in a warning, once.
2. **Keys every backend understands**: `er.logging.level.<logger>` and
   `er.logging.level.root` (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`), and
   `er.logging.pattern`, the layout of the console output a backend sets up when its
   own configuration sets up none.
3. **The backend's own configuration**, which can express what these can't: `log4j.*` for
   reload4j (read as it always has been, through `PropertyConfigurator`), and for logback the
   file `logback.configurationFile` names or `logback.xml` on the classpath. If logback's
   configuration gives the root logger output of its own, the console output from layer 2
   stands aside.
4. **`er.logging.level.*` set on the running instance**
   (`ERXConfigurationManager.setProperty()`, the admin console), which wins over everything.

## Initialization timeline (why order matters)

Roughly, in sequence:

1. `ERXApplication.main()` runs. Its first act is `ERXLoggingSupport.configureDefaultLogging()`:
   the backend's console output at INFO, so logging works from here on. It then installs the NSLog
   bridge (see below).
2. `main()` composes the configuration (`ERXConfigurationManager`, see `CONFIGURATION.md`), and
   straight after, `ERXLoggingSupport.configureAndFollowChanges()` configures logging from it
   (`ERXLoggingBackend.configure()`, in the layers under *Configuring logging*), replacing the
   console output from step 1. From here on, everything logged reaches the configured log: every
   plugin hook, and the whole of the application's construction. Only what's logged while the
   configuration is being composed goes to the console output from step 1. The same call makes
   logging follow the configuration: a change to a logging property (`er.logging.*`,
   `log4j.*`, `logback.configurationFile`), from a watched file or set on the running instance,
   configures logging again (`ERXConfigurationManager.onChange()`).
   Then `WOApplication.main()` constructs the application. WebObjects' constructor redirects
   `System.out`/`System.err` to the `WOOutputPath` file when it's set; at the top of
   `ERXApplication`'s constructor, `ERXLoggingSupport.reInitConsoleAppenders()` has reload4j's
   console appenders follow (logback's console output follows by itself).
3. The **application constructor** runs (`ERXApplication()`), which does the bulk of
   framework setup: request handler registration, cache config, environment checks, etc.
4. `ApplicationWillFinishLaunching` → `finishInitialization()`, then
   `ApplicationDidFinishLaunching` → `didFinishLaunching()`. The startup banner, "Startup
   time" line, and dev-server registration happen in this post-launch phase.

## Fixed trap: `log.*` before the Properties cascade was loaded used to be dropped

**Observed (2026-09-05):** an slf4j `log.warn(...)` from within the `ERXApplication` constructor
produced no output — neither in the app's captured log nor on the raw console — while a
`System.err.println` on the line above did, and the identical call from `didFinishLaunching()`
worked.

**Root cause (found 2026-09-15):** log4j was only configured on `AllBundlesLoadedNotification`
(`ERXExtensions.bundleDidLoad` → `configureLoggingWithSystemProperties`), which fires *during* the
WOApplication constructor. Anything logged before that point — WO's own initialization, framework
principals, Parsley's registration, the first lines of the application constructor — hit a root
logger with no appenders: log4j printed its "No appenders could be found for logger (...)" warning
once and dropped every event. `isWarnEnabled()` reading true was a level check, not an appender
check, which is what made it look like a mystery.

**Fix:** `ERXApplication.main()` now installs a plain console appender (INFO) as its very first act
(`ERXLoggingSupport.configureDefaultLogging()`), before WO starts. Everything logs from the first
line; `configureLogging()` later resets and replaces it with the appenders from the Properties
cascade. The old workaround — logging startup messages from `didFinishLaunching()` — is no longer
needed for correctness, though the startup banner stays there because that is where it reads best.

## Startup output conventions

Startup information that is a report rather than an event — the properties cascade, loaded
bundles, cache configuration, the application section with name, pid and direct connect URLs —
is printed as `System.out` banners (`=== TITLE ===` blocks) rather than as log lines, so it
reads as a document regardless of the configured pattern layout. Ordering: the application
section is deliberately last, since it is what one reaches for first.

WO's own `NSLog.debug` chatter (the WOProperties dump, "Application project found", "Waiting for
requests...") is capped at `NSLog.DebugLevelCritical` by the NSLog bridge (`ERXNSLogBridge`), installed in
`main()`; `-Der.extensions.NSLog.debugLevel=2` (or `3`) restores it. The properties report masks
any key that looks like a secret (`ERXProperties.isSecretKey`) and shows only keys the Properties
files and the command line set, not the whole of `System.getProperties()`.

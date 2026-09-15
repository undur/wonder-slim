# Logging in wonder-slim

Status: **the logging setup is known-messy and slated for a rethink.** This document
records how it currently works and the traps it holds, so the eventual cleanup starts
from facts rather than archaeology. It is descriptive, not aspirational.

## The stack, as it stands

- The framework logs through **slf4j** (`org.slf4j.Logger`), as does application code.
- The backend is **log4j 1.x via reload4j**, wired up in the `ERLoggingReload4j` module.
- `er.extensions.ERXLoggingSupport` is a thin reflective bridge from ERExtensions into
  that backend (`er.extensions.logging.ERXTemporaryLoggingBridge` — the class's own
  javadoc calls it "Temporary bridge until we work out a nicer method of initializing
  logging"), so ERExtensions carries no compile dependency on the backend.
- WebObjects' own `NSLog` output is redirected **into** log4j by
  `ERXNSLogLog4jBridge` (installed in `ERXLogger.configureLogging`), and log4j's
  `ConsoleAppender` writes back **out** to `System.out`. So `NSLog` → log4j → `System.out`
  is a loop that the configuration code has to be careful not to feed twice — this is why,
  for example, `ERXConsoleCapture` attaches at the appender rather than teeing the streams.

## Initialization timeline (why order matters)

Roughly, in sequence:

1. `ERXApplication.main()` runs. Its first act is `ERXLoggingSupport.configureDefaultLogging()`:
   a plain console appender at INFO on the root logger, so logging works from here on. It then
   installs the clamping `NSLog.debug` logger (see below) and hands over to `WOApplication.main()`.
2. WO loads the bundles. On `AllBundlesLoadedNotification`, `ERXExtensions.bundleDidLoad` runs
   `ERXConfigurationManager.initialize()` and then
   `ERXLogger.configureLoggingWithSystemProperties()`: `LogManager.resetConfiguration()`,
   `BasicConfigurator.configure()`, install the `NSLog` bridge, then
   `PropertyConfigurator.configure(properties)` from the merged Properties cascade. If the
   properties yield no appenders it falls back to a default `ConsoleAppender` on `System.out`.
   This replaces the appender from step 1. At the top of the application constructor,
   `ERXLoggingSupport.reInitConsoleAppenders()` calls `activateOptions()` on the console
   appender(s) "so we get logging into `WOOutputPath` again."
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
requests...") is capped at `NSLog.DebugLevelCritical` by a clamping debug logger installed in
`main()`; `-Der.extensions.NSLog.debugLevel=2` (or `3`) restores it. The properties report masks
any key that looks like a secret (`ERXProperties.isSecretKey`) and shows only keys the Properties
files and the command line set, not the whole of `System.getProperties()`.

## For the eventual cleanup

Open threads worth folding into a proper logging story:

- Kill the "temporary" reflective bridge (`ERXTemporaryLoggingBridge`) once initialization
  has a real home.
- Decide whether `NSLog` still needs to be bridged into log4j at all, or whether WO's
  stream usage can be handled more directly.
- The default fallback appender pattern and the property-driven pattern differ; unify.

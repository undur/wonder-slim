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

1. `ERXApplication.main()` runs. During plugin init (`ERXPlugin.init()`),
   `ERXLogger.configureLoggingWithSystemProperties()` configures log4j from the merged
   Properties: `BasicConfigurator.configure()`, install the `NSLog` bridge, then
   `PropertyConfigurator.configure(properties)`. If the properties yield no appenders it
   falls back to a default `ConsoleAppender` on `System.out`.
2. Still in `main()`, `ERXLoggingSupport.reInitConsoleAppenders()` calls
   `activateOptions()` on the existing `ConsoleAppender`(s) "so we get logging into
   `WOOutputPath` again."
3. The **application constructor** runs (`ERXApplication()`), which does the bulk of
   framework setup: request handler registration, cache config, environment checks, etc.
4. `ApplicationWillFinishLaunching` → `finishInitialization()`, then
   `ApplicationDidFinishLaunching` → `didFinishLaunching()`. The startup banner, "Startup
   time" line, and dev-server registration happen in this post-launch phase.

## Trap: `log.*` from the application constructor is silently dropped

**Observed, reproducible (2026-09-05):** an slf4j `log.warn(...)` called from within the
`ERXApplication` constructor produces **no output** — not to the app's captured log, and
not to the raw Eclipse/stdout console — even though, at that same point:

- the logger's `isWarnEnabled()` already returns `true`, and
- a plain `System.err.println(...)` on the line immediately above **does** reach the
  console, and
- the identical `log.warn(...)` call routed through the running app's `/eval` endpoint
  (i.e. after launch) works and appears in the console.

The same call works fine when moved to `didFinishLaunching()`. That is the current
workaround: **emit startup-time log messages from the post-launch lifecycle hooks
(`finishInitialization` / `didFinishLaunching`), not from the constructor.** Messages that
truly must appear during construction use `System.out`/`System.err` directly — which is
what the startup banner and `logImportantMessage()` already do.

Root cause is **not yet pinned down**. The appender exists and is configured before the
constructor runs (step 1 above), so "no appender yet" is not it; `isWarnEnabled()` reading
true rules out a level threshold. The leading suspect is the `NSLog`↔log4j bridge loop
and/or an appender target/stream state that isn't fully live until launch completes, but
this has not been isolated. Whoever does the logging rethink should either fix this so
constructor logging is reliable, or make the failure loud instead of silent.

First live sighting: `ERXApplication.warnIfWODisplayExceptionPagesDisabled()`, which had
to be moved out of the constructor's `checkEnvironment()` into `didFinishLaunching()` for
its warning to appear at all.

## For the eventual cleanup

Open threads worth folding into a proper logging story:

- Kill the "temporary" reflective bridge (`ERXTemporaryLoggingBridge`) once initialization
  has a real home.
- Decide whether `NSLog` still needs to be bridged into log4j at all, or whether WO's
  stream usage can be handled more directly.
- Fix or diagnose the constructor-logging drop above rather than routing around it.
- The default fallback appender pattern and the property-driven pattern differ; unify.

# Changelog

## 2026-09-04 (8.0.4)

- **Page cache reuse measurement**
  Every cache hit records the restored instance's idle time and instance-LRU depth into app-wide
  histograms, shown on the session cache overview with a reverse-cumulative "broken by bound" column
  that reads directly as "a TTL/cap at this bound would have broken N restores". Restores broken by
  SESSION EXPIRY - invisible to in-cache counters, since the session dies before any cache is
  consulted - are counted at the `restoreSessionWithID` choke point (deliberately not in
  `handleSessionRestorationErrorInContext`, which applications override for their own expiry UX).
  A notable-reaches list names the pages users actually reach back for.

- **ERXPageCachePressureValve: caches trim themselves under real memory pressure**
  Instead of an always-on TTL (paying with broken deep reaches even when memory is plentiful),
  eviction now happens only when memory is actually short: when a GC completes with old gen still
  above `threshold` (default 85%), every session is trimmed to `trimToFraction` (default 50%) of the
  page cache cap, LRU instances first; above `aggressiveThreshold` (default 90%), to
  `aggressiveTrimToFraction` (default 25%). A session is never trimmed below its most recently used
  instance. Push-based (the old-gen pool's collection-usage notification - no polling, no forced
  GCs), throttled, logged loudly, surfaced on the overview page and in the startup banner. The valve
  also subscribes to ERXLowMemoryHandler's LowMemory/StarvedMemory notifications. Opt out:
  `er.extensions.ERXPageCachePressureValve.enabled=false`. Underneath, cache accesses now
  synchronize on the cache map, properly fixing the overview page's cross-session iteration race.

- **AjaxSlim: AjaxExpansion**
  The disclosure-section element, rebuilt on native `<details>/<summary>`: four bindings (label,
  expanded, id, class), instant client-side toggling, and open state that survives morphs of
  enclosing containers - every toggle pings the server, so re-renders always carry the correct
  open attribute. The legacy effect bindings and lazy content loading are gone.

- **AjaxSlim: modal light dismiss + pre-adoption hardening**
  AjaxModalContainer closes on a backdrop click (clicks inside the dialog's box, and drags that
  merely end outside, never dismiss). The pre-adoption review's fix list landed: an open modal
  survives morphs of enclosing regions (data-morph-ignore while open); a full-document ajax
  response - the expired-session shape - is diverted to the error contract instead of being
  swallowed or morphed into a container; AjaxSelfUpdatingContainer.action no longer fires when the
  container is merely another trigger's render target; field observers no longer fire into a real
  form submission; quoting and content-policy fixes throughout. Focus is preserved when tabbing out
  of a field that triggers a morph, including multi-container updates.

- **Development mode: /eval and /problems endpoints**
  A JShell REPL inside the running app (`…/App.woa/eval?snippet=…`, loopback-only) and the rendered
  binding-error boxes as JSON (`…/App.woa/problems`), aligned with ng-objects' dev endpoints. The
  dev-loop machinery is consolidated under `er.extensions.dev`; apps report runtime and pid when
  registering with the dev server; the launch banner prints external direct-connect URLs.

- **Release dependency hygiene**
  Parsley pinned to the released 1.6.0; the ng-core compile dependency severed by temporarily
  vendoring four small dev-mode classes into `er.extensions.dev.ng` (each marked with its deletion
  condition: ng-core >= 0.1.2 released and parsley re-pinned). The released artifacts depend only on
  published artifacts.

- **Elements & housekeeping**
  Full `.apiext` adoption across AjaxSlim (typed constraints, defaults, deprecations,
  unknown-attribute and content policies); the empty-update 500 explains itself; experimental
  SVG-aware ERXWOImage (opt-in); ERXWOTextField cleanups; RouteTable names its unhandled-response
  userInfo key; json 20260719, junit 6.1.3, vermilingua 1.1.7.


## 2026-07-03

- **New default: ERXComponentActionRequestHandler - component-action dispatch rewritten as owned code**
  A from-requirements rewrite of the /wo/ handler, replacing the patched-stock ERXComponentRequestHandler
  as the DEFAULT. Same behavior, verified by the full playground suite, the cache/replay/expired-session
  harnesses, and a differential URL-matrix probe (dispatch-differential.mjs: happy paths plus every
  malformed-URL/expired-session edge, run against both handlers and diffed - one intended divergence),
  with deliberate exceptions: a malformed action URL (no contextID)
  gets a clean page-restoration error instead of the old handler's exception page; awake/sleep and session
  check-in are correctly paired on all paths including exceptions; errors log through slf4j; the
  page-recreation branch (WO's cacheless component-action mode, pageCacheSize=0) is gone - that mode has
  been dead in this lineage since named-page parsing was removed, in both handlers alike. The complete
  divergence list is in the class javadoc. Escape hatch back to the legacy handler:
  `er.extensions.ERXComponentActionRequestHandler.enabled=false`.

- **Unified the page cache: ERXAjaxSession's cache is now THE page cache, WO's private caches are never fed**
  One session-side `contextID -> live page instance` map now serves every restore — back button, component
  actions and ajax updates alike. `savePage` never calls super, so the storage routing decision (which cache
  does this page go in) is gone: an ajax update is just another alias for the same instance and can never
  evict the foreground page. Bounded by distinct page instances via WO's own knob, `WOPageCacheSize` /
  `WOApplication.pageCacheSize()` (note: that now counts retained live page trees, not backtrack steps —
  prefer small values). The repeated-request guard WO's cache used to provide moved with it: entries
  record request provenance and `ERXComponentRequestHandler` consults
  `ERXAjaxSession.contextIDForRepeatedRequest` instead of `_contextIDMatchingIDs` — gated on
  `isPageRefreshOnBacktrackEnabled()` exactly like stock WO (flag off = an identical re-submit
  re-executes the action, as always; flag on = the repeat re-renders the stored result, because that
  mode makes the browser re-issue requests during history navigation). The frames-era permanent page
  cache is obsolete and fails loudly: its machinery is deleted (`er.extensions.overridePrivateCache`
  and the storage itself, which had a leak in its dormant path) and `savePageInPermanentCache` now
  throws `UnsupportedOperationException` — instance-LRU eviction removed the context churn that
  permanent pages needed protection from, and silently downgrading a pinning request to LRU semantics
  would be a behavior change with no error. Also deleted the dead `original_context_id` header write.
  `er.extensions.maxPageReplacementCacheSize` is no longer used (startup WARN if set);
  per-store cache logging renamed to `er.extensions.appserver.ajax.ERXAjaxSession.logPageCache`.
  See `docs/UNIFIED_PAGE_CACHE.md`.

---

*Everything below this line is a retroactive reconstruction. wonder-slim had no releases before 8.0.0 —
the 0.x versions were assigned to the natural waves in the commit history, long after the fact
(in September 2026). The dates and changes are real; only the version numbers are invented.
A few sections absorb changelog notes that were actually written at the time.*

---

## 2026-03-13 (0.25.0)

- **Routing handles URLs at the application root**
  Routed URLs no longer need a request-handler prefix: RouteRequestHandler dispatches them straight
  from the app root, with query strings stripped before matching and lenient suffix handling for
  generic URLs.
- **Routing API tightened**
  RouteAction now extends WODirectAction, ComponentClassRouteHandler became a record,
  BiFunctionRouteHandler was deleted, and the deprecated `RouteTable.urlForDevelopment()` was
  removed before it could become public API.
- **Resource URLs without a context now throw**
  Generating a resource URL with no WOContext used to fail quietly and wrongly; now it's an
  IllegalStateException.
- **`type` can be overridden on ERXWOTextField / `wo:textfield`**
- **Documented reality: targets JDK 25, runs fine up to and including JDK 26**

## 2025-12-28 (0.24.0)

- **Deleted the SSL direct-connect adaptor stack**
  ERXDefaultAdaptor and ERXSecureDefaultAdaptor are gone. TLS is the web server's job.
- **Experimental pure-Java HTTP adaptor**
  WOAdaptorPlain, built on the JDK's own HttpServer: streams request content properly, guards
  against chunked transfer encoding, populates remote/local addresses on the WORequest and reports
  the actually-bound port back into WOPort. Additive and self-contained — the classic adaptor
  remains the default.
- **Startup measured and slimmed**
  The app now reports time from JVM launch to first request accepted. `loadOptionalProperties` is
  off by default. WOTimer usage replaced with `CompletableFuture.delayedExecutor()`.
- **Added ERXErrorPage; direct component access disabled for good**
  Everything goes through the component request handler now.
- **Parsley 1.3.0** (off snapshot)

## 2025-10-31 (0.23.0)

- **Deleted ERXResourceManager and ERXStaticResourceRequestHandler**
  The single largest deletion of the era (−714 lines). The application-served resource pipeline
  introduced in 0.20.0 is now the only path.
- **JDK 25**
  Wanted: writing code before invoking the super constructor. All modules migrated.
- **ERXApplication sheds its config**
  Proxy-balancer support extracted to ERXProxyBalancerConfig, URL rewriting to ERXURLRewriteConfig;
  `installPatches()` and the binding-debugging extensions deleted; ERXPrivateer renamed to
  ERXPrivateKVC and moved to a `.hacks` package where it belongs.
- **Started the ERXP enum**
  One place for every property key the framework reads, instead of strings scattered everywhere.

## 2025-10-24 (0.22.0)

- **The great core cleanout**
  Nine days, roughly ninety commits, almost all of them removing methods from ERXApplication,
  ERXWOContext and ERXRequest: `userInfo()`/`mutableUserInfo()`, the browser form-value-encoding
  override machinery, `instantiatePage()`, `rawName()`, ERXRetainer, ERXDate and a long tail of
  accreted conveniences. This is where the "slim" gets earned.
- **Lambdas as notification listeners**
  Observer registration takes a lambda, retains it, and does so thread-safely.
- **Initialization made comprehensible**
  `ERXApp.setup()` merged into `main()`; ERXFrameworkPrincipal initialization reworked.

## 2025-10-15 (0.21.0)

- **URL routing**
  New `er.extensions.routes` package: RouteTable, RouteAction, RouteURL, RouteHandler — and the
  project's first unit tests.
- **Typed notifications**
  ERXNotification replaces string-keyed NSNotificationCenter usage with an enum and nicer syntax
  for registering observers and posting.
- **JDK 21 formally required** via maven-enforcer; **Parsley moves to the released 1.2.0**.
- **Deleted AjaxRemoteLogging and ERXAppRunner**

## 2025-10-07 (0.20.0)

- **The application serves its own webserver resources**
  Instead of relying on an external web server for /WebServerResources: an in-memory resource
  cache, client-side cache headers in production, corrected mimetypes (`.js` is `text/javascript`,
  real font types) and user-defined types via `AdditionalMimeTypes.plist`. On by default.
- **Deleted the resource version manager and `isDeployedAsServlet()`**
- **vermilingua 1.0.5**

## 2025-09-25 (0.19.0)

- **ERXExceptionManager**
  The application keeps a log of thrown exceptions, browsable on the new
  ERXExceptionManagementPage: stack traces, most-recent-first, filterable.
- **ERXComponentRequestHandler no longer creates sessions**
  A component action URL with no session gets an error, not a fresh session.
- **Java arrays work in `list` bindings**
  For WOPopUpButton and every other WOInputList-based element — and unknown bound types now throw
  instead of silently rendering an empty popup.
- **Build modernization**
  JDK version via `<maven.compiler.release>` (21), Maven ≥ 3.9 enforced, UTF-8 encoding pinned
  across all modules, vermilingua on the released 1.0.4.
- **Deleted WOMethodInvocation**; README rewritten to describe the current shape of the project.

## 2025-07-26 (0.18.0)

- **Parsley replaces the in-tree template parser**
  The `er.extensions.bettertemplates` package — the WOOgnl parser absorbed back in 0.8.0, seventeen
  WOHelperFunction classes, 2,400 lines — deleted, supplanted by the external Parsley template
  engine. Template parsing is no longer this framework's problem.

## 2025-06-27 (0.17.0)

- **ERXPatcher's XHTML machinery torn down**
  `processResponse()`, `cleanupXHTML()` and the response-rewriting layer deleted; the surviving
  element patches (ERXWOForm, ERXWORepetition, ERXWOString, ERXWOTextField) promoted to real
  classes and always enabled, localization or not.
- **Deleted ERXWOBrowser** (370 lines).
- **ERXWORepetition thinks in Lists**
  Internal `Context` renamed to `ListWrapper`, NSArray special-casing dropped.
- **A KVC reflection hack for modern JDKs**
  ERXKVCReflectionHack — self-describedly "absolutely horrifying" — keeps key-value coding working
  against Java's ever-more-private internals. Enabled globally.

## 2025-06-08 (0.16.0)

- **Run development mode straight from a Maven project**
  A `build.properties` file marks a project as under development; `src/main/woresources/Properties`
  is found where Maven puts it; NSProjectBundleEnabled set automatically.
- **Deleted ERXLoader**
  845 lines of classpath munging, refactored for a week, then proved unnecessary and deleted
  outright ("YOLO"). Replaced by a 49-line classpath validation at startup that fails loudly when
  the order is actually wrong.
- **Added the ERXDate element**; ERXApplication's constructor restructured into something readable.

## 2025-04-28 (0.15.0)

- **JDK 24 readiness**
  `sun.security.action.GetPropertyAction` was removed from the JDK, so WebObjects' internals need a
  stand-in: vendored.
- **Added ERXSwitchComponentInstance**
  An experimental switch component taking component instances rather than names.
- **Small things**: the development-mode flag is cached, WOHostUtilities obtains localhost IPs
  automatically, slf4j 2.0.17, reload4j 1.2.26.

## 2024-12-20 (0.14.0)

- **ERXMonitorServer**
  A small HTTP monitoring service wired into ERXApplication, gated on a
  `WOMonitorServicePassword` property.
- **The old template parser becomes opt-out**
  Better-templates initialization moved behind a `useBetterTemplates()` override instead of an
  unconditional call — the first step toward its removal in 0.18.0.
- **Assorted**: statistics package modernized, exception pages show class names in stack traces,
  `SameSite=Lax` on the proxy-balancer route cookie, development apps are terminated by port and
  given an honest amount of time to die.

## 2024-05-02 (0.13.0)

- **Exception IDs**
  Every exception gets an ID, surfaced on WOExceptionPage — grep the log for the ID a user reports.
- **Reproducible build**: vermilingua-maven-plugin moves off SNAPSHOT.
- README corrected to match reality; slf4j bumps.

## 2023-09-29 (0.12.0)

- **JDK 21**
  Source/target 17 → 21 across all modules; first JDK-21-era API usage (`Locale.of()`).

## 2023-07-22 (0.11.0)

- **Deleted the legacy jar checker**
  ERXJarChecker — classpath scanning from another age — extracted, examined, and removed.
  ERXLoader trimmed and de-instanced in the process.
- **First seed of a Prototype-free Ajax**
  A plain-JavaScript field observation experiment lands in AjaxSlim's webserver resources.

## 2023-04-21 (0.10.1)

- **Stack traces name their JARs**
  WOExceptionPage identifies which JAR a class came from when it isn't inside a bundle, and
  presents Maven bundles more readably. slf4j and reload4j bumps.

## 2022-12-20 (0.10.0)

- **AjaxSlim is born**
  The Ajax framework forked wholesale into a new AjaxSlim module (both remain in the build), and
  then the fork put on a diet: some 35 components deleted in a single day — AjaxAutoComplete,
  AjaxTree, AjaxTabbedPanel, AjaxSlider, AjaxDatePicker, the file uploads, drag and drop, progress
  bars, rico.js — 228 files and 21,600 lines gone.
- **The first Prototype-free update container**
  A proof-of-concept AjaxUpdateLink/AjaxUpdateContainer on plain asynchronous XMLHttpRequest.
  Parked for now ("Don't use the new scripts"), but the direction is set.
- **AjaxComponent deleted**; AjaxUtils gutted of everything ERXResponseRewriter already does.
- **Nicer URLs for webserver resources inside JARs** in development mode.

## 2022-10-15 (0.9.0)

- **Deleted wondaculous.js**
  13,381 lines of concatenated JavaScript, gone in one commit — the first real cut into Ajax's
  script payload.
- **Publishing plumbing**
  The WOCommunity Maven repository configured and hoisted to the parent pom.
- **Logging setup moved out of ERXApplication** into ERXLoggingSupport.
- **Deleted the testapp** — the repo is frameworks only now.
- **NSArray/NSDictionary begin their walk to List/Map**, starting with WOExceptionPage.
- **Error pages link into the IDE**: a click on a stack trace line opens the Java editor via the
  WOLips server.

## 2022-03-22 (0.8.0)

- **OGNL removed**
  WOOgnl forked into WONoOgnl with the OGNL machinery stripped out, the survivor absorbed into
  ERExtensions as `er.extensions.bettertemplates`, and both WOOgnl and WONoOgnl deleted as
  modules. The template syntax stays; the expression language goes.
- **Inline bindings enabled by default.**
- **ERLoggingReload4j module created**
  The log4j-specific code moves out of ERExtensions into its own small framework, behind a logging
  facade.
- **EOF dependency dropped**
  JavaEOAccess removed from the pom — this time it sticks.
- **Deleted ERXMessageEncoding**; serialVersionUIDs obliterated; Localizable.strings deleted;
  admin direct actions always allowed in development mode; Java 17 Eclipse settings; Xerces pulled
  in as an explicit dependency.
- **Added an updateContainerID binding to AjaxUpdateTrigger**

## 2022-01-22 (0.7.0)

- **log4j → slf4j, completed; reload4j as the backend**
  The migration begun in December finishes across all frameworks, with reload4j replacing the
  ancient log4j 1.2.17.
- **The validation subsystem eliminated**
  ERXValidationFactory, ERXValidationException, the delegates, the template strings — some 680
  lines of machinery for a thing applications do better themselves.
- **ERXProperties put in its place**
  No longer inherits from `Properties`, can't be instantiated, KVC implementation removed, cache
  made concurrent — roughly 25 consecutive deletion commits. ERXSystem deleted alongside.
- **ERXDirectAction reshaped**
  The old kitchen-sink class renamed to ERXAdminDirectAction; a new, thin ERXDirectAction takes
  its name.
- **ERXLocalizer pruned** (plurification helpers, ERXNonPluralFormLocalizer); the ancient
  Safari-on-Leopard workaround removed; `Loader` renamed to `ERXLoader`.

## 2021-11-20 (0.6.0)

- **Java 17** (from 11).
- **Deleted the forked `com.webobjects.foundation` package**
  NSSet and friends, 4,285 lines — the framework now lives with the Foundation it's given.
- **Deleted ERXResponse**
  Plain WOResponse everywhere.
- **ThreadLocals are no longer cloned** — ERXCloneableThreadLocal deleted.
- **Direct component access disallowed; plain WOApplication no longer supported**
  An ERXApplication subclass and the component request handler are the supported path.
- **ERXApplication decomposition begins**: the Loader promoted to its own file, AppClassLoader
  deleted, session-store deadlock detection removed, ERXDictionaryUtilities and ERXValidation
  deleted. The Japanese-javadoc purge, running since May, completes.

## 2021-07-14 (0.5.0)

- **Build plugin: wolifecycle → vermilingua**
  Small diff, big decision — the build now runs on the maintained plugin.
- **The utility classes dissolve**
  A systematic method applied to ERXFileUtilities and ERXStringUtilities: move each method to its
  single use site, then delete the empty husk. ERXFileUtilities dies here; ERXStringUtilities is
  deprecated and takes another eighteen months to stay dead.
- **`.wo` templates read as UTF-8** instead of platform encoding — a real bug fix hiding in the
  cleanup.
- **Component pruning continues post-absorption**: WOTabPanel, WOCheckboxMatrix,
  WORadioButtonMatrix, WOTable, the old WOExceptionPage; ERXExceptionPage takes the WOExceptionPage
  name; packages reorganized into `stats` and `error`.
- **`handleException()` returns a proper 500**; startup log noise reduced.

## 2021-05-31 (0.4.0)

- **Third-party cords cut**
  The Apache commons-lang and EOControl dependencies removed (EOAccess may still pull EOControl in
  transitively — pragmatism won).
- **JavaWOExtensions absorbed**
  Moved into ERExtensions wholesale and deleted as a module; the reactor drops to three frameworks.
- **Legacy web resources purged**: every gif, clippy.swf, dhtml.js, date-picker.js and their
  friends.
- **This changelog introduced.** The entries below this one, from May 2021, are the original notes
  written at the time.
- **Deleted ERXArrayUtilities, ERXConstant, ERXSubmitButton**
- **Moved response compression from dispatchRequest() to new class ERXResponseCompression**
  Makes the code easier on the eyes. Still considering full removal of response compression since that tends to be handled by the web server in most environments I know of.
- **Moved ERXCompressionUtilities class into ERXResponseCompression and made it private.**
  Slim is not a generic compression framework, so it's reasonable that the only user of the code keeps it.
- **Renamed ERXHyperlink to ERXWOHyperlink**
  Naming conventions are good.
- **Renamed ERXSwitchComponent to ERXWOSwitchComponent**
  Naming conventions are good.
- **Removed ERXSession.javascriptEnabled**
  If you need this sort of functionality, do it yourself
- **Deleted ERXDirectAction.browser()**
  ERXRequest already holds a browser object and a direct action holds a request.
- **Deleted ERXGracefulShutdownHook**
  It's been disabled by default for a while. It used `sun.misc.Signal` and `Signalhandler` whose usage is not recommended. Use ERXShutdownHook instead.
- **Deleted `ERXApplication._startRequest()` and `ERXApplication._endRequest()`**
  If you need to do stuff before and after requests, override `dispatchRequest()`.
- **Moved the ERXExtensions.initApp(...) methods to new class ERXAppRunner**
  ERXExtensions should serve only as the ERExtensions framework's principal/initialization class
- **Removed threadInterrupt stuff from ERXRuntimeUtilities**
  Logic not actually used by any code inside the frameworks.
- **Deleted ERXActiveImage**
  Doesn't seem to serve any purpose
- **Deleted ERXWOPasswordField**
  The improvements offered by it are negligible in the age of ubiquitous https
- **Deleted userInfo() stuff from ERXResponse**
  It seems to have been there mostly to keep comptibility with older WO versions.
- **Removed pushContent(), popContent(), __setContent() etc. from ERXWOContext**
  Looks like the vestiges of a 13 year old experiment by mschrag.
- **Deleted ERXDelayedRequestHandler**
  Cool idea, but reading the mailing list it seems to have it's problems. I'd prefer a mechanism that allows the programmer to consciously decide to use long responses when desired, not something that alters the global behaviour of the application.
- **Moved ERXBrowser and it's companions to a separate package; er.extensions.browser**
  The er.extensions.appserver package is pretty full as is
- **Deleted ERXTimestampUtilities**
  Modern java uses the classes from java.time.
- **Deleted ERXSelectorUtilities**

## 2021-04-05 (0.3.0)

- **The component museum deleted**
  Roughly two hundred legacy components in two days: the checkbox and radio matrices, tab panels,
  batch navigation, grouping tables, ERXFlashMovie, ERXClippy, ERXLoremIpsum, the RSS and podcast
  pages, ERXRemoteShell, ERXDatabaseConsole and their kin. ERExtensions' `.wo` bundle count falls
  from 239 to 26.
- **Utility classes hollowed out**
  Most of ERXArrayUtilities, ERXDictionaryUtilities, ERXValueUtilities, ERXStringUtilities,
  ERXFileUtilities; ERXMutableDictionary and ERXMutableArray replaced with plain collections; the
  crypting package (BCrypt included) deleted.
- **A real reactor build**
  A proper parent pom (`undur-parent`) replaces per-module boilerplate.
- **The Ajax framework comes home**
  Exiled to its own repo in October 2020, re-imported (327 files, 62k lines) and added to the
  build.
- **Pragmatic walk-backs**: JavaEOControl and JavaEOAccess re-added as compile-time dependencies to
  keep things building; `ERXStringUtilities.safeIdentifierName()` re-added after its deletion was
  regretted in use.

## 2021-03-29 (0.2.0)

- **EOF removed from ERExtensions**
  The defining change of the fork. `er.extensions.eof`, the qualifiers, the EOControl package,
  display groups, partials, migrations, the JDBC utilities and the EOAccess patches — deleted in a
  day and a half. ERExtensions goes from 525 Java classes to 223, from a database framework to a
  web framework.
- **Servlet support deleted**: ServletAdaptor, ERXWOServletContext, ERXServletApplication and the
  servlet-api dependency.
- **The ERXJS component family deleted.**
- **Java 11** (from 1.8); **groupId becomes `undur`**.
- **Dependencies shed**: joda-time, commons-codec (→ `java.util.Base64`), icu, commons-httpclient,
  junit, commons-lang's CharEncoding (→ `StandardCharsets`).
- **A test application added** — the first in-repo way to actually run the thing.

## 2020-10-15 (0.1.0)

- **The fork**
  Project Wonder cut down to the frameworks actually in use: the Applications, Examples, Archives,
  Utilities and Tests directories and some 380 frameworks deleted — 13,500 files, 1.2 million
  lines — leaving ERExtensions, JavaWOExtensions and WOOgnl at the repo root.
- **The Ajax framework exiled** to its own repository (it returns in 0.3.0).
- **ant is dead; the build is Maven now**
  The ant build deleted, all three frameworks restructured to Maven layout ("Ye'r a maven project
  now, laddie").
- **The version is set to 8.0.0-SNAPSHOT** — on day one, choosing the number that would finally
  ship five and a half years later.

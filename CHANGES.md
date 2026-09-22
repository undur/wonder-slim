# Changelog

## Unreleased

- **ERControl: the framework's control panel**
  A new framework, the counterpart of ng-objects' ng-control, holding the diagnostic and
  administrative pages for a running application behind one gate and one set of routes beneath
  `/wonder/admin`. Overview (uptime, sessions, memory, instance facts; collect garbage, stop),
  statistics (what the statistics store has counted, and the ERXStats summary), events (which
  event classes are recorded, and the recorded events as a tree), exceptions, sessions and
  caches, threads, the log, properties (everything in effect, explicitly set keys marked, secrets
  masked, set a property on the running instance) and bundles. A request is let in in
  development mode, or once its session has logged in with `WOMonitorServicePassword` - the
  same property ERXMonitorServer reads, so one secret opens both; unset means closed. Optional:
  an application that does not depend on the framework has no administrative surface at all.

  Everything only the control panel uses moves there from ERExtensions, package names
  unchanged: WOStatsPage, the WOEvent pages, ERXExceptionManagementPage, the session cache
  overview pages, ERXStatsSummary, the WX outline components, and the resources only they use.
  The data they show stays in ERExtensions. The admin direct actions keep working where the
  framework is present.

- **One canonical URL form for every inbound request**
  `ERXApplication.createRequest()` turns every inbound URL into the canonical WebObjects URL for it
  before WO parses it (`ERXShortURLs.canonicalize`), so nothing downstream depends on the shape the
  front end delivered. A URL whose first path segment names a registered request handler becomes
  `<prefix>[/N]/<key>/...`; everything else is a route and becomes `<prefix>[/N]/route/<path>`.
  That holds for freestyle URLs (`/a/b`), URLs carrying an adaptor prefix (`/Apps/WebObjects/App.woa/a/b`,
  whatever adaptor path the front end uses), URLs carrying the instance number mod_WebObjects adds
  (`/App.woa/1/a/b`), and URLs already in the canonical form. The prefix a request carried is kept;
  one that carried none gets the application's own. URLs naming another application pass through.

  `route` is a registered request handler key, served by `RouteRequestHandler`, which hands the
  request to the route table. It is internal: generated URLs never contain it, and public URLs are
  identical in development and deployment. `RouteRequestHandler` is also the default request handler,
  as a safety net for a request that arrives uncanonicalized. Since WO only ever parses well-formed
  URLs now, the `WODynamicURL` subclass that disabled its validity check is gone.

  A front end forwarding paths to a WebObjects adaptor should forward the canonical form itself:
  `/Apps/WebObjects/App.woa/route/<path>`, for every path. Behind the key the URL is an ordinary WO
  handler URL the adaptor passes through untouched, so a numeric path such as `/1234` or a trailing
  slash survive - in the bare-prefix form WO's URL grammar, and the adaptor, read `1234` as an
  instance number. The application sorts handler-key URLs (`/route/res/...`) from routes itself, so
  the front end needs no list of handler keys.

  `RouteRequestHandler.routePath(WORequest)` is public: the path a request asked for - the route
  path of a route, the short path (`/wa/default`) of a handler request - for applications
  describing the current page (canonical URLs and the like) instead of reading `request.uri()`. Mapping a route whose first segment is a registered request handler key fails at
  mapping time, since such a route could never be matched. `tools/playwright-bridge/examples/
  route-url-shapes.mjs` probes the URL-shape matrix.

- **`com.webobjects.woextensions` is gone**
  The components inherited from JavaWOExtensions now live in packages of the framework's own:
  the error pages in `er.extensions.components.errorpages`, the rest (WOCollapsibleComponentContent,
  WOIFrame, WOKeyValueConditional, WOLongResponsePage, WOMetaRefresh, and in ERControl the
  statistics and event pages) in `er.extensions.components.woextensions`. Templates are
  unaffected; an application importing `ERXErrorPage` or `WOExceptionPage` by class updates the
  import.

- **Parsley 1.6.1**
  The `/problems` development endpoint now reports from Parsley's recording store, so it lists the
  runtime problems the application rendered into its pages.

- **ERXURLRewriter is gone**
  The pattern-and-replacement applied to generated URLs
  (`er.extensions.ERXApplication.replaceApplicationPath.pattern` / `.replace`) has been removed.
  It rewrote outbound only, leaving the inbound half to the front end, and with short URLs on it
  never saw the long form its pattern was written to match. An application that still sets either
  property refuses to launch and says why, rather than ignoring the configuration.

- **Admin action password check**
  `ERXAdminDirectAction` reads the statistics store's password itself; `ERXPrivateKVC` is gone.

## 2026-09-20 (8.0.6)

- **Legible startup output**
  The properties report is two banner sections: the Properties files in load order (including
  framework Properties inside jars, which the old report skipped), and every property in effect,
  alphabetically, with the keys a Properties file or the command line set marked with `*` - so the
  application's own configuration stands out while WebObjects' and the JVM's defaults stay
  available. Keys that look like secrets (passwords, API keys, tokens, credentials) are masked; the
  classpath is printed one entry per line. WO's own NSLog.debug chatter - the dump of every WO
  default, "Application project found", "Waiting for requests..." - is capped at DebugLevelCritical
  by default (`-Der.extensions.NSLog.debugLevel=2` restores it). The banner ends with an
  application section: the name WebObjects resolved (with the bundle name alongside when the two
  differ), the pid, and the direct connect URLs - in every mode, and last, since it is what one
  reaches for first. The ERXFrameworkPrincipal lifecycle trace is opt-in
  (`er.extensions.ERXFrameworkPrincipal.logLifecycle`).

- **Logging works from the first line**
  A console appender is installed as `main()`'s first act, so nothing logged during WO's and the
  application's own initialization is dropped any more and log4j's "No appenders could be found"
  is gone. That was the root cause of constructor-time log calls silently vanishing: log4j was only
  configured once the bundles had loaded, which is during the WOApplication constructor.
  `docs/LOGGING.md` documents the stack, the timeline and the remaining traps.

- **Generated URLs name the application on every request**
  A root request (`/`, or a front end's rewrite of it that omits the application name) parses to an
  empty name, and a context created by a handler a route delegates to never saw the fixup RouteAction
  applied for its own - every URL such a context generated read `/cgi-bin/WebObjects/.woa/wo/…`,
  which WO accepts but short URLs could not recognise as the application's own. The context's URL
  base is now normalised in ERXWOContext for every context.

- **User-facing error pages**
  WOSessionRestorationError, WOPageRestorationError, WOSessionCreationError and ERXErrorPage share
  one calm layout - a centered card with an icon, a heading, a plain-language explanation, one
  action back to the application, and the application's name - self-contained (inline CSS, no web
  resources, no scripts), since an error page must render when something has already gone wrong.
  The session page tells the user the timeout. The 2000-era tables and exclamation.gif are gone.

- **Browser pool and reference counting removed**
  ERXBrowserFactory kept shared ERXBrowser objects in a manually reference-counted pool, released
  from ERXSession.terminate() and ERXRequest.finalize(). The counting never freed anything - the
  factory also cached every browser by user-agent string in a static, unbounded map, which was the
  actual leak (one entry per distinct user-agent ever seen). Pool, counters, retainBrowser /
  releaseBrowser and the finalizer are gone; the user-agent cache is a bounded LRU.

- **WOConditional resolves to ERXWOConditional**
  The full element name now maps to ERXWOConditional like the `if` / `conditional` / `condition`
  shortcuts already did, so every conditional tracks its state and `<wo:else>` works after any of
  them. The `else` shortcut moved into ERExtensions' own tag aliases, next to the element it names.

- **Streams of unknown length are never compressed**
  An open-ended response (server-sent events, say) has no end to read to; gzipping it would block
  forever. ERXResponseCompression leaves such responses alone.

- **Housekeeping**
  ng-core 0.1.2 released, so the dev-mode classes are a regular dependency again; vermilingua 1.1.10;
  a note in RouteAction on what it would need to stand alone if routing were split out. AjaxPlayground
  gained server-sent-events and Datastar-over-SSE scenario pages (on the released wo-adaptor-jetty).


## 2026-09-15 (8.0.5)

- **Short URLs, on by default**
  An application now accepts `/wa/…`, `/wo/…` and `/res/…` as if the adaptor prefix were present, and
  generates its URLs without it - the same URLs in development and deployment. The long form keeps
  working and explicit routes take precedence. Both directions are pure functions of the application's
  URL prefix and its registered handler keys (ERXShortURLs), hooked in at `createRequest()` inbound and
  `_urlWithRequestHandlerKey()` / `_newLocationForRequest()` outbound; the prefix removed outbound is the
  one the request was built from, so a front end rewriting into `/Apps/WebObjects/App.woa/…` is answered
  in kind. Opt out: `er.extensions.ERXApplication.shortURLs=false`.

- **Routes match beneath any adaptor prefix**
  `/`, `/cgi-bin/WebObjects/App.woa/` and `/Apps/WebObjects/App.woa/` all route as `/`, so an
  application no longer registers its routes once per prefix in circulation.

- **URL rewriting reduced to the one escape hatch**
  ERXURLRewriter is now only `er.extensions.ERXApplication.replaceApplicationPath.pattern` /
  `.replace` (the unprefixed `er.extensions.replaceApplicationPath.*` spelling is gone). The
  development-only `rewriteDirectConnect` is removed - it never produced a parseable URL - and the
  direct connect URL is shortened but never passed through the front-end rewriter.

- **ERXWOComponentInstance: embed a constructed component instance**
  `<wo:ERXWOComponentInstance instance="$inspector" selectedObject="$object" />` embeds an instance the
  page constructed and configured itself - bindings, wrapped content, page caching and awake/sleep all as
  for a normally embedded component - so a page can hold a typed handle to its subcomponent and call
  methods on it instead of passing everything through bindings. The `instance` binding is re-evaluated
  every phase; a different instance replaces the embedded one and the old one leaves the page. Null,
  stateless (pooled) and already-embedded-elsewhere instances are refused with an exception. Construct
  with `ERXComponentUtilities.instantiate(MyComponent.class, context())`, which - unlike
  `pageWithName()` - neither awakens the instance nor flags it as a page. Replaces the experimental
  ERXSwitchComponentInstance (nothing switched; the binding was `componentInstance`).

- **handleActionRequestError() rewritten to its minimal honest form**
  The override that routed direct-action errors into `handleException()` shed its WO 5.2-era baggage
  (the InstantiationError/InvocationError session check-in that 5.4.3's own finally blocks made
  redundant) but stays: answering WO's empty hook ourselves is what keeps action-request error handling
  independent of `WODisplayExceptionPages` - in stock WO that property does not hide exception pages, it
  skips `handleException()` for action errors entirely. An explicit `WODisplayExceptionPages=false` now
  draws a startup warning saying so. To hide stack traces from end users, override `handleException()`.

- **AjaxSlim: Idiomorph 0.7.4 -> 0.8.0**
  Drop-in update of the vendored morph engine: fixes the focus-restoration TypeError on elements
  without text selection, namespace preservation for recreated SVG elements, `beforeAttributeUpdated`
  firing for unchanged attributes, and CSS-special characters in ids; adds a console warning for
  duplicate ids. The full playground bridge suite passes before and after.

- **Housekeeping**
  slf4j 2.0.19; `docs/LOGGING.md` documents the logging setup and its known traps (notably that
  `log.*` from the application constructor is silently dropped - log from `didFinishLaunching()`).


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

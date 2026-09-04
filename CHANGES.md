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

## 2022-02-06
- **Added an updateContainerID binding to AjaxUpdateTrigger**

## 2021-05-24

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

## 2021-05-23

- **Deleted ERXGracefulShutdownHook**
  It's been disabled by default for a while. It used `sun.misc.Signal` and `Signalhandler` whose usage is not recommended. Use ERXShutdownHook instead.

- **Deleted `ERXApplication._startRequest()` and `ERXApplication._endRequest()`**

  If you need to do stuff before and after requests, override `dispatchRequest()`.

- **Moved the ERXExtensions.initApp(...) methods to new class ERXAppRunner**
  ERXExtensions should serve only as the ERExtensions framework's principal/initialization class

- **Removed threadInterrupt stuff from ERXRuntimeUtilities**
  Logic not actually used by any code inside the frameworks.

- **Deleted ERXArrayUtilities**

- **Deleted ERXActiveImage**
  Doesn't seem to serve any purpose

- **Deleted ERXWOPasswordField**
  The improvements offered by it are negligible in the age of ubiquitous https

- **Deleted userInfo() stuff from ERXResponse**
  It seems to have been there mostly to keep comptibility with older WO versions.

- **Removed pushContent(), popContent(), __setContent() etc. from ERXWOContext**
  Looks like the vestiges of a 13 year old experiment by mschrag.

## 2021-05-21

- **Deleted ERXDelayedRequestHandler**
  Cool idea, but reading the mailing list it seems to have it's problems. I'd prefer a mechanism that allows the programmer to consciously decide to use long responses when desired, not something that alters the global behaviour of the application.
- **Moved ERXBrowser and it's companions to a separate package; er.extensions.browser**
  The er.extensions.appserver package is pretty full as is
- **Deleted ERXTimestampUtilities**
  Modern java uses the classes from java.time.
- **Deleted ERXSelectorUtilities**

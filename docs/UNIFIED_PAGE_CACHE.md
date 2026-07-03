# The unified page cache

How `ERXAjaxSession`'s page cache works after unification: **one** session-side cache serves every
page restore — the back button, plain component actions and ajax updates alike. WO's private
backtrack cache (`_contextRecords`) is never fed and stays empty.

Supersedes the two-cache design described in [`AJAX_PAGE_CACHE.md`](AJAX_PAGE_CACHE.md) (historical)
and [`AJAX_PAGE_CACHE_REWRITE.md`](AJAX_PAGE_CACHE_REWRITE.md) (the contextID-keyed model this builds
on — its keying and eviction are unchanged; what changed here is that the cache absorbed WO's too).

## Why unify

Under the previous design a page's lifetime was governed by two caches with independent eviction:
full renders went into WO's private, context-bounded backtrack cache; ajax updates went into our
instance-bounded replacement cache. The two-cache split forced a **routing decision on every save**
(`DONT_STORE_PAGE`, the page-cache-key header, the ajax-submit hack…), and that routing layer is
where most of the historical bugs lived. Memory was never the real cost — both caches held pointers
to the same live page instances — the cost was two eviction policies over one set of objects, plus
the machinery to steer between them.

The unification insight: the instance-bounded, contextID-keyed model **subsumes** backtrack
semantics. The back button restores by contextID; any contextID of a retained instance resolves. So
one map serves both callers, and the concepts "backtrack cache" and "page fragment cache" collapse
into "the page cache".

## The model

The cache is `contextID → record(live page instance)`, in the session (`ERXAjaxSession._pageCache`).

- **Store** (`savePage`): every render — full-page or ajax — is stored under the contextID it
  happened in, which is exactly the contextID a link rendered now will carry when later clicked.
  Never calls super; WO's cache stays empty.
- **Restore** (`restorePageForContextID`): a direct O(1) `get(contextID)`. ContextIDs are
  session-unique, so a hit is always the right page (no cross-instance bleed).
- **Bound**: the number of distinct page INSTANCES, configured by WO's own knob —
  `WOApplication.pageCacheSize()` / `WOPageCacheSize` (default 30). One instance accumulates many
  contextID keys over its life; those are cheap pointers and are not bounded. Eviction is LRU over
  instances (store or restore touches all of an instance's keys to the tail); evicting an instance
  drops all its keys together. **Semantic shift from stock WO**: the bound counts retained live page
  trees, not backtrack steps — prefer small values, single digits serve a real user fine.
- **An ajax ping costs nothing**: a background update just adds an alias for the same instance, so
  ajax activity can never evict the foreground page — which was the entire reason the don't-store
  routing existed. The one render class deliberately NOT stored is an ajax response that produced no
  restorable content (flagged don't-store with no page-cache key, e.g. a progress poll): nothing
  references its contextID, and pollers would otherwise bloat the alias count.
- **`pageCacheSize() == 0`** disables page storage entirely (the knob's historical meaning),
  including for ajax.

What the cache holds is unchanged and remains the load-bearing good idea: **a live `WOComponent`
instance, never rendered HTML** — re-rendered from current model state on every restore, so there is
no invalidation problem. The permanent page cache is deliberately obsolete — see below;
`savePageInPermanentCache` throws.

## The repeated-request guard

Storing pages was only one of the jobs WO's backtrack cache did; the other was powering
`_contextIDMatchingIDs` — the mechanism the component request handler consults on every dispatch to
detect a byte-identical repeat of an already-handled request. With WO's cache empty, that would
silently die, so the unified cache owns it.

**When it applies — gated on `isPageRefreshOnBacktrackEnabled()`, exactly like stock WO.** This was
verified empirically against the pre-unification stack (ScenarioReplay counters over raw HTTP):

- Flag **off** (WO's default): an identical re-submit **re-executes the action** — the behavior every
  WO developer expects. The guard never fires; idempotency is the application's business.
- Flag **on**: an identical repeat is answered by re-rendering the stored result page **without**
  invoking `takeValues`/`invokeAction`. Identical behavior pre- and post-unification.

The gating is the point, not an accident: page-refresh-on-backtrack disables client caching so the
*browser itself* re-issues requests — including re-POSTs — as part of ordinary history navigation.
Those mechanical replays must not re-execute actions, or walking Back through history would re-run
them. The guard exists to serve that mode; outside it, a re-submit is a deliberate client act.

Mechanics (active mode only):

- Each entry stored for a **plain component action** records the *provenance* of the request that
  produced it: the request's contextID, senderID and a fingerprint of its form values (sorted keys,
  verbatim values — no hashing, because a hash collision would silently *skip* an action).
- `ERXComponentRequestHandler` asks `ERXAjaxSession.contextIDForRepeatedRequest(context)` where
  stock WO consulted `_contextIDMatchingIDs`.
- Only a genuinely un-re-rendered repeat can match: every response re-renders its links under a
  fresh contextID, so two *distinct* clicks of "the same" link carry different contextIDs and both
  execute.
- Never applies to ajax requests (their contexts never entered WO's cache either), multipart
  uploads (streamed bodies aren't comparable), or requests without a sender.

One deliberate improvement over stock: in guard mode the match works however old the repeat is, as
long as its page instance is retained — stock WO's matching aged out with its ~30 context records.

## What this deleted / changed

- **The storage routing decision.** `DONT_STORE_PAGE` no longer decides *where* a page is stored
  (there is only one place); it now only marks "this ajax render produced no restorable content".
  The page-cache-key header now marks that an ajax render is worth storing at all, and is kept on
  the entry purely as a diagnostic tag.
- **The permanent page cache** (`overridePrivateCache`, `_permanentPageCache`,
  `savePageInPermanentCache`'s own storage, `_shouldPutInPermanentCache`, the `ERXPrivateKVC`
  reflection) — deleted, and the feature declared **obsolete, loudly**: `savePageInPermanentCache`
  now **throws** `UnsupportedOperationException`. The feature was a frames-era exemption from
  context-churn eviction (a frameset or navigation-frame page, loaded once and never re-requested,
  must not get pushed out of the page cache by navigation elsewhere); instance-LRU eviction removed
  context churn as an eviction force, so the problem it solved no longer exists. Throwing rather
  than silently routing to a normal save is deliberate: the caller asked for session-lifetime
  pinning, and quietly downgrading that contract to LRU semantics would be a behavior change with
  no error. (Should a real pinning need ever resurface, it belongs as a `pinned` flag on a cache
  entry excluded from the instance limit — not a second cache.) The dormant old code path also had
  a real leak (pages re-added directly to the dict were never tracked for eviction).
- **The vestigial `original_context_id` request-header write** in `restorePageForContextID` — dead
  since the rewrite deleted its only reader.
- **`er.extensions.maxPageReplacementCacheSize`** — no longer used; the bound is
  `WOPageCacheSize` / `WOApplication.pageCacheSize()`. A startup WARN fires if the old property is
  still set. (The historical "set it to thousands" workaround was already pointless after the
  rewrite; now the property is gone entirely.)
- Renames, since "replacement cache" is no longer a thing: diagnostics are
  `pageCacheSummary()` / `pageCacheSnapshot()` / `pageCacheDiagnostics()`, per-store logging is
  behind `er.extensions.appserver.ajax.ERXAjaxSession.logPageCache`, and the exception extra-info
  key is `PageCache`.

## Observability

- `pageCacheSummary()` — one line: `pageCache: N contexts / M instance(s) (oldest stored …, last used …)`.
  Logged per store behind `…ERXAjaxSession.logPageCache` (enabled in AjaxPlayground).
- `pageCacheDiagnostics()` — the multi-line per-instance report, attached to exception extra-info.
- The session cache-monitor page reports the unified cache with full age data, and still reports
  WO's backtrack/permanent caches via reflection — those cards now double as a **regression check**:
  anything other than "empty" means some code path is still storing pages the old way.

## Testing

The playwright-bridge harnesses drive the cache-hungry patterns end-to-end (see
`tools/playwright-bridge/examples/`): `invoice-cache-stress.json` (stale links after many targeted
updates), `cache-thrash.mjs` (multi-instance bleed torture, default cache size),
`cache-thrash-degenerate.mjs` (eviction corner — run with `-DWOPageCacheSize=1`; must error, never
serve another instance's content), `cache-lru.mjs` (edit-and-wander endurance, run with
`-DWOPageCacheSize=3`). AjaxPlayground's `Application` only applies its generous default page-cache
size when `WOPageCacheSize` isn't set explicitly, so the harnesses can force tiny caches.

The repeated-request behavior has its own harness: `replay-guard.mjs` (raw HTTP, no browser) drives
`ScenarioReplay`'s plain component actions and re-sends byte-identical requests, asserting BOTH
modes: `MODE=replay` (default; app started normally — repeats must re-execute) and `MODE=guard`
(app started with `-DWOPageRefreshOnBacktrackEnabled=true` — repeats must be answered from the
stored page, fresh clicks must execute). Run with
`BASE=http://localhost:<port> [MODE=guard] node examples/replay-guard.mjs`.

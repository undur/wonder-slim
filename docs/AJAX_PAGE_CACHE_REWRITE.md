# Ajax page cache — the rewrite

How `ERXAjaxSession`'s ajax page-replacement cache works after the rewrite, and why.

## The problem it solves

A component-action link rendered inside an ajax-updated region carries the contextID it was rendered
in. When clicked, the server must map that contextID back to the live page so the action can run.

WO's backtrack cache (`_contextRecords`) does this, but it's tiny (~30 entries) and meant for the back
button. Under ajax every interaction mints a new contextID, so after ~30 interactions a still-rendered
link's contextID has aged out of WO's cache — and clicking it 500s with "backtracked too far", even
though the page is right there and the user never left it. This is the bug that bit real apps editing
one page for a while (a receipt with many line edits, then clicking a row control that hadn't
re-rendered).

## The model: key by render contextID, bound by instance

The cache is `contextID → page instance`, stored in the session.

- **Store** (`savePage`): `put(context.contextID(), record)` — keyed by the context THIS render happened
  in, which is exactly the contextID a link rendered now will carry when later clicked.
- **Restore** (`restorePageForContextID`): `get(contextID)` — a direct O(1) hit. No reverse-map through
  WO's backtrack cache, no container-name scan.

Every interaction mints a new contextID, so **one page instance accumulates many contextID keys over
its life** — `0, 2, 4, 6, …`. That's fine: they're cheap pointers to the same live object. We do not
bound the contextID count. We bound the number of **distinct page instances** (by object identity):
when a genuinely new instance would exceed `maxPageReplacementCacheSize`, the least-recently-used
instance is evicted and all of its contextID keys go together. Access (store or restore) moves an
instance's keys to the tail, so eviction is LRU over instances.

That's the whole thing. A link resolves directly however many interactions ago it was rendered, as long
as its instance is still one of the live ones. ContextIDs are globally unique, so a hit is always the
right page — no cross-instance bleed.

## What this removed

The old code keyed fragments by `(originalContextID, container)` and, because that key never matched a
returning link's contextID, leaned entirely on a fallback: reverse-map the link's contextID to a page
via WO's backtrack cache, then match by container name. That fallback is what aged out (WO's 30 cap),
and matching by container name (not page-unique) is what caused cross-instance bleed, which then needed
a page-identity guard, which needed the "refuse rather than guess" 500. All of it — the fallback, the
container scan, the bleed guard, the WARN, the two-state old-page debounce — is **deleted**. Keying by
the contextID the link actually carries makes restore a plain `get()`; none of that machinery is needed.

## A note on the dead end (originalContextID)

First attempt keyed by `originalContextID` (the page's *first* render context), on the theory that every
link carries it. Instrumentation disproved this: a link carries the context it was **rendered** in, not
the page origin. A link rendered inside an ajax update at context `2` carries `2`; storing under the
page's origin `0` means `get("2")` misses. The store side and an arbitrary returning link do **not**
share one stable id — which is exactly why the original code needed the reverse-map. The fix is to keep
a key for *every* render contextID (this model), not to pick one "stable" id.

## Safety: not bypassing WO machinery

`WOTransactionRecord` carries `_senderID` + `_formValues` feeding `isMatchingIDs` — WO's
duplicate-request/reload-replay guard, used only by the **backtrack** cache, never by the page-fragment
path (WO's own fragment cache uses the no-formValues constructor and never calls it). Our cache doesn't
use them either. We keep what the fragment path uses: the live page instance. We dropped the two-state
old-page debounce because it only ever compensated for contextID key-churn, which this model doesn't
have (a returning link hits the current record directly; the record holds the live instance, re-rendered
from current model state).

## Where it lives

Directly in `ERXAjaxSession` (`er.extensions.appserver.ajax`). The cache was briefly extracted into an
`AjaxPageCacheSession` superclass to do the rewrite in isolation, then collapsed back: caching is
ERXAjaxSession's whole reason to exist, so a separate class earned nothing.

Bound: `er.extensions.maxPageReplacementCacheSize` (default 30) — now distinct instances, not contexts.
Observability: `pageReplacementCacheSummary()` reports `N contexts / M instance(s)`; logged per store
behind `er.extensions.appserver.ajax.ERXAjaxSession.logPageReplacementCache`.

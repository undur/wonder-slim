# The Ajax page-fragment cache

How `ERXAjaxSession`'s page-replacement (fragment) cache works, why it exists, and the
`(page, container)` redesign that fixed an age-old "backtracked too far" failure.

This lives in **ERExtensions** (`er.extensions.appserver.ajax.ERXAjaxSession`) and is
shared by **both** the legacy `Ajax` framework and `AjaxSlim` — they emit identical
server signals (`_u`, the page-replacement cache key, `DONT_STORE_PAGE`), so the cache
behaves the same for both.

---

## The two caches

WebObjects already has a page cache. So why a second one?

**1. WO's main page cache** (`WOSession`, the *backtrack* cache). Keyed by **context id**,
bounded by `WOApplication.pageCacheSize` (~30). Holds page instances so the **back button**
works. Its map (`_contextRecords`) is **private** — Wonder can't read it, extend its keys,
or hook its eviction.

**2. The page-replacement (fragment) cache** (this doc). A separate structure in the
session, holding page instances for **ajax-action restore**.

### Why ajax needs its own cache

Ajax updates are flagged **don't-store-page** (`shouldNotStorePage`), so they do **not** go
into WO's main cache. If they did, a few dozen background ajax updates would fill the
backtrack cache and evict the **foreground** page — then a normal component action on that
page would 500 ("the page has expired"), because its context is gone.

So ajax updates are kept out of the main cache. But a component action *inside* an
ajax-updated area still needs *its* page restorable later. That's what the fragment cache is
for: it keeps the most-recent ajax state of each updated region restorable, without
consuming backtrack slots.

> This is the design intent from the original author (mschrag). The novelty here is the
> *keying and eviction*, below.

---

## What a cache entry actually is

**A cache entry holds a strong reference to the live page instance — not rendered HTML, not
a snapshot of any container.** This is the single most important thing to understand.

Crucially: **every ajax fragment of one page points at the *same* page instance.** WO restores
one page object per session-page, mutates it in place across ajax updates, and re-saves it.
We verified this directly: across many ajax updates, `System.identityHashCode(page)` is
constant. The context id was only ever a proxy for "which instance" — and there is exactly
one.

Consequences of "entry = pointer to the one live page":

- **No staleness, no invalidation.** An "old" entry for container `bork` isn't stale: it has
  no content to go stale. It's a handle to the page, which holds *current* state for
  everything. Resolving it returns the live page, re-rendered fresh.
- **An entry is a strong reference that keeps the page alive** during the ajax window when
  WO's main cache has stopped holding it. Without it, the page would be GC'd and the next
  ajax action would 500. *This retention is a real reason the cache must exist* — it's not
  just an index.

---

## The bug: a flat, context-id-keyed LRU

The original cache stored entries keyed by **context id** and evicted the single oldest entry
once it passed `maxPageReplacementCacheSize * 2`. A flat, insertion-order LRU that **grows one
entry per interaction**.

That breaks any page doing many **targeted** partial updates:

1. You edit one container; only *that* container re-renders. Every **other** container's action
   link keeps the (now older) context it was rendered in.
2. Each interaction adds a new entry, evicting the oldest by insertion order.
3. Eventually the entry a *still-valid* but rarely-re-rendered container needs is evicted —
   even though its state is perfectly current.
4. That container's action link now carries a context that's gone → **500, "backtracked too
   far."**

Apps worked around this by cranking `maxPageReplacementCacheSize` huge (hundreds, thousands).
That number was really a budget for *interactions*, and it always eventually ran out.

mschrag's own comment notes the *preferable* behaviour — ajax records expiring with their
page — but says it was unreachable because WO's `_contextRecords` map is private.

---

## The fix: identify entries by `(page, container)`, evict by page

The insight: **a container can only hold one valid state at a time**, so the cache should be
sized by **(pages × containers)**, not by interaction count. Two facts (both measured) make
this work and make it safe:

- **Single instance.** All fragments of one page share one `WOComponent` — so a fragment is
  resolvable to the live page regardless of which context number a link carries.
- **`_u` is available at restore.** The incoming ajax request names its target container(s) in
  the `_u` form value, readable in `restorePageForContextID`. So a link can be resolved by
  *what it targets*, not by a context number.

### What is the actual map key?

Important precision: the cache map is **still keyed by context id** —
`pageReplacementCache.put(context.contextID(), record)`. We did **not** change the map's key.

What changed is that each record now carries two extra fields — its **`pageID`** (the page it
belongs to) and a **composite identity** `pageID_container` (`record.key()`) — and the
*eviction* and the *fallback lookup* use those instead of the map's insertion order. So:

- **Map key:** context id (unchanged). This is the fast, `O(1)` restore path.
- **`(page, container)`:** a *logical identity* stored on each entry. Eviction groups by
  `pageID`; the fallback scans for a container suffix. It is **not** the map key.

This is why "keyed by container" is the wrong way to say it. Container is a tag we *scan* for
when the context-id key misses — not how entries are stored. (A from-scratch design — see the
last section — would make `(page, container)` an actual index; here it's a property we reason
and evict by, layered over a context-id map.)

### Storage / eviction

The cache is bounded by the number of distinct **pages** (by `pageID`): when a new page arrives
over the limit, the **oldest page's entire set of entries is dropped together** (a dead page's
fragments are dead too). A still-valid container is never evicted just for being old. Per
`(page, container)` we keep at most two states — the current one, plus one previous for the
brief race window where the browser still shows the prior HTML and clicks its link.

### Restore — two indexes, one answer

A restore tries, in order:

1. **By context id** (fast path, `O(1)`): the link's context is still cached → exact hit. This
   is the common case (freshly-rendered links).
2. **By container** (fallback): the link's context has aged out, but its `_u` names its
   container(s). We find the cached fragment for **any** of those containers and return its
   page. Because of single-instance, that *is* the live page.

> **Why both?** They answer different questions. Context-id asks *"do I have this exact
> render?"*; container asks *"which page owns this container?"*. A link's context id is frozen
> at render time and ages out; its container id does not. Context-id-only is exactly what
> broke. Container-only would lose the fast path and mishandle non-ajax restores. So: context
> id is the fast hit, container is the resolver.

### Multi-target updates store ONE leaf

A multi-update (`_u=a;b;c`) is **one request, one context, one `savePage` call → one entry**
(keyed, like all entries, by that one context id). Its `(page, container)` *identity* is tagged
with whichever container the update pass rendered **last** — each container's render overwrites
the shared page-replacement-cache-key request header, last wins. So `_u=a;b;c`'s entry carries
the identity `…_c`, not `…_a`, and there is **not** an entry per container.

That's correct — one entry holds the whole page (single instance). But it means the container
fallback must check **every** container in `_u`, not just the first, since the entry's container
tag may be any of them. (It does.)

---

## Safety

The same basis as WO's own back button:

- A record holds **no live `WOContext`** — just the page object plus string ids.
- Restore **re-awakens** the returned page in the **current** request's context
  (`page._awakeInContext(context())`), exactly as the existing code already did. We serve the
  one true page instance; WO binds it to a fresh context for the new request.

And regular (non-ajax) WO actions are unaffected: ajax never touches WO's main cache, so the
foreground page stays in the backtrack cache, and a regular action restores via `super`
(verified: a normal component action works after heavy ajax activity).

---

## What `maxPageReplacementCacheSize` controls now

**Before:** fragment-*entry* count (×2), consumed one-per-interaction — a budget for clicks.

**After:** the number of distinct **pages** the fragment cache holds. Each page holds *its*
containers × ≤2 states. So total entries ≈ Σ over ≤MAX pages of (containers-on-that-page × ~2)
— bounded by your **real UI structure**, independent of interaction count.

So the historical "set it to thousands" workaround is now pointless, and arguably harmful:
under the new model a slot retains a genuinely distinct page tree (real memory), and you need
very few of them — a single user is rarely on more than a handful of ajax-live pages at once.
The default **30** is generous; lower is fine.

> **Note on memory.** Because entries are *strong* references that keep pages alive after WO's
> own cache drops them, a large size now means real retained page trees, not cheap duplicate
> references. Prefer small.

---

## Observability

`ERXAjaxSession.pageReplacementCacheSummary()` renders the size + page→container tree on one
line. Logged per fragment-store behind
`er.extensions.appserver.ajax.ERXAjaxSession.logPageReplacementCache` (off by default; enabled
in AjaxPlayground for the showcase):

```
[fragment-cache] stored 0_renderedInvoice for context 12 -> pageReplacementCache: 4 fragments / 1 page(s) | page 0[linebox-1, totalsPanel, renderedInvoice, summaryPanel]
```

Watch the **fragment count stay flat** while the **context id climbs** — that's the fix
working: bounded by structure, not by history.

---

## Testing

AjaxPlayground's invoice page (`ScenarioInvoice`) deliberately uses **per-row targeted
updates** — the cache-hungry pattern that broke the old cache — as the standing stress bed.
`tools/playwright-bridge/examples/invoice-cache-stress.json` does ~40 interactions then moves
a long-stale row; it 500s on the old cache and passes on the new one. The fix was also verified
with `maxPageReplacementCacheSize=1` (forcing eviction of every context): the move still works,
resolved through the container fallback.

---

## The bigger picture (for ng-objects)

The two caches are really *one cache of live pages with two indexes* — by context (backtrack)
and by container (ajax) — that WO's private `_contextRecords` forced us to split. The "fragment
leaf" isn't a fundamental thing; it's a **second key on the page entry**, plus the strong
reference that keeps the page alive. In a framework that owns its page cache from scratch
(ng-objects), you'd unify them: one page store, indexed by both context and container, evicted
by page lifetime — and the separate fragment cache, with its redundant-pointer entries, simply
stops existing as a concept. The one thing that would make a fragment cache genuinely hard —
and require real invalidation — is caching *rendered output* per container instead of page
references. The whole reason there's no invalidation problem here is that we cache the live
object, not its HTML.

# Ajax for WebObjects, designed from scratch

> **Status: design proposal / thought experiment.** This is NOT how the framework works
> today. It describes what the Ajax additions to WebObjects might look like if designed from a
> blank page — WO only, never having seen the existing `Ajax`/page-replacement-cache design. It
> exists to be evaluated against the current architecture (see
> [`AJAX_PAGE_CACHE.md`](AJAX_PAGE_CACHE.md)), not to describe shipping behaviour. No code
> implements this yet.

---

## Why write this down

The current ajax page cache is clever, correct, and — after the cross-instance-bleed fix — safe.
But its entire *shape* is inherited: it accepts WebObjects' "every interaction mints a new
contextID" reflex and then works heroically to bound the explosion that reflex causes under ajax.
Two whole bug classes we've chased (the "backtracked too far" eviction failure and the
cross-instance content bleed) are consequences of that inherited shape, not of ajax being hard.

This document re-derives the problem from first principles and proposes a different shape, to see
what falls away. The headline result: **most of the page-replacement-cache machinery — pageID
tagging, container-suffix scanning, two-state race windows, identity-via-request-header — exists
only to bound a contextID explosion that a stable page identity prevents from ever happening.**

---

## First principles: what WO is

WO's request loop rests on one idea: a page is a stateful server-side object graph (a
`WOComponent` tree), and the URL points back into it via a **contextID**. Each user action:

1. restores the page object from a session-side cache keyed by contextID (the **backtrack
   cache**, bounded ~30),
2. runs `takeValues → invokeAction → appendToResponse`,
3. produces a **new** contextID and ships a **whole new page**.

The backtrack cache is small and linear because it exists to serve the **back button** — a
shallow history. One assumption is baked deep:

> **one user action → one whole-page response → one new contextID.**

## What ajax actually is

Ajax breaks that assumption in exactly one place: **one user action → a *partial* response (one
region) → and the rest of the page stays on the client, still showing links/forms that point at
the *old* contextID.**

Everything else is downstream of this. After a partial update the client DOM is a **patchwork of
contextIDs**: the updated region's links point at the new context; everything else points at the
context from the last full render. Left alone, WO's backtrack cache fills with these
partial-update contexts — ~30 ajax pings and the **foreground** page's context is evicted, so the
next real click 500s with "backtracked too far."

The existing framework's answer is a *second* cache holding "the most recent state per ajax
region," tagged so partial-update contexts don't evict the foreground page. **That solves the
explosion. This proposal removes the explosion.**

---

## The core move: bind each page to one stable identity for its whole client lifetime

Assign every rendered page a **page key** — a server-generated opaque id (a UUID) — embedded once
in the page. Every ajax request from that page sends its page key back. The server keeps **one**
map:

```
pageKey → live WOComponent instance      (the whole page; the live object graph)
```

That is the entire ajax cache. **One entry per live page the user is actually looking at** — not
one per interaction, not one per region.

### What this addresses, point by point

1. **The patchwork-of-contextIDs problem disappears.** Links inside an ajax-updated region carry
   `pageKey + elementPath` (which element fired), not a freshly-minted contextID we then have to
   cache. The page key is stable — it does not age out after 30 interactions, because it is not
   minted per interaction. Background pings address the *same* page key, so they cannot evict the
   foreground page.

2. **No fragment cache. No `(page, container)`. No replacement. No old-page race window.** Those
   concepts exist *entirely* to bound the per-interaction contextID explosion. Remove the
   explosion, remove the machinery. A container is just a region you re-render; it is never a
   cache key.

3. **The cross-instance bleed becomes structurally impossible.** Two receipts open = two page
   keys = two map entries. A request carries *its* page key. There is no global scan by container
   name because containers were never an identity — the page key is. The entire bug class is
   *unrepresentable*, not guarded-against. (Contrast: the current fix had to borrow page-instance
   identity from WO's backtrack cache to scope a container-name scan. Here, identity is the
   primary key.)

4. **Eviction becomes honest LRU over a meaningful unit.** The map is "live pages the user has
   open." Bound it access-ordered by count (the N most-recently-touched page keys) and/or by idle
   TTL (a key untouched for, say, 30 min is almost certainly a closed tab). On eviction a stale
   ajax request gets a **typed** "page expired — reload" signal the client can handle (re-fetch
   the region or the page), instead of a raw 500 leaking through.

---

## The shape

```
Full-page render:   WO's normal flow. contextID backtrack cache. Back button works. Unchanged.
                  + assign a pageKey, embed it once, register  pageKey → live page.

Ajax request:       carries  pageKey + elementPath  (+ target region id(s)).
                    look up live page by pageKey        — O(1), no scan, no fallback
                    awake it in this request's context
                    run takeValues / invokeAction targeting elementPath
                    re-render ONLY the requested region(s)
                    page stays live under the same pageKey.

Eviction:           access-ordered LRU over pageKeys + idle TTL.
                    evicted → typed "reload" signal to the client, never a raw 500.
```

### The client contract

- **Embedded once per full render:** the `pageKey` (a `<meta>` tag, a JS global, or a hidden
  field injected into every form on the page).
- **Sent on every ajax request:** `pageKey`, the `elementPath` of the element that fired, and the
  id(s) of the region(s) to update.
- **Received:** the re-rendered HTML for the named region(s), or a typed expiry signal.

### The server pipeline (what the framework owns)

This is the real cost of the design, stated honestly. WO's `invokeAction` machinery restores by
contextID and walks the element tree to find the sender by elementID. Addressing by
`pageKey + elementPath` means the framework **owns the restore → action → render pipeline** for
ajax requests rather than overriding two `WOSession` methods:

1. look up the live page by `pageKey`,
2. `_awakeInContext` it in the current request's context,
3. set the senderID / elementID from `elementPath`,
4. drive `takeValues` / `invokeAction`,
5. render only the requested region(s) (partial `appendToResponse`),
6. leave the page live under the same `pageKey`.

More code at the framework boundary — but it lives in **one** place with **one** identity model,
instead of three caches coordinating through a request header.

---

## What we deliberately KEEP from WO

The one thing the existing design got exactly right, and which is **orthogonal** to all of the
above, is preserved unchanged:

> **The cache holds a live `WOComponent` instance — not rendered HTML, not a snapshot.**

`pageKey` maps to a live object, re-rendered from current model state on every request. No HTML
snapshots, no staleness, **no invalidation problem.** Every hard cache problem (coherence, TTL of
content, stale reads) is *dissolved* by caching the object rather than its output. This proposal
changes only the **addressing** (contextID → stable page key), never the **substance** of what is
cached. The day anyone caches rendered output per region "for performance," this safety collapses
and you inherit real invalidation — for this design and the current one alike.

We also **keep WO's backtrack cache doing only its job:** full-page navigations flow through WO's
standard contextID cache and still support the back button. The page key is an *additional* index
for ajax addressing. So there are still two indexes — but now orthogonal by **purpose**
(back-button history vs. live-page addressing), not two competing caches of the same thing
fighting over eviction. That is a far cleaner split than the current three-maps-one-truth
situation, where identity is reconstructed through a request header
(`original_context_id`) and borrowed across caches.

---

## The hard part this design does NOT make free: concurrency

Honesty requires flagging the one place this is genuinely harder, not easier.

With a single live instance per page key, **multiple in-flight ajax requests for one page hit the
same live object** (e.g. a background poll overlapping a user click). The current design
side-steps this: each request gets its own contextID, so concurrent requests touch distinct
restored states (mschrag's note: *"each component is requesting in its own thread and generating
their own non-overlapping context ids"*).

Options for the page-key model:

- **Serialize per page key** (a per-page lock). WO already serializes requests per *session* by
  default, so for the common case this mostly falls out for free.
- **Accept last-writer semantics** for overlapping updates to the same page.
- **Per-region isolation** if true *parallel independent* updates are a goal — at which point you
  re-introduce some of the per-region bookkeeping this design deleted.

That last point matters because parallel independent partial updates is a real, parked idea (a
flag to fire N independent updates for heavy containers). **No design makes that free.** If it is
a goal, it must be designed in from the start here — bolting it on later is precisely how the
current design accreted its complexity. The single-live-instance model is simplest and correct
for WO's default serialized-per-session requests; parallel partial updates are a separate, harder
problem to decide on deliberately.

---

## Bottom line

The existing framework is a very clever **bound on a problem it created** by inheriting WO's
"new contextID per interaction" reflex into the ajax world. From a blank page:

- give each live page a **stable opaque identity** (a page key),
- **address ajax requests to that identity**, not to a per-render contextID,
- let WO's backtrack cache go back to doing only what it is good at (the back button),
- keep the one good idea (**cache the live object, never its HTML**),
- decide the concurrency model **up front**.

You trade *"two `WOSession` overrides + three coordinating caches + identity-via-header"* for
*"own the ajax pipeline + one page-key map + a real typed expiry."* More framework code in one
spot; far less emergent complexity; and the bug class fixed in
[`AJAX_PAGE_CACHE.md`](AJAX_PAGE_CACHE.md) (cross-instance content bleed) becomes impossible by
construction rather than guarded-against.

### Open questions before this is more than a sketch

1. **`elementPath` stability** — can we reliably address the sender element across partial
   re-renders without WO's contextID-scoped elementID? (Stable DOM ids per logical element, as
   the invoice scenario already uses, suggests yes — but this needs proving.)
2. **Concurrency model** — serialize-per-page vs. last-writer vs. designed-in parallel regions.
   Pick one deliberately; it shapes everything.
3. **Expiry UX** — what exactly the client does on a typed "reload" signal (silent region
   re-fetch? full reload? user prompt?), and how that degrades for a genuinely dead session.
4. **Migration** — could this coexist with the current cache during a transition, or is it a hard
   replacement? (The page key is additive at render time, which suggests a gradual path is
   possible.)

# Multi-container update in one round-trip — design

**Status:** design proposal, for review. No code yet.

## Goal

Let **one trigger** (an `AjaxUpdateLink` / `AjaxObserveField` / `AjaxSubmitButton`) refresh **several**
named AjaxUpdateContainers in **one** HTTP round-trip, by declaring the targets up front:

```html
<wo:AjaxUpdateLink action="$foo" updateContainerIDs="a;b;c">…</wo:AjaxUpdateLink>
```

Today, refreshing N containers from one trigger means N separate fetches (one `AUC.update(id)` per
container, each a request). When the developer already knows the N targets and they have no inter-
dependency, that's pure waste — one request should refresh them all.

## What this is NOT (the separate path we leave untouched)

There are two unrelated "multiple containers get updated" mechanisms; this design touches only the
first:

- **Explicit multi-target from one trigger** (THIS design). One link/observe/submit names several
  containers (`updateContainerIDs="a;b;c"`); there is no cascade or dependency — they just refresh
  together. One request is the right model.
- **Self-update + `AjaxUpdateTrigger` cascade** (UNCHANGED). An AUC refreshes itself, and inside its
  re-rendered content an `AjaxUpdateTrigger` emits `AUC.update('other')`, causing a **follow-up**
  request. This is a *cascade* — the second container's new state depends on the first having updated —
  so the extra request is correct and intended. We do **not** fold this into one request. Likewise the
  AUC `action` binding only ever self-targets (a self-refreshing region handles its own action); it is
  never a way to update a sibling. None of this is involved in, or changed by, the multi-target trigger.

## Load-bearing invariant: a targeted container is a PASSIVE render target

Verified in the code (`AjaxDynamicElement.invokeAction` → `shouldHandleRequest` →
`AjaxUpdateContainer.handleRequest`): an AUC's own request-handling — including its `action` binding —
fires **only when that AUC is itself the request *sender*** (`senderID == its elementID`), i.e. when it
refreshes *itself* via its own `data-updateUrl` (an `AUC.update('x')` of x's own URL, or its own
`frequency`/`observeFieldID` self-poll).

When some **other** trigger drives the update (`AjaxUpdateLink` / `AjaxObserveField` with
`updateContainerID="x"`), the **trigger** is the sender: the trigger's own `action` fires, and the
container `x` is reached only in the append phase to **re-render its content**. Container `x`'s
`handleRequest` is skipped and **its `action` binding never fires.**

This is exactly the property that makes the multi-target feature a *pure rendering generalization*:
when one trigger names `updateContainerIDs="a;b;c"`, **the trigger is the sender** and `a`/`b`/`c` are
all passive render targets — **none of their `action` bindings fire.** Widening the `_u` gate from one
id to a set therefore only changes *which containers re-render*; it introduces **no** new action
invocations and no behavioural entanglement. The single-target and multi-target cases are identical in
every respect except how many containers re-render.

> **Note (API smell, tracked separately):** that an AUC's `action` fires *only* on self-update — and
> not when an external trigger updates it — is genuinely confusing; the natural reading of "an
> `action` on an updatable container" is "it fires whenever the container updates," which is the
> opposite of the truth. This is a symptom of one element conflating two concepts ("a passive updatable
> region" vs. "a region that self-updates"). Splitting those into two elements is a worthwhile cleanup,
> noted at the end of this doc — but it is independent of the multi-update feature, which works
> regardless.

## Hard constraint: format-by-arity (backward compatibility)

> **One container targeted → today's exact response and behaviour, byte-for-byte. Multiple targeted →
> the new multi format.** The single-container path does not change at all (every existing app keeps
> working; the legacy `Ajax` framework is not modified). The multi path is purely additive and only
> activates when more than one id is requested. The decision is made by the **arity of the requested id
> set** at one choke point on each side (server: `_u` has a separator or not; client: the trigger has
> >1 id or not).

## The mechanism (smaller than it first looks)

The key realisation: **an `AjaxUpdateLink`'s action already renders the whole page tree** — it just
gates output to one container. The link fires its *own* component-action URL and, server-side, sets
`_u = <target id>` in the request context (`AjaxUpdateContainer.setUpdateContainerID(request, id)`).
During the ensuing render pass, WO walks the page from the top; **every** AUC's `appendToResponse`
runs, but only the one whose id equals `_u` actually emits its content — the rest render transparently
(empty). So the multi case needs **no new "page-level render endpoint"**; we simply **widen the `_u`
gate from one id to a set**.

### Request: `_u` becomes a set

`_u=a;b;c` (semicolon-separated, matching the `updateContainerIDs="a;b;c"` template syntax). `_u=a`
(no separator) is parsed and behaves exactly as today — the single path is untouched.

`AjaxUpdateContainer.updateContainerID(request)` (returns one string today) gains a sibling
`updateContainerIDs(request)` returning the set; the per-render "am I the target?" check
(`currentUpdateContainerID()` equality, via `shouldRenderContainer`) generalises to **set membership**.

### Response: arity-gated framing

- **Arity 1:** the response is the bare container HTML, exactly as today. No envelope, no change.
- **Arity > 1:** each matched AUC emits its content **framed** so the client can demux:

  ```html
  <ajaxslim-fragment data-id="a"><div id="a" data-updateUrl="…" data-morph="true">…</div></ajaxslim-fragment>
  <ajaxslim-fragment data-id="b">…</ajaxslim-fragment>
  <ajaxslim-fragment data-id="c">…</ajaxslim-fragment>
  ```

  `<ajaxslim-fragment>` is an inert custom element (never rendered), unambiguous to parse, collision-
  free with content, and proxy-safe. (Open decision 1: custom element vs. comment delimiters vs. a JSON
  envelope.)

### Client: one fetch, demux, morph each

`AjaxSlim.AUC.updateMany(['a','b','c'], actionUrl)` → one `fetch` with `_u=a;b;c` → split the response
on `<ajaxslim-fragment>` → for each, `Morph.morph(liveContainer, innerHTML)` honouring that container's
`data-morph`. One round-trip; all containers updated; `onRefreshComplete` fires per container. A
container whose id is no longer on the page is skipped; the rest still update.

## Triggers

### `updateContainerIDs="a;b;c"` on link / observe / submit

`AjaxUpdateLink`, `AjaxObserveField`, `AjaxSubmitButton` gain an `updateContainerIDs` binding (plural).
When bound with >1 id, the emitted client call is `AUC.updateMany([...], url)` (or the multi variant of
`ASB.update`/`partial`) instead of the single `update`/`partial`. With the existing singular
`updateContainerID`, or `updateContainerIDs` holding one id, the current single path is used verbatim.

`AjaxUpdateTrigger` already accepts `updateContainerIDs`. Note it is the *cascade* element (it emits
client-side update calls into an already-returned response), so for it "multiple" can stay as
independent `update` calls, OR — since it knows all its ids — emit one `updateMany`. (Open decision 2:
whether AjaxUpdateTrigger should also batch into one request, given it runs after a response rather than
being a fresh trigger. Likely yes, for the same efficiency reason, but it is a slightly different code
path.)

## Open decisions (need sign-off before building)

1. **Fragment framing format.** `<ajaxslim-fragment data-id>` custom element (proposed) vs. HTML comment
   delimiters vs. a JSON envelope (`{"a":"…html…"}`). Custom element is easy to emit/parse and survives
   proxies; JSON is cleanest to split but needs HTML-in-JSON escaping.
2. **`AjaxUpdateTrigger` batching.** Leave it as N independent `update` calls, or have it emit one
   `updateMany`? It's the cascade path, so this is optional polish, not core.
3. **`_u` separator + ordering.** Semicolon (matches the template syntax) — confirm it can't collide
   with a legitimate container id. Fragments morph in document/emit order; independent, so order is
   cosmetic.

## Build order (once signed off)

1. Server: parse `_u` as a set (`updateContainerIDs(request)`); generalise the
   `shouldRenderContainer`/`currentUpdateContainerID` gate to set membership; emit framed fragments for
   arity > 1, bare HTML for arity 1 (unchanged).
2. Client: `AUC.updateMany(ids, url)` — one fetch, demux `<ajaxslim-fragment>`s, morph each.
3. Trigger binding: `updateContainerIDs=` on `AjaxUpdateLink` (first — simplest), then `AjaxObserveField`
   / `AjaxSubmitButton`.
4. Playground scenario + bridge regression: one fetch updates N containers; **prove the single-container
   path is byte-for-byte unchanged** (the compat guarantee).
5. (Optional) `AjaxUpdateTrigger` batching.

## Why this is now low-risk

The phantom hard part — "a single request that re-renders multiple AUC subtrees" — was never needed:
the trigger's action render already walks the full page and is merely gated to one container by `_u`.
The whole server change is **widening that gate to a set and framing the output when there's more than
one**. No new render endpoint, no fight with WO's component-action model, single-container path
untouched.

---

## Appendix: separating "updatable region" from "self-updating region" (independent cleanup)

`AjaxUpdateContainer` today does two jobs through one element, distinguished only by which bindings are
present:

1. **A passive updatable region** — a morph target that *something else* refreshes (a trigger with
   `updateContainerID="x"`). This is the overwhelmingly common use.
2. **A self-updating region** — one that refreshes *itself* (`frequency=` periodic poll, or an
   `observeFieldID`/`action` directly on the container), handling its own action in `handleRequest`.

The confusion is the `action` binding: on a passive region it is meaningless (never fires — see the
invariant above), but its name strongly implies "runs when this container updates." A reader cannot tell
from the tag which mode they are in. The clean design is **two elements**:

- `AjaxUpdateContainer` — passive target only. Bindings: `id`, `elementName`, `class`, `style`, `morph`,
  `onRefreshComplete`. **No `action`, no `frequency`, no `observeFieldID`.** Its whole contract is
  "a named region other things can morph."
- A self-updating element (e.g. `AjaxSelfUpdatingContainer`, or fold this into `AjaxPing`'s family) —
  carries `frequency`/`action`/`observeFieldID` and owns its own refresh, where `action` firing on
  self-refresh is the *expected* behaviour.

**Status:** a worthwhile API cleanup, but **independent of and not blocking the multi-update feature** —
multi-update works the same whether or not this split happens.

**Usage audit (done):** across all 17 of the user's apps, **none** put `action`, `frequency`, or
`observeFieldID` directly on an `AjaxUpdateContainer` — the self-updating-region mode is entirely unused
in application code. So shedding those bindings from the passive `AjaxUpdateContainer` would be a **clean
break** for the apps. The one real consumer is **internal**: AjaxSlim's own `AjaxPing` renders an
`<wo:AjaxUpdateContainer frequency=… stopped=…>` for its periodic poll. So the split is not "remove the
capability" but "move it": the passive `AjaxUpdateContainer` drops `action`/`frequency`/`observeFieldID`;
a self-updating element keeps them (AjaxPing already *is* essentially that element — its template would
target the self-updating variant instead of a frequency-bound AUC). Decide and sequence separately from
multi-update.

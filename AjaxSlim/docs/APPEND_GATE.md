# Append-gating the ajax response — a single-pass alternative to the senderID replay

_Parked design note. Theoretical. The current mechanism **works**; the core of the
Ajax framework is load-bearing and battle-tested, so rewriting it carries real risk
for a modest reward. This is filed for "one day when we have nothing else to do,"
not as a planned change. No code._

## TL;DR

AjaxSlim produces a partial response by running the request-response loop **twice**:
a normal loop whose output it captures and discards, then a **second `invokeAction`
replay** with the senderID blanked so only the targeted container appends. An
alternative — borrowed from how ng's ajax used to work — is to run the **normal loop
once** and **gate the response's append methods** so content emitted *outside* the
requested container is discarded. That deletes one full tree traversal per ajax
request and removes the most WO-hostile code in the framework.

The win is real but bounded: it makes the (unavoidable) tree walk happen **once
instead of twice**. It does **not** make the walk cheaper — repetitions outside the
target still iterate and still pull their `list` (see the measurement below). The
"don't pay for unrelated data access" dream is a *separate* project (request-scoped
memoization / stable `pageKey` caching) and is orthogonal to this one. They compose.

## How it works today (the two-pass replay)

`er.ajax.AjaxResponse.generateResponse()` (AjaxSlim):

```java
if (AjaxUpdateContainer.hasUpdateContainerID(_request)) {
    String originalSenderID = _context.senderID();
    _context._setSenderID("");                       // (1) blank the sender
    try {
        CharSequence originalContent = _content;     // (2) capture the 1st-loop output
        _content = new StringBuilder();              //     ...and throw it away
        userInfo.setObjectForKey(TRUE, AJAX_UPDATE_PASS);
        WOApplication.application().invokeAction(_request, _context);  // (3) SECOND pass
        _content.append(originalContent);            // (4) re-append the captured head/etc
        ...
    } finally {
        _context._setSenderID(originalSenderID);
    }
}
```

With the senderID blanked, `AjaxComponent.invokeAction` →
`AjaxUtils.shouldHandleRequest` matches only the targeted container(s) (by id
membership, not sender), so only their `handleRequest` appends content. The body
ends up containing just the targeted container(s)' freshly-rendered HTML.

Observable result: a surgical response body. Cost: the action/traversal phase runs
**twice** (the normal loop + the replay at step 3), plus a capture/blank/replay of
`_content`, plus mid-flight mutation of the context's senderID. This is the most
re-entrant, WO-hostile code in the framework, and it sits next to the
page-replacement cache surface that produced the cross-instance bleed bug.

## The measurement that motivates this (and bounds it)

Probe on `ScenarioInvoice` (invoice-stress): instrumented `lines()` (the rows
repetition `list`), `grandTotal()` (a derived total, different container), and
`serverTime()` (the background ticker's own content). Fired a targeted update of
**only** the `activityTicker` container.

| binding | fired during ticker-only update | belongs to the ticker? |
|---|---|---|
| `serverTime` | 1 | yes — the ticker's content |
| `lines` | **3** | **no — the rows repetition, a different container** |
| `grandTotal` | **1** | **no — the totals, a different container** |

Two takeaways:

1. **Updating one small container re-pulled bindings from unrelated containers.** If
   `lines()` were a DB fetch, a 10s background ticker would query the DB every tick
   for rows nobody asked to refresh.
2. **`lines` fired _more_ during the targeted update (3) than during a full page
   render (2)** — because the action-phase traversal must evaluate every
   `WORepetition`'s `list` to count positional element IDs (`0.7.3.2`…) and find the
   target element. **You cannot walk past a repetition without asking it how many
   children it has.** The traversal *is* the expense, and repetitions drag their data
   source into it.

This is the wall: append-gating suppresses *output*, not *traversal*. The `lines()`
cost survives **any** append-gating scheme. What append-gating removes is the
**second** traversal (the replay), i.e. it halves the count, not the per-walk cost.

## The proposed mechanism (single-pass append-gate)

Model it on ng's original ajax loop, which (per Hugi) was "a perfectly simple"
ordinary component request whose only ajax-specific behavior was: **content appended
outside a container is thrown out. No phase repeated.**

- Run the **normal** R-R loop once. Delete the `generateResponse` replay entirely —
  no senderID blanking, no `_content` capture/replay, no second `invokeAction`.
- Override the append surface on `AjaxResponse` so each append **no-ops when the gate
  says we're outside a requested container**.
- Drive the gate from the `currentUpdateContainerID` stack that
  `AjaxUpdateContainer.appendToResponse` **already** pushes/pops (with try/finally) —
  see AUC lines ~138-186. The bookkeeping the gate needs already exists and is
  already correctly nested.
- Gate rule: an append is allowed iff the current container stack indicates we are
  inside (or are) a requested target id (`isRequestedUpdateContainer`, already a set
  → multi-update works for free).

### Why this is achievable in WO when element-level gating is not

ng's *current* design gates inside `NGElement.appendToResponse` — the element decides
not to recurse. **We can't do that in WO**: the framework elements that emit most
content (`WOString`, `WORepetition` children, `WOConditional`, static HTML,
`WOForm`) are `com.webobjects.appserver._private` classes we don't control, so we
can't blanket-override their `appendToResponse`.

But we don't need to. ng's *original* loop gated at the **response** level, and the
response (`AjaxResponse`) is ours. **All** element output — including plain WO
elements — funnels through the response's append methods. Gating there intercepts
everything, sidestepping the `_private`-elements problem entirely.

### The full append surface that must be gated

From `javap` on `WOMessage`/`WOResponse` (JavaWebObjects 5.4.5). Gating only
`appendContentString` is **not** sufficient — WO's framework elements call several of
these directly (notably `_appendContentAsciiString`, the fast path for known-ASCII
static content, and `_appendTagAttributeAndValue` for attributes). Miss one and
ungated content leaks into the body. The complete set:

```
appendContentString(String)
appendContentHTMLString(String)
appendContentCharacter(char)
appendContentData(NSData)
appendContentHTMLAttributeValue(String)
_appendContentAsciiString(String)             // package-visible fast path — easy to miss
_appendTagAttributeAndValue(String,String,boolean)
appendContentDOMDocumentFragment(DocumentFragment)   // if used
```

Finite and enumerable, but all of them, or it leaks.

## What changes, what doesn't

**Deleted:** the entire `generateResponse` override (senderID blank, content
capture/blank/replay, second `invokeAction`). This is the big simplicity/robustness
win — it's the framework's most re-entrant code and is adjacent to the bleed-bug
cache surface.

**Reused as-is:** AUC's `setCurrentUpdateContainerID` push/pop stack;
`isRequestedUpdateContainer` set membership; the `<ajaxslim-fragment data-id>`
multi-update framing (it just moves into the gated append path).

**Unchanged costs:** the tree is still walked once; repetitions still iterate; the
`lines()` / DB-access cost is identical. Net vs. today: **one fewer full traversal
per ajax request**, never more.

## Caveats / open questions (resolve before any implementation)

1. **Complete append coverage.** Must gate all ~7 methods above. The
   `_appendContentAsciiString` fast path is the trap. **Verification spike:** gate
   all methods on a throwaway `AjaxResponse` subclass, render every playground
   scenario through it, and diff the gated output **byte-for-byte** against the
   current senderID-trick output. If they match across all scenarios, the surface is
   fully interceptable and the mechanism is sound.

2. **Nesting semantics (the one place ng and a WO port can subtly diverge).** When a
   requested container is nested inside a region that is itself gated-closed, does the
   gate **re-open** for the inner target, or does a closed ancestor keep all
   descendants closed? The WO port must match whatever ng's gate did here. **Need to
   read ng's `NGElement.appendToResponse` gate to pin the exact rule** (the ng source
   is not in this repo's working dirs).

3. **Form structure / side effects.** WO's append phase has side effects (hidden
   fields via `WOForm`, element-ID finalization, JS registration). Gating *content*
   of an out-of-target region drops its structural output too — which is **correct**
   for a partial fragment and is exactly what the senderID trick already achieves, so
   no regression is expected. The thing to confirm: nothing the *targeted* container
   depends on lives in a gated-closed sibling. (Target is gate-open, so its own form
   bits render normally.)

4. **The `_appendContentAsciiString` visibility.** It's package-visible
   (`com.webobjects.appserver`). `AjaxResponse` is in `er.ajax`, so confirm we can
   actually override it from outside the package (may require care, a bridge, or
   accepting that it can only be reached via the public methods that delegate to it —
   which would need verifying the delegation actually happens).

## Recommendation

File and leave parked. The current mechanism works; the core is risky to disturb for
a one-traversal saving. If we ever return to it, do caveat **#1 (the byte-for-byte
gate spike)** and **#2 (read ng's nesting rule)** *first* — they're cheap, they're
non-destructive (a throwaway subclass, no change to the live path), and together they
either validate the whole idea or kill it before any load-bearing code is touched.

### Related parked work

- The "don't pay for unrelated data access" dream → request-scoped memoization /
  stable `pageKey` identity (see `docs/AJAX_FROM_SCRATCH.md`). Orthogonal to this;
  composes with it (this makes the walk happen once; that makes the walk's data pulls
  free on repeat).
- The cache surface this would simplify away from → the cross-instance bleed bug
  (already fixed, but the replay lives next door).

# Common elements — sharing dynamic elements across Wonder (WO) and ng-objects

**Status:** design proposal, for review. No code yet.

## The problem

`AjaxPopUpButton` and `AjaxBrowser` (the morph-native wonder-select widgets) are needed in
ng-objects projects, not just Wonder ones. More broadly: we want a way to write a dynamic element
**once** and have it work in **both** frameworks.

A `WODynamicElement` cannot live in an ng project, and an `NGElement` cannot live in a WO project —
the two frameworks are clean-room separate at the API level (`com.webobjects.*` vs `ng.appserver.*`),
with no shared base type. So an element written against either framework's API is locked to it.

## Why the usual shortcuts don't apply here

- **Can't reuse a native base element.** Today `AjaxPopUpButton extends ERXPatcher…PopUpButton` — it
  decorates WO's own `WOPopUpButton`. ng has its own separate `NGPopUpButton`. The rendering logic
  these inherit is exactly the part that *can't* be shared, because it's framework-native. So a common
  element must **render itself**, not extend a framework base.
- **The client is already shared for wonder-select** (but not for other Ajax elements). `wonder-select.js`/`.css`
  are dependency-free vanilla and framework-agnostic — they self-enhance any `<select class="ajax-popup-button">`
  regardless of who served it. This is why the wonder-select pair is the **right pilot**: it's the one
  place where the client contract needs no reconciliation. (ng's other Ajax elements — ObserveField,
  SubmitButton, UpdateContainer, UpdateLink — were reimplemented from scratch with their *own*
  data-attribute client protocol, which has already diverged from wonder-slim's Prototype-based one.
  Those can't go common until their client protocols are unified first. wonder-select has no such debt.)

## The design: neutral element + per-framework holder

Mirrors the `WOComponentReference` / `NGComponentReference` indirection, but for **elements across two
frameworks**.

```
                         ┌─────────────────────────────┐
                         │   common-elements module    │   depends on NOTHING framework-specific
                         │                             │
                         │   interface CommonElement   │   ← the neutral lifecycle
                         │   interface CommonContext   │   ← neutral context (elementID, sender, …)
                         │   interface CommonResponse  │   ← neutral "append a string"
                         │   interface CommonBindings  │   ← neutral association bag
                         │                             │
                         │   class CommonPopUpButton   │   ← written ONCE, framework-free
                         │   class CommonBrowser       │
                         └─────────────────────────────┘
                            ▲                       ▲
         depends on         │                       │        depends on
   ┌────────────────────────┴───────┐   ┌───────────┴────────────────────────┐
   │  wonder-slim (Ajax)            │   │  ng-objects (ng-appserver)          │
   │                                │   │                                     │
   │  class WOCommonElementHolder   │   │  class NGCommonElementHolder        │
   │    extends WODynamicElement    │   │    implements NGElement             │
   │    owns a CommonElement,       │   │    owns a CommonElement,            │
   │    adapts WOContext↔CommonCtx, │   │    adapts NGContext↔CommonCtx,      │
   │    WOResponse↔CommonResponse,  │   │    NGResponse↔CommonResponse,       │
   │    WOAssociation↔CommonBinding │   │    NGAssociation↔CommonBinding      │
   └────────────────────────────────┘   └─────────────────────────────────────┘
```

**One holder per framework, not per element.** N common elements cost N neutral classes + 2 holders
(written once, ever). The holder is the only framework-coupled code.

### How a holder works

A holder **is** a real element of its framework, so the template engine finds and drives it natively.
On each lifecycle call it adapts the framework objects into the neutral shape, forwards to the common
element, and adapts results back. Roughly (WO side):

```java
public class WOCommonElementHolder extends WODynamicElement {
    private final CommonElement _element;

    public WOCommonElementHolder(String name, NSDictionary assoc, WOElement template) {
        super(name, assoc, template);
        // pick the common element by name; hand it a neutral view of the bindings
        _element = CommonElementRegistry.create(name, new WOBindings(assoc));
    }

    @Override public void appendToResponse(WOResponse r, WOContext c) {
        _element.appendToResponse(new WOResponseAdapter(r), new WOContextAdapter(c));
    }
    @Override public WOActionResults invokeAction(WORequest q, WOContext c) {
        return WOActionResultsAdapter.toWO(_element.invokeAction(new WOContextAdapter(c)));
    }
    @Override public void takeValuesFromRequest(WORequest q, WOContext c) {
        _element.takeValuesFromRequest(new WORequestAdapter(q), new WOContextAdapter(c));
    }
}
```

The ng holder is the same shape against `NGContext`/`NGResponse`/`NGAssociation`.

## Align with ng's render redesign — design to the *future* shape, not today's

**Critical context:** ng has an in-progress render-pipeline redesign (`ng-objects/docs/render-redesign.md`)
that changes the very signature a common element implements. The current `appendToResponse(NGResponse,
NGContext)` is being replaced by:

```java
void appendToResponse(NGOutput out, NGRenderContext context);
```

with three changes that bear directly on the neutral API:

1. **`NGOutput`** — a plain append-strings/bytes sink (HTTP buffer, WebSocket frame, deferred-fragment
   buffer, test `StringBuilder`) — replaces `NGResponse` as the render output. The element no longer
   constructs responses; output destination is plumbing, not state.
2. **`NGRenderContext`** — a slim traversal context (`component()`, `elementID()`, `page()`,
   `Optional<request>`, `shouldTraverse()`) — replaces the `NGContext` god-object for rendering.
   Actions get a separate **`NGActionContext extends NGRenderContext`** that adds `senderID()` and
   `currentElementIsSender()`.
3. **`shouldAppendToResponse()` → `shouldTraverse()`** — renamed and unified across all three phases
   (the "is this subtree part of the page being interacted with" gate is a *traversal* concern, applied
   identically to append / takeValues / invokeAction).

**The happy accident:** ng's redesigned shape is almost exactly the neutral API a cross-framework
abstraction wants — an output sink + a slim render context + a render/action context split. ng is
independently converging on the clean shape we need. So the neutral API should be **modeled on ng's
*future* API**, with these consequences:

- The neutral output type mirrors `NGOutput` (append sink), not `WOResponse`/`NGResponse`.
- The neutral context mirrors `NGRenderContext`, with a `CommonActionContext` adding sender info —
  matching ng's render/action split exactly.
- Use `shouldTraverse()` semantics, not the old `shouldAppendToResponse()` name.
- The **ng holder becomes nearly a pass-through** once the redesign lands (the neutral API ≈ ng's
  native API). The **WO holder does the real adapting** — mapping WO's `WOContext` god-object and
  `WOResponse` onto the clean neutral shape, and sourcing the `shouldTraverse` gate from
  `ERXAjaxContext`.

This is the core reason to design now rather than code now: pinning the neutral API to today's
string-append `NGResponse` shape would bake in an abstraction obsolete before it ships. **Open
question for review:** how settled is the redesign? If it's landing soon, design straight to it; if
it's still fluid, the neutral API should track its stable core (output sink + render/action context
split, which the doc marks as "still wanted regardless") and avoid the parts still in flux (immutable
cursor, `ElementLocal`, frame stack — all marked "likely dropped").

## The neutral API surface — and why it's faithfully thin

The good news from reading both frameworks: **WO's `WOContext` and ng's `NGContext` are structurally
parallel, often method-for-method, minus the `WO`/`NG` prefix** (by deliberate design — ng's appserver
API was modeled on WO's). The neutral API is essentially the *intersection*, and the intersection is
large. Concretely, the surface a wonder-select-class element needs:

| Neutral concept | WO | ng | Notes |
|---|---|---|---|
| append output | `WOResponse.appendContentString` | `NGResponse.appendContentString` | identical |
| read a binding | `WOAssociation.valueInComponent(component)` | `NGAssociation.valueInComponent(component)` | identical shape |
| boolean binding | `…booleanValueInComponent` | `NGAssociation.booleanValueInComponent` | both present |
| the component (KVC root) | `WOContext.component()` | `NGContext.component()` | neutral type can be `Object` (only passed back to bindings) |
| element id | `WOContext.elementID()` | `NGContext.elementID()` | string-izable on both |
| is-sender test | `context…senderID().equals(elementID())` | `NGContext.currentElementIsSender()` | ng has a convenience; WO assembles it |
| action url | `WOContext…componentActionURL()` | `NGContext.componentActionURL()` | identical concept |
| should-append (Ajax partial) | (via ERXAjaxContext) | `NGContext.shouldAppendToResponse()` | **corner — see risks** |
| update-container targeting | ERXAjaxContext machinery | `NGContext.containingUpdateContainerIDs()` etc. | **corner — see risks** |

For the **wonder-select pilot specifically**, the needed surface is even smaller: read a few
associations (`list`, `item`, `selection`/`selections`, `displayString`, `noSelectionString`, `id`,
`class`, `multiple`), merge a marker class, emit a `<select>` with options, inject two resources. No
`invokeAction` sender logic, no partial-update corners. That's why it's the safe first proof.

### Neutral interfaces (sketch — modeled on ng's *redesigned* shape)

```java
public interface CommonElement {
    void appendToResponse(CommonOutput out, CommonRenderContext ctx);
    default void takeValuesFromRequest(CommonRenderContext ctx) {}
    default CommonActionResults invokeAction(CommonActionContext ctx) { return null; }
}

// mirrors ng's NGOutput — an append sink, not a response object
public interface CommonOutput { void append(String s); }

// mirrors ng's NGRenderContext — slim traversal context
public interface CommonRenderContext {
    Object component();              // opaque KVC root, only handed back to bindings
    String elementID();
    String componentActionURL();
    boolean shouldTraverse();        // the unified gate (was shouldAppendToResponse)
}

// mirrors ng's NGActionContext — adds sender info, only present in action phases
public interface CommonActionContext extends CommonRenderContext {
    boolean currentElementIsSender();
}

public interface CommonBindings { CommonBinding get(String name); }
public interface CommonBinding  { Object value(CommonRenderContext ctx); boolean booleanValue(CommonRenderContext ctx); }
```

The split (`CommonRenderContext` vs `CommonActionContext`) is taken straight from ng's redesign, so the
ng holder maps 1:1 once that lands. The WO holder synthesizes both from `WOContext` (which still carries
everything on one object) — e.g. `currentElementIsSender()` is assembled from WO's
`senderID().equals(elementID())`, and `shouldTraverse()` is sourced from `ERXAjaxContext`.

Resource injection (`AjaxUtils.addScriptResourceInHead`) is framework-specific and does **not** belong
in the neutral element. Instead the common element *declares* its resources
(`Set<Resource> requiredResources()`) and each holder injects them its own way.

## Where it lives (module / dependency structure)

A new module, e.g. **`common-elements`** (or `ng-wo-common`), that depends on **nothing
framework-specific**. Then:

- `wonder-slim` `Ajax` module → depends on `common-elements`, adds `WOCommonElementHolder` + adapters.
- `ng-objects` `ng-appserver` (or a small `ng-common-elements`) → depends on `common-elements`, adds
  `NGCommonElementHolder` + adapters.

Dependency arrows point **only** from each framework *into* the common module. The common module never
imports `com.webobjects.*` or `ng.appserver.*`. (Open question below: does it live in its own repo, or
inside one of the existing repos?)

### Registration

Each framework registers its holder under the element names the common elements expose:
- ng already has the mechanism: `NGCorePlugin` does `.elementClass(NGPopUpButton.class, "popUpButton")`.
  An `NGCommonElementsPlugin` would do `.elementClass(NGCommonElementHolder.class, "AjaxPopUpButton")`
  (the holder reads *which* common element from the tag name).
- WO registers dynamic elements through its element-name → class lookup similarly.

## Risks / open corners

1. **Partial-update / morph context.** wonder-select itself doesn't need it, but the *next* common
   elements will. WO routes "should I append" through `ERXAjaxContext`; ng has it natively on
   `NGContext` (`shouldAppendToResponse`, `containingUpdateContainerIDs`). The neutral API can expose a
   thin `shouldAppendToResponse()`, but the WO adapter has to source it from `ERXAjaxContext`. Provable
   only on a real second element.
2. **Element-ID & sender semantics.** Both frameworks have `elementID`/`senderID`, but the *traversal*
   (how IDs are assigned during append/take/invoke) is engine-internal. As long as a common element only
   *reads* `elementID()` / `currentElementIsSender()` and never tries to drive traversal, this is safe.
   Elements that manage child templates (like `NGDynamicGroup` does) are a harder case — wonder-select
   has no children, so the pilot dodges it.
3. **Form-value extraction (`takeValuesFromRequest`).** wonder-select is a `<select>`; its value
   round-trips through the *native* select element handling. A common element that fully renders its own
   `<select>` must also fully handle pulling the selected value(s) back out of the request — this is the
   real meat that `WOPopUpButton`/`ERXPatcher` does today and that we'd be reimplementing neutrally.
   **This is the single biggest chunk of actual work** and the thing to validate first.
4. **Association/value coercion quirks.** WO's `NSArray`/`NSDictionary` vs plain Java collections; null
   handling; KVC differences. The neutral binding API hands back `Object`; coercion stays in the common
   element using plain Java, avoiding both frameworks' foundation types.
5. **Client-protocol unification (future).** Not a wonder-select problem (its client is already shared),
   but the moment we want *other* common Ajax elements, wonder-slim's Prototype client and ng's
   data-attribute client must converge on one contract. That's a separate, larger track — flagged so the
   common-element mechanism isn't oversold as "instantly shares all Ajax elements."

## Recommended path

1. **Pilot: `CommonPopUpButton` + `CommonBrowser`** as neutral elements, with `WOCommonElementHolder`
   and `NGCommonElementHolder`. Share `wonder-select.js`/`.css` (already agnostic). This proves the
   holder pattern, the neutral context/response/bindings API, and — critically — the
   `takeValuesFromRequest` select-value reimplementation (risk #3), on the case with the fewest corners.
2. **Harden the neutral API** from what the pilot actually needed (extract, don't pre-invent).
3. **Only then** consider common elements that drag in partial-update context or child templates, and
   separately, the client-protocol unification needed to make the *other* Ajax elements common.

## Decisions needed before coding

- **ng render-redesign timing (the gating decision).** How settled is `render-redesign.md`, and is it
  landing before or after this work? If before: design the neutral API straight to
  `NGOutput`/`NGRenderContext`/`NGActionContext`. If it's still fluid: track only its "still wanted
  regardless" core (output sink + render/action split + `shouldTraverse`) and avoid the in-flux parts.
  Either way the neutral API should *not* be pinned to today's `NGResponse`/`NGContext` shape. This is
  the main reason the work is at design stage rather than coding.
- **Module home:** new standalone repo for `common-elements`, or host it inside one of the existing
  repos (and which)? Affects the dependency/release story for both frameworks.
- **Naming:** element names common across both (`AjaxPopUpButton`?) or framework-idiomatic aliases?
- **Resource sharing:** is `wonder-select.js`/`.css` copied into both frameworks' resources, or served
  from the shared module? (A shared module shipping webserver-resources is possible but new ground.)
- **`takeValuesFromRequest` reimplementation (risk #3).** The select value-extraction logic currently
  provided by `WOPopUpButton`/`ERXPatcher` must be rewritten neutrally and validated on the pilot — the
  largest single chunk of real work. ng's render redesign unifies the traversal gate but does *not*
  do this for us.

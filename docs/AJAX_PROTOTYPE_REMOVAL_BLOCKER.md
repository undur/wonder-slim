# Prototype/Scriptaculous removal — binding-compatibility blocker

**Status:** leaning CLEAN BREAK; usage audit done (results below); final go not yet given — user is
mulling it over. The "63 bindings" worry below is the *theoretical* surface; the audit shows the
*real* surface across all of the user's apps is ~6 occurrences of 2 bindings. The blocker is
effectively de-fanged, pending the user's decision to start.

## Audit result (2026 — measured across all of the user's apps)

Grepped every app that uses Ajax.framework (nb, strimillinn, SoloWeb, USWebObjects, asi, Logman, Tatu,
lidamot, rebbi, helium5, Hugi, husvordurinn, concept, SWAPP, ExcelPublisher, MrBlinken — excluding
upstream `wonder/` and the framework itself).

**Element usage is rudimentary and concentrated:** ~97% of all Ajax-element uses are the five core
update elements — AjaxUpdateContainer (678), AjaxUpdateLink (432), AjaxObserveField (244),
AjaxSubmitButton (42), AjaxUpdateTrigger (35). The rest is a thin long tail (ModalContainer 24, the
wonder-select Browser/PopUpButton, scattered AutoComplete/Highlight/Expansion/InPlaceEditor/SortableList
in single digits).

**The Prototype/Scriptaculous BINDING surface is essentially unused:** of the ~63 suspect bindings,
across the entire app fleet — `effect`, `insertion`, `onComplete`, `evalScripts`, `asynchronous`, and
the rest are **ZERO**. The only non-zero hits: `onSuccess` (3), `onRefreshComplete` (3), and
`frequency` (~7 — and `frequency` is the poll interval, a kept concept, not Prototype eye-candy).
(Control: `action`=983, `id`=516, `updateContainerID`=282 confirm the grep works.)

**Conclusion:** the compatibility contract this doc was written to agonize over protects against
breakage that doesn't exist in practice. Clean break is safe. The total app-side punch-list is ~6
sites using `onSuccess`/`onRefreshComplete`, all the user's own code, ~10 minutes to migrate.

**The user's read on *why* usage is rudimentary:** not minimalism by preference — the existing elements
are "dated and often questionable," so they reach only for the four primitives that genuinely work and
avoid the rest. Implication: a clean modern rewrite of just those primitives would likely *expand*
willingness to use the framework, not merely preserve current usage.

## The one open design point (the only thing the audit didn't settle)

The post-update **callback capability** (`onRefreshComplete`/`onSuccess` — "run my JS when this update
finishes"). This is NOT Prototype eye-candy; it's a genuinely useful idea the user actually uses (~6
sites), and wonder-select / the flexible-upload reimpl already do this pattern natively. So "goodbye
Prototype" should mean goodbye to Prototype's *implementation* of it, NOT goodbye to the concept.
Options when the rewrite starts: (a) keep `onRefreshComplete`, fired from native fetch/morph completion;
(b) drop it, app JS listens to a native event the morph dispatches; (c) decide at AUC-rewrite time.
NOT yet decided — user still thinking.

## The concern (why this isn't a private refactor) — the THEORETICAL surface, now de-fanged by the audit above

The assessment frames ~32 elements as "Needs rewrite": keep the server contract, move the client off
Prototype.js/Scriptaculous onto fetch()+idiomorph. That direction is right, but it **understates the
migration cost in one specific way**: the Prototype/Scriptaculous dependency is not internal — it is
**exposed as public bindings** that apps have written into their templates. Those bindings are a
contract. Removing Prototype silently changes the meaning of, or removes, bindings real apps depend on.

This is wider than "effects." Three distinct categories leak through the public API.

## The surface (63 bindings, measured from the .api files)

### 1. Effects (~41 bindings) — Scriptaculous `Effect.*` eye-candy
`effect`, `beforeEffect`/`afterEffect` (+`*ID`, +`*Duration`), `hideEffect`/`showEffect`/`newEffect`/
`updateEffect`, `endeffect`/`starteffect`/`reverteffect`, `duration` and the many `*Duration` variants,
`highlightcolor`/`highlightendcolor`, `inactiveFade`, `slideUpDuration`/`slideDownDuration`/
`overlayDuration`/`resizeDuration`, `useJavascriptForHoverEffect`.
Declared on: AjaxUpdateLink, AjaxSubmitButton, AjaxDefaultSubmitButton, AjaxUpdateContainer,
AjaxHighlight, AjaxToggleLink, AjaxInPlace(Editor), AjaxExpansion, AjaxModalDialog, AjaxDraggable,
AjaxSortableList, AjaxSlider, AjaxDroppable, AjaxHoverable.
→ These name Scriptaculous animations directly. With no Scriptaculous, `effect="blind"` has nothing to
call.

### 2. Insertion (~4 bindings) — Scriptaculous DOM-insertion shortcuts (`Effect.PAIRS`)
`insertion`, `editInsertion`, `saveInsertion`, `cancelInsertion`.
Declared on: AjaxUpdateContainer, AjaxUpdateLink, AjaxSubmitButton, AjaxObserveField, AjaxExpansion,
AjaxInPlace.

### 3. The Prototype callback / Ajax.Request-options surface (~18 bindings) — THE DANGEROUS ONE
`onComplete`, `onSuccess`, `onFailure`, `onLoading`, `onBeforeUpdate`, `onBeforeSubmit`, `onRefreshComplete`,
`onClickBefore`, `onClickServer`, `onClick`, `onCreate`, `onExpansionComplete`, `evalScripts`,
`asynchronous`, `frequency`, `decay`, `observeFieldFrequency`, drag-drop callbacks.
Declared on the **most-used core elements**: AjaxUpdateContainer, AjaxUpdateLink, AjaxSubmitButton,
AjaxDefaultSubmitButton, AjaxObserveField, AjaxExpansion, AjaxInPlaceEditor, AjaxPing, etc.
→ These are NOT effects — they are the Prototype `Ajax.Request`/`Ajax.Updater`/`Ajax.Responder` options
model exposed as bindings. `onComplete="refreshSidebar()"` today fires inside Prototype's responder
lifecycle; under fetch() the firing point, ordering, and `this`/argument shape differ. `evalScripts`,
`asynchronous`, `frequency`/`decay` are literally Prototype option names. This category is where a
rewrite changes *behavior*, not just *animation*.

The concrete breakage example: an existing template
`<wo:AjaxUpdateLink action="$edit" updateContainerID="detail" effect="highlight" onComplete="refreshSidebar()"/>`
— under a naive rewrite, `effect="highlight"` does nothing (no Scriptaculous) and `onComplete` fires at
a different time with a different context, or not at all.

## The decision to make (the contract)

What is our compatibility stance toward these bindings when Prototype goes away? Candidate stances:

- **A. Tiered — keep callbacks, drop effects.** Re-wire the callback/lifecycle bindings (onComplete/
  onSuccess/onFailure/onLoading/onClickBefore/evalScripts/onRefreshComplete) to native fetch events so
  app *logic* survives; accept-but-no-op the effect/insertion/duration bindings (then remove them) since
  they only drove eye-candy. Most pragmatic: preserves behavior, sheds the cosmetic. The open sub-problem
  is fidelity of the callback re-wiring (firing point, args, `this`) — apps may rely on Prototype-specific
  timing.
- **B. Full back-compat shim.** Reimplement the Scriptaculous effects actually used (fade/blind/highlight/
  appear/slide) as small vanilla-JS/CSS equivalents, so `effect="highlight"` still animates. Nothing
  breaks; most effort, for eye-candy.
- **C. Clean break + migration guide.** Remove the bindings; document the replacements; bump a major
  version. Lowest framework complexity, highest app-side churn. Only viable if real usage is low.

## What's needed before deciding

**Audit real-world usage.** The contract should be driven by what would actually break, not the full
63-binding theoretical surface. Grep real app templates (nb, and any other Ajax.framework consumers)
for `effect=`, `insertion=`, `onComplete=`, `onSuccess=`, `frequency=`, etc. — produce a list of which
bindings and which effects are genuinely used in the wild. A surface that's theoretically 63 bindings
might be, say, 6 in practice (likely `onComplete`/`onSuccess`/`effect="highlight"`/`frequency`), which
would make stance A or even C cheap and safe. We don't know yet — measure first.

## Why this is captured as a blocker, not actioned now

Setting this contract is hard to reverse: it defines what breaks for existing apps, and it lands in the
binding API. The framework-internal Prototype usage can be modernized at our own pace, but the
**public-binding semantics cannot change without an explicit, agreed contract**. So: Prototype removal
is gated on (1) the real-world usage audit, then (2) picking a stance (A/B/C). Until then, the
"Needs rewrite" elements stay as-is — correct in direction, blocked on this contract.

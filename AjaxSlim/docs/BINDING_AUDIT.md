# AjaxSlim binding audit — per-element findings

> **Working document — contains private app names. Do not commit as-is** (anonymize or delete the
> evidence columns first). Generated 2026-07-05 from a three-source audit; the **Decision** column is
> yours.

## Method

Three evidence sources, cross-referenced per binding:

1. **Declared** — the binding appears in the element's `.apiext` (the authoritative API).
2. **Wired** — the element code actually reads it (all zero-usage candidates below were verified wired:
   they are live code paths with no users).
3. **Used** — scanned every template (`.html` inline tags + `.wod`) in every repo under `~/git` for the
   14 element names, extracting per-element attribute usage. `wonder` (er.ajax's own examples/tests) and
   `wonder-slim` (the playground, synthetic by design) are discounted — **"real usage" means the ~16
   actual application repos.**

The lens: AjaxSlim is a transitional framework whose surviving surface becomes the foundation of the
ng-objects Ajax framework — so the bias is *prove you deserve to live*. A drop from the declared API is
not necessarily a code change (for bindings inherited from stock WO superclasses the runtime keeps
honoring them); it is a decision about what the framework *supports*.

**Proposal legend:** `keep` · `keep (deprecated alias)` · `**drop**` (recommended) · `drop?` (weak /
paired decision — see note).

---

## AjaxUpdateContainer

*449 template uses; 20 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `id` | 446× / 20 apps: ExcelPublisher, Hugi, SWAPP, SoloWeb, Tatu, USWebObjects, asi, concept, helium, helium5, husvordurinn, lidamot, nb, ng-hafnium, ng-objects, ng-testapp2, rebbi, strimillinn, undur-deployment, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `elementName` | 75× / 9 apps: Tatu, USWebObjects, asi, lidamot, nb, ng-hafnium, ng-testapp2, strimillinn, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `morph` | — | wonder-slim | keep | new, AjaxSlim-native | |
| `onRefreshComplete` | 13× / 1 apps: nb | wonder, wonder-slim | keep | real use in the largest consumer | |
| `optional` | — | — | **drop** | the render-no-container-when-nested legacy trick; zero usage anywhere, ever | |

**Used but NOT declared:**
- `evalScripts` — er.ajax examples/playground only (wonder, wonder-slim)
- `frequency` — er.ajax examples/playground only (wonder, wonder-slim)
- `action` — 6× / 1 apps: nb
- `stopped` — er.ajax examples/playground only (wonder, wonder-slim)
- `decay` — er.ajax examples/playground only (wonder)
- `onLoading` — er.ajax examples/playground only (wonder, wonder-slim)
- `skipFunction` — er.ajax examples/playground only (wonder, wonder-slim)
- `asynchronous` — er.ajax examples/playground only (wonder)
- `fullSubmit` — er.ajax examples/playground only (wonder)
- `insertion` — er.ajax examples/playground only (wonder)
- `insertionDuration` — er.ajax examples/playground only (wonder)
- `observeFieldID` — er.ajax examples/playground only (wonder)
- `onComplete` — er.ajax examples/playground only (wonder)
- `onSuccess` — er.ajax examples/playground only (wonder)
- `parameters` — er.ajax examples/playground only (wonder)

## AjaxSelfUpdatingContainer

*3 template uses; 0 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `frequency` | — | wonder-slim | keep |  | |
| `stopped` | — | wonder-slim | keep |  | |
| `observeFieldID` | — | — | **drop** | **the structural drop**: container-observes-field shortcut duplicates AjaxObserveField; source of the action-does-not-fire trap (review M6) — dropping resolves it by construction; ASUC becomes purely "the periodic container" | |
| `fullSubmit` | — | — | **drop** | only exists to serve observeFieldID; falls with it | |
| `action` | — | wonder-slim | keep | periodic-only semantics now documented | |
| `id` | — | wonder-slim | keep |  | |
| `elementName` | — | wonder-slim | keep |  | |
| `morph` | — | — | keep |  | |
| `onRefreshComplete` | — | wonder-slim | keep |  | |
| `optional` | — | — | **drop** | same as AUC | |

## AjaxUpdateLink

*277 template uses; 16 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `action` | 271× / 16 apps: Hugi, MrBlinken, SoloWeb, Tatu, USWebObjects, asi, concept, helium, helium5, husvordurinn, lidamot, nb, ng-objects, ng-testapp2, rebbi, strimillinn | wonder, wonder-slim | keep |  | |
| `directActionName` | — | wonder | **drop** | 1 use ever, in er.ajax's own examples; conceptually odd (a direct action is a sessionless page-creator, the opposite of an ajax update). Dropping also removes the ?query-param path | |
| `updateContainerID` | 258× / 15 apps: Hugi, SoloWeb, Tatu, USWebObjects, asi, concept, helium, helium5, husvordurinn, lidamot, nb, ng-objects, ng-testapp2, rebbi, strimillinn | wonder, wonder-slim | keep |  | |
| `replaceID` | — | wonder, wonder-slim | **drop** | already agreed — #28 | |
| `string` | — | wonder | **drop** | label-as-binding, er.ajax examples only; children are the label | |
| `onClick` | — | wonder, wonder-slim | keep | zero real use BUT the element owns the onclick attribute (merges with the generated handler) — a passthrough onclick would double-attribute, so the binding must exist | |
| `onClickBefore` | 12× / 2 apps: asi, nb | wonder, wonder-slim | keep | real use in 2 apps (the confirm() gate) | |
| `onClickServer` | — | wonder, wonder-slim | **drop** | server-returned-JS response mode; er.ajax examples only; pairs with ignoreActionResponse — dropping both collapses the response-mode mutex to "update or nothing" | |
| `onSuccess` | — | wonder, wonder-slim | keep | hook family; real use of the family on AjaxObserveField | |
| `onComplete` | — | wonder, wonder-slim | keep | hook family | |
| `ignoreActionResponse` | — | — | **drop** | zero uses anywhere on any element — the fire-and-forget mode nobody fired; drops a whole response mode | |
| `function` | — | wonder, wonder-slim | **drop** | wrap-in-custom-JS hook, er.ajax examples only; functionName covers the real need | |
| `functionName` | — | wonder, wonder-slim | keep | the sortable integration depends on it (named JS function form) | |
| `elementName` | — | wonder, wonder-slim | drop? | zero real usage; rides with the button decision (the "render as something else" family) | |
| `button` | — | wonder, wonder-slim | drop? | zero real usage; PARKED-KEEP per the earlier button-naming decision ("useful") — re-confirm under the transitional lens | |
| `disabled` | 12× / 2 apps: SoloWeb, USWebObjects | wonder, wonder-slim | keep | real use; table-stakes | |
| `title` | 10× / 1 apps: nb | wonder, wonder-slim | keep |  | |
| `class` | 173× / 13 apps: Hugi, SoloWeb, Tatu, USWebObjects, asi, concept, helium5, lidamot, nb, ng-objects, ng-testapp2, rebbi, strimillinn | wonder, wonder-slim | keep |  | |
| `style` | 9× / 5 apps: Tatu, asi, husvordurinn, nb, strimillinn | wonder, wonder-slim | keep |  | |
| `id` | — | wonder, wonder-slim | keep |  | |
| `accesskey` | 4× / 1 apps: strimillinn | wonder, wonder-slim | keep | 1 production app | |

**Used but NOT declared:**
- `evalScripts` — er.ajax examples/playground only (wonder, wonder-slim)
- `insertion` — er.ajax examples/playground only (wonder, wonder-slim)
- `insertionDuration` — er.ajax examples/playground only (wonder, wonder-slim)
- `onFailure` — er.ajax examples/playground only (wonder, wonder-slim)
- `onLoading` — er.ajax examples/playground only (wonder, wonder-slim)
- `afterInsertionDuration` — er.ajax examples/playground only (wonder, wonder-slim)
- `beforeInsertionDuration` — er.ajax examples/playground only (wonder, wonder-slim)
- `formName` — er.ajax examples/playground only (wonder, wonder-slim)
- `value` — er.ajax examples/playground only (wonder, wonder-slim)

## AjaxSubmitButton

*83 template uses; 16 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `action` | 75× / 16 apps: SWAPP, SoloWeb, Tatu, USWebObjects, asi, helium, helium5, husvordurinn, lidamot, nb, ng-hafnium, ng-objects, ng-testapp2, strimillinn, undur-deployment, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `updateContainerID` | 68× / 16 apps: SWAPP, SoloWeb, Tatu, USWebObjects, asi, helium, helium5, husvordurinn, lidamot, nb, ng-hafnium, ng-objects, ng-testapp2, strimillinn, undur-deployment, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `replaceID` | — | — | **drop** | #28 | |
| `value` | 61× / 16 apps: SWAPP, SoloWeb, Tatu, USWebObjects, asi, helium, helium5, husvordurinn, lidamot, nb, ng-hafnium, ng-objects, ng-testapp2, strimillinn, undur-deployment, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `name` | — | — | **drop** | zero; the element computes its own name | |
| `button` | 14× / 1 apps: lidamot | wonder, wonder-slim | keep | real use (1 production app): renders input-button instead of link | |
| `useButtonTag` | — | — | drop? | zero anywhere; PARKED-KEEP (button-naming analysis) — re-confirm | |
| `formName` | — | wonder, wonder-slim | drop? | only needed by functionName / button-outside-form; falls or stays with functionName | |
| `functionName` | — | wonder, wonder-slim | drop? | submit-from-JS; er.ajax examples + playground only; if kept, formName stays too (they pair) | |
| `showUI` | — | wonder | **drop** | render-nothing-but-still-register mode; er.ajax examples only | |
| `onClick` | 8× / 1 apps: SoloWeb | wonder, wonder-slim | keep | real use (1 app) | |
| `onClickBefore` | — | wonder, wonder-slim | keep | hook family; real on AjaxUpdateLink | |
| `onClickServer` | — | wonder, wonder-slim | **drop** | pairs with it | |
| `onSuccess` | — | wonder, wonder-slim | keep | hook family | |
| `onComplete` | 1× / 1 apps: SoloWeb | — | keep | real use (1 app) | |
| `disabled` | — | — | keep | zero usage but table-stakes for a form control | |
| `ignoreActionResponse` | — | — | **drop** | zero anywhere | |
| `id` | 11× / 5 apps: SoloWeb, Tatu, nb, strimillinn, undur-deployment | wonder, wonder-slim | keep |  | |
| `elementName` | — | — | **drop** | zero on this element (the link-mode tag override) | |

**Used but NOT declared:**
- `evalScripts` — er.ajax examples/playground only (wonder, wonder-slim)
- `onLoading` — 3× / 1 apps: strimillinn
- `afterInsertionDuration` — er.ajax examples/playground only (wonder, wonder-slim)
- `beforeInsertionDuration` — er.ajax examples/playground only (wonder, wonder-slim)
- `formSerializer` — er.ajax examples/playground only (wonder, wonder-slim)
- `insertion` — er.ajax examples/playground only (wonder, wonder-slim)
- `insertionDuration` — er.ajax examples/playground only (wonder, wonder-slim)
- `onFailure` — er.ajax examples/playground only (wonder, wonder-slim)
- `updtaContainerID` — 1× / 1 apps: SoloWeb

## AjaxDefaultSubmitButton

*1 template uses; 0 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `action` | — | wonder-slim | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `updateContainerID` | — | wonder-slim | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `replaceID` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `formName` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `name` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `value` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `onClick` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `onClickBefore` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `onClickServer` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `ignoreActionResponse` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `onSuccess` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `onComplete` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `disabled` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `id` | — | wonder-slim | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `class` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |
| `accesskey` | — | — | keep | rides with AjaxSubmitButton — same surface, decisions inherit | |

## AjaxObserveField

*266 template uses; 19 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `observeFieldID` | 223× / 15 apps: Hugi, SoloWeb, Tatu, USWebObjects, asi, helium, helium5, lidamot, nb, ng-hafnium, ng-objects, ng-testapp2, rebbi, strimillinn, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `updateContainerID` | 251× / 16 apps: Hugi, SoloWeb, Tatu, USWebObjects, asi, helium, helium5, husvordurinn, lidamot, nb, ng-hafnium, ng-objects, ng-testapp2, rebbi, strimillinn, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `action` | 106× / 14 apps: Hugi, MrBlinken, Tatu, USWebObjects, asi, helium, helium5, nb, ng-hafnium, ng-testapp2, rebbi, strimillinn, undur-deployment, x-ng-hafnium-2026-05-23-pre-rewrite | wonder, wonder-slim | keep |  | |
| `fullSubmit` | 21× / 3 apps: nb, ng-objects, ng-testapp2 | wonder | keep | real use | |
| `observeDelay` | — | wonder, wonder-slim | keep | the modern spelling | |
| `observeFieldFrequency` | 28× / 8 apps: Hugi, Tatu, USWebObjects, helium, helium5, nb, rebbi, strimillinn | wonder | keep (deprecated alias) | 28 real uses across 7+ apps — the deprecated alias thoroughly earns its keep | |
| `onBeforeSubmit` | — | — | **drop** | the JS submit-gate: zero uses anywhere, ever | |
| `onSuccess` | 3× / 1 apps: nb | — | keep | real use (largest consumer) | |
| `onComplete` | — | — | drop? | zero usage; keep only if the hook family should stay symmetrical | |
| `name` | — | — | **drop** | zero; purpose unclear even in legacy | |
| `id` | — | — | **drop** | wrapper cosmetic, zero | |
| `elementName` | 13× / 2 apps: asi, nb | wonder, wonder-slim | keep | real use (wrapper tag) | |
| `class` | 10× / 2 apps: asi, nb | wonder, wonder-slim | keep | real use | |
| `style` | — | — | **drop** | wrapper cosmetic, zero (class is used; keep class) | |

## AjaxModalContainer

*17 template uses; 4 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `label` | 17× / 4 apps: USWebObjects, helium, helium5, strimillinn | wonder, wonder-slim | keep |  | |
| `title` | — | wonder, wonder-slim | keep |  | |
| `closeLabel` | 15× / 4 apps: USWebObjects, helium, helium5, strimillinn | wonder | keep |  | |
| `open` | — | — | **drop** | auto-open-on-render: zero usage anywhere | |
| `id` | — | wonder, wonder-slim | keep |  | |
| `class` | 12× / 4 apps: USWebObjects, helium, helium5, strimillinn | wonder, wonder-slim | keep |  | |
| `style` | 4× / 3 apps: helium, helium5, strimillinn | — | keep |  | |

**Used but NOT declared:**
- `height` — er.ajax examples/playground only (wonder)
- `width` — er.ajax examples/playground only (wonder)
- `ajax` — er.ajax examples/playground only (wonder)
- `href` — er.ajax examples/playground only (wonder)
- `action` — er.ajax examples/playground only (wonder)

## AjaxBusySpinner

*6 template uses; 4 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `id` | — | wonder-slim | keep |  | |
| `class` | 1× / 1 apps: strimillinn | — | keep |  | |
| `style` | — | — | drop? | zero usage; tiny element, cheap either way | |
| `delay` | 1× / 1 apps: strimillinn | — | keep |  | |
| `fade` | 1× / 1 apps: strimillinn | — | keep |  | |

## AjaxPing

*4 template uses; 1 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `updateContainerID` | — | wonder-slim | keep | the modern spelling | |
| `targetContainerID` | 3× / 1 apps: nb | wonder | keep (deprecated alias) | real uses in production — the alias earns its keep | |
| `cacheKey` | 4× / 1 apps: nb | wonder, wonder-slim | keep |  | |
| `frequency` | 4× / 1 apps: nb | wonder, wonder-slim | keep |  | |
| `onBeforeUpdate` | — | — | drop? | zero real usage on either ping element; the gate for expensive refreshes — drop from BOTH or keep on both (AjaxPing internally forwards it to its embedded AjaxPingUpdate) | |
| `stop` | 2× / 1 apps: nb | — | keep | real use; NOTE the naming clash with ASUC's `stopped` — rename candidate via the deprecated-alias pattern | |
| `id` | — | wonder, wonder-slim | keep |  | |

## AjaxPingUpdate

*3 template uses; 0 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `updateContainerID` | — | wonder-slim | keep |  | |
| `targetContainerID` | — | wonder, wonder-slim | keep (deprecated alias) |  | |
| `cacheKey` | — | wonder, wonder-slim | keep |  | |
| `onBeforeUpdate` | — | wonder, wonder-slim | drop? | see AjaxPing — decide as a pair | |

## AjaxPopUpButton

*14 template uses; 2 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `list` | 14× / 2 apps: nb, strimillinn | wonder-slim | keep |  | |
| `item` | 14× / 2 apps: nb, strimillinn | wonder-slim | keep |  | |
| `selection` | 14× / 2 apps: nb, strimillinn | wonder-slim | keep |  | |
| `displayString` | 8× / 2 apps: nb, strimillinn | — | keep |  | |
| `noSelectionString` | 9× / 2 apps: nb, strimillinn | wonder-slim | keep |  | |
| `value` | — | — | **drop** | the string-value alternative to object selection: zero usage. Inherited surface — the runtime keeps honoring it either way; dropping is an API-declaration decision | |
| `selectedValue` | — | — | **drop** | pairs with value | |
| `disabled` | — | — | keep | zero usage but table-stakes for a form control | |
| `escapeHTML` | — | — | **drop** | zero | |
| `otherTagString` | — | — | keep (deprecated alias) | already deprecated (native passthrough supersedes it) | |
| `id` | 8× / 2 apps: nb, strimillinn | wonder-slim | keep |  | |
| `name` | — | — | **drop** | zero | |
| `class` | 4× / 2 apps: nb, strimillinn | — | keep |  | |
| `style` | — | — | **drop** | zero (class is used) | |

## AjaxBrowser

*12 template uses; 2 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `list` | 12× / 2 apps: husvordurinn, strimillinn | wonder-slim | keep |  | |
| `item` | 12× / 2 apps: husvordurinn, strimillinn | wonder-slim | keep |  | |
| `selections` | 12× / 2 apps: husvordurinn, strimillinn | wonder-slim | keep |  | |
| `selectedValues` | — | — | **drop** | pairs with value | |
| `value` | — | — | **drop** | as AjaxPopUpButton | |
| `multiple` | 12× / 2 apps: husvordurinn, strimillinn | wonder-slim | keep |  | |
| `displayString` | 10× / 2 apps: husvordurinn, strimillinn | wonder-slim | keep |  | |
| `otherTagString` | — | — | keep (deprecated alias) | already deprecated | |
| `disabled` | — | — | keep | table-stakes | |
| `escapeHTML` | — | — | **drop** | zero | |
| `id` | 3× / 1 apps: strimillinn | wonder-slim | keep |  | |
| `name` | — | — | **drop** | zero | |
| `class` | 5× / 1 apps: husvordurinn | — | keep |  | |
| `style` | — | — | **drop** | zero | |

**Used but NOT declared:**
- `noSelectionString` — 12× / 2 apps: husvordurinn, strimillinn

## AjaxSortable

*3 template uses; 1 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `listID` | 3× / 1 apps: strimillinn | wonder-slim | keep |  | |
| `action` | 3× / 1 apps: strimillinn | wonder-slim | keep |  | |
| `updateContainerID` | 3× / 1 apps: strimillinn | wonder-slim | keep |  | |

## AjaxUpdateTrigger

*35 template uses; 5 real apps.*

| Binding | Real-app usage | wonder / playground | Proposal | Note | Decision |
|---|---|---|---|---|---|
| `updateContainerID` | 16× / 2 apps: asi, nb | wonder-slim | keep |  | |

**Used but NOT declared:**
- `updateContainerIDs` — 19× / 4 apps: Tatu, nb, rebbi, strimillinn
- `resetAfterUpdate` — er.ajax examples/playground only (wonder)

---

## Cross-element findings

### Gaps — declare these

1. **AjaxBrowser `noSelectionString`** — 12 uses in 2 production apps, properly wired (surfaced as
   `data-placeholder` for the widget), never declared. **Declare it.**
2. **AjaxUpdateTrigger `updateContainerIDs`** — the deprecated plural: 19 real uses in 4 apps, kept in
   code as an alias, invisible in the API file. **Declare + `<deprecated>`** (the `targetContainerID`
   pattern).

### Migration hazards (not drops — landmines for migrating apps)

1. **`action` on a plain AjaxUpdateContainer** (one large consumer app does this) now throws with the
   AjaxSelfUpdatingContainer pointer — loud and correct, but know it before migrating.
2. **The passthrough trap**: legacy transport/effect bindings (`onLoading`, `onFailure`, `evalScripts`,
   `insertion*`, `effect*`, …) in old templates will silently render as **HTML attributes** on
   passthrough elements (one production app has `onLoading` today). Proposed fix, fail-loudly style: a
   shared **legacy-binding reject-list** on the AjaxSlim elements, same pattern as the passive
   container's rejection of self-updating bindings — migration errors instead of silent attributes.
3. One app has a live `updtaContainerID` (sic) typo that has silently done nothing for years — the
   template-validation pitch in one line.

### Naming (parked, listed for completeness)

- `stop` (AjaxPing) vs `stopped` (ASUC) — same concept, two names; alias-and-deprecate candidate.
- `onClickBefore` (link/buttons) vs `onBeforeSubmit` (observe field, a drop candidate anyway) vs
  `onBeforeUpdate` (pings, a drop candidate) — if the drops land, the inconsistency mostly dissolves.

### Summary

~120 declared bindings → **~25 drop candidates** (zero real usage, all verified wired), **2 gaps** to
declare, core surface strongly validated. The elements' shapes are right; the legacy periphery never
earned its ticket.

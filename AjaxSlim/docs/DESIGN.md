# AjaxSlim — design

## What AjaxSlim is

AjaxSlim is a from-scratch, **morph-native, fetch-based, zero-Prototype/Scriptaculous** rebuild of
the *actually-used* core of the legacy Wonder Ajax framework. It is deliberately slim:

- **Morph-native.** DOM updates reconcile the live DOM against freshly-rendered HTML via
  [Idiomorph](https://github.com/bigskysoftware/idiomorph) by default (preserving focus, scroll,
  selection, input values and unchanged subtrees), rather than blowing away `innerHTML`.
- **Fetch-based.** The client runtime uses the platform `fetch()` API. No `XMLHttpRequest` wrapper,
  no `Ajax.Updater`.
- **Zero Prototype / Scriptaculous.** No `prototype.js`, no `effects.js`, no `$`, no
  `Object.extend`, no `Effect.*`. The only client dependency is `idiomorph.js`.
- **Drop-in `er.ajax` replacement.** Elements live in package `er.ajax`, same names as legacy, so a
  template using `<wo:AjaxUpdateContainer>` binds to AjaxSlim's element with no template change. The
  two frameworks ship the same class names and therefore **cannot share a classpath** — AjaxSlim
  *replaces* the legacy Ajax framework, it does not co-exist with it.
- **ERExtensions-only dependency.** ERExtensions carries the WO-Ajax substrate
  (`ERXAjaxApplication` / `ERXAjaxSession` / `ERXAjaxContext`, `ERXResponseRewriter`,
  `ERXDynamicElement`) that the elements stand on. AjaxSlim adds nothing else.

The legacy `Ajax` framework remains untouched as the donor/legacy reference.

## Architecture

### Bootstrap (the framework principal)

`er.ajax.AjaxSlim` (extends `ERXFrameworkPrincipal`) is the principal, modeled on the legacy
`er.ajax.Ajax`. On `finishInitialization` it:

1. Registers `AjaxRequestHandler` under the `ajax` request-handler key (unless already registered).
   This handler is a `WOComponentRequestHandler` that flips `enableShouldNotStorePage()` so ajax
   action requests don't pollute the page cache.
2. Installs `AjaxResponse.AjaxResponseDelegate` on the `ERXAjaxApplication`, which repairs the
   double-click / null-action-result border case by reconstructing an `AjaxResponse` from the
   `_u` query parameter.

It **drops** the legacy comet/push request handler (`AjaxPushRequestHandler`) — server-push is out
of scope.

### Response plumbing

- `AjaxResponse` (port of legacy) is the `WOResponse` subclass that drives the **ajax update pass**:
  when a request carries an update-container id, `generateResponse()` re-invokes the action with the
  `_ajaxUpdatePass` flag set so the targeted `AjaxUpdateContainer` (matched by id, not sender) renders
  its fragment. It also hosts the `AjaxResponseDelegate` and the optional `AjaxResponseAppender`
  mechanism.
- `AjaxUtils` (trimmed port) supplies resource injection (`addScriptResourceInHead` defaulting to the
  `AjaxSlim` framework), `createResponse`, the script header/footer helpers, `ajaxComponentActionUrl`,
  and `shouldHandleRequest`.

### Element model

- `AjaxDynamicElement` / `AjaxComponent` — base classes (ported as-is) giving Ajax elements their
  `invokeAction` → `shouldHandleRequest` → `handleRequest` routing and resource-injection helpers.
- `AjaxUpdateContainer` — the refreshable region (see below).
- `AjaxUpdateLink` / `AjaxObserveField` / `AjaxSubmitButton` / `AjaxDefaultSubmitButton` /
  `AjaxUpdateTrigger` — the rest of the core element spine, all funnelling into the shared
  `ajaxslim.js` fetch + morph core (see "Core element spine" below).
- `AjaxPopUpButton` / `AjaxBrowser` — the wonder-select widgets (searchable, morph-native single /
  multi select), driven by `wonder-select.js` / `wonder-select.css`.

### JS runtime

`ajaxslim.js` + `idiomorph.js`, injected into `<head>` by `AjaxUpdateContainer`.

## The JS runtime contract (`ajaxslim.js`)

A single namespace, `AjaxSlim`, with an `AUC` sub-object. The registry is a **`Map` keyed by DOM id**
— not `window`-globals built by name — so ids that aren't valid JS identifiers (notably UUIDs with
`-`) work, and repeated registration under morphing is idempotent.

### API

| Call | Behavior |
| --- | --- |
| `AjaxSlim.AUC.register(id, options)` | Store this container's `options` (currently `{ onRefreshComplete }`) keyed by `id`. Safe to call repeatedly. |
| `AjaxSlim.AUC.update(id [, options])` | Fetch the container's `data-updateUrl` and morph/replace the response into the element; fire `onRefreshComplete`. |
| `AjaxSlim.AUC.registerPeriodic(id, canStop, stopped, frequencySeconds)` | `setInterval`-based periodic refresh. Clears any prior timer for `id` first (idempotent). |
| `AjaxSlim.AUC.stopPeriodic(id)` | Clear and forget the periodic timer for `id`. |
| `AjaxSlim.AUC.observeField(id, fieldId, fullSubmit)` | Refresh container `id` on the named field's change. Thin wrapper over `ASB.observeField` (`fullSubmit` honored: `true` submits the form, `false` re-fetches the container). |
| `AjaxSlim.AUL.update(targetId, actionUrl, options)` | Update-link: `fetch` the action URL and morph the result into `targetId`. `options`: `{ replace, onSuccess, onComplete }`. |
| `AjaxSlim.AUL.request(actionUrl, options)` | Update-link with no target — fire the action and run any returned `<script>`s (e.g. an `AjaxUpdateTrigger` in the response). |
| `AjaxSlim.ASB.update(targetId, form, options)` | Background-submit `form` (POST, serialized) and morph the result into `targetId`. `options`: `{ submitButtonName, replace, onSuccess, onComplete }`. |
| `AjaxSlim.ASB.request(form, options)` | Background-submit `form` with no morph target; run returned scripts. |
| `AjaxSlim.ASB.partial(targetId, fieldId, options)` | Submit ONLY `fieldId` (+ the `_partialSenderID` marker) and morph `targetId`. |
| `AjaxSlim.ASB.observeField(targetId, fieldId, frequency, partial, observeDelay, options)` | Bind native `change`/`input` (debounced) on `fieldId`; on change do a partial submit (`partial=true`), full-form submit (`partial=false` + `targetId`) or fire-and-forget. Idempotent per (field × signature). |
| `AjaxSlim.ASB.observeDescendentFields(targetId, containerId, frequency, partial, observeDelay, options)` | Same, for every descendant field of `containerId`. |
| `AjaxSlim.queryString(additionalParams)` | Turn an object / query-string / null into a leading-`&` URL fragment (used by named-function update links). |

All POST/GET fetches send `x-requested-with: XMLHttpRequest` (required: `ERXAjaxApplication.isAjaxRequest`
keys on it). Every path routes through one shared `fetchAndMorph(targetId, url, body?, onDone)` core:
`body` present ⇒ POST; `targetId` null ⇒ run the response's scripts instead of morphing a container.

### The update flow

`update(id)`:

1. `document.getElementById(id)` → read `data-updateUrl`.
2. Append `_u=<id>` (the update-container-id key `ERXAjaxApplication.KEY_UPDATE_CONTAINER_ID`) plus a
   neutral cache-buster `_=<timestamp>`, via `URLSearchParams` (preserving existing query params).
   **`_r` is deliberately not used as the cache-buster** — `ERXAjaxApplication` treats `_r` as the
   "ajax replacement" marker.
3. `fetch(url, { credentials: 'same-origin', headers: { 'x-requested-with': 'XMLHttpRequest' } })`.
   The **`x-requested-with` header is required**: `ERXAjaxApplication.isAjaxRequest` keys on it, so
   without it the server would not treat the request as ajax and would not return a fragment.
4. Read `response.text()`, re-resolve the element (a parent morph may have replaced the node), then:
   - if `data-morph != "false"`: `Idiomorph.morph(receiver, html, { morphStyle: 'innerHTML',
     callbacks })` with the `data-morph-ignore` / `-preserve` / `-preserve-style` escape hatches;
   - else: plain `innerHTML` replacement.
   In both paths `<script>`s are stripped before insertion and then run afterwards, each in its own
   `try/catch` (one bad fragment logs and continues rather than killing siblings).
5. Fire the registered `onRefreshComplete` hook, if any.

### The HTML contract (emitted by `AjaxUpdateContainer.java`)

- `data-updateUrl` — the ajax component-action URL to refetch this container from.
- `data-morph` — `"true"` (Idiomorph reconcile) or `"false"` (classic replace). **Always emitted
  explicitly** so the JS side has a single, unambiguous source of truth; an explicit
  `data-morph="false"` is a permanent opt-out even after `MORPH_BY_DEFAULT` flips.

The element also emits, inside a script block: one `AjaxSlim.AUC.register('id', {...})` call, plus an
optional `registerPeriodic(...)` (when `frequency` is bound) and `observeField(...)` (when
`observeFieldID` is bound).

## Core element spine

The remaining update-driving elements all emit a small declarative `onclick` (or registration
script) that calls into the `ajaxslim.js` runtime, instead of the legacy `new Ajax.Updater(...)` /
`new Ajax.Request(...)`. They share the one `fetchAndMorph` core, so a target with
`data-morph="true"` is reconciled, not blown away.

### `AjaxUpdateLink`

Renders an anchor (or `button`) whose click fires a server action and morphs the result into
`updateContainerID` (via `_u`) or `replaceID` (via `_r`). Emits
`AjaxSlim.AUL.update('target', 'actionUrl', {...})`, or `AjaxSlim.AUL.request('actionUrl', {...})`
when there is no target. `functionName` renders a named `function(additionalParams){…}` instead of an
inline handler (the params are appended via `AjaxSlim.queryString`).

- **Kept:** `action`, `directActionName`, `updateContainerID`, `replaceID`, `elementName`, `button`,
  `string`, `class`, `style`, `id`, `title`, `accesskey`, `disabled`, `function`, `functionName`,
  `onClick` (client hook, runs after the request is issued), `onClickBefore` (gate), `onClickServer`
  (server-returned JS), `ignoreActionResponse`, and **`onComplete` / `onSuccess` kept as post-update
  JS hooks** — wired to the runtime's `onDone`, run after the morph completes. The audit showed
  `onComplete`/`onSuccess` are the only callback bindings real apps use, and they map cleanly onto
  "run this after the morph", so they stay.
- **Dropped:** all Scriptaculous effect/insertion bindings
  (`effect`/`beforeEffect`/`afterEffect`/`*EffectID`/`*Duration`, `insertion`/`*InsertionDuration`)
  and the Prototype-`Ajax.Request` transport options `onLoading`/`onFailure`/`onException`/
  `evalScripts`/`asynchronous` — the transport that consumed them is gone.

### `AjaxObserveField` (the real one — replaces the runtime stub)

Watches a single field (`observeFieldID`) or, with no `observeFieldID`, every descendant field (it
renders a wrapper element and emits `observeDescendentFields`). On change it does an Ajax submit and
optional container morph. Emits `AjaxSlim.ASB.observeField(...)` / `observeDescendentFields(...)`,
which bind native `change` (always) + `input` (when debounced) listeners — no Prototype
`Form.Element.Observer`, no `Form.serialize`.

- **`fullSubmit` is now real, both ways.** `fullSubmit=true` ⇒ the runtime serializes the whole form
  (`URLSearchParams`, submits excluded) and POSTs it (`ASB.update`); `fullSubmit=false` (default) ⇒
  **partial submit**: only the changed field's value plus the `_partialSenderID` marker are POSTed
  (`ASB.partial`), so `ERXWOForm` treats the field's form as submitted just like the legacy partial
  path. The earlier minimal stub ignored `fullSubmit` and always re-fetched; this replaces it.
- **Kept:** `observeFieldID`, `updateContainerID`, `action`, `fullSubmit`, `observeDelay` /
  `observeFieldFrequency` (both collapse to a debounce in ms — the larger wins), `onBeforeSubmit`
  (gate; return false to deny), `onComplete`/`onSuccess` (post-update hooks), `id`/`elementName`/
  `class`/`style` (the wrapper), `name` (the ajax-submit-button name the server matches on).
- **Dropped:** the Prototype option dictionary
  (`onLoading`/`onException`/`insertion`/`evalScripts`).
- The `AJAX_SUBMIT_BUTTON_NAME` server protocol is preserved: the element's `name` is passed as
  `submitButtonName` and the runtime appends it to the POST body, and `invokeAction` matches on
  `ERXAjaxApplication.ajaxSubmitButtonName(request)` exactly as before.

### `AjaxSubmitButton` / `AjaxDefaultSubmitButton`

Background form submit + morph. Emits `AjaxSlim.ASB.update('id', formRef, {...})` or
`AjaxSlim.ASB.request(formRef, {...})`, where `formRef` is `this.form` or `document.<formName>`. The
form is serialized in the runtime with `URLSearchParams` (submits/images/files excluded — the legacy
`serializeWithoutSubmits` behavior).

- **The `AJAX_SUBMIT_BUTTON_NAME` / partial-submit wire contract is preserved.**
  `KEY_AJAX_SUBMIT_BUTTON_NAME` is still `"AJAX_SUBMIT_BUTTON_NAME"`, `KEY_PARTIAL_FORM_SENDER_ID`
  still `"_partialSenderID"`; the button's `name` is passed to the runtime as `submitButtonName` and
  appended to the body, and `invokeAction` keeps the multiple-submit-form matching on it.
- **Kept:** `action`, `name`, `value`, `id`, `class`, `style`, `title`, `tabindex`, `accesskey`,
  `onClick`, `onClickBefore`, `onClickServer`, `onComplete`/`onSuccess` (post-update hooks), `button`,
  `useButtonTag`, `formName`, `functionName`, `showUI`, `updateContainerID`, `replaceID`,
  `elementName`, `disabled`. `formSerializer` is still *accepted* in the `.api` but is a **no-op** —
  custom JS form serializers were a Prototype-era escape hatch; the runtime always serializes itself.
- **Dropped:** all effect/insertion bindings + the Prototype option dictionary
  (`onLoading`/`onFailure`/`evalScripts`/`asynchronous`).
- `AjaxDefaultSubmitButton` (the off-screen "Enter submits this" default button) is ported as a
  subclass and reuses the parent's emission, then appends `; return false;` to cancel the native
  post. Its legacy **IE&lt;9 keypress workaround** (Prototype `Event.observe` / `$$` / `fireEvent`)
  is **dropped** — modern browsers fire the default submit button's click on Enter natively. The
  legacy `AjaxModalDialog.isInDialog` focus-ring tweak is also dropped (ModalContainer isn't built
  yet).

### `AjaxUpdateTrigger`

Server-side-only, no UI: when rendered into a response it forces named containers elsewhere to
refresh. Emits, per target, `if (document.getElementById('id')) { AjaxSlim.AUC.update('id'); }`
(replacing the legacy `new Ajax.Updater(...)` per target). `evalScripts` is dropped (the fetch path
always runs the fragment's scripts, isolated per-script). `updateContainerID` / `updateContainerIDs`
/ `resetAfterUpdate` are kept.

### `AjaxPopUpButton` / `AjaxBrowser` (wonder-select)

Ported essentially verbatim from the prototype — they are already dependency-free custom-element
wrappers (no Prototype). `AjaxPopUpButton extends ERXPatcher.DynamicElementsPatches.PopUpButton` and
`AjaxBrowser extends …Browser`; each just (1) merges the `ajax-popup-button` marker class onto the
rendered `<select>` and (2) injects `wonder-select.js` / `wonder-select.css`. `wonder-select.js` is a
custom element that owns the native `<select>` and renders its own trigger/search/options subtree, so
it survives morphing (no foreign sibling for morph to strip) and re-applies state in
`connectedCallback`. `AjaxBrowser` additionally surfaces `noSelectionString` as a `data-placeholder`
attribute (a multi-select renders no "no selection" option to read it from). No code changes were
needed beyond moving the files into AjaxSlim.

### `AjaxBusySpinner`

Shows a busy indicator while any ajax request is in flight. The runtime carries an **ajax-activity
broker**: `fetchAndMorph` (the single choke point for all ajax activity) keeps an in-flight counter
and, on the 0↔1 transitions, sets/clears `data-ajaxslim-busy="true"` on `<html>` and dispatches
`ajaxslim:busy` / `ajaxslim:idle` events on `document`. The spinner is then **pure CSS** — hidden by
default, revealed by `html[data-ajaxslim-busy] .ajaxslim-busy-spinner` — so a bare
`<wo:AjaxBusySpinner/>` needs no JavaScript of its own. Three pulsing dots, no images.

Divergence from legacy: the legacy spinner was an `AjaxComponent` carrying ~17 bindings (busyClass,
divID, watchContainerID, onCreate/onComplete, and the spin.js option set — lines/length/width/radius/
color/speed/trail/shadow + a `spinOpts` JSON built via `org.json`), driven by a global Prototype
`Ajax.Responders.register`, and it shipped `spin.js` + `prototype.js` + `effects.js`. In practice every
real use was a bare `<wo:AjaxBusySpinner/>` with no bindings, so AjaxSlim drops the entire config
surface, spin.js, the Prototype responder, and the org.json dependency: id/class/style only, CSS-driven
off the activity broker. Apps wanting a bespoke indicator can style `.ajaxslim-busy-spinner` or listen
for the `ajaxslim:busy`/`ajaxslim:idle` events.

### `AjaxModalContainer`

Renders a trigger button that opens its inline children in a modal, built on the native
`<dialog>` element. The trigger carries `data-ajaxslim-modal-open="<dialogId>"`; `ajaxslim-modal.js`
(one delegated click listener on `document`, so it survives morphs with no re-init) opens that dialog
with `showModal()`. The close button is a `<form method="dialog">` submit, which the platform closes
natively. `<dialog>` provides the backdrop (`::backdrop`), focus trapping and Esc-to-close for free.

Divergence from legacy: the legacy element wrapped the **iBox** lightbox (Prototype) — it **relocated**
the content node to `<body>` (hence its `data-morph-ignore` relocation hack), drew its own overlay, and
supported iframe (`href`), `directActionName`, and ajax-fetched (`ajax`/`action`) content modes plus
`skin`/`locked`/`secure`. Every real use across the apps is the **inline-content** pattern (a labelled
button popping its children in a confirm dialog with a close button), so AjaxSlim implements only that:
`label`/`closeLabel`/`title`/`class`/`style`/`id`/`open`. The content stays **in place** in the DOM
(native `<dialog>` doesn't relocate it), so forms/links inside submit and navigate normally and there is
no morph collision — the relocation hack is gone. No iBox, no Prototype, no iframe/direct-action/skin
machinery.

## Divergence from the legacy Ajax framework

| Concern | Legacy Ajax | AjaxSlim |
| --- | --- | --- |
| Client foundation | Prototype + Scriptaculous (`prototype.js`, `effects.js`) | **none** — vanilla JS + `idiomorph.js` only |
| Runtime file | `wonder.js` | `ajaxslim.js` |
| Transport / swap | `new Ajax.Updater(id, url, options)` | `fetch()` → Idiomorph morph (or `innerHTML`) |
| Update link | `AUL.update` over `Ajax.Updater` | `AjaxSlim.AUL.update/request` over `fetchAndMorph` |
| Submit button | `ASB.update/request` + `Form.serializeWithoutSubmits` | `AjaxSlim.ASB.update/request/partial` + `URLSearchParams` serializer |
| Field observer | `Form.Element.Observer` + `Form.serialize` (polling) | native `change`/`input` + debounce + `FormData`/`URLSearchParams` |
| Default-submit Enter key | Prototype IE&lt;9 `Event.observe`/`$$`/`fireEvent` keypress shim | native default-submit-button click (shim dropped) |
| Searchable select | Chosen (jQuery) — foreign sibling, breaks under morph | `wonder-select` custom element — morph-native |
| Trigger registry | `eval(id + "...")` / `window[name]` globals (throws on UUID ids) | **`Map` keyed by id** (fixes the UUID-with-`-` bug) |
| Periodic refresh | `Ajax.PeriodicalUpdater` (self-scheduling) | `setInterval` with an idempotency guard (clears prior timer for the id) |
| Effects / insertion | `insertion` / `Effect.*` / `AUC.insertionFunc` / `beforeInsertionDuration` / `afterInsertionDuration` | **DROPPED** (see rationale) |
| `createAjaxOptions` / `AjaxOption` / `AjaxOptions` | full Prototype option dictionaries (`onLoading`, `onComplete`, `evalScripts`, `asynchronous`, `method`, `decay`, …) | **DROPPED** — not needed by the fetch path |
| Comet / push | `AjaxPushRequestHandler` registered by principal | **DROPPED** — out of scope |
| `AjaxUtils` array helpers | `arrayValueForObject` etc. via `org.json` | **DROPPED** — avoids the `org.json` dependency |
| `updateDomElement` / `AjaxValue` | present | **DROPPED** — unused by the core |

### What was DROPPED on `AjaxUpdateContainer`, and why

- **All effect / insertion bindings** — `insertion`, `insertionDuration`, `beforeInsertionDuration`,
  `afterInsertionDuration`, and the `expandInsertion` / `AUC.insertionFunc` machinery. The usage
  audit confirmed these are unused in real apps, and they depend entirely on Scriptaculous `Effect`s
  which AjaxSlim does not ship. Not emitted, not read.
- **The Prototype option dictionary** — `decay`, `onLoading`, `onComplete`, `onSuccess`, `onFailure`,
  `onException`, `evalScripts`, `asynchronous`, `method`, `parameters`, `skipFunction`. These shaped
  the `Ajax.Updater` call, which no longer exists. `evalScripts` behavior is now unconditional and
  isolated per-script in the runtime.

### What was KEPT on `AjaxUpdateContainer`

- `id`, `elementName`, `class`, `style` — basic rendering.
- `action` — the action invoked when the container refreshes.
- `frequency` (+ `stopped`) — periodic refresh, a real capability, now via `setInterval`.
- `observeFieldID` (+ `fullSubmit`) — refresh-on-field-change, wired to a minimal runtime
  `observeField` (a full `AjaxObserveField` port is a later slice; `fullSubmit` is accepted but the
  current minimal observer always refreshes the container).
- `onRefreshComplete` — post-update callback, fired by the runtime after the morph/replace completes.
- `optional` — skip rendering the container tags when already inside an update container.
- `morph` — per-container override of `MORPH_BY_DEFAULT` (which defaults to **`true`**, same as
  legacy).

### How the morph path works without Prototype

The legacy morph logic lived as an override of `Ajax.Updater#updateContent` (the
`morph-block.LEGACY.js` / `AjaxMorph` block). It was already vanilla apart from the Prototype string
helpers (`stripScripts`, `extractScripts`, `evalScripts`). AjaxSlim lifts that logic into a
standalone `Morph` object inside `ajaxslim.js`:

- `Morph.morph(receiver, html)` → `Idiomorph.morph(receiver, stripScripts(html), { morphStyle:
  'innerHTML', callbacks })` then runs the stripped scripts.
- The `callbacks` (`beforeNodeMorphed` / `beforeNodeRemoved` / `beforeAttributeUpdated`) implement
  the `data-morph-ignore` / `data-morph-preserve` / `data-morph-preserve-style` escape hatches
  verbatim from legacy.
- `stripScripts` / `runScripts` are small regex helpers replacing Prototype's `String#stripScripts` /
  `String#extractScripts` + isolated indirect-`eval`.

## Status / not yet built

**Now built** — the framework bootstrap, the whole `ajaxslim.js` runtime (`AUC` / `AUL` / `ASB` +
`fetchAndMorph` core + real `observeField`), and the core element spine:

- `AjaxUpdateContainer`
- `AjaxUpdateLink`
- `AjaxObserveField` (the real one, honoring `fullSubmit` with both full-form and partial submit)
- `AjaxSubmitButton` / `AjaxDefaultSubmitButton`
- `AjaxUpdateTrigger`
- `AjaxPopUpButton` / `AjaxBrowser` + `wonder-select.js` / `wonder-select.css`
- `AjaxBusySpinner` (+ the runtime's ajax-activity broker + `ajaxslim-busy.css`)
- `AjaxModalContainer` (native `<dialog>`, + `ajaxslim-modal.js` / `ajaxslim-modal.css`)

**Proven in production:** Strimillinn runs on AjaxSlim end-to-end. (A focus-on-Tab regression in the
descendant-observe path was found and fixed — see the `observeField` notes — by binding observers to
field nodes rather than churning synthetic positional ids that drift under morphs.)

This now covers everything Strimillinn (and the shared `helium5` library all the apps depend on) use:
UpdateContainer, UpdateLink, ObserveField, SubmitButton, UpdateTrigger, PopUpButton, Browser,
BusySpinner, ModalContainer.

Still to build / do for a full framework:

- **`AjaxAutoComplete`** — deliberately deferred. It is very rarely used (only an experimental
  Strimillinn feature) and its legacy form is a 397-line, ~40-binding Scriptaculous `Ajax.Autocompleter`
  wrapper. When wanted, it should be a small fresh build on native `<datalist>` (or a tiny custom
  element), not a port.
- **`AjaxModalDialog`** — 4 uses total, none in Strimillinn; not built. (`AjaxDefaultSubmitButton`
  dropped its `AjaxModalDialog.isInDialog` focus-ring tweak; only relevant if/when this lands.)
- **Dependency migration + playground verification** — apps still depend on the legacy `Ajax`
  framework. Because both ship `er.ajax.*`, swapping an app to AjaxSlim is a one-line pom change
  (Strimillinn has done this in production). Verified no app/helium5 JS depends on the legacy
  `AUC`/`ASB`/`AUL`/`iBox` globals (AjaxSlim namespaces under `AjaxSlim.*`), so the swap is clean.
- **Dependency migration + playground verification** — AjaxPlayground (and downstream apps) currently
  depend on the legacy `Ajax` framework. Because both ship `er.ajax.*`, exercising AjaxSlim requires
  swapping that dependency to `AjaxSlim` (a separate, later step — not done here). The
  `GalleryAjaxSlimUpdateContainer` playground page is wired up and ready for that swap; the new
  elements still need to be exercised there once the swap is in.

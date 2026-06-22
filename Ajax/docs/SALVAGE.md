# Ajax.framework — what's worth salvaging into AjaxSlim

_A focused answer to one question: of the ~47 legacy `Ajax` elements NOT yet in AjaxSlim, which carry a **genuinely good idea worth porting well** (with the old framework as reference), rather than re-inventing ad-hoc the day we hit the gap? This is opinionated and deliberately short — see `ASSESSMENT.md` for the full per-element breakdown._

The lens here is different from `ASSESSMENT.md`. That doc asks "what's the cleanup debt." This one asks: **is the idea good, and would we want it implemented well in a modern, consistent way?** An element can be "needs rewrite" in the assessment yet a clear _skip_ here (because the browser now does it), or a clear _port_ here (because the idea is timeless and the server contract is clean).

The recurring test: **does it have a clean server-side contract that survives, with only the Prototype/Scriptaculous client needing a fetch+morph reimplementation?** Those are the good ports. Things that are "all client effect, no server idea" or "the platform now does this" are skips.

---

## Tier 1 — Port these. Good idea, clean server contract, no platform equivalent.

These are the ones we'd otherwise miss and re-invent badly.

### `AjaxInPlaceEditor` (click-to-edit-in-place)
The classic "click a label, it becomes a field, blur/save commits, Esc cancels" pattern. Timeless UX, and the **server contract is clean**: a value binding + optional formatter/dateformat/numberformat + escapeHTML handling. The whole client is Scriptaculous `Ajax.InPlaceEditor`, but the server half (push the edited value through a formatter back into a binding, re-render the read view) is exactly what we'd keep. Modern port: a small custom element that swaps view↔edit and does a partial submit + morph. **High value, medium effort.**

### `AjaxAutoComplete` (type-to-search-from-server)
Type-ahead against a server method that returns a filtered list per keystroke (debounced). We already have the pieces — `wonder-select` does searchable selection, `AjaxObserveField` does debounced field observation — but **server-fed autocomplete on a free-text field** is a distinct, commonly-wanted capability we don't cover. The server contract (push partial value → return list fragment) is clean. Best port may be a `wonder-select` variant with a `remoteSearch` mode rather than a new element. **High value; note this overlaps the parked "remote search" design.**

### `AjaxFileUpload` + `AjaxFileUploadRequestHandler` + `AjaxUploadProgress` (non-blocking upload w/ progress)
The standout server idea in the whole framework: the **request handler consumes the multipart upload out-of-band, streaming to a temp file while tracking progress, without holding the session lock.** That's genuinely hard to get right and worth keeping as reference. The `AjaxFileUploadRequestHandler` is already Prototype-free (assessment: Core/Modern). The legacy client uses a hidden-iframe hack; a modern port is `fetch` + `XMLHttpRequest`/`ReadableStream` progress against the same handler. NB: a native XHR2 reimplementation was already prototyped on the `flexible-file-upload-experiment` branch — that's the reference, not the iframe version. **High value, higher effort; the server engine is the prize.**

---

## Tier 2 — Port the *idea*, not the element. The valuable part is a mechanism, not the chrome.

### `AjaxTabbedPanel` → keep the **lazy-load-on-demand** mechanism
Tab chrome is CSS/ARIA (skip). But "only the selected tab's content is rendered; others load on demand when first selected" is a real, useful pattern (heavy tabs, dashboards). Port that as a general capability — a container that defers rendering its content until first shown — usable beyond tabs. **Medium value; generalize, don't copy.**

### `AjaxExpansion` → the **"animate only the contents, mark the trigger"** idea
Expand/collapse where the toggle link is NOT inside the updated region, so animating the contents doesn't animate the link; the link instead gets an `expanded` class for icon styling. Subtle but correct. **This belongs in the parked effects session** — it's the same family as morph enter/exit animations. Note it as a design input there rather than a standalone port.

### `AjaxHighlight` → the **server-flags-an-object-as-changed → highlight-on-next-render** contract
`AjaxHighlight.highlight(obj)` in an action, then on the next page any element bound to that object flashes. Decouples "what changed" (server) from "how it's emphasized" (client). Lovely idea, and a natural fit once we have the effects layer (the flash becomes a CSS animation triggered by a server-emitted marker class). **Defer to the effects session; keep this contract as the model.**

---

## Tier 3 — Niche but clean; port only when a real need appears.
Good enough ideas, but we shouldn't pre-build them. `AjaxDraggable`/`AjaxDroppable` (general drag-and-drop primitives — we have `AjaxSortable` for the common reorder case; full free drag/drop is rarer), `AjaxTree`+`AjaxTreeModel` (server-driven tree; the `AjaxTreeModel` parent/children/delegate abstraction is the reusable bit), `AjaxLongResponse` (stay-on-page long task + progress polling — overlaps `AjaxSelfUpdatingContainer` + `AjaxPing`, may already be expressible). Port with reference if/when hit.

---

## Skip — the browser won, or it's dead.

**Platform replaced it** (use the native thing, don't port):
- `AjaxAccordion`/`AjaxAccordionTab` → `<details>`/`<summary>`
- `AjaxDatePicker` → `<input type="date">`
- `AjaxModalDialog`/`AjaxModalDialogOpener` → already covered by `AjaxModalContainer` on `<dialog>`
- `AjaxSlider` → `<input type="range">`
- `AjaxHoverable`, `AjaxBehaviour` → CSS `:hover` / a line of JS
- `AjaxResetButton`, `AjaxToggleLink` → trivial native
- `FocusText`, `FocusTextField` → `autofocus` attribute

**Dead / superseded:**
- `AjaxSortableList` → superseded by our `AjaxSortable`
- `AjaxIncludeScript`, `AjaxRoundEffect`, `AjaxTextHinter`, `AjaxSocialNetwork(Link)` → obsolete

**Infra that ships with its feature, not a standalone decision:** `Ajax` (principal), `AjaxRequestHandler`, `AjaxPushRequestHandler`, `AjaxOption(s)`/`AjaxConstantOption`/`AjaxValue`/`AjaxProgress` (option/value plumbing) — these come along _if_ we port the feature that needs them.

---

## Recommended order, if/when we act

1. **`AjaxInPlaceEditor`** — highest value-to-effort, self-contained, no dependencies on parked work.
2. **`AjaxAutoComplete` / remote-search `wonder-select`** — fold into the already-designed remote-search work.
3. **File upload** — port the request-handler engine + the XHR2 client from the `flexible-file-upload-experiment` branch.
4. **Effects session** — pull in `AjaxExpansion` and `AjaxHighlight` as design inputs (server-marker → CSS animation), alongside morph enter/exit.
5. Tier 3 on demand.

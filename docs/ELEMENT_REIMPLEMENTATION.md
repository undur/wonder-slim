# Reimplementing WO's built-in dynamic elements — sizing the job

Research note, 2026-09-06. Question: how much work is it to replace every stock WebObjects dynamic
element with our own implementation, ideally sharing the implementing core with ng-objects, so that
template rendering is entirely under our control and WO → ng migration is binding-compatible by
construction?

Short answer: **the surface is small (45 classes, ~28 in real use), the leaf
elements are mostly string appending, and the cost concentrates in four pieces of shared
infrastructure. Roughly three focused weeks for full coverage of everything the apps use, five to six
for the complete stock inventory. Sharing the core with ng is possible now, without waiting for ng's
render redesign, if the sharing is done at the level of framework-neutral logic classes rather than
neutral element objects.**

## 1. The inventory (WO 5.4.3, everything transitively extending WODynamicElement)

45 classes. By role:

| Role | Classes | Notes |
|---|---|---|
| **Bases / infrastructure** | WODynamicElement, WODynamicGroup, WOHTMLDynamicElement (36 methods), WOInput (18), WOInputList (12), WOHTMLURLValuedElement, WOClientSideScript | Where the real behaviour lives: attribute passthrough and `otherTagString`, element naming, form-value lookup, selection matching, traversal. Everything else is thin on top of these. |
| **Structural** | WOConditional, WORepetition, WOComponentReference, WOComponentContent, WOSwitchComponent | Traversal, subcomponent registration, iteration state. |
| **Text / links** | WOString, WOHyperlink, WOActionURL, WOResourceURL, WOGenericElement, WOGenericContainer | Mostly output. |
| **Form inputs** | WOForm, WOTextField, WOText, WOPasswordField, WOHiddenField, WOCheckBox, WORadioButton, WOSubmitButton, WOResetButton, WOSearchField, WOFileUpload, WOImageButton, WOActiveImage | `takeValuesFromRequest` semantics are the work; rendering is trivial. |
| **Selection lists** | WOPopUpButton, WOBrowser, WOCheckBoxList, WORadioButtonList (all on WOInputList) | The single hardest chunk: list/selection/displayString/noSelection matching and value extraction. |
| **Resources / media** | WOImage, WOBody, WOFrame, WOEmbeddedObject, WOJavaScript, WOVBScript, WOParam, WOApplet, WOQuickTime | Half of these are dead technology. |
| **Misc** | WOXMLNode, WONestedList | Rare. |

## 2. What the apps actually use

Census over 26 wonder-slim consumer repositories (3,379 templates, 22,109 element tags), with tag
shortcuts and slim's replacements folded back to their canonical element:

| Element (canonical) | Uses | Status in slim today |
|---|---|---|
| WOString → ERXWOString | 5,591 | reimplemented |
| WOConditional → ERXWOConditional | 2,366 (+1,127 still hitting stock `WOConditional` from .wod templates) | reimplemented — but the raw name isn't aliased yet |
| WORepetition → ERXWORepetition | 1,561 | reimplemented |
| WOHyperlink → ERXWOHyperlink | 1,407 | thin subclass of stock |
| WOTextField → ERXWOTextField | 1,088 | reimplemented on stock WOInput base |
| WOGenericContainer | 567 | stock |
| WOSubmitButton | 556 | thin patch |
| WOForm → ERXWOForm | 499 | reimplemented on stock WOHTMLDynamicElement base |
| WOImage | 339 | stock (ERXWOImage exists, opt-in as `svg`) |
| WOPopUpButton | 324 | thin patch |
| WOCheckBox | 266 | stock |
| WOText | 170 | thin patch |
| WOComponentContent → ERXWOComponentContent | 132 | reimplemented |
| WORadioButton | 86 | stock |
| WOKeyValueConditional | 71 | stock (woextensions) |
| WOSwitchComponent → ERXWOSwitchComponent | 57 | reimplemented |
| WOActionURL, WOGenericElement, WOPasswordField | 43–46 each | stock / patch |
| WOBrowser, WOHiddenField, WOJavaScript, WOResourceURL, WOFileUpload, WOImageButton, WOActiveImage, WORadioButtonList, WOBody, WOParam, WOXMLNode, WOToOneRelationship | < 30 each | stock |

Stock elements and their slim replacements account for roughly three quarters of every tag in every
template; the rest are app components and Ajax elements. **28 distinct stock elements are in use.**
Never used by any app: WOApplet, WOQuickTime, WOVBScript, WOEmbeddedObject, WOFrame, WONestedList,
WOResetButton, WOSearchField, WOCheckBoxList — candidates to not port at all (fail loudly at the alias).

## 3. What exists already

**slim** (er.extensions.components): ERXWOString, ERXWOConditional + ERXElse, ERXWORepetition,
ERXWOHyperlink, ERXWOTextField, ERXWOForm, ERXWOSwitchComponent, ERXWOComponentContent, ERXWOImage,
ERXWOComponentInstance — ~2,300 lines. Plus eight thin patches (ERXDynamicElementsPatches: SubmitButton,
ActiveImage, Text, HiddenField, PasswordField, PopUpButton, Browser, CheckBoxList) that subclass the
stock element and adjust one behaviour each. Note that three of the "reimplemented" ones still sit on
stock bases (WOInput, WOHTMLDynamicElement, WOHyperlink) — full control means owning those bases.

**ng-objects** (ng.appserver.templating.elements): NGString, NGConditional, NGRepetition, NGHyperlink,
NGForm, NGTextField, NGText, NGCheckbox, NGPasswordField, NGFileUpload, NGSubmitButton, NGPopUpButton,
NGSwitchComponent, NGComponentReference, NGComponentContent, NGGenericElement/Container, NGImage,
NGJavaScript, NGStylesheet, NGResourceURL, NGActionURL — ~2,900 lines including the ajax elements.
Missing: radio button, browser, checkbox/radio lists, hidden field, image/active-image buttons.

**Shared already:** the parser (Parsley), the `.apiext` binding-spec format, and — usefully — the alias
mechanism: ng's `NGElementManager` reads the very same `parsley-tag-aliases.properties` resource that
slim uses to swap `WOString → ERXWOString`. So "the same implementation behind the same tag name in
both frameworks" is a registration step once the implementations exist.

## 4. Where the cost actually is

Looking at the stock elements, the leaf elements are cheap. Four things are not:

1. **The HTML-element base** (WOHTMLDynamicElement): passthrough of unknown attributes, `otherTagString`,
   boolean-attribute rendering, `elementName`/`id` handling, the `secure`/`directActionName` URL
   plumbing shared by links, forms and images. ~3 days to own properly, and it pays for every element
   above it.
2. **The input base** (WOInput): `name` generation from the element ID, `value`/`disabled`/`readonly`
   semantics, the form-value lookup contract, `checked`/`selection` push-back rules. ~2 days.
3. **Selection lists** (WOInputList and its four children): `list`/`item`/`displayString`/`selection`
   /`selectedValue`/`noSelectionString`/`escapeHTML` matching on the way out, value extraction on the way
   in, with the corner cases everyone has been bitten by (identity vs equality, arrays vs lists — slim
   already patched arrays in). This is COMMON_ELEMENTS.md's "risk #3" and the largest single chunk:
   ~4–5 days including a proper test matrix.
4. **Multipart / coordinate submissions** (WOFileUpload, WOImageButton, WOActiveImage): ~2 days, and
   WOFileUpload's streaming path deserves its own test.

Everything else — text, links, generic elements, buttons, hidden/password/checkbox/radio, component
content/reference, the structural elements already done — is 0.5–1 day each including a playground
scenario and a bridge assertion.

## 5. Estimate

| Scope | Effort |
|---|---|
| Own the bases (items 1–2) and convert the existing ERX elements off stock bases | ~1 week |
| The 28 elements in use, minus what's already reimplemented (≈17 remaining, incl. the selection family and multipart) | ~2 weeks |
| Differential test harness: render stock vs new for a binding matrix per element, diff the markup; plus form round-trips through the bridge | ~3–4 days — this is what buys "almost 100% predictability", don't skip it |
| The unused tail (9 elements): implement or explicitly refuse | 1–2 days |
| ng parity: audit each ng element's bindings against the apiext, port the missing ones onto the shared core | ~1 week |

**Roughly three focused weeks for full coverage of what the apps use, five to six for the whole
inventory including ng parity.** The order that minimizes risk: bases first (nothing visible changes),
then the selection family (highest risk, most bindings), then the cheap leaves in census order.

## 6. Sharing the core with ng — two shapes

**(a) Neutral elements + per-framework holders** — the design in `docs/COMMON_ELEMENTS.md`.
Full sharing: one element class, two 20-line holders. Its stated gate is ng's render redesign
(`NGOutput` / `NGRenderContext`), and per `ng-objects/docs/migration-readiness.md` (2026-08-22) that
redesign is deliberately *not* first — the migrating apps are meant to be its design input. So this
shape is blocked for months.

**(b) Shared logic core + thin framework shells** — recommended now. Everything that makes an element
hard is framework-free: attribute/`otherTagString` rendering, boolean and negate semantics for
conditionals, list/selection matching, value coercion and form-value parsing, name generation rules.
Put that in plain Java classes with no `com.webobjects.*` or `ng.*` types (inputs are strings, lists,
maps and small records; output is a `StringBuilder`-style sink). Then each framework's element is a
30–50 line shell that extends its native base, pulls its bindings, and calls the core. That shares
sixty to seventy percent of the code — the percent that carries the behaviour — with zero dependency on
ng's redesign, and when the redesign does land, the holders of design (a) can be built *on the same
core*, so nothing is thrown away. Module home: a small `element-core` artifact both ERExtensions and
ng-appserver depend on (both already share Parsley the same way).

**Binding compatibility comes from the spec, not the code.** With one `.apiext` per element as the
contract, the differential harness checking both frameworks against it, and the same alias file on both
sides, "WO and ng render this template identically" becomes a test that runs, not a promise.

## 7. Small things to do regardless

- Alias `WOConditional = ERXWOConditional` in ERExtensions' alias file: 1,127 template uses still hit
  the stock element because only the shortcuts (`if`, `conditional`) are mapped.
- Decide the fate of the nine never-used elements: an alias to a "refused" element that throws with a
  message beats silently keeping the stock class around.
- WOComponentReference is the one structural element still stock; it is ~60 lines to own (its
  behaviour was fully mapped while building ERXWOComponentInstance).

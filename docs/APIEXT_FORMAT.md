# The `.apiext` element-API format

> **Status: captures the CURRENT format.** This documents the classic WebObjects `.api` format and the
> `.apiext` extension AjaxSlim uses, as they exist today — for tooling (e.g. a WOLips fork) that renders a
> tag's API preview. It intentionally adds nothing speculative; the larger v2 wishlist (deprecation,
> directionality, value-sets, tag shortcuts, …) surveyed in the WOLips `proposal-element-spec` is **not**
> here. Grammar: [`apiext.dtd`](./apiext.dtd).

---

## What this is

`.api` files declare a dynamic element's binding API (for IDE autocomplete/validation and live preview).
`.apiext` is a backward-compatible **superset**: same XML structure, plus per-element and per-binding
documentation, typed bindings, and an element-level passthrough flag — everything a tool needs to render
a rich preview of a tag's API.

The DTD [`apiext.dtd`](./apiext.dtd) validates all three of: existing `.api` files, our `.apiext` files,
and WO's own canonical `WebObjectsDefinitions.xml` (39 elements). A document that validates against it is
guaranteed parseable by a conforming consumer.

> **Note — DTD limits.** A DTD constrains *structure*, not *value types*. Every attribute is `CDATA`
> (untyped string): booleans are the literal strings `"true"`/`"false"` (or WO's `"YES"`/`"NO"`), and
> `<type>` content is a Java FQN or value-set name. The consumer interprets these strings.

## Structure (what a preview can read)

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<!DOCTYPE wodefinitions SYSTEM "apiext.dtd">
<wodefinitions>
  <wo class="AjaxUpdateContainer" wocomponentcontent="true" passthrough="true">

    <doc><![CDATA[A region of the page refreshed independently via Ajax… (Markdown allowed)]]></doc>

    <binding name="updateContainerID" required="true">
      <pull>
        <type>java.lang.String</type>
        <type>java.util.List</type>
      </pull>
      <doc>The container(s) to refresh — a single id, a `;`-separated set, or a `List`.</doc>
    </binding>

    <!-- a two-way binding: pulls one type to display, pushes another back -->
    <binding name="selection">
      <pull><type>java.lang.Object</type></pull>
      <push><type>java.lang.Object</type></push>
      <doc>The currently selected object.</doc>
    </binding>

    <validation message="Only one of 'replaceID' or 'updateContainerID' can be bound.">
      <bound name="replaceID"/>
      <bound name="updateContainerID"/>
    </validation>

  </wo>
</wodefinitions>
```

### Element — `<wo>`

| Field | Origin | Meaning (for preview) |
|---|---|---|
| `class` | `.api` | Component class — simple name (as in the wild) or fully-qualified. |
| `wocomponentcontent` | `.api` | `"true"` if the element wraps child content. |
| `passthrough` | **.apiext** | `"true"` if the element forwards unhandled attributes onto the rendered tag (show a "Passthrough" affordance + a "any other attribute is passed through" note). |
| `<doc>` | **.apiext** | The element's role/description. May contain a Markdown subset (see below). |

### Binding — `<binding>`

| Field | Origin | Meaning (for preview) |
|---|---|---|
| `name` | `.api` | The binding name. |
| `required` | `.api` | `"true"` → mark the binding required (e.g. a `•`). |
| `settable` | `.api` | `"true"` → binding is push-capable (two-way). |
| `defaults` | `.api` | WO autocomplete-preset string (e.g. `Boolean`, `Actions`, `Page Names`). |
| `passthrough` | `.api` | Present in WO's vocabulary; per-binding pass-through flag. |
| `<pull>` / `<push>` | **.apiext** | **Directionality** — see below. Each holds the `<type>`(s) for that direction. Their *presence* declares the direction. |
| `<type>` (repeatable) | **.apiext** | Accepted type(s): a fully-qualified Java class (`java.lang.String`) or a value-set name. **Always** inside a `<pull>`/`<push>` block — never a direct child of `<binding>` — so a declared type always has a direction. Multiple = the accepted set (render shortened + joined, e.g. `String \| List`). Optional `interpretation` attribute (see below). |
| `<doc>` | **.apiext** | The binding's description (Markdown subset). |

### Directionality — `<pull>` / `<push>`

A binding's value type can differ by **direction**: an element may *pull* one type (read it, to display) and
*push* a different type back (e.g. a text field pulls `java.lang.Object` to display, but pushes
`java.lang.String` — what the user typed). So directionality isn't a flat attribute; it's a split in the
binding's *value* definition.

The model is deliberately narrow and self-documenting:

- **`<pull>` present** → the binding is **read** (its value is pulled to render). Holds the pulled `<type>`(s).
- **`<push>` present** → the binding is **written** (the element pushes a value back). Holds the pushed `<type>`(s).
- **both** → two-way. **neither** (a bare `<type>` under `<binding>`) → direction-agnostic/legacy.

The **presence of the block is itself the directionality declaration** — there is no separate
`direction="pull|push|both"` attribute to contradict it, and `settable` becomes redundant (a `<push>` *is*
"push-capable"). Only **type** is direction-specific; identity facts (`name`, `doc`, `required`) and
authoring/display facts (value-sets, default values) stay on `<binding>` — a value-set can't be validated
against a *pushed* value (you only know its type, not its value), so per-direction value-sets would buy
nothing.

For preview, surface the direction lightly. AjaxSlim's reference page uses `↓` (pull), `↑` (push), `↕`
(two-way), and:

- **single type** (pull-only, push-only, or a two-way binding whose pull/push types **match**) → one row,
  e.g. `↓ String`, `↑ Boolean`, `↕ Object`.
- **a true split** (two-way with **different** pull/push types) → **two rows**, `↓ pullType` over
  `↑ pushType` — clearer than an inline `→`, especially once types are long or multi-valued.

Colour the markers by behaviour (the glyphs are thin, so the colour needs to be saturated): pull faint,
**push orange**, **two-way red** — the writeable cases stand out.

#### Worked example: a binding whose type differs by direction

The cleanest real case is a checkbox's `checked` (see [`examples/WOCheckBox.apiext`](./examples/WOCheckBox.apiext)
— an illustrative file, not a shipping element; no AjaxSlim element happens to have a genuine split). It
**pulls** any object read by *truthiness* (to decide the initial checked state) and **pushes** a hard
`java.lang.Boolean` (the state the user left it in):

```xml
<binding name="checked">
  <pull><type interpretation="truthy">java.lang.Object</type></pull>
  <push><type>java.lang.Boolean</type></push>
</binding>
```

Rendered as two rows (this is what the reference page / IDE tooltip produces):

| Binding | Type | Description |
|---|---|---|
| `checked` | `↓ Object (truthy)`<br/>`↑ Boolean` | Pulled by truthiness to decide the initial state; pushed back as a Boolean. |

The split shows at a glance that *what you bind* (any value, read as a boolean) and *what comes back* (a
`Boolean`) are not the same shape. Contrast a pull-only binding (`↓ String`) and a same-type two-way one
(`↕ Object`), which stay on a single row.

### Interpretation — `<type interpretation="...">`

Some bindings read a value through a **coercion rule** rather than by its type. The classic case is
truthiness: `WOConditional`'s `condition` accepts *any* value but reads it as a boolean — `null`, the
number `0`, and `false` are false; everything else is true. That rule is **not a type** (the binding isn't
"a Boolean"; it's "anything, read as a boolean").

The optional `interpretation` attribute on `<type>` names that rule **without changing the type**:

```xml
<pull><type interpretation="truthy">java.lang.Object</type></pull>
```

- **The type stays the real, validatable constraint** — `Object` here means "accepts anything", which is
  the honest, enforceable claim. A validator uses the type and ignores the interpretation.
- **The interpretation is documentation** — shown as a qualifier, e.g. `Object (truthy)`, telling the
  author *how* the value is read. The only value today is `truthy`.

This keeps validation honest (always a real type) while surfacing the human-facing "how it's interpreted"
signal — the same discipline as everywhere else: the validatable thing stays clean, the editorial nuance
is additive.

### Validation — `<validation>`

A cross-binding rule: `message` plus a predicate tree. When the predicates hold, the message applies.
Predicates: `<bound>`, `<unbound>`, `<ungettable>`, `<unsettable>` (each `name="…"`), combinable with
`<and>`, `<or>`, and `<count test="…">`. Real-world files (incl. ours) often place predicates directly
under `<validation>`; the DTD allows both that and the nested form.

### Documentation pointer — `<documentation>`

WO's external-doc pointer (`directory`/`domain`/`path`). Rare; kept for `.api` compatibility. Distinct
from the inline `<doc>` extension.

## The Markdown subset in `<doc>`

`<doc>` bodies (element and binding) may use a small, safe Markdown subset — a renderer should support:
inline `` `code` ``, `**bold**`, `*italic*`, `[text](url)`, and fenced ` ```lang … ``` ` code blocks
(paragraphs split on blank lines). Wrap a `<doc>` containing Markdown specials or a code fence in
`<![CDATA[ … ]]>` so its characters need no XML escaping. A consumer that doesn't render Markdown can show
the raw text and lose only formatting.

## Why there are no category "tags" in the format

An earlier draft carried a `<tags>` element (AjaxSlim's `update`/`widget`/`server`/`trigger` badges). It
was **removed**: those values are AjaxSlim's own editorial taxonomy, not facts about an element's API
contract, and nothing structural consumes them (they only drove badge colours on AjaxSlim's reference
page). The format describes the *binding contract*; framework-specific categorization and its presentation
belong to the framework's own tooling, not the shared format — the same reason badge styling was always
client-side.

For the record, AjaxSlim's reference page categorizes its elements like so (this mapping lives in the
`AjaxSlimElementReference` component, not in the `.apiext` files):

| Category | Elements |
|---|---|
| Update | AjaxUpdateContainer, AjaxSelfUpdatingContainer, AjaxUpdateLink, AjaxSubmitButton, AjaxDefaultSubmitButton, AjaxObserveField, AjaxModalContainer |
| Widget | AjaxPopUpButton, AjaxBrowser |
| Server | AjaxUpdateTrigger, AjaxPingUpdate |
| Activity (`trigger`) | AjaxBusySpinner, AjaxPing |
| (none) | AjaxSortable |

## Relationship to WO's DTD

WO ships `com/webobjects/appserver/WebObjectsDefinitions.dtd`. `apiext.dtd` keeps that vocabulary verbatim
(`wodefinitions`/`wo`/`binding`/`validation`/`and`/`or`/`count`/`bound`/`unbound`/`ungettable`/
`unsettable`/`documentation`) and adds only the `.apiext` items above (`passthrough` on `<wo>`; `<doc>`
under `<wo>`; `<doc>`, `<pull>`, `<push>` under `<binding>`; `<type>` — with an optional `interpretation`
attribute — inside `<pull>`/`<push>`). So every existing `.api` validates unchanged (they carry no
`.apiext` children), and the extension is additive.

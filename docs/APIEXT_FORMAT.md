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
documentation, typed bindings, framework tags, and an element-level passthrough flag — everything a tool
needs to render a rich preview of a tag's API.

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

    <tags><tag>update</tag></tags>

    <binding name="updateContainerID" required="true">
      <type>java.lang.String</type>
      <type>java.util.List</type>
      <doc>The container(s) to refresh — a single id, a `;`-separated set, or a `List`.</doc>
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
| `<tags><tag>` | **.apiext** | Framework categorization (`update`/`widget`/`server`/`trigger`/…). The value is portable; badge colour/label is the tool's choice. |

### Binding — `<binding>`

| Field | Origin | Meaning (for preview) |
|---|---|---|
| `name` | `.api` | The binding name. |
| `required` | `.api` | `"true"` → mark the binding required (e.g. a `•`). |
| `settable` | `.api` | `"true"` → binding is push-capable (two-way). |
| `defaults` | `.api` | WO autocomplete-preset string (e.g. `Boolean`, `Actions`, `Page Names`). |
| `passthrough` | `.api` | Present in WO's vocabulary; per-binding pass-through flag. |
| `<type>` (repeatable) | **.apiext** | Accepted type(s): a fully-qualified Java class (`java.lang.String`) or a value-set name. Multiple `<type>` = the accepted set (render shortened + joined, e.g. `String \| List`). |
| `<doc>` | **.apiext** | The binding's description (Markdown subset). |

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

## Relationship to WO's DTD

WO ships `com/webobjects/appserver/WebObjectsDefinitions.dtd`. `apiext.dtd` keeps that vocabulary verbatim
(`wodefinitions`/`wo`/`binding`/`validation`/`and`/`or`/`count`/`bound`/`unbound`/`ungettable`/
`unsettable`/`documentation`) and adds only the `.apiext` items above (`passthrough` on `<wo>`; `<doc>`,
`<tags>`/`<tag>` under `<wo>`; `<type>`, `<doc>` under `<binding>`). So every existing `.api` validates
unchanged, and the extension is additive.

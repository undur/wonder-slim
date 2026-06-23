# `.apiext` / binding documentation — status & what's next

> **Living status doc.** Where the extended element-API effort stands and what remains, so it stops being
> spread across the WOLips `proposal-element-spec`, the format spec, and conversation. See also:
> [`APIEXT_FORMAT.md`](./APIEXT_FORMAT.md) (the format), [`apiext.dtd`](./apiext.dtd) (the grammar), and
> WOLips `proposal-element-spec/README.md` (the v2 wishlist + structural survey).

---

## Shipped & working

The `.apiext` extended element-API format is live and consumed end-to-end.

- **Format authored** — all 14 AjaxSlim elements have `.apiext` files in `AjaxSlim/src/main/components/`
  (commit `7e9f272b4`).
- **DTD** — [`apiext.dtd`](./apiext.dtd): a verified **superset** of WO's canonical
  `WebObjectsDefinitions.dtd`. Validates all 14 `.apiext` files, vanilla `.api` files, and WO's full
  39-element `WebObjectsDefinitions.xml` (commit `7de78e4ae`).
- **Spec doc** — [`APIEXT_FORMAT.md`](./APIEXT_FORMAT.md): field-by-field semantics for tooling.
- **Consumers** — AjaxSlim's reference page renders entirely from `.apiext` (commit `398fef064`); the
  **WOLips/Eclipse fork reads `.apiext` and renders a tag's API preview inline in the template editor**.
- **Markdown in `<doc>`** — injection-safe subset (inline code, bold, italic, links, fenced code blocks).
- **Tags removed** — the framework-editorial category badges were a leak; removed from the format and
  relocated to the reference component (commit `00a1cfb96`).

### What the format expresses today

- **Element**: `class`, `wocomponentcontent`, `passthrough`, `<doc>` (role, Markdown).
- **Per binding**: `name`, `required`, `settable`, `defaults`, one-or-more `<type>` (FQ Java class or
  value-set name), `<doc>` (Markdown).
- **Cross-binding rules**: `<validation>` with the full `and`/`or`/`count`/`bound`/`unbound`/`ungettable`/
  `unsettable` vocabulary.

---

## Remaining — two tracks

Kept separate on purpose: Track A is small plumbing on the *current* format; Track B is the larger v2
vocabulary expansion, deliberately deferred.

### Track A — finish the current format's plumbing (small, concrete)

1. **Enforce `<type>`.** Types are declared but **no consumer validates a bound value against them yet** —
   the "this is validation data, not just docs" intent is unrealized.
2. **Make `.apiext` the single source of truth.** Today `.api` and `.apiext` are *parallel* files (two to
   keep in sync). Endgame: `.apiext` canonical, `.api` regenerated from it or retired. Not started.
3. **Reconcile `.api` ↔ reference.** A few elements have bindings the `.api` lacked (e.g.
   AjaxDefaultSubmitButton's `replaceID`/`disabled`); `.apiext` documents the truer surface, but the
   `.api` files weren't reconciled.

### Track B — v2 vocabulary expansion (larger, deferred)

Authoritative wishlist: WOLips `proposal-element-spec/README.md` (its ng-objects-runtime and Parsley
FIXME lists agree on the destination). **Intentionally not in the format yet** — the deliberate "don't add
stuff at this stage" stance.

| Wishlist item | Status |
|---|---|
| Per-binding `<doc>` | ✅ have it |
| Per-binding `<type>` (multi) | ✅ have it (not enforced — Track A.1) |
| `required` | ✅ have it |
| Cross-binding rules (`<validation>`) | ✅ have it |
| Directionality (pull / push / both) | ✅ have it — `<pull>`/`<push>` blocks; presence = direction; type can differ per direction |
| Value interpretation (e.g. truthiness) | ✅ have it — `<type interpretation="truthy">`; rides alongside the type without changing it (validation uses the type) |
| Default values (`<default>`) | 🔲 not in format |
| Deprecation (with migration docs) | 🔲 not in format |
| Allowed value-sets (enum-style, `<values-from>`) | 🔲 not in format (`defaults` is WO's hardcoded version) |
| Unknown-binding policy (strict / permissive / passthrough) | 🔲 not in format |
| Tag shortcuts / aliases (`<shortcut>`) | 🔲 not in format (currently Parsley prefs) |
| Page-level vs. reusable distinction | 🔲 not in format |
| Fully-qualified class names | 🔲 still simple names |
| Metadata location (project overrides / framework-bundled + precedence) | 🔲 structural decision unmade |

### Structural decisions that gate Track B (none made yet)

From the proposal's §4 / §6:

- **Where element metadata lives** — per-file / project-override / framework-bundled, and the precedence
  order.
- **`.api` v1 compatibility** — parallel forever / auto-migrate on save / drop at a major version.
- **Mandatory vs. inferred** — strict error for a component with no file, or reflection-based fallback.
- **What the runtime enforces** — vs. IDE-only warnings, and at what severity.
- **Where the shared parser/model library lives** — a new `ng-element-spec` subproject, or existing.
- **Class names** — simple, fully-qualified, or both.

---

## Ownership note

Track A is wonder-slim/AjaxSlim work. Track B is owned more by **ng-objects and Parsley** (the runtime and
the IDE) than by wonder-slim — the format is the shared contract, but the v2 vocabulary and its structural
decisions belong to the consumers. wonder-slim's `.apiext` files are the proving ground / reference corpus.

## The headline

The format **ships and drives real tooling.** What remains splits cleanly: a few small plumbing tasks to
make `.apiext` the single source of truth and actually *enforce* the types it declares (Track A), versus
the larger, well-surveyed-but-decision-gated v2 expansion (Track B).

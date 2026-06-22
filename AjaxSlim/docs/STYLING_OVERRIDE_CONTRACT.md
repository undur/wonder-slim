# Styling override contract for CSS-shipping elements — design

**Status:** design proposal, for discussion. No code yet (beyond `ajaxslim-sortable.css`, which was
written to follow the proposed contract). Applies retroactively to the existing element stylesheets.

## Goal

Define **one consistent way** an app overrides the styling of any AjaxSlim element that ships a default
stylesheet — instead of the current ad-hoc situation where some elements are easy to override and some
fight you on specificity. An app developer should be able to learn the rule once and apply it to every
element.

Four elements currently ship CSS:

| File | Custom props | `!important` | Specificity | Overridable today? |
|---|---|---|---|---|
| `ajaxslim-busy.css` | yes (`--ajaxslim-busy-delay/fade`) | none | low (single class) | **easily** — the model to follow |
| `ajaxslim-sortable.css` | yes (`--ajaxslim-drag-opacity/shadow`) | none | low | **easily** (written to this contract) |
| `ajaxslim-modal.css` | none | none | moderate (`dialog.ajaxslim-modal .ajaxslim-modal-body`) | yes, but no tunable knobs |
| `wonder-select.css` | none | none | **high** (`wonder-select.ws-host select.ajax-popup-button`, `:has()` chains) | **hard** — app must out-specify it |

So the gap is real: `busy` and `sortable` already model the contract; `modal` exposes no knobs; and
`wonder-select` is genuinely hard to override.

## The contract (three layers)

### 1. Foundation — overridable by construction

Every framework CSS rule must be beatable by ordinary app CSS:

- **No `!important`.** Ever. (Transient states like `html.ajaxslim-sorting` get their reach from a
  qualified selector, not `!important`.)
- **Keep specificity low and flat.** Prefer a single stable class per styleable piece
  (`.ajaxslim-dragging`, `.ajaxslim-modal-body`). Avoid deep descendant chains and `:has()` where a
  single class would do. The more specific our selector, the more an app has to mirror it to win — which
  is exactly the wonder-select problem.
- **Stable, documented class hooks.** The classes/attributes we style are public API (they already are
  for the JS: `data-sortable-grip`, `.ajaxslim-dragging`, `html[data-ajaxslim-busy]`). An app styling
  against them must be able to trust they won't churn.

This layer alone makes everything overridable. Custom properties below are *convenience*, not the
mechanism.

### 2. Convenience — tunable custom properties

Expose the **commonly-tuned values** (colors, sizes, durations, shadows) as CSS custom properties with
sensible defaults, so the 80% case is a one-liner that needs no specificity battle:

```css
:root { --ajaxslim-drag-shadow: none; }   /* kill the sortable lift's shadow app-wide */
```

Naming convention: **`--ajaxslim-<element>-<property>`** (`--ajaxslim-busy-fade`,
`--ajaxslim-drag-opacity`, `--ajaxslim-modal-backdrop`, …). One prefix, predictable, greppable.
`busy` and `sortable` already follow this; `modal` and `wonder-select` should grow the props that are
actually worth tuning (modal: backdrop colour/blur, max-width, radius; wonder-select: the harder case —
may need both props *and* a specificity reduction).

### 3. Escape hatch — suppress the default stylesheet entirely

For the app that wants to own an element's look completely (not tweak it). **This is the open question
— see below.** Options:

- **Global property:** `er.ajax.elements.defaultStylesheets=false` (or per-element keys) — skip the
  `addStylesheetResourceInHead` call. Coarse, app-wide, zero template churn.
- **Per-element binding:** `disableDefaultStylesheet="$true"` on the element — fine-grained, but adds a
  binding to every CSS-shipping element and only helps where the element is the stylesheet's loader.
- **No hatch:** rely on layers 1+2. If our CSS is always overridable, "own it completely" = override
  everything, which low-specificity makes feasible. Least surface, but an app replacing (not tweaking) a
  big stylesheet like wonder-select's 156 lines may prefer to just not load ours.

Lean: probably the **global property** (simplest, matches how WO apps configure framework behaviour via
properties), but it's worth deciding deliberately rather than defaulting.

## Open decisions (need sign-off before applying)

1. **The escape hatch** — global property, per-element binding, or none? (The one explicitly deferred
   here.)
2. **wonder-select** — it's the hard case. Reduce its specificity (risky — its `:has()`/host selectors
   may be load-bearing for the morph-native widget behaviour) or accept it's override-by-custom-props
   only and document that? Needs a careful look at *why* the specificity is high before flattening it.
3. **How far to retrofit** — do `modal` and `wonder-select` get the full custom-property treatment now,
   or just the foundation guarantees (no `!important`, which they already meet) plus props added lazily
   when someone needs them?

## Apply order (once signed off)

1. Audit all four files against layer 1 (no `!important`, specificity sanity). `busy`/`sortable` pass;
   confirm `modal`; assess `wonder-select`.
2. Add layer-2 custom properties to `modal` (and `wonder-select` if feasible), consistently named.
3. Implement the chosen escape hatch (layer 3) in the shared `addStylesheetResourceInHead` path or per
   element.
4. Document the contract on the AjaxSlim guide (a short "Styling AjaxSlim elements" section): the class
   hooks, the `--ajaxslim-*` props, and the opt-out.

## Why this is worth formalizing

It's a small surface (four files), but it's **app-facing API** — once apps style against these classes
and props, the contract is hard to change. Pinning "no `!important`, low specificity, `--ajaxslim-*`
props, documented hooks" now, while only two files are styled to it, is far cheaper than retrofitting a
convention after a dozen apps have each found their own way to out-specify our CSS.

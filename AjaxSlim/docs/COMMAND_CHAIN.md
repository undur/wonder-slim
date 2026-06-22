# Update response as a command chain — design

**Status:** design proposal, for discussion. No code yet. This is where we're headed, not a today task.

## Goal

Make an AjaxSlim update response an explicit, ordered list of **commands** the client executes, instead
of the implicit "it's either a bag of fragments *or* some JavaScript" it is today. The immediate payoff
is being able to **mix update semantics in one response**:

> Update two cheap containers *immediately* (their fresh HTML rides back in this response), while
> *triggering* a third heavy/slow container to refresh on its **own** later request — so the user sees
> the fast stuff instantly and the expensive container streams in when it's ready, without blocking the
> response on it.

```
response = [
  morph   "summary"  with <fresh html>,    // inline — data is here now
  morph   "details"  with <fresh html>,    // inline
  trigger "heavyChart",                     // deferred — client fetches it separately
]
```

The client executes the chain top to bottom: morph the two cheap containers instantly, then fire the
deferred fetch for the heavy one. One mental model, composable.

## The two update semantics (today, and why they should compose)

There are genuinely two different things, and AjaxSlim already does both — it just treats one as the
blessed path and the other as deprecated cruft. They are not "good vs legacy"; they are **synchronous
vs deferred**, and both are useful:

| | Inline update | Trigger update |
|---|---|---|
| API today | `AjaxUpdater.add` / `set` | `AjaxUpdater.triggerUpdate` / `triggerSafeUpdate` |
| What comes back | the container's fresh HTML, as an `<ajaxslim-fragment>` in **this** response | an `AjaxSlim.AUC.update('id')` JS command that makes the client issue a **separate** fetch |
| Round-trips | 1 | 1 + N (one extra fetch per triggered container) |
| Right when | the data is cheap / already computed | the container is heavy, slow, or independently cacheable — don't make the fast 90% wait for it |

**Consequence for the deprecation:** once the command chain exists, `triggerUpdate` should be
**un-deprecated**. The N-extra-fetches mechanism isn't legacy — it is the correct primitive for a
*deliberately deferred* refresh. It's only "bad" today because there's no way to combine it with inline
updates in one response, so it reads as the worse of two whole-response modes. The command chain removes
that false choice.

## Where we are today (what this replaces)

The response is implicitly one of two shapes, and the client sniffs which:

- **Fragment shape** — one or more `<ajaxslim-fragment data-id="…">…</ajaxslim-fragment>` blocks. The
  server frames *every* targeted container this way (`AjaxUpdateContainer.handleRequest`), uniformly,
  whether one container or many, client- or server-targeted.
- **Script shape** — a `text/javascript` body or `<script>` tags, run globally. This is the
  `triggerUpdate` JS-command path, and the "action returned arbitrary JS" path.

The client dispatch is literally:

```js
// ajaxslim.js, simplified
if (/<ajaxslim-fragment\b/i.test(text)) {
    applyFragments(text);          // demux + morph each into its container
} else {
    Morph.runResponseScripts(text, contentType);   // run as JS
}
```

The two shapes are **mutually exclusive in one response** — that's exactly the limitation. You can
return fragments, or you can return JS, but not "morph these two and trigger that one." The command
chain dissolves the `if/else` into "execute each command by its type."

## The mechanism (sketch — this is the part to argue about)

### A command is a typed, framed block

Generalize today's `<ajaxslim-fragment>` into a command with a `type`. Strawman wire format (kept
HTML-ish so it survives the same response pipeline, content-type sniffing, and Parsley's injection-on-
`</body>` guard — see [[parsley_injection_corrupts_js]]):

```html
<ajaxslim-command type="morph"   data-id="summary"> …fresh html… </ajaxslim-command>
<ajaxslim-command type="morph"   data-id="details"> …fresh html… </ajaxslim-command>
<ajaxslim-command type="trigger" data-id="heavyChart"></ajaxslim-command>
```

The client parses the chain in order and dispatches each command:

- **`morph`** — reconcile `data-id`'s container with the inner HTML via Idiomorph (today's fragment
  behaviour; honours `data-morph`, fires `onRefreshComplete`).
- **`replace`** — innerHTML replacement (the `morph="$false"` case), as a distinct command rather than
  an attribute, if we want it explicit.
- **`trigger`** — issue a separate fetch to refresh `data-id` on its own request (today's
  `AUC.update('id')`). The deferred primitive.
- **`run-js`** — run a script body. Subsumes the current `text/javascript` path *and* the per-container
  `onRefreshComplete` scripts — they become `run-js` commands in the chain rather than a special case.

Today's two response shapes both become **degenerate chains**: a fragment response is a chain of all
`morph` commands; a script response is a chain of one `run-js`. So the migration is "the existing
behaviour is the special case," not a rewrite of behaviour — only the framing and the client dispatch
change.

### Server API

`AjaxUpdater` already has the right verbs; they just need to all feed one ordered command list on the
response instead of two separate mechanisms:

- `add` / `set` → append `morph` commands (inline).
- `triggerUpdate` → append a `trigger` command (deferred). **No longer deprecated.**
- a new escape hatch for `run-js` if an action wants to push arbitrary client JS in-chain.

The client-declared `updateContainerID="a;b;c"` path also lowers to `morph` commands, same as today.

## Open decisions (need sign-off before building)

1. **Ordering & timing contract.** Inline `morph`s apply synchronously; a `trigger` fetch lands later
   (async). Is the chain "apply all inline commands, *then* fire triggers"? Are triggers fired in
   parallel or sequentially? Does a `run-js` command run before or after the morphs it sits between in
   the chain — i.e. is execution strictly positional, or are there phases (morph-phase, then js-phase,
   then trigger-phase)? Positional is simplest to reason about; phased may match what authors expect.

2. **Trigger vs inline collision.** If the same container id is both inline-`morph`ed *and* `trigger`ed
   in one response (e.g. server adds it, client also requested it), the trigger is redundant — the
   container is already fresh. Suppress the trigger? Last-command-wins? Error? Suppression seems right
   (it's already been updated this round) but needs a rule.

3. **Wire format.** Is `<ajaxslim-command type="…">` the right framing, or do we want something more
   structured (a JSON manifest + HTML payloads)? HTML-framed keeps the response pipeline and the
   content-type story unchanged and dodges the `</body>`-injection corruption class; JSON is cleaner to
   parse but changes the transport. Lean HTML-framed for continuity, revisit if it strains.

4. **Does the chain fully replace the dispatch, or augment it?** Cleanest is *replace*: there is only
   ever a command chain (the degenerate cases cover today's two shapes), and the `if (/fragment/)`
   sniff is deleted. That's a breaking change to the wire format — fine, since we control every client
   (the client is `ajaxslim.js`, shipped with the framework) and the package/API churn already in
   flight gives a natural migration window. Confirm we want the clean break vs a compat period.

5. **`onRefreshComplete` as `run-js`.** Folding per-container completion scripts into chain commands is
   elegant but changes their ordering guarantee (today they run right after their fragment morphs). Make
   sure "morph X, then run X's onRefreshComplete" stays expressible — probably a `run-js` command
   immediately after the `morph` for that id.

## Build order (once signed off)

1. Define the command vocabulary + wire format; write the client chain-executor alongside the current
   dispatch (feature-flagged or sniffed), so old and new responses both work during the cutover.
2. Lower today's fragment framing to `morph` commands and today's JS responses to `run-js` commands —
   prove the degenerate chains reproduce current behaviour (the existing `server-update`,
   `server-update-fragments`, focus/accumulation/nested scenarios should pass unchanged).
3. Wire `AjaxUpdater.triggerUpdate` to emit a `trigger` command **in the same chain** as `add`/`set`
   morphs; un-deprecate it. Add a playground scenario for the headline case (two inline + one deferred
   heavy container).
4. Delete the old `if (/fragment/)` dispatch once everything is on the chain.

## Why this is the natural next step

- It's the honest model: the response already carries heterogeneous instructions (morph this, run that);
  the command chain just names them and lets them coexist.
- It rehabilitates `triggerUpdate` from "deprecated wart" to "the deferred-refresh primitive," which is
  the right framing — and the recent rename (`update`→`triggerUpdate`) was chosen with this in mind.
- It composes with the from-scratch identity rethink ([[project_ajax_from_scratch_design]]) and the
  server-side update redesign ([[project_server_side_update_redesign]]) rather than conflicting — both
  are about *what the server decides to send*; this is about *how the client is told to apply it*.

## Related

- `MULTI_UPDATE.md` — the one-trigger-many-containers feature; its uniform fragment framing is exactly
  what generalizes into `morph` commands here.
- `AJAX_FROM_SCRATCH.md`, `SERVER_SIDE_UPDATE_REDESIGN.md` (if present) — adjacent rethinks of identity
  and same-pass rendering.

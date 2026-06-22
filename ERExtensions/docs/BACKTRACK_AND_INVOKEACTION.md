# Backtrack, reload, and the invokeAction conflation

> **Status: design exploration / problem statement.** Not a fix and not shipping behaviour. It names a
> critical error-path problem in WO's request model, the conceptual root cause, and a spectrum of
> directions — to be solved properly in ng-objects, if not in WO. Connects to the page-identity rethink
> in [`../../docs/AJAX_FROM_SCRATCH.md`](../../docs/AJAX_FROM_SCRATCH.md); this is the backtrack corollary
> of that idea. No code.

---

## The problem, in one sentence

A **backtrack or reload re-runs `invokeAction`**, so a navigation that should mean "show me the state I
was at" can instead *re-perform a side effect* — backtrack onto a delete-and-return-page URL and you may
delete again. A navigation through history should never re-execute anything.

## Why WO can't just "re-render instead of re-invoking"

The obvious fix — "on backtrack, re-render the page instead of re-invoking the action" — does not work
in WO, and seeing *why* is the crux:

> **`invokeAction` determines what gets generated.** The page you receive *is* the return value of the
> action. There is no render step separable from the invoke — the invoke IS the render decision.

So you cannot re-render a backtracked action without re-invoking it, because the action is what decides
*what to render*. Render and side-effect are welded together in one phase. That weld is the original sin.

## The sharper idea: a backtrack replays the last response for that instance

If you can't re-invoke (side effects) and can't re-render (no render separable from invoke), the way out
is to do **neither**: a backtrack returns the **bytes already produced** for that page — the cached
response artifact, keyed to the context. Backtrack becomes "give me back what I saw," literally a replay,
touching no application code.

This is conceptually clean and avoids the re-execution hazard entirely. But it opens a can of worms, and
naming the worms is how we judge whether it's tractable.

### Worm 1 — faithful to "what you saw" vs "what's true"

The replayed response is a snapshot of bytes. If the underlying data changed since (another user edited
the object, a job ran), backtrack shows pixels that no longer reflect reality. PRG-style re-rendering is
faithful to *what's true*; byte-replay is faithful to *what you saw*. A backtrack is a history operation,
so "what you saw" is arguably the right semantic — but it's a deliberate choice, not free.

### Worm 2 — the dead bytes vs the live instance

You backtrack, get the replayed bytes, then *interact* (click a link in that page). That request carries
a contextID pointing at... what? If the live page **instance** aged out, the next interaction
"backtracks too far" anyway — the problem just moved one click downstream. If you keep the instance alive
to receive it, you've made the cache hold instances longer (memory). (This is exactly the Safari
behaviour observed against the `no-store` stopgap: replayed-looking bytes over a still-live instance that
"just keeps working" — bytes and instance out of sync but both surviving.)

### Worm 3 — what *is* "the last response for that instance"?

An instance generates many responses over its life (every ajax update, every backtrack re-renders it).
"Last" is a moving target. Cache every response per context, or only the most recent? Keyed by contextID
(which changes every interaction) or by a stable page identity? With per-interaction contextIDs, "the
response for this page" is ambiguous; with a stable identity it is well-defined.

## The worms share one root cause

Worms 2 and 3 both point at the same thing: WO ties **page identity**, **instance lifetime**, and
**response artifact** to a single, ever-changing key — the contextID. Every interaction mints a new one
(the same reflex [`AJAX_FROM_SCRATCH.md`](../../docs/AJAX_FROM_SCRATCH.md) identifies as the source of the
ajax page-cache explosion and the cross-instance bleed). Because the three are conflated:

- "the last response for this page" is ill-defined (no stable page key) → worm 3
- instance lifetime can't be reasoned about separately from response caching → worm 2

**A stable per-page identity untangles them.** Given a `pageKey` that is constant across a page's
interactions (the from-scratch proposal): "the response for this page" becomes well-defined (worm 3
dissolves), and instance lifetime becomes a deliberate, separable policy rather than an accident of
contextID churn (worm 2 becomes a decision, not a bug). So the backtrack problem and the page-identity
rethink are **the same problem from two angles**:

- from-scratch asks: *what is a page's stable identity?*
- backtrack asks: *what should a navigation to a past identity return?*

"Replay the last response for that instance" is only cleanly answerable once the first question has a
clean answer.

## The principle to design toward

> **Backtrack and reload are pure operations. Only a deliberate action invocation runs side effects. The
> two must be separated by the request model itself — not conflated in one contextID that both restores
> state and re-invokes behaviour.**

## Implementation spectrum (for ng-objects)

From pragmatic to foundational:

1. **POST-redirect-GET for actions.** An action that returns a page redirects to a clean render URL; the
   action URL never enters history, so backtrack/reload hits the render URL and re-renders, never
   re-invokes. Solves the *re-execution* class with a known pattern and modest effort. Does NOT by itself
   solve "what does a backtrack render" (still re-renders fresh → worm 1's other horn) but removes the
   dangerous one.
2. **Explicit phase separation.** A request either *restores/renders* a page (pure, never invokes) or
   *invokes* an action (one-shot, side-effecting, must then redirect). Make it structurally impossible
   for one request to do both. The contextID (or pageKey) becomes purely a render-state key, never an
   action token.
3. **Replay-the-response.** Backtrack returns the cached response artifact for the page's stable
   identity — no invoke, no re-render. Requires the stable `pageKey` (above) to be well-defined; inherits
   worms 1–2 as explicit policy choices (staleness semantics, instance-lifetime policy).
4. **Reconstructible state.** The deepest: a page's state is *derivable from its URL/key* rather than
   *retrieved from a bounded cache*. Then "backtracked too far" cannot occur — there is no cache to age
   out; backtrack is never an error because state is derived, not stored. Hardest; the natural endpoint
   of the from-scratch identity model.

## Why this is worth solving

"Backtracked too far" and accidental action re-execution are not edge cases — they are a **critical error
path** every stateful WO app hits, and the current handling (bound the caches, patch the error status,
disable client caching per-page) treats symptoms. WO carries the conflation for compatibility.
**ng-objects does not have to inherit it** — it can separate restore from invoke at the request-model
level, which is where this belongs. Pair this with the page-identity rethink rather than solving it
standalone.

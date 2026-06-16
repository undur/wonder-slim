# Playwright bridge

A small headless-browser driver that lets an agent (or a script) **act on a running
WebObjects app and read back what the browser actually did** — rendered DOM, console
messages, and the network requests a single interaction triggered. It exists so the
client-side behaviour of the Ajax framework (DOM morphing in particular) can be tested
automatically instead of only by a human clicking around.

It pairs with the **AjaxPlayground** app (login-free, stable element ids) as its target.

## Why it lives here (and might move later)

It lives in `wonder-slim/tools/` for now because wonder-slim is what we're working on and
is a dependency of every downstream app, so the bridge travels with it. It is deliberately
split into:

- **command core** (`src/commands.js`) — pure browser logic: given a Playwright page and a
  step, do the action and capture the result. Knows nothing about *how* the request to run
  it arrived.
- **transport** (`src/cli.js`) — the thin layer that receives work. Today it's a CLI
  (`node run.js <script.json>`). Later this could become an HTTP endpoint inside the
  Parslips dev server (so `localhost:9485` gains browser-driving alongside refresh/validate).

Migrating to Parslips is then a transport swap, not a rewrite — the command core is reused
verbatim.

## What it captures

For each run against a URL it returns JSON:

- `console` — every browser console message (level + text), so JS errors surface.
- `requests` — every network request the page made, with method + url + a tag for which
  step triggered it. This is how we assert "one change fired N requests" (the
  observer-accumulation / duplicate-request class).
- `steps` — per-step results (e.g. the value/attribute/innerText/activeElement snapshots a
  `read` step asked for).
- `failures` — assertions that did not hold.

## Usage (CLI transport)

```bash
npm install            # first time; also installs the chromium browser
node run.js examples/focus.json
```

A run script is a JSON object: `{ "url": "...", "steps": [ ... ] }`. See `examples/`.

Optional top-level keys:

- `viewport` — `{ "width": N, "height": N }` to pin the window (for viewport-relative behaviour).
- `allowPageErrors` — `true`, or an array of substrings, to whitelist EXPECTED page errors. Use when a
  scenario deliberately throws (e.g. `scripts` throws mid-batch to prove inline-script isolation); without
  this, the runner treats any uncaught page error as a failure.

## Running the whole suite

ALWAYS run the full suite with the serial runner, never a shell `for` loop:

```bash
node run-all.js            # every examples/*.json, one at a time, against the auto-discovered port
node run-all.js multi      # only scenarios whose filename contains "multi"
PORT=1200 node run-all.js  # pin the port instead of auto-discovering
```

The AjaxPlayground is a single WebObjects instance and serialises requests; driving it from two browsers
at once (a backgrounded `for` loop) makes scenarios intermittently time out or read stale state — flaky
"errors" that are really contention. `run-all.js` launches ONE browser, runs each scenario in its own fresh
context (no session/counter bleed), prints a per-scenario pass/fail summary, and exits non-zero if anything
failed (so CI / an agent can branch on it). The port is discovered by probing for a live AjaxPlayground.

### Assertion + read vocabulary

`assert` comparisons: `equals`, `notEquals`, `contains`, `notContains`, `changedFrom` (differs from another
named read), `atMost`, `atLeast`. `read` `what`: `value`, `text`, `attribute`, `activeElementId`, `count`,
`exists`, `visible`, `requestCount`, `withinViewport`, `boundingEdge`, and `jsExpression` (evaluate an
arbitrary JS expression in the page — for probing state a selector can't reach, e.g. a `window.*` marker set
by an `onRefreshComplete` hook).

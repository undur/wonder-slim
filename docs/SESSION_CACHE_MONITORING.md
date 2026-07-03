# Session cache monitoring — findings & plan

A place to record what we've learned about WO/wonder session page caches and the plan for an overview that
lets us right-size them (and, later, understand their true memory weight).

## The caches involved (all per-session unless noted)

| Cache | Scope | Holds | Bound by | Configured by |
|---|---|---|---|---|
| WO backtrack cache | per session | page contexts (back-button / re-submit targets) | count (`pageCacheSize`, WO default 30) | `setPageCacheSize` |
| WO permanent page cache | per session | explicitly-retained long-lived pages | count (`permanentPageCacheSize`, default 30) | `setPermanentPageCacheSize` |
| ERXAjaxSession replacement cache | per session | page **instances** (so ajax component-actions inside updated regions resolve) | count of distinct **instances** (`er.extensions.maxPageReplacementCacheSize`, default 30) | property |
| Cayenne shared snapshot cache (`DataRowStore`) | **process-wide** | `DataRow`s (raw column maps), shared across all contexts | Cayenne's own policy | Cayenne config |

The first three are the WO/wonder page caches. The fourth is shared infrastructure — **must be reported
separately, never folded into a per-session figure.**

## Key finding 1 — eviction is pure size-LRU, with NO aging

Verified in `ERXAjaxSession` (`enforcePageReplacementCacheInstanceLimit` + `touchInstanceInReplacementCache`):
the replacement cache is a `LinkedHashMap` bounded **only by instance count**, evicting the least-recently-
used instance when a genuinely new one pushes over the limit. There is **no TTL / idle / age-based eviction
anywhere** (grep for `TTL|maxAge|expir|idle|stale` finds nothing).

Consequence: an **abandoned page is never evicted on its own merits.** A user opens a heavy page (e.g. the
invoice editor), works, navigates away — that page instance (and everything it retains) stays pinned until
either (a) enough *new* pages arrive to LRU it out, or (b) the session times out (NB: **4 hours**). With a
cache cap larger than the real working set, the abandoned entry may **never** be evicted before session
death. So a session's steady state is typically *a few active pages + many abandoned-but-retained ones*.

Implication for tuning: the size cap bounds the **worst case** but does nothing about the **common case** of
stale-but-not-yet-evicted entries. **Age data is what surfaces that** — and points at whether a smaller cap
(or a future idle-eviction policy) would reclaim memory with no user-visible cost.

## Key finding 2 — the age data already exists, unused

`ERXAjaxSession.TransactionRecord` already carries `createdAt()` and `lastAccessedAt()` (the latter updated
by `markAccessed()` on every restore). The code comments note they are "free to read and never affect cache
behavior." So `now - lastAccessedAt` is a **ready-made idle-time signal** we can surface at zero cost. No new
instrumentation needed for the age view of the replacement cache.

## Key finding 3 — with Cayenne, the weight is mostly NOT the page

NB uses Cayenne with **locally-scoped ObjectContexts** (often bound to the component/page, not the session).
So a cached page is a thin shell; the heavy thing hanging off it is its **ObjectContext** and that context's
registered `DataObject`s. Caching N page instances can pin N ObjectContexts and their object graphs.

This means real "session footprint" is mostly a **Cayenne** question (registered-object counts per OC, by
entity), separable cleanly because the OCs are locally scoped — a natural measurement boundary (stop at
`ObjectContext`; don't follow into the shared `DataRowStore`). The cheap, exact, high-value metric is
**object counts by entity per OC**, not bytes. Bytes (via `jol`, sampled) are a later, optional refinement.

## Key finding 4 — the metric that actually matters is REACH DEPTH

The optimization question is not "how big is the cache" but **"how big does it need to be?"** Three quantities:

- **Cache size** — entries currently held (capacity used).
- **Active cache size** — entries actually reached recently (the live working set).
- **Reach depth** — on each restore, the **LRU-rank of the resolved entry** (0 = most-recent, N = Nth from the
  tail). The distribution of reach depths over a session answers the real question directly: if 99% of
  restores resolve entries within the top ~5 LRU positions, the *needed* cache size is ~5–10 regardless of
  the cap, and everything past the deepest real reach is provably dead weight. **Size the cap just above the
  observed max reach, not 100x it.**

Instrumentation note: age (`createdAt`/`lastAccessedAt`) is free (already recorded). **Reach depth is the one
NEW measurement** — it requires recording the resolved entry's index in the `LinkedHashMap` order at restore
time. Hook point confirmed: `restorePageForContextID`, right where `pageReplacementCache.get(contextID)`
resolves a `TransactionRecord` (before `markAccessed()`). The map's iteration order IS the LRU order, so the
entry's position = reach depth. Cheap, read-only against the map; record into a per-session histogram.

## Plan (staged)

1. **NOW — session cache overview component.** A **standalone reusable component** (in wonder-slim) showing,
   per session: each page cache's entry count vs. its cap, a **by-page-class** breakdown, **age** columns
   (created / last-accessed / idle) for replacement-cache entries, and the **reach-depth** stats (max reach,
   reach histogram) once instrumented. Goal: right-size the three caps from real data and *see* the
   abandoned-instance tail / the active-vs-held gap. Reuses `pageReplacementCacheSummary()` and the existing
   `createdAt`/`lastAccessedAt`. Lives in wonder-slim (every app benefits — the cache machinery is in
   ERExtensions, not Ajax.framework).
   - **Template:** a Bootstrap-friendly subset that renders acceptably under both Bootstrap 3 and 5. When a
     choice is forced, **target Bootstrap 5** (NB is migrating everything there). Stick to the stable
     intersection: `table`/`table-striped`/`table-sm`, `badge` (avoid bg-color utilities that differ across
     versions — `badge badge-secondary` (bs3) vs `badge bg-secondary` (bs5); prefer a neutral class +
     inline-minimal styling, or plain `<span class="badge">` with our own color), `progress`/`progress-bar`,
     `card`. Avoid bs5-only spacing/flex utilities and bs3-only `panel`/`label`.
   - **Split:** generic report engine + current-session view in wonder-slim (works everywhere); the app
     (NB) supplies all-sessions enumeration (`SessionManager.singleton()`) and admin gating on top.
2. **LATER — Cayenne object-count layer.** Per cached page, reach its component-bound ObjectContext(s) and
   report registered-object histograms by entity, rolled up per page class / session; plus a separate
   process-wide `DataRowStore` report. Count-based, cheap, exact. This is "partly a Cayenne project." Open
   coupling question: how a cached page exposes its OC(s) to the reporter (interface vs. base-class
   convention vs. reflection sweep).
3. **BONUS / LATER — byte weight.** `jol` `GraphLayout` on a *sample* OC graph (stop-boundary at the shared
   store) to turn "12,000 InvoiceLines" into a rough MB figure. Caveat: cached graphs share structure, so
   per-page byte numbers are **directional, not additive**; the only honest total-footprint number comes
   from a heap dump's dominator analysis (Eclipse MAT), which stays the ground-truth validation tool.

## Architecture decision — neutral model over BOTH cache families, minimal touch

The reporter must cover **both** ERXAjaxSession's replacement cache *and* WO's own page caches (backtrack +
permanent), which are structurally different. So we do **not** bake reporting logic into either cache.
Instead:

- **Neutral DTO** — `CachedPageEntry { pageClass, contextID, createdAt?, lastAccessedAt?, reachDepth? }` and
  `CacheReport { cacheName, cap, entries[] }`. Knows nothing about which cache it came from. Age/reach are
  **nullable** — present for our cache, absent ("—") for WO's, made explicit rather than faked.
- **One adapter per cache family**, each emitting neutral DTOs:
  - **ERXAjaxSession** → via a small **read-only accessor** added to ERXAjaxSession (the map lives under a
    private session key, unreadable from outside). Additive, read-only; does **not** alter eviction/storage/
    records. This is the *only* cache touch in Phase 1.
  - **WO backtrack/permanent** → via **reflection** on `WOSession._contextRecords` / `_permanentPageCache`
    (both private, no getter). Reads `WOTransactionRecord.responsePage()` + `contextID()`. WO records carry
    **no wall-clock age** (only `touch()`/`isExpired()` for the bounce timeout), so age = null there.
- **Reporter + component** consume only DTOs — identical over both caches; a future third cache is just
  another adapter.

### Minimal-touch summary
- **Created + last-touched timestamps already answer the sizing question** for our cache: size, **active**
  size (touched within N min), and the **stale/abandoned tail** (`now - lastAccessedAt`). Phase 1 needs no
  new cache *logic* — just the read-only accessor.
- **Reach depth is DEFERRED.** Idle-time is a strong proxy (a deep-but-still-touched entry is rare; the
  long-idle entries are the dead tail). Add the restore-time reach hook only if the precise LRU-rank
  distribution turns out to be needed after seeing idle-time in practice. Phase 1 touches cache behavior
  **zero**.

## TODO — live JVM/heap monitoring on the dashboard (parked)

Separate from the per-session cache view: a **live stacked heap chart** (eden + survivor + old over time,
GC events marked) on the ops dashboard. Notes from working it out:

- **Right tool for a live dashboard chart = Micrometer → Prometheus → Grafana**, not JFR. Micrometer's JVM
  binders (`JvmMemoryMetrics`, `JvmGcMetrics`) expose per-generation occupancy + GC counts/pauses as
  time-series; the stacked-heap chart is a canned Grafana JVM panel. Needs a `/metrics` endpoint (WO can
  serve it via a direct action) + the binder registration — an afternoon's work.
- **JFR is the complement, not the dashboard.** JDK Flight Recorder (~1% overhead, built in) is a
  record-then-analyze forensic tool. Enable continuous recording (`-XX:StartFlightRecording=disk=true,
  maxage=6h,...`) so the *next* weird event is `jcmd <pid> JFR.dump` + open in JMC. JFR *event streaming*
  (`RecordingStream`, Java 14+) could feed the same chart but is more work for the same picture.
- **Both need code/agent in the process → a redeploy.** Land them alongside the cache-cap redeploy.
- **No-redeploy stopgap:** scrape `jstat -gcutil <pid>` columns into the dashboard (crude, but the same
  data we watched by eye — zero code, works on the running process).
- Like the cache monitoring, the reusable part belongs in ERExtensions/helium5 so every app gets it free.

Observed-behavior note (2026-07-01, first-of-month load, ~12 GB G1 heap): healthy sawtooth — Old gen
climbed ~50%→90% over ~40 min, then a single young GC (~130 ms) dropped it back to ~50%, FGC stayed 0
throughout. The climb was collectable garbage (abandoned session graphs), not live-set growth: GC already
reclaims abandoned-session memory regardless of session-store harvesting. Watch the **floor after GC**
(rising floor = real leak) and **FGC** (0 = concurrent collector keeping up), not the peak. Fat caches
inflate the sawtooth *amplitude* (less margin, more frequent GC), which is what right-sizing the caps fixes.

## GC baseline — BEFORE the cache-cap fix (measured 2026-07-01, first-of-month load)

Baseline captured on the live accounting instance (NB) running the **absurd defaults**
(`pageCacheSize=5000`, `permanentPageCacheSize=5000`, `maxPageReplacementCacheSize=10000`; 4-hour session
timeout). To be compared against the same readings after deploying the fixed values (`100 / 30 / 100`).

- **Environment:** G1GC, `-Xmx` ≈ 12 GB (12582912K reserved, committed). OpenJDK 25. `jstat -gcutil <pid>`.
- **Live-set floor (Old gen after a young GC):** ~50% (~6 GB).
- **Sawtooth peak (Old gen before collection):** ~88–90%.
- **Sawtooth period:** ~30–40 min to climb floor→peak under morning load.
- **Young GC:** ~one every ~5–6 min under load; ~130–160 ms each; cheap.
- **Full GC (FGC):** **0** across the whole morning — the concurrent collector never fell behind.
- **Survivor (S1):** pinned at 100% (promotion straight to old on young GC).

Raw sample points (Old gen % / YGC count / FGC), one busy morning:

| Time (approx) | O (Old %) | YGC | FGC | Note |
|---|---|---|---|---|
| start | 49.6 | 122 | 0 | early, light load |
| +1h | 53.6 | 127 | 0 | ramping |
| +2h | 62.1 | — | 0 | ramping faster |
| +2h10m | 89.0 | 134 | 0 | promotion wave (looked alarming) |
| +2h20m | 89.5 (flat) | 134 | 0 | plateau, allocation quiet |
| +2h40m | 49.7 | 135 | 0 | **one young GC dropped O 90→50** (~130 ms) |
| +3h10m | 88.2 | 140 | 0 | climbed again — steady sawtooth |
| +4h (midday, ~12:00) | floor rose to ~69–94 band | 152→159 | 0 | full-load sawtooth: Old-gen *floor* drifted up from ~50% (morning) to ~69% as more concurrent sessions accumulated live-ish cache. Peaked ~94% (looked alarming — 0 headroom), then **one young GC dropped O 94→69% in ~200 ms, no full GC**. |

**Key lesson (2026-07-01):** a high, pinned Old-gen *floor* (even ~94%) is NOT an OOM risk by itself — G1
deliberately defers collection until Old gen is high, then reclaims a big chunk cheaply. Confirmed live: O
sat pinned at ~94% for ~10+ min, then a single young GC reclaimed ~25 points (~3 GB) with FGC still 0. **The
only true danger signal is FGC leaving 0** (collector *forced* into stop-the-world full GC). It never did,
all day. Watch the FGC counter, not the O level. The higher midday floor (~69% vs morning ~50%) IS the
oversized caches (more live sessions retain more cache) — a margin cost the fix targets, not a stability threat.

**Interpretation:** healthy but **high-amplitude** sawtooth. Peak ~90% because the fat caches retain a lot
of collectable garbage between cycles; floor stable at ~50% (no leak). Fine, but little headroom and
frequent GC.

### What to capture AFTER the fix (same instance, comparable load — ideally next first-of-month, or any
comparable busy day)

Record the same fields so the comparison is apples-to-apples:
- Old-gen **floor** (after a young GC) — expected: similar (~50%; live-set is unchanged by cache caps... or
  slightly lower if the caches held some genuinely-live abandoned graphs).
- Old-gen **peak** — **expected to drop** from ~90% to ~65–70% (less garbage retained per cycle).
- Sawtooth **period** — **expected to lengthen** (less garbage generated per unit time → slower climb).
- **YGC frequency** — **expected to fall** (fewer collections needed).
- **FGC** — expected to stay 0 (it already was; headroom only improves).
- Note load context (time of day, active session count from the cache-overview page) so "comparable load"
  is honest.

Prediction to test: **peak ↓, period ↑, YGC frequency ↓, floor ≈ unchanged, FGC = 0.** If the floor also
drops meaningfully, that quantifies how much genuinely-live memory the oversized caches were pinning.

### AFTER the fix — first reading (2026-07-03, fresh JVM, LIGHT/slow-morning load)

Redeployed with the corrected caps (`pageCacheSize=100`, `permanentPageCacheSize=30`,
`maxPageReplacementCacheSize=100`). Same 12 GB G1 heap. First reading, ~9 young GCs in, low traffic:

- **Old gen: ~0.56%** — essentially empty. (Before: floored ~50%, climbed to 90%+ by mid-morning.)
- **FGC: 0**, total GC time 0.28s (fresh JVM).
- **Survivor (S1): 100%** — still pinned. So the *cache fix did not change survivor saturation* (that's a
  separate tuning axis — survivor ratio / tenuring). But now promotion lands in a near-empty old gen, so
  S1=100% is harmless here, vs. before when it fed an already-full old gen.

Caveat: this is a **fresh JVM on a light morning**, so part of the low old-gen is "just started + low load",
not purely the fix. The honest apples-to-apples is a reading once load builds (a busy hour, ideally next
first-of-month). But even discounted for load, ~0.5% old-gen vs. the old config's early-morning ~50% floor
is a dramatic, real improvement. **The prediction (floor & peak far lower, FGC 0) is holding so far.**

TODO: capture an **AFTER (full load)** reading to complete the comparison — expect the old-gen floor well
below the old 50–69%, a shallow low sawtooth, FGC still 0.

## Caveat carried throughout

Don't sum per-page byte estimates into a "session total" — shared object graphs (and the shared snapshot
cache) make naive sums wrong both ways. Counts are safe to roll up; bytes are not.

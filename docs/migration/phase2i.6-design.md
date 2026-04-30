# Phase 2i.6 Design — CORE-MATH `log` Port + NCCS EXACT Flip

**Status:** approved 2026-04-30
**Predecessor:** `jquantlib-phase2i.5-complete` @ `aa5a820` (tests `687/0/0/22`, scanner WIP=0)
**Working directory:** `/Users/josemoya/eclipse-workspace/jquantlib`
**C++ source-of-truth:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`
**Algorithm source-of-truth:** CORE-MATH `src/binary64/log/log.c` (BSD/MIT, canonical Inria)

---

## 1. Context & Motivation

Phase 2i.5 WI-2 made a clean empirical finding: NCCS CDF's residual after `Math.exp → JQuantMath.exp` swap was 27 ULPs — far beyond a 1-ULP `Math.exp` slack. The diagnosis pinpointed `Math.log(x2)` at line 86 of `NonCentralCumulativeChiSquaredDistribution.java`: that result feeds the exponent inside `JQuantMath.exp(f2 * Math.log(x2) - x2 - ...)`, so log slack propagates through and dominates.

Phase 2i.6 is a **micro-phase**, smaller in scope than even 2i.5: port `JQuantMath.log` and discharge the now-trivial NCCS tier flip. Two commits.

### Goals (in scope)

- **WI-1:** Port CORE-MATH correctly-rounded `log` to `org.jquantlib.math.transcendental.JQuantMath.log`. Bit-exact agreement with CORE-MATH `cr_log`. New class `LogKernel` reusing the existing `Dint64` infrastructure (Phase 2i.5 sub-layer 1.0).
- **WI-2:** Rewire `NonCentralCumulativeChiSquaredDistribution` to use `JQuantMath.log` (line 86) and flip its tier annotation TIGHT → EXACT.

### Non-goals (explicit)

- Codebase-wide Math.log → JQuantMath.log sweep — only NCCS gets rewired this phase.
- `JQuantMath.pow` — depends on log + exp, but not yet in scope; defer.
- `JQuantMath.tan/asin/acos/atan/atan2/sinh/cosh/tanh/expm1/log1p/cbrt/hypot` — not cited.
- Re-audit of all TIGHT/per-test pins for hidden log dependencies — would burn time without specific suspects (per the "tight scope" decision).
- BroadieKaya retry — still deferred (would also need pow eventually).
- Gaussian1D family — Phase 2j primary scope, after this phase.

### Outcome forecast

| Metric | Phase 2i.5 tip | Phase 2i.6 target | Phase 2i.6 ceiling |
|--------|----------------|--------------------|---------------------|
| Tests | 687/0/0/22 | 688/0/0/22 (+1 EXACT log test) | 688 |
| Scanner WIP | 0 | 0 | 0 |
| `JQuantMath` primitives | exp + cos + sin | exp + cos + sin + **log** | same |
| NCCS CDF tier | TIGHT (A19, Math.log floor) | **EXACT** (one-line test flip after log lands) | EXACT |

### Why log is bigger than exp but smaller than cos+sin

- **No Payne-Hanek argument reduction** (log doesn't have periodicity, just exponent extraction + mantissa polynomial). This is what made cos+sin's combined ~4000 LOC.
- **Uses Dint64 for accurate path** (similar to cos/sin), but tables are smaller — typically ~64 entries vs cos/sin's 256.
- **Hard-cases handling** likely an exception-table mechanism (like cos/sin), not a 51-entry DB (like exp).
- **Estimated LOC:** 600-1000 in `LogKernel.java`, including ~300 LOC of static-table initializers extracted via the Phase 2i.5 Python pattern.

### Why the "tight" scope is right-sized

The Phase 2i tier audit cited log 0×. Phase 2i.5 WI-2 surfaced one log-floored test (NCCS) by empirical accident. A blanket re-audit would scan ~30+ TIGHT pins looking for hidden log dependencies and likely find 0-2 — too low yield for a half-session. If log-floored tests exist outside NCCS, they will surface organically the next time someone pushes a tier promotion. No reason to front-load that discovery.

---

## 2. Approach Comparison

| Approach | Description | Verdict |
|----------|-------------|---------|
| **A. Standalone CORE-MATH `log` port via Dint64** *(chosen)* | Single `LogKernel.java` housing `cr_log`'s algorithm + tables. Reuses existing `Dint64` infrastructure. Public method `JQuantMath.log(double)`. | Standard Phase 2i.5 shape; smallest scope; fastest to land. |
| **B. Bundled `log + log1p + log2 + log10` family port** | Port all four log-family primitives (CORE-MATH provides `cr_log`, `cr_log1p`, `cr_log2`, `cr_log10`). | Rejected — Phase 2i tier audit cited none of `log1p`/`log2`/`log10`. Adds ~3× LOC for zero current-tier-promotion benefit; defer until specific demand surfaces. |
| **C. Defer log; do Gaussian1D first then come back** | Skip Phase 2i.6 entirely; jump to Gaussian1D as originally planned. | Rejected by user already — Path B chosen at the prior gate. Including for completeness. |

**Decision (P2I6-1):** Approach A.

**Decision (P2I6-2):** Algorithm source = canonical Inria CORE-MATH `src/binary64/log/log.c`. URL: `https://gitlab.inria.fr/core-math/core-math/-/raw/master/src/binary64/log/log.c`. Per Phase 2i.5 finding: gitlab.inria.fr serves reliably; mockingbirdnest GitHub mirror has C++/MSVC adaptations and is NOT what we want.

**Decision (P2I6-3):** Probe oracle = CORE-MATH `cr_log` directly via `#include "coremath/log.c"` in the probe — NOT `std::log` from `<cmath>`. Per Phase 2i A3 lesson and Phase 2i.5 P2I5-14 precedent.

**Decision (P2I6-4):** Probe shape mirrors Phase 2i `exp_probe.cpp` — IEEE-754 specials + dense + sparse + hard-cases (whatever CORE-MATH's `log.c` provides as exception table or DB). Java test follows the collect-all-failures pattern.

**Decision (P2I6-5):** Reuse Phase 2i.5 Python table-extractor pattern for any static tables in `log.c` — mechanical extraction of CORE-MATH hex floats into Java `Double.longBitsToDouble` initializers. Zero hand-transcription error opportunity.

**Decision (P2I6-6):** No new Dint64 operations needed unless `log.c` uses dint methods not currently in `Dint64`'s 9-op surface (`fromDouble, toDouble, copyFrom, addAssign, mulAssign, mul21Assign, subnormalize, cmpAbs, isZero`). If `log.c` calls additional ops (e.g. `inv_dint`, `sqr_dint`), those become a small `Dint64` extension commit that lands BEFORE the LogKernel commit. Implementer reports if so.

---

## 3. Worktree Topology & Layer Ordering

Two worktrees. WI-2 (NCCS flip) is a strict downstream consumer of WI-1 (log port) — no parallelism opportunity within Phase 2i.6.

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2i6-A` | `phase-2i6-A-log-port` | WI-1 `JQuantMath.log` port (1 commit, possibly +1 if Dint64 needs new ops per P2I6-6) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2i6-B` | `phase-2i6-B-nccs-exact-flip` | WI-2 NCCS CDF EXACT flip — dispatches AFTER WI-1 lands |

### Layer ordering

- **L0** — Pre-flight + 2 worktrees + progress doc (1 commit on main).
- **L1 (WI-1)** — `JQuantMath.log` port in worktree A. If `log.c` needs Dint64 operations beyond the existing 9, land those as a separate prep commit first (so the test surface is "Dint64 extension passes its own probe; LogKernel passes the log probe"). Most likely outcome: no new dint ops needed — 1 commit total.
- **L2 (WI-2)** — NCCS rewire + EXACT flip in worktree B (rebased on main post-WI-1). 1 commit.
- **L3** — Completion doc + tag `jquantlib-phase2i.6-complete` + memory + worktree teardown.

### Why two worktrees, not one

WI-1 commit message scope: `infra(math.transcendental)`. WI-2 commit message scope: `align(math.distributions)`. Different commit scopes cleanly separates the foundation work from the consumer-side flip. This matches Phase 2i.5's A/B/C pattern (with C dropped here since there's no third WI in the tight scope).

Could fold WI-2 into worktree A. The two-worktree split adds ~30 seconds of setup overhead for a meaningful audit-trail benefit — negligible cost for a clearer git log.

### Sub-task ordering inside WI-1

CORE-MATH `log.c` likely has the same fast-path / accurate-path / exception-table structure as cos/sin. Single commit transcribes:

1. (If needed per P2I6-6) Dint64 extension prep commit
2. Fetch canonical Inria source (`https://gitlab.inria.fr/core-math/core-math/-/raw/master/src/binary64/log/log.c` + any helpers it `#include`s)
3. Vendor under `migration-harness/cpp/probes/transcendental/coremath/log.c`
4. Probe `log_probe.cpp` with `#include "coremath/log.c"` and `cr_log(x)` calls
5. Probe input set: IEEE-754 specials (±0 → -∞, +∞, -∞ → NaN, NaN → NaN, negative → NaN), exact-result inputs (1.0 → +0, e → 1.0 — within rounding), powers of 2 (subset; log(2^k) = k·ln2), dense (0, 10] @ 0.01, sparse (0, max-double] at logarithmic spacing, hard-cases from log.c source
6. `LogKernel.java` transcription (~600-1000 LOC including static-table initializers extracted via Python pattern)
7. `JQuantMath.log(double)` facade method
8. `JQuantMathLogTest.java` using `MathTestSupport.bitsEqual` collect-all-failures pattern

### Sub-task ordering inside WI-2

After WI-1 lands on main:

1. Rebase worktree B on main tip
2. Add `import org.jquantlib.math.transcendental.JQuantMath;` to `NonCentralCumulativeChiSquaredDistribution.java`
3. Swap `Math.log(x2)` → `JQuantMath.log(x2)` at line 86 (Phase 2i.5 left this as the single remaining Math.* call — Math.exp was already swapped in WI-2)
4. Edit `NonCentralCumulativeChiSquaredDistributionTest.java` — change the TIGHT assertion to `MathTestSupport.assertBitsEqual(cpp, java)` (per Phase 2i.5 P2I5-15 plan)
5. Run NCCS test → expect EXACT to pass since:
   - 27-ULP residual was the empirical Math.log slack
   - With `JQuantMath.log` providing correctly-rounded log, the residual should collapse to bit-exact
6. If A19 fires unexpectedly (maybe `Math.sqrt` or Sankaran polynomial has secondary slack), back off to TIGHT-with-doc

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers

| Tier | Threshold | Phase 2i.6 usage |
|------|-----------|--------------------|
| **EXACT** | bit-identical via `MathTestSupport.assertBitsEqual` (NaN-canonicalised) | WI-1 unit test for `JQuantMath.log` against CORE-MATH `cr_log`. WI-2 NCCS CDF target (the headline outcome). |
| **TIGHT** | `abs 1e-14 + rel 1e-12` | WI-2 fallback if A19 fires (unexpected — Phase 2i.5 already swapped Math.exp; only Math.log remains as transcendental). |
| **LOOSE** | `abs 1e-8 + rel 1e-8` | Not expected. |
| **per-test exception** | numeric value + inline justification | Not expected. |

### Probes

#### WI-1: new probe under `migration-harness/cpp/probes/transcendental/`

**`log_probe.cpp`** — ~600 inputs:

- **IEEE-754 specials:**
  - `+0.0` → `-∞`, `-0.0` → `-∞` (per IEEE-754 — both signed zeros map to `-∞`)
  - `+∞` → `+∞`, `-∞` → NaN, NaN → NaN
  - `Double.MIN_VALUE` (denorm_min), `Double.min` (smallest normal), `Double.MAX_VALUE`
- **Negative inputs** (all → NaN): `-1.0`, `-π`, `-0.5`, `-Double.MIN_VALUE`
- **Exact-or-near-exact result inputs:**
  - `1.0` → `+0.0` (mathematically exact)
  - `e ≈ 2.718281828459045` → ~1.0
  - Powers of 2 sampled: `2^k` for `k = -1074, -1000, -100, -10, -1, 0, 1, 10, 100, 1000, 1023`
- **Dense `(0, 10]` at 0.01 spacing** (~1000 cases — covers most real-world inputs to log)
- **Sparse logarithmic `(0, 1e308]`:** `10^k` for `k = -300, -290, ..., 290, 300` (~60 cases; covers full normal-double exponent range)
- **Tiny-near-1 inputs** (where log(x) ≈ x - 1; argument-reduction stress): `1 + 2^-52`, `1 - 2^-52`, `1 + 2^-30`, `1 - 2^-30`
- **All hard-cases from CORE-MATH `log.c` source.** Read the source's hard-cases / exception table and add ALL entries explicitly. Phase 2i WI-1.1 had to retrofit this; Phase 2i.5 did it from day one — same pattern here.

**Probe oracle:** `#include "coremath/log.c"` and call `cr_log(x)` directly. NOT `std::log`.

JSON schema same as prior phases:
```json
{"name": "...", "inputs": {"x": ...}, "expected": {"y_bits": "0x..."}}
```

### Test discipline (carry-forward)

Inherited and binding:

1. **Probe-before-port** — Java test loads via `ReferenceReader.load("math/transcendental/log")`; never invent expected values inline.
2. **No backfilling green via tolerance** — if `JQuantMath.log(x)` differs from probe at any input, fix the algorithm. A2 fires if EXACT is unreachable.
3. **One sub-task = one commit.** WI-1 lands as ONE commit (with optional Dint64-extension prep commit only if `log.c` calls dint ops not in current Dint64 surface).
4. **Bit-pattern comparison** for transcendental EXACT — `MathTestSupport.assertBitsEqual` and the non-throwing `bitsEqual` variant for collect-all-failures pattern.
5. **Hard-cases coverage from day one** — probe must include every hard-cases entry from `log.c` source, not rely on dense grid to randomly hit them.
6. **Probe oracle = CORE-MATH `cr_log`** directly, not platform `std::log` (Phase 2i A3 lesson).

### Test count expectations

| Event | Δ tests | Notes |
|-------|---------|-------|
| WI-1 log port | +1 EXACT | one parameterized test iterating ~600 cases via collect-all-failures |
| WI-2 NCCS EXACT flip | 0 | tier change on existing NonCentralCumulativeChiSquaredDistributionTest assertion |

**Aggregate target:** `687 → 688`. Scanner WIP unchanged at 0.

### Risk analysis (residual)

**Risk: WI-2 EXACT doesn't pass after both Math.exp and Math.log are correctly-rounded.**

Possible secondary slack sources, in decreasing likelihood:
- `gammaFunction_.logValue(f2 + 1.0)` — uses Java's `Math.log` internally (Lanczos approximation typically). NCCS line 86 has `f2 * Math.log(x2) - x2 - gammaFunction_.logValue(f2 + 1.0)`. The gamma function's accumulated polynomial rounding could survive both transcendental swaps.
- `Math.sqrt` — JVM matches CORE-MATH/correctly-rounded; not a source.
- `Math.abs` / `Math.PI` / arithmetic — not transcendental, not slack.
- Sankaran polynomial coefficient table precision — pure arithmetic, but Sankaran approximation has its own intrinsic error vs the true non-central χ² CDF. CORE-MATH's `cr_log` makes the *implementation* correctly-rounded; it doesn't fix the *approximation's* mathematical error.

**If A19 fires:** the residual most likely points at `gammaFunction_.logValue` as the next port candidate (Lanczos coefficient + polynomial → would need a `JQuantMath.lgamma` port in a future phase). Document and stay TIGHT.

**Mitigation:** if WI-2 EXACT fails, the test message will identify which inputs differ. We then have a decision point:
- Small residual (1-2 ULPs): possibly `gammaFunction_.logValue` accumulated error — defer with A19 doc
- Large residual (10+ ULPs): something structural we missed — pause and investigate

---

## 5. Pause Triggers, Decision Log & Exit Criteria

### Pause triggers

| ID | Condition | Phase 2i.6 interpretation | Action |
|----|-----------|----------------------------|--------|
| A2 | Tolerance looser than `1e-8` needed | If WI-1 log EXACT fails for any probe input | **Pause** — port wrong, fix algorithm |
| A3 | Cross-validation suggests reference itself wrong | If `cr_log` disagrees with mpmath at high precision (extremely unlikely) | **Pause** — surface to user |
| A4 | New class outside planned scope | Disabled for `org.jquantlib.math.transcendental` (planned: `LogKernel.java` + possible Dint64 extension); armed for *other* unplanned packages | n/a expected |
| A6 | End-of-layer ack | **Disabled** per memory | Run end-to-end |
| A8 | Test suite red unrelated | If WI-1 or WI-2 breaks an unrelated test | **Pause** — investigate |
| A9 | Worktree merge conflict | A and B run sequentially (B rebases on A) — conflict only if WI-2 touches files WI-1 also touched (unlikely; A=transcendental package, B=distributions package) | **Coordinate** if surfaces |
| A13 | JVM transcendental ULP slack on a non-cos/sin/exp/log primitive | Re-arms for any other Math.* method we encounter | n/a expected this phase |
| A15 | Previously-hidden bug surface | If log port exposes some other latent issue | **Pause** — bundle as `align(...)` commit |
| A16 | Missing dependency outside planned scope | E.g. WI-1 needs Dint64 ops not currently present | If isolated to Dint64 extension: prep commit (within scope per P2I6-6); otherwise pause |
| A17 | >2 unplanned `align(...)` commits | Cumulative across A/B | **Pause** — re-evaluate scope |
| A18 | NaN payload divergence | Same mitigation as Phase 2i (canonicalisation in `MathTestSupport`) | n/a expected |
| **A19** | NCCS EXACT flip fails after JQuantMath.log swap-in | Most likely residual source: `gammaFunction_.logValue` Lanczos approximation | **Document inline, back off to TIGHT, continue** to L3 |

A1/A10/A11/A12/A14 inactive.

### Decision log

| # | Decision | Rationale |
|---|----------|-----------|
| **P2I6-1** | Standalone CORE-MATH `log` port (Approach A) | Smallest scope; no log-family bundle needed since audit cited only log |
| **P2I6-2** | Algorithm source = CORE-MATH `src/binary64/log/log.c` (canonical Inria) | BSD/MIT, correctly-rounded by design. Same family Phase 2i used for exp; Phase 2i.5 used for sin/cos |
| **P2I6-3** | Probe oracle = CORE-MATH `cr_log` directly | Phase 2i A3 lesson: platform `std::log` not always correctly-rounded |
| **P2I6-4** | Probe shape mirrors prior phases | Consistency; reuses ReferenceReader + MathTestSupport infrastructure |
| **P2I6-5** | Reuse Python table-extractor pattern | Phase 2i.5 P2I5-12 success: zero hand-transcription error opportunity |
| **P2I6-6** | If `log.c` needs Dint64 ops not in current 9-op surface, land as prep commit BEFORE `LogKernel.java` | Audit-trail benefit: "Dint64 extension passed its own probe; LogKernel passed log probe." Avoids bundling unrelated changes. |
| **P2I6-7** | Tight scope — no Math.log audit re-sweep | Audit cited log 0× explicitly; blanket re-audit unlikely to find more than 0-2 candidates; let log-floored tests surface organically |
| **P2I6-8** | If WI-2 NCCS EXACT fails, A19 → TIGHT-with-doc citing gamma function | Lanczos approximation in `gammaFunction_.logValue` is the likely secondary slack source — flag as future-phase candidate, do not pursue this phase |
| **P2I6-9** | 2 worktrees: A=log port, B=NCCS flip (sequential) | Different commit scopes (`infra` vs `align`) cleanly separate foundation from consumer |
| **P2I6-10** | Direct-to-main, signed `-s`, no `Co-authored-by` | Standing project rule |

### Exit criteria

Phase 2i.6 is complete when **all** hold:

1. **WI-1**: 1 EXACT-tier parameterized test (`JQuantMathLogTest`) passes against CORE-MATH `cr_log` probe oracle. All ~600 probe inputs incl. hard-cases bit-exact.
2. **WI-2**: NCCS CDF code uses `JQuantMath.log` (line 86 swap). Tier outcome:
   - **EXACT** (success path — clean discharge of Phase 2i.5 A19); OR
   - **TIGHT** (with inline A19 documentation citing `gammaFunction_.logValue` or other identified residual source).
3. **Test suite**: `mvn -pl jquantlib test` reports `0 failures, 0 errors`. Test count `688/0/0/22`.
4. **Scanner**: WIP unchanged at 0.
5. **Documentation**: `docs/migration/phase2i.6-completion.md` written following Phase 2i.5 shape (test count tracking, per-WI summary, A-trigger fire history, decision log, Phase 2j seed list refresh).
6. **Tag**: `jquantlib-phase2i.6-complete` on resulting main tip.
7. **Memory**: `MEMORY.md` and `project_jquantlib_migration.md` updated to reflect Phase 2i.6 completion.
8. **Worktree teardown**: 2 worktrees removed, branches deleted local + remote.

### Out-of-scope (explicit)

- BroadieKaya asset-leg retry — still deferred (would also need `JQuantMath.pow`).
- `JQuantMath.pow`, `tan`, `asin`, `acos`, `atan`, `atan2`, `sinh`, `cosh`, `tanh`, `expm1`, `log1p`, `cbrt`, `hypot` — Phase 2j+ candidates.
- `JQuantMath.lgamma` / `JQuantMath.gamma` — only relevant if A19 fires for NCCS and points at `gammaFunction_.logValue`. Even then, deferred to future phase.
- Math.log audit re-sweep — explicitly skipped per tight scope.
- Codebase-wide `Math.log → JQuantMath.log` swap — only NCCS line 86 gets rewired.
- `Math.sqrt`, `Math.fma`, basic arithmetic — JVM matches CORE-MATH.
- Gaussian1D family port — Phase 2j primary scope, after this phase.

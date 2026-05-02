# Phase 2j Design — Gaussian1D Family Port

**Status:** approved 2026-05-02
**Predecessor:** `jquantlib-phase2i.6-complete` @ `44be66c` (tests `688/0/0/22`, scanner WIP=0)
**Working directory:** `/Users/josemoya/eclipse-workspace/jquantlib`
**C++ source-of-truth:** QuantLib v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

---

## 1. Context & Motivation

The Gaussian1D family is QuantLib's **non-standard swaption pricing infrastructure** — Hull-White's 1-factor short-rate generalized to support arbitrary volatility term structures, smile-aware swaption / cap-floor pricing, and exotic instruments (callable bonds, Bermudan swaptions on float-float swaps, etc.). It's been on the seed list since Phase 2h and was originally planned as Phase 2j before the three transcendental prep phases (2i, 2i.5, 2i.6) intervened.

With correctly-rounded `JQuantMath.{exp, cos, sin, log}` now available, Phase 2j ports the full Gaussian1D family from QuantLib v1.42.1 — the largest port in the project so far (~6119 LOC C++; estimated 7000-9000 LOC Java).

### Goals (in scope)

Port the **complete Gaussian1D family**:

- **Model layer:**
  - `Gaussian1dModel` (abstract base, ~547 LOC C++)
  - `Gsr` (Gaussian Short Rate concrete model, ~425 LOC)
  - `MarkovFunctional` (sophisticated calibration model, ~1710 LOC — biggest single piece)
- **Process layer:**
  - `GsrProcessCore` (~493 LOC) and `GsrProcess` (~199 LOC) — stochastic process for Gsr
- **Volatility-structure layer:**
  - `Gaussian1dSmileSection` (~185 LOC)
  - `Gaussian1dSwaptionVolatility` (~163 LOC)
- **Engine layer:**
  - `Gaussian1dSwaptionEngine` (standard, ~445 LOC)
  - `Gaussian1dJamshidianSwaptionEngine` (~182 LOC)
  - `Gaussian1dNonstandardSwaptionEngine` (~636 LOC)
  - `Gaussian1dFloatFloatSwaptionEngine` (~848 LOC, largest engine)
  - `Gaussian1dCapFloorEngine` (~286 LOC)

11 file pairs, ~6119 LOC C++. Each port carries cross-validation probes, EXACT/TIGHT/LOOSE tier discipline.

### Non-goals (explicit)

- `JQuantMath.lgamma` / `JQuantMath.pow` — out of scope per Phase 2j brainstorming gate.
- BroadieKaya retry — still deferred (needs pow).
- NCCS EXACT — gammaFunction floor remains; not addressable this phase.
- Douglas ADI / FdmAffineModelTermStructure — Phase 2j+ candidate, not in this scope.
- Other Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, FdBlackScholesVanilla) — separate future phase.
- `U128.java` shared util extraction — defer to Phase 2j.5 cleanup pass.

### Outcome forecast

| Metric | Phase 2i.6 tip | Phase 2j target | Phase 2j ceiling |
|--------|----------------|-----------------|--------------------|
| Tests | 688/0/0/22 | ~700-710/0/0/22 (per-engine cross-validation tests + model probes) | ~715 |
| Scanner WIP | 0 | 0 | 0 |
| New Java production LOC | — | ~7000-9000 | ~10000 |
| New Java packages | — | `org.jquantlib.{models.shortrate.onefactormodels.gaussian1d, processes.gsr, pricingengines.swaption.gaussian1d, pricingengines.capfloor.gaussian1d, termstructures.volatility.gaussian1d}` (5 new subpackages) | same |
| Tier outcomes per engine | n/a | TIGHT for most; LOOSE for tests with deep numeric integration | mixed (engine-specific) |

### Why this is the largest phase yet

Comparison to prior major ports:

| Phase | LOC | Files | Sub-layered? |
|-------|-----|-------|--------------|
| 2h Fdm framework | ~3826 | ~30 | Yes (5 sub-layers) |
| 2i.5 cos/sin via Dint64 | ~2225 (Dint64 + SinCosKernel) | 4 | Yes (1.0 + 1.1) |
| **2j Gaussian1D family** | **~6119 → ~7000-9000 Java** | **22 (11 file pairs)** | **Yes (4 dep layers, see §3)** |

Phase 2j needs explicit dependency-aware sub-layering and parallelism. Section 3 covers topology in detail.

---

## 2. Approach Comparison

| Approach | Description | Verdict |
|----------|-------------|---------|
| **A. Layer-by-dependency: sub-layered model in one worktree, engines parallelized after** *(chosen)* | WI-1 sub-layers in worktree A: `Gaussian1dModel` → `GsrProcessCore`/`GsrProcess` → `Gsr` → `MarkovFunctional` → `Gaussian1dSmileSection` + `Gaussian1dSwaptionVolatility` (5 sub-commits, sequential). Then WI-2/WI-3/WI-4 dispatch engines in parallel worktrees. Mirrors Phase 2h's proven pattern. | Clean dependency ordering; maximal parallelism without coupling risk. |
| **B. Two worktrees from start** (model-side + engine-side, parallel) | Engines depend on models — would have to dispatch engines against stub models, then integrate. | Rejected — coupling risk; engines need real model APIs to validate against C++ probes. Stub-then-integrate doubles work and breaks probe-before-port discipline. |
| **C. Per-file sequential commits in single worktree** | One commit per file pair (11 commits sequential). No sub-layering; no parallelism. | Rejected — wall-time multiplier with no quality benefit. Phase 2h's 5-sub-layer pattern proved sub-layering scales better than per-file. |

**Decision (P2J-1):** Approach A.

**Decision (P2J-2):** Algorithm source = QuantLib v1.42.1 C++ at pinned SHA `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`. Unlike Phase 2i/2i.5/2i.6 (which used CORE-MATH for transcendentals), Phase 2j is a straight port of QuantLib's own Java-translatable code — the C++ files under `migration-harness/cpp/quantlib/ql/` are the source-of-truth.

**Decision (P2J-3):** Cross-validation probe per concrete model and per engine — not per file. Approximate probe count:
- 1 probe for `Gaussian1dModel` base behaviors (forward measure conversions, etc.)
- 1 probe for `Gsr` (model parameters, calibration)
- 1 probe for `MarkovFunctional` (calibration + forward swap rate fits)
- 1 probe for `GsrProcess` (drift, diffusion, expectation/variance)
- 1 probe for `Gaussian1dSmileSection` + `SwaptionVolatility` (vol surface eval)
- 1 probe per swaption engine (5 total) + cap-floor engine

≈ 11 new probes total. Each emits a deterministic numerical fingerprint that the Java test loads via `ReferenceReader`.

**Decision (P2J-4):** Tolerance tier per artifact, in decreasing strictness:
- Model getter / arithmetic state: **EXACT** (no integration; bit-exact achievable since pure arithmetic + correctly-rounded transcendentals)
- `GsrProcessCore` drift/diffusion at fixed (t, x): **TIGHT** (single transcendental + arithmetic; minor accumulation through volatility integration)
- Concrete model NPV / forward swap rate: **TIGHT** (calibration loops accumulate)
- Swaption engine NPV: **TIGHT** for standard, possibly **LOOSE** for FloatFloat (deepest integration)
- MarkovFunctional calibrated parameters: **TIGHT** at default; per-test loose if calibration sensitivity demands

**Decision (P2J-5):** No new transcendental dependency surfaces during this port. If Gaussian1D code calls `Math.exp`/`Math.log`/`Math.cos`/`Math.sin`, swap to `JQuantMath.*` from day one (avoid the Phase 2i.5/2i.6 pattern of "port first, swap later"). If Gaussian1D calls `Math.pow` (1 site in GsrProcessCore confirmed), leave as `Math.pow` per Phase 2j-pre B3 decision; flag as Phase 2j followup candidate if A19 fires.

**Decision (P2J-6):** Direct-to-main commits, signed `-s`, no `Co-authored-by`. Standing project rule.

---

## 3. Worktree Topology & Layer Ordering

Four worktrees. WI-1 (model layer) sub-layered sequentially in worktree A; WI-2/WI-3/WI-4 (engines) parallel after WI-1 lands.

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2j-A` | `phase-2j-A-gaussian1d-model` | WI-1 model layer (5 sub-commits, sequential) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2j-B` | `phase-2j-B-standard-engines` | WI-2 standard engines (Gaussian1dSwaptionEngine + Gaussian1dCapFloorEngine) — dispatches AFTER WI-1 lands |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2j-C` | `phase-2j-C-niche-swaption-engines` | WI-3 niche swaption engines (Jamshidian + Nonstandard + FloatFloat) — dispatches AFTER WI-1 lands; parallel with B |
| D | `/Users/josemoya/eclipse-workspace/jquantlib-2j-D` | `phase-2j-D-markov-functional` | WI-4 MarkovFunctional concrete model — dispatches AFTER WI-1 sub-layer 1.1 lands; parallel with WI-2/WI-3 |

### Layer ordering

- **L0** — Pre-flight + 4 worktrees + progress doc (1 commit on main).
- **L1 (WI-1)** — Sub-layered in worktree A:
  - **1.1:** `Gaussian1dModel` abstract base (~547 LOC C++ → ~700-900 LOC Java) + 1 probe + base test
  - **1.2:** `GsrProcessCore` + `GsrProcess` (~692 LOC C++ → ~900-1100 Java) + 1 probe
  - **1.3:** `Gsr` concrete model (~425 LOC C++ → ~550-700 Java) + 1 probe
  - **1.4:** `Gaussian1dSmileSection` + `Gaussian1dSwaptionVolatility` (~348 LOC C++ → ~450-600 Java) + 1 probe
  - **(MarkovFunctional handled in WI-4, parallel after sub-layer 1.1 lands — see below)**
- **L2 — parallel after WI-1 sub-layer 1.4 lands:**
  - **WI-2 (worktree B):** Gaussian1dSwaptionEngine + Gaussian1dCapFloorEngine (~731 LOC C++ → ~950-1200 Java; 2 commits)
  - **WI-3 (worktree C):** Jamshidian + Nonstandard + FloatFloat swaption engines (~1666 LOC C++ → ~2100-2700 Java; 3 commits)
- **L3 — parallel after WI-1 sub-layer 1.1 lands** (concurrent with L1's later sub-layers and L2):
  - **WI-4 (worktree D):** MarkovFunctional (~1710 LOC C++ → ~2200-2700 Java; 1 commit, possibly +1 if calibration probe needs separate dispatch)
- **L4** — Completion doc + tag `jquantlib-phase2j-complete` + memory + worktree teardown.

### Critical-path analysis

```
                 ┌─ WI-1.1 base ──┬─ WI-1.2 process ─┬─ WI-1.3 Gsr ─┬─ WI-1.4 vol struct ─┬─ WI-2 std engines
L0 ─ progress ───┤                │                                                       └─ WI-3 niche engines
                 └─ (after 1.1) ──┴─ WI-4 MarkovFunctional (parallel) ────────────────────────────────────┘
```

**Critical path:** L0 → WI-1.1 → WI-1.2 → WI-1.3 → WI-1.4 → max(WI-2, WI-3) → L4. WI-4 (MarkovFunctional) runs in parallel with WI-1.2/1.3/1.4/WI-2/WI-3 — saves ~half the Markov port wall time.

### Why four worktrees, not three or five

- **WI-2 + WI-3 split:** standard engines (B-1 SwaptionEngine, B-2 CapFloorEngine) have very different test surfaces from the niche engines (Jamshidian/Nonstandard/FloatFloat). Splitting keeps subagent context tight and review focused.
- **WI-4 separate from WI-1:** MarkovFunctional is 1710 LOC alone and uses different calibration plumbing than `Gsr`. Folding it into WI-1's sequential chain wastes ~1.5h of wall time. Letting it run parallel from WI-1.1 onwards is a clean ~30% wall-time saving.
- **5+ worktrees:** would require splitting WI-3 further (one per niche engine). Engine code shares enough idiom (Gaussian1dEngine base patterns) that one subagent doing all three efficiently caches context. Diminishing returns past 4.

### Sub-task dependency notes

- WI-1.2 (GsrProcessCore) needs WI-1.1's `Gaussian1dModel` base interface (some methods are virtual on Gaussian1dModel that GsrProcessCore implements indirectly).
- WI-1.3 (Gsr) needs both 1.1 (extends Gaussian1dModel) and 1.2 (uses GsrProcess).
- WI-1.4 (vol structures) needs only 1.1 (smile section keys off Gaussian1dModel API).
- WI-4 (MarkovFunctional) needs only 1.1 — it implements its own forward measure machinery, doesn't depend on Gsr or vol structures.
- Engines (WI-2 + WI-3) depend on the entire WI-1 layer.

### Cross-worktree rebase discipline

Same pattern as Phase 2h/2i.5:
- Worktree A: lands sub-commits sequentially, fast-forwards to main after each.
- Worktrees B/C/D: rebase on main before dispatching their first task; if a later WI-1 sub-commit lands while B/C/D are mid-flight, controller orchestrates a rebase between commits.
- A9 (worktree merge conflict): expected zero — disjoint package trees (model/process/engine/vol-structure all live in different subpackages).

---

## 4. Tolerance, Probes & Test Discipline

### Tolerance tiers

| Tier | Threshold | Phase 2j usage |
|------|-----------|-----------------|
| **EXACT** | bit-identical via `MathTestSupport.assertBitsEqual` | Pure-arithmetic model getters / state accessors (e.g. `Gsr` parameter readback, `Gaussian1dModel` time-grid arithmetic). Achievable since all transcendentals on the path are correctly-rounded `JQuantMath.*`. |
| **TIGHT** | `abs 1e-14 + rel 1e-12` | Model integrals/expectations (single-step transcendental + arithmetic accumulation); standard swaption / cap-floor engine NPVs. Default tier for most Phase 2j tests. |
| **LOOSE** | `abs 1e-8 + rel 1e-8` | FloatFloat swaption engine (deepest integration), MarkovFunctional calibration outputs (iterative root-finding with sensitivity multipliers). Justified inline. |
| **per-test exception** | numeric value + inline justification | MarkovFunctional smile-fit residuals if calibration sensitivity demands a specific bound. |

### Probe specifications (~11 new probes)

All under `migration-harness/cpp/probes/` — extends existing structure.

#### Model layer

1. **`gaussian1d_model_probe.cpp`** (`models/shortrate/onefactormodels/`)
   - Forward-measure conversion fingerprints across a (t, x, T) grid (~50 cases)
   - Time-discretization integration on a fixed mesh (~20 cases)
   - Standard swap rate at fixed (t, x, expiry, tenor) (~30 cases)

2. **`gsr_probe.cpp`** (`models/shortrate/onefactormodels/`)
   - Gsr parameter readback (volatility, reversion stepwise) — EXACT tier
   - Forward variance V(t,T,T') across a 4D grid (~80 cases) — TIGHT
   - Discount bond price P(t,T,x) (~50 cases) — TIGHT
   - Numeraire rebasing identity check (~20 cases) — EXACT

3. **`markov_functional_probe.cpp`** (`models/shortrate/onefactormodels/`)
   - Calibrated parameter table (post-fit sigma stepwise) — TIGHT (calibration root-finding)
   - Forward swap rate fits at calibration grid points (~40 cases) — TIGHT
   - Annuity numeraire conversion identities (~30 cases) — TIGHT

#### Process layer

4. **`gsr_process_probe.cpp`** (`processes/`)
   - Drift μ(t, x) on a (t, x) grid (~80 cases) — TIGHT
   - Diffusion σ(t, x) on a (t, x) grid (~80 cases) — TIGHT
   - Expectation E[X(T) | X(t)] at varying (t, T, x) (~50 cases) — TIGHT
   - Variance Var[X(T) | X(t)] (~50 cases) — TIGHT

#### Volatility-structure layer

5. **`gaussian1d_vol_probe.cpp`** (`termstructures/volatility/`)
   - SmileSection variance/density at strike grid (~100 cases) — TIGHT
   - SwaptionVolatility surface eval at (expiry, tenor, strike) grid (~150 cases) — TIGHT

#### Engine layer (5 separate probes)

6. **`gaussian1d_swaption_engine_probe.cpp`** (`pricingengines/swaption/`)
   - Standard swaption NPV across (expiry, tenor, strike, integration-density) grid (~80 cases) — TIGHT
7. **`gaussian1d_jamshidian_swaption_engine_probe.cpp`** — Jamshidian decomposition results (~50 cases) — TIGHT
8. **`gaussian1d_nonstandard_swaption_engine_probe.cpp`** — non-standard swaption NPV with custom amortization (~60 cases) — TIGHT
9. **`gaussian1d_float_float_swaption_engine_probe.cpp`** — FloatFloat NPV (deepest integration, ~40 cases) — TIGHT or LOOSE per outcome
10. **`gaussian1d_capfloor_engine_probe.cpp`** (`pricingengines/capfloor/`) — cap/floor NPV across grid (~60 cases) — TIGHT

#### MarkovFunctional engine compatibility (if engines exercise it)

11. **`markov_functional_engine_probe.cpp`** (only if WI-2/WI-3 engine tests need MF-specific calibration outputs distinct from probe 3) — typically not needed; engines should be model-agnostic at the API level.

### Test discipline (carry-forward)

Inherited and binding:

1. **Probe-before-port** — Java tests load via `ReferenceReader.load(...)`; never invent expected values inline. Every Java test for a Phase 2j artifact gets its expected values from the corresponding probe JSON.
2. **No backfilling green via tolerance** — if Java differs from probe at any input, fix the algorithm (or fix the probe input if the Java port revealed a bug in the C++ test pattern; rare). A2 fires if EXACT can't be reached on a pure-arithmetic path.
3. **One sub-task = one commit.** Each WI-1 sub-layer + each engine + MarkovFunctional = one commit each (with optional align-fix prep commits for unrelated bugs surfaced during port; cap at 2 unplanned per A17).
4. **Inline justification** required for any LOOSE / per-test exception with the source-of-slack named.
5. **Use `JQuantMath.*` from day one** — never write `Math.exp` etc. in new Phase 2j code. The 4 correctly-rounded primitives (exp/log/sin/cos) are available and should be used at first transcription pass, not retrofitted.
6. **Per-engine compile + test gate** — each engine commit must pass its own probe AND not regress earlier ones. The WI-2/WI-3 worktrees rebase on WI-1's tip and run `mvn test` before pushing.

### Test count expectations

| Event | Δ tests | Notes |
|-------|---------|-------|
| WI-1.1 Gaussian1dModel base | +1 model probe test | iterates ~50 forward-measure + ~20 time-grid + ~30 swap-rate cases |
| WI-1.2 GsrProcess(Core) | +1 probe test | iterates ~260 drift/diffusion/E/Var cases |
| WI-1.3 Gsr concrete model | +1 probe test | ~80 + ~50 + ~20 cases |
| WI-1.4 vol structures | +1 probe test (combined SmileSection + SwaptionVolatility) | ~250 cases |
| WI-2 standard engines | +2 tests (one per engine) | ~80 + ~60 cases |
| WI-3 niche engines | +3 tests (one per engine) | ~50 + ~60 + ~40 cases |
| WI-4 MarkovFunctional | +1 probe test | ~40 + ~30 + ~30 cases |

**Aggregate target:** `688 → 698` (+10 tests). Ceiling `~715` if MarkovFunctional needs split tests for calibration-vs-application or if engines need compatibility tests against multiple model types.

### Risk analysis

**Risk 1: MarkovFunctional calibration non-determinism.** The C++ MarkovFunctional uses iterative calibration (Brent + custom search). If the Java port's iteration order differs, calibrated parameters drift. **Mitigation:** probe captures the calibrated parameter vector to fingerprint level — Java port matches C++ iteration order strictly (no early-exit optimizations during port).

**Risk 2: FloatFloat engine has the most numerical depth.** Combines forward-measure conversion + numeraire rebasing + amortization integration + numerical integration over the forward distribution. Likely lands at TIGHT but might require LOOSE. **Mitigation:** probe includes both standard and stress-case (long-tenor + amortizing) inputs; expected outcome documented up front.

**Risk 3: Math.pow at GsrProcessCore (per Phase 2j-pre B3).** May trip a TIGHT tier into LOOSE for tests that exercise the GsrProcess deep-time path. **Mitigation:** if A19 fires, document the Math.pow site as a Phase 2j followup mini-phase candidate; back off to LOOSE-with-doc, don't block the phase.

**Risk 4: Existing Java HullWhite/Vasicek interface drift.** Gaussian1dModel inherits from QuantLib's TermStructureConsistentModel + similar machinery. Java side may have aged differently than C++; align-fix bonus commits expected during WI-1.1. **Mitigation:** A17 caps at 2 unplanned aligns before pause.

---

## 5. Pause Triggers, Decision Log & Exit Criteria

### Pause triggers

| ID | Condition | Phase 2j interpretation | Action |
|----|-----------|--------------------------|--------|
| A2 | Tolerance looser than `1e-8` needed | If a test that should be EXACT or TIGHT falls below LOOSE (i.e., requires per-test 1e-7 or worse) | **Pause** — likely a port bug, not legitimate slack |
| A3 | Cross-validation suggests reference itself wrong | If a C++ probe value disagrees with mathematical expectation (extremely unlikely; QuantLib v1.42.1 is well-tested) | **Pause** — surface to user |
| A4 | Stub needs new class outside scoped packages | Phase 2j creates 5 planned new subpackages (`models.shortrate.onefactormodels.gaussian1d`, `processes.gsr`, `pricingengines.swaption.gaussian1d`, `pricingengines.capfloor.gaussian1d`, `termstructures.volatility.gaussian1d`); armed for any *other* unplanned package | **Pause** — decide scope-add vs phase-defer |
| A6 | End-of-layer ack | **Disabled** per memory `feedback_phase2a_no_a6.md` | Run end-to-end |
| A8 | Test suite red unrelated to current work | If a Phase 2j commit breaks an existing test outside the Gaussian1D family | **Pause** — investigate before continuing |
| A9 | Worktree merge conflict | A/B/C/D run with disjoint package trees; conflict only if multiple worktrees touch shared infrastructure (e.g. CalibratedModel base, IborIndex helper) | **Coordinate** — controller resolves via rebase |
| A13 | JVM transcendental ULP slack on a non-{exp,log,sin,cos} primitive | Re-arms for `Math.pow` (1 site at GsrProcessCore — known) and any other Math.* surfaced during port | **Document with site, defer to Phase 2j followup if blocking** |
| A15 | Previously-hidden bug surface | If port exposes latent bugs in existing JQuantLib (e.g. Phase 2h surfaced BicubicSpline Address-mapping bug) | **Pause** — bundle fix as `align(...)` commit, then continue |
| A16 | Missing dependency outside planned scope | If Gaussian1D needs a primitive (e.g. specific calibration helper) not currently in JQuantLib | **Pause** — decide scope-add vs phase-defer |
| A17 | >2 unplanned `align(...)` commits during port | Cumulative across A/B/C/D | **Pause** — re-evaluate scope |
| A18 | NaN payload divergence | Same mitigation as Phase 2i (canonicalisation in `MathTestSupport`) | n/a expected |
| A19 | Tier promotion fails after correct swap-in | Specifically: if `Math.pow` at GsrProcessCore is the dominant slack source for a test that should be TIGHT | **Document inline, back off one tier, continue;** flag pow port as Phase 2j followup |
| **A20** *(new)* | MarkovFunctional calibration produces non-deterministic outputs across runs | Iterative root-finding sensitivity; if Java calibration produces different parameters than C++ probe due to iteration-order divergence | **Pause** — verify iteration order matches C++ exactly; if root cause is solver state machine difference, may need an `align(math.solvers1D)` align-fix commit |
| **A21** *(new)* | Phase 2j wall-time projection exceeds 3 sessions | Mid-phase progress audit signals that the planned Full-scope is taking longer than expected | **Pause** — present user with scope-trim options (defer MarkovFunctional, defer 1-2 niche engines, etc.) |

A1/A10/A11/A12/A14 inactive.

### Decision log

| # | Decision | Rationale |
|---|----------|-----------|
| **P2J-1** | Layer-by-dependency sub-layered approach (Approach A) | Maximal parallelism without coupling risk; mirrors Phase 2h's proven 5-sub-layer pattern |
| **P2J-2** | Algorithm source = QuantLib v1.42.1 C++ at pinned SHA `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` | Direct port of QuantLib's own code (not transcendental like 2i/2i.5/2i.6 which used CORE-MATH) |
| **P2J-3** | ~11 cross-validation probes, one per concrete artifact (model + process + each engine + vol-structure pair) | Probe-before-port discipline; per-artifact granularity gives clean tier outcomes |
| **P2J-4** | Tier per artifact (EXACT for accessors; TIGHT for integrals/NPVs; LOOSE only as documented exception) | Empirical-leverage discipline from Phase 2h-2i.6; specific outcomes documented in completion |
| **P2J-5** | Use `JQuantMath.{exp,log,sin,cos}` from day one in new Phase 2j code; leave `Math.pow` at the 1 GsrProcessCore site | Phase 2j-pre B3 decision: empirical leverage too low to bundle pow port; A19 escape valve if floor surfaces |
| **P2J-6** | Direct-to-main commits, signed `-s`, no `Co-authored-by` | Standing project rule |
| **P2J-7** | 4 worktrees: A=model+vol (sequential), B=standard engines, C=niche engines, D=MarkovFunctional | A/D parallel from sub-layer 1.1; B/C parallel after 1.4 |
| **P2J-8** | WI-4 (MarkovFunctional) dispatches as a single commit despite 1710 LOC C++ | Calibration logic is internally cohesive; splitting risks intermediate-state probe gaps. Phase 2h's WI-1.4 (similar size) dispatched as one commit successfully. |
| **P2J-9** | Phase 2j followup mini-phase reserved for: pow port (if A19 from GsrProcessCore), U128 shared util extraction, MarkovFunctional calibration optimization | Carve outs documented in completion doc seed list |
| **P2J-10** | If A21 fires, scope-trim order is: MarkovFunctional first, then FloatFloat engine, then Nonstandard engine, then Jamshidian engine. Standard SwaptionEngine + CapFloorEngine are the floor. | Reverse priority of empirical demand: MF and FloatFloat have the most niche use-cases; Standard SwaptionEngine + CapFloorEngine are the most-used. |

### Exit criteria

Phase 2j is complete when **all** hold:

1. **WI-1**: All 5 model-layer sub-commits land:
   - `Gaussian1dModel` base + probe test passing
   - `GsrProcessCore` + `GsrProcess` + probe test passing
   - `Gsr` + probe test passing
   - `Gaussian1dSmileSection` + `Gaussian1dSwaptionVolatility` + probe test passing
   - (MarkovFunctional handled in WI-4)
2. **WI-2**: Both standard engines (`Gaussian1dSwaptionEngine`, `Gaussian1dCapFloorEngine`) land + probe tests passing.
3. **WI-3**: All 3 niche swaption engines (`Jamshidian`, `Nonstandard`, `FloatFloat`) land + probe tests passing.
4. **WI-4**: `MarkovFunctional` lands + probe test passing (or A20-documented if calibration non-determinism surfaces).
5. **Test suite**: `mvn -pl jquantlib test` reports `0 failures, 0 errors`. Test count `688 → 698` (target) or `~715` (ceiling); all new tests at TIGHT or stricter unless A19 fired with documentation.
6. **Scanner**: WIP unchanged at 0.
7. **Documentation**: `docs/migration/phase2j-completion.md` written following Phase 2h shape (test count tracking, per-WI summary, A-trigger fire history, decision log additions, Phase 2k seed list refresh).
8. **Tag**: `jquantlib-phase2j-complete` on resulting main tip.
9. **Memory**: `MEMORY.md` and `project_jquantlib_migration.md` updated.
10. **Worktree teardown**: 4 worktrees removed, branches deleted local + remote.

### Out-of-scope (explicit, deferred)

- `JQuantMath.lgamma` — no correctly-rounded source available.
- `JQuantMath.pow` — empirical leverage too low (1 GsrProcessCore site); Phase 2j followup if A19 fires.
- `U128.java` shared util extraction (LogKernel + Dint64 duplicates) — Phase 2j followup.
- BroadieKaya retry — needs pow.
- NCCS EXACT — needs lgamma.
- Douglas ADI / FdmAffineModelTermStructure floors — separate phase.
- Other Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, FdBlackScholesVanilla) — separate phase.
- Phase 2h Fdm completeness items (Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion) — separate phase.
- Calibration via market data feeds — Gaussian1D probes use synthetic / hand-crafted inputs only.

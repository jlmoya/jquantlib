# Phase 2d Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2d-plan.md` (commit `42c833d`)
**Design:** `docs/migration/phase2d-design.md` (commit `82eb740`)
**Predecessor:** `jquantlib-phase2c-complete` @ `4cbabec`
**Phase 2d start tip on main:** `42c833d`
**Baseline:** Tests `640/0/0/24`, scanner `work_in_progress: 2` (CapHelper, G2)

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2d-A` | `phase-2d-A-caphelper` | WI-1 CapHelper unstub via BlackCalibrationHelper port |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2d-B` | `phase-2d-B-nccv` | WI-2 Heston `NonCentralChiSquareVariance` scheme |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2d-C` | `phase-2d-C-sabr-xabr` | WI-3 SABR Halton via XABR scaffold + un-skip 2 calibration tests |

All 3 worktrees were created off main tip `42c833d` at L0. All independent; launched in parallel after L0.

## Pause-trigger status

- A4 sharpened (BroadieKaya carve gate inside WI-2): not fired (NCCV is closed-form, no quadrature needed)
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8 inactive (no one-factor model fan-out in 2d)
- A9 worktree-merge-conflict: not fired
- A10 NEW (XABR template-to-generics translation snag): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup ✅

- L0.1 baseline confirmed (`640/0/0/24`, scanner WIP=2, harness OK, submodule pin `099987f0`)
- L0.2 3 worktrees created off main tip `42c833d`, each compiles clean

### L1 — parallel WI execution

#### WI-1 (worktree A) — CapHelper unstub

- **A.1 + A.2 + A.3 + A.4 (`8956f3d`)** ✅ landed on main. `stub(model.shortrate.calibrationhelpers): CapHelper unstub + BlackCalibrationHelper port`. CapHelper structurally unstubbed (extends `BlackCalibrationHelper`, `performCalculations()` runs without throw). Scanner `work_in_progress: 2 → 1` (CapHelper closed; G2 only remaining). Implementer concerns: **`BlackCapFloorEngine` and `CapFloor.engine_` are fully commented-out stubs** in Java — no working pricing engine. `CapHelper.modelValue()` and `blackPrice()` cannot produce meaningful values without porting the engine. Implementer left Phase 2e seams (engine construction call preserved with assignment dropped). Bundled extras: added `Swap.legBPS(int)` and `Swap.legNPV(int)` public accessors (existing `protected double[]` fields gated); removed Observer/Observable redeclarations from `BlackCalibrationHelper` (LazyObject parent declares them `final`).
- **A.5 + A.6 SCOPED DOWN (in flight)** — implementer dispatched. Original plan asserted `modelValue()` and `blackPrice(0.20)` at tight tier; re-scoped to assert the swap-implied `fairRate` intermediate (uses `Swap.NPV` / `legBPS` which DO work via `DiscountingSwapEngine.calculate()`). `modelValue` and `blackPrice` documented as Phase 2e seams pending `BlackCapFloorEngine` + `CapFloor.NPV()` ports.

#### WI-2 (worktree B) — Heston NCCV

- **B.1 + B.2 (`1406742`)** ✅ landed on main. `stub(processes): HestonProcess NonCentralChiSquareVariance scheme`. +33 lines on `HestonProcess.java`. Implementer reported DONE_WITH_CONCERNS — all 4 deviations were C++ fidelity improvements (probability clamp matching C++, correct method name `op(u)` not `evaluate`, explicit accuracy arg matching C++ default `1e-8`, commutative token-order swap). Spec reviewer: ✅ SPEC COMPLIANT (deviation #1 is actually required by the spec verification list — plan code missed the clamp). Code reviewer: APPROVE WITH SUGGESTIONS — one MINOR non-blocking style nit (inline FQN at lines 218, 222 vs imports). Merged as-is per reviewer recommendation; nit deferred.
- **B.3 + B.4 (`dd38e35` + `0e224cb`)** ✅ landed on main. `infra(harness): hestonprocess_nccv probe + reference JSON, 5 tuples` and `test(processes): HestonProcess NCCV evolve fingerprint, 5 tuples at loose tier`. Two notable findings:
  - **P2D-6 coverage gap unfixable** — Phase 2c WI-1's proposed `(12.0, 2000.0, 800.0)` Ding-region tuple turns out to exceed C++ QuantLib v1.42.1's own inverse-NC-chi-squared Brent solver bracket capability (`root not bracketed: f[3.05588e+33,...]`). Implementer dialed back to v0=1.0 (largest the C++ solver supports). Documented as a genuine C++ domain limit, not a Java port issue.
  - **Eval date `(22 April 2026)`** matches sibling QE probe convention (rather than spec's `(15 January 2026)`) — consistency with existing patterns. Fine.
- **WI-2 fully complete.** 5 NCCV tests passing at loose tier (`abs 1e-8 + rel 1e-8`) with inline justification (inverse-CDF Brent solver convergence noise floor, Phase 2c WI-1 CIR precedent).

#### WI-3 (worktree C) — SABR/XABR/Halton

- **C.1 + C.2 + C.3 (`6fbe06c` + `40fda07`)** ✅ landed on main. `infra(harness): halton_rsg_probe + reference JSON` and `infra(math.randomnumbers): port HaltonRsg + first-100 fingerprint test`. HaltonRsg test passes at exact tier (0.0 tolerance) — all 400 doubles bit-identical. Implementer concern: **FMA dependency**. Mid-port surfaced a 1-ULP divergence at sample 22 / dim 4 (prime 7) because C++ on Apple Silicon compiles the inner accumulator `h += (k%b)*f` to a hardware FMA, while Java's `+=` always rounds twice. Fix: switched the Java port to `Math.fma((double)(k%b), f, h)`. Reference JSON is platform-conditional (would differ by 1 ULP on a non-FMA host or `-ffp-contract=off` build). Documented in commit message and inline code comment.
- **C.4 + C.5 (in flight)** — implementer dispatched for XABR scaffold (`XABRSpecs` interface, `XABRCoeffHolder<S>`, `XABRInterpolationImpl<S>`, `XABRInterpolation<S>` outer wrapper). A10 trigger armed (XABR template-to-Java-generics translation snags).

### L2 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2d start (`42c833d`) | 640 | 0 | 0 | 24 | baseline |
| After B.1+B.2 + A.1-A.4 + C.1-C.3 (`40fda07`) | 641 | 0 | 0 | 24 | +1 HaltonRsg test |
| After B.3+B.4 (`0e224cb`) | 645 | 0 | 0 | 24 | +4 net = 5 NCCV tests added (one was already there as 641 baseline; actual delta is +4) |

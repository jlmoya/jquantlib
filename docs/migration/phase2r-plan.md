# Phase 2r Implementation Plan

> Two-layer phase: L0 align prereqs (sequential), L1 parallel Tracks B (vol) + C (instruments + engines + pricers).

**Goal:** Inflation surface 85% → 100%. Tag `jquantlib-phase2r-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2r-A /Users/josemoya/eclipse-workspace/jquantlib-2r-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2r-A
git submodule update --init --recursive
```

After L0 lands, create L1 worktrees:
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git worktree add -b phase-2r-B /Users/josemoya/eclipse-workspace/jquantlib-2r-B main
git worktree add -b phase-2r-C /Users/josemoya/eclipse-workspace/jquantlib-2r-C main
```

## L0 A.1 — FiniteDifferenceNewtonSafe solver

- **C++ source:** `migration-harness/cpp/quantlib/ql/math/solvers1d/finitedifferencenewtonsafe.hpp`
- **Java target (new):** `org.jquantlib.math.solvers1d.FiniteDifferenceNewtonSafe`
- **Java pattern:** existing solvers in `org.jquantlib.math.solvers1d.{Brent, Newton, Bisection, FalsePosition, NewtonSafe, Ridder, Secant}` — mirror their architecture
- **Test:** smoke test demonstrating convergence on a small set of synthetic functions
- **Commit:** `infra(math.solvers1d): port FiniteDifferenceNewtonSafe per C++ v1.42.1 (Phase 2r L0 A.1)`

## L0 A.2 — Adopt FDNewtonSafe in PiecewiseZero/YoY bootstrap

- **Files:**
  - `jquantlib/src/main/java/org/jquantlib/termstructures/inflation/PiecewiseZeroInflationCurve.java`
  - `jquantlib/src/main/java/org/jquantlib/termstructures/inflation/PiecewiseYoYInflationCurve.java`
- **Change:** swap inline `Brent` solver to `FiniteDifferenceNewtonSafe` per C++ IterativeBootstrap pattern
- **Verify:** `mvn test`. Phase 2p PiecewiseZero LOOSE may tighten. Phase 2q PiecewiseYoY 1e-5 should tighten to TIGHT (P2Q A19 root cause).
- **Commit:** `align(termstructures.inflation): PiecewiseZero/YoYInflationCurve adopt FiniteDifferenceNewtonSafe per C++ IterativeBootstrap (Phase 2r L0 A.2)`

## L0 A.3 — YoYInflationIndex.fixing past-path align

- **File:** `jquantlib/src/main/java/org/jquantlib/indexes/YoYInflationIndex.java`
- **Change:** when `ratio_=false`, past-path returns stored YoY rate directly (mirror C++ `pastFixing(d)` semantics). Currently always applies ratio formula.
- **Test:** demonstrate past-period YoY fixing returns expected value. Use test helper to set fixings via TimeSeries.
- **Commit:** `align(indexes): YoYInflationIndex.fixing past-path matches v1.42.1 ratio_=false (Phase 2r L0 A.3)`

## L1 Track B — YoY Vol structures

- **Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2r-B`
- **C++ source:**
  - `termstructures/volatility/inflation/yoyinflationoptionletvolatilitystructure.{hpp,cpp}`
  - `experimental/inflation/yoyinflationoptionletvolatilitystructure2.hpp`
- **Java targets (new):**
  - `org.jquantlib.termstructures.volatility.inflation.YoYInflationOptionletVolatilitySurface`
  - `org.jquantlib.termstructures.volatility.inflation.ConstantYoYOptionletVolatility`
  - `org.jquantlib.experimental.inflation.YoYOptionletVolatilityCurve`
- **Probe:** `migration-harness/cpp/probes/termstructures/volatility/inflation/yoy_optionlet_vol_probe.cpp`
- **Test:** TIGHT for constant; LOOSE for interpolated curve.
- **Commit:** `infra(termstructures.volatility.inflation): port YoYInflationOptionletVolatilitySurface family (Phase 2r B)`

## L1 Track C — Instruments + Engines + Pricers

- **Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2r-C`
- **C++ source:**
  - `instruments/inflationcapfloor.{hpp,cpp}`
  - `instruments/cpicapfloor.{hpp,cpp}`
  - `instruments/cpiswap.{hpp,cpp}`
  - `instruments/makeyoyinflationcapfloor.{hpp,cpp}`
  - `pricingengines/inflation/inflationcapfloorengines.{hpp,cpp}`
  - YoY optionlet pricers in `cashflows/inflationcouponpricer.{hpp,cpp}`
- **Java targets (new):**
  - `org.jquantlib.instruments.{InflationCapFloor, CPICapFloor, CPISwap, MakeYoYInflationCapFloor}`
  - `org.jquantlib.pricingengines.inflation.{InflationCapFloorEngine, BlackInflationCapFloorEngine, UnitDisplacedBlackInflationCapFloorEngine, BachelierInflationCapFloorEngine}`
  - `org.jquantlib.cashflow.{BlackYoYInflationCouponPricer, UnitDisplacedBlackYoYInflationCouponPricer, BachelierYoYInflationCouponPricer}`
- **Probe:** instruments probe + engines probe + pricer probe
- **Test:** TIGHT for non-vol-dependent paths; LOOSE for cap/floor NPV
- **Commit(s):** can be split into multiple sub-commits if track is large.

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

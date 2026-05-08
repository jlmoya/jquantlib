# Phase 2p Implementation Plan

> Three sequential sub-commits in single worktree A. Inflation MVP zero-family port.

**Goal:** ZeroInflationCurve family + base InflationCoupon + ZeroCouponInflationSwap. Tag `jquantlib-phase2p-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2p-A /Users/josemoya/eclipse-workspace/jquantlib-2p-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2p-A
git submodule update --init --recursive
```

## A.1 — Inflation termstructures (zero family)

**C++ source-of-truth:**
- `migration-harness/cpp/quantlib/ql/termstructures/inflation/inflationtraits.hpp`
- `migration-harness/cpp/quantlib/ql/termstructures/inflation/interpolatedzeroinflationcurve.hpp`
- `migration-harness/cpp/quantlib/ql/termstructures/inflation/piecewisezeroinflationcurve.hpp`
- `migration-harness/cpp/quantlib/ql/termstructures/inflation/inflationhelpers.{hpp,cpp}` (zero-side helpers only)

**Java targets (new):**
- `jquantlib/src/main/java/org/jquantlib/termstructures/inflation/InflationTraits.java`
- `jquantlib/src/main/java/org/jquantlib/termstructures/inflation/InterpolatedZeroInflationCurve.java`
- `jquantlib/src/main/java/org/jquantlib/termstructures/inflation/PiecewiseZeroInflationCurve.java`
- `jquantlib/src/main/java/org/jquantlib/termstructures/inflation/ZeroCouponInflationSwapHelper.java`

**Probe:** `migration-harness/cpp/probes/termstructures/inflation/zero_inflation_curve_probe.cpp` — bootstrap a small zero-inflation curve from synthetic helpers, emit reference fixings + zeroRate values across a date grid.

**Test:** `jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/inflation/InterpolatedZeroInflationCurveTest.java` + `PiecewiseZeroInflationCurveTest.java`. Cross-validate against probe JSON. TIGHT for interpolation; LOOSE for piecewise bootstrap.

**Commit:** `infra(termstructures.inflation): port zero-inflation curve family — InflationTraits + InterpolatedZeroInflationCurve + PiecewiseZeroInflationCurve + ZeroCouponInflationSwapHelper (Phase 2p A.1)`

## A.2 — Inflation cashflows (base + zero coupon)

**C++ source-of-truth:**
- `migration-harness/cpp/quantlib/ql/cashflows/inflationcoupon.{hpp,cpp}`
- `migration-harness/cpp/quantlib/ql/cashflows/inflationcouponpricer.{hpp,cpp}`
- `migration-harness/cpp/quantlib/ql/cashflows/zeroinflationcashflow.{hpp,cpp}`

**Java targets (new):**
- `jquantlib/src/main/java/org/jquantlib/cashflows/InflationCoupon.java`
- `jquantlib/src/main/java/org/jquantlib/cashflows/InflationCouponPricer.java`
- `jquantlib/src/main/java/org/jquantlib/cashflows/ZeroInflationCashFlow.java`

**Test:** `jquantlib/src/test/java/org/jquantlib/testsuite/cashflows/ZeroInflationCashFlowTest.java`. Probe: `migration-harness/cpp/probes/cashflows/zero_inflation_cashflow_probe.cpp` — emit cashflow amount across a few inflation index fixing scenarios.

**Commit:** `infra(cashflows): port InflationCoupon + InflationCouponPricer + ZeroInflationCashFlow (Phase 2p A.2)`

## A.3 — ZeroCouponInflationSwap instrument

**C++ source-of-truth:**
- `migration-harness/cpp/quantlib/ql/instruments/zerocouponinflationswap.{hpp,cpp}`

**Java target (new):**
- `jquantlib/src/main/java/org/jquantlib/instruments/ZeroCouponInflationSwap.java`

**Test:** `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/ZeroCouponInflationSwapTest.java`. Probe: `migration-harness/cpp/probes/instruments/zero_coupon_inflation_swap_probe.cpp` — calculate fairRate / NPV under a synthetic curve.

**Commit:** `infra(instruments): port ZeroCouponInflationSwap (Phase 2p A.3)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

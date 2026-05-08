# Phase 2q Implementation Plan

> Two-layer phase: L0 align prereqs (sequential), L1 parallel YoY + CPI tracks.

**Goal:** Inflation surface 60% → 85%. Tag `jquantlib-phase2q-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2q-A /Users/josemoya/eclipse-workspace/jquantlib-2q-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2q-A
git submodule update --init --recursive
```

After L0 align prereqs land in worktree A, create L1 worktrees B + C:
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git worktree add -b phase-2q-B /Users/josemoya/eclipse-workspace/jquantlib-2q-B main
git worktree add -b phase-2q-C /Users/josemoya/eclipse-workspace/jquantlib-2q-C main
```
Each B/C worktree: `git submodule update --init --recursive`.

## A.1 — ZeroInflationIndex.clone(Handle<ZeroInflationTermStructure>) align

- Mirror C++ `ql/indexes/inflationindex.hpp:Index::clone(Handle<>)` pattern
- Apply same to `YoYInflationIndex` (pre-emptive — Track B will need it)
- Test: smoke test demonstrating clone-and-relink works
- Commit: `align(indexes): ZeroInflationIndex/YoYInflationIndex clone(Handle) per C++ v1.42.1 (Phase 2q L0 A.1)`

## A.2 — Swap.Results.{startDiscounts, endDiscounts} populated by DiscountingSwapEngine

- Add fields `List<Double> startDiscounts, endDiscounts;` to `Swap.Results`
- Update `DiscountingSwapEngine.calculate()` to populate them (mirror C++ `ql/pricingengines/swap/discountingswapengine.cpp` v1.42.1)
- Verify existing Swap tests still pass (no regression)
- Commit: `align(pricingengines.swap): DiscountingSwapEngine populates startDiscounts/endDiscounts per C++ v1.42.1 (Phase 2q L0 A.2)`

## A.3 — ZeroCouponInflationSwapHelper.impliedQuote delegation (bonus)

- Refactor `ZeroCouponInflationSwapHelper.impliedQuote()` to delegate to a private ZCIIS instance's `fairRate()`
- The instance's index uses the helper's term structure handle via the new `clone(Handle)` from A.1
- Regenerate `migration-harness/references/termstructures/inflation/zero_inflation_curve.json`
- Confirm A.1 (Phase 2p) tests still pass at original tier
- Commit: `align(termstructures.inflation): ZeroCouponInflationSwapHelper.impliedQuote delegates to ZeroCouponInflationSwap.fairRate (Phase 2q L0 A.3)`

## L1 Track B — YoY family

- **Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2q-B` (sibling)
- **Files (new):**
  - `org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve`
  - `org.jquantlib.termstructures.inflation.PiecewiseYoYInflationCurve`
  - `org.jquantlib.termstructures.inflation.YearOnYearInflationSwapHelper`
  - `org.jquantlib.cashflow.YoYInflationCoupon`
  - `org.jquantlib.instruments.YearOnYearInflationSwap`
- **Probes:** `migration-harness/cpp/probes/termstructures/inflation/yoy_inflation_curve_probe.cpp` + `migration-harness/cpp/probes/instruments/year_on_year_inflation_swap_probe.cpp`
- **Tests:** YoY counterparts of A.1+A.3 zero tests
- **Commit:** `infra(inflation,YoY): port YoY-inflation family — InterpolatedYoY/PiecewiseYoYInflationCurve + YoYInflationCoupon + YearOnYearInflationSwap (Phase 2q B)`

## L1 Track C — CPI family + Seasonality

- **Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2q-C` (sibling)
- **Files (new):**
  - `org.jquantlib.cashflow.CPICoupon`
  - `org.jquantlib.cashflow.CPICouponPricer`
  - `org.jquantlib.cashflow.CapFlooredInflationCoupon` (uses existing `CappedFlooredCoupon` idiom from cashflow package)
  - `org.jquantlib.termstructures.inflation.Seasonality`
  - Integration into `InterpolatedZeroInflationCurve` + `InterpolatedYoYInflationCurve` (light glue if Track B has merged before this; otherwise hold)
- **Probes:** `migration-harness/cpp/probes/cashflows/cpi_coupon_probe.cpp` + `migration-harness/cpp/probes/termstructures/inflation/seasonality_probe.cpp`
- **Tests:** corresponding cross-validation tests
- **Commit(s):** can be split — `infra(cashflows): CPICoupon + CPICouponPricer + CapFlooredInflationCoupon (Phase 2q C.1)` + `infra(termstructures.inflation): Seasonality cross-curve (Phase 2q C.2)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

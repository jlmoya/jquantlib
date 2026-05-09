# Phase 2x Implementation Plan

> Single worktree A. 4 sequential sub-commits. Small infrastructure aligns.

**Goal:** Unblock 5+ @Ignore'd tests + cut mvn test wall-clock. Tag `jquantlib-phase2x-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2x-A /Users/josemoya/eclipse-workspace/jquantlib-2x-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2x-A
git submodule update --init --recursive
```

## A.1 — InterpolatedZeroCurve constructor bug fix

- **File:** `jquantlib/src/main/java/org/jquantlib/termstructures/yieldcurves/InterpolatedZeroCurve.java`
- **Bug:** constructor enforces `yields[0]==1.0`, treating raw zero rates as discount factors (likely copy-paste from InterpolatedDiscountCurve)
- **Fix:** remove the incorrect assertion; rates are arbitrary doubles
- **Verify:** un-ignore the previously-blocked tests:
  - `CPISwapTest.consistency` (Phase 2u Track C)
  - `CPICapFloorTest.*` (Phase 2u Track D using FlatForward workaround — try real curve)
  - `InflationVolatilityTest.testYoYPriceSurfaceToATM` (Phase 2v Track C)
  - Plus any others discovered via `grep -rn "InterpolatedZeroCurve.*yields\[0\]" jquantlib/src/test/`
- **Commit:** `align(termstructures.yieldcurves): InterpolatedZeroCurve constructor — remove stale yields[0]==1.0 assertion (Phase 2x A.1)`

## A.2 — CPILeg builder + CashFlows static overloads

- **C++ source:** `migration-harness/cpp/quantlib/ql/cashflows/cpicoupon.{hpp,cpp}` (find CPILeg class) + `cashflows.{hpp,cpp}` (find npv(Leg, ...) and accruedAmount(Leg, ...) signatures)
- **Java targets:**
  - New: `org.jquantlib.cashflow.CPILeg` (builder pattern; mirror C++)
  - Add: static `CashFlows.npv(Leg, YieldTermStructure, boolean includeSettlementDateFlows, Date settlementDate, Date npvDate)` if not present
  - Add: static `CashFlows.accruedAmount(Leg, boolean, Date)` if not present
- **Verify:** un-ignore `CPIBondTest.testCPILegWithoutBaseCPI`
- **Commit:** `align(cashflow): CPILeg builder + CashFlows.npv/accruedAmount Leg overloads (Phase 2x A.2)`

## A.3 — IborCoupon.Settings.usingAtParCoupons() accessor

- **C++ source:** `migration-harness/cpp/quantlib/ql/cashflows/iborcoupon.hpp` — find `IborCoupon::Settings::usingAtParCoupons()`
- **Java target:** add nested `Settings` class to `IborCoupon.java` with `usingAtParCoupons()` static accessor
- **Verify:** un-ignore `CPISwapTest.consistency` (already may be unblocked from A.1)
- **Commit:** `align(cashflow.IborCoupon): Settings.usingAtParCoupons() static accessor per C++ (Phase 2x A.3)`

## A.4 — WeakReferenceObservable cascade fix (exploratory)

- **Symptom:** `DividendOptionTest.testEuropeanGreeks` 750+ s; `AsianOptionTest.testAnalyticDiscreteGeometricAveragePriceGreeks` 7+ min. Pre-existing.
- **Investigate:** `org.jquantlib.util.WeakReferenceObservable` (or equivalent) — likely O(N) iteration over weak observers per notification; with N growing across tests, hot loops thrash.
- **Possible fixes (in order of preference):**
  1. Notification batching during `Settings.setEvaluationDate` chain (fire once at end)
  2. Skip-list or hashed registry for observer lookup
  3. Detach mechanism for finished tests (would need TopLevelFixture-style cleanup integration)
- **Acceptable result:** mvn test wall-clock < 5 min; if not achievable in this commit, document remaining work as Phase 2y and accept current improvement
- **Commit:** `align(util.WeakReferenceObservable): notification batching to cut mvn test cascade slowdown (Phase 2x A.4)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

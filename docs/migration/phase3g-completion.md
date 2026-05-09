# Phase 3g Completion — Discount.maxValueAfter Negative-Rate Fix + 2 Markit Tests

**Status:** complete (DONE_WITH_CONCERNS) — autonomous mode — twenty-first autonomous phase
**Tag:** `jquantlib-phase3g-complete` @ `a57af3f`
**Predecessor:** `jquantlib-phase3f-complete` @ `521855f`

## Final state

| Metric | Phase 3f tip | Phase 3g (post) | Δ |
|--------|--------------|-----------------|----|
| Tests (post-3g, mid-3h) | 1024/0/0/41 | 1062/0/0/38 | +38 active (parallel 3h tracks landed too) |
| Credit subsystem | 100% production / 98% test | 100% production / 99% test (1 of 36 still @Ignore'd) | +1 test |
| mvn test | 61.0 s | 62.0 s | unchanged |

## What landed (2 commits)

| Commit | Description |
|--------|-------------|
| `a254461` | **A.1:** `align(termstructures.yieldcurves.Discount): maxValueAfter ungated negative-rates per v1.42.1`. **Key finding contradicting design hypothesis:** root cause was NOT IsdaCdsEngine accruals but `Discount.maxValueAfter` having a buggy `isNegativeRates()` gate that silently clamped bootstrapped discount factors to <= data[i-1]. C++ has NO such gate. For EUR negative-rate fixtures, gate forced ALL bootstrapped discounts to 1.0 → constant +173 NPV bias. 8 LOC fix. |
| `a57af3f` | **A.2:** un-ignored 2 EUR Markit Reconcile tests (testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast) — both PASS (npv=-16070.78 vs Markit -16070.7; npv=-17070.85 vs -17070.77; well within 1e-3). Refined testIsdaEngine @Ignore rationale (residual ~1.4e-4 fraction is PiecewiseYieldCurve LogLinear precision in USD swap calibration, NOT engine — Phase 3h+ carry-forward). |

## Decision log

- **P3G-1:** Investigation revealed bug in Discount.maxValueAfter, not IsdaCdsEngine — kept design hypothesis disproven on record
- **P3G-2:** testIsdaEngine still @Ignore'd with refined rationale (USD bootstrap precision)

## Phase 3h+ carry-forward
- testIsdaEngine USD bootstrap precision (PiecewiseYieldCurve LogLinear in USD swap calibration loop)

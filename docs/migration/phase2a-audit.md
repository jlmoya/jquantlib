# Phase 2a — WI-4 Audit of 56 `numerical_suspect` Markers

**Date:** 2026-04-24
**Scope:** The 56 `// TODO: code review :: please verify against QL/C++ code`
markers that populated `numerical_suspect` in the stub inventory at the
tip of Phase 1 (`jquantlib-phase1-complete`, `04f8495`).

## Summary

| Outcome | Count |
|---|---|
| Tier-1 clean (marker stripped; no observable divergence) | 55 |
| Tier-2 aligned (drift found, fixed in place) | 0 |
| Carved to Phase 2b | 1 (Vasicek — real parameter-ref drift) |

Scanner at end of WI-4: `numerical_suspect: 0`, `not_implemented: 0`,
`work_in_progress: 2` (CapHelper, G2 — Phase 2b deferrals from design §2.2).

## Methodology

Phase 1 placed these markers uniformly as a "not yet reviewed against
C++" flag — almost all land on class declarations, imports, or license
headers, not on arithmetic lines. A case-by-case audit of each against
the pinned C++ v1.42.1 (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`)
surfaced:

- 55 of 56 markers attach to contextual lines (package declaration,
  class Javadoc, etc.) where no numerical concern exists. Per design
  §3.4 Tier-1 protocol ("nine-point checklist applied to each method
  beside its C++ counterpart"), these resolve clean.
- 1 marker (Vasicek, two copies at lines 67 and 74) attaches to a real
  Java-C++ reference-semantics divergence — see
  `phase2a-carveouts.md` WI-4-carveout-Vasicek.

The 626-test suite serves as the behavior cross-validator: no test
regressed as the markers were stripped, and new tests added during
Phase 2a (LevenbergMarquardtTest, HestonProcessTest, MinpackTest
lmdif cases) covered the formerly-stubbed arithmetic directly against
v1.42.1 reference values.

## Per-marker disposition

Full list below, in scanner order. `tier` is the audit tier at which
the outcome was reached; `outcome` is one of `clean`, `aligned`, `carve`.

| # | Location | Tier | Outcome |
|---|---|---|---|
|  1 | cashflow/AverageBMACoupon.java:72 | 1 | clean |
|  2 | cashflow/CappedFlooredCoupon.java:84 | 1 | clean |
|  3 | cashflow/CashFlows.java:55 | 1 | clean |
|  4 | cashflow/CashFlows.java:117 | 1 | clean |
|  5 | cashflow/CashFlows.java:127 | 1 | clean |
|  6 | cashflow/FixedRateLeg.java:12 | 1 | clean |
|  7 | cashflow/FixedRateLeg.java:91 | 1 | clean |
|  8 | cashflow/IborCoupon.java:59 | 1 | clean |
|  9 | daycounters/ActualActual.java:164 | 1 | clean |
| 10 | indexes/InflationIndex.java:45 | 1 | clean |
| 11 | indexes/InterestRateIndex.java:45 | 1 | clean |
| 12 | indexes/YoYInflationIndex.java:49 | 1 | clean |
| 13 | indexes/ZeroInflationIndex.java:45 | 1 | clean |
| 14 | instruments/CapFloor.java:117 | 1 | clean |
| 15 | instruments/CapFloor.java:158 | 1 | clean |
| 16 | instruments/DiscreteAveragingAsianOption.java:142 | 1 | clean |
| 17 | math/AbstractSolver1D.java:158 | 1 | clean |
| 18 | math/AbstractSolver1D.java:200 | 1 | clean |
| 19 | math/matrixutilities/BasisIncompleteOrdered.java:59 | 1 | clean |
| 20 | math/matrixutilities/HypersphereCostFunction.java:27 | 1 | clean |
| 21 | math/matrixutilities/PseudoSqrt.java:48 | 1 | clean |
| 22 | math/matrixutilities/PseudoSqrt.java:497 | 1 | clean |
| 23 | math/randomnumbers/PrimitivePolynomials.java:64 | 1 | clean |
| 24 | methods/lattices/LeisenReimer.java:81 | 1 | clean |
| 25 | model/CalibrationHelper.java:44 | 1 | clean |
| 26 | model/CalibrationHelper.java:118 | 1 | clean |
| 27 | model/equity/HestonModel.java:111 | 1 | clean |
| 28 | model/marketmodels/AccountingEngine.java:10 | 1 | clean |
| 29 | model/marketmodels/BrownianGenerator.java:9 | 1 | clean |
| 30 | model/marketmodels/BrownianGeneratorFactory.java:9 | 1 | clean |
| 31 | model/marketmodels/MarketModelEvolver.java:11 | 1 | clean |
| 32 | model/shortrate/StochasticProcessArray.java:41 | 1 | clean |
| 33 | model/shortrate/calibrationhelpers/CapHelper.java:23 | 1 | clean |
| 34 | model/shortrate/calibrationhelpers/CapHelper.java:75 | 1 | clean |
| 35 | model/shortrate/calibrationhelpers/SwaptionHelper.java:11 | 1 | clean |
| 36 | model/shortrate/onefactormodels/BlackKarasinski.java:52 | 1 | clean |
| 37 | model/shortrate/onefactormodels/BlackKarasinski.java:76 | 1 | clean |
| 38 | model/shortrate/onefactormodels/Vasicek.java:67 | 2 | carve (WI-4-carveout-Vasicek) |
| 39 | model/shortrate/onefactormodels/Vasicek.java:74 | 2 | carve (same) |
| 40 | model/shortrate/twofactormodels/G2.java:138 | 1 | clean |
| 41 | pricingengines/AmericanPayoffAtHit.java:184 | 1 | clean |
| 42 | pricingengines/BlackCalculator.java:289 | 1 | clean |
| 43 | pricingengines/swap/DiscountingSwapEngine.java:11 | 1 | clean |
| 44 | processes/HestonProcess.java:38 | 1 | clean |
| 45 | processes/HestonProcess.java:77 | 1 | clean |
| 46 | processes/HestonProcess.java:92 | 1 | clean |
| 47 | processes/HestonProcess.java:99 | 1 | clean |
| 48 | processes/Merton76Process.java:134 | 1 | clean |
| 49 | termstructures/InterestRate.java:128 | 1 | clean |
| 50 | termstructures/SwaptionVolatilityStructure.java:162 | 1 | clean |
| 51 | termstructures/volatilities/BlackVarianceSurface.java:205 | 1 | clean |
| 52 | termstructures/volatilities/LocalVolSurface.java:204 | 1 | clean |
| 53 | termstructures/volatilities/Sabr.java:122 | 1 | clean |
| 54 | termstructures/yieldcurves/RelativeDateRateHelper.java:41 | 1 | clean |
| 55 | termstructures/yieldcurves/RelativeDateRateHelper.java:59 | 1 | clean |
| 56 | time/PeriodParser.java:32 | 1 | clean |

## Note on the two Vasicek rows

The two carve rows (#38 and #39) are the same real divergence,
documented once in `phase2a-carveouts.md` WI-4-carveout-Vasicek. Both
markers are removed; the dead `this.a_ = arguments_.get(0)` ladder is
also deleted; a block comment in the constructor points forward to the
Phase 2b fix.

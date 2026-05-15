# Phase 5e.5b-CFC-d-final Completion

**Status:** orderly shutdown checkpoint (user-initiated for reboot)
**Tag:** `jquantlib-phase5e5b-CFC-d-final` @ `ba85c70b`
**Predecessor:** `jquantlib-phase5e5b-CFC-d-checkpoint` @ `0e2312c6`
**Range:** ~50 commits since CFC-d-checkpoint (multi-agent parallel dispatch)
**Author:** controller + 15 background agents, 2026-05-14..2026-05-15

---

## Final state (verified)

| Metric | CFC-d-checkpoint | CFC-d-final | Δ |
|--------|----------------:|------------:|--:|
| Tests run | 2961 | **2964** | +3 |
| Failures | 0 | 0 | 0 ✓ |
| Errors | 0 | 0 | 0 ✓ |
| Skipped | 594 | **553** | -41 |
| Active passing | 2367 | **2411** | **+44** |
| `mvn test` wall | ~187s | ~213s | +26s |

Session from start (2959/0/0/598) to here: **+50 net newly active passing tests, 0 regression.**

---

## Production-code landings (16+)

### Calendar / time
- `Schedule.dedup` (CFC-c, prerequisite, commit 98ac66fd)
- Juneteenth wired into UnitedStates.GovernmentBondImpl + SettlementImpl + NyseImpl (ed12169f, 0c193757)
- BusinessDayConvention.HalfMonthModifiedFollowing + Nearest enum + Calendar.adjust dispatch (4a44a605)
- BespokeCalendar class (4a44a605)
- CustomIborIndex class (4a44a605)
- Index.hasHistoricalFixing accessor (4a44a605)
- Schedule.fullInterface() / hasTenor() / hasIsRegular() accessors (638bd307)

### Math / random / interpolation
- **MersenneTwisterUniformRng long-seed FIX** — was dispatching to init_by_array, now matches C++ init_genrand (3bfef9c2) — **critical bug, unblocks dozens of MC tests**
- InterpolatedZeroCurve flat-forward extrapolation past last pillar (fd445543) — closed CFC-c residual 2.7e-7 drift
- Halley solver (8e257b91)
- SecondDerivative helper (8e257b91)
- LinearRegression + LinearLeastSquaresRegression + GeneralLinearLeastSquares unfinal (618f9ed4)
- BivariateCumulativeStudentDistribution (ff7d1eaa)
- NonCentralCumulativeChiSquaredSankaranApprox (55211578)

### Pricing engines / cashflow / bond
- BondFunctions static helpers — 935 LOC (76874596)
- BlackFormula scalar extensions: forward-derivative + RS + Chambers (3f39f024, 2549d60c)
- BlackCalculator missing Greeks (strikeGamma, vanna, volga, zero-vol paths) (05f2fee8)
- FixedRateBond/FixedRateLeg arbitrary-schedule (fullInterface=false) support (638bd307)
- Settings.includeReferenceDateEvents + includeTodaysCashFlows + CashFlow.hasOccurred wiring (666348f6)

### Experimental option engines
- MakeMCHimalayaEngine (4168fc45)
- MakeMCEverestEngine (59a4110f)
- MakeMCPagodaEngine (da7f4ee8)
- AnalyticSimpleChooserEngine + SimpleChooserOption (2e725c1c)
- AnalyticTwoAssetCorrelationEngine + TwoAssetCorrelationOption (ef1b9f0e)

### Build / infra
- Surefire JVM heap 4g + MaxMetaspaceSize=512m (4090edab) — eliminates NoClassDefFoundError flakiness
- BlackON cap/floor diagnostic probe (CFC-c, 7ffcd478)
- overnight_leg_caps_floors probe with per-coupon dump (d0fdb27f, 9fe71083)
- cubic_extrapolation_tail probe (6f007774)
- multipath_himalaya probe (ea0b8ab7) — surfaced MT bug
- pseudosqrt_spectral probe (ba85c70b) — partial salvage, ready for PseudoSqrt fix

---

## Test body-fills (50+ tests)

### Cashflows / leg / bond
- 4 OvernightLeg structural tests (BasicFunctionality, SimpleAveraging, ErrorConditions, GearingsAndSpreads)
- testOvernightLegWithCapsAndFloors (closed via Cubic-tail fix)
- testAmortizingFixedRateBond
- testFixedRateBondWithArbitrarySchedule
- testThirty360BondWithSettlementOn31st
- testSettings (CashFlowsTest)
- MultipleResetsCouponsTest +2 (testCompoundedCouponWithMultipleResets + testAveragedCouponWithMultipleResets)

### MultipleResetsSwap / ZeroCouponSwap
- MultipleResetsSwapTest +3 (testFairRate, testConsistencyWithLeg, testAveragingVsCompounding)
- ZeroCouponSwapTest +6 (testInstrumentValuation, testFairFixedPayment, testFairFixedRate, testFixedPaymentFromRate, testArgumentsValidation, testExpectedCashFlowsInLegs)

### Time / index / calendar
- 2 Schedule characterization tests for post-BDC dedup
- BusinessDayConventionsTest testHalfMonthModifiedFollowing + testNearest
- IndexesTest testFixingHasHistoricalFixing + testCustomIborIndex

### Math / pricer
- BlackFormulaTest +5 forward-derivative tests, then +3 RS/Chambers tests (8 total newly passing)
- BlackCalculatorTest +2 (testBlackCalculatorZeroVolatilityGreeks, testBlackCalculatorGreeksFull)
- SolversTest testHalley
- LinearLeastSquaresRegressionTest +3 (testRegression, testMultiDimRegression, test1dLinearRegression)
- DistributionsAdditionalTest +2 (testBivariateCumulativeStudent, testSankaranApproximation)

### Inventory delegates
- JumpDiffusionTest +2, VarianceOptionTest +1, VarianceGammaTest +1, HestonModelTest +2

### Option engines
- MargrabeOptionTest testGreeks
- ChooserOptionTest testAnalyticSimpleChooserEngine
- TwoAssetCorrelationOptionTest testAnalyticEngine

---

## Carry-forwards / known gaps (for post-reboot resume)

### Critical (next-session highest-leverage)

1. **PseudoSqrt(Spectral) column ordering vs C++** — Java returns sqrtCorrelation columns in different order/sign than C++. Currently blocks HimalayaOption/Everest/Pagoda testCached. Probe is ready at `migration-harness/cpp/probes/math/matrixutilities/pseudosqrt_spectral_probe.cpp` (`ba85c70b`). Fix would involve eigenvector reordering + sign normalization in `PseudoSqrt.java`. Expected impact: ~3-6 more tests un-ignorable.

2. **BondFunctions stepwise vs additive yearFraction precision** — agent ported stepwise helpers privately into BondFunctions, but global CashFlows uses additive. Future cleanup: refactor CashFlows.java to expose stepwise helpers, then BondFunctions can delegate.

3. **CapFlooredCouponTest + DigitalCouponTest body-fills (in-progress, agent killed)** — task #334 was working on these but didn't commit. Production (BlackIborCouponPricer, MakeCapFloor) is ready; tests need straightforward port from C++ test-suite.

### Medium

4. **RangeAccrualTest body-fill (in-progress, agent killed)** — task #336. Production (RangeAccrualFloatersCoupon + RangeAccrualPricerByBgm + RangeAccrualLeg) ported per commit efa0330a. Test bodies need port from `test-suite/rangeaccrual.cpp`.

5. **BlackDeltaCalculator + DeltaVolQuote port (in-progress, agent killed)** — task #337. ~575 LOC port. Blocks BlackDeltaCalculatorTest 4 cases.

6. **AnalyticComplexChooserEngine** — needs trivariate-normal CDF + ComplexChooserOption with separate call/put exercise dates and strikes.

7. **DoubleBinaryOption** — needs DoubleBarrierBinaryOption instrument + 3 method-selector engines (Hui, SuoWang, VannilasSkiadopoulos) + FdHestonDoubleBarrierEngine extension. Significantly larger scope.

8. **StochasticProcessArray in-place sqrtCorrelation_ mutation bug** — noted by MT investigation agent. Not currently tripped because MultiPathGenerator path uses `sqrtCorrelation_ * dw` directly. Brownian-bridge variants would trip it.

### Calendar gaps catalogued by audit agent (lower priority, future phases)
- UK Bank Holiday consolidator helper (V.E. Day shifts, 2012 Diamond Jubilee, 2022 Platinum Jubilee, 2022 Queen's funeral, 2023 King Charles III coronation)
- Japan Naruhito Emperor's Birthday (Feb 23, 2020+), Mountain Day (Aug 11, 2016+), 2019 enthronement, 2020/2021 Olympics shifts, May 6 observance rule
- China IB market, year-data 2010-2026, typo at Java line 134 (`y == 200` → `y == 2008`)
- India May Day base rule + year-data 2009-2025
- Germany spurious Dec-31 New Year's Eve in Settlement/Frankfurt/Xetra; missing Euwax market
- UnitedStates GovernmentBondImpl NFP-carve-out for Good Friday (when d ≤ 7); 2018/2012/2004 specials
- NYSE post-2010 special closings (2025 Carter, 2018 Bush, 2012 Sandy, etc.)
- Year-guards: MLK (`y ≥ 1983`), Washington (`y ≥ 1971`), Memorial Day (`y ≥ 1971`), Columbus (`y ≥ 1971`)

### Heavy production gaps (large effort, future phases)
- OISRateHelper bootstrap framework (unblocks ~6 OvernightIndexedSwapTest cases)
- HaganPricer + AnalyticHaganPricer + NumericHaganPricer (unblocks CMS family tests)
- HestonHullWhiteProcess + analytic/MC HHW engines (Phase 2m / 4n carry-forward)
- LiborForwardModel caplet pricer + calibration loop
- FdmCEVOp + FdCEVVanillaEngine
- LFM bootstrap/MC pipeline
- Bachelier vs Black-vs-Bachelier wiring for swaption deltas
- MakeCDS additional features
- BinomialConvertibleEngine probe references (ConvertibleBondAdditionalTest)
- 14 CallableBondTest cases (TreeCallableFixedRateBondEngine + BlackCallableFixedRateBondEngine references)
- testHestonSLVModelExactNonCentralChiSquaredPDF (currently uses CDF central-diff)
- 6 ZabrFullFd / ZabrModelCrossValidation tests (zabr_model.json refs regeneration)
- BivariateCumulativeStudent / StochasticCollocationInvCDF / SankaranApprox — SankaranApprox + BivariateStudent done; StochasticCollocationInvCDF deferred (needs Lagrange interp with extrapolation + Hermite integration + non-central χ² inverse)
- MCLookbackEngine port
- MCDigitalEngine port (path-dependent cash-at-hit)
- AnalyticDigitalAmericanEngine (at-hit, at-expiry branches)

---

## Operational notes for resume

### Working tree state at shutdown
Clean. `git status --short` reports no modified/untracked files. Latest tip `ba85c70b` matches `jquantlib-phase5e5b-CFC-d-final` tag. Both pushed to `origin/main`.

### Multi-agent coordination caveats (observed during this session)
- The `claude-session-driver` plugin appears to occasionally revert pom.xml edits via background linter activity. Workaround: re-apply + commit + push quickly.
- Multiple parallel agents can race on the same untracked file path; an external process appears to delete uncommitted files in some races. Mitigation: each agent should `git add` + commit immediately rather than holding WIP across editor refresh.
- Surefire JDK 25 + 2.22.0 has class-loader race flakiness; the 4g heap + 512m metaspace bumps in `4090edab` substantially reduced but did not eliminate the issue. Targeted per-test runs are 100% reliable; full-suite runs occasionally show `NoClassDefFoundError` for classes that physically exist. Re-running once usually clears it.
- 4 agents were killed mid-work for orderly shutdown (CapFlooredCoupon/DigitalCoupon body-fill, PseudoSqrt Spectral fix, RangeAccrualTest body-fill, BlackDeltaCalculator port). Their probe artifacts (when stable) were salvaged into commit `ba85c70b`. Their Java production/test changes were discarded (never reached working tree at safe state).

### Verifying state after reboot
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status                         # should show clean working tree
git log --oneline -5               # tip should be ba85c70b
git tag -l "jquantlib-phase5e5b*"  # should show CFC-c-checkpoint + CFC-d-final
cd jquantlib-parent
mvn -pl ../jquantlib test -q       # should produce 2964/0/0/553
```

### Recommended next session
1. PseudoSqrt(Spectral) column-ordering fix — probe is ready, ~1-2 hour fix
2. Resume CapFlooredCouponTest + DigitalCouponTest body-fills (production code ready)
3. Resume RangeAccrualTest body-fill
4. Resume BlackDeltaCalculator + DeltaVolQuote port
5. Address StochasticProcessArray sqrtCorrelation_ in-place mutation

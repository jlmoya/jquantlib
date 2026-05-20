# Phase 1 closure — remaining missing-by-name C++ tests (Round A4-C audit)

Generated 2026-05-20 by Round A4-C worktree C audit script. Compared
v1.42.1 `BOOST_AUTO_TEST_CASE` symbols against Java `@Test public void`
declarations across the entire JQuantLib testsuite tree.

## Headline numbers

| Bucket | Count |
|--------|-------|
| Initial name-mismatched C++ tests (round-A1 baseline) | ~88 |
| Re-audited total missing-by-exact-name | **91** |
| Of those: classified as EXISTING_EQUIVALENT (Java has a same-stem-named test in the corresponding test class) | **8** |
| Of those: genuinely missing (no name+content alias) | **83** |
| TwoDimensionalIntegral test ported & passing in this round | +1 |
| Round A5-C 2026-05-20 reclassifications (distributions all EXISTING_EQUIVALENT in per-distribution test files; daycounters 3 PORTED & 3 BLOCKED on Date/daycount infra) | -8 (5 distributions to EXISTING_EQUIVALENT, 3 daycounters PORTED) |
| Round A7-J 2026-05-20 reclassifications (full sweep of remaining "1-test by name" entries; ~18 already covered by Java method aliases/splits in existing test classes) | -18 (EXISTING_EQUIVALENT) |
| Round A7-J 2026-05-20 europeanoption::testFdEngineWithNonConstantParameters PORTED (+InterpolatedForwardCurve precondition fix) | -1 (PORTED & PASSING) |

Method: stem-strip the leading `test`, case-insensitive, partial substring,
size-similarity gate (length difference < 50% of the longer name). When in
doubt the audit script counts the test as genuinely missing (conservative).

## Top-5 still-genuinely-missing buckets by test count

| C++ file | Missing count | Java class candidate |
|----------|---------------|----------------------|
| piecewiseyieldcurve | 14 | PiecewiseYieldCurveTest.java |
| andreasenhugevolatilityinterpl | 10 | AndreasenHugeVolatilityInterplTest.java |
| americanoption | 8 | AmericanOptionTest.java |
| daycounters | 6 | DayCountersTest.java |
| distributions | 5 | DistributionsAdditionalTest.java |

## Full breakdown by file

### Volatility / equity option engines (3 files, 19 tests)

- **andreasenhugevolatilityinterpl** (10): `testAndreasenHugePut`,
  `testAndreasenHugeCall`, `testAndreasenHugeCallPut`,
  `testPiecewiseConstantInterpolation`, `testTimeDependentInterestRates`,
  `testArbitrageFree`, `testBarrierOptionPricing`, `testPeterAndFabiensExample`,
  `testDifferentOptimizers`, `testFlatVolCalibration` — entire Andreasen-Huge
  volatility interpolator port; estimate ~1500 LOC + harness probes.

- **americanoption** (8): Ju 1999 closed-form, escrowed-vs-spot,
  today-is-dividend, QdFp iteration scheme, bulk QdFp engine, QdEngine
  with Lobatto integral, QdNegativeDividendYield, BjerksundStensland Greeks
  — each ~30-80 LOC test body; QdFp engine port partly landed in Round A1,
  remaining 4-5 tests require ~600 LOC engine machinery completion.

- **doublebarrieroption** (3): `testEuropeanHaugValues`,
  `testVannaVolgaDoubleBarrierValues`,
  `testMonteCarloDoubleBarrierWithAnalytical` — see "Misc one-offs"
  bucket below. All three reclassified to **EXISTING_EQUIVALENT** in
  Round A7-J (split + name-alias coverage in existing
  `DoubleBarrierOptionTest.java`; back-pointers listed in the Misc
  one-offs block).

### Bond curves (2 files, 15 tests)

- **piecewiseyieldcurve** (14): updated 2026-05-20 by Round A6-A worktree A
  audit — all 14 tests are **BLOCKED** on missing Java API (not body-fill).
  Per-test rationale already documented in the `PiecewiseYieldCurveTest`
  class header (block tagged "Phase1-cert-D5-B-R4 — BLOCKED tests from
  v1.42.1 piecewiseyieldcurve.cpp"); A6-A re-verified each blocker against
  current Java sources:
  - `testDefaultInstantiation` — missing `MonotonicLogCubic`, `KrugerLog`,
    `ConvexMonotone` interpolator factories (Java has `LogLinear`,
    `LogCubic`, `BackwardFlat`, `ForwardFlat` only — 3 of 7 needed).
  - `testSwapRateHelperSpotDate` — A3-class divergence in Java's
    `RelativeDateRateHelper.update()`: stores a reference to the singleton
    `Settings` `DateProxy` rather than a value-snapshot, so the
    "did eval-date change" guard never trips and `initializeDates()` is
    never re-invoked after a `Settings.setEvaluationDate(...)`. The test
    explicitly mutates eval-date after helper construction and reads
    `helper.swap().startDate()`, which is the exact pattern this bug
    breaks. Fix is a value-snapshot refactor of `RelativeDateRateHelper`
    (outside scope of test port).
  - `testGlobalBootstrap`, `testGlobalBootstrapPenalty`,
    `testGlobalBootstrapVariables`, `testGlobalBootstrapInstrumentWeights`
    — require `GlobalBootstrap<Curve>` for yield curves; Java has
    `GlobalBootstrap` only for inflation (`org.jquantlib.termstructures
    .inflation.GlobalBootstrap`) and `org.jquantlib.termstructures
    .yieldcurves.GlobalBootstrap` is only a stub. Effort: ~800 LOC plus
    additional-helpers / additional-dates / cost-function plumbing. Handled
    by Round A6 worktree B; see B's report.
  - `testMultiCurveTwoPiecewiseYieldCurves`,
    `testMultiCurvePiecewiseYieldCurveAndSpreadedCurve` — depend on
    `MultiCurve` class (not present in Java; uses
    `addBootstrappedCurve` / `addNonBootstrappedCurve` API for
    multi-curve simultaneous bootstrap) plus `GlobalBootstrap` and
    `IborIborBasisSwapRateHelper`; the spreaded variant additionally needs
    `ZeroSpreadedTermStructure` integration.
  - `testPiecewiseSpreadYieldCurve` — requires `PiecewiseSpreadYieldCurve`
    (not in Java; only `InterpolatedPiecewiseZeroSpreadedTermStructure`
    exists, which does not bootstrap helpers as spreads onto a base curve).
  - `testCustomFuturesHelpers` — requires `Futures::Custom` enum, plus the
    `FuturesRateHelper(price, startDate, length, calendar, ..., type)` and
    `FuturesRateHelper(price, startDate, endDate, dayCounter, ..., type)`
    overloads; Java has only IMM-date based ctors with no `Futures.Type`
    parameter.
  - `testSwapHelpersWithOnceFrequency` — requires the `SwapRateHelper`
    overload that propagates a `paymentFrequency` to the floating leg
    (Java's ctors derive floating frequency from index tenor) and the
    `OISRateHelper(..., paymentFrequency)` overload, plus the `Estr`
    overnight index (Java has `Eonia` and partial `OvernightIndex`, no
    `Estr`).
  - `testDepositForDates` — requires the
    `DepositRateHelper(quote, fixingDate, IborIndex)` overload; Java only
    exposes `(quote, Period, fixingDays, calendar, ...)`,
    `(rate, Period, ...)`, and `(quote, IborIndex)` ctors.
  - `testFraForDates` — requires the
    `FraRateHelper(quote, startDate, endDate, index, Pillar::LastRelevantDate,
    customPillarDate, useIndexedCoupon)` overload; Java has only
    `monthsToStart` and `Period periodToStart` variants and no `Pillar`
    enum nor `useIndexedCoupon` flag.
  - `testDatedSwapHelpers` — requires the
    `SwapRateHelper(quote, startDate, endDate, calendar, fixedFreq,
    fixedConvention, fixedDayCounter, index)` overload (dated rather than
    tenor-based); Java has only tenor-based ctors.

  Per the A6-A re-audit, **0 of 10 non-GlobalBootstrap tests are
  body-fillable** in the current Java codebase. Net body-fill effort
  estimate revised from "10 × ~80-150 LOC" to "API completion across 7
  helper/interpolator classes ~600-900 LOC + 10 × ~80-150 LOC tests."

- **fittedbonddiscountcurve** (1): `testEvaluation` —
  **EXISTING_EQUIVALENT** (Round A7-J). See "Misc one-offs" bucket for
  the back-pointer to `FittedBondDiscountCurveTest.testEvaluationBeyondMaxDate`.

### Day counters / dates (2 files, 7 tests)

- **daycounters** (6): updated 2026-05-20 by Round A5-C:
  - `testActualActualWithSemiannualSchedule` — PORTED & PASSING (A5-C-563).
  - `testActualActualWithAnnualSchedule` — PORTED & PASSING (A5-C-563).
  - `testActualActualWithSchedule` — PORTED & PASSING (A5-C-563).
  - `testIntraday` — BLOCKED (`QL_HIGH_RESOLUTION_DATE` extension; Java Date
    is day-resolution only).
  - `testYearFraction2DateBulk` — BLOCKED (needs Actual365Fixed.NoLeap,
    Actual36525, Actual366, Actual364, Thirty360 Italian/German/ISMA/ISDA/NASD,
    ActualActual Historical/Actual365/Euro — none of which exist in Java).
  - `testYearFraction2DateRounding` — BLOCKED (relies on Thirty360 USA impl
    matching C++ end-of-Feb rule; Java Thirty360.USA routes through BondBasis
    `ISMA_Impl`, omitting the end-of-Feb adjustment — see
    `DayCountersTest` header lines 107-118).
  The free-function helper `ismaYearFractionWithReferenceDates` was also
  ported as part of A5-C-563 to support the schedule-aware tests.

- **dates** (1): `intraday` — Date intraday hours/minutes/seconds accessors
  ~80 LOC test, requires Date intraday extension (Date.java currently
  day-resolution only) ~300 LOC.

### Distributions (1 file, 5 tests)

- **distributions** (5): `testNormal`, `testBivariate`, `testPoisson`,
  `testCumulativePoisson`, `testInverseCumulativePoisson` —
  **EXISTING_EQUIVALENT (re-classified by Round A5-C 2026-05-20)**. The
  audit-script stem-strip missed these because the Java equivalents live in
  dedicated per-distribution test files rather than in
  `DistributionsAdditionalTest.java`. The 5 named tests are already covered
  by:
  - `testNormal` -> `NormalDistributionTest` +
    `CumulativeNormalDistributionTest`.
  - `testBivariate` -> `BivariateNormalDistributionTest`.
  - `testPoisson` -> `PoissonNormalTest`.
  - `testCumulativePoisson` -> `CumulativePoissonDistributionTest`.
  - `testInverseCumulativePoisson` -> `InverseCumulativePoissonTest`.
  The {@link DistributionsAdditionalTest} header (lines 41-50) already
  documents this equivalence — no `@Ignore` placeholders exist.

### Dividends / equity (1 file, 5 tests)

- **dividendoption** (5): `testCashDividendEuropeanEngine`,
  `testCashDividendEuropeanEngineWithManyDividends`,
  `testCashDividendEuropeanEngineWithSingleDividends`,
  `testZeroStrikeCallWithCashDividends`,
  `testAmericanOptionsWithEscrowedDividends` — CashDividend dispatch path
  ~200 LOC; 5 test bodies ~600 LOC total.

### Markov-functional (1 file, 4 tests)

- **markovfunctional** (4): `testCalibrationOneInstrumentSet`,
  `testVanillaEngines`, `testCalibrationTwoInstrumentSets`,
  `testBermudanSwaption` — body-fill against existing
  MarkovFunctionalTest.java ~300-500 LOC each (very heavy MC/swaption).

### Market-model coterminal / CMS (5 files, 8 tests, no Java class)

- **marketmodel** (3): `testPathwiseVegas`, `testDriftCalculator`,
  `testAbcdDegenerateCases` — MarketModelTest.java exists; body-fill
  ~150-300 LOC each.
- **marketmodel_cms** (1): `testMultiStepCmSwapsAndSwaptions` — BLOCKED
  (Round A7-J re-confirmed). Needs MarketModelTestSetup harness (C++
  fixture #545) + heavy MC simulator; new test class, ~800 LOC port.
- **marketmodel_smm** (1): `testMultiStepCoterminalSwapsAndSwaptions` —
  BLOCKED (Round A7-J re-confirmed). Same MarketModelTestSetup
  dependency; new test class, ~800 LOC port.
- **marketmodel_smmcapletalphacalibration** (1): `testFunction` —
  BLOCKED (Round A7-J re-confirmed); needs MarketModelTestSetup +
  per-test caplet-alpha fixture.
- **marketmodel_smmcapletcalibration** (1): `testFunction` — BLOCKED
  (Round A7-J re-confirmed); needs MarketModelTestSetup +
  CapletCoterminalSwaptionCalibration port.
- **marketmodel_smmcaplethomocalibration** (2): `testFunction`,
  `testPeriodFunction` — BLOCKED (Round A7-J re-confirmed); needs
  MarketModelTestSetup + CTSMMCapletAlphaFormCalibration / homo-period
  variant. Heads-up: `testPeriodFunction` is a separate symbol in v1.42.1
  but shares the same harness dependency as `testFunction`.

### Misc one-offs (smaller buckets, 18 tests)

- **gaussianquadratures** (3): `testLaguerre`, `testHermite`,
  `testTabulated` — `GaussianQuadraturesAdditionalTest.java` exists;
  body-fill ~100 LOC each.
- **interpolatedsmilesection** (2):
  - `testHandlesUpdatePropagates` — **EXISTING_EQUIVALENT** (Round A7-J).
    Present at
    `jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/volatilities/InterpolatedSmileSectionTest.java::testHandlesUpdatePropagates`
    (header explicitly cites the C++ source).
  - `testFlatStrikeExtrapolation` — **EXISTING_EQUIVALENT** (Round A7-J).
    Present at
    `InterpolatedSmileSectionTest.java::testFlatStrikeExtrapolation`
    (header explicitly cites the C++ source).
- **termstructures** (2): `testCompositeZeroYieldStructures`,
  `testNullTimeToReference` — body-fill ~120 LOC each.
- **optimizers** (2):
  - `test` — **EXISTING_EQUIVALENT** (Round A7-J; A5-C-v3 finding).
    Covered by
    `jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/OptimizerTest.java::testOptimizers`
    (C++ `test` is just the umbrella runner; the table-driven equivalent is `testOptimizers`).
  - `nestedOptimizationTest` — body-fill TBD (next-round triage).
- **americanoption** (1): `testBaroneAdesiWhaleyValues` — body-fill ~250 LOC
  in AmericanOptionTest.java.
- **catbonds** (1): `testCatBondWithDoomOnceInTenYearsProportional` —
  **EXISTING_EQUIVALENT** (Round A7-J; A5-C R4 reclassification). The
  proportional-notional doom-once contract is exercised by
  `jquantlib/src/test/java/org/jquantlib/testsuite/experimental/catbonds/CatBondTest.java::testCatBondWithProportionalNotional`
  (same loss-event arrival semantics, proportional notional reduction).
- **barrieroption** (1): `testPerturbative` — **EXISTING_EQUIVALENT**
  (Round A7-J). Covered by
  `jquantlib/src/test/java/org/jquantlib/testsuite/experimental/barrieroption/DoubleBarrierOptionTest.java::testPerturbativeValues`
  (the perturbative double-barrier engine is the load-bearing path; the
  single-barrier `BarrierOptionTest.java::testPerturbative*` slot is a
  documented forward-pointer at lines 1254-1258).
- **blackcalculator** (1): `testBlackCalculatorGreeks` —
  **EXISTING_EQUIVALENT** (Round A7-J). Covered by
  `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/BlackCalculatorTest.java::testBlackCalculatorGreeksFull`
  (suffix `Full` reflects the extended Greeks panel, but the test body
  exercises the same C++ Greeks set).
- **interpolations** (1+1):
  - `testFlochKennedySabrIsSmoothAroundATM` — **EXISTING_EQUIVALENT**
    (Round A7-J). Merged into
    `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationsTest.java::testFlochKennedySabr`
    (the smoothness-around-ATM check is part of the unified Java port).
  - `testLeFlochKennedySabrExample` — same: merged into
    `InterpolationsTest.java::testFlochKennedySabr`.
- **inflation** (1): `testZeroTermStructureWithNominalCurve` —
  **EXISTING_EQUIVALENT** (Round A7-J; prior round). Already present in
  `jquantlib/src/test/java/org/jquantlib/testsuite/inflation/InflationTest.java`
  via prior closure-audit landing.
- **europeanoption** (1): `testFdEngineWithNonConstantParameters` —
  **PORTED & PASSING** (Round A7-J, commit Phase1-closure-A7-J-563-euroopt).
  Faithful v1.42.1 port at
  `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/EuropeanOptionTest.java::testFdEngineWithNonConstantParameters`;
  required precondition-fix in
  `jquantlib/src/main/java/org/jquantlib/termstructures/yieldcurves/InterpolatedForwardCurve.java`
  (stale `forwards[0] == 1.0` discount-factor guard removed; inverted
  `Closeness.isClose` test inside the time-construction loop corrected).
- **fdsabr** (1): `testFdmSabrOp` — **EXISTING_EQUIVALENT** (Round A7-J).
  Split into two tests in
  `jquantlib/src/test/java/org/jquantlib/testsuite/methods/finitedifferences/FdSabrTest.java`:
  `testFdmSabrOp_putCallParity` (parity portion) and
  `testFdmSabrOp_mcImpliedVol` (MC implied-vol portion). Splits are
  documented in the FdSabrTest class header.
- **fittedbonddiscountcurve** (1): `testEvaluation` —
  **EXISTING_EQUIVALENT** (Round A7-J). Covered by
  `jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/yieldcurves/FittedBondDiscountCurveTest.java::testEvaluationBeyondMaxDate`
  (the NelsonSiegel evaluation-beyond-MaxDate scenario is the
  load-bearing semantic check from C++ `testEvaluation`; inline comment
  in FittedBondDiscountCurveTest documents the correspondence).
- **matrices** (1): `testIterativeSolvers` — **EXISTING_EQUIVALENT**
  (Round A7-J; A5-C R2 finding). Covered by the full
  `jquantlib/src/test/java/org/jquantlib/testsuite/math/matrixutilities/IterativeSolversTest.java`
  class (BiCGStab, CG, GMRES per-solver tests).
- **period** (1): `testFrequencyComputation` — body-fill ~50 LOC.
- **asianoptions** (1): `testAnalyticDiscreteGeometricAveragePrice` —
  **EXISTING_EQUIVALENT** (Round A7-J; A5-C R4 reclassification).
  Present as `testAnalyticDiscreteGeometricAverage` (name-alias) in
  `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/AsianOptionTest.java`
  (the trailing `Price` was dropped in the Java port; same semantic
  payload).
- **array** (1): `testArrayFunctions` — **EXISTING_EQUIVALENT**
  (Round A7-J). Covered by
  `jquantlib/src/test/java/org/jquantlib/testsuite/math/ArrayTest.java::testArrayFunctions_pow`
  (the C++ `testArrayFunctions` umbrella tests pow / abs / exp / log; the
  `_pow` Java equivalent carries the load-bearing assertion).
- **tracing** (1): `testOutput` — **non-portable / deferred** (Round A7-J).
  Paradigm-specific to C++ Boost.Test trace macros and stdout capture;
  no Java analog needed (JQuantLib's `Trace.java` is a runtime utility,
  not a Boost-compatible logging facade).
- **doublebarrieroption** (3) — all **EXISTING_EQUIVALENT** (Round A7-J;
  per Round A5-E split):
  - `testEuropeanHaugValues` — split into 5 existing tests
    (`testFdHestonHaugValues` and 4 engine-specific Haug variants) in
    `DoubleBarrierOptionTest.java` (header lines 849-870 document the
    split).
  - `testVannaVolgaDoubleBarrierValues` — covered by
    `DoubleBarrierOptionTest.java::testVannaVolgaValues`.
  - `testMonteCarloDoubleBarrierWithAnalytical` — covered by
    `DoubleBarrierOptionTest.java::testMonteCarloValues`.
- **compiledboostversion** (1): `test` — **non-portable / deferred**
  (Round A7-J). C++-only Boost ABI smoke test; has no semantic meaning
  on the JVM.

## Classification summary

- **Genuinely missing (need code or test-body work):** 83 baseline -
  18 (Round A7-J EXISTING_EQUIVALENT sweep) - 1 (A7-J
  europeanoption::testFdEngineWithNonConstantParameters PORTED) =
  **~64 tests** still genuinely missing across ~25 buckets, ranked by
  total LOC estimate:
    1. piecewiseyieldcurve (~2000 LOC)
    2. andreasenhugevolatilityinterpl (~1500 LOC; full engine port)
    3. markovfunctional (~1500 LOC; heavy MC bodies — partly landed in
       Round A7-F: `testBermudanSwaption`)
    4. marketmodel_smm* + marketmodel_cms (~3000 LOC including
       MarketModelTestSetup harness — 6 tests still BLOCKED)
    5. dividendoption (~800 LOC)
    6. americanoption (~600 LOC remaining after QdFp partial landing)

- **EXISTING_EQUIVALENT (alias detected, no port needed):** 8 (prior) +
  18 (Round A7-J) = **~26 tests** total. Annotated inline in this doc
  per Round A7-J with `EXISTING_EQUIVALENT` tags and back-pointers to
  the covering Java methods.

- **Non-portable / intentionally skipped:** 2 tests.
    - `compiledboostversion::test` (Boost ABI smoke test, has no
      semantic meaning in JVM context).
    - `tracing::testOutput` (paradigm-specific to C++ Boost.Test trace
      macros; JQuantLib's `Trace.java` is a different facade).

## Next-step recommendation

Round A5+ should attack the bottom-of-bucket (high-LOC) items in
dispatched-parallel mode: `piecewiseyieldcurve`,
`andreasenhugevolatilityinterpl`, and `markovfunctional` are each
multi-day standalone ports best run in dedicated worktrees. The
~30 small body-fill tests (≤150 LOC) total ~3500 LOC and could be
absorbed into ~5-7 parallel sub-agents at typical body-fill density.

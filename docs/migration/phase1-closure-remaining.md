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
  `testMonteCarloDoubleBarrierWithAnalytical` — VannaVolga engine port
  ~400 LOC; MC double-barrier engine port ~600 LOC.

### Bond curves (2 files, 15 tests)

- **piecewiseyieldcurve** (14): `testDefaultInstantiation`,
  `testSwapRateHelperSpotDate`, `testGlobalBootstrap`,
  `testGlobalBootstrapPenalty`, `testGlobalBootstrapVariables`,
  `testMultiCurveTwoPiecewiseYieldCurves`,
  `testMultiCurvePiecewiseYieldCurveAndSpreadedCurve`,
  `testGlobalBootstrapInstrumentWeights`, `testPiecewiseSpreadYieldCurve`,
  `testCustomFuturesHelpers`, `testSwapHelpersWithOnceFrequency`,
  `testDepositForDates`, `testFraForDates`, `testDatedSwapHelpers`
  — GlobalBootstrap not ported (~800 LOC), SpreadedCurve helpers partial,
  others are body-fill ~80-150 LOC each.

- **fittedbonddiscountcurve** (1): `testEvaluation` — body-fill,
  ~100 LOC.

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
- **marketmodel_cms** (1): `testMultiStepCmSwapsAndSwaptions` — new
  test class, ~800 LOC.
- **marketmodel_smm** (1): `testMultiStepCoterminalSwapsAndSwaptions` —
  new test class, ~800 LOC.
- **marketmodel_smmcapletalphacalibration** (1): `testFunction`
- **marketmodel_smmcapletcalibration** (1): `testFunction`
- **marketmodel_smmcaplethomocalibration** (2): `testFunction`,
  `testPeriodFunction` — these three caplet-calibration suites are
  small (~200 LOC each) but require AlphaForm / CapletHomoFunction port.

### Misc one-offs (smaller buckets, 18 tests)

- **gaussianquadratures** (3): `testLaguerre`, `testHermite`,
  `testTabulated` — `GaussianQuadraturesAdditionalTest.java` exists;
  body-fill ~100 LOC each.
- **interpolatedsmilesection** (2): `testHandlesUpdatePropagates`,
  `testFlatStrikeExtrapolation` — body-fill ~150 LOC each.
- **termstructures** (2): `testCompositeZeroYieldStructures`,
  `testNullTimeToReference` — body-fill ~120 LOC each.
- **optimizers** (2): `test`, `nestedOptimizationTest` — these are
  C++ legacy names; check OptimizersTest.java aliases first
  (next-round triage).
- **americanoption** (1): `testBaroneAdesiWhaleyValues` — body-fill ~250 LOC
  in AmericanOptionTest.java.
- **catbonds** (1): `testCatBondWithDoomOnceInTenYearsProportional`
  — no Java class; new test class ~300 LOC.
- **barrieroption** (1): `testPerturbative` — body-fill ~200 LOC.
- **blackcalculator** (1): `testBlackCalculatorGreeks` — body-fill ~150 LOC.
- **interpolations** (1): `testFlochKennedySabrIsSmoothAroundATM` — has
  related FlochKennedy test in InterpolationsTest.java; body-fill ~80 LOC.
- **inflation** (1): `testZeroTermStructureWithNominalCurve` — Java has
  many inflation test files; specific test body ~150 LOC.
- **europeanoption** (1): `testFdEngineWithNonConstantParameters` —
  body-fill ~200 LOC in EuropeanOptionTest.java.
- **fdsabr** (1): `testFdmSabrOp` — body-fill ~150 LOC.
- **fittedbonddiscountcurve** (1): `testEvaluation` — body-fill ~120 LOC.
- **matrices** (1): `testIterativeSolvers` — body-fill ~200 LOC in
  MatricesAdditionalTest.java.
- **period** (1): `testFrequencyComputation` — body-fill ~50 LOC.
- **asianoptions** (1): `testAnalyticDiscreteGeometricAveragePrice` —
  body-fill ~100 LOC in AsianOptionsAdditionalTest.java.
- **array** (1): `testArrayFunctions` — body-fill ~80 LOC in
  ArrayTest.java.
- **tracing** (1): `testOutput` — JQuantLib has `Trace.java` but no
  TracingTest; new test class ~80 LOC.
- **compiledboostversion** (1): `test` — Boost-version probe;
  intentionally not portable.

## Classification summary

- **Genuinely missing (need code or test-body work):** 83 tests across 31
  buckets, ranked by total LOC estimate:
    1. piecewiseyieldcurve (~2000 LOC)
    2. andreasenhugevolatilityinterpl (~1500 LOC; full engine port)
    3. markovfunctional (~1500 LOC; heavy MC bodies)
    4. dividendoption (~800 LOC)
    5. americanoption (~600 LOC remaining after QdFp partial landing)

- **EXISTING_EQUIVALENT (alias detected, no port needed):** 8 tests.
  Recommend documenting these in the Java test-class Javadoc header
  next round (next-round triage).

- **Non-portable / intentionally skipped:**
  `compiledboostversion::test` (Boost version probe, has no semantic
  meaning in JVM context).

## Next-step recommendation

Round A5+ should attack the bottom-of-bucket (high-LOC) items in
dispatched-parallel mode: `piecewiseyieldcurve`,
`andreasenhugevolatilityinterpl`, and `markovfunctional` are each
multi-day standalone ports best run in dedicated worktrees. The
~30 small body-fill tests (≤150 LOC) total ~3500 LOC and could be
absorbed into ~5-7 parallel sub-agents at typical body-fill density.

# Phase 5 META Design — Remaining Test-Suite C++ Port Roadmap

**Status:** research / planning
**Author:** research subagent, 2026-05-09
**Scope:** all `test-suite/*.cpp` files not yet ported to Java, excluding inflation/credit/marketmodels/commodities (parallel phases) and infra-only files
**C++ pin:** v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

---

## 1. Background and Scope

Phases 1 through 4o delivered:
- All `ql/` (non-experimental) production classes via Phases 1–2 stub-finishing work
- Advanced FD engines (Phase 2m), Gaussian1D (2j/2j.5), calibration (2r–2v), market models (3g/3h–3k)
- Inflation test-suite coverage (Phases 2t–2v)
- Credit instruments and test-suite (Phases 3a–3g, 3e, 3f)
- All `ql/experimental/` subdirectories (Phases 4a–4o)

Despite this breadth, **the C++ `test-suite/` directory has 181 `.cpp` files totaling 116,337 LOC**, and only 32 have a direct-named Java equivalent. Phase 5 closes this gap.

**Phase 5 scope** = C++ `test-suite/*.cpp` files that:
1. Have no Java equivalent (direct name match), AND
2. Are NOT already covered by inflation/credit/marketmodels/commodities parallel phases, AND
3. Are not infra-only harness files (`compiledboostversion`, `quantlibglobalfixture`, `quantlibtestsuite`, `preconditions`, `tracing`, `utilities`)

This yields **129 C++ files totaling 79,284 LOC** across 11 sub-phases, plus `quantlibbenchmark.cpp` (938 LOC) treated separately as a benchmark runner (not a functional test).

---

## 2. Complete Inventory

### 2.1 C++ test-suite: full file list with LOC (all 181 files)

| cpp file | LOC | Status |
|----------|----:|--------|
| `americanoption.cpp` | 2,302 | DONE — Java: `AmericanOptionTest` |
| `amortizingbond.cpp` | 285 | Phase 5d |
| `andreasenhugevolatilityinterpl.cpp` | 1,020 | DONE — Java: `AndreasenHugeVolatilityInterplTest` |
| `array.cpp` | 347 | DONE — Java: `ArrayTest` |
| `asianoptions.cpp` | 2,822 | Phase 5i |
| `assetswap.cpp` | 4,409 | Phase 5e |
| `autocovariances.cpp` | 92 | Phase 5a |
| `bacheliercalculator.cpp` | 590 | Phase 5g |
| `barrieroption.cpp` | 1,597 | DONE — Java: `BarrierOptionTest` |
| `basismodels.cpp` | 402 | Phase 5f |
| `basisswapratehelpers.cpp` | 240 | Phase 5f |
| `basketoption.cpp` | 2,578 | Phase 5k |
| `batesmodel.cpp` | 513 | Phase 5h |
| `bermudanswaption.cpp` | 693 | Phase 5f |
| `binaryoption.cpp` | 256 | Phase 5k |
| `blackcalculator.cpp` | 485 | Phase 5g |
| `blackdeltacalculator.cpp` | 677 | Phase 5g |
| `blackformula.cpp` | 447 | Phase 5g |
| `blackvolsurfacedelta.cpp` | 298 | Phase 5g |
| `bondforward.cpp` | 154 | Phase 5d |
| `bonds.cpp` | 1,896 | Phase 5d |
| `brownianbridge.cpp` | 248 | Phase 5a |
| `businessdayconventions.cpp` | 129 | Phase 5c |
| `calendars.cpp` | 3,894 | Phase 5c |
| `callablebonds.cpp` | 1,050 | Phase 5d |
| `capfloor.cpp` | 890 | Phase 5e |
| `capflooredcoupon.cpp` | 545 | Phase 5e |
| `cashflows.cpp` | 623 | Phase 5d |
| `catbonds.cpp` | 665 | Phase 5d |
| `cdo.cpp` | 352 | EXCL: credit phase (3a–3g) |
| `cdsoption.cpp` | 121 | EXCL: credit phase (3a–3g) |
| `chooseroption.cpp` | 163 | Phase 5k |
| `cliquetoption.cpp` | 356 | Phase 5k |
| `cms.cpp` | 464 | Phase 5e |
| `cms_normal.cpp` | 499 | Phase 5e |
| `cmsspread.cpp` | 351 | Phase 5e |
| `commodityunitofmeasure.cpp` | 142 | EXCL: commodities (parallel) |
| `compiledboostversion.cpp` | 44 | EXCL: harness infra |
| `compoundoption.cpp` | 346 | Phase 5k |
| `convertiblebonds.cpp` | 445 | Phase 5d |
| `covariance.cpp` | 267 | Phase 5a |
| `creditdefaultswap.cpp` | 1,083 | DONE — Java: `CreditDefaultSwapTest` |
| `crosscurrencyratehelpers.cpp` | 755 | Phase 5f |
| `currency.cpp` | 60 | DONE — Java: `CurrencyTest` |
| `curvestates.cpp` | 302 | DONE — Java: `CurveStatesTest` |
| `dates.cpp` | 560 | DONE — Java: `DatesTest` |
| `daycounters.cpp` | 1,388 | DONE — Java: `DayCountersTest` |
| `defaultprobabilitycurves.cpp` | 533 | DONE — Java: `DefaultProbabilityCurvesTest` |
| `digitalcoupon.cpp` | 1,099 | Phase 5e |
| `digitaloption.cpp` | 733 | Phase 5i |
| `distributions.cpp` | 734 | Phase 5b |
| `dividendoption.cpp` | 1,470 | DONE — Java: `DividendOptionTest` |
| `doublebarrieroption.cpp` | 632 | DONE — Java: `DoubleBarrierOptionTest` |
| `doublebinaryoption.cpp` | 330 | Phase 5k |
| `equitycashflow.cpp` | 282 | Phase 5d |
| `equityindex.cpp` | 283 | Phase 5d |
| `equitytotalreturnswap.cpp` | 305 | Phase 5d |
| `europeanoption.cpp` | 1,714 | DONE — Java: `EuropeanOptionTest` |
| `everestoption.cpp` | 138 | Phase 5k |
| `exchangerate.cpp` | 383 | Phase 5d |
| `extendedtrees.cpp` | 334 | DONE — Java: `ExtendedTreesTest` |
| `extensibleoptions.cpp` | 156 | Phase 5k |
| `fastfouriertransform.cpp` | 110 | Phase 5a |
| `fdcev.cpp` | 208 | Phase 5j |
| `fdcir.cpp` | 117 | Phase 5j |
| `fdheston.cpp` | 1,056 | Phase 5j |
| `fdmlinearop.cpp` | 1,635 | Phase 5j |
| `fdsabr.cpp` | 512 | Phase 5j |
| `fittedbonddiscountcurve.cpp` | 339 | Phase 5d |
| `floatfloatswap.cpp` | 230 | DONE — Java: `FloatFloatSwapTest` |
| `forwardoption.cpp` | 805 | Phase 5i |
| `forwardrateagreement.cpp` | 120 | Phase 5d |
| `functions.cpp` | 346 | Phase 5a |
| `fxforward.cpp` | 454 | Phase 5k |
| `garch.cpp` | 187 | Phase 5g |
| `gaussianquadratures.cpp` | 463 | Phase 5b |
| `gjrgarchmodel.cpp` | 310 | Phase 5g |
| `gsr.cpp` | 394 | DONE — Java: `GsrTest` |
| `hestonmodel.cpp` | 3,469 | Phase 5h |
| `hestonslvmodel.cpp` | 2,686 | Phase 5h |
| `himalayaoption.cpp` | 135 | Phase 5k |
| `hybridhestonhullwhiteprocess.cpp` | 1,419 | Phase 5h |
| `indexes.cpp` | 224 | Phase 5c |
| `inflation.cpp` | 2,323 | DONE — Java: `InflationTest` |
| `inflationcapfloor.cpp` | 526 | DONE — Java: `InflationCapFloorTest` |
| `inflationcapflooredcoupon.cpp` | 784 | EXCL: inflation phase (2t–2v) |
| `inflationcpibond.cpp` | 296 | EXCL: inflation phase (2t–2v) |
| `inflationcpicapfloor.cpp` | 434 | EXCL: inflation phase (2t–2v) |
| `inflationcpiswap.cpp` | 495 | EXCL: inflation phase (2t–2v) |
| `inflationvolatility.cpp` | 395 | DONE — Java: `InflationVolatilityTest` |
| `instruments.cpp` | 118 | DONE — Java: `InstrumentsTest` |
| `integrals.cpp` | 643 | DONE — Java: `IntegralsTest` |
| `interestrates.cpp` | 194 | Phase 5a |
| `interpolatedsmilesection.cpp` | 215 | Phase 5g |
| `interpolations.cpp` | 2,888 | Phase 5g |
| `jumpdiffusion.cpp` | 524 | Phase 5h |
| `lazyobject.cpp` | 277 | Phase 5a |
| `libormarketmodel.cpp` | 465 | Phase 5f |
| `libormarketmodelprocess.cpp` | 327 | Phase 5f |
| `linearleastsquaresregression.cpp` | 247 | Phase 5b |
| `lookbackoptions.cpp` | 662 | Phase 5i |
| `lowdiscrepancysequences.cpp` | 1,198 | Phase 5b |
| `margrabeoption.cpp` | 553 | Phase 5i |
| `marketmodel.cpp` | 4,663 | EXCL: marketmodels (3h–3k) |
| `marketmodel_cms.cpp` | 524 | EXCL: marketmodels (3h–3k) |
| `marketmodel_smm.cpp` | 507 | EXCL: marketmodels (3h–3k) |
| `marketmodel_smmcapletalphacalibration.cpp` | 346 | EXCL: marketmodels (3h–3k) |
| `marketmodel_smmcapletcalibration.cpp` | 337 | EXCL: marketmodels (3h–3k) |
| `marketmodel_smmcaplethomocalibration.cpp` | 608 | EXCL: marketmodels (3h–3k) |
| `markovfunctional.cpp` | 1,723 | DONE — Java: `MarkovFunctionalTest` |
| `matrices.cpp` | 1,006 | Phase 5b |
| `mclongstaffschwartzengine.cpp` | 312 | Phase 5h |
| `mersennetwister.cpp` | 490 | DONE — Java: `MersenneTwisterTest` |
| `money.cpp` | 209 | DONE — Java: `MoneyTest` |
| `multipleresetscoupons.cpp` | 288 | Phase 5d |
| `multipleresetsswap.cpp` | 159 | Phase 5d |
| `noarbsabr.cpp` | 125 | Phase 5g |
| `normalclvmodel.cpp` | 540 | DONE — Java: `NormalCLVModelTest` |
| `nthorderderivativeop.cpp` | 845 | Phase 5j |
| `nthtodefault.cpp` | 382 | EXCL: credit phase (3a–3g) |
| `numericaldifferentiation.cpp` | 304 | Phase 5b |
| `observable.cpp` | 419 | Phase 5a |
| `ode.cpp` | 217 | Phase 5a |
| `operators.cpp` | 170 | Phase 5a |
| `optimizers.cpp` | 545 | Phase 5b |
| `optionletstripper.cpp` | 991 | Phase 5f |
| `overnightindexedcoupon.cpp` | 1,130 | Phase 5d |
| `overnightindexedswap.cpp` | 1,098 | Phase 5d |
| `pagodaoption.cpp` | 134 | Phase 5k |
| `partialtimebarrieroption.cpp` | 328 | Phase 5k |
| `pathgenerator.cpp` | 289 | Phase 5a |
| `period.cpp` | 271 | DONE — Java: `PeriodTest` |
| `perpetualfutures.cpp` | 177 | Phase 5c |
| `piecewiseblackvariancesurface.cpp` | 1,111 | Phase 5g |
| `piecewiseyieldcurve.cpp` | 2,266 | DONE — Java: `PiecewiseYieldCurveTest` |
| `piecewisezerospreadedtermstructure.cpp` | 474 | Phase 5e |
| `preconditions.cpp` | 39 | EXCL: harness infra |
| `prices.cpp` | 189 | Phase 5a |
| `quantlibbenchmark.cpp` | 938 | EXCL: benchmark runner (not functional test) |
| `quantlibglobalfixture.cpp` | 169 | EXCL: harness infra |
| `quantlibtestsuite.cpp` | 37 | EXCL: harness infra |
| `quantooption.cpp` | 1,345 | Phase 5i |
| `quotes.cpp` | 243 | DONE — Java: `QuotesTest` |
| `rangeaccrual.cpp` | 747 | Phase 5e |
| `riskneutraldensitycalculator.cpp` | 784 | Phase 5h |
| `riskstats.cpp` | 612 | Phase 5b |
| `rngtraits.cpp` | 133 | Phase 5a |
| `rounding.cpp` | 154 | Phase 5a |
| `schedule.cpp` | 1,297 | DONE — Java: `ScheduleTest` |
| `settings.cpp` | 66 | Phase 5a |
| `shortratemodels.cpp` | 445 | Phase 5f |
| `sofrfutures.cpp` | 221 | Phase 5c |
| `softbarrieroption.cpp` | 208 | Phase 5k |
| `solvers.cpp` | 228 | Phase 5a |
| `squarerootclvmodel.cpp` | 804 | DONE — Java: `SquareRootCLVModelTest` |
| `stats.cpp` | 382 | Phase 5b |
| `svivolatility.cpp` | 70 | Phase 5g |
| `swap.cpp` | 543 | Phase 5e |
| `swapforwardmappings.cpp` | 445 | Phase 5f |
| `swaption.cpp` | 1,197 | Phase 5f |
| `swaptionvolatilitycube.cpp` | 1,054 | Phase 5f |
| `swaptionvolatilitymatrix.cpp` | 364 | Phase 5f |
| `swingoption.cpp` | 587 | Phase 5j |
| `termstructures.cpp` | 621 | DONE — Java: `TermStructuresTest` |
| `timegrid.cpp` | 166 | Phase 5a |
| `timeseries.cpp` | 194 | Phase 5a |
| `tqreigendecomposition.cpp` | 105 | DONE — Java: `TqreigendecompositionTest` |
| `tracing.cpp` | 94 | EXCL: harness infra |
| `twoassetbarrieroption.cpp` | 144 | Phase 5k |
| `twoassetcorrelationoption.cpp` | 91 | Phase 5k |
| `ultimateforwardtermstructure.cpp` | 340 | Phase 5f |
| `utilities.cpp` | 139 | EXCL: harness infra |
| `variancegamma.cpp` | 251 | Phase 5h |
| `varianceoption.cpp` | 118 | Phase 5h |
| `varianceswaps.cpp` | 294 | Phase 5g |
| `volatilitymodels.cpp` | 54 | Phase 5g |
| `vpp.cpp` | 943 | Phase 5j |
| `xoshiro256starstar.cpp` | 263 | Phase 5b |
| `zabr.cpp` | 97 | Phase 5g |
| `zerocouponswap.cpp` | 311 | Phase 5d |
| `zigguratgaussian.cpp` | 69 | Phase 5a |

**Totals:**
- All files: 181 files, 116,337 LOC
- Already done (32 direct-name matches): 26,540 LOC
- Excluded (inflation 4 + credit 3 + marketmodels 6 + commodity 1 + infra 6): 20 files, 10,513 LOC
- Phase 5 scope (129 files): 78,346 LOC (+ 938 benchmark = 79,284)

---

## 2.2 Java current state — what is already covered

The 32 "DONE" files (direct-name match) represent 26,540 LOC of C++ already faithfully ported:

| Subsystem | Files | Notes |
|-----------|------:|-------|
| Inflation | `inflation`, `inflationcapfloor`, `inflationvolatility` | Phases 2t–2v |
| Credit | `creditdefaultswap`, `defaultprobabilitycurves` | Phases 3a–3g |
| Gaussian1D / GSR | `markovfunctional`, `gsr`, `normalclvmodel`, `squarerootclvmodel` | Phase 2j / 4j |
| Yield curves | `piecewiseyieldcurve`, `termstructures` | Phase 2 |
| Options | `americanoption`, `europeanoption`, `dividendoption`, `barrieroption`, `doublebarrieroption`, `extendedtrees`, `andreasenhugevolatilityinterpl` | Phases 2m/2n/4e |
| FI basics | `floatfloatswap`, `instruments`, `integrals`, `mersennetwister` | Phases 1–2 |
| Time/schedule | `dates`, `period`, `schedule`, `daycounters`, `currency`, `money`, `curvestates`, `quotes` | Phases 1–2 |
| GSR | `array`, `normalclvmodel` | Phases 2n/4j |

**Partially covered (different Java test name):** `calendars.cpp` (30 per-country `CalendarTest` subclasses), `distributions.cpp` (9 distribution tests), `interpolations.cpp` (`InterpolationTest` + 10 focused tests), `bonds.cpp` (`BondTest`), `solvers.cpp` (5 solver tests), `stats.cpp` (`StatisticsTest`). These have meaningful coverage but not all tests from the C++ file are represented; Phase 5 must audit and fill gaps.

---

## 3. Phase 5 Gap Matrix

| Phase 5 sub-phase | Files | C++ LOC | Theme |
|-------------------|------:|--------:|-------|
| 5a | 19 | 3,482 | Core infra: lazy/observable/operators/settings + small math |
| 5b | 11 | 6,100 | Numerics: matrices/statistics/distributions/optimizers |
| 5c | 5 | 4,645 | Calendar/time: calendars + futures/SOFR indexes |
| 5d | 18 | 9,816 | Bond instruments: govts/corp/callables/OIS/cashflows |
| 5e | 11 | 10,466 | Rates instruments: assetswap/caps/CMS/digital coupons |
| 5f | 12 | 7,273 | Rate vol & swaptions: swaption cubes/optionletstripper/LMM |
| 5g | 15 | 7,848 | Vol infrastructure: Black formula/SABR/interpolations |
| 5h | 9 | 10,076 | Stochastic vol: Heston/Bates/HW+H/jumps/VG |
| 5i | 6 | 6,920 | Equity analytics: Asian/quanto/digital/lookback/Margrabe |
| 5j | 8 | 5,903 | FD engine test suite: fdheston/fdmlinearop/vpp/swing/SABR |
| 5k | 15 | 5,817 | Exotic options + barrier: basket/FX/clique/compound/multi-asset |
| **TOTAL** | **129** | **78,346** | |

(Benchmark: `quantlibbenchmark.cpp` 938 LOC excluded — not a functional test; belongs to a performance-benchmarking sub-phase if ever needed.)

---

## 4. Detailed Phase Descriptions

### Phase 5a — Core Infrastructure & Small Math (19 files, ~3,500 LOC)

**C++ files:**
`settings.cpp` (66), `operators.cpp` (170), `lazyobject.cpp` (277), `observable.cpp` (419),
`prices.cpp` (189), `rounding.cpp` (154), `timegrid.cpp` (166), `timeseries.cpp` (194),
`interestrates.cpp` (194), `solvers.cpp` (228), `ode.cpp` (217), `pathgenerator.cpp` (289),
`brownianbridge.cpp` (248), `autocovariances.cpp` (92), `covariance.cpp` (267),
`fastfouriertransform.cpp` (110), `zigguratgaussian.cpp` (69), `rngtraits.cpp` (133),
`functions.cpp` (346)

**Rationale:** Foundational patterns — observer, lazy evaluation, operator algebra — tested before
anything else. Most of these tests are very shallow (unit-level) and many have *partial* Java
coverage already (e.g., solvers has 5 solver `*Test.java` classes). Phase 5a audits the gap and
fills missing test cases.

**Java packages:** `org.jquantlib.math`, `math.solvers`, `math.ode`, `math.integrals` (extend),
`time`, `processes` (BrownianBridge path generation), `patterns` (Observable/LazyObject)

**Key dependencies:** None beyond JQuant core (Phase 1–2).

**Design concern D1:** `settings.cpp` tests global `Settings` object thread-safety; Java `Settings`
is a thread-local singleton — semantics differ. Port only the functional (not thread-safety) tests.

---

### Phase 5b — Numerics & Statistics (11 files, ~6,100 LOC)

**C++ files:**
`matrices.cpp` (1,006), `lowdiscrepancysequences.cpp` (1,198), `distributions.cpp` (734),
`stats.cpp` (382), `riskstats.cpp` (612), `gaussianquadratures.cpp` (463),
`optimizers.cpp` (545), `numericaldifferentiation.cpp` (304),
`linearleastsquaresregression.cpp` (247), `xoshiro256starstar.cpp` (263)

**Rationale:** Linear algebra, MC sequence quality, statistical estimators, and quadrature.
Many of these already have partial Java test classes (`StatisticsTest`, 9 `*Distribution` tests);
Phase 5b audits and fills remaining test cases from the C++ suite.

**Java packages:** `org.jquantlib.math.matrixtransformations`, `math.statistics`,
`math.integrals` (Gaussian quadratures), `math.randomnumbers` (LDS sequences, xoshiro),
`math.optimization` (extend with numerical-differentiation tests)

**Key dependencies:** Phase 5a (observable/lazy infrastructure).

**Design concern D2:** `lowdiscrepancysequences.cpp` tests Sobol, Halton, Faure — Java's LDS
classes exist but dimensional independence requires exact seed matching with C++. Use
probe-harness to validate sequence equality.

---

### Phase 5c — Calendar / Time / Futures Indexes (5 files, ~4,600 LOC)

**C++ files:**
`calendars.cpp` (3,894), `businessdayconventions.cpp` (129), `indexes.cpp` (224),
`sofrfutures.cpp` (221), `perpetualfutures.cpp` (177)

**Rationale:** `calendars.cpp` at 3,894 LOC is the largest single file in this phase; it exercises
all 65+ country/exchange calendars. Java already has ~30 per-country `*CalendarTest` classes. Phase
5c consolidates into a single `CalendarsTest` (mirroring C++ structure) and adds any missing
calendar assertions. `indexes.cpp` exercises IBOR/overnight index construction. SOFR and perpetual
futures are newer instruments.

**Java packages:** `org.jquantlib.testsuite.calendars` (extend existing), `time.calendars`,
`indexes`

**Key dependencies:** Phase 1–2 calendar infrastructure.

**Design concern D3:** `businessdayconventions.cpp` uses a parametric test table (129 LOC, 90+
cases). Java JUnit 5 `@MethodSource` or a data-driven approach recommended.

---

### Phase 5d — Bond Instruments (18 files, ~9,800 LOC)

**C++ files:**
`bonds.cpp` (1,896), `overnightindexedcoupon.cpp` (1,130), `overnightindexedswap.cpp` (1,098),
`callablebonds.cpp` (1,050), `catbonds.cpp` (665), `cashflows.cpp` (623),
`convertiblebonds.cpp` (445), `exchangerate.cpp` (383), `fittedbonddiscountcurve.cpp` (339),
`zerocouponswap.cpp` (311), `equitytotalreturnswap.cpp` (305), `multipleresetscoupons.cpp` (288),
`amortizingbond.cpp` (285), `equityindex.cpp` (283), `equitycashflow.cpp` (282),
`multipleresetsswap.cpp` (159), `bondforward.cpp` (154), `forwardrateagreement.cpp` (120)

**Rationale:** The bond instrument suite. `bonds.cpp` (1,896 LOC) and the OIS coupon files are the
largest. Java already has `BondTest`, `ConvertibleBondTest`, `CallableBondTest` (partial from
experimental); Phase 5d provides faithful full-suite equivalents. Equity-linked instruments
(equitycashflow, equityindex, equitytotalreturnswap) are newer QuantLib instruments.

**Java packages:** `org.jquantlib.instruments` (extend), `cashflows` (extend),
`pricingengines.bond` (extend)

**Key dependencies:** Phase 5c (calendar/schedule), Phase 2 yield-curve infrastructure.

**Design concern D4:** `convertiblebonds.cpp` tests `BinomialConvertibleEngine` which exists in
v1.42.1 (Java had a stub deleted in Phase 2m). Verify re-implementation against v1.42.1 before
Phase 5d begins.

---

### Phase 5e — Rates Instruments: Caps / Swaps / Coupons (11 files, ~10,500 LOC)

**C++ files:**
`assetswap.cpp` (4,409), `capfloor.cpp` (890), `capflooredcoupon.cpp` (545), `cms.cpp` (464),
`cms_normal.cpp` (499), `cmsspread.cpp` (351), `digitalcoupon.cpp` (1,099), `swap.cpp` (543),
`rangeaccrual.cpp` (747), `piecewisezerospreadedtermstructure.cpp` (474)

**Rationale:** Interest rate instrument test suite. `assetswap.cpp` at 4,409 LOC is the single
largest non-marketmodel C++ test file; it drives asset-swap spread calculations and is a critical
multi-curve test. CMS (constant maturity) and digital coupon suites exercise the coupon pricer
hierarchy. Range accrual tests LIBOR-in-arrears and range-accrual coupons.

**Java packages:** `org.jquantlib.instruments` (swaps, caps), `cashflows.coupons` (CMS, digital,
range-accrual), `termstructures.volatilities` (cap/floor vol surfaces)

**Key dependencies:** Phase 5c (calendars), Phase 5d (cashflows), Phase 2j (cap floor engines).

**Design concern D5:** `assetswap.cpp` exercises settlement/clean-dirty price dynamics across
many bond types; requires full bond + yield curve infra from Phase 5d to be complete first.
Suggest implementing Phase 5d before or concurrent with 5e.

---

### Phase 5f — Rate Volatility & Swaptions (12 files, ~7,300 LOC)

**C++ files:**
`swaption.cpp` (1,197), `swaptionvolatilitycube.cpp` (1,054), `swaptionvolatilitymatrix.cpp` (364),
`bermudanswaption.cpp` (693), `optionletstripper.cpp` (991), `shortratemodels.cpp` (445),
`libormarketmodel.cpp` (465), `libormarketmodelprocess.cpp` (327),
`swapforwardmappings.cpp` (445), `ultimateforwardtermstructure.cpp` (340),
`basisswapratehelpers.cpp` (240), `basismodels.cpp` (402), `crosscurrencyratehelpers.cpp` (755)

**Rationale:** The swaption vol surface and calibration test suite. `swaptionvolatilitycube.cpp`
at 1,054 LOC is the most demanding — it tests SABR vol cube fitting, smile interpolation, and
spreads. `optionletstripper.cpp` tests bootstrapping caplet vol surfaces. LMM process and
model tests. Ultimate Forward Rate (UFR) is a regulatory curve extension.

**Java packages:** `org.jquantlib.termstructures.volatilities.swaption`,
`termstructures.volatilities.optionlet`, `pricingengines.swaption` (extend),
`model.shortrate` (extend), `indexes` (LIBOR)

**Key dependencies:** Phase 5e (cap/floor instruments), Phase 2j/5g (vol infrastructure).

**Design concern D6:** `swaptionvolatilitycube.cpp` requires SABR cube fitting; Java SABR
interpolation (Phase 2r + experimental Phase 4f) must be verified as prereq.

---

### Phase 5g — Vol Infrastructure: Black / SABR / Interpolation (15 files, ~7,800 LOC)

**C++ files:**
`blackcalculator.cpp` (485), `blackdeltacalculator.cpp` (677), `blackformula.cpp` (447),
`blackvolsurfacedelta.cpp` (298), `bacheliercalculator.cpp` (590),
`interpolations.cpp` (2,888), `interpolatedsmilesection.cpp` (215),
`piecewiseblackvariancesurface.cpp` (1,111), `garch.cpp` (187), `gjrgarchmodel.cpp` (310),
`noarbsabr.cpp` (125), `svivolatility.cpp` (70), `zabr.cpp` (97),
`varianceswaps.cpp` (294), `volatilitymodels.cpp` (54)

**Rationale:** Black / Bachelier pricing formula correctness (foundational), vol surface
construction, and SABR/SVI/ZABR smoke tests. `interpolations.cpp` at 2,888 LOC is the largest
here — Java already has 10+ `*InterpolationTest` classes covering partial ground. Phase 5g
adds a consolidated `InterpolationsTest` matching C++ structure. GARCH and GJR-GARCH volatility
model tests.

**Java packages:** `org.jquantlib.pricingengines.BlackCalculator`,
`termstructures.volatilities.equityfx`, `math.interpolations` (extend)

**Key dependencies:** Phase 4f (experimental SABR/SVI/ZABR for smoke tests).

**Design concern D7:** `blackdeltacalculator.cpp` (677 LOC) tests FX delta conventions
(premium-adjusted, unadjusted, forward, spot) — the Java `BlackDeltaCalculator` exists in
`experimental/fx/` (Phase 4l). Verify experimental port completeness before Phase 5g.

---

### Phase 5h — Stochastic Volatility Cluster (9 files, ~10,100 LOC)

**C++ files:**
`hestonmodel.cpp` (3,469), `hestonslvmodel.cpp` (2,686), `hybridhestonhullwhiteprocess.cpp` (1,419),
`batesmodel.cpp` (513), `riskneutraldensitycalculator.cpp` (784),
`jumpdiffusion.cpp` (524), `mclongstaffschwartzengine.cpp` (312),
`variancegamma.cpp` (251), `varianceoption.cpp` (118)

**Rationale:** The Heston stochastic volatility cluster. `hestonmodel.cpp` at 3,469 LOC is the
second-largest non-calendar test file; it tests Heston analytic, FD, and calibration thoroughly.
`hestonslvmodel.cpp` tests stochastic-local vol (SLV) — depends on the AndreasenHuge infrastructure
(Phase 2m). `hybridhestonhullwhiteprocess.cpp` tests the hybrid SV/IR model. Bates adds jumps to
Heston. Variance Gamma and Risk-Neutral Density calculator round out the group.

**Java packages:** `org.jquantlib.model.stochasticvolatility`, `processes` (HestonSLV),
`pricingengines.vanilla` (extend AnalyticHestonEngine)

**Key dependencies:** Phase 4j (CLV models), Phase 2m (SLV engine), Phase 4c (VG experimental).

**Design concern D8:** `hestonmodel.cpp` has very slow calibration tests (multi-minute in C++).
These must be tagged `@SlowTest` in Java and excluded from CI fast-lane. Reference values are
pre-computed via probe-harness.

**Design concern D9:** `hestonslvmodel.cpp` requires FdmHestonSolver (Phase 2m) and
AndreasenHuge local vol surface (Phase 2m) — both exist in Java. Verify complete before Phase 5h.

---

### Phase 5i — Equity Analytics & Exotic Options (6 files, ~6,900 LOC)

**C++ files:**
`asianoptions.cpp` (2,822), `quantooption.cpp` (1,345), `forwardoption.cpp` (805),
`digitaloption.cpp` (733), `lookbackoptions.cpp` (662), `margrabeoption.cpp` (553)

**Rationale:** Analytic equity option pricing extensions. `asianoptions.cpp` at 2,822 LOC tests
geometric/arithmetic Asian options across analytic, MC, and FD engines — it is the primary test
for the Asian option pricing infrastructure. `quantooption.cpp` tests quanto option pricing.
Forward-start, digital, lookback, and spread (Margrabe) options complete the set.

**Java packages:** `org.jquantlib.pricingengines.asian` (extend), `pricingengines.vanilla` (extend),
`instruments` (ForwardOption, LookbackOption)

**Key dependencies:** Phase 5g (Black/Bachelier formulas), Phase 2m (FD vanilla).

**Design concern D10:** `asianoptions.cpp` exercises `AnalyticDiscreteGeometricAveragePriceAsian`,
`AnalyticContinuousGeometricAveragePriceAsian`, and MC Asian engines — Java stubs exist in
`experimental/asian/` (Phase 4a). Verify Phase 4a ports are complete before Phase 5i.

---

### Phase 5j — FD Engine Test Suite (8 files, ~5,900 LOC)

**C++ files:**
`fdmlinearop.cpp` (1,635), `fdheston.cpp` (1,056), `nthorderderivativeop.cpp` (845),
`vpp.cpp` (943), `swingoption.cpp` (587), `fdsabr.cpp` (512),
`fdcev.cpp` (208), `fdcir.cpp` (117)

**Rationale:** The FD framework and model test suite for the *non-experimental* FD engines.
`fdmlinearop.cpp` at 1,635 LOC tests the FD linear operator infrastructure (FdmHestonOp,
FdmBlackScholesOp, ADI schemes). `fdheston.cpp` tests FdHestonVanillaEngine (already partially
tested in Phase 2m Java). VPP (943 LOC) and swing (587 LOC) exercise energy option FD engines
from `experimental/finitedifferences/`. FdSABR and FdCEV/CIR are analytic FD model tests.

**Java packages:** `org.jquantlib.methods.finitedifferences` (extend),
`pricingengines.vanilla` (extend FdHeston), `experimental.finitedifferences` (extend VPP/swing)

**Key dependencies:** Phase 2m (FD framework), Phase 4n (experimental FD processes/engines).

**Design concern D11:** `fdmlinearop.cpp` has a suite of 15+ FD operator tests that exercise ADI
scheme correctness numerically. These are tolerance-sensitive; use LOOSE tier (`1e-8` rel) for
ADI scheme comparisons, TIGHT tier for eigenvalue/operator-norm checks.

---

### Phase 5k — Exotic Options + Barrier + Basket (15 files, ~5,800 LOC)

**C++ files:**
`basketoption.cpp` (2,578), `fxforward.cpp` (454), `partialtimebarrieroption.cpp` (328),
`doublebinaryoption.cpp` (330), `cliquetoption.cpp` (356), `compoundoption.cpp` (346),
`softbarrieroption.cpp` (208), `binaryoption.cpp` (256), `chooseroption.cpp` (163),
`extensibleoptions.cpp` (156), `himalayaoption.cpp` (135), `everestoption.cpp` (138),
`pagodaoption.cpp` (134), `twoassetbarrieroption.cpp` (144), `twoassetcorrelationoption.cpp` (91)

**Rationale:** Multi-asset, basket, barrier, and niche exotic options. `basketoption.cpp` at
2,578 LOC exercises Kirk spread approximation, Stulz two-asset, Levy approximation, MC basket
engines. The remaining files are smaller but collectively cover the full exotic option space.
Himalaya, Everest, Pagoda are MC mountain-range options (from `experimental/exoticoptions/`).

**Java packages:** `org.jquantlib.pricingengines.basket`, `instruments.basketoption`,
`pricingengines.vanilla` (binary, chooser, compound), `experimental.exoticoptions` (extend)

**Key dependencies:** Phase 4h (experimental exotics), Phase 4e (double barrier), Phase 2m (MC).

**Design concern D12:** `basketoption.cpp` tests `MCEuropeanBasketEngine` — Java MC framework
requires `MonteCarloModel` + payoff visitor pattern. Port basket MC engine stub before Phase 5k
if not already present.

---

## 5. Phase Ordering Rationale

```
Phase 5a  (infra/math foundations)         — start here; widest prereq for all others
Phase 5b  (numerics/statistics)            — depends on 5a (lazy/observable)
Phase 5c  (calendars/time/indexes)         — standalone; best run early
Phase 5d  (bond instruments)               — depends 5a, 5c; many downstream deps
Phase 5g  (vol infrastructure)             — depends 5b; prereq for 5e, 5f, 5h
Phase 5e  (rates instruments)              — depends 5c, 5d, 5g
Phase 5f  (rate vol + swaptions)           — depends 5e, 5g
Phase 5h  (stochastic vol cluster)         — depends 5g; Phase 2m prereqs already done
Phase 5i  (equity analytics)               — depends 5g, 5h
Phase 5j  (FD engine suite)                — depends Phase 2m + Phase 4n (already done)
Phase 5k  (exotic options + barrier)       — depends 5h, 5i, Phase 4e/4h (already done)
```

Phases 5a–5c can run in parallel (no mutual dependency). Phase 5g can start concurrent with 5d.
Phase 5j can start any time after Phase 4n is confirmed complete.

---

## 6. Coverage After Phase 5

| Category | C++ files | After Phase 5 |
|----------|----------:|------------:|
| Covered (32 direct-match) | 32 | DONE |
| Excluded (inflation/credit/marketmodels/commodity/infra) | 20 | Other phases |
| Phase 5 ported | 129 | ALL DONE |
| Total test-suite | 181 | 181/181 |

After Phase 5: **100% of the QuantLib v1.42.1 `test-suite/*.cpp` files have Java equivalents**
(modulo `quantlibbenchmark.cpp` which is a performance runner, not a functional test).

---

## 7. Partially-Covered Files: Audit Plan

The following C++ test files have some Java test coverage but not a complete faithful equivalent.
Phase 5 should audit these and add missing cases (not replace existing Java tests):

| C++ file | Existing Java coverage | Gap |
|----------|----------------------|-----|
| `calendars.cpp` (3,894 LOC) | 30 per-country `*CalendarTest` classes | Joint calendar tests, non-business-day advance rules, calendar math |
| `distributions.cpp` (734 LOC) | 9 `*Distribution*Test` classes | Bivariate normal, non-central chi-squared edge cases, inverse CDF tails |
| `interpolations.cpp` (2,888 LOC) | `InterpolationTest` + 10 focused tests | Natural cubic, monotone convex, piecewise, 2D bilinear grid tests |
| `bonds.cpp` (1,896 LOC) | `BondTest` | Duration/convexity, yield/price iteration, settlement rules |
| `solvers.cpp` (228 LOC) | 5 `*Test` solver classes | Accuracy/convergence tests comparing methods |
| `stats.cpp` (382 LOC) | `StatisticsTest` | Convergence statistics, incremental update tests |

---

## 8. Known Design Concerns

### D1. Settings thread-safety (Phase 5a)
`settings.cpp` tests thread-safety of the global `Settings::evaluationDate`. Java `Settings` is
`ThreadLocal`-backed; thread-safety tests are C++-specific. Port functional tests only;
annotate `@Ignore` on C++ thread-safety cases with justification.

### D2. Low-discrepancy sequence seeding (Phase 5b)
`lowdiscrepancysequences.cpp` uses fixed Sobol/Halton seed sequences. Java and C++ must generate
identical sequences — use probe-harness to validate first 1000 draws from each dimension.

### D3. Business day convention table (Phase 5c)
129 LOC covering ~90 parametric cases. Use JUnit 5 `@MethodSource` with a static provider method
to mirror C++ `boost::unit_test::data::make` parametrization.

### D4. ConvertibleBond binomial engine (Phase 5d)
`convertiblebonds.cpp` exercises `BinomialConvertibleEngine`. The Java stub was removed in Phase
2m. Re-implement before Phase 5d; cross-validate all dilution factor / coupon cases.

### D5. Asset swap ordering (Phase 5e)
`assetswap.cpp` (4,409 LOC) depends on the full bond + yield curve infrastructure. Must run
after Phase 5d is complete. Consider splitting Phase 5e into two Java test classes:
`AssetSwapTest` (spread/YTM) and `AssetSwapPricingTest` (engine variants).

### D6. SABR vol cube prereq (Phase 5f)
`swaptionvolatilitycube.cpp` drives SABR cube fitting. Phase 2r SABR interpolation and Phase 4f
experimental SABR vol surface must be verified complete before Phase 5f.

### D7. FX delta calculator prereq (Phase 5g)
`blackdeltacalculator.cpp` exercises `BlackDeltaCalculator` (experimental/fx). Verify Phase 4l
ports this class before Phase 5g.

### D8. Slow Heston calibration (Phase 5h)
Heston model calibration tests in `hestonmodel.cpp` are CPU-intensive (> 60 seconds in C++).
Tag Java equivalents `@Tag("slow")` and exclude from default Maven surefire run. Gate on
`mvn -Pslowtest test`.

### D9. HestonSLV prereqs (Phase 5h)
`hestonslvmodel.cpp` requires both the AndreasenHuge local vol surface (Phase 2m) and the
FdmHestonSolver (Phase 2m). Confirm both are Java-complete with green tests before Phase 5h.

### D10. Asian option experimental prereqs (Phase 5i)
`asianoptions.cpp` exercises engines from `experimental/asian/`. Phase 4a must be complete.

### D11. ADI scheme tolerance (Phase 5j)
FD ADI scheme tests in `fdmlinearop.cpp` use `LOOSE` tier (`1e-8` rel) for accumulated
numerical discretization errors. Document tolerance choice inline per project rules.

### D12. MC basket engine Java stub (Phase 5k)
`basketoption.cpp` requires `MCEuropeanBasketEngine`. Check if this exists in Java; if not, add
as a prerequisite stub before Phase 5k implementation begins.

---

## 9. Quality Gates (same as all prior phases)

- Every sub-phase: `mvn -pl jquantlib test` green before commit.
- TDD: cross-validate each Java test against C++ v1.42.1 via `migration-harness/` probe scripts.
- Tolerance tiers: exact / tight `1e-12` rel `1e-14` abs / loose `1e-8` rel.
  Exceptions require inline justification (see D8 for `@SlowTest` Heston).
- One commit per logical unit (test class or cluster of related small tests).
- No `Co-authored-by: Claude` trailer; `-s` Signed-off-by only.
- Slow tests (`@Tag("slow")`) excluded from `mvn -pl jquantlib test` default profile.

---

## 10. Open Questions for Controller

1. **Phase 5 start gate:** Phase 5a/5b/5c can start as soon as Phase 4o (commodities) is tagged
   or in parallel with Phase 4 completion. Which is preferred?
2. **`quantlibbenchmark.cpp` (938 LOC):** This is a performance micro-benchmark runner, not a
   functional test. Include in Phase 5 as a `JMH`-based Java benchmark, or defer indefinitely?
3. **Partially-covered files strategy:** For files like `calendars.cpp` where Java has 30
   per-country classes, should Phase 5c produce a single consolidated `CalendarsTest` class
   (mirroring C++) or just gap-fill into existing per-country classes?
4. **SlowTest profile:** Heston calibration (D8) and possibly LDS sequence exhaustive tests
   need a CI-exclude profile. Confirm `-Pslowtest` Maven profile approach is acceptable.
5. **Phase 5 labeling:** Should these be labeled Phase 5a–5k (11 sub-phases) or use a different
   numbering scheme to distinguish from Phase 4's lettered scheme?

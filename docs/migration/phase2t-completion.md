# Phase 2t Completion — First Test-Suite Phase (inflation.cpp)

**Status:** complete (autonomous mode — eleventh autonomous phase; **first test-suite phase under rigor directive 2026-05-08**)
**Tag:** `jquantlib-phase2t-complete` @ `37e88b8`
**Predecessor:** `jquantlib-phase2s-complete` @ `5d7a5ea`
**Plan + Design:** `docs/migration/phase2t-{design,plan}.md`

## Final state

| Metric | Phase 2s tip | Phase 2t tip | Δ |
|--------|--------------|--------------|----|
| Tests | 888/0/0/22 | 919/0/0/40 | +31 (13 active + 18 @Ignore'd) |
| Scanner WIP | 0 | 0 | unchanged |
| Test-suite ports | 0 | inflation.cpp (1/7 inflation files) | first |
| Inflation test-suite coverage | 0% (5,253 LOC C++) | 44% (2,323 / 5,253 LOC) | +44% |

## What landed (4 commits)

| Commit | Description |
|--------|-------------|
| `ecd7e4b` | Phase 2t design + plan |
| `bde77d5` | **A.1:** InflationCommonVars test fixture (281 LOC). Mirrors C++ inflation.cpp's CommonVars struct + makeHelpers utility. |
| `af06bca` | **Align prereq:** `align(termstructures.InflationTermStructure)` — inflationPeriod sub-annual formula bug. Java was using `3*(month-1)/3+1` instead of C++ `month - (month-1) % nMonths`. **Pre-existing port bug since original Java port** — only Monthly was widely exercised, so divergence went undetected until Phase 2t's testPeriod port caught it. Generalized to handle EveryFourthMonth and Bimonthly per C++. |
| `37e88b8` | **A.2:** InflationTest (988 LOC). 25 C++ BOOST_AUTO_TEST_CASE → 31 Java @Test methods (25 direct + 6 InflationCommonVars helper smoke tests). 13 active passing + 18 @Ignore'd with documented Phase 2u/2v/2x rationale per binding rigor directive (no silent skips). |

## Active test breakdown (13)

- `testPeriod` — every month × frequency 1950-2050, EXACT
- `testInterpolatedZeroTermStructure` — full port, EXACT date equality
- `testCpiFlatInterpolation` / `testCpiLinearInterpolation` / `testCpiAsIndexInterpolation` — CPI.laggedFixing all 3 modes, LOOSE 1e-6 (matches C++ QL_CHECK_CLOSE 1e-8 relative)
- `testCpiYoYQuotedFlatInterpolation` / `testCpiYoYQuotedLinearInterpolation` — CPI.laggedYoYRate, TIGHT 1e-10
- `testNotifications` — Observer chain through ZeroInflationCashFlow, EXACT
- `testSeasonalityCorrection` — setSeasonality/hasSeasonality contract smoke (full assertion block deferred to Phase 2x pending lastFixingDate align)
- 4 helper-data smoke tests on InflationCommonVars

## @Ignore'd test backlog (18, with documented rationale)

### Phase 2u/2v — missing classes (6 tests blocked)
- AUCPI, UKHICP, USCPI, EUHICPXT inflation index classes (4 tests)
- GlobalBootstrap template for inflation curves (1 test)
- PiecewiseZeroInflationCurve lazy-baseDate Supplier ctor (1 test)

### Phase 2x — small aligns (12 tests blocked, ~85 LOC total fix)

1. **UKRPI/EUHICP/YYUKRPI/YYEUHICP availabilityLag** → 1 month per C++ (Java currently 2/3M); fix ~4 lines
2. **ZeroInflationIndex.lastFixingDate()** method, mirrors C++ inflationindex.cpp:186-191; ~10 lines
3. **YoYInflationIndex(ZeroInflationIndex underlying)** ratio constructor; ~30 lines
4. **ZeroCouponInflationSwapHelper** dual-date + CPI::Linear/Flat overloads; ~30 lines
5. **YoYInflationIndex(ZeroInflationIndex underlying, boolean interpolated)** deprecated overload; small

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A26 (cross-cutting align prereq)** | A.2 | InflationTermStructure.inflationPeriod sub-annual bug caught by testPeriod port. Bundled as separate align prereq commit `af06bca`. |
| **A29 (test exercises class/method that diverges from C++)** | A.2 | 12 tests gated on small Java↔C++ aligns; documented as Phase 2x backlog rather than silently skipping. Per rigor directive. |

A1-A25, A27, A28, A30 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2T-1** | Test-suite ports use existing `Tolerance` + assertion framework | No new test infra needed |
| **P2T-2** | C++ `boost::shared_ptr` test refs → Java regular references with `Handle` where needed | Idiomatic Java; no semantic change |
| **P2T-3** | Tests requiring missing classes are @Ignore'd with explicit "Phase 2u/2v/2x: needs X" — never silently skipped | Per binding rigor directive (`feedback_test_suite_rigor.md`); Phase 2u/2v/2x backlog is now a precise catalogue |
| **P2T-4** | Pre-existing port bugs caught by test-suite ports get separate align prereq commit | Same project rule as Phase 2c-2s; keeps test-port commit clean |
| **P2T-5** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 2u+ seed list

### Phase 2u — Inflation cap/floor + cpiswap test ports
- `inflationcapfloor.cpp` (526 LOC C++) — port to InflationCapFloorTest.java
- `inflationcapflooredcoupon.cpp` (784 LOC) — port to CapFlooredInflationCouponTest.java (Phase 2q D.1 production already done)
- `inflationcpiswap.cpp` (495 LOC) — port to CPISwapTest.java (Phase 2r C.1 production already done — current test is smoke only)
- `inflationcpicapfloor.cpp` (434 LOC) — port to CPICapFloorTest.java (Phase 2r production done)

### Phase 2v — Inflation cpibond + volatility test ports + missing core
- `inflationcpibond.cpp` (296 LOC) — requires `CPIBond` instrument port (~400 LOC C++) first
- `inflationvolatility.cpp` (395 LOC) — exercises Phase 2s experimental coverage
- AUCPI, UKHICP, USCPI, EUHICPXT inflation index classes (~50 LOC each)
- GlobalBootstrap template for inflation curves
- PiecewiseZeroInflationCurve lazy-baseDate Supplier ctor

### Phase 2x — Small aligns to unblock Phase 2t @Ignore'd tests (~85 LOC)
- UKRPI/EUHICP availabilityLag align
- ZeroInflationIndex.lastFixingDate()
- YoYInflationIndex(ZeroInflationIndex) constructors (current + deprecated)
- ZeroCouponInflationSwapHelper dual-date + CPI::Linear/Flat overloads

### Phase 3+ subsystem ports
- termstructures/credit/ + tests (~6K LOC + tests)
- models/marketmodels/ + tests (~25-30K + tests)
- experimental/ (non-inflation, non-credit) + tests
- Remaining test-suite files (~70+ cpp files in test-suite/ beyond inflation)

## Out-of-scope (explicit, deferred)

- All Phase 2u+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source

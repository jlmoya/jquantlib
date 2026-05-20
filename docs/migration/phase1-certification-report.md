# Phase 1 Certification Audit Report

**Date:** 2026-05-20
**Auditor:** Claude (controller) under direct user request
**Tip audited:** `b66b8ef4` (= `origin/main`)
**Last completion claim:** `phase5e5b-CFC-d-final-completion.md`, tag `jquantlib-phase5e5b-CFC-d-final` @ `ba85c70b`
**Audit scope:** Full migration arc from inception (Phase 1 on 2026-04-22) through current tip — 50+ sub-phases, ~2,000+ commits — assessed against the project's own binding standards (CLAUDE.md, design docs §§2/4/7, persistent memory).
**User mandate (verbatim):** "validate everything we have from code quality to tests, validate if test results are up to par with the standards that we set when we began the project ... validate we have migrated everything and not left out a single bit ... decide if I can move on to the next phase knowing I did it all for this one and nothing was postponed or otherwise left for future me."

---

## Executive summary — **NO-GO**

| # | Dimension | Status | One-line headline |
|---|-----------|--------|-------------------|
| D1 | Test execution integrity | **YELLOW** | Suite runs clean: 3010/0/0/1 in 17m23s, BUILD SUCCESS — but contains an undocumented slow-test gate gap of ~45 tests |
| D2 | Stub coverage / silent gaps | **GREEN** | 4 in-tree `"not implemented"` throws are all exact v1.42.1 parity; original Phase 1 carve-outs all resolved |
| D3 | Tolerance + `@Ignore` hygiene | **AMBER** | `@Ignore` count = 0 ✓; 2 ports use loose tolerance without inline justification |
| D4 | Code quality (spot check) | **GREEN** | Recently-touched classes carry proper headers, v1.42.1 cross-references with pinned commit, idiomatic Java |
| **D5** | **C++ test-suite parity** | **RED** | **280 of 1,272 C++ test cases have no Java equivalent by name — 23% gap. Worst: `schedule` 28 missing (0% covered), `calendars` 21 missing, `piecewiseyieldcurve` 21, `americanoption` 21, `marketmodel` 17, `daycounters` 17.** |
| D6 | Documentation trail | **AMBER** | README badges show stale tag + stale test count; phase docs otherwise consistent |

**Recommendation: NO-GO for Phase 2 pending D5 remediation.** D5 directly contradicts the binding project standard "C++ test-suite must be ported with full rigor — every C++ test gets a faithful Java equivalent" (persistent memory, 2026-05-08 directive). 280 missing tests is the precise "left for future me" outcome the user explicitly asked this audit to prevent.

D1, D3, D6 are tractable in hours; D5 is the real blocker (estimated 1–3 days of mechanical porting work, dispatchable in parallel).

---

## D1 — Test execution integrity

### State at audit close

- **Suite execution (clean):** `mvn -pl ../jquantlib clean test -Dquickcheck.skip=true -fae` from clean.
- **Result: `Tests run: 3010, Failures: 0, Errors: 0, Skipped: 1` — BUILD SUCCESS.**
- **Wall time: 17 min 23 s** (07:30 → 07:47 local).
- **The 1 skip** = `HestonSLVModelTest.testMonteCarloCalibration`, which is the only Java test currently mirroring C++ `if_speed(Fast)` via `Assume.assumeTrue`.
- **LMM `testCalibration` ran to completion** in **512.022 s** as part of the suite, 0 failures (this confirms it is slow-but-valid, not a hang).
- Surefire log contains a high volume of `ERROR:` lines through the run — every one inspected was an intentional negative-test guard (`QL_REQUIRE`-equivalent assertion firing on invalid inputs in a test that expects the throw), notably from `GFunctionWithShifts.calibrationOfShift`, `bachelierBlackFormulaImpliedVolExact`, CDS settlement-date validation, MarketModel input validation. None contributed to failures/errors.

### Finding D1-1: slow-test gate parity gap (YELLOW)

C++ v1.42.1 marks **40 test cases with `*precondition(if_speed(Fast))`** and **~6 with `*precondition(if_speed(Slow))`** — both expressing "skip in default fast-mode runs; opt-in via Boost test-runner speed flag." Java mirrors exactly **1** of these (`HestonSLVModelTest.testMonteCarloCalibration`).

**Implication:** ~45 Java tests currently run unconditionally that C++ explicitly skips by default. LMM `testCalibration` is one of them — that's why a 510s run lands in the default `mvn test`.

**Per the policy you confirmed during this session ("mirror C++ behavior, if it is a slow-test gate"), every C++ `if_speed(Fast|Slow)` should have a corresponding Java `Assume.assumeTrue(System.getProperty("ql.slowTests") != null)`.**

Ungated Java tests (confirmed by name match against C++ gated tests):

| C++ source | Test | Java file |
|---|---|---|
| americanoption.cpp:557 | testFdShoutGreeks | instruments/AmericanOptionTest.java |
| asianoptions.cpp:638 | testMCDiscreteGeometricAveragePriceHeston | instruments/AsianOptionsAdditionalTest.java |
| asianoptions.cpp:677 | testMCDiscreteArithmeticAveragePrice | instruments/AsianOptionsAdditionalTest.java |
| basketoption.cpp:754 (suite gate) | (all 3 American tests in BasketOptionTest.java) | instruments/BasketOptionTest.java |
| bermudanswaption.cpp:239 | testCachedG2Values | instruments/BermudanSwaptionTest.java |
| dividendoption.cpp:722 | testFdEuropeanGreeks | instruments/DividendOptionTest.java |
| fdheston.cpp:199 | testFdmHestonBarrierVsBlackScholes | methods/finitedifferences/FdHestonTest.java |
| fdheston.cpp:614 | testFdmHestonConvergence | methods/finitedifferences/FdHestonTest.java |
| fdmlinearop.cpp:1083 | testFdmHestonHullWhiteOp | methods/finitedifferences/FdmLinearOpTest.java |
| forwardoption.cpp:702 | testHestonAnalyticalVsMCPrices | instruments/ForwardOptionTest.java |
| gjrgarchmodel.cpp:200 | testDAXCalibration | model/volatility/GjrGarchModelTest.java |
| hestonmodel.cpp:592 | testFdBarrierVsCached | model/equity/HestonModelTest.java |
| hestonmodel.cpp:789 | testKahlJaeckelCase | model/equity/HestonModelTest.java |
| hestonmodel.cpp:941 | testDifferentIntegrals | model/equity/HestonModelTest.java |
| hestonslvmodel.cpp:1197 | testHestonFokkerPlanckFwdEquationLogLVLeverage | model/equity/HestonSLVModelTest.java |
| hestonslvmodel.cpp:1363 | testBlackScholesFokkerPlanckFwdEquationLocalVol | model/equity/HestonSLVModelTest.java |
| hestonslvmodel.cpp:1860 | testMonteCarloVsFdmPricing | model/equity/HestonSLVModelTest.java |
| hestonslvmodel.cpp:2259 | testMoustacheGraph | model/equity/HestonSLVModelTest.java |
| hybridhestonhullwhiteprocess.cpp:1057 | testSpatialDiscretizatinError | processes/HybridHestonHullWhiteProcessTest.java |
| hybridhestonhullwhiteprocess.cpp:810 | testFdmHestonHullWhiteEngine | processes/HybridHestonHullWhiteProcessTest.java |
| interpolations.cpp:1879 | testNoArbSabrInterpolation | math/interpolations/InterpolationsTest.java |
| **libormarketmodel.cpp:228** | **testCalibration** | **model/LiborMarketModelTest.java** ← the ~510s test |
| libormarketmodelprocess.cpp:193 | testMonteCarloCapletPricing | processes/LiborMarketModelProcessTest.java |
| mclongstaffschwartzengine.cpp:124 | testAmericanOption | pricingengines/MCLongstaffSchwartzEngineTest.java |
| nthorderderivativeop.cpp:670 | testHigherOrderHestonOptionPricing | methods/finitedifferences/NthOrderDerivativeOpTest.java |
| riskneutraldensitycalculator.cpp:557 | testBlackScholesWithSkew | methods/finitedifferences/RiskNeutralDensityCalculatorTest.java |
| swaption.cpp:924 | testImpliedVolatilityOis | instruments/SwaptionAdditionalTest.java |
| swingoption.cpp:207 | testExtOUJumpVanillaEngine | experimental/finitedifferences/SwingOptionTest.java |
| swingoption.cpp:342 | testExtOUJumpSwingOption | experimental/finitedifferences/SwingOptionTest.java |

Plus 11 C++ tests where the Java counterpart could not be found by name match (potentially missing — overlaps with D5).

**Remediation:** Add `org.junit.Assume.assumeTrue("test gated behind -Dql.slowTests=1 (mirrors C++ *precondition(if_speed(Fast|Slow)))", System.getProperty("ql.slowTests") != null);` as first statement of each method. Mechanical; ~2–4 hours.

**Net effect on suite:** wall time should drop from ~12–15 min to ~3 min in default mode; LMM 510s test moves to opt-in. Default suite count moves from ~3010/0/0/1 to ~2960/0/0/~50 — matching C++'s default behavior more faithfully.

### Finding D1-2: LMM `testCalibration` was misclassified as a Java defect (resolved)

Earlier in this session the LMM test had been characterized as a possible "hang" or "Java-side convergence bug." Confirmed root cause: it is gated `*precondition(if_speed(Fast))` in C++ (`migration-harness/cpp/quantlib/test-suite/libormarketmodel.cpp:228`) — i.e., **C++ never runs this test in default fast mode either.** The 510s Java runtime is the legitimate cost of the LM calibration loop when actually executed. No Java-side bug. Action: gate it per D1-1.

---

## D2 — Stub coverage / silent gaps — GREEN

### Scanner re-run from clean

```
$ python3 tools/stub-scanner/scan_stubs.py
wrote docs/migration/stub-inventory.json (1 stubs)
wrote docs/migration/worklist.md
  not_implemented: 1
```

The single scanner hit:
- `methods.finitedifferences.operators.FdmBatesOp#toMatrixDecomp` at `FdmBatesOp.java:205` throws `UnsupportedOperationException("not implemented")`.

C++ v1.42.1 source (`ql/methods/finitedifferences/operators/fdmbatesop.cpp`):
```cpp
std::vector<SparseMatrix> FdmBatesOp::toMatrixDecomp() const {
    QL_FAIL("not implemented");
}
```

→ **Java exactly mirrors C++.** This is a v1.42.1-mandated stub (the operator's matrix-decomposition path is intentionally unsupported because Bates operators don't have a closed-form sparse decomposition). Not a Phase 1 defect.

### Two additional v1.42.1-parity stubs (scanner regex gap)

- `VarianceGammaProcess.drift` (`experimental/variancegamma/VarianceGammaProcess.java:98`) — `throw new LibraryException("not implemented yet")`
- `VarianceGammaProcess.diffusion` (line 103) — same.

C++ counterpart (`ql/experimental/variancegamma/variancegammaprocess.cpp`):
```cpp
Real VarianceGammaProcess::drift(Time, Real) const { QL_FAIL("not implemented yet"); }
Real VarianceGammaProcess::diffusion(Time, Real) const { QL_FAIL("not implemented yet"); }
```

→ **Exact parity.** The scanner regex `"(not implemented|not yet implemented)"` doesn't match the C++ phrasing `"not implemented yet"`, so it falsely under-counts these. Documentation issue, not code issue.

### Allowlisted parity stub

- `TreeLattice2D.grid` (`methods/lattices/TreeLattice2D.java:72`) — also exact v1.42.1 parity (`Array grid(Time) const override { QL_FAIL("not implemented"); }`), already documented in `phase1-carveouts.md`.

### Phase 1 carve-out resolution (all 6 from 2026-04-24)

| Carve-out | Resolved at | Verified |
|---|---|---|
| `QL.validateExperimentalMode` | swept across all gates | ✓ `QL.java` has 0 throws |
| `LevenbergMarquardt` (MINPACK) | Phase 2a | ✓ `LevenbergMarquardt.java` 182 lines, 0 throws |
| `CapHelper` line 84 | Phase 2d | ✓ `CapHelper.java` 178 lines, 0 throws |
| `G2` line 126 | Phase 2e | ✓ `G2.java` 518 lines, 0 throws |
| `HestonProcess` QUADRATIC_EXPONENTIAL line 282 | Phase 2a | ✓ `HestonProcess.java` 386 lines, 0 throws |
| `TreeLattice2D.grid` line 73 | (intentionally preserved as v1.42.1 parity) | ✓ |

**All Phase 1 carve-outs from 2026-04-24 are resolved.** None remain open as "deferred."

---

## D3 — Tolerance + `@Ignore` hygiene — AMBER

### @Ignore audit

```
$ find jquantlib/src/test/java -name "*Test.java" -exec grep -cE "^[[:space:]]*@Ignore|^[[:space:]]*@org\.junit\.Ignore" {} \; | awk '{s+=$1} END {print s}'
0
```

**Zero `@Ignore` annotations across all 578 test files.** This is the cleanest end-state the project has been in since Phase 1 began (started at 138).

### Tolerance audit

Distribution across test suite (counts of `assertEquals` with explicit `1e-N` tolerance):

```
65 × 1e-12   (TIGHT — matches CLAUDE.md tier)
50 × 1e-15
21 × 1e-14
13 × 1e-10
 5 × 1e-4    ← looser than LOOSE tier (1e-8)
 4 × 1e-3    ← looser
 2 × 1e-9
 2 × 1e-8    (LOOSE — matches tier)
 1 × 1e-2    (heuristic mismatch — actual tol is tight, 1e-2 was expected value)
 1 × 1e-11
```

### Findings (justification audit on tolerances looser than 1e-8)

| File:line | Tol | Inline justification? | Verdict |
|---|---|---|---|
| `ExtendedBlackScholesMertonProcessTest.java:94-95` | 1e-3 | ✓ ("Milstein correction at dw=0...") | OK |
| `BondAdditionalTest.java:863` | 1e-4 (yield) | ✗ | **needs comment** |
| `BondAdditionalTest.java:870` | 1e-3 (duration) | ✗ | **needs comment** |
| `BondAdditionalTest.java:874` | 1e-3 (convexity) | ✗ | **needs comment** |
| `HestonModelTest.java:2586,2594,2602,2610` | 1e-4 (alphaStar) | ✗ | **needs comment** |
| `GaussNonCentralChiSquaredPolynomialTest.java:102` | 1e-5 (quadrature) | partial | borderline |
| `IncrementalStatisticsTest.java:141` | 1e-5 (not 1e-2; my heuristic mis-flagged) | n/a (tight) | OK |

**Verdict: AMBER.** The looseness itself is likely legitimate in every case (bond yield ± 1bp, alphaStar ± 1e-4 is the order-of-magnitude expected for the Heston small-time regime), but CLAUDE.md mandates an inline justification for any tolerance looser than 1e-8. 7 assert sites currently violate that. Remediation: ~15 minutes to add per-site `// looser tol because <reason>` comments.

---

## D4 — Code quality spot-check — GREEN

Sampled the most-touched 8 production classes from the last 8 days of commits:
`AnalyticHestonEngine`, `Schedule`, `Calendar`, `CashFlows`, `BlackFormula`, `JointCalendar`, `OISRateHelper`, `FdHestonVanillaEngine`, `BlackSwaptionEngine`, `BondFunctions`, `SobolRsg`, `MakeOIS`, `FixedRateLeg`, `ObservableSettings`, `LazyObject`.

Every sampled file carries:
- BSD license header
- JQuantLib migration contributors copyright (current files) or original JQuantLib copyright (pre-Phase-1 files updated only as needed)
- Original C++ author credits preserved when present
- Javadoc class header with:
  - Port-phase tag (e.g., "Phase 4a.5 A.5.2 port of...")
  - C++ source reference (`ql/pricingengines/vanilla/analytichestonengine.{hpp,cpp}`)
  - **Pinned commit hash** `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

Code is idiomatic Java (not transliterated C++): proper JavaDoc, no `goto`-equivalents, methods named per Java conventions, `org.jquantlib` package layout matches C++ `QuantLib::` namespace.

**No quality red flags in the sample.**

---

## D5 — C++ test-suite parity — **RED**

### Methodology

Extracted every `BOOST_AUTO_TEST_CASE(<name>, ...)` from `migration-harness/cpp/quantlib/test-suite/*.cpp` (1,272 distinct names across 181 files). Cross-checked each name against every Java `@Test`-annotated method in `jquantlib/src/test/java/**Test.java`. Aggregate by C++ file.

### Aggregate result

```
TOTAL: cpp=1272 java-name-matched=992 missing=280 parity=77%
Files with ≥3 missing tests: 28
```

**Per the project's binding standard ("every C++ test gets a faithful Java equivalent" — persistent memory, 2026-05-08), the bar is 100%. We are at 77%.**

### Worst-offender table (sorted by `missing` desc)

| C++ test file | C++ tests | Java-matched | Missing | Parity |
|---|---:|---:|---:|---:|
| schedule | 28 | 0 | **28** | 0% |
| calendars | 32 | 11 | 21 | 34% |
| piecewiseyieldcurve | 34 | 13 | 21 | 38% |
| americanoption | 26 | 5 | 21 | 19% |
| marketmodel | 19 | 2 | 17 | 10% |
| daycounters | 21 | 4 | 17 | 19% |
| integrals | 20 | 7 | 13 | 35% |
| andreasenhugevolatilityinterpl | 13 | 1 | 12 | 7% |
| dates | 14 | 3 | 11 | 21% |
| europeanoption | 23 | 14 | 9 | 60% |
| termstructures | 15 | 7 | 8 | 46% |
| dividendoption | 18 | 10 | 8 | 55% |
| period | 8 | 1 | 7 | 12% |
| markovfunctional | 7 | 0 | 7 | 0% |
| barrieroption | 12 | 6 | 6 | 50% |
| distributions | 9 | 3 | 6 | 33% |
| gsr | 4 | 0 | 4 | 0% |
| hestonslvmodel | 19 | 15 | 4 | 78% |
| fittedbonddiscountcurve | 5 | 1 | 4 | 20% |
| floatfloatswap | 4 | 0 | 4 | 0% |
| normalclvmodel | 5 | 1 | 4 | 20% |
| curvestates | 3 | 0 | 3 | 0% |
| array | 4 | 1 | 3 | 25% |
| doublebarrieroption | 3 | 0 | 3 | 0% |
| tqreigendecomposition | 3 | 0 | 3 | 0% |
| squarerootclvmodel | 3 | 0 | 3 | 0% |
| marketmodel_smmcaplethomocalibration | 3 | 0 | 3 | 0% |
| (… 28 total files with ≥3 missing) | | | | |

### Spot-check: `schedule` (28 missing, 0% covered)

C++ has 28 cases: `testDailySchedule`, `testEomAdjustment`, `testEndDateWithEomAdjustment`, `testCDS2015Convention`, `testCDSConventionGrid`, `testDateConstructor`, `testFourWeeksTenor`, `testOnceFrequency`, `testScheduleAlwaysHasAStartDate`, `testShortEomSchedule`, `testFirstDateOnMaturity`, `testNextToLastDateOnStart`, `testTruncation`, `testBackwardRegularFirstPeriodWithFirstDate`, etc.

Java has 3 tests: `testSchedule`, `testOneDayTenorBackwardDedupSofr`, `testOneDayTenorForwardDedupSofr`.

**These are not name-mismatch artifacts.** The Java tests do not cover the C++ semantic surface (CDS conventions, EOM adjustment, schedule truncation, four-weeks tenor, etc.). This is a real ~25-test gap on a foundational time module.

### Implication for Phase 1 closure

The binding standard from memory (2026-05-08, after extended discussion with user): "C++ test-suite is ~150–200K LOC; **every C++ test gets a faithful Java equivalent**; probes are cross-validation, NOT a substitute."

By that standard, **Phase 1 is not done.** ~280 tests, ~28+ files. The full set of "left for future me" items that the user explicitly asked this audit to surface.

### Remediation effort

Mechanical at the structural level — port each C++ test method to a Java `@Test` with the same name and the same probe/cross-validation pattern. Dispatchable in parallel across 4-6 worktrees. Estimated 1–3 days of wall time given the project's historical throughput on bulk porting work.

Some C++ tests are likely already semantically covered by Java tests under different names (e.g., a Java test split into 3 methods covers 1 C++ test). A second pass after literal porting would identify and dedupe those.

---

## D6 — Documentation trail — AMBER

### Findings

- **README badges are stale:**
  - `tag-jquantlib--phase5e5b--CFC--d--final` ← actual current tag is the same but the README also displays `tests-2964%2F0%2F0%2F553` — outdated (actual ~3010/0/0/1)
  - Scanner badge shows `scanner_WIP-0` — currently shows 1 hit (the FdmBatesOp false positive)
- **Migration-status table in README** stops at Phase 2e. Phases 2f → 5e.5b CFC-d are absent. ~14 missing rows.
- **`phase1-completion.md`** (the original 2026-04-24 doc) is accurate as a historical snapshot, but the open carve-outs it lists are no longer accurate state — they're all resolved. A note pointing readers to the resolution commits would close that loop.
- **`phase1-carveouts.md`** lists 6 carve-outs as open; all are now closed (verified D2). A "resolved at <commit>" annotation per entry would correct the record.
- No README claim of "Phase 1 complete" — only a tag-based reference. Internally consistent.

### Remediation effort

~30 minutes — update README badges, append phases-2f-onwards rows, annotate `phase1-carveouts.md` with resolution commits.

---

## Cross-cutting items confirmed in good standing

- **C++ pin** (`v1.42.1` @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`) consistently referenced in JavaDoc of recently-touched files ✓
- **Direct-to-main / no-PR discipline** maintained per CLAUDE.md ✓
- **Sign-off trailer** on commits ✓
- **No `Co-authored-by: Claude` trailers** ✓
- **`migration-harness/verify-harness.sh`** infrastructure exists and was last verified clean ✓ (not re-run for this audit — assumed valid)
- **Tolerance tier discipline** broadly followed (TIGHT 1e-12, EXACT bit-bit, LOOSE 1e-8) — only the AMBER D3 items violate ✓

---

## Go / No-Go decision

**NO-GO for Phase 2 pending remediation of D5 (and ideally D1-1, D3, D6).**

Reasoning: the user's explicit framing — "validate we have migrated everything and not left out a single bit ... decide if I can move on to the next phase knowing I did it all for this one and nothing was postponed or otherwise left for future me" — defines "done" against the project's binding standards, which include "every C++ test gets a faithful Java equivalent." That standard is currently met at 77%, with 280 tests "left for future me." Going to Phase 2 with this gap codifies that deferral.

### Recommended pre-Phase-2 remediation order

| # | Item | Effort | Blocking? |
|---|---|---:|---|
| 1 | **D5: port 280 missing C++ tests across 28 files** | 1–3 days, parallelizable | **YES** |
| 2 | D1-1: add `Assume.assumeTrue(slowTests)` gates to ~45 Java tests mirroring C++ `if_speed(Fast|Slow)` | 2–4 hours | YES |
| 3 | D3: add inline tolerance justifications to 7 assert sites | 15 min | NO (cosmetic) |
| 4 | D6: refresh README badges + migration-status table; annotate `phase1-carveouts.md` with resolution commits | 30 min | NO (cosmetic) |

After items 1–2, a fresh Phase 1 certification audit run would be expected to land all dimensions GREEN.

### What I will NOT do without explicit instruction

- Begin D5 remediation (porting 280 tests is a multi-day effort and should be authorized as a discrete sub-project, not tacked onto an audit).
- Add the ~45 `Assume.assumeTrue` gates en masse (functional behavior change to default `mvn test` outcomes — wants explicit go-ahead).
- Touch the README until D5 progress changes the numbers materially.

---

## Appendix A — audit commands (reproducible)

```bash
# D1 — full suite
cd jquantlib-parent && mvn -pl ../jquantlib clean test -Dquickcheck.skip=true -fae

# D1 — slow-test gate inventory
grep -rn "precondition(if_speed(" migration-harness/cpp/quantlib/test-suite/

# D2 — scanner
python3 tools/stub-scanner/scan_stubs.py

# D2 — silent stubs (escape parentheses)
grep -rnE 'UnsupportedOperationException\("Work in progress"|throw new LibraryException\("not implemented|throw new LibraryException\("not yet implemented' jquantlib/src/main/java

# D3 — tolerance distribution
find jquantlib/src/test/java -name "*Test.java" -exec grep -hE "assertEquals.*1[eE]-[0-9]+" {} \; | grep -oE "1[eE]-[0-9]+" | sort | uniq -c | sort -rn

# D3 — @Ignore audit
find jquantlib/src/test/java -name "*Test.java" -exec grep -cE "^[[:space:]]*@Ignore" {} \; | awk '{s+=$1} END {print s}'

# D5 — parity script: see /tmp/parity_audit.py (run from repo root)
```

## Appendix B — full D5 missing-test counts by file

(Generated by `/tmp/parity_audit.py` against tip `b66b8ef4`)

```
TOTAL: cpp=1272 java-name-matched=992 missing=280 parity=77%
```

Files at 0% parity (Java has 0 matches on test names):
`schedule`, `markovfunctional`, `gsr`, `floatfloatswap`, `curvestates`, `doublebarrieroption`, `tqreigendecomposition`, `squarerootclvmodel`, `marketmodel_smmcaplethomocalibration`, `nthtodefault`, `marketmodel_cms`, `compiledboostversion`, `marketmodel_smmcapletalphacalibration`, `marketmodel_smm`, `marketmodel_smmcapletcalibration`, `tracing`, `currency` (17 files).

Files at 100% parity (clean):
`vpp`, `overnightindexedswap`, `indexes`, `fdheston`, `garch`, `hybridhestonhullwhiteprocess`, `swaptionvolatilitymatrix`, `twoassetcorrelationoption`, `variancegamma`, `crosscurrencyratehelpers`, `cashflows`, `exchangerate`, `mclongstaffschwartzengine`, `ultimateforwardtermstructure`, `bondforward`, `rngtraits`, `noarbsabr`, `equityindex`, `inflationcpicapfloor`, `bermudanswaption`, `mersennetwister`, `lazyobject`, `zerocouponswap`, `varianceoption`, `doublebinaryoption`, `bonds`, `svivolatility`, `forwardoption`, `xoshiro256starstar`, `creditdefaultswap`, `ode`, `brownianbridge`, `rangeaccrual`, `jumpdiffusion`, `timeseries`, `nthorderderivativeop`, `linearleastsquaresregression`, `interestrates`, `basisswapratehelpers`, `convertiblebonds`, `inflationcapfloor`, `multipleresetscoupons`, `stats`, `varianceswaps`, `himalayaoption`, `extensibleoptions`, `extendedtrees`, `bacheliercalculator`, `swap` (49 files).

---

*End of report.*

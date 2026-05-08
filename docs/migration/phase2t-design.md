# Phase 2t Design — Inflation Test-Suite (inflation.cpp foundation)

**Status:** approved 2026-05-08 (autonomous mode — eleventh autonomous phase; **first test-suite phase under new rigor directive**)
**Predecessor:** `jquantlib-phase2s-complete` (tests `888/0/0/22`, scanner WIP=0)

## 1. Context

Per binding directive 2026-05-08 (`feedback_test_suite_rigor.md`): C++ test-suite must be ported with full rigor. Every C++ test deserves a faithful Java equivalent.

Phases 2p-2s closed the inflation subsystem in core code (100% surface coverage). Phase 2t begins porting the corresponding test-suite (~5,253 LOC C++ total across 7 files), starting with `inflation.cpp` (2,323 LOC — largest and foundational).

## 2. Scope

**In scope (Phase 2t):**
- `migration-harness/cpp/quantlib/test-suite/inflation.cpp` (2,323 LOC C++) — covers:
  - InflationIndex, ZeroInflationIndex, YoYInflationIndex behavior (fixings, registration, etc.)
  - InterpolatedZero/PiecewiseZeroInflationCurve bootstrap + queries
  - InterpolatedYoY/PiecewiseYoYInflationCurve bootstrap + queries
  - Seasonality (multiplicative + Kerkhof)
  - Inflation cashflows (zero/YoY/CPI base)
  - InflationTraits + InflationHelpers
- Foundational test fixture (`CommonVars` equivalent) extracted into a shared Java test helper

**Out of scope (Phase 2u+):**
- `inflationcapfloor.cpp` (526 LOC) — Phase 2u
- `inflationcapflooredcoupon.cpp` (784 LOC) — Phase 2u
- `inflationcpiswap.cpp` (495 LOC) — Phase 2u
- `inflationcpicapfloor.cpp` (434 LOC) — Phase 2u
- `inflationvolatility.cpp` (395 LOC) — Phase 2v (experimental coverage)
- `inflationcpibond.cpp` (296 LOC) + CPIBond instrument port — Phase 2v

## 3. Approach

Single worktree A. Two sub-commits:
- **A.1** `InflationCommonVars` test helper class — shared fixture for inflation tests (mirrors C++ inflation.cpp's fixture struct ~lines 50-200)
- **A.2** `InflationTest` Java class porting all `BOOST_AUTO_TEST_CASE` blocks from inflation.cpp

Test-suite-port discipline:
- One C++ `BOOST_AUTO_TEST_CASE` → one Java `@Test` method
- C++ fixture members → Java helper class fields (same names where possible)
- C++ `BOOST_CHECK_CLOSE`/`BOOST_CHECK_EQUAL` → Java `assertEquals` with appropriate Tolerance
- Tier expectation per test: TIGHT for deterministic checks, LOOSE for bootstrapped values, EXACT for metadata

## 4. Decisions

- **P2T-1:** Test-suite ports use existing Java assertion framework + `Tolerance` helper; no new test infrastructure introduced.
- **P2T-2:** Where C++ uses `boost::shared_ptr` test helpers, Java uses regular references with `Handle` where curve handles are needed.
- **P2T-3:** Where C++ test exercises a class Java hasn't ported yet, that test is skipped with `@Ignore("Phase 2u/2v: needs ClassName port")` AND the missing class is added to the Phase 2u/2v scope. Document inline.
- **P2T-4:** Direct-to-main signed `-s` no Co-authored-by per standing rule.
- **P2T-5:** Test count growth target +30-50 (typical inflation.cpp has 30-50 BOOST_AUTO_TEST_CASE blocks).

## 5. Pause triggers

Carry-forward A1-A29 + new **A30:** `inflation.cpp` exercises a Java method/constructor that diverges from C++ semantics in a way that breaks the test. Bundle as align prereq commit OR carve to follow-up.

## Outcome forecast

| Metric | Phase 2s tip | Phase 2t target |
|--------|--------------|-----------------|
| Tests | 888/0/0/22 | ~920-940 |
| Inflation test-suite coverage | 0% | ~44% (inflation.cpp / 5,253 total LOC) |
| Phase 2t test-port phases remaining for inflation | 7 cpp files / 5,253 LOC | 6 cpp files / 2,930 LOC |

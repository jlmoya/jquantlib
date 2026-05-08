# Phase 2v Implementation Plan

> Three-layer phase: L0 sequential prereqs, L1 parallel B+C, L2 Track D after B.

**Goal:** Inflation 100% (production + test-suite). Tag `jquantlib-phase2v-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2v-A /Users/josemoya/eclipse-workspace/jquantlib-2v-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2v-A
git submodule update --init --recursive
```

After L0 lands, create L1+L2 worktrees:
```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git worktree add -b phase-2v-B /Users/josemoya/eclipse-workspace/jquantlib-2v-B main
git worktree add -b phase-2v-C /Users/josemoya/eclipse-workspace/jquantlib-2v-C main
```

## L0 A.1 — 6 missing CPI base index classes + YY variants

- C++ source: `ql/indexes/inflation/{aucpi,ukhicp,uscpi,frhicp,zacpi}.hpp` + `euhicp.hpp` (EUHICPXT inside)
- Java targets:
  - `org.jquantlib.indexes.inflation.AUCPI` (~82 LOC)
  - `org.jquantlib.indexes.inflation.UKHICP` (~41 LOC)
  - `org.jquantlib.indexes.inflation.USCPI` (~83 LOC)
  - `org.jquantlib.indexes.inflation.FRHICP`
  - `org.jquantlib.indexes.inflation.ZACPI`
  - `org.jquantlib.indexes.inflation.EUHICPXT` (additive to EUHICP.java)
  - YY variants: `YYAUCPI, YYUKHICP, YYUSCPI, YYFRHICP, YYZACPI, YYEUHICPXT`
- Tests: smoke test for each index (name, region, frequency, availabilityLag — match C++)
- Commit: `infra(indexes.inflation): port 6 CPI base + YY variants — AUCPI, UKHICP, USCPI, FRHICP, ZACPI, EUHICPXT (Phase 2v L0 A.1)`

## L0 A.2 — GlobalBootstrap template

- C++ source: search `ql/termstructures/` for `GlobalBootstrap` (likely in `globalbootstrap.hpp` or similar)
- Java target: `org.jquantlib.termstructures.bootstrap.GlobalBootstrap`
- Verify InflationTest's GlobalBootstrap-blocked tests un-ignore + pass
- Commit: `infra(termstructures.bootstrap): port GlobalBootstrap template per C++ v1.42.1 (Phase 2v L0 A.2)`

## L0 A.3 — PiecewiseZeroInflationCurve lazy-baseDate Supplier ctor

- C++ source: `piecewisezeroinflationcurve.hpp` constructor with `Date baseDate, Date startDate` overload supporting lazy initialization
- Java target: add overloaded constructor accepting `Supplier<Date>` baseDate
- Verify InflationTest.testZeroTermStructureLazyBaseDate un-ignores and passes
- Commit: `align(termstructures.inflation): PiecewiseZeroInflationCurve lazy-baseDate Supplier ctor (Phase 2v L0 A.3)`

## L0 A.4 — YearOnYearInflationSwapHelper(quote, ..., discountCurve) overload

- C++ source: `inflationhelpers.{hpp,cpp}` — find the overload taking `Handle<YieldTermStructure> discountCurve` parameter
- Java target: add corresponding overload to YearOnYearInflationSwapHelper.java (Phase 2u Track F already added nominalTermStructure parameter — extend or alias)
- Verify InflationCapFloorTest.testCachedValue un-ignores and passes
- Commit: `align(termstructures.inflation): YearOnYearInflationSwapHelper discount-curve overload (Phase 2v L0 A.4)`

## L1 Track B — CPIBond instrument port (+ basic test)

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-2v-B`
- C++ source: `migration-harness/cpp/quantlib/ql/instruments/bonds/cpibond.{hpp,cpp}` (~700 LOC C++)
- Java target: `org.jquantlib.instruments.bonds.CPIBond` (or `CPIBondImpl` — match existing bond hierarchy)
- Mirror C++ structure: extends Bond, takes nominalSchedule + observation lag + index + base CPI + payment day count + paying calendar etc.
- Probe + reference: `cpi_bond_probe.cpp` exercising NPV, fairRate
- Smoke test: `CPIBondTest.java` verifying construction + basic NPV
- Commit: `infra(instruments.bonds): port CPIBond + basic NPV smoke (Phase 2v B)`

## L1 Track C — inflationvolatility.cpp test port

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-2v-C`
- C++ source: `migration-harness/cpp/quantlib/test-suite/inflationvolatility.cpp` (395 LOC)
- Exercises Phase 2s experimental coverage (KInterpolated/Piecewise YoYOptionletVol, YoYOptionletStripper, term-price surfaces)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/volatility/inflation/InflationVolatilityTest.java`
- Port every BOOST_AUTO_TEST_CASE per rigor
- Commit: `infra(testsuite.termstructures.volatility.inflation): port inflationvolatility.cpp test cases (Phase 2v C)`

## L2 Track D — inflationcpibond.cpp test port (sequential after B)

- Worktree: reuse `/Users/josemoya/eclipse-workspace/jquantlib-2v-B` after Track B's commit lands (or create fresh `2v-D`)
- C++ source: `migration-harness/cpp/quantlib/test-suite/inflationcpibond.cpp` (296 LOC)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/CPIBondTest.java` (replace Track B's smoke)
- Port every BOOST_AUTO_TEST_CASE per rigor
- Commit: `infra(testsuite.instruments): port inflationcpibond.cpp test cases (Phase 2v D)`

## L3 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

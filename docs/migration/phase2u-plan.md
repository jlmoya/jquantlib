# Phase 2u Implementation Plan

> Two-layer phase: L0 small aligns (sequential, unblock 12 @Ignore'd tests), L1 parallel test-suite ports.

**Goal:** Inflation test-suite 44% → 86%; @Ignore'd count 40 → ~28. Tag `jquantlib-phase2u-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2u-A /Users/josemoya/eclipse-workspace/jquantlib-2u-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2u-A
git submodule update --init --recursive
```

After L0 lands, create L1 worktrees B/C/D/E.

## L0 A.1 — UKRPI/EUHICP/YYUKRPI/YYEUHICP availabilityLag → 1 month

- C++: `ql/indexes/inflation/{ukrpi,euhicp}.hpp` shows `availabilityLag = Period(1, Months)`.
- Java: `org.jquantlib.indexes.inflation.{UKRPI,EUHICP,YYUKRPI,YYEUHICP}` — change availabilityLag from current (likely 2 or 3 months) to 1 month.
- Commit: `align(indexes.inflation): UKRPI/EUHICP availabilityLag → 1 month per C++ v1.42.1 (Phase 2u L0 A.1)`

## L0 A.2 — ZeroInflationIndex.lastFixingDate()

- C++ source: `ql/indexes/inflation/inflationindex.cpp:186-191`
- Java target: add public method `Date lastFixingDate()` to `ZeroInflationIndex.java` and `YoYInflationIndex.java` if separate
- Commit: `align(indexes): ZeroInflationIndex.lastFixingDate() per C++ v1.42.1 (Phase 2u L0 A.2)`

## L0 A.3 — YoYInflationIndex(ZeroInflationIndex underlying) ratio ctor

- C++ source: `ql/indexes/inflation/yoyinflationindex.{hpp,cpp}`
- Java target: add `YoYInflationIndex(ZeroInflationIndex underlying)` and `YoYInflationIndex(ZeroInflationIndex underlying, boolean interpolated)` constructors
- Commit: `align(indexes): YoYInflationIndex(ZeroInflationIndex) ratio constructors per C++ v1.42.1 (Phase 2u L0 A.3)`

## L0 A.4 — ZeroCouponInflationSwapHelper dual-date + CPI::Linear/Flat overloads

- C++ source: `ql/termstructures/inflation/inflationhelpers.{hpp,cpp}`
- Java target: add overloaded constructors taking dual dates + observation interpolation parameter
- Commit: `align(termstructures.inflation): ZeroCouponInflationSwapHelper dual-date + CPI::Linear/Flat overloads (Phase 2u L0 A.4)`

## L0 A.5 — Remove @Ignore from 12 unblocked tests in InflationTest

- After A.1-A.4 land, re-enable the 12 currently @Ignore'd tests that depend on those aligns
- Run `mvn test -Dtest=InflationTest` to confirm they all pass
- Commit: `align(testsuite.inflation): re-enable 12 InflationTest @Ignore'd tests post-L0 aligns (Phase 2u L0 A.5)`

## L1 Track B — inflationcapfloor.cpp port

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-2u-B` (sibling)
- C++ source: `migration-harness/cpp/quantlib/test-suite/inflationcapfloor.cpp` (526 LOC)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/InflationCapFloorTest.java` (replaces existing smoke test)
- Commit: `infra(testsuite.instruments): port inflationcapfloor.cpp test cases (Phase 2u B)`

## L1 Track C — inflationcpiswap.cpp port

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-2u-C`
- C++ source: `migration-harness/cpp/quantlib/test-suite/inflationcpiswap.cpp` (495 LOC)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/CPISwapTest.java` (replaces existing smoke test)
- Commit: `infra(testsuite.instruments): port inflationcpiswap.cpp test cases (Phase 2u C)`

## L1 Track D — inflationcpicapfloor.cpp port

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-2u-D`
- C++ source: `migration-harness/cpp/quantlib/test-suite/inflationcpicapfloor.cpp` (434 LOC)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/instruments/CPICapFloorTest.java` (replaces existing smoke test)
- Commit: `infra(testsuite.instruments): port inflationcpicapfloor.cpp test cases (Phase 2u D)`

## L1 Track E — inflationcapflooredcoupon.cpp port

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-2u-E`
- C++ source: `migration-harness/cpp/quantlib/test-suite/inflationcapflooredcoupon.cpp` (784 LOC)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/cashflows/CapFlooredInflationCouponTest.java` (replaces existing smoke test)
- Commit: `infra(testsuite.cashflows): port inflationcapflooredcoupon.cpp test cases (Phase 2u E)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

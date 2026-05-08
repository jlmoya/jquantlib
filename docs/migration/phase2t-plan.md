# Phase 2t Implementation Plan

> Single worktree A. Test-suite port: `inflation.cpp` → `InflationTest.java` + shared fixture.

**Goal:** First test-suite phase under rigor directive. Tag `jquantlib-phase2t-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2t-A /Users/josemoya/eclipse-workspace/jquantlib-2t-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2t-A
git submodule update --init --recursive
```

## A.1 — InflationCommonVars test helper

- **C++ source:** `migration-harness/cpp/quantlib/test-suite/inflation.cpp` lines 50-200 (struct CommonVars or equivalent fixture, plus utility helpers like `makeHelpers`)
- **Java target (new):** `jquantlib/src/test/java/org/jquantlib/testsuite/util/InflationCommonVars.java`
- **Members:** evaluation date, calendar, day counter, settlement days, observation lag, frequency, plus convenience builders for `ZeroInflationIndex`, `YoYInflationIndex`, term structures
- **Test:** small smoke test demonstrating fixture builds correctly
- **Commit:** `infra(testsuite.util): InflationCommonVars test fixture (Phase 2t A.1)`

## A.2 — InflationTest port

- **C++ source:** `migration-harness/cpp/quantlib/test-suite/inflation.cpp` (full file)
- **Java target (new):** `jquantlib/src/test/java/org/jquantlib/testsuite/inflation/InflationTest.java`
- Port every `BOOST_AUTO_TEST_CASE` block as `@Test public void` method. Mirror C++ test structure exactly.
- Where C++ test exercises a class Java doesn't port, skip with `@Ignore("Phase 2u/2v: needs ClassName")` and document inline.
- **Commit:** `infra(testsuite.inflation): port inflation.cpp test cases (Phase 2t A.2)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

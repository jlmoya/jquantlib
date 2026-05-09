# Phase 3a Implementation Plan

> Three-layer phase. L0 sequential foundation, L1 parallel curves, L2 test port.

**Goal:** Credit termstructures + first test-suite port. Tag `jquantlib-phase3a-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3a-A /Users/josemoya/eclipse-workspace/jquantlib-3a-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-3a-A
git submodule update --init --recursive
```

## L0 — Foundation

- C++ source:
  - `migration-harness/cpp/quantlib/ql/termstructures/credit/probabilitytraits.hpp`
  - `migration-harness/cpp/quantlib/ql/termstructures/credit/defaultdensitystructure.{hpp,cpp}`
  - `migration-harness/cpp/quantlib/ql/termstructures/credit/hazardratestructure.{hpp,cpp}`
  - `migration-harness/cpp/quantlib/ql/termstructures/credit/survivalprobabilitystructure.{hpp,cpp}`
  - `migration-harness/cpp/quantlib/ql/termstructures/credit/flathazardrate.{hpp,cpp}`
- Java targets (new package `org.jquantlib.termstructures.credit`):
  - `ProbabilityTraits` (or split into traits per curve type)
  - `DefaultDensityStructure` (abstract base)
  - `HazardRateStructure` (abstract base)
  - `SurvivalProbabilityStructure` (abstract base)
  - `FlatHazardRate` (concrete)
- Probe + smoke test for each base + FlatHazardRate
- Commit: `infra(termstructures.credit): port probabilitytraits + 3 base structures + FlatHazardRate (Phase 3a L0)`

## L1 — Curves (parallel — single subagent OK if scope is tight)

- C++ source:
  - `interpolateddefaultdensitycurve.hpp`
  - `interpolatedhazardratecurve.hpp`
  - `interpolatedsurvivalprobabilitycurve.hpp`
  - `piecewisedefaultcurve.hpp`
  - `defaultprobabilityhelpers.{hpp,cpp}` — non-CDS variants only (CDS variants → Phase 3b)
- Java targets:
  - `InterpolatedDefaultDensityCurve`
  - `InterpolatedHazardRateCurve`
  - `InterpolatedSurvivalProbabilityCurve`
  - `PiecewiseDefaultCurve`
  - `DefaultProbabilityHelper` base + non-CDS subclasses
- Probes + tests
- Commit: `infra(termstructures.credit): port 3 interpolated curves + PiecewiseDefaultCurve + non-CDS helpers (Phase 3a L1)`

## L2 — defaultprobabilitycurves.cpp test port

- C++ source: `migration-harness/cpp/quantlib/test-suite/defaultprobabilitycurves.cpp` (533 LOC)
- Java target: `jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/credit/DefaultProbabilityCurvesTest.java`
- Port every BOOST_AUTO_TEST_CASE per rigor; @Ignore CDS-dependent tests with Phase 3b rationale
- Commit: `infra(testsuite.termstructures.credit): port defaultprobabilitycurves.cpp test cases (Phase 3a L2)`

## L3 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

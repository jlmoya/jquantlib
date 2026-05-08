# Phase 2s Implementation Plan

> Two-layer phase: L0 utilities (sequential), L1 parallel B+C tracks.

**Goal:** Experimental inflation subsystem 100%. Tag `jquantlib-phase2s-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2s-A /Users/josemoya/eclipse-workspace/jquantlib-2s-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2s-A
git submodule update --init --recursive
```

After L0 lands:
```bash
git worktree add -b phase-2s-B /Users/josemoya/eclipse-workspace/jquantlib-2s-B main
git worktree add -b phase-2s-C /Users/josemoya/eclipse-workspace/jquantlib-2s-C main
```

## L0 A.1 — Polynomial2DSpline + GenericIndexes utilities

- C++ source: `migration-harness/cpp/quantlib/ql/experimental/inflation/{polynomial2Dspline,genericindexes}.hpp`
- Java targets:
  - `org.jquantlib.experimental.inflation.Polynomial2DSpline`
  - `org.jquantlib.experimental.inflation.GenericIndexes` (or split into one class per generic index if necessary)
- Test: smoke tests for both
- Commit: `infra(experimental.inflation): port Polynomial2DSpline + GenericIndexes utilities (Phase 2s L0)`

## L1 Track B — Vol structures + stripper

- Files (new):
  - `org.jquantlib.experimental.inflation.KInterpolatedYoYOptionletVolatilitySurface`
  - `org.jquantlib.experimental.inflation.PiecewiseYoYOptionletVolatility`
  - `org.jquantlib.experimental.inflation.YoYOptionletStripper` (base)
  - `org.jquantlib.experimental.inflation.InterpolatedYoYOptionletStripper`
  - `org.jquantlib.experimental.inflation.YoYOptionletHelpers`
- Probes + tests under matching directories
- Commit(s): split per-class is fine; one combined commit also OK
- Push: `git push origin phase-2s-B:main`

## L1 Track C — Term-price surfaces + experimental engines

- Files (new):
  - `org.jquantlib.experimental.inflation.CPICapFloorTermPriceSurface`
  - `org.jquantlib.experimental.inflation.YoYCapFloorTermPriceSurface`
  - `org.jquantlib.experimental.inflation.CPICapFloorEngines` (engines for CPICapFloor — different from Phase 2r InflationCapFloorEngines)
- Probes + tests
- Commit(s)
- Push: `git push origin phase-2s-C:main`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

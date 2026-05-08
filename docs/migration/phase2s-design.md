# Phase 2s Design — Experimental Inflation Subsystem Closeout

**Status:** approved 2026-05-08 (autonomous mode — tenth autonomous phase)
**Predecessor:** `jquantlib-phase2r-complete` (tests `853/0/0/22`, scanner WIP=0)

## 1. Context

Phase 2r closed core inflation at 100% surface coverage. Phase 2s closes `ql/experimental/inflation/` (~2,751 LOC C++) — strippers, term-price surfaces, generic indexes, polynomial 2D spline utility, and additional experimental vol structures.

## 2. Scope

**In scope (~2,751 LOC C++):**
- L0 utilities (sequential prereqs):
  - `experimental/inflation/polynomial2Dspline.hpp` (~107 LOC) — 2D spline interpolator
  - `experimental/inflation/genericindexes.hpp` (~99 LOC) — generic index registry
- Track B (vol structures + stripper):
  - `experimental/inflation/kinterpolatedyoyoptionletvolatilitysurface.hpp` (~208 LOC)
  - `experimental/inflation/piecewiseyoyoptionletvolatility.hpp` (~234 LOC)
  - `experimental/inflation/yoyoptionletstripper.hpp` (~65 LOC) — base stripper
  - `experimental/inflation/interpolatedyoyoptionletstripper.hpp` (~303 LOC) — concrete stripper
  - `experimental/inflation/yoyoptionlethelpers.{hpp,cpp}` (~164 LOC)
- Track C (term-price surfaces + experimental engines):
  - `experimental/inflation/cpicapfloortermpricesurface.{hpp,cpp}` (~504 LOC)
  - `experimental/inflation/yoycapfloortermpricesurface.{hpp,cpp}` (~707 LOC)
  - `experimental/inflation/cpicapfloorengines.{hpp,cpp}` (~155 LOC)

## 3. Approach

L0 sequential, L1 parallel B+C. Same pattern as Phase 2r.

## 4. Decisions

- **P2S-1:** L0 utilities (Polynomial2DSpline + GenericIndexes) land first; L1 may depend on them.
- **P2S-2:** Track B + C parallel; coordinate via main pull.
- **P2S-3:** Tier-stratified per usual.
- **P2S-4:** Direct-to-main signed `-s` no Co-authored-by per standing rule.

## 5. Pause triggers

Carry-forward A1-A28 + new **A29:** experimental class depends on missing core class — bundle as align prereq OR defer.

## Outcome forecast

| Metric | Phase 2r tip | Phase 2s target |
|--------|--------------|-----------------|
| Tests | 853/0/0/22 | ~865-880 |
| Inflation surface coverage | 100% core (experimental absent) | 100% core + 100% experimental |
| Java inflation full | 60% of subsystem (excl. experimental) | 100% (incl. experimental) |

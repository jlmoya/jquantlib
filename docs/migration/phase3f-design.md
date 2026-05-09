# Phase 3f Design — Interpolation Copy-vs-Reference Fix + Final Credit Test Closure

**Status:** approved 2026-05-09 (autonomous mode — twentieth autonomous phase)
**Predecessor:** `jquantlib-phase3e-complete` (tests `1024/0/0/41`, scanner WIP=0, mvn 60.0s)

## 1. Context

Phase 3e identified the architectural issue blocking 3 Markit-reconciliation tests:
- `Array(double[])` constructor uses `System.arraycopy` to copy input array
- `AbstractInterpolation` subclasses (e.g., `LogLinearInterpolation`) read from the copy
- `IterativeBootstrap.BootstrapError.op` writes to the source via `data()[i] = guess` then calls `interpolation().update()` — but `update()` reads from stale copy

Three potential fix paths (per Phase 3e report):
1. Make `Array(double[])` store reference (no arraycopy) — risk: cascade through tests assuming copy semantics
2. Rebuild interpolation inside `IterativeBootstrap.BootstrapError.op` per call — most surgical
3. Thread explicit `setData/refresh` hook through `Interpolation` — clean architectural change

## 2. Scope (~50-200 LOC, investigation-first)

**Investigation:**
- Read Array(double[]) usage across the codebase. How many tests would break if Array stored reference?
- Read AbstractInterpolation hierarchy. What's the cleanest hook for refreshing data?
- Identify minimum-surface fix that closes the 3 Markit tests

**Production fix:**
- Land the chosen fix. Target: <200 LOC. If exceeds, scope-trim.

**Test un-ignore:**
- Un-ignore + verify pass: testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast (bodies already in CreditDefaultSwapTest.java from Phase 3e)

## 3. Approach

Single worktree A. Investigation → fix → un-ignore → final report.

## 4. Decisions

- **P3F-1:** Investigation-first; if fix exceeds 200 LOC scope, defer architectural change to Phase 3g and try a more surgical workaround
- **P3F-2:** BOOST_CHECK_CLOSE tolerance is in PERCENT (per Phase 3e finding); test bodies may need assertion adjustment after fix
- **P3F-3:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A35.

## Outcome forecast

| Metric | Phase 3e tip | Phase 3f target |
|--------|--------------|-----------------|
| Tests | 1024/0/0/41 | 1027/0/0/38 (3 un-ignored if fix lands) |
| Credit subsystem | 100% production / 98% test | 100% (production + test) |

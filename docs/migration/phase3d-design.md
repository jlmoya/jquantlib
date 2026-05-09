# Phase 3d Design — IsdaCdsEngine + Final Credit Closeout

**Status:** approved 2026-05-09 (autonomous mode — eighteenth autonomous phase)
**Predecessor:** `jquantlib-phase3c-complete` (tests `1018/0/0/44`, scanner WIP=0, mvn 59.4s)

## 1. Context

Phase 3c landed IntegralCdsEngine + MakeCDS + DateGeneration.CDS + un-ignored 7 of 8 deferred tests. Phase 3d closes credit subsystem with IsdaCdsEngine (sophisticated, 488 LOC C++) + IterativeBootstrap configuration + final un-ignore work.

After Phase 3d, credit subsystem reaches 100% v1.42.1 surface coverage (production + test-suite), matching inflation completion arc.

## 2. Scope (~600 LOC C++)

**Production:**
- IsdaCdsEngine ~488 LOC C++ (sophisticated, with calibration logic)
- IterativeBootstrap configuration object + dontThrow/dontThrowFallback mode (~30 LOC)
- Actual360(true) DayCounter variant + FixedRateLeg.withLastPeriodDayCounter follow-through (~30 LOC)

**Test un-ignore:**
- Track C 4 Isda-specific tests (testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast, testDefaultConventions)
- Phase 3a's testUpfrontBootstrap + testIterativeBootstrapRetries

**Out of scope (Phase 3e):**
- models/marketmodels/ + tests
- experimental/ non-inflation/non-credit
- Remaining test-suite ports
- Phase 2y carry-forwards

## 3. Approach

Two-step:
- **L0 sequential**: IterativeBootstrap config object + Actual360(true) + FixedRateLeg.withLastPeriodDayCounter (foundation prerequisites)
- **L1**: IsdaCdsEngine port + un-ignore sweep

## 4. Decisions

- **P3D-1:** IsdaCdsEngine is the sophisticated engine; expect possibly @Ignore'd edge cases on first attempt
- **P3D-2:** Phase 3d closes credit; subsequent phases pivot to models/marketmodels/ or experimental/
- **P3D-3:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A35.

## Outcome forecast

| Metric | Phase 3c tip | Phase 3d target |
|--------|--------------|-----------------|
| Tests | 1018/0/0/44 | ~1030/0/0/38 |
| Credit subsystem | ~95% | 100% |
| Phase 3a/3b/3c remaining @Ignore'd | 5 (4 Isda + testUpfrontBootstrap + testIterativeBootstrapRetries) | 0 (all unblocked) |

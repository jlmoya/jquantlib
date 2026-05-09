# Phase 3c Design — IntegralCdsEngine + MakeCreditDefaultSwap + DateGeneration.CDS + Un-ignore

**Status:** approved 2026-05-08 (autonomous mode — seventeenth autonomous phase)
**Predecessor:** `jquantlib-phase3b-complete` (tests `1004/0/0/51`, scanner WIP=0, mvn 59.2s)

## 1. Context

Phase 3b landed CDS instrument + MidPointCdsEngine + helpers but left 10 Track C tests + 3 Phase 3a tests @Ignore'd. Phase 3c lands IntegralCdsEngine + MakeCreditDefaultSwap factory + DateGeneration.CDS/CDS2015/OldCDS enum values + IterativeBootstrap refinement, then un-ignores all unblockable tests.

IsdaCdsEngine (488 LOC, sophisticated) deferred to Phase 3d.

## 2. Scope (~700 LOC C++)

**Production:**
- IntegralCdsEngine ~250 LOC C++ → ~400 LOC Java
- MakeCreditDefaultSwap factory — ~150 LOC C++
- DateGeneration.CDS / CDS2015 / OldCDS enum values + Schedule rule support — ~50 LOC additive
- IterativeBootstrap initial-guess refinement (additive to existing) — ~30 LOC

**Test un-ignore work:**
- Track C 5 MidPoint-dependent tests (un-ignore + verify pass)
- Track C 1 MakeCDS-dependent test (un-ignore + verify pass after MakeCDS lands)
- Phase 3a 3 still-deferred tests (testLogLinearSurvivalConsistency / testUpfrontBootstrap / testIterativeBootstrapRetries)

**Out of scope (Phase 3d):**
- IsdaCdsEngine + Track C 4 Isda tests
- Phase 2y + 3+ items

## 3. Approach

Two-layer:
- **L0 sequential:** DateGeneration.CDS enum + IterativeBootstrap refinement + MakeCreditDefaultSwap (small foundations)
- **L1 parallel:** Track B IntegralCdsEngine + Track C un-ignore sweep

## 4. Decisions

- **P3C-1:** Phase 3c tightly bounded — Isda deferred to 3d to keep this focused
- **P3C-2:** MakeCreditDefaultSwap follows existing JQuantLib MakeXxx patterns (e.g., MakeYoYInflationCapFloor from Phase 2r)
- **P3C-3:** Un-ignore work is part of L1 — verify each previously-deferred test passes; if not, refine rationale (no silent skips)
- **P3C-4:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A35.

## Outcome forecast

| Metric | Phase 3b tip | Phase 3c target |
|--------|--------------|-----------------|
| Tests | 1004/0/0/51 | ~1015/0/0/45 (un-ignore 6+ Track C tests + 3 Phase 3a tests) |
| Credit subsystem coverage | termstructures + MidPoint + helpers | + Integral + MakeCDS + most tests un-ignored |
| Phase 3a still-deferred @Ignore'd | 3 | 0 (all un-blocked) |
| Phase 3b Track C @Ignore'd | 10 | 4 (only Isda tests remain — Phase 3d) |

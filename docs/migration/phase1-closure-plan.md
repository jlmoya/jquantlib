# Phase 1 Closure Plan (Path A — full D5 closure)

**Started:** 2026-05-20
**Base tip:** `5a53734e`
**Goal:** Resolve all 11 D5 infrastructure prerequisites, then port the 116 currently-BLOCKED tests. End state: D5 RED → GREEN, Phase 1 closed by every standard the project set.

**User mandate:** "Leave nothing for future me" — true closure of the 280-test gap surfaced by the Phase 1 certification audit (`docs/migration/phase1-certification-report.md`).

---

## Prerequisite work items

Ranked roughly by leverage (tests unblocked / LOC).

| # | TODO | LOC est | Tests unblocked | Domain |
|---|------|--------:|----------------:|--------|
| 547 | Array.resize + (size,start,step) ctor | 50 | 1 | math |
| 551 | CotSwapFromFwdCorrelation | 150 | 4 | model.marketmodels.correlations |
| 548 | Calendar gaps (Denmark/Russia/Israel/China.SSE/Mexico/NZ/S.Korea) | 400 | 10 | time.calendars |
| 545 | AbcdCalibration + MarketModelTestSetup helper | 600 | 16 | model.marketmodels |
| 553 | MultiCompositeQuote + CompositeInstrument + tracing + ConstantLossModel | 600 | 4 | quotes/instruments/experimental.credit |
| 555 | Escrowed dividend + CashDividendEuropeanEngine + FdBlackScholesBarrierEngine | 700 | 7 | pricingengines.vanilla |
| 552 | InterpolatedPiecewiseForwardSpreadedTermStructure + BondHelper + FFT/QMC | 900 | 9 | termstructures + pricingengines |
| 554 | Daycounter+Date infra (ECB, ASX, ActualActual(Convention,Sched), Thirty365, Actual366, 365.25, DateParser, yearFractionToDate) | 1600 | 21 | time + daycounters |
| 546 | American-option engines (QdPlus + QdFp + FdShout + BjerksundStensland rewrite) | 2000 | 17 | pricingengines.vanilla |
| 549 | AndreasenHuge calibrator A3 production bug | research | 13 | termstructures.volatilities.equityfx |
| 550 | HestonRNDCalculator deep-OTM A3 divergence | research | 1 | experimental.models |

**Cumulative**: ~7000 LOC of production-engine porting; ~116 tests subsequently re-port-able.

---

## Execution model

- Four parallel worktrees as in D5: `/Users/josemoya/eclipse-workspace/jquantlib-d5-{A,B,C,D}`. Branches renamed/reused per round.
- Per worktree per round, one subagent gets one TODO (or a subset of one large TODO).
- Each subagent's task is **infra-port + downstream-test-port + verify green**, in that order, with align-prep commits separated.
- Each commit signed off with `-s`. No `Co-authored-by: Claude`.
- Landings via rebase-onto-main + fast-forward, per project convention.

### Round A1 (dispatched 2026-05-20)

- **A**: #547 Array.resize + ctor (~50 LOC, 1 test) — quick win + capacity to take a second small TODO
- **B**: #546-part1 QdPlusAmericanEngine + QdFpAmericanEngine + QdFp*Scheme (~1200 LOC, 9 Qd* tests)
- **C**: #554 Daycounter+Date infra (~1600 LOC, 21 tests) — largest single TODO; might split if budget pressured
- **D**: #545 AbcdCalibration + MarketModelTestSetup helper (~600 LOC, 16 marketmodel.cpp tests)

### Round A2 (planned)

- **A**: #548 Calendar gaps (Denmark first, others follow)
- **B**: #546-part2 FdBlackScholesShoutEngine + BjerksundStensland rewrite (~800 LOC, 8 BS/Shout tests)
- **C**: #551 CotSwapFromFwdCorrelation + #553 MultiComposite/CompositeInstrument
- **D**: #555 Escrowed dividend + CashDividend + FdBSBarrier (~700 LOC, 7 tests)

### Round A3 (planned)

- **A**: #552 ForwardSpreadedTermStructure + BondHelper + FFT/QMC engines (~900 LOC, 9 tests)
- **B**: #549 AndreasenHuge A3 calibrator fix (research → patch + 13 tests)
- **C**: #550 HestonRNDCalculator deep-OTM A3 fix (1 test)
- **D**: Tracing-infra + ConstantLossModel from #553 + sweep

---

## Definition of done

1. Every D5 BLOCKED test from the certification report now has an Added @Test method that passes `mvn -pl ../jquantlib test -Dtest=<ClassName>`.
2. Full `mvn -pl ../jquantlib clean test` is BUILD SUCCESS with `Failures: 0, Errors: 0`. Skipped count permitted only for tests that mirror C++ `*precondition(if_speed(Fast|Slow))` gates or other v1.42.1-equivalent skips.
3. `@Ignore` count returns to 0 (current 4 placeholders cleaned up when their integrators land).
4. `docs/migration/phase1-certification-report.md` D5 dimension updated to GREEN; all 11 prereq TODOs marked completed.
5. Tag: `jquantlib-phase1-true-closure` (with the resolved certification report committed before tagging).

---

*This document will be updated as each round lands. Last update: 2026-05-20 — Round A1 dispatched.*

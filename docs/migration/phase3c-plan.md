# Phase 3c Implementation Plan

> Two-layer: L0 small foundations, L1 parallel engine + un-ignore.

**Goal:** Complete CDS subsystem except IsdaCdsEngine. Tag `jquantlib-phase3c-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3c-A /Users/josemoya/eclipse-workspace/jquantlib-3c-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-3c-A
git submodule update --init --recursive
```

## L0 A.1 — DateGeneration.CDS / CDS2015 / OldCDS enum values

- C++ source: `migration-harness/cpp/quantlib/ql/time/dategenerationrule.hpp` (or wherever DateGeneration enum is defined)
- Java target: `org.jquantlib.time.DateGeneration` enum + `org.jquantlib.time.Schedule` rule support
- Add CDS / CDS2015 / OldCDS values + previousTwentieth() / nextTwentieth() static helpers
- Test smoke: schedule construction with CDS rule
- Commit: `align(time): DateGeneration.CDS/CDS2015/OldCDS enum values + Schedule rule support per C++ v1.42.1 (Phase 3c L0 A.1)`

## L0 A.2 — IterativeBootstrap initial-guess refinement

- File: `org.jquantlib.termstructures.credit.PiecewiseDefaultCurve` (Phase 3a) — add initial-guess refinement loop matching C++ IterativeBootstrap (try multiple guess strategies, retry on convergence failure)
- Verify: un-ignore Phase 3a's `testLogLinearSurvivalConsistency` and `testIterativeBootstrapRetries`
- Commit: `align(termstructures.credit): PiecewiseDefaultCurve IterativeBootstrap initial-guess refinement (Phase 3c L0 A.2)`

## L0 A.3 — MakeCreditDefaultSwap factory

- C++ source: search for `MakeCreditDefaultSwap` in `ql/instruments/`
- Java target: `org.jquantlib.instruments.MakeCreditDefaultSwap` (fluent builder; mirror C++)
- Verify: un-ignore Track C's `testAccrualRebateAmounts`
- Commit: `infra(instruments): port MakeCreditDefaultSwap factory (Phase 3c L0 A.3)`

## L1 Track B — IntegralCdsEngine

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3c-B`
- C++ source: `migration-harness/cpp/quantlib/ql/pricingengines/credit/integralcdsengine.{hpp,cpp}` (250 LOC C++)
- Java target: `org.jquantlib.pricingengines.credit.IntegralCdsEngine`
- Probe + tests
- Commit: `infra(pricingengines.credit): port IntegralCdsEngine (Phase 3c B)`

## L1 Track C — Un-ignore sweep

- Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3c-C` (or share with B if conflict-free)
- Targets: 5 Track C MidPoint-dependent tests (testCachedValue / testCachedMarketValue / testImpliedHazardRate / testFairSpread / testFairUpfront) — all should pass with MidPoint engine now landed (Phase 3b Track B)
- Run each, remove @Ignore, verify pass; if fail, refine rationale
- Commit: `align(testsuite.instruments): un-ignore 5 CreditDefaultSwapTest MidPoint tests post-Phase-3b (Phase 3c C)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

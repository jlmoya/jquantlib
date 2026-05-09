# Phase 3e Implementation Plan

> Single worktree A. Markit fixture investigation + 3-test closeout.

**Goal:** Credit subsystem 100% (production + test). Tag `jquantlib-phase3e-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3e-A /Users/josemoya/eclipse-workspace/jquantlib-3e-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-3e-A
git submodule update --init --recursive
```

## A.1 — Markit fixture investigation + production aligns

- Investigate Java's PiecewiseYieldCurve LogLinear-IterativeBootstrap support
- Investigate DepositRateHelper + SwapRateHelper Markit-compatible variants
- Investigate IborCoupon.usingAtParCoupons toggle integration in tests
- Land any missing additive production code (~50-150 LOC max — if larger, defer specifics to follow-up)
- Commit: `align(termstructures.yieldcurves,cashflow): Markit-compatible PiecewiseYieldCurve + helpers + IborCoupon.usingAtParCoupons (Phase 3e A.1)`

## A.2 — Un-ignore 3 Markit-reconciliation tests with full bodies

- Read C++ `creditdefaultswap.cpp` — find testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast
- Read existing Java CreditDefaultSwapTest.java for Phase 3b body templates (Phase 3b Track C left bodies in skeleton form)
- For each test: port body fully, remove @Ignore, run, verify pass at C++ tolerance (1e-3 typical for Markit reconciliation)
- If a test still fails: refine @Ignore rationale (no silent skip)
- Commit: `align(testsuite.instruments): un-ignore 3 CreditDefaultSwapTest Markit reconciliation tests (Phase 3e A.2)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

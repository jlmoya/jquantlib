# Phase 3g Implementation Plan

> Single worktree A. Two focused IsdaCdsEngine accrual fixes + un-ignore.

**Goal:** Final credit subsystem closeout. Tag `jquantlib-phase3g-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3g-A /Users/josemoya/eclipse-workspace/jquantlib-3g-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-3g-A
git submodule update --init --recursive
```

## A.1 — IsdaCdsEngine short-tenor accrual + T+3 settlement rebate

**Investigation:**
1. Read C++ `migration-harness/cpp/quantlib/ql/pricingengines/credit/isdacdsengine.cpp`
2. Read Java `org.jquantlib.pricingengines.credit.IsdaCdsEngine` (Phase 3d port)
3. Compare default leg / coupon leg / accrual rebate calculations side-by-side
4. Identify short-tenor accrual divergence (testIsdaEngine residual)
5. Identify T+3 settlement accrual rebate divergence (testIsdaCalculatorReconcile* -173 diff)

**Fix:** small additive corrections within IsdaCdsEngine. Target <100 LOC.

**Verify:** un-ignore 3 Markit tests + run focused mvn.

**Commit:** `align(pricingengines.credit.IsdaCdsEngine): short-tenor accrual + T+3 settlement rebate fixes (Phase 3g A.1)`

## A.2 — Un-ignore 3 Markit tests

If A.1 closes the gap fully, remove @Ignore from all 3 tests in CreditDefaultSwapTest.java.

If only some pass, refine @Ignore rationale on remaining (no silent skip).

**Commit:** `align(testsuite.instruments): un-ignore 3 CreditDefaultSwapTest Markit reconciliation tests post-Phase-3g (Phase 3g A.2)` (or split if partial).

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline.

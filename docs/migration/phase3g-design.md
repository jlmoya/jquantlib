# Phase 3g Design — IsdaCdsEngine Accrual Fixes (Final Credit Closure)

**Status:** approved 2026-05-09 (autonomous mode — twenty-first autonomous phase)
**Predecessor:** `jquantlib-phase3f-complete` (tests `1024/0/0/41`, scanner WIP=0, mvn 61.0s)

## 1. Context

Phase 3f Array(double[]) reference fix improved testIsdaEngine bootstrap drift 100× (from ~1% to 2e-5..1.4e-4 fraction; budget 1e-3 PERCENT i.e. 1e-5 fraction). Residual drift inversely correlates with maturity (worst at 1-yr, best at 10-yr) → IsdaCdsEngine short-tenor accrual handling.

testIsdaCalculatorReconcile* unchanged constant -173 absolute diff on both EUR fixtures (~6.3 days × ~27.4 nominal*coupon/365) → IsdaCdsEngine T+3 settlement accrual rebate bug.

Both issues are orthogonal to bootstrap; both lie in IsdaCdsEngine accrual / settlement-date paths.

## 2. Scope (~50-100 LOC, two focused fixes)

**Production:**
- IsdaCdsEngine short-tenor accrual fix (residual drift correlation with maturity)
- IsdaCdsEngine T+3 settlement accrual rebate fix (constant -173 diff)

**Test:**
- Un-ignore 3 Markit tests + verify pass at C++ tolerance

## 3. Approach

Single worktree A. Two sequential fixes + un-ignore.

## 4. Decisions

- **P3G-1:** Investigation-first; if fixes exceed 100 LOC scope, defer to Phase 3h
- **P3G-2:** This is the final credit closeout; subsequent phases pivot to models/marketmodels/

## 5. Pause triggers

Carry-forward A1-A35.

## Outcome forecast

| Metric | Phase 3f tip | Phase 3g target |
|--------|--------------|-----------------|
| Tests | 1024/0/0/41 | 1027/0/0/38 (3 un-ignored) |
| Credit subsystem | 100% production / 98% test | 100% (production + test) |

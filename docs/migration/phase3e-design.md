# Phase 3e Design — Markit Fixture + Final Credit Test Closure

**Status:** approved 2026-05-09 (autonomous mode — nineteenth autonomous phase)
**Predecessor:** `jquantlib-phase3d-complete` (tests `1024/0/0/41`, scanner WIP=0, mvn 60.0s)

## 1. Context

Phase 3d closed credit production (IsdaCdsEngine + 5 sanity tests). 3 Markit-reconciliation Isda tests remain @Ignore'd: testIsdaEngine, testIsdaCalculatorReconcileSingleQuote, testIsdaCalculatorReconcileSingleWithIssueDateInThePast. Blocker: external fixture — PiecewiseYieldCurve\<Discount, LogLinear, IterativeBootstrap\> bootstrap from EUR/USD deposit + swap rate helpers + IborCoupon.usingAtParCoupons setting.

Phase 3e ports the missing fixture pieces and un-ignores the 3 tests, closing credit subsystem to 100% v1.42.1.

## 2. Scope (~250 LOC)

**Production (additive):**
- `org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve` LogLinear/IterativeBootstrap variant if not already supported (likely most infrastructure exists from earlier phases — verify scope)
- `DepositRateHelper` and `SwapRateHelper` enhancements if needed for Markit conventions
- `IborCoupon.usingAtParCoupons` test integration plumbing

**Test (3 un-ignores + bodies):**
- testIsdaEngine — 5×2×2 sweep against 20 cached Markit values
- testIsdaCalculatorReconcileSingleQuote — EUR side
- testIsdaCalculatorReconcileSingleWithIssueDateInThePast — withTradeDate variant

## 3. Approach

Single worktree A. Sequential:
- Investigate gap (DepositRateHelper / SwapRateHelper / PiecewiseYieldCurve LogLinear-IterativeBootstrap availability in current Java)
- Land minimal additive production for whatever's missing
- Un-ignore + body-fill the 3 tests
- Commit + push

## 4. Decisions

- **P3E-1:** Phase 3e is final credit closeout; subsequent phases pivot to models/marketmodels/ or experimental/
- **P3E-2:** Direct-to-main signed `-s` no Co-authored-by

## 5. Pause triggers

Carry-forward A1-A35.

## Outcome forecast

| Metric | Phase 3d tip | Phase 3e target |
|--------|--------------|-----------------|
| Tests | 1024/0/0/41 | ~1027/0/0/38 |
| Credit subsystem | 100% production / 98% test | 100% (production + test) |

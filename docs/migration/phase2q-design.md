# Phase 2q Design — Inflation YoY + CPI Families + Seasonality

**Status:** approved 2026-05-08 (autonomous mode — eighth autonomous phase)
**Predecessor:** `jquantlib-phase2p-complete` (tests `822/0/0/22`, scanner WIP=0)

## 1. Context & Motivation

Phase 2p closed the zero-inflation slice (curves + base coupon + ZeroCouponInflationSwap), bringing Java inflation coverage from ~30% to ~60% of C++ v1.42.1 surface. Phase 2q closes the YoY + CPI cashflow/instrument layer plus seasonality. Phase 2r will handle inflation caps/floors + engines + vol structures.

Phase 2p A.3 also surfaced two carry-forward align prereqs needed to complete the bonus refactor (delegating ZCIIS helper impliedQuote to ZeroCouponInflationSwap.fairRate):
- ZeroInflationIndex.clone(Handle<ZeroInflationTermStructure>) — match C++ Index::clone
- Swap.Results.{startDiscounts, endDiscounts} — match C++ DiscountingSwapEngine output

## 2. Scope

Two-layer phase:

**Layer A (sequential, small):** Phase 2p A.3 align unblockers
- A.1 `ZeroInflationIndex.clone(Handle<ZeroInflationTermStructure>)` + apply to YoYInflationIndex
- A.2 `Swap.Results.{startDiscounts, endDiscounts}` populated by `DiscountingSwapEngine`
- A.3 (bonus, time-permitting): `ZeroCouponInflationSwapHelper.impliedQuote()` delegates to `ZeroCouponInflationSwap.fairRate()`

**Layer B (parallel):** YoY + CPI completion

- **Track B (YoY family ~1700 LOC C++):**
  - `interpolatedyoyinflationcurve.hpp` (~177 LOC)
  - `piecewiseyoyinflationcurve.hpp` (~157 LOC)
  - `inflationhelpers.cpp` YearOnYearInflationSwapHelper (~150 LOC)
  - `cashflows/yoyinflationcoupon.{hpp,cpp}` (~374 LOC)
  - `instruments/yearonyearinflationswap.{hpp,cpp}` (~435 LOC)

- **Track C (CPI family + Seasonality ~1200 LOC C++):**
  - `cashflows/cpicoupon.{hpp,cpp}` (~648 LOC)
  - `cashflows/cpicouponpricer.{hpp,cpp}` (~241 LOC)
  - `cashflows/capflooredinflationcoupon.{hpp,cpp}` (~295 LOC)
  - `termstructures/inflation/seasonality.{hpp,cpp}` (~471 LOC)

Total Phase 2q C++ scope: ~3,300 LOC.

**Out of scope (Phase 2r):**
- inflationcapfloor.{hpp,cpp}
- cpicapfloor.{hpp,cpp}
- cpiswap.{hpp,cpp}
- makeyoyinflationcapfloor
- inflationcapfloorengines
- yoyinflationoptionletvolatilitystructure (+ experimental v2)

## 3. Approach

Two-layer with parallel L1 tracks. Single worktree A for L0 align prereqs (small + sequential dependencies); two parallel worktrees B + C for L1 cashflow/instrument ports.

**L0 → L1 ordering:** L0 align must land before L1 starts (YoY/CPI cashflows depend on inflation index handles being mutable).

## 4. Decisions

- **P2Q-1:** YoY family mirrors Phase 2p zero-family architecture (same package layout, same probe-before-port discipline)
- **P2Q-2:** CPI family adds CapFlooredInflationCoupon (uses existing CappedFlooredCoupon idiom from existing cashflow package)
- **P2Q-3:** Seasonality is cross-cutting between zero + yoy curves — port as `org.jquantlib.termstructures.inflation.Seasonality` and integrate into both curve types
- **P2Q-4:** Phase 2p A.3 bonus refactor (ZCIIS helper delegation) folded into L0 A.3 if Java has clean access pattern after A.1 + A.2; otherwise still deferred
- **P2Q-5:** Direct-to-main signed `-s` no Co-authored-by per standing rule

## 5. Pause triggers

Carry-forward A1-A26 + new **A27**: YoY/CPI surface discovers cross-cutting dependency on Phase 2r class (e.g., InflationCapFloor primitive). Bundle as separate align commit OR defer the dependent pieces to Phase 2r.

## Outcome forecast

| Metric | Phase 2p tip | Phase 2q target |
|--------|--------------|-----------------|
| Tests | 822/0/0/22 | ~830-840 (+8-18 across YoY + CPI + Seasonality) |
| Scanner WIP | 0 | 0 |
| Inflation surface coverage | 60% | 85% (caps/floors/engines = 15% remains for Phase 2r) |
| Phase 2p A.3 bonus refactor | deferred | done (if A.1+A.2 expose clean access) |

## Sub-layer order

L0 sequentially: A.1 → A.2 → A.3 (bonus). Each commit + push.
L1 parallel B + C after L0 lands.

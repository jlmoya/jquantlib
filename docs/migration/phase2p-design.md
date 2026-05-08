# Phase 2p Design — Inflation Subsystem Core (Zero family)

**Status:** approved 2026-05-08 (autonomous mode — seventh autonomous phase; Phase-3 transition begins)
**Predecessor:** `jquantlib-phase2o-complete` (tests `818/0/0/22`, scanner WIP=0)

## 1. Context & Motivation

QuantLib v1.42.1 provides a substantial inflation subsystem (~5,000 LOC C++ across termstructures, cashflows, instruments, engines, vol structures). Java has a partial foundation (`InflationTermStructure`, `YoYInflationTermStructure`, `ZeroInflationTermStructure`, `InflationIndex`, `ZeroInflationIndex`, `YoYInflationIndex`, plus 6 concrete index classes for UKRPI/EUHICP/YYUKRPI/YYEUHICP) but is missing the working surface: curves, helpers, cashflows, instruments, engines, vol structures.

Per binding exit criterion, every C++ class must have a Java equivalent. Phase 2p begins the inflation port.

## 2. Scope (MVP — zero family)

Trim to a coherent zero-inflation slice. Defer YoY + CPI + caps/floors + engines to Phase 2q+:

**In scope:**
- `termstructures/inflation/inflationtraits.hpp` (~50 LOC) — bootstrap traits
- `termstructures/inflation/interpolatedzeroinflationcurve.hpp` (~170 LOC) — interpolated zero curve
- `termstructures/inflation/piecewisezeroinflationcurve.hpp` (~186 LOC) — piecewise bootstrap
- `termstructures/inflation/inflationhelpers.{hpp,cpp}` zero-side only (~250 LOC) — bootstrap helpers
- `cashflows/inflationcoupon.{hpp,cpp}` (~236 LOC) — base inflation coupon
- `cashflows/inflationcouponpricer.{hpp,cpp}` (~422 LOC) — base pricer
- `cashflows/zeroinflationcashflow.{hpp,cpp}` (~139 LOC) — zero-coupon cashflow
- `instruments/zerocouponinflationswap.{hpp,cpp}` (~336 LOC) — zero-coupon inflation swap

Total C++ scope: ~1,800 LOC. Estimated Java port: ~3,000-3,500 LOC.

**Out of scope (Phase 2q+):**
- YoY family (interpolatedyoy, piecewiseyoy, yoyinflationcoupon, yearonyearinflationswap)
- CPI family (cpicoupon, cpicouponpricer, capflooredinflationcoupon)
- Seasonality (cross-cutting with both zero + yoy)
- Caps/floors + engines + vol structures

## 3. Approach

Three parallel tracks, single worktree A (smaller scale — no need for parallel worktrees within this phase):

- **A.1** `termstructures/inflation` — InflationTraits + InterpolatedZeroInflationCurve + PiecewiseZeroInflationCurve + ZeroInflationHelper. Foundation.
- **A.2** `cashflows` — InflationCoupon + InflationCouponPricer + ZeroInflationCashflow. Depends on A.1's curve.
- **A.3** `instruments` — ZeroCouponInflationSwap. Depends on A.1+A.2.

Sequential within the worktree. Three commits, each compiles + passes tests.

## 4. Decisions

- **P2P-1:** MVP scope = zero-inflation slice only. YoY + CPI + caps/floors deferred. Rationale: Phase 2j precedent showed that ambitious-scope phases trigger A16 fires; smaller bounded slices land more reliably.
- **P2P-2:** Probe oracle for inflation curves uses C++ QuantLib v1.42.1 directly via existing harness pattern. No new transcendental dependencies.
- **P2P-3:** Tier expectations:
  - InflationTraits / coupon discount: TIGHT (constants + interpolation)
  - PiecewiseCurve bootstrap: LOOSE (Newton solver convergence)
  - Engine NPVs: LOOSE
- **P2P-4:** Direct-to-main signed `-s` no Co-authored-by per standing rule.
- **P2P-5:** Naming convention: keep C++ class names (`ZeroCouponInflationSwap` not `ZeroCouponInflationSwapImpl` etc.). Capitalization matches Java conventions.

## 5. Pause triggers

Carry-forward A1-A25 + new **A26**: inflation scope discovers cross-cutting dependency on absent class (e.g., bootstrap traits framework not in Java). Bundle as align prereq commit before main port commit.

## Outcome forecast

| Metric | Phase 2o tip | Phase 2p target |
|--------|--------------|-----------------|
| Tests | 818/0/0/22 | ~821-825 (+3-7 inflation cross-validation tests) |
| Scanner WIP | 0 | 0 |
| Java inflation surface | 3 termstructure base + 6 indexes | + curves family + base coupon + swap |
| C++ inflation gap | full subsystem absent | zero-side closed; yoy/cpi/caps remain |

## Risk assessment

- **Risk 1:** Inflation bootstrap traits framework may need scaffolding if Java doesn't have generic bootstrap helper structure. Phase 2j Jamshidian had similar but smaller-scale precedent.
- **Risk 2:** Inflation indexes already exist in Java but the existing API may not match what curves + helpers need. Sub-commit may include align prereqs.
- **Risk 3:** Probe regen surface for inflation tests is new; may need to add `migration-harness/cpp/probes/termstructures/inflation/` directory + CMakeLists registration.

## Sub-layer order

A.1 → A.2 → A.3 sequential. Each commits + pushes before next starts.

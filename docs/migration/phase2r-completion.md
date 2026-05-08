# Phase 2r Completion — Inflation Subsystem 100% Surface Coverage

**Status:** complete (autonomous mode — ninth autonomous phase)
**Tag:** `jquantlib-phase2r-complete` @ `5565664`
**Predecessor:** `jquantlib-phase2q-complete` @ `f3acf32`
**Plan + Design:** `docs/migration/phase2r-{design,plan}.md`

## Final state

| Metric | Phase 2q tip | Phase 2r tip | Δ |
|--------|--------------|--------------|----|
| Tests | 832/0/0/22 | 853/0/0/22 | +21 |
| Scanner WIP | 0 | 0 | unchanged |
| **Inflation surface coverage (vs C++ v1.42.1)** | 85% | **100%** | full coverage |
| New solver | — | FiniteDifferenceNewtonSafe | +1 |

## What landed (8 commits)

### L0 — Sequential align prereqs

| Commit | Description |
|--------|-------------|
| `12822f5` | Phase 2r design + plan |
| `69589f8` | **L0 A.1:** FiniteDifferenceNewtonSafe solver port (mirrors C++ ql/math/solvers1d/) |
| `6fa3ed6` | **L0 A.2:** PiecewiseZero/YoYInflationCurve adopt FiniteDifferenceNewtonSafe per C++ IterativeBootstrap. **Tier discovery:** Phase 2q PiecewiseYoY 1e-5 loosening did NOT tighten — root cause is `Linear.global() == false` (non-global interpolator), so bootstrap exits after first iteration before FDNewtonSafe ever fires. Comment refreshed to reflect correct attribution. FDNewtonSafe will be exercised when global interpolators (Cubic splines) are used in future phases. |
| `550ebd4` | **L0 A.3:** YoYInflationIndex.fixing past-path matches v1.42.1 ratio_=false (returns stored YoY rate directly when ratio_=false). Phase 2q D.1 carry-forward closed; past-period YoY testing unblocked. |

### L1 Track B — YoY Vol structures (parallel worktree)

| Commit | Description |
|--------|-------------|
| `ba1be51` | **B:** YoYOptionletVolatilitySurface family — base abstract + ConstantYoYOptionletVolatility (TIGHT) + InterpolatedYoYOptionletVolatilityCurve (LOOSE) under `org.jquantlib.experimental.inflation`. ~778 LOC main + 378 test + 213 probe + 1228-LOC reference (105 cases). C++ v1.42.1 `yoyinflationoptionletvolatilitystructure2.hpp` defines exactly one class (canonical name InterpolatedYoYOptionletVolatilityCurve); ported under that name. |

### L1 Track C — Instruments + Engines + Pricers (parallel worktree, 3 sub-commits)

| Commit | Description |
|--------|-------------|
| `1cf1bbc` | **C.1 Instruments:** InflationCapFloor (309 LOC) + CPICapFloor (224) + CPISwap (393) + MakeYoYInflationCapFloor (220). 4 tests. inflation_cap_floor_probe with 4 structural cases. |
| `b79dd1d` | **C.3 Pricers:** YoYInflationCouponPricer (modified to base abstract) + BlackYoYInflationCouponPricer + UnitDisplacedBlackYoYInflationCouponPricer + BachelierYoYInflationCouponPricer. ~270 net new LOC + 1 test (YoYOptionletPricersTest). yoy_optionlet_pricer_probe with 9 cases TIGHT. |
| `5565664` | **C.2 Engines:** InflationCapFloorEngine (base) + Black/UnitDisplacedBlack/Bachelier InflationCapFloorEngine. inflation_cap_floor_engines_probe with 12 NPV cases LOOSE. |

## Track B/C coordination outcome

Track C started before Track B landed. Used Strategy 1: forward-declared `org.jquantlib.cashflow.YoYOptionletVolatilitySurface` placeholder. Mid-run, Track B landed (`ba1be51`); Track C performed `git pull --ff-only` and refactored to delete the placeholder and use Track B's canonical `org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface` directly. Net: zero collisions; engines + pricer tests pass against the canonical Track B surface.

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A28 (cross-track parallelism)** | Track C | YoYOptionletVolatilitySurface dependency on Track B handled via Strategy 1 (forward-declare → refactor on Track B landing). Clean. |
| **A19 (post-swap tier)** | L0 A.2 | FDNewtonSafe adoption did NOT tighten Phase 2q PiecewiseYoY 1e-5 loosening — root cause was Linear (non-global) interpolator, NOT solver choice. Inline comment refreshed. Tier stays 1e-5 with corrected attribution. Phase 2s seed: try Cubic global interpolator to actually exercise FDNewtonSafe. |

A1-A18, A20-A27 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2R-1** | L0 sequential → L1 parallel B+C | Phase 2q precedent; L0 unblocks both tracks |
| **P2R-2** | C++ v1.42.1 experimental v2 has only one vol curve class (InterpolatedYoYOptionletVolatilityCurve), not 4 | Plan misread; canonical port name used |
| **P2R-3** | Track C used Strategy 1 (forward-declare → refactor on Track B landing) | Clean parallelism; no race condition |
| **P2R-4** | A.2 tier didn't tighten — Linear non-global interpolator exits before FDNewtonSafe fires | Phase 2q P2Q-A19 assumption refined; FDNewtonSafe wired correctly but never exercised on Linear interpolated YoY. |
| **P2R-5** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 2s+ seed list

### From Phase 2r findings

1. **Try Cubic (global) interpolator in PiecewiseZero/YoY bootstrap** — would actually exercise FDNewtonSafe and likely tighten LOOSE 1e-5 to TIGHT.
2. **MakeYoYInflationCapFloor null-curve graceful handling** — current `build()` throws when both strike default and no curve are absent.
3. **CPISwap fixedRate=0.0 path** — FixedRateCoupon optimization not yet ported; throws when exercised.
4. **CPICapFloor pricing engine** — separate from InflationCapFloorEngines; needs CPI vol surface (not in v1.42.1 vol structures track).
5. **Extract yoyInflationLeg / CPILeg standalone builders** — currently inline in CPISwap and MakeYoYInflationCapFloor.

### Phase 2s — Inflation experimental subsystem (~2,751 LOC C++)

6. **KInterpolatedYoYOptionletVolatilitySurface** (208 LOC) — strike-time interpolated vol surface
7. **PiecewiseYoYOptionletVolatility** (234) — piecewise bootstrap
8. **InterpolatedYoYOptionletStripper + YoYOptionletStripper** (368) — strip optionlets from caps/floors
9. **YoYOptionletHelpers** (164) — calibration helpers
10. **CPICapFloorTermPriceSurface** (504) — CPI cap/floor term-price surface
11. **YoYCapFloorTermPriceSurface** (707) — YoY cap/floor term-price surface
12. **CPICapFloorEngines.experimental** (155) — CPI cap/floor engines
13. **Polynomial2DSpline** (107) — 2D spline interpolator (likely shared utility)
14. **GenericIndexes** (99)

### Higher carry-forwards (still relevant)

15. **HestonModel negative-rho cross-validation tests** (Phase 2o A.1 follow-up — needs heston probe).
16. **AndreasenHuge calibration loop** (Phase 2m Track D follow-up).
17. **U128.java shared util refactor** (code hygiene).
18. **Douglas ADI / FdmAffineModelTermStructure** (FdHullWhite floor).
19. **JQuantMath.lgamma** (still no source).

### Phase 3+ subsystem ports (post-inflation)

20. **C++ test-suite Java equivalents** — substantial scope.
21. **`models/marketmodels/`** — Libor Market Model.
22. **`termstructures/credit/`** — credit term structures + CDS + CDX.
23. **`experimental/`** (other than inflation/) — large surface.

## Out-of-scope (explicit, deferred)

- All Phase 2s+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma

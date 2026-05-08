# Phase 2p Completion — Inflation Subsystem Core (Zero family)

**Status:** complete (autonomous mode — seventh autonomous phase; Phase-3 transition begins)
**Tag:** `jquantlib-phase2p-complete` @ `dbc1648`
**Predecessor:** `jquantlib-phase2o-complete` @ `4cd1f48`
**Plan + Design:** `docs/migration/phase2p-{design,plan}.md`

## Final state

| Metric | Phase 2o tip | Phase 2p tip | Δ |
|--------|--------------|--------------|----|
| Tests | 818/0/0/22 | 822/0/0/22 | +4 (3 new test classes incl. multi-case) |
| Scanner WIP | 0 | 0 | unchanged |
| Java inflation surface | 3 termstructure base + 6 indexes | + zero-curve family + base coupon + cashflow + swap | substantial |
| Java packages added | — | `org.jquantlib.termstructures.inflation` | +1 |
| Inflation subsystem coverage (vs C++ v1.42.1) | indexes only (~1500/5000 LOC = 30%) | + curves + cashflows + zero-coupon swap (~3000/5000 LOC = 60%) | +30% |

## What landed (8 commits across single worktree A)

| Commit | Description |
|--------|-------------|
| `0c4c0d1` | Phase 2p design + plan |
| `39bf08d` | **A.1 align prereq:** assign frequency field in InflationIndex constructor |
| `0006c05` | **A.1:** zero-inflation curves family — InflationTraits + InterpolatedZeroInflationCurve + PiecewiseZeroInflationCurve + ZeroCouponInflationSwapHelper. ~1125 LOC main + 409 test + 239 probe + 469-line JSON (43 reference cases). New package `org.jquantlib.termstructures.inflation`. Self-contained inflation-only bootstrap loop (existing JQuantLib bootstrap framework was tightly-coupled to YieldTermStructure). |
| `63bf448` | **A.2 align prereq:** null-safe addFixings + match v1.42.1 ZeroInflationIndex.forecastFixing (`period(baseDate).first` time anchor + `period(fixingDate).first` for both interpolated/uninterpolated cases per C++ inflationYearFraction(NoInterpolation) convention) |
| `f6829a2` | **A.2:** InflationCoupon + InflationCouponPricer + IndexedCashFlow + ZeroInflationCashFlow + CPI namespace. ~822 LOC main + 208 test + probe + JSON (8 reference cases TIGHT). |
| `dbc1648` | **A.3:** ZeroCouponInflationSwap. ~441 LOC main + 241 test + 194 probe + JSON (5 reference cases TIGHT). Local Type enum mirrors VanillaSwap.Type / BMASwap.Type idiom. fairRate() works without engine (closed-form). legBPS()/legNPV()/NPV() route through DiscountingSwapEngine. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A26 (cross-cutting align prereq)** | A.1 | InflationIndex frequency field uninitialized — bundled as separate align commit `39bf08d` per project rule |
| **A26** | A.2 | Index.addFixings null-safety + ZeroInflationIndex.forecastFixing C++ alignment — bundled as separate align commit `63bf448` |
| **A.3 BLOCKED partial — bonus refactor** | A.3 | ZCIIS helper impliedQuote delegation to ZeroCouponInflationSwap.fairRate blocked by missing ZeroInflationIndex.clone(Handle) and non-relinkable handle. Documented as Phase 2q seed; A.1's closed-form impliedQuote remains correct. |

A1-A25 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2P-1** | MVP scope = zero-inflation slice only (YoY + CPI + caps deferred) | Phase 2j precedent — bounded scope reduces A16 fires |
| **P2P-2** | Inflation-only bootstrap loop in PiecewiseZeroInflationCurve.performCalculations | Existing JQuantLib bootstrap framework tightly-coupled to YieldTermStructure; refactoring would have inflated scope |
| **P2P-3** | Java `cashflow` package singular (not plural `cashflows`) | Existing JQuantLib convention; A.2 followed it |
| **P2P-4** | Local Type enum on ZeroCouponInflationSwap | Mirrors existing VanillaSwap.Type idiom; keeps class self-contained |
| **P2P-5** | fixedLegBPS recovered algebraically via legNPV/fixedAmount | Java Swap.Results lacks endDiscounts (C++ populates them in DiscountingSwapEngine); algebraic recovery exact when fixedRate ≠ 0 |
| **P2P-6** | A.3 bonus refactor deferred to Phase 2q | Requires either ZeroInflationIndex.clone(Handle) addition OR promotion of internal handle to RelinkableHandle — both base-class touches outside A.3 scope |
| **P2P-7** | Direct-to-main signed `-s` no Co-authored-by per standing rule | |

## Phase 2q+ seed list

### Direct A.3 bonus refactor unblockers

1. **`ZeroInflationIndex.clone(Handle<ZeroInflationTermStructure>)`** — match C++ `Index::clone`. Unblocks ZCIIS bootstrap from interpolated-observation quotes (CPI.Linear).
2. **`Swap.Results.startDiscounts`/`endDiscounts`** — match C++ DiscountingSwapEngine output; simplify ZCIIS BPS calculation.

### Phase 2q — YoY + CPI inflation completion

3. **YoY inflation family:** InterpolatedYoYInflationCurve, PiecewiseYoYInflationCurve, YoYInflationCoupon, YearOnYearInflationSwap. ~1700 LOC C++.
4. **CPI inflation family:** CPICoupon, CPICouponPricer, CapFlooredInflationCoupon. Add specialised CPISwap, CPICapFloor variants. ~750 LOC C++.
5. **Seasonality** — cross-cutting class for both zero + yoy curves.

### Phase 2r — Inflation caps/floors + engines + vol structures

6. **Inflation caps/floors:** InflationCapFloor, MakeYoYInflationCapFloor.
7. **Inflation engines:** InflationCapFloorEngines (Bachelier branch, Black-DD, Unit-Displaced).
8. **YoYInflationOptionletVolatilityStructure** + experimental v2 variant.

### Higher carry-forwards (still relevant)

9. **HestonModel negative-rho cross-validation tests** (Phase 2o A.1 follow-up — needs heston probe).
10. **AndreasenHuge calibration loop** (Phase 2m Track D follow-up).
11. **U128.java shared util refactor** (code hygiene).
12. **Douglas ADI / FdmAffineModelTermStructure** (FdHullWhite floor).
13. **JQuantMath.lgamma** (still no source).

### Phase 3+ subsystem ports

14. **C++ test-suite Java equivalents** — substantial scope.
15. **`experimental/`** — large surface.
16. **`models/marketmodels/`** — Libor Market Model.
17. **`termstructures/credit/`** — credit term structures + CDS + CDX.

## Out-of-scope (explicit, deferred)

- All Phase 2q+ items above
- Inflation caps/floors / engines / vol (Phase 2r)
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma

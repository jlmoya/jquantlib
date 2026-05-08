# Phase 2q Completion — Inflation YoY + CPI Families + Seasonality

**Status:** complete (autonomous mode — eighth autonomous phase)
**Tag:** `jquantlib-phase2q-complete` @ `f3acf32`
**Predecessor:** `jquantlib-phase2p-complete` @ `dbc1648`
**Plan + Design:** `docs/migration/phase2q-{design,plan}.md`

## Final state

| Metric | Phase 2p tip | Phase 2q tip | Δ |
|--------|--------------|--------------|----|
| Tests | 822/0/0/22 | 832/0/0/22 | +10 |
| Scanner WIP | 0 | 0 | unchanged |
| Inflation surface coverage (vs C++ v1.42.1) | 60% | 85% | +25% |
| Java inflation files | curves family + base coupon + zero swap | + YoY family + CPI family + Seasonality + CapFlooredYoY | substantial |

## What landed (10 commits)

### L0 — Align prereqs (sequential)

| Commit | Description |
|--------|-------------|
| `88027d1` | Phase 2q design + plan |
| `2cb1a51` | **L0 A.1:** ZeroInflationIndex/YoYInflationIndex `clone(Handle)` per C++ Index::clone |
| `4685830` | **L0 A.2:** DiscountingSwapEngine populates `Swap.Results.startDiscounts/endDiscounts` per C++ v1.42.1 |
| `f66a52a` | **L0 A.3 (bonus):** ZeroCouponInflationSwapHelper.impliedQuote delegates to ZeroCouponInflationSwap.fairRate via cloned-index pattern (Phase 2p A.3 carry-forward closed) |

### L1 Track B — YoY family (parallel worktree)

| Commit | Description |
|--------|-------------|
| `b01a9b9` | **B:** Full YoY family — InterpolatedYoYInflationCurve + PiecewiseYoYInflationCurve + YearOnYearInflationSwapHelper + YoYInflationCoupon + YearOnYearInflationSwap + YoYInflationCouponPricer (base) + YoYInflationTraits + CPI.laggedYoYRate. ~1822 LOC main + 660 test + 482 probe. 28 curve probe cases + 4 swap probe cases. PiecewiseYoY ~1e-5 inline-justified loosening on 4 cases due to C++ FDNewtonSafe vs Java Brent solver convergence drift (~3.5e-6 abs / ~1e-4 rel on 10Y bootstrap node). Same root cause as Phase 2p loosening. |

### L1 Track C — CPI family + Seasonality (parallel worktree)

| Commit | Description |
|--------|-------------|
| `61a2998` | **C.2:** Seasonality + MultiplicativePriceSeasonality + KerkhofSeasonality. InflationTermStructure additive setSeasonality()/seasonality()/hasSeasonality() (30 LOC). InterpolatedZeroInflationCurve.zeroRate(d, ext) wired (7 LOC). ~467 LOC main + 200 test + 199 probe. 60+ probe cases TIGHT. |
| `9492f85` | **C.1:** CPICoupon + CPICouponPricer + CPICashFlow. ~675 LOC main + 431 test + 313 probe. 11 probe cases TIGHT. |

### Phase 2q D — Cross-track follow-ups

| Commit | Description |
|--------|-------------|
| `618e2a3` | **D.1:** CappedFlooredYoYInflationCoupon (was Track C scope but blocked by parallel ordering on Track B's YoYInflationCoupon). 290 LOC main + 280 test + 280 probe + 367 ref. 10 probe cases TIGHT (5 PASS_ pass-through + 5 META_ metadata). |
| `f3acf32` | **D.2:** InterpolatedYoYInflationCurve.yoyRate(d, ext) seasonality wiring matching C++ pattern. 7-LOC infra change + 6 new probe cases + test sub-assertions. |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A19 (post-swap tier)** | L1 Track B PiecewiseYoY | C++ FDNewtonSafe vs Java Brent solver convergence drift; per-test 1e-5 inline-justified on 4 cases. Phase 2r seed: port FDNewtonSafe and adopt in PiecewiseZero/YoY for tighter bootstrap convergence. |
| **A26 (cross-cutting align prereq)** | L0 | 3 align prereqs landed cleanly (clone(Handle) + endDiscounts + impliedQuote delegation) |
| **A27 (cross-track parallelism)** | Track C deferred CapFlooredYoYInflationCoupon to D.1 | YoY/CPI cross-track dependency couldn't run in parallel; closed at D.1 after Track B landed |
| **Pre-existing Java divergence (D.1 finding)** | YoYInflationIndex.fixing past-path | Java applies ratio formula even when ratio_=false; C++ returns stored YoY rate directly. D.1 worked around with future-only accrual periods. Phase 2r seed. |

A1-A18, A20-A25 did not fire. A2/A3/A4 (transcendental) inactive — no transcendental work in this phase.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2Q-1** | Sequential L0 → parallel L1 (B + C) | L0 align prereqs unblock both tracks; B and C have minimal overlap (only shared base class additive change) |
| **P2Q-2** | Track B writes separate YoYInflationTraits class (not parameterized InflationTraits) | YoY traits differ in initialValue (baseRate, not AVG_INFLATION) and updateGuess (does NOT propagate to data[0]) |
| **P2Q-3** | Seasonality wiring in InterpolatedZeroInflationCurve only (Track C) | Track C scope; YoY wiring was deferred to Phase 2q D.2 to avoid cross-track conflicts |
| **P2Q-4** | A.3 bonus refactor confirmed bit-equivalent to closed-form | Phase 2p tests still pass at original tier; no regression |
| **P2Q-5** | CapFlooredYoYInflationCoupon ported as Phase 2q D.1 (not deferred to 2r) | Mechanical given precedents, fits Phase 2q's "inflation cashflow/instrument layer closeout" theme |
| **P2Q-6** | InterpolatedZeroInflationCurve.zeroRate(time, ext) does NOT apply seasonality | Mirrors C++ inflationtermstructure.cpp lines 134-180; only date-based override applies seasonality |
| **P2Q-7** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 2r+ seed list

### Direct Phase 2q follow-ups

1. **`FiniteDifferenceNewtonSafe` solver port + adoption in PiecewiseZero/YoY bootstrap** — would tighten LOOSE 1e-5 to TIGHT for both bootstrap families.
2. **`align(indexes): YoYInflationIndex.fixing past-path matches v1.42.1 ratio_=false`** — Java currently always applies ratio formula; C++ returns stored YoY rate directly when ratio_=false. Unblocks past-period YoY testing.
3. **CPIVolatilitySurface + YoYOptionletVolatilitySurface** — needed for cap/floor pricer paths in CPICouponPricer and YoYInflationCouponPricer (currently throw on caplet/floorlet calls).

### Phase 2r — Inflation caps/floors + engines + vol structures

4. **Instruments:** InflationCapFloor (~394 LOC C++), CPICapFloor (~258 LOC), CPISwap (~444 LOC), MakeYoYInflationCapFloor (~240 LOC). Total ~1,336 LOC C++.
5. **Engines:** InflationCapFloorEngines — Bachelier branch, Black-DD, Unit-Displaced (~300 LOC C++).
6. **Vol structures:** YoYInflationOptionletVolatilityStructure (~438 LOC C++) + experimental v2 variant (~190 LOC).
7. **Cap/floor pricers:** Black/UnitDisplaced/BachelierYoYInflationCouponPricer.
8. **Cap/floor leg builder:** yoyInflationLeg (analog of cmsLeg / iborLeg).

Total Phase 2r forecast scope: ~2,264 LOC C++.

### Higher carry-forwards (still relevant)

9. **HestonModel negative-rho cross-validation tests** (Phase 2o A.1 follow-up — needs heston probe).
10. **AndreasenHuge calibration loop** (Phase 2m Track D follow-up).
11. **U128.java shared util refactor** (code hygiene).
12. **Douglas ADI / FdmAffineModelTermStructure** (FdHullWhite floor).
13. **JQuantMath.lgamma** (still no source).

### Phase 3+ subsystem ports (post-inflation)

14. **C++ test-suite Java equivalents** — substantial scope.
15. **`experimental/`** — large surface.
16. **`models/marketmodels/`** — Libor Market Model.
17. **`termstructures/credit/`** — credit term structures + CDS + CDX.

## Out-of-scope (explicit, deferred)

- All Phase 2r+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma

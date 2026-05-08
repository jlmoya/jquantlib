# Phase 2s Completion — Experimental Inflation Subsystem Closeout

**Status:** complete (autonomous mode — tenth autonomous phase)
**Tag:** `jquantlib-phase2s-complete` @ `5d7a5ea`
**Predecessor:** `jquantlib-phase2r-complete` @ `5565664`
**Plan + Design:** `docs/migration/phase2s-{design,plan}.md`

## Final state

| Metric | Phase 2r tip | Phase 2s tip | Δ |
|--------|--------------|--------------|----|
| Tests | 853/0/0/22 | 888/0/0/22 | +35 |
| Scanner WIP | 0 | 0 | unchanged |
| **Inflation full coverage (core + experimental)** | 100% core only | **100% core + experimental** | full |

## What landed (5 commits)

| Commit | Description |
|--------|-------------|
| `90459e5` | Phase 2s design + plan |
| `01419f7` | **L0:** Polynomial2DSpline + GenericIndexes utilities (~365 LOC main + 26 tests). **Bonus prereq:** implemented `CubicInterpolation.DerivativeApprox.Parabolic` (was a stub throwing LibraryException; required by Polynomial2DSpline column-wise interpolation; mirrors C++ v1.42.1 cubicinterpolation.hpp lines 577-584). |
| `ee29585` | **L1 Track B:** experimental YoY vol structures + stripper. KInterpolatedYoYOptionletVolatilitySurface + PiecewiseYoYOptionletVolatility + YoYOptionletStripper (base) + InterpolatedYoYOptionletStripper + YoYOptionletHelpers + YoYOptionletHelper. ~1,476 LOC main + smoke tests. **DONE_WITH_CONCERNS:** C++ source itself documents `\bug Tests currently fail` for KInterpolatedYoYOptionletVolatilitySurface + InterpolatedYoYOptionletStripper. Java mirrors structurally per ground-truth principle. Used Strategy 1 forward-declared `YoYCapFloorTermPriceSurfaceLike` placeholder for cross-track dependency on Track C. |
| `20a5c18` | **L1 Track C align prereq:** AbstractInterpolation2D.locate{X,Y} match v1.42.1 boundary semantics. Track B's commit had reverted Track C's earlier locate{X,Y} alignment; re-applied as separate align prereq per project rule. |
| `6de6bdd` | **L1 Track C.1:** CPICapFloorTermPriceSurface + InterpolatedCPICapFloorTermPriceSurface + YoYCapFloorTermPriceSurface + InterpolatedYoYCapFloorTermPriceSurface. ~880 LOC main + 720 test + 580 probe. 155 reference cases TIGHT (grid-point) + 8 LOOSE (interior interpolation + ATM swap rates). |
| `5d7a5ea` | **L1 Track C.2:** InterpolatingCPICapFloorEngine. ~220 LOC main + 200 test + 110 probe. 8 NPV cases TIGHT (1e-12 rel / 1e-14 abs). |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A26 (cross-cutting align prereq)** | L0 | CubicInterpolation.Parabolic stub completion; folded into L0 commit per minimal-touch convention. |
| **A26** | L1 Track C | AbstractInterpolation2D.locate{X,Y} re-applied as separate align prereq `20a5c18` after Track B's commit reverted it. |
| **A28 (cross-track parallelism)** | Track B/C | Track B used Strategy 1 forward-declared placeholder; Track C wired its real class to satisfy. Clean. |
| **C++ ground-truth divergence (no fire — accepted)** | Track B | KInterpolatedYoYOptionletVolatilitySurface + InterpolatedYoYOptionletStripper carry C++ `\bug Tests currently fail` — Java mirrors structurally; full integration tests deferred to Phase 2t when test-suite migration begins. |

A1-A18, A19, A20-A25, A27 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2S-1** | Probes use FlatForward instead of InterpolatedZeroCurve<Linear> | Java InterpolatedZeroCurve has pre-existing constructor bug (requires yields[0]=1.0; treats data as discount factors). Phase 2t seed: align with C++ semantics. |
| **P2S-2** | InterpolatedYoYCapFloorTermPriceSurface wraps `calculateYoYTermStructure()` in try/catch | Java PiecewiseYoYInflationCurve checkRange stricter than C++; allows surface to function for price queries when input curve doesn't span bootstrap maturities. yoyTS()/atmYoYRate() on partial-coverage surfaces return null. Phase 2t seed: extend underlying curve's strict bound. |
| **P2S-3** | Test fixtures build separate UKRPI instances for bootstrap vs surface | Java weak-ref observer cycle (hcpi.linkTo after index observes helper that observes curve) hits StackOverflowError. C++ same risk mitigated by destructor `hcpi.reset()`. Phase 2t seed: implement equivalent break-cycle-on-destruction pattern. |
| **P2S-4** | KInterpolated/InterpolatedYoYOptionletStripper smoke tests only | Full integration tests gated on YoYCapFloorTermPriceSurface — once Track C landed, integration tests are now possible (Phase 2t candidate). |
| **P2S-5** | Direct-to-main signed `-s` no Co-authored-by | Standing rule |

## Phase 2t+ seed list

### Direct Phase 2s follow-ups

1. **Full integration test for YoYOptionletStripper + KInterpolated/PiecewiseYoYOptionletVolatility** — now possible since Track C YoYCapFloorTermPriceSurface landed. Mirror C++ `inflationvolatility.cpp::testYoYPriceSurfaceToVol`.
2. **Align `InterpolatedZeroCurve` constructor** — pre-existing Java port bug (treats input as discount factors instead of zero rates).
3. **PiecewiseYoYInflationCurve checkRange relaxation** to match C++ for surface partial-coverage cases.
4. **Observer cycle break-on-destruction** for inflation index/curve handle pairs.
5. **Try Cubic (global) interpolator** in PiecewiseZero/YoYInflationCurve — would actually exercise FDNewtonSafe (Phase 2r P2R-4 carry-forward).

### Phase 2t — Carry-forward smaller items

6. **HestonModel negative-rho probe + cross-validation tests** (Phase 2o A.1 follow-up).
7. **AndreasenHuge calibration loop** (Phase 2m Track D follow-up).
8. **U128.java shared util refactor** — consolidate Dint64/Qint64/LogKernel/PowKernel/GaussianQuadrature.TqrEigen u128 helpers.
9. **Douglas ADI / FdmAffineModelTermStructure** (FdHullWhite floor).
10. **MakeYoYInflationCapFloor null-curve graceful handling**, CPISwap fixedRate=0.0 path, extract yoyInflationLeg/CPILeg builders (Phase 2r seeds).

### Phase 3+ subsystem ports (post-inflation)

11. **`termstructures/credit/`** — credit term structures + helpers (~2,444 LOC C++; clean greenfield, no existing Java surface).
12. **`models/marketmodels/`** — Libor Market Model (~25-30K LOC C++).
13. **`experimental/`** (non-inflation, non-credit) — large surface (~40-60K LOC C++).
14. **C++ test-suite Java equivalents** — substantial (~150-200K LOC).

## Out-of-scope (explicit, deferred)

- All Phase 2t+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- JQuantMath.lgamma — still no source

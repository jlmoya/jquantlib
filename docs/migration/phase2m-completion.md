# Phase 2m Completion — Fdm-Dependent Engines + AndreasenHuge LocalVol

**Status:** complete (autonomous mode — fourth autonomous phase)
**Tag:** `jquantlib-phase2m-complete` @ `<FILL_AT_TAG>`
**Predecessor:** `jquantlib-phase2l-complete` @ `9dab878`
**Plan + Design:** `docs/migration/phase2m-{design,plan}.md` (commit `943d904`)

## Final state

| Metric | Phase 2l tip | Phase 2m tip | Δ |
|--------|--------------|--------------|----|
| Tests | 812/0/0/22 | 816/0/0/22 | +4 |
| Scanner WIP | 0 | 0 | unchanged |
| New Fdm-dependent engines | — | FdBlackScholesVanilla + FdHestonHullWhiteVanilla + FdSabrVanilla | 3 ✅ |
| AndreasenHuge LocalVol family | absent | 3 classes (Interpolation + VolatilityAdapter + LocalVolAdapter) | ✅ |
| Total commits | — | 8 (Track A 1 + Track B 1 + Track C 2 incl. align + Track D 4 incl. align) | |

## What landed (8 commits across 4 parallel worktrees)

### Track A — FdBlackScholesVanillaEngine ✅

| Commit | Description |
|--------|-------------|
| `47bf7bb` | `FdBlackScholesVanillaEngine` + 5 prereq classes (Concentrating1dMesher, FdmBlackScholesMesher, FdmLogInnerValue, FdmBlackScholesOp, FdmBlackScholesSolver). 33-case test (European/American × 4 vols × strikes/maturities × Douglas+ImplicitEuler × grid sizes × damping). Tier: LOOSE (1e-8 NPV+delta; 1e-7 gamma inline-justified). Deferred A16: Escrowed dividends, localVol branch, Quanto helper, CrankNicolsonType variant. |

### Track B — FdHestonHullWhiteVanillaEngine ✅

| Commit | Description |
|--------|-------------|
| `126f0cd` | `FdHestonHullWhiteVanillaEngine` + 4 prereq classes (FdmHestonVarianceMesher non-central χ² grid, FdmHestonHullWhiteOp 3-factor PDE op with rho_sv/rho_sr cross-correlations, Fdm3DimSolver 3D FD solver, FdmHestonHullWhiteSolver). Plus extension to FdmBlackScholesMesher.processHelper(). Tier: within(1e-2) — Java `HestonModel` stores `rho` under `PositiveConstraint` (pre-existing divergence from C++ `BoundaryConstraint(-1,1)`); test uses rho=+0.3 to stay within constraint. controlVariate=false. |

### Track C — FdSabrVanillaEngine ✅

| Commit | Description |
|--------|-------------|
| `e6a1d6e` | **Align prereq:** `align(math.interpolations)` — fixed `AbstractInterpolation2D` to validate vx/vy independently. Latent bug exposed by FdSabrVanillaEngine's asymmetric 400×50 grid. |
| `23d683d` | `FdSabrVanillaEngine` + 7 prereq classes (FdmCEV1dMesher, CEVRNDCalculator, FdmTimeDepDirichletBoundary, FdmDiscountDirichletBoundary, FdmCellAveragingInnerValue, FdmSabrOp, JQuantMath.log version of Concentrating1dMesher). 36-case test, LOOSE 1e-8. |

### Track D — AndreasenHuge LocalVol family ✅

| Commit | Description |
|--------|-------------|
| `8300816` | **Align prereq:** `align(instruments,pricingengines)` — `Option.exercise()`/`payoff()` public accessors + `BlackFormula.blackFormulaImpliedStdDevLiRS` (LiRS fixed-point solver, port of C++ v1.42.1). Surfaced by AndreasenHuge code paths needing cross-package access. |
| `edc3144` | **D.1:** `AndreasenHugeVolatilityInterpl` (~858 LOC). LiRS round-trip uses Acklam ICN instead of C++ Maddock ICN — actual residual ~1e-10, well within LOOSE 1e-8. Inline-justified. |
| `a35bd45` | **D.2:** `AndreasenHugeVolatilityAdapter` (~102 LOC). |
| `8000dc1` | **D.3:** `AndreasenHugeLocalVolAdapter` (~83 LOC). |

## A-trigger fire history

| Trigger | Where | Outcome |
|---------|-------|---------|
| **A15 (latent bug)** | Track C | `AbstractInterpolation2D` shared vx/vy validation crashed on asymmetric grids; fixed in `e6a1d6e` align prereq commit. |
| **A16 (planned scope expansion)** | All tracks | Each track surfaced 4-7 prereq classes (meshers, operators, RND calculators, boundary conditions, etc.) — all bundled into the track's main commit. Track B surfaced `Fdm3DimSolver` + 3 supporting classes; Track C surfaced 7 helpers; Track D surfaced 2 align targets. |
| **A16 collision** | Concentrating1dMesher | Track A and Track D both ported it; resolved by keeping Track A's simpler 5-param version + Track C upgrading to JQuantMath.log. |
| **A19** | Tracks A, B, C, D LiRS | LOOSE tier accepted for engine NPVs (1e-8 to 1e-2 range depending on numerical depth). Tier matches design §4 risk 2. |
| **A22 (constraint mismatch)** | Track B | Java `HestonModel.rho` stored under `PositiveConstraint` vs C++ `BoundaryConstraint(-1,1)` — pre-existing divergence; test uses rho=+0.3 to stay within constraint. Documented inline. Future Phase 2n+ candidate to align HestonModel constraints with C++. |

A2/A3/A4/A6/A8/A9/A13/A17/A18/A20/A21/A23 did not fire.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2M-8** | Track B accepts within(1e-2) tier due to Java HestonModel rho-constraint divergence | Pre-existing PositiveConstraint vs C++ BoundaryConstraint(-1,1); restricts test to positive rho. Future align candidate. |
| **P2M-9** | Track D LiRS uses Acklam ICN instead of Maddock ICN | Residual ~1e-10 (within LOOSE 1e-8); pre-existing Java `InverseCumulativeNormal` is Acklam, not Maddock. Aligning to Maddock is a Phase 2n+ candidate (low priority — error well within tolerance). |
| **P2M-10** | Track C align fix `e6a1d6e` `AbstractInterpolation2D` validation | Genuine pre-existing bug surfaced by Track C's asymmetric 400×50 grid usage. Bundled as separate align commit per project rule (vs hidden inside infra commit). |
| **P2M-11** | Concentrating1dMesher resolution: Track A's 5-param version kept, Track C upgrades to JQuantMath.log | Simpler API satisfies all known callers; Track D was using a stale signature anyway. |

## Phase 2n+ seed list

### High-leverage carry-forwards

1. **HestonModel rho constraint align** — change `PositiveConstraint` → `BoundaryConstraint(-1,1)` to match C++ v1.42.1. Unblocks Track B FdHestonHullWhite tier improvement + general Heston correctness for negative-rho cases.
2. **`JQuantMath.pow`** — empirical leverage rising; sites now: GsrProcessCore (Phase 2j-pre B3), MethodOfLinesScheme (Phase 2l C.5), and likely some FdmBlackScholesOp pricing paths (Phase 2m). Worth a focused port phase.
3. **C++ test-suite Java equivalents** — every C++ engine test deserves a Java equivalent. Substantial scope (~150-200K LOC of tests across the project).
4. **AndreasenHuge calibration** — Track D ported the surfaces but not their calibration loop. Future phase.

### Other carry-forwards

5. **`SABRInterpolation` shifted-strike support** — Phase 2k Track A Scenario C unblocker.
6. **`U128.java` shared util extraction** — refactor candidate.
7. **`JQuantMath.lgamma`** — still no path; carry-forward.
8. **Douglas ADI / FdmAffineModelTermStructure** — FdHullWhite real floor (Phase 2i WI-2 B-1 A19).

### Phase 3+ subsystem ports

9. **`experimental/`** — large surface
10. **`models/marketmodels/`** — Libor Market Model family
11. **`termstructures/credit/`** — credit term structures + CDS + CDX
12. **`inflation/`** — inflation indexes + curves + linkers + caps/floors

## Out-of-scope (explicit, deferred)

- All Phase 2n+ items above
- BroadieKaya retry — needs pow + lgamma
- NCCS EXACT — needs lgamma
- FdConvertibleBond — does not exist in v1.42.1

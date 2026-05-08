# Phase 2o Completion — Constraint Aligns + SABR Shifted-Strike Activation

**Status:** complete (autonomous mode — sixth autonomous phase)
**Tag:** `jquantlib-phase2o-complete` @ `4cd1f48`
**Predecessor:** `jquantlib-phase2n-complete` @ `7de3c1e`
**Plan + Design:** `docs/migration/phase2o-{design,plan}.md`

## Final state

| Metric | Phase 2n tip | Phase 2o tip | Δ |
|--------|--------------|--------------|----|
| Tests | 818/0/0/22 | 818/0/0/22 | 0 (Scenario C activations don't change count — they were already in the test class as parameterized cases) |
| Scanner WIP | 0 | 0 | unchanged |
| HestonModel constraints | 5×PositiveConstraint | 4×Positive + 1×Boundary(-1,1) for rho | C++ aligned |
| BlackFormula `strike >= 0` guards | 3 unshifted | 3 shifted (`strike+displacement >= 0`) | aligned |
| SABRInterpolation Scenario C status | skipped (Phase 2k carry-forward) | active (21 cases LOOSE) | unblocked |

## What landed (3 commits)

| Commit | Description |
|--------|-------------|
| `2943f65` | Phase 2o design + plan |
| `4dd32f5` | **A.1:** `align(model.equity.HestonModel): rho uses BoundaryConstraint(-1,1) per C++ v1.42.1`. 1-line change at HestonModel.java:67. Existing rho=+0.3 cases still pass (FdHestonHullWhite within(1e-2) preserved). Negative-rho cross-validation cases deferred (need C++ probe regen at `migration-harness/cpp/probes/equity/heston_probe.cpp`). |
| `4cd1f48` | **A.2:** `align(pricingengines.BlackFormula): allow strike+displacement>=0 in stdDevDerivative; activate SABR shifted-strike path`. Three `QL.require(strike >= 0)` sites relaxed (blackFormulaImpliedStdDevApproximation, blackFormulaImpliedStdDev, blackFormulaStdDevDerivative). SABRInterpolation.op() and XABRInterpolationImpl.value() updated to pre-apply shift, matching C++ `shiftedSabrVolatility` semantics. SabrInterpolatedSmileSectionTest Scenario C unskipped — 21 C_* cases now active passing at LOOSE. |

**A.3 (sweep) — no commit:** the Math.pow tier-promotion sweep found zero actionable changes. Phase 2n A.2's MOL_TOL comment refresh was already correct. All remaining LOOSE-tier tests have correctly-attributed non-pow residuals (Gaussian quadrature integration, Fourier-CDF, LM-optimizer vs Boost, FD accumulation, Brent+Parameter-fitting, adaptive ODE step-size selection).

## A-trigger fire history

A1-A25 did not fire. Phase 2o was the smoothest autonomous-mode phase to date.

## Decision log additions

| # | Decision | Rationale |
|---|----------|-----------|
| **P2O-1** | HestonModel rho is BoundaryConstraint(-1,1) per C++ v1.42.1 | Ground-truth alignment; 1-line port-bug fix. |
| **P2O-2** | BlackFormula's three displacement-aware guards relax to `strike+displacement >= 0` | Consistency with line 118's pattern; enables shifted-SABR negative-strike path. |
| **P2O-3** | SABRInterpolation + XABRInterpolationImpl pre-apply shift before calling SABRSpecs.volatility() | The C++ `shiftedSabrVolatility` is the source-of-truth; SABRSpecs interface stays minimal. |
| **P2O-4** | A.3 sweep produces zero changes — that IS the correct empirical outcome | All currently-documented LOOSE tier justifications correctly attribute non-pow residuals; Phase 2n A.2 already cleared the Math.pow ones. |

## Phase 2p+ seed list

### Promotion-readiness pending probe regen

1. **HestonModel negative-rho cross-validation** — need to add `heston_probe.cpp` cases at rho=-0.3, rho=-0.7 to generate oracle values. Then add corresponding Java test cases. Once landed, FdHestonHullWhite tier may promote from within(1e-2) to tighter LOOSE/TIGHT.

### High-leverage carry-forwards (from Phase 2n+)

2. **AndreasenHuge calibration loop** — Phase 2m Track D ported the surfaces but not the calibration; substantive work.
3. **`U128.java` shared util refactor** — consolidate u128 helpers across `Dint64`, `Qint64`, `LogKernel`, `PowKernel`, `GaussianQuadrature.TqrEigen`. Mostly mechanical.
4. **PowKernel stage-3 (Qint64 chain + exact_pow)** — only on demand; no oracle case currently triggers stage-2 fall-through.
5. **Douglas ADI / FdmAffineModelTermStructure** — FdHullWhite real floor (Phase 2i WI-2 B-1 A19).

### Other carry-forwards

6. **`JQuantMath.lgamma`** — still no source.
7. **HestonProcess discountBondOption** — Phase 2c B-3 carry-forward (depends on aligning Java's NonCentralChiSquaredDistribution).

### Phase 3+ subsystem ports

8. **C++ test-suite Java equivalents** — substantial.
9. **`experimental/`** — large surface.
10. **`models/marketmodels/`** — Libor Market Model.
11. **`termstructures/credit/`** — credit term structures + CDS + CDX.
12. **`inflation/`** — inflation indexes + curves + linkers.

## Out-of-scope (explicit, deferred)

- All Phase 2p+ items above
- BroadieKaya retry — needs lgamma
- NCCS EXACT — needs lgamma
- FdConvertibleBond — does not exist in v1.42.1

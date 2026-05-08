# Phase 2o Design — Constraint Aligns + Tier-Promotion Sweep

**Status:** approved 2026-05-08 (autonomous mode — sixth autonomous phase)
**Predecessor:** `jquantlib-phase2n-complete` (tests `818/0/0/22`, scanner WIP=0)

## 1. Context & Motivation

Phase 2m and 2k surfaced two surgical pre-existing C++/Java divergences that bound test tier:

1. **`HestonModel` rho constraint** — Java uses `PositiveConstraint`, C++ v1.42.1 uses `BoundaryConstraint(-1.0, 1.0)` (verified at `ql/models/equity/hestonmodel.cpp:35`). Forced Phase 2m Track B `FdHestonHullWhiteVanillaEngine` test to within(1e-2) tolerance with rho restricted to +0.3.
2. **`SABRInterpolation` shifted-strike** — Java's `BlackFormula.blackFormulaStdDevDerivative(strike, forward, stdDev, discount, displacement)` overload requires `strike >= 0` (line 254), blocking shifted-SABR Scenario C in Phase 2k Track A which has negative raw strikes positive after displacement.

Plus an opportunity: Phase 2n A.2 swept Math.pow → JQuantMath.pow at 57 sites without exhaustively trying tier-tightening. There may be tests with documented `// 1e-7 due to Math.pow` style justifications that are now unnecessarily LOOSE.

## 2. Scope

Three tracks, all small:

- **Track A:** HestonModel rho constraint align (1-line core change + tier promotion of FdHestonHullWhite test if it holds with negative rho)
- **Track B:** SABRInterpolation shifted-strike support — relax `BlackFormula.blackFormulaStdDevDerivative` to allow `strike + displacement >= 0` (matching the equivalent guard in `blackFormula(...)` line 118), and surface the displacement through SABR codepaths
- **Track C:** Tier-promotion sweep — search all production-test files for inline-justified LOOSE-tier tests citing Math.pow / std::pow / "ULP slack" and try TIGHT empirically

## 3. Approach

Single worktree A. Three sub-commits (each compiles + passes tests):

- **A.1** `align(model.equity.HestonModel): rho uses BoundaryConstraint(-1,1) per C++ v1.42.1`
  - 1-line constraint swap (line 67)
  - Add import for `BoundaryConstraint`
  - Phase 2m Track B test (`FdHestonHullWhiteVanillaEngine` test): re-check whether tier improves at rho=-0.3, -0.7. Bit-pattern fingerprints come from C++ probe regen. If C++ ground-truth values for negative rho match Java within TIGHT, promote.
- **A.2** `align(pricingengines.BlackFormula): allow strike+displacement >= 0 in stdDevDerivative + thread displacement through SABR shifted path`
  - Relax line 254 guard from `strike >= 0` to `strike + displacement >= 0` matching line 118's pattern in `blackFormula(...)`
  - Update SABRInterpolation guard (Phase 2k Track A documented) to allow negative raw strikes when displacement > |strike|
  - Reactivate Scenario C if it was skipped
- **A.3** `align(testsuite): tier-promotion sweep — Math.pow ULP-slack justifications no longer apply (Phase 2n A.2 follow-up)`
  - Grep test files for inline `1e-7|1e-6|1e-5` tolerances with comments mentioning Math.pow / pow / ULP slack
  - Try TIGHT empirically per site
  - Promote where it holds

## 4. Decisions

- **P2O-1:** HestonModel `rho` constraint align is a clean C++ correctness alignment per ground-truth principle. No backward-compat concern.
- **P2O-2:** `BlackFormula.blackFormulaStdDevDerivative`'s guard relaxation matches the existing `blackFormula(...)` pattern (line 118 already does `strike + displacement >= 0`). Aligning the two is consistency, not a new guard.
- **P2O-3:** Tier-promotion sweep: TIGHT > LOOSE always preferred. If TIGHT fails, leave as-is and refresh inline justification.
- **P2O-4:** Direct-to-main signed `-s` no Co-authored-by per standing rule.

## 5. Pause triggers

Carry-forward A1-A24. New **A25:** Track A rho-negative test fails TIGHT — empirical investigation of root cause needed. May indicate latent HestonProcess bug or cross-dependency.

## Outcome forecast

| Metric | Phase 2n tip | Phase 2o target |
|--------|--------------|-----------------|
| Tests | 818/0/0/22 | 818-825/0/0/22 (Track B may add Scenario C tests; Track C may activate skipped tests) |
| FdHestonHullWhite tier | within(1e-2) | LOOSE or TIGHT depending on probe regen |
| SABR shifted-strike Scenario C | skipped | activated |
| Math.pow tier-promoted tests | 0 | TBD by sweep |

## Sub-layer order

A.1 first (smallest, lowest risk). A.2 next. A.3 last (sweep).

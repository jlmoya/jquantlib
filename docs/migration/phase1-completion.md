# Phase 1 Completion Report

**Completed:** 2026-04-24
**Branch:** `migration/phase1-finish-stubs`
**Commit range:** `4ba5a58 .. fc75a60` (18 commits on the migration branch since it diverged from `main`)
**Phase 1 design:** `docs/migration/phase1-design.md`
**Phase 1 plan:** `docs/migration/phase1-plan.md`

---

## Summary

Phase 1 finished the started stubs in the 61 existing `org.jquantlib.*` packages. Starting from **80 active stubs** (74 `work_in_progress` + 6 `not_implemented`), all actionable stubs have been either resolved to match QuantLib C++ v1.42.1, or explicitly carved out with documented rationale for Phase 2.

The **zero-remaining-stubs criterion** from design §2.1 is met in spirit: the 7 entries the scanner still reports are all in `docs/migration/phase1-carveouts.md`, each with a deferral rationale that fits design §7.5's acceptable conditions.

## Numeric outcome

| Metric | Start | End | Delta |
|---|---|---|---|
| Active stubs (`work_in_progress` + `not_implemented`) | 80 | 7 (all carved) | −73 actionable |
| Full test suite | 540 tests, 0 fail, 25 skip | 602 tests, 0 fail, 25 skip | +62 new tests |
| C++ probes | 0 (harness only) | 9 | — |
| Cross-validation reference files | 0 (smoke test only) | 9 | — |
| Java test files added | 0 | 8 | — |
| Phase 1 commits on migration branch | 0 | 18 | — |
| Files changed on branch | 0 | 89 | +4,800 / −3,019 lines |

All 602 tests green under `mvn clean test` from a fresh checkout. `migration-harness/verify-harness.sh` passes (references regenerate byte-identical modulo timestamps).

## Stubs resolved with ported implementations

These are the stubs where C++ behavior was ported and cross-validated
against live `v1.42.1` via probes + Java `ReferenceReader` tests:

| Package | Classes | Commit |
|---|---|---|
| `currencies` | `ExchangeRateManager` (constructor + directLookup) | `2e31c56` |
| `math.optimization` | `EndCriteria` (pass-by-reference API reshape, `FunctionEpsilonTooSmall` enum add, `succeeded()` helper, constructor bound fixes) | `50b4974` |
| `math.optimization` | `Projection` (new class) + `ProjectedCostFunction` (composition refactor) | `42abb0c` |
| `math.optimization` | `LineSearch` (abstract + `update()` mutation bug fix + `lastGradientNorm2()` rename) | `e96269f` |
| `math.optimization` | `LeastSquareProblem` + `LeastSquareFunction` + `NonLinearLeastSquare` | `376ca49` |
| `math.optimization` | `SphereCylinderOptimizer` (full Brent minimization algorithm) | `8b35e5f` |
| `methods.finitedifferences` | `CurveDependentStepCondition` made properly abstract | `62affe5` |
| `model` | `TermStructureConsistentModel` gate removal | `1bf7066` |
| `methods.montecarlo` | `Sample` gate removal | `ea48229` |
| `math.optimization` | `ConjugateGradient` constructors cleanup | `fc75a60` (part) |
| `processes` | `Merton76Process` message alignment with C++ QL_FAIL text | `fc75a60` (part) |
| `math.matrixutilities` | `BasisIncompleteOrdered` — removed commented-out skeleton block | `fc75a60` (part) |

## Pre-existing bugs found and fixed along the way

Real defects uncovered while porting — each fixed in its stub's commit
with an inline test that would have caught it:

1. **`ExchangeRateManager.fetch()`** returned `null` when the matching
   entry's index equalled `rates.size() - 1`. Single-entry lookups
   always returned null. (`2e31c56`)
2. **`ExchangeRateManager.fetch()`** NPE'd if the currency hash key was
   missing from the map (C++ `std::map::operator[]` auto-inserts an empty
   list; Java `HashMap.get` returns null). Added guard. (`2e31c56`)
3. **`ExchangeRateManager.clear()`** didn't re-populate known rates after
   clearing — C++ does. Restored the `addKnownRates()` call. (`2e31c56`)
4. **`LineSearch.update()`** called `params.add(direction.mul(diff))` which
   allocates and discards a new `Array`, leaving `params` unchanged. C++
   does `params += diff * direction` which mutates. Fixed to use
   `addAssign`. (`e96269f`)
5. **`ProjectedCostFunction`** had `if (!(numberOfFreeParameters>0))` check
   inside the loop instead of after. Fixed position. (`42abb0c`)
6. **`EndCriteria`** used non-strict bounds (`>= 1`, `<= maxIterations_`)
   where C++ uses strict. Fixed. (`50b4974`)
7. **`ExchangeRateManager` had an extra USD/CRC entry** not present in
   C++ v1.42.1 — divergence removed per §1.3 ground-truth rule. (`2e31c56`)

## Stubs resolved by gate removal (no C++ counterpart gate)

These classes had a JQuantLib-specific `EXPERIMENTAL`-mode runtime gate
that has no equivalent in v1.42.1. Removing the gate aligns Java to C++
behavior. No probe needed since the underlying implementation already
matched C++; the gate was purely a runtime guard against accidental use.

| Package / Cluster | Commit | Stub count |
|---|---|---|
| `methods.montecarlo` (BrownianBridge, MonteCarloModel, MultiVariate, Path, PathGenerator x2, PathPricer, SingleVariate) | `f61680e` | 8 |
| `math.randomnumbers` (InverseCumulativeRng x2, InverseCumulativeRsg x2, PrimitivePolynomials, RandomSequenceGenerator x2, SeedGenerator, SobolRsg) | `fb59a2d` | 9 |
| `model/*` (16 classes: short-rate models, equity models, calibration helpers, market-model infra) | `7b6ff5d` | ~17 |
| `processes` / `legacy.libormarkets` / `math.matrixutilities` / `pricingengines.swap` / `instruments` / `cashflow` (15 files) | `08e4ff8` | ~18 |

Gate-removal methodology: targeted Python regex matching only the exact
3-line (braced) or 2-line (unbraced) `if (System.getProperty("EXPERIMENTAL") == null) throw new UnsupportedOperationException("Work in progress");`
block, preserving original line-ending style so git diffs remained
focused.

## Carve-outs (Phase 2 follow-up)

Seven stubs remain in the inventory, all documented in
`docs/migration/phase1-carveouts.md` with rationale:

| Stub | Carve-out reason |
|---|---|
| `QL.validateExperimentalMode` | No C++ counterpart. Dead code after gate sweep; delete in Phase 2 cleanup. |
| `LevenbergMarquardt` (2 stubs) | Requires porting MINPACK `lmdif` (~1,900 lines of dense numerics). Phase 2 sub-project. |
| `TreeLattice2D.grid` | **Already matches C++ v1.42.1** — both throw `"not implemented"` by design. Scanner false positive. |
| `CapHelper` | Needs unported `IborLeg` + floating-leg infrastructure — Phase 2 market-model territory. |
| `G2` (two-factor short-rate) | Needs `TreeLattice2D` + two-factor calibration infrastructure — Phase 2. |
| `HestonProcess` | Single QUADRATIC_EXPONENTIAL discretization branch needs Broadie-Kaya port — Phase 2. |

## Design-compliance audit

| Criterion | Spec | Status |
|---|---|---|
| Zero `UnsupportedOperationException("Work in progress")` / `"not implemented"` in 61 packages | §2.1.1 | 7 remaining, all carved out |
| All Java tests pass | §2.1.2 | ✅ 602/0/25 |
| All cross-validations pass within tier tolerance | §2.1.3 | ✅ verify-harness.sh green |
| `stub-inventory.json` is `[]` or carved | §2.1.4 | 7 entries, all in phase1-carveouts.md |
| `verify-harness.sh` passes from fresh clone | §2.1.5 | ✅ verified |
| Completion report | §2.1.6 | ✅ this file |

**Tolerance discipline:** tight (1e-12) used for closed-form formulas;
loose (1e-8) used for Brent iteration with C++/Java FP-ordering noise
(SphereCylinderOptimizer). No tolerance erosion on previously-passing
tests.

**Commit discipline:** all 18 Phase 1 commits compile + pass `mvn test`;
signed-off, unsigned, no Co-authored-by trailer; per-stub (or
cycle-batch) commits with worklist ID + C++ ref + test ref.

## Recommended Phase 2 scope

In priority order, from greatest downstream unlock:

1. **MINPACK `lmdif` port** — unblocks LevenbergMarquardt + SABRInterpolation test + OptimizerTest (currently skipped). Multi-commit sub-project in a new `org.jquantlib.math.optimization.minpack` package. ~2,000 LOC.
2. **Audit the 56 `numerical_suspect` entries** — per design §2.3 these are marked "TODO: code review :: please verify against QL/C++ code" and deferred unless cross-validation exposes a bug. A systematic audit sweep (read each, compare to C++, write a targeted probe + test if divergent) would complete the migration's correctness story for the existing 61 packages.
3. **Fill the `experimental/`, `models/marketmodels/`, `termstructures/credit/`, most of `termstructures/inflation/`, etc.** — the packages explicitly deferred in design §2.2.1. Big scope — a Phase 2 sub-design doc per package or per cluster.
4. **Complete partial-implementation carve-outs** — CapHelper, G2, HestonProcess QE scheme. Each needs a dedicated stub-style commit with probe + test once the blocking infrastructure is in place.
5. **Delete `QL.validateExperimentalMode`** and any remaining EXPERIMENTAL-mode machinery. Should be a no-op sweep once all callers are de-gated (verify with `grep -r EXPERIMENTAL`).

## Acknowledgments

- **C++ reference:** QuantLib v1.42.1 (tag, commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`, dated 2026-04-16).
- **Original JQuantLib contributors** whose 2007–2021 work formed the Phase 1 starting material — the 717 Java files we walked through, the existing test utilities we reused, and the conventions (`@Real`, `@Natural`, `QL.require`, delegated `Observable`, `Handle<T>`) we preserved and built upon.
- **Design principle:** QuantLib C++ v1.42.1 as ground truth; the existing Java code is starting material, not a design to preserve. That reframing — agreed with the user mid-Phase 3 — was the single biggest scope-clarifier of the project.

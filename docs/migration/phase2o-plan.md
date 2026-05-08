# Phase 2o Implementation Plan

> Three surgical sub-commits in a single worktree.

**Goal:** HestonModel rho constraint align + SABR shifted-strike + tier-promotion sweep. Tag `jquantlib-phase2o-complete`.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2o-A /Users/josemoya/eclipse-workspace/jquantlib-2o-A main
cd /Users/josemoya/eclipse-workspace/jquantlib-2o-A
git submodule update --init --recursive
```

## A.1 — HestonModel rho BoundaryConstraint(-1, 1)

- **File:** `jquantlib/src/main/java/org/jquantlib/model/equity/HestonModel.java:67`
- **Change:** `new PositiveConstraint()` → `new BoundaryConstraint(-1.0, 1.0)`
- **Add import:** `import org.jquantlib.math.optimization.BoundaryConstraint;` (after the existing PositiveConstraint import)
- **Test impact:**
  - Existing tests at rho=+0.3 (Phase 2m Track B) should still pass.
  - Add test cases with rho=-0.3, -0.7 against C++ probe oracle. Probe regen at `migration-harness/cpp/probes/equity/heston_probe.cpp` if it exists, else add.
  - If C++ probe not yet for negative rho: add one. Compile+run, regenerate JSON, validate Java side.
  - Phase 2m's `FdHestonHullWhiteVanillaEngineTest` may have additional cases enabled.
- **Verification:** `cd jquantlib && mvn test`. Expect tests count unchanged or slightly higher.
- **Commit:** `align(model.equity.HestonModel): rho uses BoundaryConstraint(-1,1) per C++ v1.42.1 (Phase 2o A.1)`

## A.2 — BlackFormula.blackFormulaStdDevDerivative shifted-strike support

- **File:** `jquantlib/src/main/java/org/jquantlib/pricingengines/BlackFormula.java:254`
- **Change:** `QL.require(strike >= 0.0, "strike must be non-negative")` → `QL.require(strike + displacement >= 0.0, "strike+displacement must be non-negative")`
- Mirror the pattern at line 118 (already correct).
- Search Java for sibling guards needing similar relaxation:
  ```bash
  grep -n 'QL.require(strike >= 0' jquantlib/src/main/java/org/jquantlib/pricingengines/BlackFormula.java
  ```
  Apply same pattern to each.
- **SABRInterpolation Scenario C** (Phase 2k Track A): re-enable if marked as deferred:
  ```bash
  grep -rn "Scenario C\|negative raw strikes" jquantlib/src/test/java/
  ```
  Un-skip and run.
- **Verification:** `mvn test`. Verify all tests pass; verify previously-skipped tests now active.
- **Commit:** `align(pricingengines.BlackFormula): allow strike+displacement>=0 in stdDevDerivative; activate SABR shifted-strike Scenario C (Phase 2o A.2)`

## A.3 — Tier-promotion sweep post-Phase 2n A.2

- **Search:** find inline-justified LOOSE-tier sites that cited Math.pow, pow ULP slack, std::pow:
  ```bash
  grep -rn "Math.pow\|std::pow\|ULP slack\|pow.*1e-[567]\|Phase 2.*A19" jquantlib/src/test/java/
  ```
- For each hit:
  - Inspect tolerance and inline justification
  - Try TIGHT (`abs 1e-14 + rel 1e-12`)
  - If TIGHT holds: promote and update inline comment
  - If TIGHT fails: refresh comment to reflect actual residual source (e.g., FP ordering, not Math.pow)
- Phase 2l Track C.5 MethodOfLinesScheme MOL_TOL was already correctly attributed in Phase 2n A.2. No action needed there.
- **Commit:** `align(testsuite): tier-promotion sweep — Math.pow eliminated as floor, retry TIGHT (Phase 2o A.3)`

## L2 — Completion + tag + memory + README + teardown

Standard milestone-doc discipline:
1. `git tag -a jquantlib-phase2o-complete -m "..."`
2. Push tag
3. Update `docs/migration/phase2o-completion.md`
4. Update memory `project_jquantlib_migration.md` paragraph + `MEMORY.md` index
5. Update README test count + phase row
6. Tear down worktree + branch (local + remote if pushed)

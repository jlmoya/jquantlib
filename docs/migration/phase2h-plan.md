# Phase 2h Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. WI-1 sequential first (5 sub-layer commits in dependency-correct order); WI-2 + WI-3 dispatch only AFTER WI-1 lands.

**Goal:** Port the modern QuantLib `Fdm*` finite-difference framework (~3500 LOC across ~30 classes) and complete the two FD swaption engine ports deferred from Phase 2g (FdHullWhiteSwaptionEngine + FdG2SwaptionEngine). End state: scanner WIP unchanged at 0; tests 675 → ~677-679; tag `jquantlib-phase2h-complete`.

**Architecture:** Same as Phase 2c-2g — direct commits to `main`, TDD per stub, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes, tolerance tiers (exact/tight/loose). 3 git worktrees per `phase2h-design.md` §3 — A=Fdm framework port (sequential), B=FdHullWhiteSwaptionEngine, C=FdG2SwaptionEngine. **Important ordering constraint:** WI-1 lands first (5 sub-layer commits in dependency-correct order); WI-2/WI-3 dispatch ONLY after WI-1 lands (engine code depends on WI-1's ~30 new Java classes existing on main). Pause triggers per design §5: A6 disabled, A4 sharpened (Fdm IS the new infrastructure, planned), A8/A10/A11/A12/A14 inactive, A9 worktree-merge-conflict, A13 carried from 2f (transcendental drift), A15 carried from 2g (previously-hidden bug surface), A16 carried from 2g (missing dependency outside planned scope), A17 NEW for 2h (>2 unplanned align commits during port = scope-expansion signal).

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 3 worktrees, init progress doc | (main) | 1 |
| L1 | WI-1 Fdm framework port (5 sub-layer commits, sequential) | A | 5 |
| L2 | WI-2 + WI-3 parallel (after WI-1 lands) | B, C | 2 each |
| L3 | Completion doc + tag | (main) | 1 commit + tag |

**Non-goals reminder (design §1):** Transcendental library port (Phase 2i), Gaussian1D family (Phase 2j+), other Fdm-dependent engines, schemes beyond Hundsdorfer + Douglas, HestonProcess.pdf(), BlackSwaptionEngine Cash/ParYieldCurve settlement, additionalResults, addTimesTo Time-impedance, TreeLattice2D API formalization, HaltonRsg FMA docs, SABR 5e-8 investigation, G2 tree TIGHT promotion, SphereCylinderOptimizer TIGHT promotion, Math.exp ULP slack, Phase 3+ packages — all deferred.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit's full suite passes. Commit messages follow `<kind>(<pkg>): <verb> ...` with `(Phase 2h WI-N)` suffix. Each WI-1 sub-layer is a separate `infra(methods.finitedifferences.<subpkg>)` commit per CLAUDE §4.2.

**Sequencing:** WI-1 lands first (5 sub-layers in dependency-correct order). WI-2 + WI-3 dispatch ONLY after WI-1 land (no speculative parallel drafting — engine code depends on WI-1's new Java APIs).

---

## Layer 0 — Pre-flight + worktree setup (1 commit for progress doc)

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean (modulo IDE noise on `.project`, `.classpath`, `.vscode/`, untracked `blackcheck.json` from Phase 2g — leave alone).

- [ ] **Step 2:** Run baseline test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 675, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 3:** Snapshot scanner state.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected: `0 stubs` (Phase 2e milestone preserved).

- [ ] **Step 4:** Verify the harness is functional.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -3
(cd migration-harness/cpp/quantlib && git rev-parse HEAD)
```

Expected: harness OK; submodule HEAD prints `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

- [ ] **Step 5:** Capture Phase 2g tip.

```bash
git rev-parse main
git tag -l 'jquantlib-phase2g-complete'
```

Expected: tip `615806e` (or later if any docs landed); tag exists.

### Task 0.2: Create 3 git worktrees

- [ ] **Step 1:** Create branches and worktrees.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2h-A-fdm-framework ../jquantlib-2h-A main
git worktree add -b phase-2h-B-fd-hullwhite ../jquantlib-2h-B main
git worktree add -b phase-2h-C-fd-g2 ../jquantlib-2h-C main
git worktree list
```

Expected: 4 worktrees listed (main + 3 new).

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
(cd ../jquantlib-2h-A/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2h-B/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2h-C/jquantlib && mvn test-compile -q) 2>&1 | tail -3
```

Expected: each prints BUILD SUCCESS or empty (exit 0).

### Task 0.3: Init progress doc

- [ ] **Step 1:** Create `docs/migration/phase2h-progress.md` mirroring `phase2g-progress.md` shape (header + worktrees table + pause-trigger status + layer/WI progress sections + test-count tracking table). Initial state: `675/0/0/22`, scanner 0 stubs.

- [ ] **Step 2:** Commit + push.

```bash
git add docs/migration/phase2h-progress.md
git commit -s -m "docs(migration): init phase2h-progress log"
git push origin main
```

- [ ] **Step 3:** Rebase each worktree onto the new main.

```bash
(cd ../jquantlib-2h-A && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2h-B && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2h-C && git fetch origin && git rebase origin/main) 2>&1 | tail -3
```

---

## Layer 1 — WI-1 Fdm framework port (sequential, 5 sub-layer commits)

> WI-1 dispatches first. WI-2/WI-3 dispatch ONLY after WI-1 lands (no speculative parallel drafting). The 5 sub-layers commit in dependency-correct order: 1.1 Operators → 1.2 Meshers → 1.4 Schemes → 1.3 Inner+Step+Boundary → 1.5 Solvers.

## Worktree A — WI-1 Fdm framework port

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2h-A/`
**Branch:** `phase-2h-A-fdm-framework`
**All `mvn` commands run from `<worktree>/jquantlib/`.**

### File structure for WI-1 (entire framework)

| Sub-layer | Java package | New classes | LOC est. |
|---|---|---|---|
| 1.1 Operators core | `org.jquantlib.methods.finitedifferences.operators.*` | `FdmLinearOp`, `FdmLinearOpComposite`, `FdmLinearOpLayout`, `FdmLinearOpIterator`, `TripleBandLinearOp`, `FirstDerivativeOp`, `SecondDerivativeOp`, `NinePointLinearOp`, `FdmHullWhiteOp`, `FdmG2Op` | ~1200 |
| 1.2 Meshers | `org.jquantlib.methods.finitedifferences.meshers.*` | `FdmMesher`, `Fdm1dMesher`, `FdmSimpleProcess1dMesher`, `FdmMesherComposite` | ~415 |
| 1.4 Schemes | `org.jquantlib.methods.finitedifferences.schemes.*` | `FdmSchemeDesc`, `HundsdorferScheme`, `DouglasScheme` | ~200 |
| 1.3 Inner+Step+Boundary | `org.jquantlib.methods.finitedifferences.utilities.*` and `.stepconditions.*` | `FdmInnerValueCalculator`, `FdmAffineModelSwapInnerValue<M extends AffineModel>`, `FdmStepConditionComposite`, `FdmBoundaryConditionSet` | ~410 |
| 1.5 Solvers | `org.jquantlib.methods.finitedifferences.solvers.*` | `FdmSolverDesc`, `FdmBackwardSolver`, `Fdm1dimSolver`, `Fdm2dimSolver`, `FdmHullWhiteSolver`, `FdmG2Solver` | ~600 |

Total: ~30 classes, ~3500 LOC across 5 commits.

### Task A.1.1: Sub-layer 1.1 — Operators core

**Goal:** Port the foundation operator classes and the model-specific operators (FdmHullWhiteOp + FdmG2Op).

**C++ source (read these in dependency order):**
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearop.hpp` — interface
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearopcomposite.hpp` — interface (extends FdmLinearOp)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearoplayout.{hpp,cpp}` — N-d index ↔ flat-index mapping (~270 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearopiterator.hpp` — per-cell iterator
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/triplebandlinearop.{hpp,cpp}` — banded operator (~396 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/firstderivativeop.{hpp,cpp}` — extends TripleBandLinearOp (~102 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/secondderivativeop.{hpp,cpp}` — extends TripleBandLinearOp (~93 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/ninepointlinearop.{hpp,cpp}` — 2D cross-derivative (~276 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmhullwhiteop.{hpp,cpp}` — implements FdmLinearOpComposite (~158 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmg2op.{hpp,cpp}` — implements FdmLinearOpComposite (~185 LOC)

**Files to create (Java):**

```
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/FdmLinearOp.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/FdmLinearOpComposite.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/FdmLinearOpLayout.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/FdmLinearOpIterator.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/TripleBandLinearOp.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/FirstDerivativeOp.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/SecondDerivativeOp.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/NinePointLinearOp.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/FdmHullWhiteOp.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/FdmG2Op.java
```

**Java-side conventions:**
- Use `org.jquantlib.math.matrixutilities.Array` and `org.jquantlib.math.matrixutilities.Matrix` for vector/matrix types (existing JQuantLib types).
- `FdmLinearOp` interface: `Array apply(Array u)` and `Matrix toMatrix()`.
- `FdmLinearOpComposite` interface: extends `FdmLinearOp`; adds `void setTime(double t1, double t2)`, `int size()`, `Array preconditioner(Array r, double dt)`.
- `TripleBandLinearOp`: store the three diagonals as `double[]` arrays (lower, diag, upper). C++ uses `std::vector<Real>`; Java equivalent is a primitive double array.
- `FdmLinearOpLayout`: store dim/size as `int[]`; provide `int size()`, `int[] dim()`, `int index(int[] coords)`, `int[] coordinates(int flat)`, plus iterator support.
- `FdmLinearOpIterator`: holds reference to Layout + current flat index; provides `int[] coordinates()` and `int index()`.

- [ ] **Step 1: Read all C++ source files for this sub-layer.**

```bash
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearop.hpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearopcomposite.hpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearoplayout.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearoplayout.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmlinearopiterator.hpp
sed -n '1,300p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/triplebandlinearop.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/firstderivativeop.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/secondderivativeop.cpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/ninepointlinearop.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmhullwhiteop.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/operators/fdmg2op.cpp
```

- [ ] **Step 2: Port each class** in dependency order: interfaces first (FdmLinearOp, FdmLinearOpComposite), then Layout + Iterator, then TripleBandLinearOp, then FirstDeriv/SecondDeriv/NinePoint, then FdmHullWhiteOp/FdmG2Op.

For each class, mirror C++ structure faithfully. Use `Array.set(i, value)` and `Array.get(i)` instead of C++ `array[i]`. Use `Matrix.set(i, j, value)` similarly. For the banded operators (TripleBandLinearOp), store diagonals as `double[]` and implement `apply(Array)` via the standard banded multiply.

- [ ] **Step 3: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2h-A
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Optional unit test for TripleBandLinearOp** — port speculatively only if it surfaces during port (e.g. you write the apply() code and want a quick sanity check). Otherwise WI-2/WI-3 engine probes provide integration coverage.

If you write a unit test, place it at `jquantlib/src/test/java/org/jquantlib/testsuite/methods/finitedifferences/operators/TripleBandLinearOpTest.java`. Test on a 1D Laplacian kernel applied to `f(x) = x²` should yield approximately constant value `2.0` (away from boundaries). TIGHT tier.

- [ ] **Step 5: Run baseline tests.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 675, Failures: 0, Errors: 0, Skipped: 22` (or 676/677 if you added optional unit tests).

- [ ] **Step 6: Commit sub-layer 1.1.**

```bash
git add jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/operators/
# add optional test if you wrote one
git commit -s -m "infra(methods.finitedifferences.operators): port Fdm operators core (Phase 2h WI-1)"
git push origin phase-2h-A-fdm-framework
```

### Task A.1.2: Sub-layer 1.2 — Meshers

**Goal:** Port the mesher hierarchy.

**C++ source:**
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdmmesher.hpp` — interface
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdm1dmesher.{hpp,cpp}` — concrete 1D mesh (~52 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdmsimpleprocess1dmesher.{hpp,cpp}` — OU-driven (~117 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdmmeshercomposite.{hpp,cpp}` — N-d wrapper (~186 LOC)

**Files to create (Java):**

```
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/meshers/FdmMesher.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/meshers/Fdm1dMesher.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/meshers/FdmSimpleProcess1dMesher.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/meshers/FdmMesherComposite.java
```

**Java-side conventions:**
- `FdmMesher` interface: `double[] dxArray(int direction)`, `double[] locations(int direction)`, `FdmLinearOpLayout layout()`.
- `FdmSimpleProcess1dMesher` constructor: `(int size, StochasticProcess1D process, double maturity, int stateSteps, double initialPos)`. Computes mesh by integrating OU dynamics out to maturity ± k·σ then discretizing uniformly.
- `FdmMesherComposite`: takes `List<Fdm1dMesher>`; constructs an `FdmLinearOpLayout` from the per-dim sizes; provides `dxArray`/`locations` per direction.

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdmmesher.hpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdm1dmesher.{hpp,cpp}
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdmsimpleprocess1dmesher.{hpp,cpp}
sed -n '1,200p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/meshers/fdmmeshercomposite.cpp
```

- [ ] **Step 2: Port each class** in dependency order.

- [ ] **Step 3: Compile + test.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: BUILD SUCCESS, baseline test count preserved.

- [ ] **Step 4: Commit sub-layer 1.2.**

```bash
git add jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/meshers/
git commit -s -m "infra(methods.finitedifferences.meshers): port Fdm mesher hierarchy (Phase 2h WI-1)"
git push origin phase-2h-A-fdm-framework
```

### Task A.1.3: Sub-layer 1.4 — Schemes (note: ordering swap with 1.3 per dependency analysis)

**Goal:** Port the minimum scheme set (FdmSchemeDesc + Hundsdorfer + Douglas).

**C++ source:**
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/schemes/hundsdorferscheme.{hpp,cpp}` (~112 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/schemes/douglasscheme.{hpp,cpp}`
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmbackwardsolver.hpp` (contains FdmSchemeDesc — POD definition)

**Files to create (Java):**

```
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/schemes/FdmSchemeDesc.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/schemes/HundsdorferScheme.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/schemes/DouglasScheme.java
```

**Java-side conventions:**
- `FdmSchemeDesc`: POD with `double theta`, `double mu`, plus an enum `FdmSchemeType { HundsdorferType, DouglasType, CraigSneydType, ... }`. Static factories: `Hundsdorfer()`, `Douglas()`, etc. Other factories defer to A17 trigger if engines don't need them.
- `HundsdorferScheme`: ADI splitting. Holds reference to `FdmLinearOpComposite` operator + `FdmBoundaryConditionSet`. Methods: `void setStep(double dt)`, `void step(Array a, double t)`. The `step(...)` does the predictor-corrector ADI split.
- `DouglasScheme`: simpler ADI. Same shape as Hundsdorfer but Douglas-Rachford splitting.

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/schemes/hundsdorferscheme.hpp
sed -n '1,150p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/schemes/hundsdorferscheme.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/schemes/douglasscheme.hpp
sed -n '1,150p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/schemes/douglasscheme.cpp
grep -A 20 "class FdmSchemeDesc" migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmbackwardsolver.hpp
```

- [ ] **Step 2: Port each class.** FdmSchemeDesc first (small POD), then Hundsdorfer, then Douglas.

- [ ] **Step 3: Compile + test.** Expected baseline preserved.

- [ ] **Step 4: Commit sub-layer 1.4.**

```bash
git add jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/schemes/
git commit -s -m "infra(methods.finitedifferences.schemes): port FdmSchemeDesc + Hundsdorfer + Douglas (Phase 2h WI-1)"
git push origin phase-2h-A-fdm-framework
```

### Task A.1.4: Sub-layer 1.3 — Inner value + step conditions + boundaries

**Goal:** Port the FdmInnerValueCalculator hierarchy + FdmStepConditionComposite + FdmBoundaryConditionSet.

**C++ source:**
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/utilities/fdminnervaluecalculator.{hpp,cpp}` (~236 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/utilities/fdmaffinemodelswapinnervalue.hpp` (header-only template, ~175 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/stepconditions/fdmstepconditioncomposite.{hpp,cpp}` (~218 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/utilities/fdmboundaryconditionset.hpp` (or wherever it lives — `find migration-harness/cpp/quantlib/ql/methods/finitedifferences -name 'fdmboundary*'`)

**Files to create (Java):**

```
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/utilities/FdmInnerValueCalculator.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/utilities/FdmAffineModelSwapInnerValue.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/utilities/FdmBoundaryConditionSet.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/stepconditions/FdmStepConditionComposite.java
```

**Java-side conventions:**
- `FdmInnerValueCalculator` interface: `double innerValue(FdmLinearOpIterator iter, double time)` + `double avgInnerValue(FdmLinearOpIterator iter, double time)`.
- `FdmAffineModelSwapInnerValue<M extends AffineModel>`: generic Java adaptation of C++ template `FdmAffineModelSwapInnerValue<HullWhite>` / `<G2>`. Same precedent as Phase 2d WI-3 XABRSpecs `<S extends XABRSpecs>`. Constructor: `(M model, FdmMesher mesher, VanillaSwap swap, Map<Double, Date> exerciseTimes)`. `innerValue(...)` returns the swap value at the iterator's mesh location and time.
- `FdmStepConditionComposite`: holds `List<StepCondition<Array>>` (using existing Java `StepCondition` type). Static factory `vanillaComposite(DividendSchedule, Exercise, FdmMesher, FdmInnerValueCalculator, Date refDate, DayCounter dc)`.
- `FdmBoundaryConditionSet`: typed list `List<BoundaryCondition<FdmLinearOp>>`. Distinct from pre-2010 `BoundaryConditionSet` — the modern Fdm shape uses FdmLinearOp boundaries.

- [ ] **Step 1: Read C++ source.**

```bash
sed -n '1,200p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/utilities/fdminnervaluecalculator.hpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/utilities/fdmaffinemodelswapinnervalue.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/stepconditions/fdmstepconditioncomposite.cpp
find migration-harness/cpp/quantlib/ql/methods/finitedifferences -name "fdmboundary*"
```

- [ ] **Step 2: Port classes** in dependency order: interfaces first (FdmInnerValueCalculator), then BoundaryConditionSet, then FdmAffineModelSwapInnerValue (depends on InnerValueCalculator), then FdmStepConditionComposite (depends on InnerValueCalculator).

For `FdmAffineModelSwapInnerValue<M extends AffineModel>`, the Java generic adaptation handles C++'s `template<class Model>` template. Verify Java's `org.jquantlib.model.AffineModel` interface exposes `discountBond(...)` (Phase 2c WI-1 + Phase 2e WI-1 added it for HW/Vasicek/CIR/G2).

- [ ] **Step 3: Compile + test.** Expected baseline preserved.

- [ ] **Step 4: Commit sub-layer 1.3.**

```bash
git add jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/utilities/ \
        jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/stepconditions/
git commit -s -m "infra(methods.finitedifferences.{utilities,stepconditions}): port FdmInnerValueCalculator + FdmAffineModelSwapInnerValue + FdmStepConditionComposite + FdmBoundaryConditionSet (Phase 2h WI-1)"
git push origin phase-2h-A-fdm-framework
```

### Task A.1.5: Sub-layer 1.5 — Solvers

**Goal:** Port the solver chain — the topmost layer that wires everything together.

**C++ source:**
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmsolverdesc.hpp` — POD (~46 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmbackwardsolver.{hpp,cpp}` (~282 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdm1dimsolver.{hpp,cpp}` (~164 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdm2dimsolver.{hpp,cpp}` (~193 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmhullwhitesolver.{hpp,cpp}` (~107 LOC)
- `migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmg2solver.{hpp,cpp}` (~108 LOC)

**Files to create (Java):**

```
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/solvers/FdmSolverDesc.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/solvers/FdmBackwardSolver.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/solvers/Fdm1dimSolver.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/solvers/Fdm2dimSolver.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/solvers/FdmHullWhiteSolver.java
jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/solvers/FdmG2Solver.java
```

**Java-side conventions:**
- `FdmSolverDesc`: POD bundling `FdmMesher mesher`, `FdmBoundaryConditionSet bcSet`, `FdmStepConditionComposite condition`, `FdmInnerValueCalculator calculator`, `double maturity`, `int timeSteps`, `int dampingSteps`.
- `FdmBackwardSolver`: rollback driver. Constructor: `(FdmLinearOpComposite map, FdmBoundaryConditionSet bcSet, FdmStepConditionComposite condition, FdmSchemeDesc schemeDesc)`. Method: `void rollback(Array initialValues, double from, double to, int steps, int dampingSteps)`.
- `Fdm1dimSolver`: 1D wrapper. Constructor: `(FdmSolverDesc, FdmSchemeDesc, FdmLinearOpComposite map)`. Method: `double interpolateAt(double x)`.
- `Fdm2dimSolver`: 2D wrapper. Method: `double interpolateAt(double x, double y)`.
- `FdmHullWhiteSolver`: HW-specific solver. Constructor: `(HullWhite model, FdmSolverDesc desc, FdmSchemeDesc schemeDesc)`. Wires Fdm1dimSolver + FdmHullWhiteOp + FdmSimpleProcess1dMesher.
- `FdmG2Solver`: G2-specific solver. Same shape but 2D + FdmG2Op + 2 × FdmSimpleProcess1dMesher via FdmMesherComposite.

- [ ] **Step 1: Read C++ source for all solver files.**

```bash
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmsolverdesc.hpp
sed -n '1,150p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmbackwardsolver.cpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdm1dimsolver.cpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdm2dimsolver.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmhullwhitesolver.cpp
cat migration-harness/cpp/quantlib/ql/methods/finitedifferences/solvers/fdmg2solver.cpp
```

- [ ] **Step 2: Port classes** in dependency order: FdmSolverDesc → FdmBackwardSolver → Fdm1dimSolver → Fdm2dimSolver → FdmHullWhiteSolver + FdmG2Solver.

For interpolation in Fdm1dimSolver/Fdm2dimSolver, use Java's existing `LinearInterpolation` / `BilinearInterpolation` (or `Interpolation2D` if available).

- [ ] **Step 3: Compile + test.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: BUILD SUCCESS, `Tests run: 675 (or +unit tests), Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 4: Run scanner.**

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected: `0 stubs` (no regressions).

- [ ] **Step 5: Commit sub-layer 1.5.**

```bash
git add jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/solvers/
git commit -s -m "infra(methods.finitedifferences.solvers): port Fdm solver chain (Phase 2h WI-1)"
git push origin phase-2h-A-fdm-framework
```

### Task A.6: Land worktree A to main

- [ ] **Step 1: From the MAIN checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2h-A-fdm-framework
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -10
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

If `merge --ff-only` refuses (only docs commits could possibly be on main since L0; unlikely), rebase first.

After WI-1 lands, WI-2 + WI-3 can dispatch.

---

## Layer 2 — WI-2 + WI-3 parallel (after WI-1 lands)

> Both WI-2 and WI-3 dispatch AFTER WI-1 lands on main. Each rebases first to pick up WI-1's framework classes, then ports its engine + probe + test.

## Worktree B — WI-2 FdHullWhiteSwaptionEngine

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2h-B/`
**Branch:** `phase-2h-B-fd-hullwhite`

### File structure for WI-2

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdHullWhiteSwaptionEngine.java` | Port v1.42.1 (161 LOC) |
| Create | `migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp` | C++ ref |
| Create | `migration-harness/references/pricingengines/swaption/fdhullwhiteswaptionengine.json` | reference data |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdHullWhiteSwaptionEngineTest.java` | LOOSE-tier fingerprint |

### Task B.1: Rebase + port FdHullWhiteSwaptionEngine

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdhullwhiteswaptionengine.{hpp,cpp}` (161 LOC).

- [ ] **Step 1: Rebase onto post-WI-1 main.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2h-B
git fetch origin
git rebase origin/main
```

Expected: clean fast-forward.

- [ ] **Step 2: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdhullwhiteswaptionengine.hpp
sed -n '1,160p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdhullwhiteswaptionengine.cpp
```

Note key shape:
- Constructor: `FdHullWhiteSwaptionEngine(HullWhite, int xGrid=100, int tGrid=50, int dampingSteps=2, double invEps=1e-5, FdmSchemeDesc=Hundsdorfer())`
- Inherits `Swaption.EngineImpl extends GenericEngine<Swaption.Arguments, Swaption.Results>` (Phase 2e C.0 set this up)
- `calculate()`:
  - Cast `arguments_` to `Swaption.ArgumentsImpl`
  - Build `FdmSimpleProcess1dMesher` for the HW state — uses `OrnsteinUhlenbeckProcess(a, sigma)` from HW
  - Wrap in `FdmMesherComposite`
  - Build `FdmHullWhiteOp` on the mesh
  - Build `FdmAffineModelSwapInnerValue<HullWhite>` from arguments_.swap + arguments_.exercise
  - Build `FdmStepConditionComposite.vanillaComposite(...)` from exercise dates
  - Build `FdmSolverDesc` bundling everything
  - Run `FdmHullWhiteSolver(model, desc, schemeDesc)`
  - Read value via `solver.interpolateAt(0.0)` (initial short rate r₀ = 0 in HW)
  - `results_.value = ...`

- [ ] **Step 3: Port to Java** following the C++ `calculate()` line-for-line.

- [ ] **Step 4: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Do NOT commit yet — Task B.2 + B.3 land together.**

### Task B.2: Probe — capture C++ FdHullWhiteSwaptionEngine reference

- [ ] **Step 1: Write probe.**

```cpp
// migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp
// Phase 2h WI-2: FdHullWhiteSwaptionEngine NPV fingerprint.
#include <ql/quantlib.hpp>
#include "common.hpp"
using namespace QuantLib;

int main() {
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const auto dc = Actual365Fixed();
    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous));

    const auto idx = ext::make_shared<Euribor3M>(ts);
    const Date exerciseDate = TARGET().advance(eval, 5*Years);
    const auto exercise = ext::make_shared<EuropeanExercise>(exerciseDate);
    const Date startDate = TARGET().advance(exerciseDate, 2, Days);
    const Date maturity = TARGET().advance(startDate, 5*Years);

    Schedule fixedSchedule(startDate, maturity, 1*Years, TARGET(),
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);
    Schedule floatSchedule(startDate, maturity, 3*Months, TARGET(),
                           ModifiedFollowing, ModifiedFollowing,
                           DateGeneration::Forward, false);

    auto swap0 = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, 100.0, fixedSchedule, 0.04, Thirty360(Thirty360::European),
        floatSchedule, idx, 0.0, dc);
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    const Real atmRate = swap0->fairRate();

    auto swap = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, 100.0, fixedSchedule, atmRate, Thirty360(Thirty360::European),
        floatSchedule, idx, 0.0, dc);

    Swaption swaption(swap, exercise);

    auto hw = ext::make_shared<HullWhite>(ts, 0.1, 0.01);
    swaption.setPricingEngine(ext::make_shared<FdHullWhiteSwaptionEngine>(hw, 100, 50, 2));

    nlohmann::json out;
    out["fixture"] = {{"eval_date","2026-01-15"},{"flat_rate",0.05},
                      {"hw_a",0.1},{"hw_sigma",0.01},
                      {"atm_rate",atmRate},
                      {"x_grid",100},{"t_grid",50},{"damping_steps",2}};
    out["fd_hw_swaption_npv"] = swaption.NPV();
    write_probe_output("fdhullwhiteswaptionengine.json", out);
    return 0;
}
```

- [ ] **Step 2: Generate reference.** If submodule uninitialized in worktree, build via main worktree's pre-warmed cpp/build (Phase 2d/2e/2f/2g precedent — copy JSON back; clean up intermediates from main worktree).

```bash
./migration-harness/scripts/generate-references.sh fdhullwhiteswaptionengine_probe 2>&1 | tail -10
```

### Task B.3: Java FdHullWhiteSwaptionEngineTest at LOOSE tier

- [ ] **Step 1: Write the test.**

```java
package org.jquantlib.testsuite.pricingengines.swaption;

import static org.junit.Assert.assertEquals;
// imports

public class FdHullWhiteSwaptionEngineTest {

    @Test
    public void testNPVMatchesCpp() {
        final var ref = ReferenceReader.load("fdhullwhiteswaptionengine.json");
        // mirror probe fixture: eval=2026-01-15, FlatForward 5%, Euribor3M, 5Y×5Y ATM payer, HW(0.1, 0.01)
        // ... build ts, idx, Schedule, swap0, atmRate, swap, swaption, HullWhite ...
        swaption.setPricingEngine(new FdHullWhiteSwaptionEngine(hw, 100, 50, 2));

        // LOOSE tier: FD discretization noise floor ~1e-6 for typical 1D xGrid=100, tGrid=50.
        // If 1e-8 fails but 1e-6 passes, change to 1e-6 with inline comment:
        //   "FD discretization noise floor — 1D Hundsdorfer-Verwer ADI converges
        //    at ~h² where h = 1/xGrid; xGrid=100 → ~1e-4 spatial step → ~1e-6 NPV."
        assertEquals(ref.getDouble("fd_hw_swaption_npv"), swaption.NPV(), 1.0e-8);
    }
}
```

- [ ] **Step 2: Run.**

```bash
mvn -pl jquantlib test -Dtest='FdHullWhiteSwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at LOOSE tier. If 1e-8 fails but 1e-6 passes, document inline justification + change to 1e-6.

- [ ] **Step 3: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 676, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 4: Commit (engine + probe + test together).**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdHullWhiteSwaptionEngine.java \
        migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/fdhullwhiteswaptionengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdHullWhiteSwaptionEngineTest.java
git commit -s -m "stub(pricingengines.swaption): port FdHullWhiteSwaptionEngine + loose-tier fingerprint test (Phase 2h WI-2)"
git push origin phase-2h-B-fd-hullwhite
```

### Task B.4: Land worktree B to main

Same pattern as Task A.6. May need rebase if WI-3 landed first.

---

## Worktree C — WI-3 FdG2SwaptionEngine

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2h-C/`
**Branch:** `phase-2h-C-fd-g2`

### File structure for WI-3

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdG2SwaptionEngine.java` | Port v1.42.1 (173 LOC) |
| Create | `migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp` | C++ ref |
| Create | `migration-harness/references/pricingengines/swaption/fdg2swaptionengine.json` | reference data |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdG2SwaptionEngineTest.java` | LOOSE-tier fingerprint (per-test exception likely) |

### Task C.1: Rebase + port FdG2SwaptionEngine

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdg2swaptionengine.{hpp,cpp}` (173 LOC).

- [ ] **Step 1: Rebase onto post-WI-1 main.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2h-C
git fetch origin
git rebase origin/main
```

- [ ] **Step 2: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdg2swaptionengine.hpp
sed -n '1,170p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdg2swaptionengine.cpp
```

Note key shape:
- Constructor: `FdG2SwaptionEngine(G2, int xGrid=50, int yGrid=50, int tGrid=50, int dampingSteps=2, double invEps=1e-5, FdmSchemeDesc=Hundsdorfer())`
- `calculate()`:
  - Build 2 × `FdmSimpleProcess1dMesher` (one per OU component of G2)
  - Wrap in `FdmMesherComposite` for 2D
  - Build `FdmG2Op` on the 2D mesh
  - Build `FdmAffineModelSwapInnerValue<G2>` from arguments
  - Build `FdmStepConditionComposite.vanillaComposite(...)`
  - Build `FdmSolverDesc`
  - Run `FdmG2Solver(model, desc, schemeDesc)`
  - Read value via `solver.interpolateAt(0.0, 0.0)`
  - `results_.value = ...`

- [ ] **Step 3: Port to Java** following the C++ `calculate()` line-for-line.

- [ ] **Step 4: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2h-C
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Do NOT commit yet — Task C.2 + C.3 land together.**

### Task C.2: Probe — capture C++ FdG2SwaptionEngine reference

- [ ] **Step 1: Write probe** (same fixture shape as FdHullWhite probe but G2 model + 2D FD):

```cpp
// migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp
auto g2 = ext::make_shared<G2>(ts, 0.1, 0.01, 0.1, 0.005, -0.5);
swaption.setPricingEngine(ext::make_shared<FdG2SwaptionEngine>(g2, 50, 50, 50, 2));
out["fixture"] = {/* same as FdHullWhite + g2 params + xGrid=50, yGrid=50, tGrid=50, dampingSteps=2 */};
out["fd_g2_swaption_npv"] = swaption.NPV();
write_probe_output("fdg2swaptionengine.json", out);
```

- [ ] **Step 2: Generate reference.**

```bash
./migration-harness/scripts/generate-references.sh fdg2swaptionengine_probe 2>&1 | tail -10
```

### Task C.3: Java FdG2SwaptionEngineTest at LOOSE tier (per-test exception likely)

- [ ] **Step 1: Write the test** at LOOSE tier — try 1e-8 first, expect to need 1e-5 with justification:

```java
// 2D FD has lower convergence rate than 1D — expect noise floor ~1e-5.
// If 1e-8 fails, change to 1e-5 with inline comment:
//   "2D FD discretization noise floor — Hundsdorfer-Verwer ADI on a
//    50×50×50 grid converges at ~h^{1.5}-like rate for cross-derivative
//    terms; observed residual ~1e-5."
assertEquals(ref.getDouble("fd_g2_swaption_npv"), swaption.NPV(), 1.0e-8);
```

If 1e-8 fails: change to 1e-5 with inline comment. **Don't loosen below 1e-5 without further investigation.**

- [ ] **Step 2: Run.**

```bash
mvn -pl jquantlib test -Dtest='FdG2SwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at LOOSE tier (with per-test exception if needed).

- [ ] **Step 3: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 677, Failures: 0, Errors: 0, Skipped: 22` (depends on B's land state).

- [ ] **Step 4: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdG2SwaptionEngine.java \
        migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/fdg2swaptionengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdG2SwaptionEngineTest.java
git commit -s -m "stub(pricingengines.swaption): port FdG2SwaptionEngine + loose-tier fingerprint test (Phase 2h WI-3)"
git push origin phase-2h-C-fd-g2
```

### Task C.4: Land worktree C to main

Same pattern as Task A.6 / B.4.

---

## Layer 3 — Completion doc + tag

### Task L3.1: Write `phase2h-completion.md`

- [ ] **Step 1: Gather final state.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
git log --oneline 615806e..HEAD
```

Expected: `Tests run: ~677-679`; `0 stubs`; ~10-12 commits since Phase 2g tip.

- [ ] **Step 2: Write the completion doc** following Phase 2g's structure:
  - Header (date, predecessor tag, what's in)
  - Per-WI summary with commit hashes
  - **WI-1 framework completeness disclosure** (per design §7.8): which sub-layers landed; which optional unit tests were added; whether scheme scope crept beyond Hundsdorfer + Douglas; final class count + LOC delta
  - **WI-2/WI-3 tier disclosure** (per design §7.9): what tier each engine test landed at; per-test exception noise floors observed
  - Final scanner state (still 0)
  - Test suite final state with delta table
  - Deviations from plan (any A4/A13/A15/A16/A17 firings)
  - Phase 2i seed list with explicit transcendental library port option

- [ ] **Step 3: Commit.**

```bash
git add docs/migration/phase2h-completion.md
git commit -s -m "docs(migration): Phase 2h completion report"
git push origin main
```

### Task L3.2: Tag and push

```bash
git tag jquantlib-phase2h-complete
git push origin jquantlib-phase2h-complete
git tag -l 'jquantlib-phase2*'
```

Expected: 8 tags (`phase1` through `phase2h`).

### Task L3.3: Worktree cleanup

```bash
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2h-A 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2h-B 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2h-C 2>&1
git worktree prune
git worktree list

git branch -D phase-2h-A-fdm-framework phase-2h-B-fd-hullwhite phase-2h-C-fd-g2 2>&1 || true
git push origin --delete phase-2h-A-fdm-framework phase-2h-B-fd-hullwhite phase-2h-C-fd-g2 2>&1
```

If `remove --force` fails (Phase 2c-2g precedent), fall back to `rm -rf` then `git worktree prune`.

### Task L3.4: Update memory

Update `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md` description and body with Phase 2h milestone (Fdm framework port + 2 engines + class count + LOC delta + tier outcomes).

Also update `MEMORY.md` index entry.

### Task L3.5: Final verification

```bash
git status
git log --oneline -10
git tag -l 'jquantlib-phase2*'
git worktree list
git branch -a | grep '2h' || echo "no 2h branches"
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
```

Expected: clean state, `phase2h-complete` tag exists, no 2h branches, tests `Failures: 0, Errors: 0, Skipped: 22`, scanner `0 stubs`.

---

## Self-Review notes

- All 11 design exit criteria mapped to tasks: §7.1 (mvn green) → final verification L3.5; §7.2 (test delta) → B.3 + C.3; §7.3 (Skipped: 22) → final verification; §7.4 (scanner WIP=0) → final verification; §7.5 (worktrees gone) → L3.3; §7.6 (probes regenerate) → B.2 + C.2; §7.7 (loose-tier inline justification) → enforced per task; §7.8 (WI-1 framework completeness disclosure) → L3.1; §7.9 (WI-2/WI-3 tier disclosure) → L3.1; §7.10 (completion doc) → L3.1; §7.11 (tag pushed + memory updated) → L3.2 + L3.4.
- All 7 design pause triggers covered: A4 sharpened → A.1.1-A.1.5 (any sub-layer dependency surprise); A6 disabled; A9 → A.6/B.4/C.4 rebase paths; A13 → B.3 + C.3 (transcendental drift in FD long-rollback); A15 → A.1.1-A.1.5 (any sub-layer surfacing previously-hidden bug); A16 → A.1.1-A.1.5 (missing dependency outside ~30 planned classes); A17 → A.1.1-A.1.5 (>2 unplanned align commits).
- The plan does not invent classes/methods that don't exist. Where Java-side API shape isn't pinned by the existing code base, the plan calls out "verify against actual" with the search command.
- All commit messages follow the `<kind>(<pkg>): <verb>` convention with `(Phase 2h WI-N)` suffix.
- WI-1 sub-layer commits in dependency-correct order: 1.1 Operators (A.1.1) → 1.2 Meshers (A.1.2) → 1.4 Schemes (A.1.3) → 1.3 Inner+Step+Boundary (A.1.4) → 1.5 Solvers (A.1.5).
- WI-2 + WI-3 dispatch ONLY after WI-1 lands — explicit in §3 and the L2 prerequisite text.

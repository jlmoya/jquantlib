# Phase 2e Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Each WI runs in its own git worktree (see L0 setup); all 3 worktrees A/B/C run concurrently after L0.

**Goal:** Land the three Phase 2e work items per `docs/migration/phase2e-design.md`: WI-1 G2 model body port (closes the last `work_in_progress` scanner item), WI-2 BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit (completes Phase 2d WI-1's CapHelper vision), WI-3 Swaption pricing infrastructure (BlackSwaptionEngine + DiscretizedSwaption + TreeSwaptionEngine) + SwaptionHelper full body. End state: scanner reports `work_in_progress: 0` (Phase 1's "finish all stubs" mandate fully met), 0 `not_implemented`, 0 `numerical_suspect`; tag `jquantlib-phase2e-complete`.

**Architecture:** Same as Phase 2c/2d — direct commits to `main`, TDD per stub, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes, tolerance tiers (exact/tight/loose). 3 git worktrees per `phase2e-design.md` §3 — A=WI-1 G2, B=WI-2 BlackCapFloorEngine + CapFloor.NPV() + CapHelper retrofit, C=WI-3 Swaption infrastructure + SwaptionHelper body. Each worktree fast-forwards to `main` async; controller orchestrates rebases and force-pushes-with-lease (Phase 2c/2d lessons baked in: always merge from main checkout). Pause triggers per design §5: A6 disabled, A4 sharpened (new `pricingengines.swaption` directory is in scope and planned, not a surprise), A8/A10 inactive, A9 worktree-merge-conflict, A11 NEW for G2 swaption integral path needing non-trivial integrator, A12 NEW for `Swaption.NPV()` wiring needing deeper engine-arguments dispatch refactor.

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 3 worktrees | (main) | 0 |
| L1 | All 3 worktrees launch in parallel | A, B, C | 4–7 each |
| L2 | Completion doc + tag | (main) | 1 commit + tag |

**Non-goals reminder (design §1):** BroadieKaya×3 deferred to Phase 2f (need Lobatto/Laguerre integrator ports); AnalyticCapFloorEngine deferred (redundant with Black engine for WI-2 retrofit goal); JamshidianSwaptionEngine deferred (TreeSwaptionEngine covers HW + G2 + BK in one engine); FdHullWhiteSwaptionEngine, FdG2SwaptionEngine, Gaussian1D variants deferred; Heston `discountBondOption` deferred (chi-squared drift question); BachelierCapFloorEngine deferred (Normal-vol path).

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit's full suite passes. Commit messages follow `<kind>(<pkg>): <verb> ...` with `(Phase 2e WI-N)` suffix.

**Parallelism (P2E-4):** worktrees A/B/C launch their first implementer subagent in parallel after L0. All three are fully independent in the dep graph — no shared files. Per-task spec-reviewer + code-quality-reviewer pipeline stays sequential per the skill rule.

---

## Layer 0 — Pre-flight + worktree setup (no commits)

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree from the main checkout.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean (modulo IDE noise on `jquantlib-parent/.project`, `jquantlib/.classpath`, `.vscode/` — leave alone).

- [ ] **Step 2:** Run baseline test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 649, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 3:** Snapshot scanner state.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected: `work_in_progress: 1` (G2 only).

```bash
grep '"id"' docs/migration/stub-inventory.json
```

Expected: 1 entry — `model.shortrate.twofactormodels.G2#G2`.

- [ ] **Step 4:** Verify the harness is functional and the C++ submodule is pinned.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -3
(cd migration-harness/cpp/quantlib && git rev-parse HEAD)
```

Expected: harness OK; submodule HEAD prints `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

- [ ] **Step 5:** Capture Phase 2d tip.

```bash
git rev-parse main
git tag -l 'jquantlib-phase2d-complete'
```

Expected: tip `06450e6` (or later if any docs landed); tag `jquantlib-phase2d-complete` exists.

### Task 0.2: Create 3 git worktrees

- [ ] **Step 1:** Create branches and worktrees.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2e-A-g2 ../jquantlib-2e-A main
git worktree add -b phase-2e-B-cap-engine ../jquantlib-2e-B main
git worktree add -b phase-2e-C-swaption ../jquantlib-2e-C main
git worktree list
```

Expected:
```
/Users/josemoya/eclipse-workspace/jquantlib              <SHA> [main]
/Users/josemoya/eclipse-workspace/jquantlib-2e-A         <SHA> [phase-2e-A-g2]
/Users/josemoya/eclipse-workspace/jquantlib-2e-B         <SHA> [phase-2e-B-cap-engine]
/Users/josemoya/eclipse-workspace/jquantlib-2e-C         <SHA> [phase-2e-C-swaption]
```

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
(cd ../jquantlib-2e-A/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2e-B/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2e-C/jquantlib && mvn test-compile -q) 2>&1 | tail -3
```

Expected: each prints BUILD SUCCESS (or no errors, exit 0).

- [ ] **Step 3:** Note for the controller — orchestration discipline (Phase 2c/2d lessons baked in):
  - Always run `git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/<branch>` from the **main checkout**, never `cd` into a worktree first.
  - After each worktree lands to main, `git push origin main` from main; any unmerged worktree pulls and rebases onto the new tip before its next implementer dispatch (force-push-with-lease after rebase).
  - If a rebase conflicts → A9 fires (pause and ask).
  - If a subagent watchdog stalls mid-flight (Phase 2d C precedent), controller commits the in-progress state if it's clean and dispatches a focused continuation. Don't restart from scratch unless the worktree is inconsistent.

### Task 0.3: Init progress doc

- [ ] **Step 1:** Create `docs/migration/phase2e-progress.md` with the same shape as `phase2d-progress.md` (header, worktrees table, pause-trigger status, layer/WI progress sections, test-count tracking table). Initial test/scanner state: `649/0/0/22`, scanner WIP=1.

- [ ] **Step 2:** Commit + push.

```bash
git add docs/migration/phase2e-progress.md
git commit -s -m "docs(migration): init phase2e-progress log"
git push origin main
```

- [ ] **Step 3:** Rebase each worktree onto the new main (the docs commit didn't touch any source so this is a fast-forward).

```bash
(cd ../jquantlib-2e-A && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2e-B && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2e-C && git fetch origin && git rebase origin/main) 2>&1 | tail -3
```

---

## Layer 1 — Parallel WI execution

> Worktrees A/B/C dispatch their first implementer in parallel from this point. The ordering inside each worktree is sequential.

---

## Worktree A — WI-1 G2 model body port

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2e-A/`
**Branch:** `phase-2e-A-g2`
**All `mvn` commands run from `<worktree>/jquantlib/`.**

### File structure for WI-1

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `jquantlib/src/main/java/org/jquantlib/model/shortrate/twofactormodels/G2.java` | replace stub body with v1.42.1 port |
| Create | `migration-harness/cpp/probes/model/shortrate/twofactormodels/g2_probe.cpp` | C++ probe for analytic discount + tree fingerprint cells |
| Create | `migration-harness/references/model/shortrate/twofactormodels/g2.json` | reference data (generated by probe) |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/twofactormodels/G2Test.java` | cross-validation tests |

### Task A.1: Port G2 body — Parameter indirection + Dynamics inner class + analytic functions

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/twofactormodels/G2.java` (whole file)

**Reference:** C++ `migration-harness/cpp/quantlib/ql/models/shortrate/twofactormodels/g2.{hpp,cpp}` (full files, ~430 LOC total).

- [ ] **Step 1: Read the C++ source thoroughly.**

```bash
sed -n '1,200p' migration-harness/cpp/quantlib/ql/models/shortrate/twofactormodels/g2.hpp
sed -n '1,250p' migration-harness/cpp/quantlib/ql/models/shortrate/twofactormodels/g2.cpp
```

Note key v1.42.1 details:
- `G2(termStructure, a=0.1, sigma=0.01, b=0.1, eta=0.01, rho=-0.75)` constructor; calls `TwoFactorModel(5)` super then binds `a_(arguments_[0])` etc — Java needs the same `arguments_[i]` indirection pattern (Phase 2b precedent).
- `ConstantParameter(value, PositiveConstraint())` for a, sigma, b, eta; `BoundaryConstraint(-1.0, 1.0)` for rho.
- `generateArguments()` rebuilds `phi_ = FittingParameter(termStructure, a, sigma, b, eta, rho)`.
- `sigmaP(t, s)`, `discountBond(t, T, x, y)`, `discountBondOption(type, strike, maturity, bondMaturity)`, `V(t)`, `A(t, T)`, `B(x, t)` are all closed-form analytic helpers (lines 60-145 g2.cpp).
- `swaption(arguments, fixedRate, range, intervals)` uses `SegmentIntegral` (line 150-200 g2.cpp). Uses inner `SwaptionPricingFunction`. **A11 trigger watch:** if `SegmentIntegral` doesn't expose the same operator() interface as C++, pause.
- Inner `Dynamics` class extends `TwoFactorModel.ShortRateDynamics`; uses two `OrnsteinUhlenbeckProcess` instances (one for x, one for y) plus correlation. `shortRate(t, x, y)` returns `phi_(t) + x + y`.
- Inner `FittingParameter` extends `TermStructureFittingParameter`; uses an internal `Impl` to compute `phi(t) = forward(t) + 0.5*V(t)` (where V is from G2.V).

- [ ] **Step 2: Replace G2.java's body.** The current Java file (~470 lines) has imports + class header + 3-arg ctor + a few stubbed bodies that throw `UnsupportedOperationException`. Keep the existing imports + class header + Quality annotation; replace the implementation. Strategy: write the full ported body, then verify field/method shapes match what `TwoFactorModel`, `Parameter`, `ConstantParameter`, `PositiveConstraint`, `BoundaryConstraint`, `TermStructureFittingParameter`, `OrnsteinUhlenbeckProcess` actually expose in Java. If any helper has a different signature than C++, adjust to the actual Java API.

Key constructor pattern (mirrors Phase 2b one-factor models):

```java
    public G2(final Handle<YieldTermStructure> termStructure,
              final double a, final double sigma, final double b,
              final double eta, final double rho) {
        super(5);  // 5 parameters
        this.termStructureConsistentModelClass =
                new TermStructureConsistentModelClass(termStructure);
        // Phase 2b indirection pattern: arguments_[i] is the canonical Parameter slot.
        this.a_     = arguments_.get(0);
        this.sigma_ = arguments_.get(1);
        this.b_     = arguments_.get(2);
        this.eta_   = arguments_.get(3);
        this.rho_   = arguments_.get(4);

        a_.setParam(new ConstantParameter(a,     new PositiveConstraint()));
        // ... etc; check actual Parameter / ConstantParameter assignment API in Java
        // (Phase 2b commits like 244bc92 / dc02443 show the actual pattern)

        generateArguments();
        registerWith(termStructure);
    }
```

(Verify against Phase 2b commits `dc02443` (Vasicek), `244bc92` (HullWhite), `072d25d` (BlackKarasinski), `82697d2` (CIR) — these show the canonical Parameter-ref-via-arguments_ pattern that worked.)

Inner classes to add:

```java
    private final class Dynamics extends TwoFactorModel.ShortRateDynamics {
        private final Parameter fitting_;

        Dynamics(final Parameter fitting, final double a, final double sigma,
                 final double b, final double eta, final double rho) {
            super(new OrnsteinUhlenbeckProcess(a, sigma, 0.0, 0.0),
                  new OrnsteinUhlenbeckProcess(b, eta,  0.0, 0.0),
                  rho);
            this.fitting_ = fitting;
        }
        @Override
        public double shortRate(final double t, final double x, final double y) {
            return fitting_.getOperatorEq(t) + x + y;  // verify accessor name
        }
    }

    private static final class FittingParameter extends TermStructureFittingParameter {
        // Inner Impl that computes phi(t) = forward(t) + 0.5*V(t),
        // where V is the G2 V(t) helper. Mirror C++ g2.cpp lines 30-50 of FittingParameter.
        // ... same shape as Phase 2b's HullWhite FittingParameter / Vasicek FittingParameter.
    }
```

(For `FittingParameter`, look at the existing Java `HullWhite.FittingParameter` or `BlackKarasinski.FittingParameter` for the structural pattern.)

Analytic helpers:

```java
    private double sigmaP(final double t, final double s) {
        // Mirror C++ g2.cpp lines 60-75 verbatim.
    }
    public double discountBond(final double now, final double maturity, final Array factors) {
        QL.require(factors.size() > 1, g2_model_needs_two_factors);
        return discountBond(now, maturity, factors.get(0), factors.get(1));
    }
    public double discountBond(final double t, final double T, final double x, final double y) {
        return A(t, T) * Math.exp(-B(a(), T - t) * x - B(b(), T - t) * y);
    }
    @Override
    public double discountBondOption(final Option.Type type, final double strike,
            final double maturity, final double bondMaturity) {
        final double v = sigmaP(maturity, bondMaturity);
        final double f = termStructure().currentLink().discount(bondMaturity);
        final double k = termStructure().currentLink().discount(maturity) * strike;
        return blackFormula(type, k, f, v);  // Java has BlackFormula.blackFormula static
    }
    private double V(final double t) {
        // Mirror C++ g2.cpp lines 95-110 verbatim.
    }
    private double A(final double t, final double T) {
        // Mirror C++ g2.cpp lines 115-125 verbatim.
    }
    private double B(final double x, final double t) {
        return (1.0 - Math.exp(-x * t)) / x;
    }
```

`swaption(...)` — port C++ g2.cpp lines 150-200. Uses `SegmentIntegral` and an inner `SwaptionPricingFunction` cost function. **A11 watch.** If integrator API doesn't match cleanly, the `swaption` method can be left throwing `UnsupportedOperationException("Phase 2f seam")` — it's not strictly required for the scanner-WIP closure (which only needs the class to be unstubbed; the model + tree path is the primary value).

- [ ] **Step 3: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2e-A
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Run baseline (no test added yet — must be unchanged).**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 649, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 5: Run scanner.**

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected: `work_in_progress: 0` (G2 closed!).

- [ ] **Step 6: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/twofactormodels/G2.java
git commit -s -m "stub(model.shortrate.twofactormodels): G2 model body port (Phase 2e WI-1)"
```

### Task A.2: Probe — capture C++ G2 reference

**Files:**
- Create: `migration-harness/cpp/probes/model/shortrate/twofactormodels/g2_probe.cpp`
- Create: `migration-harness/references/model/shortrate/twofactormodels/g2.json` (output)

- [ ] **Step 1: Look at sibling probes for the convention.**

```bash
ls migration-harness/cpp/probes/model/shortrate/onefactor*/
cat migration-harness/cpp/probes/model/shortrate/onefactor/hullwhite_probe.cpp 2>/dev/null | head -40
```

Match include style + output helper name (`write_probe_output` or whatever the convention is).

- [ ] **Step 2: Write the probe.**

```cpp
// migration-harness/cpp/probes/model/shortrate/twofactormodels/g2_probe.cpp
// Phase 2e WI-1: G2 analytic discount fingerprint.
#include <ql/quantlib.hpp>
#include "common.hpp"
using namespace QuantLib;

int main() {
    const Date eval(15, January, 2026);
    Settings::instance().evaluationDate() = eval;
    const auto dc = Actual365Fixed();
    const Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(eval, 0.05, dc, Continuous));

    // (a, sigma, b, eta, rho) per spec §4 fixture
    G2 g2(ts, 0.1, 0.01, 0.1, 0.005, -0.5);

    nlohmann::json out;
    out["fixture"] = {{"eval_date","2026-01-15"},{"flat_rate",0.05},
                      {"a",0.1},{"sigma",0.01},{"b",0.1},
                      {"eta",0.005},{"rho",-0.5}};

    // Analytic discount(t) at multiple maturities
    nlohmann::json disc = nlohmann::json::array();
    for (double t : {0.5, 1.0, 2.0, 5.0, 10.0}) {
        disc.push_back({{"t", t}, {"discount", g2.discount(t)}});
    }
    out["analytic_discount"] = disc;

    // discountBondOption fingerprints
    nlohmann::json dbo = nlohmann::json::array();
    for (double k : {0.95, 1.0, 1.05}) {
        dbo.push_back({{"strike", k},
                       {"call_5y_to_10y", g2.discountBondOption(Option::Call, k, 5.0, 10.0)}});
    }
    out["discount_bond_option"] = dbo;

    // Tree fingerprint — build a TimeGrid and a tree, sample a few cells.
    TimeGrid grid(10.0, 5);   // 5 steps to T=10
    auto tree = g2.tree(grid);
    nlohmann::json cells = nlohmann::json::array();
    for (int i = 0; i < 5; ++i) {
        for (int j = 0; j < tree->size(i); ++j) {
            cells.push_back({{"i", i}, {"j", j},
                             {"underlying", tree->underlying(i, j)}});
        }
    }
    out["tree_cells"] = cells;

    write_probe_output("g2.json", out);
    return 0;
}
```

(Adjust `tree->underlying(i, j)` to whatever Java `TreeLattice2D.underlying(...)` exposes — check the actual C++ API; it may be `tree->presentValue(...)` or grid-driven values.)

- [ ] **Step 3: Generate the reference.**

```bash
./migration-harness/scripts/generate-references.sh g2_probe 2>&1 | tail -10
```

If the generate script can't build (submodule not initialized in this worktree), build via the main worktree's pre-warmed cpp/build (Phase 2d B/C precedent) and copy the JSON back.

- [ ] **Step 4: Commit.**

```bash
git add migration-harness/cpp/probes/model/shortrate/twofactormodels/g2_probe.cpp \
        migration-harness/references/model/shortrate/twofactormodels/g2.json
# Add CMakeLists.txt if not glob-driven
git commit -s -m "infra(harness): g2_probe + reference JSON (Phase 2e WI-1)"
```

### Task A.3: Java G2Test — analytic discount fingerprint

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/twofactormodels/G2Test.java`

- [ ] **Step 1: Write `G2Test.java`.** Pattern from existing tests like `HullWhiteCalibrationTest`:

```java
package org.jquantlib.testsuite.model.shortrate.twofactormodels;

import static org.junit.Assert.assertEquals;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.instruments.Option;
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.harness.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeGrid;
import org.junit.Test;

public class G2Test {

    @Test
    public void testAnalyticDiscountFingerprint() {
        final var ref = ReferenceReader.load("g2.json");
        final Date eval = new Date(15, Month.January, 2026);
        // ... set up FlatForward + Handle<YieldTermStructure> ts matching the probe ...
        final G2 g2 = new G2(ts, 0.1, 0.01, 0.1, 0.005, -0.5);

        // tight tier: closed-form affine
        for (var row : ref.getArray("analytic_discount")) {
            final double t = row.getDouble("t");
            assertEquals(row.getDouble("discount"), g2.discount(t), 1.0e-12);
        }
    }

    @Test
    public void testDiscountBondOptionFingerprint() {
        final var ref = ReferenceReader.load("g2.json");
        // ... same fixture ...
        final G2 g2 = new G2(ts, 0.1, 0.01, 0.1, 0.005, -0.5);
        for (var row : ref.getArray("discount_bond_option")) {
            final double k = row.getDouble("strike");
            assertEquals(row.getDouble("call_5y_to_10y"),
                    g2.discountBondOption(Option.Type.Call, k, 5.0, 10.0),
                    1.0e-12);
        }
    }

    @Test
    public void testTreeFingerprint() {
        final var ref = ReferenceReader.load("g2.json");
        // ... same fixture ...
        final G2 g2 = new G2(ts, 0.1, 0.01, 0.1, 0.005, -0.5);
        final TimeGrid grid = new TimeGrid(10.0, 5);
        final var tree = g2.tree(grid);
        // Loose tier: Brent solver in TermStructureFittingParameter,
        // same precedent as Phase 2c WI-5 BK tree fingerprint.
        for (var row : ref.getArray("tree_cells")) {
            final int i = row.getInt("i");
            final int j = row.getInt("j");
            assertEquals(row.getDouble("underlying"),
                    tree.underlying(i, j),  // verify actual TreeLattice2D API
                    1.0e-8);
        }
    }
}
```

(Verify `ReferenceReader` API — it may be `getJsonArray`, `getNode`, etc. Check `jquantlib/src/test/java/org/jquantlib/testsuite/harness/ReferenceReader.java` and look at how Phase 2c-era tests use it.)

- [ ] **Step 2: Run.**

```bash
mvn -pl jquantlib test -Dtest='G2Test' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: 3 tests pass — discount + discountBondOption at tight, tree at loose.

If discount or discountBondOption fail tight, root-cause first (likely `A(t,T)` or `sigmaP` formula off). If tree fails loose, the tree construction or `B(a, dt)` calculation may differ from C++; document with inline justification or root-cause.

- [ ] **Step 3: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 652, Failures: 0, Errors: 0, Skipped: 22` (+3 from G2Test).

- [ ] **Step 4: Commit.**

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/twofactormodels/G2Test.java
git commit -s -m "test(model.shortrate.twofactormodels): G2 analytic + tree fingerprints (Phase 2e WI-1)"
git push origin phase-2e-A-g2
```

### Task A.4 (optional): G2.swaption integral path

If the G2 swaption integral path was implemented (not stubbed), add a `testSwaptionIntegral` method to G2Test using a probe for one well-posed swaption fixture. Loose tier (quadrature noise floor).

If `swaption(...)` was left as `UnsupportedOperationException("Phase 2f seam")`, skip this task — document as Phase 2f follow-up in the completion doc.

### Task A.5: Land worktree A to main

- [ ] **Step 1: From main checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2e-A-g2
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -5
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

If `merge --ff-only` refuses (because B or C landed first), rebase first:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2e-A
git fetch origin
git rebase origin/main
git push --force-with-lease origin phase-2e-A-g2
```

Then re-attempt the merge from main checkout. Conflicts → A9 fires.

---

## Worktree B — WI-2 BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2e-B/`
**Branch:** `phase-2e-B-cap-engine`

### File structure for WI-2

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BlackCapFloorEngine.java` | uncomment + port v1.42.1 body |
| Modify | `jquantlib/src/main/java/org/jquantlib/instruments/CapFloor.java` | wire NPV() to dispatch through engine_.calculate() |
| Modify | `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java` | add modelValue + blackPrice assertions at tight tier (probe already exists from Phase 2d) |

### Task B.1: Port `BlackCapFloorEngine` body

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BlackCapFloorEngine.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/capfloor/blackcapfloorengine.{hpp,cpp}`.

- [ ] **Step 1: Read the C++ source.**

```bash
sed -n '1,80p' migration-harness/cpp/quantlib/ql/pricingengines/capfloor/blackcapfloorengine.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/pricingengines/capfloor/blackcapfloorengine.cpp
```

Note structure:
- Three constructors: `(yts, vol, dc)`, `(yts, vol-handle, dc)`, `(yts, optionletVolStruct)`.
- `calculate()` iterates over `arguments_.startDates`, computes `forward = arguments_.forwards[i]`, `strike` from cap/floor type, `stdDev = vol * sqrt(t)`, `value += discount * accrualTime * blackFormula(...)`.
- Inherits from `CapFloor::engine` (which gives access to `arguments_` and `results_`).

- [ ] **Step 2: Replace the Java file.** Currently every line is commented out. Uncomment + port.

```java
package org.jquantlib.pricingengines.capfloor;

// imports — match the existing commented imports plus what's needed:
// BlackFormula, OptionletVolatilityStructure, ConstantOptionletVolatility,
// CapFloor, Handle, Quote, YieldTermStructure, etc.

import org.jquantlib.instruments.CapFloor;
import org.jquantlib.pricingengines.BlackFormula;
// ... etc

public class BlackCapFloorEngine extends CapFloor.EngineImpl {

    private final Handle<YieldTermStructure> termStructure_;
    private final Handle<OptionletVolatilityStructure> volatility_;
    private final double displacement_;

    public BlackCapFloorEngine(final Handle<YieldTermStructure> ts,
            final double vol, final DayCounter dc) {
        this(ts, new Handle<>(new SimpleQuote(vol)), dc, 0.0);
    }

    public BlackCapFloorEngine(final Handle<YieldTermStructure> ts,
            final Handle<Quote> vol, final DayCounter dc) {
        this(ts, vol, dc, 0.0);
    }

    public BlackCapFloorEngine(final Handle<YieldTermStructure> ts,
            final Handle<Quote> vol, final DayCounter dc, final double displacement) {
        this.termStructure_ = ts;
        this.volatility_ = new Handle<>(
                new ConstantOptionletVolatility(0, new NullCalendar(),
                        BusinessDayConvention.Following, vol, dc));
        this.displacement_ = displacement;
        ts.addObserver(this);
        this.volatility_.addObserver(this);
    }

    public BlackCapFloorEngine(final Handle<YieldTermStructure> ts,
            final Handle<OptionletVolatilityStructure> vol) {
        this.termStructure_ = ts;
        this.volatility_ = vol;
        this.displacement_ = 0.0;
        ts.addObserver(this);
        vol.addObserver(this);
    }

    @Override
    public void calculate() {
        final CapFloor.ArgumentsImpl args = (CapFloor.ArgumentsImpl) arguments_;
        final CapFloor.ResultsImpl   res  = (CapFloor.ResultsImpl) results_;
        double value = 0.0;
        double vega = 0.0;
        // Iterate over optionlets. Mirror C++ blackcapfloorengine.cpp lines 60-130.
        // For each i:
        //   final double t = ... (volatility_.timeFromReference(args.fixingDates[i]));
        //   final double a = args.accrualTimes[i];
        //   final double discount = termStructure_.currentLink().discount(args.endDates[i]);
        //   final double forward = args.forwards[i];
        //   final double strike = (cap)? args.capRates[i] : args.floorRates[i];
        //   final double stdDev = volatility_.currentLink().volatility(t, strike) * Math.sqrt(t);
        //   value += discount * a * BlackFormula.blackFormula(...);
        // ...
        res.value = value;
    }
}
```

(Verify the actual `CapFloor.ArgumentsImpl` field names by reading `jquantlib/src/main/java/org/jquantlib/instruments/CapFloor.java`. The field names may differ from C++ — `forwards` vs `forwardRates`, `accrualTimes` vs `accrualTimes_`, etc.)

If `ConstantOptionletVolatility` doesn't exist in Java, port it as a small helper class as part of this task — small (~30 LOC) mechanical port from C++ `ql/termstructures/volatility/optionlet/constantoptionletvol.{hpp,cpp}`.

- [ ] **Step 3: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2e-B
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Run baseline (no behavior change should break existing tests).**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 649, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 5: Do NOT commit yet — Tasks B.2 + B.3 land together.**

### Task B.2: Wire `CapFloor.NPV()` to engine

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/instruments/CapFloor.java`

- [ ] **Step 1: Find current state.**

```bash
grep -n "NPV\|setupArguments\|fetchResults\|calculate\|engine_" jquantlib/src/main/java/org/jquantlib/instruments/CapFloor.java | head -20
```

The `NPV()` method may currently return 0 or NaN. The standard Instrument pattern:

```java
@Override
public double NPV() {
    calculate();  // inherited from Instrument
    return NPV;   // or this.value, depending on what the class uses
}
```

And `calculate()` (from Instrument or LazyObject) needs to dispatch through `engine_.calculate()`.

- [ ] **Step 2: Look at how Java's `Swap` handles this.** `Swap.java` already wires NPV() correctly via `DiscountingSwapEngine` (which works — Phase 2d WI-1 verified). Mirror that pattern.

- [ ] **Step 3: Apply the wiring.** The change should be small — likely just:
  - Confirm `setupArguments(args)` populates the optionlet rate/date/discount info that BlackCapFloorEngine.calculate() reads
  - Confirm `fetchResults(res)` reads `value` back out
  - Make sure `NPV()` calls `calculate()` and returns the result

If the change is non-mechanical (e.g. CapFloor doesn't have proper Engine/Arguments/Results plumbing), this is bigger than expected — escalate as A12 (similar to Swaption wiring concern).

- [ ] **Step 4: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Smoke-test by manually constructing a CapFloor with the engine** (optional manual verification — proper test in B.3).

### Task B.3: Extend CapHelperTest with modelValue + blackPrice assertions

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java`

The Phase 2d caphelper_probe.json already captures `model_value` and `black_price_at_vol`. The current test asserts only `fairRate`. Now extend it.

- [ ] **Step 1: Read the existing test.**

```bash
cat jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java
```

- [ ] **Step 2: Add `modelValue()` + `blackPrice(0.20)` assertions.**

Existing test (Phase 2d) likely sets up CapHelper with the `BlackCapFloorEngine` constructor and calls `marketValue()`. Now also call `modelValue()` and `blackPrice(0.20)`, and assert against probe values at tight tier:

```java
        // Phase 2e WI-2 retrofit: now that BlackCapFloorEngine is real,
        // these assertions go from "documented Phase 2e seam" to enforced.
        // tight tier (1e-12 rel + 1e-14 abs); closed-form Black-76.
        assertEquals(ref.getDouble("model_value"),
                h.modelValue(), 1.0e-12);
        assertEquals(ref.getDouble("black_price_at_vol"),
                h.blackPrice(0.20), 1.0e-12);
```

- [ ] **Step 3: Run the test.**

```bash
mvn -pl jquantlib test -Dtest='CapHelperTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at tight tier on all three assertions.

If FAIL on `modelValue` or `blackPrice` at tight, root-cause:
- Is `BlackCapFloorEngine.calculate()` producing the right value?
- Is `CapFloor.NPV()` actually dispatching through the engine?
- Are the optionlet `forward` / `accrualTime` / `discount` values correct?

Don't loosen tier without inline justification.

- [ ] **Step 4: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 649, Failures: 0, Errors: 0, Skipped: 22` (+0 if extending an existing test method, +2 if adding a new test method with the modelValue assertion + a separate one for blackPrice).

- [ ] **Step 5: Commit (bundles B.1 + B.2 + B.3 — they need to land atomically because the test depends on the engine + NPV wiring).**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BlackCapFloorEngine.java \
        jquantlib/src/main/java/org/jquantlib/instruments/CapFloor.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java
# Add ConstantOptionletVolatility.java if you ported it
git commit -s -m "stub(pricingengines.capfloor): port BlackCapFloorEngine + CapFloor.NPV() wiring + CapHelper retrofit (Phase 2e WI-2)"
git push origin phase-2e-B-cap-engine
```

### Task B.4: Land worktree B to main

- [ ] **Step 1: From main checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2e-B-cap-engine
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -5
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

If A landed first, rebase first (same pattern as Task A.5).

---

## Worktree C — WI-3 Swaption pricing infrastructure + SwaptionHelper full body

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2e-C/`
**Branch:** `phase-2e-C-swaption`

### File structure for WI-3

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/BlackSwaptionEngine.java` | port v1.42.1 blackswaptionengine.{hpp,cpp} |
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/DiscretizedSwaption.java` | helper for tree-based swaption valuation |
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/TreeSwaptionEngine.java` | generic lattice-based swaption engine |
| Modify | `jquantlib/src/main/java/org/jquantlib/instruments/Swaption.java` | wire NPV() if needed (similar to CapFloor in WI-2) |
| Modify | `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java` | full body port (replaces Phase 2d compile-only stub) |
| Create | 3 probe files under `migration-harness/cpp/probes/pricingengines/swaption/` | C++ refs for blackswaption, treeswaption, swaptionhelper |
| Create | 3 reference JSONs under `migration-harness/references/pricingengines/swaption/` | reference data |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/BlackSwaptionEngineTest.java` | tight-tier fingerprint |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/TreeSwaptionEngineTest.java` | loose-tier fingerprint |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/SwaptionHelperTest.java` | modelValue + blackPrice cross-validation |

### Task C.1: Port `BlackSwaptionEngine`

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/BlackSwaptionEngine.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/blackswaptionengine.{hpp,cpp}` (412 LOC total).

- [ ] **Step 1: Read the C++ source.**

```bash
sed -n '1,200p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/blackswaptionengine.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/blackswaptionengine.cpp
```

Note: C++ has a templated `BlackStyleSwaptionEngine<Spec>` with two `Spec` types (`Black76Spec`, `BachelierSpec`). For Phase 2e we only port the Black76 path — Bachelier is the BachelierCapFloorEngine sibling, deferred to Phase 2f. Java implementation can be a single class `BlackSwaptionEngine` (no template, just the Black76 path).

- [ ] **Step 2: Write the Java port.**

```java
package org.jquantlib.pricingengines.swaption;

// imports — Handle, Quote, YieldTermStructure, SwaptionVolatilityStructure,
// VanillaSwap, Swaption, BlackFormula, etc.

public class BlackSwaptionEngine extends Swaption.EngineImpl {

    private final Handle<YieldTermStructure> termStructure_;
    private final Handle<SwaptionVolatilityStructure> volatility_;
    private final double displacement_;

    public BlackSwaptionEngine(final Handle<YieldTermStructure> ts,
            final double vol) {
        this(ts, new Handle<>(new SimpleQuote(vol)));
    }

    public BlackSwaptionEngine(final Handle<YieldTermStructure> ts,
            final Handle<Quote> vol) {
        // wraps in a ConstantSwaptionVolatility(...) — port that helper if needed
        // (small: ~50 LOC from constantswaptionvol.{hpp,cpp})
        this.termStructure_ = ts;
        this.volatility_ = ...;
        this.displacement_ = 0.0;
        ts.addObserver(this);
        this.volatility_.addObserver(this);
    }

    public BlackSwaptionEngine(final Handle<YieldTermStructure> ts,
            final Handle<SwaptionVolatilityStructure> vol) {
        this.termStructure_ = ts;
        this.volatility_ = vol;
        this.displacement_ = 0.0;
        ts.addObserver(this);
        vol.addObserver(this);
    }

    @Override
    public void calculate() {
        // Mirror C++ blackswaptionengine.cpp lines ~50-150:
        //   - extract underlying VanillaSwap from Swaption.arguments
        //     (the underlying swap, exercise dates, fixed-leg properties)
        //   - compute par swap rate (atmForward) and forward annuity (numeraire)
        //   - get volatility from the SwaptionVolatilityStructure
        //   - stdDev = vol * sqrt(timeToExpiry)
        //   - value = numeraire * BlackFormula.blackFormula(type, atmStrike, atmForward, stdDev, displacement_)
        // ... fill in from C++ source
    }
}
```

(Verify `Swaption.EngineImpl` exists — if not, the Swaption Engine pattern needs to be set up. Check `Swaption.java`.)

- [ ] **Step 3: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2e-C
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Do NOT commit yet — tests + helpers in subsequent tasks.**

### Task C.2: Probe — BlackSwaptionEngine fingerprint

**Files:**
- Create: `migration-harness/cpp/probes/pricingengines/swaption/blackswaptionengine_probe.cpp`
- Create: `migration-harness/references/pricingengines/swaption/blackswaptionengine.json`

- [ ] **Step 1: Write probe.**

```cpp
// migration-harness/cpp/probes/pricingengines/swaption/blackswaptionengine_probe.cpp
// Phase 2e WI-3: BlackSwaptionEngine NPV fingerprint.
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
    // Build a 5Y×5Y ATM payer swaption.
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

    // Compute ATM fixed rate first
    auto swap0 = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, 100.0, fixedSchedule, 0.04, Thirty360(Thirty360::European),
        floatSchedule, idx, 0.0, dc);
    swap0->setPricingEngine(ext::make_shared<DiscountingSwapEngine>(ts));
    const Real atmRate = swap0->fairRate();

    auto swap = ext::make_shared<VanillaSwap>(
        VanillaSwap::Payer, 100.0, fixedSchedule, atmRate, Thirty360(Thirty360::European),
        floatSchedule, idx, 0.0, dc);

    Swaption swaption(swap, exercise);
    swaption.setPricingEngine(ext::make_shared<BlackSwaptionEngine>(ts, 0.20, dc));

    nlohmann::json out;
    out["fixture"] = {{"eval_date","2026-01-15"},{"flat_rate",0.05},
                      {"vol",0.20},{"atm_rate",atmRate},
                      {"exercise_years",5},{"swap_years",5},
                      {"fixed_freq","Annual"},{"float_freq","3M"}};
    out["swaption_npv"] = swaption.NPV();
    write_probe_output("blackswaptionengine.json", out);
    return 0;
}
```

- [ ] **Step 2: Generate the reference.**

```bash
./migration-harness/scripts/generate-references.sh blackswaptionengine_probe 2>&1 | tail -10
```

- [ ] **Step 3: Commit (probe only — Java code stays uncommitted until test passes).**

```bash
git add migration-harness/cpp/probes/pricingengines/swaption/blackswaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/blackswaptionengine.json
git commit -s -m "infra(harness): blackswaptionengine_probe + reference JSON (Phase 2e WI-3)"
```

### Task C.3: BlackSwaptionEngineTest at tight tier

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/BlackSwaptionEngineTest.java`

- [ ] **Step 1: Write the test.** Mirror the probe fixture exactly:

```java
package org.jquantlib.testsuite.pricingengines.swaption;

import static org.junit.Assert.assertEquals;

// imports

public class BlackSwaptionEngineTest {

    @Test
    public void testNPVMatchesCpp() {
        final var ref = ReferenceReader.load("blackswaptionengine.json");
        // ... mirror probe fixture: eval date, FlatForward 5%, Euribor3M index,
        //     5Y×5Y ATM payer swaption, vol=0.20 ...
        final BlackSwaptionEngine engine = new BlackSwaptionEngine(ts, 0.20);
        // ... build VanillaSwap, Swaption, set engine ...
        // tight tier: closed-form Black76
        assertEquals(ref.getDouble("swaption_npv"), swaption.NPV(), 1.0e-12);
    }
}
```

- [ ] **Step 2: Run.**

```bash
mvn -pl jquantlib test -Dtest='BlackSwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at tight tier.

- [ ] **Step 3: Commit BlackSwaptionEngine + test together.**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/BlackSwaptionEngine.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/BlackSwaptionEngineTest.java
# Add ConstantSwaptionVolatility if ported
git commit -s -m "stub(pricingengines.swaption): port BlackSwaptionEngine + tight-tier fingerprint test (Phase 2e WI-3)"
```

### Task C.4: Port `DiscretizedSwaption` helper

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/DiscretizedSwaption.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/discretizedswaption.{hpp,cpp}` (183 LOC total).

- [ ] **Step 1: Read the C++ source.**

```bash
sed -n '1,80p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/discretizedswaption.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/discretizedswaption.cpp
```

Note: extends `DiscretizedOption` (which is `DiscretizedAsset` + Exercise wrapping). Java has both base classes already at `org.jquantlib.instruments.DiscretizedOption` and `DiscretizedAsset`.

- [ ] **Step 2: Write the Java port.**

```java
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.instruments.DiscretizedAsset;
import org.jquantlib.instruments.DiscretizedOption;
import org.jquantlib.instruments.Swaption;
// ...

public class DiscretizedSwaption extends DiscretizedOption {

    private final Swaption.ArgumentsImpl arguments_;
    // ... fields per C++ discretizedswaption.hpp lines 30-50 ...

    public DiscretizedSwaption(final Swaption.ArgumentsImpl args,
            final Date referenceDate, final DayCounter dayCounter) {
        super(/* underlying = DiscretizedSwap built from args */, args.exercise.type(), /* exerciseTimes */);
        this.arguments_ = args;
        // ... port C++ ctor logic from discretizedswaption.cpp lines 30-80 ...
    }

    @Override
    public void reset(final int size) {
        // ... port C++ reset(...) lines 100-130 ...
    }

    @Override
    public List<Time> mandatoryTimes() {
        // ... port C++ mandatoryTimes() lines 140-160 ...
    }
}
```

(The full port has nuanced logic — call the methods/fields actually present in Java's `DiscretizedAsset` / `DiscretizedOption`. Verify before coding.)

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Do NOT commit yet — TreeSwaptionEngine + test in next tasks.**

### Task C.5: Port `TreeSwaptionEngine`

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/TreeSwaptionEngine.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/treeswaptionengine.{hpp,cpp}` (170 LOC total).

- [ ] **Step 1: Read the C++ source.**

```bash
sed -n '1,80p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/treeswaptionengine.hpp
sed -n '1,150p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/treeswaptionengine.cpp
```

Note: extends a generic `LatticeShortRateModelEngine` template; for Java, a non-template class works since we know the Swaption type.

- [ ] **Step 2: Write the Java port.**

```java
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.instruments.Swaption;
import org.jquantlib.methods.lattices.Lattice;
import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.time.TimeGrid;

public class TreeSwaptionEngine extends Swaption.EngineImpl {

    private final ShortRateModel model_;
    private final TimeGrid timeGrid_;
    private final int timeSteps_;
    private final Handle<YieldTermStructure> termStructure_;

    public TreeSwaptionEngine(final ShortRateModel model, final int timeSteps,
            final Handle<YieldTermStructure> ts) {
        this.model_ = model;
        this.timeSteps_ = timeSteps;
        this.timeGrid_ = null;  // built lazily in calculate()
        this.termStructure_ = ts;
        model.addObserver(this);
        ts.addObserver(this);
    }

    public TreeSwaptionEngine(final ShortRateModel model, final TimeGrid grid,
            final Handle<YieldTermStructure> ts) {
        this.model_ = model;
        this.timeSteps_ = 0;
        this.timeGrid_ = grid;
        this.termStructure_ = ts;
        model.addObserver(this);
        ts.addObserver(this);
    }

    @Override
    public void calculate() {
        // Mirror C++ treeswaptionengine.cpp lines ~30-100:
        //   - build TimeGrid from arguments_.exercise + timeSteps_ if timeGrid_ is null
        //   - build the model's tree on that grid
        //   - construct DiscretizedSwaption from arguments_
        //   - initialize swaption on the lattice at exercise time
        //   - rollback to t=0
        //   - results_.value = swaption.presentValue()
    }
}
```

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

### Task C.6: Probe + test for TreeSwaptionEngine

**Files:**
- Create: `migration-harness/cpp/probes/pricingengines/swaption/treeswaptionengine_probe.cpp`
- Create: `migration-harness/references/pricingengines/swaption/treeswaptionengine.json`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/TreeSwaptionEngineTest.java`

- [ ] **Step 1: Write probe** — same fixture as BlackSwaptionEngine probe but with HullWhite model + TreeSwaptionEngine instead of Black engine:

```cpp
// Use HW (a=0.1, sigma=0.01) tree with 100 steps; capture swaption.NPV().
auto hw = ext::make_shared<HullWhite>(ts, 0.1, 0.01);
swaption.setPricingEngine(ext::make_shared<TreeSwaptionEngine>(hw, 100, ts));
out["swaption_npv_hw_tree"] = swaption.NPV();
```

- [ ] **Step 2: Generate reference + commit probe-only.**

```bash
./migration-harness/scripts/generate-references.sh treeswaptionengine_probe
git add migration-harness/cpp/probes/pricingengines/swaption/treeswaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/treeswaptionengine.json
git commit -s -m "infra(harness): treeswaptionengine_probe + reference JSON (Phase 2e WI-3)"
```

- [ ] **Step 3: Write `TreeSwaptionEngineTest`.** Loose tier (Brent solver in tree calibration):

```java
        // Loose tier: same Brent-noise floor as Phase 2c WI-5 BK tree fingerprint.
        assertEquals(ref.getDouble("swaption_npv_hw_tree"), swaption.NPV(), 1.0e-8);
```

- [ ] **Step 4: Run.**

```bash
mvn -pl jquantlib test -Dtest='TreeSwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at loose tier with inline justification.

- [ ] **Step 5: Commit DiscretizedSwaption + TreeSwaptionEngine + test.**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/DiscretizedSwaption.java \
        jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/TreeSwaptionEngine.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/TreeSwaptionEngineTest.java
git commit -s -m "stub(pricingengines.swaption): port DiscretizedSwaption + TreeSwaptionEngine + loose-tier fingerprint test (Phase 2e WI-3)"
```

### Task C.7: Wire `Swaption.NPV()` if needed

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/instruments/Swaption.java` (if NPV doesn't dispatch to engine)

- [ ] **Step 1: Check current state.**

```bash
grep -n "NPV\|setupArguments\|fetchResults\|calculate\|engine_" jquantlib/src/main/java/org/jquantlib/instruments/Swaption.java | head -15
```

If NPV() already dispatches via Instrument's calculate() pattern, no change needed.

If not — apply the same wiring pattern as `Swap.java` and `CapFloor.java` (post-WI-2). If the change is non-mechanical, **A12 fires** — escalate.

- [ ] **Step 2: If a wiring change was needed, commit it separately.**

```bash
git add jquantlib/src/main/java/org/jquantlib/instruments/Swaption.java
git commit -s -m "align(instruments): Swaption.NPV() wiring to engine_.calculate() (Phase 2e WI-3)"
```

### Task C.8: Port SwaptionHelper full body

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/models/shortrate/calibrationhelpers/swaptionhelper.{hpp,cpp}`.

- [ ] **Step 1: Read the C++ source.**

```bash
sed -n '1,150p' migration-harness/cpp/quantlib/ql/models/shortrate/calibrationhelpers/swaptionhelper.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/models/shortrate/calibrationhelpers/swaptionhelper.cpp
```

Note: SwaptionHelper has two ctor variants (period-based + start-date-based). Both build an underlying VanillaSwap + Swaption. Same structural shape as Phase 2d's CapHelper port.

- [ ] **Step 2: Replace SwaptionHelper.java's body.** The current Java file is the Phase 2d compile-only stub (extends BlackCalibrationHelper but every method returns 0). Replace with the v1.42.1 port:

```java
package org.jquantlib.model.shortrate.calibrationhelpers;

// imports — VanillaSwap, Swaption, EuropeanExercise, Schedule, IborIndex,
// DiscountingSwapEngine, BlackSwaptionEngine, etc.

public class SwaptionHelper extends BlackCalibrationHelper {

    private final Period maturity_;
    private final Period length_;
    private final Handle<Quote> volatility_;
    private final IborIndex index_;
    private final Period fixedLegTenor_;
    private final DayCounter fixedLegDayCounter_;
    private final DayCounter floatingLegDayCounter_;
    private final Handle<YieldTermStructure> termStructure_;

    private VanillaSwap swap_;
    private Swaption swaption_;
    private double exerciseRate_;

    // Period-based ctor (matches Phase 2d's compile-only stub signature
    // expanded with the v1.42.1 v1.42.1 parameters).
    public SwaptionHelper(final Period maturity, final Period length,
            final Handle<Quote> volatility, final IborIndex index,
            final Period fixedLegTenor, final DayCounter fixedLegDayCounter,
            final DayCounter floatingLegDayCounter,
            final Handle<YieldTermStructure> termStructure) {
        // call super with defaults — error-type=RelativePriceError, vol-type=ShiftedLognormal, shift=0.0
        super(volatility);
        this.maturity_ = maturity;
        // ... etc
        this.termStructure_.addObserver(this);
        this.volatility_.addObserver(this);
        this.index_.addObserver(this);
    }

    // Start-date-based ctor: same as period-based but takes (exerciseDate, endDate)
    public SwaptionHelper(final Date exerciseDate, final Date endDate,
            final Handle<Quote> volatility, final IborIndex index,
            final Period fixedLegTenor, final DayCounter fixedLegDayCounter,
            final DayCounter floatingLegDayCounter,
            final Handle<YieldTermStructure> termStructure) {
        // ... similar setup
    }

    @Override
    protected void performCalculations() {
        // Mirror C++ swaptionhelper.cpp lines 60-120:
        //   - Build floatSchedule (from exercise to endDate, index_.tenor())
        //   - Build fixedSchedule (from exercise to endDate, fixedLegTenor_)
        //   - Build VanillaSwap with placeholder fixed rate (e.g. 0.04)
        //   - Set DiscountingSwapEngine on it
        //   - Compute fairRate = swap.fairRate()
        //   - Rebuild VanillaSwap with the fair rate
        //   - Build EuropeanExercise on the exercise date
        //   - Build Swaption from VanillaSwap + Exercise
        //   - super.performCalculations() to set marketValue_ via blackPrice
    }

    @Override
    public void addTimesTo(final ArrayList<Time> times) {
        // Collect mandatory times from DiscretizedSwaption
        // Mirror C++ swaptionhelper.cpp addTimesTo() lines.
    }

    @Override
    public double modelValue() {
        calculate();
        swaption_.setPricingEngine(engine_);
        return swaption_.NPV();
    }

    @Override
    public double blackPrice(final double sigma) {
        calculate();
        final Handle<Quote> vol = new Handle<>(new SimpleQuote(sigma));
        final BlackSwaptionEngine engine = new BlackSwaptionEngine(termStructure_, vol);
        swaption_.setPricingEngine(engine);
        final double value = swaption_.NPV();
        swaption_.setPricingEngine(engine_);
        return value;
    }
}
```

(Verify the Phase 2d compile-only stub's ctor signature matches one of the two new ctors. If the existing Phase 2d signature `(Handle<Quote>, Handle<YieldTermStructure>, boolean)` doesn't match, keep it as a deprecated 3-arg ctor that delegates to the 8-arg ctor with reasonable defaults.)

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Do NOT commit yet — probe + test land together.**

### Task C.9: Probe + test for SwaptionHelper

**Files:**
- Create: `migration-harness/cpp/probes/model/shortrate/calibrationhelpers/swaptionhelper_probe.cpp`
- Create: `migration-harness/references/model/shortrate/calibrationhelpers/swaptionhelper.json`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/SwaptionHelperTest.java`

- [ ] **Step 1: Write probe** — fixture similar to BlackSwaptionEngine but using SwaptionHelper API with HullWhite for modelValue:

```cpp
// migration-harness/cpp/probes/model/shortrate/calibrationhelpers/swaptionhelper_probe.cpp
#include <ql/quantlib.hpp>
#include "common.hpp"
using namespace QuantLib;

int main() {
    Settings::instance().evaluationDate() = Date(15, January, 2026);
    const auto dc = Actual365Fixed();
    const Handle<YieldTermStructure> ts(...);
    const auto idx = ext::make_shared<Euribor3M>(ts);
    const Handle<Quote> vol(ext::make_shared<SimpleQuote>(0.20));

    SwaptionHelper helper(5*Years, 5*Years, vol, idx,
                          1*Years, Thirty360(...), dc, ts);
    auto hw = ext::make_shared<HullWhite>(ts, 0.1, 0.01);
    helper.setPricingEngine(ext::make_shared<JamshidianSwaptionEngine>(hw, ts));
    // OR TreeSwaptionEngine if Jamshidian is unavailable in port

    nlohmann::json out;
    out["fixture"] = {...};
    out["market_value"] = helper.marketValue();
    out["black_price_at_vol"] = helper.blackPrice(0.20);
    out["model_value"] = helper.modelValue();
    write_probe_output("swaptionhelper.json", out);
    return 0;
}
```

(For modelValue, use either C++'s JamshidianSwaptionEngine or TreeSwaptionEngine on the C++ side — both work. Java will use TreeSwaptionEngine since Jamshidian is deferred.)

- [ ] **Step 2: Generate reference.**

```bash
./migration-harness/scripts/generate-references.sh swaptionhelper_probe
```

- [ ] **Step 3: Write `SwaptionHelperTest`.**

```java
public class SwaptionHelperTest {
    @Test
    public void testSwaptionHelperMatchesCpp() {
        final var ref = ReferenceReader.load("swaptionhelper.json");
        // ... mirror probe fixture ...
        final SwaptionHelper helper = new SwaptionHelper(...);
        final HullWhite hw = new HullWhite(ts, 0.1, 0.01);
        helper.setPricingEngine(new TreeSwaptionEngine(hw, 100, ts));

        // tight tier: closed-form Black76 for blackPrice
        assertEquals(ref.getDouble("black_price_at_vol"), helper.blackPrice(0.20), 1.0e-12);
        // tight tier: marketValue is blackPrice(volatility_.value())
        assertEquals(ref.getDouble("market_value"), helper.marketValue(), 1.0e-12);
        // loose tier: tree-based modelValue (Brent solver in HW tree)
        assertEquals(ref.getDouble("model_value"), helper.modelValue(), 1.0e-8);
    }
}
```

- [ ] **Step 4: Run.**

```bash
mvn -pl jquantlib test -Dtest='SwaptionHelperTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: 1 test, 3 assertions, all pass at the documented tiers.

- [ ] **Step 5: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: ~656, Failures: 0, Errors: 0, Skipped: 22` (from worktree C's pre-merge baseline of ~654 + test counts above).

- [ ] **Step 6: Commit (probe + SwaptionHelper port + test).**

```bash
git add migration-harness/cpp/probes/model/shortrate/calibrationhelpers/swaptionhelper_probe.cpp \
        migration-harness/references/model/shortrate/calibrationhelpers/swaptionhelper.json \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/SwaptionHelperTest.java
git commit -s -m "stub(model.shortrate.calibrationhelpers): SwaptionHelper full body port + probe + cross-validation test (Phase 2e WI-3)"
git push origin phase-2e-C-swaption
```

### Task C.10: Land worktree C to main

- [ ] **Step 1: From main checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2e-C-swaption
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -10
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

If A or B landed first, rebase first (same pattern as Task A.5/B.4).

---

## Layer 2 — Completion doc + tag

### Task L2.1: Write `phase2e-completion.md`

**Files:**
- Create: `docs/migration/phase2e-completion.md`

- [ ] **Step 1: From the main checkout, gather final state.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
git log --oneline 06450e6..HEAD
```

Expected: `Tests run: ~660, Failures: 0, Errors: 0, Skipped: 22`; scanner `work_in_progress: 0` (G2 closed!); 12-15 commits since Phase 2d tip.

- [ ] **Step 2: Write the completion doc** following Phase 2d's structure:
  - Header (date, predecessor tag, what's in)
  - Per-WI summary with commit hashes
  - Final scanner state — emphasize the symbolic milestone (`work_in_progress: 0` reaches Phase 1's "finish all stubs" mandate)
  - Test suite final state with delta table
  - Deviations from plan (any per-test loose-tier exceptions, any A4/A11/A12 firings, any optional tasks like A.4 G2 swaption integral path skipped)
  - Phase 2f seed list (BroadieKaya×3 + Lobatto/Laguerre integrators + AnalyticCapFloorEngine + JamshidianSwaptionEngine + Heston.discountBondOption + remaining swaption engines + BachelierCapFloorEngine + any new seeds)
  - Worktree cleanup checklist

- [ ] **Step 3: Commit.**

```bash
git add docs/migration/phase2e-completion.md
git commit -s -m "docs(migration): Phase 2e completion report"
git push origin main
```

### Task L2.2: Tag `jquantlib-phase2e-complete` and push

- [ ] **Step 1:** Tag and push.

```bash
git tag jquantlib-phase2e-complete
git push origin jquantlib-phase2e-complete
git tag -l 'jquantlib-phase2*'
```

Expected: 5 tags now (`phase1`, `phase2a`, `phase2b`, `phase2c`, `phase2d`, `phase2e`).

### Task L2.3: Worktree cleanup

- [ ] **Step 1: Remove the 3 worktrees.**

```bash
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2e-A 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2e-B 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2e-C 2>&1
git worktree prune
git worktree list
```

If any `remove --force` fails with "Directory not empty" (Phase 2c/2d precedent), fall back to:

```bash
rm -rf /Users/josemoya/eclipse-workspace/jquantlib-2e-A
rm -rf /Users/josemoya/eclipse-workspace/jquantlib-2e-B
rm -rf /Users/josemoya/eclipse-workspace/jquantlib-2e-C
git worktree prune
```

- [ ] **Step 2: Delete the branches local + remote.**

```bash
git branch -D phase-2e-A-g2 phase-2e-B-cap-engine phase-2e-C-swaption 2>&1 || true
git push origin --delete phase-2e-A-g2 phase-2e-B-cap-engine phase-2e-C-swaption 2>&1
```

- [ ] **Step 3: Update memory.**

Update `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md` description and body with Phase 2e milestone (date, tag, tip, test count delta, scanner WIP delta = **1 → 0** symbolic milestone, key deviations).

Also update `MEMORY.md` index entry.

- [ ] **Step 4: Final verification.**

```bash
git status
git log --oneline -10
git tag -l 'jquantlib-phase2*'
git worktree list
git branch -a | grep '2e' || echo "no 2e branches"
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
```

Expected:
- branch `main`, working tree clean
- log shows recent Phase 2e commits + the completion doc
- `phase2e-complete` tag exists
- only the main worktree exists
- no `2e` branches anywhere
- tests `Failures: 0, Errors: 0, Skipped: 22`
- **scanner `work_in_progress: 0`** (Phase 1 mandate complete)

---

## Self-Review notes

- All 9 design exit criteria mapped to tasks: §7.1 (mvn green) → final verification L2.3 Step 4; §7.2 (test delta) → A.3 + B.3 + C.3 + C.6 + C.9; §7.3 (Skipped: 22) → final verification (no un-skip work); §7.4 (scanner WIP=0) → A.1 Step 5 + final verification; §7.5 (worktrees gone) → L2.3; §7.6 (probes regenerate) → A.2 + C.2 + C.6 + C.9 use `generate-references.sh`; §7.7 (loose-tier inline justification) → enforced per task (A.3, C.6, C.9); §7.8 (completion doc) → L2.1; §7.9 (tag pushed + memory updated) → L2.2 + L2.3.
- All 6 design pause triggers covered: A4 → B.1 ConstantOptionletVolatility port, C.1 ConstantSwaptionVolatility port (small mechanical ports inside existing packages); A6 disabled; A9 → A.5/B.4/C.10 step 1 (rebase-conflict path); A11 → A.1 Step 2 (G2 swaption integrator gotcha); A12 → B.2 Step 3 + C.7 Step 1 (CapFloor and Swaption NPV wiring escalation paths).
- The plan does not invent classes/methods that don't exist. Where Java-side API shape isn't pinned by the existing code base, the plan calls out "verify against actual" with the search command.
- All commit messages follow the `<kind>(<pkg>): <verb>` convention with `(Phase 2e WI-N)` suffix.
- Optional task A.4 (G2 swaption integral path) is genuinely optional — the WI's primary value (scanner WIP closure + analytic + tree fingerprint) is delivered without it.
- WI-2 reuses Phase 2d's `caphelper_probe.json` (no new probe needed) — explicit in the design and reflected in B.3.

# Phase 2f Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Each WI runs in its own git worktree (see L0 setup); all 3 worktrees A/B/C run concurrently after L0.

**Goal:** Land the three Phase 2f work items per `docs/migration/phase2f-design.md`: WI-1 cap engines (AnalyticCapFloor + BachelierCapFloor + Bachelier path in BlackCapFloor), WI-2 swaption engines + G2.swaption (JamshidianSwaption + Bachelier path in BlackSwaption + G2.swaption integral path), WI-3 Heston BroadieKaya + NCCS tightening (Lobatto + Laguerre integrators + Fourier-inversion harness + 3 BroadieKaya schemes + tighten Java NCCS to bit-faithfully match C++ + un-stub HestonProcess.discountBondOption at TIGHT tier + promote Phase 2d WI-2 NCCV tests from loose to tight tier if NCCS tightening makes it possible). End state: scanner WIP unchanged at 0; tests 656 → ~672; tag `jquantlib-phase2f-complete`.

**Architecture:** Same as Phase 2c/2d/2e — direct commits to `main`, TDD per stub, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes, tolerance tiers (exact/tight/loose). 3 git worktrees per `phase2f-design.md` §3 — A=cap engines, B=swaption engines + G2.swaption, C=Heston BroadieKaya + NCCS tightening. Each worktree fast-forwards to `main` async; controller orchestrates rebases and force-pushes-with-lease (Phase 2c/2d/2e lessons baked in: always merge from main checkout). Pause triggers per design §5: A6 disabled, A4 sharpened (Lobatto/Laguerre/Fourier in scope, planned), A8/A10/A11/A12 inactive, A9 worktree-merge-conflict, A13 NEW for NCCS structural drift impossibility, A14 NEW for Complex arithmetic infrastructure gap.

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 3 worktrees, init progress doc | (main) | 1 |
| L1 | All 3 worktrees launch in parallel | A, B, C | 4–8 each |
| L2 | Completion doc + tag | (main) | 1 commit + tag |

**Non-goals reminder (design §1):** FdHullWhite/FdG2 swaption engines, Gaussian1D family, additionalResults, addTimesTo Time-impedance, TreeLattice2D API formalization, HaltonRsg FMA docs, SABR 5e-8 investigation, BlackSwaption Cash/ParYieldCurve settlement path, Phase 3+ packages — all deferred.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit's full suite passes. Commit messages follow `<kind>(<pkg>): <verb> ...` with `(Phase 2f WI-N)` suffix.

**Parallelism (P2F-4):** worktrees A/B/C launch their first implementer subagent in parallel after L0. All three are fully independent in the dep graph — no shared files. Per-task spec-reviewer + code-quality-reviewer pipeline stays sequential per the skill rule.

---

## Layer 0 — Pre-flight + worktree setup (1 commit for progress doc)

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean (modulo IDE noise on `.project`, `.classpath`, `.vscode/` — leave alone).

- [ ] **Step 2:** Run baseline test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 656, Failures: 0, Errors: 0, Skipped: 22`.

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

- [ ] **Step 5:** Capture Phase 2e tip.

```bash
git rev-parse main
git tag -l 'jquantlib-phase2e-complete'
```

Expected: tip `a533fbd` (or later if any docs landed); tag exists.

### Task 0.2: Create 3 git worktrees

- [ ] **Step 1:** Create branches and worktrees.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2f-A-cap-engines ../jquantlib-2f-A main
git worktree add -b phase-2f-B-swaption-engines ../jquantlib-2f-B main
git worktree add -b phase-2f-C-heston-bk ../jquantlib-2f-C main
git worktree list
```

Expected: 4 worktrees listed (main + 3 new).

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
(cd ../jquantlib-2f-A/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2f-B/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2f-C/jquantlib && mvn test-compile -q) 2>&1 | tail -3
```

Expected: each prints BUILD SUCCESS or empty (exit 0).

- [ ] **Step 3:** Note for the controller — orchestration discipline (Phase 2c/2d/2e lessons baked in):
  - Always run `git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/<branch>` from the **main checkout**, never `cd` into a worktree first.
  - After each worktree lands, any unmerged worktree pulls and rebases onto the new tip before its next implementer dispatch (force-push-with-lease after rebase).
  - If a rebase conflicts → A9 fires.
  - If a subagent watchdog stalls mid-flight, controller commits the in-progress state if clean and dispatches a focused continuation. Don't restart from scratch unless the worktree is inconsistent.
  - If implementers use the main worktree's pre-warmed `cpp/build`, expect orphan files in main checkout — diff-then-rm before merge (Phase 2d/2e precedent).

### Task 0.3: Init progress doc

- [ ] **Step 1:** Create `docs/migration/phase2f-progress.md` with the same shape as `phase2e-progress.md` (header, worktrees table, pause-trigger status, layer/WI progress sections, test-count tracking table). Initial state: `656/0/0/22`, scanner WIP=0.

- [ ] **Step 2:** Commit + push.

```bash
git add docs/migration/phase2f-progress.md
git commit -s -m "docs(migration): init phase2f-progress log"
git push origin main
```

- [ ] **Step 3:** Rebase each worktree onto the new main (the docs commit didn't touch any source so this is a fast-forward).

```bash
(cd ../jquantlib-2f-A && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2f-B && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2f-C && git fetch origin && git rebase origin/main) 2>&1 | tail -3
```

---

## Layer 1 — Parallel WI execution

> Worktrees A/B/C dispatch their first implementer in parallel from this point. The ordering inside each worktree is sequential.

---

## Worktree A — WI-1 Cap engines

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2f-A/`
**Branch:** `phase-2f-A-cap-engines`
**All `mvn` commands run from `<worktree>/jquantlib/`.**

### File structure for WI-1

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `jquantlib/src/main/java/org/jquantlib/termstructures/volatilities/optionlet/OptionletVolatilityStructure.java` | Add `volatilityType()` and `displacement()` accessors with default impls |
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/AnalyticCapFloorEngine.java` | Replace stub with v1.42.1 port (175 LOC) |
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BachelierCapFloorEngine.java` | New port (207 LOC) |
| Modify | `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BlackCapFloorEngine.java` | Add Bachelier branch on `volatilityType_` |
| Modify | `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/CapHelper.java` | Replace `case Normal: throw ...` with `BachelierCapFloorEngine` construction |
| Create | 2 probe files under `migration-harness/cpp/probes/pricingengines/capfloor/` | C++ refs |
| Create | 2 reference JSONs under `migration-harness/references/pricingengines/capfloor/` | reference data |
| Create | 2 new test files | tight-tier fingerprints |
| Modify | `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java` | Add Normal-vol case |

### Task A.1: OVS volType/displacement API alignment

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/termstructures/volatilities/optionlet/OptionletVolatilityStructure.java`

- [ ] **Step 1: Read the current Java OVS class and its v1.42.1 C++ counterpart.**

```bash
cat jquantlib/src/main/java/org/jquantlib/termstructures/volatilities/optionlet/OptionletVolatilityStructure.java | head -80
sed -n '1,120p' migration-harness/cpp/quantlib/ql/termstructures/volatility/optionlet/optionletvolatilitystructure.hpp
```

- [ ] **Step 2: Add `volatilityType()` and `displacement()` accessors.** If Java's OVS is abstract or an interface, add default impls that return `VolatilityType.ShiftedLognormal` and `0.0` respectively for back-compat:

```java
import org.jquantlib.model.VolatilityType;

// inside OptionletVolatilityStructure (or its abstract base):
public VolatilityType volatilityType() {
    return VolatilityType.ShiftedLognormal;
}

public double displacement() {
    return 0.0;
}
```

(If Java's OVS hierarchy doesn't have a single common ancestor where this fits, place the methods on the most derived shared class. Verify by checking subclasses with `grep -rn "extends OptionletVolatilityStructure" jquantlib/src/main/java`.)

- [ ] **Step 3: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2f-A
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/termstructures/volatilities/optionlet/OptionletVolatilityStructure.java
git commit -s -m "align(termstructures.volatilities.optionlet): add volatilityType + displacement accessors (Phase 2f WI-1)"
```

### Task A.2: Port AnalyticCapFloorEngine

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/AnalyticCapFloorEngine.java` (replace existing stub)

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/capfloor/analyticcapfloorengine.{hpp,cpp}` (175 LOC total). Read both first.

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/pricingengines/capfloor/analyticcapfloorengine.hpp
sed -n '1,120p' migration-harness/cpp/quantlib/ql/pricingengines/capfloor/analyticcapfloorengine.cpp
```

Note key shape:
- Constructor: `AnalyticCapFloorEngine(ext::shared_ptr<AffineModel>, Handle<YieldTermStructure>)`
- Inherits from `GenericModelEngine<AffineModel, CapFloor::arguments, CapFloor::results>`
- `calculate()`: iterates over optionlets; for each computes `model.discountBondOption(Option.Type.Put|Call, strike, fixingTime, paymentTime)` and accumulates weighted sum
- The Cap maps to Put options on bonds (and Floor → Call), inverted from the cap/floor direction

- [ ] **Step 2: Replace the existing Java stub.** Current state of `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/AnalyticCapFloorEngine.java` is empty stub (verified earlier — `public class AnalyticCapFloorEngine{ public AnalyticCapFloorEngine(){}` with everything commented out).

```java
package org.jquantlib.pricingengines.capfloor;

// imports — Handle, YieldTermStructure, AffineModel, CapFloor, Option,
// GenericModelEngine (or equivalent base), QL, etc.

public class AnalyticCapFloorEngine extends CapFloor.EngineImpl {

    private final AffineModel model_;
    private final Handle<YieldTermStructure> termStructure_;

    public AnalyticCapFloorEngine(final AffineModel model,
            final Handle<YieldTermStructure> termStructure) {
        this.model_ = model;
        this.termStructure_ = termStructure;
        // Phase 2e WI-2 wired CapFloor.EngineImpl extends GenericEngine<...>;
        // observers go through the base class
        if (termStructure != null) termStructure.addObserver(this);
        if (model != null) model.addObserver(this);
    }

    @Override
    public void calculate() {
        QL.require(model_ != null, "null model");

        // Mirror C++ analyticcapfloorengine.cpp lines ~38-120:
        //   - extract referenceDate + dayCounter from model's termStructure() if it
        //     implements TermStructureConsistentModel, else from termStructure_
        //   - iterate over arguments_.endDates / fixingDates / capRates / floorRates
        //   - for each optionlet, compute fixingTime = dc.yearFraction(referenceDate, fixingDates[i])
        //     and paymentTime = dc.yearFraction(referenceDate, endDates[i])
        //   - if Cap: value += accrual * model.discountBondOption(Put, strike, fixingTime, paymentTime) / strikeRate
        //     (verify against C++ lines for exact factoring; this is the standard cap-via-bond-option formula)
        //   - if Floor: value += accrual * model.discountBondOption(Call, strike, fixingTime, paymentTime) / strikeRate
        //   - if Collar: handle both
        //   - results_.value = value
    }
}
```

(Verify Java's `AffineModel` interface exposes `discountBondOption(Option.Type, double, double, double)` — Phase 2c WI-1 added it for HW; Phase 2e WI-1 added it for G2. If the Java interface signature differs from C++, adapt.)

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Do NOT commit yet — Tasks A.3 (probe) + A.4 (test) land together with this.**

### Task A.3: Probe — capture C++ AnalyticCapFloorEngine reference

**Files:**
- Create: `migration-harness/cpp/probes/pricingengines/capfloor/analyticcapfloorengine_probe.cpp`
- Create: `migration-harness/references/pricingengines/capfloor/analyticcapfloorengine.json`

- [ ] **Step 1: Look at sibling probe convention.**

```bash
ls migration-harness/cpp/probes/pricingengines/swaption/  # Phase 2e precedent
head -40 migration-harness/cpp/probes/pricingengines/swaption/blackswaptionengine_probe.cpp
```

- [ ] **Step 2: Write the probe.**

```cpp
// migration-harness/cpp/probes/pricingengines/capfloor/analyticcapfloorengine_probe.cpp
// Phase 2f WI-1: AnalyticCapFloorEngine NPV fingerprint with HullWhite model.
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
    Schedule schedule(eval + 0*Days, eval + 5*Years, 3*Months,
                      TARGET(), ModifiedFollowing, ModifiedFollowing,
                      DateGeneration::Forward, false);
    Leg floatingLeg = IborLeg(schedule, idx)
        .withNotionals(100.0)
        .withPaymentAdjustment(idx->businessDayConvention())
        .withFixingDays(0);

    auto cap = ext::make_shared<Cap>(floatingLeg, std::vector<Rate>(1, 0.05));

    auto hw = ext::make_shared<HullWhite>(ts, 0.1, 0.01);
    cap->setPricingEngine(ext::make_shared<AnalyticCapFloorEngine>(hw, ts));

    nlohmann::json out;
    out["fixture"] = {{"eval_date","2026-01-15"},{"flat_rate",0.05},
                      {"hw_a",0.1},{"hw_sigma",0.01},
                      {"cap_strike",0.05},{"cap_years",5},
                      {"index_tenor_months",3}};
    out["analytic_cap_npv"] = cap->NPV();
    write_probe_output("analyticcapfloorengine.json", out);
    return 0;
}
```

- [ ] **Step 3: Generate the reference JSON.** If the worktree's submodule isn't initialized, build from main worktree's pre-warmed cpp/build (Phase 2d/2e precedent).

```bash
./migration-harness/scripts/generate-references.sh analyticcapfloorengine_probe 2>&1 | tail -10
```

- [ ] **Step 4: Verify the JSON has finite values.**

```bash
cat migration-harness/references/pricingengines/capfloor/analyticcapfloorengine.json
```

### Task A.4: Java AnalyticCapFloorEngineTest at TIGHT tier

**Files:**
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/capfloor/AnalyticCapFloorEngineTest.java`

- [ ] **Step 1: Write the test.** Mirror probe fixture exactly:

```java
package org.jquantlib.testsuite.pricingengines.capfloor;

import static org.junit.Assert.assertEquals;
// imports

public class AnalyticCapFloorEngineTest {

    @Test
    public void testNPVMatchesCpp() {
        final var ref = ReferenceReader.load("analyticcapfloorengine.json");
        // mirror probe fixture: eval=2026-01-15, FlatForward 5%, Euribor3M, 5Y cap @ 5%, HW(0.1, 0.01)
        // ... build ts, idx, Schedule, IborLeg, Cap, HullWhite, AnalyticCapFloorEngine ...
        cap.setPricingEngine(new AnalyticCapFloorEngine(hw, ts));
        // tight tier: closed-form analytic via discountBondOption
        assertEquals(ref.getDouble("analytic_cap_npv"), cap.NPV(), 1.0e-12);
    }
}
```

(Verify the `ReferenceReader` API — Phase 2c-era tests use `getDouble(...)`. Match the existing convention.)

- [ ] **Step 2: Run.**

```bash
mvn -pl jquantlib test -Dtest='AnalyticCapFloorEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at tight tier.

If FAIL, root-cause: AffineModel.discountBondOption signature mismatch, fixingTime/paymentTime arithmetic, accrual factoring. **Don't loosen tier without inline justification.**

- [ ] **Step 3: Commit (probe + Java engine + test together).**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/AnalyticCapFloorEngine.java \
        migration-harness/cpp/probes/pricingengines/capfloor/analyticcapfloorengine_probe.cpp \
        migration-harness/references/pricingengines/capfloor/analyticcapfloorengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/capfloor/AnalyticCapFloorEngineTest.java
git commit -s -m "stub(pricingengines.capfloor): port AnalyticCapFloorEngine + tight-tier fingerprint test (Phase 2f WI-1)"
```

### Task A.5: Port BachelierCapFloorEngine

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BachelierCapFloorEngine.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/capfloor/bacheliercapfloorengine.{hpp,cpp}` (207 LOC total).

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/pricingengines/capfloor/bacheliercapfloorengine.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/pricingengines/capfloor/bacheliercapfloorengine.cpp
```

- [ ] **Step 2: Port to Java.** Same structural shape as Phase 2e WI-2 BlackCapFloorEngine but uses `BlackFormula.bachelierBlackFormula(...)` instead of `blackFormula(...)`. Three constructors mirroring the Black engine.

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS. If `BlackFormula.bachelierBlackFormula` doesn't exist in Java, port it as a small static method on `BlackFormula` (~30 LOC; mirrors C++ blackformula.hpp `bachelierBlackFormula`).

- [ ] **Step 4: Do NOT commit yet — probe + test land together.**

### Task A.6: Probe + test for BachelierCapFloorEngine

- [ ] **Step 1: Write probe** mirroring AnalyticCapFloorEngine_probe but using `BachelierCapFloorEngine(ts, 0.01, dc)` (normal-vol = 1% absolute):

```cpp
auto cap = ext::make_shared<Cap>(floatingLeg, std::vector<Rate>(1, 0.05));
cap->setPricingEngine(ext::make_shared<BachelierCapFloorEngine>(ts, 0.01, dc));
out["bachelier_cap_npv"] = cap->NPV();
write_probe_output("bacheliercapfloorengine.json", out);
```

- [ ] **Step 2: Generate reference.**

```bash
./migration-harness/scripts/generate-references.sh bacheliercapfloorengine_probe 2>&1 | tail -10
```

- [ ] **Step 3: Write `BachelierCapFloorEngineTest`** at TIGHT tier:

```java
final var ref = ReferenceReader.load("bacheliercapfloorengine.json");
// mirror fixture
final BachelierCapFloorEngine engine = new BachelierCapFloorEngine(ts, 0.01, new Actual365Fixed());
cap.setPricingEngine(engine);
assertEquals(ref.getDouble("bachelier_cap_npv"), cap.NPV(), 1.0e-12);
```

- [ ] **Step 4: Run.**

```bash
mvn -pl jquantlib test -Dtest='BachelierCapFloorEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: PASS; full suite at +2 over baseline (+1 from this, +1 from Analytic).

- [ ] **Step 5: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BachelierCapFloorEngine.java \
        jquantlib/src/main/java/org/jquantlib/pricingengines/BlackFormula.java \
        migration-harness/cpp/probes/pricingengines/capfloor/bacheliercapfloorengine_probe.cpp \
        migration-harness/references/pricingengines/capfloor/bacheliercapfloorengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/capfloor/BachelierCapFloorEngineTest.java
# Drop BlackFormula.java from add if no edit was needed
git commit -s -m "stub(pricingengines.capfloor): port BachelierCapFloorEngine + tight-tier fingerprint test (Phase 2f WI-1)"
```

### Task A.7: BlackCapFloorEngine Bachelier branch + CapHelper retrofit

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BlackCapFloorEngine.java`
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/CapHelper.java`
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java`

- [ ] **Step 1: Add Bachelier runtime branch in BlackCapFloorEngine.calculate().**

```java
@Override
public void calculate() {
    final VolatilityType type = volatility_.currentLink().volatilityType();
    if (type == VolatilityType.Normal) {
        // Bachelier path — use bachelierBlackFormula instead of blackFormula
        // ... Mirror the existing Black76 loop structure but call BlackFormula.bachelierBlackFormula
    } else {
        // existing ShiftedLognormal path (Phase 2e WI-2)
    }
}
```

- [ ] **Step 2: In CapHelper.blackPrice, replace the `case Normal: throw ...` (Phase 2d WI-1) with BachelierCapFloorEngine construction.**

Find:
```java
case Normal:
    throw new UnsupportedOperationException(
            "VolatilityType.Normal requires BachelierCapFloorEngine "
                    + "(Phase 2e seed)");
```

Replace with:
```java
case Normal:
    engine = new BachelierCapFloorEngine(termStructure_, vol, new Actual365Fixed());
    break;
```

- [ ] **Step 3: Extend CapHelperTest with a Normal-vol case.**

```java
@Test
public void testCapHelperNormalVolBachelierPath() {
    // Same fixture as existing testCapHelperFingerprintMatchesCpp but with VolatilityType.Normal
    // Probe needs to be extended too — see Step 4.
    // ... assert at tight tier ...
}
```

- [ ] **Step 4: Extend `caphelper_probe.cpp` with a Normal-vol case** and regenerate the JSON. Add `BachelierCapFloorEngine` arm + a `bachelier_model_value` and `bachelier_black_price` field to the JSON.

- [ ] **Step 5: Run.**

```bash
mvn -pl jquantlib test -Dtest='CapHelperTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: all CapHelperTest methods pass; full suite at +3 over baseline.

- [ ] **Step 6: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/BlackCapFloorEngine.java \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/CapHelper.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/calibrationhelpers/CapHelperTest.java \
        migration-harness/cpp/probes/model/shortrate/calibrationhelpers/caphelper_probe.cpp \
        migration-harness/references/model/shortrate/calibrationhelpers/caphelper.json
git commit -s -m "stub(pricingengines.capfloor,model.shortrate.calibrationhelpers): BlackCapFloorEngine Bachelier branch + CapHelper Normal-vol retrofit (Phase 2f WI-1)"
git push origin phase-2f-A-cap-engines
```

### Task A.8: Land worktree A to main

- [ ] **Step 1: From the MAIN checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2f-A-cap-engines
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -5
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

If `merge --ff-only` refuses (because B or C landed first), rebase first:

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2f-A
git fetch origin
git rebase origin/main
git push --force-with-lease origin phase-2f-A-cap-engines
```

Then re-attempt the merge from main checkout. Conflicts → A9 fires.

---

## Worktree B — WI-2 Swaption engines + G2.swaption

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2f-B/`
**Branch:** `phase-2f-B-swaption-engines`

### File structure for WI-2

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `jquantlib/src/main/java/org/jquantlib/termstructures/SwaptionVolatilityStructure.java` | Add `volatilityType()` and `shift()` accessors |
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/JamshidianSwaptionEngine.java` | New port (201 LOC) |
| Modify | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/BlackSwaptionEngine.java` | Add Bachelier branch |
| Modify | `jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java` | Choose Black/Bachelier engine in `blackPrice` |
| Modify | `jquantlib/src/main/java/org/jquantlib/model/shortrate/twofactormodels/G2.java` | Replace `swaption(...)` Phase 2e A11 stub with port |
| Modify | `jquantlib/src/main/java/org/jquantlib/instruments/VanillaSwap.java` | (conditional) fix setupArguments inverted isAssignableFrom + List capacity |
| Create | 3 probe files | Jamshidian + Bachelier + G2 swaption refs |
| Create | 3 reference JSONs | reference data |
| Create | 2 new test files + extend G2Test | tight + tight + loose tier fingerprints |

### Task B.1: SwVS volType/shift API alignment

Mirror the structure of Task A.1 but for `SwaptionVolatilityStructure`. Add `volatilityType()` returning `ShiftedLognormal` and `shift()` returning `0.0` as defaults.

Commit:
```bash
git commit -s -m "align(termstructures): SwaptionVolatilityStructure add volatilityType + shift accessors (Phase 2f WI-2)"
```

### Task B.2: Port JamshidianSwaptionEngine

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/JamshidianSwaptionEngine.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/jamshidianswaptionengine.{hpp,cpp}` (201 LOC total).

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/pricingengines/swaption/jamshidianswaptionengine.hpp
sed -n '1,200p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/jamshidianswaptionengine.cpp
```

Note key shape:
- Constructor: `JamshidianSwaptionEngine(ext::shared_ptr<OneFactorAffineModel>, Handle<YieldTermStructure>)`
- Inner `rStarFinder` class is a Brent cost function: takes (model, nominal, maturity, valueTime, fixedPayTimes, amounts), `operator()(Rate x)` returns `value = strike - sum(amounts[i] * model.discountBond(maturity, times[i], x) / B)`
- `calculate()`:
  - QL_REQUIRE settlement is Physical or Cash/CollateralizedCashPrice (NOT ParYieldCurve)
  - QL_REQUIRE exercise is European
  - QL_REQUIRE swap.spread() == 0
  - Build `fixedPayTimes`, `amounts` from arguments_.swap.fixedLeg
  - Find r* via Brent solver on rStarFinder
  - Sum: `value += amounts[i] * Option_value_at_strike(model.discountBond(maturity, times[i], rStar))`
  - For Payer: bonds are puts; for Receiver: calls (or vice-versa, mirror C++ exactly)

- [ ] **Step 2: Port to Java.**

```java
package org.jquantlib.pricingengines.swaption;

// imports — Brent, OneFactorAffineModel, Swaption, VanillaSwap, Settlement, etc.

public class JamshidianSwaptionEngine extends Swaption.EngineImpl {

    private final OneFactorAffineModel model_;
    private final Handle<YieldTermStructure> termStructure_;

    public JamshidianSwaptionEngine(final OneFactorAffineModel model,
            final Handle<YieldTermStructure> termStructure) {
        this.model_ = model;
        this.termStructure_ = termStructure;
        if (termStructure != null) termStructure.addObserver(this);
        if (model != null) model.addObserver(this);
    }

    public JamshidianSwaptionEngine(final OneFactorAffineModel model) {
        this(model, new Handle<YieldTermStructure>());
    }

    @Override
    public void calculate() {
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        QL.require(args.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with JamshidianSwaptionEngine");
        // ... QL.require exercise.type() == European, swap.spread() == 0, etc.
        // ... port the rest from C++ jamshidianswaptionengine.cpp lines 30-130
    }

    private static class RStarFinder implements Ops.DoubleOp {
        private final double strike_;
        private final double maturity_;
        private final double valueTime_;
        private final double[] times_;
        private final double[] amounts_;
        private final OneFactorAffineModel model_;

        RStarFinder(final OneFactorAffineModel model, final double nominal,
                final double maturity, final double valueTime,
                final double[] fixedPayTimes, final double[] amounts) {
            this.strike_ = nominal;
            this.maturity_ = maturity;
            this.valueTime_ = valueTime;
            this.times_ = fixedPayTimes.clone();
            this.amounts_ = amounts.clone();
            this.model_ = model;
        }

        @Override
        public double op(final double x) {
            double value = strike_;
            final double B = model_.discountBond(maturity_, valueTime_, x);
            for (int i = 0; i < times_.length; ++i) {
                final double dbValue = model_.discountBond(maturity_, times_[i], x) / B;
                value -= amounts_[i] * dbValue;
            }
            return value;
        }
    }
}
```

(Verify Java's `OneFactorAffineModel` interface exposes `discountBond(now, maturity, rate)` — Phase 2b ports added it for HW/Vasicek/CIR.)

- [ ] **Step 3: If Jamshidian's setupArguments chain surfaces VanillaSwap.setupArguments inverted-isAssignableFrom + List capacity-vs-size bug** (Phase 2e WI-3 left it intact), fix it as a SEPARATE `align(instruments)` commit per CLAUDE §4.2:

```java
// in VanillaSwap.setupArguments — invert the isAssignableFrom check;
// allocate List with `new ArrayList<>(Collections.nCopies(size, null))`
// or equivalent so .set(i, ...) works
```

If not surfaced, leave for Phase 2g.

- [ ] **Step 4: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Do NOT commit yet — probe + test land with this.**

### Task B.3: Probe + test for JamshidianSwaptionEngine

- [ ] **Step 1: Write probe.** Same fixture as Phase 2e BlackSwaptionEngine probe (5Y×5Y ATM payer) but with HullWhite + JamshidianSwaptionEngine:

```cpp
auto hw = ext::make_shared<HullWhite>(ts, 0.1, 0.01);
swaption.setPricingEngine(ext::make_shared<JamshidianSwaptionEngine>(hw, ts));
out["jamshidian_swaption_npv"] = swaption.NPV();
```

- [ ] **Step 2: Generate reference + write Java test** at TIGHT tier (1e-12):

```java
final HullWhite hw = new HullWhite(ts, 0.1, 0.01);
swaption.setPricingEngine(new JamshidianSwaptionEngine(hw, ts));
assertEquals(ref.getDouble("jamshidian_swaption_npv"), swaption.NPV(), 1.0e-12);
```

- [ ] **Step 3: Run.**

```bash
mvn -pl jquantlib test -Dtest='JamshidianSwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at tight tier. If FAIL, root-cause: rStarFinder formula, Brent bracket, bond-option weighting.

- [ ] **Step 4: Commit (probe + Jamshidian + test together; align fix separate if surfaced).**

```bash
# First (if surfaced): the align commit
git add jquantlib/src/main/java/org/jquantlib/instruments/VanillaSwap.java
git commit -s -m "align(instruments): VanillaSwap.setupArguments inverted isAssignableFrom + List capacity fix (Phase 2f WI-2)"

# Then the main port commit
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/JamshidianSwaptionEngine.java \
        migration-harness/cpp/probes/pricingengines/swaption/jamshidianswaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/jamshidianswaptionengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/JamshidianSwaptionEngineTest.java
git commit -s -m "stub(pricingengines.swaption): port JamshidianSwaptionEngine + tight-tier fingerprint test (Phase 2f WI-2)"
```

### Task B.4: BlackSwaptionEngine Bachelier branch

Same shape as Task A.7 step 1 but for swaption. Add runtime branch on `volatilityType_` in `BlackSwaptionEngine.calculate()`. If `Normal`, use Bachelier formula; otherwise existing Black76.

Add probe + test:

- Probe: extends `blackswaptionengine.json` with a `bachelier_swaption_npv` field
- Test: `BachelierBlackSwaptionEngineTest` at TIGHT tier

Update `SwaptionHelper.blackPrice` to choose Black or Bachelier engine based on `volatilityType_` (currently always Black).

Commit:
```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/BlackSwaptionEngine.java \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/calibrationhelpers/SwaptionHelper.java \
        migration-harness/cpp/probes/pricingengines/swaption/blackswaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/blackswaptionengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/BachelierBlackSwaptionEngineTest.java
git commit -s -m "stub(pricingengines.swaption): BlackSwaptionEngine Bachelier branch + SwaptionHelper Normal-vol routing (Phase 2f WI-2)"
```

### Task B.5: G2.swaption integral path

**Reference:** C++ `g2.cpp` lines 130-200 (the swaption(...) method using SegmentIntegral + SwaptionPricingFunction).

Three sub-tasks per design P2F-6:

- [ ] **Step 1: Align SegmentIntegral function-object interface.** Java's `SegmentIntegral` may not accept arbitrary `Ops.DoubleOp` cleanly. Read `jquantlib/src/main/java/org/jquantlib/math/integrals/SegmentIntegral.java`. If C++'s pattern (`Real operator()(Real)`) doesn't have a Java equivalent, add a small adapter method that takes a `Ops.DoubleOp` and returns the integral. Bundle into the G2.swaption commit.

- [ ] **Step 2: Port SwaptionPricingFunction as a private inner class of G2.** Mirror the C++ inner class (g2.cpp lines 130-180). Uses Brent solver internally (Java has Brent at `org.jquantlib.math.solvers1D.Brent`).

- [ ] **Step 3: Align Swaption.ArgumentsImpl field access for the integral parameters.** The G2.swaption(...) method receives a `Swaption.arguments` struct in C++. In Java this is `Swaption.ArgumentsImpl`. Verify the fields G2.swaption needs (fixed leg payment dates, exercise time, strike) are accessible — Phase 2e C.0 set up the impl class; Phase 2e WI-3 also added VanillaSwap accessors. Confirm; add accessors if minor gap.

- [ ] **Step 4: Port G2.swaption body.** Replace the Phase 2e A11 stub `throw new UnsupportedOperationException("G2.swaption(...) deferred to Phase 2f")` with the full port.

- [ ] **Step 5: Add probe** — extend `g2.json` with a `swaption_integral` field for one well-posed fixture (e.g. the existing G2(0.1, 0.01, 0.1, 0.005, -0.5) + a 5Y×5Y ATM payer fixture).

- [ ] **Step 6: Extend G2Test** with `testSwaptionIntegralFingerprint` at LOOSE tier (Brent + SegmentIntegral noise floor). Inline justification.

- [ ] **Step 7: Run.**

```bash
mvn -pl jquantlib test -Dtest='G2Test' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: all G2Test methods pass.

- [ ] **Step 8: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/twofactormodels/G2.java \
        jquantlib/src/main/java/org/jquantlib/math/integrals/SegmentIntegral.java \
        jquantlib/src/main/java/org/jquantlib/instruments/Swaption.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/twofactormodels/G2Test.java \
        migration-harness/cpp/probes/model/shortrate/twofactormodels/g2_probe.cpp \
        migration-harness/references/model/shortrate/twofactormodels/g2.json
git commit -s -m "stub(model.shortrate.twofactormodels): G2.swaption integral path port + loose-tier fingerprint (Phase 2f WI-2, closes Phase 2e A11 carve)"
git push origin phase-2f-B-swaption-engines
```

### Task B.6: Land worktree B to main

Same pattern as Task A.8.

---

## Worktree C — WI-3 Heston BroadieKaya + NCCS tightening

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2f-C/`
**Branch:** `phase-2f-C-heston-bk`

### File structure for WI-3

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java` | Tighten to bit-faithfully match C++ |
| Modify (maybe) | `jquantlib/pom.xml` | Add commons-math3 dep IF implementer chose option (a) for Complex |
| Create (maybe) | `jquantlib/src/main/java/org/jquantlib/math/Complex.java` | Minimal Complex class IF implementer chose option (b) |
| Create | `jquantlib/src/main/java/org/jquantlib/math/integrals/GaussLaguerreIntegration.java` | Fixed-order GL quadrature (~150 LOC C++) |
| Create | `jquantlib/src/main/java/org/jquantlib/math/integrals/GaussLobattoIntegral.java` | Adaptive GL-Kronrod quadrature (227 LOC C++) |
| Create | `jquantlib/src/main/java/org/jquantlib/processes/HestonHelpers.java` (or static helpers in HestonProcess) | Fourier-inversion harness (cdf_nu_ds, Phi, ch, cornishFisherEps) |
| Modify | `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java` | Add 3 BroadieKaya enum values + factors() update + 3 evolve branches + un-stub discountBondOption |
| Create | 5 probe files | Lobatto + Laguerre + BroadieKaya + discountBondOption + extend NCCS probe |
| Create | 5 reference JSONs | reference data |
| Create | ~6 new test files / extensions | exact + tight + loose fingerprints |

### Task C.1: Tighten NCCS to bit-faithfully match C++

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java`
- Extend: `migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp` (existing from Phase 2c WI-1)

**Goal:** EXACT-tier match between Java NCCS and C++ NCCS for all (df, ncp, x) tuples.

- [ ] **Step 1: Diagnose the ~1.5e-12 drift via probe-driven comparison.**

```bash
# Read existing probe + Java impl
cat migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp
cat jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java
sed -n '1,200p' migration-harness/cpp/quantlib/ql/math/distributions/chisquaredistribution.{hpp,cpp}
```

- [ ] **Step 2: Extend the probe with a regression suite** — multiple (df, ncp, x) tuples spanning Sankaran threshold, Patnaik series convergence regime, edge cases. Run the probe + capture exact C++ values.

- [ ] **Step 3: Add a Java test that asserts EXACT match** for each tuple. Run — it should fail at some tuple, exposing the drift.

- [ ] **Step 4: Diagnose root cause** per the four categories from design §4:
  - (a) Sankaran/Patnaik switching threshold differs
  - (b) Series convergence criterion differs
  - (c) FMA accumulation pattern (Phase 2d HaltonRsg precedent — `Math.fma(...)` may be needed)
  - (d) Bessel function approximation differs

- [ ] **Step 5: Apply the fix.** Likely 1-30 LOC depending on category.

- [ ] **Step 6: Re-run the EXACT-tier test — should pass.**

- [ ] **Step 7: A13 trigger watch:** if the drift cannot be eliminated to bit-faithful match (e.g. Boost-specific Bessel internals not portable), STOP and report DONE_WITH_CONCERNS describing the drift's structural source. The Phase 2f design §5 A13 trigger covers this — pause to discuss whether to accept `1e-15` or another tier compromise.

- [ ] **Step 8: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/math/distributions/NonCentralCumulativeChiSquaredDistribution.java \
        migration-harness/cpp/probes/math/distributions/noncentral_chi_squared_probe.cpp \
        migration-harness/references/math/distributions/noncentral_chi_squared.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/distributions/NonCentralCumulativeChiSquaredDistributionTest.java
git commit -s -m "align(math.distributions): NCCS bit-faithful match to C++ via <fix-category> (Phase 2f WI-3)"
```

(Replace `<fix-category>` with the actual category that applied. The completion doc records this per design §7.8.)

### Task C.2: Port GaussLaguerreIntegration

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/integrals/GaussLaguerreIntegration.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/math/integrals/gausslaguerrecosinepolynomial.{hpp,cpp}` AND `gausslaguerre.{hpp,cpp}` (~150 LOC combined).

- [ ] **Step 1: Read C++ source.**

```bash
ls migration-harness/cpp/quantlib/ql/math/integrals/ | grep -i laguerre
cat migration-harness/cpp/quantlib/ql/math/integrals/gausslaguerrecosinepolynomial.hpp
# port the actual integration class — likely in gaussianquadratures.{hpp,cpp}
sed -n '1,150p' migration-harness/cpp/quantlib/ql/math/integrals/gaussianquadratures.hpp 2>/dev/null
```

- [ ] **Step 2: Port to Java.** Fixed-order Gauss-Laguerre quadrature using pre-computed nodes/weights for `n=128` (matches C++ default for BroadieKaya). The nodes/weights table is large (~2KB); embed as `static final double[]` arrays.

- [ ] **Step 3: Compile + write a small unit test** with a known-integral function (e.g. `f(x) = e^{-x}` over `[0, ∞)` should integrate to `1.0`).

- [ ] **Step 4: Probe + EXACT-tier test** against C++ reference.

- [ ] **Step 5: Commit.**

```bash
git commit -s -m "infra(math.integrals): port GaussLaguerreIntegration + exact-tier fingerprint test (Phase 2f WI-3)"
```

### Task C.3: Port GaussLobattoIntegral

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/integrals/GaussLobattoIntegral.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/math/integrals/gausslobattointegral.{hpp,cpp}` (227 LOC total).

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/math/integrals/gausslobattointegral.hpp
sed -n '1,160p' migration-harness/cpp/quantlib/ql/math/integrals/gausslobattointegral.cpp
```

- [ ] **Step 2: Port to Java.** Adaptive Gauss-Lobatto-Kronrod quadrature with Richardson extrapolation. Key methods: `calculateAbsTolerance`, `adaptive_kronrod15`, `adaptive_lobatto5`. Implements `Integrator`.

- [ ] **Step 3: Probe + EXACT-tier test** against C++ for several test functions (polynomial, transcendental, oscillating).

- [ ] **Step 4: Commit.**

```bash
git commit -s -m "infra(math.integrals): port GaussLobattoIntegral + exact-tier fingerprint test (Phase 2f WI-3)"
```

### Task C.4: Port Heston Fourier-inversion harness

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/processes/HestonHelpers.java` (or static methods inside HestonProcess)
- Maybe modify: `jquantlib/pom.xml` (commons-math3 dep) OR create `jquantlib/src/main/java/org/jquantlib/math/Complex.java`

**Reference:** C++ hestonprocess.cpp lines 230-350 (`cdf_nu_ds`, `Phi`, `ch`, `cornishFisherEps`).

- [ ] **Step 1: Choose the Complex arithmetic path.** Per design A14:
  - Option (a): Add commons-math3 to `jquantlib/pom.xml`:
    ```xml
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-math3</artifactId>
        <version>3.6.1</version>
    </dependency>
    ```
    Use `org.apache.commons.math3.complex.Complex`.
  - Option (b): Port a minimal Java `Complex` class (~80 LOC) at `jquantlib/src/main/java/org/jquantlib/math/Complex.java`.

  Pick (a) if the team is OK with a new Maven dep; otherwise (b). Document the choice in the commit message.

- [ ] **Step 2: Port the helpers.** Read C++ source first.

```bash
sed -n '230,360p' migration-harness/cpp/quantlib/ql/processes/hestonprocess.cpp
```

The four functions:
- `Phi(process, u, nu_0, nu_t, dt)` — characteristic function (returns Complex)
- `ch(process, x, u, nu_0, nu_t, dt)` — kernel for Fourier inversion (returns Real)
- `cdf_nu_ds(process, x, nu_0, nu_t, dt, discretization)` — switches over BroadieKaya{Lobatto,Laguerre,Trapezoidal} variants, calls the appropriate integrator
- `cornishFisherEps(process, nu_0, nu_t, dt, eps)` — variance moment computation for upper-bound estimation

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Do NOT commit yet — Tasks C.5 (BroadieKaya schemes) + C.6 (tests) land together with this.**

### Task C.5: Add 3 BroadieKaya enum values + factors() + evolve branches

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java`

- [ ] **Step 1: Add enum values** at the end of `HestonProcess.Discretization`:

```java
public enum Discretization {
    PartialTruncation, FullTruncation, Reflection,
    NonCentralChiSquareVariance,
    QuadraticExponential, QuadraticExponentialMartingale,
    BroadieKayaExactSchemeLobatto,
    BroadieKayaExactSchemeLaguerre,
    BroadieKayaExactSchemeTrapezoidal
}
```

- [ ] **Step 2: Update `factors()` to return 3 for BroadieKaya schemes.**

```java
public int factors() {
    switch (discretization_) {
        case BroadieKayaExactSchemeLobatto:
        case BroadieKayaExactSchemeLaguerre:
        case BroadieKayaExactSchemeTrapezoidal:
            return 3;
        default:
            return 2;
    }
}
```

- [ ] **Step 3: Add 3 evolve branches** sharing one body (mirror C++ hestonprocess.cpp lines 517-540):

```java
case BroadieKayaExactSchemeLobatto:
case BroadieKayaExactSchemeLaguerre:
case BroadieKayaExactSchemeTrapezoidal: {
    // Mirror C++ hestonprocess.cpp lines 517-540:
    //   final double nu_0 = x01;
    //   final double nu_t = varianceDistribution(nu_0, dw1, dt);
    //   final double x = Math.min(1.0 - QL_EPSILON,
    //           Math.max(0.0, new CumulativeNormalDistribution().op(dw[2])));
    //   final double vds = new Brent().solve(
    //       xi -> cdf_nu_ds_minus_x(this, xi, nu_0, nu_t, dt, discretization_, x),
    //       1e-5, theta_*dt, 0.1*theta_*dt);
    //   final double vdw = (nu_t - nu_0 - kappa*theta*dt + kappa*vds) / sigma;
    //   final double mu = (r-q)*dt - 0.5*vds + rho_*vdw;
    //   final double sig = Math.sqrt((1-rho*rho)*vds);
    //   final double s = x00 * Math.exp(mu + sig*dw[0]);
    //   retVal[0] = s;
    //   retVal[1] = nu_t;
    break;
}
```

(Verify Java's `Brent.solve(...)` API — Phase 2c era code shows the call shape. The lambda might need an explicit `Ops.DoubleOp` wrapper instead of method reference.)

- [ ] **Step 4: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Do NOT commit yet — tests land together.**

### Task C.6: BroadieKaya tests + probe + commit

- [ ] **Step 1: Write probe** capturing `HestonProcess.evolve(...)` for each of the 3 BroadieKaya schemes for ~3 fixtures each (loose-tier, so a few well-chosen fixtures suffice).

- [ ] **Step 2: Generate reference JSON.**

- [ ] **Step 3: Extend `HestonProcessTest`** with 3 test methods (one per scheme):

```java
@Test public void testBroadieKayaLobattoEvolve() { /* loose tier 1e-8, inline justification */ }
@Test public void testBroadieKayaLaguerreEvolve() { /* same */ }
@Test public void testBroadieKayaTrapezoidalEvolve() { /* same */ }
```

- [ ] **Step 4: Run.**

```bash
mvn -pl jquantlib test -Dtest='HestonProcessTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: all HestonProcessTest methods pass at the documented tiers.

- [ ] **Step 5: Commit (Fourier-inversion + 3 BroadieKaya schemes + probe + tests).**

```bash
git add jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java \
        jquantlib/src/main/java/org/jquantlib/processes/HestonHelpers.java \
        jquantlib/src/main/java/org/jquantlib/math/Complex.java \  # if option (b)
        jquantlib/pom.xml \                                          # if option (a)
        migration-harness/cpp/probes/processes/heston_broadiekaya_probe.cpp \
        migration-harness/references/processes/heston_broadiekaya.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java
git commit -s -m "stub(processes): port Heston Fourier-inversion + 3 BroadieKaya schemes + loose-tier fingerprints (Phase 2f WI-3)"
```

### Task C.7: Un-stub HestonProcess.discountBondOption at TIGHT tier

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java`
- Probe: `migration-harness/cpp/probes/processes/heston_discountbondoption_probe.cpp`
- Test: extend `HestonProcessTest` with `testDiscountBondOptionMatchesCpp`

**Depends on Task C.1 (NCCS tightening).** If C.1 succeeded EXACT, this should reach TIGHT.

- [ ] **Step 1: Find current state of `HestonProcess.discountBondOption`.** Phase 2c WI-1 left it stubbed (since the chi-squared drift made the test unreliable).

```bash
grep -n "discountBondOption\|UnsupportedOperationException" jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java | head -10
```

- [ ] **Step 2: Port the body** mirroring C++ hestonprocess.cpp `HestonProcess::discountBondOption` (likely uses NCCS distribution + analytic formula).

- [ ] **Step 3: Write probe + test at TIGHT tier (1e-12).**

- [ ] **Step 4: If test fails at tight,** the NCCS tightening from C.1 didn't fully eliminate the propagation. Re-investigate or accept LOOSE with inline justification (this is acceptable per design §4 — not a hard exit-criterion blocker).

- [ ] **Step 5: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java \
        migration-harness/cpp/probes/processes/heston_discountbondoption_probe.cpp \
        migration-harness/references/processes/heston_discountbondoption.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java
git commit -s -m "stub(processes): un-stub HestonProcess.discountBondOption at tight tier (Phase 2f WI-3)"
```

### Task C.8: Promote NCCV tests from loose to tight tier

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java`

The Phase 2d WI-2 NCCV tests sit at `1.0e-8` loose tier with inline justification (inverse-CDF Brent solver convergence noise floor). With Task C.1's NCCS tightening, the noise floor MAY drop to tight tier.

- [ ] **Step 1: Find the NCCV test methods.**

```bash
grep -n "nccv_\|NCCV" jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java
```

- [ ] **Step 2: Try tightening to 1e-12.** If the tests still pass, great — promote them and update the inline justification to reflect the post-NCCS-tightening floor.

- [ ] **Step 3: If tightening fails,** roll back to loose tier and document in the completion doc that NCCS tightening alone wasn't sufficient (Brent solver still introduces noise).

- [ ] **Step 4: Commit (only if tier promotion succeeded).**

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java
git commit -s -m "test(processes): promote NCCV evolve fingerprints from loose to tight tier (Phase 2f WI-3, post-NCCS-tightening)"
git push origin phase-2f-C-heston-bk
```

### Task C.9: Land worktree C to main

Same pattern as Task A.8 / B.6. Note that C is the long pole — when it lands, all Phase 2f WIs are done.

---

## Layer 2 — Completion doc + tag

### Task L2.1: Write `phase2f-completion.md`

- [ ] **Step 1: Gather final state.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
git log --oneline a533fbd..HEAD
```

Expected: `Tests run: ~672`; `0 stubs`; ~15-20 commits since Phase 2e tip.

- [ ] **Step 2: Write the completion doc** following Phase 2e's structure:
  - Header (date, predecessor tag, what's in)
  - Per-WI summary with commit hashes
  - **NCCS tightening disclosure** (per design §7.8): which fix category (a/b/c/d) was the root cause; whether NCCV tier-promotion succeeded
  - Final scanner state (still 0)
  - Test suite final state with delta table
  - Deviations from plan (any A13/A14 firings, any tier compromises, any aligned-but-not-bundled fixes)
  - Phase 2g seed list

- [ ] **Step 3: Commit.**

```bash
git add docs/migration/phase2f-completion.md
git commit -s -m "docs(migration): Phase 2f completion report"
git push origin main
```

### Task L2.2: Tag and push

```bash
git tag jquantlib-phase2f-complete
git push origin jquantlib-phase2f-complete
git tag -l 'jquantlib-phase2*'
```

Expected: 6 tags (`phase1`, `phase2a` through `phase2f`).

### Task L2.3: Worktree cleanup

```bash
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2f-A 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2f-B 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2f-C 2>&1
git worktree prune
git worktree list

git branch -D phase-2f-A-cap-engines phase-2f-B-swaption-engines phase-2f-C-heston-bk 2>&1 || true
git push origin --delete phase-2f-A-cap-engines phase-2f-B-swaption-engines phase-2f-C-heston-bk 2>&1
```

If any `remove --force` fails with "Directory not empty" (Phase 2c/2d/2e precedent), fall back to `rm -rf` then `git worktree prune`.

### Task L2.4: Update memory

Update `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md` description and body with Phase 2f milestone + which NCCS fix category was needed.

Also update `MEMORY.md` index entry.

### Task L2.5: Final verification

```bash
git status
git log --oneline -10
git tag -l 'jquantlib-phase2*'
git worktree list
git branch -a | grep '2f' || echo "no 2f branches"
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
```

Expected: clean state, `phase2f-complete` tag exists, no 2f branches, tests `Failures: 0, Errors: 0, Skipped: 22`, scanner `0 stubs`.

---

## Self-Review notes

- All 10 design exit criteria mapped to tasks: §7.1 (mvn green) → final verification L2.5; §7.2 (test delta) → A.4 + A.6 + A.7 + B.3 + B.4 + B.5 + C.2 + C.3 + C.6 + C.7 + C.8; §7.3 (Skipped: 22) → final verification; §7.4 (scanner WIP=0) → final verification; §7.5 (worktrees gone) → L2.3; §7.6 (probes regenerate) → all probe tasks use `generate-references.sh`; §7.7 (loose-tier inline justification) → enforced per task; §7.8 (NCCS disclosure) → Task C.1 commit message records category; §7.9 (completion doc) → L2.1; §7.10 (tag pushed + memory updated) → L2.2 + L2.4.
- All 6 design pause triggers covered: A4 → C.2/C.3/C.4 (Lobatto/Laguerre/Fourier are in scope, planned); A6 disabled; A9 → A.8/B.6/C.9 step rebase paths; A13 → C.1 step 7 (NCCS structural drift escalation); A14 → C.4 step 1 (Complex arithmetic path choice); A1/A2/A3/A7 inherit silently from prior phases.
- The plan does not invent classes/methods that don't exist. Where Java-side API shape isn't pinned by the existing code base, the plan calls out "verify against actual" with the search command.
- All commit messages follow the `<kind>(<pkg>): <verb>` convention with `(Phase 2f WI-N)` suffix.
- VanillaSwap.setupArguments fix (P2F-7) is conditional in B.3 step 3 — only landed if Jamshidian's setupArguments chain surfaces it.
- C.7 / C.8 are conditional success items — if NCCS tightening doesn't fully propagate, both fall back gracefully (loose tier with inline justification or rollback).

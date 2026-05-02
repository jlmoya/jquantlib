# Phase 2j Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. WI-1 sub-layers in worktree A serialize; WI-2/WI-3 dispatch only AFTER WI-1.4 lands; WI-4 (MarkovFunctional) dispatches in parallel after WI-1.1 lands.

**Goal:** Port the full Gaussian1D family from QuantLib v1.42.1 — abstract `Gaussian1dModel` base + `Gsr` + `MarkovFunctional` concrete models + `GsrProcess`/`GsrProcessCore` + `Gaussian1dSmileSection` + `Gaussian1dSwaptionVolatility` + 5 swaption engines + 1 cap-floor engine. ~6119 LOC C++ → ~7000-9000 Java, 11 file pairs across 5 new subpackages. End state: scanner WIP unchanged at 0; tests `688 → ~698` (target) / ceiling `~715`; tag `jquantlib-phase2j-complete`.

**Architecture:** Same as Phase 2h-2i.6 — direct commits to `main`, TDD per artifact, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes (~11 new probes), tier-stratified tolerances. **Use `JQuantMath.{exp,log,sin,cos}` from day one** in all new code (per design P2J-5); leave the 1 `Math.pow` site at `GsrProcessCore` per Phase 2j-pre B3 decision (A19 escape valve if it floors a tier). 4 git worktrees per `phase2j-design.md` §3 — A=model layer (5 sub-commits sequential), B=standard engines, C=niche engines, D=MarkovFunctional. **Critical-path dependency:** L0 → WI-1.1 → WI-1.2 → WI-1.3 → WI-1.4 → max(WI-2, WI-3) → L4. WI-4 runs parallel from after WI-1.1. Pause triggers per design §5: A2 EXACT unreachable, A3 reference itself wrong, A4 disabled for the 5 planned new subpackages, A6 disabled, A8/A9/A15/A16/A17/A18 carry-forward, A13 re-armed for `Math.pow`, A19 expected for GsrProcessCore Math.pow path, NEW A20 (MarkovFunctional calibration non-determinism), NEW A21 (wall-time projection > 3 sessions; scope-trim per P2J-10).

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution. **Note:** Java package layout is `org.jquantlib.model.*` (singular) and `org.jquantlib.processes.*` (plural) — do not mirror C++'s `ql/models/` blindly.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 4 worktrees, init progress doc | (main) | 1 |
| L1 | WI-1 model layer (5 sub-commits, sequential in worktree A) | A | 4 |
| L2 | WI-2 standard engines (parallel after WI-1.4); WI-3 niche engines (parallel) | B, C | 2 + 3 |
| L3 | WI-4 MarkovFunctional (parallel after WI-1.1) | D | 1 (or 2) |
| L4 | Completion doc + tag | (main) | 1 commit + 1 tag |

**Non-goals reminder (design §1):** lgamma, pow port, BroadieKaya retry, NCCS EXACT, Douglas ADI, other Fdm-dependent engines, U128.java extraction — all deferred.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit's full suite passes. Commit messages follow `<kind>(<pkg>): <verb> ...` with `(Phase 2j WI-N[.M])` suffix.

**5 new Java subpackages:**
- `org.jquantlib.model.shortrate.onefactormodels.gaussian1d` (model classes)
- `org.jquantlib.processes.gsr` (process classes)
- `org.jquantlib.pricingengines.swaption.gaussian1d` (4 swaption engines)
- `org.jquantlib.pricingengines.capfloor.gaussian1d` (cap-floor engine)
- `org.jquantlib.termstructures.volatility.gaussian1d` (smile section + swaption volatility structure)

**Probe directories:** all under `migration-harness/cpp/probes/` mirroring the production package layout.

---

## Layer 0 — Pre-flight + worktree setup

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean.

- [ ] **Step 2:** Run baseline test suite.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected final summary: `Tests run: 688, Failures: 0, Errors: 0, Skipped: 22`.

- [ ] **Step 3:** Snapshot scanner state.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `0 stubs`.

- [ ] **Step 4:** Verify the harness submodule pin.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib/migration-harness/cpp/quantlib && git rev-parse HEAD
```

Expected: `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

- [ ] **Step 5:** Capture predecessor tag + design commit.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git rev-parse main
git tag -l 'jquantlib-phase2i.6-complete'
git log --oneline -1 docs/migration/phase2j-design.md
```

Expected: predecessor tag `jquantlib-phase2i.6-complete` exists @ `44be66c`; design at `368dbda` or later.

### Task 0.2: Create 4 git worktrees

- [ ] **Step 1:** Create branches and worktrees off main tip.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2j-A-gaussian1d-model ../jquantlib-2j-A main
git worktree add -b phase-2j-B-standard-engines ../jquantlib-2j-B main
git worktree add -b phase-2j-C-niche-swaption-engines ../jquantlib-2j-C main
git worktree add -b phase-2j-D-markov-functional ../jquantlib-2j-D main
git worktree list
```

Expected: 5 worktrees (main + 4 new).

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A/jquantlib && mvn test-compile -q 2>&1 | tail -3
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-B/jquantlib && mvn test-compile -q 2>&1 | tail -3
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-C/jquantlib && mvn test-compile -q 2>&1 | tail -3
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-D/jquantlib && mvn test-compile -q 2>&1 | tail -3
```

Expected: each prints no error output.

- [ ] **Step 3:** Verify submodules are init'd in each worktree.

```bash
for wt in A B C D; do
  cd /Users/josemoya/eclipse-workspace/jquantlib-2j-$wt
  git submodule status migration-harness/cpp/quantlib | awk '{print $1}'
done
```

Expected: each prints `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` (no `-` prefix).

If any prints `-` prefix:

```bash
for wt in A B C D; do
  cd /Users/josemoya/eclipse-workspace/jquantlib-2j-$wt
  git submodule update --init --recursive
done
```

### Task 0.3: Initialize Phase 2j progress doc

**Files:**
- Create: `docs/migration/phase2j-progress.md`

- [ ] **Step 1:** Write the initial progress doc.

```markdown
# Phase 2j Progress Log

Living document — updated by the controller after every implementer subagent run.

**Plan:** `docs/migration/phase2j-plan.md` (commit TBD-after-plan-lands)
**Design:** `docs/migration/phase2j-design.md` (commit `368dbda`)
**Predecessor:** `jquantlib-phase2i.6-complete` @ `44be66c`
**Phase 2j start tip on main:** `<fill at L0 land>`
**Baseline:** Tests `688/0/0/22`, scanner `0 stubs`

## Worktrees

| WT | Path | Branch | Scope |
|----|------|--------|-------|
| A | `/Users/josemoya/eclipse-workspace/jquantlib-2j-A` | `phase-2j-A-gaussian1d-model` | WI-1 model layer (5 sub-commits, sequential) |
| B | `/Users/josemoya/eclipse-workspace/jquantlib-2j-B` | `phase-2j-B-standard-engines` | WI-2 standard engines — dispatches AFTER WI-1.4 |
| C | `/Users/josemoya/eclipse-workspace/jquantlib-2j-C` | `phase-2j-C-niche-swaption-engines` | WI-3 niche engines — dispatches AFTER WI-1.4 |
| D | `/Users/josemoya/eclipse-workspace/jquantlib-2j-D` | `phase-2j-D-markov-functional` | WI-4 MarkovFunctional — dispatches AFTER WI-1.1 |

## Pause-trigger status

- A2 (tolerance looser than 1e-8): not fired
- A3 (cross-validation reveals reference wrong): not fired
- A4 (unplanned new packages outside the 5 planned): not fired
- A6 disabled per memory `feedback_phase2a_no_a6.md`
- A8 (test suite red unrelated): not fired
- A9 worktree-merge-conflict: not fired
- A13 re-armed for Math.pow: not fired
- A15 (previously-hidden bug surface): not fired
- A16 (missing dependency outside planned scope): not fired
- A17 (>2 unplanned align commits during port): not fired
- A18 (NaN payload divergence): not fired
- A19 (Math.pow at GsrProcessCore floors a tier): not fired
- A20 NEW (MarkovFunctional calibration non-determinism): not fired
- A21 NEW (wall-time projection > 3 sessions): not fired

## Layer / WI progress

### L0 — pre-flight + worktree setup
_(Pending)_

### L1 — WI-1 model layer (5 sub-commits, sequential, worktree A)

#### Sub-layer 1.1 — Gaussian1dModel base
_(Pending — first implementer dispatched after L0)_

#### Sub-layer 1.2 — GsrProcessCore + GsrProcess
_(Pending — dispatch after 1.1 lands)_

#### Sub-layer 1.3 — Gsr concrete model
_(Pending — dispatch after 1.2 lands)_

#### Sub-layer 1.4 — Gaussian1dSmileSection + Gaussian1dSwaptionVolatility
_(Pending — dispatch after 1.3 lands)_

### L2 — WI-2 standard engines + WI-3 niche engines (parallel after WI-1.4 lands)

#### WI-2 (worktree B): SwaptionEngine + CapFloorEngine
_(Pending — dispatches after WI-1.4 lands)_

#### WI-3 (worktree C): Jamshidian + Nonstandard + FloatFloat
_(Pending — dispatches after WI-1.4 lands; parallel with WI-2)_

### L3 — WI-4 MarkovFunctional (parallel after WI-1.1 lands)
_(Pending — dispatches after WI-1.1 lands; runs concurrent with WI-1.2/1.3/1.4 + WI-2/WI-3)_

### L4 — completion doc + tag
_(Not yet started)_

## Test count tracking

| Event | Tests | Failures | Errors | Skipped | Notes |
|-------|-------|----------|--------|---------|-------|
| Phase 2j start (`<fill>`) | 688 | 0 | 0 | 22 | baseline |
```

- [ ] **Step 2:** Commit the progress doc on main.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git add docs/migration/phase2j-progress.md
git commit -s -m "docs(migration): init phase2j-progress log"
git push origin main
```

Update the start-tip line in the progress doc with the resulting commit SHA after push.

---

## Layer 1 — WI-1 model layer (worktree A, 4 sub-commits sequential)

**Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2j-A`
**Branch:** `phase-2j-A-gaussian1d-model`
**Subagent class:** opus for sub-layers 1.1 + 1.2 (architectural), sonnet for 1.3 + 1.4 (mostly mechanical given established pattern). Controller dispatches one fresh implementer per sub-layer; each commits and lands fast-forward to main before the next dispatches.

**Important constraint for ALL Phase 2j code:** use `JQuantMath.{exp,log,sin,cos}` from day one (per design P2J-5). Add `import org.jquantlib.math.transcendental.JQuantMath;` and call `JQuantMath.exp(x)` not `Math.exp(x)`. The 1 exception is `GsrProcessCore`'s single `Math.pow` site, which stays as `Math.pow` (per Phase 2j-pre B3 decision).

### Task 1.1: Port `Gaussian1dModel` abstract base

**C++ source files:**
- `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/gaussian1dmodel.hpp` (~262 LOC)
- `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/gaussian1dmodel.cpp` (~285 LOC)

**Java target files (create):**
- `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/Gaussian1dModel.java` (estimate ~700-900 LOC)
- `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/package-info.java` (with package Javadoc citing C++ source)

**Probe (create):**
- `migration-harness/cpp/probes/models/shortrate/onefactormodels/gaussian1d_model_probe.cpp` (emit forward-measure conversion, time-grid integration, swap-rate fingerprints)
- `migration-harness/references/models/shortrate/onefactormodels/gaussian1d_model.json` (~100 cases generated)

**Java test (create):**
- `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/Gaussian1dModelTest.java` (probe-driven)

#### Steps

- [ ] **Step 1: Rebase worktree A on main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A
git fetch origin
git rebase origin/main
git log --oneline -3
```

Expected: clean rebase. Tip should be at the L0 progress-doc commit.

- [ ] **Step 2: Read C++ source**

```bash
cat /Users/josemoya/eclipse-workspace/jquantlib-2j-A/migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/gaussian1dmodel.hpp
cat /Users/josemoya/eclipse-workspace/jquantlib-2j-A/migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/gaussian1dmodel.cpp
```

Familiarize: identify the abstract methods (`numeraire`, `zerobond`, `forwardRate`, `swapRate`, `swapAnnuity`, `forwardMeasureExpectation`, etc.), the protected helper methods, and what dependencies the class has on existing JQuantLib infrastructure (e.g. `TermStructureConsistentModel`, `LazyObject`, `Observable`, `Handle<YieldTermStructure>`).

- [ ] **Step 3: Inventory existing Java dependencies**

```bash
find /Users/josemoya/eclipse-workspace/jquantlib-2j-A/jquantlib/src/main/java/org/jquantlib/model/shortrate -name "*.java" | head -10
grep -l "TermStructureConsistentModel\|extends.*Model\|implements.*Observable" /Users/josemoya/eclipse-workspace/jquantlib-2j-A/jquantlib/src/main/java/org/jquantlib/model/shortrate/*.java 2>/dev/null
```

Confirm `TermStructureConsistentModel` exists in Java (or its equivalent base). If a missing dependency surfaces, A16 fires — document and decide scope-add vs phase-defer.

- [ ] **Step 4: Write the probe `gaussian1d_model_probe.cpp`**

Create `migration-harness/cpp/probes/models/shortrate/onefactormodels/gaussian1d_model_probe.cpp`:

```cpp
// migration-harness/cpp/probes/models/shortrate/onefactormodels/gaussian1d_model_probe.cpp
// Phase 2j WI-1.1 — emit Gaussian1dModel base behaviors:
//   - forward-measure conversion E^T[f(x_t)] / numeraire identities
//   - time-discretization on a fixed mesh
//   - standard swap rate at fixed (t, x, expiry, tenor)
// Oracle: Gsr instantiated with a simple flat curve + stepwise vol — exercises
// the BASE class methods that all concrete Gaussian1dModel subclasses share.

#include <ql/version.hpp>
#include "../../common.hpp"

#include <ql/models/shortrate/onefactormodels/gsr.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/target.hpp>
#include <ql/quotes/simplequote.hpp>
#include <ql/indexes/swap/euriborswap.hpp>

#include <vector>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("models/shortrate/onefactormodels/gaussian1d_model", QL_VERSION, "gaussian1d_model_probe");

    // Build a Gsr instance with deterministic inputs.
    const Date today(15, May, 2026);
    Settings::instance().evaluationDate() = today;
    Handle<YieldTermStructure> yts(ext::make_shared<FlatForward>(today, 0.03, Actual360()));

    std::vector<Date> volStepDates;
    volStepDates.push_back(today + Period(1, Years));
    volStepDates.push_back(today + Period(2, Years));
    std::vector<Real> volatilities = {0.01, 0.012, 0.015};
    Real reversion = 0.01;

    ext::shared_ptr<Gsr> gsr = ext::make_shared<Gsr>(yts, volStepDates, volatilities, reversion);

    // Forward-measure conversion fingerprints
    int idx = 0;
    for (Real t : {0.5, 1.0, 1.5, 2.0, 3.0}) {
        for (Real x : {-0.02, -0.01, 0.0, 0.01, 0.02, 0.03}) {
            for (Real T : {t + 0.5, t + 1.0, t + 2.0, t + 5.0}) {
                const Real n = gsr->numeraire(t, x);
                const Real z = gsr->zerobond(T, t, x);
                char nm[48]; std::snprintf(nm, sizeof nm, "fm_t%.1f_x%.2f_T%.1f", t, x, T);
                out.addCase(nm,
                    json{{"t", t}, {"x", x}, {"T", T}},
                    json{{"numeraire", n}, {"zerobond", z}});
                idx++;
            }
        }
    }

    // Standard swap rate at (t, x, expiry, tenor)
    ext::shared_ptr<SwapIndex> idx_helper = ext::make_shared<EuriborSwapIsdaFixA>(2 * Years, yts);
    for (Real t : {0.0, 0.5, 1.0, 2.0}) {
        for (Real x : {-0.01, 0.0, 0.01}) {
            for (Date expiry : {today + Period(1, Years), today + Period(3, Years), today + Period(5, Years)}) {
                Date fixingDate = expiry;
                Real sr = gsr->swapRate(fixingDate, idx_helper, x);
                char nm[48]; std::snprintf(nm, sizeof nm, "sr_t%.1f_x%.2f_e%d", t, x, fixingDate.serialNumber());
                out.addCase(nm,
                    json{{"t", t}, {"x", x}, {"expiry", fixingDate.serialNumber()}, {"tenor", "2Y"}},
                    json{{"swap_rate", sr}});
            }
        }
    }

    // Time-discretization mesh
    const Real T_max = 5.0;
    const Size n_steps = 20;
    for (Size i = 0; i <= n_steps; ++i) {
        const Real t_i = (T_max * i) / n_steps;
        const Real n_i = gsr->numeraire(t_i, 0.0);
        char nm[32]; std::snprintf(nm, sizeof nm, "mesh_%02zu", i);
        out.addCase(nm,
            json{{"t", t_i}, {"x", 0.0}},
            json{{"numeraire", n_i}});
    }

    out.write();
    return 0;
}
```

This probe reaches into `Gsr` (a concrete model) to exercise the base class behaviors — that's intentional: the base class `Gaussian1dModel` is abstract and can't be instantiated directly. WI-1.3's Gsr probe will overlap some inputs to ensure consistent behavior.

- [ ] **Step 5: Build probe and generate references**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A
bash migration-harness/setup.sh 2>&1 | tail -5
bash migration-harness/generate-references.sh 2>&1 | tail -10
ls -la migration-harness/references/models/shortrate/onefactormodels/gaussian1d_model.json
python3 -c "import json; d=json.load(open('migration-harness/references/models/shortrate/onefactormodels/gaussian1d_model.json')); print('cases:', len(d['cases']))"
```

Expected: ~100 cases. If the probe build fails because `Gsr` isn't yet a defined symbol — that's fine: the probe still compiles against the C++ submodule's Gsr (we're not depending on Java Gsr existing). The Java port of Gsr lands in WI-1.3.

- [ ] **Step 6: Implement `Gaussian1dModel.java`**

Create `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/Gaussian1dModel.java`. The implementer subagent transcribes from `gaussian1dmodel.{hpp,cpp}`:

- Class signature: extends existing JQuantLib `TermStructureConsistentModel` (or equivalent base), implements `LazyObject`, observes the YieldTermStructure handle.
- Public abstract methods (in order from .hpp):
  - `numeraire(Time t, Real x, Handle<YieldTermStructure> yts) → Real`
  - `zerobond(Time T, Time t, Real x, Handle<YieldTermStructure> yts) → Real`
  - `numeraireDeflatedZerobond(Time T, Time t, Real x, Handle<YieldTermStructure> yts) → Real`
  - `forwardRate(Date fixingDate, Date referenceDate, Real x, ext::shared_ptr<IborIndex>) → Real`
  - `swapRate(Date fixingDate, Period swapTenor, Real x, ext::shared_ptr<SwapIndex>) → Real`
  - `swapAnnuity(...) → Real`
  - and several more — port ALL public methods listed in the .hpp
- Protected helpers:
  - `addAdditionalTimesTo(...)`, `xGrid(...)`, `gaussianPolynomialIntegral(...)`, etc.
- `forwardMeasureExpectation(...)` — the core forward-measure conversion machinery.

For `Math.*` calls in the C++ source: use `JQuantMath.*` for exp/log/sin/cos. Math.sqrt, Math.abs, Math.max, Math.min stay as Math.*. Math.PI stays.

Add comprehensive class-level Javadoc citing the C++ source pin SHA, design commit, and Phase 2j context.

- [ ] **Step 7: Create package-info.java**

```java
// jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/package-info.java
/**
 * Gaussian1D family of one-factor short-rate models (Hull-White generalization
 * with arbitrary volatility term structures and smile-aware swaption pricing).
 *
 * <p>Ported from QuantLib v1.42.1 {@code ql/models/shortrate/onefactormodels/}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) per Phase 2j of
 * the JQuantLib migration. See {@code docs/migration/phase2j-design.md}.
 *
 * <p>Classes:
 * <ul>
 *   <li>{@link Gaussian1dModel} — abstract base
 *   <li>{@link Gsr} — Gaussian Short Rate concrete model (Phase 2j WI-1.3)
 *   <li>{@link MarkovFunctional} — calibration-driven concrete model (Phase 2j WI-4)
 * </ul>
 */
package org.jquantlib.model.shortrate.onefactormodels.gaussian1d;
```

- [ ] **Step 8: Write the Java test**

Create `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/Gaussian1dModelTest.java` — probe-driven. Loads `models/shortrate/onefactormodels/gaussian1d_model` reference and asserts each fingerprint at TIGHT tier (numeraire/zerobond/swapRate fingerprints).

The test requires constructing a Java `Gsr` instance — but Gsr doesn't exist yet (lands in WI-1.3). **For WI-1.1 only:** mark the test class with `@Ignore` and a comment "// Re-enabled in WI-1.3 once Gsr lands." Then it compiles but doesn't run. The probe + reference still land in this commit so WI-1.3 doesn't have to backfill them.

Actually, simpler: defer the Java test entirely to WI-1.3. WI-1.1's commit just lands `Gaussian1dModel` + probe + reference. WI-1.3 lands `Gsr` AND the test that exercises both.

- [ ] **Step 9: Run mvn test-compile to ensure Gaussian1dModel.java compiles**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A/jquantlib
mvn test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS. If compile errors surface (likely: missing Java equivalents of QuantLib types), they indicate either:
- A simple type-equivalence renaming (e.g. `Date` → `org.jquantlib.time.Date`)
- An A16 missing-dependency event (some referenced type doesn't exist in Java yet)

If A16: pause and report.

- [ ] **Step 10: Run full suite (no test added yet)**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
```

Expected: `Tests run: 688, Failures: 0, Errors: 0, Skipped: 22` (no new test methods yet).

- [ ] **Step 11: Scanner check + discard timestamp regens**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
git status --short | head -20
git checkout -- migration-harness/references/_smoke_test.json \
                migration-harness/references/currencies/ \
                migration-harness/references/math/ \
                migration-harness/references/model/ \
                migration-harness/references/patterns/ \
                migration-harness/references/pricingengines/ \
                migration-harness/references/processes/ \
                migration-harness/references/termstructures/ 2>&1 | head -3 || true
```

Expected staged: only `gaussian1d_model_probe.cpp`, `gaussian1d_model.json`, `Gaussian1dModel.java`, `package-info.java`.

- [ ] **Step 12: Commit and push**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A
git add migration-harness/cpp/probes/models/shortrate/onefactormodels/gaussian1d_model_probe.cpp \
        migration-harness/references/models/shortrate/onefactormodels/gaussian1d_model.json \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/Gaussian1dModel.java \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/package-info.java
git commit -s -m "infra(model.shortrate.onefactormodels.gaussian1d): port Gaussian1dModel abstract base (Phase 2j WI-1.1)"
git push origin phase-2j-A-gaussian1d-model
```

- [ ] **Step 13: Fast-forward to main**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git merge --ff-only origin/phase-2j-A-gaussian1d-model
git push origin main
```

- [ ] **Step 14: Update progress doc — mark sub-layer 1.1 ✅** with commit SHA. After this lands, the controller can dispatch BOTH WI-1.2 (worktree A continues) AND WI-4 (worktree D, MarkovFunctional) in parallel.

---

### Task 1.2: Port `GsrProcessCore` + `GsrProcess`

**C++ source files:**
- `migration-harness/cpp/quantlib/ql/processes/gsrprocesscore.{hpp,cpp}` (~493 LOC total)
- `migration-harness/cpp/quantlib/ql/processes/gsrprocess.{hpp,cpp}` (~199 LOC total)

**Java target files (create):**
- `jquantlib/src/main/java/org/jquantlib/processes/gsr/GsrProcessCore.java` (~600-700 LOC)
- `jquantlib/src/main/java/org/jquantlib/processes/gsr/GsrProcess.java` (~250-300 LOC)
- `jquantlib/src/main/java/org/jquantlib/processes/gsr/package-info.java`

**Probe (create):**
- `migration-harness/cpp/probes/processes/gsr_process_probe.cpp`
- `migration-harness/references/processes/gsr_process.json`

**Java test (create):**
- `jquantlib/src/test/java/org/jquantlib/testsuite/processes/gsr/GsrProcessTest.java`

#### Steps

- [ ] **Step 1: Rebase worktree A on main** (worktree A's tip is the WI-1.1 commit; main has just been ff-updated, so already in sync — but `git fetch && git status` to verify).

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A
git fetch origin
git status
git log --oneline -5
```

Expected: clean tree, branch is at the WI-1.1 commit, identical to origin.

- [ ] **Step 2: Read C++ source**

```bash
cat migration-harness/cpp/quantlib/ql/processes/gsrprocesscore.hpp
cat migration-harness/cpp/quantlib/ql/processes/gsrprocesscore.cpp
cat migration-harness/cpp/quantlib/ql/processes/gsrprocess.hpp
cat migration-harness/cpp/quantlib/ql/processes/gsrprocess.cpp
```

Note: `GsrProcessCore` has 1 `std::pow` call (the only Math.pow site in the entire Gaussian1D family per design P2J-5). Leave as `Math.pow` in Java; document inline that it's a Phase 2j followup candidate if A19 fires.

- [ ] **Step 3: Write the probe `gsr_process_probe.cpp`**

Create `migration-harness/cpp/probes/processes/gsr_process_probe.cpp`. Fingerprints per design §4 probe spec: drift μ(t,x), diffusion σ(t,x), expectation E[X(T)|X(t)], variance Var[X(T)|X(t)] across (t, x, T) grids.

```cpp
#include <ql/version.hpp>
#include "../common.hpp"

#include <ql/processes/gsrprocess.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/quotes/simplequote.hpp>

using namespace jqml_harness;
using namespace QuantLib;

int main() {
    ReferenceWriter out("processes/gsr_process", QL_VERSION, "gsr_process_probe");

    const Date today(15, May, 2026);
    Settings::instance().evaluationDate() = today;
    Handle<YieldTermStructure> yts(ext::make_shared<FlatForward>(today, 0.03, Actual360()));

    std::vector<Real> volStepTimes = {1.0, 2.0};
    std::vector<Real> vols = {0.01, 0.012, 0.015};
    Real reversion = 0.01;
    Real T_horizon = 10.0;

    ext::shared_ptr<GsrProcess> proc = ext::make_shared<GsrProcess>(volStepTimes, vols, std::vector<Real>(1, reversion), T_horizon, today, yts);

    // Drift μ(t, x) on grid
    int idx = 0;
    for (Real t : {0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0}) {
        for (Real x : {-0.02, -0.01, 0.0, 0.01, 0.02}) {
            const Real mu = proc->drift(t, x);
            char nm[32]; std::snprintf(nm, sizeof nm, "drift_%02d", idx++);
            out.addCase(nm, json{{"t", t}, {"x", x}}, json{{"value", mu}});
        }
    }

    // Diffusion σ(t, x)
    idx = 0;
    for (Real t : {0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 5.0}) {
        for (Real x : {-0.02, -0.01, 0.0, 0.01, 0.02}) {
            const Real sig = proc->diffusion(t, x);
            char nm[32]; std::snprintf(nm, sizeof nm, "diff_%02d", idx++);
            out.addCase(nm, json{{"t", t}, {"x", x}}, json{{"value", sig}});
        }
    }

    // E[X(T)|X(t)]
    idx = 0;
    for (Real t : {0.0, 1.0, 2.0}) {
        for (Real T : {t + 0.5, t + 1.0, t + 2.0, t + 5.0}) {
            for (Real x : {-0.01, 0.0, 0.01}) {
                const Real e = proc->expectation(t, x, T - t);
                char nm[32]; std::snprintf(nm, sizeof nm, "exp_%02d", idx++);
                out.addCase(nm, json{{"t", t}, {"T", T}, {"x", x}}, json{{"value", e}});
            }
        }
    }

    // Var[X(T)|X(t)]
    idx = 0;
    for (Real t : {0.0, 1.0, 2.0}) {
        for (Real T : {t + 0.5, t + 1.0, t + 2.0, t + 5.0}) {
            for (Real x : {-0.01, 0.0, 0.01}) {
                const Real v = proc->variance(t, x, T - t);
                char nm[32]; std::snprintf(nm, sizeof nm, "var_%02d", idx++);
                out.addCase(nm, json{{"t", t}, {"T", T}, {"x", x}}, json{{"value", v}});
            }
        }
    }

    out.write();
    return 0;
}
```

- [ ] **Step 4: Build probe and generate references**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A
bash migration-harness/generate-references.sh 2>&1 | tail -5
ls -la migration-harness/references/processes/gsr_process.json
python3 -c "import json; d=json.load(open('migration-harness/references/processes/gsr_process.json')); print('cases:', len(d['cases']))"
```

Expected: ~260 cases.

- [ ] **Step 5: Implement `GsrProcessCore.java`**

Transcribe from C++. Use `JQuantMath.{exp,log,sin,cos}` for transcendentals. Leave the 1 `std::pow` site as `Math.pow` with inline comment:

```java
// Phase 2j P2J-5: leaving as Math.pow per design — only Math.pow site in
// the Gaussian1D family. If A19 fires (tier-flooring), Phase 2j followup
// mini-phase ports CORE-MATH cr_pow.
final double exponentTerm = Math.pow(integrand, 2.0);
```

- [ ] **Step 6: Implement `GsrProcess.java`**

Transcribe from C++. Wraps `GsrProcessCore` to implement the `StochasticProcess` (or `StochasticProcess1D`) interface that JQuantLib's existing process infrastructure uses.

- [ ] **Step 7: Write `GsrProcessTest.java`**

Probe-driven test loading `processes/gsr_process` reference. Iterates all ~260 cases, asserts at TIGHT tier:

```java
package org.jquantlib.testsuite.processes.gsr;

import org.jquantlib.processes.gsr.GsrProcess;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GsrProcessTest {
    @Test
    public void drift_diffusion_expectation_variance_matchCpp() {
        final ReferenceReader ref = ReferenceReader.load("processes/gsr_process");
        final GsrProcess proc = buildStandardGsrProcess(); // helper reconstructs the C++ probe's process
        for (String name : ref.caseNames()) {
            // Dispatch by name prefix: drift_*/diff_*/exp_*/var_*
            // Compare actual vs expected; assert TIGHT
            // ...
        }
    }
}
```

(Implementer subagent fills in the dispatch logic per probe-case naming.)

- [ ] **Step 8: Run test**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A/jquantlib
mvn test -Dtest='GsrProcessTest' 2>&1 | tail -10
```

Expected: pass at TIGHT.

If A19 fires due to Math.pow at GsrProcessCore: document inline (LOOSE-with-doc) and continue. Note the Math.pow site for completion-doc Phase 2j followup section.

- [ ] **Step 9: Full suite + scanner**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A/jquantlib && mvn test 2>&1 | grep "Tests run:" | tail -3
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A && python3 tools/stub-scanner/scan_stubs.py 2>&1 | tail -3
```

Expected: `Tests run: 689, Failures: 0, Errors: 0, Skipped: 22` (688 + 1). Scanner: 0 stubs.

- [ ] **Step 10: Discard timestamp-only regens; commit and push**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-A
git status --short | head -30
git checkout -- migration-harness/references/_smoke_test.json \
                migration-harness/references/currencies/ \
                migration-harness/references/math/ \
                migration-harness/references/model/ \
                migration-harness/references/patterns/ \
                migration-harness/references/pricingengines/ \
                migration-harness/references/termstructures/ 2>&1 | head -3 || true
# Don't discard models/shortrate/onefactormodels/ — that's our WI-1.1 ref + likely will get touched
git status --short

git add migration-harness/cpp/probes/processes/gsr_process_probe.cpp \
        migration-harness/references/processes/gsr_process.json \
        jquantlib/src/main/java/org/jquantlib/processes/gsr/ \
        jquantlib/src/test/java/org/jquantlib/testsuite/processes/gsr/
git commit -s -m "infra(processes.gsr): port GsrProcessCore + GsrProcess (Phase 2j WI-1.2)"
git push origin phase-2j-A-gaussian1d-model
```

- [ ] **Step 11: Fast-forward to main; update progress doc** ✅ for sub-layer 1.2.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib && git fetch origin && git merge --ff-only origin/phase-2j-A-gaussian1d-model && git push origin main
```

---

### Task 1.3: Port `Gsr` concrete model

**C++ source files:**
- `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/gsr.{hpp,cpp}` (~425 LOC total)

**Java target files (create):**
- `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/Gsr.java` (~550-700 LOC)

**Probe (create):**
- `migration-harness/cpp/probes/models/shortrate/onefactormodels/gsr_probe.cpp`
- `migration-harness/references/models/shortrate/onefactormodels/gsr.json`

**Java test (create):**
- `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/onefactormodels/gaussian1d/GsrTest.java`
- Modify (un-`@Ignore`): `Gaussian1dModelTest.java` from WI-1.1 — now Gsr exists, the WI-1.1 test can be enabled.

#### Steps

- [ ] **Step 1: Rebase worktree A** (per Task 1.2 Step 1).
- [ ] **Step 2: Read C++ source.**
- [ ] **Step 3: Write `gsr_probe.cpp`** per design §4: parameter readback (EXACT), forward variance V(t,T,T') (TIGHT, ~80 cases), discount bond P(t,T,x) (TIGHT, ~50 cases), numeraire identities (EXACT, ~20 cases).
- [ ] **Step 4: Build probe + generate references.** ~150 cases expected.
- [ ] **Step 5: Implement `Gsr.java`** — extends `Gaussian1dModel`. Calibration helper integration: extends QuantLib's `CalibratedModel` machinery if needed. Use `JQuantMath.*` for transcendentals.
- [ ] **Step 6: Write `GsrTest.java`** — probe-driven, asserts EXACT for parameter readback + numeraire identities, TIGHT for forward variance + discount bonds.
- [ ] **Step 7: Re-enable `Gaussian1dModelTest`** — remove `@Ignore`, wire up to Gsr, asserts TIGHT.
- [ ] **Step 8: Run tests.** Expected: 2 new tests pass (Gsr + the now-enabled Gaussian1dModelTest).
- [ ] **Step 9: Full suite.** Expected: `Tests run: 691, Failures: 0, Errors: 0, Skipped: 22` (689 + 2).
- [ ] **Step 10: Scanner; discard regens; commit; push; ff to main.**

```bash
git commit -s -m "infra(model.shortrate.onefactormodels.gaussian1d): port Gsr concrete model + enable Gaussian1dModelTest (Phase 2j WI-1.3)"
```

- [ ] **Step 11: Update progress doc** ✅ for sub-layer 1.3.

---

### Task 1.4: Port `Gaussian1dSmileSection` + `Gaussian1dSwaptionVolatility`

**C++ source files:**
- `migration-harness/cpp/quantlib/ql/termstructures/volatility/gaussian1dsmilesection.{hpp,cpp}` (~185 LOC)
- `migration-harness/cpp/quantlib/ql/termstructures/volatility/swaption/gaussian1dswaptionvolatility.{hpp,cpp}` (~163 LOC)

**Java target files (create):**
- `jquantlib/src/main/java/org/jquantlib/termstructures/volatility/gaussian1d/Gaussian1dSmileSection.java` (~250-300 LOC)
- `jquantlib/src/main/java/org/jquantlib/termstructures/volatility/gaussian1d/Gaussian1dSwaptionVolatility.java` (~200-250 LOC)
- `jquantlib/src/main/java/org/jquantlib/termstructures/volatility/gaussian1d/package-info.java`

**Probe (create):**
- `migration-harness/cpp/probes/termstructures/volatility/gaussian1d_vol_probe.cpp`
- `migration-harness/references/termstructures/volatility/gaussian1d_vol.json`

**Java test (create):**
- `jquantlib/src/test/java/org/jquantlib/testsuite/termstructures/volatility/gaussian1d/Gaussian1dVolTest.java`

#### Steps

- [ ] **Step 1: Rebase worktree A.**
- [ ] **Step 2: Read C++ source for both vol-structure files.**
- [ ] **Step 3: Write `gaussian1d_vol_probe.cpp`** — SmileSection variance/density at strike grid (~100 cases), SwaptionVolatility surface eval (~150 cases).
- [ ] **Step 4: Build probe + generate references** (~250 cases).
- [ ] **Step 5: Implement `Gaussian1dSmileSection.java`** — extends `SmileSection` base; provides volatility + density via the underlying Gaussian1dModel.
- [ ] **Step 6: Implement `Gaussian1dSwaptionVolatility.java`** — extends `SwaptionVolatilityStructure`; uses Gaussian1dSmileSection per (expiry, swap-tenor).
- [ ] **Step 7: Create `package-info.java`** with package Javadoc.
- [ ] **Step 8: Write `Gaussian1dVolTest.java`** — probe-driven, TIGHT tier.
- [ ] **Step 9: Run tests.** Expected: 1 new test (combined SmileSection + SwaptionVolatility) passes.
- [ ] **Step 10: Full suite.** Expected: `Tests run: 692, Failures: 0, Errors: 0, Skipped: 22` (691 + 1).
- [ ] **Step 11: Scanner; discard regens; commit; push; ff to main.**

```bash
git commit -s -m "infra(termstructures.volatility.gaussian1d): port Gaussian1dSmileSection + Gaussian1dSwaptionVolatility (Phase 2j WI-1.4)"
```

- [ ] **Step 12: Update progress doc** ✅ for sub-layer 1.4. **WI-1 complete.** Now dispatch WI-2 + WI-3 in parallel (both depend on full WI-1 layer).

---

## Layer 2 — WI-2 standard engines + WI-3 niche engines (parallel after WI-1.4 lands)

Both worktrees rebase on main tip post-WI-1.4. Disjoint package trees:
- WI-2 (worktree B): `pricingengines/swaption/gaussian1d/Gaussian1dSwaptionEngine` + `pricingengines/capfloor/gaussian1d/Gaussian1dCapFloorEngine`
- WI-3 (worktree C): `pricingengines/swaption/gaussian1d/{Gaussian1dJamshidian,Gaussian1dNonstandard,Gaussian1dFloatFloat}SwaptionEngine`

A9 (worktree merge conflict) expected zero — different files within the same package; Java compiler doesn't conflict on independent class files.

### WI-2 Task 2.1: Port `Gaussian1dSwaptionEngine`

**C++ source:** `migration-harness/cpp/quantlib/ql/pricingengines/swaption/gaussian1dswaptionengine.{hpp,cpp}` (~445 LOC).
**Java target:** `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/gaussian1d/Gaussian1dSwaptionEngine.java` (~600-750 LOC).
**Probe:** `migration-harness/cpp/probes/pricingengines/swaption/gaussian1d_swaption_engine_probe.cpp` — standard swaption NPV across (expiry, tenor, strike, integration-density) grid (~80 cases). TIGHT tier.
**Test:** `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/gaussian1d/Gaussian1dSwaptionEngineTest.java`.

#### Steps

- [ ] **Step 1: Rebase worktree B on main** (post-WI-1.4).
- [ ] **Step 2: Read C++ source.**
- [ ] **Step 3: Write probe.** Use Gsr as the underlying model (exercise the standard pricing path).
- [ ] **Step 4: Build + generate references.** ~80 cases.
- [ ] **Step 5: Implement `Gaussian1dSwaptionEngine.java`** — extends `Swaption.Engine`; uses `Gaussian1dModel` interface; numerical integration over the model's forward distribution.
- [ ] **Step 6: Create `package-info.java`** for the new subpackage.
- [ ] **Step 7: Write probe-driven test** at TIGHT tier.
- [ ] **Step 8: Run tests + full suite.** Expected: +1 test (`Tests run: 693`).
- [ ] **Step 9: Scanner; discard regens; commit; push; ff to main.**

```bash
git commit -s -m "infra(pricingengines.swaption.gaussian1d): port Gaussian1dSwaptionEngine (Phase 2j WI-2 B-1)"
```

### WI-2 Task 2.2: Port `Gaussian1dCapFloorEngine`

**C++ source:** `migration-harness/cpp/quantlib/ql/pricingengines/capfloor/gaussian1dcapfloorengine.{hpp,cpp}` (~286 LOC).
**Java target:** `jquantlib/src/main/java/org/jquantlib/pricingengines/capfloor/gaussian1d/Gaussian1dCapFloorEngine.java` (~400-500 LOC).
**Probe:** `migration-harness/cpp/probes/pricingengines/capfloor/gaussian1d_capfloor_engine_probe.cpp` — cap/floor NPV across grid (~60 cases). TIGHT tier.
**Test:** `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/capfloor/gaussian1d/Gaussian1dCapFloorEngineTest.java`.

#### Steps

- [ ] **Step 1-9: Mirror Task 2.1 steps** with capfloor-specific paths.

```bash
git commit -s -m "infra(pricingengines.capfloor.gaussian1d): port Gaussian1dCapFloorEngine (Phase 2j WI-2 B-2)"
```

Expected after WI-2: `Tests run: 694, Failures: 0, Errors: 0, Skipped: 22`.

---

### WI-3 Task 3.1: Port `Gaussian1dJamshidianSwaptionEngine`

**C++ source:** `gaussian1djamshidianswaptionengine.{hpp,cpp}` (~182 LOC).
**Java target:** `Gaussian1dJamshidianSwaptionEngine.java` (~250-300 LOC).
**Probe:** `gaussian1d_jamshidian_swaption_engine_probe.cpp` — Jamshidian decomposition (~50 cases). TIGHT tier.
**Test:** `Gaussian1dJamshidianSwaptionEngineTest.java`.

#### Steps

- [ ] **Step 1: Rebase worktree C on main** (post-WI-1.4).
- [ ] **Step 2-9: Standard port flow** (read C++, write probe, generate refs, implement Java, write test, run, scanner, commit, push, ff).

```bash
git commit -s -m "infra(pricingengines.swaption.gaussian1d): port Gaussian1dJamshidianSwaptionEngine (Phase 2j WI-3 C-1)"
```

### WI-3 Task 3.2: Port `Gaussian1dNonstandardSwaptionEngine`

**C++ source:** `gaussian1dnonstandardswaptionengine.{hpp,cpp}` (~636 LOC).
**Java target:** `Gaussian1dNonstandardSwaptionEngine.java` (~800-1000 LOC).
**Probe:** `gaussian1d_nonstandard_swaption_engine_probe.cpp` — non-standard swaption with custom amortization (~60 cases). TIGHT tier.

#### Steps

- [ ] **Step 1-9: Standard port flow.**

```bash
git commit -s -m "infra(pricingengines.swaption.gaussian1d): port Gaussian1dNonstandardSwaptionEngine (Phase 2j WI-3 C-2)"
```

### WI-3 Task 3.3: Port `Gaussian1dFloatFloatSwaptionEngine`

**C++ source:** `gaussian1dfloatfloatswaptionengine.{hpp,cpp}` (~848 LOC — largest engine).
**Java target:** `Gaussian1dFloatFloatSwaptionEngine.java` (~1100-1400 LOC).
**Probe:** `gaussian1d_float_float_swaption_engine_probe.cpp` — FloatFloat NPV deepest integration (~40 cases). TIGHT tier expected; **LOOSE acceptable per design** if numerical depth requires (document inline with source-of-slack named).

#### Steps

- [ ] **Step 1-9: Standard port flow.**

If LOOSE tier required: inline comment cites the integration depth (e.g. "outer numeraire rebasing × inner cashflow accumulation × strike integration → ULP magnification beyond TIGHT bound"). Per design §4 risk 2.

```bash
git commit -s -m "infra(pricingengines.swaption.gaussian1d): port Gaussian1dFloatFloatSwaptionEngine (Phase 2j WI-3 C-3)"
```

After WI-3: 3 new tests added. Expected: `Tests run: 697, Failures: 0, Errors: 0, Skipped: 22` (694 + 3 from WI-3).

---

## Layer 3 — WI-4 MarkovFunctional (parallel after WI-1.1 lands)

**Worktree:** `/Users/josemoya/eclipse-workspace/jquantlib-2j-D`
**Branch:** `phase-2j-D-markov-functional`
**Pre-requisite:** WI-1.1 lands (provides `Gaussian1dModel` base). Can dispatch concurrent with WI-1.2/1.3/1.4 + WI-2/WI-3.
**Subagent class:** opus (1710 LOC C++ → ~2200-2700 Java; calibration logic is the most algorithmically dense single piece in Phase 2j).

### Task 4.1: Port `MarkovFunctional`

**C++ source:** `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/markovfunctional.{hpp,cpp}` (~1710 LOC total).
**Java target:** `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/gaussian1d/MarkovFunctional.java` (~2200-2700 LOC).
**Probe:** `migration-harness/cpp/probes/models/shortrate/onefactormodels/markov_functional_probe.cpp` — calibrated parameter table (TIGHT, ~30 sigma values), forward swap rate fits (TIGHT, ~40 cases), annuity numeraire identities (TIGHT, ~30 cases).
**Test:** `MarkovFunctionalTest.java` — probe-driven.

#### Steps

- [ ] **Step 1: Rebase worktree D on main** (post-WI-1.1, before WI-1.2 lands is fine — MarkovFunctional only depends on Gaussian1dModel base, not Gsr/process).

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2j-D
git fetch origin
git rebase origin/main
git log --oneline -5
```

Expected: tip includes WI-1.1 commit.

- [ ] **Step 2: Read C++ source carefully.** MarkovFunctional has its own forward measure machinery, calibration loop (Brent-based fitting of sigma to swap rates at each calibration date), and smile-fit handling. Substantially different shape from Gsr.

- [ ] **Step 3: Write probe.** Crucially: probe captures the calibrated parameter vector (sigma stepwise) — that's the fingerprint that detects iteration-order divergence (A20 trigger).

```cpp
// Capture calibrated sigma after running the C++ MarkovFunctional calibration
// over a fixed (calibration_dates, swap_rates) input. Java port must produce
// the same sigma vector to pass.
const auto sigmas = mf->calibratedSigmas();
for (Size i = 0; i < sigmas.size(); ++i) {
    char nm[32]; std::snprintf(nm, sizeof nm, "sigma_%02zu", i);
    out.addCase(nm, json{{"i", i}}, json{{"value", sigmas[i]}});
}
```

- [ ] **Step 4: Build probe + generate references.** ~100 cases.

- [ ] **Step 5: Implement `MarkovFunctional.java`.** Key transcription discipline:
  - **Match C++ iteration order strictly.** Do NOT add any "early-exit" optimization to the calibration loop — even if mathematically equivalent, ordering changes can produce different floating-point results (A20 risk).
  - Use `JQuantMath.{exp,log,sin,cos}` from day one.
  - Calibration uses `Brent` solver (already in JQuantLib from Phase 2g) — pass identical `accuracy`, `maxEvaluations`, initial guess, bracket arguments matching the C++ caller.

- [ ] **Step 6: Write `MarkovFunctionalTest.java`.** TIGHT tier. If A20 fires (calibrated sigmas differ across runs or from C++): pause and investigate iteration-order divergence; may need an `align(math.solvers1D.Brent)` align-fix commit.

- [ ] **Step 7: Run tests.** Expected: 1 new test passes.

If A20: pause, diagnose, decide — re-port with stricter ordering, or add Brent align-fix.

- [ ] **Step 8: Full suite.** After WI-4 lands on main (alongside WI-2 + WI-3 already merged): `Tests run: 698, Failures: 0, Errors: 0, Skipped: 22` (697 + 1).

- [ ] **Step 9: Scanner; discard regens; commit; push; ff to main.**

```bash
git commit -s -m "infra(model.shortrate.onefactormodels.gaussian1d): port MarkovFunctional concrete model (Phase 2j WI-4)"
```

- [ ] **Step 10: Update progress doc** ✅ for WI-4 (and possibly WI-2/WI-3 if they're already done).

---

## Layer 4 — Completion doc + tag

### Task 4.1: Write the completion doc

**Files:**
- Create: `docs/migration/phase2j-completion.md`

- [ ] **Step 1:** Write the completion doc following Phase 2h shape.

Sections:
1. Header (status, predecessor tag, this phase's tip SHA, plan + design + progress doc commits).
2. Final state table (test count `688 → ~698`, scanner WIP=0, 5 new subpackages, ~7000-9000 new Java LOC, tier outcomes per engine).
3. Per-WI summary (commit SHAs for each sub-layer + each engine + MarkovFunctional, tier outcomes, A19/A20 fires).
4. A-trigger fire history.
5. Decision log additions (any P2J-11+ surfaced during execution).
6. Math.pow disposition: did A19 fire at GsrProcessCore? If yes: documented Phase 2j followup. If no: TIGHT held even with Math.pow — interesting empirical finding.
7. Phase 2k seed list:
   - `JQuantMath.pow` followup (if A19 fired)
   - `JQuantMath.lgamma` (if a correctly-rounded source surfaces)
   - `U128.java` shared util extraction (LogKernel + Dint64 duplication)
   - Other Fdm-dependent engines (FdHestonHullWhite, FdSabrVanilla, FdConvertibleBond, FdAndreasenHugeLocalVol, FdBlackScholesVanilla)
   - Phase 2h Fdm completeness items (Bermudan/American/dividend, BiCGStab/GMRES, scheme expansion)
   - Douglas ADI / FdmAffineModelTermStructure investigation
   - Calibration-via-market-data integration (Gaussian1D ready for it)

- [ ] **Step 2: Commit and push.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git add docs/migration/phase2j-completion.md
git commit -s -m "docs(migration): Phase 2j completion — Gaussian1D family port complete"
git push origin main
```

### Task 4.2: Tag the phase

- [ ] **Step 1: Create and push the tag.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git tag -a jquantlib-phase2j-complete -m "Phase 2j complete — Gaussian1D family port (Gaussian1dModel base + Gsr + MarkovFunctional + GsrProcess + 2 vol structures + 5 swaption/cap-floor engines). ~7000-9000 new Java LOC across 5 new subpackages. Test count <FILL>/0/0/22; scanner WIP=0."
git push origin jquantlib-phase2j-complete
```

- [ ] **Step 2: Verify tag.**

```bash
git show jquantlib-phase2j-complete --stat | head -10
```

### Task 4.3: Update memory

**Files:**
- Modify: `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/MEMORY.md`
- Modify: `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md`

- [ ] **Step 1:** Update `project_jquantlib_migration.md`:
  - Add a Phase 2j paragraph under Phase 2i.6 with WI-1/WI-2/WI-3/WI-4 outcomes, A-trigger fires, completion details, key findings.
  - Add 2026-05-NN date entry for Phase 2j completion.
  - Update description-line frontmatter with new tag/tip.

- [ ] **Step 2:** Update `MEMORY.md`:
  - Update the JQuantLib migration line — new tip SHA, test count, Gaussian1D family complete, refreshed Phase 2k candidates.

(Memory updates are not committed.)

### Task 4.4: Tear down worktrees

- [ ] **Step 1: Remove the 4 phase-2j worktrees.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git worktree remove --force ../jquantlib-2j-A
git worktree remove --force ../jquantlib-2j-B
git worktree remove --force ../jquantlib-2j-C
git worktree remove --force ../jquantlib-2j-D
git worktree list
```

Expected: only main remains.

- [ ] **Step 2: Delete merged branches local + remote.**

```bash
git branch -D phase-2j-A-gaussian1d-model phase-2j-B-standard-engines phase-2j-C-niche-swaption-engines phase-2j-D-markov-functional
git push origin --delete phase-2j-A-gaussian1d-model phase-2j-B-standard-engines phase-2j-C-niche-swaption-engines phase-2j-D-markov-functional
```

---

## Self-Review

(Run by writer before handoff — for controller / executor reference.)

**Spec coverage:**
- §1 goals (full Gaussian1D family) → covered by L1 (5 sub-commits in WI-1) + WI-4 + L2 (WI-2 + WI-3 engines).
- §2 chosen approach (Approach A sub-layered + parallel engines) → encoded in L1 sequential + L2 parallel structure.
- §3 worktree topology (4 worktrees A/B/C/D) → enforced in L0 setup + dispatch ordering (D parallel from WI-1.1; B/C parallel after WI-1.4).
- §4 tolerance tiers + 11 probes → covered: 5 model-side probes (gaussian1d_model + gsr + markov_functional + gsr_process + gaussian1d_vol) + 5 engine probes (1 standard swaption + 1 capfloor + 1 jamshidian + 1 nonstandard + 1 floatfloat) + 1 reserved for engine-MF compat (likely unused).
- §4 test discipline (probe-before-port, no backfilling green, JQuantMath from day one, per-engine compile + test gate) → embedded across all task templates.
- §4 test count target `688 → ~698` → tracked in per-task expectations.
- §5 pause triggers (A2/A3/A4/A8/A9/A13/A15/A16/A17/A18/A19/A20/A21) → invocation conditions described where they could fire.
- §5 exit criteria → covered by L4 (completion + tag + memory + teardown).

**Placeholder scan:**
- `<fill at L0 land>` and `<FILL>` (tag message) appear intentionally as deferred fill-ins for the controller post-execution.
- Task templates 3.1/3.2/3.3 say "Standard port flow" — Phase 2h plan used the same compactness; for similarly-shaped engines after the first detailed task, repetition is noise. The implementer subagent prompt at dispatch time will spell out the full step list per engine.
- Task 1.3 / 1.4 use compact step lists (versus Task 1.1's explicit code blocks) — same rationale: the implementer dispatch prompt will spell out details. Acceptable per Phase 2h precedent.

**Type consistency:**
- Java package paths consistent: `org.jquantlib.model.shortrate.onefactormodels.gaussian1d` (singular `model`), `org.jquantlib.processes.gsr` (plural `processes`), `org.jquantlib.pricingengines.swaption.gaussian1d`, `org.jquantlib.pricingengines.capfloor.gaussian1d`, `org.jquantlib.termstructures.volatility.gaussian1d`.
- Probe test-group strings consistent with directory layout.
- All references to `JQuantMath.{exp,log,sin,cos}` use existing facade (Phase 2i/2i.5/2i.6 deliverables).
- `Math.pow` exception isolated to GsrProcessCore (1 site, per design P2J-5).

# Phase 2g Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Each WI runs in its own git worktree (see L0 setup); WI-1 lands first then WI-2 + WI-3 run concurrently.

**Goal:** Land the three Phase 2g work items per `docs/migration/phase2g-design.md`: WI-1 Brent.solveImpl pre-loop init alignment + bundled small alignment fixes (VanillaSwap.setupArguments + FloatingRateCoupon.fixingDays) + tier promotions; WI-2 FdHullWhiteSwaptionEngine port (Java's first FD pricing engine); WI-3 FdG2SwaptionEngine port (sibling 2D FD engine using TreeLattice2D infrastructure). End state: scanner WIP unchanged at 0; tests 675 → ~677-678; tag `jquantlib-phase2g-complete`.

**Architecture:** Same as Phase 2c-2f — direct commits to `main`, TDD per stub, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes, tolerance tiers (exact/tight/loose). 3 git worktrees per `phase2g-design.md` §3 — A=Brent + alignments, B=FdHullWhiteSwaptionEngine, C=FdG2SwaptionEngine. **Important ordering constraint:** WI-2 and WI-3 indirectly depend on WI-1 (FD engines use HW/G2 tree() which goes through Brent), so probe reference generation in WI-2/WI-3 happens AFTER WI-1 lands. Pause triggers per design §5: A6 disabled, A4 sharpened (FD scaffold extension if needed), A8/A10/A11/A12/A14 inactive, A9 worktree-merge-conflict, A13 carried from 2f (transcendental drift), A15 NEW for Brent fix surfacing previously-hidden bug, A16 NEW for FD scaffold gap.

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule (commit `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`); Python 3 for scanner tooling; nlohmann/json for probe output; git worktrees for parallel implementer execution.

---

## Overview

| Layer | Description | Worktree | Expected commits |
|-------|-------------|----------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner, set up 3 worktrees, init progress doc | (main) | 1 |
| L1a | WI-1 first (priority + blocking dependency) | A | 6–10 |
| L1b | WI-2 + WI-3 parallel (after WI-1 lands) | B, C | 2–3 each |
| L2 | Completion doc + tag | (main) | 1 commit + tag |

**Non-goals reminder (design §1):** Gaussian1D family, transcendental library port (P2G-6 → Phase 2h), HestonProcess.pdf(), BlackSwaptionEngine Cash/ParYieldCurve settlement, additionalResults, addTimesTo Time-impedance, TreeLattice2D API formalization, HaltonRsg FMA docs, SABR 5e-8 investigation, BroadieKaya asset-leg tolerance, Math.exp ULP slack — all deferred.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by: Claude` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit's full suite passes. Commit messages follow `<kind>(<pkg>): <verb> ...` with `(Phase 2g WI-N)` suffix. Each WI-1 fix is a separate `align(<pkg>)` commit per CLAUDE §4.2.

**Parallelism (P2G-3):** WI-2/WI-3 implementers can dispatch their port code work in parallel with WI-1 dispatch (working off pre-Brent main), but **probe reference generation MUST happen post-WI-1 land + rebase**. Final commits land sequentially: WI-1 first, then WI-2 + WI-3 in parallel.

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

- [ ] **Step 5:** Capture Phase 2f tip.

```bash
git rev-parse main
git tag -l 'jquantlib-phase2f-complete'
```

Expected: tip `debedf9` (or later if any docs landed); tag exists.

### Task 0.2: Create 3 git worktrees

- [ ] **Step 1:** Create branches and worktrees.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-2g-A-brent-aligns ../jquantlib-2g-A main
git worktree add -b phase-2g-B-fd-hullwhite ../jquantlib-2g-B main
git worktree add -b phase-2g-C-fd-g2 ../jquantlib-2g-C main
git worktree list
```

Expected: 4 worktrees listed (main + 3 new).

- [ ] **Step 2:** Verify each worktree builds clean.

```bash
(cd ../jquantlib-2g-A/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2g-B/jquantlib && mvn test-compile -q) 2>&1 | tail -3
(cd ../jquantlib-2g-C/jquantlib && mvn test-compile -q) 2>&1 | tail -3
```

Expected: each prints BUILD SUCCESS or empty (exit 0).

### Task 0.3: Init progress doc

- [ ] **Step 1:** Create `docs/migration/phase2g-progress.md` mirroring `phase2f-progress.md` (header + worktrees table + pause-trigger status + layer/WI progress sections + test-count tracking table). Initial state: `675/0/0/22`, scanner WIP=0.

- [ ] **Step 2:** Commit + push.

```bash
git add docs/migration/phase2g-progress.md
git commit -s -m "docs(migration): init phase2g-progress log"
git push origin main
```

- [ ] **Step 3:** Rebase each worktree onto the new main.

```bash
(cd ../jquantlib-2g-A && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2g-B && git fetch origin && git rebase origin/main) 2>&1 | tail -3
(cd ../jquantlib-2g-C && git fetch origin && git rebase origin/main) 2>&1 | tail -3
```

---

## Layer 1a — WI-1 first (priority + blocking dependency)

> WI-1 dispatches first. WI-2/WI-3 may dispatch their port-code work in parallel but their final commits gate on WI-1 landing.

## Worktree A — WI-1 Brent.solveImpl alignment + bundled fixes

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2g-A/`
**Branch:** `phase-2g-A-brent-aligns`

### File structure for WI-1

| Action | Path | Responsibility |
|--------|------|----------------|
| Modify | `jquantlib/src/main/java/org/jquantlib/math/solvers1D/Brent.java` | Port C++ pre-loop init |
| Modify | (potentially many probe `.cpp` + `.json` files) | Regenerate Brent-touching references |
| Modify | (potentially many test `.java` files) | Tier promotions Jamshidian + G2.swaption + maybe NCCV; reference-shift updates |
| Modify | `jquantlib/src/main/java/org/jquantlib/instruments/VanillaSwap.java` | Fix inverted `isAssignableFrom` + List capacity-vs-size |
| Modify | `jquantlib/src/main/java/org/jquantlib/cashflow/FloatingRateCoupon.java` | Honor `fixingDays==0` literally |

### Task A.1: Port Brent.solveImpl pre-loop init

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/solvers1D/Brent.java`

**Reference:** C++ `migration-harness/cpp/quantlib/ql/math/solvers1d/brent.hpp` lines 40-90 (the `solveImpl` method's pre-loop init).

- [ ] **Step 1: Read C++ source for the pre-loop init.**

```bash
sed -n '40,100p' migration-harness/cpp/quantlib/ql/math/solvers1d/brent.hpp
```

Note key C++ details:
- Pre-loop: `froot = f(root_); ++evaluationNumber_;`
- Branch on sign: `if (froot * fxMin_ < 0) { xMax_ = xMin_; fxMax_ = fxMin_; } else { xMin_ = xMax_; fxMin_ = fxMax_; }`
- `Real d = root_ - xMax_; Real e = d;`
- (At convergence return: also calls `f(root_); ++evaluationNumber_;` — minor)

- [ ] **Step 2: Read current Java state.**

```bash
sed -n '45,90p' jquantlib/src/main/java/org/jquantlib/math/solvers1D/Brent.java
```

Currently:
```java
double d = 0.0, e = 0.0;

root = xMax;
froot = fxMax;
while (evaluationNumber <= getMaxEvaluations()) {
    ...
}
```

This skips evaluating `f(root)` before the loop and uses `xMax` as the initial root pivot — diverges from C++.

- [ ] **Step 3: Apply the fix.** Replace the pre-loop init in Brent.java with:

```java
// Phase 2g WI-1: align with C++ v1.42.1 brent.hpp pre-loop init.
// Evaluate f at root (which equals guess from AbstractSolver1D.solve)
// before entering the main loop. This seeds the Brent state on the
// correct side of the bracket and changes downstream Dekker-Brent
// pivot selection to match C++.
froot = f.op(root);
evaluationNumber++;
if (froot * fxMin < 0) {
    xMax = xMin;
    fxMax = fxMin;
} else {
    xMin = xMax;
    fxMin = fxMax;
}
double d = root - xMax;
double e = d;

while (evaluationNumber <= getMaxEvaluations()) {
    // ... rest of method unchanged ...
```

(Verify: `root` is set by the parent `AbstractSolver1D.solve(...)` to `guess` before `solveImpl` is called; `xMin`, `xMax`, `fxMin`, `fxMax` are similarly seeded by the parent's bracket logic. Read `AbstractSolver1D.java` to confirm.)

Also at the convergence-return inside the main loop (where `Math.abs(xMid) <= xAcc1 || froot == 0.0`), C++ adds:
```cpp
if (std::fabs(xMid) <= xAcc1 || (close(froot, 0.0))) {
    f(root_);
    ++evaluationNumber_;
    return root_;
}
```

Mirror in Java:
```java
if (Math.abs(xMid) <= xAcc1 || froot == 0.0) {
    f.op(root);
    evaluationNumber++;
    return root;
}
```

(Note also: C++ uses `close(froot, 0.0)` which is a tolerance check, not strict equality. Java's `froot == 0.0` is stricter. If a test fails because of this stricter check, port `Closeness.isClose(froot, 0.0)` semantics — check `org.jquantlib.math.Closeness` for the existing helper.)

- [ ] **Step 4: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2g-A
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Run all tests — expect drift on Brent-touching probes.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "FAILED|^\[WARNING\] Tests run: [0-9]+,"
```

Expected outcome: many tests **may** drift slightly. Failures most likely in Brent-touching probes whose existing reference values were captured under the old Brent. **DO NOT panic.** Task A.2 regenerates references.

If tests pass with no drift at all, that's also acceptable (means existing references were tolerant enough). Proceed to commit.

- [ ] **Step 6: Commit Brent fix in isolation.**

```bash
git add jquantlib/src/main/java/org/jquantlib/math/solvers1D/Brent.java
git commit -s -m "align(math.solvers1D): Brent.solveImpl pre-loop init match C++ v1.42.1 (Phase 2g WI-1)"
```

(Don't push yet — Task A.2 regeneration may need to land in separate commits per probe-touching test file.)

### Task A.2: Regenerate Brent-touching probe references + verify Java drift

**Background:** 11 production Brent callers — each one's tests may need probe regeneration:
1. `JamshidianSwaptionEngine` — was LOOSE, expected to drop to TIGHT
2. `G2.swaption(...)` — was LOOSE, expected to drop to TIGHT
3. `HestonProcess` (varianceDistribution → InverseNCCS via Brent + BroadieKaya cdf_nu_ds → Brent)
4. `CashFlows` (yield-from-NPV solver)
5. `InverseNonCentralCumulativeChiSquaredDistribution` (Brent inversion of NCCS)
6. `Bond` (yield calculation)
7. `ImpliedVolatilityHelper`
8. `BlackKarasinski` (TermStructureFittingParameter)
9. `OneFactorModel` (in `.tree(grid)` path, TermStructureFittingParameter)
10. `BlackCalibrationHelper` (impliedVolatility solver)

Plus indirect: `G2.tree(grid)` → TermStructureFittingParameter Brent. Phase 2c WI-5 BK tree, Phase 2c WI-4 HW tree, Phase 2e WI-1 G2 tree fingerprints all touch this.

- [ ] **Step 1: Run full test suite + capture failures.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "FAILED|^\[WARNING\] Tests run: [0-9]+," | head -50
```

For each failed test, identify which probe reference is being checked and which `.json` file is involved.

- [ ] **Step 2: Regenerate every Brent-touching probe.** Use the harness to regenerate via C++ (which will pick up the C++ Brent reference values that Java now matches):

```bash
./migration-harness/scripts/generate-references.sh
# Or, more targeted, regenerate specific probes:
# ./migration-harness/scripts/generate-references.sh jamshidianswaptionengine_probe g2_probe heston_nccv_probe heston_broadiekaya_probe ...
```

If submodule not initialized in worktree A, build via main worktree's pre-warmed `cpp/build` (Phase 2d/2e/2f precedent — copy JSONs back).

- [ ] **Step 3: Re-run Java tests — verify all pass at their declared tolerances.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "FAILED|^\[WARNING\] Tests run: [0-9]+,"
```

Expected: all pass at their existing tier (some tests may be passing with much tighter actual residuals — these are candidates for tier promotion in Task A.3).

If a previously-TIGHT test fails TIGHT after regeneration, **A15 trigger watch:** the Brent fix may have surfaced a previously-hidden Java port bug that the old Brent's slop was masking. Pause and report DONE_WITH_CONCERNS describing the failing test + observed-vs-reference values.

- [ ] **Step 4: Commit regenerated references.**

```bash
git add migration-harness/references/
# Also any test files that were updated to load the new references (most should not change)
git commit -s -m "infra(harness): regenerate Brent-touching probe references for new C++ Brent semantics (Phase 2g WI-1)"
```

### Task A.3: Tier promotions

- [ ] **Step 1: Promote JamshidianSwaptionEngine test from LOOSE to TIGHT.**

Find the test:
```bash
grep -rn "JamshidianSwaptionEngineTest\|jamshidian_swaption_npv" jquantlib/src/test/java
```

The Phase 2f test was at LOOSE (1e-8). Change to TIGHT (1e-12):
```java
// Phase 2g WI-1: post-Brent-fix tier promotion (was LOOSE due to pre-fix Brent
// pre-loop init divergence; new Brent matches C++ bit-faithfully).
assertEquals(ref.getDouble("jamshidian_swaption_npv"), swaption.NPV(), 1.0e-12);
```

Run + verify:
```bash
mvn -pl jquantlib test -Dtest='JamshidianSwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```
Expected: PASS at tight tier.

- [ ] **Step 2: Promote G2.swaption integral test from LOOSE to TIGHT.**

Find:
```bash
grep -rn "testSwaptionIntegralFingerprint\|G2Test" jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/twofactormodels/
```

Same pattern: change `1.0e-8` to `1.0e-12` in the swaption integral assertion. Run + verify.

- [ ] **Step 3: Attempt NCCV tier promotion (conditional commit).**

Phase 2f WI-3 rolled this back. Try again now that Brent init is C++-faithful:
```bash
grep -n "nccv_\|NCCV\|1\\.0e-8" jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java
```

If the runner's tolerance constant is shared across NCCV cases, promote it:
```java
// Phase 2g WI-1: post-Brent-fix tier promotion attempt.
// Phase 2f's rollback was due to pre-fix Brent amplifying Math.exp ULP drift;
// new Brent init may narrow the gap enough for tight tier.
assertDoubleTight(...) // or similar
```

Run:
```bash
mvn -pl jquantlib test -Dtest='HestonProcessTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

If passes: keep TIGHT, commit. If fails: roll back to LOOSE with updated inline comment ("Phase 2g WI-1 retry: NCCV tier still gated by Math.exp ULP slack — Phase 2f A13 carry-over"). NCCV tier promotion is **conditional** — A13 trigger if it fails.

- [ ] **Step 4: Opportunistic CIR/HW/BK tree fingerprint promotions.**

Some tree fingerprint tests at LOOSE may now pass TIGHT. For each such test, try TIGHT; if pass, promote; if fail, leave at LOOSE.

```bash
grep -rn "1\\.0e-8.*tree\|tree.*1\\.0e-8" jquantlib/src/test/java
```

For each candidate, change tolerance to TIGHT and run. Promote if it passes.

- [ ] **Step 5: Commit tier promotions.** One commit per WI to keep blame clean:

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/JamshidianSwaptionEngineTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/twofactormodels/G2Test.java \
        # ... any opportunistic tree promotions
git commit -s -m "test(pricingengines.swaption,model.shortrate): tier promotions post-Brent fix — Jamshidian + G2.swaption tight, opportunistic tree promotions (Phase 2g WI-1)"

# Separate commit if NCCV tier promotion succeeds:
git add jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java
git commit -s -m "test(processes): NCCV tier promotion to tight post-Brent fix (Phase 2g WI-1)"
```

### Task A.4: VanillaSwap.setupArguments fix

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/instruments/VanillaSwap.java`

**Reference:** Phase 2e WI-3 + Phase 2f WI-2 deliberately bypassed this. C++ `vanillaswap.cpp` `VanillaSwap::setupArguments`.

- [ ] **Step 1: Find the buggy code.**

```bash
grep -n "setupArguments\|isAssignableFrom\|new ArrayList<>(" jquantlib/src/main/java/org/jquantlib/instruments/VanillaSwap.java | head -20
```

Two known bugs:
1. Inverted `isAssignableFrom` check — currently always-false for non-VanillaSwap callers
2. `new ArrayList<>(size)` (capacity, not size) followed by `.set(i, ...)` — IndexOutOfBoundsException

- [ ] **Step 2: Apply the fix.** Invert the `isAssignableFrom` test direction (from `superclass.isAssignableFrom(this.getClass())` to `this.getClass().isAssignableFrom(superclass)` — verify exact direction against C++ semantics by reading `migration-harness/cpp/quantlib/ql/instruments/vanillaswap.cpp` setupArguments).

For the List allocation, change:
```java
List<Date> resetDates = new ArrayList<>(size);  // capacity, not size — broken
// ...
resetDates.set(i, ...);  // IndexOutOfBoundsException
```

To:
```java
List<Date> resetDates = new ArrayList<>(Collections.nCopies(size, null));
// ...
resetDates.set(i, ...);  // works
```

Apply to all affected List allocations in setupArguments (likely several — fixedResetDates, floatingResetDates, fixedPayDates, floatingPayDates, etc. — read full method body first).

- [ ] **Step 3: Compile.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Run tests.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run|FAILED|ERROR" | head
```

Expected: all pass. If failures surface, they're tests that were previously bypassing the broken setupArguments and are now hitting the fixed path (which is a good thing — port-correctness improvement).

- [ ] **Step 5: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/instruments/VanillaSwap.java
git commit -s -m "align(instruments): VanillaSwap.setupArguments inverted isAssignableFrom + List capacity-vs-size fix (Phase 2g WI-1)"
```

### Task A.5: FloatingRateCoupon.fixingDays==0 fix

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/cashflow/FloatingRateCoupon.java`
- Modify (regenerate): probes that previously avoided `withFixingDays(0)` — Phase 2f WI-1 cap probes deliberately omitted this.

- [ ] **Step 1: Find the divergence.**

```bash
grep -n "fixingDays\|fixingDays_" jquantlib/src/main/java/org/jquantlib/cashflow/FloatingRateCoupon.java | head -20
```

Likely:
```java
this.fixingDays_ = fixingDays == 0 ? index.fixingDays() : fixingDays;
```

- [ ] **Step 2: Apply the fix — honor 0 literally.** Match C++ `floatingratecoupon.cpp`:
```java
this.fixingDays_ = fixingDays;
```

(Verify against C++ source. If C++ does have a 0→default behavior somewhere else, mirror that.)

- [ ] **Step 3: Compile + run tests.**

```bash
mvn -pl jquantlib test-compile -q 2>&1 | tail -3
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run|FAILED|ERROR" | head
```

Expected: all pass — previous probes deliberately avoided `withFixingDays(0)`.

- [ ] **Step 4: Optional — flip Phase 2f WI-1 cap probes to use `withFixingDays(0)` explicitly** (since the divergence is now fixed). Find:

```bash
grep -rn "withFixingDays\|fixing_days" migration-harness/cpp/probes/pricingengines/capfloor/
```

If the implementer chooses to do this enrichment, regenerate the JSONs + re-run Java tests. Optional — keep as-is also acceptable.

- [ ] **Step 5: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/cashflow/FloatingRateCoupon.java
# Plus any probe regeneration
git commit -s -m "align(cashflow): FloatingRateCoupon honor fixingDays==0 literally (Phase 2g WI-1)"
git push origin phase-2g-A-brent-aligns
```

### Task A.6: Land worktree A to main

- [ ] **Step 1: From the MAIN checkout, fast-forward.**

```bash
git -C /Users/josemoya/eclipse-workspace/jquantlib fetch origin
git -C /Users/josemoya/eclipse-workspace/jquantlib merge --ff-only origin/phase-2g-A-brent-aligns
git -C /Users/josemoya/eclipse-workspace/jquantlib log --oneline -10
git -C /Users/josemoya/eclipse-workspace/jquantlib push origin main
```

If `merge --ff-only` refuses, no other phase-2g branches have landed yet (WI-1 is first per L1a) — investigate before force-merging.

After WI-1 lands, WI-2 + WI-3 implementers can rebase and proceed to probe-reference generation.

---

## Layer 1b — WI-2 + WI-3 parallel (after WI-1 lands)

> Both WI-2 and WI-3 dispatch port code in parallel with WI-1's dispatch (work is independent of Brent's exact behavior). Their final commits gate on WI-1 landing because probe references must be regenerated against post-Brent main.

## Worktree B — WI-2 FdHullWhiteSwaptionEngine

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2g-B/`
**Branch:** `phase-2g-B-fd-hullwhite`

### File structure for WI-2

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdHullWhiteSwaptionEngine.java` | Port v1.42.1 (161 LOC) |
| Modify (maybe) | `jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/...` | FD scaffold extension if needed (A4 / A16 watch) |
| Create | `migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp` | C++ ref |
| Create | `migration-harness/references/pricingengines/swaption/fdhullwhiteswaptionengine.json` | reference data |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdHullWhiteSwaptionEngineTest.java` | LOOSE-tier fingerprint |

### Task B.1: Port FdHullWhiteSwaptionEngine

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdhullwhiteswaptionengine.{hpp,cpp}` (161 LOC total).

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdhullwhiteswaptionengine.hpp
sed -n '1,160p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdhullwhiteswaptionengine.cpp
```

Note key shape:
- Constructor: `FdHullWhiteSwaptionEngine(HullWhite, int xGrid, int tGrid, int dampingSteps, double invEps, FdmSchemeDesc)` (verify exact param list — likely some defaults)
- Inherits from `Swaption.EngineImpl extends GenericEngine<Swaption.Arguments, Swaption.Results>`
- `calculate()`:
  - Cast `arguments_` to `Swaption.ArgumentsImpl`
  - Build a Hull-White FD mesh (1D in state, time grid from exercise dates back to t=0)
  - Set up boundary conditions for swaption payoff at exercise
  - Roll back via FD scheme (operator splitting / Crank-Nicolson / damping steps)
  - Read swaption value at the t=0 node corresponding to the initial state
  - `results_.value = ...`

- [ ] **Step 2: Look at existing Java FD scaffold.**

```bash
ls jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/
grep -ln "class FdmHullWhite\|HullWhiteFwd" jquantlib/src/main/java
```

Java has 35 FD scaffold files. Identify which classes are the analogues of C++ `FdmHullWhiteOp`, `FdmStepConditionComposite`, `FdmAffineModelSwapInnerValue` — needed by the engine port. If a piece is missing (e.g. `FdmAffineModelSwapInnerValue` for swaption inner value), bundle as `align(methods.finitedifferences)` fix per A16 trigger guidance.

- [ ] **Step 3: Port to Java.**

```java
package org.jquantlib.pricingengines.swaption;

// imports — HullWhite, Swaption, Settlement, FdmStepConditionComposite,
// FdmHullWhiteOp, FdmAffineModelSwapInnerValue, FdmMesher, FdmScheme, etc.

public class FdHullWhiteSwaptionEngine extends Swaption.EngineImpl {

    private final HullWhite model_;
    private final int xGrid_;
    private final int tGrid_;
    private final int dampingSteps_;
    private final double invEps_;
    private final FdmSchemeDesc schemeDesc_;

    public FdHullWhiteSwaptionEngine(final HullWhite model) {
        this(model, 100, 50, 2, 1e-5, FdmSchemeDesc.Hundsdorfer());
    }

    public FdHullWhiteSwaptionEngine(final HullWhite model,
            final int xGrid, final int tGrid, final int dampingSteps,
            final double invEps, final FdmSchemeDesc schemeDesc) {
        this.model_ = model;
        this.xGrid_ = xGrid;
        this.tGrid_ = tGrid;
        this.dampingSteps_ = dampingSteps;
        this.invEps_ = invEps;
        this.schemeDesc_ = schemeDesc;
        model.addObserver(this);
    }

    @Override
    public void calculate() {
        // Mirror C++ fdhullwhiteswaptionengine.cpp lines ~30-110:
        //   - Cast arguments_ to Swaption.ArgumentsImpl
        //   - Extract underlying VanillaSwap + exerciseDates
        //   - Build FdmMesher (xGrid points, range based on HW vol + exercise time)
        //   - Build FdmHullWhiteOp (HW dynamics on the mesh)
        //   - Build inner value calculator FdmAffineModelSwapInnerValue
        //   - Build step condition composite (exercise dates + payoff)
        //   - Run FD scheme (FdmBackwardSolver or equivalent)
        //   - Read value at t=0 / x=0 (initial short rate)
        //   - results_.value = ...
    }
}
```

(Verify Java's `FdmSchemeDesc`, `FdmMesher`, `FdmHullWhiteOp` etc. actually exist with those names. If Java's FD scaffold uses different naming, adapt.)

- [ ] **Step 4: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2g-B
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS. If a Java FD scaffold piece is missing (A16 trigger), pause and report DONE_WITH_CONCERNS or BLOCKED. Don't improvise architecture — discuss first.

- [ ] **Step 5: Do NOT commit yet.** Probe + test land together AFTER WI-1 has merged to main.

### Task B.2: Wait for WI-1, rebase, generate probe + test

**Prerequisite:** WI-1 must have landed on main (controller signals).

- [ ] **Step 1: Rebase onto post-WI-1 main.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2g-B
git fetch origin
git rebase origin/main
```

If conflicts: A9 fires — pause and resolve.

- [ ] **Step 2: Write probe.** Create `migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp`:

```cpp
// Phase 2g WI-2: FdHullWhiteSwaptionEngine NPV fingerprint.
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

- [ ] **Step 3: Generate reference.**

```bash
./migration-harness/scripts/generate-references.sh fdhullwhiteswaptionengine_probe 2>&1 | tail -10
```

If submodule uninitialized in worktree, build via main worktree's pre-warmed cpp/build (Phase 2d/2e/2f precedent) and copy JSON back.

- [ ] **Step 4: Write Java test** at LOOSE tier:

```java
package org.jquantlib.testsuite.pricingengines.swaption;
// imports

public class FdHullWhiteSwaptionEngineTest {
    @Test
    public void testNPVMatchesCpp() {
        final var ref = ReferenceReader.load("fdhullwhiteswaptionengine.json");
        // ... mirror probe fixture: eval=2026-01-15, FlatForward 5%, Euribor3M, 5Y×5Y ATM payer, HW(0.1, 0.01), xGrid=100, tGrid=50, dampingSteps=2 ...
        final HullWhite hw = new HullWhite(ts, 0.1, 0.01);
        swaption.setPricingEngine(new FdHullWhiteSwaptionEngine(hw, 100, 50, 2));

        // LOOSE tier: FD discretization noise floor ~1e-6 for typical 1D grids.
        // If the C++ FD result and Java FD result agree at 1e-8, great. If
        // they only agree at 1e-6 due to small implementation differences in
        // FD operator scheduling, document inline and use 1e-6.
        assertEquals(ref.getDouble("fd_hw_swaption_npv"), swaption.NPV(), 1.0e-8);
    }
}
```

- [ ] **Step 5: Run.**

```bash
mvn -pl jquantlib test -Dtest='FdHullWhiteSwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
```

Expected: PASS at LOOSE tier. If 1e-8 fails but 1e-6 passes, document inline as a per-test exception (FD discretization noise floor) and proceed.

- [ ] **Step 6: Run full suite.**

```bash
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: count goes up by 1.

- [ ] **Step 7: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdHullWhiteSwaptionEngine.java \
        migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/fdhullwhiteswaptionengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdHullWhiteSwaptionEngineTest.java
# Add any FD scaffold extensions if you bundled them
git commit -s -m "stub(pricingengines.swaption): port FdHullWhiteSwaptionEngine + loose-tier fingerprint test (Phase 2g WI-2)"
git push origin phase-2g-B-fd-hullwhite
```

### Task B.3: Land worktree B to main

Same pattern as Task A.6. May need rebase if WI-3 landed first.

---

## Worktree C — WI-3 FdG2SwaptionEngine

**Worktree path:** `/Users/josemoya/eclipse-workspace/jquantlib-2g-C/`
**Branch:** `phase-2g-C-fd-g2`

### File structure for WI-3

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdG2SwaptionEngine.java` | Port v1.42.1 (173 LOC) |
| Modify (maybe) | `jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/...` | 2D FD scaffold extension if needed (A4 / A16 watch) |
| Create | `migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp` | C++ ref |
| Create | `migration-harness/references/pricingengines/swaption/fdg2swaptionengine.json` | reference data |
| Create | `jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdG2SwaptionEngineTest.java` | LOOSE-tier fingerprint (likely per-test exception) |

### Task C.1: Port FdG2SwaptionEngine

**Reference:** C++ `migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdg2swaptionengine.{hpp,cpp}` (173 LOC total).

- [ ] **Step 1: Read C++ source.**

```bash
cat migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdg2swaptionengine.hpp
sed -n '1,170p' migration-harness/cpp/quantlib/ql/pricingengines/swaption/fdg2swaptionengine.cpp
```

Key shape: similar to FdHullWhiteSwaptionEngine but 2D (x × y × time) using `FdmG2Op` + 2D mesher. Constructor: `FdG2SwaptionEngine(G2, int xGrid, int yGrid, int tGrid, int dampingSteps, double invEps, FdmSchemeDesc)`.

- [ ] **Step 2: Look at existing Java FD scaffold for 2D support.**

```bash
grep -rln "PdeSecondOrderParabolic\|DynamicPdeSecondOrderParabolic\|2[dD]" jquantlib/src/main/java/org/jquantlib/methods/finitedifferences/
```

Verify 2D operator + mesher exist. If a 2D-specific piece (`FdmG2Op` analogue) is missing, A16 trigger — pause.

- [ ] **Step 3: Port to Java.** Same shape as FdHullWhiteSwaptionEngine but 2D:

```java
package org.jquantlib.pricingengines.swaption;
// imports — G2, Swaption, Settlement, 2D mesher, FdmG2Op, etc.

public class FdG2SwaptionEngine extends Swaption.EngineImpl {

    private final G2 model_;
    private final int xGrid_, yGrid_, tGrid_, dampingSteps_;
    private final double invEps_;
    private final FdmSchemeDesc schemeDesc_;

    public FdG2SwaptionEngine(final G2 model) {
        this(model, 50, 50, 50, 2, 1e-5, FdmSchemeDesc.Hundsdorfer());
    }

    public FdG2SwaptionEngine(final G2 model, final int xGrid, final int yGrid,
            final int tGrid, final int dampingSteps, final double invEps,
            final FdmSchemeDesc schemeDesc) {
        // ... store fields, observer wiring
    }

    @Override
    public void calculate() {
        // Mirror C++ fdg2swaptionengine.cpp lines ~30-130:
        //   - Cast arguments_ to Swaption.ArgumentsImpl
        //   - Build 2D FdmMesher (xGrid×yGrid points)
        //   - Build FdmG2Op (G2 dynamics on the mesh)
        //   - Build inner value + step condition composite
        //   - Run 2D FD scheme
        //   - Read value at t=0 / (x=0, y=0)
    }
}
```

- [ ] **Step 4: Compile.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2g-C
mvn -pl jquantlib test-compile -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Do NOT commit yet — probe + test land after WI-1.**

### Task C.2: Wait for WI-1, rebase, generate probe + test

Same pattern as Task B.2 but for G2 + 2D FD.

- [ ] **Step 1: Rebase onto post-WI-1 main.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-2g-C
git fetch origin
git rebase origin/main
```

- [ ] **Step 2: Write probe** — same fixture as FdHullWhite probe but G2 model + 2D FD:

```cpp
// migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp
auto g2 = ext::make_shared<G2>(ts, 0.1, 0.01, 0.1, 0.005, -0.5);
swaption.setPricingEngine(ext::make_shared<FdG2SwaptionEngine>(g2, 50, 50, 50, 2));
out["fixture"] = {/* same as FdHullWhite + g2 params + xGrid=50, yGrid=50, tGrid=50, dampingSteps=2 */};
out["fd_g2_swaption_npv"] = swaption.NPV();
write_probe_output("fdg2swaptionengine.json", out);
```

- [ ] **Step 3: Generate reference + Java test** at LOOSE tier:

```java
// 2D FD has lower convergence rate than 1D — expect noise floor ~1e-5.
// Per-test exception likely needed. Try 1e-8 first; if it fails, document
// inline justification ("2D FD noise floor — see Phase 2g design §4 loose-tier
// expectations") and use 1e-5.
assertEquals(ref.getDouble("fd_g2_swaption_npv"), swaption.NPV(), 1.0e-8);
```

If 1e-8 fails: change to 1e-5 with inline comment. **Don't loosen further without justification.**

- [ ] **Step 4: Run.**

```bash
mvn -pl jquantlib test -Dtest='FdG2SwaptionEngineTest' 2>&1 | grep -E "Tests run|FAILED|ERROR" | head
mvn -pl jquantlib test 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: PASS at chosen tier.

- [ ] **Step 5: Commit.**

```bash
git add jquantlib/src/main/java/org/jquantlib/pricingengines/swaption/FdG2SwaptionEngine.java \
        migration-harness/cpp/probes/pricingengines/swaption/fdg2swaptionengine_probe.cpp \
        migration-harness/references/pricingengines/swaption/fdg2swaptionengine.json \
        jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/FdG2SwaptionEngineTest.java
git commit -s -m "stub(pricingengines.swaption): port FdG2SwaptionEngine + loose-tier fingerprint test (Phase 2g WI-3)"
git push origin phase-2g-C-fd-g2
```

### Task C.3: Land worktree C to main

Same pattern as Task A.6 / B.3.

---

## Layer 2 — Completion doc + tag

### Task L2.1: Write `phase2g-completion.md`

- [ ] **Step 1: Gather final state.**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
git log --oneline debedf9..HEAD
```

Expected: `Tests run: ~677-678`; `0 stubs`; ~10-15 commits since Phase 2f tip.

- [ ] **Step 2: Write the completion doc** following Phase 2f's structure:
  - Header (date, predecessor tag, what's in)
  - Per-WI summary with commit hashes
  - **Tier-promotion disclosure** (per design §7.8): which tests promoted from LOOSE to TIGHT (Jamshidian + G2.swaption expected); whether NCCV tier promotion succeeded; opportunistic tree promotions
  - **Alignment-fix disclosure** (per design §7.9): what changes the FloatingRateCoupon.fixingDays fix surfaced; probe regeneration count from Brent fix
  - Final scanner state (still 0)
  - Test suite final state with delta table
  - Deviations from plan (any A13/A15/A16 firings, any tier compromises)
  - Phase 2h seed list with explicit transcendental library port option

- [ ] **Step 3: Commit.**

```bash
git add docs/migration/phase2g-completion.md
git commit -s -m "docs(migration): Phase 2g completion report"
git push origin main
```

### Task L2.2: Tag and push

```bash
git tag jquantlib-phase2g-complete
git push origin jquantlib-phase2g-complete
git tag -l 'jquantlib-phase2*'
```

Expected: 7 tags (`phase1` through `phase2g`).

### Task L2.3: Worktree cleanup

```bash
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2g-A 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2g-B 2>&1
git worktree remove --force /Users/josemoya/eclipse-workspace/jquantlib-2g-C 2>&1
git worktree prune
git worktree list

git branch -D phase-2g-A-brent-aligns phase-2g-B-fd-hullwhite phase-2g-C-fd-g2 2>&1 || true
git push origin --delete phase-2g-A-brent-aligns phase-2g-B-fd-hullwhite phase-2g-C-fd-g2 2>&1
```

If any `remove --force` fails (Phase 2c-2f precedent), fall back to `rm -rf` then `git worktree prune`.

### Task L2.4: Update memory

Update `/Users/josemoya/.claude/projects/-Users-josemoya-eclipse-workspace-jquantlib/memory/project_jquantlib_migration.md` description and body with Phase 2g milestone + tier promotion outcomes + which align fixes landed.

Also update `MEMORY.md` index entry.

### Task L2.5: Final verification

```bash
git status
git log --oneline -10
git tag -l 'jquantlib-phase2*'
git worktree list
git branch -a | grep '2g' || echo "no 2g branches"
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
python3 tools/stub-scanner/scan_stubs.py
```

Expected: clean state, `phase2g-complete` tag exists, no 2g branches, tests `Failures: 0, Errors: 0, Skipped: 22`, scanner `0 stubs`.

---

## Self-Review notes

- All 11 design exit criteria mapped to tasks: §7.1 (mvn green) → final verification L2.5; §7.2 (test delta) → B.2 + C.2; §7.3 (Skipped: 22) → final verification; §7.4 (scanner WIP=0) → final verification; §7.5 (worktrees gone) → L2.3; §7.6 (probes regenerate) → A.2 + B.2 + C.2; §7.7 (loose-tier inline justification) → enforced per task; §7.8 (tier-promotion disclosure) → A.3 + L2.1; §7.9 (alignment-fix disclosure) → A.4 + A.5 + L2.1; §7.10 (completion doc) → L2.1; §7.11 (tag pushed + memory updated) → L2.2 + L2.4.
- All 6 design pause triggers covered: A4 → B.1 step 5 + C.1 step 4 (FD scaffold extension if needed); A6 disabled; A9 → A.6/B.3/C.3 rebase paths; A13 → A.3 step 3 (NCCV tier compromise); A15 → A.2 step 3 (Brent fix surfaces hidden bug); A16 → B.1 step 5 + C.1 step 4 (FD scaffold gap escalation).
- The plan does not invent classes/methods that don't exist. Where Java-side API shape isn't pinned by the existing code base, the plan calls out "verify against actual" with the search command.
- All commit messages follow the `<kind>(<pkg>): <verb>` convention with `(Phase 2g WI-N)` suffix.
- Per-fix commits (Brent / VanillaSwap.setupArguments / FloatingRateCoupon.fixingDays) per CLAUDE §4.2; tier promotion commits separate.
- WI-2 + WI-3 dispatch port code in parallel with WI-1 but final commits gate on WI-1 land — explicit in §3 and the L1b prerequisite text.

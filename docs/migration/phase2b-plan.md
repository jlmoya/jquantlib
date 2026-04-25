# Phase 2b Implementation Plan — JQuantLib Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the four drift-fix / incremental-port work items from `docs/migration/phase2b-design.md`: WI-1 HestonProcess `QuadraticExponentialMartingale`, WI-2 Simplex 1D-dim fix + un-skip OptimizerTest, WI-3 Vasicek family `Parameter`-ref sweep across all four one-factor models, WI-4 SABR transformation fix-or-carve (time-boxed). End state: scanner reports 2 `work_in_progress` (CapHelper, G2 — Phase-2c seeds, unchanged), 0 `not_implemented`, 0 `numerical_suspect`; tag `jquantlib-phase2b-complete`.

**Architecture:** Same as Phase 2a — direct commits to `main`, TDD per stub, cross-validated against C++ QuantLib v1.42.1 via `migration-harness/` probes. Layer ordering per design P2B-5 (ascending complexity, SABR last with explicit time-box). Pause triggers per design §5: A6 disabled, A4 redirected to SABR carve gate, new A8 for Vasicek-pattern alignment risk.

**Tech Stack:** Java 11 / Maven / JUnit 4 (existing); C++17 / CMake / QuantLib v1.42.1 pinned via submodule; Python 3 for scanner tooling; nlohmann/json for probe output.

---

## Overview

| Layer | Description | Expected commits |
|-------|-------------|------------------|
| L0 | Pre-flight: confirm baseline, snapshot scanner state | 0 |
| L1 | WI-1 HestonProcess `QuadraticExponentialMartingale` | 1 |
| L2 | WI-2 Simplex 1D-dim fix + un-skip `OptimizerTest` | 1–2 |
| L3 | WI-3 Vasicek family `Parameter`-ref sweep (4 models) | 4–6 |
| L4 | WI-4 SABR fix-or-carve (time-boxed) | 0–4 |
| L5 | Completion doc + tag | 1 commit + tag |

**Non-goals reminder (design §2.2):** no new top-level packages; CapHelper/G2/remaining-Heston-schemes/LM-analytic-Jacobian deferred to Phase 2c; no scope creep on SABR.

**Git discipline (inherited):** every commit signed off with `-s`; no `Co-authored-by` trailer; unsigned (no GPG/SSH); push direct to `origin main` after each commit is verified green. Commit messages follow `<kind>(<pkg>): <verb> ...` where `<kind>` is `stub`, `align`, `infra`, `chore`, `docs`, or `test`.

---

## Layer 0 — Pre-flight (no commits)

### Task 0.1: Confirm `main` is clean and green

- [ ] **Step 1:** Verify branch and clean working tree.

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git status
git branch --show-current
```

Expected: branch `main`, working tree clean. If not clean, stop and ask the user before any change.

- [ ] **Step 2:** Run baseline test suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run: [0-9]+,"
```

Expected: `Tests run: 626, Failures: 0, Errors: 0, Skipped: 25`. Note this number — Phase 2b's exit criterion expects ≥ 633 (fix path) or ≥ 630 (carve path).

- [ ] **Step 3:** Snapshot scanner state.

```bash
python3 tools/stub-scanner/scan_stubs.py
```

Expected printed tail:
```
  work_in_progress: 2
```

Both entries should be CapHelper and G2:
```bash
grep '"id"' docs/migration/stub-inventory.json
```

Expected: 2 entries — `model.shortrate.calibrationhelpers.CapHelper#line23` and `model.shortrate.twofactormodels.G2#generateArguments`. If anything else, stop and investigate.

- [ ] **Step 4:** Verify the harness is functional.

```bash
./migration-harness/verify-harness.sh 2>&1 | tail -3
```

Expected: exits 0. If it fails, fix the harness before any work item.

- [ ] **Step 5:** Confirm the C++ submodule pin.

```bash
(cd migration-harness/cpp/quantlib && git rev-parse HEAD)
```

Expected: `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`.

---

## Layer 1 — WI-1 HestonProcess `QuadraticExponentialMartingale`

Add a `QuadraticExponentialMartingale` enum value to `HestonProcess.Discretization` and the martingale-correction `k0` recomputation inside the existing QE branch. C++ ref: `migration-harness/cpp/quantlib/ql/processes/hestonprocess.cpp` lines 461–516 (the QE+QEM combined `case` block).

### Task 1.1: Extend the Heston probe with QEM cases (commit 1, part A)

**Files:**
- Modify: `migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp`

- [ ] **Step 1:** Open `hestonprocess_qe_probe.cpp` and locate `runAndEmit`. The function constructs a `HestonProcess` with a hard-coded `HestonProcess::QuadraticExponential` discretization in its body. Refactor `runAndEmit` to take the discretization as a parameter on `LmdifCase`-equivalent struct (rename the existing `QECase` struct to keep backwards compat, but add a `HestonProcess::Discretization disc` field).

```cpp
struct QECase {
    std::string name;
    HestonProcess::Discretization disc;  // NEW
    Real r, q, s0;
    Real v0, kappa, theta, sigma, rho;
    Real t0, dt;
    Real x00, x01;
    Real dw0, dw1;
};
```

In `runAndEmit`, replace the hard-coded `HestonProcess::QuadraticExponential` with `tc.disc`.

- [ ] **Step 2:** Update the existing 3 cases to set `tc.disc = HestonProcess::QuadraticExponential;`. Verify the file still compiles.

- [ ] **Step 3:** Add 2 new QEM cases at the bottom of `main()`, mirroring the 2 QE cases that exercise the two sub-branches:

```cpp
// --- Case 4: QEM, psi-low sub-branch (martingale correction A < 1/(2*a)) ---
{
    QECase tc;
    tc.name = "qem_psiLow_centralVol";
    tc.disc = HestonProcess::QuadraticExponentialMartingale;
    tc.r = 0.05; tc.q = 0.02; tc.s0 = 100.0;
    tc.v0 = 0.04; tc.kappa = 2.0; tc.theta = 0.04;
    tc.sigma = 0.5; tc.rho = -0.7;
    tc.t0 = 0.5; tc.dt = 0.1;
    tc.x00 = 100.0; tc.x01 = 0.04;
    tc.dw0 = 0.3; tc.dw1 = -0.2;
    runAndEmit(out, tc);
}

// --- Case 5: QEM, psi-high sub-branch (martingale correction A < beta) ---
{
    QECase tc;
    tc.name = "qem_psiHigh_lowInitV";
    tc.disc = HestonProcess::QuadraticExponentialMartingale;
    tc.r = 0.03; tc.q = 0.0; tc.s0 = 100.0;
    tc.v0 = 0.005; tc.kappa = 0.3; tc.theta = 0.04;
    tc.sigma = 1.0; tc.rho = -0.9;
    tc.t0 = 0.0; tc.dt = 0.25;
    tc.x00 = 100.0; tc.x01 = 0.005;
    tc.dw0 = 0.5; tc.dw1 = 1.5;
    runAndEmit(out, tc);
}
```

- [ ] **Step 4:** Build + run the probe to capture references.

```bash
./migration-harness/generate-references.sh hestonprocess_qe_probe 2>&1 | tail -5
```

Expected: builds and runs cleanly. Verify the JSON gained 2 cases:

```bash
grep '"name"' migration-harness/references/processes/hestonprocess_qe.json
```

Expected: 5 names (`qe_psiLow_centralVol`, `qe_psiHigh_lowInitV`, `qe_psiHigh_zeroVarianceDraw`, `qem_psiLow_centralVol`, `qem_psiHigh_lowInitV`).

### Task 1.2: Add Java QEM tests (commit 1, part B)

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java`

- [ ] **Step 1:** The existing `runCase` method always builds the process with `HestonProcess.Discretization.QuadraticExponential`. Refactor to read the discretization from the test invocation. Easiest: add an overload `runCase(String, HestonProcess.Discretization)` and have the existing tests call `runCase(name, HestonProcess.Discretization.QuadraticExponential)`.

```java
private static void runCase(final String name) {
    runCase(name, HestonProcess.Discretization.QuadraticExponential);
}

private static void runCase(final String name,
                            final HestonProcess.Discretization disc) {
    // existing body, but pass `disc` to the HestonProcess ctor instead of
    // hard-coded QuadraticExponential
}
```

- [ ] **Step 2:** Add 2 new tests:

```java
@Test
public void qem_psiLow_centralVol() {
    runCase("qem_psiLow_centralVol",
            HestonProcess.Discretization.QuadraticExponentialMartingale);
}

@Test
public void qem_psiHigh_lowInitV() {
    runCase("qem_psiHigh_lowInitV",
            HestonProcess.Discretization.QuadraticExponentialMartingale);
}
```

- [ ] **Step 3:** Confirm the tests fail (the enum value doesn't exist yet).

```bash
(cd jquantlib && mvn test -Dtest=HestonProcessTest) 2>&1 | tail -10
```

Expected: compile error `cannot find symbol: variable QuadraticExponentialMartingale`. That is the correct red state.

### Task 1.3: Port the QEM branch (commit 1, part C)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java`

- [ ] **Step 1:** Add `QuadraticExponentialMartingale` to the `Discretization` enum:

```java
public enum Discretization {
    PartialTruncation, FullTruncation, Reflection,
    QuadraticExponential, QuadraticExponentialMartingale
}
```

- [ ] **Step 2:** In `evolve()`, change the existing case label from a single match to a fall-through pair, mirroring C++ lines 461–462 (`case QuadraticExponential: case QuadraticExponentialMartingale:`):

```java
case QuadraticExponential:
case QuadraticExponentialMartingale: {
    // ... existing QE body ...
}
```

- [ ] **Step 3:** Inside that block, add the `A` constant and the QEM-only `k0` overrides. Locate the existing block. Replace `final double k0 = ...;` with a non-final declaration so it can be overwritten:

```java
double k0 = -rhov_ * kappav_ * thetav_ * dt / sigmav_;
final double k1 = g1 * dt * (kappav_ * rhov_ / sigmav_ - 0.5) - rhov_ / sigmav_;
final double k2 = g2 * dt * (kappav_ * rhov_ / sigmav_ - 0.5) + rhov_ / sigmav_;
final double k3 = g1 * dt * (1 - rhov_ * rhov_);
final double k4 = g2 * dt * (1 - rhov_ * rhov_);
final double A  = k2 + 0.5 * k4;  // NEW
```

- [ ] **Step 4:** In the `if (psi < 1.5)` sub-branch, after computing `b2`, `b`, `a`, add the QEM correction:

```java
if (psi < 1.5) {
    final double b2 = 2 / psi - 1 + Math.sqrt(2 / psi * (2 / psi - 1));
    final double b = Math.sqrt(b2);
    final double a = m / (1 + b2);

    if (discretization_ == Discretization.QuadraticExponentialMartingale) {
        // martingale correction; mirrors hestonprocess.cpp 488-493
        QL.require(A < 1 / (2 * a), "illegal value");
        k0 = -A * b2 * a / (1 - 2 * A * a)
                + 0.5 * Math.log(1 - 2 * A * a)
                - (k1 + 0.5 * k3) * x01;
    }
    retVal[1] = a * (b + dw1) * (b + dw1);
}
```

- [ ] **Step 5:** In the `else` (psi ≥ 1.5) sub-branch, after computing `pp`, `beta`, `u`, add the QEM correction:

```java
} else {
    final double pp = (psi - 1) / (psi + 1);
    final double beta = (1 - pp) / m;
    final double u = new CumulativeNormalDistribution().op(dw1);

    if (discretization_ == Discretization.QuadraticExponentialMartingale) {
        // martingale correction; mirrors hestonprocess.cpp 502-506
        QL.require(A < beta, "illegal value");
        k0 = -Math.log(pp + beta * (1 - pp) / (beta - A))
                - (k1 + 0.5 * k3) * x01;
    }
    retVal[1] = (u <= pp) ? 0.0 : Math.log((1 - pp) / (1 - u)) / beta;
}
```

- [ ] **Step 6:** Add `import org.jquantlib.QL;` if not already present.

- [ ] **Step 7:** Run the QEM tests + the existing QE tests.

```bash
(cd jquantlib && mvn test -Dtest=HestonProcessTest) 2>&1 | tail -5
```

Expected: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` (3 QE + 2 QEM all green).

- [ ] **Step 8:** Run the full suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 628, Failures: 0, Errors: 0, Skipped: 25` (626 + 2).

- [ ] **Step 9:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/processes/HestonProcess.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/processes/HestonProcessTest.java \
        migration-harness/cpp/probes/processes/hestonprocess_qe_probe.cpp \
        migration-harness/references/processes/hestonprocess_qe.json
git commit -s -m "$(cat <<'EOF'
stub(processes): port HestonProcess QuadraticExponentialMartingale

Adds the QEM enum value and martingale-correction k0 recomputation in
both QE sub-branches (psi<1.5 and psi>=1.5). Direct port of
ql/processes/hestonprocess.cpp lines 461-516, sharing the QE case
block via fall-through to mirror the C++ structure.

QEM is C++'s default HestonProcess discretization, so absent it Java's
default behavior (FullTruncation) silently differs from C++. Test
coverage extended to 5 cases (3 QE + 2 QEM, one per sub-branch);
hestonprocess_qe probe gains 2 reference outputs.

Test count: 626 -> 628.
EOF
)"
```

Push:

```bash
git push origin main
```

---

## Layer 2 — WI-2 Simplex 1D-dim fix + un-skip OptimizerTest

The carved test `OptimizerTest#testOptimizers` fails with `IllegalArgumentException: Independent variable must be 1 dimensional` from inside Simplex's vertex construction. Per `docs/migration/phase2a-carveouts.md::WI-2-carveout-simplex`, this is a Simplex implementation issue, not LM.

### Task 2.1: Reproduce + diagnose (no commit)

- [ ] **Step 1:** Run the carved test (with `@Ignore` removed temporarily — do NOT commit yet) to capture the full stack:

```bash
# DO NOT COMMIT - diagnosis only
git stash
sed -i '' 's|^    @Ignore$|    // @Ignore-temp|' jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/OptimizerTest.java
(cd jquantlib && mvn test -Dtest=OptimizerTest) 2>&1 | grep -A 30 "IllegalArgumentException"
```

Inspect the stack trace. The exception will name the throw site — it is most likely either inside `org.jquantlib.math.matrixutilities.Array` (Array dimension check) or `org.jquantlib.math.optimization.Constraint.update` (when called from `Simplex.minimize` line 152 with a 1D direction array).

- [ ] **Step 2:** Restore the file, then read the diagnosis:

```bash
git checkout jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/OptimizerTest.java
git stash pop  # if you stashed anything
```

- [ ] **Step 3:** Compare with C++. Read `migration-harness/cpp/quantlib/ql/math/optimization/simplex.cpp` `Simplex::minimize`, paying attention to vertex-array construction (look for `vertices_`, `Array(n+1)`, and how `direction` is constructed). Diff the construction shape: C++ allocates `Array direction(n, 0.0)` and assigns `direction[i] = 1.0`; Java does the same in `Simplex.java:150-151`. The bug is most likely upstream — `OptimizerTest.java:91-92` builds `final Array initialValue = new Array(0); initialValue.add(-100.0);` which produces a 1D Array via `add()`. Trace whether `Array.add(double)` mutates the size correctly or returns a new Array.

```bash
grep -n "public Array add\b\|public Array addAssign" jquantlib/src/main/java/org/jquantlib/math/matrixutilities/Array.java
```

Read the `add()` method. If it returns a new Array (immutable-style) instead of mutating, the test's `initialValue` stays size 0, and Simplex's `vertices_.add(new Array(x_))` copies a size-0 Array; then `for (i = 0; i < n; i++)` (where n=0) never runs, and the failure is somewhere else.

- [ ] **Step 4:** Form a single-sentence diagnosis. The fix decision tree:
  - **Diagnosis A:** `Array.add(double)` is non-mutating → the test's initialValue stays size 0 → `n=0`, vertex loops are no-ops, and the exception comes from a downstream consumer that requires size ≥ 1. Fix in the test: use `new Array(new double[]{ -100.0 })`.
  - **Diagnosis B:** `Array.add(double)` mutates correctly → Simplex's vertex/direction construction fails for `n=1`. Fix in `Simplex.java`.
  - **Diagnosis C:** `Constraint.update` doesn't accept 1D arrays. Fix in `Constraint` or `NoConstraint`.

Whichever it is, the fix should be ≤ 20 LOC. If the fix appears to need ≥ 100 LOC or new infrastructure, A4 fires → re-gate with @Ignore + carve to Phase 2c with diagnosis (analogous to WI-4 carve path; document in `phase2a-carveouts.md` rather than 2c since 2b just leaves it).

### Task 2.2: Apply the fix (commit 1)

**Files (depending on diagnosis):**
- Modify: one of `Simplex.java`, `Array.java`, or `OptimizerTest.java` per Step 3 of Task 2.1.

- [ ] **Step 1:** Apply the smallest fix that resolves the failure. Show the diff (`git diff`), confirm the fix file matches the diagnosis from Task 2.1.

- [ ] **Step 2:** Run only OptimizerTest with `@Ignore` still in place:

```bash
(cd jquantlib && mvn test -Dtest=OptimizerTest) 2>&1 | tail -5
```

Expected: passes the previously-failing line. Test still skipped — but compile + dependent paths verified.

- [ ] **Step 3:** Run the full suite to verify no regression.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: same as baseline (628 + 0 = 628). If any previously-passing test now fails, revert and re-diagnose.

- [ ] **Step 4:** Commit.

```bash
git add <fix-file>
git commit -s -m "$(cat <<'EOF'
align(<pkg>): <diagnosis-summary> for Simplex 1D problems

Carved in Phase-2a as WI-2-carveout-simplex (OptimizerTest fails with
"Independent variable must be 1 dimensional" before LM even runs).
Root cause: <one-sentence diagnosis from Task 2.1 Step 3>.

Fix: <one-sentence fix description>. Existing tests unchanged; the
un-skip lands separately in the next commit so the fix and the test
re-enable can be diffed independently.
EOF
)"
```

### Task 2.3: Un-skip OptimizerTest + uncomment LM entry (commit 2)

**Files:**
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/OptimizerTest.java`
- Modify: `docs/migration/phase2a-carveouts.md`

- [ ] **Step 1:** Remove the `@Ignore` annotation and the carveout pointer comment block immediately above `testOptimizers` (lines 47-52 region). Replace with a single-line acknowledgement of the fix:

```java
// Un-skipped in Phase 2b WI-2 (commit <hash from Task 2.2>); the
// previous Simplex 1D-dim failure is fixed.
@Test
public void testOptimizers() {
```

- [ ] **Step 2:** Uncomment the `LevenbergMarquardt` entry in the active method list at line 104:

```java
final OptimizationMethodType optimizationMethodTypes[] = {
    OptimizationMethodType.simplex,
    OptimizationMethodType.levenbergMarquardt
};
```

- [ ] **Step 3:** Run OptimizerTest:

```bash
(cd jquantlib && mvn test -Dtest=OptimizerTest) 2>&1 | tail -10
```

Expected: passes. The test runs both Simplex and LM against the parabolic cost function and compares converged params to the analytic minimum `xMin = -b/(2a)`.

If the test fails on the LM portion (e.g., a tolerance mismatch from cumulative FMA drift), the LM-side tolerance inside the test loop may need an inline justification comment — but do NOT loosen `endCriteria.functionEpsilon_` since that's the contract under test.

- [ ] **Step 4:** Run the full suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 628, Failures: 0, Errors: 0, Skipped: 24` (628 unchanged because OptimizerTest is one test method — Skipped drops by 1).

- [ ] **Step 5:** Update the carveout entry. In `docs/migration/phase2a-carveouts.md`, change the disposition section of `WI-2-carveout-simplex` to:

```markdown
**Disposition:**
Fixed in Phase 2b WI-2 (commit <hash from Task 2.2>). OptimizerTest
un-skipped in commit <hash from this task>. Both Simplex and LM now
run in the active method matrix.
```

- [ ] **Step 6:** Commit.

```bash
git add jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/OptimizerTest.java \
        docs/migration/phase2a-carveouts.md
git commit -s -m "$(cat <<'EOF'
test(math.optimization): un-skip OptimizerTest after Simplex 1D fix

Removes the @Ignore from testOptimizers and uncomments the
LevenbergMarquardt entry in line 104's optimization-method matrix
so both Simplex and LM run against the 1D parabolic cost function.
WI-2-carveout-simplex disposition updated.

Skipped: 25 -> 24.
EOF
)"
git push origin main
```

---

## Layer 3 — WI-3 Vasicek family `Parameter`-ref sweep

Apply the same indirection pattern to all four one-factor models so member parameter accessors read through `arguments_.get(i)` rather than holding a stale copy. Per design §3.3, no abstract extract — each model gets the pattern applied locally.

The shared design: each model's `protected Parameter a_;` field becomes a getter `protected Parameter a() { return arguments_.get(0); }`. All internal usages of `a_.get(0.0)` become `a().get(0.0)`. The constructor sets `arguments_.set(0, new ConstantParameter(a, ...))` instead of touching `a_` directly.

### Task 3.1: Pattern reference + Vasicek (commit 1)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/Vasicek.java`
- Create: `migration-harness/cpp/probes/model/shortrate/vasicek_calibration_probe.cpp`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/VasicekCalibrationTest.java`

Vasicek is the pattern reference — every other model in this layer follows its shape.

- [ ] **Step 1:** Read `Vasicek.java` lines 50-80. The current structure:

```java
protected double r0_;
protected Parameter a_;
protected Parameter b_;
protected Parameter sigma_;
protected Parameter lambda_;

public Vasicek(...) {
    super(4);
    this.r0_ = r0;
    // (Phase 2a left this without the dead arguments_.get(i) ladder)
    this.a_ = new ConstantParameter(a, new PositiveConstraint());
    this.b_ = new ConstantParameter(b, new NoConstraint());
    this.sigma_ = new ConstantParameter(sigma, new PositiveConstraint());
    this.lambda_ = new ConstantParameter(lambda, new NoConstraint());
}
```

- [ ] **Step 2:** Replace the four `protected Parameter` fields with accessor methods that read through `arguments_`. Delete the field declarations entirely. Add:

```java
protected Parameter a()      { return arguments_.get(0); }
protected Parameter b()      { return arguments_.get(1); }
protected Parameter sigma()  { return arguments_.get(2); }
protected Parameter lambda() { return arguments_.get(3); }
```

These overload (replace) the existing zero-arg accessors at the bottom of the class — note that the existing `protected double a()` returns `a_.get(0.0)` (the scalar evaluation). To avoid breaking callers, change the new accessors' names to avoid clash:

```java
// Internal Parameter accessors (Phase 2b WI-3 indirection).
private Parameter aParam()      { return arguments_.get(0); }
private Parameter bParam()      { return arguments_.get(1); }
private Parameter sigmaParam()  { return arguments_.get(2); }
private Parameter lambdaParam() { return arguments_.get(3); }

// Existing scalar accessors now route through the indirection.
protected double a()      { return aParam().get(0.0); }
protected double b()      { return bParam().get(0.0); }
protected double sigma()  { return sigmaParam().get(0.0); }
protected double lambda() { return lambdaParam().get(0.0); }
```

- [ ] **Step 3:** Rewrite the constructor to populate `arguments_` directly via `set`:

```java
public Vasicek(/* @Rate */ final double r0, final double a, final double b,
               final double sigma, final double lambda) {
    super(4);
    this.r0_ = r0;
    arguments_.set(0, new ConstantParameter(a, new PositiveConstraint()));
    arguments_.set(1, new ConstantParameter(b, new NoConstraint()));
    arguments_.set(2, new ConstantParameter(sigma, new PositiveConstraint()));
    arguments_.set(3, new ConstantParameter(lambda, new NoConstraint()));
}
```

- [ ] **Step 4:** Search for any remaining direct `a_`/`b_`/`sigma_`/`lambda_` reads in the file:

```bash
grep -n "a_\.\|b_\.\|sigma_\.\|lambda_\." jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/Vasicek.java
```

Expected: no matches (the field-reads are gone). If any remain, replace with the accessor form (e.g., `a_.get(0.0)` → `a()`).

- [ ] **Step 5:** Compile.

```bash
(cd jquantlib && mvn compile) 2>&1 | tail -5
```

Expected: BUILD SUCCESS. If `arguments_` is private in `OneFactorAffineModel`, change it to `protected` in that file (necessary for the indirection pattern across the family); do this as part of this same commit and note in the commit message.

- [ ] **Step 6:** Build the C++ calibration probe:

```cpp
// migration-harness/cpp/probes/model/shortrate/vasicek_calibration_probe.cpp
// Reference values for Vasicek calibration round-trip via OneFactor
// affine calibration against synthetic discount-bond prices. Verifies
// that recovered params match the originals after LM converges.

#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/vasicek.hpp>
#include <ql/models/shortrate/calibrationhelpers/swaptionhelper.hpp>
#include <ql/quotes/simplequote.hpp>
#include "../../common.hpp"

#include <vector>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/vasicek_calibration", QL_VERSION,
                        "vasicek_calibration_probe");

    // Single round-trip case: build Vasicek with known params, evaluate
    // a few discountBondOption values at fixed strikes/maturities, capture
    // those as the "to-recover" reference points. (A full calibrate()
    // round-trip needs SwaptionHelpers which add boilerplate; the
    // discountBondOption fingerprint is sufficient to verify the
    // arguments_-indirection wiring works through the Parameter system.)
    {
        const Real r0 = 0.05, a = 0.1, b = 0.05, sigma = 0.01, lambda = 0.0;
        Vasicek model(r0, a, b, sigma, lambda);

        // Sample at three (strike, maturity, bondMaturity) tuples that
        // exercise the interesting branches of discountBondOption.
        std::vector<std::tuple<Real, Time, Time>> samples = {
            {0.95, 0.5, 1.0},
            {1.00, 1.0, 2.0},
            {1.05, 2.0, 5.0}
        };

        json sampleArr = json::array();
        for (auto& s : samples) {
            const Real strike = std::get<0>(s);
            const Time mat   = std::get<1>(s);
            const Time bMat  = std::get<2>(s);
            const Real call = model.discountBondOption(Option::Call, strike, mat, bMat);
            const Real put  = model.discountBondOption(Option::Put,  strike, mat, bMat);
            sampleArr.push_back({{"strike", strike}, {"maturity", mat},
                                 {"bondMaturity", bMat},
                                 {"call", call}, {"put", put}});
        }

        out.addCase("vasicek_round_trip",
            json{{"r0", r0}, {"a", a}, {"b", b},
                 {"sigma", sigma}, {"lambda", lambda}},
            json{{"samples", sampleArr}});
    }

    out.write();
    return 0;
}
```

Note: a calibration round-trip is the design's preferred test, but it requires SwaptionHelper instances and a synthetic yield curve — substantial harness boilerplate. The `discountBondOption` fingerprint above is a leaner alternative that exercises the same `arguments_`-routed `a()`/`b()`/`sigma()` accessors during pricing; if the indirection is wrong, the fingerprint values diverge from C++. If a fuller calibrate() round-trip is desired later, extend the probe.

- [ ] **Step 7:** Build and run the probe.

```bash
mkdir -p migration-harness/cpp/probes/model/shortrate
./migration-harness/generate-references.sh vasicek_calibration_probe 2>&1 | tail -5
```

Expected: builds and runs cleanly. Verify the JSON:

```bash
cat migration-harness/references/model/shortrate/vasicek_calibration.json | head -30
```

- [ ] **Step 8:** Write the failing Java test:

```java
// jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/VasicekCalibrationTest.java
package org.jquantlib.testsuite.model.shortrate;

import org.jquantlib.instruments.Option;
import org.jquantlib.model.shortrate.onefactormodels.Vasicek;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class VasicekCalibrationTest {

    @Test
    public void roundTripFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load("model/shortrate/vasicek_calibration");
        final Case c = reader.getCase("vasicek_round_trip");
        final JSONObject in = c.inputs();
        final Vasicek model = new Vasicek(
                in.getDouble("r0"), in.getDouble("a"), in.getDouble("b"),
                in.getDouble("sigma"), in.getDouble("lambda"));

        final JSONObject exp = (JSONObject) c.expectedRaw();
        final JSONArray samples = exp.getJSONArray("samples");
        for (int i = 0; i < samples.length(); i++) {
            final JSONObject s = samples.getJSONObject(i);
            final double strike = s.getDouble("strike");
            final double mat = s.getDouble("maturity");
            final double bMat = s.getDouble("bondMaturity");
            final double expCall = s.getDouble("call");
            final double expPut = s.getDouble("put");
            final double gotCall = model.discountBondOption(Option.Type.Call, strike, mat, bMat);
            final double gotPut = model.discountBondOption(Option.Type.Put, strike, mat, bMat);
            if (!Tolerance.tight(gotCall, expCall)) {
                fail("call[" + i + "]: exp=" + expCall + " got=" + gotCall);
            }
            if (!Tolerance.tight(gotPut, expPut)) {
                fail("put[" + i + "]: exp=" + expPut + " got=" + gotPut);
            }
        }
    }
}
```

- [ ] **Step 9:** Run the test.

```bash
(cd jquantlib && mvn test -Dtest=VasicekCalibrationTest) 2>&1 | tail -10
```

Expected: passes at tight tier. If it fails, the indirection wasn't applied uniformly — re-check Step 4's grep returns zero matches. If a Java method name (e.g., `discountBondOption`, or its accessor visibility) differs from the C++ test's expectations, adjust accordingly.

- [ ] **Step 10:** Run the full suite.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 629, Failures: 0, Errors: 0, Skipped: 24` (628 + 1).

If any previously-passing test in `model.shortrate` now fails, **A8 fires** — pause and ask the user whether to revise the indirection, carve Vasicek alone and proceed with the others, or carve the entire family.

- [ ] **Step 11:** Commit.

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/Vasicek.java \
        jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/OneFactorAffineModel.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/VasicekCalibrationTest.java \
        migration-harness/cpp/probes/model/shortrate/vasicek_calibration_probe.cpp \
        migration-harness/references/model/shortrate/vasicek_calibration.json
git commit -s -m "$(cat <<'EOF'
align(model.shortrate.onefactor): Vasicek through arguments_ indirection

Replaces the dead Parameter member fields with accessor methods that
route every read through arguments_.get(i). The constructor now
populates arguments_ directly via set(); subsequent
ConstantParameter writes therefore propagate to the calibratable
vector, restoring C++'s Parameter& reference-binding semantics.
arguments_ access modifier in OneFactorAffineModel raised from
private to protected so the pattern can land uniformly across the
family in subsequent commits.

Probe: vasicek_calibration_probe captures discountBondOption
fingerprint values at three (strike, maturity, bondMaturity) tuples;
the Java test calls the same accessors through the arguments_
indirection. Tight tier.

Carved as WI-4-carveout-Vasicek in Phase 2a; resolution will be
recorded in phase2a-carveouts.md after the family-sweep commits land.

Test count: 628 -> 629.
EOF
)"
git push origin main
```

### Task 3.2: HullWhite — same pattern (commit 2)

`HullWhite extends Vasicek`, so it inherits the indirection from Task 3.1. Its own constructor sets parameters via the parent — verify it still works after Task 3.1's changes.

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java`
- Create: `migration-harness/cpp/probes/model/shortrate/hullwhite_calibration_probe.cpp`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/HullWhiteCalibrationTest.java`

- [ ] **Step 1:** Read HullWhite.java's three constructors (lines 71-95). HullWhite has its own `b_` field shadowing, plus parameters specific to its time-dependent drift. Identify which fields need indirection beyond what Vasicek already provides. C++ ref: `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/hullwhite.cpp`.

- [ ] **Step 2:** Apply the same indirection pattern: any `protected Parameter X_` field that's set in the ctor and assumed to track `arguments_[i]` becomes a `Parameter X()` accessor. Constructor populates `arguments_.set(...)`.

- [ ] **Step 3:** Build the C++ probe (analogous shape to Vasicek's, but with HullWhite's parameter set: `a`, `sigma`). HullWhite needs a `Handle<YieldTermStructure>` — use the same `flatCurve` helper pattern from `hestonprocess_qe_probe.cpp`. The probe captures discountBondOption fingerprints under HullWhite-specific drift.

```cpp
// migration-harness/cpp/probes/model/shortrate/hullwhite_calibration_probe.cpp
#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/hullwhite.hpp>
#include <ql/termstructures/yield/flatforward.hpp>
#include <ql/time/calendars/nullcalendar.hpp>
#include <ql/time/daycounters/actual365fixed.hpp>
#include "../../common.hpp"

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/hullwhite_calibration", QL_VERSION,
                        "hullwhite_calibration_probe");

    Settings::instance().evaluationDate() = Date(22, April, 2026);
    Handle<YieldTermStructure> ts(
        ext::make_shared<FlatForward>(Date(22, April, 2026), 0.04, Actual365Fixed()));

    const Real a = 0.1, sigma = 0.01;
    HullWhite model(ts, a, sigma);

    json sampleArr = json::array();
    for (auto t : {std::make_tuple(0.95, 0.5, 1.0),
                   std::make_tuple(1.00, 1.0, 2.0),
                   std::make_tuple(1.05, 2.0, 5.0)}) {
        const Real strike = std::get<0>(t);
        const Time mat = std::get<1>(t);
        const Time bMat = std::get<2>(t);
        sampleArr.push_back({{"strike", strike}, {"maturity", mat},
                             {"bondMaturity", bMat},
                             {"call", model.discountBondOption(Option::Call, strike, mat, bMat)},
                             {"put",  model.discountBondOption(Option::Put,  strike, mat, bMat)}});
    }

    out.addCase("hullwhite_round_trip",
        json{{"r_curve", 0.04}, {"a", a}, {"sigma", sigma}},
        json{{"samples", sampleArr}});

    out.write();
    return 0;
}
```

- [ ] **Step 4:** Build and capture references. `./migration-harness/generate-references.sh hullwhite_calibration_probe`.

- [ ] **Step 5:** Write the Java test. Same shape as `VasicekCalibrationTest` but constructs `HullWhite` with a flat `FlatForward` curve at rate `0.04` matching the probe.

- [ ] **Step 6:** Run the test, then the full suite. Expected: 630 tests green.

- [ ] **Step 7:** Commit + push.

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/HullWhite.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/HullWhiteCalibrationTest.java \
        migration-harness/cpp/probes/model/shortrate/hullwhite_calibration_probe.cpp \
        migration-harness/references/model/shortrate/hullwhite_calibration.json
git commit -s -m "$(cat <<'EOF'
align(model.shortrate.onefactor): HullWhite through arguments_ indirection

Follows the Vasicek pattern landed in commit <Task 3.1 hash>. Any
HullWhite-specific Parameter member fields (beyond what Vasicek
provides via inheritance) are now accessor methods routed through
arguments_.get(i); constructor populates arguments_ via set().

Probe: hullwhite_calibration_probe captures discountBondOption
fingerprint values under HullWhite's time-dependent drift.

Test count: 629 -> 630.
EOF
)"
git push origin main
```

### Task 3.3: BlackKarasinski — same pattern (commit 3)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java`
- Create: `migration-harness/cpp/probes/model/shortrate/blackkarasinski_calibration_probe.cpp`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/BlackKarasinskiCalibrationTest.java`

- [ ] **Step 1:** Read `BlackKarasinski.java` lines 53-80. The current constructor has `this.a_ = arguments_.get(0); this.sigma_ = arguments_.get(1);` (2-parameter model). Same drift as Phase-2a Vasicek had: assigns the slot, then never updates it.

- [ ] **Step 2:** Delete the `protected Parameter a_; protected Parameter sigma_;` fields. Add `Parameter a() { return arguments_.get(0); }` and `Parameter sigma() { return arguments_.get(1); }` accessors. Constructor populates via `arguments_.set(0, new ConstantParameter(a, ...))`. C++ ref: `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/blackkarasinski.cpp`.

- [ ] **Step 3:** Find any internal `a_.` / `sigma_.` reads with grep; route them through the new accessors.

- [ ] **Step 4:** Build the C++ probe. BlackKarasinski needs a `Handle<YieldTermStructure>` and uses `numericalImpl()` — the discountBondOption fingerprint may not be available for log-normal short-rate models; use `discount(t, x)` or `tree(grid)` outputs instead. Read C++ `blackkarasinski.hpp` to find the simplest non-trivial accessor that exercises `a_`/`sigma_` reads. If no clean fingerprint exists for the discountBondOption shape, capture `tree(grid).underlying(0, 0)` at a fixed grid as the fingerprint.

- [ ] **Step 5:** If a fingerprint accessor is available, capture refs (`./migration-harness/generate-references.sh blackkarasinski_calibration_probe`); write `BlackKarasinskiCalibrationTest.java` mirroring `VasicekCalibrationTest`'s shape; run.

  If BlackKarasinski has no scalar-pricing fingerprint that exercises the indirection, **document that in the commit message and skip the probe**, but still apply the indirection refactor and verify it with a reflection-based unit test that confirms `aParam()` and `sigmaParam()` return the same instance that `arguments_.get(i)` returns:

  ```java
  // jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/BlackKarasinskiCalibrationTest.java
  // Reflection-based fallback: no scalar-pricing fingerprint available
  // (BlackKarasinski uses a tree/numerical impl that requires substantial
  // setup). Verifies the Parameter-arguments_ indirection wired up
  // correctly by confirming accessors and arguments_ vector agree.
  package org.jquantlib.testsuite.model.shortrate;

  import java.lang.reflect.Method;
  import org.jquantlib.model.shortrate.onefactormodels.BlackKarasinski;
  import org.jquantlib.quotes.Handle;
  import org.jquantlib.termstructures.YieldTermStructure;
  // ... build flat-curve termStructure as in HestonProcessTest ...
  import org.junit.Test;
  import static org.junit.Assert.assertSame;

  public class BlackKarasinskiCalibrationTest {
      @Test
      public void parameterAccessorsRouteThroughArguments() throws Exception {
          // build with a=0.1, sigma=0.01 over a flat-rate term structure
          BlackKarasinski model = new BlackKarasinski(<flat-curve handle>, 0.1, 0.01);
          Method aParam = BlackKarasinski.class.getDeclaredMethod("aParam");
          Method sigmaParam = BlackKarasinski.class.getDeclaredMethod("sigmaParam");
          aParam.setAccessible(true);
          sigmaParam.setAccessible(true);
          // The indirection must return the same Parameter instances held
          // in arguments_; if the accessor copied or rebuilt, the test
          // catches it.
          assertSame(model.getArguments().get(0), aParam.invoke(model));
          assertSame(model.getArguments().get(1), sigmaParam.invoke(model));
      }
  }
  ```

  (`getArguments` is shorthand — use the actual public accessor for the `arguments_` vector if one exists; if not, use reflection on `arguments_` directly.)

- [ ] **Step 6:** Run the test, then the full suite. Expected: 631 tests green (prior 630 + 1 new test).

- [ ] **Step 7:** Commit + push.

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/BlackKarasinski.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/BlackKarasinskiCalibrationTest.java
# Add probe + reference files only if Step 5 went the fingerprint path:
# git add migration-harness/cpp/probes/model/shortrate/blackkarasinski_calibration_probe.cpp \
#         migration-harness/references/model/shortrate/blackkarasinski_calibration.json
git commit -s -m "$(cat <<'EOF'
align(model.shortrate.onefactor): BlackKarasinski through arguments_ indirection

Follows the Vasicek pattern landed in commit <Task 3.1 hash>. The
two Parameter member fields (a_, sigma_) are now accessor methods
routed through arguments_.get(i); constructor populates arguments_
via set().

Test: <fingerprint test name> verifies the indirection (or:
reflection-based test confirming aParam()/sigmaParam() return the
same instance held in arguments_ — BlackKarasinski has no scalar
pricing accessor that's clean enough for a fingerprint probe).

Test count: 630 -> 631.
EOF
)"
git push origin main
```

### Task 3.4: CoxIngersollRoss — same pattern (commit 4)

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/CoxIngersollRoss.java`
- Create: `migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp`
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/CoxIngersollRossCalibrationTest.java`

- [ ] **Step 1:** Read `CoxIngersollRoss.java` lines 54-85. The constructor has `theta_ = arguments_.get(0); k_ = arguments_.get(1); sigma_ = arguments_.get(2); r0_ = arguments_.get(3);` — same drift pattern, four parameters.

- [ ] **Step 2:** Apply the indirection pattern. CIR has `discountBondOption` so the Vasicek-style fingerprint probe works directly here. C++ ref: `migration-harness/cpp/quantlib/ql/models/shortrate/onefactormodels/coxingersollross.cpp`.

- [ ] **Step 3:** Build the C++ probe:

```cpp
// migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp
// Reference values for CoxIngersollRoss discountBondOption fingerprint
// across three (strike, maturity, bondMaturity) tuples; verifies that
// the arguments_-indirection wiring carries through the Parameter system.

#include <ql/version.hpp>
#include <ql/models/shortrate/onefactormodels/coxingersollross.hpp>
#include "../../common.hpp"

#include <vector>
#include <tuple>

using namespace QuantLib;
using namespace jqml_harness;

int main() {
    ReferenceWriter out("model/shortrate/coxingersollross_calibration", QL_VERSION,
                        "coxingersollross_calibration_probe");

    const Real r0 = 0.05, theta = 0.06, k = 0.5, sigma = 0.04;
    CoxIngersollRoss model(r0, theta, k, sigma);

    json sampleArr = json::array();
    for (auto t : {std::make_tuple(0.95, 0.5, 1.0),
                   std::make_tuple(1.00, 1.0, 2.0),
                   std::make_tuple(1.05, 2.0, 5.0)}) {
        const Real strike = std::get<0>(t);
        const Time mat = std::get<1>(t);
        const Time bMat = std::get<2>(t);
        sampleArr.push_back({{"strike", strike}, {"maturity", mat},
                             {"bondMaturity", bMat},
                             {"call", model.discountBondOption(Option::Call, strike, mat, bMat)},
                             {"put",  model.discountBondOption(Option::Put,  strike, mat, bMat)}});
    }

    out.addCase("cir_round_trip",
        json{{"r0", r0}, {"theta", theta}, {"k", k}, {"sigma", sigma}},
        json{{"samples", sampleArr}});

    out.write();
    return 0;
}
```

Build and run: `./migration-harness/generate-references.sh coxingersollross_calibration_probe`.

- [ ] **Step 4:** Write the Java test (`CoxIngersollRossCalibrationTest.java`) mirroring `VasicekCalibrationTest`'s shape — load reference, construct `CoxIngersollRoss(r0, theta, k, sigma)` (note CIR's parameter order is **r0, theta, k, sigma**, not the same order as Vasicek), iterate samples, assert tight-tier on call and put.

- [ ] **Step 5:** Run the test, then the full suite. Expected: 632 tests green (prior 631 + 1 new test).

- [ ] **Step 6:** Commit + push.

```bash
git add jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/CoxIngersollRoss.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/model/shortrate/CoxIngersollRossCalibrationTest.java \
        migration-harness/cpp/probes/model/shortrate/coxingersollross_calibration_probe.cpp \
        migration-harness/references/model/shortrate/coxingersollross_calibration.json
git commit -s -m "$(cat <<'EOF'
align(model.shortrate.onefactor): CoxIngersollRoss through arguments_ indirection

Follows the Vasicek pattern landed in commit <Task 3.1 hash>. The
four Parameter member fields (theta_, k_, sigma_, r0_) are now
accessor methods routed through arguments_.get(i); constructor
populates arguments_ via set().

Probe: coxingersollross_calibration_probe captures
discountBondOption fingerprint values across three (strike,
maturity, bondMaturity) tuples.

Test count: 631 -> 632.
EOF
)"
git push origin main
```

### Task 3.5: Update Vasicek carveout entry (commit 5)

**Files:**
- Modify: `docs/migration/phase2a-carveouts.md`

- [ ] **Step 1:** Open `docs/migration/phase2a-carveouts.md`. Locate the `WI-4-carveout-Vasicek` section's `**Disposition (Phase 2a):**` block.

- [ ] **Step 2:** Replace the disposition with:

```markdown
**Disposition:**
Fixed in Phase 2b WI-3 across the entire one-factor model family:
- Vasicek (commit <hash from Task 3.1>)
- HullWhite (commit <hash from Task 3.2>)
- BlackKarasinski (commit <hash from Task 3.3>)
- CoxIngersollRoss (commit <hash from Task 3.4>)

Each model now exposes its `Parameter` members through accessors that
read `arguments_.get(i)` directly, so subsequent `arguments_.set(i, ...)`
writes propagate to all reads — restoring the C++ `Parameter&`
reference-binding semantics in a Java-idiomatic way. Calibration
round-trip fingerprint tests landed for each model (`*CalibrationTest`).
```

- [ ] **Step 3:** Run the full suite once more.

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 632, Failures: 0, Errors: 0, Skipped: 24` (628 + 4 calibration tests = 632, assuming all four landed; adjust if BlackKarasinski's probe was skipped per Task 3.3 fallback).

- [ ] **Step 4:** Commit.

```bash
git add docs/migration/phase2a-carveouts.md
git commit -s -m "$(cat <<'EOF'
docs(migration): WI-4-carveout-Vasicek resolved across one-factor family

Updates the carveout disposition with the four Phase-2b WI-3
fix-commit references. The Parameter-arguments_ indirection now
applies uniformly to Vasicek, HullWhite, BlackKarasinski, and
CoxIngersollRoss.
EOF
)"
git push origin main
```

---

## Layer 4 — WI-4 SABR fix-or-carve (time-boxed)

This layer has two outcome paths: fix or carve. Decide after Task 4.1's investigation. Both outcomes update the carveout pointer; only the fix path lands code commits.

### Task 4.1: Investigation (no commit)

**Files (read-only):**
- `jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java`
- `migration-harness/cpp/quantlib/ql/termstructures/volatilities/interpolation/sabrinterpolation.hpp`
- `jquantlib/src/main/java/org/jquantlib/termstructures/volatilities/Sabr.java` (the validator)

- [ ] **Step 1:** Reproduce the failure. Temporarily un-skip SABRInterpolationTest and capture the full stack:

```bash
git stash
sed -i '' '/Phase-2a status (2026-04-24)/,/^    @Ignore$/d' jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java
(cd jquantlib && mvn test -Dtest=SABRInterpolationTest) 2>&1 | grep -A 20 "beta must be"
git checkout jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java
git stash pop  # if anything stashed
```

Note the call sequence in the stack — particularly which `validateSabrParameters` call site fires and what β value is being passed.

- [ ] **Step 2:** Read `SABRInterpolation.java::SabrParametersTransformation::direct` (line 410). The β transformation is `y_.set(1, Math.exp(-(x.get(1) * x.get(1))))` — this maps any real x into (0, 1]. So β should never be ≤ 0 or > 1 from the transformation alone. **However**, if the LM iterate `x.get(1)` is `NaN` (which can happen if the optimizer diverges), then `Math.exp(-NaN*NaN) = NaN`, and `NaN >= 0.0 && NaN <= 1.0` evaluates to `false`, throwing the validation.

Alternatively, the issue might be in the `isBetaFixed` path: when β is fixed to `0.5` at construction, the optimizer doesn't transform it — but if `validateSabrParameters` is called with `beta=0.5` directly, `0.5 >= 0.0 && 0.5 <= 1.0` is true and it should pass. So the failure mode probably only triggers when `isBetaFixed=false` AND the LM iterate produces NaN.

- [ ] **Step 3:** Diagnose. Three possible failure modes (per design §3.4):
  - **(a)** Transcription bug in the transformation (commented `atan`/`tan` lines hint at a partial port). Likely fix: ≤ 20 LOC.
  - **(b)** NaN propagation from a divergent LM iterate fed to the transformation. Likely fix: NaN guard in the SABRError cost function or in validateSabrParameters; ≤ 20 LOC.
  - **(c)** Deeper algorithmic divergence (e.g., constraint-projection scheme missing).

Consult C++ `SABRWrapper` (in `sabrinterpolation.hpp`) and `SABRSpecs::guess` to see how C++ avoids the failure.

- [ ] **Step 4:** Decide and announce.

- **If (a) or (b):** proceed to Task 4.2 (fix path).
- **If (c) or "I cannot localize this in one session":** proceed to Task 4.3 (carve path). **A4 fires automatically** — no user ask.

### Task 4.2: Fix path (commits 1-3, only if Task 4.1 → (a) or (b))

**Files:**
- Modify: `jquantlib/src/main/java/org/jquantlib/math/interpolations/SABRInterpolation.java` (or wherever the diagnosed bug lives)
- Create: `migration-harness/cpp/probes/math/interpolations/sabr_interpolation_probe.cpp` (transformation fingerprints + 1 calibration round-trip)
- Create: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRTransformationTest.java` (exact-input transformation tests at fixed (x,y) tuples)
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java`
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java`
- Modify: `docs/migration/phase2a-carveouts.md`

- [ ] **Step 1:** Apply the fix per Task 4.1 Step 4 diagnosis. Show the diff.

- [ ] **Step 2:** Build the SABR transformation probe. Capture C++ `SABRWrapper::direct(x)` outputs at 5 fixed `x` vectors (one identity-like, two large positive components, two large negative components). The probe verifies the transformation maps to legal (α, β, ν, ρ) values.

- [ ] **Step 3:** Capture refs, write the `SABRTransformationTest`, run.

- [ ] **Step 4:** Commit the fix + probe + transformation test.

- [ ] **Step 5:** Un-skip both `SABRInterpolationTest#testSABRInterpolationTest` and `InterpolationTest#testSabrInterpolation`. Run them. If they pass, update the carveout pointer comment in each test file to "Fixed in Phase 2b WI-4 (commit <hash>)".

If un-skipped tests **fail** (because the surrounding SABR algorithm has additional drift beyond the transformation), revert the un-skip changes only — keep the transformation fix and the probe — and write a focused carveout entry for the remaining drift in `phase2a-carveouts.md` updating the existing `WI-2-carveout-SABR` to "transformation drift fixed in Phase 2b WI-4 (commit <hash>); residual SABR algorithm drift remains carved to Phase 2c".

- [ ] **Step 6:** Run full suite. Expected: 632 + 1 (transformation test) + 0/1/2 (un-skips) = 633–635.

- [ ] **Step 7:** Update `docs/migration/phase2a-carveouts.md::WI-2-carveout-SABR` disposition section to "Fixed in Phase 2b WI-4" with commit hashes.

- [ ] **Step 8:** Commit + push.

```bash
git commit -s -m "..." && git push origin main
```

### Task 4.3: Carve path (commit 1, only if Task 4.1 → (c))

**Files:**
- Create: `docs/migration/phase2c-carveouts.md`
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java` (update pointer comment)
- Modify: `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java` (same)
- Modify: `docs/migration/phase2a-carveouts.md` (note re-carve to 2c)

- [ ] **Step 1:** Create `docs/migration/phase2c-carveouts.md` with a `## WI-2-SABR — SABR algorithm depth (re-carved from Phase 2b WI-4)` section. Include:
  - The exact failure mode discovered in Task 4.1 (which `validateSabrParameters` call, what β value, what stack frame).
  - The deeper-algorithmic-divergence hypothesis (the (c) reasoning).
  - The infrastructure or scope item that would unblock this in Phase 2c.

- [ ] **Step 2:** Update the `@Ignore` pointer comment in both test files to point at `phase2c-carveouts.md::WI-2-SABR` instead of `phase2a-carveouts.md::WI-2-carveout-SABR`. Tests stay skipped.

- [ ] **Step 3:** In `phase2a-carveouts.md::WI-2-carveout-SABR`, append a `**Re-carve note (Phase 2b WI-4, 2026-04-25):**` paragraph summarizing the Task 4.1 finding and pointing forward to `phase2c-carveouts.md`.

- [ ] **Step 4:** Run the full suite (no code changes — only docs and `@Ignore` pointer comments).

```bash
(cd jquantlib && mvn test) 2>&1 | grep -E "^\[WARNING\] Tests run"
```

Expected: `Tests run: 632, Failures: 0, Errors: 0, Skipped: 25` (no test count change; SABR stays skipped; Skipped is 25 again because OptimizerTest was un-skipped in WI-2 but SABR×2 stay skipped — same total as Phase 2a).

Wait — recompute: Phase-2a baseline was 626/25. WI-1 added 2 → 628/25. WI-2 un-skipped 1 → 628/24. WI-3 added 4 → 632/24. WI-4 carve adds 0 tests, leaves SABR×2 skipped → still 632/24.

If carve outcome: final count is 632/24 (not 632/25 — re-check the Skipped delta).

- [ ] **Step 5:** Commit + push.

```bash
git add docs/migration/phase2c-carveouts.md \
        docs/migration/phase2a-carveouts.md \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java
git commit -s -m "$(cat <<'EOF'
docs(migration): re-carve SABR transformation from Phase 2b WI-4 to Phase 2c

The Phase-2b WI-4 time-boxed investigation localized the
SABRInterpolationTest/InterpolationTest failure to <one-sentence
summary from Task 4.1 Step 3>. The fix requires <infrastructure
description>, which is outside the 61-package fence and trips A4.
Per design §3.4, A4 firing inside WI-4 is the carve gate: no further
deliberation, ship Phase 2b without the SABR fix.

Documents the diagnosis in docs/migration/phase2c-carveouts.md (new
file). Updates @Ignore pointer comments in both test files to point
at the 2c carveout. Test count and skip count unchanged.
EOF
)"
git push origin main
```

---

## Layer 5 — Completion doc + tag

### Task 5.1: Write `phase2b-completion.md` (commit 1)

**Files:**
- Create: `docs/migration/phase2b-completion.md`

- [ ] **Step 1:** Write the completion report. Use `docs/migration/phase2a-completion.md` as the template; sections:
  - Header (date, tip commit, predecessor tag, target tag)
  - Exit criteria table (mapping to design §6 numbered list)
  - Per-WI summary with commit hashes
  - Final scanner state (`work_in_progress: 2`, both Phase-2c carveouts unchanged)
  - Test suite final state
  - Deviations from the plan (anything that differed from this document — e.g., BlackKarasinski probe skipped, SABR carve path taken, A8 fired during WI-3, etc.)
  - Phase 2c seed list (carry forward from Phase 2a's seed list, minus what 2b landed)

- [ ] **Step 2:** Run the scanner one more time and embed its output in the completion doc:

```bash
python3 tools/stub-scanner/scan_stubs.py
```

- [ ] **Step 3:** Commit.

```bash
git add docs/migration/phase2b-completion.md
git commit -s -m "$(cat <<'EOF'
docs(migration): Phase 2b completion report

Summarizes the four WIs landed (or carved) in Phase 2b:
- WI-1 HestonProcess QEM (commit <hash>)
- WI-2 Simplex 1D fix + OptimizerTest un-skip (commits <hashes>)
- WI-3 Vasicek family Parameter-ref sweep (commits <hashes>)
- WI-4 SABR <fix-or-carve> (commits <hashes> or carve doc reference)

Final state: <test count>/0/0/<skipped count>; scanner WIP 2
(CapHelper, G2 — Phase 2c seeds, unchanged). All Phase-2a carveouts
either resolved or re-carved with explicit Phase-2c diagnosis.

Next: tag jquantlib-phase2b-complete.
EOF
)"
git push origin main
```

### Task 5.2: Tag and push

- [ ] **Step 1:** Tag the completion-doc commit.

```bash
git tag -a jquantlib-phase2b-complete -m "Phase 2b complete. See docs/migration/phase2b-completion.md."
```

- [ ] **Step 2:** Push the tag.

```bash
git push origin jquantlib-phase2b-complete
```

- [ ] **Step 3:** Verify both `main` and the tag are at the same commit.

```bash
git log --oneline -3
git show jquantlib-phase2b-complete --stat | head -5
```

Phase 2b complete.

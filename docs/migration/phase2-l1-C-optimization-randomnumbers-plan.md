# L1-C optimization + randomnumbers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port 12 classes — 6 math/optimization + 6 math/randomnumbers — from
C++ v1.42.1 to Java with TDD cross-validation.

**Architecture:** Each class is mostly self-contained (an optimization method
or a random-number generator). Optimization classes implement the existing
`org.jquantlib.math.optimization.OptimizationMethod` interface; RNGs
implement existing `org.jquantlib.math.randomnumbers.UniformRandomGenerator`
/ `GaussianRandomGenerator` interface.

**Tech Stack:** JDK 25, Maven (surefire 3.5.2, junit 4.13.2), JDK 25 idioms
(records for ctor-param bundles, switch expressions, pattern matching).

---

## Scope

### math/optimization (6)
1. **SimulatedAnnealing** — `simulatedannealing.hpp` (~200 LOC) — Metropolis-step optimizer
2. **GoldsteinLineSearch** — `goldstein.hpp` (~100 LOC) — alternative to ArmijoLineSearch
3. **DifferentialEvolution.Candidate** — internal DTO (record candidate)
4. **DifferentialEvolution.Configuration** — internal DTO (record candidate)
5. **NonhomogeneousBoundaryConstraint** — `constraint.hpp` — non-uniform bounds
6. **SimpleCostFunction** — `costfunction.hpp` — function-pointer wrapper (use Java functional interface)

### math/randomnumbers (6)
7. **BoxMullerGaussianRng** — `boxmullergaussianrng.hpp` — Box-Muller transform on uniform pairs
8. **KnuthUniformRng** — `knuthuniformrng.hpp` — Knuth's MMIX-style LCG
9. **LecuyerUniformRng** — `lecuyeruniformrng.hpp` — L'Ecuyer combined LCG
10. **Ranlux64UniformRng** — `ranluxuniformrng.hpp` — 64-bit RANLUX (subtract-with-borrow)
11. **CLGaussianRng** — `centrallimitgaussianrng.hpp` — Central Limit Theorem averager
12. **Burley2020SobolBrownianBridgeRsg** — `sobolbrownianbridgersg.hpp` — Burley owen-scrambled Sobol

---

## File Structure

**Created** (12 production + 12 test files, plus 0 SAM since interfaces already exist):
- `jquantlib/src/main/java/org/jquantlib/math/optimization/SimulatedAnnealing.java`
- `jquantlib/src/main/java/org/jquantlib/math/optimization/GoldsteinLineSearch.java`
- `jquantlib/src/main/java/org/jquantlib/math/optimization/SimpleCostFunction.java`
- `jquantlib/src/main/java/org/jquantlib/math/optimization/NonhomogeneousBoundaryConstraint.java`
- DifferentialEvolution.Candidate/Configuration: ADD as nested records in the existing `DifferentialEvolution.java` (verify file exists; otherwise port the whole class)
- `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/BoxMullerGaussianRng.java`
- `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/KnuthUniformRng.java`
- `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/LecuyerUniformRng.java`
- `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/Ranlux64UniformRng.java`
- `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/CLGaussianRng.java`
- `jquantlib/src/main/java/org/jquantlib/math/randomnumbers/Burley2020SobolBrownianBridgeRsg.java`
- 12 corresponding `*Test.java` files

**C++ sources** (read-only reference):
- `migration-harness/cpp/quantlib/ql/math/optimization/*.hpp`
- `migration-harness/cpp/quantlib/ql/math/randomnumbers/*.hpp`
- `migration-harness/cpp/quantlib/test-suite/{optimizers,randomnumbers}.cpp`

---

## Per-class task template

Each class follows this 5-step TDD cycle. Concrete steps vary by class; the
implementer is expected to read the C++ source, write a failing test against
a probe-derived expected value, then implement + commit.

### Common preflight

- [ ] **Preflight 1: Sync branch**

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib-d5-C && git pull origin main
```

- [ ] **Preflight 2: Verify existing interfaces**

```bash
ls jquantlib/src/main/java/org/jquantlib/math/optimization/{OptimizationMethod,Constraint,CostFunction,LineSearch}.java
ls jquantlib/src/main/java/org/jquantlib/math/randomnumbers/{UniformRandomGenerator,GaussianRandomGenerator,RandomSequenceGenerator}.java
# Expected: all exist (we ported these earlier — verify class signatures)
```

### Task pattern (repeat per class)

For each class `X`:

- [ ] **Step 1: Read C++ source**

```bash
cat migration-harness/cpp/quantlib/ql/math/<subpkg>/<xlowercase>.hpp
cat migration-harness/cpp/quantlib/ql/math/<subpkg>/<xlowercase>.cpp 2>/dev/null
# Identify: parent class/interface, ctor params, key methods
```

- [ ] **Step 2: Write the failing test**

For RNGs: assert the first 3-10 outputs match the C++ reference sequence
exactly (run C++ binary or probe). For optimizers: assert convergence on a
known cost function (e.g., parabolic `f(x) = x^2 + x + 1`) within
`functionEpsilon=1e-12`.

```java
@Test
public void testFirstOutputsMatchCpp() {
    final var rng = new XRng(seed=42);
    // Expected pulled from C++ probe at /tmp/<x>-probe.out
    final double[] expected = { /* exact C++ first-N outputs */ };
    for (int i = 0; i < expected.length; i++) {
        assertEquals(expected[i], rng.next(), 1e-15);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest=XTest
# Expected: FAIL
```

- [ ] **Step 4: Implement port**

Verbatim translation of C++ source to Java mirroring an existing analogue
(e.g., `MersenneTwisterUniformRng.java` for new RNGs;
`LevenbergMarquardt.java` for new optimizers).

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn -pl ../jquantlib test -Dtest=XTest
# Expected: PASS
```

- [ ] **Step 6: Commit**

```bash
git add jquantlib/src/main/java/org/jquantlib/math/<subpkg>/X.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/<subpkg>/XTest.java
git commit -s -m "infra(math.<subpkg>.X): port v1.42.1 (phase2-L1-C-<x>)"
```

---

## Final sweep

- [ ] **Final 1: Targeted test sweep**

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest='*SimulatedAnnealing*Test,*GoldsteinLineSearch*Test,*BoxMuller*Test,*Knuth*Test,*Lecuyer*Test,*Ranlux*Test,*CLGaussian*Test,*Burley*Test,*DifferentialEvolution*Test,*NonhomogeneousBoundary*Test,*SimpleCostFunction*Test'
# Expected: 12+ tests / 0 failures
```

- [ ] **Final 2: Regression check on optimization-using tests**

```bash
mvn -pl ../jquantlib test -Dtest='OptimizerTest,SwaptionAdditional*Test,*Calibration*Test,*MonteCarlo*Test'
# Expected: green (existing tests should not regress; new RNGs only used where opted-in)
```

- [ ] **Final 3: Push branch**

```bash
git push origin d5-C-math-misc
```

---

## Self-review

1. **Spec coverage:** 12 classes match L1-C scope in `phase2-l1-plan.md`. ✓
2. **Placeholder scan:** Test code uses `/* exact C++ first-N outputs */` markers — implementer MUST replace via C++ probe. The probe expectation is intentional (no hand-derivation).
3. **Type consistency:** All RNGs implement the existing `UniformRandomGenerator`/`GaussianRandomGenerator` interface; optimizers extend `OptimizationMethod`. ✓
4. **TDD discipline:** Each class has Red-Green-Commit cycle. ✓
5. **Probe note:** For RNGs, expected outputs MUST come from running the actual C++ binary at v1.42.1 — these are 64-bit-deterministic state machines and bit-exactness matters for downstream MC test reproducibility.

---

## Definition of done

- 12 classes ported (6 optimization + 6 randomnumbers)
- 12+ tests passing (at minimum 1 per class, more for RNGs that need sequence validation)
- Full mvn `*Copula*,*Calibration*,*MonteCarlo*` regression green
- Commits use `-s` sign-off, per-class commit messages
- Branch pushed

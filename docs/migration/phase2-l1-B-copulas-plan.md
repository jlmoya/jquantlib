# L1-B Copulas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the 13 bivariate copula classes from C++ v1.42.1 to Java under
`org.jquantlib.math.copulas`, with TDD-cross-validation against C++ probe values.

**Architecture:** Each copula is a small stateless 2-arg function class
(`apply(u, v) -> double`) implementing the `BinaryFunction<Double,Double,Double>`
interface (or a copula-specific `Copula` SAM). C++ uses `std::function` /
`operator()`; Java mirrors with a functional-interface lambda.

**Tech Stack:** JDK 25 (records for parameter-bundle DTOs), Maven, JUnit 4.13.2.

---

## File Structure

**Created** (one production file + one test file per copula, 26 total):
- `jquantlib/src/main/java/org/jquantlib/math/copulas/Copula.java` — SAM interface
- `jquantlib/src/main/java/org/jquantlib/math/copulas/AliMikhailHaqCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/ClaytonCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/FarlieGumbelMorgensternCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/FrankCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/GalambosCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/GaussianCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/GumbelCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/HuslerReissCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/IndependentCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/MarshallOlkinCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/MaxCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/MinCopula.java`
- `jquantlib/src/main/java/org/jquantlib/math/copulas/PlackettCopula.java`
- 13 corresponding `*Test.java` files in `jquantlib/src/test/java/org/jquantlib/testsuite/math/copulas/`

**C++ sources** (read-only reference):
- `migration-harness/cpp/quantlib/ql/math/copulas/*.hpp` (13 files)
- `migration-harness/cpp/quantlib/test-suite/copulas.cpp` (if present — verify with grep)

---

## Per-copula task template

Each of the 13 copulas follows this 5-step TDD cycle. **Task 0** establishes the
SAM interface; **Tasks 1-13** apply this template per copula.

### Task 0: Copula SAM interface

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/copulas/Copula.java`
- Test: none (interface only)

- [ ] **Step 1: Create the interface file**

```java
package org.jquantlib.math.copulas;

/**
 * Java SAM for a bivariate copula C(u, v) where u, v ∈ [0, 1].
 *
 * <p>Mirrors C++ v1.42.1 copula function-object interface (each copula header
 * declares {@code operator()(Real, Real) const}). Java uses an explicit SAM
 * so call sites can pass copulas as lambdas or method references.
 */
@FunctionalInterface
public interface Copula {
    /**
     * Evaluate the copula at {@code (u, v)}.
     *
     * @param u first marginal CDF value in [0, 1]
     * @param v second marginal CDF value in [0, 1]
     * @return joint CDF value C(u, v) in [0, 1]
     */
    double apply(double u, double v);
}
```

- [ ] **Step 2: Verify compile**

```bash
cd jquantlib-parent && mvn -pl ../jquantlib compile
# Expected: BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add jquantlib/src/main/java/org/jquantlib/math/copulas/Copula.java
git commit -s -m "infra(math.copulas.Copula): SAM interface for bivariate copulas (phase2-L1-B-iface)"
```

### Tasks 1-13: Per-copula port (TDD)

For each copula `X`, follow exactly:

**Files:**
- Create: `jquantlib/src/main/java/org/jquantlib/math/copulas/XCopula.java`
- Test: `jquantlib/src/test/java/org/jquantlib/testsuite/math/copulas/XCopulaTest.java`

- [ ] **Step 1: Read the C++ source**

```bash
cat migration-harness/cpp/quantlib/ql/math/copulas/x_lowercase.hpp
# Identify: ctor params (e.g., theta for Clayton), apply(u, v) formula,
# boundary conditions (u or v = 0, u or v = 1).
```

- [ ] **Step 2: Write the failing test**

```java
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.XCopula;
import org.junit.Test;

public class XCopulaTest {

    /**
     * Faithful port of v1.42.1 test-suite/copulas.cpp::testXCopula (if present).
     * If C++ has no test, cross-validate against the analytic formula at
     * (u=0.3, v=0.6) with the relevant ctor parameter.
     */
    @Test
    public void testApplyAtKnownPoints() {
        // Replace with the actual expected value from a C++ probe run, e.g.
        // const Real expected = XCopula(theta=0.5)(0.3, 0.6);  // C++
        final var copula = new XCopula(/* params */ 0.5);
        // Expected pulled from C++ probe at /tmp/x-copula-probe.out
        final double expected = /* EXACT C++ output, e.g. */ 0.2247...;
        assertEquals(expected, copula.apply(0.3, 0.6), 1e-12);
    }

    @Test
    public void testBoundaryConditions() {
        final var copula = new XCopula(0.5);
        // C(u, 0) = 0 for all u; C(u, 1) = u for all u (sklar's theorem)
        assertEquals(0.0, copula.apply(0.5, 0.0), 1e-15);
        assertEquals(0.5, copula.apply(0.5, 1.0), 1e-15);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest=XCopulaTest
# Expected: FAIL with "XCopula not found"
```

- [ ] **Step 4: Implement the copula**

```java
package org.jquantlib.math.copulas;

/**
 * Java port of v1.42.1 X copula at ql/math/copulas/x_lowercase.hpp.
 *
 * <p>Formula: C(u, v) = ... (taken from C++ source).
 */
public final class XCopula implements Copula {

    private final double theta;

    public XCopula(final double theta) {
        // Mirror C++ ctor assertion(s)
        if (theta < /* C++ lower bound */) {
            throw new IllegalArgumentException("theta out of range: " + theta);
        }
        this.theta = theta;
    }

    @Override
    public double apply(final double u, final double v) {
        // Verbatim translation of C++ operator()(Real u, Real v) const
        // body. Boundary cases handled per C++ source.
        return /* C++ formula in Java */;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest=XCopulaTest
# Expected: PASS — 2/0/0/0 BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add jquantlib/src/main/java/org/jquantlib/math/copulas/XCopula.java \
        jquantlib/src/test/java/org/jquantlib/testsuite/math/copulas/XCopulaTest.java
git commit -s -m "infra(math.copulas.XCopula): port v1.42.1 (phase2-L1-B-x)"
```

---

## The 13 copulas to port (in alphabetical order — pick any order)

1. AliMikhailHaqCopula — `theta` param, formula uv / (1 - theta(1-u)(1-v))
2. ClaytonCopula — `theta` param, formula (max(u^-theta + v^-theta - 1, 0))^(-1/theta)
3. FarlieGumbelMorgensternCopula — `theta` param, formula uv + theta*u*v*(1-u)*(1-v)
4. FrankCopula — `theta` param, formula uses log + exp
5. GalambosCopula — `theta` param, extreme-value copula
6. GaussianCopula — `rho` param, formula uses bivariate normal CDF (use existing Java `BivariateCumulativeNormalDistribution`)
7. GumbelCopula — `theta >= 1` param, formula exp(-((-log u)^theta + (-log v)^theta)^(1/theta))
8. HuslerReissCopula — `theta` param, extreme-value, uses Phi (normal CDF)
9. IndependentCopula — no params, formula C(u, v) = u * v
10. MarshallOlkinCopula — `alpha, beta` params (2-arg ctor)
11. MaxCopula — no params, formula min(u, v) [upper Fréchet-Hoeffding bound]
12. MinCopula — no params, formula max(u + v - 1, 0) [lower Fréchet-Hoeffding bound]
13. PlackettCopula — `theta` param, formula via quadratic

---

## Final task: Sanity sweep

- [ ] **Step 1: Run all copula tests together**

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest='*Copula*Test'
# Expected: 26/0/0/0 BUILD SUCCESS (13 copulas × 2 tests each)
```

- [ ] **Step 2: Verify no regression in dependent tests**

```bash
mvn -pl ../jquantlib test -Dtest='*LatentModel*Test,*GaussianCopula*Test,*Credit*Test'
# Expected: all green (these use GaussianCopula via Java's existing infrastructure)
```

- [ ] **Step 3: Push branch**

```bash
git push origin d5-B-yield-vanilla
```

---

## Self-review

1. **Spec coverage:** 13 copulas listed match the L1-B scope in `phase2-l1-plan.md`. ✓
2. **Placeholder scan:** Test code uses placeholder `/* params */` and `/* C++ formula in Java */` markers — implementer MUST replace per copula from C++ source. The probe-expected value placeholder is intentional (must come from running C++).
3. **Type consistency:** All copulas implement the single `Copula` interface from Task 0; constructors take `final double` parameters; `apply(double u, double v)` returns `double`. ✓
4. **TDD discipline:** Each copula has 5-step Red-Green-Commit cycle. ✓
5. **Note for implementer:** The "Expected pulled from C++ probe" expects the implementer to either (a) run the existing C++ test-suite if `copulas.cpp` has a test, OR (b) write a tiny one-off C++ probe that prints `cout << XCopula(0.5)(0.3, 0.6)` and use that as the expected value. Either way: NO hand-derived expected values — must come from C++.

---

## Definition of done

- 13 copulas + 1 Copula SAM interface ported
- 26+ tests passing (2 per copula minimum)
- Full suite still 3270+/0/0 baseline
- Commits use `-s` sign-off, no `Co-authored-by`, per-copula commit messages

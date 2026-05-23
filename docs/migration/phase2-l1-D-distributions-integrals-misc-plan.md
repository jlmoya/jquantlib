# L1-D distributions + integrals + statistics + matrixutilities + ode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port 26 math classes spanning distributions, integrals, statistics,
matrix utilities, and ODE wrappers from C++ v1.42.1 to Java with TDD
cross-validation.

**Architecture:** Mixed bag of helper classes — chi-square distributions
plug into existing `Distribution` interface; Gauss quadrature variants
plug into existing `GaussianQuadrature`; matrix-utility result records
are pure DTOs; OdeFctWrapper adapts a function for AdaptiveRungeKutta.

**Tech Stack:** JDK 25, Maven, JUnit 4.13.2. Heavy use of JDK 25 records
for the result-DTO classes (BiCGStabResult, GMRESResult).

---

## Scope

### math/distributions (8)
1. **BivariateCumulativeNormalDistributionWe04DP** — `bivariatenormaldistribution.hpp` (~150 LOC) — West 2004 Drezner-Wesolowsky combined algorithm
2. **CumulativeChiSquareDistribution** — `chisquaredistribution.hpp` — `1 - regularizedGamma(k/2, x/2)`
3. **CumulativeGammaDistribution** — `gammadistribution.hpp` — `regularizedGamma(a, x)`
4. **InverseNonCentralCumulativeChiSquareDistribution** — `chisquaredistribution.hpp`
5. **MaddockCumulativeNormal** — `normaldistribution.hpp` — Maddock-impl alternative
6. **MaddockInverseCumulativeNormal** — `normaldistribution.hpp` — Maddock inverse alternative
7. **NonCentralCumulativeChiSquareDistribution** — `chisquaredistribution.hpp` — non-central χ² CDF
8. **NonCentralCumulativeChiSquareSankaranApprox** — `chisquaredistribution.hpp` — Sankaran's approximation

### math/integrals (11)
9. **DiscreteSimpsonIntegrator** — `discreteintegrals.hpp`
10. **DiscreteTrapezoidIntegral** — `discreteintegrals.hpp`
11. **GaussChebyshev2ndIntegration** — `gaussianquadratures.hpp`
12. **GaussChebyshevIntegration** — `gaussianquadratures.hpp`
13. **GaussGegenbauerIntegration** — `gaussianquadratures.hpp`
14. **GaussGegenbauerPolynomial** — `gaussianorthogonalpolynomial.hpp`
15. **GaussHyperbolicIntegration** — `gaussianquadratures.hpp`
16. **GaussJacobiIntegration** — `gaussianquadratures.hpp`
17. **GaussianQuadratureIntegrator** — `gaussianquadratures.hpp` — wrapper class
18. **MidPoint** / **Default** — `trapezoidintegral.hpp` — small static tag types (verify if they have any Java-meaningful surface)

### math/statistics (2)
19. **DoublingConvergenceSteps** — `convergencestatistics.hpp`
20. **StatsHolder** — `gaussianstatistics.hpp`

### math/matrixutilities (4)
21. **BiCGStabResult** — `bicgstab.hpp` — result record (`{Size iterations; Real error; Array x;}`)
22. **GMRESResult** — `gmres.hpp` — result record
23. **FrobeniusCostFunction** — `tapcorrelations.hpp`
24. **SalvagingAlgorithm** — `pseudosqrt.hpp` — enum (Spectral, Hypersphere, etc.)

### math/ode (1)
25. **OdeFctWrapper** — `adaptiverungekutta.hpp` — adapts a scalar function to the vector-state ODE callback shape

---

## File Structure

**Created** (~25 production + ~25 test files):
Mirror C++ subpackage structure under `jquantlib/src/main/java/org/jquantlib/math/...`.

**C++ sources** (read-only):
- `migration-harness/cpp/quantlib/ql/math/{distributions,integrals,statistics,matrixutilities,ode}/*.hpp`
- `migration-harness/cpp/quantlib/test-suite/{distributions,integrals,statistics,matrices,ode}.cpp`

---

## Per-class task template

Same 5-step TDD cycle as L1-B and L1-C plans. For each class:
1. Read C++ source
2. Write failing test (expected from C++ test-suite or one-off probe)
3. Verify fail
4. Implement
5. Verify pass
6. Commit per logical batch with `-s` sign-off

JDK 25 features to use naturally:
- **Records for result DTOs**: `BiCGStabResult`, `GMRESResult`
- **Enum for SalvagingAlgorithm**: explicit values mirroring C++
- **Switch expressions in dispatch sites**

---

## Final sweep

- [ ] **Final 1: Targeted test sweep**

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest='*ChiSquare*Test,*GammaDistribution*Test,*Maddock*Test,*Bivariate*Test,*GaussChebyshev*Test,*GaussGegenbauer*Test,*GaussHyperbolic*Test,*GaussJacobi*Test,*Discrete*Integral*Test,*DiscreteSimpson*Test,*DoublingConvergence*Test,*StatsHolder*Test,*BiCGStab*Test,*GMRES*Test,*Frobenius*Test,*Salvaging*Test,*OdeFct*Test'
# Expected: 25+ tests / 0 failures
```

- [ ] **Final 2: Regression check on dependent tests**

```bash
mvn -pl ../jquantlib test -Dtest='*Heston*Test,*PseudoSqrt*Test,*RungeKutta*Test,*Quadrature*Test,*Integrator*Test'
# Expected: green
```

- [ ] **Final 3: Push branch**

```bash
git push origin d5-D-marketmodel
```

---

## Self-review

1. **Spec coverage:** 25 classes match L1-D scope (8 distributions + 11 integrals + 2 statistics + 4 matrixutilities + 1 ode = 26). One discrepancy — the `MidPoint`/`Default` tag types in `trapezoidintegral.hpp` may or may not warrant separate files; implementer can decide based on usage.
2. **Placeholder scan:** Per-class test code is a template; implementer fills in actual C++-derived expected values.
3. **Type consistency:** All Distribution classes implement existing `Distribution`/`CumulativeDistribution` interface; Integration classes extend existing `GaussianQuadrature` family; Result classes are records.
4. **Probe note:** Bivariate normal We04DP needs high-precision (~1e-15) cross-validation against C++ output since downstream basket pricing depends on it.

---

## Definition of done

- 25 classes ported (or annotated as no-Java-analog with rationale)
- Targeted tests pass 25+/0/0
- Regression on Heston/PseudoSqrt/RungeKutta green
- Commits `-s` signed, per-class batched
- Branch pushed

# Phase 2 forward closure — L1 (math/utilities/time/patterns) plan

**Date:** 2026-05-22
**Predecessor:** `jquantlib-jdk25-modernized-w1-w4` @ `683bf14e`
**Base:** JDK 25 LTS, suite 3270/0/0/24, modernized W1-W4 landed

## Audit-refined scope

After filtering out 558 noise items from the original 690 "missing"
(case-mismatch already-present classes like `BFGS`→`Bfgs`, C++ template
helpers like `Impl`/`_holder`/`_visitor`/`Singleton`/`Tracing`/`Null`),
**132 genuinely missing classes** remain in L1:

| Sub-package | Count | Note |
|---|---|---|
| math/interpolations | 61 | Many are inner helpers — Phase 1.1-B already ported 10 ConvexMonotone helpers as inner classes. Needs deeper per-class audit. |
| math/copulas | 13 | Standalone copula family |
| math (root) | 12 | Rounding (4 variants), BernsteinPolynomial, PascalTriangle, etc. |
| math/integrals | 11 | Gauss quadrature variants + DiscreteSimpson/Trapezoid |
| math/distributions | 8 | Chi-square family + Maddock normal |
| math/randomnumbers | 6 | BoxMuller, Knuth, Lecuyer, Ranlux64, Burley2020 Sobol |
| math/optimization | 6 | SimulatedAnnealing, GoldsteinLineSearch, DifferentialEvolution |
| time/calendars | 6 | Austria, Botswana, Chile, France, Romania, Thailand |
| math/matrixutilities | 4 | GMRES/BiCGStab result helpers, FrobeniusCostFunction |
| math/statistics | 2 | StatsHolder, DoublingConvergenceSteps |
| math/ode | 1 | OdeFctWrapper |
| time/daycounters | 1 | OneDayCounter |
| patterns | 1 | Singleton |

## Clusters

- **L1-A**: time + simple math (pilot — calendars + day counter + rounding + BernsteinPolynomial + PascalTriangle, ~13 classes)
- **L1-B**: math/copulas (13)
- **L1-C**: math/optimization + randomnumbers (12)
- **L1-D**: math/distributions + integrals + statistics + matrixutilities + ode (~26)
- **L1-E**: math (root) + math/interpolations (audit-first — 73 entries)

## Sequencing

1. L1-A first (pilot validates workflow on modernized base)
2. L1-B, L1-C, L1-D in parallel (3 worktrees)
3. L1-E audit + dispatch last

## Discipline

Each cluster: implementer subagent → spec-compliance reviewer →
pr-review-toolkit:code-reviewer → fix-up loop → FF-merge.

## Definition of done

- All L1 genuinely-missing classes ported OR annotated as no-Java-analog
- Full suite still 3270+/0/0 baseline
- Tag `jquantlib-phase2-l1-complete`

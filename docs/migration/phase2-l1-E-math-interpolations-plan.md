# L1-E math root + math/interpolations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> to implement this plan task-by-task.

**Goal:** Port 36 math classes (8 math-root + 28 math-interpolations factories
and helpers) from C++ v1.42.1 to Java, with TDD cross-validation.

**Architecture:** Most interpolation entries are factory classes that configure
the existing Java `CubicInterpolation` framework with specific (Derivative,
BoundaryCondition) settings. A handful (`MixedInterpolation`, `Abcd`, `Zabr`,
`UpdatedYInterpolation`) are genuinely new top-level interpolators.

**Tech Stack:** JDK 25, Maven, JUnit 4.13.2.

---

## Scope

### math root (8)
1. **AbcdMathFunction** — `abcdmathfunction.hpp` (~100 LOC) — `f(t) = (a + b*t) * exp(-c*t) + d`
2. **PolynomialFunction** — `polynomialmathfunction.hpp` (~80 LOC) — power-series with coefficients
3. **LinearFct + LinearFcts** — `linearleastsquaresregression.hpp` — functor helpers (verify if already covered by Java `LinearLeastSquaresRegression`)
4. **Solver1D** — `solver1d.hpp` — interface class; Java has concrete solvers but verify the umbrella interface exists
5. **earlier_than** — `comparison.hpp` — Date comparator helper (Java may have via `Comparator<Date>`)
6. **Foo** — `solver1d.hpp` — Singleton<Foo> template usage; SKIP unless Java needs it

### math/interpolations (28)
**Factory-style cubic spline variants** (likely just factory classes over existing `CubicInterpolation`):
- AkimaCubicInterpolation, CubicNaturalSpline, MonotonicCubicNaturalSpline, MonotonicParabolic, Parabolic, KrugerCubic, HarmonicCubic, CubicSplineOvershootingMinimization1, CubicSplineOvershootingMinimization2

**Log-cubic family** (cubic on log-transformed y, similar factory pattern):
- LogCubicNaturalSpline, MonotonicLogCubicNaturalSpline, KrugerLogCubic, HarmonicLogCubic, FritschButlandLogCubic, LogParabolic, MonotonicLogParabolic

**Mixed-linear-cubic family**:
- LogMixedLinearCubic, LogMixedLinearCubicInterpolation, LogMixedLinearCubicNaturalSpline, MonotonicLogMixedLinearCubic, KrugerLogMixedLinearCubic, MixedInterpolation

**Other interpolators**:
- Abcd + AbcdInterpolation — abcd-form fitter (CapHelper-related)
- Bicubic — `bicubicsplineinterpolation.hpp` factory wrapper
- BackwardflatLinearInterpolation
- UpdatedYInterpolation — `lagrangeinterpolation.hpp` updater
- Zabr — `zabrinterpolation.hpp` SABR variant interpolator

---

## File Structure

**Created** — paths follow existing Java interpolation idiom:
- `jquantlib/src/main/java/org/jquantlib/math/AbcdMathFunction.java`
- `jquantlib/src/main/java/org/jquantlib/math/PolynomialFunction.java`
- `jquantlib/src/main/java/org/jquantlib/math/interpolations/AkimaCubicInterpolation.java`
- `jquantlib/src/main/java/org/jquantlib/math/interpolations/factories/AkimaCubic.java` (if factory needed)
- ...one per class for the 28 interpolations
- Tests in `jquantlib/src/test/java/org/jquantlib/testsuite/math/...`

**C++ sources** (read-only):
- `migration-harness/cpp/quantlib/ql/math/{abcdmathfunction,polynomialmathfunction,linearleastsquaresregression,solver1d,comparison}.hpp`
- `migration-harness/cpp/quantlib/ql/math/interpolations/*.hpp`

---

## Process

For each class:
1. Read C++ source to understand the factory's settings vs new logic
2. Check if Java already exposes the variant via existing class (e.g., `CubicInterpolation(NotAKnot, Akima)` may already work)
3. If just-a-factory: create thin factory class
4. If new logic: implement with TDD cross-validation against C++ probe values
5. Commit per logical batch with `-s` sign-off

**Heuristic for factory vs new**: If C++ class is a one-line subclass like
```cpp
class AkimaCubicInterpolation : public CubicInterpolation {
public:
    template <class X, class Y>
    AkimaCubicInterpolation(X x_begin, X x_end, Y y_begin)
      : CubicInterpolation(x_begin, x_end, y_begin, Spline, false, Akima, 0, Akima, 0) {}
};
```
then Java factory class is similarly trivial. Skip with rationale if Java users can already call `new CubicInterpolation(x, y, Akima, ...)` directly.

---

## Constraints

- No `Co-authored-by`. `-s` sign-off.
- BUDGET: <90 min wall time. Many are 1-line factory classes.
- JDK 25 idioms naturally.
- DO NOT add @Ignore'd placeholders.
- Cross-validate ALL interpolators with C++ probe values at a few representative grids.
- Skip entries where Java already has the equivalent via existing constructor.

---

## Final sweep

```bash
cd jquantlib-parent && mvn -pl ../jquantlib test -Dtest='*Cubic*Test,*Spline*Test,*Interpolation*Test,*AbcdMath*Test,*Polynomial*Test,*Solver1D*Test'
# Expected: green
mvn -pl ../jquantlib test -Dtest='*PiecewiseYieldCurve*Test,*PiecewiseDefault*Test,*PiecewiseZero*Test'
# Expected: no regression — these depend on cubic interpolation
git push origin d5-E-yield-misc
```

## Definition of done

- 36 items ported OR annotated as already-present (via existing CubicInterpolation, etc.)
- Targeted tests pass
- No regression in PiecewiseYieldCurve tests

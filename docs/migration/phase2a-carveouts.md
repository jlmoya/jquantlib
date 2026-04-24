# Phase 2a — Carveouts

**Last update:** 2026-04-24

Items originally scoped into Phase 2a that revealed out-of-scope
downstream issues on un-skipping and have been deferred.

---

## WI-2-carveout-SABR — SABRInterpolation β-range failure

**Tests:**
- `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/SABRInterpolationTest.java::testSABRInterpolationTest`
- `jquantlib/src/test/java/org/jquantlib/testsuite/math/interpolations/InterpolationTest.java::testSabrInterpolation`

**Observed:**
Both tests — un-skipped in Phase 2a Task 2.8/2.9 after
`Minpack.lmdif` landed — fail with
`Library beta must be in (0.0, 1.0)` thrown from
`Sabr.validateSabrParameters` (line 124). The thrown value of `β`
is outside `[0, 1]`, which means the LM optimizer converges to (or
the initial-guess path feeds) a SABR β outside the legal range.

**Root cause (hypothesis, unconfirmed):**
Not in the MINPACK port. The C++ `SABRInterpolation` maintains a
parameters transformation (`SabrParametersTransformation` in the
Java file, lines 399+) that maps unconstrained LM iterates onto the
legal SABR parameter cube via `atan`/`tan`. The Java port's
transformation path may be incomplete or disconnected — note the
commented-out `y_(1) = std::atan(dilationFactor_*x(1))/M_PI + 0.5`
hint at line 412 of `SABRInterpolation.java`. Needs audit.

**Disposition:**
Re-gated with `@Ignore` + pointer to this file. LM itself verified
independently via `LevenbergMarquardtTest` and
`MinpackTest#lmdif_*`. Phase 2b (or a dedicated SABR pass).

---

## WI-2-carveout-simplex — OptimizerTest Simplex dimension bug

**Test:**
- `jquantlib/src/test/java/org/jquantlib/testsuite/math/optimization/OptimizerTest.java::testOptimizers`

**Observed:**
Un-skipped in Phase 2a Task 2.10 after LM gate lifted. Fails with
`IllegalArgumentException: Independent variable must be 1 dimensional`
at line 116, before LM even gets a chance to run.

**Root cause:**
The test's active optimization-method list is declared at line 104:
`final OptimizationMethodType optimizationMethodTypes[] = { OptimizationMethodType.simplex };`
— only Simplex. The `levenbergMarquardt` entry is commented out in
the same line. So the failing optimizer is `Simplex`, not LM.

The error comes from how `Simplex` constructs its internal Array
from a scalar-valued 1D problem (`OneDimensionalPolynomDegreeN`) —
an integration mismatch with the `Array(0)` zero-size construction
used at line 91. Pre-existing, unrelated to Phase 2a.

**Disposition:**
Re-gated with `@Ignore` + pointer to this file. Phase 2b (Simplex
cleanup or OptimizerTest rework). Note that the commented-out
LM line should be uncommented once the Simplex path is fixed, so
the full optimizer matrix runs.

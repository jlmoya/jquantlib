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

## WI-4-carveout-Vasicek — Parameter-reference semantics drift

**File:** `jquantlib/src/main/java/org/jquantlib/model/shortrate/onefactormodels/Vasicek.java`

**Observed:**
The WI-4 audit sweep turned up a real (not Phase-1-cosmetic)
divergence in the Vasicek constructor. QuantLib C++ uses the
initializer-list `a_(arguments_[0])`, `b_(arguments_[1])`, … which
binds the member `Parameter&` references to slots in the
`arguments_` vector; the subsequent `a_ = ConstantParameter(...)`
assignments then update `arguments_[i]` through those references.

The Java port (v0) assigned copies: `this.a_ = arguments_.get(0);`
then `this.a_ = new ConstantParameter(...)`. No reference semantics
— the two copies diverge, leaving `arguments_[i]` stuck at whatever
default the super(4) init left behind. The pre-existing Phase-1
"Seems to be non-sense!" marker flagged this correctly.

**Impact:**
Latent for callers that don't touch the calibratable parameter
vector. Visible only when Vasicek is calibrated via
`OneFactorAffineModel.calibrate`, which feeds values through
`arguments_` — and CapHelper + G2 (already Phase-2b carveouts) are
the typical calibration clients. No current JQuantLib test is
believed to calibrate Vasicek directly.

**Fix (Phase 2b):**
Correct Java port requires an indirection wrapper (e.g., each
`Parameter` member becomes a small getter that reads
`arguments_[i]`) or, more invasively, a redesigned `Parameter`
class with an indirection field. This ripples into HullWhite,
BlackKarasinski, CoxIngersollRoss — all share the same pattern. A
single-shot fix across one-factor models is Phase 2b scope.

**Disposition (Phase 2a):**
Dead `this.a_ = arguments_.get(0)` lines removed; an explanatory
comment in the constructor points callers at this doc; dead
Parameter values no longer confuse readers. Behavior for the
non-calibration code path is unchanged (the `ConstantParameter`
writes already came through). Phase-1 numerical_suspect marker
cleared.

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
Fixed in Phase 2b WI-2:
- Simplex 1D-dim bug root-caused and fixed in commit `f593de6` (the
  test's `initialValue` was built via the non-mutating `Array.add(double)`
  and the size-1 result was discarded; aligned to C++
  `test-suite/optimizers.cpp:231-232` idiom `new Array(new double[]{-100.0})`).
- `OptimizerTest#testOptimizers` un-skipped in this commit, with the
  `LevenbergMarquardt` entry uncommented in the `optimizationMethodTypes`
  matrix. Both Simplex and LM now run; `Skipped` 25 → 24.

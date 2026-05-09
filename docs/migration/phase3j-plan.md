# Phase 3j Implementation Plan

> Three tracks: L0 prereqs (blocking), Track A (core models + adapters), Track B (calibration framework).
> Track A and Track B run in parallel after L0. Tag `jquantlib-phase3j-complete`.

**Goal:** Port `ql/models/marketmodels/models/` — 18 C++ class pairs, ~4,674 LOC C++. Enable Phase 3k products.

---

## L0 — Prereqs + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3j-A /Users/josemoya/eclipse-workspace/jquantlib-3j-A main
git worktree add -b phase-3j-B /Users/josemoya/eclipse-workspace/jquantlib-3j-B main
cd /Users/josemoya/eclipse-workspace/jquantlib-3j-A && git submodule update --init --recursive
cd /Users/josemoya/eclipse-workspace/jquantlib-3j-B && git submodule update --init --recursive
```

**Baseline check (both worktrees):**
```bash
mvn -pl jquantlib test   # must pass at 1087+/0/0/38 (or Phase 3i tip if in flight)
```

---

### L0.1 — PiecewiseConstantVariance (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ql/models/marketmodels/models/piecewiseconstantvariance.hpp/.cpp` (100 LOC)

**What to port:**
- Abstract class `PiecewiseConstantVariance` with abstract methods: `variances()`, `volatilities()`, `rateTimes()`
- Concrete methods: `variance(int i)`, `volatility(int i)`, `totalVariance(int i)`, `totalVolatility(int i)`
- `totalVariance(i)` = sum of `variances()[0..i]`; `totalVolatility(i)` = sqrt(totalVariance(i))

**Note:** Create new Java package `org.jquantlib.model.marketmodels.models`. This class is the foundation for all calibration and variance classes.

**Test:** `PiecewiseConstantVarianceTest` — instantiate via anonymous subclass; verify totalVariance/totalVolatility for a 3-step variance array. Tolerance: exact (integer sums) / 1e-12 (sqrt).

**Commit:** `port(model.marketmodels.models): PiecewiseConstantVariance abstract base (Phase 3j L0.1)`

---

### L0.2 — AbcdFunction termstructure (`org.jquantlib.termstructures.volatility`)

**C++ source:** `ql/termstructures/volatility/abcd.hpp/.cpp` (216 LOC)

**What to port:**
- `AbcdFunction` — stores (a, b, c, d) ABCD parameters; computes `operator()(T, t)` = instantaneous vol
- `AbcdFunction.covariance(t1, t2, T)` — integrated covariance
- `AbcdFunction.volatility(tMin, tMax, T)` — RMS volatility over period
- Validation: `validateAbcdParameters(a, b, c, d)` — checks d > 0, d+a > 0, c > 0

**Note:** This is **not** `AbcdCalibration` (which requires Levenberg-Marquardt and lives in
`abcdcalibration.*`). Only port `AbcdFunction` itself. If `volatility/abcd.hpp` pulls in
`AbcdCalibration`, check carefully — the calibration class is declared separately.

**Check prerequisite:** Verify `ql/termstructures/volatility/abcd.hpp` does not include calibration
machinery. If AbcdCalibration is required, scope-adjust and pause per P3J-D trigger.

**Test:** `AbcdFunctionTest` — compute `operator()(0.5, 0.25)`, `volatility(0, 1.0, 1.0)`, `covariance(0, 1.0, 0.5, 1.0)` against C++ probe. Tolerance: tight (1e-12 rel).

**Commit:** `port(termstructures.volatility): AbcdFunction — ABCD instantaneous vol (Phase 3j L0.2)`

---

### L0.3 — Quadratic (`org.jquantlib.math`)

**C++ source:** `ql/math/quadratic.hpp` (48 LOC — header-only in practice)

**What to port:**
- `Quadratic(double a, double b, double c)`
- `turningPoint()` — returns `-b/(2a)`
- `valueAtTurningPoint()` — evaluates at turning point
- `apply(double x)` — returns `a*x^2 + b*x + c`
- `discriminant()` — `b^2 - 4*a*c`
- `roots(double[] out)` — returns false if complex roots; puts real roots in out[0], out[1]

**Note:** Small utility; port as final class with no inheritance.

**Test:** `QuadraticTest` — verify roots of `x^2 - 5x + 6 = 0` (roots 2, 3), discriminant, turningPoint. Tolerance: tight (1e-14).

**Commit:** `port(math): Quadratic — quadratic formula utility (Phase 3j L0.3)`

---

### L0.5 — SwapForwardMappings.swaptionImpliedVolatility tail

**C++ source:** `ql/models/marketmodels/swapforwardmappings.hpp/.cpp` (partial — previously deferred from Phase 3h A.8)

**What to port:** Complete the `swaptionImpliedVolatility` static method in the existing Java
`SwapForwardMappings.java`. This method was stubbed/left incomplete because it depends on
`PiecewiseConstantVariance` (now provided by L0.1).

**Check first:** Read current state of `SwapForwardMappings.java` to confirm which methods are missing.

**Commit:** `port(model.marketmodels): SwapForwardMappings.swaptionImpliedVolatility (Phase 3j L0.5)`

---

After L0 commits are on `main`, dispatch Track A and Track B in parallel worktrees.

---

## Track A — Core Models + Adapters

*(Track A worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3j-A`)*
*(Pull from main after each L0 commit before starting A.1)*

### A.1 — FlatVol + FlatVolFactory (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ql/models/marketmodels/models/flatvol.hpp/.cpp` (337 LOC)

**What to port:**
- `FlatVol extends MarketModel` — constructor:
  `(double[] volatilities, PiecewiseConstantCorrelation corr, EvolutionDescription evolution, int numberOfFactors, double[] initialRates, double[] displacements)`
- Constructor body: for each step, form `(n×nFactors)` pseudo-root from vol × Cholesky of corr matrix; calls `PseudoSqrt.rankReducedSqrt(corrMatrix, numberOfFactors, 1.0, SalvagingAlgorithm.None)`
- All `MarketModel` overrides: trivial accessors returning stored fields
- `FlatVolFactory extends MarketModelFactory implements Observer`:
  `(double longTermCorr, double beta, double[] times, double[] vols, Handle<YieldTermStructure> yieldCurve, double displacement)`
  - `create(EvolutionDescription, int numberOfFactors)` — builds ExponentialForwardCorrelation + linear-interpolated vol + calls FlatVol constructor
  - `update()` — invalidates interpolation, recomputes on next create()

**Note:** `FlatVol` constructor internally uses `ExponentialForwardCorrelation` for the correlation argument in `FlatVolFactory.create()`. The correlation is passed externally in the direct constructor.

**Test:** `FlatVolTest` — construct with 3-rate grid, flat vol=0.2, ExponentialForwardCorrelation (longTermCorr=0.1, beta=0.1); verify `pseudoRoot(0)` entries against C++ probe. Tolerance: tight (1e-12 rel).

**Commit:** `port(model.marketmodels.models): FlatVol + FlatVolFactory (Phase 3j A.1)`

---

### A.2 — AbcdVol (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ql/models/marketmodels/models/abcdvol.hpp/.cpp` (237 LOC)

**What to port:**
- `AbcdVol extends MarketModel` — constructor:
  `(double a, double b, double c, double d, double[] ks, PiecewiseConstantCorrelation corr, EvolutionDescription evolution, int numberOfFactors, double[] initialRates, double[] displacements)`
- Constructor body: for each step and each rate, compute vol from `AbcdFunction(a,b,c,d)(timeToReset, rateTime)` scaled by `ks[i]`; then build pseudo-root via PseudoSqrt
- All `MarketModel` overrides: trivial accessors

**Note:** Depends on L0.2 `AbcdFunction`. The `ks` vector scales the ABCD vol per rate (rate-dependent multiplier).

**Test:** `AbcdVolTest` — construct with a=0, b=0, c=1, d=1 (flat vol=1 everywhere), 3-rate grid; verify pseudo-root matches FlatVol(vol=1.0) result. Tolerance: tight (1e-12).

**Commit:** `port(model.marketmodels.models): AbcdVol (Phase 3j A.2)`

---

### A.3 — FwdPeriodAdapter + FwdPeriodAdapterFactory (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ql/models/marketmodels/models/fwdperiodadapter.hpp/.cpp` (218 LOC)

**What to port:**
- `FwdPeriodAdapter extends MarketModel` — constructor:
  `(MarketModel largeModel, int period, int offset, double[] newDisplacements)`
- Maps large-grid pseudo-roots to coarser period grid by accumulating Cholesky factors
- All `MarketModel` overrides

**Test:** `FwdPeriodAdapterTest` — wrap a 6-rate FlatVol as a 3-rate period-2 model; verify `numberOfRates()` = 3, `pseudoRoot(0)` dimensions. Tolerance: tight (1e-12 rel).

**Commit:** `port(model.marketmodels.models): FwdPeriodAdapter (Phase 3j A.3)`

---

### A.4 — CotSwapToFwdAdapter + Factory (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ql/models/marketmodels/models/cotswaptofwdadapter.hpp/.cpp` (205 LOC)

**What to port:**
- `CotSwapToFwdAdapter extends MarketModel` — wraps a coterminal-measure model, converts pseudo-roots to forward measure using `SwapForwardMappings` Jacobian
- `CotSwapToFwdAdapterFactory implements MarketModelFactory, Observer`

**Note:** Verify `SwapForwardMappings.coterminalToCap()` or equivalent is available in the Java class (from Phase 3h partial port). If missing, add that method before this WI.

**Commit:** `port(model.marketmodels.models): CotSwapToFwdAdapter + Factory (Phase 3j A.4)`

---

### A.5 — FwdToCotSwapAdapter + Factory (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ql/models/marketmodels/models/fwdtocotswapadapter.hpp/.cpp` (205 LOC)

**What to port:** Inverse of A.4 — wraps a forward-measure model, converts pseudo-roots to coterminal swap measure.

**Test (combined A.4 + A.5):** `ModelAdaptersTest` — round-trip: `FlatVol → FwdToCotSwap → CotSwapToFwd`; verify recovered pseudo-root is close to original. Tolerance: tight (1e-10 rel, small accumulated Jacobian error acceptable).

**Commit:** `port(model.marketmodels.models): FwdToCotSwapAdapter + Factory (Phase 3j A.5)`

---

### A.6 — PseudoRootFacade (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ql/models/marketmodels/models/pseudorootfacade.hpp/.cpp` (171 LOC)

**What to port:**
- Two constructors:
  1. `(List<Matrix> covariancePseudoRoots, double[] rateTimes, double[] initialRates, double[] displacements)` — raw matrices path; can land immediately
  2. `(CTSMMCapletCalibration calibrator)` — extracts `swapCovariancePseudoRoots()` from calibrator; requires Track B B.3 to be on main first
- All `MarketModel` overrides

**Note:** Port constructor 1 first; add constructor 2 after B.3 is committed.

**Commit (two parts):**
- `port(model.marketmodels.models): PseudoRootFacade raw-matrix constructor (Phase 3j A.6a)`
- After B.3 lands: `port(model.marketmodels.models): PseudoRootFacade calibrator constructor (Phase 3j A.6b)`

---

## Track B — Calibration Framework

*(Track B worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3j-B`)*
*(Pull from main after each L0 commit before starting B.1)*

### B.1 — AlphaForm interface + AlphaFormConcrete (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `alphaform.hpp` (36 LOC) + `alphaformconcrete.hpp/.cpp` (108 LOC)

**What to port:**
- `AlphaForm` interface: `double apply(int i)` (maps C++ `operator()(Integer i)`), `setAlpha(double alpha)`
- `AlphaFormLinearHyperbolic extends AlphaForm` — `alpha * (i/(i+1))`-style form
- `AlphaFormInverseLinear extends AlphaForm`

**Test:** `AlphaFormTest` — verify `apply(5)` for both forms with known alpha. Tolerance: tight (1e-12).

**Commit:** `port(model.marketmodels.models): AlphaForm interface + AlphaFormLinearHyperbolic + AlphaFormInverseLinear (Phase 3j B.1)`

---

### B.2 — PiecewiseConstantAbcdVariance (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `piecewiseconstantabcdvariance.hpp/.cpp` (132 LOC)

**What to port:**
- `PiecewiseConstantAbcdVariance extends PiecewiseConstantVariance`
- Constructor: `(double a, double b, double c, double d, int resetIndex, double[] rateTimes)`
- Computes `variances_[i]` = `AbcdFunction(a,b,c,d).covariance(rateTimes[i], rateTimes[i+1], rateTimes[resetIndex])`
- `getABCD(double[] out)` — returns parameters

**Note:** Depends on L0.1 PiecewiseConstantVariance and L0.2 AbcdFunction.

**Test:** `PiecewiseConstantAbcdVarianceTest` — verify variances[] for a=0.1, b=0.2, c=1.5, d=0.1, resetIndex=0, 3-rate grid against C++ probe. Tolerance: tight (1e-12 rel).

**Commit:** `port(model.marketmodels.models): PiecewiseConstantAbcdVariance (Phase 3j B.2)`

---

### B.3 — CTSMMCapletCalibration abstract base (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `ctsmmcapletcalibration.hpp/.cpp` (398 LOC)

**What to port:**
- `CTSMMCapletCalibration` abstract base class
- Constructor: `(EvolutionDescription evolution, PiecewiseConstantCorrelation corr, List<PiecewiseConstantVariance> displacedSwapVariances, double[] mktCapletVols, CurveState cs, double displacement)`
- `calibrate(int numberOfFactors, int maxIterations, double tolerance, int innerMaxIterations, double innerTolerance)` — calls abstract `calibrationImpl_`
- All inspector methods: `failures()`, `deformationSize()`, `capletRmsError()`, `capletMaxError()`, `swaptionRmsError()`, `swaptionMaxError()`, `swapPseudoRoots()`, `swapPseudoRoot(int i)`, `mktCapletVols()`, `mdlCapletVols()`, `mktSwaptionVols()`, `mdlSwaptionVols()`, `curveState()`, `displacements()`
- Static `performChecks(...)` — validates inputs
- Abstract protected: `calibrationImpl_(int numberOfFactors, int innerMaxIterations, double innerTolerance) → int failures`
- Java: `calibrationImpl_` → `protected abstract int calibrationImpl(int nFactors, int innerMax, double innerTol)`

**Note:** `swapCovariancePseudoRoots_` is a `List<Matrix>` protected field (subclasses populate it). The `calibrated_` boolean guards inspector access.

**Test:** Subclass test deferred to B.4 (first concrete impl). This WI has a compile-only test: verify abstract class compiles and `performChecks` static validates correctly. Tolerance: N/A (no numerical output).

**Commit:** `port(model.marketmodels.models): CTSMMCapletCalibration abstract base (Phase 3j B.3)`

---

### B.4 — CTSMMCapletOriginalCalibration (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `capletcoterminalswaptioncalibration.hpp/.cpp` (425 LOC)

**What to port:**
- `CTSMMCapletOriginalCalibration extends CTSMMCapletCalibration`
- Constructor adds: `double[] alpha`, `boolean lowestRoot`, `boolean useFullApprox`
- Static `calibrationFunction(...)` — the main numerical calibration: for each rate, solve quadratic equation for pseudo-root entries matching target caplet variance
- `calibrationImpl_` delegates to `calibrationFunction`
- Uses `SwapForwardMappings` to map swap rates; uses `PseudoSqrt.rankReducedSqrt`

**Test:** `CTSMMCapletOriginalCalibrationTest` — run calibration on a 5-rate LMM with flat vols; verify `mdlCapletVols()` match `mktCapletVols()` within loose tolerance (1e-4 rel) against C++ probe.

**Commit:** `port(model.marketmodels.models): CTSMMCapletOriginalCalibration (Phase 3j B.4)`

---

### B.7 — AlphaFinder (`org.jquantlib.model.marketmodels.models`)

*(B.7 before B.5 because B.5 depends on AlphaFinder)*

**C++ source:** `alphafinder.hpp/.cpp` (659 LOC)

**What to port:**
- `AlphaFinder(AlphaForm parametricForm)` constructor
- `solve(double alpha0, int stepindex, double[] rateonevols, double[] ratetwohomogeneousvols, double[] correlations, double w0, double w1, double targetVariance, double tolerance, double alphaMax, double alphaMin, int steps, double[] alpha, double[] a, double[] b, double[] ratetwovols) → boolean`
- `solveWithMaxHomogeneity(...)` — variant maximizing homogeneity
- Private: `computeLinearPart`, `computeQuadraticPart`, `valueAtTurningPoint`, `finalPart`, etc.
- Uses L0.3 `Quadratic` for the polynomial solve

**Note:** C++ out-parameters (`Real& alpha`, `Real& a`, etc.) → wrap result in a small `AlphaFinderResult(boolean success, double alpha, double a, double b, double[] ratetwovols)` record, or use `double[]` single-element arrays for in/out semantics (existing JQuantLib pattern). Check existing conventions; use `double[]` array if that is the established pattern.

**Test:** `AlphaFinderTest` — solve with known inputs from C++ test-suite probe; verify alpha, a, b within tight tolerance (1e-10 rel). Cross-validate against `marketmodel_smmcapletalphacalibration.cpp` test data.

**Commit:** `port(model.marketmodels.models): AlphaFinder — alpha vol solver (Phase 3j B.7)`

---

### B.5 — CTSMMCapletAlphaFormCalibration (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `capletcoterminalalphacalibration.hpp/.cpp` (352 LOC)

**What to port:**
- `CTSMMCapletAlphaFormCalibration extends CTSMMCapletCalibration`
- Constructor adds: `double[] alphaInitial`, `double[] alphaMax`, `double[] alphaMin`, `boolean maximizeHomogeneity`, `AlphaForm parametricForm`
- Static `capletAlphaFormCalibration(...)` — the main function: iterates over rates using AlphaFinder
- `calibrationImpl_` delegates to static function
- Inspector: `alpha()` returns calibrated alpha vector

**Note:** Depends on B.7 (AlphaFinder). Can run in parallel with B.4 once B.7 is on main.

**Test:** `CTSMMCapletAlphaFormCalibrationTest` — run on a 5-rate grid; verify alpha[] values and mdlCapletVols against C++ probe. Tolerance: loose (1e-4 rel).

**Commit:** `port(model.marketmodels.models): CTSMMCapletAlphaFormCalibration (Phase 3j B.5)`

---

### B.6 — CTSMMCapletMaxHomogeneityCalibration (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `capletcoterminalmaxhomogeneity.hpp/.cpp` (492 LOC)

**What to port:**
- `CTSMMCapletMaxHomogeneityCalibration extends CTSMMCapletCalibration`
- Constructor adds: `double caplet0Swaption1Priority` (default 1.0)
- Static `capletMaxHomogeneityCalibration(...)` — uses `SphereCylinderOptimizer` and
  `BasisIncompleteOrdered` for constrained optimization
- `calibrationImpl_` delegates to static function; stores `totalSwaptionError_`

**Note:** Check Java `SphereCylinderOptimizer` method names before starting — C++ has `minimize(...)` with specific signature. Verify `BasisIncompleteOrdered` method calls match.

**Test:** `CTSMMCapletMaxHomogeneityCalibrationTest` — run on a 5-rate grid; verify `deformationSize()` and `mdlCapletVols` against C++ probe. Tolerance: loose (1e-4 rel).

**Commit:** `port(model.marketmodels.models): CTSMMCapletMaxHomogeneityCalibration (Phase 3j B.6)`

---

### B.8 + B.9 — VolatilityInterpolationSpecifier + AbcdImpl (`org.jquantlib.model.marketmodels.models`)

**C++ source:** `volatilityinterpolationspecifier.hpp` (58 LOC) + `volatilityinterpolationspecifierabcd.hpp/.cpp` (273 LOC)

**What to port:**
- `VolatilityInterpolationSpecifier` interface: `setScalingFactors(double[])`, `setLastCapletVol(double)`, `interpolatedVariances()`, `originalVariances()`, `getPeriod()`, `getOffset()`, `getNoBigRates()`, `getNoSmallRates()`
- `VolatilityInterpolationSpecifierAbcd extends VolatilityInterpolationSpecifier`

**Test:** `VolatilityInterpolationSpecifierTest` — construct AbcdImpl; verify `interpolatedVariances()` dimension and a few values against C++ probe. Tolerance: tight (1e-12 rel).

**Commit:** `port(model.marketmodels.models): VolatilityInterpolationSpecifier + AbcdImpl (Phase 3j B.8-B.9)`

---

### B.10 — capletSwaptionPeriodicCalibration (conditional)

**C++ source:** `capletcoterminalperiodic.hpp/.cpp` (246 LOC)

**What to port:**
- Free function `capletSwaptionPeriodicCalibration(...)` with full signature
- Iterates MaxHomogeneity calibration in a period structure using `VolatilityInterpolationSpecifier`
- Depends on `CotSwapFromFwdCorrelation` (Phase 3i)

**Condition:** Port this WI **only if** `CotSwapFromFwdCorrelation` is on main (Phase 3i complete).
Otherwise move to Phase 3j.5.

**Test:** `CapletCoterminalPeriodicTest` — run periodic calibration on a standard grid; verify `iterationsDone > 0` and `errorImprovement` converged. Tolerance: loose (1e-3 rel).

**Commit:** `port(model.marketmodels.models): capletSwaptionPeriodicCalibration (Phase 3j B.10)` *(or Phase 3j.5)*

---

## L1 — Integration verification

After all Track A and Track B committed WIs are on main:

1. Full compile: `mvn -pl jquantlib compile` — no errors in `org.jquantlib.model.marketmodels.models.*`
2. Baseline test: `mvn -pl jquantlib test` — 1115+/0/0/38
3. Focused tests:
```bash
mvn -pl jquantlib test -Dtest=PiecewiseConstantVarianceTest,AbcdFunctionTest,QuadraticTest,\
FlatVolTest,AbcdVolTest,ModelAdaptersTest,PseudoRootFacadeTest,\
AlphaFormTest,PiecewiseConstantAbcdVarianceTest,AlphaFinderTest,\
CTSMMCapletOriginalCalibrationTest,CTSMMCapletAlphaFormCalibrationTest,\
CTSMMCapletMaxHomogeneityCalibrationTest
```

---

## L2 — Completion + tag + memory + docs + teardown

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git tag jquantlib-phase3j-complete
git push origin jquantlib-phase3j-complete

# Update memory
# Update docs/migration/phase3j-completion.md
# Update README.md

# Remove worktrees
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-3j-A
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-3j-B
git branch -d phase-3j-A phase-3j-B
```

---

## Reference: C++ probes to generate

For migration-harness cross-validation, generate probes for:
1. `FlatVolProbe` — 3-rate grid (rateTimes=[0.5,1.0,1.5,2.0]), flat vol=0.2, ExponentialCorr (longTerm=0.1,beta=0.1), 2 factors; output: pseudoRoot(0) entries (6 doubles)
2. `AbcdVolProbe` — same grid, a=0.1,b=0.2,c=1.5,d=0.1, ks=[1.0,1.0,1.0]; output: pseudoRoot(0) entries
3. `PiecewiseConstantAbcdVarianceProbe` — a=0.1,b=0.2,c=1.5,d=0.1, resetIndex=0, rateTimes=[0.5,1.0,1.5,2.0]; output: variances[0..2], totalVariance(2)
4. `AlphaFinderProbe` — stepindex=0; 3 rates, flat rateonevols=0.2, ratetwohomogeneousvols=0.15, correlations=0.9, w0=1,w1=1, targetVariance=0.04; output: alpha, a, b, ratetwovols[0..2]
5. `CTSMMOriginalCalibrationProbe` — 5-rate standard grid from C++ test-suite; output: mdlCapletVols[0..4], failures count
6. `CTSMMAlphaFormCalibrationProbe` — same grid; output: alpha[0..4], mdlCapletVols[0..4]
7. `CTSMMMaxHomogeneityProbe` — same grid; output: deformationSize, mdlCapletVols[0..4]

All probes → `migration-harness/references/models/marketmodels/models/` JSON files.
Script addition: `migration-harness/cpp/probes/marketmodels_models_probe.cpp`

---

## Parallel dispatch note

**L0 is sequential** (blocking). Once all four L0 commits are on `main`:

- **Track A** (core models): worktree `jquantlib-3j-A`
  - A.1 → A.2 → A.3 → A.4 → A.5 → A.6a (independent after A.2 given AbcdFunction)
  - A.6b (PseudoRootFacade calibrator ctor) after B.3 lands
- **Track B** (calibration): worktree `jquantlib-3j-B`
  - B.1 and B.2 in parallel
  - B.3 after B.1 + B.2
  - B.7 after B.1 (AlphaFinder)
  - B.4, B.5 (after B.7), B.6 — three-way parallel after B.3
  - B.8 + B.9 in parallel with B.4/B.5/B.6
  - B.10 conditional on Phase 3i

**Critical path:** L0.1 → L0.2 → B.2 → B.3 → B.4 (deepest calibration chain)
**Longest Track A path:** L0.2 → A.2 → A.3 → A.4 → A.5

Full parallel two-agent dispatch is safe for Tracks A and B once L0 is complete.

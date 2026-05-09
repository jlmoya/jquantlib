# Phase 3h Implementation Plan

> Two parallel tracks (A: foundation+curvestates+drift, B: correlations+brownian+accounting). Merge to main after each WI. Tag `jquantlib-phase3h-complete`.

**Goal:** Port LMM foundation layer — 21 C++ classes, ~3 600 LOC C++. Enable Phase 3i evolvers.

## L0 — Pre-flight + worktree setup

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git fetch origin
git worktree add -b phase-3h-A /Users/josemoya/eclipse-workspace/jquantlib-3h-A main
git worktree add -b phase-3h-B /Users/josemoya/eclipse-workspace/jquantlib-3h-B main
cd /Users/josemoya/eclipse-workspace/jquantlib-3h-A
git submodule update --init --recursive
cd /Users/josemoya/eclipse-workspace/jquantlib-3h-B
git submodule update --init --recursive
```

**Baseline check (both worktrees):**
```bash
mvn -pl jquantlib test   # must pass at 1027/0/0/38 (or whatever tip shows)
```

---

## Track A — Foundation + CurveStates + DriftCalculators

### A.1 — Utilities (`org.jquantlib.model.marketmodels`)

**C++ source:** `ql/models/marketmodels/utilities.hpp/.cpp` (176 LOC)

**What to port:**
- `mergeTimes(List<List<Double>> times, List<Double> mergedTimes, List<boolean[]> isPresent)`
- `isInSubset(double[] set, double[] subset) → boolean[]`
- `checkIncreasingTimes(double[] times)`
- `checkIncreasingTimesAndCalculateTaus(double[] times, double[] taus)`

**Note:** `std::valarray<bool>` → Java `boolean[]`. `mergeTimes` modifies out-params → return a small result record or use `double[]` arrays passed by reference.

**Test:** `MarketModelUtilitiesTest` — exercise mergeTimes with known rate/evolution time grids from C++ test-suite setup. Tolerance: exact (boolean/integer results) or 1e-12 for tau values.

**Commit:** `port(model.marketmodels): Utilities — mergeTimes/isInSubset/checkIncreasingTimes (Phase 3h A.1)`

---

### A.2 — EvolutionDescription (`org.jquantlib.model.marketmodels`)

**C++ source:** `ql/models/marketmodels/evolutiondescription.hpp/.cpp` (309 LOC)

**What to port:**
- `EvolutionDescription` class with `rateTimes`, `evolutionTimes`, `relevanceRates`, `rateTaus`, `firstAliveRate`
- Numeraire helper functions: `checkCompatibility`, `isInTerminalMeasure`, `isInMoneyMarketPlusMeasure`, `isInMoneyMarketMeasure`
- Factory functions: `terminalMeasure`, `moneyMarketPlusMeasure`, `moneyMarketMeasure`

**Note:** `std::pair<Size,Size>` → `int[]` pair or a small `Range(int first, int second)` inner class.

**Test:** `EvolutionDescriptionTest` — construct with standard 5-rate grid; verify rateTaus, firstAliveRate, terminal/moneyMarket measure outputs against C++ probe values. Tolerance: exact (integer indices) / 1e-12 (time values).

**Commit:** `port(model.marketmodels): EvolutionDescription + numeraire helpers (Phase 3h A.2)`

---

### A.3 — CurveState base class (`org.jquantlib.model.marketmodels`)

**C++ source:** `ql/models/marketmodels/curvestate.hpp/.cpp` (234 LOC)

**What to port:** Fill the existing `CurveState.java` stub completely:
- Abstract methods: `discountRatio`, `forwardRate`, `coterminalSwapRate`, `coterminalSwapAnnuity`, `cmSwapRate`, `cmSwapAnnuity`, `forwardRates()`, `coterminalSwapRates()`, `cmSwapRates(int spanningForwards)`, `clone()`
- Concrete `swapRate(int begin, int end)`
- Free functions: `forwardsFromDiscountRatios`, `coterminalFromDiscountRatios`, `constantMaturityFromDiscountRatios`

**Test:** Verify `swapRate` formula at concrete subclass level in A.4.

**Commit:** `port(model.marketmodels): CurveState abstract base + free rate functions (Phase 3h A.3)`

---

### A.4 — LMMCurveState (`org.jquantlib.model.marketmodels.curvestates`)

**C++ source:** `ql/models/marketmodels/curvestates/lmmcurvestate.hpp/.cpp` (293 LOC)

**What to port:**
- `LMMCurveState extends CurveState`
- `setOnForwardRates(double[] fwdRates, int firstValidIndex)`
- `setOnDiscountRatios(double[] discRatios, int firstValidIndex)`
- All overrides: `discountRatio`, `forwardRate`, `coterminalSwapRate`, `coterminalSwapAnnuity`, `cmSwapRate`, `cmSwapAnnuity`, `forwardRates()`, `coterminalSwapRates()`, `cmSwapRates(int)`, `clone()`
- Mutable lazy-compute for coterminal/cm swap rates/annuities

**Test:** `LMMCurveStateTest` — set forward rates from C++ probe; verify discountRatios, swapRates, coterminalSwapRates against C++ reference values. Tolerance: tight (1e-12 rel).

**Commit:** `port(model.marketmodels.curvestates): LMMCurveState (Phase 3h A.4)`

---

### A.5 — CoterminalSwapCurveState (`org.jquantlib.model.marketmodels.curvestates`)

**C++ source:** `ql/models/marketmodels/curvestates/coterminalswapcurvestate.hpp/.cpp` (234 LOC)

**What to port:** `CoterminalSwapCurveState extends CurveState` — analogous to LMMCurveState but initialized on coterminal swap rates and annuities.

**Test:** `CoterminalSwapCurveStateTest` — probe vs C++ reference. Tolerance: tight.

**Commit:** `port(model.marketmodels.curvestates): CoterminalSwapCurveState (Phase 3h A.5)`

---

### A.6 — CMSwapCurveState (`org.jquantlib.model.marketmodels.curvestates`)

**C++ source:** `ql/models/marketmodels/curvestates/cmswapcurvestate.hpp/.cpp` (257 LOC)

**What to port:** `CMSwapCurveState extends CurveState` — initialized on constant-maturity swap rates.

**Test:** `CMSwapCurveStateTest` — probe vs C++ reference. Tolerance: tight.

**Commit:** `port(model.marketmodels.curvestates): CMSwapCurveState (Phase 3h A.6)`

---

### A.7 + A.8 — Mapping functions (`org.jquantlib.model.marketmodels`)

**C++ source:** `forwardforwardmappings.hpp/.cpp` (188 LOC) + `swapforwardmappings.hpp/.cpp` (312 LOC)

**What to port:** Static utility functions for rate mapping (coterminal/CMS/forward conversions). Port as two utility classes with static methods.

**Test:** Probe a few mapping values against C++ references. Tolerance: tight (1e-12).

**Commit:** `port(model.marketmodels): ForwardForwardMappings + SwapForwardMappings (Phase 3h A.7-A.8)`

---

### A.9 — MarketModelDifferences

**C++ source:** `marketmodeldifferences.hpp/.cpp` (165 LOC) — functions computing rate/price differences under curve state evolution.

**Commit:** `port(model.marketmodels): MarketModelDifferences (Phase 3h A.9)`

---

### A.10-A.13 — Drift Calculators (`org.jquantlib.model.marketmodels.driftcomputation`)

**C++ source:** 4 drift calculators, total 909 LOC (excluding all.hpp).

**A.10 — LMMDriftCalculator** (263 LOC)
- Constructor: `(Matrix pseudo, double[] displacements, double[] taus, int numeraire, int alive)`
- `compute(LMMCurveState, double[] drifts)` — reduced-factor fast path
- `computePlain(...)` — full covariance path
- `computeReduced(...)` — factor-reduced path

**A.11 — LMMNormalDriftCalculator** (241 LOC) — normal (additive) drift variant

**A.12 — SMMDriftCalculator** (197 LOC) — coterminal swap market model drift

**A.13 — CMSMMDriftCalculator** (208 LOC) — constant-maturity swap market model drift

**Test:** `DriftCalculatorTest` — compute drifts for a known pseudo-root from C++ test-suite references. Tolerance: tight (1e-12 rel) for LMM; tight for others.

**Commit (per calculator):**
- `port(model.marketmodels.driftcomputation): LMMDriftCalculator (Phase 3h A.10)`
- `port(model.marketmodels.driftcomputation): LMMNormalDriftCalculator (Phase 3h A.11)`
- `port(model.marketmodels.driftcomputation): SMMDriftCalculator (Phase 3h A.12)`
- `port(model.marketmodels.driftcomputation): CMSMMDriftCalculator (Phase 3h A.13)`

---

## Track B — Correlations + Brownian + Accounting

(Track B can run in parallel with A.7–A.13)

### B.1 — PiecewiseConstantCorrelation interface

**C++ source:** `piecewiseconstantcorrelation.hpp` (54 LOC)

**What to port:** Abstract interface `PiecewiseConstantCorrelation` — `times()`, `rateTimes()`, `correlations()`, `correlation(int i)`, `numberOfRates()`.

**Commit:** `port(model.marketmodels): PiecewiseConstantCorrelation interface (Phase 3h B.1)`

---

### B.2 — ExponentialCorrelations (`org.jquantlib.model.marketmodels.correlations`)

**C++ source:** `correlations/expcorrelations.hpp/.cpp` (210 LOC)

**What to port:** `ExponentialCorrelations implements PiecewiseConstantCorrelation` — exponential decay correlation structure `ρ(i,j) = longTermCorr + (1-longTermCorr)*exp(-β|i-j|)`.

**Test:** `CorrelationsTest` — verify correlation matrix values. Tolerance: tight.

**Commit:** `port(model.marketmodels.correlations): ExponentialCorrelations (Phase 3h B.2)`

---

### B.3 + B.4 — TimeHomogeneous + CotSwapFromFwd Correlations

**C++ source:** `timehomogeneousforwardcorrelation.hpp/.cpp` (140 LOC) + `cotswapfromfwdcorrelation.hpp/.cpp` (128 LOC)

**Commit:** `port(model.marketmodels.correlations): TimeHomogeneousForwardCorrelation + CotSwapFromFwdCorrelation (Phase 3h B.3-B.4)`

---

### B.5 — MarketModel abstract base

**C++ source:** `marketmodel.hpp/.cpp` (143 LOC)

**What to port:** Abstract class `MarketModel` — `initialRates()`, `displacements()`, `evolution()`, `numberOfRates()`, `numberOfFactors()`, `numberOfSteps()`, `pseudoRoot(int i)`, default-implemented `covariance(int i)`, `totalCovariance(int endIndex)`, `timeDependentVolatility(int i)`.

Also port `MarketModelFactory` interface.

**Note:** The covariance/totalCovariance computations use lazy-populated `mutable std::vector<Matrix>` fields — Java: compute on-demand with null-check or populate in constructor.

**Commit:** `port(model.marketmodels): MarketModel abstract base + MarketModelFactory (Phase 3h B.5)`

---

### B.6 — MarketModelDiscounter

**C++ source:** `discounter.hpp/.cpp` (100 LOC)

**What to port:** `MarketModelDiscounter(double paymentTime, double[] rateTimes)` — computes numeraire bond value from curve state.

**Commit:** `port(model.marketmodels): MarketModelDiscounter (Phase 3h B.6)`

---

### B.7 — MTBrownianGenerator (`org.jquantlib.model.marketmodels.browniangenerators`)

**C++ source:** `browniangenerators/mtbrowniangenerator.hpp/.cpp` (143 LOC)

**What to port:**
- Fix `BrownianGenerator` abstract: change `nextStep()` → `nextStep(double[] result)` to match C++ out-parameter signature
- `MTBrownianGenerator extends BrownianGenerator` — wraps `RandomSequenceGenerator<MersenneTwisterUniformRng>` + `InverseCumulativeNormal`
- `MTBrownianGeneratorFactory extends BrownianGeneratorFactory`

**Note:** This is the only WI that requires an API change to the existing `BrownianGenerator` stub. The change is correct-by-design (P3H-1).

**Commit:** `align(model.marketmodels): fix BrownianGenerator.nextStep() signature to nextStep(double[]) matching C++ out-param (Phase 3h B.7-align)`
then:
`port(model.marketmodels.browniangenerators): MTBrownianGenerator + MTBrownianGeneratorFactory (Phase 3h B.7)`

---

### B.8 — AccountingEngine (complete)

**C++ source:** `accountingengine.hpp/.cpp` (193 LOC)

**What to port:** Complete the existing `AccountingEngine.java` stub:
- Constructor: `(MarketModelEvolver evolver, MarketModelMultiProduct product, double initialNumeraireValue)`
- `multiplePathValues(GenericSequenceStatistics stats, int numberOfPaths)` — maps C++ `multiplePathValues(SequenceStatisticsInc&, Size)`
- Private `singlePathValues(double[] values)` — the inner path loop

**Note:** C++ `Clone<MarketModelMultiProduct>` → Java stores the product as `MarketModelMultiProduct` directly (no deep-clone wrapper needed; use `clone()` method from the interface).

**Note:** C++ `SequenceStatisticsInc` → use Java `GenericSequenceStatistics` (already has incremental `add`).

**Test:** `AccountingEngineTest` — run a minimal 1-product (caplet) simulation with LMMCurveState + LognormalFwdRatePc evolver (deferred to Phase 3i integration test); Phase 3h test covers construction + one synthetic path manually.

**Commit:** `port(model.marketmodels): AccountingEngine complete (Phase 3h B.8)`

---

## L1 — MarketModelMultiProduct interface

**C++ source:** `multiproduct.hpp` (77 LOC)

**What to port:** `MarketModelMultiProduct` interface — `suggestedNumeraires()`, `evolution()`, `possibleCashFlowTimes()`, `numberOfProducts()`, `maxNumberOfCashFlowsPerProductPerStep()`, `reset()`, `nextTimeStep(...)`, `clone()`. Also port inner `CashFlow` struct.

**Note:** This is a pure interface with no implementations in Phase 3h. Implementations land in Phase 3j.

**Commit:** `port(model.marketmodels): MarketModelMultiProduct interface + CashFlow struct (Phase 3h L1)`

---

## L2 — ConstrainedEvolver interface

**C++ source:** `constrainedevolver.hpp` (54 LOC)

**What to port:** `ConstrainedEvolver extends MarketModelEvolver` — adds `setConstraintType`, `setThisStep`, `browniansThisStep`.

**Commit:** `port(model.marketmodels): ConstrainedEvolver interface (Phase 3h L2)`

---

## L3 — Integration verification

After all A.* and B.* WIs are committed and passing independently:

1. Verify full compile: `mvn -pl jquantlib compile` — no errors in `org.jquantlib.model.marketmodels.*`
2. Verify test baseline unchanged: `mvn -pl jquantlib test` — 1027+/0/0/38 (Phase 3g baseline preserved)
3. Run focused Phase 3h tests: `mvn -pl jquantlib test -Dtest=MarketModelUtilitiesTest,EvolutionDescriptionTest,LMMCurveStateTest,DriftCalculatorTest,CorrelationsTest,MTBrownianGeneratorTest`

---

## L4 — Completion + tag + memory + docs + teardown

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git tag jquantlib-phase3h-complete
git push origin jquantlib-phase3h-complete

# Update memory
# Update docs/migration/phase3h-completion.md
# Update README.md

# Remove worktrees
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-3h-A
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-3h-B
git branch -d phase-3h-A phase-3h-B
```

---

## Reference: C++ probes to generate

For migration-harness cross-validation, generate probes for:
1. `EvolutionDescriptionProbe` — 5-rate grid, terminal + moneyMarket measures, output: `int[]` firstAliveRate, `double[]` rateTaus
2. `LMMCurveStateProbe` — set 5 fwd rates, output: discountRatios[0..5], coterminalSwapRates[0..4], swapRate(1,3)
3. `LMMDriftCalculatorProbe` — known pseudo-root (flat vol, identity corr), output: drifts[0..4] (plain + reduced)
4. `ExponentialCorrelationsProbe` — longTermCorr=0.5, beta=0.3, output: correlation matrix entries

All probes → `migration-harness/references/models/marketmodels/` JSON files.
Script addition: `migration-harness/cpp/probes/marketmodels_foundation_probe.cpp`.

---

## Parallel dispatch note

Tracks A and B are fully independent until B.8 (AccountingEngine), which references `MarketModelMultiProduct` (L1) and `MarketModelEvolver` (existing stub). Both L1 and MarketModelEvolver exist before implementation starts, so B.8 is unblocked. A full parallel two-agent dispatch is safe for Tracks A and B.

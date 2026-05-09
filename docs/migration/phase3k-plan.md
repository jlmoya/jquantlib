# Phase 3k Implementation Plan

> Three tracks: L0 prereqs (blocking), Track A (products layer), Track B (callability), Track C (pathwise products + engine).
> Track A + B + C run in parallel after L0. Tag `jquantlib-phase3k-complete`.

**Goal:** Port all `ql/models/marketmodels/products/`, `callability/`, `pathwisegreeks/`, and
`pathwiseaccountingengine` classes — ~57 new Java classes across 6 new packages, ~11,732 LOC C++.

---

## L0 — Monte Carlo prereqs + pathwise base

*(Single worktree — sequential — must land before any track dispatch)*

### L0.1 — ExerciseStrategy interface

**C++ source:** `ql/methods/montecarlo/exercisestrategy.hpp` (42 LOC)

**Java class:** `org.jquantlib.methods.montecarlo.ExerciseStrategy`

**What to port:**
- Interface (not generic — C++ template always instantiated with `CurveState`)
- Methods: `exerciseTimes()`, `relevantTimes()`, `reset()`, `exercise(CurveState currentState)`, `nextStep(CurveState currentState)`, `clone()`
- Return types: `double[]` for times, `boolean` for exercise, `ExerciseStrategy` for clone

**Note:** Check whether `org.jquantlib.methods.montecarlo` package already contains any exercise-related
classes. If `ExerciseStrategy` partially exists, align rather than replace.

**Test:** `ExerciseStrategyTest` — compile-level test (anonymous subclass); verify interface is consistent with `ExerciseAdapter` usage (deferred to Track A A.10).

**Commit:** `port(methods.montecarlo): ExerciseStrategy interface (Phase 3k L0.1)`

---

### L0.2 — NodeData class

**C++ source:** `ql/methods/montecarlo/nodedata.hpp` (40 LOC)

**Java class:** `org.jquantlib.methods.montecarlo.NodeData`

**What to port:**
- Simple data class: `double exerciseValue`, `double cumulatedCashFlows`, `double[] values`, `double controlValue`, `boolean isValid`
- Default constructor initializing all to 0/false

**Test:** None needed (pure data class — verified at use-site by B.13 collectNodeData test).

**Commit:** `port(methods.montecarlo): NodeData data class (Phase 3k L0.2)`

---

### L0.3 — ParametricExercise interface + genericEarlyExerciseOptimization

**C++ source:** `ql/methods/montecarlo/parametricexercise.hpp` (70 LOC)

**Java class:** `org.jquantlib.methods.montecarlo.ParametricExercise`
**Java utility:** `org.jquantlib.methods.montecarlo.GenericEarlyExercise` (for the free function)

**What to port:**
- `ParametricExercise` interface: `numberOfVariables()`, `numberOfParameters()`, `exercise(int exerciseNumber, double[] parameters, double[] variables)`, `guess(int exerciseNumber, double[] parameters)`
- `GenericEarlyExercise.optimize(List<List<NodeData>> simulationData, ParametricExercise exercise, List<double[]> parameters, EndCriteria endCriteria, OptimizationMethod method) → double`

**Check first:** Verify that `EndCriteria` and `OptimizationMethod` are available in Java
(`org.jquantlib.math.optimization` package). If missing, stub them or defer the free function
(P3K-A trigger). The interface itself can always land.

**Commit:** `port(methods.montecarlo): ParametricExercise interface + GenericEarlyExercise (Phase 3k L0.3)`

---

### L0.4 — MarketModelPathwiseMultiProduct base verification

**C++ source:** `ql/models/marketmodels/pathwisemultiproduct.hpp` (60 LOC)

**Java class:** `org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct`

**What to verify/complete:**
- Check if the Java class exists; if so, verify it has:
  - Inner class `CashFlow` with `int timeIndex` AND `double[] amount` (vector, not scalar!)
  - Abstract methods: `suggestedNumeraires()`, `evolution()`, `possibleCashFlowTimes()`, `numberOfProducts()`, `maxNumberOfCashFlowsPerProductPerStep()`, `alreadyDeflated()`, `reset()`, `nextTimeStep(...)`, `clone()`
- If the inner `CashFlow` only has `double amount` (scalar), align it to `double[] amount` now

**Test:** Compile-level only at L0 stage.

**Commit:** `align/port(model.marketmodels): MarketModelPathwiseMultiProduct + pathwise CashFlow (Phase 3k L0.4)` *(align if fixing; port if creating)*

---

## Track A — Products Layer

*(Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3k-A`)*
*(Pull from main after all L0 commits before starting A.1)*

All classes: `org.jquantlib.model.marketmodels.products` (bases + composites) and subpackages.

### A.1 + A.2 — Base classes (ship together)

**C++ source:**
- `products/multiproductmultistep.hpp/.cpp` (127 LOC)
- `products/multiproductonestep.hpp/.cpp` (115 LOC)

**Java classes:**
- `org.jquantlib.model.marketmodels.products.MultiProductMultiStep` (abstract)
- `org.jquantlib.model.marketmodels.products.MultiProductOneStep` (abstract)

**What to port:**
- `MultiProductMultiStep(double[] rateTimes)` constructor; stores `rateTimes_`, builds `EvolutionDescription evolution_` (one step per rate, terminal measure); provides `suggestedNumeraires()` and `evolution()` final implementations
- `MultiProductOneStep(double[] rateTimes)` — same pattern but single-step evolution

**Note:** Both base classes implement `suggestedNumeraires()` as "all rate-times indices" (terminal numeraire). The `EvolutionDescription` construction in the multistep case sets each evolution time = each rate reset time.

**Test:** `BaseProductTest` — instantiate each via anonymous subclass; verify `evolution().rateTimes()` and `suggestedNumeraires()` against known values. Tolerance: exact.

**Commit:** `port(model.marketmodels.products): MultiProductMultiStep + MultiProductOneStep base classes (Phase 3k A.1-A.2)`

---

### A.3 — One-step products (four classes, ship together)

*(After A.1+A.2 on main)*

**C++ source:** `products/onestep/` (580 LOC total)

**Java package:** `org.jquantlib.model.marketmodels.products.onestep`

**Classes:**
- `OneStepForwards` — FRA cash flows: `amount = (rate - strike) * accrual`, discount to payment
- `OneStepOptionlets` — `Payoff.apply(rate)` cash flows using stored optionlet payoffs
- `OneStepCoinitialSwaps` — co-initial (same start) swap cash flows
- `OneStepCoterminalSwaps` — co-terminal swap cash flows

**Note:** All four follow the same pattern: constructor stores accruals/strikes/paymentTimes;
`nextTimeStep()` reads rates from `CurveState.forwardRate(i)` and computes one cash flow per product.
`numberOfProducts()` = length of rates array. All implement `reset()` as `currentIndex_ = 0`.

**Test:** `OneStepProductsTest` — create each product with a 4-rate test setup; feed a mock `CurveState` with known forward rates; verify cash flows against manually computed values. Cross-validate `OneStepForwards` + `OneStepOptionlets` against C++ `testOneStepForwardsAndOptionlets` test setup. Tolerance: 1e-12.

**Commit:** `port(model.marketmodels.products.onestep): OneStepForwards + OneStepOptionlets + CoinitialSwaps + CoterminalSwaps (Phase 3k A.3)`

---

### A.4 + A.5 + A.6 — Composite infrastructure (ship together)

*(After A.1+A.2 on main)*

**C++ source:**
- `products/compositeproduct.hpp/.cpp` (241 LOC) — `MarketModelComposite`
- `products/multiproductcomposite.hpp/.cpp` (172 LOC) — `MultiProductComposite`
- `products/singleproductcomposite.hpp/.cpp` (64 LOC) — `SingleProductComposite`

**Java package:** `org.jquantlib.model.marketmodels.products`

**What to port:**
- `MarketModelComposite` abstract class (extends MarketModelMultiProduct):
  - `add(MarketModelMultiProduct product, double multiplier)` — appends to `components_`
  - `subtract(MarketModelMultiProduct product, double multiplier)` — appends with negative multiplier
  - `finalize()` — merges evolution times, builds `isInSubset_` boolean matrix, sets `evolution_`
  - `reset()` — resets each sub-product; `evolution()`, `suggestedNumeraires()`, `possibleCashFlowTimes()`
  - `size()`, `item(int i)`, `multiplier(int i)` accessors
  - Inner `SubProduct`: holds `MarketModelMultiProduct product`, `double multiplier`, `int[] numberOfCashflows`, `CashFlow[][] cashflows`, `int[] timeIndices`, `boolean done`
- `MultiProductComposite` extends `MarketModelComposite`: implements `numberOfProducts()`, `maxNumberOfCashFlowsPerProductPerStep()`, `nextTimeStep()` (delegates to each sub-product and merges cash flows)
- `SingleProductComposite` extends `MarketModelComposite`: wraps to single-product view

**Note:** `finalize()` must compute the union of all evolution times across sub-products and build for each sub-product a boolean array (`isInSubset`) indicating which merged times correspond to that sub-product's own evolution times. This is the most complex part — cross-check with C++ `compositeproduct.cpp` logic.

**Test:** `MultiProductCompositeTest` — add `OneStepForwards` and `OneStepOptionlets` (as in testOneStepForwardsAndOptionlets); verify `numberOfProducts()` = 8 (4+4), `evolution().evolutionTimes()` correct, cash flows from one `nextTimeStep` call match. Tolerance: 1e-12 for amounts.

**Commit:** `port(model.marketmodels.products): MarketModelComposite + MultiProductComposite + SingleProductComposite (Phase 3k A.4-A.6)`

---

### A.7 — Simple multistep products (batch 1, six classes)

*(After A.1+A.2 on main — parallel with A.3 and A.4-A.6)*

**C++ source:** (multistep/, 133+139+164+164+117+170 = 887 LOC)

**Java package:** `org.jquantlib.model.marketmodels.products.multistep`

**Classes:**
- `MultiStepForwards` — FRA cash flows per step
- `MultiStepOptionlets` — optionlet cash flows using `Payoff.apply(rate)` 
- `MultiStepSwaption` — swaption cash flow at exercise time (swap NPV if positive)
- `MultiStepSwap` — payer/receiver swap cash flows per step
- `MultiStepNothing` — no cash flows (used as underlying in callable Bermudan)
- `MultiStepCashRebate` — fixed rebate payment at each step

**Note:** All follow `MultiProductMultiStep` pattern. `MultiStepSwap` has a `boolean payer` flag
reversing signs. `MultiStepOptionlets` takes `Payoff[]` (Java `instruments.Payoff[]`).

**Test:** `MultiStepSimpleProductsTest` — 4-rate grid; verify cash flows for `MultiStepForwards` and `MultiStepSwap` over all steps against C++ probe values. Tolerance: 1e-12.

**Commit:** `port(model.marketmodels.products.multistep): MultiStepForwards + Optionlets + Swaption + Swap + Nothing + CashRebate (Phase 3k A.7)`

---

### A.8 — Complex multistep products (batch 2, six classes)

*(After A.1+A.2 on main — parallel with A.3, A.4-A.6, A.7)*

**C++ source:** (multistep/, 149+147+140+87+161+185 = 869 LOC)

**Java package:** `org.jquantlib.model.marketmodels.products.multistep`

**Classes:**
- `MultiStepCoinitialSwaps` — co-initial swap cash flows per step
- `MultiStepCoterminalSwaps` — co-terminal swap cash flows per step
- `MultiStepCoterminalSwaptions` — co-terminal swaption cash flows
- `MultiStepInverseFloater` — inverse floater: `fixed - floatingSpread - floatingRate * leverage` per step
- `MultiStepRatchet` — ratchet swap: floor = max(prev_floor, gearing*fixing + spread)
- `MultiStepTarn` — Target Accrual Redemption Note: pays until `couponPaid >= totalCoupon`

**Note:** `MultiStepRatchet` and `MultiStepTarn` carry path-dependent state (`floor_` and `couponPaid_` respectively). The `reset()` method must reinitialize these along with `currentIndex_`.

**Test:** `MultiStepComplexProductsTest` — one test per product with a 4-rate grid and known forward rate scenario; verify final cash flow amounts against C++ probe. Tolerance: 1e-12. For `MultiStepTarn`, verify early termination condition.

**Commit:** `port(model.marketmodels.products.multistep): MultiStepCoinitial/Coterminal + InverseFloater + Ratchet + Tarn (Phase 3k A.8)`

---

### A.9 — MultiStepPeriodCapletSwaptions

*(After A.1+A.2 on main)*

**C++ source:** `products/multistep/multistepperiodcapletswaptions.hpp/.cpp` (232 LOC)

**Java class:** `org.jquantlib.model.marketmodels.products.multistep.MultiStepPeriodCapletSwaptions`

**What to port:**
- Constructor: `(double[] rateTimes, double[] forwardOptionPaymentTimes, double[] swaptionPaymentTimes, StrikedTypePayoff[] forwardPayOffs, StrikedTypePayoff[] swapPayOffs, int period, int offset)`
- Computes `numberFRAs_`, `numberBigFRAs_`, `lastIndex_`, `paymentTimes_` (union of forward and swaption times)
- `nextTimeStep()` — at each period boundary, computes period-average forward rate and swaption rate; applies respective payoffs
- `numberOfProducts()` = 2 * numberBigFRAs_

**Test:** `PeriodCapletSwaptionsTest` — 6-rate grid, period=2; verify `numberOfProducts()` = 6 and cash flow structure. Tolerance: exact for structure, 1e-12 for amounts.

**Commit:** `port(model.marketmodels.products.multistep): MultiStepPeriodCapletSwaptions (Phase 3k A.9)`

---

### A.10 — ExerciseAdapter

*(After A.1+A.2, B.1 MarketModelExerciseValue on main)*

**C++ source:** `products/multistep/exerciseadapter.hpp/.cpp` (147 LOC)

**Java class:** `org.jquantlib.model.marketmodels.products.multistep.ExerciseAdapter`

**What to port:**
- `ExerciseAdapter(MarketModelExerciseValue exercise)` — wraps an ExerciseValue as a rebate product
- Delegates `nextTimeStep()` to `exercise.value(currentState)` and packages it as a CashFlow
- Implements all `MarketModelMultiProduct` interface methods

**Note:** This bridges `MarketModelExerciseValue` (callability) and `MarketModelMultiProduct` (products),
so it requires B.1 from Track B.

**Commit:** `port(model.marketmodels.products.multistep): ExerciseAdapter (Phase 3k A.10)`

---

### A.11 — CallSpecifiedMultiProduct

*(After A.1, A.10, B.1 ExerciseValue, L0.1 ExerciseStrategy on main)*

**C++ source:** `products/multistep/callspecifiedmultiproduct.hpp/.cpp` (278 LOC)

**Java class:** `org.jquantlib.model.marketmodels.products.multistep.CallSpecifiedMultiProduct`

**What to port:**
- Constructor: `(MarketModelMultiProduct underlying, ExerciseStrategy strategy, MarketModelMultiProduct rebate)`
- Clones all three at construction time
- `nextTimeStep()`: at exercise times, queries `strategy.exercise(state)`; if exercised, switches to rebate product; otherwise advances underlying
- `enableCallability()` / `disableCallability()` — toggles the callable feature
- Accessors: `underlying()`, `strategy()`, `rebate()`

**Note:** This is the central callable-product class; `testCallableSwapNaif` and `testCallableSwapLS`
both use it. The `evolution_` merges underlying, strategy, and rebate evolution times.

**Test:** `CallSpecifiedProductTest` — create a callable receiver swap with a naif strategy; run one complete path via `nextTimeStep` in a loop; verify terminal cash flow. Tolerance: 1e-10 (deterministic one-path test).

**Commit:** `port(model.marketmodels.products.multistep): CallSpecifiedMultiProduct (Phase 3k A.11)`

---

### A.12 — MultiStepPathwiseWrapper

*(After A.1, L0.4 PathwiseMultiProduct on main)*

**C++ source:** `products/multistep/multisteppathwisewrapper.hpp/.cpp` (159 LOC)

**Java class:** `org.jquantlib.model.marketmodels.products.multistep.MultiStepPathwiseWrapper`

**What to port:**
- Wraps a `MarketModelPathwiseMultiProduct` and presents it as a `MarketModelMultiProduct`
- `nextTimeStep()` calls the pathwise product and sums the `amount[]` vector entries into a scalar amount
- `numberOfProducts()` = wrapped product's `numberOfProducts()`

**Commit:** `port(model.marketmodels.products.multistep): MultiStepPathwiseWrapper (Phase 3k A.12)`

---

## Track B — Callability Layer

*(Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3k-B`)*
*(Pull from main after all L0 commits before starting)*

All classes: `org.jquantlib.model.marketmodels.callability`

### B.1-B.4 — Core interfaces (ship as one commit)

**C++ source:** exercisevalue.hpp, nodedataprovider.hpp, marketmodelbasissystem.hpp, marketmodelparametricexercise.hpp (total 184 LOC)

**Java interfaces:**
- `MarketModelExerciseValue` — `numberOfExercises()`, `evolution()`, `possibleCashFlowTimes()`, `nextStep(CurveState)`, `reset()`, `isExerciseTime()`, `value(CurveState) → CashFlow`, `clone()`
- `MarketModelNodeDataProvider` — `numberOfExercises()`, `numberOfData()`, `evolution()`, `nextStep(CurveState)`, `reset()`, `isExerciseTime()`, `values(CurveState, double[] results)`
- `MarketModelBasisSystem extends MarketModelNodeDataProvider` — adds `numberOfFunctions()`, `clone()`; overrides `numberOfData()` → `numberOfFunctions()`
- `MarketModelParametricExercise extends MarketModelNodeDataProvider` — extends `ParametricExercise` (L0.3); overrides `numberOfData()` → `numberOfVariables()`

**Note:** `MarketModelExerciseValue.value()` returns `MarketModelMultiProduct.CashFlow`. Import must use the non-pathwise `CashFlow` (with scalar `amount`).

**Commit:** `port(model.marketmodels.callability): exercise-value + node-data-provider + basis-system + parametric-exercise interfaces (Phase 3k B.1-B.4)`

---

### B.5 + B.6 — Exercise value implementations (parallel after B.1)

**C++ source:**
- `callability/nothingexercisevalue.hpp/.cpp` (148 LOC)
- `callability/bermudanswaptionexercisevalue.hpp/.cpp` (146 LOC)

**Java classes:**
- `NothingExerciseValue` — always returns `CashFlow(timeIndex=0, amount=0.0)`; used as null rebate
- `BermudanSwaptionExerciseValue` — computes swaption value as annuity × max(swapRate - fixedRate, 0); depends on `CoterminalSwapCurveState`

**Note:** `NothingExerciseValue` constructor takes `double[] rateTimes`; `isExerciseTime()` returns all-true. `BermudanSwaptionExerciseValue` constructor takes `double[] rateTimes`, `double[] accruals`, `double[] displacements`, `double fixedRate`.

**Test:** `NothingAndBermudanTest` — verify `NothingExerciseValue.value()` returns 0.0; verify `BermudanSwaptionExerciseValue.value()` for a at-the-money swaption against C++ probe. Tolerance: 1e-10.

**Commit:** `port(model.marketmodels.callability): NothingExerciseValue + BermudanSwaptionExerciseValue (Phase 3k B.5-B.6)`

---

### B.7 + B.8 — Basis systems (parallel after B.3)

**C++ source:**
- `callability/swapbasissystem.hpp/.cpp` (135 LOC) — `SwapBasisSystem`
- `callability/swapforwardbasissystem.hpp/.cpp` (191 LOC) — `SwapForwardBasisSystem`

**Java classes:**
- `SwapBasisSystem` — basis functions are co-terminal swap rates for each exercise; `numberOfFunctions()` = 2 (constant + swap rate) per exercise
- `SwapForwardBasisSystem` — basis functions include both swap rates and forward rates; larger basis

**Note:** Both depend on `CurveState.coterminalSwapRate(i)` and related methods. Verify these are
present in `LMMCurveState` or `CoterminalSwapCurveState`. If missing, add as an align commit before B.7.

**Test:** `BasisSystemTest` — construct each; verify `numberOfFunctions()` and `values()` output for a known CurveState. Tolerance: 1e-12.

**Commit:** `port(model.marketmodels.callability): SwapBasisSystem + SwapForwardBasisSystem (Phase 3k B.7-B.8)`

---

### B.9 — SwapRateTrigger (after L0.1)

**C++ source:** `callability/swapratetrigger.hpp/.cpp` (127 LOC)

**Java class:** `SwapRateTrigger implements ExerciseStrategy`

**What to port:**
- `SwapRateTrigger(double[] rateTimes, double[] swapTriggers, double[] exerciseTimes)`
- `exercise(CurveState state)` — true if co-terminal swap rate at `currentIndex_` > trigger
- All `ExerciseStrategy` methods: `exerciseTimes()`, `relevantTimes()`, `reset()`, `nextStep()`, `clone()`

**Test:** `SwapRateTriggerTest` — verify fires on high swap rate, does not fire on low swap rate. Tolerance: exact.

**Commit:** `port(model.marketmodels.callability): SwapRateTrigger (Phase 3k B.9)`

---

### B.10 — TriggeredSwapExercise (after B.1, L0.1)

**C++ source:** `callability/triggeredswapexercise.hpp/.cpp` (156 LOC)

**Java class:** `TriggeredSwapExercise implements MarketModelExerciseValue`

**What to port:**
- Wraps a `MarketModelExerciseValue` and an `ExerciseStrategy`; fires value only when strategy says to exercise
- `value(CurveState)` returns the inner exercise value if triggered, else zero

**Commit:** `port(model.marketmodels.callability): TriggeredSwapExercise (Phase 3k B.10)`

---

### B.11 — ParametricExerciseAdapter (after B.4, L0.3)

**C++ source:** `callability/parametricexerciseadapter.hpp/.cpp` (127 LOC)

**Java class:** `ParametricExerciseAdapter implements ExerciseStrategy`

**What to port:**
- Wraps a `MarketModelParametricExercise` (provides node-data + parametric exercise logic)
- `exercise(CurveState)` delegates to `MarketModelParametricExercise.exercise(exerciseNumber, parameters, variables)`

**Commit:** `port(model.marketmodels.callability): ParametricExerciseAdapter (Phase 3k B.11)`

---

### B.12 — LongstaffSchwartzExerciseStrategy (after B.3, B.5-B.6, B.7-B.8, L0.1)

**C++ source:** `callability/lsstrategy.hpp/.cpp` (231 LOC)

**Java class:** `LongstaffSchwartzExerciseStrategy implements ExerciseStrategy`

**What to port:**
- Constructor: `(MarketModelBasisSystem basisSystem, List<double[]> basisCoefficients, EvolutionDescription evolution, int[] numeraires, MarketModelExerciseValue exercise, MarketModelExerciseValue control)`
- `exercise(CurveState state)` — evaluates basis functions; computes dot product with stored regression coefficients; compares exercise value vs continuation value
- `nextStep(CurveState state)` — updates internal state (discounters, rebate accumulation)
- All `ExerciseStrategy` interface methods

**Note:** This is the most algorithmically complex callability class. The `basisCoefficients` are
pre-computed by `collectNodeData` + a regression; they are passed in at construction (not computed here).
The strategy then uses them online during simulation.

**Test:** `LSStrategyTest` — use pre-computed coefficients (from a C++ probe); verify `exercise()` returns the expected boolean for a given CurveState. Tolerance: exact (boolean comparison).

**Commit:** `port(model.marketmodels.callability): LongstaffSchwartzExerciseStrategy (Phase 3k B.12)`

---

### B.13 — collectNodeData (after B.2, B.1, L0.2)

**C++ source:** `callability/collectnodedata.hpp/.cpp` (245 LOC)

**Java class:** `org.jquantlib.model.marketmodels.callability.CollectNodeData`

**What to port:**
- Static method: `collect(MarketModelEvolver evolver, MarketModelMultiProduct product, MarketModelNodeDataProvider dataProvider, MarketModelExerciseValue rebate, MarketModelExerciseValue control, int numberOfPaths, List<List<NodeData>> collectedData)`
- Loop: `numberOfPaths` paths × `numberOfSteps` steps; accumulate cash flows, exercise values, basis-function values into `NodeData` grid
- Uses `MarketModelDiscounter` for discount factors

**Note:** This is a free function in C++. Java: static method in `CollectNodeData` class.

**Test:** `CollectNodeDataTest` — run collect for a simple receiver swap with a naif strategy; verify `collectedData.size()` and that `exerciseValue` is positive in early steps. Tolerance: loose (functional test).

**Commit:** `port(model.marketmodels.callability): CollectNodeData (Phase 3k B.13)`

---

### B.14 — UpperBoundEngine (after A.5, B.1, B.9 SwapRateTrigger, L0.1)

**C++ source:** `callability/upperboundengine.hpp/.cpp` (430 LOC)

**Java class:** `UpperBoundEngine`

**What to port:**
- Constructor: `(MarketModelEvolver evolver, List<MarketModelEvolver> innerEvolvers, MarketModelMultiProduct underlying, MarketModelExerciseValue rebate, MarketModelMultiProduct hedge, MarketModelExerciseValue hedgeRebate, ExerciseStrategy hedgeStrategy, double initialNumeraireValue)`
- `multiplePathValues(Statistics stats, int outerPaths, int innerPaths)` — outer loop drives the evolver; at each exercise time, inner evolvers run to estimate the upper bound correction
- `singlePathValue(int innerPaths) → [double value, double error]`
- Private `collectCashFlows(...)` utility

**Note:** The constructor internally builds a `MultiProductComposite` from underlying + rebate + hedge + hedgeRebate products. Requires A.5 (`MultiProductComposite`) to be on main.

**Test:** `UpperBoundEngineTest` — run a naif-strategy callable swap; verify the upper bound > lower bound from `AccountingEngine` (functional ordering test). Tolerance: functional (upper > lower).

**Commit:** `port(model.marketmodels.callability): UpperBoundEngine (Phase 3k B.14)`

---

## Track C — Pathwise Products + Engine

*(Worktree: `/Users/josemoya/eclipse-workspace/jquantlib-3k-C`)*
*(Pull from main after L0 and A.1+A.2 commits)*

### C.1-C.5 — Pathwise products (batch, can ship as 2-3 commits)

**C++ source:** `products/pathwise/` (1,902 LOC)

**Java package:** `org.jquantlib.model.marketmodels.products.pathwise`

**Order (simplest to most complex):**
1. `PathwiseProductCashRebate` (192 LOC) — fixed rebate with derivative = 0
2. `PathwiseProductSwap` (220 LOC) — swap cash flows + derivatives wrt rate
3. `PathwiseProductInverseFloater` (236 LOC) — inverse floater cash flows + derivatives
4. `PathwiseProductSwaption` (394 LOC) — swaption with deflated numeraire
5. `PathwiseProductCaplet` (581 LOC) — three classes: `MarketModelPathwiseMultiCaplet`, `MarketModelPathwiseMultiDeflatedCaplet`, `MarketModelPathwiseMultiDeflatedCap`

**Note:** All classes extend `MarketModelPathwiseMultiProduct`. The key difference from non-pathwise
products is that `CashFlow.amount` is a `double[]` with one entry per rate (the derivative ∂payoff/∂rate_i
plus the payoff itself in `amount[0]`). The `alreadyDeflated()` flag distinguishes deflated variants.

**Test (per commit):**
- `PathwiseCashRebateTest`, `PathwiseSwapTest` — deterministic one-step; verify `amount[]` entries vs manual calculation. Tolerance: 1e-12.
- `PathwiseCapletTest` — verify `MarketModelPathwiseMultiCaplet.nextTimeStep()` for a 3-rate grid; compare derivatives against finite-difference approximation. Tolerance: 1e-6 rel (FD comparison).

**Commits:**
- `port(model.marketmodels.products.pathwise): PathwiseProductCashRebate + Swap + InverseFloater (Phase 3k C.1-C.4 partial)`
- `port(model.marketmodels.products.pathwise): PathwiseProductSwaption + Caplet family (Phase 3k C.1-C.5 complete)`

---

### C.6 — MarketModelPathwiseDiscounter (standalone)

**C++ source:** `pathwisediscounter.hpp/.cpp` (146 LOC)

**Java class:** `org.jquantlib.model.marketmodels.MarketModelPathwiseDiscounter`

**What to port:**
- Constructor: `(double paymentTime, double[] rateTimes)`; identifies `before_`, `beforeWeight_`, `postWeight_`, `taus_`
- `getFactors(Matrix LIBORRates, Matrix Discounts, int currentStep, double[] factors)` — computes discount factors and their derivatives with respect to each LIBOR rate

**Note:** This is structurally similar to `MarketModelDiscounter` but returns a vector of factor derivatives rather than a scalar.

**Test:** `PathwiseDiscounterTest` — verify `getFactors()` output matches `MarketModelDiscounter.numeraireBonds()` for the first element. Tolerance: 1e-12.

**Commit:** `port(model.marketmodels): MarketModelPathwiseDiscounter (Phase 3k C.6)`

---

### C.7 — RatePseudoRootJacobian (three classes)

**C++ source:** `pathwisegreeks/ratepseudorootjacobian.hpp/.cpp` (478 LOC)

**Java package:** `org.jquantlib.model.marketmodels.pathwisegreeks`

**Classes:**
- `RatePseudoRootJacobianNumerical` — numerical (bumped) Jacobian for testing
- `RatePseudoRootJacobian` — analytic Jacobian (page 95 of Giles-Glasserman paper)
- `RatePseudoRootJacobianAllElements` — analytic Jacobian for all pseudo-root elements

**What to port:**
- All three: constructor stores pseudo-root, aliveIndex, taus, displacements
- `getBumps(double[] oldRates, double[] oneStepDFs, double[] newRates, double[] gaussians, Matrix B)` — fills the Jacobian matrix B
- `RatePseudoRootJacobianNumerical` uses `LMMDriftCalculator` internally

**Note:** `RatePseudoRootJacobian` and `AllElements` are analytic; `Numerical` is the FD test reference. C++ tested in `testPathwiseVegas` which verifies analytic matches numerical to 1e-6.

**Test:** `RatePseudoRootJacobianTest` — compute analytic vs numerical `getBumps()` for a 3-rate 2-factor pseudo-root; verify max absolute difference < 1e-5. Tolerance: 1e-5 (FD comparison).

**Commit:** `port(model.marketmodels.pathwisegreeks): RatePseudoRootJacobian family (Phase 3k C.7)`

---

### C.8 — SwaptionPseudoJacobian

**C++ source:** `pathwisegreeks/swaptionpseudojacobian.hpp/.cpp` (494 LOC)

**Java class:** `SwaptionPseudoJacobian`

**What to port:**
- Computes the derivative of swaption value with respect to pseudo-root elements
- Constructor stores model structure; `getBumps(Matrix& B)` fills Jacobian

**Test:** `SwaptionPseudoJacobianTest` — structural test verifying matrix dimensions. Tolerance: functional.

**Commit:** `port(model.marketmodels.pathwisegreeks): SwaptionPseudoJacobian (Phase 3k C.8)`

---

### C.9 — BumpInstrumentJacobian (after C.7, C.8)

**C++ source:** `pathwisegreeks/bumpinstrumentjacobian.hpp/.cpp` (325 LOC)

**Java class:** `BumpInstrumentJacobian`

**What to port:**
- Combines rate-pseudo-root Jacobian and instrument-to-rate sensitivity to give instrument vega
- `getBumps(Matrix& B)` — chain-rule combination

**Commit:** `port(model.marketmodels.pathwisegreeks): BumpInstrumentJacobian (Phase 3k C.9)`

---

### C.10 — VegaBumpCluster (after C.9)

**C++ source:** `pathwisegreeks/vegabumpcluster.hpp/.cpp` (373 LOC)

**Java class:** `VegaBumpCluster`

**What to port:**
- Groups a set of vega bumps (market instruments) for aggregation
- Constructor and `getFactors()` / `getVegaBumps()` methods

**Commit:** `port(model.marketmodels.pathwisegreeks): VegaBumpCluster (Phase 3k C.10)`

---

### C.11 — PathwiseAccountingEngine (after C.1-C.10, LogNormalFwdRateEuler[3i])

**C++ source:** `pathwiseaccountingengine.hpp/.cpp` (1,478 LOC — two classes)

**Java classes:**
- `PathwiseAccountingEngine` — Giles-Glasserman adjoint Delta engine; computes both price and Delta Greeks in one pass
- `PathwiseVegasAccountingEngine` — extends with vega bump clusters for market vega Greeks

**What to port:**
- `PathwiseAccountingEngine(LogNormalFwdRateEuler evolver, MarketModelPathwiseMultiProduct product, MarketModel pseudoRootStructure, double initialNumeraireValue)`
- `multiplePathValues(SequenceStatistics stats, int numberOfPaths)` — outer loop; calls `singlePathValues(double[] values)` per path
- `singlePathValues(double[] values)` — drives the Euler evolver step by step; uses `RatePseudoRootJacobian` to propagate Delta; uses `MarketModelPathwiseDiscounter` to discount; accumulates path-wise value + Delta
- `PathwiseVegasAccountingEngine` — same structure but also accumulates vega bumps using `VegaBumpCluster`

**Note:** This is the most complex class in the entire Phase 3k scope. The `singlePathValues` method
implements the Giles-Glasserman algorithm: at each step, record LIBOR ratios and Euler step gaussians;
after the full path, work backwards to accumulate adjoint sensitivities. Carefully follow the C++ body
in `pathwiseaccountingengine.cpp` — do not compress or simplify; keep the same variable names and
structure for traceability. The C++ file is 1,195 lines.

**Test:** `PathwiseAccountingEngineTest` — run on a 5-rate AbcdVol model with 1,000 paths; verify Delta of a DeflatedCaplet portfolio vs finite-difference with `LogNormalFwdRateEulerConstrained`. Tolerance: loose (1e-4 rel, MC noise). This is the equivalent of `testPathwiseGreeks`.

**Commit:** `port(model.marketmodels): PathwiseAccountingEngine + PathwiseVegasAccountingEngine (Phase 3k C.11)`

---

### C.12 — PathwiseProductCallSpecified (deferred if needed)

**C++ source:** `products/pathwise/pathwiseproductcallspecified.hpp/.cpp` (269 LOC)

**Java class:** `PathwiseProductCallSpecified`

**Condition:** Port immediately after C.5 if Track B B.12 (LongstaffSchwartzExerciseStrategy) is on main. Otherwise defer to Phase 3k.5.

**Commit:** `port(model.marketmodels.products.pathwise): PathwiseProductCallSpecified (Phase 3k C.12)`

---

## L1 — Integration verification

After all tracks are on main:

```bash
# Full compile
mvn -pl jquantlib compile

# Full test
mvn -pl jquantlib test
# Expected: 1220+/0/0/38

# Focused tests
mvn -pl jquantlib test -Dtest=\
BaseProductTest,OneStepProductsTest,MultiProductCompositeTest,\
MultiStepSimpleProductsTest,MultiStepComplexProductsTest,\
PeriodCapletSwaptionsTest,CallSpecifiedProductTest,\
CallabilityInterfacesTest,NothingAndBermudanTest,\
SwapRateTriggerTest,BasisSystemTest,\
LSStrategyTest,CollectNodeDataTest,UpperBoundEngineTest,\
PathwiseCashRebateTest,PathwiseSwapTest,PathwiseCapletTest,\
PathwiseDiscounterTest,RatePseudoRootJacobianTest,\
PathwiseAccountingEngineTest
```

---

## L2 — Completion + tag + docs teardown

```bash
cd /Users/josemoya/eclipse-workspace/jquantlib
git tag jquantlib-phase3k-complete
git push origin jquantlib-phase3k-complete

# Update memory + docs/migration/phase3k-completion.md + README

git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-3k-A
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-3k-B
git worktree remove /Users/josemoya/eclipse-workspace/jquantlib-3k-C
git branch -d phase-3k-A phase-3k-B phase-3k-C
```

---

## Reference: C++ probes to generate

All probes → `migration-harness/references/models/marketmodels/products/` and `callability/` JSON files.

1. **OneStepForwardsProbe** — 4-rate grid (rateTimes=[0.5,1.0,1.5,2.0,2.5]), accruals=0.5, forwardRates=[0.04,0.045,0.05,0.055], strikes=[0.04,0.04,0.04,0.04]; output: cash flows[0..3]
2. **MultiStepSwapProbe** — same 4-rate grid, fixedRate=0.04; output: cash flows at each step (payer swap)
3. **MultiStepInverseFloaterProbe** — 4-rate grid; output: cash flows at each step with known forward rates
4. **MultiStepTarnProbe** — 4-rate grid, totalCoupon=0.08; output: which step terminates, final couponPaid
5. **NothingExerciseValueProbe** — output: value = 0.0, isExerciseTime = all true
6. **BermudanSwaptionExerciseProbe** — 4-rate grid, fixedRate=0.04, at-money; output: value at step 0
7. **PathwiseCapletProbe** — 3-rate grid, DeflatedCaplet; output: amount[0..3] (price + 3 deltas)
8. **RatePseudoRootJacobianProbe** — 3-rate 2-factor setup; output: B matrix (6×2) for a known gaussian vector

Script addition: `migration-harness/cpp/probes/marketmodels_products_probe.cpp`

---

## Parallel dispatch note

**L0 is sequential** (blocking, 4 small commits). Once all L0 commits are on main, dispatch three
parallel worktrees:

| Worktree | Work | Critical path |
|---|---|---|
| `jquantlib-3k-A` | Track A: A.1→A.2→(A.3, A.4-A.6, A.7, A.8, A.9 parallel)→A.10→A.11→A.12 | L0 → A.1/A.2 → A.4/A.5/A.6 → B.1→A.10 → A.11 |
| `jquantlib-3k-B` | Track B: B.1-B.4→(B.5-B.6, B.7-B.8, B.9 parallel)→B.10-B.11→B.12→B.13→B.14 | L0 → B.1-B.4 → B.7-B.8 → B.12 |
| `jquantlib-3k-C` | Track C: C.1-C.5 parallel→C.6→C.7→C.8→C.9→C.10→C.11→C.12 | L0 → L0.4 → C.1-C.5 → C.6/C.7 → C.11 |

Track C C.11 (`PathwiseAccountingEngine`) blocks on `LogNormalFwdRateEuler` from Phase 3i. If Phase 3i
is confirmed complete, C.11 can start after C.7 is ready. If Phase 3i is still in flight, defer C.11.

**Estimated total wall-clock:** 3 parallel worktrees × ~6 commits each = ~18 commits. With parallel agents
this can complete in ~3 sessions of focused work.

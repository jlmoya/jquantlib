# Phase 3k Design — MarketModels Products + Callability

**Status:** approved 2026-05-09 (autonomous mode — Phase 3k research)
**Predecessor:** `jquantlib-phase3j-complete` (tests `1150+/0/0/38` est., Phase 3j models complete)

## 1. Context

Phase 3j delivered the concrete market models + calibration framework layer (~4,674 LOC C++: FlatVol,
AbcdVol, adapters, CTSMM caplet calibration family). Phase 3i delivered evolvers + Sobol BrownianGenerator.
Phase 3h delivered the LMM foundation.

Phase 3k covers the **products and callability** layer — the classes that define what is being priced and
the early-exercise machinery — together with the **pathwise Greeks** subsystem. This is the largest
remaining C++ slice of the LMM marketmodels machinery:

| C++ subdirectory | LOC | Role |
|---|---|---|
| `products/` top-level | 719 | Base classes: MultiProductMultiStep, MultiProductOneStep, MarketModelComposite, MultiProductComposite, SingleProductComposite |
| `products/onestep/` | 580 | Four one-step products (Forwards, Optionlets, CoinitialSwaps, CoterminalSwaps) |
| `products/multistep/` | 2,648 | Fourteen multi-step products (Swaps, Swaptions, Forwards, Optionlets, RatchetSwaps, InverseFloaters, TARNs, PeriodCapletSwaptions, Nothin, CashRebate, CallSpecified, ExerciseAdapter, PathwiseWrapper, etc.) |
| `products/pathwise/` | 1,902 | Six pathwise products (Caplet×2, Swaption, Swap, InverseFloater, CashRebate, CallSpecified) |
| `callability/` | 2,143 | Exercise-value interfaces, LS strategy, basis systems, collect-node-data, UpperBoundEngine, SwapRateTrigger, TriggeredSwapExercise, ParametricExerciseAdapter, NothingExerciseValue, BermudanSwaptionExerciseValue |
| `pathwisegreeks/` | 1,678 | RatePseudoRootJacobian, SwaptionPseudoJacobian, BumpInstrumentJacobian, VegaBumpCluster |
| `pathwiseaccountingengine` + `pathwisediscounter` | 1,624 | PathwiseAccountingEngine (smoky-adjoint Greeks), MarketModelPathwiseDiscounter |
| `pathwisemultiproduct.hpp` (already present) | ~60 | Abstract base for pathwise products (3h prereq) |
| **Total** | **~11,354** | |

Also required from `ql/methods/montecarlo/`:
- `ExerciseStrategy<State>` (60 LOC) — template interface; maps to Java interface
- `NodeData` struct (40 LOC) — trivial struct
- `ParametricExercise` (70 LOC) — interface + free function `genericEarlyExerciseOptimization`

Grand total scope: **~11,524 LOC C++**. This is the largest Phase-3 slice to date, justifying a 3-track
decomposition with careful dependency ordering.

## 2. C++ Inventory

### 2.1 products/ top-level (719 LOC)

| File | LOC | Description |
|---|---|---|
| `multiproductmultistep.hpp/.cpp` | 127 | MultiProductMultiStep abstract base — stores rateTimes, evolution; provides `suggestedNumeraires`, `evolution` |
| `multiproductonestep.hpp/.cpp` | 115 | MultiProductOneStep abstract base — same pattern |
| `compositeproduct.hpp/.cpp` | 241 | MarketModelComposite abstract base — `add`, `subtract`, `finalize`, subproduct vector, merged cash-flow times |
| `multiproductcomposite.hpp/.cpp` | 172 | MultiProductComposite (extends MarketModelComposite) — concrete `nextTimeStep`, `numberOfProducts`, `maxCashFlows` |
| `singleproductcomposite.hpp/.cpp` | 64 | SingleProductComposite — wraps a multi-product as a single-product view |

### 2.2 products/onestep/ (580 LOC)

| File | LOC | Description |
|---|---|---|
| `onestepforwards.hpp/.cpp` | 136 | OneStepForwards — forward FRAs in a single step |
| `onestepoptionlets.hpp/.cpp` | 140 | OneStepOptionlets — optionlets in a single step |
| `onestepcoinitialswaps.hpp/.cpp` | 149 | OneStepCoinitialSwaps — co-initial swaps (same start) |
| `onestepcoterminalswaps.hpp/.cpp` | 147 | OneStepCoterminalSwaps — co-terminal swaps |

### 2.3 products/multistep/ (2,648 LOC)

| File | LOC | Description |
|---|---|---|
| `callspecifiedmultiproduct.hpp/.cpp` | 278 | CallSpecifiedMultiProduct — wraps product + ExerciseStrategy + rebate; the core callable product class |
| `multistepperiodcapletswaptions.hpp/.cpp` | 232 | MultiStepPeriodCapletSwaptions — period-adapter caplets and swaptions |
| `multisteptarn.hpp/.cpp` | 185 | MultiStepTarn — Target Accrual Redemption Notes |
| `cashrebate.hpp/.cpp` | 170 | MultiStepCashRebate — fixed cash payments on exercise |
| `multisteppathwisewrapper.hpp/.cpp` | 159 | MultiStepPathwiseWrapper — adapts pathwise product to multi-step interface |
| `multistepratchet.hpp/.cpp` | 161 | MultiStepRatchet — ratchet floater product |
| `multistepswaption.hpp/.cpp` | 164 | MultiStepSwaption — single swaption |
| `multistepinversefloater.hpp/.cpp` | 161 | MultiStepInverseFloater — inverse floater swap |
| `multistepcoinitialswaps.hpp/.cpp` | 147 | MultiStepCoinitialSwaps |
| `multistepcoterminalswaps.hpp/.cpp` | 146 | MultiStepCoterminalSwaps |
| `multistepcoterminalswaptions.hpp/.cpp` | 140 | MultiStepCoterminalSwaptions |
| `multistepoptionlets.hpp/.cpp` | 139 | MultiStepOptionlets |
| `multistepforwards.hpp/.cpp` | 133 | MultiStepForwards |
| `multistepnothing.hpp/.cpp` | 117 | MultiStepNothing — no-op product (for Bermudan payoff extraction) |
| `exerciseadapter.hpp/.cpp` | 147 | ExerciseAdapter — wraps MarketModelExerciseValue as a rebate product |

### 2.4 products/pathwise/ (1,902 LOC)

| File | LOC | Description |
|---|---|---|
| `pathwiseproductcaplet.hpp/.cpp` | 581 | MarketModelPathwiseMultiCaplet + DeflatedCaplet + DeflatedCap |
| `pathwiseproductswaption.hpp/.cpp` | 394 | MarketModelPathwiseMultiDeflatedSwaption |
| `pathwiseproductcallspecified.hpp/.cpp` | 269 | PathwiseProductCallSpecified — callable pathwise product |
| `pathwiseproductinversefloater.hpp/.cpp` | 236 | PathwiseProductInverseFloater |
| `pathwiseproductswap.hpp/.cpp` | 220 | PathwiseProductSwap |
| `pathwiseproductcashrebate.hpp/.cpp` | 192 | PathwiseProductCashRebate |

### 2.5 callability/ (2,143 LOC)

| File | LOC | Description |
|---|---|---|
| `upperboundengine.hpp/.cpp` | 430 | UpperBoundEngine — Andersen upper-bound estimator using inner simulations |
| `collectnodedata.hpp/.cpp` | 245 | `collectNodeData()` free function — collects exercise/control data for LS regression |
| `lsstrategy.hpp/.cpp` | 231 | LongstaffSchwartzExerciseStrategy — LS exercise strategy using basis functions |
| `swapforwardbasissystem.hpp/.cpp` | 191 | SwapForwardBasisSystem — swap rate + forward rate basis functions |
| `triggeredswapexercise.hpp/.cpp` | 156 | TriggeredSwapExercise — parametric exercise by swap rate trigger |
| `nothingexercisevalue.hpp/.cpp` | 148 | NothingExerciseValue — zero exercise value (for Bermudan `rebate`) |
| `bermudanswaptionexercisevalue.hpp/.cpp` | 146 | BermudanSwaptionExerciseValue — swaption exercise value |
| `swapbasissystem.hpp/.cpp` | 135 | SwapBasisSystem — pure swap rate basis functions |
| `swapratetrigger.hpp/.cpp` | 127 | SwapRateTrigger — exercise when swap rate crosses threshold |
| `parametricexerciseadapter.hpp/.cpp` | 127 | ParametricExerciseAdapter — wraps ParametricExercise as ExerciseStrategy |
| `exercisevalue.hpp` | 53 | MarketModelExerciseValue abstract interface |
| `nodedataprovider.hpp` | 52 | MarketModelNodeDataProvider abstract interface |
| `marketmodelbasissystem.hpp` | 39 | MarketModelBasisSystem — extends NodeDataProvider |
| `marketmodelparametricexercise.hpp` | 40 | MarketModelParametricExercise — combines NodeDataProvider + ParametricExercise |

### 2.6 pathwisegreeks/ (1,678 LOC)

| File | LOC | Description |
|---|---|---|
| `swaptionpseudojacobian.hpp/.cpp` | 494 | SwaptionPseudoJacobian — pathwise vega Jacobian for swaptions |
| `ratepseudorootjacobian.hpp/.cpp` | 478 | RatePseudoRootJacobian + Numerical + AllElements — rate-to-pseudo-root sensitivity |
| `vegabumpcluster.hpp/.cpp` | 373 | VegaBumpCluster — market-model vega bump cluster |
| `bumpinstrumentjacobian.hpp/.cpp` | 325 | BumpInstrumentJacobian — instrument-to-model Jacobian |

### 2.7 pathwise accounting engine + discounter (1,624 LOC)

| File | LOC | Description |
|---|---|---|
| `pathwiseaccountingengine.hpp/.cpp` | 1,478 | PathwiseAccountingEngine — Giles-Glasserman adjoint Delta engine; also PathwiseVegasAccountingEngine |
| `pathwisediscounter.hpp/.cpp` | 146 | MarketModelPathwiseDiscounter |

### 2.8 methods/montecarlo prerequisites (~170 LOC)

| File | LOC | Description |
|---|---|---|
| `exercisestrategy.hpp` | 42 | ExerciseStrategy<State> template interface |
| `nodedata.hpp` | 40 | NodeData struct (exerciseValue, cumulatedCashFlows, values[], controlValue, isValid) |
| `parametricexercise.hpp` | 70 + fn | ParametricExercise interface + `genericEarlyExerciseOptimization` free function |

## 3. Java Current State

### 3.1 Already landed (Phase 3h-3j)

All Java lives under `org.jquantlib.model.marketmodels.*`:

| Class | Package | Phase |
|---|---|---|
| MarketModelMultiProduct (abstract base) | model.marketmodels | 3h |
| MarketModelEvolver | model.marketmodels | 3h |
| MarketModelFactory | model.marketmodels | 3h |
| MarketModelDiscounter | model.marketmodels | 3h |
| AccountingEngine | model.marketmodels | 3h |
| EvolutionDescription | model.marketmodels | 3h |
| CurveState | model.marketmodels | 3h |
| BrownianGenerator, BrownianGeneratorFactory | model.marketmodels | 3h |
| All curvestates (LMM, CotSwap, CMSwap) | model.marketmodels.curvestates | 3h |
| All drift calculators | model.marketmodels.driftcomputation | 3h |
| Correlations (Exponential, TimeHomogeneous) | model.marketmodels.correlations | 3h |
| MT + Sobol BrownianGenerators | model.marketmodels.browniangenerators | 3h/3i |
| All 9 evolvers | model.marketmodels.evolvers | 3i |
| MarketModel (abstract base) | model.marketmodels | 3h |
| FlatVol, AbcdVol, adapters | model.marketmodels.models | 3j |
| CTSMMCaplet calibration family | model.marketmodels.models | 3j |

### 3.2 Not yet in Java — Phase 3k scope

**Zero classes** in the `products/`, `products/multistep/`, `products/onestep/`, `products/pathwise/`
subdirectories exist in Java. Zero callability classes exist.

### 3.3 Prerequisite gap analysis

| C++ dependency | Java state | Action |
|---|---|---|
| `ExerciseStrategy<CurveState>` template | Missing — no `ExerciseStrategy` interface | New interface; Track A.0 prereq |
| `NodeData` struct | Missing | New Java class/record; Track B.0 prereq |
| `ParametricExercise` interface | Missing | New interface; Track B.0 prereq |
| `genericEarlyExerciseOptimization` free fn | Missing | Port as static method in `ParametricExercise`; Track B.0 |
| `StrikedTypePayoff` | Present: `instruments.StrikedTypePayoff` | Usable directly |
| `Payoff` base | Present: `instruments.Payoff` | Usable directly |
| `MarketModelMultiProduct.CashFlow` | Present (inner class) | Usable directly |
| `SequenceStatisticsInc` | `GenericSequenceStatistics` / `SequenceStatistics` present | Map to Java equivalent |
| `PathwiseMultiProduct` abstract base | Present as `pathwisemultiproduct.hpp` (see note) | Verify Java class exists |
| `LMMDriftCalculator` | Present | Present |
| `Matrix` | Present | Present |

**Note on PathwiseMultiProduct:** The abstract base `MarketModelPathwiseMultiProduct` is defined in
`pathwisemultiproduct.hpp` (at the `ql/models/marketmodels/` level, not a subdirectory). This file
defines the `CashFlow` inner struct with a `std::vector<Real> amount` field (unlike the non-pathwise
`CashFlow` which has a single `double amount`). A Java equivalent must be confirmed or created as a
prerequisite for Track C.

**Note on ExerciseStrategy:** The C++ template `ExerciseStrategy<State>` is always instantiated as
`ExerciseStrategy<CurveState>`. In Java this maps to a simple interface `ExerciseStrategy` (non-generic,
since all exercises are over `CurveState`). This keeps the Java API clean and avoids Java generics
complexity.

**Note on Clone utility:** C++ `Clone<T>` is a deep-copy smart pointer. In Java all parameters are
passed as direct references (object identity). The clone semantic is preserved by calling `clone()` at
construction time (as done in `AccountingEngine`). No Java `Clone` class needed.

**Note on `std::valarray<bool>`:** Used in several callability classes to track which evolution times
are exercise times. Map to `boolean[]` in Java.

## 4. Dependency Graph

```
L0 — Monte Carlo prereqs (no marketmodels deps):
  L0.1  ExerciseStrategy interface        (ql/methods/montecarlo/exercisestrategy.hpp)
  L0.2  NodeData class                    (ql/methods/montecarlo/nodedata.hpp)
  L0.3  ParametricExercise interface +    (ql/methods/montecarlo/parametricexercise.hpp)
        genericEarlyExerciseOptimization
  L0.4  MarketModelPathwiseMultiProduct   verify/complete pathwise abstract base + CashFlow inner
        abstract base

Track A — Products layer (all depend on MarketModelMultiProduct[3h], EvolutionDescription[3h]):
  A.1  MultiProductMultiStep abstract base      (products/multiproductmultistep.hpp/.cpp)
  A.2  MultiProductOneStep abstract base        (products/multiproductonestep.hpp/.cpp)
  A.3  onestep/ four products                   (onestep/*.hpp/.cpp, depends A.2)
         OneStepForwards, OneStepOptionlets,
         OneStepCoinitialSwaps, OneStepCoterminalSwaps
  A.4  MarketModelComposite                     (products/compositeproduct.hpp/.cpp, depends A.1)
  A.5  MultiProductComposite                    (products/multiproductcomposite.hpp/.cpp, depends A.4)
  A.6  SingleProductComposite                   (products/singleproductcomposite.hpp/.cpp, depends A.4)
  A.7  multistep/simple products (batch 1)      (depends A.1)
         MultiStepForwards, MultiStepOptionlets,
         MultiStepSwaption, MultiStepSwap,
         MultiStepNothing, MultiStepCashRebate
  A.8  multistep/complex products (batch 2)     (depends A.1)
         MultiStepCoinitialSwaps, MultiStepCoterminalSwaps,
         MultiStepCoterminalSwaptions, MultiStepRatchet,
         MultiStepInverseFloater, MultiStepTarn
  A.9  MultiStepPeriodCapletSwaptions           (depends A.1, StrikedTypePayoff[present])
  A.10 ExerciseAdapter                          (depends A.1, callability.MarketModelExerciseValue[B.1])
  A.11 CallSpecifiedMultiProduct                (depends A.1, L0.1 ExerciseStrategy, B.1 ExerciseValue,
                                                 A.10 ExerciseAdapter)
  A.12 MultiStepPathwiseWrapper                 (depends A.1, L0.4 PathwiseMultiProduct)

Track B — Callability layer (depends on L0.1, L0.2, L0.4 MarketModelMultiProduct[3h]):
  B.1  MarketModelExerciseValue interface       (callability/exercisevalue.hpp)
  B.2  MarketModelNodeDataProvider interface    (callability/nodedataprovider.hpp)
  B.3  MarketModelBasisSystem interface         (callability/marketmodelbasissystem.hpp, depends B.2)
  B.4  MarketModelParametricExercise interface  (callability/marketmodelparametricexercise.hpp,
                                                 depends B.2, L0.3)
  B.5  NothingExerciseValue                     (callability/nothingexercisevalue.hpp/.cpp, depends B.1)
  B.6  BermudanSwaptionExerciseValue            (callability/bermudanswaptionexercisevalue.hpp/.cpp,
                                                 depends B.1, CurveState[3h], SwapForwardMappings[3h/3j])
  B.7  SwapBasisSystem                          (callability/swapbasissystem.hpp/.cpp, depends B.3)
  B.8  SwapForwardBasisSystem                   (callability/swapforwardbasissystem.hpp/.cpp, depends B.3)
  B.9  SwapRateTrigger                          (callability/swapratetrigger.hpp/.cpp, depends L0.1)
  B.10 TriggeredSwapExercise                    (callability/triggeredswapexercise.hpp/.cpp,
                                                 depends B.1, L0.1)
  B.11 ParametricExerciseAdapter                (callability/parametricexerciseadapter.hpp/.cpp,
                                                 depends B.4, L0.3)
  B.12 LongstaffSchwartzExerciseStrategy        (callability/lsstrategy.hpp/.cpp,
                                                 depends B.3, B.1, L0.1, MarketModelDiscounter[3h])
  B.13 collectNodeData free function            (callability/collectnodedata.hpp/.cpp,
                                                 depends B.2, B.1, L0.2, MarketModelEvolver[3h],
                                                 MarketModelDiscounter[3h])
  B.14 UpperBoundEngine                         (callability/upperboundengine.hpp/.cpp,
                                                 depends A.5 MultiProductComposite, L0.1, B.1,
                                                 MarketModelEvolver[3h], MarketModelDiscounter[3h])

Track C — Pathwise products + engine:
  C.1  products/pathwise/pathwiseproductcaplet  (depends L0.4, depends A.1)
  C.2  products/pathwise/pathwiseproductswaption (depends L0.4)
  C.3  products/pathwise/pathwiseproductswap    (depends L0.4)
  C.4  products/pathwise/pathwiseproductinversefloater (depends L0.4)
  C.5  products/pathwise/pathwiseproductcashrebate (depends L0.4)
  C.6  pathwisediscounter                       (pathwisediscounter.hpp/.cpp — standalone)
  C.7  ratepseudorootjacobian                   (pathwisegreeks/ratepseudorootjacobian, depends LMMDriftCalculator[3h])
  C.8  swaptionpseudojacobian                   (pathwisegreeks/swaptionpseudojacobian)
  C.9  bumpinstrumentjacobian                   (pathwisegreeks/bumpinstrumentjacobian, depends C.7, C.8)
  C.10 vegabumpcluster                          (pathwisegreeks/vegabumpcluster, depends C.9)
  C.11 PathwiseAccountingEngine                 (pathwiseaccountingengine, depends C.1-C.6, C.7,
                                                 L0.4, LogNormalFwdRateEuler[3i])
  C.12 products/pathwise/pathwiseproductcallspecified (depends L0.4, L0.1)
       → deferred to Phase 3k.5 if B.14 UpperBoundEngine complexity blocks timeline
```

## 5. Test-Suite Scope

The C++ test suite for Phase 3k is in `test-suite/marketmodel.cpp` (4,662 LOC total):

| Test case | Lines | Exercises |
|---|---|---|
| `testOneStepForwardsAndOptionlets` | 699-783 | OneStepForwards, OneStepOptionlets, MultiProductComposite, AccountingEngine |
| `testOneStepNormalForwardsAndOptionlets` | 784-867 | Same with NormalPc evolver |
| `testInverseFloater` | 868-1211 | MultiStepInverseFloater, MultiStepSwap, MultiProductComposite |
| `testAllMultiStepProducts` | 1212-1379 | All major multistep products batch test |
| `testCallableSwapNaif` | 1380-1532 | SwapRateTrigger, CallSpecifiedMultiProduct, NothingExerciseValue, MultiStepSwap, MultiStepNothing, ExerciseAdapter, UpperBoundEngine |
| `testCallableSwapLS` | 1533-1708 | LongstaffSchwartzExerciseStrategy, SwapBasisSystem, collectNodeData, CallSpecifiedMultiProduct |
| `testCallableSwapAnderson` | 1709-1875 | ParametricExercise, BermudanSwaptionExerciseValue, TriggeredSwapExercise, ParametricExerciseAdapter |
| `testGreeks` | 1876-2088 | MultiStepOptionlets, LogNormalFwdRateEulerConstrained, finite-difference Greeks |
| `testPathwiseGreeks` | 2089-2323 | PathwiseAccountingEngine, MarketModelPathwiseMultiCaplet, RatePseudoRootJacobian |
| `testPathwiseVegas` | 2324-3471 | PathwiseVegasAccountingEngine, pathwise products, VegaBumpCluster |
| `testPathwiseMarketVegas` | 3472-4131 | Full pathwise market Vegas, BumpInstrumentJacobian, SwaptionPseudoJacobian |

### 5.1 Java test mapping

**Track A tests (exact/tight tier):**
- `MultiProductCompositeTest` — add/finalize/nextTimeStep; verify merged cash-flow times
- `OneStepProductsTest` — OneStepForwards + OneStepOptionlets; verify cash flows at one step with known rate inputs; tolerance: exact for indices, 1e-12 for amounts
- `MultiStepProductsTest` — MultiStepForwards, MultiStepOptionlets, MultiStepSwap, MultiStepSwaption; verify cash flows across multiple steps; tolerance: 1e-12
- `ComplexProductsTest` — MultiStepInverseFloater, MultiStepRatchet, MultiStepTarn; one test each with C++ probe values; tolerance: 1e-12

**Track B tests (integration level, loose tier):**
- `CallabilityInterfacesTest` — compile-level test of all callability interfaces
- `NothingAndBermudanTest` — NothingExerciseValue.value() = 0; BermudanSwaptionExerciseValue.value() vs C++ probe; tolerance: 1e-10
- `SwapRateTriggerTest` — trigger fires at expected exercise times; tolerance: exact
- `LSStrategyTest` — LongstaffSchwartzExerciseStrategy + collectNodeData; run with simple FlatVol model, verify callable swap price vs C++ probe; tolerance: loose (1e-4 rel, MC noise)
- `UpperBoundEngineTest` — verify upper bound > lower bound for a callable swap; tolerance: loose (1e-4)

**Track C tests (pathwise, tight then integration):**
- `PathwiseCapletTest` — MarketModelPathwiseMultiCaplet: pathwise cash flows at one step vs C++ probe; tolerance: 1e-10
- `PathwiseAccountingEngineTest` — run PathwiseAccountingEngine on a 5-rate grid; verify Delta vs finite-difference analytic; tolerance: loose (1e-4 rel)

## 6. Phase Decomposition — Three Tracks

### LOC per track summary

| Track | C++ LOC | Java classes est. | Complexity |
|---|---|---|---|
| L0 — Prereqs | ~170 + verify | 5 new + 1 verify | Low |
| Track A — Products | ~5,947 | ~25 new | Medium (repetitive pattern) |
| Track B — Callability | ~2,313 | ~14 new | High (stateful LS regression) |
| Track C — Pathwise | ~3,302 | ~13 new | Very high (Jacobians + adjoint engine) |
| **Total** | **~11,732** | **~57 new** | |

### 6.1 L0 — Monte Carlo prereqs + pathwise base (~170 LOC)

Blocks everything. Must land on main before Track A/B/C dispatch.

- **L0.1** `ExerciseStrategy` interface (42 LOC) — `exerciseTimes()`, `relevantTimes()`, `reset()`, `exercise(CurveState)`, `nextStep(CurveState)`, `clone()`; Java package: `org.jquantlib.methods.montecarlo`
- **L0.2** `NodeData` class (40 LOC) — plain data object: `exerciseValue`, `cumulatedCashFlows`, `double[] values`, `controlValue`, `isValid`; Java package: `org.jquantlib.methods.montecarlo`
- **L0.3** `ParametricExercise` interface + `genericEarlyExerciseOptimization` free function (70 LOC); Java package: `org.jquantlib.methods.montecarlo`
- **L0.4** `MarketModelPathwiseMultiProduct` — verify/complete Java abstract base in `org.jquantlib.model.marketmodels`; the C++ `CashFlow` inner struct has `int timeIndex` + `double[] amount` (vector), unlike the non-pathwise `CashFlow`

### 6.2 Track A — Products layer (~5,947 LOC)

All classes go into `org.jquantlib.model.marketmodels.products` (top-level products) or
`org.jquantlib.model.marketmodels.products.onestep` / `.multistep` subpackages.

Sequential within-track order:
1. A.1 `MultiProductMultiStep` + A.2 `MultiProductOneStep` (blocking base classes)
2. A.3 four onestep products (parallel after A.2)
3. A.4 `MarketModelComposite` + A.5 `MultiProductComposite` + A.6 `SingleProductComposite` (after A.1)
4. A.7 simple multistep batch (after A.1)
5. A.8 complex multistep batch (after A.1)
6. A.9 `MultiStepPeriodCapletSwaptions` (after A.1)
7. A.10 `ExerciseAdapter` (after A.1 and B.1)
8. A.11 `CallSpecifiedMultiProduct` (after A.1, L0.1, B.1, A.10)
9. A.12 `MultiStepPathwiseWrapper` (after A.1, L0.4)

Parallelism: A.3/A.4/A.7/A.8/A.9 can all run in parallel after A.1+A.2 are on main. A.10 can run
after B.1. A.11 requires A.10 and B.1.

### 6.3 Track B — Callability (~2,313 LOC)

Package: `org.jquantlib.model.marketmodels.callability`

Sequential-compatible order (with some parallelism):
1. B.1 `MarketModelExerciseValue` + B.2 `MarketModelNodeDataProvider` + B.3 `MarketModelBasisSystem` + B.4 `MarketModelParametricExercise` (4 tiny interfaces, ship in one commit)
2. B.5 `NothingExerciseValue` + B.6 `BermudanSwaptionExerciseValue` (parallel after B.1)
3. B.7 `SwapBasisSystem` + B.8 `SwapForwardBasisSystem` (parallel after B.3)
4. B.9 `SwapRateTrigger` (after L0.1)
5. B.10 `TriggeredSwapExercise` (after B.1, L0.1)
6. B.11 `ParametricExerciseAdapter` (after B.4, L0.3)
7. B.12 `LongstaffSchwartzExerciseStrategy` (after B.3, B.1, L0.1, all exercise values)
8. B.13 `collectNodeData` free function (after B.2, B.1, L0.2)
9. B.14 `UpperBoundEngine` (after A.5, L0.1, B.1)

Track B.12-B.14 are the most complex and require the full product + callability stack; schedule them
after the main A.5-A.11 sequence.

### 6.4 Track C — Pathwise (~3,302 LOC)

Package: `org.jquantlib.model.marketmodels.products.pathwise` (products) and
`org.jquantlib.model.marketmodels.pathwisegreeks` (Jacobians).

Order:
1. C.1-C.5 pathwise products (parallel after L0.4; C.5 `CashRebate` simplest first, then C.3 `Swap`, C.4 `InverseFloater`, C.2 `Swaption`, C.1 `Caplet` which has three classes)
2. C.6 `MarketModelPathwiseDiscounter` (standalone; no product deps)
3. C.7 `RatePseudoRootJacobian` (depends LMMDriftCalculator[3h])
4. C.8 `SwaptionPseudoJacobian` (standalone, uses Matrix)
5. C.9 `BumpInstrumentJacobian` (after C.7, C.8)
6. C.10 `VegaBumpCluster` (after C.9)
7. C.11 `PathwiseAccountingEngine` (after C.1-C.6, C.7, LogNormalFwdRateEuler[3i]) — the most complex class in the entire 3k scope
8. C.12 `PathwiseProductCallSpecified` (after L0.4, L0.1) — can defer to Phase 3k.5

Track C can run in parallel with Track B after L0 and A.1+A.2. C.11 (PathwiseAccountingEngine) should
be last and may be the implementation bottleneck (1,195-LOC .cpp file).

## 7. Key Design Decisions

- **P3K-1: Package structure** — Four Java packages parallel to C++:
  - `org.jquantlib.methods.montecarlo` (ExerciseStrategy, NodeData, ParametricExercise — prereqs)
  - `org.jquantlib.model.marketmodels.products` (abstract bases + composites)
  - `org.jquantlib.model.marketmodels.products.onestep` / `.multistep` / `.pathwise`
  - `org.jquantlib.model.marketmodels.callability`
  - `org.jquantlib.model.marketmodels.pathwisegreeks`

- **P3K-2: ExerciseStrategy Java interface** — Non-generic; C++ `ExerciseStrategy<CurveState>` maps
  directly to Java `ExerciseStrategy` (concrete state type is always `CurveState`). This avoids Java
  generics and matches established JQuantLib patterns.

- **P3K-3: Clone<T> → direct reference with explicit clone()** — C++ `Clone<T>` call sites become
  `product.clone()` at Java construction time (same as `AccountingEngine` pattern in Phase 3h). All
  constructors receiving a product/exerciseValue call `.clone()` on the argument.

- **P3K-4: std::valarray<bool> → boolean[]** — Exercise-time boolean arrays are always fixed-size;
  `boolean[]` is idiomatic Java replacement.

- **P3K-5: Pathwise CashFlow inner struct** — `MarketModelPathwiseMultiProduct.CashFlow` has
  `int timeIndex` + `double[] amount` (one element per rate, for Greeks). This differs from the
  non-pathwise `CashFlow` (single `double amount`). The Java `MarketModelPathwiseMultiProduct` must
  declare this as a separate inner class. If `pathwisemultiproduct.hpp` has already been partially
  ported, verify the `CashFlow` inner struct includes the `double[] amount` array field.

- **P3K-6: UpperBoundEngine inner evolver vector** — C++ takes
  `std::vector<ext::shared_ptr<MarketModelEvolver>>` inner evolvers. Java: `List<MarketModelEvolver>`.
  The outer evolver is a single `MarketModelEvolver`; no special shared_ptr handling needed.

- **P3K-7: collectNodeData free function placement** — Port as a static method of a utility class
  `MarketModelCallabilityUtils` in the callability package, or as a `collectNodeData(...)` static method
  in the `LongstaffSchwartzExerciseStrategy` class. Prefer standalone class for testability.
  Java class: `CollectNodeData` in `org.jquantlib.model.marketmodels.callability`.

- **P3K-8: genericEarlyExerciseOptimization** — Port as a static method in `ParametricExercise` Java
  interface (using a default method would not work since it has a body; use a companion
  `ParametricExerciseUtils` class or place in `ParametricExerciseAdapter`). Simplest: static method in
  a `GenericEarlyExercise` utility class in `org.jquantlib.methods.montecarlo`.

- **P3K-9: PathwiseAccountingEngine two classes** — C++ `pathwiseaccountingengine.hpp/.cpp` defines
  two classes: `PathwiseAccountingEngine` (Delta Greeks) and `PathwiseVegasAccountingEngine` (Vega
  Greeks). Both have the same structure but `PathwiseVegasAccountingEngine` adds bump-cluster machinery.
  Port both in the same WI C.11 commit.

- **P3K-10: Tolerance tiers**
  - Composite product structural tests (dimensions, times): exact
  - One-step/multi-step cash flow amounts (deterministic): tight (1e-12 rel)
  - Callable swap prices (Monte Carlo): loose (1e-4 rel, 1e-6 abs)
  - Longstaff-Schwartz exercise strategy convergence: loose (1e-3 rel)
  - Upper bound vs lower bound ordering: functional test (upper > lower - tolerance)
  - Pathwise caplet Greeks (adjoint): comparison vs finite-difference Greeks; loose (1e-3 rel)

- **P3K-11: MultiStepPeriodCapletSwaptions StrikedTypePayoff** — The constructor takes
  `List<StrikedTypePayoff>` for payoffs. Java `StrikedTypePayoff` is present in
  `org.jquantlib.instruments`. No new port needed; just use existing class.

- **P3K-12: PathwiseProductCallSpecified deferred** — `pathwiseproductcallspecified.hpp/.cpp` (269 LOC)
  depends on both pathwise product infrastructure AND callability strategy. Defer to Phase 3k.5 if it
  creates a circular ordering problem between Track B and Track C. It is not tested by any priority
  test case.

- **P3K-13: MarketModelPathwiseMultiProduct base** — If the Java class does not yet exist or lacks the
  `boolean alreadyDeflated()` method, create/complete it as L0.4 before Track C work. This base is in
  `ql/models/marketmodels/pathwisemultiproduct.hpp` — not in the `products/` subdirectory.

## 8. Pause Triggers

Carry-forward A1-A35 (existing system triggers).
Additional for Phase 3k:

- **P3K-A:** If `ParametricExercise.genericEarlyExerciseOptimization` depends on `EndCriteria` or
  `OptimizationMethod` classes not yet ported, assess scope of the optimization prerequisite chain.
  The function signature requires both — check whether the existing Java optimization package covers
  these or whether stubs suffice.

- **P3K-B:** If `PathwiseAccountingEngine` depends on `LogNormalFwdRateEuler` in a way that requires
  internal evolver state not exposed by the Phase 3i port, pause and assess whether Phase 3i needs
  an alignment commit.

- **P3K-C:** If `UpperBoundEngine` requires `SequenceStatisticsInc` (C++ typedef for
  `GenericSequenceStatistics<IncrementalStatistics>`) and the Java class is missing the required
  accumulation methods, add them as an alignment commit before B.14.

- **P3K-D:** If `SwapForwardMappings` methods called by `BermudanSwaptionExerciseValue` or
  `SwapBasisSystem` are not yet ported from Phase 3j (the partial L0.5 completion), add an align
  commit before B.6.

## 9. Outcome Forecast

| Metric | Phase 3j tip | Phase 3k target |
|---|---|---|
| Tests | 1150+/0/0/38 (est.) | 1220+/0/0/38 (~70+ new tests) |
| MarketModels Java surface | ~46 classes | ~100+ classes |
| Phase 3l readiness | Products needed | Full LMM simulation chain complete |
| New Java packages | 6 | 11 (+ products, products.onestep, products.multistep, products.pathwise, callability, pathwisegreeks) |

## 10. Phase 3k.5 Scope (conditional)

If any of the following are not completed in Phase 3k proper, they form a small Phase 3k.5:

1. `PathwiseProductCallSpecified` (P3K-12 deferral candidate)
2. `genericEarlyExerciseOptimization` body (P3K-A trigger if optimization prereqs missing)
3. Full `PathwiseVegasAccountingEngine` if C.11 is split
4. Any pathwise Greeks that exceed Track C timeline

Phase 3k.5 is not pre-designed; scope is defined by what 3k leaves incomplete.

## 11. Phase 3i Interaction

Phase 3k Track C (`PathwiseAccountingEngine`) depends directly on `LogNormalFwdRateEuler` from Phase
3i. Track A and Track B do not depend on any Phase 3i class. Phase 3i must be **confirmed complete
and merged** before Track C C.11 (`PathwiseAccountingEngine`) can start. If Phase 3i is still in
flight, delay C.11 until it lands; proceed with C.1-C.10 in parallel.

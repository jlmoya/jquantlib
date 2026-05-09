# Phase 3h Design — MarketModels LMM Foundation (First Slice)

**Status:** approved 2026-05-09 (autonomous mode — twenty-second autonomous phase)
**Predecessor:** `jquantlib-phase3g-complete` (tests `1027/0/0/38`, scanner WIP=0)

## 1. Context

Phase 3g closed the credit subsystem. Phase 3h begins the `org.jquantlib.model.marketmodels` subsystem, which is the Java port of QuantLib's `ql/models/marketmodels/` — the Libor Market Model (LMM) engine family.

This is the largest remaining new subsystem: 24 437 LOC of C++ across 222 files in 11 subdirectories. The Java starting surface is minimal — five stubs in `org.jquantlib.model.marketmodels` (AccountingEngine, BrownianGenerator, BrownianGeneratorFactory, CurveState, MarketModelEvolver), all nearly empty.

Phase 3h covers only the **LMM foundation layer** — the minimum set of classes that must exist before any evolver or concrete model can compile. Total Phase 3h C++ scope: ~3 600 LOC.

## 2. C++ Inventory (full subsystem, 24 437 LOC)

### Root-level abstractions (~4 596 LOC, excludes pathwiseaccountingengine)
| File (hpp+cpp pair) | LOC | Role |
|---|---|---|
| marketmodel.hpp/.cpp | 68+75 | Abstract base: MarketModel + MarketModelFactory |
| evolver.hpp | 49 | Abstract base: MarketModelEvolver |
| multiproduct.hpp | 77 | Abstract base: MarketModelMultiProduct |
| curvestate.hpp/.cpp | 108+126 | Abstract base: CurveState + free functions |
| evolutiondescription.hpp/.cpp | 119+190 | EvolutionDescription + numeraire helpers |
| browniangenerator.hpp | 51 | Abstract base: BrownianGenerator + BrownianGeneratorFactory |
| accountingengine.hpp/.cpp | 71+122 | Monte Carlo path accumulator |
| discounter.hpp/.cpp | 44+56 | MarketModelDiscounter |
| utilities.hpp/.cpp | 50+126 | mergeTimes, isInSubset, checkIncreasing… |
| swapforwardmappings.hpp/.cpp | 91+221 | Swap/forward mapping functions |
| forwardforwardmappings.hpp/.cpp | 63+125 | Forward/forward mapping |
| marketmodeldifferences.hpp/.cpp | 48+117 | Rate differences |
| constrainedevolver.hpp | 54 | ConstrainedEvolver interface |
| piecewiseconstantcorrelation.hpp | 54 | PiecewiseConstantCorrelation interface |
| pathwiseaccountingengine.hpp/.cpp | 283+1195 | Pathwise Greek engine (deferred to Phase 3k) |
| pathwisediscounter.hpp/.cpp | 62+84 | Pathwise discounter (deferred to Phase 3k) |
| pathwisemultiproduct.hpp | 84 | Pathwise multi-product interface |
| proxygreekengine.hpp/.cpp | 82+191 | Proxy Greek engine (deferred to Phase 3k) |
| historicalratesanalysis.hpp/.cpp | 80+95 | Historical rates (deferred) |
| historicalforwardratesanalysis.hpp | 302 | Historical fwd rates (deferred) |

### Subdirectory totals
| Subdirectory | LOC | Phase |
|---|---|---|
| curvestates/ (3 concrete CurveStates) | 791 | 3h |
| driftcomputation/ (4 drift calculators) | 917 | 3h |
| correlations/ (3 correlation models) | 485 | 3h |
| browniangenerators/ (MT + Sobol) | 543 | 3h/3i |
| evolvers/ (9 evolvers + volprocesses) | 2 761 | 3i |
| models/ (FlatVol, AbcdVol, calibration) | 4 674 | 3i/3j |
| products/ (multistep, onestep, pathwise) | 5 849 | 3j |
| callability/ (Bermudan exercise, LS) | 2 143 | 3k |
| pathwisegreeks/ (Greeks by bumping) | 1 678 | 3k |

### Test-suite files
| File | LOC |
|---|---|
| test-suite/marketmodel.cpp | 4 663 |
| test-suite/marketmodel_smm.cpp | 507 |
| test-suite/marketmodel_cms.cpp | 524 |
| test-suite/marketmodel_smmcapletcalibration.cpp | 337 |
| test-suite/marketmodel_smmcapletalphacalibration.cpp | 346 |
| test-suite/marketmodel_smmcaplethomocalibration.cpp | 608 |
| test-suite/libormarketmodel.cpp | 465 |
| test-suite/libormarketmodelprocess.cpp | 327 |
| **Total** | **7 777** |

## 3. Java Current State

### `org.jquantlib.model.marketmodels` (5 stubs)
| Class | State |
|---|---|
| `AccountingEngine` | Empty stub — constructor + commented-out body |
| `BrownianGenerator` | Stub — correct abstract method signatures (nextStep/nextPath/numberOfFactors/numberOfSteps) but signature mismatch: C++ `nextStep(std::vector<Real>&)` returns `Real`; Java has `nextStep()` no-arg |
| `BrownianGeneratorFactory` | Stub — correct abstract `create(int factors, int steps)` |
| `CurveState` | Stub — partial constructor + 2 commented-out method bodies, ~90% of abstract methods missing |
| `MarketModelEvolver` | Stub — correct abstract method signatures, but `numeraires()` returns `int[]` (should be `List<Integer>` or `int[]` per C++ `vector<Size>`) — acceptable as-is |

### `org.jquantlib.legacy.libormarkets` (6 classes — LFM legacy, NOT LMM)
These are `LmVolatilityModel`, `LmCorrelationModel`, `LfmCovarianceProxy`, `LmFixedVolatilityModel`, `LmLinearExponentialCorrelationModel`, `LmLinearExponentialVolatilityModel`. These belong to the **legacy LFM** (LiborFlowModel, in `ql/models/`) and are not part of the marketmodels subsystem. Do not conflate.

### Prereq infrastructure available
| Component | State |
|---|---|
| `Matrix`, `Array` | Complete (Phase 1 / Phase 2a) |
| `PseudoSqrt` | 607 LOC — complete |
| `SobolRsg` | 1 702 LOC — complete |
| `MersenneTwisterUniformRng` | Present |
| `RandomSequenceGenerator` | Present |
| `BrownianBridge` | 243 LOC — present |
| `InverseCumulativeRsg` | Present |
| `GenericSequenceStatistics` | 607 LOC — complete (`add`, `mean`, etc.) |
| `SequenceStatistics` | 62 LOC — thin wrapper, present |
| `GaussianStatistics` | Present |

**Key gap:** C++ `AccountingEngine` uses `SequenceStatisticsInc` (incremental variant). Java has `GenericSequenceStatistics` which provides the incremental semantics under a different name. Phase 3h must map the Java name correctly.

## 4. Dependency Graph (Phase 3h scope)

```
utilities             (no deps on LMM)
evolutiondescription  (depends on: utilities)
curvestate            (depends on: evolutiondescription)
  lmmcurvestate       (extends: curvestate)
  coterminalswapcurvestate (extends: curvestate)
  cmswapcurvestate    (extends: curvestate)
piecewiseconstantcorrelation (no deps)
  correlations/expcorrelations
  correlations/timehomogeneousforwardcorrelation
  correlations/cotswapfromfwdcorrelation
driftcomputation/lmmdriftcalculator   (depends on: Matrix, LMMCurveState)
driftcomputation/lmmnormaldriftcalculator (same)
driftcomputation/smmdriftcalculator   (depends on: CoterminalSwapCurveState)
driftcomputation/cmsmmdriftcalculator (depends on: CMSwapCurveState)
marketmodel           (depends on: evolutiondescription, Matrix)
discounter            (depends on: curvestate)
accountingengine      (depends on: evolver, multiproduct, discounter, statistics)
browniangenerators/mtbrowniangenerator  (depends on: browniangenerator, MT RNG)
browniangenerators/sobolbrowniangenerator (depends on: SobolRsg, BrownianBridge)
```

Evolvers (`lognormalfwdratepc` etc.) depend on `LMMDriftCalculator` and `LMMCurveState` — so both must land in Phase 3h before Phase 3i begins.

## 5. Phase Decomposition Recommendation

### Phase 3h (~3 600 LOC C++) — LMM Foundation
**Scope:** All root abstractions + curvestates + driftcomputation + correlations + MTBrownianGenerator.

**Rationale:** This is the minimal set needed for Phase 3i evolvers. The Sobol generator is deferred to 3h.5/3i because it needs BrownianBridge integration testing against the MC engine.

Track A (foundation + curvestates + drift): ~2 300 LOC
Track B (correlations + MT Brownian + accounting engine close-out): ~1 300 LOC

### Phase 3i (~4 500 LOC C++) — Evolvers + SobolBrownianGenerator
**Scope:** All 9 evolvers (lognormalfwdratepc, euler, balland, ipc, iballand, eulerconstrained, cotswap, cmswap, normalfwdrate) + svddfwdratepc + SobolBrownianGenerator + volprocesses/squarerootandersen.

Tracks: one per evolver family (LMM-lognormal, CotSwap, CMSwap, Normal).

### Phase 3j (~4 700 LOC C++) — Concrete Models + Calibration
**Scope:** models/ subdirectory — FlatVol, AbcdVol, PseudoRootFacade, FwdPeriodAdapter, FwdToCotSwapAdapter, CotSwapToFwdAdapter, PiecewiseConstantVariance, alphaform/alphafinder, CTSMM caplet calibration, caplet coterminal family.

### Phase 3k (~6 700 LOC C++) — Products + Callability
**Scope:** products/ (multistep, onestep, composite) + callability/ (Bermudan, LS, triggers) + PathwiseAccountingEngine + ProxyGreekEngine.

### Phase 3l (~1 700 LOC C++) — Pathwise Greeks + Historical Analysis
**Scope:** pathwisegreeks/ (BumpInstrumentJacobian, RatePseudoRootJacobian, SwaptionPseudoJacobian, VegaBumpCluster) + historicalforwardratesanalysis + proxygreekengine.

## 6. Phase 3h Scope Detail (Track A + Track B)

### Track A — Foundation + CurveStates + DriftCalcs (~2 300 LOC C++)
| Work item | C++ files | LOC | Priority |
|---|---|---|---|
| A.1 utilities | utilities.hpp/.cpp | 176 | L0 |
| A.2 evolutiondescription | evolutiondescription.hpp/.cpp | 309 | L0 |
| A.3 CurveState base | curvestate.hpp/.cpp | 234 | L1 |
| A.4 LMMCurveState | curvestates/lmmcurvestate.hpp/.cpp | 293 | L1 |
| A.5 CoterminalSwapCurveState | curvestates/coterminalswapcurvestate.hpp/.cpp | 234 | L1 |
| A.6 CMSwapCurveState | curvestates/cmswapcurvestate.hpp/.cpp | 257 | L1 |
| A.7 forwardforwardmappings | forwardforwardmappings.hpp/.cpp | 188 | L1 |
| A.8 swapforwardmappings | swapforwardmappings.hpp/.cpp | 312 | L1 |
| A.9 marketmodeldifferences | marketmodeldifferences.hpp/.cpp | 165 | L1 |
| A.10 LMMDriftCalculator | driftcomputation/lmmdriftcalculator.hpp/.cpp | 263 | L2 |
| A.11 LMMNormalDriftCalculator | driftcomputation/lmmnormaldriftcalculator.hpp/.cpp | 241 | L2 |
| A.12 SMMDriftCalculator | driftcomputation/smmdriftcalculator.hpp/.cpp | 197 | L2 |
| A.13 CMSMMDriftCalculator | driftcomputation/cmsmmdriftcalculator.hpp/.cpp | 208 | L2 |

### Track B — Correlations + Brownian + Accounting (~1 300 LOC C++)
| Work item | C++ files | LOC | Priority |
|---|---|---|---|
| B.1 PiecewiseConstantCorrelation (interface) | piecewiseconstantcorrelation.hpp | 54 | L1 |
| B.2 ExpCorrelations | correlations/expcorrelations.hpp/.cpp | 210 | L1 |
| B.3 TimeHomogeneousForwardCorrelation | correlations/timehomogeneousforwardcorrelation.hpp/.cpp | 140 | L1 |
| B.4 CotSwapFromFwdCorrelation | correlations/cotswapfromfwdcorrelation.hpp/.cpp | 128 | L1 |
| B.5 MarketModel (abstract) | marketmodel.hpp/.cpp | 143 | L1 |
| B.6 MarketModelDiscounter | discounter.hpp/.cpp | 100 | L1 |
| B.7 MTBrownianGenerator | browniangenerators/mtbrowniangenerator.hpp/.cpp | 143 | L1 |
| B.8 AccountingEngine (complete) | accountingengine.hpp/.cpp | 193 | L2 |

## 7. Key Design Decisions

- **P3H-1:** `BrownianGenerator.nextStep()` → fix to `nextStep(double[])` matching C++ `nextStep(std::vector<Real>&)`. The out-parameter carries the generated Gaussians; Java uses `double[]` pass-by-reference semantics.
- **P3H-2:** `MarketModelEvolver.numeraires()` returns `int[]`; accept current Java stub as-is (consistent with existing `int[]` patterns in JQuantLib).
- **P3H-3:** `SequenceStatisticsInc` in C++ → map to `GenericSequenceStatistics<IncrementalStatistics>` pattern in Java; use existing `GenericSequenceStatistics` (already has incremental `add` + `mean`).
- **P3H-4:** `std::valarray<bool>` in utilities → Java `boolean[]`; no library equivalent needed.
- **P3H-5:** `std::unique_ptr<CurveState> clone()` → Java `CurveState clone()` (no generics needed).
- **P3H-6:** SobolBrownianGenerator deferred to Phase 3i (needs end-to-end MC test with evolvers).
- **P3H-7:** PathwiseAccountingEngine, ProxyGreekEngine, historicalforwardratesanalysis deferred to Phase 3k/3l.
- **P3H-8:** Tolerance tier: drift calculator values → tight (1e-12 rel). AccountingEngine MC values → loose (1e-4 rel, Monte Carlo noise).
- **P3H-9:** Java package is `org.jquantlib.model.marketmodels` (matches existing 5 stubs); subpackages parallel C++ subdirectories: `.curvestates`, `.driftcomputation`, `.correlations`, `.browniangenerators`.

## 8. Pause Triggers

Carry-forward A1-A35.
Additional for Phase 3h:
- **P3H-A:** If `GenericSequenceStatistics` is missing key methods needed by `AccountingEngine.multiplePathValues`, pause and assess.
- **P3H-B:** If BrownianBridge.java has `nextStep` signature incompatible with LMM usage, pause.

## Outcome Forecast

| Metric | Phase 3g tip | Phase 3h target |
|---|---|---|
| Tests | 1027/0/0/38 | 1040+/0/0/38 (13+ new LMM foundation tests) |
| MarketModels Java surface | 5 stubs (empty) | 21 classes (complete, compilable) |
| Phase 3i readiness | Blocked | Evolvers can land immediately |

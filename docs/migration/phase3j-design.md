# Phase 3j Design — MarketModels Concrete Models + Calibration

**Status:** approved 2026-05-09 (autonomous mode — Phase 3j research; Phase 3i in parallel)
**Predecessor:** `jquantlib-phase3h-complete` (tests `1087/0/0/38`, Phase 3i evolvers in parallel flight)

## 1. Context

Phase 3h delivered the LMM foundation layer (~3,600 LOC C++: utilities, curve states, drift calculators,
correlations, MT Brownian generator, accounting engine). Phase 3i (evolvers + Sobol BG +
CovarianceDecomposition, ~4,500 LOC C++) is currently in flight in a parallel worktree.

Phase 3j covers the `models/` subdirectory of `ql/models/marketmodels/` — the concrete volatility
models (FlatVol, AbcdVol), their calibrators (CTSMM caplet family), and the adapter/facade layer that
bridges FwdModel ↔ CotSwapModel. Total C++ scope: **4,674 LOC** across 18 hpp/cpp pairs (plus one
aggregate all.hpp).

Phase 3j must land **after** Phase 3i because the evolvers depend on `FlatVol`/`AbcdVol` as their
primary model inputs, but Phase 3j's own models depend only on Phase 3h foundation classes — so
Track A (prereqs + core models) can begin from the Phase 3h tip without waiting for Phase 3i to
complete.

## 2. C++ Inventory (models/ subdirectory, 4,674 LOC total)

### 2.1 Full file listing

| File (hpp+cpp pair) | hpp LOC | cpp LOC | Total | Family |
|---|---|---|---|---|
| `piecewiseconstantvariance.hpp/.cpp` | 49 | 51 | 100 | L0 base |
| `alphaform.hpp` | 36 | — | 36 | AlphaForm interface |
| `alphaformconcrete.hpp/.cpp` | 54 | 54 | 108 | AlphaForm impls |
| `piecewiseconstantabcdvariance.hpp/.cpp` | 52 | 80 | 132 | ABCD variance |
| `flatvol.hpp/.cpp` | 131 | 206 | 337 | Core models |
| `abcdvol.hpp/.cpp` | 100 | 137 | 237 | Core models |
| `pseudorootfacade.hpp/.cpp` | 92 | 79 | 171 | Adapters |
| `fwdperiodadapter.hpp/.cpp` | 93 | 125 | 218 | Adapters |
| `cotswaptofwdadapter.hpp/.cpp` | 102 | 103 | 205 | Adapters |
| `fwdtocotswapadapter.hpp/.cpp` | 102 | 103 | 205 | Adapters |
| `volatilityinterpolationspecifier.hpp` | 58 | — | 58 | Calibration support |
| `volatilityinterpolationspecifierabcd.hpp/.cpp` | 77 | 196 | 273 | Calibration support |
| `ctsmmcapletcalibration.hpp/.cpp` | 194 | 204 | 398 | Calibration base |
| `capletcoterminalswaptioncalibration.hpp/.cpp` | 71 | 354 | 425 | Calibration impl |
| `capletcoterminalalphacalibration.hpp/.cpp` | 90 | 262 | 352 | Calibration impl |
| `capletcoterminalmaxhomogeneity.hpp/.cpp` | 69 | 423 | 492 | Calibration impl |
| `capletcoterminalperiodic.hpp/.cpp` | 66 | 180 | 246 | Calibration impl |
| `alphafinder.hpp/.cpp` | 96 | 563 | 659 | Calibration support |
| `all.hpp` | 22 | — | 22 | Aggregate include |
| **Total** | **1,554** | **3,120** | **4,674** | |

### 2.2 Family classification

**L0 — Prerequisite base (must land before anything else):**
- `PiecewiseConstantVariance` (abstract base, 100 LOC) — needed by CTSMMCapletCalibration,
  VolatilityInterpolationSpecifier, FlatVol/AbcdVol constructors indirectly

**Track A — Core models (simple MarketModel implementations):**
- `FlatVol` + `FlatVolFactory` (337 LOC) — depends on PiecewiseConstantCorrelation (Phase 3h),
  PseudoSqrt (Phase 2a), LinearInterpolation (Phase 2), ExponentialForwardCorrelation (Phase 3h)
- `AbcdVol` + `AbcdVolFactory` (237 LOC) — depends on Abcd termstructure volatility
- `FwdPeriodAdapter` + factory (218 LOC) — adapts coarser period models
- `CotSwapToFwdAdapter` + factory (205 LOC) — coterminal → forward
- `FwdToCotSwapAdapter` + factory (205 LOC) — forward → coterminal
- `PseudoRootFacade` (171 LOC) — wraps calibration result as MarketModel

**Track B — Calibration framework (CTSMM caplet family):**
- `AlphaForm` interface (36 LOC)
- `AlphaFormConcrete` — `AlphaFormLinearHyperbolic`, `AlphaFormInverseLinear` (108 LOC)
- `PiecewiseConstantAbcdVariance` (132 LOC)
- `VolatilityInterpolationSpecifier` interface (58 LOC)
- `VolatilityInterpolationSpecifierAbcd` (273 LOC)
- `CTSMMCapletCalibration` base class (398 LOC)
- `CTSMMCapletOriginalCalibration` (425 LOC)
- `CTSMMCapletAlphaFormCalibration` (352 LOC)
- `CTSMMCapletMaxHomogeneityCalibration` (492 LOC)
- `capletcoterminalperiodic` free function (246 LOC)
- `AlphaFinder` (659 LOC)

## 3. Java Current State

### 3.1 `org.jquantlib.model.marketmodels` (after Phase 3h)

Phase 3h delivered 23 classes across 5 packages. The `models/` subpackage does **not yet exist** in Java.

No Java files correspond to any Phase 3j class. Zero starting surface — all classes are new ports.

### 3.2 Prereq infrastructure state

| Component | Java state | Notes |
|---|---|---|
| `MarketModel` abstract base | Complete (Phase 3h B.5) | All 3j models extend this |
| `EvolutionDescription` | Complete (Phase 3h A.2) | Constructor arg for FlatVol/AbcdVol |
| `PiecewiseConstantCorrelation` | Complete interface (Phase 3h B.1) | Constructor arg for FlatVol/AbcdVol |
| `ExponentialForwardCorrelation` | Complete (Phase 3h B.2) | Used in FlatVol/FlatVolFactory |
| `SwapForwardMappings` | Partial (Phase 3h A.8; swaptionImpliedVolatility deferred) | Needed by calibration impls |
| `CurveState` abstract base | Complete (Phase 3h A.3) | Constructor arg for CTSMMCapletCalibration |
| `PseudoSqrt` | Complete (Phase 2a) | Used in FlatVol.cpp, AbcdVol.cpp |
| `BasisIncompleteOrdered` | Complete Java class exists | Used in MaxHomogeneity calibration |
| `SphereCylinderOptimizer` | Java class exists (`SphereCylinderOptimizer`) | Used in MaxHomogeneity calibration |
| `Abcd` termstructure volatility | **Missing** — not ported | Used in AbcdVol.cpp |
| `Quadratic` | **Missing** — small 48-LOC class needed by AlphaFinder | New class required |
| `CotSwapFromFwdCorrelation` | Deferred from Phase 3h B.4 (CovarianceDecomposition needed) | Needed by periodic calibration |
| Evolvers | Phase 3i in parallel flight | Not a 3j dependency |

### 3.3 Key gap: CotSwapFromFwdCorrelation (Phase 3h B.4 deferral)

Phase 3h deferred `CotSwapFromFwdCorrelation` because it needs `CovarianceDecomposition` (Phase 3i
scope). The periodic calibration function (`capletcoterminalperiodic`) calls it indirectly through the
`CTSMMCapletMaxHomogeneityCalibration`. This is only needed for the periodic calibration track —
Tracks A and B.1-B.4 are not blocked. The `capletcoterminalperiodic` function (Track B.5) should be
deferred to Phase 3j.5 if Phase 3i CovarianceDecomposition has not landed.

## 4. Dependency Graph

```
L0:  PiecewiseConstantVariance            (standalone — no models/ deps)

Track A (can run in parallel with B after L0):
  A.1  FlatVol + FlatVolFactory           (depends: L0, PiecewiseConstantCorrelation[3h],
                                           PseudoSqrt[2a], LinearInterpolation)
  A.2  AbcdVol                            (depends: L0, PiecewiseConstantCorrelation[3h],
                                           Abcd termstructure [NEW prereq])
  A.3  FwdPeriodAdapter                   (depends: MarketModel[3h])
  A.4  CotSwapToFwdAdapter                (depends: MarketModel[3h], SwapForwardMappings[3h])
  A.5  FwdToCotSwapAdapter                (depends: MarketModel[3h], SwapForwardMappings[3h])
  A.6  PseudoRootFacade                   (depends: MarketModel[3h], CTSMMCapletCalibration[B.3])

Track B (calibration chain):
  B.1  AlphaForm + AlphaFormConcrete      (standalone — only ql/types.hpp)
  B.2  PiecewiseConstantAbcdVariance      (depends: L0)
  B.3  CTSMMCapletCalibration (base)      (depends: L0, CurveState[3h], Evolution[3h],
                                           PiecewiseConstantCorrelation[3h])
  B.4  CTSMMCapletOriginalCalibration     (depends: B.3, SwapForwardMappings[3h], PseudoSqrt[2a])
  B.5  CTSMMCapletAlphaFormCalibration    (depends: B.3, B.1, AlphaFinder[B.7],
                                           SwapForwardMappings[3h])
  B.6  CTSMMCapletMaxHomogeneityCalibration (depends: B.3, SwapForwardMappings[3h],
                                             SphereCylinderOptimizer, BasisIncompleteOrdered)
  B.7  AlphaFinder                        (depends: B.1, Quadratic[NEW])
  B.8  VolatilityInterpolationSpecifier   (depends: L0 PiecewiseConstantVariance)
  B.9  VolatilityInterpolationSpecifierAbcd (depends: B.8, B.2)
  B.10 capletcoterminalperiodic free fn   (depends: B.3, B.6, CotSwapFromFwdCorrelation[3i],
                                           multiple adapters from Track A)
       → DEFER to Phase 3j.5 if 3i CotSwapFromFwdCorrelation not yet landed

Prereq new class (no models/ parent):
  P.1  Abcd termstructure                 (ql/termstructures/volatility/abcd.hpp/.cpp, 216 LOC)
       → lands before Track A A.2 (AbcdVol) — fits in Track A's L0
  P.2  Quadratic                          (ql/math/quadratic.hpp, 48 LOC)
       → lands before B.7 AlphaFinder — fits as B.7 prereq
```

### 4.1 Phase 3h.5 carry-forwards that Phase 3j resolves

From Phase 3h completion doc:
- **A.7 `ForwardForwardMappings` partial** — the partial port was committed at `40d83d9`.
  Phase 3j PiecewiseConstantVariance resolves the last dependency for swaptionImpliedVolatility stub
  in SwapForwardMappings (Phase 3h A.8 remainder). This can land as Phase 3j L0.5.
- **`CotSwapFromFwdCorrelation`** — blocked on CovarianceDecomposition from Phase 3i; lands with 3i.

## 5. Test-Suite Scope

The C++ test suite for Phase 3j is spread across:

| File | LOC | Relevant test cases |
|---|---|---|
| `test-suite/marketmodel.cpp` | 4,662 | `testAbcdVolatilityIntegration`, `testAbcdVolatilityCompare`, `testAbcdVolatilityFit`, `testCovariance`, `testPeriodAdapter` (uses FlatVol/AbcdVol/adapters), `testOneStepForwardsAndOptionlets` (model setup), `testAllMultiStepProducts` |
| `test-suite/marketmodel_smmcapletcalibration.cpp` | 337 | `testFunction` (CTSMMCapletOriginalCalibration) |
| `test-suite/marketmodel_smmcapletalphacalibration.cpp` | 346 | `testFunction` (CTSMMCapletAlphaFormCalibration) |
| `test-suite/marketmodel_smmcaplethomocalibration.cpp` | 608 | `testFunction`, `testPeriodFunction`, `testSphereCylinder` |
| **Total calibration test LOC** | **1,291** | |

The calibration test files exercise the complete CTSMM family end-to-end. Java tests will focus on:
1. `FlatVol` + `AbcdVol` construction and `pseudoRoot()` output (tight 1e-12)
2. `FwdPeriodAdapter` / `CotSwapToFwdAdapter` / `FwdToCotSwapAdapter` structural correctness
3. `CTSMMCapletOriginalCalibration.calibrate()` — convergence check vs C++ reference values (loose 1e-4)
4. `CTSMMCapletAlphaFormCalibration.calibrate()` — alpha parameter outputs (loose 1e-4)
5. `CTSMMCapletMaxHomogeneityCalibration.calibrate()` — deformation size (loose 1e-4)
6. `AlphaFinder.solve()` — alpha/a/b outputs for known input (tight 1e-10 — deterministic solve)
7. `PiecewiseConstantAbcdVariance` — variances[] against C++ probe (tight 1e-12)

**Tolerance rationale:** Calibration routines involve iterative solvers (bisection, sphere-cylinder);
loose tier (1e-4 rel) is appropriate. Algebraic model outputs (pseudoRoots, ABCD variances) use tight
tier (1e-12 rel).

## 6. Phase Decomposition

### Phase 3j (~4,674 LOC C++) — Three tracks

The 4,674 LOC splits cleanly into three tracks of 1,400–1,800 LOC each, with a small L0.

**L0 — Prereqs (~300 LOC C++)**
- `PiecewiseConstantVariance` (100 LOC) — abstract base needed by all three tracks
- `Abcd` termstructure port (216 LOC, new class `org.jquantlib.termstructures.volatility.AbcdVolatility`)
- `Quadratic` small class (48 LOC, new class `org.jquantlib.math.Quadratic`)
- SwapForwardMappings `swaptionImpliedVolatility` remainder (Phase 3h A.8 tail)
- Java package: `org.jquantlib.model.marketmodels.models`

**Track A — Core Models + Adapters (~1,436 LOC C++)**
Independent of calibration. Can run in parallel with Track B after L0.
- A.1 `FlatVol` + `FlatVolFactory` (337 LOC)
- A.2 `AbcdVol` (237 LOC)
- A.3 `FwdPeriodAdapter` + factory (218 LOC)
- A.4 `CotSwapToFwdAdapter` + factory (205 LOC)
- A.5 `FwdToCotSwapAdapter` + factory (205 LOC)
- A.6 `PseudoRootFacade` (171 LOC) — after B.3 lands

**Track B — Calibration Framework (~2,938 LOC C++)**
Sequential calibration dependency chain; some B items can overlap with Track A.
- B.1 `AlphaForm` interface + `AlphaFormLinearHyperbolic` + `AlphaFormInverseLinear` (144 LOC)
- B.2 `PiecewiseConstantAbcdVariance` (132 LOC)
- B.3 `CTSMMCapletCalibration` abstract base (398 LOC) — L1 gating
- B.4 `CTSMMCapletOriginalCalibration` (425 LOC) — parallel with B.5
- B.5 `CTSMMCapletAlphaFormCalibration` (352 LOC) — parallel with B.4 after B.7
- B.6 `CTSMMCapletMaxHomogeneityCalibration` (492 LOC) — parallel with B.4/B.5
- B.7 `AlphaFinder` (659 LOC) — needed by B.5
- B.8 `VolatilityInterpolationSpecifier` interface (58 LOC)
- B.9 `VolatilityInterpolationSpecifierAbcd` (273 LOC) — depends B.8, B.2
- B.10 `capletSwaptionPeriodicCalibration` free function (246 LOC) — **defer to Phase 3j.5** if
  `CotSwapFromFwdCorrelation` (Phase 3i) has not landed

### Phase 3j.5 (conditional) — Periodic calibration + Phase 3i integration
- B.10 `capletSwaptionPeriodicCalibration` + `VolatilityInterpolationSpecifierAbcd`
  `setLastCapletVol`/`setScalingFactors` integration with period adapters
- Lands after Phase 3i CotSwapFromFwdCorrelation is confirmed complete

## 7. Key Design Decisions

- **P3J-1: Java package name** — `org.jquantlib.model.marketmodels.models` parallel to C++
  `ql/models/marketmodels/models/`. All new classes land here unless they are cross-cutting
  prereqs (Abcd termstructure → `org.jquantlib.termstructures.volatility`; Quadratic →
  `org.jquantlib.math`).

- **P3J-2: AbcdVol depends on Abcd termstructure** — `ql/termstructures/volatility/abcd.hpp`
  provides `AbcdFunction` (not yet ported). This must land as an L0 prereq in Phase 3j.
  The Java class name will be `AbcdFunction` per C++ (note: distinct from `Abcd` calibration
  surface in `ql/termstructures/volatility/abcdcalibration.*` — that is later).

- **P3J-3: Quadratic class** — `ql/math/quadratic.hpp` (48 LOC) needed by `AlphaFinder`.
  Simple algebraic utility; exact tolerance. Java package: `org.jquantlib.math`.

- **P3J-4: FlatVolFactory Observer** — C++ `FlatVolFactory` extends both `MarketModelFactory` and
  `Observer`. Java: implement both interfaces; `update()` recomputes interpolated volatility via
  `LinearInterpolation`. The `Handle<YieldTermStructure>` becomes `Handle<YieldTermStructure>` via
  existing Java `Handle` infrastructure.

- **P3J-5: CotSwapToFwdAdapter computation** — the constructor does a linear-algebra transformation
  (coterminal pseudo-roots → forward pseudo-roots) using `SwapForwardMappings.coterminalToCap()`.
  SwapForwardMappings is partially complete in Java; verify the specific methods are present before
  Track A A.4.

- **P3J-6: PseudoRootFacade two constructors** — one takes `CTSMMCapletCalibration` (cross-track
  dependency: A.6 must land after B.3); one takes raw `Matrix[]` vectors (can land early). Split:
  port the raw-matrix constructor in Track A, add the calibration constructor in Track B close-out.

- **P3J-7: ext::shared_ptr → direct references** — as established in Phase 3h, Java uses direct
  object references where C++ uses `shared_ptr`. For factory patterns, store direct reference.

- **P3J-8: SphereCylinderOptimizer** — C++ `spherecylinder.cpp` optimization; Java class exists as
  `SphereCylinderOptimizer`. Verify method signatures match before Track B B.6.

- **P3J-9: capletcoterminalperiodic deferral trigger** — if `CotSwapFromFwdCorrelation` has not
  landed (Phase 3i still in flight) at the time B.10 is scheduled, move B.10 to Phase 3j.5. Do not
  block the rest of Phase 3j for it.

- **P3J-10: Tolerance tiers**
  - `PiecewiseConstantAbcdVariance` variances: tight (1e-12 rel)
  - `FlatVol`/`AbcdVol` pseudo-root entries: tight (1e-12 rel)
  - Adapter structural tests (numberOfRates/Factors/Steps): exact
  - Calibration solver convergence (CTSMMCaplet* output vols): loose (1e-4 rel)
  - `AlphaFinder.solve()`: tight (1e-10 rel — deterministic bisection, no MC noise)

- **P3J-11: Swaptionimpliedvolatility tail** — `SwapForwardMappings.swaptionImpliedVolatility`
  stub (Phase 3h A.8 deferral) needs `PiecewiseConstantVariance`. After L0, complete this method
  as Phase 3j L0.5 before Track A A.1 starts.

- **P3J-12: MarketModel abstract base** — already complete from Phase 3h B.5. All Track A models
  simply extend it and implement `pseudoRoot(int i)` returning from a pre-computed `List<Matrix>`.

## 8. Pause Triggers

Carry-forward A1-A35 (existing system triggers).
Additional for Phase 3j:
- **P3J-A:** If `SwapForwardMappings.swaptionImpliedVolatility` body depends on classes not yet
  ported (beyond PiecewiseConstantVariance), pause and assess scope of prerequisite chain.
- **P3J-B:** If `SphereCylinderOptimizer` Java signature diverges materially from C++ `spherecylinder.hpp`
  interface, pause — MaxHomogeneity calibration depends on it.
- **P3J-C:** If `BasisIncompleteOrdered` Java is missing methods called by MaxHomogeneity, pause.
- **P3J-D:** If `Abcd` termstructure port reveals dependencies on `ql/math/optimization` calibration
  machinery (AbcdCalibration → Levenberg-Marquardt etc.), scope exceeds L0; pause and assess.

## 9. Outcome Forecast

| Metric | Phase 3h tip | Phase 3j target |
|---|---|---|
| Tests | 1087/0/0/38 | 1115+/0/0/38 (~28 new model + calibration tests) |
| MarketModels Java surface | 23 classes (foundation only) | ~46 classes (+23 models/ classes) |
| Phase 3k readiness | Blocked (no models for products to reference) | Products (3k) can land immediately |
| New Java packages | 5 | 6 (`+ .models`) |

## 10. Phase 3i Interaction

Phase 3j is designed so that **Track A and Track B L0-B.6 are fully independent of Phase 3i**.
The only 3i dependency is `CotSwapFromFwdCorrelation` needed by B.10 `capletcoterminalperiodic`,
which is explicitly deferred. If Phase 3i is complete before Phase 3j reaches B.10, proceed; otherwise
defer B.10 to Phase 3j.5. Evolvers (Phase 3i) consume FlatVol/AbcdVol as inputs — confirming the
correct dependency direction: 3j models must exist before full 3i end-to-end tests can run, but 3j
implementation does not depend on 3i classes.

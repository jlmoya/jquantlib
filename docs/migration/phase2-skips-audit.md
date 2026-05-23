# Phase 2 forward closure — SKIPs audit

**Date:** 2026-05-23
**Tag:** `jquantlib-migration-complete` @ `4549eb7f`
**Suite:** 3531 / 0 / 0 / 24 BUILD SUCCESS

This document audits **every SKIP-with-rationale** decision made during Phase 2
L1-L5 execution. The user raised the concern: *"I thought nothing would be
skipped."* This audit categorizes each SKIP so you can decide which to re-port.

## TL;DR

| Category | Count | Description | Recommendation |
|---|---|---|---|
| A | ~70 | Already present in Java under different name/structure | Keep SKIP — false-positive from audit script |
| B | ~20 | Commented-out / deprecated-empty in C++ v1.42.1 itself | Keep SKIP — porting commented-out code is wrong |
| C | ~10 | No Java caller AND no C++ caller (test-suite or example) | Keep SKIP unless future need |
| D | **~12** | **Genuine gaps that would need real implementation work** | **User decision needed** |
| E | 1 | SKELETON (throws UnsupportedOperationException pending dependency) | User decision needed |
| F | 2 | Design deviations (not SKIPs but worth noting) | Keep design — Java-language limitation |

**The user's review should focus on Category D + E (~13 items).** A/B/C are
defensible and safe to leave as-is per ground-truth principle.

---

## Category A — Already present in Java (false-positive SKIPs)

The audit script enumerates C++ class declarations and Java file names. When a
class is implemented in Java as a nested/inner class, or under a different
top-level name, the script flags it as "missing" but it isn't.

### L1-A — Rounding subclasses (5)
| C++ class | Java equivalent |
|---|---|
| `CeilingTruncation` | `org.jquantlib.math.Rounding.CeilingTruncation` (inner) |
| `ClosestRounding` | `org.jquantlib.math.Rounding.ClosestRounding` (inner) |
| `DownRounding` | `org.jquantlib.math.Rounding.DownRounding` (inner) |
| `FloorTruncation` | `org.jquantlib.math.Rounding.FloorTruncation` (inner) |
| `UpRounding` | `org.jquantlib.math.Rounding.UpRounding` (inner) |

`RoundingTest` already 5/5 green, verified by L1-A spec reviewer.

### L1-D — Trapezoid integrator tag types (2)
| C++ class | Java equivalent |
|---|---|
| `MidPoint` | `TrapezoidIntegral.MidPoint` (inner) |
| `Default` | `TrapezoidIntegral.Default` (inner) |

### L1-E — Cubic spline already-accessible variants
| C++ class | Java equivalent |
|---|---|
| `CubicNaturalSpline` | `NaturalCubicInterpolation` |
| `MonotonicCubicNaturalSpline` | `MonotonicNaturalCubicInterpolation` |
| `FritschButlandCubic` | `FritschButlandCubic` (exists) |
| `Bicubic` | `BicubicSpline` factory + `BicubicSplineInterpolation` |
| `UpdatedYInterpolation` | `LagrangeInterpolation.value(double[], double)` provides equivalent |
| `Zabr` (interpolator) | `org.jquantlib.experimental.volatility.ZabrInterpolation` |
| 6 mixed-linear-cubic variants | `MixedLinearCubicInterpolation` public static inner classes |

### L1-E — math root utilities
| C++ class | Java equivalent |
|---|---|
| `LinearFct` / `LinearFcts` | Covered by `LinearLeastSquaresRegression` machinery |
| `Solver1D` | `AbstractSolver1D` exists |
| `earlier_than` | `EarlierThanCashFlowComparator` + `java.util.Comparator<Date>` |
| `Foo` | C++ doc-only example (Singleton<Foo>) — not a real class |

### L2-B — SpreadTraits
SKIP rationale: already-present as `SpreadBootstrapTraits.java` from Phase 1.4 closure.

### L2-D — Cdi
SKIP rationale: already-present as `org.jquantlib.indexes.ibor.Brlcdi` with the C++ Cdi forecastFixing 252-bday compounding override. Same class, different name.

### L3-A — payoff/option helpers (5)
| C++ class | Java equivalent |
|---|---|
| `Average` | `org.jquantlib.instruments.AverageType` enum |
| `Barrier` | `org.jquantlib.instruments.BarrierType` enum |
| `DoubleBarrier` | `org.jquantlib.experimental.barrieroption.DoubleBarrierType` enum |
| `ForwardOptionArguments` | `ForwardVanillaOption.ArgumentsImpl` (inner) |
| `QuantoOptionResults` | `QuantoVanillaOption.ResultsImpl` (inner) |

### L5-A — RandomLM / simEvent (3)
| C++ class | Java equivalent |
|---|---|
| `RandomLM` | `RandomDefaultLM` (Java collapses CRTP into single concrete class) |
| `simEvent` / `simEvent2` | `DefaultSimEvent` |

### L5-B — already-nested or inlined (6)
| C++ class | Java equivalent |
|---|---|
| `Integrand` (continuous) | `AnalyticContinuousGeometricAveragePriceAsianHestonEngine.Integrand` (private inner) |
| `DcfIntegrand` | Same file (private static) |
| `Integrand` (discrete) | `AnalyticDiscreteGeometricAveragePriceAsianHestonEngine.Integrand` (private inner) |
| `FdmZabrUnderlyingPart` | `FdmZabrOp.FdmZabrUnderlyingPart` (static final nested) |
| `FdmZabrVolatilityPart` | Same file |
| `FdmSimple3dExtOUJumpSolver` | Inlined into `FdSimpleExtOUJumpSwingEngine` |

### L5-C — already-present in mainline Java (8 actually used)
All 22 C++ headers in `ql/experimental/exoticoptions/` were either ported in mainline locations or are v1.42.1-deprecated. See `docs/migration/phase2-L5-C-audit.md`.

### L5-D — split/renamed in Java (~25)
Many `experimental/*` headers were split into multiple per-class Java files. Examples:
- `experimental/basismodels/swaptioncfs` → `SwaptionCashFlows.java`
- `experimental/commodities/petroleumunitsofmeasure` → split per-unit Java files
- `experimental/lattices/extendedbinomialtree` → split per-tree (10 files)
- `experimental/math/{convolvedstudentt,gaussiancopulapolicy,...}` → split per class
- `experimental/termstructures/{basisswapratehelpers,crosscurrencyratehelpers}` → split per helper
- `experimental/callablebonds/{black,tree}callablebondengine` → split into FixedRate + ZeroCoupon variants

**Total Category A: ~70 entries.**

---

## Category B — C++ v1.42.1 deprecated / commented-out

Porting code that C++ has commented out or deprecated would introduce dead code into Java. These are correctly NOT ported.

### L3-A (3): commented-out in `stickyratchet.hpp` (lines 168-227 inside `/* ... */`)
- `RatchetPayoff_2`
- `StickyPayoff_2`
- `StickyRatchetPayoff`

### L5-A (1): ESFIntegrator
C++ `saddlepointlossmodel.hpp:356` carries the comment `"Just for testing the ESF direct integration, not for release, this is very inefficient"`.

### L5-C (14): v1.42.1-deprecated headers
14 of 22 C++ `experimental/exoticoptions/*.hpp` headers carry `#pragma message("Warning: this file is empty and will disappear in a future release")`. The classes moved to mainline (`pricingengines/barrier/`, `pricingengines/vanilla/`, etc.) — which Java already mirrors.

### L5-D (~10): deprecated empty headers
- `experimental/averageois/{arithmeticaverageois, arithmeticoisratehelper, makearithmeticaverageois}`
- `experimental/risk/{creditriskplus, sensitivityanalysis}`
- `experimental/fx/blackdeltacalculator` (moved to `ql/pricingengines/`)
- `experimental/volatility/{zabr, zabrinterpolatedsmilesection}` (moved to `ql/termstructures/volatility/`)

**Total Category B: ~28 entries.**

---

## Category C — No Java caller AND no C++ caller anywhere

These classes have neither a Java caller (no consumer in `jquantlib/src`) nor a C++ test/example caller. Porting them creates dead code with no validation path.

### L5-A (2)
- `AffineHazardRate` — Traits struct in `interpolatedaffinehazardratecurve.hpp:152`, used internally by `InterpolatedAffineHazardRateCurve` (Java) but the bootstrap-helper machinery `PiecewiseDefaultCurve<AffineHazardRate, ...>` isn't present in Java. **No C++ test caller either.**
- `BaseCorrelationLossModel` — 2D correl-surface + attach/detach sub-baskets + template specializations on `GaussianLHPLossModel` / `BilinearInterpolation`. **No C++ test caller or example.**

### L5-B (3)
- `VannaVolga` — C++ template-traits factory; only `VannaVolgaInterpolation` is consumed in Java
- `FdmVPPStepConditionParams` / `FdmVPPStepConditionMesher` — 0 callers anywhere

**Total Category C: ~5 entries.**

---

## Category D — Genuine gaps (USER DECISION NEEDED)

These would need real Java implementation work. None are blockers for the
current suite (3531/0/0/24 BUILD SUCCESS) but you wanted "nothing skipped",
so these are the items to decide on.

### D1. L1-E — CubicInterpolation derivative-algorithm extensions (5)
The Java `CubicInterpolation.DerivativeApprox` enum has `Spline / Parabolic / FritschButland / Akima / Kruger`. C++ has additional values that would need both:
- enum extension
- full numerical algorithm implementation

| Missing | Where used in C++ |
|---|---|
| `HarmonicCubic` | `cubicinterpolation.hpp` |
| `HarmonicLogCubic` | `loginterpolation.hpp` (depends on Harmonic enum) |
| `CubicSplineOvershootingMinimization1` | `cubicinterpolation.hpp` |
| `CubicSplineOvershootingMinimization2` | `cubicinterpolation.hpp` |

**Estimated effort:** ~300-500 LOC per algorithm = ~1500-2000 LOC total + tests.

### D2. L1-E — Abcd + AbcdInterpolation
`AbcdMathFunction` was ported (math-root utility). The `Abcd`/`AbcdInterpolation` interpolator wrapper class was deferred — needs `OptimizationMethod` + `EndCriteria` plumbing.

**Status:** Java has `AbcdCalibration` already; the missing piece is the
`Interpolation`-interface wrapper. Not currently referenced by any Java
caller, but C++ uses it for cap volatility fitting.

**Estimated effort:** ~200 LOC + tests.

### D3. L5-D — experimental/volatility large volatility-cube infrastructure (5)
Independent volatility-cube classes interlinked, no current Java callers:
- `AbcdAtmVolCurve`
- `ExtendedBlackVarianceSurface`
- `NoArbSabrSwaptionVolatilityCube`
- `SabrVolSurface`
- `VolCube`

**Estimated effort:** ~2000 LOC total. Useful for advanced vol surface modeling.

### D4. L5-D — experimental/math isolated optimization/integration trees (4)
- `HybridSimulatedAnnealing` — combines SA with local search
- `LatentModel` — generic latent-variable model
- `MultidimIntegrator` / `MultidimQuadrature` — N-dim integration framework

**Estimated effort:** ~1500-2000 LOC total. Math utility expansion.

**Total Category D: ~12 entries, ~5000-7000 LOC of additional work.**

---

## Category E — SKELETON (1)

### E1. L3-D — FdBlackScholesAsianEngine
SKELETON state: `calculate()` throws `UnsupportedOperationException`. Class
exists but is unusable. Pending dependency: `FdmArithmeticAverageCondition`
port. Full implementation requires:
- Port `FdmArithmeticAverageCondition` (~200 LOC)
- Implement `FdBlackScholesAsianEngine.calculate()` per C++ (~150 LOC)
- Add cross-validation test

**Estimated effort:** ~400 LOC + tests.

---

## Category F — Design deviations (not SKIPs, but worth noting)

### F1. L2-A — OvernightIndexedSwapIndex.underlyingSwap
Java cannot overload methods by return type. C++:
```cpp
shared_ptr<VanillaSwap> SwapIndex::underlyingSwap(Date) const;
shared_ptr<OvernightIndexedSwap> OvernightIndexedSwapIndex::underlyingSwap(Date) const;  // shadows
```
Java: `OvernightIndexedSwapIndex.underlyingSwap(Date)` throws `UnsupportedOperationException`; OIS variant accessed via new method `underlyingOvernightIndexedSwap(Date)`.

**No existing Java caller breaks** — verified during L2-A review.

### F2. L3-C — BlackStyleSwaptionEngine API omissions
- `swap.valuationDate` not used (consistent with existing `BlackSwaptionEngine` port — Java DiscountingSwapEngine uses curve refDate)
- `accrualStartDate >= exerciseDate` guard omitted (consistent with existing port)

---

## Per-cluster SKIP tally

| Cluster | A | B | C | D | E | Notes |
|---|---|---|---|---|---|---|
| L1-A | 5 | 0 | 0 | 0 | 0 | Rounding inners |
| L1-B | 0 | 0 | 0 | 0 | 0 | All 13 copulas DONE |
| L1-C | 2 | 0 | 0 | 0 | 0 | DifferentialEvolution.Candidate/Configuration already nested |
| L1-D | 2 | 0 | 0 | 0 | 0 | MidPoint/Default inners |
| L1-E | 13 | 0 | 0 | 7 | 0 | Inc. CubicNaturalSpline etc., Abcd+5 derivatives |
| L2-A | 0 | 0 | 0 | 0 | 0 | All ported; F1 deviation only |
| L2-B | 1 | 0 | 0 | 0 | 0 | SpreadTraits |
| L2-C | 0 | 0 | 0 | 0 | 0 | All ported |
| L2-D | 1 | 0 | 0 | 0 | 0 | Cdi=Brlcdi |
| L3-A | 5 | 3 | 0 | 0 | 0 | enums/inners + commented-out |
| L3-B | 0 | 0 | 0 | 0 | 0 | All ported |
| L3-C | 0 | 0 | 0 | 0 | 0 | All ported; F2 deviation only |
| L3-D | 0 | 0 | 0 | 0 | 1 | FdBlackScholesAsianEngine SKELETON |
| L4-A | 0 | 0 | 0 | 0 | 0 | All ported |
| L4-B+C | 0 | 0 | 0 | 0 | 0 | All ported |
| L5-A | 3 | 1 | 2 | 0 | 0 | Subsumed + ESFIntegrator + AffineHazardRate/BaseCorrelationLossModel |
| L5-B | 6 | 0 | 3 | 0 | 0 | Already-nested + VannaVolga/FdmVPP* |
| L5-C | 8 | 14 | 0 | 0 | 0 | Already-mainline + deprecated |
| L5-D | 25 | 10 | 0 | 5 | 0 | Split-renamed + deprecated + vol-cube infrastructure |
| **TOTAL** | **~70** | **~28** | **~5** | **~12** | **1** | |

---

## Recommendation

**Categories A, B, C (~103 entries): leave as-is.** All are defensible per
ground-truth principle (don't port what isn't needed, don't port
commented-out / deprecated code, don't port classes with no callers).

**Category D (~12 entries, ~5000-7000 LOC): decide which to port.** Suggested priority order:

1. **D1 CubicInterpolation derivatives (HighlyAccurate)** — most invasive but
   most impactful (extends the core spline framework). Recommended.
2. **D2 AbcdInterpolation** — small, ports cleanly given AbcdCalibration
   already exists. Recommended.
3. **D3 vol-cube infrastructure** — useful for advanced modeling but no
   current consumer. Defer unless you have a target.
4. **D4 math isolated trees** — math infrastructure expansion; defer unless
   targeted.

**Category E (1 entry, ~400 LOC): port FdBlackScholesAsianEngine** — it's
currently a hard-throwing stub; you'd want it working or removed.

If you want all 13 (D + E) ported, that's ~5500-7500 LOC of additional work,
roughly 4-6 cluster-dispatches at the current pace.

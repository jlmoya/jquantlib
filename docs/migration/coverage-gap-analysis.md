# Coverage gap analysis — full C++ surface vs Java

**Date:** 2026-05-29
**Audit tool:** `migration-harness/check_coverage.py` (reproducible; run from repo root)
**C++ denominator:** submodule `ql/**/*.hpp` at v1.42.1 @ `099987f0` (excl. `all.hpp` aggregators)
**Trigger:** user correction — `jquantlib-final` was tagged with `experimental` and other
C++ classes never ported. "100% of the C++ project" means *every* class in `ql/`, with no
self-defined carve-outs. This document replaces estimate-based "done" claims with a
mechanical, name-keyed diff.

## Headline

| Metric | Value |
|---|---|
| C++ headers scanned | 1320 |
| C++ distinct class/struct names | 2024 |
| Found in Java (by class name) | 1847 |
| **Not found in Java** | **177** |
| Name-coverage | 91.3% |

The match key is the **class name** (QuantLib→JQuantLib preserves PascalCase class names;
only packages differ). The audit OVER-reports (counts nested helper structs, template-tag
types, `detail::` helpers, forward-decls) and UNDER-reports (misses Java renames). So the
177 needs triage — but it is reproducible and it is the denominator we drive to zero.

## Triage of the 177

### Bucket A — verified present under a different name / form (NOT missing work)

Each verified by grep against the Java tree (evidence in the audit session):

| C++ name(s) | Java reality |
|---|---|
| `BFGS` | `math.optimization.Bfgs` (case) |
| `SABR` | `Sabr` (case) |
| `Solver1D` | `math.AbstractSolver1D` (rename) |
| `TARGET` | `time.calendars.Target` (case) |
| `Bicubic`, `BicubicSplineDerivatives` | `BicubicSplineInterpolation` |
| `BackwardflatLinear` | `math.interpolations.BackwardflatLinearInterpolation` |
| `Cdi` | `indexes.ibor.Brlcdi` (rename) |
| `Error` | `lang.exceptions.LibraryException` (rename) |
| `Average`, `Barrier`, `DoubleBarrier` | `AverageType` / `BarrierType` / `DoubleBarrierType` enums |
| `ForwardOptionArguments`, `QuantoOptionResults` | inner `Arguments`/`Results` of the option classes |
| `Thirty360_Impl` | inner impl of `Thirty360` |
| `LinearFct`, `LinearFcts` | folded into `LinearLeastSquaresRegression` |
| `RatchetPayoff_2`, `StickyPayoff_2`, `StickyRatchetPayoff` | commented-out in C++ v1.42.1 (verified in Phase 3-A) — N/A |
| `patterns/*` (`AcyclicVisitor`, `CuriouslyRecurringTemplate`, `Foo`, `Proxy`, `Singleton`, `UpdateChecker`) | C++ template idioms (CRTP/visitor/singleton); Java realizes the capability without a 1:1 class |
| `utilities/*` (`Clone`, `Null`, `Tracing`) | C++ template utilities; Java equivalents are language-level |
| `AbcdCoeffHolder`, `CubicInterpolationBaseImpl`, `EmptyArg/Dim/Res`, `SABRWrapper` | template impl/tag helpers, no runtime analog |

**Bucket A ≈ 45-50 entries.** Each will be added to a reviewed allowlist in the audit script
with a one-line rationale, so the script can reach a *defensible* zero.

### Bucket B — GENUINE MISSING (real porting work)

This is the answer to "what else are we missing." Roughly **~125-130 classes**, by category:

| Category | Count | Examples |
|---|---|---|
| **Currencies** | **42** | AED, EGP, KES, NGN, MAD, OMR, QAR, MYR, IDR, PHP, VND, UAH, GEL, KZT, LKR, HRK, RSD, TND, UGX, UYU, XOF, BWP, AOA, ETB, GHS, MUR + crypto (BTC, ETH, BCH, LTC, XRP, ZEC, DASH, ETC) + CNH/CLF/COU/MXV. **Confirmed: Java has 69 `*Currency` classes, C++ has 109.** |
| **experimental/credit** | ~12 | `CDO`, `SyntheticCDO`, `MidPointCDOEngine`, `IntegralCDOEngine`, `BaseCorrelationLossModel`, `RandomDefaultModel`, `GaussianRandomDefaultModel`, `RandomLM`, `AffineHazardRate` |
| **experimental/volatility** | ~5 | `NoArbSabr`, `SwaptionVolCubeNoArbSabrModel`, `VannaVolga` |
| **experimental/math (PSO)** | ~8 | particle-swarm: `AdaptiveInertia`, `LevyFlightInertia`, `SimpleRandomInertia`, `DecreasingGaussianWalk`, `DistributionRandomWalk`, `ClubsTopology`, `KNeighbors`; `Ziggurat` RNG |
| **experimental/inflation** | ~3 | `PiecewiseYoYOptionletVolatilityCurve`, `YoYInflationVolatilityTraits` |
| **experimental/fdm (VPP/energy)** | ~3 | `FdmSimple3dExtOUJumpSolver`, `FdmVPPStepConditionMesher`, `FdmVPPStepConditionParams` |
| **experimental/mcbasket** | ~2 | `MakeMCAmericanPathEngine`, `MakeMCPathBasketEngine` |
| **experimental/cashflows** | ~2 | `SwapCashFlows`, `IborLegCashFlows` |
| **volatility termstructures** | ~14 | `CPIVolatilitySurface`, `ConstantCPIVolatility`, `ConstantCapFloorTermVolatility`, `CmsMarket`, `CmsMarketCalibration`, `GridModelLocalVolSurface`, `SwaptionVolCubeSabrModel`, `SwaptionVolCubeZabrModel`, `XabrSwaptionVolatilityCube`, `XabrModelTraits`, `ZeroInflationTraits`, `RelativeDateBootstrapHelper`, `InterpolatedCurve`, `SingleStepCalibrationResult` |
| **cashflows (digital)** | ~10 | `DigitalCmsCoupon`/`Leg`, `DigitalIborCoupon`/`Leg`, `AmortizingPayment`, `TimeBasket`, `IrrFinder`, `PriceHelper`, `VegaRatioHelper`, `BlackCompoundingOvernightIndexedCouponPricer` |
| **GJR-GARCH (model+process+engine)** | 3 | `GJRGARCHModel`, `GJRGARCHProcess`, `MCEuropeanGJRGARCHEngine` |
| **methods/FDM** | ~7 | `FdmBatesSolver`, `FdmCIRSolver`, `FdmHestonLocalVolatilityVarianceMesher`, `TRBDF2`, `FdmDirichletBoundary`, `FdmIndicesOnBoundary`, `UniformGridMesher` |
| **processes** | ~5 | `HestonSLVProcess`, `JointStochasticProcess`, `G2Process`, `G2ForwardProcess` (BlackScholesProcess/BlackProcess/GarmanKohlagenProcess need per-item check vs GBSM family) |
| **quotes** | ~4 | `DerivedQuote`, `ForwardSwapQuote`, `LastFixingQuote`, `EurodollarFuturesImpliedStdDevQuote` |
| **interpolation tail** | ~3 | `MixedInterpolation`, `UpdatedYInterpolation`, `LinearFlatInterpolation` |
| **misc** | ~3 | `RebatedExercise`, `Redemption`, `Function` (some may be inner — verify) |

**Bucket B is the real remaining migration.** It is dominated by: 42 currencies (mechanical),
the `experimental/credit` CDO + default-model family, NoArbSABR/vanna-volga vol, CPI &
swaption/CMS volatility surfaces, GJR-GARCH, digital CMS/Ibor coupons, and FDM solver tail.

## End-state definition (what "100%" means, mechanically)

`migration-harness/check_coverage.py` reports **0 unflagged gaps**, where every C++ class is
either:

1. **Ported** — a Java class of the same name exists, with cross-validated tests; OR
2. **Allowlisted** — listed in the script's reviewed allowlist with a one-line rationale
   (`case-rename`, `enum-holder`, `inner-class`, `cpp-template-idiom`, `commented-out-upstream`).

No silent carve-outs. No self-narrowed scope. The script is the denominator; the user can
re-run it any time to verify the claim.

## Execution

Drive Bucket B to zero, cluster by cluster, highest-confidence-mechanical first:

1. **Currencies (42)** — pilot. One C++ probe dumps `(name, code, numeric, fractions,
   symbol, fractionSymbol, rounding, format)` for all 109; Java adds the 42 missing as
   inner classes in the right `Africa/America/Asia/Europe/Oceania` file (crypto → a new
   `Crypto.java` mirroring C++ `cryptocurrencies.hpp`). Cross-validated against the probe.
2. **experimental/credit** (CDO + default models).
3. **Volatility surfaces** (CPI vol, CMS market, NoArbSABR/Xabr swaption cubes).
4. **GJR-GARCH** (model + process + analytic + MC engine).
5. **Digital coupons** + cashflow tail.
6. **FDM solver tail** + specialty processes.
7. **PSO optimizer family** + Ziggurat + interpolation tail + quotes + misc.
8. **Bucket A allowlist** + final audit → script reports 0.

Each cluster: implementer → spec review → code-quality review → FF-merge, per the project's
standing discipline. Tag only when `check_coverage.py` confirms 0 unflagged gaps.

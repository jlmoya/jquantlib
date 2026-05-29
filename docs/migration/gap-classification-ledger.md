# Gap classification ledger — PORT vs ALLOWLIST (COMPLETE)

**Date:** 2026-05-29
**Source:** `migration-harness/check_coverage.py` audit (177 → 135 after currencies).
**Method:** 6 read-only verification agents (one per subsystem) + orchestrator adjudication.
Every verdict is backed by concrete evidence (open the Java file / confirm the C++ role),
never a loose substring match. When torn between PORT and ALLOWLIST for a *real* class, default PORT.

**RESULT: 135 gaps = 42 PORT (genuinely absent) + 93 ALLOWLIST (verified false-positives).**

Verdict vocabulary: PORT · ALLOWLIST:case-rename→X · ALLOWLIST:inner-of→X ·
ALLOWLIST:cpp-idiom · ALLOWLIST:commented-out-upstream · ALLOWLIST:traits-folded→X.

---

## THE 42 PORTS (the real remaining migration), by cluster

| # | Cluster (Java package) | Count | Classes |
|---|---|---|---|
| A1 | quotes (`quotes`) | 4 | DerivedQuote, EurodollarFuturesImpliedStdDevQuote, ForwardSwapQuote, LastFixingQuote |
| A2 | cashflows (`cashflow`) | 7 | Redemption, AmortizingPayment, TimeBasket, DigitalCmsCoupon, DigitalCmsLeg, DigitalIborCoupon, DigitalIborLeg |
| B1 | processes (`processes`) | 7 | BlackProcess, BlackScholesProcess, GarmanKohlagenProcess, EndEulerDiscretization, G2Process, G2ForwardProcess, JointStochasticProcess (+inner CachingKey) |
| B2 | credit (`experimental.credit`) | 4 | CDO, BaseCorrelationLossModel, RandomDefaultModel, GaussianRandomDefaultModel |
| C1 | vol-surfaces (`termstructures.volatility`) | 6 | CPIVolatilitySurface, ConstantCPIVolatility, CmsMarket, CmsMarketCalibration, ConstantCapFloorTermVolatility, GridModelLocalVolSurface |
| C2 | FDM (`methods.finitedifferences`) | 5 | FdmDirichletBoundary, FdmIndicesOnBoundary, FdmHestonLocalVolatilityVarianceMesher, UniformGridMesher, TRBDF2(legacy template) |
| D1 | PSO/firefly (`experimental.math`) | 6 | AdaptiveInertia, ClubsTopology, KNeighbors, LevyFlightInertia, SimpleRandomInertia, DecreasingGaussianWalk |
| D2 | mcbasket builders (`experimental.mcbasket`) | 2 | MakeMCAmericanPathEngine, MakeMCPathBasketEngine |
| D3 | exercise (`exercise`) | 1 | RebatedExercise (ql/rebatedexercise.hpp) |

**Porting waves (2-3 package-disjoint worktrees at a time):**
- Wave A (IN FLIGHT): A1 quotes + A2 cashflows
- Wave B: B1 processes + B2 credit
- Wave C: C1 vol-surfaces + C2 FDM
- Wave D: D1 PSO/firefly + D2 mcbasket + D3 RebatedExercise

---

## THE 93 ALLOWLIST (verified false-positives, with rationale)

**Case-renames (Java has it, different case/spelling) — 22:**
BFGS→Bfgs, BiCGstab→BiCGStab, SABR→Sabr, TARGET→Target, GJRGARCHModel→GjrGarchModel,
GJRGARCHProcess→GjrGarchProcess, MCEuropeanGJRGARCHEngine→MCEuropeanGjrGarchEngine,
VolatilityInterpolationSpecifierabcd→…Abcd, IntegralCDOEngine→IntegralCdoEngine,
MidPointCDOEngine→MidPointCdoEngine, SyntheticCDO→SyntheticCdo, IrrFinder→IRRFinder,
HestonSLVProcess→HestonStochasticLocalVolProcess, Error→LibraryException, Cdi→Brlcdi,
Average→AverageType, Barrier→BarrierType, DoubleBarrier→DoubleBarrierType,
BlackCompoundingOvernightIndexedCouponPricer→BlackOvernightIndexedCouponPricer,
PiecewiseYoYOptionletVolatilityCurve→PiecewiseYoYOptionletVolatility,
CubicNaturalSpline→NaturalCubicInterpolation, MonotonicCubicNaturalSpline→MonotonicNaturalCubicInterpolation,
Parabolic→ParabolicCubicInterpolation, MonotonicParabolic→MonotonicParabolicCubicInterpolation,
Solver1D→AbstractSolver1D, Bicubic→BicubicSpline(factory). *(26 listed — exceeds 22; see note.)*

**inner-of (realized as inner class/enum/static-factory/Arguments/Results) — :**
IntegrationFactory→LatentModel, VectorIntegrator→GaussianQuadMultidimIntegrator, CachingKey→JointStochasticProcess,
Function→NumericHaganPricer, PriceHelper→LinearTsrPricer, VegaRatioHelper→LinearTsrPricer,
ForwardOptionArguments→ForwardVanillaOption, QuantoOptionResults→QuantoVanillaOption, Thirty360_Impl→Thirty360,
SingleStepCalibrationResult→AndreasenHugeVolatilityInterpl.StepResult, FdmVPPStepConditionMesher→FdmVPPStepCondition.Mesher,
FdmVPPStepConditionParams→FdmVPPStepCondition.Params, LinearFlatInterpolation→GeneralizedHullWhite, InterpolationData→NormalCLVModel.

**cpp-idiom (C++ template/CRTP/proxy/tag/policy/detail-helper; no Java runtime analog) — :**
RandomLM, IntegrationBase, BaseCurrencyProxy, ConversionTypeProxy, AcyclicVisitor, CuriouslyRecurringTemplate,
Foo, Proxy, Singleton, UpdateChecker, Clone, Null, Tracing, AbcdCoeffHolder, BackwardflatLinear,
BicubicSplineDerivatives, CubicInterpolationBaseImpl, SABRWrapper, UpdatedYInterpolation, EmptyArg, EmptyDim,
EmptyRes, LinearFct, LinearFcts, EarlyExerciseTraits, OperatorTraits, FdmBatesSolver(inlined→FdBatesVanillaEngine),
FdmCIRSolver(inlined→FdCIRVanillaEngine), FdmSimple3dExtOUJumpSolver(inlined→FdSimpleExtOUJumpSwingEngine, covered by SwingOptionTest),
DistributionRandomWalk, Ziggurat(traits; generator ZigguratRng ported), NoArbSabr(interp-traits tag), VannaVolga(interp-traits tag),
LinearFlat(interp-traits tag), SwaptionVolCubeNoArbSabrModel(tag), PrivateObserver, XabrSwaptionVolatilityCube(CRTP base), Root(solver functor→lambda).

**traits-folded (C++ traits/policy struct folded into Java generics/class) — :**
AffineHazardRate→InterpolatedAffineHazardRateCurve, InterpolatedCurve→Interpolated*Curve,
RelativeDateBootstrapHelper→RelativeDateRateHelper, SwaptionVolCubeSabrModel→SabrSwaptionVolatilityCube,
SwaptionVolCubeZabrModel→ZabrSwaptionVolatilityCube, XabrModelTraits→the 3 cubes, ZeroInflationTraits→InflationTraits,
YoYInflationVolatilityTraits→PiecewiseYoYOptionletVolatility, IborLegCashFlows→SwaptionCashFlows, SwapCashFlows→SwaptionCashFlows,
MixedInterpolation→MixedLinearCubicInterpolation.Behavior.

**commented-out-upstream (inside `/* */` or `//` in v1.42.1) — 4:**
RatchetPayoff_2, StickyPayoff_2, StickyRatchetPayoff (stickyratchet.hpp:168-227), ESFIntegrator (saddlepointlossmodel.hpp:355-379).

> NOTE: the exact category counts will be regenerated programmatically when the allowlist
> is encoded in check_coverage.py (task #616) — that encoding is the authoritative artifact;
> this prose is the human-readable rationale record. Per-agent detail in git history of this file.

---

## Per-agent detail (evidence)

### V1 — experimental/credit (DONE)
PORT(4): CDO (cdo.hpp:98, live CdoTests suite), BaseCorrelationLossModel (basecorrelationlossmodel.hpp:92),
RandomDefaultModel (randomdefaultmodel.hpp:37), GaussianRandomDefaultModel (randomdefaultmodel.hpp:63).
ALLOWLIST(9): SyntheticCdo, MidPointCdoEngine, IntegralCdoEngine (case); IntegrationFactory, VectorIntegrator (inner-of);
RandomLM, IntegrationBase (cpp-idiom CRTP); AffineHazardRate (traits-folded); ESFIntegrator (commented-out).

### V2 — experimental/other (DONE; FdmSimple3d + InterpolationData adjudicated → ALLOWLIST)
PORT(8): AdaptiveInertia, ClubsTopology, KNeighbors, LevyFlightInertia, SimpleRandomInertia (PSO Inertia/Topology),
DecreasingGaussianWalk (firefly), MakeMCAmericanPathEngine, MakeMCPathBasketEngine (fluent builders).
ALLOWLIST(16): DistributionRandomWalk, Ziggurat, NoArbSabr, SwaptionVolCubeNoArbSabrModel, VannaVolga, LinearFlat, Root (cpp-idiom);
FdmVPPStepConditionMesher, FdmVPPStepConditionParams, LinearFlatInterpolation, InterpolationData (inner-of);
YoYInflationVolatilityTraits, IborLegCashFlows, SwapCashFlows (traits-folded); PiecewiseYoYOptionletVolatilityCurve (case);
FdmSimple3dExtOUJumpSolver (cpp-idiom, inlined→FdSimpleExtOUJumpSwingEngine, covered by SwingOptionTest).

### V3 — math + methods/FDM (DONE; FdmIndicesOnBoundary added → PORT)
PORT(5): FdmDirichletBoundary (fdmdirichletboundary.hpp:38), FdmIndicesOnBoundary (fdmindicesonboundary.hpp:35, used by Dirichlet BCs),
FdmHestonLocalVolatilityVarianceMesher (fdmhestonvariancemesher.hpp:51), UniformGridMesher (uniformgridmesher.hpp:35),
TRBDF2 (trbdf2.hpp:73, legacy template — PORT for legacy-scheme-family parity).
ALLOWLIST(23): 4 cubic convenience renames, Solver1D→AbstractSolver1D, Bicubic→BicubicSpline,
MixedInterpolation (traits-folded), + detail/template helpers (AbcdCoeffHolder, BackwardflatLinear, BicubicSplineDerivatives,
CubicInterpolationBaseImpl, SABRWrapper, UpdatedYInterpolation, EmptyArg/Dim/Res, LinearFct/Fcts, EarlyExerciseTraits,
OperatorTraits, FdmBatesSolver, FdmCIRSolver).

### V4 — termstructures vol surfaces (DONE)
PORT(6): CPIVolatilitySurface, ConstantCPIVolatility, CmsMarket, CmsMarketCalibration, ConstantCapFloorTermVolatility, GridModelLocalVolSurface.
ALLOWLIST(9): InterpolatedCurve, RelativeDateBootstrapHelper, SwaptionVolCubeSabrModel, SwaptionVolCubeZabrModel,
XabrModelTraits, ZeroInflationTraits (traits-folded); PrivateObserver, XabrSwaptionVolatilityCube (cpp-idiom);
SingleStepCalibrationResult (inner-of).

### V5 — processes + quotes + root (DONE)
PORT(12): BlackProcess, BlackScholesProcess, GarmanKohlagenProcess, EndEulerDiscretization, G2Process, G2ForwardProcess,
JointStochasticProcess, DerivedQuote, EurodollarFuturesImpliedStdDevQuote, ForwardSwapQuote, LastFixingQuote, RebatedExercise.
ALLOWLIST(5): HestonStochasticLocalVolProcess (case), Error→LibraryException (case), BaseCurrencyProxy, ConversionTypeProxy (cpp-idiom),
CachingKey (inner-of JointStochasticProcess).

### V6 — cashflows + idiom buckets (DONE)
PORT(7): Redemption, AmortizingPayment, TimeBasket, DigitalCmsCoupon, DigitalCmsLeg, DigitalIborCoupon, DigitalIborLeg.
ALLOWLIST(21): BlackCompounding…→BlackOvernight… (case), Function/PriceHelper/VegaRatioHelper (inner-of), Average/Barrier/DoubleBarrier→*Type (case),
ForwardOptionArguments/QuantoOptionResults (inner-of), RatchetPayoff_2/StickyPayoff_2/StickyRatchetPayoff (commented-out),
patterns/* ×6 + utilities/* ×3 (cpp-idiom), Thirty360_Impl (inner-of), Cdi→Brlcdi (case).

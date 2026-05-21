# Phase 2 forward closure — L0 audit

**Generated:** 2026-05-20 (post-Phase-1-Path-A, tag `jquantlib-phase1-true-closure` @ `d56d5f87`)
**C++ baseline:** v1.42.1 @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5`

## Summary

- C++ unique class names: **2121**
- Java unique class names: **1750**
- PRESENT (name-match): **1415**
- MISSING in Java: **706**

## Classification

| Status | Count |
|---|---:|
| CONSOLIDATED | 142 |
| MISSING-NEEDED | 458 |
| MISSING-DEFERRED | 106 |

## Per-subdirectory breakdown

| Subdir | CONSOL | NEEDED | DEFER | TOTAL |
|---|---:|---:|---:|---:|
| `math/` | 15 | 127 | 0 | 142 |
| `currencies/` | 110 | 0 | 0 | 110 |
| `experimental/` | 3 | 0 | 106 | 109 |
| `pricingengines/` | 0 | 59 | 0 | 59 |
| `termstructures/` | 2 | 51 | 0 | 53 |
| `indexes/` | 0 | 42 | 0 | 42 |
| `instruments/` | 0 | 35 | 0 | 35 |
| `models/` | 3 | 31 | 0 | 34 |
| `time/` | 9 | 18 | 0 | 27 |
| `methods/` | 0 | 21 | 0 | 21 |
| `cashflows/` | 0 | 20 | 0 | 20 |
| `ql-root/` | 0 | 16 | 0 | 16 |
| `utilities/` | 0 | 11 | 0 | 11 |
| `processes/` | 0 | 10 | 0 | 10 |
| `patterns/` | 0 | 8 | 0 | 8 |
| `quotes/` | 0 | 8 | 0 | 8 |
| `legacy/` | 0 | 1 | 0 | 1 |

## MISSING-NEEDED (Phase 2 forward backlog)


### `cashflows/` (20 classes)

- `AmortizingPayment` — `cashflows/simplecashflow.hpp`
- `BlackCompoundingOvernightIndexedCouponPricer` — `cashflows/blackovernightindexedcouponpricer.hpp`
- `ConundrumIntegrand` — `cashflows/conundrumpricer.hpp`
- `DigitalCmsCoupon` — `cashflows/digitalcmscoupon.hpp`
- `DigitalCmsLeg` — `cashflows/digitalcmscoupon.hpp`
- `DigitalIborCoupon` — `cashflows/digitaliborcoupon.hpp`
- `DigitalIborLeg` — `cashflows/digitaliborcoupon.hpp`
- `Duration` — `cashflows/duration.hpp`
- `Function` — `cashflows/conundrumpricer.hpp`
- `GFunctionExactYield` — `cashflows/conundrumpricer.hpp`
- `GFunctionStandard` — `cashflows/conundrumpricer.hpp`
- `GFunctionWithShifts` — `cashflows/conundrumpricer.hpp`
- `IrrFinder` — `cashflows/cashflows.hpp`
- `ObjectiveFunction` — `cashflows/conundrumpricer.hpp`
- `PriceHelper` — `cashflows/lineartsrpricer.hpp`
- `Redemption` — `cashflows/simplecashflow.hpp`
- `TimeBasket` — `cashflows/timebasket.hpp`
- `VegaRatioHelper` — `cashflows/lineartsrpricer.hpp`
- `integrand_f` — `cashflows/lineartsrpricer.hpp`
- `yoyInflationLeg` — `cashflows/yoyinflationcoupon.hpp`

### `indexes/` (42 classes)

- `Aonia` — `indexes/ibor/aonia.hpp`
- `Bbsw` — `indexes/ibor/bbsw.hpp`
- `Bbsw1M` — `indexes/ibor/bbsw.hpp`
- `Bbsw2M` — `indexes/ibor/bbsw.hpp`
- `Bbsw3M` — `indexes/ibor/bbsw.hpp`
- `Bbsw4M` — `indexes/ibor/bbsw.hpp`
- `Bbsw5M` — `indexes/ibor/bbsw.hpp`
- `Bbsw6M` — `indexes/ibor/bbsw.hpp`
- `Bibor` — `indexes/ibor/bibor.hpp`
- `Bibor1M` — `indexes/ibor/bibor.hpp`
- `Bibor1Y` — `indexes/ibor/bibor.hpp`
- `Bibor2M` — `indexes/ibor/bibor.hpp`
- `Bibor3M` — `indexes/ibor/bibor.hpp`
- `Bibor6M` — `indexes/ibor/bibor.hpp`
- `BiborSW` — `indexes/ibor/bibor.hpp`
- `Bkbm` — `indexes/ibor/bkbm.hpp`
- `Bkbm1M` — `indexes/ibor/bkbm.hpp`
- `Bkbm2M` — `indexes/ibor/bkbm.hpp`
- `Bkbm3M` — `indexes/ibor/bkbm.hpp`
- `Bkbm4M` — `indexes/ibor/bkbm.hpp`
- `Bkbm5M` — `indexes/ibor/bkbm.hpp`
- `Bkbm6M` — `indexes/ibor/bkbm.hpp`
- `CaseInsensitiveCompare` — `indexes/indexmanager.hpp`
- `Cdi` — `indexes/ibor/cdi.hpp`
- `Corra` — `indexes/ibor/corra.hpp`
- `CustomRegion` — `indexes/region.hpp`
- `Destr` — `indexes/ibor/destr.hpp`
- `EURLiborON` — `indexes/ibor/eurlibor.hpp`
- `Estr` — `indexes/ibor/estr.hpp`
- `Euribor1W` — `indexes/ibor/euribor.hpp`
- `Kofr` — `indexes/ibor/kofr.hpp`
- `Mosprime` — `indexes/ibor/mosprime.hpp`
- `Nzocr` — `indexes/ibor/nzocr.hpp`
- `OvernightIndexedSwapIndex` — `indexes/swapindex.hpp`
- `Pribor` — `indexes/ibor/pribor.hpp`
- `Robor` — `indexes/ibor/robor.hpp`
- `Saron` — `indexes/ibor/saron.hpp`
- `Shibor` — `indexes/ibor/shibor.hpp`
- `Swestr` — `indexes/ibor/swestr.hpp`
- `THBFIX` — `indexes/ibor/thbfix.hpp`
- `Tonar` — `indexes/ibor/tonar.hpp`
- `Wibor` — `indexes/ibor/wibor.hpp`

### `instruments/` (35 classes)

- `AmortizingCmsRateBond` — `instruments/bonds/amortizingcmsratebond.hpp`
- `Average` — `instruments/averagetype.hpp`
- `BTP` — `instruments/bonds/btp.hpp`
- `Barrier` — `instruments/barriertype.hpp`
- `CCTEU` — `instruments/bonds/btp.hpp`
- `Cap` — `instruments/capfloor.hpp`
- `Collar` — `instruments/capfloor.hpp`
- `DoubleBarrier` — `instruments/doublebarriertype.hpp`
- `DoubleStickyRatchetPayoff` — `instruments/stickyratchet.hpp`
- `FaceValueAccrualClaim` — `instruments/claim.hpp`
- `FixedVsFloatingSwap` — `instruments/fixedvsfloatingswap.hpp`
- `Floor` — `instruments/capfloor.hpp`
- `ForwardOptionArguments` — `instruments/forwardvanillaoption.hpp`
- `Price` — `instruments/bond.hpp`
- `QuantoOptionResults` — `instruments/quantovanillaoption.hpp`
- `RatchetMaxPayoff` — `instruments/stickyratchet.hpp`
- `RatchetMinPayoff` — `instruments/stickyratchet.hpp`
- `RatchetPayoff` — `instruments/stickyratchet.hpp`
- `RatchetPayoff_2` — `instruments/stickyratchet.hpp`
- `RendistatoBasket` — `instruments/bonds/btp.hpp`
- `RendistatoCalculator` — `instruments/bonds/btp.hpp`
- `RendistatoEquivalentSwapLengthQuote` — `instruments/bonds/btp.hpp`
- `RendistatoEquivalentSwapSpreadQuote` — `instruments/bonds/btp.hpp`
- `StickyMaxPayoff` — `instruments/stickyratchet.hpp`
- `StickyMinPayoff` — `instruments/stickyratchet.hpp`
- `StickyPayoff` — `instruments/stickyratchet.hpp`
- `StickyPayoff_2` — `instruments/stickyratchet.hpp`
- `StickyRatchetPayoff` — `instruments/stickyratchet.hpp`
- `SuperFundPayoff` — `instruments/payoffs.hpp`
- `SuperSharePayoff` — `instruments/payoffs.hpp`
- `YoYInflationCap` — `instruments/inflationcapfloor.hpp`
- `YoYInflationCapFloor` — `instruments/inflationcapfloor.hpp`
- `YoYInflationCollar` — `instruments/inflationcapfloor.hpp`
- `YoYInflationFloor` — `instruments/inflationcapfloor.hpp`
- `engine` — `instruments/cpicapfloor.hpp`

### `legacy/` (1 classes)

- `Var_Helper` — `legacy/libormarketmodels/lfmcovarparam.hpp`

### `math/` (127 classes)

- `Abcd` — `math/interpolations/abcdinterpolation.hpp`
- `AbcdCoeffHolder` — `math/interpolations/abcdinterpolation.hpp`
- `AbcdInterpolation` — `math/interpolations/abcdinterpolation.hpp`
- `AbcdMathFunction` — `math/abcdmathfunction.hpp`
- `AkimaCubicInterpolation` — `math/interpolations/cubicinterpolation.hpp`
- `AliMikhailHaqCopula` — `math/copulas/alimikhailhaqcopula.hpp`
- `BFGS` — `math/optimization/bfgs.hpp`
- `BackwardflatLinear` — `math/interpolations/backwardflatlinearinterpolation.hpp`
- `BackwardflatLinearInterpolation` — `math/interpolations/backwardflatlinearinterpolation.hpp`
- `BernsteinPolynomial` — `math/bernsteinpolynomial.hpp`
- `BiCGStabResult` — `math/matrixutilities/bicgstab.hpp`
- `BiCGstab` — `math/matrixutilities/bicgstab.hpp`
- `Bicubic` — `math/interpolations/bicubicsplineinterpolation.hpp`
- `BicubicSplineDerivatives` — `math/interpolations/bicubicsplineinterpolation.hpp`
- `BivariateCumulativeNormalDistributionWe04DP` — `math/distributions/bivariatenormaldistribution.hpp`
- `BoxMullerGaussianRng` — `math/randomnumbers/boxmullergaussianrng.hpp`
- `Burley2020SobolBrownianBridgeRsg` — `math/randomnumbers/sobolbrownianbridgersg.hpp`
- `CLGaussianRng` — `math/randomnumbers/centrallimitgaussianrng.hpp`
- `Candidate` — `math/optimization/differentialevolution.hpp`
- `CeilingTruncation` — `math/rounding.hpp`
- `ClaytonCopula` — `math/copulas/claytoncopula.hpp`
- `ClosestRounding` — `math/rounding.hpp`
- `ComboHelper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `Configuration` — `math/optimization/differentialevolution.hpp`
- `ConstantGradHelper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `ConvexMonotone2Helper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `ConvexMonotone3Helper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `ConvexMonotone4Helper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `ConvexMonotone4MinHelper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `CubicNaturalSpline` — `math/interpolations/cubicinterpolation.hpp`
- `CubicSplineOvershootingMinimization1` — `math/interpolations/cubicinterpolation.hpp`
- `CubicSplineOvershootingMinimization2` — `math/interpolations/cubicinterpolation.hpp`
- `CumulativeChiSquareDistribution` — `math/distributions/chisquaredistribution.hpp`
- `CumulativeGammaDistribution` — `math/distributions/gammadistribution.hpp`
- `DataTable` — `math/interpolations/multicubicspline.hpp`
- `Default` — `math/integrals/trapezoidintegral.hpp`
- `DiscreteSimpsonIntegrator` — `math/integrals/discreteintegrals.hpp`
- `DiscreteTrapezoidIntegral` — `math/integrals/discreteintegrals.hpp`
- `DoublingConvergenceSteps` — `math/statistics/convergencestatistics.hpp`
- `DownRounding` — `math/rounding.hpp`
- `EmptyArg` — `math/interpolations/multicubicspline.hpp`
- `EmptyDim` — `math/interpolations/multicubicspline.hpp`
- `EmptyRes` — `math/interpolations/multicubicspline.hpp`
- `EverywhereConstantHelper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `FarlieGumbelMorgensternCopula` — `math/copulas/farliegumbelmorgensterncopula.hpp`
- `FloorTruncation` — `math/rounding.hpp`
- `FrankCopula` — `math/copulas/frankcopula.hpp`
- `FritschButlandLogCubic` — `math/interpolations/loginterpolation.hpp`
- `FrobeniusCostFunction` — `math/matrixutilities/tapcorrelations.hpp`
- `GMRESResult` — `math/matrixutilities/gmres.hpp`
- `GalambosCopula` — `math/copulas/galamboscopula.hpp`
- `GaussChebyshev2ndIntegration` — `math/integrals/gaussianquadratures.hpp`
- `GaussChebyshevIntegration` — `math/integrals/gaussianquadratures.hpp`
- `GaussGegenbauerIntegration` — `math/integrals/gaussianquadratures.hpp`
- `GaussGegenbauerPolynomial` — `math/integrals/gaussianorthogonalpolynomial.hpp`
- `GaussHyperbolicIntegration` — `math/integrals/gaussianquadratures.hpp`
- `GaussJacobiIntegration` — `math/integrals/gaussianquadratures.hpp`
- `GaussianCopula` — `math/copulas/gaussiancopula.hpp`
- `GaussianQuadratureIntegrator` — `math/integrals/gaussianquadratures.hpp`
- `GoldsteinLineSearch` — `math/optimization/goldstein.hpp`
- `GumbelCopula` — `math/copulas/gumbelcopula.hpp`
- `HarmonicCubic` — `math/interpolations/cubicinterpolation.hpp`
- `HarmonicLogCubic` — `math/interpolations/loginterpolation.hpp`
- `HuslerReissCopula` — `math/copulas/huslerreisscopula.hpp`
- `IndependentCopula` — `math/copulas/independentcopula.hpp`
- `Int2Type` — `math/interpolations/multicubicspline.hpp`
- `InverseNonCentralCumulativeChiSquareDistribution` — `math/distributions/chisquaredistribution.hpp`
- `KnuthUniformRng` — `math/randomnumbers/knuthuniformrng.hpp`
- `KrugerCubic` — `math/interpolations/cubicinterpolation.hpp`
- `KrugerLogCubic` — `math/interpolations/loginterpolation.hpp`
- `KrugerLogMixedLinearCubic` — `math/interpolations/loginterpolation.hpp`
- `LecuyerUniformRng` — `math/randomnumbers/lecuyeruniformrng.hpp`
- `LinearFct` — `math/linearleastsquaresregression.hpp`
- `LinearFcts` — `math/linearleastsquaresregression.hpp`
- `LogCubicNaturalSpline` — `math/interpolations/loginterpolation.hpp`
- `LogMixedLinearCubic` — `math/interpolations/loginterpolation.hpp`
- `LogMixedLinearCubicInterpolation` — `math/interpolations/loginterpolation.hpp`
- `LogMixedLinearCubicNaturalSpline` — `math/interpolations/loginterpolation.hpp`
- `LogParabolic` — `math/interpolations/loginterpolation.hpp`
- `MaddockCumulativeNormal` — `math/distributions/normaldistribution.hpp`
- `MaddockInverseCumulativeNormal` — `math/distributions/normaldistribution.hpp`
- `MarshallOlkinCopula` — `math/copulas/marshallolkincopula.hpp`
- `MaxCopula` — `math/copulas/maxcopula.hpp`
- `MidPoint` — `math/integrals/trapezoidintegral.hpp`
- `MinCopula` — `math/copulas/mincopula.hpp`
- `MixedInterpolation` — `math/interpolations/mixedinterpolation.hpp`
- `MixedLinearCubicNaturalSpline` — `math/interpolations/mixedinterpolation.hpp`
- `MixedLinearFritschButlandCubic` — `math/interpolations/mixedinterpolation.hpp`
- `MixedLinearKrugerCubic` — `math/interpolations/mixedinterpolation.hpp`
- `MixedLinearMonotonicCubicNaturalSpline` — `math/interpolations/mixedinterpolation.hpp`
- `MixedLinearMonotonicParabolic` — `math/interpolations/mixedinterpolation.hpp`
- `MixedLinearParabolic` — `math/interpolations/mixedinterpolation.hpp`
- `MonotonicCubicNaturalSpline` — `math/interpolations/cubicinterpolation.hpp`
- `MonotonicLogCubicNaturalSpline` — `math/interpolations/loginterpolation.hpp`
- `MonotonicLogMixedLinearCubic` — `math/interpolations/loginterpolation.hpp`
- `MonotonicLogParabolic` — `math/interpolations/loginterpolation.hpp`
- `MonotonicParabolic` — `math/interpolations/cubicinterpolation.hpp`
- `NonCentralCumulativeChiSquareDistribution` — `math/distributions/chisquaredistribution.hpp`
- `NonCentralCumulativeChiSquareSankaranApprox` — `math/distributions/chisquaredistribution.hpp`
- `NonhomogeneousBoundaryConstraint` — `math/optimization/constraint.hpp`
- `OdeFctWrapper` — `math/ode/adaptiverungekutta.hpp`
- `Parabolic` — `math/interpolations/cubicinterpolation.hpp`
- `PascalTriangle` — `math/pascaltriangle.hpp`
- `PlackettCopula` — `math/copulas/plackettcopula.hpp`
- `Point` — `math/interpolations/multicubicspline.hpp`
- `PolynomialFunction` — `math/polynomialmathfunction.hpp`
- `QuadraticMinHelper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `Ranlux64UniformRng` — `math/randomnumbers/ranluxuniformrng.hpp`
- `SABR` — `math/interpolations/sabrinterpolation.hpp`
- `SABRSpecs` — `math/interpolations/sabrinterpolation.hpp`
- `SABRWrapper` — `math/interpolations/sabrinterpolation.hpp`
- `SalvagingAlgorithm` — `math/matrixutilities/pseudosqrt.hpp`
- `SectionHelper` — `math/interpolations/convexmonotoneinterpolation.hpp`
- `SimpleCostFunction` — `math/optimization/costfunction.hpp`
- `SimulatedAnnealing` — `math/optimization/simulatedannealing.hpp`
- `Solver1D` — `math/solver1d.hpp`
- `StatsHolder` — `math/statistics/gaussianstatistics.hpp`
- `UpRounding` — `math/rounding.hpp`
- `UpdatedYInterpolation` — `math/interpolations/lagrangeinterpolation.hpp`
- `XABRError` — `math/interpolations/xabrinterpolation.hpp`
- `Zabr` — `math/interpolations/zabrinterpolation.hpp`
- `ZabrSpecs` — `math/interpolations/zabrinterpolation.hpp`
- `base_cubic_spline` — `math/interpolations/multicubicspline.hpp`
- `base_cubic_splint` — `math/interpolations/multicubicspline.hpp`
- `n_cubic_spline` — `math/interpolations/multicubicspline.hpp`
- `n_cubic_splint` — `math/interpolations/multicubicspline.hpp`
- `quadratic` — `math/quadratic.hpp`

### `methods/` (21 classes)

- `Branching` — `methods/lattices/trinomialtree.hpp`
- `EarlyExerciseTraits` — `methods/montecarlo/earlyexercisepathpricer.hpp`
- `FdmArithmeticAverageCondition` — `methods/finitedifferences/stepconditions/fdmarithmeticaveragecondition.hpp`
- `FdmBatesSolver` — `methods/finitedifferences/solvers/fdmbatessolver.hpp`
- `FdmCIREquityPart` — `methods/finitedifferences/operators/fdmcirop.hpp`
- `FdmCIRMixedPart` — `methods/finitedifferences/operators/fdmcirop.hpp`
- `FdmCIRRatesPart` — `methods/finitedifferences/operators/fdmcirop.hpp`
- `FdmCIRSolver` — `methods/finitedifferences/solvers/fdmcirsolver.hpp`
- `FdmDirichletBoundary` — `methods/finitedifferences/utilities/fdmdirichletboundary.hpp`
- `FdmHestonEquityPart` — `methods/finitedifferences/operators/fdmhestonop.hpp`
- `FdmHestonHullWhiteEquityPart` — `methods/finitedifferences/operators/fdmhestonhullwhiteop.hpp`
- `FdmHestonLocalVolatilityVarianceMesher` — `methods/finitedifferences/meshers/fdmhestonvariancemesher.hpp`
- `FdmHestonVariancePart` — `methods/finitedifferences/operators/fdmhestonop.hpp`
- `FdmIndicesOnBoundary` — `methods/finitedifferences/utilities/fdmindicesonboundary.hpp`
- `FdmOrnsteinUhlenbeckOp` — `methods/finitedifferences/operators/fdmornsteinuhlenbeckop.hpp`
- `IntegroIntegrand` — `methods/finitedifferences/operators/fdmbatesop.hpp`
- `InvCDFHelper` — `methods/finitedifferences/utilities/riskneutraldensitycalculator.hpp`
- `OperatorTraits` — `methods/finitedifferences/operatortraits.hpp`
- `TRBDF2` — `methods/finitedifferences/trbdf2.hpp`
- `TimeSetter` — `methods/finitedifferences/tridiagonaloperator.hpp`
- `UniformGridMesher` — `methods/finitedifferences/meshers/uniformgridmesher.hpp`

### `models/` (31 classes)

- `BatesDoubleExpDetJumpModel` — `models/equity/batesmodel.hpp`
- `Burley2020SobolBrownianGenerator` — `models/marketmodels/browniangenerators/sobolbrowniangenerator.hpp`
- `Burley2020SobolBrownianGeneratorFactory` — `models/marketmodels/browniangenerators/sobolbrowniangenerator.hpp`
- `CachedSwapKey` — `models/shortrate/onefactormodels/gaussian1dmodel.hpp`
- `CachedSwapKeyHasher` — `models/shortrate/onefactormodels/gaussian1dmodel.hpp`
- `CalibrationFunction` — `models/model.hpp`
- `CalibrationPoint` — `models/shortrate/onefactormodels/markovfunctional.hpp`
- `CustomSmileFactory` — `models/shortrate/onefactormodels/markovfunctional.hpp`
- `CustomSmileSection` — `models/shortrate/onefactormodels/markovfunctional.hpp`
- `Dynamics` — `models/shortrate/twofactormodels/g2.hpp`
- `FellerConstraint` — `models/equity/hestonmodel.hpp`
- `FittingParameter` — `models/shortrate/twofactormodels/g2.hpp`
- `GJRGARCHModel` — `models/equity/gjrgarchmodel.hpp`
- `Helper` — `models/shortrate/onefactormodel.hpp`
- `HistoricalForwardRatesAnalysis` — `models/marketmodels/historicalforwardratesanalysis.hpp`
- `HistoricalRatesAnalysis` — `models/marketmodels/historicalratesanalysis.hpp`
- `LogEntry` — `models/equity/hestonslvfdmmodel.hpp`
- `ModelOutputs` — `models/shortrate/onefactormodels/markovfunctional.hpp`
- `ModelSettings` — `models/shortrate/onefactormodels/markovfunctional.hpp`
- `PrivateConstraint` — `models/model.hpp`
- `ReversionObserver` — `models/shortrate/onefactormodels/gsr.hpp`
- `ShortRateDynamics` — `models/shortrate/twofactormodel.hpp`
- `ShortRateTree` — `models/shortrate/twofactormodel.hpp`
- `SobolBrownianGeneratorBase` — `models/marketmodels/browniangenerators/sobolbrowniangenerator.hpp`
- `SubProduct` — `models/marketmodels/products/compositeproduct.hpp`
- `SwaptionPricingFunction` — `models/shortrate/twofactormodels/g2.hpp`
- `VolatilityConstraint` — `models/equity/gjrgarchmodel.hpp`
- `VolatilityInterpolationSpecifierabcd` — `models/marketmodels/models/volatilityinterpolationspecifierabcd.hpp`
- `VolatilityObserver` — `models/shortrate/onefactormodels/gsr.hpp`
- `ZeroHelper` — `models/shortrate/onefactormodels/markovfunctional.hpp`
- `curveState` — `models/marketmodels/products/pathwise/pathwiseproductcallspecified.hpp`

### `patterns/` (8 classes)

- `CuriouslyRecurringTemplate` — `patterns/curiouslyrecurring.hpp`
- `Defaults` — `patterns/lazyobject.hpp`
- `Foo` — `patterns/singleton.hpp`
- `Proxy` — `patterns/observable.hpp`
- `Signal` — `patterns/observable.hpp`
- `Singleton` — `patterns/singleton.hpp`
- `UpdateChecker` — `patterns/lazyobject.hpp`
- `hash` — `patterns/observable.hpp`

### `pricingengines/` (59 classes)

- `AP_Helper` — `pricingengines/vanilla/analytichestonengine.hpp`
- `AnalyticBlackVasicekEngine` — `pricingengines/vanilla/analyticeuropeanvasicekengine.hpp`
- `BachelierSpec` — `pricingengines/swaption/blackswaptionengine.hpp`
- `BarrierPathPricer` — `pricingengines/barrier/mcbarrierengine.hpp`
- `BiasedBarrierPathPricer` — `pricingengines/barrier/mcbarrierengine.hpp`
- `BinomialBarrierEngine` — `pricingengines/barrier/binomialbarrierengine.hpp`
- `Black76Spec` — `pricingengines/swaption/blackswaptionengine.hpp`
- `BlackStyleSwaptionEngine` — `pricingengines/swaption/blackswaptionengine.hpp`
- `CEVCalculator` — `pricingengines/vanilla/analyticcevengine.hpp`
- `Calculator` — `pricingengines/blackcalculator.hpp`
- `CounterpartyAdjSwapEngine` — `pricingengines/swap/cvaswapengine.hpp`
- `DigitalPathPricer` — `pricingengines/vanilla/mcdigitalengine.hpp`
- `DiscretizedBarrierOption` — `pricingengines/barrier/discretizedbarrieroption.hpp`
- `DiscretizedCapFloor` — `pricingengines/capfloor/discretizedcapfloor.hpp`
- `DiscretizedDermanKaniBarrierOption` — `pricingengines/barrier/discretizedbarrieroption.hpp`
- `EuropeanGJRGARCHPathPricer` — `pricingengines/vanilla/mceuropeangjrgarchengine.hpp`
- `FdBlackScholesAsianEngine` — `pricingengines/asian/fdblackscholesasianengine.hpp`
- `Fj_Helper` — `pricingengines/vanilla/analytich1hwengine.hpp`
- `ForwardEuropeanBSPathPricer` — `pricingengines/forward/mcforwardeuropeanbsengine.hpp`
- `ForwardEuropeanHestonPathPricer` — `pricingengines/forward/mcforwardeuropeanhestonengine.hpp`
- `G2SwaptionEngine` — `pricingengines/swaption/g2swaptionengine.hpp`
- `HestonHullWhitePathPricer` — `pricingengines/vanilla/mchestonhullwhiteengine.hpp`
- `HullWhiteCapFloorPricer` — `pricingengines/capfloor/mchullwhiteengine.hpp`
- `Integrand` — `pricingengines/forward/mcvarianceswapengine.hpp`
- `Integration` — `pricingengines/vanilla/analytichestonengine.hpp`
- `LPP3HestonExpansion` — `pricingengines/vanilla/hestonexpansionengine.hpp`
- `LatticeShortRateModelEngine` — `pricingengines/latticeshortratemodelengine.hpp`
- `MCBarrierEngine` — `pricingengines/barrier/mcbarrierengine.hpp`
- `MCEuropeanGJRGARCHEngine` — `pricingengines/vanilla/mceuropeangjrgarchengine.hpp`
- `MCForwardVanillaEngine` — `pricingengines/forward/mcforwardvanillaengine.hpp`
- `MCHullWhiteCapFloorEngine` — `pricingengines/capfloor/mchullwhiteengine.hpp`
- `MCLookbackEngine` — `pricingengines/lookback/mclookbackengine.hpp`
- `MakeFdBlackScholesVanillaEngine` — `pricingengines/vanilla/fdblackscholesvanillaengine.hpp`
- `MakeFdCIRVanillaEngine` — `pricingengines/vanilla/fdcirvanillaengine.hpp`
- `MakeFdHestonVanillaEngine` — `pricingengines/vanilla/fdhestonvanillaengine.hpp`
- `MakeMCAmericanBasketEngine` — `pricingengines/basket/mcamericanbasketengine.hpp`
- `MakeMCAmericanEngine` — `pricingengines/vanilla/mcamericanengine.hpp`
- `MakeMCBarrierEngine` — `pricingengines/barrier/mcbarrierengine.hpp`
- `MakeMCDigitalEngine` — `pricingengines/vanilla/mcdigitalengine.hpp`
- `MakeMCEuropeanBasketEngine` — `pricingengines/basket/mceuropeanbasketengine.hpp`
- `MakeMCEuropeanEngine` — `pricingengines/vanilla/mceuropeanengine.hpp`
- `MakeMCEuropeanGJRGARCHEngine` — `pricingengines/vanilla/mceuropeangjrgarchengine.hpp`
- `MakeMCForwardEuropeanBSEngine` — `pricingengines/forward/mcforwardeuropeanbsengine.hpp`
- `MakeMCForwardEuropeanHestonEngine` — `pricingengines/forward/mcforwardeuropeanhestonengine.hpp`
- `MakeMCHestonHullWhiteEngine` — `pricingengines/vanilla/mchestonhullwhiteengine.hpp`
- `MakeMCHullWhiteCapFloorEngine` — `pricingengines/capfloor/mchullwhiteengine.hpp`
- `MakeMCPerformanceEngine` — `pricingengines/cliquet/mcperformanceengine.hpp`
- `MakeMCVarianceSwapEngine` — `pricingengines/forward/mcvarianceswapengine.hpp`
- `MatchHelper` — `pricingengines/swaption/basketgeneratingengine.hpp`
- `OptimalAlpha` — `pricingengines/vanilla/analytichestonengine.hpp`
- `PastFixingsOnly` — `pricingengines/asian/mcdiscreteasianenginebase.hpp`
- `QdPlusAddOnValue` — `pricingengines/vanilla/qdplusamericanengine.hpp`
- `QdPlusBoundaryEvaluator` — `pricingengines/vanilla/qdplusamericanengine.hpp`
- `QuantoEngine` — `pricingengines/quanto/quantoengine.hpp`
- `SumExponentialsRootSolver` — `pricingengines/basket/singlefactorbsmbasketengine.hpp`
- `TreeCapFloorEngine` — `pricingengines/capfloor/treecapfloorengine.hpp`
- `VariancePathPricer` — `pricingengines/forward/mcvarianceswapengine.hpp`
- `YoYInflationCapFloorEngine` — `pricingengines/inflation/inflationcapfloorengines.hpp`
- `rStarFinder` — `pricingengines/swaption/jamshidianswaptionengine.hpp`

### `processes/` (10 classes)

- `BlackProcess` — `processes/blackscholesprocess.hpp`
- `BlackScholesProcess` — `processes/blackscholesprocess.hpp`
- `CachingKey` — `processes/jointstochasticprocess.hpp`
- `EndEulerDiscretization` — `processes/endeulerdiscretization.hpp`
- `G2ForwardProcess` — `processes/g2process.hpp`
- `G2Process` — `processes/g2process.hpp`
- `GJRGARCHProcess` — `processes/gjrgarchprocess.hpp`
- `GarmanKohlagenProcess` — `processes/blackscholesprocess.hpp`
- `HestonSLVProcess` — `processes/hestonslvprocess.hpp`
- `JointStochasticProcess` — `processes/jointstochasticprocess.hpp`

### `ql-root/` (16 classes)

- `AcyclicVisitor` — `event.hpp`
- `BaseCurrencyProxy` — `money.hpp`
- `ConversionTypeProxy` — `money.hpp`
- `Data` — `currency.hpp`
- `DateProxy` — `settings.hpp`
- `Error` — `errors.hpp`
- `Greeks` — `option.hpp`
- `Link` — `handle.hpp`
- `MoreGreeks` — `option.hpp`
- `RebatedExercise` — `rebatedexercise.hpp`
- `arguments` — `option.hpp`
- `discretization` — `stochasticprocess.hpp`
- `earlier_than` — `cashflow.hpp`
- `results` — `pricingengine.hpp`
- `reverse` — `timeseries.hpp`
- `simple_event` — `event.hpp`

### `quotes/` (8 classes)

- `CompositeQuote` — `quotes/compositequote.hpp`
- `DerivedQuote` — `quotes/derivedquote.hpp`
- `EurodollarFuturesImpliedStdDevQuote` — `quotes/eurodollarfuturesquote.hpp`
- `ForwardSwapQuote` — `quotes/forwardswapquote.hpp`
- `ForwardValueQuote` — `quotes/forwardvaluequote.hpp`
- `FuturesConvAdjustmentQuote` — `quotes/futuresconvadjustmentquote.hpp`
- `ImpliedStdDevQuote` — `quotes/impliedstddevquote.hpp`
- `LastFixingQuote` — `quotes/lastfixingquote.hpp`

### `termstructures/` (51 classes)

- `AbcdError` — `termstructures/volatility/abcdcalibration.hpp`
- `AbcdParametersTransformation` — `termstructures/volatility/abcdcalibration.hpp`
- `AdditionalBootstrapVariables` — `termstructures/globalbootstrap.hpp`
- `AndreasenHugeCostFunction` — `termstructures/volatility/equityfx/andreasenhugevolatilityinterpl.hpp`
- `AtmAdjustedSmileSection` — `termstructures/volatility/atmadjustedsmilesection.hpp`
- `CPIVolatilitySurface` — `termstructures/volatility/inflation/cpivolatilitystructure.hpp`
- `CmsMarket` — `termstructures/volatility/swaption/cmsmarket.hpp`
- `CmsMarketCalibration` — `termstructures/volatility/swaption/cmsmarketcalibration.hpp`
- `CompositeZeroYieldStructure` — `termstructures/yield/compositezeroyieldstructure.hpp`
- `ConstantCPIVolatility` — `termstructures/volatility/inflation/constantcpivolatility.hpp`
- `ConstantCapFloorTermVolatility` — `termstructures/volatility/capfloor/constantcapfloortermvol.hpp`
- `CubicBSplinesFitting` — `termstructures/yield/nonlinearfittingmethods.hpp`
- `DefaultDensity` — `termstructures/credit/probabilitytraits.hpp`
- `ExponentialSplinesFitting` — `termstructures/yield/nonlinearfittingmethods.hpp`
- `FittingCost` — `termstructures/yield/fittedbonddiscountcurve.hpp`
- `FxSwapRateHelper` — `termstructures/yield/ratehelpers.hpp`
- `GridModelLocalVolSurface` — `termstructures/volatility/equityfx/gridmodellocalvolsurface.hpp`
- `HazardRate` — `termstructures/credit/probabilitytraits.hpp`
- `InterpolatedCurve` — `termstructures/interpolatedcurve.hpp`
- `InterpolatedSimpleZeroCurve` — `termstructures/yield/interpolatedsimplezerocurve.hpp`
- `InterpolatedSpreadDiscountCurve` — `termstructures/yield/spreaddiscountcurve.hpp`
- `MultiCurve` — `termstructures/multicurve.hpp`
- `MultiCurveBootstrap` — `termstructures/globalbootstrap.hpp`
- `MultiCurveBootstrapContributor` — `termstructures/globalbootstrap.hpp`
- `MultiCurveBootstrapProvider` — `termstructures/multicurve.hpp`
- `NaturalCubicFitting` — `termstructures/yield/nonlinearfittingmethods.hpp`
- `PiecewiseSpreadYieldCurve` — `termstructures/yield/piecewisespreadyieldcurve.hpp`
- `PrivateObserver` — `termstructures/volatility/swaption/sabrswaptionvolatilitycube.hpp`
- `RelativeDateBootstrapHelper` — `termstructures/bootstraphelper.hpp`
- `SimpleQuoteVariables` — `termstructures/globalbootstrapvars.hpp`
- `SimpleZeroYield` — `termstructures/yield/bootstraptraits.hpp`
- `SingleStepCalibrationResult` — `termstructures/volatility/equityfx/andreasenhugevolatilityinterpl.hpp`
- `SofrFutureRateHelper` — `termstructures/yield/overnightindexfutureratehelper.hpp`
- `SpreadFittingMethod` — `termstructures/yield/nonlinearfittingmethods.hpp`
- `SpreadTraits` — `termstructures/yield/spreadbootstraptraits.hpp`
- `SurvivalProbability` — `termstructures/credit/probabilitytraits.hpp`
- `SwaptionVolCubeSabrModel` — `termstructures/volatility/swaption/sabrswaptionvolatilitycube.hpp`
- `SwaptionVolCubeZabrModel` — `termstructures/volatility/swaption/zabrswaptionvolatilitycube.hpp`
- `XabrModelTraits` — `termstructures/volatility/swaption/zabrswaptionvolatilitycube.hpp`
- `XabrSwaptionVolatilityCube` — `termstructures/volatility/swaption/sabrswaptionvolatilitycube.hpp`
- `ZabrFullFd` — `termstructures/volatility/zabrsmilesection.hpp`
- `ZabrInterpolatedSmileSection` — `termstructures/volatility/zabrinterpolatedsmilesection.hpp`
- `ZabrLocalVolatility` — `termstructures/volatility/zabrsmilesection.hpp`
- `ZabrShortMaturityLognormal` — `termstructures/volatility/zabrsmilesection.hpp`
- `ZabrShortMaturityNormal` — `termstructures/volatility/zabrsmilesection.hpp`
- `ZeroInflationTraits` — `termstructures/inflation/inflationtraits.hpp`
- `aHelper` — `termstructures/volatility/kahalesmilesection.hpp`
- `cFunction` — `termstructures/volatility/kahalesmilesection.hpp`
- `curve` — `termstructures/credit/probabilitytraits.hpp`
- `sHelper` — `termstructures/volatility/kahalesmilesection.hpp`
- `sHelper1` — `termstructures/volatility/kahalesmilesection.hpp`

### `time/` (18 classes)

- `Austria` — `time/calendars/austria.hpp`
- `Botswana` — `time/calendars/botswana.hpp`
- `Chile` — `time/calendars/chile.hpp`
- `France` — `time/calendars/france.hpp`
- `OneDayCounter` — `time/daycounters/one.hpp`
- `Romania` — `time/calendars/romania.hpp`
- `TARGET` — `time/calendars/target.hpp`
- `Thailand` — `time/calendars/thailand.hpp`
- `formatted_date_holder` — `time/date.hpp`
- `iso_date_holder` — `time/date.hpp`
- `iso_datetime_holder` — `time/date.hpp`
- `long_date_holder` — `time/date.hpp`
- `long_period_holder` — `time/period.hpp`
- `long_weekday_holder` — `time/weekday.hpp`
- `short_date_holder` — `time/date.hpp`
- `short_period_holder` — `time/period.hpp`
- `short_weekday_holder` — `time/weekday.hpp`
- `shortest_weekday_holder` — `time/weekday.hpp`

### `utilities/` (11 classes)

- `Clone` — `utilities/clone.hpp`
- `Null` — `utilities/null.hpp`
- `Tracing` — `utilities/tracing.hpp`
- `null_checker` — `utilities/dataformatters.hpp`
- `null_deleter` — `utilities/null_deleter.hpp`
- `ordinal_holder` — `utilities/dataformatters.hpp`
- `percent_holder` — `utilities/dataformatters.hpp`
- `power_of_two_holder` — `utilities/dataformatters.hpp`
- `sequence_holder` — `utilities/dataformatters.hpp`
- `step_iterator` — `utilities/steppingiterator.hpp`
- `variant_visitor` — `utilities/variants.hpp`

## MISSING-DEFERRED (106 classes)

Experimental classes without current Java consumers. Re-classify
if a Java caller emerges.

- `AbcdAtmVolCurve` — `experimental/volatility/volcube.hpp`
- `AdaptiveInertia` — `experimental/math/particleswarmoptimization.hpp`
- `AffineHazardRate` — `experimental/credit/interpolatedaffinehazardratecurve.hpp`
- `BaseCorrelationLossModel` — `experimental/credit/basecorrelationlossmodel.hpp`
- `BlackIborQuantoCouponPricer` — `experimental/coupons/quantocouponpricer.hpp`
- `CDO` — `experimental/credit/cdo.hpp`
- `ClubsTopology` — `experimental/math/particleswarmoptimization.hpp`
- `ConstantLossLatentmodel` — `experimental/credit/constantlosslatentmodel.hpp`
- `CorrelationStructure` — `experimental/basismodels/tenoroptionletvts.hpp`
- `DcfIntegrand` — `experimental/asian/analytic_cont_geom_av_price_heston.hpp`
- `DecreasingGaussianWalk` — `experimental/math/fireflyalgorithm.hpp`
- `DecreasingInertia` — `experimental/math/particleswarmoptimization.hpp`
- `DefaultSettlement` — `experimental/credit/defaultevent.hpp`
- `DistributionRandomWalk` — `experimental/math/fireflyalgorithm.hpp`
- `ESFIntegrator` — `experimental/credit/saddlepointlossmodel.hpp`
- `ExponentialIntensity` — `experimental/math/fireflyalgorithm.hpp`
- `ExtendedBlackVarianceSurface` — `experimental/volatility/extendedblackvariancesurface.hpp`
- `FdOrnsteinUhlenbeckVanillaEngine` — `experimental/finitedifferences/fdornsteinuhlenbeckvanillaengine.hpp`
- `FdmSimple3dExtOUJumpSolver` — `experimental/finitedifferences/fdmsimple3dextoujumpsolver.hpp`
- `FdmVPPStepConditionMesher` — `experimental/finitedifferences/fdmvppstepcondition.hpp`
- `FdmVPPStepConditionParams` — `experimental/finitedifferences/fdmvppstepcondition.hpp`
- `FdmZabrUnderlyingPart` — `experimental/finitedifferences/fdmzabrop.hpp`
- `FdmZabrVolatilityPart` — `experimental/finitedifferences/fdmzabrop.hpp`
- `GaussianRandomDefaultModel` — `experimental/credit/randomdefaultmodel.hpp`
- `GaussianWalk` — `experimental/math/fireflyalgorithm.hpp`
- `GenericCPI` — `experimental/inflation/genericindexes.hpp`
- `GenericRegion` — `experimental/inflation/genericindexes.hpp`
- `GlobalTopology` — `experimental/math/particleswarmoptimization.hpp`
- `HybridSimulatedAnnealing` — `experimental/math/hybridsimulatedannealing.hpp`
- `IborLegCashFlows` — `experimental/basismodels/swaptioncfs.hpp`
- `ImpliedVolHelper` — `experimental/callablebonds/callablebond.hpp`
- `Inertia` — `experimental/math/particleswarmoptimization.hpp`
- `IntegralCDOEngine` — `experimental/credit/integralcdoengine.hpp`
- `IntegrationBase` — `experimental/math/latentmodel.hpp`
- `IntegrationFactory` — `experimental/math/latentmodel.hpp`
- `Intensity` — `experimental/math/fireflyalgorithm.hpp`
- `InterpolationData` — `experimental/models/normalclvmodel.hpp`
- `InverseLawSquareIntensity` — `experimental/math/fireflyalgorithm.hpp`
- `KNeighbors` — `experimental/math/particleswarmoptimization.hpp`
- `LevyFlightInertia` — `experimental/math/particleswarmoptimization.hpp`
- `LevyFlightWalk` — `experimental/math/fireflyalgorithm.hpp`
- `LinearFlat` — `experimental/shortrate/generalizedhullwhite.hpp`
- `LinearFlatInterpolation` — `experimental/shortrate/generalizedhullwhite.hpp`
- `MakeMCAmericanPathEngine` — `experimental/mcbasket/mcamericanpathengine.hpp`
- `MakeMCPathBasketEngine` — `experimental/mcbasket/mcpathbasketengine.hpp`
- `ManipulateDistribution` — `experimental/credit/distribution.hpp`
- `MappingFunction` — `experimental/models/squarerootclvmodel.hpp`
- `MidPointCDOEngine` — `experimental/credit/midpointcdoengine.hpp`
- `NPVSpreadHelper` — `experimental/callablebonds/callablebond.hpp`
- `NoArbSabr` — `experimental/volatility/noarbsabrinterpolation.hpp`
- `NoArbSabrSpecs` — `experimental/volatility/noarbsabrinterpolation.hpp`
- `PathInfo` — `experimental/mcbasket/longstaffschwartzmultipathpricer.hpp`
- `PiecewiseYoYOptionletVolatilityCurve` — `experimental/inflation/piecewiseyoyoptionletvolatility.hpp`
- `Polynomial` — `experimental/inflation/polynomial2Dspline.hpp`
- `ProbabilityAlwaysDownhill` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `ProbabilityBoltzmann` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `ProbabilityBoltzmannDownhill` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `ProxyIbor` — `experimental/coupons/proxyibor.hpp`
- `RandomDefaultModel` — `experimental/credit/randomdefaultmodel.hpp`
- `RandomLM` — `experimental/credit/randomdefaultlatentmodel.hpp`
- `RandomWalk` — `experimental/math/fireflyalgorithm.hpp`
- `ReannealingFiniteDifferences` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `ReannealingTrivial` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `RecursiveLossModel` — `experimental/credit/recursivelossmodel.hpp`
- `Root` — `experimental/credit/randomdefaultlatentmodel.hpp`
- `SabrVolSurface` — `experimental/volatility/sabrvolsurface.hpp`
- `SaddleObjectiveFunction` — `experimental/credit/saddlepointlossmodel.hpp`
- `SaddlePercObjFunction` — `experimental/credit/saddlepointlossmodel.hpp`
- `SaddlePointLossModel` — `experimental/credit/saddlepointlossmodel.hpp`
- `SamplerCauchy` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `SamplerGaussian` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `SamplerLogNormal` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `SamplerMirrorGaussian` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `SamplerRingGaussian` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `SamplerVeryFastAnnealing` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `SimpleRandomInertia` — `experimental/math/particleswarmoptimization.hpp`
- `StrippedCappedFlooredCoupon` — `experimental/coupons/strippedcapflooredcoupon.hpp`
- `StrippedCappedFlooredCouponLeg` — `experimental/coupons/strippedcapflooredcoupon.hpp`
- `Svi` — `experimental/volatility/sviinterpolation.hpp`
- `SviSpecs` — `experimental/volatility/sviinterpolation.hpp`
- `SwapCashFlows` — `experimental/basismodels/swaptioncfs.hpp`
- `SwaptionVolCubeNoArbSabrModel` — `experimental/volatility/noarbsabrswaptionvolatilitycube.hpp`
- `SyntheticCDO` — `experimental/credit/syntheticcdo.hpp`
- `TemperatureBoltzmann` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `TemperatureCauchy` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `TemperatureCauchy1D` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `TemperatureExponential` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `TemperatureVeryFastAnnealing` — `experimental/math/hybridsimulatedannealingfunctors.hpp`
- `TenorOptionletSmileSection` — `experimental/basismodels/tenoroptionletvts.hpp`
- `TenorSwaptionSmileSection` — `experimental/basismodels/tenorswaptionvts.hpp`
- `Topology` — `experimental/math/particleswarmoptimization.hpp`
- `TrivialInertia` — `experimental/math/particleswarmoptimization.hpp`
- `TwoParameterCorrelation` — `experimental/basismodels/tenoroptionletvts.hpp`
- `ValuationData` — `experimental/mcbasket/adaptedpathpayoff.hpp`
- `VannaVolga` — `experimental/barrieroption/vannavolgainterpolation.hpp`
- `VectorIntegrator` — `experimental/math/multidimquadrature.hpp`
- `VolatilityCube` — `experimental/volatility/volcube.hpp`
- `YYGenericCPI` — `experimental/inflation/genericindexes.hpp`
- `YoYInflationVolatilityTraits` — `experimental/inflation/piecewiseyoyoptionletvolatility.hpp`
- `Ziggurat` — `experimental/math/zigguratrng.hpp`
- `identity` — `experimental/shortrate/generalizedhullwhite.hpp`
- `integrand` — `experimental/volatility/noarbsabr.hpp`
- `multiplyV` — `experimental/math/latentmodel.hpp`
- `p_integrand` — `experimental/volatility/noarbsabr.hpp`
- `param_type` — `experimental/math/levyflightdistribution.hpp`
- `simEvent` — `experimental/credit/randomdefaultlatentmodel.hpp`

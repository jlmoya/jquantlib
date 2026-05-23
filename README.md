# JQuantLib

> A 100%-Java port of [QuantLib](https://www.quantlib.org/) — the de-facto open-source library for quantitative finance — being systematically rebuilt from C++ v1.42.1 with bit-exact precision guarantees.

[![Tag](https://img.shields.io/badge/tag-jquantlib--phase2--l6--complete-brightgreen)](#migration-status)
[![Tests](https://img.shields.io/badge/tests-3574%20%2F%200%20fail-success)](#migration-status)
[![JDK](https://img.shields.io/badge/JDK-25%20LTS-orange)](#migration-status)
[![Scanner](https://img.shields.io/badge/scanner_WIP-0-success)](#migration-status)
[![C%2B%2B%20pin](https://img.shields.io/badge/C%2B%2B%20pin-v1.42.1-informational)](#ground-truth)
[![License](https://img.shields.io/badge/license-BSD-green)](#license)

---

## What is JQuantLib?

JQuantLib provides Java developers and quants with the mathematical, statistical, and modelling toolset needed to value equities, options, futures, swaps, fixed-income instruments, and a wide range of derivatives. It mirrors QuantLib's C++ API as closely as Java idioms allow, offering a smooth bridge for organizations running JVM-based pricing infrastructure.

The original Java port began ~2007, stalled in 2021, and is now being **revived as a multi-phase, precision-first migration from C++ QuantLib v1.42.1**.

## Project posture (2026-04 → present)

This is not a maintenance branch. It is a **systematic, full-fidelity port** with five operating principles:

| Principle | What it means in practice |
|-----------|---------------------------|
| **C++ is source of truth** | Where Java diverges from QuantLib v1.42.1 — signatures, implementations, constants, behavior — the Java code is updated to match C++. The pre-existing Java API is starting material, not a design to preserve. |
| **Cross-validation before commit** | Every functional change is backed by a C++ "probe" (a small program linked against the pinned QuantLib submodule) that emits reference values to JSON. Java tests load those JSONs and assert against them. No expected value is invented inline. |
| **Tier-stratified tolerances** | Comparisons land in one of three tiers — **EXACT** (bit-identical via `Double.doubleToRawLongBits`), **TIGHT** (`abs 1e-14 + rel 1e-12`), **LOOSE** (`abs 1e-8 + rel 1e-8`). Per-test exceptions require an inline written justification. |
| **Bulletproof, not fast** | Every commit compiles and passes the full suite. One stub fix = one commit. No `--no-verify`, no skipped hooks. Mid-port architectural divergence becomes a separate `align(...)` commit, never bundled. |
| **Direct-to-main** | Solo single-owner repo; no PR overhead. Each phase ends with a signed git tag (`jquantlib-phase<N>-complete`) and a completion document under `docs/migration/`. |

## Migration status

| Phase | Tag | What landed | Tests | Date |
|-------|-----|-------------|-------|------|
| 1 | `jquantlib-phase1-complete` | Finished 80 started stubs across 61 existing packages | — | 2026-04-24 |
| 2a | `jquantlib-phase2a-complete` | MINPACK + LM, Heston QuadraticExponential, 56 numerical-suspect markers swept | 626/0/0/25 | 2026-04-24 |
| 2b | `jquantlib-phase2b-complete` | One-factor model Parameter-ref refactor (Vasicek, HullWhite, BK, CIR), Simplex 1D, SABR sentinel fix | 632/0/0/22 | 2026-04-25 |
| 2c | `jquantlib-phase2c-complete` | Chi-squared CDF + CIR.discountBondOption, SABR α-default, HullWhite latent items, BK tree-pricing | 649/0/0/22 | 2026-04-25 |
| 2d | `jquantlib-phase2d-complete` | CapHelper via BlackCalibrationHelper, Heston NCCS scheme, SABR Halton via XABR | 649/0/0/22 | 2026-04-26 |
| 2e | `jquantlib-phase2e-complete` | G2 model body (**scanner WIP: 1 → 0**), BlackCapFloorEngine, full Swaption infra (Black/Tree/Discretized), SwaptionHelper | 656/0/0/22 | 2026-04-26 |
| 2f | `jquantlib-phase2f-complete` | Cap engines (Bachelier branch), G2.swaption integral, Heston BroadieKaya + NCCS tightening | 675/0/0/22 | 2026-04-26 |
| 2g | `jquantlib-phase2g-complete` | Brent.solveImpl pre-loop alignment → **+19 LOOSE→TIGHT promotions**; FdHullWhite/FdG2 deferred to 2h on framework gap | 675/0/0/22 | 2026-04-26 |
| 2h | `jquantlib-phase2h-complete` | Full `Fdm*` finite-difference framework (~3826 LOC, 30 classes); FdHullWhiteSwaptionEngine + **FdG2 TIGHT 8.4e-15 (bit-exact)** + bonus BicubicSpline Address-mapping fix | 677/0/0/22 | 2026-04-27 |
| 2i | `jquantlib-phase2i-complete` | **CORE-MATH correctly-rounded `JQuantMath.exp`**; FdHullWhite tier LOOSE → `within(3e-12)`; A3 finding (Apple libm not always correctly-rounded) | 684/0/0/22 | 2026-04-28 |
| 2i.5 | `jquantlib-phase2i.5-complete` | `JQuantMath.cos` + `.sin` via Dint64 (u128 emulation); NCCS A19 partial — Math.log floor identified | 687/0/0/22 | 2026-04-28 |
| 2i.6 | `jquantlib-phase2i.6-complete` | `JQuantMath.log` (CORE-MATH, 1203-case bit-exact first-shot); NCCS A19 re-fire pinned `gammaFunction_.logValue` Lanczos as actual residual | 688/0/0/22 | 2026-04-30 |
| 2j | `jquantlib-phase2j-complete` | Gaussian1D family partial (P2J-10 trim): full model layer + 3 of 5 engines (Standard SwaptionEngine LOOSE, CapFloorEngine LOOSE, Jamshidian TIGHT) + 3 MF prereqs; Nonstandard + FloatFloat + MarkovFunctional deferred (4× A16 fires) | 792/0/0/22 | 2026-05-02 |
| 2j.5 | `jquantlib-phase2j.5-complete` | Full Gaussian1D family completion: 5/5 engines + MarkovFunctional (split tier) + 4 niche-engine instruments + GaussHermiteIntegration family + AtmSmileSection + 11 align-fix commits | 801/0/0/22 | 2026-05-02 |
| 2k | `jquantlib-phase2k-complete` | Gaussian1D feature completion: SabrInterpolatedSmileSection + BasketGeneratingEngine + TqrEigenDecomposition lifted + MF.CustomSmileFactory; all 4 MarkovFunctional smile branches operational | 809/0/0/22 | 2026-05-02 |
| 2l | `jquantlib-phase2l-complete` | Fdm framework completeness: BiCGStab + GMRES + 6 new schemes (9 total) + Bermudan/American/dividend step conditions + vanillaComposite wiring + new `math.ode` package | 812/0/0/22 | 2026-05-02 |
| 2m | `jquantlib-phase2m-complete` | 3 Fdm-dependent vanilla engines (FdBlackScholesVanilla + FdHestonHullWhiteVanilla + FdSabrVanilla) + AndreasenHuge LocalVol family (3 classes). 8 commits across 4 parallel worktrees including 2 align prereqs. | 816/0/0/22 | 2026-05-08 |
| 2n | `jquantlib-phase2n-complete` | `JQuantMath.pow` correctly-rounded via CORE-MATH cr_pow — 100% bit-exact (2,763/2,763 oracle cases). Qint64 (256-bit u128-pair) infra. 8 commits including 29-file/57-site Math.pow → JQuantMath.pow swap. JQuantMath now covers all 5 transcendentals: exp/log/sin/cos/pow. | 818/0/0/22 | 2026-05-08 |
| 2o | `jquantlib-phase2o-complete` | HestonModel rho constraint aligned to C++ v1.42.1 (`BoundaryConstraint(-1,1)`); BlackFormula stdDevDerivative shifted-strike guards relaxed; SABRInterpolation.op + XABRInterpolationImpl pre-apply shift; SabrInterpolatedSmileSection Scenario C activated (21 cases LOOSE). | 818/0/0/22 | 2026-05-08 |
| 2p | `jquantlib-phase2p-complete` | First subsystem-port phase — Inflation zero family. New package `org.jquantlib.termstructures.inflation` (InflationTraits + Interpolated/PiecewiseZeroInflationCurve + ZeroCouponInflationSwapHelper). New cashflow classes + ZeroCouponInflationSwap. Plus 2 align prereqs. Java inflation coverage 30% → 60%. | 822/0/0/22 | 2026-05-08 |
| 2q | `jquantlib-phase2q-complete` | Inflation YoY + CPI + Seasonality + CapFlooredYoY closeout. 10 commits across 3 worktrees. Java inflation surface coverage 60% → 85%. | 832/0/0/22 | 2026-05-08 |
| 2r | `jquantlib-phase2r-complete` | Inflation 100% core surface coverage. YoYOptionletVolatilitySurface family + 4 inflation cap/floor instruments + InflationCapFloorEngines (Black/UnitDisplaced/Bachelier) + 3 YoY optionlet pricers + FDNewtonSafe solver + YoYInflationIndex.fixing past-path align. 8 commits across 3 worktrees. Java inflation core surface 85% → 100%. | 853/0/0/22 | 2026-05-08 |
| 2s | `jquantlib-phase2s-complete` | Experimental inflation closeout — Polynomial2DSpline + GenericIndexes + KInterpolated/Piecewise YoYOptionletVol + YoY Optionlet Stripper family + CPI/YoY CapFloorTermPriceSurface + InterpolatingCPICapFloorEngine. Inflation subsystem fully complete. | 888/0/0/22 | 2026-05-08 |
| 2t | `jquantlib-phase2t-complete` | First test-suite phase under rigor directive — inflation.cpp ported (2,323 LOC → 31 @Test methods). InflationCommonVars test fixture + inflationPeriod sub-annual bug fix. | 919/0/0/40 | 2026-05-08 |
| 2u | `jquantlib-phase2u-complete` | Inflation test-suite expansion (44%→86%): 4 cpp files ported + 5 L0 Phase 2x aligns + Track F body fills with 4 infrastructure aligns. 11 commits across 6 worktrees. | 933/0/0/37 | 2026-05-08 |
| 2v | `jquantlib-phase2v-complete` | Inflation 100% (production + test-suite). CPIBond + 12 missing CPI index classes + GlobalBootstrap + lazy-baseDate ctor + YYIIS discount-curve overload + 2 remaining test-suite ports. 7 commits + 1 manual recovery. | 950/0/0/39 | 2026-05-08 |
| 2x | `jquantlib-phase2x-complete` | Small infrastructure aligns + WeakReferenceObservable batched notification. mvn test wall-clock 30+ min → 59.5s (32x speedup). | 951/0/0/38 | 2026-05-08 |
| 3a | `jquantlib-phase3a-complete` | First Phase-3 subsystem (greenfield credit termstructures): 3 abstract bases + FlatHazardRate + 3 interpolated curves + PiecewiseDefaultCurve + ProbabilityTraits + DefaultProbabilityHelper non-CDS + defaultprobabilitycurves.cpp test port. New package `org.jquantlib.termstructures.credit`. | 973/0/0/45 | 2026-05-08 |
| 3b | `jquantlib-phase3b-complete` | CreditDefaultSwap instrument + Protection/Claim/FaceValueClaim + MidPointCdsEngine + 3 CDS helpers + creditdefaultswap.cpp test skeleton + 4 of 7 Phase 3a CDS-deferred tests un-ignored. | 1004/0/0/51 | 2026-05-08 |
| 3c | `jquantlib-phase3c-complete` | IntegralCdsEngine + MakeCreditDefaultSwap + DateGeneration.CDS + Schedule rule + WeekendsOnly + IterativeBootstrap refinement. 7 of 8 deferred tests un-ignored. Drive-by fixes: Settings.TODAYS_PAYMENTS, InterpolatedDiscountCurve 2 bugs. | 1018/0/0/44 | 2026-05-09 |
| 3d | `jquantlib-phase3d-complete` | IsdaCdsEngine (480 LOC sophisticated) + IterativeBootstrap config + Actual360(true) + 3 of 6 deferred un-ignores. Credit production 100% v1.42.1. | 1024/0/0/41 | 2026-05-09 |
| 3e | `jquantlib-phase3e-complete` | DONE_WITH_CONCERNS — 4 pre-existing PiecewiseYieldCurve bugs fixed + 3 Markit test bodies fully ported (461 LOC). Tests stay @Ignore'd pending Phase 3f. | 1024/0/0/41 | 2026-05-09 |
| 3f | `jquantlib-phase3f-complete` | Array(double[]) copy-vs-reference fix (Path A, 73 LOC). testIsdaEngine bootstrap drift improved 100× (~1% → 1.4e-4). | 1024/0/0/41 | 2026-05-09 |
| 3g | `jquantlib-phase3g-complete` | Discount.maxValueAfter ungated negative-rates fix (key finding: bug was Discount traits, NOT IsdaCdsEngine). 2 EUR Markit tests un-ignored + passing. | 1062/0/0/38 | 2026-05-09 |
| 3h | `jquantlib-phase3h-complete` | First marketmodels (LMM) slice — Track A (Utilities + EvolutionDescription + CurveState + 3 curvestates + 4 drift calculators) + Track B (3 correlations + MTBrownianGenerator + AccountingEngine + MarketModelDiscounter + MultiProduct + Evolver align). MersenneTwisterUniformRng latent unsigned-long bug fixed. New packages: `org.jquantlib.model.marketmodels.*`. ~3,600 LOC C++ ported. 7 commits across 2 parallel worktrees. | 1087/0/0/38 | 2026-05-09 |
| 3i–5e.5b | `jquantlib-phase5e5b-CFC-c-checkpoint` | **Rolled-up retro** for the 545-commit gap — see [`docs/migration/phase3i-through-5e5b-rolledup-completion.md`](docs/migration/phase3i-through-5e5b-rolledup-completion.md). Covers Phase 3i+ marketmodels evolvers/products/callability, Phase 4a-4o.5 `ql/experimental/` ports (~63K LOC C++), Phase 5a-5e.5b `test-suite/` ports (~79K LOC C++ scope). Per-sub-phase completion docs slipped Phase 3i+; resumes from 5e.5b-CFC-c onward. | 2959/0/0/598 | 2026-05-14 |
| 5e.5b-CFC-c | `jquantlib-phase5e5b-CFC-c-checkpoint` | `align(time.Schedule)` post-BDC dedup fix in Backward/Forward loops (mirrors C++ `schedule.cpp:229-233 / :326-330`). Root-caused via probe-instrumentation diff after BlackON cap/floor body-fills surfaced 6.7e-7 drift. 20-LOC fix unblocked 2 tests; no regression across 2957 other tests. New BlackON diagnostic probe added. | 2959/0/0/598 | 2026-05-14 |
| 5e.5b-CFC-d | `jquantlib-phase5e5b-CFC-d-checkpoint` | OvernightLeg structural body-fills + Schedule characterization tests + `align(time.calendars)` Juneteenth port to GovernmentBondImpl + new `Market.SOFR` + `SofrImpl`. Surfaced latent Juneteenth bug — helper existed but never wired into GovernmentBond. Plus `overnight_leg_caps_floors` probe with cross-validated NPV. testOvernightLegWithCapsAndFloors @Ignore'd pending Cubic-extrapolation root cause. | 2961/0/0/594 | 2026-05-14 |
| **5e.5b-CFC-d-final** | **`jquantlib-phase5e5b-CFC-d-final`** | **Continuation of CFC-d: Juneteenth wired into all 3 main US calendars (Settlement/NYSE/GovernmentBond). BlackFormula forward-derivative + RS/Chambers ports (5 new tests). AmortizingFixedRateBond body-fill (1). FixedRateBond/FixedRateLeg arbitrary-schedule support (1). InterpolatedZeroCurve flat-forward extrapolation fix closed the residual 2.7e-7 drift; testOvernightLegWithCapsAndFloors now PASSES (1). Surefire JVM heap 2g eliminated NoClassDefFoundError flakiness. Plus body-fills: MultipleResetsCouponsTest (2), MultipleResetsSwapTest (3), ZeroCouponSwapTest (6).** | **2961/0/0/575** | **2026-05-14** |
| **CFC-d-181 → 321 sweep** | (untagged — on `main`) | **Massive `@Ignore`-clearing sweep across ~180 commits via ~50 parallel background agents. Foundational align fixes: Period.eq/le/ge/neq strict-unit comparison (CFC-d-318); Distribution constant-dx invariant (CFC-d-319); SinCosKernel sin(±Inf) → NaN parity (CFC-d-295); InflationCapFloor cached values refreshed under A3 carve-out (CFC-d-321). Production landings: MCLookback, MCEuropeanGjrGarch, GjrGarchModel/Process/AnalyticEngine, VarianceSwap/MC/Replicating, DiscountingPerpetualFutures, FlatHazardRate, Brlcdi/Business252, StochasticCollocationInvCDF, AdaptiveRungeKutta 1D/Complex, Concentrating1dMesher, TridiagonalOperator SOR, SVD wide-matrix, IncrementalStatistics Welford, FdBlackScholesVanillaEngine localVol, LocalVolSurface Dupire, ProjectedConstraint, HullWhite.FixedReversion, NoArbSabrModel + D0Interpolator + absorption tables, VannaVolgaBarrier + DoubleBarrier engines, FdExtOUJumpVanilla + FdSimpleExtOUStorage, VanillaStorageOption, LFM Sobol LDS, FFTVarianceGamma, TreeVanillaSwap, Swaption(OIS), BlackSwaptionEngine OIS, CappedFlooredCmsCoupon, CEVRNDCalculator delta>=2, MCEuropeanHeston SLV, SabrSwaptionVolatilityCube (919 LOC), HestonStochasticLocalVolProcess.evolve, FdmHestonExpressCondition, DigitalOption Greeks bumping, CatBondAdditional, Asian Average-Strike engines, Heston calibration, SABR FlochKennedy, XABR ParameterTransformation, GemanRoncoroniProcess MC, HHHW testCallableEquityPricing, HHW testHestonHullWhiteCalibration, CmsNormal.testFairRate, AssetSwap.specializedBond, CallableFixedRateBond ex-coupon ctor, FdHestonVanilla leverageFct, HestonSLV LocalVol. **End state: all 138 initial `@Ignore` annotations cleared.** | 3010/0/0/1 | 2026-05-20 |
| **Path A — Phase 1 closure (D5)** | (untagged — on `d5-D-marketmodel` worktree) | **Post-certification-audit D5-RED → essentially GREEN remediation. 16 of 17 prereq TODOs closed across rounds A1–A6 (~135 commits, ~15,000 LOC of production code, ~200 net @Test methods added). Production-port landings: QdPlus + QdFp American engines (12 schemes), AbcdCalibration + MarketModelTestSetup, CotSwapFromFwdCorrelation, MultiCompositeQuote + CompositeInstrument, calendar greenfield (Russia/Israel/Mexico/NZ/SouthKorea/Denmark/China.SSE+IB), daycounter ports (Thirty365/Actual366/Actual36525/Actual364, ECB+ASX helpers, ActualActual ISMA_Impl, DateParser strftime), Array(size,start,step)+resize, FFTVanillaEngine, FdBlackScholesShoutEngine, FdBlackScholesBarrierEngine + escrowed dividend model, NelsonSiegel (weights,opt,l2) ctor, InterpolatedPiecewiseForwardSpreadedTermStructure, BondHelper bond-helper fitting, ExpSinhIntegral + FilonIntegral + TwoDimensionalIntegral + TanhSinhIntegral, GenericLongstaffSchwartzRegression + ProxyGreekEngine, ConstantLossModel, GlobalBootstrap, BjerksundStenslandApproximationEngine v1.42.1 rewrite, AnalyticEuropeanEngine (process,discountCurve) ctor + dispatch. **6 latent production bugs found & fixed via faithful porting:** (a) AndreasenHugeVolatilityInterpl put/call payoff swap — latent since 2007 port (commit 958afc29); (b) TanhSinhIntegral missing level-0 integer-multiple nodes (commit 844d75ed); (c) MethodOfLinesScheme missing bcSet.setTime() call (commit 214f7b8a); (d) PathwiseVegasOuterAccountingEngine Jacobian transpose (commit 462b8456); (e) QdPlusAmericanEngine xMax NaN-at-spot-0 (commit b8f4d88a); (f) Money.greater/greaterEqual infinite-recursion (commit 35510f03). Residual ~75–80 still-genuinely-missing-by-name tests catalogued in [`phase1-closure-remaining.md`](docs/migration/phase1-closure-remaining.md). | 3213 methods / 0 fail (in-progress) | 2026-05-20 |
| Phase 1 genuine closure (JDK 25) | `jquantlib-phase1-complete-jdk25` (`0e72b77a`) | Closed P1.1/P1.2/P1.3/P1.4 carve-outs on JDK 25 LTS base. MultiCurve + LocalBootstrap + GlobalBootstrap + SpreadBootstrapTraits + InterpolatedSpreadDiscountCurve + PiecewiseSpreadYieldCurve + ConvexMonotone helper hierarchy + AverageBMALeg + multiple marketmodel test ports. **Missing-by-name vs C++ v1.42.1 test-suite: 0.** 20+ latent production bugs surfaced and fixed. Memory leak hypothesis (#565) resolved as external corrupted node_modules. | 3270/0/0/24 | 2026-05-22 |
| JDK 25 modernization W1-W4 | `jquantlib-jdk25-modernized-w1-w4` (`683bf14e`) | JDK 25 LTS upgrade (source/target/release 25, surefire 3.5.2, junit 4.13.2, slf4j 2.0.16) + 4-wave syntax modernization. W1 cosmetic (312 var + 1327 diamond / 367 files); W2 pattern matching + switch expressions (74 instanceof + 52 switch + 5 switch-with-patterns / 105 files, -361 LOC); W3 records for DTOs (15 records, -385 LOC); W4 sealed type hierarchies (Exercise/Payoff/Dividend + nits). Each wave: implementer → spec-compliance reviewer → code-quality reviewer → fix-up → FF. W5 virtual threads + W6 Vector API deferred (no existing Executor / Vector API still incubator on JDK 25). | 3270/0/0/24 | 2026-05-22 |
| Phase 2 forward closure — L1 | `jquantlib-phase2-l1-complete` (`899f60e7`) | math/utilities/time/patterns layer. 5 clusters L1-A..E: time + simple math pilot (6 calendars + OneDayCounter + BernsteinPolynomial + PascalTriangle), 13 copulas + Copula SAM (probe-validated), 12 optimization + RNGs (bit-exact MT(42) probes), 25 distributions/integrals/stats/matrixutils/ode, math root + interpolation factories. Bonus production fix: CubicInterpolation Akima derivative (was throwing "not implemented yet"). | ~3370+/0/0 | 2026-05-22 |
| Phase 2 forward closure — L2 | `jquantlib-phase2-l2-complete` (`6f6ecf22`) | termstructures + indexes layer. 4 clusters L2-A..D: 11 indexes (BBSW + Aonia + root), 7 termstructures/yield (CPIBondHelper, FxSwapRateHelper, 4 fitting methods), 6 ZABR family via sealed interface + AtmAdjustedSmileSection, 26 indexes/ibor remainder (multi-currency). RUBCurrency added. | ~3420+/0/0 | 2026-05-22 |
| Phase 2 forward closure — L3 | `jquantlib-phase2-l3-complete` (`3c5c3862`) | instruments + pricingengines layer. 4 clusters L3-A..D: 14 instruments + 9 SKIP (StickyRatchet/YoY/FixedVsFloatingSwap), 7 Italian bonds (BTP/CCTEU/Rendistato), 5 swaption family (G2SwaptionEngine, BlackStyleSwaptionSpec sealed), 34 + 1 SKELETON pricingengines Make-factories + MC engines. Bonus fixes: FellerConstraint replaces broken VolatilityConstraint stub (L4-A); Vasicek.r0() public accessor. | ~3500+/0/0 | 2026-05-22 |
| JQuantLib migration complete | `jquantlib-migration-complete` (`4549eb7f`) | Phase 2 L4 + L5 closure. L4 11 classes (FellerConstraint + Brownian generators + Gaussian1d CachedSwapKey record + historical analysis). L5 mostly triage with aggressive SKIP-with-rationale: RecursiveLossModel + FdOrnsteinUhlenbeckVanillaEngine ported, all others either already-present, v1.42.1-deprecated, or no-Java/no-C++-caller (see [`phase2-skips-audit.md`](docs/migration/phase2-skips-audit.md)). 5 latent production bugs surfaced and fixed during Phase 2. C++ v1.42.1 → Java JDK 25 LTS migration certified. | 3531/0/0/24 | 2026-05-22 |
| Phase 2 SKIPs resolved | `jquantlib-phase2-skips-resolved` (`205f8b4b`) | Per user decision, ported all 13 Category D + E items from the SKIPs audit. D1: CubicInterpolation Harmonic/SplineOM1/SplineOM2 + 4 factories. D2: AbcdInterpolation wrapper. D3: 5 vol-cube classes + SabrSwaptionVolatilityCube hook refactor. D4: HybridSimulatedAnnealing + 16 functors. E1: FdBlackScholesAsianEngine SKELETON → functional. 5 sub-clusters across 5 worktrees. Known deferred SKIP-E1-FOLLOWUP: FdBlackScholesAsianEngine multi-fixing accuracy (~50% drift vs Levy 1997, single-fixing-vs-vanilla agreement proves rollback+mesher correct). | 3569/0/0/24 | 2026-05-23 |
| **Phase 2 L6 (test-suite parity)** | **`jquantlib-phase2-l6-test-suite-parity-complete`** (`1ffd209f`) | **Test-suite parity round. L6-A: stats + riskstats ports (5 @Test). L6-B: InflationCapFlooredCouponTest (2 tests, 840 sub-cases). L6-C: 4 marketmodel SMM tests confirmed already-ported. L6-D: commodityunitofmeasure + cdsoption (2 tests). align(math.statistics) bonus: 3 production-side bugs in GenericRiskStatistics killed (regret Expression→ComposedFunction, averageShortfall predicate Bind1st→Bind2nd, shortfall Clipped→lambda) + GaussianStatsHolder for GenericGaussianStatistics<StatsHolder> typedef parity. 3 latent production-bug fixes from L6 alone, 8 in total this Phase 2 session. Plus aggregator distributionManagement fix (mirrors jquantlib-parent for `mvn deploy` from repo root).** | **3574/0/0/24** | **2026-05-23** |

**Current tip on `main`:** `jquantlib-phase2-l6-test-suite-parity-complete` @ `1ffd209f`. **Suite:** 3574 tests / 0 failures / 0 errors / 24 skips, BUILD SUCCESS (18:38 wall on JDK 25 LTS). **Scanner WIP-stub count:** `0`. **JDK:** 25 LTS (source/target/release 25). **Operating mode:** autonomous (controller decides phase scope/sequencing per 2026-05-02 directive). **SKIPs audit:** [`docs/migration/phase2-skips-audit.md`](docs/migration/phase2-skips-audit.md) — Categories A/B/C/F (~105 items) confirmed defensibly left as-is; D/E (13 items) all resolved. **All worktrees + branches cleaned up** (d5-A through d5-E removed local+remote). **Known follow-up:** SKIP-E1-FOLLOWUP (FdBlackScholesAsianEngine multi-fixing accuracy ~50% drift vs Levy 1997 — tracked in `docs/migration/phase2-skips-audit.md`).

**[Phase 1 certification audit — 2026-05-20](docs/migration/phase1-certification-report.md):** D1 YELLOW (suite green 3010/0/0/1 in 17m23s; ~45 slow-test gates pending discussion), D2 / D4 GREEN, D3 / D6 AMBER (now remediated), **D5 RED** (280 missing C++ tests across 49 files — under active remediation). Pre-Phase-2 NO-GO until D5 closes.

> Each phase has a binding **design** doc, an executable **plan** doc, a **progress** log, and a **completion** doc — all under [`docs/migration/`](docs/migration/).

## Architecture of a phase

Every phase follows a uniform shape, refined since Phase 2a:

```
brainstorm → design → plan → execute (subagent-driven) → review → tag → memory
```

### 1. Brainstorm & design
A binding spec (`docs/migration/phase<N>-design.md`) is approved before any code is written. Sections include scope (in/out), approach comparison, worktree topology, tolerance & probe discipline, pause triggers (A1–A19), decision log.

### 2. Plan
A bite-sized, checkbox-tracked task list (`docs/migration/phase<N>-plan.md`) with exact file paths, code snippets, and expected test-count deltas per task. No "TODO" or "TBD" — every step is concrete.

### 3. Execute
Each phase runs across **2–4 git worktrees** (named `jquantlib-<phase>-A`, `-B`, `-C`, ...). A controller dispatches one fresh subagent per task with two-stage review:

1. **Spec compliance review** — does the code match the spec exactly?
2. **Code quality review** — strengths, blockers, and improvements.

Independent worktrees run in parallel; dependent ones serialize.

### 4. Pause triggers
Eighteen-and-counting documented conditions where the controller stops and asks the human. The most consequential have been:

- **A3** *(reference is itself wrong)* — fired in Phase 2i; Apple libm `std::exp` shown not always correctly-rounded; resolution = use CORE-MATH `cr_exp` directly as oracle.
- **A4** *(needs new class outside scoped packages)* — fired in Phase 2e (Swaption was a near-empty stub) and 2g (Fdm framework absent), each cleanly redirected.
- **A13** *(transcendental ULP slack)* — fired in Phase 2f; led to Phase 2i's CORE-MATH transcendental track.
- **A19** *(post-swap tier promotion fails)* — fired in Phase 2i (FdHullWhite — non-transcendental floor) and Phase 2i.5 (NCCS — Math.log floor); each pinpoints the next residual to address.

### 5. Tag + memory
Phase ends with `git tag -a jquantlib-phase<N>-complete`, a completion doc, and a refresh of long-lived project memory.

## Precision track — the JQuantMath story

A recurring discovery from Phase 2f onwards: **the JVM's `java.lang.Math` transcendentals carry up to ~1 ULP of slack relative to a correctly-rounded reference**. That slack compounds through Fourier inversions, ADI schemes, and root-finders, and limits how tight an EXACT-tier comparison can ever be.

Phase 2i and 2i.5 attack the floor head-on by porting **CORE-MATH** — an academic correctly-rounded transcendental library (BSD/MIT, Sibidanov et al., Inria) — into a new package `org.jquantlib.math.transcendental.JQuantMath`.

| Primitive | Status | Source | Note |
|-----------|--------|--------|------|
| `JQuantMath.exp` | ✅ Phase 2i | CORE-MATH `src/binary64/exp/exp.c` | Bit-exact across 508 probe cases including all 51 hard-cases DB entries |
| `JQuantMath.cos` | ✅ Phase 2i.5 | CORE-MATH `src/binary64/cos/cos.c` | Bit-exact across 1,381 probe cases incl. Payne-Hanek stress through 2^50·π |
| `JQuantMath.sin` | ✅ Phase 2i.5 | CORE-MATH `src/binary64/sin/sin.c` | Bit-exact across 1,376 probe cases |
| `JQuantMath.log` | ✅ Phase 2i.6 | CORE-MATH `src/binary64/log/log.c` | Bit-exact across 1,203 probe cases first-shot. Sister `long[4]` dint64-style helpers (log_dint.h is bit-incompatible with sin/cos's dint.h) |
| `JQuantMath.pow` | ✅ Phase 2n | CORE-MATH `src/binary64/pow/pow.c` | Bit-exact across 2,763 probe cases. Stage-1 doubles + Stage-2 Dint64 Ziv chain (Stage-3 Qint64 deferred — no oracle case demands it). Production-wired at 29 files / 57 sites. |
| `JQuantMath.lgamma` | ❌ Blocked | No correctly-rounded source available | CORE-MATH does not have lgamma; msun/glibc options either non-correctly-rounded or license-incompatible. NCCS EXACT remains blocked. |

Two supporting types in `org.jquantlib.math.transcendental` emulate the C source's extended-precision integers:

- **`Dint64`** — emulates `unsigned __int128` (`dint64_t` in CORE-MATH source). Package-private; validated across 100 probe cases. Used by `LogKernel` (log), `SinCosKernel` (sin+cos), and `PowKernel` (pow stage-2 Ziv chain).
- **`Qint64`** — emulates 256-bit u128-pair arithmetic (`qint64_t`). Package-private; validated across 322 probe cases / 16 ops. Foundation for pow's stage-3 Ziv (deferred — not exercised by current oracle) and any future `lgamma` port.

### Why CORE-MATH and not msun, glibc, or Apple libm?

This was a hard-won lesson:

- **JVM `Math.exp` (HotSpot)** ≈ 1-ULP slack
- **FreeBSD msun `e_exp.c`** ≈ same 1-ULP slack as JVM (Phase 2i pre-pivot BLOCKED finding)
- **Apple libm `std::exp`** (macOS arm64) — *almost* correctly-rounded but not provably so; ~50/2^64 hard cases are still 1 ULP off (Phase 2i A3 finding, verified via 300-bit mpmath)
- **CORE-MATH `cr_exp`** — correctly-rounded by design across all IEEE-754 rounding modes

Probe oracles for transcendentals therefore `#include "coremath/exp.c"` directly and call `cr_exp(x)` — not the platform `<cmath>`.

## Ground truth

| Resource | Detail |
|----------|--------|
| Pinned C++ submodule | QuantLib **v1.42.1** @ `099987f0ca2c11c505dc4348cdb9ce01a598e1e5` |
| Migration harness | [`migration-harness/`](migration-harness/) — CMake-built C++17 probes linked against the submodule |
| Probe references | [`migration-harness/references/`](migration-harness/references/) — JSON files keyed by `test_group`, consumed via `ReferenceReader` |
| Java module under port | [`jquantlib/`](jquantlib/) (Maven; Java 11; JUnit 4) |
| Stub scanner | [`tools/stub-scanner/scan_stubs.py`](tools/stub-scanner/) — emits `docs/migration/stub-inventory.json` and `worklist.md` |
| Phase docs | [`docs/migration/`](docs/migration/) — design, plan, progress, completion per phase |

## Repository structure

```
jquantlib/                              ← this repo root
├── jquantlib/                          ← Maven module under active port
│   ├── src/main/java/org/jquantlib/   ← production code
│   │   └── math/transcendental/       ← Phase 2i-2n (JQuantMath, ExpKernel, LogKernel, SinCosKernel, PowKernel, Dint64, Qint64)
│   └── src/test/java/                 ← JUnit 4 tests cross-validated against probes
├── jquantlib-helpers/                  ← helper classes
├── jquantlib-contrib/                  ← third-party contributions
├── jquantlib-samples/                  ← sample applications
│
├── migration-harness/                  ← C++ ground-truth scaffolding
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   ├── quantlib/                  ← pinned QuantLib v1.42.1 submodule
│   │   └── probes/                    ← per-feature C++ probes emitting JSON
│   │       └── transcendental/
│   │           └── coremath/          ← vendored CORE-MATH algorithm sources
│   ├── references/                     ← generated reference JSONs
│   ├── setup.sh / generate-references.sh / verify-harness.sh
│   └── README.md
│
├── docs/migration/                     ← phase design, plan, progress, completion docs
├── tools/stub-scanner/                 ← Python scanner for in-progress stubs
└── README.md                           ← (this file)
```

## Quick start

### Build & test

```bash
# Test the Java module (run from the inner module — not the root)
cd jquantlib && mvn test
# Expected: Tests run: 818, Failures: 0, Errors: 0, Skipped: 22

# Snapshot the stub scanner
python3 tools/stub-scanner/scan_stubs.py
# Expected: 0 stubs

# Verify the C++ harness is functional
./migration-harness/verify-harness.sh
```

### Regenerate probe references

```bash
# (re-)build the C++ harness and emit all references/*.json
bash migration-harness/setup.sh
bash migration-harness/generate-references.sh
```

### Run a sample

```bash
mvn clean verify install
# Sample apps live under jquantlib-samples/
```

## What the next phase looks like

**Phase 2n ✅ complete** — JQuantMath.pow correctly-rounded (100% bit-exact 2,763/2,763 vs CORE-MATH cr_pow); Qint64 256-bit infra; 57-site production swap. JQuantMath now covers all 5 transcendentals: exp/log/sin/cos/pow. The next priorities, in autonomous-mode dispatch order:

1. **HestonModel rho constraint align** — `PositiveConstraint` → `BoundaryConstraint(-1,1)` to match C++ v1.42.1 (Phase 2m Track B carry-forward)
2. **AndreasenHuge calibration** — Phase 2m Track D ported the surfaces but not the calibration loop
3. **SABRInterpolation shifted-strike support** — Phase 2k Track A Scenario C unblocker
4. **`U128.java` shared util** — consolidate u128 helpers across `Dint64`, `Qint64`, `LogKernel`, `PowKernel`, `GaussianQuadrature.TqrEigen`
5. **Douglas ADI / FdmAffineModelTermStructure** — FdHullWhite real floor (Phase 2i WI-2 B-1 A19)
6. **PowKernel stage-3 (Qint64 chain + exact_pow)** — only if a future test surfaces a hard-rounding case stage-2 fails
7. **`JQuantMath.lgamma`** — still blocked; CORE-MATH has no lgamma. NCCS EXACT path still gated on this.
8. **C++ test-suite Java equivalents** — every C++ engine test deserves a Java equivalent (~150-200K LOC of tests)
9. **Phase 3+ subsystems** — `experimental/`, `models/marketmodels/`, `termstructures/credit/`, `inflation/`

**Binding exit criterion:** every C++ class, function, header, and test from QuantLib v1.42.1 has a faithful Java equivalent. Realistic path to "done" is ~50-100+ phases.

## Documentation links

- [Original JQuantLib homepage](http://www.jquantlib.org)
- [Developer's guide](http://www.jquantlib.org/en/latest/developersguide.html)
- [QuantLib (C++ upstream)](https://www.quantlib.org/)
- [CORE-MATH project](https://core-math.gitlabpages.inria.fr/)

## Modules

| Module | Role |
|--------|------|
| `jquantlib-parent` | Root parent POM + aggregator (lives at the repo root) |
| `jquantlib` | Main module — actively being ported (mirrors QuantLib/C++) |
| `jquantlib-helpers` | Helper classes |
| `jquantlib-contrib` | Third-party contributions |
| `jquantlib-samples` | Sample code |

## Contributing

This repository is currently a single-owner, direct-to-main migration project. Outside contributions are welcomed via issues, but the operational model assumes one engineer driving the port through subagent-assisted pipelines. If you'd like to help with a specific package or audit a phase, open an issue first to coordinate.

## License

JQuantLib is released under the BSD License — see [LICENSE.TXT](http://www.jquantlib.org/index.php/LICENSE.TXT). Vendored CORE-MATH source files retain their original MIT license headers under `migration-harness/cpp/probes/transcendental/coremath/`.

## Acknowledgements

- The [QuantLib](https://www.quantlib.org/) team — foundational C++ codebase and ongoing reference.
- The [CORE-MATH](https://core-math.gitlabpages.inria.fr/) project (Sibidanov et al., Inria) — provably correctly-rounded transcendental algorithms.
- The original JQuantLib contributors (~2007–2021) whose work forms the starting baseline.

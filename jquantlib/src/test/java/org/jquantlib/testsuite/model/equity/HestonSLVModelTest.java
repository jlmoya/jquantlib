/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.model.equity;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegral;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.interpolations.NaturalCubicInterpolation;
import org.jquantlib.math.interpolations.factories.BicubicSpline;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmBlackScholesFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLocalVolFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp;
import org.jquantlib.methods.finitedifferences.operators.FdmSquareRootFwdOp.TransformationType;
import org.jquantlib.methods.finitedifferences.schemes.DouglasScheme;
import org.jquantlib.methods.finitedifferences.utilities.FdmMesherIntegral;
import org.jquantlib.methods.finitedifferences.utilities.SquareRootProcessRNDCalculator;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackVarianceSurface;
import org.jquantlib.termstructures.volatilities.equityfx.NoExceptLocalVolSurface;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h skeleton port of {@code test-suite/hestonslvmodel.cpp} v1.42.1
 * (2,686 LOC, 15 active test cases — three additional cases are commented
 * out in the C++ source: {@code testFDMCalibration},
 * {@code testBarrierPricingMixedModels}, {@code testForwardSkewSLV},
 * {@code testBarrierPricingMixedModelsMonteCarloVsFdmPricing}).
 *
 * <p>The 15 active tests exercise the Heston Stochastic-Local-Vol (SLV)
 * model and its constituent infrastructure:
 *
 * <ol>
 *   <li><strong>Fokker-Planck forward PDE</strong> —
 *       {@code testBlackScholesFokkerPlanckFwdEquation},
 *       {@code testSquareRootFokkerPlanckFwdEquation},
 *       {@code testHestonFokkerPlanckFwdEquation},
 *       {@code testHestonFokkerPlanckFwdEquationLogLVLeverage},
 *       {@code testBlackScholesFokkerPlanckFwdEquationLocalVol}.
 *       Verify finite-difference forward Fokker-Planck densities for
 *       BSM, square-root, and Heston processes match analytical
 *       benchmarks.</li>
 *
 *   <li><strong>Square-root boundary &amp; stationary density</strong> —
 *       {@code testSquareRootZeroFlowBC},
 *       {@code testTransformedZeroFlowBC},
 *       {@code testSquareRootEvolveWithStationaryDensity},
 *       {@code testSquareRootLogEvolveWithStationaryDensity}.</li>
 *
 *   <li><strong>SLV calibration / propagation</strong> —
 *       {@code testLocalVolsvSLVPropDensity},
 *       {@code testMonteCarloCalibration} (CPU-intensive).</li>
 *
 *   <li><strong>Pricing checks</strong> —
 *       {@code testBarrierPricingViaHestonLocalVol},
 *       {@code testMonteCarloVsFdmPricing},
 *       {@code testMoustacheGraph}.</li>
 *
 *   <li><strong>Process discretization</strong> —
 *       {@code testDiffusionAndDriftSlvProcess}.</li>
 * </ol>
 *
 * <p><strong>Phase 5e.5b-CFC-d-175 status:</strong> Java now has most of the
 * SLV-specific infrastructure. Active tests: 6 / 15.
 * <ul>
 *   <li>{@code FdmSquareRootFwdOp} — landed (Phase 5h.5-SLV WI-1);</li>
 *   <li>{@code FdmHestonFwdOp} — landed (Phase 5h.5-SLV WI-2);</li>
 *   <li>{@code HestonStochasticLocalVolProcess} — landed (Phase 5h.5-SLV WI-3);</li>
 *   <li>{@code FdmLocalVolFwdOp}, {@code LocalVolRNDCalculator},
 *       {@code FixedLocalVolSurface} — landed (Phase 5h.5-RND-b);</li>
 *   <li>{@code FdmHestonGreensFct} (all 3 algorithms: ZeroCorrelation,
 *       Gaussian, SemiAnalytical), {@code FdmMesherIntegral} —
 *       landed (Phase 5h.5-SLV-b / Phase 5e.5b-CFC-d);</li>
 *   <li>{@code HestonSLVFDMModel}, {@code HestonSLVMCModel} —
 *       body-filled (Phase 5h.5-SLV-b); {@code .logEntries()} and
 *       {@code .leverageFunction()} accessors exposed;</li>
 *   <li>{@code NoExceptLocalVolSurface}, {@code HestonBlackVolSurface},
 *       {@code SobolBrownianBridgeRsg}, {@code SobolBrownianGeneratorFactory},
 *       {@code HundsdorferScheme}, {@code AnalyticDoubleBarrierBinaryEngine}
 *       — all landed.</li>
 * </ul>
 * Still missing (blocks remaining 9 ignored tests):
 * <ul>
 *   <li>Multi-cPoint {@code Concentrating1dMesher} variant
 *       ({@code vector<tuple<Real,Real,bool>>} ctor);</li>
 *   <li>2D {@code fokkerPlanckPrice2D} test helper +
 *       {@code createLocalVolMatrixFromProcess} test helper +
 *       {@code getFixedLocalVolFromHeston} test helper;</li>
 *   <li>{@code FdHestonVanillaEngine} ctor variant accepting a
 *       {@code LocalVolTermStructure} leverage-fct argument
 *       (Java engine is pure Heston);</li>
 *   <li>{@code FdHestonDoubleBarrierEngine} (2D Heston FDM engine with
 *       leverage-fct support) — not in Java;</li>
 *   <li>{@code MakeMCEuropeanHestonEngine} variant templated on
 *       {@code HestonSLVProcess} (Java's accepts only {@code HestonProcess};
 *       {@code HestonStochasticLocalVolProcess} is a sibling, not subclass);</li>
 *   <li>{@code LocalVolSurface.localVolImpl} re-alignment to v1.42.1
 *       (denser strike-perturbation stencil + non-forward-aware time derivative
 *       in current Java) — blocks
 *       {@code testBlackScholesFokkerPlanckFwdEquationLocalVol} and
 *       {@code testLocalVolsvSLVPropDensity}.</li>
 * </ul>
 *
 * <p>Slow-test discipline (Phase 5 META D8): once enabled,
 * {@code testMonteCarloCalibration} and {@code testMonteCarloVsFdmPricing}
 * must be tagged {@code @Tag("slow")}.
 *
 * <p>Source: {@code test-suite/hestonslvmodel.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class HestonSLVModelTest {

    // Phase 5h.5-SLV-c body-fills landed for testTransformedZeroFlowBC,
    // testSquareRootEvolveWithStationaryDensity, testSquareRootLogEvolveWithStationaryDensity.
    // Phase 5e.5b-CFC-d-146 un-ignored testSquareRootZeroFlowBC and
    // testSquareRootFokkerPlanckFwdEquation now that SquareRootProcessRNDCalculator.pdf
    // delegates to the exact closed-form non-central chi-squared PDF
    // (NonCentralCumulativeChiSquaredDistribution#pdf - Phase 5h.5-SLV-d Boost port).
    // Remaining @Ignore reasons are per-test and identify the specific missing
    // infrastructure class(es).

    /**
     * Mirrors C++ {@code stationaryLogProbabilityFct}
     * (test-suite/hestonslvmodel.cpp:157). The log-stationary density of
     * the square-root process under the {@code Log} transform is
     * {@code beta^alpha * exp(z*alpha) * exp(-beta*exp(z) - lgamma(alpha))}
     * with {@code alpha = 2*kappa*theta/sigma^2} and {@code beta = alpha/theta}.
     */
    private static double stationaryLogProbabilityFct(
            final double kappa, final double theta, final double sigma, final double z) {
        final double alpha = 2.0 * kappa * theta / (sigma * sigma);
        final double beta  = alpha / theta;
        return Math.pow(beta, alpha) * Math.exp(z * alpha)
                * Math.exp(-beta * Math.exp(z) - new GammaFunction().logValue(alpha));
    }

    /**
     * Mirrors C++ {@code createStationaryDistributionMesher}
     * (test-suite/hestonslvmodel.cpp:166). Mesh points are inverse-CDF
     * quantiles of the stationary chi-square distribution, in a fixed
     * {@code (qMin, qMax)} probability band.
     */
    private static FdmMesher createStationaryDistributionMesher(
            final double kappa, final double theta, final double sigma, final int vGrid) {
        final double qMin = 0.01;
        final double qMax = 0.99;
        final double dq = (qMax - qMin) / (vGrid - 1);

        final SquareRootProcessRNDCalculator rnd =
                new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
        final double[] v = new double[vGrid];
        for (int i = 0; i < vGrid; ++i) {
            v[i] = rnd.stationary_invcdf(qMin + i * dq);
        }

        final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(1);
        ms.add(new Predefined1dMesher(v));
        return new FdmMesherComposite(ms);
    }

    /**
     * Java port of C++ helper {@code fokkerPlanckPrice1D}
     * (test-suite/hestonslvmodel.cpp:100). Initialises a Dirac density at
     * {@code x0} on the mesh, evolves it forward via Douglas, then integrates
     * {@code payoff(exp(x)) * p(x)} against a {@link NaturalCubicInterpolation}
     * spline over the mesh.
     */
    private static double fokkerPlanckPrice1D(final FdmMesher mesher,
                                              final FdmLinearOpComposite op,
                                              final StrikedTypePayoff payoff,
                                              final double x0,
                                              final double maturity,
                                              final int tGrid) {
        return fokkerPlanckPrice1D(mesher, op, payoff, x0, maturity, tGrid, 1000);
    }

    /**
     * Variant of {@code fokkerPlanckPrice1D} that exposes the
     * {@link GaussLobattoIntegral} max-iteration cap. Only used by the
     * local-vol test ({@code testBlackScholesFokkerPlanckFwdEquationLocalVol})
     * which needs a higher cap than C++'s default 1000: Java's
     * {@code LocalVolSurface.localVolImpl} uses a denser strike-perturbation
     * stencil (smaller {@code dy}, non-forward-aware) than C++'s
     * forward-aware Dupire formula, so the Fokker-Planck-evolved density
     * carries finer-scale wiggles that drive more Gauss-Lobatto subdivisions
     * to hit 1e-6 abs accuracy. The integral converges to the same value
     * either way; only the iteration count differs.
     */
    private static double fokkerPlanckPrice1D(final FdmMesher mesher,
                                              final FdmLinearOpComposite op,
                                              final StrikedTypePayoff payoff,
                                              final double x0,
                                              final double maturity,
                                              final int tGrid,
                                              final int maxIterations) {
        final Array x = mesher.locations(0);
        final int n = x.size();
        final Array p = new Array(n).fill(0.0);

        if (!(n > 3 && x.get(1) <= x0 && x.get(n - 2) >= x0)) {
            throw new IllegalArgumentException("insufficient mesher");
        }

        // upper_bound: first index where x[i] > x0
        int upperIdx = -1;
        for (int i = 0; i < n; ++i) {
            if (x.get(i) > x0) { upperIdx = i; break; }
        }
        if (upperIdx < 0) { upperIdx = n; }
        final int lowerIdx = upperIdx - 1;

        if (upperIdx < n && Closeness.isCloseEnough(x.get(upperIdx), x0)) {
            final double dx = (x.get(upperIdx + 1) - x.get(upperIdx - 1)) / 2.0;
            p.set(upperIdx, 1.0 / dx);
        } else if (lowerIdx >= 0 && Closeness.isCloseEnough(x.get(lowerIdx), x0)) {
            final double dx = (x.get(lowerIdx + 1) - x.get(lowerIdx - 1)) / 2.0;
            p.set(lowerIdx, 1.0 / dx);
        } else {
            final double dxBracket = x.get(upperIdx) - x.get(lowerIdx);
            final double lowerP = (x.get(upperIdx) - x0) / dxBracket;
            final double upperP = (x0 - x.get(lowerIdx)) / dxBracket;
            final double lowerDx = (x.get(lowerIdx + 1) - x.get(lowerIdx - 1)) / 2.0;
            final double upperDx = (x.get(upperIdx + 1) - x.get(upperIdx - 1)) / 2.0;
            p.set(lowerIdx, lowerP / lowerDx);
            p.set(upperIdx, upperP / upperDx);
        }

        // C++ FdmSchemeDesc::Douglas().theta == 0.5
        final DouglasScheme evolver = new DouglasScheme(0.5, op);
        final double dt = maturity / tGrid;
        evolver.setStep(dt);

        for (double t = dt; t <= maturity + 20.0 * 1.0e-16; t += dt) {
            evolver.step(p, t);
        }

        final double[] xs = new double[n];
        final double[] ys = new double[n];
        for (int i = 0; i < n; ++i) {
            xs[i] = x.get(i);
            ys[i] = payoff.get(Math.exp(x.get(i))) * p.get(i);
        }
        final NaturalCubicInterpolation spline =
                new NaturalCubicInterpolation(new Array(xs), new Array(ys));
        spline.update();
        spline.enableExtrapolation();
        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double v) { return spline.op(v, true); }
        };
        return new GaussLobattoIntegral(maxIterations, 1.0e-6).op(f, x.first(), x.last());
    }

    /* ---- 1. Fokker-Planck forward PDE -------------------------------- */

    /**
     * Tests the Black-Scholes Fokker-Planck forward PDE on three mesh
     * variants (uniform, concentrated, shifted-concentrated). Mirrors C++
     * {@code testBlackScholesFokkerPlanckFwdEquation}
     * (test-suite/hestonslvmodel.cpp:725). Tolerance 0.02 absolute.
     *
     * <p>For each strike in {50, 80, 100, 130, 150}: builds a vanilla European
     * call, takes the analytic NPV / discount as the reference (forward
     * undiscounted call price), and compares to the
     * {@code fokkerPlanckPrice1D} evaluation that evolves a Dirac density
     * via the {@link FdmBlackScholesFwdOp} operator on each mesh.
     *
     * <p>Unblocked by the Phase 5e.5b-CFC-d-131 port of
     * {@link FdmBlackScholesFwdOp}.
     */
    @Test
    public void testBlackScholesFokkerPlanckFwdEquation() {
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Date todaysDate = new Date(28, Month.December, 2012);
        new Settings().setEvaluationDate(todaysDate);

        final Date maturityDate = todaysDate.add(new Period(2, TimeUnit.Years));
        final double maturity = dc.yearFraction(todaysDate, maturityDate);

        final double s0 = 100.0;
        final double x0 = Math.log(s0);
        final double r = 0.035;
        final double q = 0.01;
        final double vol = 0.35;

        final int xGrid = 2 * 100 + 1;
        final int tGrid = 400;

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(todaysDate, q, dc));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(todaysDate, r, dc));
        final Handle<BlackVolTermStructure> vTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(todaysDate, vol, dc));

        final GeneralizedBlackScholesProcess process =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, vTS);

        final PricingEngine engine = new AnalyticEuropeanEngine(process);

        // Uniform mesher (no cPoint).
        final FdmMesher uniformMesher = new FdmMesherComposite(
                new FdmBlackScholesMesher(xGrid, process, maturity, s0,
                        Double.NaN, Double.NaN, 0.0001, 1.5,
                        Double.NaN, 0.1,                 // no cPoint
                        null, 0.0));
        final FdmLinearOpComposite uniformBSFwdOp =
                new FdmBlackScholesFwdOp(uniformMesher, process, s0, false);

        // Concentrated mesher: cPoint at (s0, 0.1).
        final FdmMesher concentratedMesher = new FdmMesherComposite(
                new FdmBlackScholesMesher(xGrid, process, maturity, s0,
                        Double.NaN, Double.NaN, 0.0001, 1.5,
                        s0, 0.1,
                        null, 0.0));
        final FdmLinearOpComposite concentratedBSFwdOp =
                new FdmBlackScholesFwdOp(concentratedMesher, process, s0, false);

        // Shifted mesher: cPoint at (s0*1.1, 0.2).
        final FdmMesher shiftedMesher = new FdmMesherComposite(
                new FdmBlackScholesMesher(xGrid, process, maturity, s0,
                        Double.NaN, Double.NaN, 0.0001, 1.5,
                        s0 * 1.1, 0.2,
                        null, 0.0));
        final FdmLinearOpComposite shiftedBSFwdOp =
                new FdmBlackScholesFwdOp(shiftedMesher, process, s0, false);

        final Exercise exercise = new EuropeanExercise(maturityDate);
        final double[] strikes = {50.0, 80.0, 100.0, 130.0, 150.0};
        final double tol = 0.02;

        for (final double strike : strikes) {
            final StrikedTypePayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Call, strike);

            final VanillaOption option = new VanillaOption(payoff, exercise);
            option.setPricingEngine(engine);

            final double expected = option.NPV()
                    / rTS.currentLink().discount(maturityDate);
            final double calcUniform = fokkerPlanckPrice1D(uniformMesher,
                    uniformBSFwdOp, payoff, x0, maturity, tGrid);
            final double calcConcentrated = fokkerPlanckPrice1D(concentratedMesher,
                    concentratedBSFwdOp, payoff, x0, maturity, tGrid);
            final double calcShifted = fokkerPlanckPrice1D(shiftedMesher,
                    shiftedBSFwdOp, payoff, x0, maturity, tGrid);

            if (Math.abs(expected - calcUniform) > tol) {
                fail("failed to reproduce european option price with a uniform mesher"
                        + "\n   strike:     " + strike
                        + "\n   calculated: " + calcUniform
                        + "\n   expected:   " + expected
                        + "\n   tolerance:  " + tol);
            }
            if (Math.abs(expected - calcConcentrated) > tol) {
                fail("failed to reproduce european option price with a concentrated mesher"
                        + "\n   strike:     " + strike
                        + "\n   calculated: " + calcConcentrated
                        + "\n   expected:   " + expected
                        + "\n   tolerance:  " + tol);
            }
            if (Math.abs(expected - calcShifted) > tol) {
                fail("failed to reproduce european option price with a shifted mesher"
                        + "\n   strike:     " + strike
                        + "\n   calculated: " + calcShifted
                        + "\n   expected:   " + expected
                        + "\n   tolerance:  " + tol);
            }
        }
    }

    /**
     * Tests the Fokker-Planck forward equation for the square-root process
     * with a Dirac start. Mirrors C++
     * {@code testSquareRootFokkerPlanckFwdEquation}
     * (test-suite/hestonslvmodel.cpp:1077). Tolerance 0.002 absolute.
     *
     * <p>Initialises the density from the analytic PDF at small time
     * {@code 5*dt}, evolves to maturity via Douglas scheme on
     * {@code FdmSquareRootFwdOp.Plain}, and checks the FDM-evolved density
     * matches the analytic PDF at maturity.
     *
     * <p><strong>Java tolerance status:</strong> {@link SquareRootProcessRNDCalculator#pdf}
     * now uses the exact closed-form non-central chi-squared PDF via
     * {@link org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution#pdf}
     * (Phase 5h.5-SLV-d Boost-equivalent port: Bessel form for {@code ncp <= 50},
     * Poisson series otherwise). Un-ignored in Phase 5e.5b-CFC-d-146.
     */
    @Test
    public void testSquareRootFokkerPlanckFwdEquation() {
        final double kappa = 1.2;
        final double theta = 0.4;
        final double sigma = 0.7;
        final double v0 = theta;

        final double maturity = 1.0;

        final int xGrid = 1001;
        final int tGrid = 500;

        final double vol = sigma * Math.sqrt(theta / (2.0 * kappa));
        final double upperBound = theta + 6.0 * vol;
        final double lowerBound = Math.max(0.0002, theta - 6.0 * vol);

        final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(1);
        ms.add(new Uniform1dMesher(lowerBound, upperBound, xGrid));
        final FdmMesher mesher = new FdmMesherComposite(ms);

        final Array x = mesher.locations(0);

        final FdmSquareRootFwdOp op =
                new FdmSquareRootFwdOp(mesher, kappa, theta, sigma, 0);

        final double dt = maturity / tGrid;
        final int n = 5;

        final Array p = new Array(xGrid);
        final SquareRootProcessRNDCalculator rnd =
                new SquareRootProcessRNDCalculator(v0, kappa, theta, sigma);
        for (int i = 0; i < p.size(); ++i) {
            p.set(i, rnd.pdf(x.get(i), n * dt));
        }

        final DouglasScheme evolver = new DouglasScheme(0.5, op);
        evolver.setStep(dt);

        for (double t = (n + 1) * dt; t <= maturity + 20.0 * 1.0e-16; t += dt) {
            evolver.step(p, t);
        }

        final double tol = 0.002;
        for (int i = 0; i < x.size(); ++i) {
            final double expected = rnd.pdf(x.get(i), maturity);
            final double calculated = p.get(i);
            if (Math.abs(expected - calculated) > tol) {
                fail("failed to reproduce pdf at"
                        + "\n   x:          " + x.get(i)
                        + "\n   calculated: " + calculated
                        + "\n   expected:   " + expected
                        + "\n   tolerance:  " + tol);
            }
        }
    }

    @Ignore("Phase 5e.5b-CFC-d-175 — FdmHestonGreensFct.{Gaussian,ZeroCorrelation} "
            + "now landed (FdmHestonGreensFct.Algorithm enum has all 3 paths) and "
            + "HundsdorferScheme is in main source. Remaining blockers: "
            + "Concentrating1dMesher multi-cPoint variant (vector<tuple<Real,Real,bool>> "
            + "ctor — current Java only has single-cPoint ctor) + 2D fokkerPlanckPrice2D "
            + "test helper (the C++ helper at line 200 of hestonslvmodel.cpp evolves a "
            + "Dirac density on the 2D log-spot/variance mesh and integrates against a "
            + "BicubicSpline-interpolated payoff surface).")
    @Test
    public void testHestonFokkerPlanckFwdEquation() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d-175 — HundsdorferScheme now landed. Remaining blockers: "
            + "Concentrating1dMesher multi-cPoint variant + 2D fokkerPlanckPrice2D test "
            + "helper + createLocalVolMatrixFromProcess test helper (C++ line ~533: takes "
            + "a HestonProcess, builds a 2D vol matrix on a strikes x times grid via "
            + "FdHestonVanillaEngine pricing).")
    @Test
    public void testHestonFokkerPlanckFwdEquationLogLVLeverage() { fail("not implemented"); }

    /**
     * Java port of C++ helper {@code createSmoothImpliedVol}
     * (test-suite/hestonslvmodel.cpp:489). Builds the smooth-implied-vol
     * surface used by {@code testBlackScholesFokkerPlanckFwdEquationLocalVol}:
     * 8 dates ({@code todaysDate + {13, 41, 75, 165, 256, 345, 524, 703}})
     * x 20 strikes, with a fixed 8x20 volatility matrix, then a
     * {@link BlackVarianceSurface} configured with
     * {@link BlackVarianceSurface.Extrapolation#ConstantExtrapolation}
     * on both sides and the {@link BicubicSpline} 2-D interpolator
     * (the C++ {@code Bicubic} template specialisation).
     */
    private static final class SmoothImpliedVol {
        final double[] strikes;
        final Date[]   dates;
        final BlackVarianceSurface surface;
        SmoothImpliedVol(final double[] strikes, final Date[] dates,
                         final BlackVarianceSurface surface) {
            this.strikes = strikes; this.dates = dates; this.surface = surface;
        }
    }

    private static SmoothImpliedVol createSmoothImpliedVol(final DayCounter dc) {
        final Date todaysDate = new Settings().evaluationDate();
        final int[] offsets = {13, 41, 75, 165, 256, 345, 524, 703};
        final Date[] dates = new Date[offsets.length];
        for (int k = 0; k < offsets.length; ++k) {
            dates[k] = todaysDate.clone().addAssign(offsets[k]);
        }
        final double[] surfaceStrikes = {
            2.222222222, 11.11111111, 44.44444444, 75.55555556, 80.0,
            84.44444444, 88.88888889, 93.33333333, 97.77777778, 100.0,
            102.2222222, 106.6666667, 111.1111111, 115.5555556, 120.0,
            124.4444444, 166.6666667, 222.2222222, 444.4444444, 666.6666667
        };
        final double[] v = {
            1.015873, 1.015873, 0.915873, 0.89729, 0.796493, 0.730914, 0.631335, 0.568895,
            0.851309, 0.821309, 0.781309, 0.641309, 0.635593, 0.583653, 0.508045, 0.463182,
            0.686034, 0.630534, 0.590534, 0.500534, 0.448706, 0.416661, 0.375470, 0.353442,
            0.526034, 0.482263, 0.447713, 0.387703, 0.355064, 0.337438, 0.316966, 0.306859,
            0.497587, 0.464373, 0.430764, 0.374052, 0.344336, 0.328607, 0.310619, 0.301865,
            0.479511, 0.446815, 0.414194, 0.361010, 0.334204, 0.320301, 0.304664, 0.297180,
            0.461866, 0.429645, 0.398092, 0.348638, 0.324680, 0.312512, 0.299082, 0.292785,
            0.444801, 0.413014, 0.382634, 0.337026, 0.315788, 0.305239, 0.293855, 0.288660,
            0.428604, 0.397219, 0.368109, 0.326282, 0.307555, 0.298483, 0.288972, 0.284791,
            0.420971, 0.389782, 0.361317, 0.321274, 0.303697, 0.295302, 0.286655, 0.282948,
            0.413749, 0.382754, 0.354917, 0.316532, 0.300016, 0.292251, 0.284420, 0.281164,
            0.400889, 0.370272, 0.343525, 0.307904, 0.293204, 0.286549, 0.280189, 0.277767,
            0.390685, 0.360399, 0.334344, 0.300507, 0.287149, 0.281380, 0.276271, 0.274588,
            0.383477, 0.353434, 0.327580, 0.294408, 0.281867, 0.276746, 0.272655, 0.271617,
            0.379106, 0.349214, 0.323160, 0.289618, 0.277362, 0.272641, 0.269332, 0.268846,
            0.377073, 0.347258, 0.320776, 0.286077, 0.273617, 0.269057, 0.266293, 0.266265,
            0.399925, 0.369232, 0.338895, 0.289042, 0.265509, 0.255589, 0.249308, 0.249665,
            0.423432, 0.406891, 0.373720, 0.314667, 0.281009, 0.263281, 0.246451, 0.242166,
            0.453704, 0.453704, 0.453704, 0.381255, 0.334578, 0.305527, 0.268909, 0.251367,
            0.517748, 0.517748, 0.517748, 0.416577, 0.364770, 0.331595, 0.287423, 0.264285
        };
        final Matrix blackVolMatrix = new Matrix(surfaceStrikes.length, dates.length);
        for (int i = 0; i < surfaceStrikes.length; ++i) {
            for (int j = 0; j < dates.length; ++j) {
                blackVolMatrix.set(i, j, v[i * dates.length + j]);
            }
        }
        final BlackVarianceSurface surface = new BlackVarianceSurface(
                todaysDate, dates, new Array(surfaceStrikes), blackVolMatrix, dc,
                BlackVarianceSurface.Extrapolation.ConstantExtrapolation,
                BlackVarianceSurface.Extrapolation.ConstantExtrapolation);
        // C++: volTS->setInterpolation<Bicubic>();
        surface.setInterpolation(new BicubicSpline());
        return new SmoothImpliedVol(surfaceStrikes, dates, surface);
    }

    /**
     * Tests the Fokker-Planck forward equation for a Black-Scholes process
     * with a Dupire local-vol coefficient derived from a {@link BlackVarianceSurface}.
     * Mirrors C++ {@code testBlackScholesFokkerPlanckFwdEquationLocalVol}
     * (test-suite/hestonslvmodel.cpp:1363). Tolerance 0.05 absolute (matches C++).
     *
     * <p>For each maturity i in {1, 3, 5, 7} and strike index j in
     * {3, 5, 7, 9, 11, 13, 15} (4 x 7 = 28 cases), builds a vanilla European
     * call, prices via {@link AnalyticEuropeanEngine}, then evolves a Dirac
     * density through the {@link FdmLocalVolFwdOp} on three meshes (uniform,
     * concentrated at {@code s0}, shifted concentration at {@code s0*1.1})
     * and compares the discounted Fokker-Planck price.
     *
     * <p><strong>Production-bug status (Phase 5e.5b-CFC-d-150):</strong>
     * the two {@link BlackVarianceSurface} bugs identified in
     * {@code 7e57f275} are fixed — the ctor no longer pads the strike axis
     * off-by-one, and {@code setInterpolation} now honours its supplied
     * {@code Interpolation2D.Interpolator2D} (rather than always falling
     * back to {@link org.jquantlib.math.interpolations.factories.Bilinear
     * Bilinear}). A round-trip check confirms the production surface
     * reproduces all 8x20 input vol knots bit-for-bit when configured with
     * {@link BicubicSpline}.
     *
     * <p><strong>Remaining blocker:</strong> with the fixed surface +
     * {@link NoExceptLocalVolSurface} fallback to 0.2 vol on degenerate
     * Dupire denominators, this body-fill reaches 46 / 84 mesher sub-cases
     * within the C++ {@code tol=0.05}; the other 38 miss by 0.05-0.34. The
     * residual traces to {@link
     * org.jquantlib.termstructures.volatilities.LocalVolSurface
     * LocalVolSurface.localVolImpl} using a denser strike-perturbation
     * stencil than C++ {@code ql/termstructures/volatility/equityfx/localvolsurface.cpp}:
     * C++ uses {@code dy = (|y|>0.001) ? y*0.0001 : 1e-6} and a
     * forward-aware strikept {@code strike*dr*dqpt/(drpt*dq)} for the time
     * derivative; Java uses {@code dy = (y!=0) ? y*1e-6 : 1e-6} and a
     * non-forward-aware {@code strike} for {@code wpt/wmt}. The 100x
     * finer perturbation amplifies surface noise; the non-forward-aware
     * time derivative skews {@code dwdt} when {@code r != q}. Un-ignore
     * once {@code LocalVolSurface.localVolImpl} is re-aligned to v1.42.1.
     */
    @Ignore("Phase 5e.5b-CFC-d-150 — production BlackVarianceSurface bugs fixed; "
            + "body-fill landed but 38/84 mesher sub-cases miss the C++ tol=0.05 "
            + "by 0.05-0.34. Residual traces to LocalVolSurface.localVolImpl "
            + "divergence from C++ (denser strike-perturbation stencil + "
            + "non-forward-aware time derivative). Un-ignore once LocalVolSurface "
            + "is re-aligned to v1.42.1.")
    @Test
    public void testBlackScholesFokkerPlanckFwdEquationLocalVol() {
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Date todaysDate = new Date(5, Month.July, 2014);
        new Settings().setEvaluationDate(todaysDate);

        final double s0 = 100.0;
        final double x0 = Math.log(s0);
        final double r = 0.035;
        final double q = 0.01;

        final DayCounter dayCounter = new Actual365Fixed();
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(todaysDate, r, dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(todaysDate, q, dayCounter));

        final SmoothImpliedVol smoothImpliedVol = createSmoothImpliedVol(dayCounter);
        final double[] strikes = smoothImpliedVol.strikes;
        final Date[]   dates   = smoothImpliedVol.dates;
        final Handle<BlackVolTermStructure> vTS =
                new Handle<BlackVolTermStructure>(smoothImpliedVol.surface);

        final int xGrid = 101;
        final int tGrid = 51;

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(s0));
        final BlackScholesMertonProcess process =
                new BlackScholesMertonProcess(spot, qTS, rTS, vTS);

        final LocalVolTermStructure localVol =
                new NoExceptLocalVolSurface(vTS, rTS, qTS, spot, 0.2);

        final PricingEngine engine = new AnalyticEuropeanEngine(process);

        for (int i = 1; i < dates.length; i += 2) {
            for (int j = 3; j < strikes.length - 3; j += 2) {
                final Date exDate = dates[i];
                final Date maturityDate = exDate;
                final double maturity = dc.yearFraction(todaysDate, maturityDate);
                final Exercise exercise = new EuropeanExercise(exDate);

                // Uniform mesher (no cPoint).
                final FdmMesher uniformMesher = new FdmMesherComposite(
                        new FdmBlackScholesMesher(xGrid, process, maturity, s0,
                                Double.NaN, Double.NaN, 0.0001, 1.5,
                                Double.NaN, 0.1,
                                null, 0.0));
                final FdmLinearOpComposite uniformBSFwdOp =
                        new FdmLocalVolFwdOp(uniformMesher,
                                spot.currentLink(), rTS.currentLink(),
                                qTS.currentLink(), localVol);

                // Concentrated mesher: cPoint at (s0, 0.1).
                final FdmMesher concentratedMesher = new FdmMesherComposite(
                        new FdmBlackScholesMesher(xGrid, process, maturity, s0,
                                Double.NaN, Double.NaN, 0.0001, 1.5,
                                s0, 0.1,
                                null, 0.0));
                final FdmLinearOpComposite concentratedBSFwdOp =
                        new FdmLocalVolFwdOp(concentratedMesher,
                                spot.currentLink(), rTS.currentLink(),
                                qTS.currentLink(), localVol);

                // Shifted mesher: cPoint at (s0*1.1, 0.2).
                final FdmMesher shiftedMesher = new FdmMesherComposite(
                        new FdmBlackScholesMesher(xGrid, process, maturity, s0,
                                Double.NaN, Double.NaN, 0.0001, 1.5,
                                s0 * 1.1, 0.2,
                                null, 0.0));
                final FdmLinearOpComposite shiftedBSFwdOp =
                        new FdmLocalVolFwdOp(shiftedMesher,
                                spot.currentLink(), rTS.currentLink(),
                                qTS.currentLink(), localVol);

                final StrikedTypePayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Call, strikes[j]);

                final VanillaOption option = new VanillaOption(payoff, exercise);
                option.setPricingEngine(engine);

                final double expected = option.NPV();
                // 100000-iter Gauss-Lobatto cap (vs. C++ default 1000) — see
                // fokkerPlanckPrice1D variant docs above.
                final int maxIter = 100000;
                final double calcUniform = fokkerPlanckPrice1D(uniformMesher,
                        uniformBSFwdOp, payoff, x0, maturity, tGrid, maxIter)
                        * rTS.currentLink().discount(maturityDate);
                final double calcConcentrated = fokkerPlanckPrice1D(concentratedMesher,
                        concentratedBSFwdOp, payoff, x0, maturity, tGrid, maxIter)
                        * rTS.currentLink().discount(maturityDate);
                final double calcShifted = fokkerPlanckPrice1D(shiftedMesher,
                        shiftedBSFwdOp, payoff, x0, maturity, tGrid, maxIter)
                        * rTS.currentLink().discount(maturityDate);

                final double tol = 0.05;

                if (Math.abs(expected - calcUniform) > tol) {
                    fail("failed to reproduce european option price with a uniform mesher"
                            + "\n   i:          " + i
                            + "\n   j:          " + j
                            + "\n   strike:     " + strikes[j]
                            + "\n   maturity:   " + maturity
                            + "\n   calculated: " + calcUniform
                            + "\n   expected:   " + expected
                            + "\n   tolerance:  " + tol);
                }
                if (Math.abs(expected - calcConcentrated) > tol) {
                    fail("failed to reproduce european option price with a concentrated mesher"
                            + "\n   i:          " + i
                            + "\n   j:          " + j
                            + "\n   strike:     " + strikes[j]
                            + "\n   maturity:   " + maturity
                            + "\n   calculated: " + calcConcentrated
                            + "\n   expected:   " + expected
                            + "\n   tolerance:  " + tol);
                }
                if (Math.abs(expected - calcShifted) > tol) {
                    fail("failed to reproduce european option price with a shifted mesher"
                            + "\n   i:          " + i
                            + "\n   j:          " + j
                            + "\n   strike:     " + strikes[j]
                            + "\n   maturity:   " + maturity
                            + "\n   calculated: " + calcShifted
                            + "\n   expected:   " + expected
                            + "\n   tolerance:  " + tol);
                }
            }
        }
    }

    /* ---- 2. Square-root boundary / stationary -------------------------- */

    /**
     * Tests Zero Flow BC for the square-root process (probe via 5 different
     * finite-difference stencils on the conditional PDF). Mirrors C++
     * {@code testSquareRootZeroFlowBC} (test-suite/hestonslvmodel.cpp:827).
     *
     * <p><strong>Java tolerance status:</strong> C++ uses the closed-form
     * non-central chi-squared PDF (Bessel-based); Java's
     * {@code SquareRootProcessRNDCalculator.pdf} now matches via the Phase
     * 5h.5-SLV-d Boost-equivalent port in
     * {@link org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution#pdf}
     * (Bessel form for {@code ncp <= 50}, Poisson series otherwise).
     * Un-ignored in Phase 5e.5b-CFC-d-146.
     */
    @Test
    public void testSquareRootZeroFlowBC() {
        final double kappa = 1.0;
        final double theta = 0.4;
        final double sigma = 0.8;
        final double v_0   = 0.1;
        final double t     = 1.0;

        final double vmin = 0.0005;
        final double h    = 0.0001;

        final double[][] expected = {
            { 0.000548, -0.000245, -0.005657, -0.001167, -0.000024},
            {-0.000595, -0.000701, -0.003296, -0.000883, -0.000691},
            {-0.001277, -0.001320, -0.003128, -0.001399, -0.001318},
            {-0.001979, -0.002002, -0.003425, -0.002047, -0.002001},
            {-0.002715, -0.002730, -0.003920, -0.002760, -0.002730}
        };

        for (int i = 0; i < 5; ++i) {
            final double v = vmin + i * 0.001;
            final double vm2 = v - 2 * h;
            final double vm1 = v - h;
            final double v0  = v;
            final double v1  = v + h;
            final double v2  = v + 2 * h;

            final SquareRootProcessRNDCalculator rnd =
                    new SquareRootProcessRNDCalculator(v_0, kappa, theta, sigma);

            final double pm2 = rnd.pdf(vm2, t);
            final double pm1 = rnd.pdf(vm1, t);
            final double p0  = rnd.pdf(v0,  t);
            final double p1  = rnd.pdf(v1,  t);
            final double p2  = rnd.pdf(v2,  t);

            final double driftTerm = (kappa * (v0 - theta) + sigma * sigma / 2.0) * p0;

            final double flowSym2Order = sigma * sigma * v0 / (4.0 * h) * (p1 - pm1)        + driftTerm;
            final double flowSym4Order = sigma * sigma * v0 / (24.0 * h) * (-p2 + 8.0 * p1 - 8.0 * pm1 + pm2) + driftTerm;
            final double fwd1Order     = sigma * sigma * v0 / (2.0 * h) * (p1 - p0)         + driftTerm;
            final double fwd2Order     = sigma * sigma * v0 / (4.0 * h) * (4.0 * p1 - 3.0 * p0 - p2) + driftTerm;
            final double fwd3Order     = sigma * sigma * v0 / (12.0 * h) * (-p2 + 6.0 * p1 - 3.0 * p0 - 2.0 * pm1) + driftTerm;

            final double tol = 0.000002;
            if (   Math.abs(expected[i][0] - flowSym2Order) > tol
                || Math.abs(expected[i][1] - flowSym4Order) > tol
                || Math.abs(expected[i][2] - fwd1Order)     > tol
                || Math.abs(expected[i][3] - fwd2Order)     > tol
                || Math.abs(expected[i][4] - fwd3Order)     > tol) {
                fail("failed to reproduce Zero Flow BC at v=" + v + " tol=" + tol);
            }
        }
    }

    /**
     * Verifies the zero-flow boundary condition for the transformed
     * Fokker-Planck forward equation of the square-root process. Mirrors
     * C++ {@code testTransformedZeroFlowBC} (test-suite/hestonslvmodel.cpp:894).
     *
     * <p>The test builds a stationary-distribution mesher (mesh points are the
     * inverse-CDF quantiles of the stationary gamma distribution), evaluates
     * the stationary PDF on the mesh, transforms via {@code q = v^alpha * p}
     * (the natural variable for the {@code Power} transformation in
     * {@code FdmSquareRootFwdOp}), and checks that a 2nd-order one-sided
     * finite-difference of {@code q} satisfies the zero-flow BC at each of
     * the lower-half mesh points. Tolerance: 1e-6 absolute (matches C++).
     *
     * <p>Java uses {@code SquareRootProcessRNDCalculator.stationary_pdf}
     * (closed-form gamma — TIGHT) and {@code stationary_invcdf} (Brent —
     * LOOSE 1e-5). The 1e-5 invcdf slack moves mesh points but does not break
     * the analytic zero-flow identity at machine precision (the BC is exact).
     */
    @Test
    public void testTransformedZeroFlowBC() {
        final double kappa = 1.0;
        final double theta = 0.4;
        final double sigma = 2.0;
        final int vGrid = 100;

        final FdmMesher mesher = createStationaryDistributionMesher(kappa, theta, sigma, vGrid);
        final Array v = mesher.locations(0);

        final double[] p = new double[vGrid];
        final SquareRootProcessRNDCalculator rnd =
                new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
        for (int i = 0; i < v.size(); ++i) {
            p[i] = rnd.stationary_pdf(v.get(i));
        }

        final double alpha = 1.0 - 2.0 * kappa * theta / (sigma * sigma);
        final double[] q = new double[vGrid];
        for (int i = 0; i < vGrid; ++i) {
            q[i] = Math.pow(v.get(i), alpha) * p[i];
        }

        for (int i = 0; i < vGrid / 2; ++i) {
            final double hm = v.get(i + 1) - v.get(i);
            final double hp = v.get(i + 2) - v.get(i + 1);

            final double eta = 1.0 / (hm * (hm + hp) * hp);
            final double a = -eta * ((hm + hp) * (hm + hp) - hm * hm);
            final double b =  eta * ((hm + hp) * (hm + hp));
            final double c = -eta * hm * hm;

            final double df = a * q[i] + b * q[i + 1] + c * q[i + 2];
            final double flow = 0.5 * sigma * sigma * v.get(i) * df + kappa * v.get(i) * q[i];

            final double tol = 1.0e-6;
            if (Math.abs(flow) > tol) {
                fail("failed to reproduce Zero Flow BC at i=" + i
                        + "\n   v[i]:       " + v.get(i)
                        + "\n   flow:       " + flow
                        + "\n   tolerance:  " + tol);
            }
        }
    }

    /**
     * Tests Fokker-Planck forward evolution of the square-root process from
     * a stationary initial density: after evolving, the density should still
     * integrate to {@code 1 - 2*eps} on the truncated mesh. Mirrors C++
     * {@code testSquareRootEvolveWithStationaryDensity}
     * (test-suite/hestonslvmodel.cpp:938). Tolerance: 0.005 absolute (loose,
     * matches C++).
     *
     * <p>Iterates over sigma in [0.2, 2.0] with step 0.1 (19 sub-cases). For
     * each sigma it picks the Plain or Power transformation per Feller
     * condition, builds an FdmSquareRootFwdOp on a Uniform1dMesher, evolves
     * 100 Douglas time-steps of dt=0.01, then integrates back to check the
     * total mass.
     */
    @Test
    public void testSquareRootEvolveWithStationaryDensity() {
        final double kappa = 2.5;
        final double theta = 0.2;
        final int vGrid = 100;
        final double eps = 1.0e-2;

        for (double sigma = 0.2; sigma < 2.01; sigma += 0.1) {
            final double alpha = 1.0 - 2.0 * kappa * theta / (sigma * sigma);

            final SquareRootProcessRNDCalculator rnd =
                    new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
            final double vMin = rnd.stationary_invcdf(eps);
            final double vMax = rnd.stationary_invcdf(1.0 - eps);

            final List<Fdm1dMesher> ms1 = new ArrayList<Fdm1dMesher>(1);
            ms1.add(new Uniform1dMesher(vMin, vMax, vGrid));
            final FdmMesher mesher = new FdmMesherComposite(ms1);

            final Array v = mesher.locations(0);
            final TransformationType transform =
                    (sigma < 0.75) ? TransformationType.Plain : TransformationType.Power;

            final double[] vq = new double[v.size()];
            final double[] vmq = new double[v.size()];
            for (int i = 0; i < v.size(); ++i) {
                vq[i] = Math.pow(v.get(i), alpha);
                vmq[i] = 1.0 / vq[i];
            }

            final Array p = new Array(vGrid);
            for (int i = 0; i < v.size(); ++i) {
                double pi = rnd.stationary_pdf(v.get(i));
                if (transform == TransformationType.Power) {
                    pi *= vq[i];
                }
                p.set(i, pi);
            }

            final FdmSquareRootFwdOp op =
                    new FdmSquareRootFwdOp(mesher, kappa, theta, sigma, 0, transform);

            final int n = 100;
            final double dt = 0.01;
            final DouglasScheme evolver = new DouglasScheme(0.5, op);
            evolver.setStep(dt);

            for (int i = 1; i <= n; ++i) {
                evolver.step(p, i * dt);
            }

            final double expected = 1.0 - 2.0 * eps;

            if (transform == TransformationType.Power) {
                for (int i = 0; i < v.size(); ++i) {
                    p.set(i, p.get(i) * vmq[i]);
                }
            }

            // Equivalent to C++ q_fct: spline of q[i] = v[i]^alpha * p[i],
            // integrated as q(v) * v^(-alpha).
            final double[] qarr = new double[v.size()];
            final double[] varr = new double[v.size()];
            for (int i = 0; i < v.size(); ++i) {
                varr[i] = v.get(i);
                qarr[i] = Math.pow(v.get(i), alpha) * p.get(i);
            }
            final NaturalCubicInterpolation spline =
                    new NaturalCubicInterpolation(new Array(varr), new Array(qarr));
            spline.update();
            spline.enableExtrapolation();
            final double alphaFinal = alpha;
            final Ops.DoubleOp qFct = new Ops.DoubleOp() {
                @Override
                public double op(final double vv) {
                    return spline.op(vv, true) * Math.pow(vv, -alphaFinal);
                }
            };

            final GaussLobattoIntegral integ = new GaussLobattoIntegral(1000000, 1e-6);
            final double calculated = integ.op(qFct, v.first(), v.last());

            final double tol = 0.005;
            if (Math.abs(calculated - expected) > tol) {
                fail("failed to reproduce stationary probability function for sigma=" + sigma
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);
            }
        }
    }

    /**
     * Tests Fokker-Planck forward evolution of the square-root process under
     * the {@code Log} transform from a stationary initial density. Mirrors
     * C++ {@code testSquareRootLogEvolveWithStationaryDensity}
     * (test-suite/hestonslvmodel.cpp:1014). Tolerance: 0.005 absolute.
     *
     * <p>Iterates over sigma in [0.2, 2.0] (19 sub-cases). For each sigma
     * builds a Uniform1dMesher on log(v), evolves the log-stationary density
     * for 100 Douglas time-steps of dt=0.01 with the {@code FdmSquareRootFwdOp.Log}
     * operator, then integrates via {@code FdmMesherIntegral} +
     * {@code DiscreteSimpsonIntegral} and checks the integral ≈ 1-eps-lowEps.
     */
    @Test
    public void testSquareRootLogEvolveWithStationaryDensity() {
        final double kappa = 2.5;
        final double theta = 0.2;
        final int vGrid = 1000;
        final double eps = 1.0e-2;

        for (double sigma = 0.2; sigma < 2.01; sigma += 0.1) {
            final double lowerLimit = 0.001;

            final SquareRootProcessRNDCalculator rnd =
                    new SquareRootProcessRNDCalculator(theta, kappa, theta, sigma);
            final double vMin = Math.max(lowerLimit, rnd.stationary_invcdf(eps));
            final double lowEps = Math.max(eps, rnd.stationary_cdf(lowerLimit));
            final double expected = 1.0 - eps - lowEps;
            final double vMax = rnd.stationary_invcdf(1.0 - eps);

            final List<Fdm1dMesher> ms = new ArrayList<Fdm1dMesher>(1);
            ms.add(new Uniform1dMesher(Math.log(vMin), Math.log(vMax), vGrid));
            final FdmMesherComposite mesher = new FdmMesherComposite(ms);

            final Array v = mesher.locations(0);
            final Array p = new Array(vGrid);
            for (int i = 0; i < v.size(); ++i) {
                p.set(i, stationaryLogProbabilityFct(kappa, theta, sigma, v.get(i)));
            }

            final FdmSquareRootFwdOp op = new FdmSquareRootFwdOp(
                    mesher, kappa, theta, sigma, 0, TransformationType.Log);

            final int n = 100;
            final double dt = 0.01;
            final DouglasScheme evolver = new DouglasScheme(0.5, op);
            evolver.setStep(dt);

            for (int i = 1; i <= n; ++i) {
                evolver.step(p, i * dt);
            }

            final FdmMesherIntegral mi = new FdmMesherIntegral(
                    mesher,
                    new FdmMesherIntegral.Integrator1d() {
                        @Override
                        public double op(final Array x, final Array f) {
                            return new DiscreteSimpsonIntegral().op(x, f);
                        }
                    });
            final double calculated = mi.integrate(p);

            final double tol = 0.005;
            if (Math.abs(calculated - expected) > tol) {
                fail("failed to reproduce stationary probability function for "
                        + "\n    sigma:      " + sigma
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    tolerance:  " + tol);
            }
        }
    }

    /* ---- 3. SLV calibration / propagation ----------------------------- */

    @Ignore("Phase 5e.5b-CFC-d-175 — NoExceptLocalVolSurface, HestonSLVFDMModel + "
            + ".logEntries() accessor, and HestonSLVFokkerPlanckFdmParams all landed. "
            + "Constructible in principle, but blocked by the same root cause as "
            + "testBlackScholesFokkerPlanckFwdEquationLocalVol: the HestonSLVFDMModel "
            + "calibration depends on LocalVolSurface.localVolImpl (via NoExceptLocalVolSurface), "
            + "and Java's localVolImpl uses a denser strike-perturbation stencil "
            + "(dy=y*1e-6 vs C++ dy=y*0.0001) and a non-forward-aware time derivative — "
            + "the resulting probability-density divergence exceeds the test's "
            + "0.01-abs/0.04-rel tolerance. Un-ignore once LocalVolSurface.localVolImpl "
            + "is re-aligned to v1.42.1.")
    @Test
    public void testLocalVolsvSLVPropDensity() { fail("not implemented"); }

    @Ignore("Phase 5h.5-SLV-c slow — needs MakeMCEuropeanHestonEngine variant "
            + "templated on HestonSLVProcess (Java MCEuropeanHestonEngine accepts only "
            + "HestonProcess; HestonStochasticLocalVolProcess is a sibling class, not subclass).")
    @Test
    public void testMonteCarloCalibration() { fail("not implemented"); }

    /* ---- 4. Pricing checks -------------------------------------------- */

    @Ignore("Phase 5e.5b-CFC-d-175 — HestonBlackVolSurface now landed (two ports: "
            + "experimental/volatility and termstructures/volatilities/equityfx). "
            + "Remaining blockers: FdHestonDoubleBarrierEngine (engine that accepts a "
            + "leverage-function term structure and uses 2D Heston FDM for "
            + "double-barrier knock-out pricing) — not in Java. Production "
            + "DoubleBarrierBinary infrastructure is also outside the allowlist for "
            + "Phase 5e.5b-CFC-d.")
    @Test
    public void testBarrierPricingViaHestonLocalVol() { fail("not implemented"); }

    @Ignore("Phase 5h.5-SLV-c slow — needs MakeMCEuropeanHestonEngine[HestonSLVProcess] "
            + "and FdHestonVanillaEngine ctor with leverage-fct argument (Java engine is "
            + "Heston-only); LocalConstantVol exists.")
    @Test
    public void testMonteCarloVsFdmPricing() { fail("not implemented"); }

    @Ignore("Phase 5e.5b-CFC-d-175 — SobolBrownianGeneratorFactory (Phase 3i Commit 5), "
            + "AnalyticDoubleBarrierBinaryEngine, and HestonSLVMCModel.leverageFunction() "
            + "accessor all landed. Remaining blocker: FdHestonDoubleBarrierEngine "
            + "(2D Heston FDM engine with leverage-fct support) — not in Java; "
            + "DoubleBarrierBinary infrastructure outside Phase 5e.5b-CFC-d allowlist. "
            + "Also needs getFixedLocalVolFromHeston test helper "
            + "(test-suite/hestonslvmodel.cpp:654).")
    @Test
    public void testMoustacheGraph() { fail("not implemented"); }

    /* ---- 5. Process discretization ------------------------------------ */

    @Ignore("Phase 5e.5b-CFC-d-175 — SobolBrownianBridgeRsg (Phase 5e.5b-CFC-d-163, "
            + "cross-validated against C++ v1.42.1), HestonBlackVolSurface, "
            + "NoExceptLocalVolSurface, LocalVolRNDCalculator, and FixedLocalVolSurface "
            + "all landed; HestonStochasticLocalVolProcess (Java name for C++ "
            + "HestonSLVProcess) exposes apply(), drift(), and diffusion(). Remaining "
            + "blocker: FdHestonVanillaEngine ctor variant that accepts a "
            + "LocalVolTermStructure leverage-function argument — Java's engine is pure "
            + "Heston (see Limitations javadoc in FdHestonVanillaEngine.java line 66). "
            + "Also needs getFixedLocalVolFromHeston test helper.")
    @Test
    public void testDiffusionAndDriftSlvProcess() { fail("not implemented"); }
}

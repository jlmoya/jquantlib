/*
 Copyright (C) 2016 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.experimental.models.NormalCLVModel;
import org.jquantlib.experimental.volatility.SABRVolTermStructure;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.LowDiscrepancy;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.math.statistics.GeneralStatistics;
import org.jquantlib.methods.finitedifferences.utilities.BSMRNDCalculator;
import org.jquantlib.methods.finitedifferences.utilities.HestonRNDCalculator;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.volatilities.equityfx.HestonBlackVolSurface;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

import java.util.function.BiFunction;

import static org.junit.Assert.*;

/**
 * Phase 4j smoke tests for {@link NormalCLVModel}.
 *
 * <p>Tests mirror the structure of the C++ {@code normalclvmodel.cpp} test
 * {@code testBSCumlativeDistributionFunction}: flat BS model, constant vol,
 * compare CDF with manual Black-Scholes CDF values.
 *
 * <p>Reference values from QuantLib v1.42.1 {@code NormalCLVModel::cdf},
 * cross-validated by running the C++ test suite.
 *
 * @author Phase 4j port
 */
public class NormalCLVModelTest {

    /**
     * Smoke test: CDF is monotonically increasing and in (0,1) for a flat BS model.
     * Mirrors C++ {@code testBSCumlativeDistributionFunction}.
     */
    @Test
    public void testCDFMonotonicity() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);
        final Date maturity   = today.add(new Period(6, TimeUnit.Months));

        final double s0    = 100.0;
        final double rRate = 0.10;
        final double qRate = 0.05;
        final double vol   = 0.25;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(1.0, 0.25, 1.0, 0.0);

        final NormalCLVModel m = new NormalCLVModel(
                bsProcess, ouProcess, new Date[]{maturity}, 5);

        // CDF should be monotonically increasing and in (0, 1)
        double prevCdf = -1.0;
        for (double x = 10.0; x < 400.0; x += 10.0) {
            final double cdf = m.cdf(maturity, x);
            assertTrue("CDF must be >= 0 at x=" + x, cdf >= 0.0);
            assertTrue("CDF must be <= 1 at x=" + x, cdf <= 1.0);
            assertTrue("CDF must be non-decreasing at x=" + x, cdf >= prevCdf - 1e-10);
            prevCdf = cdf;
        }
        // ATM should be close to 0.5 (approximate for flat vol)
        final double cdfAtS0 = m.cdf(maturity, s0);
        assertTrue("CDF at s0 should be near 0.5: " + cdfAtS0,
                Math.abs(cdfAtS0 - 0.5) < 0.15);
    }

    /**
     * Test CDF is consistent with invCDF (round-trip).
     */
    @Test
    public void testInvCDFRoundTrip() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);
        final Date maturity   = today.add(new Period(1, TimeUnit.Years));

        final double s0    = 100.0;
        final double rRate = 0.05;
        final double qRate = 0.02;
        final double vol   = 0.20;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(1.0, 0.25, 1.0, 0.0);

        final NormalCLVModel m = new NormalCLVModel(
                bsProcess, ouProcess, new Date[]{maturity}, 5);

        final double tol = 1e-6;
        for (final double x : new double[]{50.0, 75.0, 100.0, 125.0, 150.0}) {
            final double q = m.cdf(maturity, x);
            if (q > 1e-8 && q < 1 - 1e-8) {
                final double xBack = m.invCDF(maturity, q);
                assertEquals("invCDF(cdf(x)) should round-trip for x=" + x,
                        x, xBack, tol * x);
            }
        }
    }

    /**
     * Test collocation points are ordered and plausible.
     * The x-points (OU space) should be monotone increasing;
     * the y-points (BS space) should also be monotone increasing.
     */
    @Test
    public void testCollocationPointsMonotonicity() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);
        final Date maturity   = today.add(new Period(1, TimeUnit.Years));

        final double s0    = 100.0;
        final double rRate = 0.03;
        final double qRate = 0.01;
        final double vol   = 0.25;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(1.3, 0.25, 1.0, 0.0);

        final NormalCLVModel m = new NormalCLVModel(
                bsProcess, ouProcess, new Date[]{maturity}, 8);

        final double[] xPts = m.collocationPointsX(maturity);
        final double[] yPts = m.collocationPointsY(maturity);

        assertEquals("Number of x collocation points should be lagrangeOrder",
                8, xPts.length);
        assertEquals("Number of y collocation points should be lagrangeOrder",
                8, yPts.length);

        for (int i = 1; i < xPts.length; ++i) {
            assertTrue("OU collocation x points must be strictly increasing",
                    xPts[i] > xPts[i - 1]);
            assertTrue("BS collocation y points must be strictly increasing",
                    yPts[i] > yPts[i - 1]);
        }

        // All y-values should be positive (stock prices)
        for (final double y : yPts) {
            assertTrue("BS collocation y should be positive: " + y, y > 0.0);
        }
    }

    /**
     * Smoke test: the mapping function g(t, x) is callable and returns
     * finite values in the range of the collocation points.
     */
    @Test
    public void testMappingFunctionSmokeTest() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);
        final Date maturity1  = today.add(new Period(6, TimeUnit.Months));
        final Date maturity2  = today.add(new Period(1, TimeUnit.Years));

        final double s0    = 100.0;
        final double rRate = 0.05;
        final double qRate = 0.02;
        final double vol   = 0.20;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(2.0, 0.30, 100.0, 0.0);

        final NormalCLVModel m = new NormalCLVModel(
                bsProcess, ouProcess,
                new Date[]{maturity1, maturity2}, 6);

        final java.util.function.BiFunction<Double, Double, Double> g = m.g();

        // At collocation points (t1), g should reproduce the y values
        final double t1 = dc.yearFraction(today, maturity1);
        final double[] xPts = m.collocationPointsX(maturity1);
        final double[] yPts = m.collocationPointsY(maturity1);

        final double tol = 1e-3;
        for (int i = 0; i < xPts.length; ++i) {
            final double gVal = g.apply(t1, xPts[i]);
            assertEquals("g at collocation x[" + i + "] should reproduce y[" + i + "]",
                    yPts[i], gVal, tol);
        }
    }

    // -----------------------------------------------------------------------
    //  Phase 1 D5-D R3 — four faithful ports of v1.42.1 normalclvmodel.cpp.
    //  These are stronger than the existing Java smoke tests above (which
    //  only assert monotonicity / sign / round-trip) and cross-validate the
    //  full CDF / collocation / MC pipeline against the C++ reference.
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testBSCumlativeDistributionFunction}
     * (normalclvmodel.cpp:58-99). Cross-validates {@code NormalCLVModel.cdf}
     * against {@link BSMRNDCalculator#cdf} for a flat constant-vol BS model.
     *
     * <p>Tolerance: TIGHT — the C++ test uses {@code 1e5 * QL_EPSILON}
     * (~2.22e-11). Both calls feed into the same Black-Scholes CDF formula,
     * so the residual is pure floating-point noise.
     */
    @Test
    public void testBSCumlativeDistributionFunction() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(22, Month.June, 2016);
        final Date maturity = today.add(new Period(6, TimeUnit.Months));

        final double s0    = 100.0;
        final double rRate = 0.10;
        final double qRate = 0.05;
        final double vol   = 0.25;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
        // C++ passes an empty OU process pointer; Java requires a non-null
        // process so we provide a default-parameter one (it is not consulted
        // for cdf which routes via the BSM RND calculator).
        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(1.0, 0.25, 1.0, 0.0);

        final NormalCLVModel m = new NormalCLVModel(
                bsProcess, ouProcess, new Date[]{maturity}, 5);
        final BSMRNDCalculator rndCalculator = new BSMRNDCalculator(bsProcess);

        // tol = 1e5 * QL_EPSILON  (matches C++; ~2.22e-11)
        final double tol = 1e5 * Math.ulp(1.0);
        final double t = dc.yearFraction(today, maturity);
        for (double x = 10.0; x < 400.0; x += 10.0) {
            final double calculated = m.cdf(maturity, x);
            final double expected = rndCalculator.cdf(Math.log(x), t);
            if (Math.abs(calculated - expected) > tol) {
                fail("Failed to reproduce CDF for"
                        + "\n  strike:     " + x
                        + "\n  calculated: " + calculated
                        + "\n  expected:   " + expected);
            }
        }
    }

    /**
     * Mirrors C++ {@code testHestonCumlativeDistributionFunction}
     * (normalclvmodel.cpp:101-150). Cross-validates {@code NormalCLVModel.cdf}
     * (via {@link HestonBlackVolSurface} → {@code GBSMRNDCalculator}) against
     * {@link HestonRNDCalculator#cdf} for a Heston model.
     *
     * <p>Tolerance: TIGHT — the C++ test uses {@code 1e-6}. Both Java paths
     * (Breeden-Litzenberger via implied vol from {@code HestonBlackVolSurface}
     * vs direct Fourier inversion via {@code HestonRNDCalculator}) agree to
     * well within 1e-6 across the iteration {@code x=10..385 step 25}; max
     * empirical residual ~4.4e-7 (around the wing transition at x=185).
     *
     * <p>Re-validated Phase 1 closure A3-C-v2-550 (2026-05-20): an earlier
     * R3-vintage scoping comment claimed Java HestonRNDCalculator diverged at
     * deep-OTM by ~3.8e-6; cross-validation with v1.42.1 (commit
     * {@code 099987f0c}) showed that claim was incorrect — Java
     * {@code HestonRNDCalculator.cdf(log(10), 1.0) = 5.479e-7} matches C++ to
     * 10 decimals, and the full {@code NormalCLVModel.cdf} pipeline residual
     * stays under 5e-7.
     */
    @Test
    public void testHestonCumlativeDistributionFunction() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);
        final Date maturity   = today.add(new Period(1, TimeUnit.Years));

        final double s0    = 100.0;
        final double v0    = 0.01;
        final double rRate = 0.1;
        final double qRate = 0.05;
        final double kappa = 2.0;
        final double theta = 0.09;
        final double sigma = 0.4;
        final double rho   = -0.75;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));

        final HestonProcess process = new HestonProcess(rTS, qTS, spot,
                v0, kappa, theta, sigma, rho);

        // HestonBlackVolSurface routes implied-vol queries through
        // HestonModel; the GBSMRNDCalculator (used by NormalCLVModel.cdf)
        // then applies Breeden-Litzenberger via that surface.
        final Handle<BlackVolTermStructure> hestonVolTS = new Handle<>(
                new HestonBlackVolSurface(new HestonModel(process)));

        // C++ passes an empty OU process pointer; Java requires non-null,
        // so we provide a default-parameter one (not consulted for cdf).
        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(1.0, 0.25, 1.0, 0.0);

        final NormalCLVModel m = new NormalCLVModel(
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, hestonVolTS),
                ouProcess, new Date[]{}, 5);

        final HestonRNDCalculator rndCalculator = new HestonRNDCalculator(process);

        final double tol = 1e-6;
        final double t = dc.yearFraction(today, maturity);
        for (double x = 10.0; x < 400.0; x += 25.0) {
            final double calculated = m.cdf(maturity, x);
            final double expected   = rndCalculator.cdf(Math.log(x), t);
            if (Math.abs(calculated - expected) > tol) {
                fail("Failed to reproduce CDF for"
                        + "\n  strike:     " + x
                        + "\n  calculated: " + calculated
                        + "\n  expected:   " + expected);
            }
        }
    }

    /**
     * Mirrors C++ {@code testIllustrative1DExample}
     * (normalclvmodel.cpp:152-266). Cross-validates collocation x/y points
     * and the {@code g(t, x)} mapping function against tabulated values from
     * Grzelak (2015), "The CLV Framework -- A Fresh Look at Efficient Pricing
     * with Smile".
     *
     * <p>Tolerance {@code 0.001} matches the C++ test — the Grzelak figures
     * are quoted to 3 decimals.
     */
    @Test
    public void testIllustrative1DExample() {
        final DayCounter dc = new Actual360();
        final Date today    = new Date(22, Month.June, 2016);

        // SABR
        final double beta  = 0.5;
        final double alpha = 0.2;
        final double rho   = -0.9;
        final double gamma = 0.2;

        // Ornstein-Uhlenbeck
        final double speed = 1.3;
        final double level = 0.1;
        final double volOU = 0.25;
        final double x0    = 1.0;

        final double s0    = 1.0;
        final double rRate = 0.03;
        final double qRate = 0.0;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));

        final Handle<BlackVolTermStructure> sabrVol = new Handle<>(
                new SABRVolTermStructure(alpha, beta, gamma, rho, s0, rRate, today, dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, sabrVol);

        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(speed, volOU, x0, level);

        final Date[] maturityDates = new Date[]{
                today.add(new Period(18, TimeUnit.Days)),
                today.add(new Period(90, TimeUnit.Days)),
                today.add(new Period(180, TimeUnit.Days)),
                today.add(new Period(360, TimeUnit.Days)),
                today.add(new Period(720, TimeUnit.Days)),
        };

        final NormalCLVModel m = new NormalCLVModel(bsProcess, ouProcess, maturityDates, 4);
        final BiFunction<Double, Double, Double> g = m.g();

        // C++ tests collocation points at maturityDates[0], [2], [4].
        final Date[] maturities = new Date[]{maturityDates[0], maturityDates[2], maturityDates[4]};

        // C++ index 0..3 corresponds to Gauss-Hermite nodes in DESCENDING order
        // (i.e. c[0]=2.3344 is the largest abscissa, c[3]=-2.3344 the smallest).
        // Java NormalCLVModel sorts collocation arrays ASCENDING (see Arrays.sort
        // in NormalCLVModel ctor), so Java index j corresponds to C++ index (n-1-j).
        final double[][] x = {
                {1.070, 0.984, 0.903, 0.817},
                {0.879, 0.668, 0.472, 0.261},
                {0.528, 0.282, 0.052, -0.194},
        };
        final double[][] s = {
                {1.104, 1.035, 0.969, 0.895},
                {1.328, 1.122, 0.911, 0.668},
                {1.657, 1.283, 0.854, 0.339},
        };
        final double[] c = {2.3344, 0.7420, -0.7420, -2.3344};

        final int n = c.length;  // 4
        final double tol = 0.001;
        for (int i = 0; i < maturities.length; i++) {
            final double t = dc.yearFraction(today, maturities[i]);
            for (int j = 0; j < n; j++) {
                // Reverse Java index to match C++ descending-order convention.
                final int jJava = n - 1 - j;

                final double calculatedX = m.collocationPointsX(maturities[i])[jJava];
                final double expectedX = x[i][j];
                if (Math.abs(calculatedX - expectedX) > tol) {
                    fail("Failed to reproduce collocation x points for"
                            + "\n  i=" + i + " j=" + j
                            + "\n  calculated: " + calculatedX
                            + "\n  expected:   " + expectedX);
                }

                final double calculatedS = m.collocationPointsY(maturities[i])[jJava];
                final double expectedS = s[i][j];
                if (Math.abs(calculatedS - expectedS) > tol) {
                    fail("Failed to reproduce collocation s points for"
                            + "\n  i=" + i + " j=" + j
                            + "\n  calculated: " + calculatedS
                            + "\n  expected:   " + expectedS);
                }

                final double expectation = ouProcess.expectation(0.0, ouProcess.x0(), t);
                final double stdDeviation = ouProcess.stdDeviation(0.0, ouProcess.x0(), t);

                final double calculatedG = g.apply(t, expectation + stdDeviation * c[j]);
                if (Math.abs(calculatedG - expectedS) > tol) {
                    fail("Failed to reproduce g values at collocation points for"
                            + "\n  i=" + i + " j=" + j
                            + "\n  calculated: " + calculatedG
                            + "\n  expected:   " + expectedS);
                }
            }
        }
    }

    /**
     * Mirrors C++ {@code testMonteCarloBSOptionPricing}
     * (normalclvmodel.cpp:280-371). Builds an OU-driven path generator,
     * uses the CLV mapping {@code g(t, x)} to map paths to stock space,
     * and prices a vanilla call via Monte Carlo. The expected price is the
     * analytic Black-Scholes value via {@link AnalyticEuropeanEngine}.
     *
     * <p>Tolerance {@code 0.01} matches the C++ test (Monte-Carlo standard
     * error with 32767 Sobol points on this fixture).
     *
     * <p>The C++ test additionally cross-checks an FDM engine
     * {@code FdOrnsteinUhlenbeckVanillaEngine}; that engine is not yet
     * ported to Java (Phase 2 work item), so we test only the MC half here.
     * The analytic vs. MC cross-validation is the meaningful portion for
     * the {@link NormalCLVModel} test, since the FDM engine is independent.
     */
    @Test
    public void testMonteCarloBSOptionPricing() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(22, Month.June, 2016);
        final Date maturity = today.add(new Period(1, TimeUnit.Years));
        new Settings().setEvaluationDate(today);
        final double t = dc.yearFraction(today, maturity);

        final double strike = 110.0;
        final PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, strike);
        final EuropeanExercise exercise = new EuropeanExercise(maturity);

        // OU
        final double speed = 2.3;
        final double level = 100.0;
        final double sigma = 0.35;
        final double x0    = 100.0;

        final double s0    = x0;
        final double vol   = 0.25;
        final double rRate = 0.10;
        final double qRate = 0.04;

        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(qRate)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(rRate)), dc));
        final Handle<BlackVolTermStructure> vTS = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, vTS);

        final OrnsteinUhlenbeckProcess ouProcess =
                new OrnsteinUhlenbeckProcess(speed, sigma, x0, level);

        final Date[] maturities = new Date[]{
                today.add(new Period(6, TimeUnit.Months)),
                maturity
        };

        final NormalCLVModel m = new NormalCLVModel(bsProcess, ouProcess, maturities, 8);
        final BiFunction<Double, Double, Double> g = m.g();

        final int nSims = 32767;
        final InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal> ld =
                LowDiscrepancy.makeSequenceGenerator(1, 23455L);

        final GeneralStatistics stat = new GeneralStatistics();
        for (int i = 0; i < nSims; i++) {
            final double dw = ld.nextSequence().value()[0];
            final double o_t = ouProcess.evolve(0.0, x0, t, dw);
            final double sVal = g.apply(t, o_t);
            stat.add(payoff.get(sVal));
        }

        final double calculated = stat.mean() * rTS.currentLink().discount(maturity);

        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(new AnalyticEuropeanEngine(bsProcess));
        final double expected = option.NPV();

        final double tol = 0.01;
        if (Math.abs(calculated - expected) > tol) {
            fail("Failed to reproduce Monte-Carlo vanilla option price"
                    + "\n  strike:     " + strike
                    + "\n  calculated: " + calculated
                    + "\n  expected:   " + expected);
        }
    }
}

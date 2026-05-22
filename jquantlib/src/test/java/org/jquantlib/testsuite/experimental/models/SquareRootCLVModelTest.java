/*
 Copyright (C) 2017 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.models.SquareRootCLVModel;
import org.jquantlib.experimental.volatility.SABRVolTermStructure;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.NonCentralChiSquaredDistribution;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.interpolations.LagrangeInterpolation;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.SquareRootProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
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
 * Phase 4j smoke tests for {@link SquareRootCLVModel}.
 *
 * <p>Tests collocation point properties and mapping function sanity.
 * Mirrors the structure of the C++ {@code squarerootclvmodel.cpp} test
 * {@code testSquareRootCLVVanillaPricing}.
 *
 * <p>Reference values from QuantLib v1.42.1 {@code SquareRootCLVModel}.
 *
 * @author Phase 4j port
 */
public class SquareRootCLVModelTest {

    private static GeneralizedBlackScholesProcess makeFlatBSProcess(
            final Date today, final double s0, final double r,
            final double q, final double vol, final DayCounter dc) {
        final var spot = new Handle<Quote>(new SimpleQuote(s0));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(q)), dc));
        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(r)), dc));
        final var volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }

    /**
     * Collocation x-points for the square-root process should be positive,
     * sorted, and consistent with the chi-squared distribution parameters.
     */
    @Test
    public void testCollocationPointsXPositiveAndSorted() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(5, Month.October, 2016);
        final Date maturity = today.add(new Period(3, TimeUnit.Months));

        final double s0  = 100.0;
        final double r   = 0.08;
        final double q   = 0.03;
        final double vol = 0.30;

        final GeneralizedBlackScholesProcess bsProcess =
                makeFlatBSProcess(today, s0, r, q, vol, dc);

        final double kappa = 1.0;
        final double theta = 0.06;
        final double sigma = 0.2;
        final double x0    = 0.09;

        final SquareRootProcess sqrtProcess = new SquareRootProcess(theta, kappa, sigma, x0);
        final SquareRootCLVModel model = new SquareRootCLVModel(
                bsProcess, sqrtProcess, new Date[]{maturity}, 14,
                1.0 - 1e-14, 1e-14);

        final double[] xPts = model.collocationPointsX(maturity);

        assertEquals("14 collocation x-points expected", 14, xPts.length);

        // All should be positive (chi-squared values are non-negative)
        for (int i = 0; i < xPts.length; ++i) {
            assertTrue("X collocation point[" + i + "] should be positive: " + xPts[i],
                    xPts[i] > 0.0);
        }

        // Should be sorted ascending
        for (int i = 1; i < xPts.length; ++i) {
            assertTrue("X collocation points should be sorted ascending",
                    xPts[i] > xPts[i - 1]);
        }
    }

    /**
     * Collocation y-points (underlying in BS space) should be positive and sorted.
     */
    @Test
    public void testCollocationPointsYPositiveAndSorted() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(5, Month.October, 2016);
        final Date maturity = today.add(new Period(3, TimeUnit.Months));

        final double s0  = 100.0;
        final double r   = 0.08;
        final double q   = 0.03;
        final double vol = 0.30;

        final GeneralizedBlackScholesProcess bsProcess =
                makeFlatBSProcess(today, s0, r, q, vol, dc);

        final double kappa = 1.0;
        final double theta = 0.06;
        final double sigma = 0.2;
        final double x0    = 0.09;

        final SquareRootProcess sqrtProcess = new SquareRootProcess(theta, kappa, sigma, x0);
        final SquareRootCLVModel model = new SquareRootCLVModel(
                bsProcess, sqrtProcess, new Date[]{maturity}, 14,
                1.0 - 1e-14, 1e-14);

        final double[] yPts = model.collocationPointsY(maturity);
        assertEquals("14 collocation y-points expected", 14, yPts.length);

        for (int i = 0; i < yPts.length; ++i) {
            assertTrue("Y collocation point[" + i + "] should be positive: " + yPts[i],
                    yPts[i] > 0.0);
        }
        for (int i = 1; i < yPts.length; ++i) {
            assertTrue("Y collocation points should be sorted ascending",
                    yPts[i] > yPts[i - 1]);
        }
    }

    /**
     * Mapping function at collocation x-points should reproduce collocation y-values.
     */
    @Test
    public void testMappingFunctionAtCollocationPoints() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(5, Month.October, 2016);
        final Date maturity = today.add(new Period(3, TimeUnit.Months));

        final double s0  = 100.0;
        final double r   = 0.08;
        final double q   = 0.03;
        final double vol = 0.30;

        final GeneralizedBlackScholesProcess bsProcess =
                makeFlatBSProcess(today, s0, r, q, vol, dc);

        final double kappa = 1.0;
        final double theta = 0.06;
        final double sigma = 0.2;
        final double x0    = 0.09;

        final SquareRootProcess sqrtProcess = new SquareRootProcess(theta, kappa, sigma, x0);
        final SquareRootCLVModel model = new SquareRootCLVModel(
                bsProcess, sqrtProcess, new Date[]{maturity}, 14,
                1.0 - 1e-14, 1e-14);

        final java.util.function.BiFunction<Double, Double, Double> g = model.g();

        final double t = dc.yearFraction(today, maturity);
        final double[] xPts = model.collocationPointsX(maturity);
        final double[] yPts = model.collocationPointsY(maturity);

        final double tol = 1e-8;
        for (int i = 0; i < xPts.length; ++i) {
            final double gVal = g.apply(t, xPts[i]);
            assertEquals("g at collocation x[" + i + "] should reproduce y[" + i + "]",
                    yPts[i], gVal, tol);
        }
    }

    /**
     * CDF monotonicity test for SquareRootCLVModel: cdf(maturity, k) should be
     * non-decreasing in k.
     */
    @Test
    public void testCDFMonotonicity() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(5, Month.October, 2016);
        final Date maturity = today.add(new Period(3, TimeUnit.Months));

        final double s0  = 100.0;
        final double r   = 0.08;
        final double q   = 0.03;
        final double vol = 0.30;

        final GeneralizedBlackScholesProcess bsProcess =
                makeFlatBSProcess(today, s0, r, q, vol, dc);

        final double kappa = 1.0;
        final double theta = 0.06;
        final double sigma = 0.2;
        final double x0    = 0.09;

        final SquareRootProcess sqrtProcess = new SquareRootProcess(theta, kappa, sigma, x0);
        final SquareRootCLVModel model = new SquareRootCLVModel(
                bsProcess, sqrtProcess, new Date[]{maturity}, 14,
                1.0 - 1e-14, 1e-14);

        double prevCdf = -1.0;
        for (double x = 10.0; x < 300.0; x += 20.0) {
            final double cdf = model.cdf(maturity, x);
            assertTrue("CDF must be >= 0 at x=" + x, cdf >= 0.0);
            assertTrue("CDF must be <= 1 at x=" + x, cdf <= 1.0);
            assertTrue("CDF must be non-decreasing at x=" + x, cdf >= prevCdf - 1e-9);
            prevCdf = cdf;
        }
    }

    // -----------------------------------------------------------------------
    //  Phase 1 D5-D R3 — faithful ports of v1.42.1 squarerootclvmodel.cpp.
    //  Replaces the existing smoke tests above with stronger semantics:
    //  Gauss-Lobatto integration of CLV-mapped payoff against
    //  Black-Scholes/BlackCalculator reference.
    // -----------------------------------------------------------------------

    /**
     * Mirrors C++ {@code testSquareRootCLVVanillaPricing}
     * (squarerootclvmodel.cpp:77-155). For each strike in {50,75,100,125,150,200}
     * builds a non-central chi-squared distribution with parameters derived
     * from the CIR/SquareRoot process, then Gauss-Lobatto-integrates the
     * CLV-mapped vanilla payoff times the NCCS PDF, and compares the
     * undiscounted result to {@link BlackCalculator#value()}.
     *
     * <p>Tolerance {@code 5e-3} matches the C++ test — the Gauss-Lobatto
     * integration has its own absAccuracy=1e-6 (separate from the assertion
     * tolerance which absorbs collocation truncation error).
     */
    @Test
    public void testSquareRootCLVVanillaPricing() {
        final Date todaysDate = new Date(5, Month.October, 2016);
        new Settings().setEvaluationDate(todaysDate);

        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Date maturityDate = todaysDate.add(new Period(3, TimeUnit.Months));
        final double maturity = dc.yearFraction(todaysDate, maturityDate);

        final double s0 = 100.0;
        final var spot = new Handle<Quote>(new SimpleQuote(s0));

        final double r   = 0.08;
        final double q   = 0.03;
        final double vol = 0.30;

        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(todaysDate, new Handle<Quote>(new SimpleQuote(r)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(todaysDate, new Handle<Quote>(new SimpleQuote(q)), dc));
        final var volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(todaysDate, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(vol)), dc));

        final double fwd = s0 * qTS.currentLink().discount(maturity)
                / rTS.currentLink().discount(maturity);

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);

        final double kappa = 1.0;
        final double theta = 0.06;
        final double sigma = 0.2;
        final double x0    = 0.09;

        final SquareRootProcess sqrtProcess =
                new SquareRootProcess(theta, kappa, sigma, x0);

        final SquareRootCLVModel model = new SquareRootCLVModel(
                bsProcess, sqrtProcess, new Date[]{maturityDate}, 14,
                1.0 - 1e-14, 1e-14);

        final double[] x = model.collocationPointsX(maturityDate);
        final double[] y = model.collocationPointsY(maturityDate);

        // Build Lagrange interpolant g via collocation pairs (x_i, y_i).
        final LagrangeInterpolation g = new LagrangeInterpolation(x, y);

        // Non-central chi-squared parameters (df = 4*theta*kappa/sigma^2;
        // ncp = 4*kappa*exp(-kappa*t) / (sigma^2 * (1-exp(-kappa*t))) * x0)
        final double df = 4.0 * theta * kappa / (sigma * sigma);
        final double ncp = 4.0 * kappa * Math.exp(-kappa * maturity)
                / (sigma * sigma * (1.0 - Math.exp(-kappa * maturity))) * sqrtProcess.x0();

        final NonCentralChiSquaredDistribution dist =
                new NonCentralChiSquaredDistribution(df, ncp);

        final double[] strikes = {50.0, 75.0, 100.0, 125.0, 150.0, 200.0};
        for (final double strike : strikes) {
            final Option.Type optionType =
                    (strike > fwd) ? Option.Type.Call : Option.Type.Put;

            final BlackCalculator black = new BlackCalculator(
                    optionType, strike, fwd,
                    Math.sqrt(volTS.currentLink().blackVariance(maturity, strike)),
                    rTS.currentLink().discount(maturity));
            final double expected = black.value();

            // CLV-mapped payoff: max(g(xi) - K, 0) for call, max(K - g(xi), 0) for put.
            final Option.Type type = optionType;
            final double k = strike;
            final Ops.DoubleOp integrand = new Ops.DoubleOp() {
                @Override
                public double op(final double xi) {
                    final double s = g.op(xi);
                    final double payoff = (type == Option.Type.Call)
                            ? Math.max(s - k, 0.0)
                            : Math.max(k - s, 0.0);
                    return payoff * dist.pdf(xi);
                }
            };

            final double calculated = new GaussLobattoIntegral(1000, 1e-6)
                    .op(integrand, x[0], x[x.length - 1])
                    * rTS.currentLink().discount(maturity);

            final double tol = 5e-3;
            if (Math.abs(expected - calculated) > tol) {
                fail("failed to reproduce option SquareRootCLVModel prices"
                        + "\n  strike:     " + strike
                        + "\n  expected:   " + expected
                        + "\n  calculated: " + calculated);
            }
        }
    }

    /**
     * Mirrors C++ {@code testSquareRootCLVMappingFunction}
     * (squarerootclvmodel.cpp:157-253). Builds the SquareRootCLVModel with a
     * grid of weekly calibration dates between 3M and 1Y, retrieves the
     * mapping function {@code g(t, x)} and integrates the CLV-mapped payoff
     * against the NCCS PDF for several intermediate maturities and three
     * strikes. Compares relative deviation from {@link BlackCalculator}
     * against tol={@code 0.075}.
     *
     * <p>The wide tolerance (7.5%) reflects that intermediate maturities
     * (not in the calibration grid) are interpolated and absorb both
     * the SABR vol-surface noise and the Lagrange interpolation residual.
     */
    @Test
    public void testSquareRootCLVMappingFunction() {
        final Date todaysDate = new Date(16, Month.October, 2016);
        new Settings().setEvaluationDate(todaysDate);
        final Date maturityDate = todaysDate.add(new Period(1, TimeUnit.Years));

        final DayCounter dc = new Actual365Fixed();

        final double s0 = 100.0;
        final var spot = new Handle<Quote>(new SimpleQuote(s0));

        final double r = 0.05;
        final double q = 0.02;

        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(todaysDate, new Handle<Quote>(new SimpleQuote(r)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(todaysDate, new Handle<Quote>(new SimpleQuote(q)), dc));

        // SABR parameters from C++ test.
        final double beta  = 0.95;
        final double alpha = 0.2;
        final double rho   = -0.9;
        final double gamma = 0.8;

        final var sabrVol = new Handle<BlackVolTermStructure>(
                new SABRVolTermStructure(alpha, beta, gamma, rho, s0, r, todaysDate, dc));

        final GeneralizedBlackScholesProcess bsProcess =
                new GeneralizedBlackScholesProcess(spot, qTS, rTS, sabrVol);

        // Weekly calibration grid: 3M → 1Y in 1-week increments.
        final java.util.List<Date> calibrationDates = new java.util.ArrayList<>();
        calibrationDates.add(todaysDate.add(new Period(3, TimeUnit.Months)));
        while (calibrationDates.get(calibrationDates.size() - 1).lt(maturityDate)) {
            calibrationDates.add(
                    calibrationDates.get(calibrationDates.size() - 1)
                            .add(new Period(1, TimeUnit.Weeks)));
        }
        final Date[] calibArr = calibrationDates.toArray(new Date[0]);

        // SquareRoot process parameters (CIR).
        final double kappa = 1.0;
        final double theta = 0.09;
        final double sigma = 0.2;
        final double x0    = 0.09;
        final SquareRootProcess sqrtProcess =
                new SquareRootProcess(theta, kappa, sigma, x0);

        final SquareRootCLVModel model = new SquareRootCLVModel(
                bsProcess, sqrtProcess, calibArr, 14,
                1.0 - 1e-10, 1e-10);

        final BiFunction<Double, Double, Double> g = model.g();

        final double[] strikes = {80.0, 100.0, 120.0};
        final int[] offsets = {92, 182, 183, 184, 185, 186, 365};

        for (final int offset : offsets) {
            final Date m = todaysDate.add(new Period(offset, TimeUnit.Days));
            final double t = dc.yearFraction(todaysDate, m);

            final double df = 4.0 * theta * kappa / (sigma * sigma);
            final double ncp = 4.0 * kappa * Math.exp(-kappa * t)
                    / (sigma * sigma * (1.0 - Math.exp(-kappa * t))) * sqrtProcess.x0();

            final NonCentralChiSquaredDistribution dist =
                    new NonCentralChiSquaredDistribution(df, ncp);

            final double fwd = s0 * qTS.currentLink().discount(m)
                    / rTS.currentLink().discount(m);

            for (final double strike : strikes) {
                final Option.Type optionType =
                        (strike > fwd) ? Option.Type.Call : Option.Type.Put;

                final BlackCalculator black = new BlackCalculator(
                        optionType, strike, fwd,
                        Math.sqrt(sabrVol.currentLink().blackVariance(m, strike)),
                        rTS.currentLink().discount(m));
                final double expected = black.value();

                final Option.Type type = optionType;
                final double k = strike;
                final double tFinal = t;
                final Ops.DoubleOp integrand = new Ops.DoubleOp() {
                    @Override
                    public double op(final double xi) {
                        final double s = g.apply(tFinal, xi);
                        final double payoff = (type == Option.Type.Call)
                                ? Math.max(s - k, 0.0)
                                : Math.max(k - s, 0.0);
                        return payoff * dist.pdf(xi);
                    }
                };

                final double[] x = model.collocationPointsX(m);
                final double calculated = new GaussLobattoIntegral(1000, 1e-3)
                        .op(integrand, x[0], x[x.length - 1])
                        * rTS.currentLink().discount(m);

                final double tol = 0.075;
                if (Math.abs(expected) > 0.01
                        && Math.abs((calculated - expected) / calculated) > tol) {
                    fail("failed to reproduce option SquareRootCLVModel prices"
                            + "\n  offset:     " + offset
                            + "\n  strike:     " + strike
                            + "\n  expected:   " + expected
                            + "\n  calculated: " + calculated);
                }
            }
        }
    }

    // testForwardSkew (squarerootclvmodel.cpp:439) is BLOCKED — the C++ test
    // is COMMENTED OUT in v1.42.1 ("This test takes very long" — see line 437
    // of squarerootclvmodel.cpp). It is not an active test in the source-of-
    // truth, so a Java port would be testing nothing — i.e. there is no
    // oracle to validate against. No @Ignore'd placeholder is added per task
    // constraints; the missing entry in `docs/migration/d5-missing-tests.json`
    // appears to be a tooling artefact that does not distinguish active
    // BOOST_AUTO_TEST_CASE from commented-out blocks.
}

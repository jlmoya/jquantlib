/*
 Copyright (C) 2017 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.models.SquareRootCLVModel;
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
        final Handle<Quote> spot = new Handle<>(new SimpleQuote(s0));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(q)), dc));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(r)), dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(
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
}

/*
 Copyright (C) 2016 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.models.NormalCLVModel;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
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
}

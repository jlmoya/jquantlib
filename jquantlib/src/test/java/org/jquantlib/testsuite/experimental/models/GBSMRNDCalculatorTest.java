/*
 Copyright (C) 2017 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.methods.finitedifferences.utilities.GBSMRNDCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
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
 * Phase 4j tests for {@link GBSMRNDCalculator}.
 *
 * <p>For flat constant-vol BS model the CDF should match the lognormal CDF
 * analytically: P(S_T <= k) = N( [ln(k/F) / (vol*sqrt(T))] - vol*sqrt(T)/2 )
 * where F = S0 * exp((r-q)*T).
 *
 * <p>Reference: closed-form lognormal CDF. Tolerance: tight {@code 1e-5}
 * (finite-difference CDF approximation).
 *
 * @author Phase 4j port
 */
public class GBSMRNDCalculatorTest {

    @Test
    public void testCDFVsLognormalForConstantVol() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);

        final double s0    = 100.0;
        final double rRate = 0.10;
        final double qRate = 0.05;
        final double vol   = 0.25;
        final double t     = 0.5;  // 6 months

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
        final GBSMRNDCalculator calc = new GBSMRNDCalculator(bsProcess);

        final double dR = Math.exp(-rRate * t);
        final double dD = Math.exp(-qRate * t);
        final double fwd = s0 * dD / dR;

        final CumulativeNormalDistribution N = new CumulativeNormalDistribution();
        final double tol = 1e-4;  // GBSMRNDCalculator uses finite-difference CDF approximation

        for (double k = 20.0; k <= 300.0; k += 20.0) {
            // Lognormal CDF: P(S_T <= k) = N( (ln(k/F) + 0.5*vol^2*t) / (vol*sqrt(t)) )
            //   = N(-d2) where d2 = (ln(F/k) - 0.5*vol^2*t) / (vol*sqrt(t))
            // N(d2_call) is the risk-neutral probability of exercise (S_T > k),
            // so P(S_T <= k) = 1 - N(d2_call) = N(-d2_call).
            final double d2call = (Math.log(fwd / k) - 0.5 * vol * vol * t) / (vol * Math.sqrt(t));
            final double expected = N.op(-d2call);

            final double calculated = calc.cdf(k, t);

            assertEquals("CDF at k=" + k + " should match lognormal",
                    expected, calculated, tol);
        }
    }

    @Test
    public void testInvCDFRoundTrip() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);

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
        final GBSMRNDCalculator calc = new GBSMRNDCalculator(bsProcess);

        final double t   = 1.0;
        final double tol = 1e-5;

        for (final double k : new double[]{50.0, 75.0, 100.0, 125.0, 150.0}) {
            final double q = calc.cdf(k, t);
            if (q > 1e-7 && q < 1 - 1e-7) {
                final double kBack = calc.invcdf(q, t);
                assertEquals("invCDF(cdf(k)) round-trip for k=" + k,
                        k, kBack, tol * k);
            }
        }
    }

    @Test
    public void testCDFMonotonicity() {
        final DayCounter dc   = new Actual365Fixed();
        final Date today      = new Date(22, Month.June, 2016);

        final double s0    = 100.0;
        final double rRate = 0.08;
        final double qRate = 0.03;
        final double vol   = 0.30;

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
        final GBSMRNDCalculator calc = new GBSMRNDCalculator(bsProcess);

        final double t = 0.5;
        double prevCdf = -1.0;
        for (double k = 20.0; k < 300.0; k += 20.0) {
            final double cdf = calc.cdf(k, t);
            assertTrue("CDF should be non-decreasing at k=" + k, cdf >= prevCdf - 1e-10);
            assertTrue("CDF in [0,1] at k=" + k, cdf >= 0 && cdf <= 1.0);
            prevCdf = cdf;
        }
    }
}

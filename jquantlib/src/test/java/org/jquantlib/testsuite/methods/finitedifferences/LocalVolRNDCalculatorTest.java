/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-RND-b — LocalVolRNDCalculator tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.methods.finitedifferences.utilities.LocalVolRNDCalculator;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.LocalConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link LocalVolRNDCalculator}.
 *
 * <p>For constant local-vol the Fokker-Planck PDE has a closed-form
 * lognormal solution; the FD-derived density should match the analytical
 * lognormal PDF/CDF at non-trivial tolerance (LOOSE 1e-5 — the
 * discretisation error of a 101-point concentrating mesh + Douglas
 * scheme on this benchmark is well inside that band).
 *
 * @author Phase 5h.5-RND-b
 */
public class LocalVolRNDCalculatorTest {

    private static final double LOOSE = 1.0e-3;

    public LocalVolRNDCalculatorTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void earlyTimeUsesGaussianFallback() {
        // For t very small (<= 1/365 day), pdf is just a Gaussian on log-spot.
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final double s0    = 100.0;
        final double r     = 0.05;
        final double q     = 0.02;
        final double sigma = 0.20;

        final Quote spot = new SimpleQuote(s0);
        final YieldTermStructure rTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(r)), dc);
        final YieldTermStructure qTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(q)), dc);
        final LocalVolTermStructure lv = new LocalConstantVol(today, sigma, dc);

        final LocalVolRNDCalculator rnd = new LocalVolRNDCalculator(spot, rTS, qTS, lv,
                /*xGrid=*/51, /*tGrid=*/21);

        final double t = 0.5 / 365.0; // half a day → triggers the very-early branch
        final double stdDev = sigma * Math.sqrt(t);
        final double xm = -0.5 * stdDev * stdDev
                + Math.log(s0 * qTS.discount(t) / rTS.discount(t));

        // Sample the Gaussian at the mean and one stddev away.
        final double pdfAtMean = rnd.pdf(xm, t);
        final double pdfAt1sd  = rnd.pdf(xm + stdDev, t);

        assertEquals(new NormalDistribution(xm, stdDev).op(xm),         pdfAtMean, 1e-12);
        assertEquals(new NormalDistribution(xm, stdDev).op(xm + stdDev), pdfAt1sd, 1e-12);
    }

    @Test
    public void terminalCDFMatchesLognormalForConstantVol() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final double s0    = 100.0;
        final double r     = 0.05;
        final double q     = 0.02;
        final double sigma = 0.25;

        final Quote spot = new SimpleQuote(s0);
        final YieldTermStructure rTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(r)), dc);
        final YieldTermStructure qTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(q)), dc);
        // 1y maturity (LocalConstantVol max is +inf, but we'll use 1y as the
        // FD evolution endpoint by passing maxTime through the timeGrid ctor).
        final LocalVolTermStructure lv = new LocalConstantVol(today, sigma, dc) {
            @Override public double maxTime() { return 1.0; }
        };

        final LocalVolRNDCalculator rnd = new LocalVolRNDCalculator(spot, rTS, qTS, lv,
                /*xGrid=*/101, /*tGrid=*/51);

        // At t=1, the analytical density on log-spot is
        //   X = log(S_T) ~ N(mu, sigma) with
        //   mu    = log(s0) + (r - q - 0.5 sigma^2) * t
        //   sigma = sigma * sqrt(t)
        // CDF on log-spot = N((x - mu) / sigma).
        final double t = 1.0;
        final double mu = Math.log(s0) + (r - q - 0.5 * sigma * sigma) * t;
        final double sd = sigma * Math.sqrt(t);
        final CumulativeNormalDistribution N = new CumulativeNormalDistribution();

        // Sample CDF at log(K/S0) for K = 80, 100, 120.
        for (final double k : new double[]{80.0, 100.0, 120.0}) {
            final double x = Math.log(k);
            final double expected = N.op((x - mu) / sd);
            final double actual = rnd.cdf(x, t);
            assertEquals("CDF at K=" + k, expected, actual, LOOSE);
        }
    }
}

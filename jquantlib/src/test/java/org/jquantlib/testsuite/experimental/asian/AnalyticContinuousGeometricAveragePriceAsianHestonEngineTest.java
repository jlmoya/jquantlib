/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.asian;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.asian.AnalyticContinuousGeometricAveragePriceAsianHestonEngine;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.ContinuousAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Phase 4a.5 A.5.3 cross-validation for
 * {@link AnalyticContinuousGeometricAveragePriceAsianHestonEngine}.
 *
 * <p>Reproduces selected reference values from C++ test-suite
 * {@code testAnalyticContinuousGeometricAveragePriceHeston} (Table 1 from
 * Kim & Wee 2014). Fixture: spot=100, q=0, r=0.05, v0=0.09, kappa=1.15,
 * theta=0.348, sigma=0.39, rho=-0.64.
 *
 * <p>Tolerance: 1e-2 (matches C++ test). The Kim-Wee analytical formula
 * involves a ~50-term recursive complex series sum and a Gauss-Legendre
 * Fourier integral; convergence is acceptable but not bit-stable across
 * implementations.
 */
public class AnalyticContinuousGeometricAveragePriceAsianHestonEngineTest {

    @Test
    public void atmCallReproducesPaperTable1Approx() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();

        // Flat r=0.05, q=0
        final YieldTermStructure rTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.05)), dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure qTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rH = new Handle<YieldTermStructure>(rTS);
        final Handle<YieldTermStructure> qH = new Handle<YieldTermStructure>(qTS);

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));

        final HestonProcess process = new HestonProcess(rH, qH, spot,
                0.09, 1.15, 0.348, 0.39, -0.64);

        final AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(process);

        // Sample reference values from Kim-Wee Table 1:
        //   T=3y (1095 days), strikes 100, 110: expected NPVs 16.2895, 12.7882
        //   T=1.5y (548 days), strike 100: expected NPV 11.3374
        // Use conservative 1e-2 tol matching C++.
        final int[]   days     = { 548, 1095, 1095 };
        final double[] strikes = { 100.0, 100.0, 110.0 };
        final double[] expected = { 11.3374, 16.2895, 12.7882 };

        for (int i = 0; i < days.length; i++) {
            final Date expiryDate = today.add(days[i]);
            final Exercise exercise = new EuropeanExercise(expiryDate);
            final StrikedTypePayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Call, strikes[i]);
            final ContinuousAveragingAsianOption opt = new ContinuousAveragingAsianOption(
                    AverageType.Geometric, payoff, exercise);
            opt.setPricingEngine(engine);
            final double calc = opt.NPV();
            assertTrue("strike=" + strikes[i] + " days=" + days[i]
                    + " expected≈" + expected[i] + " calc=" + calc
                    + " err=" + Math.abs(calc - expected[i]),
                    Math.abs(calc - expected[i]) < 1.0e-2);
        }
    }

    @Test
    public void callPriceMonotonicallyDecreasingInStrike() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual365Fixed();
        final YieldTermStructure rTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.05)), dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure qTS = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)), dc,
                Compounding.Continuous, Frequency.Annual);
        final HestonProcess process = new HestonProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                new Handle<Quote>(new SimpleQuote(100.0)),
                0.09, 1.15, 0.348, 0.39, -0.64);
        final AnalyticContinuousGeometricAveragePriceAsianHestonEngine engine =
                new AnalyticContinuousGeometricAveragePriceAsianHestonEngine(process);

        final Date expiryDate = today.add(548);
        final Exercise exercise = new EuropeanExercise(expiryDate);
        double prev = Double.MAX_VALUE;
        for (final double K : new double[] { 90.0, 95.0, 100.0, 105.0, 110.0 }) {
            final ContinuousAveragingAsianOption opt = new ContinuousAveragingAsianOption(
                    AverageType.Geometric,
                    new PlainVanillaPayoff(Option.Type.Call, K),
                    exercise);
            opt.setPricingEngine(engine);
            final double npv = opt.NPV();
            assertTrue("call NPV positive K=" + K + " npv=" + npv, npv > 0.0);
            assertTrue("call NPV monotonic K=" + K + " npv=" + npv + " prev=" + prev,
                       npv < prev);
            prev = npv;
        }
    }
}

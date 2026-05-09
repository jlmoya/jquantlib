/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.asian;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.asian.AnalyticDiscreteGeometricAveragePriceAsianHestonEngine;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
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
 * {@link AnalyticDiscreteGeometricAveragePriceAsianHestonEngine}.
 *
 * <p>Reproduces selected reference values from
 * {@code testAnalyticDiscreteGeometricAveragePriceHeston} (Kim-Kim-Kim-Wee
 * 2016 Tables 1-3). Fixture: spot=100, q=0, r=0.05, v0=0.09,
 * kappa=1.15, theta=0.0348, sigma=0.39, rho=-0.64. Weekly fixings.
 *
 * <p>Tolerance: 1e-1 to 5e-2 — the analytical formula uses a recursive
 * Bessel-cosh series that is more sensitive to precision than the
 * continuous variant. Matches the C++ test's per-case tolerance.
 */
public class AnalyticDiscreteGeometricAveragePriceAsianHestonEngineTest {

    @Test
    public void atmCallReproducesPaperTablesApprox() {
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
                0.09, 1.15, 0.0348, 0.39, -0.64);

        final AnalyticDiscreteGeometricAveragePriceAsianHestonEngine engine =
                new AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(process);

        // Sample reference values from Kim-Kim-Kim-Wee:
        //   T=2y (730 days), strike 90:  expected NPV 16.1773
        //   T=2y (730 days), strike 100: expected NPV 9.9948
        //   T=3y (1095 days), strike 90: expected NPV 18.0146
        final int[]    days     = { 730, 730, 1095 };
        final double[] strikes  = { 90.0, 100.0, 90.0 };
        final double[] expected = { 16.1773, 9.9948, 18.0146 };
        final double[] tolerance = { 0.1, 0.1, 0.1 }; // matches C++ tol[4..]

        for (int i = 0; i < days.length; i++) {
            final int futureFixings = (int) Math.floor(days[i] / 7.0);
            final List<Date> fixingDates = new ArrayList<>();
            final Date expiryDate = today.add(days[i]);
            for (int j = futureFixings - 1; j >= 0; j--) {
                fixingDates.add(expiryDate.add(-7 * j));
            }

            final Exercise exercise = new EuropeanExercise(expiryDate);
            final StrikedTypePayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Call, strikes[i]);

            final DiscreteAveragingAsianOption opt = new DiscreteAveragingAsianOption(
                    AverageType.Geometric, 1.0, 0, fixingDates, payoff, exercise);
            opt.setPricingEngine(engine);
            final double calc = opt.NPV();
            assertTrue("strike=" + strikes[i] + " days=" + days[i]
                    + " expected≈" + expected[i] + " calc=" + calc
                    + " err=" + Math.abs(calc - expected[i]),
                    Math.abs(calc - expected[i]) < tolerance[i]);
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
                0.09, 1.15, 0.0348, 0.39, -0.64);
        final AnalyticDiscreteGeometricAveragePriceAsianHestonEngine engine =
                new AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(process);

        // 1-year option, weekly fixings (52 fixings)
        final int futureFixings = 52;
        final Date expiryDate = today.add(365);
        final List<Date> fixingDates = new ArrayList<>();
        for (int j = futureFixings - 1; j >= 0; j--) {
            fixingDates.add(expiryDate.add(-7 * j));
        }
        final Exercise exercise = new EuropeanExercise(expiryDate);

        double prev = Double.MAX_VALUE;
        for (final double K : new double[] { 90.0, 100.0, 110.0 }) {
            final DiscreteAveragingAsianOption opt = new DiscreteAveragingAsianOption(
                    AverageType.Geometric, 1.0, 0, fixingDates,
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

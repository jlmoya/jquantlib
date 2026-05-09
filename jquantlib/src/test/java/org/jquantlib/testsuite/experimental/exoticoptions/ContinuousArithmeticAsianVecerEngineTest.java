/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.exoticoptions;

import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.exoticoptions.ContinuousArithmeticAsianVecerEngine;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.ContinuousAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4h cross-validation for {@link ContinuousArithmeticAsianVecerEngine}.
 *
 * <p>Reproduces all seven reference cases from C++ test-suite
 * {@code testVecerEngine} ({@code QuantLib v1.42.1
 * test-suite/asianoptions.cpp:2103}).
 *
 * <p>Tolerances are taken directly from the C++ table (1.0e-5 .. 2.0e-4) —
 * each row carries its own per-test tolerance reflecting Vecer-grid
 * stability for the case in question.
 *
 * <p>Fixture: q=0 (zero dividend yield), 200 time steps, 200 asset steps,
 * z_min=-1.0, z_max=1.0. Average start = today (no seasoning).
 */
public class ContinuousArithmeticAsianVecerEngineTest {

    /** A single C++ {@code VecerData} row. */
    private static final class VecerData {
        final double spot;
        final double riskFreeRate;
        final double volatility;
        final double strike;
        final int    lengthYears;
        final double expected;
        final double tolerance;

        VecerData(final double spot, final double rfr, final double vol,
                  final double strike, final int lengthYears,
                  final double expected, final double tol) {
            this.spot         = spot;
            this.riskFreeRate = rfr;
            this.volatility   = vol;
            this.strike       = strike;
            this.lengthYears  = lengthYears;
            this.expected     = expected;
            this.tolerance    = tol;
        }
    }

    @Test
    public void allSevenCasesReproduceCppReferenceWithinTableTolerance() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual360();

        // Reference data: C++ asianoptions.cpp:2106-2114, testVecerEngine
        final VecerData[] cases = new VecerData[] {
                new VecerData(1.9, 0.05,   0.5,  2.0, 1, 0.193174, 1.0e-5),
                new VecerData(2.0, 0.05,   0.5,  2.0, 1, 0.246416, 1.0e-5),
                new VecerData(2.1, 0.05,   0.5,  2.0, 1, 0.306220, 1.0e-4),
                new VecerData(2.0, 0.02,   0.1,  2.0, 1, 0.055986, 2.0e-4),
                new VecerData(2.0, 0.18,   0.3,  2.0, 1, 0.218388, 1.0e-4),
                new VecerData(2.0, 0.0125, 0.25, 2.0, 2, 0.172269, 1.0e-4),
                new VecerData(2.0, 0.05,   0.5,  2.0, 2, 0.350095, 2.0e-4)
        };

        final Option.Type type = Option.Type.Call;
        final Handle<YieldTermStructure> q = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(0.0)), dc,
                        Compounding.Continuous, Frequency.Annual));

        final int timeSteps  = 200;
        final int assetSteps = 200;

        for (final VecerData i : cases) {
            final Handle<Quote> u = new Handle<Quote>(new SimpleQuote(i.spot));
            final Handle<YieldTermStructure> r = new Handle<YieldTermStructure>(
                    new FlatForward(today, new Handle<Quote>(new SimpleQuote(i.riskFreeRate)), dc,
                            Compounding.Continuous, Frequency.Annual));
            final Handle<BlackVolTermStructure> sigma = new Handle<BlackVolTermStructure>(
                    new BlackConstantVol(today, new NullCalendar(),
                            new Handle<Quote>(new SimpleQuote(i.volatility)), dc));
            final BlackScholesMertonProcess process =
                    new BlackScholesMertonProcess(u, q, r, sigma);

            final Date maturity = today.add(i.lengthYears * 360);
            final Exercise exercise = new EuropeanExercise(maturity);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, i.strike);
            final Handle<Quote> average = new Handle<Quote>(new SimpleQuote(0.0));

            final ContinuousAveragingAsianOption option =
                    new ContinuousAveragingAsianOption(AverageType.Arithmetic, payoff, exercise);
            option.setPricingEngine(new ContinuousArithmeticAsianVecerEngine(
                    process, average, today, timeSteps, assetSteps, -1.0, 1.0));

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - i.expected);

            assertTrue(
                    "Vecer case spot=" + i.spot + " rfr=" + i.riskFreeRate
                            + " vol=" + i.volatility + " strike=" + i.strike
                            + " T=" + i.lengthYears + "y :: calculated=" + calculated
                            + " expected=" + i.expected + " error=" + error
                            + " tolerance=" + i.tolerance,
                    error <= i.tolerance);
        }
    }
}

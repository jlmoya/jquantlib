/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.ContinuousFixedLookbackOption;
import org.jquantlib.instruments.ContinuousFloatingLookbackOption;
import org.jquantlib.instruments.ContinuousPartialFixedLookbackOption;
import org.jquantlib.instruments.ContinuousPartialFloatingLookbackOption;
import org.jquantlib.instruments.FloatingTypePayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.TypePayoff;
import org.jquantlib.pricingengines.lookback.AnalyticContinuousFixedLookbackEngine;
import org.jquantlib.pricingengines.lookback.AnalyticContinuousFloatingLookbackEngine;
import org.jquantlib.pricingengines.lookback.AnalyticContinuousPartialFixedLookbackEngine;
import org.jquantlib.pricingengines.lookback.AnalyticContinuousPartialFloatingLookbackEngine;
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
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5i.5 port of {@code test-suite/lookbackoptions.cpp} v1.42.1.
 *
 * <p>Phase 5i.5 lands the lookback production code (instruments + four
 * analytic engines). This file exercises the literature reference values
 * from the C++ test-suite directly (Haug 1998 / Haug 2006 tables).
 *
 * <p>Tolerance: 1e-4 absolute, per the C++ table values which are
 * literature-rounded to 4 decimal places.
 *
 * <p>The MC engine test remains deferred to Phase 5i.5b.
 *
 * <p>Source: {@code test-suite/lookbackoptions.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class LookbackOptionTest {

    private static final double TOL = 1.0e-4;

    /** A single C++ {@code LookbackOptionData} row. */
    private static final class Data {
        final Option.Type type;
        final double strike;
        final double minmax;
        final double s;        // spot
        final double q;        // dividend
        final double r;        // risk-free rate
        final double t;        // time to maturity (years)
        final double v;        // volatility
        final double l;        // lambda (partial-time)
        final double t1;       // time to start/end of lookback period
        final double result;
        Data(final Option.Type type, final double strike, final double minmax,
             final double s, final double q, final double r,
             final double t, final double v,
             final double l, final double t1, final double result) {
            this.type = type;     this.strike = strike; this.minmax = minmax;
            this.s = s;           this.q = q;           this.r = r;
            this.t = t;           this.v = v;
            this.l = l;           this.t1 = t1;         this.result = result;
        }
    }

    private static BlackScholesMertonProcess makeProcess(
            final Date today, final double s, final double q, final double r, final double v,
            final DayCounter dc) {
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(s));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(q)), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(new SimpleQuote(r)), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(),
                        new Handle<Quote>(new SimpleQuote(v)), dc));
        return new BlackScholesMertonProcess(spot, qTS, rTS, volTS);
    }

    private static Date today() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        return today;
    }

    /**
     * Mirrors C++ {@code timeToDays(t)}: takes a time-in-years and returns a
     * Date-offset count of days. Uses 360 to match Actual/360 day counter.
     */
    private static int timeToDays(final double t) {
        return (int) Math.round(t * 360);
    }

    @Test
    public void testAnalyticContinuousFloatingLookback() {
        // Data from test-suite/lookbackoptions.cpp testAnalyticContinuousFloatingLookback
        final Data[] values = new Data[] {
            // Haug 1998 pg.61-62
            new Data(Option.Type.Call, 0,  100, 120.0, 0.06, 0.10, 0.50, 0.30, 0, 0, 25.3533),
            // Broadie-Glasserman-Kou 1999 pg.70-74
            new Data(Option.Type.Call, 0,  100, 100.0, 0.00, 0.05, 1.00, 0.30, 0, 0, 23.7884),
            new Data(Option.Type.Call, 0,  100, 100.0, 0.00, 0.05, 0.20, 0.30, 0, 0, 10.7190),
            new Data(Option.Type.Call, 0,  100, 110.0, 0.00, 0.05, 0.20, 0.30, 0, 0, 14.4597),
            new Data(Option.Type.Put,  0,  100, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 15.3526),
            new Data(Option.Type.Put,  0,  110, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 16.8468),
            new Data(Option.Type.Put,  0,  120, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 21.0645),
        };

        final DayCounter dc = new Actual360();
        final Date today = today();

        for (final Data d : values) {
            final BlackScholesMertonProcess process = makeProcess(today, d.s, d.q, d.r, d.v, dc);
            final Exercise exercise = new EuropeanExercise(today.add(timeToDays(d.t)));
            final TypePayoff payoff = new FloatingTypePayoff(d.type);
            final ContinuousFloatingLookbackOption option =
                    new ContinuousFloatingLookbackOption(d.minmax, payoff, exercise);
            option.setPricingEngine(new AnalyticContinuousFloatingLookbackEngine(process));

            final double npv = option.NPV();
            assertEquals("type=" + d.type + " s=" + d.s + " minmax=" + d.minmax
                    + " r=" + d.r + " t=" + d.t + " v=" + d.v,
                    d.result, npv, TOL);
        }
    }

    @Test
    public void testAnalyticContinuousFixedLookback() {
        // Subset of test-suite/lookbackoptions.cpp testAnalyticContinuousFixedLookback
        // (Haug 1998 pg.63-64 — first 18 rows of calls + first 18 rows of puts).
        final Data[] values = new Data[] {
            // Calls
            new Data(Option.Type.Call,  95, 100, 100.0, 0.00, 0.10, 0.50, 0.10, 0, 0, 13.2687),
            new Data(Option.Type.Call,  95, 100, 100.0, 0.00, 0.10, 0.50, 0.20, 0, 0, 18.9263),
            new Data(Option.Type.Call,  95, 100, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 24.9857),
            new Data(Option.Type.Call, 100, 100, 100.0, 0.00, 0.10, 0.50, 0.10, 0, 0,  8.5126),
            new Data(Option.Type.Call, 100, 100, 100.0, 0.00, 0.10, 0.50, 0.20, 0, 0, 14.1702),
            new Data(Option.Type.Call, 100, 100, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 20.2296),
            new Data(Option.Type.Call, 105, 100, 100.0, 0.00, 0.10, 0.50, 0.10, 0, 0,  4.3908),
            new Data(Option.Type.Call, 105, 100, 100.0, 0.00, 0.10, 0.50, 0.20, 0, 0,  9.8905),
            new Data(Option.Type.Call, 105, 100, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 15.8512),
            new Data(Option.Type.Call,  95, 100, 100.0, 0.00, 0.10, 1.00, 0.10, 0, 0, 18.3241),
            new Data(Option.Type.Call,  95, 100, 100.0, 0.00, 0.10, 1.00, 0.20, 0, 0, 26.0731),
            new Data(Option.Type.Call,  95, 100, 100.0, 0.00, 0.10, 1.00, 0.30, 0, 0, 34.7116),
            new Data(Option.Type.Call, 100, 100, 100.0, 0.00, 0.10, 1.00, 0.10, 0, 0, 13.8000),
            new Data(Option.Type.Call, 100, 100, 100.0, 0.00, 0.10, 1.00, 0.20, 0, 0, 21.5489),
            new Data(Option.Type.Call, 100, 100, 100.0, 0.00, 0.10, 1.00, 0.30, 0, 0, 30.1874),
            new Data(Option.Type.Call, 105, 100, 100.0, 0.00, 0.10, 1.00, 0.10, 0, 0,  9.5445),
            new Data(Option.Type.Call, 105, 100, 100.0, 0.00, 0.10, 1.00, 0.20, 0, 0, 17.2965),
            new Data(Option.Type.Call, 105, 100, 100.0, 0.00, 0.10, 1.00, 0.30, 0, 0, 25.9002),
            // Puts
            new Data(Option.Type.Put,   95, 100, 100.0, 0.00, 0.10, 0.50, 0.10, 0, 0,  0.6899),
            new Data(Option.Type.Put,   95, 100, 100.0, 0.00, 0.10, 0.50, 0.20, 0, 0,  4.4448),
            new Data(Option.Type.Put,   95, 100, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0,  8.9213),
            new Data(Option.Type.Put,  100, 100, 100.0, 0.00, 0.10, 0.50, 0.10, 0, 0,  3.3917),
            new Data(Option.Type.Put,  100, 100, 100.0, 0.00, 0.10, 0.50, 0.20, 0, 0,  8.3177),
            new Data(Option.Type.Put,  100, 100, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 13.1579),
            new Data(Option.Type.Put,  105, 100, 100.0, 0.00, 0.10, 0.50, 0.10, 0, 0,  8.1478),
            new Data(Option.Type.Put,  105, 100, 100.0, 0.00, 0.10, 0.50, 0.20, 0, 0, 13.0739),
            new Data(Option.Type.Put,  105, 100, 100.0, 0.00, 0.10, 0.50, 0.30, 0, 0, 17.9140),
            new Data(Option.Type.Put,   95, 100, 100.0, 0.00, 0.10, 1.00, 0.10, 0, 0,  1.0534),
            new Data(Option.Type.Put,   95, 100, 100.0, 0.00, 0.10, 1.00, 0.20, 0, 0,  6.2813),
            new Data(Option.Type.Put,   95, 100, 100.0, 0.00, 0.10, 1.00, 0.30, 0, 0, 12.2376),
            new Data(Option.Type.Put,  100, 100, 100.0, 0.00, 0.10, 1.00, 0.10, 0, 0,  3.8079),
            new Data(Option.Type.Put,  100, 100, 100.0, 0.00, 0.10, 1.00, 0.20, 0, 0, 10.1294),
            new Data(Option.Type.Put,  100, 100, 100.0, 0.00, 0.10, 1.00, 0.30, 0, 0, 16.3889),
            new Data(Option.Type.Put,  105, 100, 100.0, 0.00, 0.10, 1.00, 0.10, 0, 0,  8.3321),
            new Data(Option.Type.Put,  105, 100, 100.0, 0.00, 0.10, 1.00, 0.20, 0, 0, 14.6536),
            new Data(Option.Type.Put,  105, 100, 100.0, 0.00, 0.10, 1.00, 0.30, 0, 0, 20.9130),
        };

        final DayCounter dc = new Actual360();
        final Date today = today();

        for (final Data d : values) {
            final BlackScholesMertonProcess process = makeProcess(today, d.s, d.q, d.r, d.v, dc);
            final Exercise exercise = new EuropeanExercise(today.add(timeToDays(d.t)));
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(d.type, d.strike);
            final ContinuousFixedLookbackOption option =
                    new ContinuousFixedLookbackOption(d.minmax, payoff, exercise);
            option.setPricingEngine(new AnalyticContinuousFixedLookbackEngine(process));

            final double npv = option.NPV();
            assertEquals("type=" + d.type + " s=" + d.s + " strike=" + d.strike
                    + " r=" + d.r + " t=" + d.t + " v=" + d.v,
                    d.result, npv, TOL);
        }
    }

    @Test
    public void testAnalyticContinuousPartialFloatingLookback() {
        // Data from test-suite/lookbackoptions.cpp testAnalyticContinuousPartialFloatingLookback
        // Haug 2006 pg.146 (subset — 6 representative rows).
        final Data[] values = new Data[] {
            new Data(Option.Type.Call, 0,  90,  90, 0, 0.06, 1, 0.1, 1, 0.25,  8.6524),
            new Data(Option.Type.Call, 0,  90,  90, 0, 0.06, 1, 0.1, 1, 0.50,  9.2128),
            new Data(Option.Type.Call, 0,  90,  90, 0, 0.06, 1, 0.1, 1, 0.75,  9.5567),
            new Data(Option.Type.Put,  0,  90,  90, 0, 0.06, 1, 0.1, 1, 0.25,  2.7189),
            new Data(Option.Type.Put,  0,  90,  90, 0, 0.06, 1, 0.1, 1, 0.50,  3.4639),
            new Data(Option.Type.Put,  0,  90,  90, 0, 0.06, 1, 0.1, 1, 0.75,  4.1912),
        };

        final DayCounter dc = new Actual360();
        final Date today = today();

        for (final Data d : values) {
            final BlackScholesMertonProcess process = makeProcess(today, d.s, d.q, d.r, d.v, dc);
            final Date matDate = today.add(timeToDays(d.t));
            final Date lookbackEnd = today.add(timeToDays(d.t1));
            final Exercise exercise = new EuropeanExercise(matDate);
            final TypePayoff payoff = new FloatingTypePayoff(d.type);
            final ContinuousPartialFloatingLookbackOption option =
                    new ContinuousPartialFloatingLookbackOption(d.minmax, d.l, lookbackEnd, payoff, exercise);
            option.setPricingEngine(new AnalyticContinuousPartialFloatingLookbackEngine(process));

            final double npv = option.NPV();
            assertEquals("type=" + d.type + " s=" + d.s + " minmax=" + d.minmax
                    + " l=" + d.l + " t1=" + d.t1,
                    d.result, npv, TOL);
        }
    }

    @Test
    public void testAnalyticContinuousPartialFixedLookback() {
        // Data from test-suite/lookbackoptions.cpp testAnalyticContinuousPartialFixedLookback
        // Haug 2006 pg.148 (subset — 6 representative rows).
        // Note: t1 is the lookback PERIOD START in C++ (not "end") for fixed-strike.
        final Data[] values = new Data[] {
            new Data(Option.Type.Call,  90, 0, 100, 0, 0.06, 1, 0.1, 0, 0.25, 20.2845),
            new Data(Option.Type.Call,  90, 0, 100, 0, 0.06, 1, 0.1, 0, 0.50, 19.6239),
            new Data(Option.Type.Call,  90, 0, 100, 0, 0.06, 1, 0.1, 0, 0.75, 18.6244),
            new Data(Option.Type.Put,  110, 0, 100, 0, 0.06, 1, 0.1, 0, 0.25, 12.6978),
            new Data(Option.Type.Put,  110, 0, 100, 0, 0.06, 1, 0.1, 0, 0.50, 10.9492),
            new Data(Option.Type.Put,  110, 0, 100, 0, 0.06, 1, 0.1, 0, 0.75,  9.1555),
        };

        final DayCounter dc = new Actual360();
        final Date today = today();

        for (final Data d : values) {
            final BlackScholesMertonProcess process = makeProcess(today, d.s, d.q, d.r, d.v, dc);
            final Date matDate = today.add(timeToDays(d.t));
            final Date lookbackStart = today.add(timeToDays(d.t1));
            final Exercise exercise = new EuropeanExercise(matDate);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(d.type, d.strike);
            final ContinuousPartialFixedLookbackOption option =
                    new ContinuousPartialFixedLookbackOption(lookbackStart, payoff, exercise);
            option.setPricingEngine(new AnalyticContinuousPartialFixedLookbackEngine(process));

            final double npv = option.NPV();
            assertEquals("type=" + d.type + " strike=" + d.strike + " s=" + d.s
                    + " t=" + d.t + " t1=" + d.t1,
                    d.result, npv, TOL);
        }
    }

    @Ignore("Phase 5i.5b — requires MCLookbackEngine port (MC continuous lookback engine)")
    @Test
    public void testMonteCarloLookback() { fail("not implemented"); }
}

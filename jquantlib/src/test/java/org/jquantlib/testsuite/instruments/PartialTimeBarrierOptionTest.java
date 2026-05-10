/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.exoticoptions.PartialBarrier;
import org.jquantlib.experimental.exoticoptions.PartialTimeBarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.barrier.AnalyticPartialTimeBarrierOptionEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase Body-Fill-5 port of {@code test-suite/partialtimebarrieroption.cpp}
 * v1.42.1 (328 LOC, 3 cases).
 *
 * <p>Exercises the partial-time barrier option (Heynen-Kat 1994):
 * call analytic engine values, put analytic engine values, and put-call
 * symmetry across barrier types.
 *
 * <p><strong>Body-fills (Phase Body-Fill-5):</strong>
 * <ul>
 *   <li>{@link #testAnalyticEngine()} — 20 reference cases (call, DownOut,
 *       EndB1) across spot / strike / cover-event-date grids.
 *   <li>{@link #testAnalyticEnginePutOption()} — 20 reference cases
 *       (put, UpOut, EndB1).
 * </ul>
 *
 * <p><strong>Carry-forward to Phase 5k.5</strong>:
 * <ul>
 *   <li>testPutCallSymmetry — symmetry across barrier types is unimplemented;
 *       awaiting a comprehensive review of which {@link PartialBarrier} values
 *       in Java map to {@code PartialBarrier::Start} in C++.
 * </ul>
 *
 * <p>Source: {@code test-suite/partialtimebarrieroption.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class PartialTimeBarrierOptionTest {

    public PartialTimeBarrierOptionTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_SYMMETRY =
            "Phase 5k.5 — partial-time barrier put-call symmetry needs "
          + "comprehensive review of how Java {@link PartialBarrier} maps to "
          + "C++ PartialBarrier::Start (Java has Start / EndB1 / EndB2 only).";

    /** Single C++ {@code TestCase} row. */
    private static final class TestCase {
        final double underlying;
        final double strike;
        final int days;
        final double result;

        TestCase(final double underlying, final double strike,
                 final int days, final double result) {
            this.underlying = underlying;
            this.strike = strike;
            this.days = days;
            this.result = result;
        }
    }

    private GeneralizedBlackScholesProcess makeBSMProcess(
            final Date today, final SimpleQuote spot,
            final double q, final double r, final double v,
            final DayCounter dc) {
        final Handle<? extends Quote> spotH = new Handle<SimpleQuote>(spot);
        final Calendar cal = new NullCalendar();
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, q, dc, Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, r, dc, Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, v, dc));
        return new GeneralizedBlackScholesProcess(spotH, qTS, rTS, volTS);
    }

    /**
     * Port of C++ {@code partialtimebarrieroption.cpp::testAnalyticEngine}.
     *
     * <p>Heynen-Kat 1994 analytic call branch.  20 reference cases.
     */
    @Test
    public void testAnalyticEngine() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final Option.Type type = Option.Type.Call;
        final DayCounter dc = new Actual360();
        final Date maturity = today.add(360);
        final Exercise exercise = new EuropeanExercise(maturity);
        final double barrier = 100.0;
        final double rebate = 0.0;

        final SimpleQuote spot = new SimpleQuote();
        final GeneralizedBlackScholesProcess process = makeBSMProcess(
                today, spot, 0.0, 0.1, 0.25, dc);

        final AnalyticPartialTimeBarrierOptionEngine engine =
                new AnalyticPartialTimeBarrierOptionEngine(process);

        final TestCase[] cases = new TestCase[] {
            new TestCase(95.0, 90.0,   1,  0.0393),
            new TestCase(95.0, 110.0,  1,  0.0000),
            new TestCase(105.0, 90.0,  1,  9.8751),
            new TestCase(105.0, 110.0, 1,  6.2303),

            new TestCase(95.0, 90.0,   90,  6.2747),
            new TestCase(95.0, 110.0,  90,  3.7352),
            new TestCase(105.0, 90.0,  90, 15.6324),
            new TestCase(105.0, 110.0, 90,  9.6812),

            new TestCase(95.0, 90.0,   180, 10.3345),
            new TestCase(95.0, 110.0,  180,  5.8712),
            new TestCase(105.0, 90.0,  180, 19.2896),
            new TestCase(105.0, 110.0, 180, 11.6055),

            new TestCase(95.0, 90.0,   270, 13.4342),
            new TestCase(95.0, 110.0,  270,  7.1270),
            new TestCase(105.0, 90.0,  270, 22.0753),
            new TestCase(105.0, 110.0, 270, 12.7342),

            new TestCase(95.0, 90.0,   359, 16.8576),
            new TestCase(95.0, 110.0,  359,  7.5763),
            new TestCase(105.0, 90.0,  359, 25.1488),
            new TestCase(105.0, 110.0, 359, 13.1376)
        };

        for (final TestCase c : cases) {
            final Date coverEventDate = today.add(c.days);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, c.strike);
            final PartialTimeBarrierOption option = new PartialTimeBarrierOption(
                    BarrierType.DownOut, PartialBarrier.EndB1,
                    barrier, rebate, coverEventDate, payoff, exercise);
            option.setPricingEngine(engine);

            spot.setValue(c.underlying);
            final double calculated = option.NPV();
            final double error = Math.abs(calculated - c.result);
            final double tolerance = 1e-4;
            if (error > tolerance) {
                fail("Partial-time-barrier (call) value: expected=" + c.result
                        + " calculated=" + calculated
                        + " error=" + error + " tolerance=" + tolerance
                        + "\n    underlying=" + c.underlying
                        + " strike=" + c.strike + " days=" + c.days);
            }
        }
        assertTrue("testAnalyticEngine passed " + cases.length + " cases", true);
    }

    /**
     * Port of C++ {@code partialtimebarrieroption.cpp::testAnalyticEnginePutOption}.
     *
     * <p>Heynen-Kat 1994 analytic put branch.  20 reference cases.
     */
    @Test
    public void testAnalyticEnginePutOption() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final Option.Type type = Option.Type.Put;
        final DayCounter dc = new Actual360();
        final Date maturity = today.add(360);
        final Exercise exercise = new EuropeanExercise(maturity);
        final double barrier = 100.0;
        final double rebate = 0.0;

        final SimpleQuote spot = new SimpleQuote();
        final GeneralizedBlackScholesProcess process = makeBSMProcess(
                today, spot, 0.0, 0.1, 0.25, dc);

        final AnalyticPartialTimeBarrierOptionEngine engine =
                new AnalyticPartialTimeBarrierOptionEngine(process);

        final TestCase[] cases = new TestCase[] {
            new TestCase(95.0, 90.0,   1,  1.5551),
            new TestCase(95.0, 95.0,   1,  2.0589),
            new TestCase(90.0, 95.0,   1,  4.4512),
            new TestCase(99.0, 90.0,   1,  0.3404),

            new TestCase(95.0, 90.0,   90,  2.4181),
            new TestCase(95.0, 95.0,   90,  3.2257),
            new TestCase(90.0, 95.0,   90,  5.0624),
            new TestCase(99.0, 90.0,   90,  1.5992),

            new TestCase(95.0, 90.0,   180,  3.0021),
            new TestCase(95.0, 95.0,   180,  4.0617),
            new TestCase(90.0, 95.0,   180,  5.7960),
            new TestCase(99.0, 90.0,   180,  2.1903),

            new TestCase(95.0, 90.0,   270,  3.4194),
            new TestCase(95.0, 95.0,   270,  4.7362),
            new TestCase(90.0, 95.0,   270,  6.4370),
            new TestCase(99.0, 90.0,   270,  2.6025),

            new TestCase(95.0, 90.0,   359,  3.5965),
            new TestCase(95.0, 95.0,   359,  5.1865),
            new TestCase(90.0, 95.0,   359,  6.8782),
            new TestCase(99.0, 90.0,   359,  2.7759)
        };

        for (final TestCase c : cases) {
            final Date coverEventDate = today.add(c.days);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, c.strike);
            final PartialTimeBarrierOption option = new PartialTimeBarrierOption(
                    BarrierType.UpOut, PartialBarrier.EndB1,
                    barrier, rebate, coverEventDate, payoff, exercise);
            option.setPricingEngine(engine);

            spot.setValue(c.underlying);
            final double calculated = option.NPV();
            final double error = Math.abs(calculated - c.result);
            final double tolerance = 1e-4;
            if (error > tolerance) {
                fail("Partial-time-barrier (put) value: expected=" + c.result
                        + " calculated=" + calculated
                        + " error=" + error + " tolerance=" + tolerance
                        + "\n    underlying=" + c.underlying
                        + " strike=" + c.strike + " days=" + c.days);
            }
        }
        assertTrue("testAnalyticEnginePutOption passed " + cases.length + " cases", true);
    }

    @Ignore(REASON_SYMMETRY)
    @Test
    public void testPutCallSymmetry() { fail("not implemented"); }
}

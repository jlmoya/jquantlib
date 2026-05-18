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
 *   <li>{@link #testPutCallSymmetry()} — Phase 5e.5b-CFC-d Round-3
 *       un-ignore: the C++ test uses only {@code PartialBarrier::EndB1}
 *       (which Java has as {@link PartialBarrier#EndB1}). 10 cases
 *       cross-validating Bjerksund-Stensland-style put-call symmetry across
 *       DownOut/UpOut pairings.
 * </ul>
 *
 * <p>Source: {@code test-suite/partialtimebarrieroption.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class PartialTimeBarrierOptionTest {

    public PartialTimeBarrierOptionTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

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

    private static GeneralizedBlackScholesProcess makeBSMProcess(
            final Date today, final SimpleQuote spotQuote,
            final double q, final double r, final double v,
            final DayCounter dc) {
        final Handle<Quote> spotH = new Handle<Quote>(spotQuote);
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

    /**
     * Single PutCall-symmetry test row.
     */
    private static final class SymmetryCase {
        final double callStrike;
        final double callBarrier;
        final BarrierType callType;
        final double putStrike;
        final double putBarrier;
        final int days;
        final BarrierType putType;

        SymmetryCase(final double callStrike, final double callBarrier,
                     final BarrierType callType,
                     final double putStrike, final double putBarrier,
                     final int days, final BarrierType putType) {
            this.callStrike = callStrike;
            this.callBarrier = callBarrier;
            this.callType = callType;
            this.putStrike = putStrike;
            this.putBarrier = putBarrier;
            this.days = days;
            this.putType = putType;
        }
    }

    /**
     * Port of C++ {@code partialtimebarrieroption.cpp::testPutCallSymmetry}
     * (lines 230-324, v1.42.1).
     *
     * <p>Cross-validates the partial-time barrier put-call symmetry
     * relation P = (Kp/S) * C, where Kp is the put strike, S the spot,
     * and C the symmetric-pair call NPV. Uses {@link PartialBarrier#EndB1}
     * (the only enum value the C++ test exercises).
     *
     * <p>Phase 5e.5b-CFC-d Round-3 un-ignore: the original
     * {@code REASON_SYMMETRY} reason text was inaccurate — Java's
     * {@link PartialBarrier} has all three values (Start, EndB1, EndB2),
     * and the C++ test only exercises {@code EndB1}.
     */
    @Test
    public void testPutCallSymmetry() {
        QL.info("Testing put-call symmetry for the partial-time barrier option...");

        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual360();
        final Date maturity = today.add(360);
        final Exercise exercise = new EuropeanExercise(maturity);
        final double r = 0.01;
        final double q = 0.0;
        final double rebate = 0.0;
        final double spotPrice = 100.0;
        final double v = 0.25;

        final Calendar cal = new NullCalendar();

        final SimpleQuote spot = new SimpleQuote();
        final Handle<Quote> underlying = new Handle<Quote>(spot);

        // Call leg uses (q, r); put leg swaps to (r, q).
        final Handle<YieldTermStructure> dividendTSCall = new Handle<YieldTermStructure>(
                new FlatForward(today, q, dc, Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> riskFreeTSCall = new Handle<YieldTermStructure>(
                new FlatForward(today, r, dc, Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> dividendTSPut = new Handle<YieldTermStructure>(
                new FlatForward(today, r, dc, Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> riskFreeTSPut = new Handle<YieldTermStructure>(
                new FlatForward(today, q, dc, Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> blackVolTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, v, dc));

        final GeneralizedBlackScholesProcess callProcess =
                new GeneralizedBlackScholesProcess(underlying,
                        dividendTSCall, riskFreeTSCall, blackVolTS);
        final GeneralizedBlackScholesProcess putProcess =
                new GeneralizedBlackScholesProcess(underlying,
                        dividendTSPut, riskFreeTSPut, blackVolTS);
        final AnalyticPartialTimeBarrierOptionEngine callEngine =
                new AnalyticPartialTimeBarrierOptionEngine(callProcess);
        final AnalyticPartialTimeBarrierOptionEngine putEngine =
                new AnalyticPartialTimeBarrierOptionEngine(putProcess);

        final SymmetryCase[] cases = new SymmetryCase[] {
            new SymmetryCase(105.2631, 95.2380, BarrierType.DownOut,
                             95.0,    105.0,   1,   BarrierType.UpOut),
            new SymmetryCase(105.2631, 95.2380, BarrierType.DownOut,
                             95.0,    105.0,   90,  BarrierType.UpOut),
            new SymmetryCase(105.2631, 95.2380, BarrierType.DownOut,
                             95.0,    105.0,   180, BarrierType.UpOut),
            new SymmetryCase(105.2631, 95.2380, BarrierType.DownOut,
                             95.0,    105.0,   270, BarrierType.UpOut),
            new SymmetryCase(105.2631, 95.2380, BarrierType.DownOut,
                             95.0,    105.0,   359, BarrierType.UpOut),

            new SymmetryCase(110.0,   120.0,   BarrierType.UpOut,
                             90.9090, 83.3333, 1,   BarrierType.DownOut),
            new SymmetryCase(110.0,   120.0,   BarrierType.UpOut,
                             90.9090, 83.3333, 90,  BarrierType.DownOut),
            new SymmetryCase(110.0,   120.0,   BarrierType.UpOut,
                             90.9090, 83.3333, 180, BarrierType.DownOut),
            new SymmetryCase(110.0,   120.0,   BarrierType.UpOut,
                             90.9090, 83.3333, 270, BarrierType.DownOut),
            new SymmetryCase(110.0,   120.0,   BarrierType.UpOut,
                             90.9090, 83.3333, 359, BarrierType.DownOut)
        };

        final double tolerance = 1e-4;
        for (final SymmetryCase c : cases) {
            final Date coverEventDate = today.add(c.days);

            final StrikedTypePayoff putPayoff =
                    new PlainVanillaPayoff(Option.Type.Put, c.putStrike);
            final PartialTimeBarrierOption putOption =
                    new PartialTimeBarrierOption(c.putType, PartialBarrier.EndB1,
                            c.putBarrier, rebate, coverEventDate, putPayoff, exercise);
            putOption.setPricingEngine(putEngine);

            final StrikedTypePayoff callPayoff =
                    new PlainVanillaPayoff(Option.Type.Call, c.callStrike);
            final PartialTimeBarrierOption callOption =
                    new PartialTimeBarrierOption(c.callType, PartialBarrier.EndB1,
                            c.callBarrier, rebate, coverEventDate, callPayoff, exercise);
            callOption.setPricingEngine(callEngine);

            spot.setValue(spotPrice);
            final double putValue = putOption.NPV();
            final double callValue = callOption.NPV();
            final double callAmount = c.putStrike / spotPrice;
            final double error = Math.abs(putValue - callAmount * callValue);
            if (error > tolerance) {
                fail("Failed to reproduce put-call symmetry for the partial-time barrier options"
                        + "\n    days:        " + c.days
                        + "\n    putType:     " + c.putType
                        + "\n    callType:    " + c.callType
                        + "\n    putValue:    " + putValue
                        + "\n    callValue:   " + callValue
                        + "\n    callAmount:  " + callAmount
                        + "\n    error:       " + error
                        + "\n    tolerance:   " + tolerance);
            }
        }
        assertTrue("testPutCallSymmetry passed " + cases.length + " cases", true);
    }
}

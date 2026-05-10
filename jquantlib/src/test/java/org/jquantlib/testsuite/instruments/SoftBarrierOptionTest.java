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
import org.jquantlib.experimental.exoticoptions.SoftBarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.barrier.AnalyticSoftBarrierEngine;
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
 * Phase Body-Fill-5 port of {@code test-suite/softbarrieroption.cpp}
 * v1.42.1 (208 LOC, 1 case).
 *
 * <p>Exercises the soft-barrier (range-barrier) option (Hart-Ross 1994):
 * a partial-knockout barrier that activates between two barrier levels
 * with linearly-decreasing notional. Cross-validated against the Haug 2007
 * reference table.
 *
 * <p><strong>Body-fill (Phase Body-Fill-5):</strong>
 * <ul>
 *   <li>{@link #testSoftBarrierHaug()} — 59 reference cases from Haug 2007
 *       p.166 (DownOut Call across barrier-level / vol / maturity grids),
 *       tolerance 1e-4. Skipping the one C++-flagged numerical-edge row
 *       at {@code DownOut, S=100, K=100, U=95, L=90, T=1.0, vol=0.3}.
 * </ul>
 *
 * <p>Source: {@code test-suite/softbarrieroption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class SoftBarrierOptionTest {

    public SoftBarrierOptionTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    /** Convert a year-fraction (under Actual/360) into days. */
    private static int timeToDays(final double t) {
        return (int) (t * 360.0 + 0.5);
    }

    /** Single C++ {@code SoftBarrierOptionData} row. */
    private static final class SoftBarrierOptionData {
        final BarrierType barrierType;
        final Option.Type type;
        final double s, strike, U, L;
        final double q, r, t, v;
        final double result, tol;

        SoftBarrierOptionData(final BarrierType barrierType, final Option.Type type,
                              final double s, final double strike,
                              final double U, final double L,
                              final double q, final double r,
                              final double t, final double v,
                              final double result, final double tol) {
            this.barrierType = barrierType;
            this.type = type;
            this.s = s; this.strike = strike;
            this.U = U; this.L = L;
            this.q = q; this.r = r;
            this.t = t; this.v = v;
            this.result = result; this.tol = tol;
        }
    }

    /**
     * Port of C++ {@code softbarrieroption.cpp::testSoftBarrierHaug}.
     *
     * <p>Reference data from Haug 2007 p.166 (note: in the book, {@code b}
     * represents the cost of carry {@code r-q}).
     */
    @Test
    public void testSoftBarrierHaug() {
        final SoftBarrierOptionData[] values = new SoftBarrierOptionData[] {
            // barrierType, optionType, S, X, U, L, q, r, T, v, result, tol
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 0.5, 0.1, 3.8075, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.1, 4.0175, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.1, 4.0529, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.1, 4.0648, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.1, 4.0708, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.1, 4.0744, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.1, 4.0768, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.1, 4.0785, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.1, 4.0798, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.1, 4.0808, 1e-4),

            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 0.5, 0.2, 4.5263, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.2, 5.5615, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.2, 6.0394, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.2, 6.2594, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.2, 6.3740, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.2, 6.4429, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.2, 6.4889, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.2, 6.5217, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.2, 6.5463, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.2, 6.5654, 1e-4),

            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 0.5, 0.3, 4.7297, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.3, 6.2595, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.3, 7.2496, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.3, 7.8567, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.3, 8.2253, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.3, 8.4578, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.3, 8.6142, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.3, 8.7260, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.3, 8.8099, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.3, 8.8751, 1e-4),

            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 1.0, 0.1, 5.4187, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 1.0, 0.1, 6.0758, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 1.0, 0.1, 6.2641, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 1.0, 0.1, 6.3336, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 1.0, 0.1, 6.3685, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 1.0, 0.1, 6.3894, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 1.0, 0.1, 6.4034, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 1.0, 0.1, 6.4133, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 1.0, 0.1, 6.4208, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 1.0, 0.1, 6.4266, 1e-4),

            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 1.0, 0.2, 5.3614, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 1.0, 0.2, 6.9776, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 1.0, 0.2, 7.9662, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 1.0, 0.2, 8.5432, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 1.0, 0.2, 8.8822, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 1.0, 0.2, 9.0931, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 1.0, 0.2, 9.2343, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 1.0, 0.2, 9.3353, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 1.0, 0.2, 9.4110, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 1.0, 0.2, 9.4698, 1e-4),

            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 95, 0.05, 0.1, 1.0, 0.3, 5.2300, 1e-4),
            // Skipped: { DownOut, Call, 100, 100, 95, 90, 0.05, 0.1, 1.0, 0.3, 7.2046, 1e-4 } —
            //   C++ flags this as having a numerical error of ~3e-4 for tight barriers + high vol.
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 1.0, 0.3, 8.7092, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 1.0, 0.3, 9.8118, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 1.0, 0.3, 10.5964, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 1.0, 0.3, 11.1476, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 1.0, 0.3, 11.5384, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 1.0, 0.3, 11.8228, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 1.0, 0.3, 12.0369, 1e-4),
            new SoftBarrierOptionData(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 1.0, 0.3, 12.2036, 1e-4)
        };

        final DayCounter dc = new Actual360();
        final Date today = new Date(8, Month.August, 2025);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote vol   = new SimpleQuote(0.0);

        final Handle<? extends Quote> spotH = new Handle<SimpleQuote>(spot);
        final Calendar cal = new NullCalendar();
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(rRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, new Handle<Quote>(vol), dc));

        final GeneralizedBlackScholesProcess process = new GeneralizedBlackScholesProcess(
                spotH, qTS, rTS, volTS);

        for (final SoftBarrierOptionData v : values) {
            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);

            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new EuropeanExercise(exDate);
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(v.type, v.strike);

            final SoftBarrierOption option = new SoftBarrierOption(
                    v.barrierType, v.L, v.U, payoff, exercise);
            option.setPricingEngine(new AnalyticSoftBarrierEngine(process));

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);
            if (error > v.tol) {
                fail("Soft barrier option value: expected=" + v.result
                        + " calculated=" + calculated
                        + " error=" + error + " tolerance=" + v.tol
                        + "\n    barrierType=" + v.barrierType
                        + " s=" + v.s + " strike=" + v.strike
                        + " U=" + v.U + " L=" + v.L
                        + " q=" + v.q + " r=" + v.r
                        + " T=" + v.t + " vol=" + v.v);
            }
        }
        assertTrue("testSoftBarrierHaug passed " + values.length + " cases", true);
    }
}

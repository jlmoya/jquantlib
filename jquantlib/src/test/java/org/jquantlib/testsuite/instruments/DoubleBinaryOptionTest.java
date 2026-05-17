/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.barrieroption.DoubleBarrierOption;
import org.jquantlib.experimental.barrieroption.DoubleBarrierType;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.barrier.AnalyticDoubleBarrierBinaryEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-79 port of {@code test-suite/doublebinaryoption.cpp}
 * v1.42.1 (330 LOC, 2 cases).
 *
 * <p>Exercises the double-binary (double-barrier touch / no-touch) option
 * against the analytic Hui-series engine
 * ({@link AnalyticDoubleBarrierBinaryEngine}), cross-validated against
 * Haug 2007 reference values from "The complete guide to option pricing
 * formulas" 2nd Ed, p.181 (KnockOut/KIKO blocks) and from Haug's VBA
 * code (KnockIn/KOKI blocks).
 *
 * <p>The C++ test also cross-checks against a {@code BinomialDoubleBarrierEngine}
 * (CRR, 500 steps, tolerance 0.22). Java has the binomial engine for the
 * continuous (plain-vanilla) double-barrier payoff via
 * {@code experimental.barrieroption.BinomialDoubleBarrierEngine}, but no
 * {@code DiscretizedDoubleBarrier(Binary)Option} specialised for the
 * cash-or-nothing payoff — porting that adds non-trivial scope (binary
 * discretized payoff + lattice cell handling) and is deferred to
 * Phase 5e.5b-CFC-d-79.1.
 *
 * <p>{@link #testPdeDoubleBarrierWithAnalytical()} requires
 * {@code FdHestonDoubleBarrierEngine} + Heston-model wiring and is kept
 * {@code @Ignore}d as a separate carry-forward.
 *
 * <p>Tolerance: LOOSE 1e-4 — matches Haug's reported precision and the
 * C++ test-suite tolerance.
 *
 * <p>Source: {@code test-suite/doublebinaryoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class DoubleBinaryOptionTest {

    public DoubleBinaryOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_PDE =
            "Phase 5e.5b-CFC-d-79.2 — testPdeDoubleBarrierWithAnalytical requires "
          + "FdHestonDoubleBarrierEngine + HestonModel/HestonProcess infrastructure "
          + "not yet ported. Cross-validates BS-analytic vs Heston-PDE; covered by "
          + "the analytic-only testHaugValues for the binary engine itself.";

    private static final class HaugDouble {
        final DoubleBarrierType barrierType;
        final double barrierLo;
        final double barrierHi;
        final double cash;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        HaugDouble(final DoubleBarrierType barrierType,
                   final double barrierLo, final double barrierHi,
                   final double cash, final double s,
                   final double q, final double r, final double t, final double v,
                   final double result, final double tol) {
            this.barrierType = barrierType;
            this.barrierLo = barrierLo;
            this.barrierHi = barrierHi;
            this.cash = cash;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    /**
     * Haug 2007 p.181 (KnockOut/KIKO) and Haug VBA (KnockIn/KOKI) values
     * — one-touch double-barrier cash-or-nothing-at-hit.
     */
    private static final HaugDouble[] HAUG_VALUES = new HaugDouble[] {
        // barrierType,                  bar_lo, bar_hi, cash,   spot,    q,    r,    t,  vol,   value, tol
        new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  9.8716, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  8.9307, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  6.3272, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  1.9094, 1e-4),

        new HaugDouble(DoubleBarrierType.KnockOut, 85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  9.7961, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  7.2300, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  3.7100, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  0.4271, 1e-4),

        new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  8.9054, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  3.6752, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  0.7960, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  0.0059, 1e-4),

        new HaugDouble(DoubleBarrierType.KnockOut, 95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  3.6323, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  0.0911, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  0.0002, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  0.0000, 1e-4),

        new HaugDouble(DoubleBarrierType.KIKO,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  0.2402, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  1.4076, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  3.8160, 1e-4),

        new HaugDouble(DoubleBarrierType.KIKO,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.0075, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  0.9910, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  2.8098, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  4.6612, 1e-4),

        new HaugDouble(DoubleBarrierType.KIKO,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.2656, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  2.7954, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  4.4024, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  4.9266, 1e-4),

        new HaugDouble(DoubleBarrierType.KIKO,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  2.6285, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  4.7523, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  4.9096, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  4.9675, 1e-4),

        // following values calculated with haug's VBA code
        new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.0042, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  0.9450, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  3.5486, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  7.9663, 1e-4),

        new HaugDouble(DoubleBarrierType.KnockIn,  85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.0797, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  2.6458, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  6.1658, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  9.4486, 1e-4),

        new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.9704, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  6.2006, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  9.0798, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  9.8699, 1e-4),

        new HaugDouble(DoubleBarrierType.KnockIn,  95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  6.2434, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  9.7847, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  9.8756, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  9.8758, 1e-4),

        new HaugDouble(DoubleBarrierType.KOKI,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.0041, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  0.7080, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  2.1581, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     80.0, 120.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  4.2061, 1e-4),

        new HaugDouble(DoubleBarrierType.KOKI,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.0723, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  1.6663, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  3.3930, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     85.0, 115.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  4.8679, 1e-4),

        new HaugDouble(DoubleBarrierType.KOKI,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  0.7080, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  3.4424, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  4.7496, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     90.0, 110.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  5.0475, 1e-4),

        new HaugDouble(DoubleBarrierType.KOKI,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.10,  3.6524, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.20,  5.1256, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.30,  5.0763, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     95.0, 105.0, 10.0, 100.0, 0.02, 0.05, 0.25, 0.50,  5.0275, 1e-4),

        // degenerate cases
        new HaugDouble(DoubleBarrierType.KnockOut, 95.0, 105.0, 10.0,  80.0, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockOut, 95.0, 105.0, 10.0, 110.0, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  95.0, 105.0, 10.0,  80.0, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KnockIn,  95.0, 105.0, 10.0, 110.0, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     95.0, 105.0, 10.0,  80.0, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KIKO,     95.0, 105.0, 10.0, 110.0, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     95.0, 105.0, 10.0,  80.0, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
        new HaugDouble(DoubleBarrierType.KOKI,     95.0, 105.0, 10.0, 110.0, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
    };

    @Test
    public void testHaugValues() {
        QL.info("Testing cash-or-nothing double barrier options against Haug's values...");

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.01);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.25);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final HaugDouble value : HAUG_VALUES) {
            // strike is 0 (cash-or-nothing-at-touch); type=Call is a placeholder
            // (CashOrNothingPayoff does not branch on type for the double-barrier
            // binary engine — the engine reads only the cash amount).
            final StrikedTypePayoff payoff = new CashOrNothingPayoff(Option.Type.Call, 0.0, value.cash);

            final Date exDate = today.add(timeToDays(value.t));
            final Exercise exercise;
            if (value.barrierType == DoubleBarrierType.KIKO || value.barrierType == DoubleBarrierType.KOKI) {
                exercise = new AmericanExercise(today, exDate);
            } else {
                exercise = new EuropeanExercise(exDate);
            }

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final DoubleBarrierOption opt = new DoubleBarrierOption(
                    value.barrierType, value.barrierLo, value.barrierHi,
                    0.0, payoff, exercise);
            opt.setPricingEngine(new AnalyticDoubleBarrierBinaryEngine(stochProcess));

            final double calculated = opt.NPV();
            final double expected = value.result;
            final double error = Math.abs(calculated - expected);
            final String msg = String.format(
                    "AnalyticDoubleBarrierBinaryEngine: type=%s barriers=[%.2f,%.2f] cash=%.2f s=%.2f "
                            + "q=%.2f r=%.2f t=%.2f v=%.2f -> expected=%.4f calculated=%.4f "
                            + "error=%.4g (tol=%.4g)",
                    value.barrierType, value.barrierLo, value.barrierHi, value.cash,
                    value.s, value.q, value.r, value.t, value.v,
                    expected, calculated, error, value.tol);
            assertTrue(msg, error <= value.tol);
        }
    }

    @Ignore(REASON_PDE)
    @Test
    public void testPdeDoubleBarrierWithAnalytical() {
        // Phase 5e.5b-CFC-d-79.2 carry-forward: needs FdHestonDoubleBarrierEngine
        // + HestonModel/HestonProcess. The analytic Hui engine itself is fully
        // exercised by testHaugValues above.
    }
}

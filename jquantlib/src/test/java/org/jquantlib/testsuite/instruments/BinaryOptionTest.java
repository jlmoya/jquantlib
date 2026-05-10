/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.barrier.AnalyticBinaryBarrierEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Test;

/**
 * Java port of {@code test-suite/binaryoption.cpp} v1.42.1
 * (Phase Body-Fill-2; AnalyticBinaryBarrierEngine + CashOrNothingPayoff
 * + AssetOrNothingPayoff are all available in JQuantLib).
 *
 * <p>Exercises one-touch cash-or-nothing-at-hit and asset-or-nothing-at-hit
 * barrier options against the Haug 2007 reference table (Option pricing
 * formulas 2nd Ed., E.G. Haug, McGraw-Hill 2007 pag. 180 — cases 13, 14,
 * 17, 18, 21, 22, 25, 26 plus VBA-derived edge cases including
 * "barrier-touched" degenerate states).
 */
public class BinaryOptionTest {

    public BinaryOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** C++ test-suite helper {@code timeToDays(Time t, Integer daysPerYear=360)}. */
    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    /** Single C++ {@code BinaryOptionData} row. */
    private static final class BinaryOptionData {
        final BarrierType barrierType;
        final double barrier;
        final double cash;
        final Option.Type type;
        final double strike;
        final double s, q, r, t, v;
        final double result, tol;
        BinaryOptionData(final BarrierType barrierType, final double barrier,
                         final double cash, final Option.Type type, final double strike,
                         final double s, final double q, final double r,
                         final double t, final double v,
                         final double result, final double tol) {
            this.barrierType = barrierType; this.barrier = barrier;
            this.cash = cash; this.type = type; this.strike = strike;
            this.s = s; this.q = q; this.r = r; this.t = t; this.v = v;
            this.result = result; this.tol = tol;
        }
    }

    @Test
    public void testCashOrNothingHaugValues() {
        QL.info("Testing cash-or-nothing barrier options against Haug's values...");

        final BinaryOptionData[] values = {
            // type, barrier, cash, type, strike, spot, q, r, t, v, expected, tol
            new BinaryOptionData(BarrierType.DownIn,  100.00, 15.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  4.9289, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 15.00, Option.Type.Call,  98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  6.2150, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Call, 102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  5.8926, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Call,  98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  7.4519, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 15.00, Option.Type.Put,  102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  4.4314, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 15.00, Option.Type.Put,   98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  3.1454, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Put,  102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  5.3297, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Put,   98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  3.7704, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 15.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  4.8758, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 15.00, Option.Type.Call,  98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  4.9081, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 15.00, Option.Type.Call, 102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 15.00, Option.Type.Call,  98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  0.0407, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 15.00, Option.Type.Put,  102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  0.0323, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 15.00, Option.Type.Put,   98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 15.00, Option.Type.Put,  102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  3.0461, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 15.00, Option.Type.Put,   98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  3.0054, 1e-4),
            // VBA cross-checks
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Call, 102.00,  95.00,-0.14, 0.10, 0.5, 0.20,  8.6806, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Call, 102.00,  95.00, 0.03, 0.10, 0.5, 0.20,  5.3112, 1e-4),
            // degenerate (barrier already touched)
            new BinaryOptionData(BarrierType.DownIn,  100.00, 15.00, Option.Type.Call,  98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  7.4926, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Call,  98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 11.1231, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 15.00, Option.Type.Put,  102.00,  98.00, 0.00, 0.10, 0.5, 0.20,  7.1344, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 15.00, Option.Type.Put,  102.00, 101.00, 0.00, 0.10, 0.5, 0.20,  5.9299, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 15.00, Option.Type.Call,  98.00,  99.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 15.00, Option.Type.Call,  98.00, 101.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 15.00, Option.Type.Put,   98.00,  99.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 15.00, Option.Type.Put,   98.00, 101.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4)
        };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.01);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.25);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(today, vol, dc));

        for (final BinaryOptionData v : values) {
            final StrikedTypePayoff payoff = new CashOrNothingPayoff(v.type, v.strike, v.cash);
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise amExercise = new AmericanExercise(today, exDate, true);

            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot), qTS, rTS, volTS);
            final PricingEngine engine = new AnalyticBinaryBarrierEngine(stochProcess);

            final BarrierOption opt = new BarrierOption(
                    v.barrierType, v.barrier, 0.0, payoff, amExercise);
            opt.setPricingEngine(engine);

            final double calculated = opt.NPV();
            final double error = Math.abs(calculated - v.result);
            if (error > v.tol) {
                fail("failed to reproduce cash-or-nothing barrier option:"
                        + "\n    expected:   " + v.result
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + v.tol
                        + "\n    barrierType=" + v.barrierType
                        + " barrier=" + v.barrier + " cash=" + v.cash
                        + " strike=" + v.strike + " s=" + v.s);
            }
        }
    }

    @Test
    public void testAssetOrNothingHaugValues() {
        QL.info("Testing asset-or-nothing barrier options against Haug's values...");

        // Haug 2007 pag. 180: cases 15,16,19,20,23,24,27,28 + VBA cross-checks +
        // degenerate "barrier-touched" cases. Cash field is unused here (asset-or-nothing).
        final BinaryOptionData[] values = {
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.0, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  37.2782, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.0, Option.Type.Call,  98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  45.8530, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.0, Option.Type.Call, 102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  44.5294, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.0, Option.Type.Call,  98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  54.9262, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.0, Option.Type.Put,  102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  27.5644, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.0, Option.Type.Put,   98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  18.9896, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.0, Option.Type.Put,  102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  33.1723, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.0, Option.Type.Put,   98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  22.7755, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.0, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  39.9391, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.0, Option.Type.Call,  98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  40.1574, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.0, Option.Type.Call, 102.00,  95.00, 0.00, 0.10, 0.5, 0.20,   0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.0, Option.Type.Call,  98.00,  95.00, 0.00, 0.10, 0.5, 0.20,   0.2676, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.0, Option.Type.Put,  102.00, 105.00, 0.00, 0.10, 0.5, 0.20,   0.2183, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.0, Option.Type.Put,   98.00, 105.00, 0.00, 0.10, 0.5, 0.20,   0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.0, Option.Type.Put,  102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  17.2983, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.0, Option.Type.Put,   98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  17.0306, 1e-4)
        };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, qRate, dc));
        final SimpleQuote rRate = new SimpleQuote(0.01);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                Utilities.flatRate(today, rRate, dc));
        final SimpleQuote vol = new SimpleQuote(0.25);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                Utilities.flatVol(today, vol, dc));

        for (final BinaryOptionData v : values) {
            final StrikedTypePayoff payoff = new AssetOrNothingPayoff(v.type, v.strike);
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise amExercise = new AmericanExercise(today, exDate, true);

            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot), qTS, rTS, volTS);
            final PricingEngine engine = new AnalyticBinaryBarrierEngine(stochProcess);

            final BarrierOption opt = new BarrierOption(
                    v.barrierType, v.barrier, 0.0, payoff, amExercise);
            opt.setPricingEngine(engine);

            final double calculated = opt.NPV();
            final double error = Math.abs(calculated - v.result);
            if (error > v.tol) {
                fail("failed to reproduce asset-or-nothing barrier option:"
                        + "\n    expected:   " + v.result
                        + "\n    calculated: " + calculated
                        + "\n    error:      " + error
                        + "\n    tolerance:  " + v.tol
                        + "\n    barrierType=" + v.barrierType
                        + " barrier=" + v.barrier
                        + " strike=" + v.strike + " s=" + v.s);
            }
        }
    }
}

/*
 Copyright (C) 2026 JQuantLib migration

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.testsuite.pricingengines.barrier;

import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
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
 * Tests for {@link AnalyticBinaryBarrierEngine}.
 * <p>
 * Mirrors {@code test-suite/binaryoption.cpp::testCashOrNothingHaugValues} and
 * {@code testAssetOrNothingHaugValues} (v1.42.1). Reference values from Haug
 * "Option pricing formulas 2nd Ed.", p.180.
 *
 * <p>Tolerance: TIGHT (1e-4 — matches C++ test suite).
 */
public class AnalyticBinaryBarrierEngineTest {

    public AnalyticBinaryBarrierEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    private static class BinaryOptionData {
        final BarrierType barrierType;
        final double barrier;
        final double cash;
        final Option.Type type;
        final double strike;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        BinaryOptionData(final BarrierType barrierType, final double barrier, final double cash,
                         final Option.Type type, final double strike, final double s,
                         final double q, final double r, final double t, final double v,
                         final double result, final double tol) {
            this.barrierType = barrierType;
            this.barrier = barrier;
            this.cash = cash;
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    private static final BinaryOptionData[] CASH_VALUES = new BinaryOptionData[] {
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
    };

    private static final BinaryOptionData[] ASSET_VALUES = new BinaryOptionData[] {
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 37.2782, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.00, Option.Type.Call,  98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 45.8530, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.00, Option.Type.Call, 102.00,  95.00, 0.00, 0.10, 0.5, 0.20, 44.5294, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.00, Option.Type.Call,  98.00,  95.00, 0.00, 0.10, 0.5, 0.20, 54.9262, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.00, Option.Type.Put,  102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 27.5644, 1e-4),
            new BinaryOptionData(BarrierType.DownIn,  100.00, 0.00, Option.Type.Put,   98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 18.9896, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.00, Option.Type.Put,  102.00,  95.00, 0.00, 0.10, 0.5, 0.20, 33.1723, 1e-4),
            new BinaryOptionData(BarrierType.UpIn,    100.00, 0.00, Option.Type.Put,   98.00,  95.00, 0.00, 0.10, 0.5, 0.20, 22.7755, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.00, Option.Type.Call, 102.00, 105.00, 0.00, 0.10, 0.5, 0.20, 39.9391, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.00, Option.Type.Call,  98.00, 105.00, 0.00, 0.10, 0.5, 0.20, 40.1574, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.00, Option.Type.Call, 102.00,  95.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.00, Option.Type.Call,  98.00,  95.00, 0.00, 0.10, 0.5, 0.20,  0.2676, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.00, Option.Type.Put,  102.00, 105.00, 0.00, 0.10, 0.5, 0.20,  0.2183, 1e-4),
            new BinaryOptionData(BarrierType.DownOut, 100.00, 0.00, Option.Type.Put,   98.00, 105.00, 0.00, 0.10, 0.5, 0.20,  0.0000, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.00, Option.Type.Put,  102.00,  95.00, 0.00, 0.10, 0.5, 0.20, 17.2983, 1e-4),
            new BinaryOptionData(BarrierType.UpOut,   100.00, 0.00, Option.Type.Put,   98.00,  95.00, 0.00, 0.10, 0.5, 0.20, 17.0306, 1e-4),
    };

    @Test
    public void testCashOrNothingHaugValues() {
        QL.info("Testing cash-or-nothing barrier options against Haug's values...");
        runCheck(CASH_VALUES, true);
    }

    @Test
    public void testAssetOrNothingHaugValues() {
        QL.info("Testing asset-or-nothing barrier options against Haug's values...");
        runCheck(ASSET_VALUES, false);
    }

    private void runCheck(final BinaryOptionData[] values, final boolean cashOrNothing) {
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

        for (final BinaryOptionData value : values) {
            final StrikedTypePayoff payoff = cashOrNothing
                    ? new CashOrNothingPayoff(value.type, value.strike, value.cash)
                    : new AssetOrNothingPayoff(value.type, value.strike);

            final Date exDate = today.add(timeToDays(value.t));
            final Exercise amExercise = new AmericanExercise(today, exDate, true);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));
            final AnalyticBinaryBarrierEngine engine = new AnalyticBinaryBarrierEngine(process);

            final BarrierOption opt = new BarrierOption(value.barrierType, value.barrier, 0.0, payoff, amExercise);
            opt.setPricingEngine(engine);

            final double calculated = opt.NPV();
            final double error = Math.abs(calculated - value.result);

            assertTrue("BinaryBarrier " + (cashOrNothing ? "Cash" : "Asset") + " "
                            + value.barrierType + " " + value.type
                            + " S=" + value.s + " K=" + value.strike + " H=" + value.barrier
                            + ": expected=" + value.result + " calculated=" + calculated
                            + " error=" + error + " tol=" + value.tol,
                    error <= value.tol);
        }
    }
}

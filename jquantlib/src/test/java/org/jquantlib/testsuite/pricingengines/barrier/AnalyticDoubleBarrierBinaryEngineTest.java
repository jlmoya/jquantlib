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
import org.junit.Test;

/**
 * Tests for {@link AnalyticDoubleBarrierBinaryEngine}.
 * <p>
 * Mirrors {@code test-suite/doublebinaryoption.cpp::testHaugValues} (v1.42.1).
 * Reference values from Haug "Option pricing formulas 2nd Ed", p.181 (KO/KIKO),
 * and Haug's VBA code (KI/KOKI).
 *
 * <p>Tolerance: TIGHT (1e-4 — matches C++ test suite).
 */
public class AnalyticDoubleBarrierBinaryEngineTest {

    public AnalyticDoubleBarrierBinaryEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    private record Data(DoubleBarrierType barrierType, double barrierLo, double barrierHi,
                        double cash, double s,
                        double q, double r, double t, double v,
                        double result, double tol) {}

    private static final Data[] HAUG_VALUES = new Data[] {
            // KnockOut from Haug p.181
            new Data(DoubleBarrierType.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10,  9.8716, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  8.9307, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  6.3272, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50,  1.9094, 1e-4),

            new Data(DoubleBarrierType.KnockOut, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10,  9.7961, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  7.2300, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  3.7100, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10,  8.9054, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  3.6752, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.10,  3.6323, 1e-4),

            // KIKO
            new Data(DoubleBarrierType.KIKO, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  0.2402, 1e-4),
            new Data(DoubleBarrierType.KIKO, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  1.4076, 1e-4),
            new Data(DoubleBarrierType.KIKO, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50,  3.8160, 1e-4),
            new Data(DoubleBarrierType.KIKO, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  0.9910, 1e-4),
            new Data(DoubleBarrierType.KIKO, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  2.8098, 1e-4),
            new Data(DoubleBarrierType.KIKO, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  4.4024, 1e-4),
            new Data(DoubleBarrierType.KIKO, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  4.7523, 1e-4),

            // KnockIn from Haug VBA
            new Data(DoubleBarrierType.KnockIn, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  0.9450, 1e-4),
            new Data(DoubleBarrierType.KnockIn, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  3.5486, 1e-4),
            new Data(DoubleBarrierType.KnockIn, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50,  7.9663, 1e-4),
            new Data(DoubleBarrierType.KnockIn, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  6.1658, 1e-4),
            new Data(DoubleBarrierType.KnockIn, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50,  9.4486, 1e-4),
            new Data(DoubleBarrierType.KnockIn, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  9.0798, 1e-4),
            new Data(DoubleBarrierType.KnockIn, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  9.7847, 1e-4),

            // KOKI
            new Data(DoubleBarrierType.KOKI, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  0.7080, 1e-4),
            new Data(DoubleBarrierType.KOKI, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  2.1581, 1e-4),
            new Data(DoubleBarrierType.KOKI, 80.00, 120.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.50,  4.2061, 1e-4),
            new Data(DoubleBarrierType.KOKI, 85.00, 115.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  1.6663, 1e-4),
            new Data(DoubleBarrierType.KOKI, 90.00, 110.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.20,  3.4424, 1e-4),
            new Data(DoubleBarrierType.KOKI, 95.00, 105.00, 10.00, 100.00, 0.02, 0.05, 0.25, 0.30,  5.0763, 1e-4),

            // degenerate cases
            new Data(DoubleBarrierType.KnockOut, 95.00, 105.00, 10.00,  80.00, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
            new Data(DoubleBarrierType.KnockOut, 95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
            new Data(DoubleBarrierType.KnockIn,  95.00, 105.00, 10.00,  80.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
            new Data(DoubleBarrierType.KnockIn,  95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
            new Data(DoubleBarrierType.KIKO,     95.00, 105.00, 10.00,  80.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
            new Data(DoubleBarrierType.KIKO,     95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
            new Data(DoubleBarrierType.KOKI,     95.00, 105.00, 10.00,  80.00, 0.02, 0.05, 0.25, 0.10,  0.0000, 1e-4),
            new Data(DoubleBarrierType.KOKI,     95.00, 105.00, 10.00, 110.00, 0.02, 0.05, 0.25, 0.10, 10.0000, 1e-4),
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

        for (final Data value : HAUG_VALUES) {
            final StrikedTypePayoff payoff = new CashOrNothingPayoff(Option.Type.Call, 0, value.cash());

            final Date exDate = today.add(timeToDays(value.t()));
            final Exercise exercise;
            if (value.barrierType() == DoubleBarrierType.KIKO || value.barrierType() == DoubleBarrierType.KOKI) {
                exercise = new AmericanExercise(today, exDate);
            } else {
                exercise = new EuropeanExercise(exDate);
            }

            spot.setValue(value.s());
            qRate.setValue(value.q());
            rRate.setValue(value.r());
            vol.setValue(value.v());

            final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final AnalyticDoubleBarrierBinaryEngine engine = new AnalyticDoubleBarrierBinaryEngine(process);
            final DoubleBarrierOption opt = new DoubleBarrierOption(
                    value.barrierType(), value.barrierLo(), value.barrierHi(), 0.0, payoff, exercise);
            opt.setPricingEngine(engine);

            final double calculated = opt.NPV();
            final double error = Math.abs(calculated - value.result());

            assertTrue("DoubleBarrierBinary " + value.barrierType()
                            + " bar=[" + value.barrierLo() + "," + value.barrierHi() + "]"
                            + " S=" + value.s() + " v=" + value.v()
                            + ": expected=" + value.result() + " calculated=" + calculated
                            + " error=" + error + " tol=" + value.tol(),
                    error <= value.tol());
        }
    }
}

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
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.exoticoptions.SoftBarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.barrier.AnalyticSoftBarrierEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Test;

/**
 * Tests for {@link AnalyticSoftBarrierEngine}.
 * <p>
 * Mirrors {@code test-suite/softbarrieroption.cpp::testSoftBarrierHaug} (v1.42.1).
 * Reference values from Haug "The complete guide to option pricing formulas 2nd Ed", p.166.
 *
 * <p>Tolerance: TIGHT (1e-4 — matches C++ test suite).
 */
public class AnalyticSoftBarrierEngineTest {

    public AnalyticSoftBarrierEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    private static class Data {
        final BarrierType barrierType;
        final Option.Type type;
        final double s;
        final double strike;
        final double U;
        final double L;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        Data(final BarrierType barrierType, final Option.Type type, final double s, final double strike,
             final double U, final double L, final double q, final double r,
             final double t, final double v, final double result, final double tol) {
            this.barrierType = barrierType;
            this.type = type;
            this.s = s;
            this.strike = strike;
            this.U = U;
            this.L = L;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    /**
     * Subset of Haug values from C++ test suite. We exclude U=L cases (which fall back to
     * AnalyticBarrierEngine via standardBarrierEquivalent path) — those are tracked separately
     * since the path differs from the soft-barrier closed form.
     */
    private static final Data[] VALUES = new Data[] {
            // sigma=0.1, T=0.5
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.1, 4.0175, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.1, 4.0529, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.1, 4.0648, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.1, 4.0708, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.1, 4.0744, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.1, 4.0768, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.1, 4.0785, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.1, 4.0798, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.1, 4.0808, 1e-4),

            // sigma=0.2, T=0.5
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.2, 5.5615, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.2, 6.0394, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.2, 6.2594, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.2, 6.3740, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.2, 6.4429, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.2, 6.4889, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.2, 6.5217, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.2, 6.5463, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.2, 6.5654, 1e-4),

            // sigma=0.3, T=0.5
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 0.5, 0.3, 6.2595, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 0.5, 0.3, 7.2496, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 0.5, 0.3, 7.8567, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 0.5, 0.3, 8.2253, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 0.5, 0.3, 8.4578, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 0.5, 0.3, 8.6142, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 0.5, 0.3, 8.7260, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 0.5, 0.3, 8.8099, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 0.5, 0.3, 8.8751, 1e-4),

            // sigma=0.1, T=1.0
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 90, 0.05, 0.1, 1.0, 0.1, 6.0758, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 85, 0.05, 0.1, 1.0, 0.1, 6.2641, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 80, 0.05, 0.1, 1.0, 0.1, 6.3336, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 75, 0.05, 0.1, 1.0, 0.1, 6.3685, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 70, 0.05, 0.1, 1.0, 0.1, 6.3894, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 65, 0.05, 0.1, 1.0, 0.1, 6.4034, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 60, 0.05, 0.1, 1.0, 0.1, 6.4133, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 55, 0.05, 0.1, 1.0, 0.1, 6.4208, 1e-4),
            new Data(BarrierType.DownOut, Option.Type.Call, 100, 100, 95, 50, 0.05, 0.1, 1.0, 0.1, 6.4266, 1e-4),
    };

    @Test
    public void testSoftBarrierHaug() {
        QL.info("Testing soft barrier option pricing against textbook values...");

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final Data value : VALUES) {
            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final Date exDate = today.add(timeToDays(value.t));
            final Exercise exercise = new EuropeanExercise(exDate);
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(value.type, value.strike);

            final GeneralizedBlackScholesProcess process = new GeneralizedBlackScholesProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final SoftBarrierOption option = new SoftBarrierOption(
                    value.barrierType, value.L, value.U, payoff, exercise);
            option.setPricingEngine(new AnalyticSoftBarrierEngine(process));

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - value.result);

            assertTrue("SoftBarrier " + value.barrierType + " " + value.type
                            + " S=" + value.s + " K=" + value.strike + " U=" + value.U + " L=" + value.L
                            + " v=" + value.v + " T=" + value.t
                            + ": expected=" + value.result + " calculated=" + calculated
                            + " error=" + error + " tol=" + value.tol,
                    error <= value.tol);
        }
    }
}

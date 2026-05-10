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
import org.jquantlib.experimental.exoticoptions.PartialBarrier;
import org.jquantlib.experimental.exoticoptions.PartialTimeBarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.barrier.AnalyticPartialTimeBarrierOptionEngine;
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
 * Tests for {@link AnalyticPartialTimeBarrierOptionEngine}.
 * <p>
 * Mirrors {@code test-suite/partialtimebarrieroption.cpp::testAnalyticEngine} and
 * {@code testAnalyticEnginePutOption} (v1.42.1). Reference values come from Haug
 * and reproduce the C++ test suite's expected results literally.
 *
 * <p>Tolerance: TIGHT (1e-4 — matches the C++ test suite, since Haug values are
 * reported to 4 decimal places).
 */
public class PartialTimeBarrierOptionTest {

    public PartialTimeBarrierOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static class TestCase {
        final double underlying;
        final double strike;
        final int days;
        final double result;

        TestCase(final double underlying, final double strike, final int days, final double result) {
            this.underlying = underlying;
            this.strike = strike;
            this.days = days;
            this.result = result;
        }
    }

    /** Reference values from C++ testAnalyticEngine (DownOut/EndB1, Call). */
    private static final TestCase[] CALL_VALUES = new TestCase[] {
            new TestCase( 95.0,  90.0,   1,  0.0393),
            new TestCase( 95.0, 110.0,   1,  0.0000),
            new TestCase(105.0,  90.0,   1,  9.8751),
            new TestCase(105.0, 110.0,   1,  6.2303),

            new TestCase( 95.0,  90.0,  90,  6.2747),
            new TestCase( 95.0, 110.0,  90,  3.7352),
            new TestCase(105.0,  90.0,  90, 15.6324),
            new TestCase(105.0, 110.0,  90,  9.6812),

            new TestCase( 95.0,  90.0, 180, 10.3345),
            new TestCase( 95.0, 110.0, 180,  5.8712),
            new TestCase(105.0,  90.0, 180, 19.2896),
            new TestCase(105.0, 110.0, 180, 11.6055),

            new TestCase( 95.0,  90.0, 270, 13.4342),
            new TestCase( 95.0, 110.0, 270,  7.1270),
            new TestCase(105.0,  90.0, 270, 22.0753),
            new TestCase(105.0, 110.0, 270, 12.7342),

            new TestCase( 95.0,  90.0, 359, 16.8576),
            new TestCase( 95.0, 110.0, 359,  7.5763),
            new TestCase(105.0,  90.0, 359, 25.1488),
            new TestCase(105.0, 110.0, 359, 13.1376),
    };

    /** Reference values from C++ testAnalyticEnginePutOption (UpOut/EndB1, Put). */
    private static final TestCase[] PUT_VALUES = new TestCase[] {
            new TestCase( 95.0,  90.0,   1,  1.5551),
            new TestCase( 95.0,  95.0,   1,  2.0589),
            new TestCase( 90.0,  95.0,   1,  4.4512),
            new TestCase( 99.0,  90.0,   1,  0.3404),

            new TestCase( 95.0,  90.0,  90,  2.4181),
            new TestCase( 95.0,  95.0,  90,  3.2257),
            new TestCase( 90.0,  95.0,  90,  5.0624),
            new TestCase( 99.0,  90.0,  90,  1.5992),

            new TestCase( 95.0,  90.0, 180,  3.0021),
            new TestCase( 95.0,  95.0, 180,  4.0617),
            new TestCase( 90.0,  95.0, 180,  5.7960),
            new TestCase( 99.0,  90.0, 180,  2.1903),

            new TestCase( 95.0,  90.0, 270,  3.4194),
            new TestCase( 95.0,  95.0, 270,  4.7362),
            new TestCase( 90.0,  95.0, 270,  6.4370),
            new TestCase( 99.0,  90.0, 270,  2.6025),

            new TestCase( 95.0,  90.0, 359,  3.5965),
            new TestCase( 95.0,  95.0, 359,  5.1865),
            new TestCase( 90.0,  95.0, 359,  6.8782),
            new TestCase( 99.0,  90.0, 359,  2.7759),
    };

    @Test
    public void testAnalyticEngineCall() {
        QL.info("Testing analytic engine for partial-time barrier call option (DownOut/EndB1)...");
        runEngineCheck(BarrierType.DownOut, Option.Type.Call, CALL_VALUES);
    }

    @Test
    public void testAnalyticEnginePut() {
        QL.info("Testing analytic engine for partial-time barrier put option (UpOut/EndB1)...");
        runEngineCheck(BarrierType.UpOut, Option.Type.Put, PUT_VALUES);
    }

    private void runEngineCheck(final BarrierType barrierType, final Option.Type optType,
                                final TestCase[] values) {
        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final Date maturity = today.add(360);
        final Exercise exercise = new EuropeanExercise(maturity);
        final double barrier = 100.0;
        final double rebate = 0.0;

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure dividendTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.1);
        final YieldTermStructure riskFreeTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.25);
        final BlackVolTermStructure blackVolTS = Utilities.flatVol(today, vol, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(dividendTS),
                new Handle<YieldTermStructure>(riskFreeTS),
                new Handle<BlackVolTermStructure>(blackVolTS));
        final AnalyticPartialTimeBarrierOptionEngine engine =
                new AnalyticPartialTimeBarrierOptionEngine(process);

        final double tolerance = 1.0e-4;

        for (final TestCase tc : values) {
            final Date coverEventDate = today.add(tc.days);
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(optType, tc.strike);
            final PartialTimeBarrierOption option = new PartialTimeBarrierOption(
                    barrierType, PartialBarrier.EndB1, barrier, rebate,
                    coverEventDate, payoff, exercise);
            option.setPricingEngine(engine);

            spot.setValue(tc.underlying);
            final double calculated = option.NPV();
            final double error = Math.abs(calculated - tc.result);

            assertTrue("PartialTimeBarrier " + barrierType + " " + optType
                            + " S=" + tc.underlying + " K=" + tc.strike + " days=" + tc.days
                            + ": expected=" + tc.result + " calculated=" + calculated
                            + " error=" + error + " tol=" + tolerance,
                    error <= tolerance);
        }
    }
}

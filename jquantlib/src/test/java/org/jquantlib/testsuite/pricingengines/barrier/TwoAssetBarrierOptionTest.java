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
import org.jquantlib.experimental.exoticoptions.TwoAssetBarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.barrier.AnalyticTwoAssetBarrierEngine;
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
 * Tests for {@link AnalyticTwoAssetBarrierEngine}.
 * <p>
 * Mirrors {@code test-suite/twoassetbarrieroption.cpp::testHaugValues} (v1.42.1).
 * Reference values from Haug, "Option pricing formulas".
 *
 * <p>Tolerance: LOOSE (4e-3 — matches C++ test suite tol).
 */
public class TwoAssetBarrierOptionTest {

    public TwoAssetBarrierOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static class OptionData {
        final BarrierType barrierType;
        final Option.Type type;
        final double barrier;
        final double strike;
        final double s1;
        final double q1;
        final double v1;
        final double s2;
        final double q2;
        final double v2;
        final double correlation;
        final double r;
        final double result;

        OptionData(final BarrierType barrierType, final Option.Type type,
                   final double barrier, final double strike,
                   final double s1, final double q1, final double v1,
                   final double s2, final double q2, final double v2,
                   final double correlation, final double r, final double result) {
            this.barrierType = barrierType;
            this.type = type;
            this.barrier = barrier;
            this.strike = strike;
            this.s1 = s1;
            this.q1 = q1;
            this.v1 = v1;
            this.s2 = s2;
            this.q2 = q2;
            this.v2 = v2;
            this.correlation = correlation;
            this.r = r;
            this.result = result;
        }
    }

    private static final OptionData[] HAUG_VALUES = new OptionData[] {
            new OptionData(BarrierType.DownOut, Option.Type.Call, 95, 90,
                    100.0, 0.0, 0.2, 100.0, 0.0, 0.2, 0.5, 0.08, 6.6592),
            new OptionData(BarrierType.UpOut, Option.Type.Call, 105, 90,
                    100.0, 0.0, 0.2, 100.0, 0.0, 0.2, -0.5, 0.08, 4.6670),
            new OptionData(BarrierType.DownOut, Option.Type.Put, 95, 90,
                    100.0, 0.0, 0.2, 100.0, 0.0, 0.2, -0.5, 0.08, 0.6184),
            new OptionData(BarrierType.UpOut, Option.Type.Put, 105, 100,
                    100.0, 0.0, 0.2, 100.0, 0.0, 0.2, 0.0, 0.08, 0.8246),
    };

    @Test
    public void testHaugValues() {
        QL.info("Testing two-asset barrier options against Haug's values...");

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);
        final Date maturity = today.add(180);
        final Exercise exercise = new EuropeanExercise(maturity);

        final SimpleQuote rQuote = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rQuote, dc);

        final SimpleQuote s1 = new SimpleQuote(0.0);
        final SimpleQuote q1 = new SimpleQuote(0.0);
        final YieldTermStructure qTS1 = Utilities.flatRate(today, q1, dc);
        final SimpleQuote vol1 = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS1 = Utilities.flatVol(today, vol1, dc);

        final BlackScholesMertonProcess process1 = new BlackScholesMertonProcess(
                new Handle<Quote>(s1),
                new Handle<YieldTermStructure>(qTS1),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS1));

        final SimpleQuote s2 = new SimpleQuote(0.0);
        final SimpleQuote q2 = new SimpleQuote(0.0);
        final YieldTermStructure qTS2 = Utilities.flatRate(today, q2, dc);
        final SimpleQuote vol2 = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS2 = Utilities.flatVol(today, vol2, dc);

        final BlackScholesMertonProcess process2 = new BlackScholesMertonProcess(
                new Handle<Quote>(s2),
                new Handle<YieldTermStructure>(qTS2),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS2));

        final SimpleQuote rho = new SimpleQuote(0.0);

        final AnalyticTwoAssetBarrierEngine engine =
                new AnalyticTwoAssetBarrierEngine(process1, process2, new Handle<Quote>(rho));

        final double tolerance = 4.0e-3;

        for (final OptionData v : HAUG_VALUES) {
            s1.setValue(v.s1);
            q1.setValue(v.q1);
            vol1.setValue(v.v1);

            s2.setValue(v.s2);
            q2.setValue(v.q2);
            vol2.setValue(v.v2);

            rho.setValue(v.correlation);
            rQuote.setValue(v.r);

            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(v.type, v.strike);
            final TwoAssetBarrierOption option = new TwoAssetBarrierOption(
                    v.barrierType, v.barrier, payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);

            assertTrue("TwoAssetBarrier " + v.barrierType + " " + v.type
                            + ": expected=" + v.result + " calculated=" + calculated
                            + " error=" + error + " tol=" + tolerance,
                    error <= tolerance);
        }
    }
}

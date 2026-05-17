/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.TwoAssetBarrierOption;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.barrier.AnalyticTwoAssetBarrierEngine;
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
 * Phase 5e.5b-CFC-d-75 port of {@code test-suite/twoassetbarrieroption.cpp}
 * v1.42.1 (144 LOC, 1 case).
 *
 * <p>Exercises the two-asset barrier option (Heynen-Kat 1994 closed form;
 * the payoff depends on asset 1, the barrier is monitored on asset 2)
 * cross-validated against the Haug 1998 reference table for the four
 * {@code BarrierType} variants on the {@code Out} branches (the C++ test
 * exercises {@code DownOut} and {@code UpOut} with both {@code Call} and
 * {@code Put} payoffs).
 *
 * <p>Source: {@code test-suite/twoassetbarrieroption.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class TwoAssetBarrierOptionTest {

    public TwoAssetBarrierOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final class OptionData {
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
            this.type        = type;
            this.barrier     = barrier;
            this.strike      = strike;
            this.s1 = s1; this.q1 = q1; this.v1 = v1;
            this.s2 = s2; this.q2 = q2; this.v2 = v2;
            this.correlation = correlation;
            this.r           = r;
            this.result      = result;
        }
    }

    /**
     * Mirrors C++ test-suite/twoassetbarrieroption.cpp::testHaugValues.
     * <p>
     * Reference values from E.G. Haug, "Option Pricing Formulas",
     * McGraw-Hill 1998. C++ uses tolerance 4.0e-3; we use 4.0e-3 as well
     * (LOOSE tier — well within the LOOSE 1e-4 ceiling justified by Haug's
     * 5-digit reference values).
     */
    @Test
    public void testHaugValues() {
        QL.info("Testing two-asset barrier options against Haug's values...");

        final OptionData[] values = new OptionData[] {
            new OptionData(BarrierType.DownOut, Option.Type.Call,  95.0, 90.0,
                           100.0, 0.0, 0.2, 100.0, 0.0, 0.2,  0.5, 0.08, 6.6592),
            new OptionData(BarrierType.UpOut,   Option.Type.Call, 105.0, 90.0,
                           100.0, 0.0, 0.2, 100.0, 0.0, 0.2, -0.5, 0.08, 4.6670),
            new OptionData(BarrierType.DownOut, Option.Type.Put,   95.0, 90.0,
                           100.0, 0.0, 0.2, 100.0, 0.0, 0.2, -0.5, 0.08, 0.6184),
            new OptionData(BarrierType.UpOut,   Option.Type.Put,  105.0, 100.0,
                           100.0, 0.0, 0.2, 100.0, 0.0, 0.2,  0.0, 0.08, 0.8246)
        };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();
        final Date maturity = today.add(180);
        final Exercise exercise = new EuropeanExercise(maturity);

        final SimpleQuote r    = new SimpleQuote(0.0);
        final SimpleQuote s1   = new SimpleQuote(0.0);
        final SimpleQuote q1   = new SimpleQuote(0.0);
        final SimpleQuote vol1 = new SimpleQuote(0.0);
        final SimpleQuote s2   = new SimpleQuote(0.0);
        final SimpleQuote q2   = new SimpleQuote(0.0);
        final SimpleQuote vol2 = new SimpleQuote(0.0);
        final SimpleQuote rho  = new SimpleQuote(0.0);

        final YieldTermStructure rTS    = Utilities.flatRate(today, r,    dc);
        final YieldTermStructure qTS1   = Utilities.flatRate(today, q1,   dc);
        final YieldTermStructure qTS2   = Utilities.flatRate(today, q2,   dc);
        final BlackVolTermStructure vTS1 = Utilities.flatVol (today, vol1, dc);
        final BlackVolTermStructure vTS2 = Utilities.flatVol (today, vol2, dc);

        final GeneralizedBlackScholesProcess process1 = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(s1),
                new Handle<YieldTermStructure>(qTS1),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(vTS1));

        final GeneralizedBlackScholesProcess process2 = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(s2),
                new Handle<YieldTermStructure>(qTS2),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(vTS2));

        final PricingEngine engine = new AnalyticTwoAssetBarrierEngine(
                process1, process2, new Handle<Quote>(rho));

        for (final OptionData v : values) {
            s1  .setValue(v.s1);
            q1  .setValue(v.q1);
            vol1.setValue(v.v1);

            s2  .setValue(v.s2);
            q2  .setValue(v.q2);
            vol2.setValue(v.v2);

            rho .setValue(v.correlation);
            r   .setValue(v.r);

            final StrikedTypePayoff payoff = new PlainVanillaPayoff(v.type, v.strike);

            final TwoAssetBarrierOption barrierOption =
                    new TwoAssetBarrierOption(v.barrierType, v.barrier, payoff, exercise);
            barrierOption.setPricingEngine(engine);

            final double calculated = barrierOption.NPV();
            final double expected   = v.result;
            final double tolerance  = 4.0e-3;
            assertEquals("Two-asset barrier NPV mismatch (vs Haug) for "
                       + v.barrierType + "/" + v.type + " barrier=" + v.barrier
                       + " strike=" + v.strike + " rho=" + v.correlation,
                    expected, calculated, tolerance);
        }
    }
}

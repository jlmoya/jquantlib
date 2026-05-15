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
import org.jquantlib.experimental.exoticoptions.AnalyticTwoAssetCorrelationEngine;
import org.jquantlib.experimental.exoticoptions.TwoAssetCorrelationOption;
import org.jquantlib.instruments.Option;
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
 * Phase 5e.5b-CFC-d-24 port of {@code test-suite/twoassetcorrelationoption.cpp}
 * v1.42.1 (91 LOC, 1 case).
 *
 * <p>Exercises the two-asset correlation option (Zhang 1995 closed form;
 * payoff is the in-the-money intrinsic of asset 2 conditional on asset 1
 * being in the money). Cross-validated against the Haug 2007 reference
 * table.
 *
 * <p>Phase 5e.5b-CFC-d-24: testAnalyticEngine bodied
 * (TwoAssetCorrelationOption + AnalyticTwoAssetCorrelationEngine ported).
 *
 * <p>Source: {@code test-suite/twoassetcorrelationoption.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class TwoAssetCorrelationOptionTest {

    public TwoAssetCorrelationOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Mirrors C++ test-suite/twoassetcorrelationoption.cpp::testAnalyticEngine.
     * <p>
     * Expected NPV: 4.7073 (Haug "Option Pricing Formulas") with tolerance 1e-4.
     */
    @Test
    public void testAnalyticEngine() {
        QL.info("Testing analytic engine for two-asset correlation option...");

        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();

        final Option.Type type = Option.Type.Call;
        final double strike1 = 50.0;
        final double strike2 = 70.0;
        final Date exDate = today.add(180);

        final Exercise exercise = new EuropeanExercise(exDate);
        final TwoAssetCorrelationOption option =
                new TwoAssetCorrelationOption(type, strike1, strike2, exercise);

        final Handle<Quote> underlying1 = new Handle<Quote>(new SimpleQuote(52.0));
        final Handle<Quote> underlying2 = new Handle<Quote>(new SimpleQuote(65.0));
        final Handle<YieldTermStructure> dividendTS1 =
                new Handle<YieldTermStructure>(Utilities.flatRate(today, 0.0, dc));
        final Handle<YieldTermStructure> dividendTS2 =
                new Handle<YieldTermStructure>(Utilities.flatRate(today, 0.0, dc));
        final Handle<YieldTermStructure> riskFreeTS =
                new Handle<YieldTermStructure>(Utilities.flatRate(today, 0.1, dc));
        final Handle<BlackVolTermStructure> blackVolTS1 =
                new Handle<BlackVolTermStructure>(Utilities.flatVol(today, 0.2, dc));
        final Handle<BlackVolTermStructure> blackVolTS2 =
                new Handle<BlackVolTermStructure>(Utilities.flatVol(today, 0.3, dc));
        final Handle<Quote> correlation = new Handle<Quote>(new SimpleQuote(0.75));

        final GeneralizedBlackScholesProcess process1 = new GeneralizedBlackScholesProcess(
                underlying1, dividendTS1, riskFreeTS, blackVolTS1);
        final GeneralizedBlackScholesProcess process2 = new GeneralizedBlackScholesProcess(
                underlying2, dividendTS2, riskFreeTS, blackVolTS2);

        option.setPricingEngine(new AnalyticTwoAssetCorrelationEngine(
                process1, process2, correlation));

        final double calculated = option.NPV();
        final double expected = 4.7073;       // Haug "Option Pricing Formulas"
        final double tolerance = 1.0e-4;
        assertEquals("Two-asset correlation NPV mismatch (vs Haug)",
                expected, calculated, tolerance);
    }
}

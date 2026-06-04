/*
 Copyright (C) 2026

 This source code is release under the BSD License.

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.DividendVanillaOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticDividendEuropeanEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDAmericanEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDDividendAmericanEngine;
import org.jquantlib.pricingengines.vanilla.finitedifferences.FDDividendEuropeanEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Characterises {@link FDDividendAmericanEngine}, the finite-difference engine for American options that pay discrete
 * cash dividends.
 *
 * <p>These tests pin the engine's behaviour to financial relationships and to the C++-cross-validated
 * {@link AnalyticDividendEuropeanEngine} oracle. The headline assertion ({@code testDividendsAreApplied}) catches a
 * regression in which the engine silently ignored the dividends entirely: because Java cannot reproduce the C++
 * {@code FDAmericanCondition<baseEngine> : public baseEngine} idiom, the wrapper had discarded the dividend engine and
 * the American value was invariant to the dividends.
 */
public class FDDividendAmericanEngineTest {

    private final Date today = new Date(15, Month.January, 2026);
    private final DayCounter dc = new Actual365Fixed();
    private final Calendar calendar = new NullCalendar();

    private BlackScholesMertonProcess process(final double spot, final double r, final double q, final double vol) {
        final Handle<Quote> s = new Handle<>(new SimpleQuote(spot));
        final Handle<YieldTermStructure> rTS = new Handle<>(new FlatForward(today, r, dc));
        final Handle<YieldTermStructure> qTS = new Handle<>(new FlatForward(today, q, dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<>(new BlackConstantVol(today, calendar, vol, dc));
        return new BlackScholesMertonProcess(s, qTS, rTS, volTS);
    }

    private static double npv(final org.jquantlib.instruments.OneAssetOption option, final PricingEngine engine) {
        option.setPricingEngine(engine);
        return option.NPV();
    }

    @Test
    public void testDividendsAreApplied() {
        new Settings().setEvaluationDate(today);

        final double spot = 100.0;
        final double strike = 100.0;
        final double r = 0.05;
        final double q = 0.0;
        final double vol = 0.25;
        final int timeSteps = 200;
        final int gridPoints = 200;

        final BlackScholesMertonProcess process = process(spot, r, q, vol);
        final Date maturity = today.add(365);
        final Exercise american = new AmericanExercise(today, maturity);
        final Exercise european = new EuropeanExercise(maturity);
        final PlainVanillaPayoff put = new PlainVanillaPayoff(Option.Type.Put, strike);

        final List<Date> divDates = new ArrayList<>();
        divDates.add(today.add(120));
        divDates.add(today.add(240));
        final List<Double> divs = new ArrayList<>();
        divs.add(5.0);
        divs.add(5.0);
        final List<Date> noDates = new ArrayList<>();
        final List<Double> noDivs = new ArrayList<>();

        final double amerWithDiv = npv(new DividendVanillaOption(put, american, divDates, divs),
                new FDDividendAmericanEngine(process, timeSteps, gridPoints));
        final double amerNoDiv = npv(new DividendVanillaOption(put, american, noDates, noDivs),
                new FDDividendAmericanEngine(process, timeSteps, gridPoints));
        final double euroWithDiv = npv(new DividendVanillaOption(put, european, divDates, divs),
                new FDDividendEuropeanEngine(process, timeSteps, gridPoints));
        final double euroAnalyticDiv = npv(new DividendVanillaOption(put, european, divDates, divs),
                new AnalyticDividendEuropeanEngine(process));
        final double vanillaAmer = npv(new VanillaOption(put, american),
                new FDAmericanEngine(process, timeSteps, gridPoints, false));

        // (1) The regression: discrete dividends MUST move the American value (a put with a lower forward is worth
        //     materially more). Before the fix the engine ignored dividends and this difference was exactly zero.
        assertTrue("FDDividendAmericanEngine ignores dividends: amerWithDiv=" + amerWithDiv + " amerNoDiv=" + amerNoDiv,
                Math.abs(amerWithDiv - amerNoDiv) > 0.5);

        // (2) Oracle: the escrowed FD European value matches the analytic dividend European engine (the FD engine's
        //     javadoc states it is "consistent with the analytic version"). LOOSE tier — FD grid resolution.
        assertEquals("FD escrowed European vs analytic dividend European", euroAnalyticDiv, euroWithDiv, 0.10);

        // (3) Early-exercise premium is non-negative: the American value is at least the European value for the same
        //     dividends and the same escrowed model.
        assertTrue("American < European with same dividends (amer=" + amerWithDiv + ", euro=" + euroWithDiv + ")",
                amerWithDiv >= euroWithDiv - 0.02);

        // (4) Invariance: with no dividends the engine reduces to the plain American FD engine. LOOSE tier.
        assertEquals("American no-dividend vs vanilla American FD", vanillaAmer, amerNoDiv, 0.10);
    }

    @Test
    public void testAmericanCallRespondsToLargeDividend() {
        new Settings().setEvaluationDate(today);

        final BlackScholesMertonProcess process = process(100.0, 0.05, 0.0, 0.25);
        final Date maturity = today.add(365);
        final Exercise american = new AmericanExercise(today, maturity);
        final PlainVanillaPayoff call = new PlainVanillaPayoff(Option.Type.Call, 100.0);

        final List<Date> divDates = new ArrayList<>();
        divDates.add(today.add(180));
        final List<Double> small = new ArrayList<>();
        small.add(0.0);
        final List<Double> large = new ArrayList<>();
        large.add(15.0);

        final double callNoDiv = npv(new DividendVanillaOption(call, american, divDates, small),
                new FDDividendAmericanEngine(process, 200, 200));
        final double callBigDiv = npv(new DividendVanillaOption(call, american, divDates, large),
                new FDDividendAmericanEngine(process, 200, 200));

        // A large dividend depresses the call's forward and must lower the American call value.
        assertTrue("American call must respond to a large dividend (noDiv=" + callNoDiv + ", bigDiv=" + callBigDiv + ")",
                callNoDiv - callBigDiv > 0.5);
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.exoticoptions.AnalyticSimpleChooserEngine;
import org.jquantlib.instruments.SimpleChooserOption;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
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
 * Phase 5e.5b-CFC-d-24 partial port of {@code test-suite/chooseroption.cpp}
 * v1.42.1 (163 LOC, 2 cases).
 *
 * <p>Exercises the chooser (preference) option: simple chooser (same strike
 * and expiry for the call and put alternatives, Rubinstein 1991 closed
 * form) and complex chooser (Rubinstein 1991 with different strikes /
 * expiries; trivariate normal CDF).
 *
 * <p>Phase 5e.5b-CFC-d-24: testAnalyticSimpleChooserEngine bodied
 * (SimpleChooserOption + AnalyticSimpleChooserEngine ported).
 *
 * <p><strong>testAnalyticComplexChooserEngine deferred</strong> — depends on
 * trivariate-normal CDF (also a Phase 5k.5 carry-forward in
 * {@code org.jquantlib.math.distributions}) and a {@code ComplexChooserOption}
 * instrument with separate call / put exercise dates and strikes.
 *
 * <p>Source: {@code test-suite/chooseroption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ChooserOptionTest {

    public ChooserOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_COMPLEX =
            "Phase 5k.5 — requires ComplexChooserOption + "
          + "AnalyticComplexChooserEngine (Rubinstein 1991; depends on "
          + "trivariate-normal CDF — also Phase 5k.5 carry-forward)";

    /**
     * Mirrors C++ test-suite/chooseroption.cpp::testAnalyticSimpleChooserEngine.
     * <p>
     * Data from Haug "Complete Guide to Option Pricing Formulas" pp. 39-40.
     * Expected NPV: 6.1071 with tolerance 3e-5.
     */
    @Test
    public void testAnalyticSimpleChooserEngine() {
        QL.info("Testing analytic simple chooser option...");

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spot = new SimpleQuote(50.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.08);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.25);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final GeneralizedBlackScholesProcess stochProcess = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final PricingEngine engine = new AnalyticSimpleChooserEngine(stochProcess);

        final double strike = 50.0;
        final Date exerciseDate = today.add(180);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Date choosingDate = today.add(90);

        final SimpleChooserOption option = new SimpleChooserOption(choosingDate, strike, exercise);
        option.setPricingEngine(engine);

        final double calculated = option.NPV();
        final double expected = 6.1071;       // Haug p. 39-40
        final double tolerance = 3.0e-5;
        assertEquals("Simple chooser NPV mismatch (vs Haug)", expected, calculated, tolerance);
    }

    @Ignore(REASON_COMPLEX)
    @Test
    public void testAnalyticComplexChooserEngine() {
        fail("not implemented");
    }
}

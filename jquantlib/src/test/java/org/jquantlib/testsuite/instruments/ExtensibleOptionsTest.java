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
import org.jquantlib.experimental.exoticoptions.AnalyticWriterExtensibleOptionEngine;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.WriterExtensibleOption;
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
 * Phase 5k port of {@code test-suite/extensibleoptions.cpp} v1.42.1
 * (156 LOC, 2 cases).
 *
 * <p>Phase 4h.5 partial: testAnalyticWriterExtensibleOptionEngine bodied
 * (writer-extensible analytic engine ported). Holder-extensible variant
 * remains a Phase 5k.5 carry-forward.
 *
 * <p>Source: {@code test-suite/extensibleoptions.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ExtensibleOptionsTest {

    public ExtensibleOptionsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_HOLDER =
            "Phase 5k.5 — requires HolderExtensibleOption + "
          + "AnalyticHolderExtensibleOptionEngine (Longstaff 1990 holder branch)";

    /**
     * Mirrors C++ test-suite/extensibleoptions.cpp::testAnalyticWriterExtensibleOptionEngine.
     * Expected NPV: 6.8238 (Haug "Option Pricing Formulas") with tolerance 1e-4.
     */
    @Test
    public void testAnalyticWriterExtensibleOptionEngine() {
        QL.info("Testing analytic engine for writer-extensible option...");

        final Option.Type type = Option.Type.Call;
        final double strike1 = 90.0;
        final double strike2 = 82.0;
        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();
        final Date exDate1 = today.add(180);
        final Date exDate2 = today.add(270);

        final SimpleQuote spot = new SimpleQuote(80.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure dividendTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.10);
        final YieldTermStructure riskFreeTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.30);
        final BlackVolTermStructure blackVolTS = Utilities.flatVol(today, vol, dc);

        final GeneralizedBlackScholesProcess process = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(dividendTS),
                new Handle<YieldTermStructure>(riskFreeTS),
                new Handle<BlackVolTermStructure>(blackVolTS));

        final PricingEngine engine = new AnalyticWriterExtensibleOptionEngine(process);

        final PlainVanillaPayoff payoff1 = new PlainVanillaPayoff(type, strike1);
        final Exercise exercise1 = new EuropeanExercise(exDate1);
        final PlainVanillaPayoff payoff2 = new PlainVanillaPayoff(type, strike2);
        final Exercise exercise2 = new EuropeanExercise(exDate2);

        final WriterExtensibleOption option = new WriterExtensibleOption(
                payoff1, exercise1, payoff2, exercise2);
        option.setPricingEngine(engine);

        final double calculated = option.NPV();
        final double expected = 6.8238; // Haug "Option Pricing Formulas"
        final double tolerance = 1.0e-4;
        assertEquals("Writer-extensible NPV mismatch (vs Haug)", expected, calculated, tolerance);
    }

    @Ignore(REASON_HOLDER) @Test public void testAnalyticHolderExtensibleOptionEngine() { fail("not implemented"); }
}

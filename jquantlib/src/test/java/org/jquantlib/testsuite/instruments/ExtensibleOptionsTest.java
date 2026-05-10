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
import org.jquantlib.experimental.exoticoptions.AnalyticHolderExtensibleOptionEngine;
import org.jquantlib.experimental.exoticoptions.AnalyticWriterExtensibleOptionEngine;
import org.jquantlib.instruments.HolderExtensibleOption;
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
import org.junit.Test;

/**
 * Phase 5k port of {@code test-suite/extensibleoptions.cpp} v1.42.1
 * (156 LOC, 2 cases).
 *
 * <p>Phase 4h.5 partial: testAnalyticWriterExtensibleOptionEngine bodied
 * (writer-extensible analytic engine ported).
 * <p>Phase 4h.5b: testAnalyticHolderExtensibleOptionEngine bodied
 * (holder-extensible analytic engine + Dr78 BVN + BlackScholesCalculator
 * ported).
 *
 * <p>Source: {@code test-suite/extensibleoptions.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ExtensibleOptionsTest {

    public ExtensibleOptionsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

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

    /**
     * Mirrors C++ test-suite/extensibleoptions.cpp::testAnalyticHolderExtensibleOptionEngine.
     * Expected NPV: 9.4233 (Haug "Option Pricing Formulas") with tolerance 1e-4.
     */
    @Test
    public void testAnalyticHolderExtensibleOptionEngine() {
        QL.info("Testing analytic engine for holder-extensible option...");

        final Option.Type type = Option.Type.Call;
        final double strike1 = 100.0;
        final double strike2 = 105.0;
        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();
        final Date exDate1 = today.add(180);
        final Date exDate2 = today.add(270);
        final double premium = 1.0;

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure dividendTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.08);
        final YieldTermStructure riskFreeTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.25);
        final BlackVolTermStructure blackVolTS = Utilities.flatVol(today, vol, dc);

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike1);
        final Exercise exercise = new EuropeanExercise(exDate1);

        final HolderExtensibleOption option = new HolderExtensibleOption(
                type, premium, exDate2, strike2, payoff, exercise);

        final GeneralizedBlackScholesProcess process = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(dividendTS),
                new Handle<YieldTermStructure>(riskFreeTS),
                new Handle<BlackVolTermStructure>(blackVolTS));

        final PricingEngine engine = new AnalyticHolderExtensibleOptionEngine(process);
        option.setPricingEngine(engine);

        final double calculated = option.NPV();
        final double expected = 9.4233; // Haug "Option Pricing Formulas"
        final double tolerance = 1.0e-4;
        assertEquals("Holder-extensible NPV mismatch (vs Haug)", expected, calculated, tolerance);
    }

    /**
     * Cross-validation against probe (C++ v1.42.1 reference): low-vol case.
     * Expected NPV taken from migration-harness/references/experimental/holder-extensible
     * /holder_extensible_option.json (case "call_atm_lower_vol").
     */
    @Test
    public void testHolderExtensibleLowerVol() {
        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.08);
        final SimpleQuote vol = new SimpleQuote(0.20);

        final GeneralizedBlackScholesProcess process = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, qRate, dc)),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, rRate, dc)),
                new Handle<BlackVolTermStructure>(Utilities.flatVol(today, vol, dc)));

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final Exercise exercise = new EuropeanExercise(today.add(180));
        final HolderExtensibleOption option = new HolderExtensibleOption(
                Option.Type.Call, 1.0, today.add(270), 105.0, payoff, exercise);
        option.setPricingEngine(new AnalyticHolderExtensibleOptionEngine(process));

        // C++ reference (probe): 7.8980381236205615
        assertEquals(7.8980381236205615, option.NPV(), 1.0e-4);
    }

    /**
     * Cross-validation: ITM call.
     * Expected from probe ("call_itm").
     */
    @Test
    public void testHolderExtensibleItmCall() {
        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.08);
        final SimpleQuote vol = new SimpleQuote(0.25);

        final GeneralizedBlackScholesProcess process = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, qRate, dc)),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, rRate, dc)),
                new Handle<BlackVolTermStructure>(Utilities.flatVol(today, vol, dc)));

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 90.0);
        final Exercise exercise = new EuropeanExercise(today.add(180));
        final HolderExtensibleOption option = new HolderExtensibleOption(
                Option.Type.Call, 1.0, today.add(270), 95.0, payoff, exercise);
        option.setPricingEngine(new AnalyticHolderExtensibleOptionEngine(process));

        // C++ reference (probe): 15.62466068541051
        assertEquals(15.62466068541051, option.NPV(), 1.0e-4);
    }

    /**
     * Cross-validation: OTM call with dividend.
     * Expected from probe ("call_with_dividend").
     */
    @Test
    public void testHolderExtensibleWithDividend() {
        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.03);
        final SimpleQuote rRate = new SimpleQuote(0.08);
        final SimpleQuote vol = new SimpleQuote(0.25);

        final GeneralizedBlackScholesProcess process = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, qRate, dc)),
                new Handle<YieldTermStructure>(Utilities.flatRate(today, rRate, dc)),
                new Handle<BlackVolTermStructure>(Utilities.flatVol(today, vol, dc)));

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final Exercise exercise = new EuropeanExercise(today.add(180));
        final HolderExtensibleOption option = new HolderExtensibleOption(
                Option.Type.Call, 1.0, today.add(270), 105.0, payoff, exercise);
        option.setPricingEngine(new AnalyticHolderExtensibleOptionEngine(process));

        // C++ reference (probe): 8.442612906636766
        assertEquals(8.442612906636766, option.NPV(), 1.0e-4);
    }
}

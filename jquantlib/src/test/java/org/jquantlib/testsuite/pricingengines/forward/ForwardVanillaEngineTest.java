/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.forward;

import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.forward.ForwardPerformanceVanillaEngine;
import org.jquantlib.pricingengines.forward.ForwardVanillaEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 5i.5-MGR — JQuantLib port of {@code ForwardVanillaEngine
 * <AnalyticEuropeanEngine>} and {@code ForwardPerformanceVanillaEngine
 * <AnalyticEuropeanEngine>} cross-validated against C++ QuantLib v1.42.1.
 *
 * <p>References:
 * {@code migration-harness/references/pricingengines/forward/forward_vanilla_engine.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-10 rel / 1e-9 abs).
 */
public class ForwardVanillaEngineTest {

    private static final String GROUP =
            "pricingengines/forward/forward_vanilla_engine";

    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    public ForwardVanillaEngineTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testAllCases() {
        int run = 0;
        for (final String name : REF.caseNames()) {
            runCase(name);
            run++;
        }
        assertTrue("expected at least 13 reference cases, got " + run, run >= 13);
    }

    private void runCase(final String name) {
        final Case c = REF.getCase(name);
        final JSONObject in = c.inputs();
        final JSONObject ex = (JSONObject) c.expectedRaw();

        final double S = in.getDouble("S");
        final double r = in.getDouble("r");
        final double q = in.getDouble("q");
        final double vol = in.getDouble("vol");
        final double moneyness = in.getDouble("moneyness");
        final int resetDays  = in.getInt("reset_days");
        final int expiryDays = in.getInt("expiry_days");
        final String typeStr = in.getString("option_type");
        final boolean isPerformance = in.has("variant") && "Performance".equals(in.getString("variant"));

        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();

        final GeneralizedBlackScholesProcess process = makeGBS(eval, S, r, q, vol, dc);

        final Date resetDate = eval.add(resetDays);
        final Date exDate    = eval.add(expiryDays);
        final Exercise exercise = new EuropeanExercise(exDate);

        final Option.Type type = "Call".equals(typeStr) ? Option.Type.Call : Option.Type.Put;
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, 0.0);

        final ForwardVanillaOption option = new ForwardVanillaOption(moneyness, resetDate, payoff, exercise);
        if (isPerformance) {
            option.setPricingEngine(new ForwardPerformanceVanillaEngine(process));
        } else {
            option.setPricingEngine(new ForwardVanillaEngine(process));
        }

        check("npv " + name, ex.getDouble("npv"), option.NPV());
        check("delta " + name, ex.getDouble("delta"), option.delta());
        check("gamma " + name, ex.getDouble("gamma"), option.gamma());
        check("theta " + name, ex.getDouble("theta"), option.theta());
        check("vega " + name, ex.getDouble("vega"), option.vega());
        check("rho " + name, ex.getDouble("rho"), option.rho());
        check("dividendRho " + name, ex.getDouble("dividendRho"), option.dividendRho());
    }

    private static void check(final String label, final double expected, final double actual) {
        final double err = Math.abs(actual - expected);
        final double bound = Math.max(1e-9, 1e-10 * Math.max(1.0, Math.abs(expected)));
        assertTrue(label + ": expected=" + expected + " actual=" + actual
                + " err=" + err + " bound=" + bound,
                err < bound);
    }

    private static GeneralizedBlackScholesProcess makeGBS(
            final Date eval, final double S, final double r, final double q,
            final double vol, final DayCounter dc) {
        final Handle<? extends Quote> spot =
                new Handle<SimpleQuote>(new SimpleQuote(S));
        final Handle<YieldTermStructure> rTS =
                new Handle<YieldTermStructure>(new FlatForward(eval, r, dc,
                        org.jquantlib.termstructures.Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> qTS =
                new Handle<YieldTermStructure>(new FlatForward(eval, q, dc,
                        org.jquantlib.termstructures.Compounding.Continuous, Frequency.Annual));
        final Calendar cal = new NullCalendar();
        final Handle<BlackVolTermStructure> volTS =
                new Handle<BlackVolTermStructure>(new BlackConstantVol(eval, cal, vol, dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }
}

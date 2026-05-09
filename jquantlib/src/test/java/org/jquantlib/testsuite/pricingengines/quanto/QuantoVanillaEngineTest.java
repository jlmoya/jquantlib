/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.quanto;

import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.QuantoVanillaOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.quanto.QuantoVanillaEngine;
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
 * Phase 5i.5-MGR — JQuantLib port of {@code QuantoEngine<VanillaOption,
 * AnalyticEuropeanEngine>} cross-validated against C++ QuantLib v1.42.1.
 *
 * <p>References:
 * {@code migration-harness/references/pricingengines/quanto/quanto_vanilla_engine.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-10 rel / 1e-9 abs) — analytic Black-Scholes
 * via QuantoTermStructure adjustment.
 */
public class QuantoVanillaEngineTest {

    private static final String GROUP =
            "pricingengines/quanto/quanto_vanilla_engine";

    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    public QuantoVanillaEngineTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testAllCases() {
        int run = 0;
        for (final String name : REF.caseNames()) {
            runCase(name);
            run++;
        }
        assertTrue("expected at least 11 reference cases, got " + run, run >= 11);
    }

    private void runCase(final String name) {
        final Case c = REF.getCase(name);
        final JSONObject in = c.inputs();
        final JSONObject ex = (JSONObject) c.expectedRaw();

        final double S = in.getDouble("S");
        final double r = in.getDouble("r");
        final double q = in.getDouble("q");
        final double vol = in.getDouble("vol");
        final double strike = in.getDouble("strike");
        final double T = in.getDouble("T");
        final String typeStr = in.getString("option_type");
        final double rf = in.getDouble("foreign_r");
        final double exVol = in.getDouble("exchange_vol");
        final double rho = in.getDouble("correlation");

        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();

        final GeneralizedBlackScholesProcess process = makeGBS(eval, S, r, q, vol, dc);

        final Handle<YieldTermStructure> foreignR =
                new Handle<YieldTermStructure>(new FlatForward(eval, rf, dc,
                        org.jquantlib.termstructures.Compounding.Continuous, Frequency.Annual));
        final Calendar cal = new NullCalendar();
        final Handle<BlackVolTermStructure> exchangeVol =
                new Handle<BlackVolTermStructure>(new BlackConstantVol(eval, cal, exVol, dc));
        final Handle<? extends Quote> corrQ =
                new Handle<SimpleQuote>(new SimpleQuote(rho));

        final Date exDate = eval.add((int) Math.round(T * 365));
        final Exercise exercise = new EuropeanExercise(exDate);

        final Option.Type type = "Call".equals(typeStr) ? Option.Type.Call : Option.Type.Put;
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

        final QuantoVanillaOption option = new QuantoVanillaOption(payoff, exercise);
        option.setPricingEngine(new QuantoVanillaEngine(process, foreignR, exchangeVol, corrQ));

        check("npv " + name, ex.getDouble("npv"), option.NPV());
        check("delta " + name, ex.getDouble("delta"), option.delta());
        check("gamma " + name, ex.getDouble("gamma"), option.gamma());
        check("theta " + name, ex.getDouble("theta"), option.theta());
        check("rho " + name, ex.getDouble("rho"), option.rho());
        check("dividendRho " + name, ex.getDouble("dividendRho"), option.dividendRho());
        check("vega " + name, ex.getDouble("vega"), option.vega());
        check("qvega " + name, ex.getDouble("qvega"), option.qvega());
        check("qrho " + name, ex.getDouble("qrho"), option.qrho());
        check("qlambda " + name, ex.getDouble("qlambda"), option.qlambda());
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

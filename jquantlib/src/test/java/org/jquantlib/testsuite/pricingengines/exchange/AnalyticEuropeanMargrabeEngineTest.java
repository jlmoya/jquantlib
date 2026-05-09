/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.exchange;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.MargrabeOption;
import org.jquantlib.pricingengines.exchange.AnalyticEuropeanMargrabeEngine;
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
 * Phase 5i.5-MGR — JQuantLib port of {@code AnalyticEuropeanMargrabeEngine}
 * cross-validated against C++ QuantLib v1.42.1 reference values.
 *
 * <p>References live in
 * {@code migration-harness/references/pricingengines/exchange/analytic_european_margrabe_engine.json}.
 *
 * <p>Tolerance tier: TIGHT (1e-9 abs / 1e-12 rel for NPV; 1e-9 abs for greeks)
 * — analytic closed-form, two GBS processes, exercises platform-libm exp/log
 * once (no integration), so very tight agreement is expected.
 */
public class AnalyticEuropeanMargrabeEngineTest {

    private static final String GROUP =
            "pricingengines/exchange/analytic_european_margrabe_engine";

    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    public AnalyticEuropeanMargrabeEngineTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testAllCases() {
        int run = 0;
        for (final String name : REF.caseNames()) {
            runCase(name);
            run++;
        }
        assertTrue("expected at least 12 reference cases, got " + run, run >= 12);
    }

    private void runCase(final String name) {
        final Case c = REF.getCase(name);
        final JSONObject in = c.inputs();
        final JSONObject ex = (JSONObject) c.expectedRaw();

        final double S1 = in.getDouble("S1");
        final double S2 = in.getDouble("S2");
        final double q1 = in.getDouble("q1");
        final double q2 = in.getDouble("q2");
        final double vol1 = in.getDouble("vol1");
        final double vol2 = in.getDouble("vol2");
        final double rho = in.getDouble("rho");
        final double T = in.getDouble("T");
        final int Q1 = in.getInt("Q1");
        final int Q2 = in.getInt("Q2");

        // Setup matches the probe: eval = 2026-01-15, Actual365Fixed, NullCalendar
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();

        final double r = 0.05;

        final GeneralizedBlackScholesProcess p1 = makeGBS(eval, S1, r, q1, vol1, dc);
        final GeneralizedBlackScholesProcess p2 = makeGBS(eval, S2, r, q2, vol2, dc);

        final Date exDate = eval.add((int) Math.round(T * 365));
        final Exercise exercise = new EuropeanExercise(exDate);

        final MargrabeOption option = new MargrabeOption(Q1, Q2, exercise);
        option.setPricingEngine(new AnalyticEuropeanMargrabeEngine(p1, p2, rho));

        final double npv = option.NPV();
        final double expectedNpv = ex.getDouble("npv");
        assertEqualsRel("NPV " + name, expectedNpv, npv, 1e-12, 1e-10);

        final double delta1 = option.delta1();
        assertEqualsRel("delta1 " + name, ex.getDouble("delta1"), delta1, 1e-12, 1e-10);

        final double delta2 = option.delta2();
        assertEqualsRel("delta2 " + name, ex.getDouble("delta2"), delta2, 1e-12, 1e-10);

        final double gamma1 = option.gamma1();
        assertEqualsRel("gamma1 " + name, ex.getDouble("gamma1"), gamma1, 1e-12, 1e-10);

        final double gamma2 = option.gamma2();
        assertEqualsRel("gamma2 " + name, ex.getDouble("gamma2"), gamma2, 1e-12, 1e-10);

        final double theta = option.theta();
        assertEqualsRel("theta " + name, ex.getDouble("theta"), theta, 1e-12, 1e-10);
    }

    private static void assertEqualsRel(final String label, final double expected,
                                        final double actual, final double relTol,
                                        final double absTol) {
        final double err = Math.abs(actual - expected);
        final double bound = Math.max(absTol, relTol * Math.max(1.0, Math.abs(expected)));
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
                new Handle<YieldTermStructure>(new FlatForward(eval, r, dc, org.jquantlib.termstructures.Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> qTS =
                new Handle<YieldTermStructure>(new FlatForward(eval, q, dc, org.jquantlib.termstructures.Compounding.Continuous, Frequency.Annual));
        final Calendar cal = new NullCalendar();
        final Handle<BlackVolTermStructure> volTS =
                new Handle<BlackVolTermStructure>(new BlackConstantVol(eval, cal, vol, dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }
}

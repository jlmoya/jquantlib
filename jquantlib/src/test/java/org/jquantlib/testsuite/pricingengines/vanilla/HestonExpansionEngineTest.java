/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.HestonExpansionEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 5h.5 cross-validation for {@link HestonExpansionEngine}.
 *
 * <p>Cross-validates Forde-formula NPVs against C++ v1.42.1 probe
 * ({@code migration-harness/cpp/probes/heston-engines/
 * heston_expansion_engine_probe.cpp}). LPP2 and LPP3 formulas are deferred
 * to Phase 5h.5b — those reference cases are present in the JSON but
 * intentionally not exercised here.
 *
 * <p><strong>Tolerance tier — TIGHT (1e-9 abs).</strong>
 * Pure analytic closed-form: degree-4 polynomial in log-moneyness fed into
 * the Black-1976 formula. Java-vs-C++ ULP drift on the elementary
 * transcendentals ({@code Math.log}, {@code Math.sqrt}, {@code Math.exp})
 * is the only error source and is empirically below 1e-12 absolute.
 */
public class HestonExpansionEngineTest {

    private static final double TIGHT_ABS = 1e-9;

    @Test
    public void fordeNpvMatchesCppProbe() {
        final ReferenceReader reader = ReferenceReader.load(
                "heston-engines/heston_expansion_engine");

        // Fixture must mirror the probe exactly.
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));
        final YieldTermStructure flatR = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.05)),
                dc, Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.02)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(flatQ);

        final HestonProcess process = new HestonProcess(rTS, qTS, s0,
                0.04,    // v0
                1.0,     // kappa
                0.04,    // theta
                0.3,     // sigma
                0.3);    // rho
        final HestonModel model = new HestonModel(process);

        // Walk every case; only handle Forde here (LPP2/LPP3 deferred).
        for (final String caseName : reader.caseNames()) {
            final Case ref = reader.getCase(caseName);
            final JSONObject in  = ref.inputs();
            final JSONObject exp = (JSONObject) ref.expectedRaw();

            final String formula = in.getString("formula");
            // Skip LPP3 (deferred to Phase 5h.5b — formulas are ~600 LOC of
            // Mathematica output across z0..z3, distinct risk profile).
            if ("LPP3".equals(formula)) {
                continue;
            }

            final double strike      = in.getDouble("strike");
            final double maturityYrs = in.getDouble("maturity_years");
            final Option.Type type   = "Call".equals(in.getString("option_type"))
                    ? Option.Type.Call : Option.Type.Put;
            final double expectedNpv = exp.getDouble("npv");

            final HestonExpansionEngine.Formula javaFormula =
                    "Forde".equals(formula) ? HestonExpansionEngine.Formula.Forde
                                            : HestonExpansionEngine.Formula.LPP2;

            final Date exerciseDate = eval.add((int) Math.round(maturityYrs * 365.0));
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
            final Exercise exercise = new EuropeanExercise(exerciseDate);

            final VanillaOption option = new VanillaOption(payoff, exercise);
            option.setPricingEngine(new HestonExpansionEngine(
                    model, process, javaFormula));

            final double npv = option.NPV();
            assertEquals(
                    formula + " Heston expansion NPV mismatch (case=" + ref.name() + ")",
                    expectedNpv, npv, TIGHT_ABS);
        }
    }

    /**
     * LPP3 placeholder — verifies the expected-deferred behavior.
     * Remove this test (or replace) when LPP3 is ported in Phase 5h.5b.
     */
    @Test
    public void lpp3ThrowsUnsupportedOperation() {
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));
        final YieldTermStructure flatR = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.05)),
                dc, Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.02)),
                dc, Compounding.Continuous, Frequency.Annual);
        final HestonProcess process = new HestonProcess(
                new Handle<YieldTermStructure>(flatR),
                new Handle<YieldTermStructure>(flatQ),
                s0, 0.04, 1.0, 0.04, 0.3, 0.3);
        final HestonModel model = new HestonModel(process);

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final Exercise exercise = new EuropeanExercise(eval.add(365));
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(new HestonExpansionEngine(
                model, process, HestonExpansionEngine.Formula.LPP3));

        try {
            option.NPV();
            fail("LPP3 should throw UnsupportedOperationException pending Phase 5h.5b port");
        } catch (final RuntimeException e) {
            // Expected: thrown either directly or wrapped through the option engine.
            Throwable cause = e;
            while (cause != null && !(cause instanceof UnsupportedOperationException)) {
                cause = cause.getCause();
            }
            if (cause == null) {
                throw e;
            }
        }
    }
}

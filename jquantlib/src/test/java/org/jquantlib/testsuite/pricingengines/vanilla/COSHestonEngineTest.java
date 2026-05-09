/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.assertEquals;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.COSHestonEngine;
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
 * Phase 5h.5 cross-validation for {@link COSHestonEngine}.
 *
 * <p>Cross-validates COS-method NPVs and the c1/c2/c3/c4 cumulants against
 * C++ v1.42.1 probe ({@code migration-harness/cpp/probes/heston-engines/
 * cos_heston_engine_probe.cpp}).
 *
 * <p><strong>Tolerance tier — TIGHT (1e-9 abs for cumulants, 1e-8 for NPV).</strong>
 * Cumulants are pure closed-form polynomials; NPVs are partial sums of N=200
 * cosine series with the Heston characteristic function, which uses log/exp
 * on complex arguments — slight ULP drift at the 1e-12 level is expected to
 * compound over the series sum.
 */
public class COSHestonEngineTest {

    private static final double NPV_ABS      = 1e-8;
    private static final double CUMULANT_ABS = 1e-9;

    private static HestonProcess buildProcess(final Date eval,
                                              final DayCounter dc,
                                              final Handle<Quote> s0) {
        final YieldTermStructure flatR = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.05)),
                dc, Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.02)),
                dc, Compounding.Continuous, Frequency.Annual);
        return new HestonProcess(
                new Handle<YieldTermStructure>(flatR),
                new Handle<YieldTermStructure>(flatQ),
                s0,
                0.04,    // v0
                1.0,     // kappa
                0.04,    // theta
                0.3,     // sigma
                0.3);    // rho (positive due to PositiveConstraint bug)
    }

    @Test
    public void npvAndCumulantsMatchCppProbe() {
        final ReferenceReader reader = ReferenceReader.load(
                "heston-engines/cos_heston_engine");

        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();
        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));

        final HestonProcess process = buildProcess(eval, dc, s0);
        final HestonModel model = new HestonModel(process);

        for (final String caseName : reader.caseNames()) {
            final Case ref = reader.getCase(caseName);
            final JSONObject in  = ref.inputs();
            final JSONObject exp = (JSONObject) ref.expectedRaw();

            if (caseName.startsWith("cumulants_")) {
                final double t = in.getDouble("t");
                final COSHestonEngine eng = new COSHestonEngine(model, process);

                assertEquals(caseName + ".c1", exp.getDouble("c1"), eng.c1(t), CUMULANT_ABS);
                assertEquals(caseName + ".c2", exp.getDouble("c2"), eng.c2(t), CUMULANT_ABS);
                assertEquals(caseName + ".c3", exp.getDouble("c3"), eng.c3(t), CUMULANT_ABS);
                assertEquals(caseName + ".c4", exp.getDouble("c4"), eng.c4(t), CUMULANT_ABS);
                assertEquals(caseName + ".mu",  exp.getDouble("mu"),  eng.mu(t),  CUMULANT_ABS);
                assertEquals(caseName + ".var", exp.getDouble("var"), eng.var(t), CUMULANT_ABS);
                assertEquals(caseName + ".skew", exp.getDouble("skew"), eng.skew(t), CUMULANT_ABS);
                assertEquals(caseName + ".kurtosis", exp.getDouble("kurtosis"), eng.kurtosis(t), CUMULANT_ABS);
            } else {
                // NPV case
                final double strike      = in.getDouble("strike");
                final double maturityYrs = in.getDouble("maturity_years");
                final Option.Type type   = "Call".equals(in.getString("option_type"))
                        ? Option.Type.Call : Option.Type.Put;
                final double L           = in.getDouble("L");
                final int N              = in.getInt("N");
                final double expectedNpv = exp.getDouble("npv");

                final Date exerciseDate = eval.add((int) Math.round(maturityYrs * 365.0));
                final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
                final Exercise exercise = new EuropeanExercise(exerciseDate);

                final VanillaOption option = new VanillaOption(payoff, exercise);
                option.setPricingEngine(new COSHestonEngine(model, process, L, N));

                final double npv = option.NPV();
                assertEquals("COS Heston NPV mismatch (case=" + caseName + ")",
                             expectedNpv, npv, NPV_ABS);
            }
        }
    }
}

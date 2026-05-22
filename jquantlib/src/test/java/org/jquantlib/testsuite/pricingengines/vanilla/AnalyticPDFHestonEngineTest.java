/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.AnalyticPDFHestonEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Phase 5h.5-RND tests for {@link AnalyticPDFHestonEngine}.
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1 via the migration harness;
 * reference data lives at
 * {@code migration-harness/references/pricingengines/vanilla/analytic_pdf_heston_engine.json}.
 *
 * <p>Setup mirrors C++ test-suite/hestonmodel.cpp testAnalyticPDFHestonEngine
 * (S0=100, v0=0.1, kappa=4.0, theta=0.05, sigma=1.0, rho=-0.5, r=0.07,
 * q=0.185, settlement=2014-01-05, maturity=2014-07-05).
 *
 * <p>Tolerance: LOOSE 1e-4 (Fourier inversion + Gauss-Lobatto outer integration
 * accumulate ULPs differently between Boost and JQuantLib's Complex).
 *
 * @author Phase 5h.5-RND port
 */
public class AnalyticPDFHestonEngineTest {

    private static final double TOL = 1.0e-4;

    private AnalyticPDFHestonEngine buildEngine() {
        final Date settlement = new Date(5, Month.January, 2014);
        new Settings().setEvaluationDate(settlement);

        final DayCounter dc = new Actual365Fixed();

        final var rTS = new Handle<YieldTermStructure>(
                new FlatForward(settlement, new Handle<Quote>(new SimpleQuote(0.07)), dc));
        final var qTS = new Handle<YieldTermStructure>(
                new FlatForward(settlement, new Handle<Quote>(new SimpleQuote(0.185)), dc));

        final var spot = new Handle<Quote>(new SimpleQuote(100.0));
        final HestonProcess process = new HestonProcess(rTS, qTS, spot,
                0.1, 4.0, 0.05, 1.0, -0.5);
        final HestonModel model = new HestonModel(process);

        return new AnalyticPDFHestonEngine(model, process, 1.0e-6, 10000);
    }

    @Test
    public void testCallNpvVsCppReference() {
        new Settings().setEvaluationDate(new Date(5, Month.January, 2014));

        final ReferenceReader ref = ReferenceReader.load(
                "pricingengines/vanilla/analytic_pdf_heston_engine");
        final AnalyticPDFHestonEngine engine = buildEngine();

        final Date maturity = new Date(5, Month.July, 2014);
        final Exercise exercise = new EuropeanExercise(maturity);

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("call_strike_")) continue;

            final ReferenceReader.Case rc = ref.getCase(name);
            final double strike = rc.inputs().getDouble("strike");
            final JSONObject exp = (JSONObject) rc.expectedRaw();
            final double expectedNpv = exp.getDouble("npv");

            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, strike);
            final VanillaOption opt = new VanillaOption(payoff, exercise);
            opt.setPricingEngine(engine);

            final double actualNpv = opt.NPV();
            assertEquals(name + " NPV", expectedNpv, actualNpv,
                    Math.max(TOL, Math.abs(expectedNpv) * TOL));
        }
    }

    @Test
    public void testPvAccessorVsCppReference() {
        new Settings().setEvaluationDate(new Date(5, Month.January, 2014));

        final ReferenceReader ref = ReferenceReader.load(
                "pricingengines/vanilla/analytic_pdf_heston_engine");
        final AnalyticPDFHestonEngine engine = buildEngine();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("Pv_k")) continue;

            final ReferenceReader.Case rc = ref.getCase(name);
            final double x = rc.inputs().getDouble("x");
            final double t = rc.inputs().getDouble("t");
            final JSONObject exp = (JSONObject) rc.expectedRaw();
            final double expectedPdf = exp.getDouble("pdf");

            assertEquals(name + " Pv", expectedPdf, engine.Pv(x, t),
                    Math.max(TOL, Math.abs(expectedPdf) * TOL));
        }
    }

    @Test
    public void testCdfAccessorVsCppReference() {
        new Settings().setEvaluationDate(new Date(5, Month.January, 2014));

        final ReferenceReader ref = ReferenceReader.load(
                "pricingengines/vanilla/analytic_pdf_heston_engine");
        final AnalyticPDFHestonEngine engine = buildEngine();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("cdf_k")) continue;

            final ReferenceReader.Case rc = ref.getCase(name);
            final double s = rc.inputs().getDouble("S");
            final double t = rc.inputs().getDouble("t");
            final JSONObject exp = (JSONObject) rc.expectedRaw();
            final double expectedCdf = exp.getDouble("cdf");

            assertEquals(name + " cdf", expectedCdf, engine.cdf(s, t),
                    Math.max(TOL, Math.abs(expectedCdf) * TOL));
        }
    }
}

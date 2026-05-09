/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4a A.2 — IntegralHestonVarianceOptionEngine cross-validation tests.

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
 */
package org.jquantlib.testsuite.experimental.varianceoption;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.varianceoption.IntegralHestonVarianceOptionEngine;
import org.jquantlib.experimental.varianceoption.VarianceOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
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
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Cross-validated tests for {@link IntegralHestonVarianceOptionEngine}
 * against v1.42.1 reference values in
 * {@code migration-harness/references/experimental/varianceoption/integral_heston_variance_option_engine.json}.
 *
 * <p>Tolerance: per-case loose tier suffices for two QL-test-suite scenarios
 * (the engine's published precision is 1e-7); for outliers we use a weaker
 * within() tier with explicit justification (the Bailey-Swarztrauber DFT
 * trades off imaginary-residual control vs. real-part precision; near-zero
 * call prices have larger relative error).
 */
public class IntegralHestonVarianceOptionEngineTest {

    private static final String GROUP =
        "experimental/varianceoption/integral_heston_variance_option_engine";

    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    public IntegralHestonVarianceOptionEngineTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void qlts_call_v0_2_0_T_1_5_K_0_05() {
        runCase("qlts_call_v0_2.0_T_1.5_K_0.05", 1.0e-7);
    }

    @Test
    public void qlts_put_v0_1_5_T_1_0_K_0_7() {
        runCase("qlts_put_v0_1.5_T_1.0_K_0.7", 1.0e-7);
    }

    @Test
    public void call_v0_1_0_T_1_0_K_0_5() {
        // small NPV (~5e-5): use absolute tolerance dominated form
        runCase("call_v0_1.0_T_1.0_K_0.5", 1.0e-9);
    }

    @Test
    public void call_v0_2_0_T_2_0_K_0_10() {
        runCase("call_v0_2.0_T_2.0_K_0.10", 1.0e-7);
    }

    @Test
    public void call_v0_1_5_T_0_5_K_0_30() {
        runCase("call_v0_1.5_T_0.5_K_0.30", 1.0e-7);
    }

    @Test
    public void call_v0_2_0_T_1_0_K_0_20_n100() {
        runCase("call_v0_2.0_T_1.0_K_0.20_n100", 1.0e-5);
    }

    private void runCase(final String name, final double absRelTol) {
        final Case c = REF.getCase(name);
        final JSONObject in = c.inputs();

        final double v0 = in.getDouble("v0");
        final double kappa = in.getDouble("kappa");
        final double theta = in.getDouble("theta");
        final double sigma = in.getDouble("sigma");
        final double rho = in.getDouble("rho");
        final double strike = in.getDouble("strike");
        final double nominal = in.getDouble("nominal");
        final int days = in.getInt("days_to_maturity");
        final String typeStr = in.getString("type");

        final Date eval = Date.todaysDate();
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual360();
        // Java HestonProcess does not accept an empty handle; pass a flat-zero
        // dividend curve to mirror the C++ probe's behaviour (q = 0). The
        // engine itself ignores dividends in the analytic formula.
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(eval, 0.0, dc, Compounding.Continuous));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(eval, 0.0, dc, Compounding.Continuous));
        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(1.0));

        final HestonProcess proc = new HestonProcess(rTS, qTS, s0,
                v0, kappa, theta, sigma, rho);
        final IntegralHestonVarianceOptionEngine engine =
                new IntegralHestonVarianceOptionEngine(proc);

        final Option.Type type = "Call".equals(typeStr) ? Option.Type.Call : Option.Type.Put;
        final Payoff payoff = new PlainVanillaPayoff(type, strike);
        final Date maturity = eval.add(days);

        final VarianceOption opt = new VarianceOption(payoff, nominal, eval, maturity);
        opt.setPricingEngine(engine);
        final double actual = opt.NPV();
        final double expected = ((JSONObject) c.expectedRaw()).getDouble("npv");

        final double err = Math.abs(actual - expected);
        final double bound = absRelTol + absRelTol * Math.abs(expected);
        if (err >= bound) {
            QL.info(String.format(
                "FAIL %s: actual=%.10g expected=%.10g err=%.3g bound=%.3g",
                name, actual, expected, err, bound));
        }
        assertTrue("VarianceOption NPV mismatch for " + name
                + ": expected=" + expected + " actual=" + actual
                + " err=" + err + " bound=" + bound,
                err < bound);
    }
}

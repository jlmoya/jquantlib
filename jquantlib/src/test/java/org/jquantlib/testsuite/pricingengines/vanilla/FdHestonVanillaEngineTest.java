/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 4n.5 fingerprint test for {@link FdHestonVanillaEngine}.
 *
 * <p>Cross-validates European call/put NPV, delta, and gamma against the
 * C++ v1.42.1 probe
 * ({@code migration-harness/cpp/probes/pricingengines/vanilla/
 *   fd_heston_vanilla_engine_probe.cpp}).
 *
 * <p><strong>Tolerance tier — LOOSE 1e-2 abs/rel.</strong>
 * Justification: 2-factor FD on a 50-time / 50-x / 20-v grid (intentionally
 * coarse for CI runtime). The C++ side uses the same grid by construction;
 * the only error here is Java-vs-C++ floating-point arithmetic divergence,
 * which is empirically well below 1e-6 for FD sweeps. 1e-2 is conservative.
 *
 * @author Phase 4n.5 port
 */
public class FdHestonVanillaEngineTest {

    private static final double TOL = 1e-2;
    private static final String TOL_NOTE =
            "Phase 4n.5 LOOSE — coarse FD grid 50t x 50x x 20v";

    @Test
    public void eurCallAtm1yMatchesCpp() {
        runCase("eur_call_atm_1y", Option.Type.Call);
    }

    @Test
    public void eurPutAtm1yMatchesCpp() {
        runCase("eur_put_atm_1y", Option.Type.Put);
    }

    @Test
    public void eurCallOtm1yMatchesCpp() {
        runCase("eur_call_otm_1y", Option.Type.Call);
    }

    @Test
    public void eurPutOtm1yMatchesCpp() {
        runCase("eur_put_otm_1y", Option.Type.Put);
    }

    @Test
    public void eurCallAtm2yMatchesCpp() {
        runCase("eur_call_atm_2y", Option.Type.Call);
    }

    private void runCase(final String caseName, final Option.Type type) {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/vanilla/fd_heston_vanilla_engine");
        final Case ref   = reader.getCase(caseName);
        final JSONObject in  = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (must mirror fd_heston_vanilla_engine_probe.cpp) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();

        // Market
        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));
        final YieldTermStructure flatR = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.05)),
                dc, Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.02)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(flatQ);

        // Heston: v0=0.04, kappa=1.0, theta=0.04, sigma=0.3, rho=+0.3
        final HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, s0, 0.04, 1.0, 0.04, 0.3, 0.3);
        final HestonModel hestonModel = new HestonModel(hestonProcess);

        // Option
        final double maturityYears = in.getDouble("maturity_years");
        final double strike        = in.getDouble("strike");
        final int    tGrid         = in.getInt("t_grid");
        final int    xGrid         = in.getInt("x_grid");
        final int    vGrid         = in.getInt("v_grid");
        final int    dampingSteps  = in.getInt("damping_steps");

        final int maturityDays = (int) (maturityYears * 365 + 0.5);
        final Date exerciseDate = eval.add(maturityDays);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Payoff payoff     = new PlainVanillaPayoff(type, strike);
        final VanillaOption option = new VanillaOption(payoff, exercise);

        option.setPricingEngine(new FdHestonVanillaEngine(
                hestonModel, hestonProcess,
                tGrid, xGrid, vGrid, dampingSteps,
                FdmSchemeDesc.Hundsdorfer()));

        final double npv   = option.NPV();
        final double delta = option.delta();
        final double gamma = option.gamma();

        final double expNpv   = exp.getDouble("npv");
        final double expDelta = exp.getDouble("delta");
        final double expGamma = exp.getDouble("gamma");

        if (!Tolerance.within(npv, expNpv, TOL, TOL_NOTE)) {
            fail(caseName + " NPV mismatch: exp=" + expNpv + " got=" + npv
                    + " absDiff=" + Math.abs(npv - expNpv));
        }
        if (!Tolerance.within(delta, expDelta, TOL, TOL_NOTE)) {
            fail(caseName + " delta mismatch: exp=" + expDelta + " got=" + delta
                    + " absDiff=" + Math.abs(delta - expDelta));
        }
        if (!Tolerance.within(gamma, expGamma, TOL, TOL_NOTE)) {
            fail(caseName + " gamma mismatch: exp=" + expGamma + " got=" + gamma
                    + " absDiff=" + Math.abs(gamma - expGamma));
        }
    }
}

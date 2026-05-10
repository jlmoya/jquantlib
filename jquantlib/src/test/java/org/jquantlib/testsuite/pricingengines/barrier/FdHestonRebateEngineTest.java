/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.barrier;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BarrierOption;
import org.jquantlib.instruments.BarrierType;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.barrier.FdHestonRebateEngine;
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
 * Phase 4n.5b fingerprint test for {@link FdHestonRebateEngine}.
 *
 * <p>Cross-validates rebate-engine NPV/delta/gamma against the C++ v1.42.1
 * probe ({@code migration-harness/cpp/probes/pricingengines/barrier/
 * fd_heston_rebate_engine_probe.cpp}). All four barrier types exercised
 * (DownOut/UpOut/DownIn/UpIn). Note the C++ rebate engine treats In/Out
 * symmetrically — the boundary placement (Lower for {@code Down*},
 * Upper for {@code Up*}) is what determines the result, not the In/Out
 * label, since the inner-value calculator is the constant rebate
 * regardless.
 *
 * <p><strong>Tolerance tier — LOOSE 1e-2 abs/rel.</strong>
 * Justification: 2-factor FD on a 50t x 50x x 20v grid (intentionally
 * coarse for CI). Same tier as {@link FdHestonBarrierEngineTest}.
 *
 * @author Phase 4n.5b port
 */
public class FdHestonRebateEngineTest {

    private static final double TOL = 1e-2;
    private static final String TOL_NOTE =
            "Phase 4n.5b LOOSE — coarse FD grid 50t x 50x x 20v, rebate";

    @Test
    public void downOutCallRebate10MatchesCpp() {
        runCase("down_out_call_K100_B80_rebate10_1y",
                BarrierType.DownOut, Option.Type.Call);
    }

    @Test
    public void upOutCallRebate10MatchesCpp() {
        runCase("up_out_call_K100_B120_rebate10_1y",
                BarrierType.UpOut, Option.Type.Call);
    }

    @Test
    public void downInCallRebate10MatchesCpp() {
        runCase("down_in_call_K100_B80_rebate10_1y",
                BarrierType.DownIn, Option.Type.Call);
    }

    @Test
    public void upInCallRebate10MatchesCpp() {
        runCase("up_in_call_K100_B120_rebate10_1y",
                BarrierType.UpIn, Option.Type.Call);
    }

    @Test
    public void downOutPutRebate10MatchesCpp() {
        runCase("down_out_put_K100_B80_rebate10_1y",
                BarrierType.DownOut, Option.Type.Put);
    }

    @Test
    public void downInPutRebate10MatchesCpp() {
        runCase("down_in_put_K100_B80_rebate10_1y",
                BarrierType.DownIn, Option.Type.Put);
    }

    // ------------------------------------------------------------------

    private void runCase(final String caseName,
                         final BarrierType barrierType,
                         final Option.Type type) {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/barrier/fd_heston_rebate_engine");
        final Case ref   = reader.getCase(caseName);
        final JSONObject in  = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final double maturityYears = in.getDouble("maturity_years");
        final double strike        = in.getDouble("strike");
        final double barrier       = in.getDouble("barrier");
        final double rebate        = in.getDouble("rebate");
        final int    tGrid         = in.getInt("t_grid");
        final int    xGrid         = in.getInt("x_grid");
        final int    vGrid         = in.getInt("v_grid");
        final int    dampingSteps  = in.getInt("damping_steps");

        final int maturityDays = (int) (maturityYears * 365 + 0.5);
        final Date exerciseDate = eval.add(maturityDays);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);
        final BarrierOption option = new BarrierOption(
                barrierType, barrier, rebate, payoff, exercise);

        option.setPricingEngine(makeEngine(tGrid, xGrid, vGrid, dampingSteps));

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

    private FdHestonRebateEngine makeEngine(final int tGrid, final int xGrid,
                                            final int vGrid, final int dampingSteps) {
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();

        final Handle<Quote> s0 = new Handle<Quote>(new SimpleQuote(100.0));
        final YieldTermStructure flatR = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.05)), dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(eval,
                new Handle<Quote>(new SimpleQuote(0.02)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(flatQ);

        final HestonProcess process = new HestonProcess(
                rTS, qTS, s0, 0.04, 1.0, 0.04, 0.3, 0.3);
        final HestonModel model = new HestonModel(process);

        return new FdHestonRebateEngine(
                model, process, tGrid, xGrid, vGrid, dampingSteps,
                FdmSchemeDesc.Hundsdorfer());
    }
}

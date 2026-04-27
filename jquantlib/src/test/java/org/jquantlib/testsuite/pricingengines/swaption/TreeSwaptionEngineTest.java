/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.swaption;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.TreeSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 2e WI-3 fingerprint test for {@link TreeSwaptionEngine}.
 *
 * <p>Cross-validates {@code Swaption.NPV()} on a 5Y x 5Y ATM payer swaption
 * priced under a HullWhite (a=0.1, sigma=0.01) tree (100 steps) against the
 * C++ v1.42.1 probe (see
 * {@code migration-harness/cpp/probes/pricingengines/swaption/treeswaptionengine_probe.cpp}).
 *
 * <p><strong>Tolerance tier — tight</strong> (abs 1e-14 + rel 1e-12)
 * post Phase 2g WI-1 Brent.solveImpl alignment. Tree-based pricing
 * depends on the model's tree construction, including the Brent solver
 * in {@code TermStructureFittingParameter} for term-structure-consistent
 * models. Phase 2g WI-1 aligned Java Brent with C++ v1.42.1 brent.hpp,
 * eliminating the Phase 2c WI-5 BK tree precedent's solver-noise-floor
 * justification for loose tier.
 */
public class TreeSwaptionEngineTest {

    private static final double TIGHT_REL_TOL = 1.0e-12;
    private static final double TIGHT_ABS_TOL = 1.0e-14;

    @Test
    public void atmPayerSwaption_hwTreeNpvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/treeswaptionengine");
        final Case ref = reader.getCase("atm_payer_5y5y_hw_tree");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (must mirror treeswaptionengine_probe.cpp) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final double flatRate = in.getDouble("flat_rate");
        final double hwA = in.getDouble("hw_a");
        final double hwSigma = in.getDouble("hw_sigma");
        final int timeSteps = in.getInt("time_steps");
        final double nominal = in.getDouble("nominal");
        final double dummyRate = in.getDouble("dummy_fixed_rate");

        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(flatRate)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);

        final Date exerciseDate = cal.advance(eval,
                new Period(in.getInt("exercise_years"), TimeUnit.Years),
                BusinessDayConvention.Following);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Date startDate = cal.advance(exerciseDate, 2, TimeUnit.Days,
                BusinessDayConvention.Following, false);
        final Date maturity = cal.advance(startDate,
                new Period(in.getInt("swap_years"), TimeUnit.Years),
                BusinessDayConvention.Following);

        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);

        final Schedule fixedSchedule = new Schedule(
                startDate, maturity, new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
        final Schedule floatSchedule = new Schedule(
                startDate, maturity,
                new Period(in.getInt("float_tenor_months"), TimeUnit.Months),
                cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        // Step 1: dummy swap to read par rate.
        final VanillaSwap swap0 = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, dummyRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        swap0.setPricingEngine(new DiscountingSwapEngine(ts));
        final double atmRate = swap0.fairRate();

        // Step 2: ATM swap + swaption priced via HullWhite tree.
        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, atmRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        final Swaption swaption = new Swaption(swap, exercise);

        final HullWhite hw = new HullWhite(ts, hwA, hwSigma);
        swaption.setPricingEngine(new TreeSwaptionEngine(hw, timeSteps, ts));
        final double npv = swaption.NPV();

        // ---- Cross-validate ----
        // Phase 2g WI-1: tight tier (abs 1e-14 + rel 1e-12) post-Brent fix.
        final double expNpv = exp.getDouble("swaption_npv_hw_tree");
        if (Math.abs(npv - expNpv) > TIGHT_ABS_TOL
                && Math.abs((npv - expNpv) / expNpv) > TIGHT_REL_TOL) {
            fail("swaption.NPV() (HW tree): exp=" + expNpv + " got=" + npv
                    + " absDiff=" + (npv - expNpv));
        }
    }
}

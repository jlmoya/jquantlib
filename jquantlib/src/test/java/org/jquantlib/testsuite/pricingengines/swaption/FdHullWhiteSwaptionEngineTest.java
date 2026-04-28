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
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.FdHullWhiteSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
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
 * Phase 2h WI-2 fingerprint test for {@link FdHullWhiteSwaptionEngine}.
 *
 * <p>Cross-validates {@code Swaption.NPV()} for a 5Y x 5Y ATM payer
 * swaption priced under a Hull-White model (a = 0.1, sigma = 0.01) using
 * the finite-difference engine against a C++ v1.42.1 probe (see
 * {@code migration-harness/cpp/probes/pricingengines/swaption/fdhullwhiteswaptionengine_probe.cpp}).
 *
 * <p><strong>Tolerance tier</strong> — loose
 * (abs 1e-8 + rel 1e-8). Observed |Java − C++| ≈ 2.0e-12, just over the
 * tight threshold ({@code 1e-14 + 1e-12 * 1.96 ≈ 1.96e-12}); the
 * residual is dominated by accumulated 1-ULP {@code Math.exp}/{@code log}
 * drift across ~100 ADI rollback steps × ~100 mesh points × ~20
 * {@code A(t,T) * exp(-B(t,T) * r)} discount-bond evaluations per node.
 * The Java port uses the same Douglas ADI rollback shape as C++ and a
 * HullWhite-specific inner-value calculator that mirrors the C++
 * {@code FdmAffineModelSwapInnerValue<HullWhite>} template specialisation
 * (clones the swap with {@code iborIndex.clone(fwdTs)}, rebinds a
 * {@link org.jquantlib.methods.finitedifferences.utilities.FdmAffineModelTermStructure}
 * per exercise date, and re-prices the legs). Phase 2g A13-style
 * structural slack — Math.exp 1-ULP — is the documented reason.
 */
public class FdHullWhiteSwaptionEngineTest {

    @Test
    public void atmPayerSwaption_npvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/fdhullwhiteswaptionengine");
        final Case ref = reader.getCase("atm_payer_5y5y");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (mirrors fdhullwhiteswaptionengine_probe.cpp) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final double flatRate = in.getDouble("flat_rate");
        final double hwA = in.getDouble("hw_a");
        final double hwSigma = in.getDouble("hw_sigma");
        final double nominal = in.getDouble("nominal");
        final double dummyRate = in.getDouble("dummy_fixed_rate");
        final int tGrid = in.getInt("t_grid");
        final int xGrid = in.getInt("x_grid");
        final int dampingSteps = in.getInt("damping_steps");
        final double invEps = in.getDouble("inv_eps");

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

        // Step 1: dummy swap to read par rate (fixture parity check).
        final VanillaSwap swap0 = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, dummyRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        swap0.setPricingEngine(new DiscountingSwapEngine(ts));
        final double atmRate = swap0.fairRate();
        final double swap0NPV = swap0.NPV();

        // Step 2: ATM swap + Hull-White FD swaption.
        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, atmRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        final Swaption swaption = new Swaption(swap, exercise);
        final HullWhite hw = new HullWhite(ts, hwA, hwSigma);
        swaption.setPricingEngine(new FdHullWhiteSwaptionEngine(
                hw, tGrid, xGrid, dampingSteps, invEps,
                FdmSchemeDesc.Douglas()));
        final double npv = swaption.NPV();

        // ---- Cross-validate ----
        final double expSwap0NPV = exp.getDouble("swap0_npv");
        final double expAtmRate = exp.getDouble("atm_rate");
        final double expSwaptionNPV = exp.getDouble("fd_hw_swaption_npv");

        // Fixture parity: swap fixture is shared with Phase 2f
        // JamshidianSwaptionEngineTest, which lands these at the tight
        // tier. Use tight here to make any fixture drift loud.
        if (!Tolerance.tight(swap0NPV, expSwap0NPV)) {
            fail("swap0.NPV(): exp=" + expSwap0NPV + " got=" + swap0NPV);
        }
        if (!Tolerance.tight(atmRate, expAtmRate)) {
            fail("atmRate: exp=" + expAtmRate + " got=" + atmRate);
        }
        // FD NPV — loose tier (see class-level note).
        if (!Tolerance.loose(npv, expSwaptionNPV)) {
            fail("FdHullWhite.NPV(): exp=" + expSwaptionNPV + " got=" + npv
                 + " diff=" + Math.abs(npv - expSwaptionNPV));
        }
    }
}

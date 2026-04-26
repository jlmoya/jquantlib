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
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
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
 * Phase 2e WI-3 fingerprint test for {@link BlackSwaptionEngine}.
 *
 * <p>Cross-validates {@code Swaption.NPV()} for a 5Y x 5Y ATM payer swaption
 * priced under Black76 (shifted-lognormal, displacement = 0) against a C++
 * v1.42.1 probe (see
 * {@code migration-harness/cpp/probes/pricingengines/swaption/blackswaptionengine_probe.cpp}).
 *
 * <p><strong>Tolerance tier</strong> — tight (abs 1e-14 + rel 1e-12).
 * Closed-form Black76 on top of {@code DiscountingSwapEngine} (which is
 * itself closed-form discounted-cashflow accumulation). Both Java and C++
 * walk identical Schedules with the same calendar/BDC, build the same legs,
 * and run the same Black formula. Should be effectively bit-equal.
 */
public class BlackSwaptionEngineTest {

    @Test
    public void atmPayerSwaption_npvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/blackswaptionengine");
        final Case ref = reader.getCase("atm_payer_5y5y");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (must mirror blackswaptionengine_probe.cpp exactly) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final double flatRate = in.getDouble("flat_rate");
        final double vol = in.getDouble("vol");
        final double nominal = in.getDouble("nominal");
        final double dummyRate = in.getDouble("dummy_fixed_rate");

        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(flatRate)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);

        // Exercise = eval + 5Y on TARGET; swap starts spot+2BD; matures +5Y.
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
        final double swap0NPV = swap0.NPV();

        // Step 2: ATM swap + swaption priced via BlackSwaptionEngine.
        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, atmRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        final Swaption swaption = new Swaption(swap, exercise);
        swaption.setPricingEngine(new BlackSwaptionEngine(ts, vol));
        final double npv = swaption.NPV();

        // ---- Cross-validate ----
        final double expSwap0NPV   = exp.getDouble("swap0_npv");
        final double expAtmRate    = exp.getDouble("atm_rate");
        final double expSwaptionNPV = exp.getDouble("swaption_npv");

        if (!Tolerance.tight(swap0NPV, expSwap0NPV)) {
            fail("swap0.NPV(): exp=" + expSwap0NPV + " got=" + swap0NPV);
        }
        if (!Tolerance.tight(atmRate, expAtmRate)) {
            fail("atmRate: exp=" + expAtmRate + " got=" + atmRate);
        }
        if (!Tolerance.tight(npv, expSwaptionNPV)) {
            fail("swaption.NPV(): exp=" + expSwaptionNPV + " got=" + npv);
        }
    }
}

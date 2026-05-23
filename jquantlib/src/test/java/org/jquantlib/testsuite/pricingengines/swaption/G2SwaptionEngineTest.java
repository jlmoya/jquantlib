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
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.G2SwaptionEngine;
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
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 2 L3-C fingerprint test for
 * {@link org.jquantlib.pricingengines.swaption.G2SwaptionEngine}.
 *
 * <p>Cross-validates {@code Swaption.NPV()} for a 5Y x 5Y ATM payer
 * swaption priced via the G2++ analytic-integral engine against the
 * existing C++ v1.42.1 reference produced by the
 * {@code model/shortrate/twofactormodels/g2_probe::testSwaptionIntegral}
 * block. The expected value lives in
 * {@code migration-harness/references/model/shortrate/twofactormodels/g2.json}
 * under the {@code g2_swaption_integral_fingerprint} case
 * ({@code swaption_integral} field).
 *
 * <p><strong>Tolerance tier — tight</strong> (abs {@code 1e-14} + rel
 * {@code 1e-12}). G2.swaption invokes {@code SegmentIntegral} over an inner
 * {@code Brent} solver; the Phase 2g WI-1 Brent fix already locked both
 * to bit-faithful C++ agreement. The engine itself adds only a spread
 * correction (zero on this ATM fixture) and a {@code DiscountingSwapEngine}
 * rebind, both of which are deterministic.
 */
public class G2SwaptionEngineTest {

    @Test
    public void atmPayerSwaption_g2NpvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "model/shortrate/twofactormodels/g2");
        final Case ref = reader.getCase("g2_swaption_integral_fingerprint");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (mirrors g2_probe.cpp swaption block exactly) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final double rCurve = in.getDouble("r_curve");
        final double nominal = in.getDouble("nominal");
        final double dummyRate = in.getDouble("dummy_fixed_rate");

        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(rCurve)), dc,
                Compounding.Continuous, org.jquantlib.time.Frequency.Annual);
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

        // Step 1: dummy swap to read par rate (must agree with C++ atm_rate).
        final VanillaSwap swap0 = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, dummyRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        swap0.setPricingEngine(new DiscountingSwapEngine(ts));
        final double atmRate = swap0.fairRate();
        final double expAtm = exp.getDouble("atm_rate");
        if (!Tolerance.tight(atmRate, expAtm)) {
            fail("atmRate: exp=" + expAtm + " got=" + atmRate);
        }

        // Step 2: ATM swap + swaption priced via G2SwaptionEngine.
        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, atmRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        final Swaption swaption = new Swaption(swap, exercise);

        final G2 model = new G2(ts,
                in.getDouble("a"), in.getDouble("sigma"),
                in.getDouble("b"), in.getDouble("eta"), in.getDouble("rho"));

        final double range = in.getDouble("range");
        final int intervals = in.getInt("intervals");
        swaption.setPricingEngine(new G2SwaptionEngine(model, range, intervals));
        final double npv = swaption.NPV();

        // ---- Cross-validate against C++ swaption_integral. ----
        final double expSwaption = exp.getDouble("swaption_integral");
        if (!Tolerance.tight(npv, expSwaption)) {
            fail("swaption.NPV() (G2 engine): exp=" + expSwaption + " got=" + npv
                    + " absDiff=" + Math.abs(npv - expSwaption));
        }
    }
}

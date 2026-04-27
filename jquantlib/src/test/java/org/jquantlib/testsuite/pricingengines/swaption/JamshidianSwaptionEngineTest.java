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
import org.jquantlib.pricingengines.swaption.JamshidianSwaptionEngine;
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
 * Phase 2f WI-2 fingerprint test for {@link JamshidianSwaptionEngine}.
 *
 * <p>Cross-validates {@code Swaption.NPV()} for a 5Y x 5Y ATM payer swaption
 * priced under a Hull-White model (a = 0.1, sigma = 0.01) using the
 * Jamshidian bond-option decomposition against a C++ v1.42.1 probe (see
 * {@code migration-harness/cpp/probes/pricingengines/swaption/jamshidianswaptionengine_probe.cpp}).
 *
 * <p><strong>Tolerance tier</strong> — loose (abs 1e-8 + rel 1e-8) for the
 * Jamshidian NPV; tight for the swap fixture sanity values. Bond / bond-option
 * pricers are deterministic and bit-exact match C++; the noise floor comes
 * from the Brent solver's r*. The Java {@link org.jquantlib.math.solvers1D.Brent}
 * solver's pre-loop initialisation differs from the C++ v1.42.1
 * {@code brent.hpp} (Java seeds the algorithm with {@code root = xMax} while
 * C++ evaluates {@code f(guess)} first to seed {@code root_/d/e}); the
 * algorithmic divergence yields an ~5e-9 root drift which propagates to a
 * ~7e-11 absolute NPV difference (~37x the tight-tier ceiling, ~5 orders of
 * magnitude below the loose-tier ceiling). Aligning the Java Brent
 * initialisation is out of scope for Phase 2f WI-2 because every Brent
 * caller in the codebase would need re-fingerprinting; the work is captured
 * in {@code docs/migration/phase2f-progress.md} as a deferred align.
 */
public class JamshidianSwaptionEngineTest {

    @Test
    public void atmPayerSwaption_npvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/jamshidianswaptionengine");
        final Case ref = reader.getCase("atm_payer_5y5y");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (mirrors jamshidianswaptionengine_probe.cpp) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final double flatRate = in.getDouble("flat_rate");
        final double hwA = in.getDouble("hw_a");
        final double hwSigma = in.getDouble("hw_sigma");
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

        // Step 1: dummy swap to read par rate (cross-check fixture parity).
        final VanillaSwap swap0 = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, dummyRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        swap0.setPricingEngine(new DiscountingSwapEngine(ts));
        final double atmRate = swap0.fairRate();
        final double swap0NPV = swap0.NPV();

        // Step 2: ATM swap + Hull-White Jamshidian swaption.
        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal, fixedSchedule, atmRate, fixedDc,
                floatSchedule, idx, 0.0, dc);
        final Swaption swaption = new Swaption(swap, exercise);
        final HullWhite hw = new HullWhite(ts, hwA, hwSigma);
        swaption.setPricingEngine(new JamshidianSwaptionEngine(hw, ts));
        final double npv = swaption.NPV();

        // ---- Cross-validate ----
        final double expSwap0NPV = exp.getDouble("swap0_npv");
        final double expAtmRate = exp.getDouble("atm_rate");
        final double expSwaptionNPV = exp.getDouble("jamshidian_swaption_npv");

        if (!Tolerance.tight(swap0NPV, expSwap0NPV)) {
            fail("swap0.NPV(): exp=" + expSwap0NPV + " got=" + swap0NPV);
        }
        if (!Tolerance.tight(atmRate, expAtmRate)) {
            fail("atmRate: exp=" + expAtmRate + " got=" + atmRate);
        }
        // Loose tier (justification at class-level Javadoc): pre-existing
        // Java Brent.solveImpl() initialisation diverges from C++ brent.hpp
        // (root = xMax vs. f(guess) seed) — leaks ~5e-9 into r* and ~7e-11
        // into NPV.
        if (!Tolerance.loose(npv, expSwaptionNPV)) {
            fail("jamshidian.NPV(): exp=" + expSwaptionNPV + " got=" + npv);
        }
    }
}

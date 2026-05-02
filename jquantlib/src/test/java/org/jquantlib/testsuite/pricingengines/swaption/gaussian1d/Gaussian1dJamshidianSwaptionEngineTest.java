// jquantlib/src/test/java/org/jquantlib/testsuite/pricingengines/swaption/gaussian1d/Gaussian1dJamshidianSwaptionEngineTest.java
//
// Phase 2j WI-3.1 — Gaussian1dJamshidianSwaptionEngine cross-validation
// against migration-harness/references/pricingengines/swaption/gaussian1djamshidianswaptionengine.json
// (oracle: C++ QuantLib v1.42.1, gaussian1djamshidianswaptionengine_probe.cpp).
//
// Tolerance tier: TIGHT (rel 1e-12, abs 1e-14).
package org.jquantlib.testsuite.pricingengines.swaption.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dJamshidianSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.Compounding;
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
 * Phase 2j WI-3.1 cross-validation test for
 * {@link Gaussian1dJamshidianSwaptionEngine}.
 *
 * <p>Single {@code @Test} with collect-all-failures pattern.
 * Tolerance tier: {@link Tolerance#tight}.
 *
 * <p>Fixture:
 * <pre>
 *   eval = 2026-01-15
 *   yts  = FlatForward(5%, Continuous/Annual, Actual365Fixed)
 *   cal  = TARGET
 *   GSR  = volStepDates=[eval+2Y, eval+5Y], vols=[0.0070,0.0080,0.0085], rev=0.02
 *   index = Euribor3M
 * </pre>
 * Ten cases: payer/receiver x {5y5y ATM, 1y5y ATM, 2y10y ATM, 5y5y ITM, 5y5y OTM}.
 */
public class Gaussian1dJamshidianSwaptionEngineTest {

    private static final Date EVAL_DATE = new Date(15, Month.January, 2026);

    // ──────────────────────────────────────────────────────────────────────
    //  Fixture helpers
    // ──────────────────────────────────────────────────────────────────────

    private static Handle<YieldTermStructure> buildYts() {
        return new Handle<YieldTermStructure>(
                new FlatForward(EVAL_DATE, 0.05,
                        new Actual365Fixed(), Compounding.Continuous, Frequency.Annual));
    }

    private static Gsr buildGsr(final Handle<YieldTermStructure> yts) {
        final Calendar cal = new Target();
        final List<Date> volStepDates = new ArrayList<Date>();
        volStepDates.add(cal.advance(EVAL_DATE, new Period(2, TimeUnit.Years)));
        volStepDates.add(cal.advance(EVAL_DATE, new Period(5, TimeUnit.Years)));
        final double[] vols = new double[]{0.0070, 0.0080, 0.0085};
        return new Gsr(yts, volStepDates, vols, 0.02);
    }

    /**
     * Builds and prices a swaption matching the C++ probe fixture.
     * Returns the NPV.
     */
    private static double priceSwaption(
            final Handle<YieldTermStructure> yts,
            final Gaussian1dJamshidianSwaptionEngine engine,
            final int exerciseYears,
            final int swapYears,
            final VanillaSwap.Type swapType,
            final double rateDelta) {

        final Calendar cal = new Target();
        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);
        final DayCounter yieldDc = new Actual365Fixed();

        final Date exerciseDate = cal.advance(EVAL_DATE, new Period(exerciseYears, TimeUnit.Years));
        final Date startDate = cal.advance(exerciseDate, 2, TimeUnit.Days);
        final Date maturity = cal.advance(startDate, new Period(swapYears, TimeUnit.Years));

        // Fixed schedule: annual
        final Schedule fixedSchedule = new Schedule(
                startDate, maturity, new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        // Float schedule: 3M (Euribor3M tenor)
        final Schedule floatSchedule = new Schedule(
                startDate, maturity, new Period(3, TimeUnit.Months), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        final double nominal = 1.0;
        final Euribor3M idx = new Euribor3M(yts);

        // ATM dummy swap to find fair rate
        final VanillaSwap swap0 = new VanillaSwap(
                VanillaSwap.Type.Payer, nominal,
                fixedSchedule, 0.05, fixedDc,
                floatSchedule, idx, 0.0, yieldDc);
        swap0.setPricingEngine(new DiscountingSwapEngine(yts));
        final double atmRate = swap0.fairRate();

        final double fixedRate = atmRate + rateDelta;

        final VanillaSwap swap = new VanillaSwap(
                swapType, nominal,
                fixedSchedule, fixedRate, fixedDc,
                floatSchedule, idx, 0.0, yieldDc);

        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Swaption swaption = new Swaption(swap, exercise);
        swaption.setPricingEngine(engine);
        return swaption.NPV();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Test
    // ──────────────────────────────────────────────────────────────────────

    @Test
    public void npvMatchesCpp() {
        new Settings().setEvaluationDate(EVAL_DATE);
        final Handle<YieldTermStructure> yts = buildYts();
        final Gsr gsr = buildGsr(yts);
        final Gaussian1dJamshidianSwaptionEngine engine =
                new Gaussian1dJamshidianSwaptionEngine(gsr);

        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/gaussian1djamshidianswaptionengine");

        final StringBuilder failures = new StringBuilder();
        int total = 0;
        int passed = 0;

        // Case-name → (exerciseYears, swapYears, swapType, rateDelta)
        final Object[][] cases = {
            // name                    exYrs swYrs type                     delta
            {"atm_payer_5y5y",        5,    5,    VanillaSwap.Type.Payer,   0.00},
            {"atm_receiver_5y5y",     5,    5,    VanillaSwap.Type.Receiver, 0.00},
            {"atm_payer_1y5y",        1,    5,    VanillaSwap.Type.Payer,   0.00},
            {"atm_payer_2y10y",       2,    10,   VanillaSwap.Type.Payer,   0.00},
            {"itm_payer_5y5y",        5,    5,    VanillaSwap.Type.Payer,  -0.01},
            {"otm_payer_5y5y",        5,    5,    VanillaSwap.Type.Payer,   0.01},
            {"atm_receiver_1y5y",     1,    5,    VanillaSwap.Type.Receiver, 0.00},
            {"atm_receiver_2y10y",    2,    10,   VanillaSwap.Type.Receiver, 0.00},
            {"itm_receiver_5y5y",     5,    5,    VanillaSwap.Type.Receiver, 0.01},
            {"otm_receiver_5y5y",     5,    5,    VanillaSwap.Type.Receiver,-0.01},
        };

        for (final Object[] c : cases) {
            final String name    = (String) c[0];
            final int exYrs      = (Integer) c[1];
            final int swYrs      = (Integer) c[2];
            final VanillaSwap.Type type = (VanillaSwap.Type) c[3];
            final double delta   = (Double) c[4];

            total++;
            try {
                // Re-set eval date before each case (stateless fixture)
                new Settings().setEvaluationDate(EVAL_DATE);

                final Case ref = reader.getCase(name);
                final JSONObject exp = (JSONObject) ref.expectedRaw();
                final double cppNpv = exp.getDouble("npv");

                final double javaTime = priceSwaption(yts, engine, exYrs, swYrs, type, delta);

                if (!Tolerance.tight(javaTime, cppNpv)) {
                    failures.append(String.format(
                            "  [%s] npv: java=%.15e cpp=%.15e diff=%.3e%n",
                            name, javaTime, cppNpv, Math.abs(javaTime - cppNpv)));
                } else {
                    passed++;
                }
            } catch (final Exception e) {
                failures.append(String.format("  [%s] EXCEPTION: %s%n", name, e.getMessage()));
            }
        }

        if (failures.length() > 0) {
            fail("Gaussian1dJamshidianSwaptionEngine: " + (total - passed) + "/" + total
                    + " case(s) failed (TIGHT tier):\n" + failures);
        }
    }
}

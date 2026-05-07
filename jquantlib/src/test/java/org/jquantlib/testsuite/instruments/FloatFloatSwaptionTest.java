// jquantlib/src/test/java/org/jquantlib/testsuite/instruments/FloatFloatSwaptionTest.java
//
// Phase 2j.5 Track B.2 — FloatFloatSwaption structural cross-validation test.
//
// Mirrors the fixture in
//   migration-harness/cpp/probes/instruments/floatfloat_swaption_probe.cpp
// and validates all 14 reference cases.
//
// Structural fields (enums, integers, booleans, dates) are checked at EXACT
// tier — there is no floating-point arithmetic involved.
//
// Single @Test with collect-all-failures pattern.

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.instruments.FloatFloatSwap;
import org.jquantlib.instruments.FloatFloatSwaption;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.quotes.Handle;
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
 * Phase 2j.5 Track B.2 fingerprint test for {@link FloatFloatSwaption}.
 *
 * <p>Validates all structural properties:
 * {@code settlementType()}, {@code settlementMethod()}, {@code type()},
 * {@code underlyingSwap()} (non-null), {@code exercise()} date,
 * {@code isExpired()}, {@code leg1().size()}, {@code leg2().size()}.
 *
 * <p>Tier: EXACT (all fields are enum ordinals, booleans, or integers —
 * no floating-point arithmetic involved).
 */
public class FloatFloatSwaptionTest {

    private static final Date EVAL      = new Date(15, Month.January, 2026);
    private static final double FLAT_RATE = 0.04;

    @Test
    public void floatFloatSwaption_structuralMatchesCpp() {
        new Settings().setEvaluationDate(EVAL);

        // ── Fixture (mirrors floatfloat_swaption_probe.cpp) ───────────────────
        final DayCounter dc  = new Actual365Fixed();
        final Calendar   cal = new Target();

        final YieldTermStructure flat = new FlatForward(
                EVAL, FLAT_RATE, dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);
        final Euribor3M idx3m = new Euribor3M(ts);
        final Euribor6M idx6m = new Euribor6M(ts);

        final ReferenceReader reader = ReferenceReader.load("instruments/floatfloat_swaption");
        final List<String> failures  = new ArrayList<String>();

        // ── Case 1: receiver_physical_otc_1y5y ───────────────────────────────
        {
            final String name = "receiver_physical_otc_1y5y";
            try {
                Date start     = cal.advance(EVAL, new Period(1, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                FloatFloatSwap swap = makeSwap(cal, VanillaSwap.Type.Receiver,
                        swapStart, 5, 1e6, idx3m, idx6m, dc, 0.0, 0.0);
                Exercise ex = new EuropeanExercise(start);
                FloatFloatSwaption ffs = new FloatFloatSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
                checkCase(failures, reader, name, ffs);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 2: payer_physical_otc_2y3y ──────────────────────────────────
        {
            final String name = "payer_physical_otc_2y3y";
            try {
                Date start     = cal.advance(EVAL, new Period(2, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                FloatFloatSwap swap = makeSwap(cal, VanillaSwap.Type.Payer,
                        swapStart, 3, 5e5, idx3m, idx6m, dc, 0.0, 0.0);
                Exercise ex = new EuropeanExercise(start);
                FloatFloatSwaption ffs = new FloatFloatSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
                checkCase(failures, reader, name, ffs);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 3: receiver_cash_ccp_1y10y ──────────────────────────────────
        {
            final String name = "receiver_cash_ccp_1y10y";
            try {
                Date start     = cal.advance(EVAL, new Period(1, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                FloatFloatSwap swap = makeSwap(cal, VanillaSwap.Type.Receiver,
                        swapStart, 10, 1e6, idx3m, idx6m, dc, 0.0, 0.0);
                Exercise ex = new EuropeanExercise(start);
                FloatFloatSwaption ffs = new FloatFloatSwaption(swap, ex,
                        Settlement.Type.Cash, Settlement.Method.CollateralizedCashPrice);
                checkCase(failures, reader, name, ffs);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 4: payer_cash_paryieldcurve_3y7y ────────────────────────────
        {
            final String name = "payer_cash_paryieldcurve_3y7y";
            try {
                Date start     = cal.advance(EVAL, new Period(3, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                FloatFloatSwap swap = makeSwap(cal, VanillaSwap.Type.Payer,
                        swapStart, 7, 2e6, idx3m, idx6m, dc, 0.0005, 0.001);
                Exercise ex = new EuropeanExercise(start);
                FloatFloatSwaption ffs = new FloatFloatSwaption(swap, ex,
                        Settlement.Type.Cash, Settlement.Method.ParYieldCurve);
                checkCase(failures, reader, name, ffs);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 5: payer_physical_cleared_5y5y ──────────────────────────────
        {
            final String name = "payer_physical_cleared_5y5y";
            try {
                Date start     = cal.advance(EVAL, new Period(5, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                FloatFloatSwap swap = makeSwap(cal, VanillaSwap.Type.Payer,
                        swapStart, 5, 1e6, idx3m, idx6m, dc, 0.0, 0.0);
                Exercise ex = new EuropeanExercise(start);
                FloatFloatSwaption ffs = new FloatFloatSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalCleared);
                checkCase(failures, reader, name, ffs);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 6: is_expired_past_exercise ─────────────────────────────────
        {
            final String name = "is_expired_past_exercise";
            try {
                Date pastDate  = new Date(14, Month.January, 2025);
                Date swapStart = cal.advance(pastDate, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                FloatFloatSwap swap = makeSwap(cal, VanillaSwap.Type.Payer,
                        swapStart, 5, 1e6, idx3m, idx6m, dc, 0.0, 0.0);
                Exercise ex = new EuropeanExercise(pastDate);
                FloatFloatSwaption ffs = new FloatFloatSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);

                final Case ref = reader.getCase(name);
                final JSONObject exp = (JSONObject) ref.expectedRaw();
                checkBool(failures, name, "isExpired", ffs.isExpired(),
                          exp.getBoolean("isExpired"));
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Cases 7-14: grid ──────────────────────────────────────────────────
        {
            // {exerciseYears, swapYears, settlType_ord, settlMethod_ord}
            int[][] gridDef = {
                {1,  2, 0, 0},  // Physical/PhysicalOTC
                {1,  5, 1, 3},  // Cash/ParYieldCurve
                {2,  5, 0, 0},  // Physical/PhysicalOTC
                {2, 10, 1, 2},  // Cash/CollateralizedCashPrice
                {3,  5, 0, 1},  // Physical/PhysicalCleared
                {5,  5, 1, 3},  // Cash/ParYieldCurve
                {5, 10, 0, 0},  // Physical/PhysicalOTC
                {10, 5, 1, 2},  // Cash/CollateralizedCashPrice
            };
            Settlement.Type[]   TYPES   = Settlement.Type.values();
            Settlement.Method[] METHODS = Settlement.Method.values();

            for (int gi = 0; gi < gridDef.length; gi++) {
                final int exerciseYears = gridDef[gi][0];
                final int swapYears     = gridDef[gi][1];
                final Settlement.Type   st = TYPES  [gridDef[gi][2]];
                final Settlement.Method sm = METHODS[gridDef[gi][3]];
                final String name = "grid_" + gi + "_" + exerciseYears + "y" + swapYears + "y";
                try {
                    Date start = cal.advance(EVAL,
                            new Period(exerciseYears, TimeUnit.Years),
                            BusinessDayConvention.ModifiedFollowing, false);
                    Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                            BusinessDayConvention.ModifiedFollowing, false);
                    FloatFloatSwap swap = makeSwap(cal, VanillaSwap.Type.Receiver,
                            swapStart, swapYears, 1e6, idx3m, idx6m, dc, 0.0, 0.0);
                    Exercise ex = new EuropeanExercise(start);
                    FloatFloatSwaption ffs = new FloatFloatSwaption(swap, ex, st, sm);

                    final Case ref = reader.getCase(name);
                    final JSONObject exp = (JSONObject) ref.expectedRaw();

                    checkInt(failures, name, "settlementType",
                             ffs.settlementType().ordinal(),
                             exp.getInt("settlementType"));
                    checkInt(failures, name, "settlementMethod",
                             ffs.settlementMethod().ordinal(),
                             exp.getInt("settlementMethod"));
                    // swapType: probe emits 0=Receiver, 1=Payer; Java ordinal matches
                    checkInt(failures, name, "swapType",
                             ffs.type().ordinal(),
                             exp.getInt("swapType"));
                    checkBool(failures, name, "hasUnderlying",
                              ffs.underlyingSwap() != null,
                              exp.getBoolean("hasUnderlying"));
                    Date exDate = ffs.exercise().lastDate();
                    checkInt(failures, name, "exerciseYear",
                             exDate.year(),
                             exp.getInt("exerciseYear"));
                    checkInt(failures, name, "exerciseMonth",
                             exDate.month().value(),
                             exp.getInt("exerciseMonth"));
                    checkBool(failures, name, "isExpired",
                              ffs.isExpired(),
                              exp.getBoolean("isExpired"));
                    checkInt(failures, name, "nLeg1Coupons",
                             ffs.underlyingSwap().leg1().size(),
                             exp.getInt("nLeg1Coupons"));
                    checkInt(failures, name, "nLeg2Coupons",
                             ffs.underlyingSwap().leg2().size(),
                             exp.getInt("nLeg2Coupons"));
                } catch (final Exception e) {
                    failures.add("[" + name + "] unexpected exception: " + e.getMessage());
                }
            }
        }

        if (!failures.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(failures.size()).append(" failure(s):\n");
            for (final String f : failures) {
                sb.append("  ").append(f).append('\n');
            }
            fail(sb.toString());
        }
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Full checkCase: reads expected JSON and checks all fields for the
     * named-cases structure (cases 1-5 share exerciseDay too).
     */
    private void checkCase(final List<String> failures,
                           final ReferenceReader reader,
                           final String name,
                           final FloatFloatSwaption ffs) {
        final Case ref = reader.getCase(name);
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        checkInt(failures, name, "settlementType",
                 ffs.settlementType().ordinal(),
                 exp.getInt("settlementType"));
        checkInt(failures, name, "settlementMethod",
                 ffs.settlementMethod().ordinal(),
                 exp.getInt("settlementMethod"));
        // swapType: probe emits 0=Receiver, 1=Payer; Java VanillaSwap.Type ordinal
        // Receiver=0, Payer=1
        checkInt(failures, name, "swapType",
                 ffs.type().ordinal(),
                 exp.getInt("swapType"));
        checkBool(failures, name, "hasUnderlying",
                  ffs.underlyingSwap() != null,
                  exp.getBoolean("hasUnderlying"));
        final Date exDate = ffs.exercise().lastDate();
        checkInt(failures, name, "exerciseYear",
                 exDate.year(),
                 exp.getInt("exerciseYear"));
        checkInt(failures, name, "exerciseMonth",
                 exDate.month().value(),
                 exp.getInt("exerciseMonth"));
        checkInt(failures, name, "exerciseDay",
                 exDate.dayOfMonth(),
                 exp.getInt("exerciseDay"));
        checkBool(failures, name, "isExpired",
                  ffs.isExpired(),
                  exp.getBoolean("isExpired"));
        checkInt(failures, name, "nLeg1Coupons",
                 ffs.underlyingSwap().leg1().size(),
                 exp.getInt("nLeg1Coupons"));
        checkInt(failures, name, "nLeg2Coupons",
                 ffs.underlyingSwap().leg2().size(),
                 exp.getInt("nLeg2Coupons"));
    }

    /**
     * Build a simple FloatFloatSwap with two Ibor legs (3M vs 6M), uniform
     * notionals, and optional spreads.
     * Mirrors the {@code makeSwap()} helper in floatfloat_swaption_probe.cpp.
     */
    private static FloatFloatSwap makeSwap(
            final Calendar cal,
            final VanillaSwap.Type type,
            final Date swapStart,
            final int swapYears,
            final double nominal,
            final Euribor3M idx1,
            final Euribor6M idx2,
            final DayCounter dc,
            final double spread1,
            final double spread2) {

        final Schedule sch1 = makeSch(cal, swapStart, swapYears, 3, TimeUnit.Months);
        final Schedule sch2 = makeSch(cal, swapStart, swapYears, 6, TimeUnit.Months);
        final int n1 = sch1.size() - 1;
        final int n2 = sch2.size() - 1;

        return new FloatFloatSwap(
                type, nominal, nominal,
                sch1, idx1, dc,
                sch2, idx2, dc,
                false, false,
                /* gear1 */ 1.0,  spread1,
                FloatFloatSwap.NULL_REAL, FloatFloatSwap.NULL_REAL,
                /* gear2 */ 1.0,  spread2,
                FloatFloatSwap.NULL_REAL, FloatFloatSwap.NULL_REAL);
    }

    private static Schedule makeSch(final Calendar cal, final Date start,
                                     final int years,
                                     final int periodLen, final TimeUnit unit) {
        final Date end = cal.advance(start, new Period(years, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing, false);
        return new Schedule(start, end, new Period(periodLen, unit),
                cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward,
                false);
    }

    private static void checkInt(final List<String> failures, final String caseName,
                                  final String field, final long java, final long cpp) {
        if (java != cpp) {
            failures.add("[" + caseName + "." + field + "] java=" + java + " cpp=" + cpp);
        }
    }

    private static void checkBool(final List<String> failures, final String caseName,
                                   final String field, final boolean java, final boolean cpp) {
        if (java != cpp) {
            failures.add("[" + caseName + "." + field + "] java=" + java + " cpp=" + cpp);
        }
    }
}

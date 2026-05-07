// jquantlib/src/test/java/org/jquantlib/testsuite/instruments/NonstandardSwaptionTest.java
//
// Phase 2j.5 Track A.2 — NonstandardSwaption structural cross-validation test.
//
// Mirrors the fixture in
//   migration-harness/cpp/probes/instruments/nonstandard_swaption_probe.cpp
// and validates all 16 reference cases.
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
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.NonstandardSwap;
import org.jquantlib.instruments.NonstandardSwaption;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
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
 * Phase 2j.5 Track A.2 fingerprint test for {@link NonstandardSwaption}.
 *
 * <p>Validates all structural properties:
 * {@code settlementType()}, {@code settlementMethod()}, {@code type()},
 * {@code underlyingSwap()} (non-null), {@code exercise()} date,
 * {@code isExpired()}, {@code fixedLeg().size()}, {@code floatingLeg().size()}.
 *
 * <p>Tier: EXACT (all fields are enum ordinals, booleans, or integers —
 * no floating-point arithmetic involved).
 */
public class NonstandardSwaptionTest {

    private static final Date EVAL      = new Date(15, Month.January, 2026);
    private static final double FLAT_RATE = 0.04;

    @Test
    public void nonstandardSwaption_structuralMatchesCpp() {
        new Settings().setEvaluationDate(EVAL);

        // ── Fixture (mirrors nonstandard_swaption_probe.cpp) ──────────────────
        final DayCounter dc      = new Actual365Fixed();
        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);
        final Calendar   cal     = new Target();

        final YieldTermStructure flat = new FlatForward(
                EVAL, FLAT_RATE, dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);
        final Euribor3M idx = new Euribor3M(ts);

        final ReferenceReader reader = ReferenceReader.load("instruments/nonstandard_swaption");
        final List<String> failures  = new ArrayList<String>();

        // ── Case 1: receiver_physical_otc_1y5y ───────────────────────────────
        {
            final String name = "receiver_physical_otc_1y5y";
            try {
                Date start = cal.advance(EVAL, new Period(1, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                final int nFixed = 5, nFloat = 20;
                double[] fixedNom   = fill(nFixed, 1e6);
                double[] floatNom   = fill(nFloat, 1e6);
                double[] fixedRates = fill(nFixed, 0.035);
                NonstandardSwap swap = new NonstandardSwap(
                        VanillaSwap.Type.Receiver, fixedNom, floatNom,
                        makeSchedule(cal, swapStart, 5, 1, TimeUnit.Years),
                        fixedRates, fixedDc,
                        makeSchedule(cal, swapStart, 5, 3, TimeUnit.Months),
                        idx, 1.0, 0.0, dc, false, false);
                Exercise ex = new EuropeanExercise(start);
                NonstandardSwaption nsw = new NonstandardSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);

                checkCase(failures, reader, name, nsw);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 2: payer_physical_otc_2y3y ──────────────────────────────────
        {
            final String name = "payer_physical_otc_2y3y";
            try {
                Date start = cal.advance(EVAL, new Period(2, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                final int nFixed = 3, nFloat = 12;
                double[] fixedNom   = fill(nFixed, 5e5);
                double[] floatNom   = fill(nFloat, 5e5);
                double[] fixedRates = fill(nFixed, 0.04);
                NonstandardSwap swap = new NonstandardSwap(
                        VanillaSwap.Type.Payer, fixedNom, floatNom,
                        makeSchedule(cal, swapStart, 3, 1, TimeUnit.Years),
                        fixedRates, fixedDc,
                        makeSchedule(cal, swapStart, 3, 3, TimeUnit.Months),
                        idx, 1.0, 0.0, dc, false, false);
                Exercise ex = new EuropeanExercise(start);
                NonstandardSwaption nsw = new NonstandardSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);

                checkCase(failures, reader, name, nsw);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 3: receiver_cash_ccp_1y10y ──────────────────────────────────
        {
            final String name = "receiver_cash_ccp_1y10y";
            try {
                Date start = cal.advance(EVAL, new Period(1, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                final int nFixed = 10, nFloat = 40;
                double[] fixedNom   = fill(nFixed, 1e6);
                double[] floatNom   = fill(nFloat, 1e6);
                double[] fixedRates = fill(nFixed, 0.03);
                NonstandardSwap swap = new NonstandardSwap(
                        VanillaSwap.Type.Receiver, fixedNom, floatNom,
                        makeSchedule(cal, swapStart, 10, 1, TimeUnit.Years),
                        fixedRates, fixedDc,
                        makeSchedule(cal, swapStart, 10, 3, TimeUnit.Months),
                        idx, 1.0, 0.0, dc, false, false);
                Exercise ex = new EuropeanExercise(start);
                NonstandardSwaption nsw = new NonstandardSwaption(swap, ex,
                        Settlement.Type.Cash, Settlement.Method.CollateralizedCashPrice);

                checkCase(failures, reader, name, nsw);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 4: payer_cash_paryieldcurve_3y7y ────────────────────────────
        {
            final String name = "payer_cash_paryieldcurve_3y7y";
            try {
                Date start = cal.advance(EVAL, new Period(3, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                final int nFixed = 7, nFloat = 28;
                double[] fixedNom   = fill(nFixed, 2e6);
                double[] floatNom   = fill(nFloat, 2e6);
                double[] fixedRates = fill(nFixed, 0.045);
                NonstandardSwap swap = new NonstandardSwap(
                        VanillaSwap.Type.Payer, fixedNom, floatNom,
                        makeSchedule(cal, swapStart, 7, 1, TimeUnit.Years),
                        fixedRates, fixedDc,
                        makeSchedule(cal, swapStart, 7, 3, TimeUnit.Months),
                        idx, 1.0, 0.001, dc, false, false);
                Exercise ex = new EuropeanExercise(start);
                NonstandardSwaption nsw = new NonstandardSwaption(swap, ex,
                        Settlement.Type.Cash, Settlement.Method.ParYieldCurve);

                checkCase(failures, reader, name, nsw);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 5: payer_physical_cleared_5y5y ──────────────────────────────
        {
            final String name = "payer_physical_cleared_5y5y";
            try {
                Date start = cal.advance(EVAL, new Period(5, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                final int nFixed = 5, nFloat = 20;
                double[] fixedNom   = fill(nFixed, 1e6);
                double[] floatNom   = fill(nFloat, 1e6);
                double[] fixedRates = fill(nFixed, 0.05);
                NonstandardSwap swap = new NonstandardSwap(
                        VanillaSwap.Type.Payer, fixedNom, floatNom,
                        makeSchedule(cal, swapStart, 5, 1, TimeUnit.Years),
                        fixedRates, fixedDc,
                        makeSchedule(cal, swapStart, 5, 3, TimeUnit.Months),
                        idx, 1.0, 0.0, dc, false, false);
                Exercise ex = new EuropeanExercise(start);
                NonstandardSwaption nsw = new NonstandardSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalCleared);

                checkCase(failures, reader, name, nsw);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 6: receiver_amortizing_2y3y ─────────────────────────────────
        {
            final String name = "receiver_amortizing_2y3y";
            try {
                Date start = cal.advance(EVAL, new Period(2, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(start, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                double[] fixedNom   = {1e6, 7e5, 4e5};
                double[] floatNom   = {1e6,1e6,1e6, 7e5,7e5,7e5, 4e5,4e5,4e5, 3e5,3e5,3e5};
                double[] fixedRates = {0.03, 0.035, 0.04};
                NonstandardSwap swap = new NonstandardSwap(
                        VanillaSwap.Type.Receiver, fixedNom, floatNom,
                        makeSchedule(cal, swapStart, 3, 1, TimeUnit.Years),
                        fixedRates, fixedDc,
                        makeSchedule(cal, swapStart, 3, 3, TimeUnit.Months),
                        idx, 1.0, 0.0, dc, false, false);
                Exercise ex = new EuropeanExercise(start);
                NonstandardSwaption nsw = new NonstandardSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);

                checkCase(failures, reader, name, nsw);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 7: from_swaption_payer_1y5y ─────────────────────────────────
        {
            final String name = "from_swaption_payer_1y5y";
            try {
                Date exDate = cal.advance(EVAL, new Period(1, TimeUnit.Years),
                        BusinessDayConvention.ModifiedFollowing, false);
                Date swapStart = cal.advance(exDate, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                Schedule fixedSch = makeSchedule(cal, swapStart, 5, 1, TimeUnit.Years);
                Schedule floatSch = makeSchedule(cal, swapStart, 5, 3, TimeUnit.Months);
                VanillaSwap vanilla = new VanillaSwap(
                        VanillaSwap.Type.Payer, 1e6, fixedSch, 0.04, fixedDc,
                        floatSch, idx, 0.0, dc,
                        BusinessDayConvention.ModifiedFollowing);
                Exercise ex = new EuropeanExercise(exDate);
                Swaption swaption = new Swaption(vanilla, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);
                // Construct NonstandardSwaption from Swaption
                NonstandardSwaption nsw = new NonstandardSwaption(swaption);

                checkCase(failures, reader, name, nsw);
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Case 8: is_expired_past_exercise ─────────────────────────────────
        {
            final String name = "is_expired_past_exercise";
            try {
                // Exercise date 2025-01-14 is before EVAL=2026-01-15
                Date pastDate = new Date(14, Month.January, 2025);
                Date swapStart = cal.advance(pastDate, new Period(2, TimeUnit.Days),
                        BusinessDayConvention.ModifiedFollowing, false);
                final int nFixed = 5, nFloat = 20;
                double[] fixedNom   = fill(nFixed, 1e6);
                double[] floatNom   = fill(nFloat, 1e6);
                double[] fixedRates = fill(nFixed, 0.04);
                NonstandardSwap swap = new NonstandardSwap(
                        VanillaSwap.Type.Payer, fixedNom, floatNom,
                        makeSchedule(cal, swapStart, 5, 1, TimeUnit.Years),
                        fixedRates, fixedDc,
                        makeSchedule(cal, swapStart, 5, 3, TimeUnit.Months),
                        idx, 1.0, 0.0, dc, false, false);
                Exercise ex = new EuropeanExercise(pastDate);
                NonstandardSwaption nsw = new NonstandardSwaption(swap, ex,
                        Settlement.Type.Physical, Settlement.Method.PhysicalOTC);

                final Case ref = reader.getCase(name);
                final JSONObject exp = (JSONObject) ref.expectedRaw();
                checkBool(failures, name, "isExpired", nsw.isExpired(),
                          exp.getBoolean("isExpired"));
            } catch (final Exception e) {
                failures.add("[" + name + "] unexpected exception: " + e.getMessage());
            }
        }

        // ── Cases 9-16: grid ──────────────────────────────────────────────────
        {
            int[][] gridDef = {
                // {exerciseYears, swapYears, settlType_ord, settlMethod_ord}
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
                    Schedule fixedSch = makeSchedule(cal, swapStart, swapYears, 1, TimeUnit.Years);
                    Schedule floatSch = makeSchedule(cal, swapStart, swapYears, 3, TimeUnit.Months);
                    int nFixed = fixedSch.size() - 1;
                    int nFloat = floatSch.size() - 1;
                    double[] fixedNom   = fill(nFixed, 1e6);
                    double[] floatNom   = fill(nFloat, 1e6);
                    double[] fixedRates = fill(nFixed, 0.035);
                    NonstandardSwap swap = new NonstandardSwap(
                            VanillaSwap.Type.Receiver, fixedNom, floatNom,
                            fixedSch, fixedRates, fixedDc,
                            floatSch, idx, 1.0, 0.0, dc, false, false);
                    Exercise ex = new EuropeanExercise(start);
                    NonstandardSwaption nsw = new NonstandardSwaption(swap, ex, st, sm);

                    final Case ref = reader.getCase(name);
                    final JSONObject exp = (JSONObject) ref.expectedRaw();

                    // settlementType ordinal
                    checkInt(failures, name, "settlementType",
                             nsw.settlementType().ordinal(),
                             exp.getInt("settlementType"));
                    // settlementMethod ordinal
                    checkInt(failures, name, "settlementMethod",
                             nsw.settlementMethod().ordinal(),
                             exp.getInt("settlementMethod"));
                    // swapType (Receiver=-1, Payer=1)
                    checkInt(failures, name, "swapType",
                             nsw.type().toInteger(),
                             exp.getInt("swapType"));
                    // hasUnderlying
                    checkBool(failures, name, "hasUnderlying",
                              nsw.underlyingSwap() != null,
                              exp.getBoolean("hasUnderlying"));
                    // exerciseYear and exerciseMonth
                    Date exDate = nsw.exercise().lastDate();
                    checkInt(failures, name, "exerciseYear",
                             exDate.year(),
                             exp.getInt("exerciseYear"));
                    checkInt(failures, name, "exerciseMonth",
                             exDate.month().value(),
                             exp.getInt("exerciseMonth"));
                    // isExpired
                    checkBool(failures, name, "isExpired",
                              nsw.isExpired(),
                              exp.getBoolean("isExpired"));
                    // coupon counts
                    checkInt(failures, name, "nFixedCoupons",
                             nsw.underlyingSwap().fixedLeg().size(),
                             exp.getInt("nFixedCoupons"));
                    checkInt(failures, name, "nFloatCoupons",
                             nsw.underlyingSwap().floatingLeg().size(),
                             exp.getInt("nFloatCoupons"));
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
     * Full checkCase: reads expected JSON from reference file and checks all
     * fields for non-isExpired cases (cases 1-7 share this structure).
     */
    private void checkCase(final List<String> failures,
                           final ReferenceReader reader,
                           final String name,
                           final NonstandardSwaption nsw) {
        final Case ref = reader.getCase(name);
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        checkInt(failures, name, "settlementType",
                 nsw.settlementType().ordinal(),
                 exp.getInt("settlementType"));
        checkInt(failures, name, "settlementMethod",
                 nsw.settlementMethod().ordinal(),
                 exp.getInt("settlementMethod"));
        checkInt(failures, name, "swapType",
                 nsw.type().toInteger(),
                 exp.getInt("swapType"));
        checkBool(failures, name, "hasUnderlying",
                  nsw.underlyingSwap() != null,
                  exp.getBoolean("hasUnderlying"));
        // Exercise date fields
        final Date exDate = nsw.exercise().lastDate();
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
                  nsw.isExpired(),
                  exp.getBoolean("isExpired"));
        checkInt(failures, name, "nFixedCoupons",
                 nsw.underlyingSwap().fixedLeg().size(),
                 exp.getInt("nFixedCoupons"));
        checkInt(failures, name, "nFloatCoupons",
                 nsw.underlyingSwap().floatingLeg().size(),
                 exp.getInt("nFloatCoupons"));
    }

    private static Schedule makeSchedule(final Calendar cal, final Date start,
                                         final int years,
                                         final int periodLen, final TimeUnit periodUnit) {
        final Date end = cal.advance(start, new Period(years, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing, false);
        return new Schedule(start, end, new Period(periodLen, periodUnit),
                cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward,
                false);
    }

    private static double[] fill(final int n, final double v) {
        final double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
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

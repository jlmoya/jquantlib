/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.OvernightIndexedCoupon;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.InterpolatedDiscountCurve;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Cross-validation of the overnight-coupon machinery reworked in C++ QuantLib v1.43 against
 * {@code migration-harness/references/cashflows/v143_overnight_coupon.json}, produced by
 * {@code migration-harness/cpp/probes/cashflows/v143_overnight_coupon_probe.cpp}.
 * <p>
 * The v1.43 delta covered here:
 * <ul>
 *   <li>value dates built with {@code Calendar::businessDayList()} and anchored on the rate-computation dates
 *       rather than a daily {@code MakeSchedule} over the accrual dates;</li>
 *   <li>{@code interestDates} front/back pinned to the rate-computation dates, so a period end landing on a
 *       fixing holiday still accrues to that end;</li>
 *   <li>observation shift no longer rewriting {@code interestDates}, driving {@code dt} off the value dates
 *       instead (and only when a lookback is present);</li>
 *   <li>the new {@code exCouponDate} and {@code roundingPrecision} constructor arguments;</li>
 *   <li>the pricer's re-worked telescopic range (partial first period, lockout, partial last period), its new
 *       annualisation denominator, and its explicit forecast-curve range guard.</li>
 * </ul>
 * <p>
 * The probe seeds the SOFR fixing series from a closed form of the date serial number
 * ({@link #syntheticFixing(Date)}) so the reference does not depend on transcribing a market-data table. The
 * modulus makes the series non-constant, so an off-by-one in the fixing dates shows up as a rate mismatch
 * instead of cancelling out.
 * <p>
 * Tolerance: TIGHT ({@value #ABS_TOL} absolute / {@value #REL_TOL} relative).
 */
public class OvernightCouponV143Test {

    private static final String GROUP = "cashflows/v143_overnight_coupon";
    private static final ReferenceReader REF = ReferenceReader.load(GROUP);

    /** TIGHT tier — absolute tolerance near zero. */
    private static final double ABS_TOL = 1e-14;
    /** TIGHT tier — relative tolerance. */
    private static final double REL_TOL = 1e-12;

    /**
     * Documented exception to the TIGHT tier, for the two quantity families that annualise a growth factor
     * sitting very close to 1:
     * <pre>
     *     x = (G - 1) / tau,     G = D(t0) / D(t1)   from the forecast curve
     * </pre>
     * The discount factors come out of {@code exp()}, and C++ libm and the JVM each round that to the nearest
     * representable double independently, so {@code G} may disagree by a ULP. That disagreement is amplified by
     * {@code 1 / (G - 1)} in {@code x}:
     * <ul>
     *   <li>a single overnight forward fixing at ~4.33% has {@code G - 1 ~ 1.2e-4}, an amplification of ~8e3,
     *       giving ~1.9e-12 relative;</li>
     *   <li>the 10bp / 15-day accrual in the rounding case has {@code G - 1 ~ 4.2e-5}, an amplification of
     *       ~2.4e4, giving ~5.3e-12 relative.</li>
     * </ul>
     * Measured worst case across all 38 reference cases: 5.33e-12 relative (2.2e-12 absolute on a coupon of
     * nominal 10 000 — i.e. agreement to within one ULP of the nominal). This is a property of {@code exp}, not
     * of the port; only a correctly-rounded {@code exp} on both sides would remove it. The margin below is ~10x
     * the measured worst case, and still 1000x tighter than the LOOSE tier.
     * <p>
     * Applied <em>only</em> to {@code indexFixings} and to the accrued amounts of the low-rate rounding case.
     * {@code rate()}, {@code amount()}, {@code dt}, the leg NPV and every other accrued amount are checked at
     * the TIGHT tier and reproduce C++ v1.43 there.
     */
    private static final double REL_TOL_ANNUALISED = 5e-11;

    private static final double NOTIONAL = 10_000_000.0;

    // ------------------------------------------------------------------
    //  environment mirroring the probe
    // ------------------------------------------------------------------

    /**
     * Deterministic fixing series, identical to the probe's {@code syntheticFixing}. Closed form over the date
     * serial number so both ports regenerate exactly the same values.
     */
    private static double syntheticFixing(final Date d) {
        return 0.0400 + 0.00001 * (d.serialNumber() % 37);
    }

    private static final class Env {
        final RelinkableHandle<YieldTermStructure> curve = new RelinkableHandle<YieldTermStructure>();
        final Sofr sofr;

        Env(final Date today, final Double flatForwardRate, final Date fixingsFrom) {
            new Settings().setEvaluationDate(today);
            if (flatForwardRate != null) {
                curve.linkTo(Utilities.flatRate(today, flatForwardRate.doubleValue(), new Actual360()));
            }
            sofr = new Sofr(curve);
            // The global IndexManager series is keyed by index name, so wipe whatever an
            // earlier test left behind before seeding this case.
            sofr.clearFixings();
            final Calendar cal = sofr.fixingCalendar();
            for (Date d = fixingsFrom.clone(); d.le(today); d = d.add(1)) {
                if (cal.isBusinessDay(d)) {
                    sofr.addFixing(d.clone(), syntheticFixing(d));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  assertion helpers
    // ------------------------------------------------------------------

    private static void checkClose(final String what, final double expected, final double actual) {
        checkClose(what, expected, actual, REL_TOL);
    }

    private static void checkClose(final String what, final double expected, final double actual,
            final double relTol) {
        final double diff = Math.abs(actual - expected);
        if (diff <= ABS_TOL || diff <= relTol * Math.abs(expected)) {
            return;
        }
        fail("Failed to reproduce " + what
                + ":\n    expected:   " + expected
                + "\n    calculated: " + actual
                + "\n    error:      " + diff
                + "\n    tolerance:  " + relTol + " relative / " + ABS_TOL + " absolute");
    }

    private static void checkDates(final String what, final JSONArray expected, final List<Date> actual) {
        assertEquals(what + ": size", expected.length(), actual.size());
        for (int i = 0; i < expected.length(); ++i) {
            assertEquals(what + "[" + i + "]", expected.getLong(i), actual.get(i).serialNumber());
        }
    }

    private static void checkDoubles(final String what, final JSONArray expected, final double[] actual) {
        checkDoubles(what, expected, actual, REL_TOL);
    }

    private static void checkDoubles(final String what, final JSONArray expected, final double[] actual,
            final double relTol) {
        assertEquals(what + ": size", expected.length(), actual.length);
        for (int i = 0; i < expected.length(); ++i) {
            checkClose(what + "[" + i + "]", expected.getDouble(i), actual[i], relTol);
        }
    }

    /**
     * Compares a quantity the probe emitted as either a number or {@code null}. A {@code null} records that C++
     * threw, so the port must throw too — silently returning a value there would be a divergence.
     */
    private static void checkOptional(final String what, final JSONObject expected, final String key,
            final DoubleSupplier actual) {
        checkOptional(what, expected, key, actual, REL_TOL);
    }

    private static void checkOptional(final String what, final JSONObject expected, final String key,
            final DoubleSupplier actual, final double relTol) {
        if (expected.isNull(key)) {
            double value;
            try {
                value = actual.getAsDouble();
            } catch (final RuntimeException expectedThrow) {
                return;
            }
            fail(what + ": C++ v1.43 raised an error here but Java returned " + value);
        }
        checkClose(what, expected.getDouble(key), actual.getAsDouble(), relTol);
    }

    private static void checkOptionalList(final String what, final JSONObject expected, final String key,
            final Supplier<double[]> actual, final double relTol) {
        if (expected.isNull(key)) {
            try {
                actual.get();
            } catch (final RuntimeException expectedThrow) {
                return;
            }
            fail(what + ": C++ v1.43 raised an error here but Java returned a value");
        }
        checkDoubles(what, expected.getJSONArray(key), actual.get(), relTol);
    }

    private static double[] toArray(final List<Double> values) {
        final double[] out = new double[values.size()];
        for (int i = 0; i < out.length; ++i) {
            out[i] = values.get(i).doubleValue();
        }
        return out;
    }

    private static JSONObject expected(final String caseName) {
        return (JSONObject) REF.getCase(caseName).expectedRaw();
    }

    /** Full structural + numerical comparison of one coupon against its reference entry. */
    private static void checkCoupon(final String what, final JSONObject e, final OvernightIndexedCoupon c) {
        checkCoupon(what, e, c, REL_TOL);
    }

    /**
     * @param accrualRelTol relative tolerance for the accrued amounts. TIGHT everywhere except the low-rate
     *                      rounding case — see {@link #REL_TOL_ANNUALISED}.
     */
    private static void checkCoupon(final String what, final JSONObject e, final OvernightIndexedCoupon c,
            final double accrualRelTol) {
        assertEquals(what + ".paymentDate", e.getLong("paymentDateSerial"), c.date().serialNumber());
        assertEquals(what + ".accrualStartDate", e.getLong("accrualStartSerial"),
                c.accrualStartDate().serialNumber());
        assertEquals(what + ".accrualEndDate", e.getLong("accrualEndSerial"), c.accrualEndDate().serialNumber());
        assertEquals(what + ".fixingDays", e.getInt("fixingDaysResolved"), c.fixingDays());
        assertEquals(what + ".n", e.getInt("n"), c.n());
        checkClose(what + ".accrualPeriod", e.getDouble("accrualPeriod"), c.accrualPeriod());
        checkClose(what + ".nominal", e.getDouble("nominal"), c.nominal());

        // The date lists are the part that actually catches a wrong schedule: a rate or an
        // amount alone can match while two errors cancel.
        checkDates(what + ".valueDates", e.getJSONArray("valueDates"), c.valueDates());
        checkDates(what + ".interestDates", e.getJSONArray("interestDates"), c.interestDates());
        checkDates(what + ".fixingDates", e.getJSONArray("fixingDates"), c.fixingDates());
        checkDoubles(what + ".dt", e.getJSONArray("dt"), c.dt());
        // Each element is a single overnight forward fixing, the maximally amplified form of
        // (G - 1) / tau — see REL_TOL_ANNUALISED.
        checkOptionalList(what + ".indexFixings", e, "indexFixings", () -> toArray(c.indexFixings()),
                REL_TOL_ANNUALISED);

        checkOptional(what + ".rate", e, "rate", () -> c.rate());
        checkOptional(what + ".amount", e, "amount", () -> c.amount());

        final JSONArray accruals = e.getJSONArray("accruals");
        for (int i = 0; i < accruals.length(); ++i) {
            final JSONObject a = accruals.getJSONObject(i);
            final Date d = new Date(a.getLong("dateSerial"));
            checkOptional(what + ".accruedAmount(" + d + ")", a, "accrued", () -> c.accruedAmount(d),
                    accrualRelTol);
        }
    }

    private static void checkCoupon(final String caseName, final OvernightIndexedCoupon c) {
        checkCoupon(caseName, expected(caseName), c);
    }

    // ------------------------------------------------------------------
    //  coupon builders mirroring the probe
    // ------------------------------------------------------------------

    /** Mirror of the probe's {@code CouponSpec}. */
    private static final class Spec {
        final String name;
        final Date start;
        final Date end;
        final int lookbackDays;
        final boolean observationShift;
        final int lockoutDays;
        final boolean act365;
        final boolean telescopic;

        Spec(final String name, final Date start, final Date end, final int lookbackDays,
                final boolean observationShift, final int lockoutDays, final boolean act365,
                final boolean telescopic) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.lookbackDays = lookbackDays;
            this.observationShift = observationShift;
            this.lockoutDays = lockoutDays;
            this.act365 = act365;
            this.telescopic = telescopic;
        }
    }

    private static OvernightIndexedCoupon makeCoupon(final Sofr sofr, final Spec s) {
        final Calendar fedCal = new UnitedStates(UnitedStates.Market.FederalReserve);
        final Date payment = fedCal.advance(s.end, 2, TimeUnit.Days);
        final DayCounter dc = s.act365 ? new Actual365Fixed() : new Actual360();
        return new OvernightIndexedCoupon(payment, NOTIONAL, s.start, s.end, sofr, 1.0, 0.0,
                new Date(), new Date(), dc, s.telescopic, RateAveraging.Type.Compound,
                s.lookbackDays, s.lockoutDays, s.observationShift, /* compoundSpreadDaily */ false);
    }

    // ------------------------------------------------------------------
    //  Group A — schedules and accruals around fixing holidays
    // ------------------------------------------------------------------

    /**
     * Good Friday is the only day that is a holiday in the SOFR fixing calendar but a business day in the
     * Federal Reserve calendar used to roll SOFR swap coupons, so it is the natural probe for the v1.43
     * value-date rework.
     */
    @Test
    public void testCouponsAroundFixingHolidays() {
        QL.info("Testing v1.43 overnight-coupon schedules and accruals around fixing holidays...");

        final Date today = new Date(29, Month.May, 2025);
        final Env env = new Env(today, Double.valueOf(0.0433), new Date(1, Month.September, 2024));

        final Date oct18_2024 = new Date(18, Month.October, 2024);
        final Date apr18_2025 = new Date(18, Month.April, 2025);
        final Date may19_2025 = new Date(19, Month.May, 2025);
        final Date apr25_2025 = new Date(25, Month.April, 2025);
        final Date may27_2025 = new Date(27, Month.May, 2025);
        final Date apr14_2025 = new Date(14, Month.April, 2025);
        final Date may14_2025 = new Date(14, Month.May, 2025);
        final Date jun16_2025 = new Date(16, Month.June, 2025);
        final Date apr23_2025 = new Date(23, Month.April, 2025);
        final Date jun23_2025 = new Date(23, Month.June, 2025);

        final List<Spec> specs = Arrays.asList(
                // accrual end on Good Friday 2025-04-18
                new Spec("end_on_fixing_holiday", oct18_2024, apr18_2025, 0, false, 0, false, false),
                new Spec("end_on_fixing_holiday_telescopic", oct18_2024, apr18_2025, 0, false, 0, false, true),
                new Spec("end_on_fixing_holiday_obsshift", oct18_2024, apr18_2025, 0, true, 0, false, false),
                new Spec("end_on_fixing_holiday_lockout4", oct18_2024, apr18_2025, 0, false, 4, false, false),
                new Spec("end_on_fixing_holiday_lockout4_telescopic", oct18_2024, apr18_2025, 0, false, 4,
                        false, true),
                new Spec("end_on_fixing_holiday_lookback5", oct18_2024, apr18_2025, 5, false, 0, false, false),
                new Spec("end_on_fixing_holiday_lookback5_obsshift", oct18_2024, apr18_2025, 5, true, 0, false,
                        false),
                new Spec("end_on_fixing_holiday_lookback5_lockout4", oct18_2024, apr18_2025, 5, false, 4,
                        false, false),
                // accrual start on Good Friday 2025-04-18
                new Spec("start_on_fixing_holiday", apr18_2025, may19_2025, 0, false, 0, false, false),
                new Spec("start_on_fixing_holiday_act365_lookback5_obsshift", apr18_2025, may19_2025, 5, true,
                        0, true, false),
                new Spec("start_on_fixing_holiday_act365", apr18_2025, may19_2025, 0, false, 0, true, false),
                // lookback window landing on the fixing holiday
                new Spec("lookback5_over_fixing_holiday", apr25_2025, may27_2025, 5, false, 0, false, false),
                new Spec("lookback5_obsshift_over_fixing_holiday", apr25_2025, may27_2025, 5, true, 0, false,
                        false),
                // period spanning the fixing holiday
                new Spec("spans_fixing_holiday", apr14_2025, may14_2025, 0, false, 0, false, false),
                // partially fixed (period straddles the evaluation date)
                new Spec("partially_fixed", apr14_2025, jun16_2025, 0, false, 0, false, false),
                new Spec("partially_fixed_lockout1", apr14_2025, jun16_2025, 0, false, 1, false, false),
                new Spec("partially_fixed_lockout5", apr14_2025, jun16_2025, 0, false, 5, false, false),
                new Spec("partially_fixed_lookback5", apr23_2025, jun23_2025, 5, false, 0, false, false),
                new Spec("partially_fixed_lookback5_obsshift", apr23_2025, jun23_2025, 5, true, 0, false,
                        false),
                new Spec("partially_fixed_lookback5_obsshift_lockout4", apr23_2025, jun23_2025, 5, true, 4,
                        false, false),
                // fully forward, period starting / ending on Good Friday 2027-03-26
                new Spec("forward_starts_on_holiday", new Date(26, Month.March, 2027),
                        new Date(28, Month.June, 2027), 0, false, 0, false, false),
                new Spec("forward_starts_on_holiday_telescopic", new Date(26, Month.March, 2027),
                        new Date(28, Month.June, 2027), 0, false, 0, false, true),
                new Spec("forward_ends_on_holiday_lockout1", new Date(26, Month.February, 2027),
                        new Date(26, Month.March, 2027), 0, false, 1, false, false));

        for (final Spec s : specs) {
            // Guard against silently drifting out of step with the reference's inputs.
            final JSONObject in = REF.getCase(s.name).inputs();
            assertEquals(s.name + ": input startSerial", in.getLong("startSerial"), s.start.serialNumber());
            assertEquals(s.name + ": input endSerial", in.getLong("endSerial"), s.end.serialNumber());
            assertEquals(s.name + ": input todaySerial", in.getLong("todaySerial"), today.serialNumber());

            checkCoupon(s.name, makeCoupon(env.sofr, s));
        }
    }

    // ------------------------------------------------------------------
    //  Group B — coupon split at a fixing holiday
    // ------------------------------------------------------------------

    /**
     * A coupon split at a fixing holiday must compound to the same growth factor as the undivided coupon
     * (upstream {@code testInterestCalculatedAccrualDateFixingHoliday}). Pinning all three rates is a stronger
     * statement than pinning the identity they satisfy, so the reference carries the coupons themselves.
     */
    @Test
    public void testCouponSplitAtFixingHoliday() {
        QL.info("Testing v1.43 overnight coupons split at a fixing holiday...");

        final Date today = new Date(19, Month.April, 2023);
        final Env env = new Env(today, Double.valueOf(0.0432), new Date(1, Month.September, 2021));
        final Calendar fixingCal = env.sofr.fixingCalendar();

        final Date[][] cases = {
                { new Date(15, Month.October, 2021), new Date(17, Month.April, 2023),
                        new Date(15, Month.April, 2022) },
                { new Date(18, Month.October, 2024), new Date(20, Month.April, 2026),
                        new Date(18, Month.April, 2025) },
        };
        final String[] labels = { "split_fixed", "split_forward" };

        for (int k = 0; k < cases.length; ++k) {
            final Date start = cases[k][0];
            final Date end = cases[k][1];
            final Date splitAt = cases[k][2];
            final Date payEnd = fixingCal.advance(end, 2, TimeUnit.Days);
            final Date paySplit = fixingCal.advance(splitAt, 2, TimeUnit.Days);

            assertEquals(labels[k] + ": split date must be a fixing holiday", true,
                    REF.getCase(labels[k] + "_total").inputs().getBoolean("splitAtIsFixingHoliday"));

            checkCoupon(labels[k] + "_total",
                    new OvernightIndexedCoupon(payEnd, NOTIONAL, start, end, env.sofr));
            checkCoupon(labels[k] + "_left",
                    new OvernightIndexedCoupon(paySplit, NOTIONAL, start, splitAt, env.sofr));
            checkCoupon(labels[k] + "_right",
                    new OvernightIndexedCoupon(payEnd, NOTIONAL, splitAt, end, env.sofr));
        }
    }

    // ------------------------------------------------------------------
    //  Group C — amount rounding (new v1.43 roundingPrecision argument)
    // ------------------------------------------------------------------

    @Test
    public void testAmountRounding() {
        QL.info("Testing v1.43 overnight-coupon amount with a rounded rate...");

        final Date today = new Date(23, Month.November, 2021);
        final Env env = new Env(today, Double.valueOf(0.0010), new Date(1, Month.September, 2021));

        final Date start = new Date(10, Month.December, 2021);
        final Date end = new Date(10, Month.January, 2022);
        final double notional = 10_000.0;

        final OvernightIndexedCoupon unrounded =
                new OvernightIndexedCoupon(end, notional, start, end, env.sofr);
        final OvernightIndexedCoupon rounded = new OvernightIndexedCoupon(end, notional, start, end, env.sofr,
                1.0, 0.0, new Date(), new Date(), new DayCounter(), /* telescopic */ false,
                RateAveraging.Type.Compound, Constants.NULL_NATURAL, 0, /* obsShift */ false,
                /* compoundSpreadDaily */ false, new Date(), new Date(), new Date(), Integer.valueOf(5));

        // Accrued amounts here carry the largest exp()-ULP amplification in the whole reference:
        // a 10bp curve over 15 days gives a growth factor only 4.2e-5 above 1. rate() and
        // amount(), which annualise over the full 31-day period, still hold at TIGHT.
        checkCoupon("amount_rounding_unrounded", expected("amount_rounding_unrounded"), unrounded,
                REL_TOL_ANNUALISED);

        final JSONObject e = expected("amount_rounding_precision5");
        checkCoupon("amount_rounding_precision5", e, rounded, REL_TOL_ANNUALISED);
        checkClose("amount_rounding.unroundedRate", e.getDouble("unroundedRate"), unrounded.rate());
        checkClose("amount_rounding.unroundedAmount", e.getDouble("unroundedAmount"), unrounded.amount());
        checkClose("amount_rounding.roundedRate", e.getDouble("roundedRate"),
                new org.jquantlib.math.Rounding.ClosestRounding(5).operator(unrounded.rate()));
        assertEquals("amount_rounding: roundingPrecision round-trip", Integer.valueOf(5),
                rounded.roundingPrecision());
        // The rounding must actually bite, otherwise the case proves nothing.
        if (rounded.amount() == unrounded.amount()) {
            fail("amount_rounding: rounded and unrounded amounts are identical");
        }
    }

    // ------------------------------------------------------------------
    //  Group D — forecast-curve range guard (new in v1.43)
    // ------------------------------------------------------------------

    @Test
    public void testForecastCurveRangeGuard() {
        QL.info("Testing v1.43 error when the forecast curve cannot value the coupon...");

        final Date today = new Date(26, Month.March, 2026);
        final Env env = new Env(today, null, new Date(1, Month.January, 2026));

        final Date start = new Date(31, Month.March, 2026);
        final Date end = new Date(31, Month.March, 2027);
        final OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(new Date(2, Month.April, 2027), 1.0,
                start, end, env.sofr, 1.0, 0.0, new Date(), new Date(), new DayCounter(),
                /* telescopic */ true, RateAveraging.Type.Compound, Constants.NULL_NATURAL, 0, false, false);

        final JSONObject e = expected("curve_range_guard");
        checkDates("curve_range_guard.valueDates", e.getJSONArray("valueDates"), coupon.valueDates());
        checkDates("curve_range_guard.interestDates", e.getJSONArray("interestDates"),
                coupon.interestDates());
        checkDates("curve_range_guard.fixingDates", e.getJSONArray("fixingDates"), coupon.fixingDates());

        // (1) no curve at all
        checkOptional("curve_range_guard.rateWithNoCurve", e, "rateWithNoCurve", () -> coupon.rate());

        // (2) curve ending one day before the accrual end — must throw
        env.curve.linkTo(discountCurveTo(today, end.sub(1), false));
        checkOptional("curve_range_guard.rateWithNarrowCurve", e, "rateWithNarrowCurve",
                () -> coupon.rate());

        // (3) curve reaching exactly the accrual end
        env.curve.linkTo(discountCurveTo(today, end, false));
        checkOptional("curve_range_guard.rateWithExactCurve", e, "rateWithExactCurve", () -> coupon.rate());

        // (4) narrow curve, but extrapolating
        env.curve.linkTo(discountCurveTo(today, end.sub(1), true));
        checkOptional("curve_range_guard.rateWithNarrowExtrapolatingCurve", e,
                "rateWithNarrowExtrapolatingCurve", () -> coupon.rate());
    }

    private static YieldTermStructure discountCurveTo(final Date today, final Date maturity,
            final boolean extrapolate) {
        final InterpolatedDiscountCurve<LogLinear> curve = new InterpolatedDiscountCurve<LogLinear>(
                LogLinear.class, new Date[] { today, maturity }, new double[] { 1.0, 0.9 }, new Actual360());
        if (extrapolate) {
            curve.enableExtrapolation();
        }
        return curve;
    }

    // ------------------------------------------------------------------
    //  Group E — ex-coupon accrued amounts (new v1.43 exCouponDate argument)
    // ------------------------------------------------------------------

    @Test
    public void testExCouponAccruedAmounts() {
        QL.info("Testing v1.43 ex-coupon accrued amounts around the accrual end date...");

        final Date today = new Date(26, Month.March, 2026);
        final Env env = new Env(today, Double.valueOf(0.04), new Date(1, Month.January, 2026));

        final Date start = new Date(31, Month.March, 2026);
        final Date end = new Date(31, Month.March, 2027);
        final Date pay = new Date(2, Month.April, 2027);

        final String[] names = { "excoupon_before_accrual_end", "excoupon_after_accrual_end" };
        final Date[] exDates = { new Date(27, Month.March, 2027), new Date(1, Month.April, 2027) };

        for (int k = 0; k < names.length; ++k) {
            final OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(pay, 100.0, start, end, env.sofr,
                    1.0, 0.0, new Date(), new Date(), new DayCounter(), /* telescopic */ true,
                    RateAveraging.Type.Compound, Constants.NULL_NATURAL, 0, false, false, new Date(),
                    new Date(), exDates[k], null);

            final JSONObject e = expected(names[k]);
            checkCoupon(names[k], e, coupon);
            assertEquals(names[k] + ".exCouponDate", e.getLong("exCouponDateSerial"),
                    coupon.exCouponDate().serialNumber());

            final JSONArray probes = e.getJSONArray("exCouponProbes");
            for (int i = 0; i < probes.length(); ++i) {
                final JSONObject p = probes.getJSONObject(i);
                final Date d = new Date(p.getLong("dateSerial"));
                assertEquals(names[k] + ".tradingExCoupon(" + d + ")", p.getBoolean("tradingExCoupon"),
                        coupon.tradingExCoupon(d));
                checkClose(names[k] + ".accruedPeriod(" + d + ")", p.getDouble("accruedPeriod"),
                        coupon.accruedPeriod(d));
                checkOptional(names[k] + ".accruedAmount(" + d + ")", p, "accrued",
                        () -> coupon.accruedAmount(d));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Group F — in-advance compounding
    // ------------------------------------------------------------------

    /**
     * With the rate-computation dates decoupled from the accrual dates, the in-advance coupon must reproduce
     * the schedule and rate of the in-arrears coupon written on the computation period.
     */
    @Test
    public void testAccruedAmountInAdvance() {
        QL.info("Testing v1.43 rate and accrued amount for in-advance compounding...");

        final Date today = new Date(21, Month.April, 2026);
        final Env env = new Env(today, Double.valueOf(0.04), new Date(1, Month.January, 2026));

        final Date accrualStart = new Date(23, Month.April, 2027);
        final Date accrualEnd = new Date(23, Month.April, 2028);
        final Date rateStart = new Date(23, Month.April, 2026);
        final Date rateEnd = new Date(23, Month.April, 2027);

        final OvernightIndexedCoupon advance = new OvernightIndexedCoupon(new Date(25, Month.April, 2028),
                NOTIONAL, accrualStart, accrualEnd, env.sofr, 1.0, 0.0, new Date(), new Date(),
                new DayCounter(), /* telescopic */ true, RateAveraging.Type.Compound,
                Constants.NULL_NATURAL, 0, false, false, rateStart, rateEnd);
        final OvernightIndexedCoupon arrears = new OvernightIndexedCoupon(rateEnd, NOTIONAL, rateStart,
                rateEnd, env.sofr, 1.0, 0.0, new Date(), new Date(), new DayCounter(),
                /* telescopic */ true, RateAveraging.Type.Compound, Constants.NULL_NATURAL, 0, false, false);

        final JSONObject e = expected("in_advance");
        checkCoupon("in_advance", e, advance);
        assertEquals("in_advance.rateComputationStartDate", e.getLong("rateComputationStartSerial"),
                advance.rateComputationStartDate().serialNumber());
        assertEquals("in_advance.rateComputationEndDate", e.getLong("rateComputationEndSerial"),
                advance.rateComputationEndDate().serialNumber());

        checkCoupon("in_arrears_reference", arrears);

        // The two coupons must agree on the schedule and the rate: that is the whole point
        // of the rate-computation dates.
        assertEquals("in_advance vs in_arrears: value-date count", arrears.valueDates().size(),
                advance.valueDates().size());
        for (int i = 0; i < arrears.valueDates().size(); ++i) {
            assertEquals("in_advance vs in_arrears: valueDates[" + i + "]",
                    arrears.valueDates().get(i).serialNumber(), advance.valueDates().get(i).serialNumber());
            assertEquals("in_advance vs in_arrears: interestDates[" + i + "]",
                    arrears.interestDates().get(i).serialNumber(),
                    advance.interestDates().get(i).serialNumber());
        }
        checkClose("in_advance vs in_arrears: rate", arrears.rate(), advance.rate());
    }

    // ------------------------------------------------------------------
    //  Group G — rate-computation start date on a fixing holiday
    // ------------------------------------------------------------------

    @Test
    public void testRateComputationStartOnFixingHoliday() {
        QL.info("Testing v1.43 coupon whose rate-computation start date is a fixing holiday...");

        final Date today = new Date(16, Month.April, 2025);
        final Env env = new Env(today, Double.valueOf(0.04), new Date(1, Month.January, 2025));

        final Date accrualStart = new Date(21, Month.April, 2025);
        final Date accrualEnd = new Date(21, Month.April, 2026);
        final Date rateStart = new Date(18, Month.April, 2025);
        final Date rateEnd = new Date(20, Month.April, 2026);

        final OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(new Date(23, Month.April, 2026),
                NOTIONAL, accrualStart, accrualEnd, env.sofr, 1.0, 0.0, new Date(), new Date(),
                new DayCounter(), /* telescopic */ true, RateAveraging.Type.Compound,
                Constants.NULL_NATURAL, 0, false, false, rateStart, rateEnd);

        final String name = "rate_computation_start_fixing_holiday";
        final JSONObject e = expected(name);
        assertEquals(name + ": rate-computation start is a fixing holiday",
                e.getBoolean("rateComputationStartIsFixingHoliday"),
                env.sofr.fixingCalendar().isHoliday(coupon.rateComputationStartDate()));
        checkClose(name + ".fixing(2025-04-17)", e.getDouble("fixingOn2025_04_17"),
                env.sofr.fixing(new Date(17, Month.April, 2025)));
        checkClose(name + ".fixing(2025-04-21)", e.getDouble("fixingOn2025_04_21"),
                env.sofr.fixing(new Date(21, Month.April, 2025)));
        checkCoupon(name, e, coupon);
    }

    // ------------------------------------------------------------------
    //  Group H — OvernightLeg with a weekend stub
    // ------------------------------------------------------------------

    /**
     * The middle schedule date is a Saturday, so the first coupon covers a single business day and the second
     * spans the weekend. The schedule carries no calendar, which exercises the leg builder's fallback to
     * {@code WeekendsOnly} when adjusting payment dates.
     */
    @Test
    public void testOvernightLegWeekendStub() {
        QL.info("Testing v1.43 overnight leg with a weekend stub period...");

        final Date today = new Date(16, Month.April, 2025);
        final Env env = new Env(today, Double.valueOf(0.04), new Date(1, Month.January, 2025));

        final List<Date> dates = new ArrayList<Date>(Arrays.asList(
                new Date(27, Month.March, 2026),  // Friday
                new Date(28, Month.March, 2026),  // Saturday
                new Date(30, Month.March, 2026))); // Monday
        final Leg leg = new OvernightLeg(new Schedule(dates), env.sofr).withNotionals(NOTIONAL).leg();

        final YieldTermStructure discount = Utilities.flatRate(today, 0.0015, new Actual360());

        final String name = "overnight_leg_weekend_stub";
        final JSONObject e = expected(name);
        assertEquals(name + ".legSize", e.getInt("legSize"), leg.size());

        final JSONArray coupons = e.getJSONArray("coupons");
        assertEquals(name + ".coupons size", coupons.length(), leg.size());
        double npv = 0.0;
        for (int i = 0; i < leg.size(); ++i) {
            final CashFlow cf = leg.get(i);
            if (!(cf instanceof OvernightIndexedCoupon)) {
                fail(name + ": leg[" + i + "] is not an OvernightIndexedCoupon: " + cf);
            }
            checkCoupon(name + ".coupons[" + i + "]", coupons.getJSONObject(i),
                    (OvernightIndexedCoupon) cf);
            npv += cf.amount() * discount.discount(cf.date());
        }
        checkClose(name + ".legNpv", e.getDouble("legNpv"), npv);
        checkClose(name + ".oneDayInterest", e.getDouble("oneDayInterest"),
                env.sofr.fixing(new Date(27, Month.March, 2026)) / 360.0 * NOTIONAL);

        final JSONArray accruals = e.getJSONArray("legAccruedAmounts");
        for (int i = 0; i < accruals.length(); ++i) {
            final JSONObject a = accruals.getJSONObject(i);
            final Date d = new Date(a.getLong("dateSerial"));
            checkOptional(name + ".legAccruedAmount(" + d + ")", a, "accrued",
                    () -> CashFlows.accruedAmount(leg, true, d));
        }
    }

    // ------------------------------------------------------------------
    //  Reference-file sanity
    // ------------------------------------------------------------------

    @Test
    public void testReferenceIsFromV143() {
        assertEquals("reference was generated against the wrong C++ release", "1.43", REF.cppVersion());
        assertEquals("reference test-group mismatch", GROUP, REF.testGroup());
    }
}

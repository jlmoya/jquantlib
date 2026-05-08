/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for CappedFlooredYoYInflationCoupon against
 QuantLib v1.42.1 via
 migration-harness/references/cashflows/capped_floored_yoy_inflation_coupon.json
 (Phase 2q L1 Track D.1).
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CappedFlooredYoYInflationCoupon;
import org.jquantlib.cashflow.YoYInflationCoupon;
import org.jquantlib.cashflow.YoYInflationCouponPricer;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link CappedFlooredYoYInflationCoupon}.
 *
 * <p>Reproduces the C++ probe setup
 * (migration-harness/cpp/probes/cashflows/capped_floored_yoy_inflation_coupon_probe.cpp):
 * a YYUKRPI YoY index seeded with monthly fixings 2005-01..2007-07 (constant
 * 2.5%), bound to a 6-pillar Linear-interpolated YoY curve.
 *
 * <h3>Scenario groups</h3>
 * <ul>
 *   <li><b>PASS_*</b> — pass-through (no cap, no floor): rate equals the
 *       underlying YoY coupon rate. Drives {@link YoYInflationCoupon#rate()}
 *       through the wrapper, plus pass-through accessors. Tier: TIGHT.</li>
 *   <li><b>META_*</b> — capped/floored configurations: probes metadata
 *       accessors only ({@code cap()}, {@code floor()}, {@code effectiveCap()},
 *       {@code effectiveFloor()}, {@code isCapped()}, {@code isFloored()}).
 *       Cap/floor rate evaluation requires a YoY optionlet pricer (deferred to
 *       Phase 2r). Tier: TIGHT for arithmetic-only effective-cap/floor.</li>
 * </ul>
 */
public class CappedFlooredYoYInflationCouponTest {

    private static final String REF_GROUP = "cashflows/capped_floored_yoy_inflation_coupon";

    @Test
    public void cappedFlooredYoYInflationCoupon_matchesCpp() {
        // ---------- Match probe setup exactly ----------
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag = new Period(3, TimeUnit.Months);

        final Date refDate = cal.adjust(evalDate, bdc);
        final Date[] nodeDates = new Date[]{
                new Date(1,  Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = new double[]{
                0.025, 0.027, 0.029, 0.031, 0.034, 0.036
        };

        final InterpolatedYoYInflationCurve<Linear> yoyCurve =
                new InterpolatedYoYInflationCurve<>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        yoyCurve.enableExtrapolation();

        final Handle<YoYInflationTermStructure> ts =
                new Handle<YoYInflationTermStructure>(yoyCurve);
        // Probe uses a generic YoYInflationIndex named "YY_UKRPI" with UKRegion;
        // YYUKRPI is the natural Java analogue. Both share the same family
        // name and obs lag.
        final YYUKRPI yyIndex = new YYUKRPI(freq, false, false, ts);

        // Seed monthly YoY fixings 2005-01..2007-07 (constant 2.5%).
        final Date[] fixDates = new Date[]{
                new Date(1, Month.January,   2005), new Date(1, Month.February,  2005),
                new Date(1, Month.March,     2005), new Date(1, Month.April,     2005),
                new Date(1, Month.May,       2005), new Date(1, Month.June,      2005),
                new Date(1, Month.July,      2005), new Date(1, Month.August,    2005),
                new Date(1, Month.September, 2005), new Date(1, Month.October,   2005),
                new Date(1, Month.November,  2005), new Date(1, Month.December,  2005),
                new Date(1, Month.January,   2006), new Date(1, Month.February,  2006),
                new Date(1, Month.March,     2006), new Date(1, Month.April,     2006),
                new Date(1, Month.May,       2006), new Date(1, Month.June,      2006),
                new Date(1, Month.July,      2006), new Date(1, Month.August,    2006),
                new Date(1, Month.September, 2006), new Date(1, Month.October,   2006),
                new Date(1, Month.November,  2006), new Date(1, Month.December,  2006),
                new Date(1, Month.January,   2007), new Date(1, Month.February,  2007),
                new Date(1, Month.March,     2007), new Date(1, Month.April,     2007),
                new Date(1, Month.May,       2007), new Date(1, Month.June,      2007),
                new Date(1, Month.July,      2007),
        };
        for (final Date d : fixDates) {
            yyIndex.addFixing(d, 0.025, true);
        }

        final YoYInflationCouponPricer pricer = new YoYInflationCouponPricer();

        // ---------- Cross-validate every case ----------
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                if (name.startsWith("PASS_")) {
                    checkPassThrough(name, c, yyIndex, swapObsLag, dc, pricer, mismatches);
                } else if (name.startsWith("META_")) {
                    checkMeta(name, c, yyIndex, swapObsLag, dc, mismatches);
                } else {
                    mismatches.add(name + ": UNRECOGNIZED case");
                }
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkPassThrough(final String name, final Case c,
                                         final YYUKRPI yyIndex,
                                         final Period swapObsLag,
                                         final DayCounter dc,
                                         final YoYInflationCouponPricer pricer,
                                         final List<String> mismatches) {
        final JSONObject inputs = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final double notional = inputs.getDouble("notional");
        final long startSerial = inputs.getLong("startDate_serial");
        final long endSerial = inputs.getLong("endDate_serial");
        final long paymentSerial = inputs.getLong("paymentDate_serial");
        final String interpStr = inputs.getString("observationInterpolation");
        final double gearing = inputs.getDouble("gearing");
        final double spread = inputs.getDouble("spread");

        final Date startDate = new Date(startSerial);
        final Date endDate = new Date(endSerial);
        final Date paymentDate = new Date(paymentSerial);
        final CPI.InterpolationType interp = CPI.InterpolationType.valueOf(interpStr);

        // Build the underlying YoYInflationCoupon — same construction as the
        // probe's underlying.
        final YoYInflationCoupon underlying = new YoYInflationCoupon(
                notional, paymentDate, startDate, endDate,
                /* fixingDays */ 0,
                yyIndex, swapObsLag, interp, dc,
                gearing, spread);
        underlying.setPricer(pricer);

        // Pure pass-through wrapper: no cap, no floor.
        final CappedFlooredYoYInflationCoupon cf = new CappedFlooredYoYInflationCoupon(
                underlying, Constants.NULL_REAL, Constants.NULL_REAL);
        cf.setPricer(pricer);

        // date_serial — exact (calendar)
        checkExact(name, "date_serial", expected, mismatches,
                cf.date().serialNumber());
        // isCapped / isFloored — boolean exact
        checkBoolean(name, "isCapped", expected, mismatches, cf.isCapped());
        checkBoolean(name, "isFloored", expected, mismatches, cf.isFloored());
        // gearing / spread — TIGHT (just the parameters echoed back)
        checkTight(name, "gearing", expected, mismatches, cf.gearing());
        checkTight(name, "spread", expected, mismatches, cf.spread());
        // underlyingRate — TIGHT (single curve interpolation)
        checkTight(name, "underlyingRate", expected, mismatches, cf.underlyingRate());
        // rate — TIGHT (pass-through equals underlying)
        checkTight(name, "rate", expected, mismatches, cf.rate());
        // amount — TIGHT (rate * accrualPeriod * notional)
        checkTight(name, "amount", expected, mismatches, cf.amount());
    }

    private static void checkMeta(final String name, final Case c,
                                  final YYUKRPI yyIndex,
                                  final Period swapObsLag,
                                  final DayCounter dc,
                                  final List<String> mismatches) {
        final JSONObject inputs = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final double notional = inputs.getDouble("notional");
        final long startSerial = inputs.getLong("startDate_serial");
        final long endSerial = inputs.getLong("endDate_serial");
        final long paymentSerial = inputs.getLong("paymentDate_serial");
        final double gearing = inputs.getDouble("gearing");
        final double spread = inputs.getDouble("spread");
        final String capStr = inputs.getString("cap");
        final String floorStr = inputs.getString("floor");

        final double cap = "null".equals(capStr) ? Constants.NULL_REAL : Double.parseDouble(capStr);
        final double floor = "null".equals(floorStr) ? Constants.NULL_REAL : Double.parseDouble(floorStr);

        final Date startDate = new Date(startSerial);
        final Date endDate = new Date(endSerial);
        final Date paymentDate = new Date(paymentSerial);

        final YoYInflationCoupon underlying = new YoYInflationCoupon(
                notional, paymentDate, startDate, endDate,
                /* fixingDays */ 0,
                yyIndex, swapObsLag, CPI.InterpolationType.AsIndex, dc,
                gearing, spread);

        final CappedFlooredYoYInflationCoupon cf = new CappedFlooredYoYInflationCoupon(
                underlying, cap, floor);

        // date_serial — exact
        checkExact(name, "date_serial", expected, mismatches,
                cf.date().serialNumber());
        checkBoolean(name, "isCapped", expected, mismatches, cf.isCapped());
        checkBoolean(name, "isFloored", expected, mismatches, cf.isFloored());

        // cap()/floor() can be null (i.e. NULL_REAL).
        checkNullableTight(name, "cap", expected, mismatches, cf.cap());
        checkNullableTight(name, "floor", expected, mismatches, cf.floor());

        if (cf.isCapped() && expected.has("effectiveCap")) {
            checkTight(name, "effectiveCap", expected, mismatches, cf.effectiveCap());
        }
        if (cf.isFloored() && expected.has("effectiveFloor")) {
            checkTight(name, "effectiveFloor", expected, mismatches, cf.effectiveFloor());
        }
    }

    //
    // helpers
    //

    private static void checkExact(final String name, final String key,
                                   final JSONObject expected, final List<String> mismatches,
                                   final long actual) {
        final long exp = expected.getLong(key);
        if (!Tolerance.exact(actual, exp)) {
            mismatches.add(name + "." + key + ": expected=" + exp + " actual=" + actual);
        }
    }

    private static void checkBoolean(final String name, final String key,
                                     final JSONObject expected, final List<String> mismatches,
                                     final boolean actual) {
        final boolean exp = expected.getBoolean(key);
        if (exp != actual) {
            mismatches.add(name + "." + key + ": expected=" + exp + " actual=" + actual);
        }
    }

    private static void checkTight(final String name, final String key,
                                   final JSONObject expected, final List<String> mismatches,
                                   final double actual) {
        final double exp = expected.getDouble(key);
        if (!Tolerance.tight(actual, exp)) {
            mismatches.add(fmt(name + "." + key, exp, actual));
        }
    }

    /**
     * Validates a possibly-null double field. JSON encodes "null" as the
     * string {@code "null"}; the Java accessor returns
     * {@link Constants#NULL_REAL} in that case.
     */
    private static void checkNullableTight(final String name, final String key,
                                           final JSONObject expected,
                                           final List<String> mismatches,
                                           final double actual) {
        final Object raw = expected.get(key);
        if (raw instanceof String) {
            // Expected null
            if (actual != Constants.NULL_REAL) {
                mismatches.add(name + "." + key
                        + ": expected=null actual=" + actual);
            }
        } else {
            final double exp = ((Number) raw).doubleValue();
            if (!Tolerance.tight(actual, exp)) {
                mismatches.add(fmt(name + "." + key, exp, actual));
            }
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}

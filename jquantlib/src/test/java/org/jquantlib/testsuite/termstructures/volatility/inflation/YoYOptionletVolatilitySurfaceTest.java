/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validated tests for YoYOptionletVolatilitySurface family against
 QuantLib v1.42.1 via
 migration-harness/references/termstructures/volatility/inflation/yoy_optionlet_vol.json.
 Phase 2r Track B.

 This source code is release under the BSD License.
*/
package org.jquantlib.testsuite.termstructures.volatility.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.InterpolatedYoYOptionletVolatilityCurve;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.termstructures.volatility.inflation.ConstantYoYOptionletVolatility;
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
import org.jquantlib.time.calendars.Target;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link ConstantYoYOptionletVolatility} (TIGHT) and
 * {@link InterpolatedYoYOptionletVolatilityCurve} (LOOSE — Linear bilinear
 * interpolation in T direction).
 *
 * <p>Reference file emits two scenario groups:
 * <ul>
 *   <li>{@code C_*}: ConstantYoYOptionletVolatility — flat surface, TIGHT.
 *   <li>{@code I_*}: InterpolatedYoYOptionletVolatilityCurve&lt;Linear&gt;
 *       — T-interpolated, LOOSE.
 * </ul>
 */
public class YoYOptionletVolatilitySurfaceTest {

    private static final String REF_GROUP =
            "termstructures/volatility/inflation/yoy_optionlet_vol";

    @Test
    public void constantYoYOptionletVolatility_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new Actual365Fixed();
        final Period observationLag = new Period(2, TimeUnit.Months);
        final Frequency freq = Frequency.Monthly;
        final int settlementDays = 0;

        final ConstantYoYOptionletVolatility constVol =
                new ConstantYoYOptionletVolatility(
                        0.18, settlementDays, cal, bdc, dc,
                        observationLag, freq, /*indexIsInterpolated*/ false);

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("C_")) continue;
            final Case c = ref.getCase(name);
            try {
                checkConstantCase(name, c, constVol, mismatches);
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    @Test
    public void interpolatedYoYOptionletVolatilityCurve_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new Actual365Fixed();
        final Period observationLag = new Period(2, TimeUnit.Months);
        final Frequency freq = Frequency.Monthly;
        final int settlementDays = 0;

        // Pillars mirror the C++ probe — refDate-shifted dates and matching vols.
        final Date refDate = cal.adjust(evalDate, bdc);
        final Date[] dates = new Date[]{
                refDate.sub(observationLag),
                refDate.add(new Period(1, TimeUnit.Years)),
                refDate.add(new Period(2, TimeUnit.Years)),
                refDate.add(new Period(5, TimeUnit.Years)),
                refDate.add(new Period(10, TimeUnit.Years)),
                refDate.add(new Period(20, TimeUnit.Years))
        };
        final double[] vols = new double[]{ 0.14, 0.15, 0.17, 0.20, 0.23, 0.26 };
        final double minStrike = -0.10;
        final double maxStrike = 0.50;

        final var curve = new InterpolatedYoYOptionletVolatilityCurve<Linear>(
                        Linear.class, settlementDays, cal, bdc, dc,
                        observationLag, freq, /*indexIsInterpolated*/ true,
                        dates, vols, minStrike, maxStrike);
        curve.enableExtrapolation();

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("I_")) continue;
            final Case c = ref.getCase(name);
            try {
                checkInterpolatedCase(name, c, curve, mismatches);
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkConstantCase(final String name, final Case c,
            final ConstantYoYOptionletVolatility constVol,
            final List<String> mismatches) {
        final JSONObject exp = (JSONObject) c.expectedRaw();

        switch (name) {
            case "C_referenceDate_serial": {
                final long expected = exp.getLong("value");
                final long actual = constVol.referenceDate().serialNumber();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "C_baseDate_serial": {
                final long expected = exp.getLong("value");
                final long actual = constVol.baseDate().serialNumber();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "C_minStrike": {
                final double expected = exp.getDouble("value");
                if (!Tolerance.tight(constVol.minStrike(), expected)) {
                    mismatches.add(fmt(name, expected, constVol.minStrike()));
                }
                return;
            }
            case "C_maxStrike": {
                final double expected = exp.getDouble("value");
                if (!Tolerance.tight(constVol.maxStrike(), expected)) {
                    mismatches.add(fmt(name, expected, constVol.maxStrike()));
                }
                return;
            }
            case "C_observationLag_days": {
                // The C++ probe records observationLag().length() — 2 for our 2-month lag.
                final long expected = exp.getLong("value");
                final long actual = constVol.observationLag().length();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "C_frequency_int": {
                final long expected = exp.getLong("value");
                final long actual = constVol.frequency().toInteger();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "C_indexIsInterpolated": {
                final boolean expected = exp.getBoolean("value");
                if (constVol.indexIsInterpolated() != expected) {
                    mismatches.add(name + ": expected=" + expected
                            + " actual=" + constVol.indexIsInterpolated());
                }
                return;
            }
            default:
                break;
        }

        if (name.startsWith("C_volatility_")) {
            final double expected = exp.getDouble("value");
            final long ms = c.inputs().getLong("matDate_serial");
            final double strike = c.inputs().getDouble("strike");
            final double actual = constVol.volatility(new Date(ms), strike);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
            return;
        }
        if (name.startsWith("C_totalVariance_")) {
            final double expected = exp.getDouble("value");
            final long ms = c.inputs().getLong("matDate_serial");
            final double strike = c.inputs().getDouble("strike");
            final double actual = constVol.totalVariance(new Date(ms), strike);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
            return;
        }
        mismatches.add(name + ": UNRECOGNIZED case");
    }

    private static void checkInterpolatedCase(final String name, final Case c,
            final InterpolatedYoYOptionletVolatilityCurve<Linear> curve,
            final List<String> mismatches) {
        final JSONObject exp = (JSONObject) c.expectedRaw();

        switch (name) {
            case "I_referenceDate_serial": {
                final long expected = exp.getLong("value");
                final long actual = curve.referenceDate().serialNumber();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "I_baseDate_serial": {
                final long expected = exp.getLong("value");
                final long actual = curve.baseDate().serialNumber();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "I_baseLevel": {
                final double expected = exp.getDouble("value");
                if (!Tolerance.loose(curve.baseLevel(), expected)) {
                    mismatches.add(fmt(name, expected, curve.baseLevel()));
                }
                return;
            }
            case "I_minStrike": {
                final double expected = exp.getDouble("value");
                if (!Tolerance.tight(curve.minStrike(), expected)) {
                    mismatches.add(fmt(name, expected, curve.minStrike()));
                }
                return;
            }
            case "I_maxStrike": {
                final double expected = exp.getDouble("value");
                if (!Tolerance.tight(curve.maxStrike(), expected)) {
                    mismatches.add(fmt(name, expected, curve.maxStrike()));
                }
                return;
            }
            case "I_observationLag_months": {
                final long expected = exp.getLong("value");
                final long actual = curve.observationLag().length();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "I_frequency_int": {
                final long expected = exp.getLong("value");
                final long actual = curve.frequency().toInteger();
                if (!Tolerance.exact(actual, expected)) {
                    mismatches.add(fmtL(name, expected, actual));
                }
                return;
            }
            case "I_dates_serials": {
                final JSONArray expArr = exp.getJSONArray("values");
                final Date[] actual = curve.dates();
                if (expArr.length() != actual.length) {
                    mismatches.add(name + ": length mismatch expected=" + expArr.length()
                            + " actual=" + actual.length);
                    return;
                }
                for (int i = 0; i < actual.length; ++i) {
                    if (!Tolerance.exact(actual[i].serialNumber(), expArr.getLong(i))) {
                        mismatches.add(name + "[" + i + "]: expected="
                                + expArr.getLong(i) + " actual="
                                + actual[i].serialNumber());
                    }
                }
                return;
            }
            case "I_times": {
                final JSONArray expArr = exp.getJSONArray("values");
                final double[] actual = curve.times();
                if (expArr.length() != actual.length) {
                    mismatches.add(name + ": length mismatch");
                    return;
                }
                for (int i = 0; i < actual.length; ++i) {
                    if (!Tolerance.loose(actual[i], expArr.getDouble(i))) {
                        mismatches.add(name + "[" + i + "]: expected="
                                + expArr.getDouble(i) + " actual=" + actual[i]);
                    }
                }
                return;
            }
            case "I_data": {
                final JSONArray expArr = exp.getJSONArray("values");
                final double[] actual = curve.data();
                if (expArr.length() != actual.length) {
                    mismatches.add(name + ": length mismatch");
                    return;
                }
                for (int i = 0; i < actual.length; ++i) {
                    if (!Tolerance.exact(actual[i], expArr.getDouble(i))) {
                        mismatches.add(name + "[" + i + "]: expected="
                                + expArr.getDouble(i) + " actual=" + actual[i]);
                    }
                }
                return;
            }
            default:
                break;
        }

        if (name.startsWith("I_volatility_")) {
            final double expected = exp.getDouble("value");
            final long ms = c.inputs().getLong("matDate_serial");
            // Inter cases are recorded without a strike — the probe uses
            // strike = 0.02 for those rows. Surface smile is flat, so this
            // is just a fixed test strike inside the curve domain.
            final double strike = c.inputs().has("strike")
                    ? c.inputs().getDouble("strike") : 0.02;
            final double actual = curve.volatility(new Date(ms), strike);
            if (!Tolerance.loose(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
            return;
        }
        if (name.startsWith("I_totalVariance_")) {
            final double expected = exp.getDouble("value");
            final long ms = c.inputs().getLong("matDate_serial");
            final double strike = c.inputs().has("strike")
                    ? c.inputs().getDouble("strike") : 0.02;
            final double actual = curve.totalVariance(new Date(ms), strike);
            if (!Tolerance.loose(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
            return;
        }
        mismatches.add(name + ": UNRECOGNIZED case");
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }

    private static String fmtL(final String name, final long expected, final long actual) {
        return name + ": expected=" + expected + " actual=" + actual;
    }
}

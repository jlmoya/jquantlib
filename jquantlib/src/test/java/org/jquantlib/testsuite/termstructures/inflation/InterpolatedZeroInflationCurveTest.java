/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for InterpolatedZeroInflationCurve against
 QuantLib v1.42.1 via
 migration-harness/references/termstructures/inflation/zero_inflation_curve.json
 (scenario I_*). Phase 2p A.1.
*/
package org.jquantlib.testsuite.termstructures.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link InterpolatedZeroInflationCurve} (Linear).
 *
 * <p>Scenario I (I_*): a curve constructed directly from {@code (dates, rates)}
 * — validates inspector accessors, baseDate, maxDate, frequency, and that
 * {@code zeroRate(date)} / {@code zeroRate(time)} reproduce C++ values at TIGHT tier.
 *
 * <p>The probe (zero_inflation_curve_probe.cpp) emits two scenario groups
 * (I_* and P_*); P_* belongs to {@link PiecewiseZeroInflationCurveTest}.
 */
public class InterpolatedZeroInflationCurveTest {

    private static final String REF_GROUP = "termstructures/inflation/zero_inflation_curve";

    @Test
    public void interpolatedZeroInflationCurve_matchesCpp() {
        // Match probe setup exactly.
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;

        final Date refDate = cal.adjust(evalDate, bdc);

        // Pillar dates — must match probe (refDate - 3M snapped to inflation
        // period start = 2007-05-01, then 5 maturities).
        // We use the same C++-derived serial values to compute baseDate
        // implicitly.
        final Date[] dates = new Date[]{
                new Date(1, Month.May,    2007), // baseDate (inflationPeriod(refDate-3M).first)
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] rates = new double[]{ 0.025, 0.030, 0.032, 0.034, 0.036, 0.038 };

        final InterpolatedZeroInflationCurve<Linear> curve =
                new InterpolatedZeroInflationCurve<>(Linear.class,
                        refDate, dates, rates, freq, dc);
        curve.enableExtrapolation();

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("I_")) continue;  // P_* belongs to the piecewise test
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, curve, mismatches);
            } catch (final Exception e) {
                mismatches.add(name + ": EXCEPTION " + e.getClass().getSimpleName()
                        + " " + e.getMessage());
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkCase(final String name, final Case c,
            final InterpolatedZeroInflationCurve<Linear> curve,
            final List<String> mismatches) {
        final JSONObject exp = (JSONObject) c.expectedRaw();

        if (name.equals("I_baseDate_serial")) {
            final long expected = exp.getLong("value");
            final long actual = curve.baseDate().serialNumber();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmtL(name, expected, actual));
            }
        } else if (name.equals("I_referenceDate_serial")) {
            final long expected = exp.getLong("value");
            final long actual = curve.referenceDate().serialNumber();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmtL(name, expected, actual));
            }
        } else if (name.equals("I_maxDate_serial")) {
            final long expected = exp.getLong("value");
            final long actual = curve.maxDate().serialNumber();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmtL(name, expected, actual));
            }
        } else if (name.equals("I_frequency")) {
            final long expected = exp.getLong("value");
            final long actual = curve.frequency().toInteger();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmtL(name, expected, actual));
            }
        } else if (name.startsWith("I_zeroRate_pillar_")) {
            final double expected = exp.getDouble("value");
            final long ds = c.inputs().getLong("date_serial");
            final Date d = new Date(ds);
            final double actual = curve.zeroRate(d, true);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.startsWith("I_zeroRate_inter_")) {
            final double expected = exp.getDouble("value");
            final long ds = c.inputs().getLong("date_serial");
            final Date d = new Date(ds);
            final double actual = curve.zeroRate(d, true);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.startsWith("I_zeroRate_time_")) {
            final double expected = exp.getDouble("value");
            final double t = c.inputs().getDouble("time");
            final double actual = curve.zeroRate(t, true);
            if (!Tolerance.tight(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.equals("I_dates_serials")) {
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
                            + expArr.getLong(i) + " actual=" + actual[i].serialNumber());
                }
            }
        } else if (name.equals("I_times")) {
            final JSONArray expArr = exp.getJSONArray("values");
            final double[] actual = curve.times();
            if (expArr.length() != actual.length) {
                mismatches.add(name + ": length mismatch");
                return;
            }
            for (int i = 0; i < actual.length; ++i) {
                if (!Tolerance.tight(actual[i], expArr.getDouble(i))) {
                    mismatches.add(name + "[" + i + "]: expected="
                            + expArr.getDouble(i) + " actual=" + actual[i]);
                }
            }
        } else if (name.equals("I_rates")) {
            final JSONArray expArr = exp.getJSONArray("values");
            final double[] actual = curve.rates();
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
        } else {
            mismatches.add(name + ": UNRECOGNIZED case");
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }

    private static String fmtL(final String name, final long expected, final long actual) {
        return name + ": expected=" + expected + " actual=" + actual;
    }
}

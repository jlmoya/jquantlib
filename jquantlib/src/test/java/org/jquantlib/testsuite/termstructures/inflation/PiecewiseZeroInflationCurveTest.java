/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for PiecewiseZeroInflationCurve against
 QuantLib v1.42.1 via
 migration-harness/references/termstructures/inflation/zero_inflation_curve.json
 (scenario P_*). Phase 2p A.1.
*/
package org.jquantlib.testsuite.termstructures.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.termstructures.inflation.PiecewiseZeroInflationCurve;
import org.jquantlib.termstructures.inflation.ZeroCouponInflationSwapHelper;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link PiecewiseZeroInflationCurve} (Linear).
 *
 * <p>Scenario P (P_*): bootstrap a curve from synthetic ZCIIS quotes
 * (1Y/2Y/5Y/10Y) against a UKRPI index with seeded historical fixings, then
 * verify:
 * <ul>
 *   <li>baseDate / referenceDate match C++</li>
 *   <li>impliedQuote at each helper round-trips to the input quote (LOOSE
 *       tier — bootstrap is iterative; C++ values are at 1e-15 from input)</li>
 *   <li>pillar dates and bootstrapped data values match C++</li>
 *   <li>zeroRate at a date grid (6m/1y/.../10y) matches C++</li>
 * </ul>
 *
 * <p>Tier: bootstrap convergence is at LOOSE (abs+rel 1e-8) per design §4.2.
 */
public class PiecewiseZeroInflationCurveTest {

    private static final String REF_GROUP = "termstructures/inflation/zero_inflation_curve";

    @Test
    public void piecewiseZeroInflationCurve_matchesCpp() {
        // Match probe setup exactly.
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag = new Period(3, TimeUnit.Months);

        final Date refDate = cal.adjust(evalDate, bdc);

        // Build UKRPI and seed UKRPI fixings exactly as the C++ probe does
        // (zero_inflation_curve_probe.cpp:49-69). The bootstrap requires the
        // baseFixing at startDate - swapObsLag = May 1, 2007 to compute
        // ZCIIS fair-rate; missing it would raise "Missing RPI fixing".
        // Phase1-closure-A8-A-563: the Index.addFixings NPE that originally
        // blocked this seeding has been resolved (see Index.java:146-178).
        final UKRPI ukRpi = new UKRPI(freq, false, false);
        seedUkRpiFixings(ukRpi);

        final Date baseDate = InflationTermStructure
                .inflationPeriod(refDate.sub(swapObsLag), freq).first();

        // Synthetic ZCIIS quotes (matches probe).
        final double[] quoteRates = { 0.0250, 0.0290, 0.0330, 0.0360 };
        final Period[] tenors = {
                new Period(1, TimeUnit.Years),
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years)
        };

        final List<ZeroCouponInflationSwapHelper> helpers = new ArrayList<>();
        for (int i = 0; i < quoteRates.length; ++i) {
            final Handle<Quote> qh = new Handle<>(new SimpleQuote(quoteRates[i]));
            final Date maturity = refDate.add(tenors[i]);
            helpers.add(new ZeroCouponInflationSwapHelper(qh, swapObsLag, maturity,
                    cal, bdc, dc, ukRpi));
        }

        final PiecewiseZeroInflationCurve<Linear> curve =
                new PiecewiseZeroInflationCurve<>(Linear.class, refDate, baseDate,
                        freq, dc, helpers);
        curve.enableExtrapolation();

        // Trigger bootstrap.
        curve.dates();

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("P_")) continue;
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, curve, helpers, mismatches);
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
            final PiecewiseZeroInflationCurve<Linear> curve,
            final List<ZeroCouponInflationSwapHelper> helpers,
            final List<String> mismatches) {
        final JSONObject exp = (JSONObject) c.expectedRaw();

        if (name.equals("P_baseDate_serial")) {
            final long expected = exp.getLong("value");
            final long actual = curve.baseDate().serialNumber();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmtL(name, expected, actual));
            }
        } else if (name.equals("P_referenceDate_serial")) {
            final long expected = exp.getLong("value");
            final long actual = curve.referenceDate().serialNumber();
            if (!Tolerance.exact(actual, expected)) {
                mismatches.add(fmtL(name, expected, actual));
            }
        } else if (name.startsWith("P_helperQuote_")) {
            final int idx = c.inputs().getInt("index");
            final double expected = exp.getDouble("value");
            final double actual = helpers.get(idx).impliedQuote();
            if (!Tolerance.loose(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
            }
        } else if (name.equals("P_pillarDates_serials")) {
            final JSONArray arr = exp.getJSONArray("values");
            final Date[] actual = curve.dates();
            if (arr.length() != actual.length) {
                mismatches.add(name + ": length mismatch expected="
                        + arr.length() + " actual=" + actual.length);
                return;
            }
            for (int i = 0; i < actual.length; ++i) {
                if (!Tolerance.exact(actual[i].serialNumber(), arr.getLong(i))) {
                    mismatches.add(name + "[" + i + "]: expected="
                            + arr.getLong(i) + " actual=" + actual[i].serialNumber());
                }
            }
        } else if (name.equals("P_pillarData")) {
            final JSONArray arr = exp.getJSONArray("values");
            final double[] actual = curve.data();
            if (arr.length() != actual.length) {
                mismatches.add(name + ": length mismatch expected="
                        + arr.length() + " actual=" + actual.length);
                return;
            }
            for (int i = 0; i < actual.length; ++i) {
                if (!Tolerance.loose(actual[i], arr.getDouble(i))) {
                    mismatches.add(name + "[" + i + "]: expected="
                            + arr.getDouble(i) + " actual=" + actual[i]);
                }
            }
        } else if (name.startsWith("P_zeroRate_grid_")) {
            final long ds = c.inputs().getLong("date_serial");
            final Date d = new Date(ds);
            final double expected = exp.getDouble("value");
            final double actual = curve.zeroRate(d, true);
            if (!Tolerance.loose(actual, expected)) {
                mismatches.add(fmt(name, expected, actual));
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

    /**
     * Seed UKRPI historical fixings matching the C++ probe
     * (migration-harness/cpp/probes/termstructures/inflation/
     * zero_inflation_curve_probe.cpp:49-69). Covers 2005-01 through 2007-07
     * monthly with synthetic real-world-ish values around 200.
     */
    private static void seedUkRpiFixings(final UKRPI ukRpi) {
        final Date[] dates = {
                new Date(1, Month.January,   2005), new Date(1, Month.February,  2005), new Date(1, Month.March,     2005),
                new Date(1, Month.April,     2005), new Date(1, Month.May,       2005), new Date(1, Month.June,      2005),
                new Date(1, Month.July,      2005), new Date(1, Month.August,    2005), new Date(1, Month.September, 2005),
                new Date(1, Month.October,   2005), new Date(1, Month.November,  2005), new Date(1, Month.December,  2005),
                new Date(1, Month.January,   2006), new Date(1, Month.February,  2006), new Date(1, Month.March,     2006),
                new Date(1, Month.April,     2006), new Date(1, Month.May,       2006), new Date(1, Month.June,      2006),
                new Date(1, Month.July,      2006), new Date(1, Month.August,    2006), new Date(1, Month.September, 2006),
                new Date(1, Month.October,   2006), new Date(1, Month.November,  2006), new Date(1, Month.December,  2006),
                new Date(1, Month.January,   2007), new Date(1, Month.February,  2007), new Date(1, Month.March,     2007),
                new Date(1, Month.April,     2007), new Date(1, Month.May,       2007), new Date(1, Month.June,      2007),
                new Date(1, Month.July,      2007),
        };
        final double[] vals = {
                189.9, 189.9, 190.5, 191.6, 192.0, 192.2, 192.2, 192.6, 193.1, 193.3, 193.6, 194.1,
                193.4, 194.2, 195.0, 196.5, 197.7, 198.5, 198.5, 199.2, 200.1, 200.4, 201.1, 202.7,
                201.6, 203.1, 204.4, 205.4, 206.2, 207.3, 206.1
        };
        // IndexManager static map persists across the test JVM — a prior test
        // (e.g. InflationTest using UKRPI) may have already seeded these
        // dates, triggering "duplicated fixing provided". Clear our own
        // history before re-seeding to ensure a fresh state regardless of
        // test ordering in the full suite.
        ukRpi.clearFixings();
        for (int i = 0; i < dates.length; ++i) {
            ukRpi.addFixing(dates[i], vals[i]);
        }
    }
}

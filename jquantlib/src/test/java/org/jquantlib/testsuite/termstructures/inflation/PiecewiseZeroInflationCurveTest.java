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

        // Build UKRPI. Note: this Java A.1 helper's impliedQuote() formula
        // (closed-form ZCIIS fair-rate from the curve) does NOT require
        // historical fixings — those are needed only when forecasting through
        // the index itself. The C++ probe seeds fixings for symmetry; we skip
        // here because Java InflationIndex.addFixings has an unrelated NPE bug
        // when bootstrapping a fresh index (TimeSeries.get returns null and
        // autoboxes, see Index.java:146). That bug is independent of this
        // sub-layer and will be addressed separately.
        final UKRPI ukRpi = new UKRPI(freq, false, false);

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
}

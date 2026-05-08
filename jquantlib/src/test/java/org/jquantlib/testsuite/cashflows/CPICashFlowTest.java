/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for CPICashFlow against
 QuantLib v1.42.1 via
 migration-harness/references/cashflows/cpi_coupon.json
 (Phase 2q L1 Track C — Section B cases B1..B5).
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CPICashFlow;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.math.Constants;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
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
 * Probe-driven tests for {@link CPICashFlow} (Section B from cpi_coupon_probe).
 *
 * <p>Validates standalone IndexedCashFlow form: explicit baseFixing vs
 * baseDate-only construction, {@code growthOnly} both ways, past + future
 * observation dates, and AsIndex/Linear interpolation. Tier: TIGHT.
 */
public class CPICashFlowTest {

    private static final String REF_GROUP = "cashflows/cpi_coupon";

    @Test
    public void cpiCashFlow_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag = new Period(3, TimeUnit.Months);

        final Date refDate = cal.adjust(evalDate, bdc);
        final Date[] nodeDates = new Date[] {
                new Date(1, Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = new double[] { 0.025, 0.030, 0.032, 0.034, 0.036, 0.038 };

        final InterpolatedZeroInflationCurve<Linear> zeroCurve =
                new InterpolatedZeroInflationCurve<>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        zeroCurve.enableExtrapolation();

        final Handle<ZeroInflationTermStructure> ts =
                new Handle<ZeroInflationTermStructure>(zeroCurve);
        final UKRPI ukRpi = new UKRPI(Frequency.Monthly, false, false, ts);

        final Date[] fixDates = new Date[] {
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
        final double[] fixVals = new double[] {
                189.9, 189.9, 190.5, 191.6, 192.0, 192.2, 192.2, 192.6, 193.1, 193.3, 193.6, 194.1,
                193.4, 194.2, 195.0, 196.5, 197.7, 198.5, 198.5, 199.2, 200.1, 200.4, 201.1, 202.7,
                201.6, 203.1, 204.4, 205.4, 206.2, 207.3, 206.1
        };
        for (int i = 0; i < fixDates.length; ++i) {
            ukRpi.addFixing(fixDates[i], fixVals[i], true);
        }

        // ---------- Cross-validate every Section B case ----------
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            if (!name.startsWith("B")) continue; // Section A is in CPICouponTest
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, ukRpi, swapObsLag, mismatches);
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
                                  final UKRPI ukRpi,
                                  final Period swapObsLag,
                                  final List<String> mismatches) {
        final JSONObject inputs = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final double notional = inputs.getDouble("notional");
        final long baseDateSerial = inputs.getLong("baseDate_serial");
        final String baseFixingStr = inputs.getString("baseFixing");
        final long obsDateSerial = inputs.getLong("observationDate_serial");
        final long paymentSerial = inputs.getLong("paymentDate_serial");
        final String interpStr = inputs.getString("interpolation");
        final boolean growthOnly = inputs.getBoolean("growthOnly");

        final boolean baseFixingIsNull = "null".equals(baseFixingStr);
        final double baseFixing = baseFixingIsNull
                ? Constants.NULL_REAL
                : Double.parseDouble(baseFixingStr);
        final Date baseDate = new Date(baseDateSerial);
        final Date observationDate = new Date(obsDateSerial);
        final Date paymentDate = new Date(paymentSerial);
        final CPI.InterpolationType interp = CPI.InterpolationType.valueOf(interpStr);

        final CPICashFlow cf = new CPICashFlow(notional, ukRpi, baseDate,
                baseFixing, observationDate, swapObsLag, interp, paymentDate, growthOnly);

        // date_serial — exact
        {
            final long exp = expected.getLong("date_serial");
            final long act = cf.date().serialNumber();
            if (!Tolerance.exact(act, exp)) {
                mismatches.add(name + ".date_serial: expected=" + exp + " actual=" + act);
            }
        }
        // baseFixing — TIGHT
        {
            final double exp = expected.getDouble("baseFixing");
            final double act = cf.baseFixing();
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".baseFixing", exp, act));
            }
        }
        // indexFixing — TIGHT
        {
            final double exp = expected.getDouble("indexFixing");
            final double act = cf.indexFixing();
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".indexFixing", exp, act));
            }
        }
        // amount — TIGHT
        {
            final double exp = expected.getDouble("amount");
            final double act = cf.amount();
            if (!Tolerance.tight(act, exp)) {
                mismatches.add(fmt(name + ".amount", exp, act));
            }
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}

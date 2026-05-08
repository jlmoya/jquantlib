/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for ZeroInflationCashFlow against
 QuantLib v1.42.1 via
 migration-harness/references/cashflows/zero_inflation_cashflow.json
 (Phase 2p A.2).
*/
package org.jquantlib.testsuite.cashflows;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.ZeroInflationCashFlow;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.UKRPI;
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
 * Probe-driven tests for {@link ZeroInflationCashFlow}.
 *
 * <p>Reproduces the C++ probe setup
 * (migration-harness/cpp/probes/cashflows/zero_inflation_cashflow_probe.cpp):
 * UKRPI index seeded with monthly fixings 2005-01..2007-07, bound to a
 * 6-pillar Linear-interpolated zero-inflation curve, then 8 cashflow
 * instances exercising each {@link CPI.InterpolationType}, both growthOnly
 * settings, and past/future end-date cases.
 *
 * <p>Tier: TIGHT — every output is deterministic from the seeded fixings and
 * the closed-form curve interpolation; no Newton solvers are involved.
 */
public class ZeroInflationCashFlowTest {

    private static final String REF_GROUP = "cashflows/zero_inflation_cashflow";

    @Test
    public void zeroInflationCashFlow_matchesCpp() {
        // ---------- Match probe setup exactly ----------
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period swapObsLag = new Period(3, TimeUnit.Months);

        // Pillar dates — match probe exactly. baseDate is
        // inflationPeriod(refDate - swapObsLag, freq).first = 2007-05-01.
        final Date refDate = cal.adjust(evalDate, bdc);
        final Date[] nodeDates = new Date[] {
                new Date(1, Month.May,    2007),  // baseDate
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

        // UKRPI bound to forecast curve. Java UKRPI ctor takes
        // (frequency, revised, interpolated, ts) — match probe (Monthly, false, false).
        final Handle<ZeroInflationTermStructure> ts =
                new Handle<ZeroInflationTermStructure>(zeroCurve);
        final UKRPI ukRpi = new UKRPI(Frequency.Monthly, false, false, ts);

        // Seed the same monthly fixings 2005-01..2007-07.
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
        // Force-overwrite to make the test idempotent across multiple JUnit runs
        // sharing the singleton IndexManager history.
        for (int i = 0; i < fixDates.length; ++i) {
            ukRpi.addFixing(fixDates[i], fixVals[i], true);
        }

        // ---------- Cross-validate every case ----------
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, ukRpi, mismatches);
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
                                  final List<String> mismatches) {
        final JSONObject inputs = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final double notional = inputs.getDouble("notional");
        final long startSerial = inputs.getLong("startDate_serial");
        final long endSerial = inputs.getLong("endDate_serial");
        final long paymentSerial = inputs.getLong("paymentDate_serial");
        final int obsLagMonths = inputs.getInt("observationLag_months");
        final String interpStr = inputs.getString("interpolation");
        final boolean growthOnly = inputs.getBoolean("growthOnly");

        final Date startDate = new Date(startSerial);
        final Date endDate = new Date(endSerial);
        final Date paymentDate = new Date(paymentSerial);
        final Period observationLag = new Period(obsLagMonths, TimeUnit.Months);
        final CPI.InterpolationType interp = CPI.InterpolationType.valueOf(interpStr);

        final ZeroInflationCashFlow cf = new ZeroInflationCashFlow(
                notional, ukRpi, interp, startDate, endDate,
                observationLag, paymentDate, growthOnly);

        // date_serial — exact (calendar arithmetic)
        {
            final long expectedDate = expected.getLong("date_serial");
            final long actualDate = cf.date().serialNumber();
            if (!Tolerance.exact(actualDate, expectedDate)) {
                mismatches.add(name + ".date_serial: expected=" + expectedDate
                        + " actual=" + actualDate);
            }
        }

        // baseFixing — TIGHT (deterministic from monthly fixings or curve forecast)
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

        // amount — TIGHT (notional * ratio, where ratio is the result of the
        // two TIGHT-bounded fixings above plus a single division/subtraction).
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

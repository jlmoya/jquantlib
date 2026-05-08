/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for InflationCapFloor against
 QuantLib v1.42.1 via
 migration-harness/references/instruments/inflation_cap_floor.json (Phase 2r C.1).
*/
package org.jquantlib.testsuite.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.YoYInflationCoupon;
import org.jquantlib.cashflow.YoYInflationCouponPricer;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.instruments.InflationCapFloor;
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
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link InflationCapFloor}.
 *
 * <p>Reproduces the C++ probe setup
 * (instruments/inflation_cap_floor_probe.cpp): a YYUKRPI YoY index with a
 * 6-pillar Linear-interpolated YoY curve, 5Y annual schedule starting
 * 13-Aug-2007 to 13-Aug-2012.
 *
 * <p>Tier rationale:
 * <ul>
 *   <li>Per-coupon dates and accrual times — exact / TIGHT.</li>
 *   <li>Cap/floor strike rates — exact equality on stored values.</li>
 *   <li>Number of coupons — exact integer.</li>
 * </ul>
 */
public class InflationCapFloorTest {

    private static final String REF_GROUP = "instruments/inflation_cap_floor";

    @Test
    public void inflationCapFloor_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);
        final Date refDate = cal.adjust(evalDate, bdc);

        // 6-pillar YoY curve
        final Date[] nodeDates = {
                new Date(1,  Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = {0.025, 0.027, 0.029, 0.031, 0.034, 0.036};
        final InterpolatedYoYInflationCurve<Linear> yoyCurve =
                new InterpolatedYoYInflationCurve<>(Linear.class, refDate,
                        nodeDates, nodeRates, freq, dc);
        yoyCurve.enableExtrapolation();
        final Handle<YoYInflationTermStructure> ts =
                new Handle<>(yoyCurve);
        final YYUKRPI yyIndex = new YYUKRPI(freq, false, false, ts);

        // Seed historic fixings
        final Date[] fixDates = {
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

        // 5Y schedule
        final Date startDate = evalDate;
        final Date endDate = new Date(13, Month.August, 2012);
        final Schedule schedule = new MakeSchedule(startDate, endDate,
                new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .schedule();

        // Build YoY leg
        final Leg yoyLeg = new Leg();
        for (int i = 0; i < schedule.size() - 1; ++i) {
            final Date start = schedule.date(i);
            final Date end = schedule.date(i + 1);
            final Date paymentDate = cal.adjust(end, bdc);
            yoyLeg.add(new YoYInflationCoupon(
                    1.0e6, paymentDate, start, end, 0,
                    yyIndex, observationLag, CPI.InterpolationType.AsIndex,
                    dc, 1.0, 0.0, start, end));
        }
        final YoYInflationCouponPricer pricer = new YoYInflationCouponPricer();
        for (final CashFlow cf : yoyLeg) {
            if (cf instanceof YoYInflationCoupon) {
                ((YoYInflationCoupon) cf).setPricer(pricer);
            }
        }

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        // Cap with strike 0.03
        {
            final List<Double> caps = new ArrayList<>();
            caps.add(0.03);
            final InflationCapFloor cap = new InflationCapFloor(
                    InflationCapFloor.Type.Cap, yoyLeg, caps);
            checkInstrument("inflcf_Cap", cap, ref.getCase("inflcf_Cap"), mismatches);
        }
        // Floor with strike 0.02
        {
            final List<Double> floors = new ArrayList<>();
            floors.add(0.02);
            final InflationCapFloor floor = new InflationCapFloor(
                    InflationCapFloor.Type.Floor, yoyLeg, floors);
            checkInstrument("inflcf_Floor", floor, ref.getCase("inflcf_Floor"), mismatches);
        }
        // Collar [0.02, 0.03]
        {
            final List<Double> caps = new ArrayList<>();
            caps.add(0.03);
            final List<Double> floors = new ArrayList<>();
            floors.add(0.02);
            final InflationCapFloor collar = new InflationCapFloor(
                    InflationCapFloor.Type.Collar, yoyLeg, caps, floors);
            checkInstrument("inflcf_Collar", collar, ref.getCase("inflcf_Collar"), mismatches);
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static void checkInstrument(final String label,
                                        final InflationCapFloor inst,
                                        final Case c,
                                        final List<String> mismatches) {
        final JSONObject expected = (JSONObject) c.expectedRaw();

        // Dates — exact
        if (inst.startDate().serialNumber() != expected.getLong("startDate_serial")) {
            mismatches.add(label + ".startDate_serial: expected="
                    + expected.getLong("startDate_serial")
                    + " actual=" + inst.startDate().serialNumber());
        }
        if (inst.maturityDate().serialNumber() != expected.getLong("maturityDate_serial")) {
            mismatches.add(label + ".maturityDate_serial: expected="
                    + expected.getLong("maturityDate_serial")
                    + " actual=" + inst.maturityDate().serialNumber());
        }
        if (inst.yoyLeg().size() != expected.getInt("numCoupons")) {
            mismatches.add(label + ".numCoupons: expected="
                    + expected.getInt("numCoupons")
                    + " actual=" + inst.yoyLeg().size());
        }

        // Per-coupon arrays
        final JSONArray expPayDates = expected.getJSONArray("payDates");
        final JSONArray expStartDates = expected.getJSONArray("startDates");
        final JSONArray expFixingDates = expected.getJSONArray("fixingDates");
        final JSONArray expAccrualTimes = expected.getJSONArray("accrualTimes");

        for (int i = 0; i < inst.yoyLeg().size(); ++i) {
            final YoYInflationCoupon cpn = (YoYInflationCoupon) inst.yoyLeg().get(i);
            if (cpn.date().serialNumber() != expPayDates.getLong(i)) {
                mismatches.add(label + ".payDates[" + i + "]: expected="
                        + expPayDates.getLong(i) + " actual=" + cpn.date().serialNumber());
            }
            if (cpn.accrualStartDate().serialNumber() != expStartDates.getLong(i)) {
                mismatches.add(label + ".startDates[" + i + "]: expected="
                        + expStartDates.getLong(i)
                        + " actual=" + cpn.accrualStartDate().serialNumber());
            }
            if (cpn.fixingDate().serialNumber() != expFixingDates.getLong(i)) {
                mismatches.add(label + ".fixingDates[" + i + "]: expected="
                        + expFixingDates.getLong(i)
                        + " actual=" + cpn.fixingDate().serialNumber());
            }
            // Accrual times — TIGHT
            if (!Tolerance.tight(cpn.accrualPeriod(), expAccrualTimes.getDouble(i))) {
                mismatches.add(label + ".accrualTimes[" + i + "]: expected="
                        + expAccrualTimes.getDouble(i)
                        + " actual=" + cpn.accrualPeriod());
            }
        }

        // Cap rates / floor rates
        if (expected.has("capRates")) {
            final JSONArray exp = expected.getJSONArray("capRates");
            for (int i = 0; i < inst.capRates().size(); ++i) {
                if (!Tolerance.exact(inst.capRates().get(i), exp.getDouble(i))) {
                    mismatches.add(label + ".capRates[" + i + "]: expected="
                            + exp.getDouble(i) + " actual=" + inst.capRates().get(i));
                }
            }
        }
        if (expected.has("floorRates")) {
            final JSONArray exp = expected.getJSONArray("floorRates");
            for (int i = 0; i < inst.floorRates().size(); ++i) {
                if (!Tolerance.exact(inst.floorRates().get(i), exp.getDouble(i))) {
                    mismatches.add(label + ".floorRates[" + i + "]: expected="
                            + exp.getDouble(i) + " actual=" + inst.floorRates().get(i));
                }
            }
        }
    }
}

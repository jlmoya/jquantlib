/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for InflationCapFloorEngines (Black, UnitDisplaced
 Black, Bachelier) against QuantLib v1.42.1 via
 migration-harness/references/pricingengines/inflation/inflation_cap_floor_engines.json
 (Phase 2r C.2).
*/
package org.jquantlib.testsuite.pricingengines.inflation;

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
import org.jquantlib.pricingengines.inflation.YoYInflationBachelierCapFloorEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationBlackCapFloorEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationUnitDisplacedBlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.termstructures.volatility.inflation.ConstantYoYOptionletVolatility;
import org.jquantlib.termstructures.volatility.inflation.YoYOptionletVolatilitySurface;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
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
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for the YoY-inflation cap/floor engine family.
 *
 * <p>Uses the {@link ConstantYoYOptionletVolatility} class (Track B,
 * landed alongside Track C) to build a constant 20% volatility surface
 * that mirrors the C++ probe's setup.
 *
 * <p>Tier rationale: cap/floor NPV involves a Black / Bachelier integral
 * over multiple optionlets with a YoY curve interpolation lookup —
 * LOOSE per design §4.2.
 */
public class InflationCapFloorEnginesTest {

    private static final String REF_GROUP =
            "pricingengines/inflation/inflation_cap_floor_engines";

    @Test
    public void inflationCapFloorEngines_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);
        final Date refDate = cal.adjust(evalDate, bdc);

        // YoY curve
        final Date[] nodeDates = {
                new Date(1,  Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] nodeRates = {0.025, 0.027, 0.029, 0.031, 0.034, 0.036};
        final var yoyCurve = new InterpolatedYoYInflationCurve<Linear>(Linear.class, refDate,
                        nodeDates, nodeRates, freq, dc);
        yoyCurve.enableExtrapolation();
        final var ts = new Handle<YoYInflationTermStructure>(yoyCurve);
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

        // Nominal discount curve: 5% Continuous Actual365Fixed
        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Continuous, Frequency.Annual);
        final var nominalTS = new Handle<YieldTermStructure>(nominalCurve);

        // Constant 20% vol surface — uses Track B's real ConstantYoYOptionletVolatility
        // (matches C++ probe setup with indexIsInterpolated=false).
        final YoYOptionletVolatilitySurface volSurface =
                new ConstantYoYOptionletVolatility(0.20, 0, cal, bdc, dc,
                        observationLag, freq, /*indexIsInterpolated*/ false);
        final var volTS = new Handle<YoYOptionletVolatilitySurface>(volSurface);

        // 5Y schedule
        final Date startDate = evalDate;
        final Date endDate = new Date(13, Month.August, 2012);
        final Schedule schedule = new MakeSchedule(startDate, endDate,
                new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .schedule();

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            final JSONObject inputs = c.inputs();
            final JSONObject expected = (JSONObject) c.expectedRaw();

            final Leg yoyLeg = buildLeg(schedule, cal, bdc, dc, yyIndex,
                    observationLag);
            final InflationCapFloor.Type type = "Cap".equals(inputs.getString("type"))
                    ? InflationCapFloor.Type.Cap
                    : "Floor".equals(inputs.getString("type"))
                            ? InflationCapFloor.Type.Floor
                            : InflationCapFloor.Type.Collar;

            final List<Double> caps = new ArrayList<>();
            final List<Double> floors = new ArrayList<>();
            if (type == InflationCapFloor.Type.Cap || type == InflationCapFloor.Type.Collar) {
                caps.add(inputs.getDouble("strikeCap"));
            }
            if (type == InflationCapFloor.Type.Floor || type == InflationCapFloor.Type.Collar) {
                floors.add(inputs.getDouble("strikeFloor"));
            }
            final InflationCapFloor inst = new InflationCapFloor(type, yoyLeg, caps, floors);

            // Black engine
            inst.setPricingEngine(new YoYInflationBlackCapFloorEngine(
                    yyIndex, volTS, nominalTS));
            final double npvBlack = inst.NPV();
            checkLoose(name, "npv_black", expected, mismatches, npvBlack);

            // Unit-Displaced Black engine
            inst.setPricingEngine(new YoYInflationUnitDisplacedBlackCapFloorEngine(
                    yyIndex, volTS, nominalTS));
            final double npvUdb = inst.NPV();
            checkLoose(name, "npv_unitDisplacedBlack", expected, mismatches, npvUdb);

            // Bachelier engine
            inst.setPricingEngine(new YoYInflationBachelierCapFloorEngine(
                    yyIndex, volTS, nominalTS));
            final double npvBach = inst.NPV();
            checkLoose(name, "npv_bachelier", expected, mismatches, npvBach);
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static Leg buildLeg(final Schedule schedule, final Calendar cal,
                                final BusinessDayConvention bdc, final DayCounter dc,
                                final YYUKRPI yyIndex, final Period observationLag) {
        final Leg leg = new Leg();
        for (int i = 0; i < schedule.size() - 1; ++i) {
            final Date start = schedule.date(i);
            final Date end = schedule.date(i + 1);
            final Date paymentDate = cal.adjust(end, bdc);
            leg.add(new YoYInflationCoupon(
                    1.0e6, paymentDate, start, end, 0,
                    yyIndex, observationLag, CPI.InterpolationType.AsIndex,
                    dc, 1.0, 0.0, start, end));
        }
        final YoYInflationCouponPricer pricer = new YoYInflationCouponPricer();
        for (final CashFlow cf : leg) {
            if (cf instanceof YoYInflationCoupon) {
                ((YoYInflationCoupon) cf).setPricer(pricer);
            }
        }
        return leg;
    }

    private static void checkLoose(final String name, final String key,
                                   final JSONObject expected,
                                   final List<String> mismatches,
                                   final double actual) {
        final double exp = expected.getDouble(key);
        if (!Tolerance.loose(actual, exp)) {
            mismatches.add(String.format("%s.%s: expected=%.17e actual=%.17e diff=%.3e",
                    name, key, exp, actual, Math.abs(actual - exp)));
        }
    }
}

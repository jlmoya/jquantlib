/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for CPISwap against
 QuantLib v1.42.1 via
 migration-harness/references/instruments/inflation_cap_floor.json (Phase 2r C.1).
*/
package org.jquantlib.testsuite.instruments;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.instruments.CPISwap;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Probe-driven test for {@link CPISwap}.
 *
 * <p>Mirrors the C++ probe (instruments/inflation_cap_floor_probe.cpp).
 *
 * <p>Tier rationale:
 * <ul>
 *   <li>Coupon counts — exact integer.</li>
 *   <li>{@code fairRate}, {@code fairSpread} — TIGHT (closed-form fallback).</li>
 *   <li>{@code legNPV}, {@code legBPS}, {@code NPV} — LOOSE (per-coupon
 *       interpolation accumulation).</li>
 * </ul>
 */
public class CPISwapTest {

    private static final String REF_GROUP = "instruments/inflation_cap_floor";

    @Test
    public void cpiSwap_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);
        final Date refDate = cal.adjust(evalDate, bdc);

        // Zero inflation curve (matches probe synthetic CPI levels)
        final Date[] zNodeDates = {
                new Date(1,  Month.May,    2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2009),
                new Date(13, Month.August, 2010),
                new Date(13, Month.August, 2012),
                new Date(13, Month.August, 2017)
        };
        final double[] zNodeFixings = {100.0, 102.0, 104.0, 106.0, 110.0, 120.0};
        final InterpolatedZeroInflationCurve<Linear> zeroCurve =
                new InterpolatedZeroInflationCurve<>(Linear.class, refDate,
                        zNodeDates, zNodeFixings, freq, dc);
        zeroCurve.enableExtrapolation();
        final Handle<ZeroInflationTermStructure> zts = new Handle<>(zeroCurve);
        final ZeroInflationIndex zeroIndex = new UKRPI(freq, false, false, zts);

        // Seed historic fixings
        final Date[] zfixDates = {
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
        double cpi = 100.0;
        for (final Date d : zfixDates) {
            zeroIndex.addFixing(d, cpi, true);
            cpi *= 1.002;
        }

        // Nominal discount curve: 5% Continuous Actual365Fixed
        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> nominalTS = new Handle<>(nominalCurve);
        final IborIndex floatIndex = new Euribor6M(nominalTS);

        // Schedules — start in mid-Dec to avoid past Euribor fixing requirements
        final Date cpiStart = new Date(15, Month.December, 2007);
        final Date cpiEnd = new Date(15, Month.December, 2012);
        final Schedule fixedSchedule = new MakeSchedule(cpiStart, cpiEnd,
                new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .schedule();
        final Schedule floatSchedule = new MakeSchedule(cpiStart, cpiEnd,
                new Period(6, TimeUnit.Months), cal,
                BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .schedule();

        final double fixedRate = 0.025;
        final double cpiBase = 100.0;
        final double spread = 0.0;
        final int fixingDays = 2;

        final CPISwap swap = new CPISwap(CPISwap.Type.Payer, 1.0e6, false,
                spread, dc, floatSchedule, bdc, fixingDays, floatIndex,
                fixedRate, cpiBase, dc, fixedSchedule, bdc, observationLag,
                zeroIndex, CPI.InterpolationType.AsIndex,
                org.jquantlib.math.Constants.NULL_REAL);
        swap.setPricingEngine(new DiscountingSwapEngine(nominalTS));

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final Case c = ref.getCase("cpiswap_5y_payer_AsIndex");
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final List<String> mismatches = new ArrayList<>();

        // Coupon counts — exact
        if (swap.cpiLeg().size() != expected.getInt("numFixedFlows")) {
            mismatches.add("numFixedFlows: expected="
                    + expected.getInt("numFixedFlows")
                    + " actual=" + swap.cpiLeg().size());
        }
        if (swap.floatLeg().size() != expected.getInt("numFloatFlows")) {
            mismatches.add("numFloatFlows: expected="
                    + expected.getInt("numFloatFlows")
                    + " actual=" + swap.floatLeg().size());
        }

        // Fair rate / spread — TIGHT
        if (!Tolerance.tight(swap.fairRate(), expected.getDouble("fairRate"))) {
            mismatches.add(fmt("fairRate", expected.getDouble("fairRate"),
                    swap.fairRate()));
        }
        if (!Tolerance.tight(swap.fairSpread(), expected.getDouble("fairSpread"))) {
            mismatches.add(fmt("fairSpread", expected.getDouble("fairSpread"),
                    swap.fairSpread()));
        }

        // NPV / legNPV / legBPS — LOOSE
        if (!Tolerance.loose(swap.NPV(), expected.getDouble("npv"))) {
            mismatches.add(fmt("npv", expected.getDouble("npv"), swap.NPV()));
        }
        if (!Tolerance.loose(swap.fixedLegNPV(), expected.getDouble("fixedLegNPV"))) {
            mismatches.add(fmt("fixedLegNPV", expected.getDouble("fixedLegNPV"),
                    swap.fixedLegNPV()));
        }
        if (!Tolerance.loose(swap.floatLegNPV(), expected.getDouble("floatLegNPV"))) {
            mismatches.add(fmt("floatLegNPV", expected.getDouble("floatLegNPV"),
                    swap.floatLegNPV()));
        }
        if (!Tolerance.loose(swap.legBPS(0), expected.getDouble("fixedLegBPS"))) {
            mismatches.add(fmt("fixedLegBPS", expected.getDouble("fixedLegBPS"),
                    swap.legBPS(0)));
        }
        if (!Tolerance.loose(swap.legBPS(1), expected.getDouble("floatLegBPS"))) {
            mismatches.add(fmt("floatLegBPS", expected.getDouble("floatLegBPS"),
                    swap.legBPS(1)));
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n" + String.join("\n", mismatches));
        }
    }

    private static String fmt(final String key, final double exp, final double act) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                key, exp, act, Math.abs(exp - act));
    }
}

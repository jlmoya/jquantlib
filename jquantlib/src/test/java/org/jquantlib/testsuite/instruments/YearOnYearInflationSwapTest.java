/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for YearOnYearInflationSwap against
 QuantLib v1.42.1 via
 migration-harness/references/instruments/year_on_year_inflation_swap.json
 (Phase 2q B).
*/
package org.jquantlib.testsuite.instruments;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.instruments.YearOnYearInflationSwap;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedYoYInflationCurve;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link YearOnYearInflationSwap}.
 *
 * <p>Reproduces the C++ probe setup (year_on_year_inflation_swap_probe.cpp):
 * a YYUKRPI-style YoY index seeded with monthly fixings, bound to a
 * 6-pillar Linear-interpolated YoY curve, with a flat-forward 5%
 * Continuous Actual365Fixed nominal discount curve.
 *
 * <h3>Tier rationale</h3>
 * <ul>
 *   <li>Calendar arithmetic ({@code *_serial}, payment-date arrays) — exact.</li>
 *   <li>{@code fairRate} and {@code fairSpread} — TIGHT. Closed-form from
 *       {@code legBPS[*]} and the engine's NPV: deterministic from the
 *       seeded fixings + curve + flat nominal discount.</li>
 *   <li>{@code legBPS}, {@code legNPV}, {@code npv} — LOOSE (1e-8). Each
 *       coupon's amount involves a {@code yoyRate} interpolation lookup;
 *       compounding errors over five or ten coupons accumulate.</li>
 * </ul>
 */
public class YearOnYearInflationSwapTest {

    private static final String REF_GROUP = "instruments/year_on_year_inflation_swap";

    @Test
    public void yearOnYearInflationSwap_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;

        final Date refDate = cal.adjust(evalDate, bdc);

        // 6-pillar YoY curve (matches probe nodes & rates).
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
        final var yoyCurve = new InterpolatedYoYInflationCurve<Linear>(Linear.class,
                        refDate, nodeDates, nodeRates, freq, dc);
        yoyCurve.enableExtrapolation();

        final Handle<YoYInflationTermStructure> ts =
                new Handle<YoYInflationTermStructure>(yoyCurve);
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

        // Nominal discount curve: 5% Continuous Actual365Fixed.
        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> nominalTS =
                new Handle<YieldTermStructure>(nominalCurve);
        final DiscountingSwapEngine engine = new DiscountingSwapEngine(nominalTS);

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            try {
                checkCase(name, c, yyIndex, evalDate, cal, bdc, dc, engine, mismatches);
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
                                  final YYUKRPI yyIndex,
                                  final Date evalDate,
                                  final Calendar cal,
                                  final BusinessDayConvention bdc,
                                  final DayCounter dc,
                                  final DiscountingSwapEngine engine,
                                  final List<String> mismatches) {
        final JSONObject inputs = c.inputs();
        final JSONObject expected = (JSONObject) c.expectedRaw();

        final YearOnYearInflationSwap.Type type =
                YearOnYearInflationSwap.Type.valueOf(inputs.getString("type"));
        final double nominal = inputs.getDouble("nominal");
        final long maturitySerial = inputs.getLong("maturity_serial");
        final int obsLagMonths = inputs.getInt("observationLag_months");
        final String interpStr = inputs.getString("interpolation");
        final double fixedRate = inputs.getDouble("fixedRate");
        final double spread = inputs.getDouble("spread");

        final Date maturity = new Date(maturitySerial);
        final Period observationLag = new Period(obsLagMonths, TimeUnit.Months);
        final CPI.InterpolationType interp = CPI.InterpolationType.valueOf(interpStr);

        // Build annual fixed/yoy schedules — both share the backwards schedule
        // (matches probe's MakeSchedule().from(evalDate).to(maturity)
        // .withTenor(1*Years).withConvention(Unadjusted).withCalendar(cal)
        // .backwards()).
        final Schedule fixedSchedule = YearOnYearInflationSwap.makeDefaultSchedule(
                evalDate, maturity, cal, bdc);
        final Schedule yoySchedule = fixedSchedule;

        final YearOnYearInflationSwap yyiis = new YearOnYearInflationSwap(
                type, nominal, fixedSchedule, fixedRate, dc,
                yoySchedule, yyIndex, observationLag, interp,
                spread, dc, cal, bdc);
        yyiis.setPricingEngine(engine);

        check(name, "startDate_actual_serial", expected, mismatches,
                yyiis.startDate().serialNumber());
        check(name, "maturityDate_actual_serial", expected, mismatches,
                yyiis.maturityDate().serialNumber());

        // fairRate and fairSpread — TIGHT (closed form via legBPS).
        checkTight(name, "fairRate", expected, mismatches, yyiis.fairRate());
        checkTight(name, "fairSpread", expected, mismatches, yyiis.fairSpread());

        // legBPS, legNPV, npv — LOOSE (interpolation + per-coupon accumulation).
        checkLoose(name, "fixedLegBPS", expected, mismatches, yyiis.legBPS(0));
        checkLoose(name, "yoyLegBPS", expected, mismatches, yyiis.legBPS(1));
        checkLoose(name, "fixedLegNPV", expected, mismatches, yyiis.fixedLegNPV());
        checkLoose(name, "yoyLegNPV", expected, mismatches, yyiis.yoyLegNPV());
        checkLoose(name, "npv", expected, mismatches, yyiis.NPV());

        // numCoupons — exact integer
        check(name, "numFixedCoupons", expected, mismatches,
                yyiis.fixedLeg().size());
        check(name, "numYoyCoupons", expected, mismatches,
                yyiis.yoyLeg().size());
    }

    private static void check(final String name, final String key,
                              final JSONObject expected, final List<String> mismatches,
                              final long actual) {
        final long exp = expected.getLong(key);
        if (!Tolerance.exact(actual, exp)) {
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

    private static void checkLoose(final String name, final String key,
                                   final JSONObject expected, final List<String> mismatches,
                                   final double actual) {
        final double exp = expected.getDouble(key);
        if (!Tolerance.loose(actual, exp)) {
            mismatches.add(fmt(name + "." + key, exp, actual));
        }
    }

    private static String fmt(final String name, final double expected, final double actual) {
        return String.format("%s: expected=%.17e actual=%.17e diff=%.3e",
                name, expected, actual, Math.abs(actual - expected));
    }
}

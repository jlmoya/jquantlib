/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Cross-validated tests for YoYOptionletHelper against C++ v1.42.1 via
 migration-harness/references/experimental/inflation/yoy_optionlet_helper.json
 (Phase 2s Track B).

 This source code is released under the BSD License.
*/
package org.jquantlib.testsuite.experimental.inflation;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.YoYOptionletHelper;
import org.jquantlib.experimental.inflation.YoYOptionletHelpers;
import org.jquantlib.indexes.CPI;
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.inflation.YoYInflationBlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
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
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Probe-driven tests for {@link YoYOptionletHelper} (and its
 * {@link YoYOptionletHelpers} static-factory facade).
 *
 * <p>Tier rationale: cap/floor implied-quote NPV under a constant vol
 * surface is identical math to {@code InflationCapFloorEnginesTest} →
 * LOOSE per design §4.2.
 */
public class YoYOptionletHelperTest {

    private static final String REF_GROUP =
            "experimental/inflation/yoy_optionlet_helper";

    @Test
    public void yoyOptionletHelper_impliedQuote_matchesCpp() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);
        final Date refDate = cal.adjust(evalDate, bdc);

        // YoY curve (mirrors probe)
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
        final Handle<YoYInflationTermStructure> ts = new Handle<>(yoyCurve);
        final YYUKRPI yyIndex = new YYUKRPI(freq, false, false, ts);

        // Seed historic fixings (mirrors probe + InflationCapFloorEnginesTest)
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

        // Nominal curve (flat 5%)
        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Compounded, Frequency.Annual);
        final Handle<YieldTermStructure> nominalTS = new Handle<>(nominalCurve);

        // Flat 20% vol surface (reusable)
        final double flatVol = 0.20;
        final YoYOptionletVolatilitySurface volSurface =
                new ConstantYoYOptionletVolatility(flatVol, 0, cal, bdc, dc,
                        observationLag, freq, /*indexIsInterpolated*/ false);
        final Handle<YoYOptionletVolatilitySurface> hVS = new Handle<>(volSurface);

        // Black cap/floor engine
        final YoYInflationBlackCapFloorEngine blackEngine =
                new YoYInflationBlackCapFloorEngine(yyIndex, hVS, nominalTS);

        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> mismatches = new ArrayList<>();

        for (final String name : ref.caseNames()) {
            final Case c = ref.getCase(name);
            final JSONObject inputs = c.inputs();
            final JSONObject expected = (JSONObject) c.expectedRaw();

            final InflationCapFloor.Type type =
                    "Cap".equals(inputs.getString("capFloorType"))
                            ? InflationCapFloor.Type.Cap
                            : InflationCapFloor.Type.Floor;
            final int n = inputs.getInt("n");
            final double strike = inputs.getDouble("strike");
            final double notional = inputs.getDouble("notional");
            final int fixingDays = inputs.getInt("fixingDays");

            final Handle<Quote> dummyQuote = new Handle<>(new SimpleQuote(1.0));

            final YoYOptionletHelper helper = new YoYOptionletHelper(
                    dummyQuote, notional, type, observationLag, dc, cal,
                    fixingDays, yyIndex, CPI.InterpolationType.Flat,
                    strike, n, blackEngine);

            // Trigger the term-structure / vol wiring (mirrors probe).
            helper.setTermStructure(volSurface);

            final double expIQ = expected.getDouble("impliedQuote");
            final double actualIQ;
            try {
                actualIQ = helper.impliedQuote();
            } catch (final RuntimeException re) {
                mismatches.add(name + ": EXCEPTION "
                        + re.getClass().getSimpleName()
                        + " " + re.getMessage());
                continue;
            }

            if (!Tolerance.loose(actualIQ, expIQ)) {
                mismatches.add(String.format(
                        "%s: expected=%.17e actual=%.17e diff=%.3e",
                        name, expIQ, actualIQ, Math.abs(actualIQ - expIQ)));
            }
        }

        if (!mismatches.isEmpty()) {
            fail(mismatches.size() + " mismatch(es):\n"
                    + String.join("\n", mismatches));
        }
    }

    /** Smoke test for the static-factory facade. */
    @Test
    public void yoyOptionletHelpers_factoryConstructs() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);
        final Date refDate = cal.adjust(evalDate, bdc);

        final Date[] nodeDates = {
                new Date(1, Month.May, 2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2010),
        };
        final double[] nodeRates = {0.025, 0.027, 0.031};
        final InterpolatedYoYInflationCurve<Linear> yoyCurve =
                new InterpolatedYoYInflationCurve<>(Linear.class, refDate,
                        nodeDates, nodeRates, freq, dc);
        yoyCurve.enableExtrapolation();
        final Handle<YoYInflationTermStructure> ts = new Handle<>(yoyCurve);
        final YYUKRPI yyIndex = new YYUKRPI(freq, false, false, ts);

        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Compounded, Frequency.Annual);
        final Handle<YieldTermStructure> nominalTS = new Handle<>(nominalCurve);

        final YoYOptionletVolatilitySurface volSurface =
                new ConstantYoYOptionletVolatility(0.20, 0, cal, bdc, dc,
                        observationLag, freq, false);
        final Handle<YoYOptionletVolatilitySurface> hVS = new Handle<>(volSurface);

        final YoYInflationBlackCapFloorEngine engine =
                new YoYInflationBlackCapFloorEngine(yyIndex, hVS, nominalTS);

        final Handle<Quote> dummyQuote = new Handle<>(new SimpleQuote(1.0));
        final YoYOptionletHelper helper = YoYOptionletHelpers.makeHelper(
                dummyQuote, 10000.0, InflationCapFloor.Type.Cap,
                observationLag, dc, cal, 0, yyIndex,
                CPI.InterpolationType.Flat, 0.03, 1, engine);
        assertNotNull("makeHelper should return a non-null helper", helper);
    }
}

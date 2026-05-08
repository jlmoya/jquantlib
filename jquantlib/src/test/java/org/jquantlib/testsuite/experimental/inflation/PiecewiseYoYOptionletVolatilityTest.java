/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

 Smoke tests for PiecewiseYoYOptionletVolatility — Phase 2s Track B.

 The full bootstrap test (calling helpers.recalculate() and verifying
 stripped vols against C++) requires Phase 2s Track C's
 YoYCapFloorTermPriceSurface to land. Until that integration is wired,
 these tests verify:

   - The class compiles and constructs
   - It is a YoYOptionletVolatilitySurface
   - It carries the InterpolatedYoYOptionletVolatilityCurve API
*/
package org.jquantlib.testsuite.experimental.inflation;

import java.util.Collections;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.PiecewiseYoYOptionletVolatility;
import org.jquantlib.experimental.inflation.YoYOptionletHelper;
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
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PiecewiseYoYOptionletVolatilityTest {

    @Test
    public void piecewiseYoYOptionletVolatility_constructsWithSingleHelper() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new Actual365Fixed();
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);
        final Date refDate = cal.adjust(evalDate, bdc);

        // YoY curve
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

        // Seed historic fixings
        for (int y = 2005; y <= 2007; ++y) {
            for (int m = 1; m <= 12; ++m) {
                if (y == 2007 && m > 7) {
                    break;
                }
                yyIndex.addFixing(new Date(1, Month.valueOf(m), y), 0.025, true);
            }
        }

        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Compounded, Frequency.Annual);
        final Handle<YieldTermStructure> nominalTS = new Handle<>(nominalCurve);

        final YoYOptionletVolatilitySurface volSurface =
                new ConstantYoYOptionletVolatility(0.20, 0, cal, bdc, dc,
                        observationLag, freq, false);
        final Handle<YoYOptionletVolatilitySurface> hVS = new Handle<>(volSurface);
        final YoYInflationBlackCapFloorEngine engine =
                new YoYInflationBlackCapFloorEngine(yyIndex, hVS, nominalTS);

        // Build a tiny single-helper bootstrap target.
        // Use a self-consistent quote (the price under a flat 20% vol).
        final Handle<Quote> q = new Handle<>(new SimpleQuote(32.12067640146455));  // cap n=2 K=0.03 from probe
        final YoYOptionletHelper helper = new YoYOptionletHelper(
                q, 10000.0, InflationCapFloor.Type.Cap,
                observationLag, dc, cal, 0, yyIndex,
                CPI.InterpolationType.Flat, 0.03, 2, engine);

        // Construct piecewise vol with this single helper. Construction
        // alone does not run the bootstrap — we don't call calculate()
        // here because that would re-pivot the engine vol, and we only
        // want to verify the API surface.
        final PiecewiseYoYOptionletVolatility<Linear> pw =
                new PiecewiseYoYOptionletVolatility<>(
                        Linear.class, 0, cal, bdc, dc, observationLag, freq,
                        /*indexIsInterpolated*/ false,
                        /*minStrike*/ 0.025,
                        /*maxStrike*/ 0.035,
                        /*baseYoYVolatility*/ 0.18,
                        Collections.singletonList(helper));

        assertNotNull("PiecewiseYoYOptionletVolatility should construct", pw);
        assertTrue("Should be a YoYOptionletVolatilitySurface",
                pw instanceof YoYOptionletVolatilitySurface);
        // Construction-time inspectors
        // (interp factory class is set, parent's API surface is intact).
        assertNotNull("interpolator class should be set", pw.interpolatorClass());
    }
}

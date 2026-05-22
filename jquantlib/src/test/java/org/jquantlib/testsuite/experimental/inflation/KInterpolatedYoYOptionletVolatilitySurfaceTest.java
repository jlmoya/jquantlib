/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

 Smoke tests for KInterpolatedYoYOptionletVolatilitySurface — Phase 2s
 Track B.

 The full integration test (constructing with a real
 YoYCapFloorTermPriceSurface and running the stripper) requires Phase 2s
 Track C's term-price surface to land. Until that integration is wired,
 these tests verify:

   - The class compiles and inherits YoYOptionletVolatilitySurface
   - The constructor pulls frequency/interpolated flag from the surface
   - Construction wires through to YoYOptionletStripper.initialize(...)
*/
package org.jquantlib.testsuite.experimental.inflation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.inflation.KInterpolatedYoYOptionletVolatilitySurface;
import org.jquantlib.experimental.inflation.YoYCapFloorTermPriceSurfaceLike;
import org.jquantlib.experimental.inflation.YoYOptionletStripper;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.pricingengines.inflation.InflationCapFloorEngine;
import org.jquantlib.pricingengines.inflation.YoYInflationBlackCapFloorEngine;
import org.jquantlib.quotes.Handle;
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
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.util.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KInterpolatedYoYOptionletVolatilitySurfaceTest {

    @Test
    public void kInterpolated_constructsAndDelegatesIndexMetadata() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new Actual365Fixed();
        final Frequency freq = Frequency.Monthly;
        final Period observationLag = new Period(3, TimeUnit.Months);
        final Date refDate = cal.adjust(evalDate, bdc);

        // YoY curve + index (mirrors helper test)
        final Date[] nodeDates = {
                new Date(1, Month.May, 2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2010),
        };
        final double[] nodeRates = {0.025, 0.027, 0.031};
        final var yoyCurve = new InterpolatedYoYInflationCurve<Linear>(Linear.class, refDate,
                        nodeDates, nodeRates, freq, dc);
        yoyCurve.enableExtrapolation();
        final var ts = new Handle<YoYInflationTermStructure>(yoyCurve);
        final YYUKRPI yyIndex = new YYUKRPI(freq, false, false, ts);

        final FlatForward nominalCurve = new FlatForward(refDate, 0.05, dc,
                Compounding.Compounded, Frequency.Annual);
        final var nominalTS = new Handle<YieldTermStructure>(nominalCurve);

        final YoYOptionletVolatilitySurface vs0 =
                new ConstantYoYOptionletVolatility(0.20, 0, cal, bdc, dc,
                        observationLag, freq, false);
        final var hVS = new Handle<YoYOptionletVolatilitySurface>(vs0);

        final YoYInflationBlackCapFloorEngine engine =
                new YoYInflationBlackCapFloorEngine(yyIndex, hVS, nominalTS);

        // Stub price surface
        final FakeTermPriceSurface fakeSurf =
                new FakeTermPriceSurface(yyIndex, refDate, dc, cal, bdc,
                        observationLag, freq);

        // Stub stripper that records initialize() and serves a fixed slice.
        final RecordingStripper stripper = new RecordingStripper();

        final var kSurf = new KInterpolatedYoYOptionletVolatilitySurface<Linear>(Linear.class,
                        0, cal, bdc, dc, observationLag,
                        fakeSurf, engine, stripper, /*slope*/ -0.5);

        assertNotNull("kSurf should construct", kSurf);
        assertTrue("kSurf should inherit YoYOptionletVolatilitySurface",
                kSurf instanceof YoYOptionletVolatilitySurface);
        // Frequency/interpolated should come from the price surface's index.
        assertEquals("frequency from yoyIndex", freq, kSurf.frequency());
        assertEquals("indexIsInterpolated from yoyIndex",
                yyIndex.interpolated(), kSurf.indexIsInterpolated());

        // Construction triggers performCalculations() → stripper.initialize(...).
        assertTrue("stripper.initialize should have been called",
                stripper.initialized);
        assertEquals("slope", -0.5, stripper.lastSlope, 1.0e-15);

        // strikes/maturities passthrough
        assertEquals("minStrike from surface", 0.02, kSurf.minStrike(), 1.0e-15);
        assertEquals("maxStrike from surface", 0.06, kSurf.maxStrike(), 1.0e-15);
    }

    // ---------------------------------------------------------------
    // Test doubles
    // ---------------------------------------------------------------

    /** Minimal in-test stub of a YoY cap/floor term-price surface. */
    private static final class FakeTermPriceSurface
            implements YoYCapFloorTermPriceSurfaceLike {

        private final YoYInflationIndex idx;
        private final Date ref;
        private final DayCounter dc;
        private final Calendar cal;
        private final BusinessDayConvention bdc;
        private final Period lag;
        private final Frequency freq;

        FakeTermPriceSurface(final YoYInflationIndex idx, final Date ref,
                             final DayCounter dc, final Calendar cal,
                             final BusinessDayConvention bdc,
                             final Period lag, final Frequency freq) {
            this.idx = idx;
            this.ref = ref;
            this.dc = dc;
            this.cal = cal;
            this.bdc = bdc;
            this.lag = lag;
            this.freq = freq;
        }

        @Override public Date referenceDate() { return ref; }
        @Override public DayCounter dayCounter() { return dc; }
        @Override public Calendar calendar() { return cal; }
        @Override public BusinessDayConvention businessDayConvention() { return bdc; }
        @Override public double timeFromReference(final Date d) {
            return dc.yearFraction(ref, d);
        }
        @Override public YoYInflationIndex yoyIndex() { return idx; }
        @Override public YoYInflationTermStructure YoYTS() {
            return idx.yoyInflationTermStructure().currentLink();
        }
        @Override public Period observationLag() { return lag; }
        @Override public Frequency frequency() { return freq; }
        @Override public boolean indexIsInterpolated() { return false; }
        @Override public int fixingDays() { return 0; }
        @Override public Date baseDate() { return ref.sub(lag); }
        @Override public List<Double> capStrikes() {
            return Arrays.asList(0.04, 0.05, 0.06);
        }
        @Override public List<Double> floorStrikes() {
            return Arrays.asList(0.02, 0.03);
        }
        @Override public List<Double> strikes() {
            return Arrays.asList(0.02, 0.03, 0.04, 0.05, 0.06);
        }
        @Override public List<Period> maturities() {
            return Arrays.asList(
                    new Period(1, TimeUnit.Years),
                    new Period(2, TimeUnit.Years),
                    new Period(3, TimeUnit.Years));
        }
        @Override public Period minMaturity() {
            return new Period(1, TimeUnit.Years);
        }
        @Override public Date yoyOptionDateFromTenor(final Period p) {
            return ref.add(p);
        }
        @Override public double capPrice(final Period p, final double k) { return 1.0; }
        @Override public double floorPrice(final Period p, final double k) { return 1.0; }
        @Override public double capPrice(final Date d, final double k) { return 1.0; }
        @Override public double floorPrice(final Date d, final double k) { return 1.0; }
    }

    /**
     * In-test stub stripper: records initialize() args, returns a fixed
     * three-point slice on every {@code slice(d)} call.
     */
    private static final class RecordingStripper extends YoYOptionletStripper {

        boolean initialized = false;
        double lastSlope = Double.NaN;

        @Override
        public void initialize(final YoYCapFloorTermPriceSurfaceLike capFloorPrices,
                               final InflationCapFloorEngine pricer,
                               final double slope) {
            this.initialized = true;
            this.lastSlope = slope;
            // Mirror the protected-state mutation that real strippers do.
            this.yoyCapFloorTermPriceSurface_ = capFloorPrices;
            this.p_ = pricer;
        }

        @Override
        public double minStrike() { return 0.02; }

        @Override
        public double maxStrike() { return 0.06; }

        @Override
        public List<Double> strikes() {
            return Arrays.asList(0.02, 0.03, 0.04, 0.05, 0.06);
        }

        @Override
        public Pair<List<Double>, List<Double>> slice(final Date d) {
            // Return a small five-point slice for the K-interpolation.
            final List<Double> ks = Arrays.asList(0.02, 0.03, 0.04, 0.05, 0.06);
            final List<Double> vs = new ArrayList<>(Arrays.asList(
                    0.18, 0.20, 0.22, 0.24, 0.26));
            return new Pair<>(ks, vs);
        }
    }
}

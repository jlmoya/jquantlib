/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.YoYInflationIndex;
import org.jquantlib.indexes.ZeroInflationIndex;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YoYInflationTermStructure;
import org.jquantlib.termstructures.ZeroInflationTermStructure;
import org.jquantlib.termstructures.inflation.InterpolatedZeroInflationCurve;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.junit.Test;

/**
 * Smoke tests for {@code clone(Handle<...>)} on inflation indexes added in
 * Phase 2q L0 A.1, mirroring C++ v1.42.1 {@code ZeroInflationIndex::clone}
 * and {@code YoYInflationIndex::clone}.
 *
 * <p>These tests verify two things:
 * <ol>
 *   <li>The clone is a distinct instance (never the same object as {@code this}).</li>
 *   <li>Forecasted fixings via the cloned instance route through the new
 *       term-structure handle (i.e. with a different curve installed under the
 *       new handle, the cloned index produces a different forecast than the
 *       original index).</li>
 * </ol>
 */
public class InflationIndexCloneTest {

    @Test
    public void zeroInflationIndex_cloneReturnsDistinctInstanceWithNewHandle() {
        final Date evalDate = new Date(13, Month.August, 2007);
        new Settings().setEvaluationDate(evalDate);

        final Calendar cal = new UnitedKingdom();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
        final Frequency freq = Frequency.Monthly;
        final Date refDate = cal.adjust(evalDate, bdc);

        final Date[] nodeDatesA = new Date[] {
                new Date(1, Month.May, 2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2010)
        };
        final double[] nodeRatesA = new double[] { 0.025, 0.030, 0.034 };
        final InterpolatedZeroInflationCurve<Linear> curveA =
                new InterpolatedZeroInflationCurve<>(Linear.class,
                        refDate, nodeDatesA, nodeRatesA, freq, dc);
        curveA.enableExtrapolation();

        final Date[] nodeDatesB = new Date[] {
                new Date(1, Month.May, 2007),
                new Date(13, Month.August, 2008),
                new Date(13, Month.August, 2010)
        };
        // Curve B uses noticeably different rates so a forecasted fixing through
        // it must differ from one through curve A.
        final double[] nodeRatesB = new double[] { 0.060, 0.065, 0.070 };
        final InterpolatedZeroInflationCurve<Linear> curveB =
                new InterpolatedZeroInflationCurve<>(Linear.class,
                        refDate, nodeDatesB, nodeRatesB, freq, dc);
        curveB.enableExtrapolation();

        final Handle<ZeroInflationTermStructure> tsA =
                new Handle<ZeroInflationTermStructure>(curveA);
        final Handle<ZeroInflationTermStructure> tsB =
                new Handle<ZeroInflationTermStructure>(curveB);

        // Original index: UKRPI (a concrete ZeroInflationIndex subclass).
        final UKRPI orig = new UKRPI(freq, false, false, tsA);

        // Add the base-period fixing required by forecastFixing().
        // baseDate of curveA is 1-May-2007 → period start is 1-May-2007.
        orig.addFixing(new Date(1, Month.May, 2007), 200.0, true);

        // Sanity: forecast through original (curveA).
        final Date forecastDate = new Date(13, Month.August, 2009);
        final double fixingA = orig.fixing(forecastDate, false);
        assertTrue("forecast through original handle should be > base fixing",
                fixingA > 200.0);

        // Clone with the alternate handle.
        final ZeroInflationIndex cloned = orig.clone(tsB);

        // Distinct instance.
        assertNotSame("clone must return a distinct instance", orig, cloned);
        assertNotNull("clone must not be null", cloned);

        // Cloned index shares static metadata with the original.
        assertEquals(orig.familyName(), cloned.familyName());
        assertEquals(orig.region().name(), cloned.region().name());
        assertEquals(orig.frequency(), cloned.frequency());
        assertEquals(orig.availabilityLag(), cloned.availabilityLag());
        assertEquals(orig.currency().code(), cloned.currency().code());
        assertEquals(orig.revised(), cloned.revised());
        assertEquals(orig.interpolated(), cloned.interpolated());

        // Index manager keys on name() so historic fixings are shared (mirrors
        // C++ where IndexManager is a singleton). The clone can therefore see
        // the base-period fixing and produce a forecast.
        final double fixingB = cloned.fixing(forecastDate, false);

        // The two forecasts must differ — they come from materially different
        // zero curves under the same compounding formula.
        // Loose tier (1e-3 absolute) is way more than enough; the difference
        // for a 2y horizon at 3% vs 6.5% is on the order of several index
        // points.
        assertTrue("clone routes through new handle: forecast must differ from original. fixingA="
                        + fixingA + " fixingB=" + fixingB,
                Math.abs(fixingA - fixingB) > 1.0e-3);

        // The clone's forecast through curve B must also exceed the base fixing
        // (the curve has positive zero rates throughout).
        assertTrue("clone forecast through curveB should be > base fixing",
                fixingB > 200.0);
    }

    @Test
    public void yoyInflationIndex_cloneReturnsDistinctInstanceWithNewHandle() {
        // No concrete YoYInflationTermStructure exists in this branch yet
        // (Track B in Phase 2q L1 ports InterpolatedYoYInflationCurve), so we
        // verify clone identity + metadata propagation + handle distinctness
        // against empty handles. Forecast-through-handle is exercised by Track
        // B's tests once the concrete curve lands.
        final Handle<YoYInflationTermStructure> tsA =
                new Handle<YoYInflationTermStructure>();
        final Handle<YoYInflationTermStructure> tsB =
                new Handle<YoYInflationTermStructure>();

        final YYUKRPI orig = new YYUKRPI(Frequency.Monthly, false, false, tsA);

        final YoYInflationIndex cloned = orig.clone(tsB);

        // Distinct instance.
        assertNotSame("clone must return a distinct instance", orig, cloned);
        assertNotNull("clone must not be null", cloned);

        // Cloned index shares static metadata with the original.
        assertEquals(orig.familyName(), cloned.familyName());
        assertEquals(orig.region().name(), cloned.region().name());
        assertEquals(orig.frequency(), cloned.frequency());
        assertEquals(orig.availabilityLag(), cloned.availabilityLag());
        assertEquals(orig.currency().code(), cloned.currency().code());
        assertEquals(orig.revised(), cloned.revised());
        assertEquals(orig.interpolated(), cloned.interpolated());
        assertEquals(orig.ratio(), cloned.ratio());

        // The cloned index's term-structure handle is the new one.
        assertNotSame("clone's term-structure handle must be tsB",
                orig.yoyInflationTermStructure(),
                cloned.yoyInflationTermStructure());
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.ibor.Eonia;
import org.jquantlib.instruments.OvernightIndexedSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Smoke tests for {@link OvernightIndexedSwap}.
 *
 * <p>Validates:
 * <ul>
 *   <li>Construction with single-schedule constructor.</li>
 *   <li>NPV evaluates without exception under
 *       {@link DiscountingSwapEngine} + a flat-forward curve.</li>
 *   <li>fairRate of an at-the-money OIS roughly matches the flat
 *       forward rate (loose tolerance — tier-stratified LOOSE 1e-3).</li>
 * </ul>
 *
 * @author JQuantLib migration team
 */
public class OvernightIndexedSwapSmokeTest {

    @Test
    public void buildsAndPrices() {
        final Date today = new Date(15, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        final Handle<YieldTermStructure> curve = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.03, new Actual360()));
        final Eonia eonia = new Eonia(curve);

        final Date start = new Date(17, Month.January, 2024);
        final Date end   = new Date(17, Month.January, 2025);
        final Schedule schedule = new Schedule(
                start, end, new Period(1, TimeUnit.Years),
                eonia.fixingCalendar(),
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward,
                false, new Date(), new Date());

        // First compute the fair rate at this curve
        final OvernightIndexedSwap discoveryOis = new OvernightIndexedSwap(
                VanillaSwap.Type.Payer, 1.0e6, schedule, 0.03,
                new Actual360(), eonia);
        discoveryOis.setPricingEngine(new DiscountingSwapEngine(curve));
        final double fair = discoveryOis.fairRate();

        // Now build an ATM swap at the discovered fair rate
        final OvernightIndexedSwap ois = new OvernightIndexedSwap(
                VanillaSwap.Type.Payer, 1.0e6, schedule, fair,
                new Actual360(), eonia);
        ois.setPricingEngine(new DiscountingSwapEngine(curve));

        assertNotNull(ois);
        final double npv = ois.NPV();
        // ATM (fixed=fairRate): NPV should be very small relative to notional
        assertTrue("NPV at fairRate should be near zero: npv=" + npv,
                Math.abs(npv) < 1.0);

        final double fixedNpv = ois.fixedLegNPV();
        final double overnightNpv = ois.overnightLegNPV();
        // legNPV already includes payer sign; total = sum
        assertEquals(npv, fixedNpv + overnightNpv, 1.0e-6);
    }

    @Test
    public void fairRateRoughlyMatchesFlatCurve() {
        final Date today = new Date(15, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        final double r = 0.025;
        final Handle<YieldTermStructure> curve = new Handle<YieldTermStructure>(
                new FlatForward(today, r, new Actual360()));
        final Eonia eonia = new Eonia(curve);

        final Date start = new Date(17, Month.January, 2024);
        final Date end   = new Date(17, Month.January, 2025);
        final Schedule schedule = new Schedule(
                start, end, new Period(1, TimeUnit.Years),
                eonia.fixingCalendar(),
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward,
                false, new Date(), new Date());

        final OvernightIndexedSwap ois = new OvernightIndexedSwap(
                VanillaSwap.Type.Payer, 1.0e6, schedule, r,
                new Actual360(), eonia);
        ois.setPricingEngine(new DiscountingSwapEngine(curve));

        final double fairRate = ois.fairRate();
        // Loose tolerance — Phase 5d.5 MVP, no telescopic / EOM tuning
        assertEquals("fairRate should be close to flat forward 2.5%",
                r, fairRate, 5.0e-4);
    }
}

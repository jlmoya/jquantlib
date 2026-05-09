/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

package org.jquantlib.testsuite.cashflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.OvernightIndexedCoupon;
import org.jquantlib.cashflow.OvernightLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.ibor.Eonia;
import org.jquantlib.math.Constants;
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
 * Smoke tests for {@link OvernightIndexedCoupon} and the
 * {@link OvernightLeg} builder.
 *
 * <p>Validates the basic compounding-rate path against a flat-forward
 * curve where the analytical answer is known: a flat overnight rate
 * <code>r</code> compounded over a coupon period of length <code>tau</code>
 * gives a coupon rate equal to <code>((1+r/365)^N - 1) / tau</code> where
 * <code>N</code> is the number of business days; for a flat curve this
 * collapses (under the telescopic shortcut) to
 * <code>(D_start/D_end - 1) / tau</code>.
 *
 * @author JQuantLib migration team
 */
public class OvernightIndexedCouponSmokeTest {

    @Test
    public void buildsOvernightCouponWithCompoundingPricer() {
        final Date today = new Date(15, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        // Forward curve at 3 % flat, Actual/360, continuous compounding
        final Handle<YieldTermStructure> curve = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.03, new Actual360()));
        final Eonia eonia = new Eonia(curve);

        final Date start = new Date(17, Month.January, 2024);   // T+2
        final Date end   = new Date(17, Month.April, 2024);     // 3-month accrual
        final Date payment = end;

        final OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(
                payment, 1.0e6, start, end, eonia);

        assertNotNull(coupon);
        // Coupon should have a non-trivial number of sub-periods (~64-65 BDs)
        assertTrue("expected n > 50 daily sub-periods, got " + coupon.n(),
                coupon.n() > 50);
        // Compounding pricer is set
        assertNotNull(coupon.pricer());

        // Rate should be close to 3% (flat curve, no spread)
        final double rate = coupon.rate();
        assertEquals("compounded rate should be close to flat 3 %",
                0.03, rate, 5e-4);
    }

    @Test
    public void overnightLegBuildsCorrectNumberOfCoupons() {
        final Date today = new Date(15, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        final Handle<YieldTermStructure> curve = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.025, new Actual360()));
        final Eonia eonia = new Eonia(curve);

        // 1-year semi-annual schedule
        final Date start = new Date(17, Month.January, 2024);
        final Date end   = new Date(17, Month.January, 2025);
        final Schedule schedule = new Schedule(
                start, end, new Period(6, TimeUnit.Months),
                eonia.fixingCalendar(),
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward,
                false /*EOM*/, new Date(), new Date());

        final org.jquantlib.cashflow.Leg leg = new OvernightLeg(schedule, eonia)
                .withNotionals(1.0e6)
                .withSpreads(0.0)
                .leg();

        // Two semi-annual coupons
        assertEquals(2, leg.size());

        // Each coupon should be an OvernightIndexedCoupon
        for (final org.jquantlib.cashflow.CashFlow cf : leg) {
            assertTrue(cf instanceof OvernightIndexedCoupon);
        }
    }

    @Test
    public void simpleAveragingPricerRunsAndReturnsRateNearForward() {
        final Date today = new Date(15, Month.January, 2024);
        new Settings().setEvaluationDate(today);

        final Handle<YieldTermStructure> curve = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.04, new Actual360()));
        final Eonia eonia = new Eonia(curve);

        final Date start = new Date(17, Month.January, 2024);
        final Date end   = new Date(17, Month.April, 2024);

        final OvernightIndexedCoupon coupon = new OvernightIndexedCoupon(
                end, 1.0e6, start, end, eonia,
                1.0, 0.0, new Date(), new Date(),
                eonia.dayCounter(),
                false, RateAveraging.Type.Simple,
                Constants.NULL_NATURAL, 0, false, false);

        final double rate = coupon.rate();
        // For an arithmetic-mean rate over a flat-curve coupon, expect ~4 %
        assertEquals("simple-averaged rate close to flat 4 %",
                0.04, rate, 5e-4);
    }
}

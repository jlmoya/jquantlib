/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5d.5-Bonds-b — FixedRateLeg builder additions
 (withPaymentCalendar, withPaymentLag, withExCouponPeriod).
*/
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Brazil;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Unit tests for the new {@link FixedRateLeg} builder methods added in
 * Phase 5d.5-Bonds-b: {@code withPaymentCalendar}, {@code withPaymentLag},
 * {@code withExCouponPeriod}. Mirrors C++ v1.42.1
 * {@code ql/cashflows/fixedratecoupon.cpp:154-174}.
 */
public class FixedRateLegBuilderTest {

    private static Schedule semiAnnualSchedule(final Calendar cal) {
        return new Schedule(
                new Date(1, Month.January, 2025),
                new Date(1, Month.January, 2027),
                new Period(Frequency.Semiannual),
                cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Backward,
                false);
    }

    @Test
    public void buildersReturnSelf() {
        final Schedule sched = semiAnnualSchedule(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND));
        final FixedRateLeg leg = new FixedRateLeg(sched, new Actual360());
        assertSame("withPaymentCalendar returns self", leg, leg.withPaymentCalendar(new NullCalendar()));
        assertSame("withPaymentLag returns self", leg, leg.withPaymentLag(2));
        assertSame("withExCouponPeriod(4) returns self", leg,
                leg.withExCouponPeriod(new Period(7, TimeUnit.Days), new NullCalendar(),
                        BusinessDayConvention.Following, false));
        assertSame("withExCouponPeriod(3) returns self", leg,
                leg.withExCouponPeriod(new Period(7, TimeUnit.Days), new NullCalendar(),
                        BusinessDayConvention.Following));
    }

    @Test
    public void defaultPaymentCalendarMatchesScheduleCalendar() {
        // Without calling withPaymentCalendar, the leg should use the
        // schedule's calendar for payment-date adjustment — same behaviour
        // as before the Phase 5d.5-Bonds-b change.
        final Calendar brazil = new Brazil(Brazil.Market.SETTLEMENT);
        final Schedule sched = semiAnnualSchedule(brazil);
        final Leg leg = new FixedRateLeg(sched, new Actual360())
                .withNotionals(1000.0)
                .withCouponRates(0.05)
                .Leg();
        assertNotNull(leg);
        for (int i = 0; i < leg.size(); i++) {
            final CashFlow cf = leg.get(i);
            // Payment dates must lie on Brazilian business days
            assertEquals("payment date #" + i + " is a business day in Brazil",
                    false, brazil.isHoliday(cf.date()));
        }
    }

    @Test
    public void paymentCalendarOverridesScheduleCalendar() {
        // Schedule uses Brazil; payment calendar overridden to NullCalendar.
        // With NullCalendar (every day a business day), payment dates are
        // unchanged from the schedule's date(i).
        final Schedule sched = semiAnnualSchedule(new Brazil(Brazil.Market.SETTLEMENT));
        final Leg leg = new FixedRateLeg(sched, new Actual360())
                .withNotionals(1000.0)
                .withCouponRates(0.05)
                .withPaymentCalendar(new NullCalendar())
                .Leg();
        assertNotNull(leg);
        // For each coupon, paymentDate == NullCalendar.adjust(scheduleDate)
        // which is just scheduleDate (NullCalendar treats every day as
        // business). Compare against schedule.date(i+1).
        for (int i = 0; i < leg.size(); i++) {
            final CashFlow cf = leg.get(i);
            final Date schedDate = sched.date(i + 1);
            assertEquals("paymentDate equals schedule date with NullCalendar override at i="+i,
                    schedDate, cf.date());
        }
    }

    @Test
    public void paymentLagAdvancesPaymentDate() {
        // With a 2 business day payment lag against NullCalendar, the
        // payment date should advance by exactly 2 days from the schedule
        // end-of-period.
        final Schedule sched = semiAnnualSchedule(new NullCalendar());
        final Leg leg = new FixedRateLeg(sched, new Actual360())
                .withNotionals(1000.0)
                .withCouponRates(0.05)
                .withPaymentCalendar(new NullCalendar())
                .withPaymentLag(2)
                .Leg();
        assertNotNull(leg);
        for (int i = 0; i < leg.size(); i++) {
            final CashFlow cf = leg.get(i);
            final Date expected = sched.date(i + 1).clone();
            expected.inc();
            expected.inc();
            assertEquals("paymentDate advanced by 2 days at i="+i, expected, cf.date());
        }
    }

    @Test
    public void couponNominalsAndRatesAreApplied() {
        // Sanity — all coupons paid the right rate and nominal.
        final Schedule sched = semiAnnualSchedule(new NullCalendar());
        final Leg leg = new FixedRateLeg(sched, new Actual360())
                .withNotionals(1000.0)
                .withCouponRates(0.05)
                .Leg();
        for (int i = 0; i < leg.size(); i++) {
            final CashFlow cf = leg.get(i);
            assertEquals("coupon", FixedRateCoupon.class, cf.getClass());
            final FixedRateCoupon coupon = (FixedRateCoupon) cf;
            assertEquals("nominal at i="+i, 1000.0, coupon.nominal(), 0.0);
            assertEquals("rate at i="+i, 0.05, coupon.rate(), 0.0);
        }
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5d.5-Bonds-b — IborLeg builder additions
 (withPaymentLag, withPaymentCalendar, withExCouponPeriod, inArrears()).
*/
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.Euribor;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Unit tests for the {@link IborLeg} builder methods added in
 * Phase 5d.5-Bonds-b: {@code inArrears()} (no-arg overload),
 * {@code withPaymentLag(int)}, {@code withPaymentCalendar(Calendar)},
 * {@code withExCouponPeriod(...)}.  Mirrors C++ v1.42.1
 * {@code ql/cashflows/iborcoupon.cpp}.
 */
public class IborLegBuilderTest {

    private static IborIndex makeEuribor3M() {
        new Settings().setEvaluationDate(new Date(1, Month.January, 2025));
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
                new FlatForward(new Date(1, Month.January, 2025),
                        new Handle<org.jquantlib.quotes.Quote>(new SimpleQuote(0.03)),
                        new Actual360()));
        return new Euribor(new Period(3, TimeUnit.Months), ts);
    }

    private static Schedule semiAnnualSchedule(final Calendar cal) {
        return new Schedule(
                new Date(15, Month.January, 2025),
                new Date(15, Month.January, 2027),
                new Period(Frequency.Quarterly),
                cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Backward,
                false);
    }

    @Test
    public void buildersReturnSelf() {
        final IborIndex idx = makeEuribor3M();
        final Schedule sched = semiAnnualSchedule(new Target());
        final IborLeg leg = new IborLeg(sched, idx);
        assertSame("inArrears() returns self", leg, leg.inArrears());
        assertSame("inArrears(false) returns self", leg, leg.inArrears(false));
        assertSame("withZeroPayments() returns self", leg, leg.withZeroPayments());
        assertSame("withPaymentLag returns self", leg, leg.withPaymentLag(2));
        assertSame("withPaymentCalendar returns self", leg, leg.withPaymentCalendar(new NullCalendar()));
        assertSame("withExCouponPeriod(4) returns self", leg,
                leg.withExCouponPeriod(new Period(7, TimeUnit.Days), new NullCalendar(),
                        BusinessDayConvention.Following, false));
        assertSame("withExCouponPeriod(3) returns self", leg,
                leg.withExCouponPeriod(new Period(7, TimeUnit.Days), new NullCalendar(),
                        BusinessDayConvention.Following));
    }

    @Test
    public void inArrearsNoArgDefaultsToTrue() {
        final IborIndex idx = makeEuribor3M();
        final Schedule sched = semiAnnualSchedule(new Target());
        // After inArrears(), the leg should compile without errors
        // (the boolean is consumed by FloatingLeg). Smoke-test only.
        final Leg leg = new IborLeg(sched, idx)
                .withNotionals(new Array(new double[] { 1000.0 }))
                .inArrears()
                .Leg();
        assertNotNull(leg);
        assertTrue("at least 1 coupon", leg.size() > 0);
    }

    @Test
    public void defaultPaymentBehaviourMatchesPriorImpl() {
        // Without calling withPaymentCalendar / withPaymentLag, the leg
        // should produce the same payment dates as before Phase 5d.5-Bonds-b
        // (which used schedule.calendar() with paymentLag=0). Smoke-check
        // payment dates land on TARGET business days.
        final IborIndex idx = makeEuribor3M();
        final Calendar tgt = new Target();
        final Schedule sched = semiAnnualSchedule(tgt);
        final Leg leg = new IborLeg(sched, idx)
                .withNotionals(new Array(new double[] { 1000.0 }))
                .Leg();
        assertNotNull(leg);
        for (int i = 0; i < leg.size(); i++) {
            final CashFlow cf = leg.get(i);
            assertTrue("payment date #" + i + " on TARGET business day",
                    !tgt.isHoliday(cf.date()));
        }
    }

    @Test
    public void paymentLagAdvancesPaymentDate() {
        // With paymentLag=2 and NullCalendar (every day a business day),
        // payment date == schedule.date(i+1) + 2 days.
        final IborIndex idx = makeEuribor3M();
        final Schedule sched = semiAnnualSchedule(new NullCalendar());
        final Leg leg = new IborLeg(sched, idx)
                .withNotionals(new Array(new double[] { 1000.0 }))
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
    public void paymentCalendarOverridesScheduleCalendar() {
        // Schedule on TARGET; payment calendar overridden to NullCalendar.
        // For each coupon, payment date == NullCalendar.adjust(schedule.date)
        // == schedule.date.
        final IborIndex idx = makeEuribor3M();
        final Schedule sched = semiAnnualSchedule(new Target());
        final Leg leg = new IborLeg(sched, idx)
                .withNotionals(new Array(new double[] { 1000.0 }))
                .withPaymentCalendar(new NullCalendar())
                .Leg();
        assertNotNull(leg);
        for (int i = 0; i < leg.size(); i++) {
            final CashFlow cf = leg.get(i);
            final Date schedDate = sched.date(i + 1);
            assertEquals("payment date == schedule date with NullCalendar override at i="+i,
                    schedDate, cf.date());
        }
    }
}

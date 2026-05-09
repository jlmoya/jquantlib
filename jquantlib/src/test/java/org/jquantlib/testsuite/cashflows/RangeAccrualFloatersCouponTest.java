/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Smoke tests for RangeAccrualFloatersCoupon construction (Phase 5e.7).

 The full BGM-based pricing path requires a SmileSection probe
 fixture that is not yet wired (deferred to Phase 5e.7b). These
 tests exercise the construction contracts that mirror C++
 v1.42.1 ql/cashflows/rangeaccrual.cpp:
   - lower < upper trigger guard
   - observation schedule start/end alignment
   - observationDates pop_back + erase_begin
   - startTime/endTime / observationTimes year-fraction wiring
*/
package org.jquantlib.testsuite.cashflows;

import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.RangeAccrualFloatersCoupon;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
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
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Smoke tests for {@link RangeAccrualFloatersCoupon}.
 */
public class RangeAccrualFloatersCouponTest {

    private static final double TOL = 1.0e-9;

    private static RangeAccrualFloatersCoupon makeCoupon(
            final Date startDate, final Date endDate,
            final double lower, final double upper) {
        final Date evalDate = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(evalDate);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
            new FlatForward(evalDate, 0.05, dc, Compounding.Continuous, Frequency.Annual));
        final Euribor3M idx = new Euribor3M(ts);

        // Observation schedule: monthly between start and end inclusive.
        final Schedule obsSchedule = new Schedule(
            startDate, endDate,
            new Period(1, TimeUnit.Months), cal,
            BusinessDayConvention.ModifiedFollowing,
            BusinessDayConvention.ModifiedFollowing,
            DateGeneration.Rule.Forward, false);

        return new RangeAccrualFloatersCoupon(
            endDate /* paymentDate */, 100.0 /* nominal */,
            idx, startDate, endDate,
            2 /* fixingDays */, dc,
            1.0 /* gearing */, 0.0 /* spread */,
            startDate /* refStart */, endDate /* refEnd */,
            obsSchedule, lower, upper);
    }

    /** Coupon construction exposes triggers, start/end times, and observation count. */
    @Test
    public void construction_exposesTriggersAndObservations() {
        final Date startDate = new Date(15, Month.April, 2026);
        final Date endDate = new Date(15, Month.July, 2026);
        final RangeAccrualFloatersCoupon c = makeCoupon(startDate, endDate, 0.03, 0.07);

        assertEquals(0.03, c.lowerTrigger(), TOL);
        assertEquals(0.07, c.upperTrigger(), TOL);

        // Schedule: April 15 -> May 15 -> June 15 -> July 15 (4 dates),
        // observationDates strips first + last -> 2 entries (May 15 + June 15).
        assertEquals(2, c.observationsNo());
        assertEquals(2, c.observationDates().size());
        assertEquals(2, c.observationTimes().size());

        // observationTimes are strictly between startTime and endTime.
        final double s = c.startTime();
        final double e = c.endTime();
        for (Double t : c.observationTimes()) {
            if (t.doubleValue() <= s || t.doubleValue() >= e) {
                fail("observation time " + t + " not in (start=" + s + ", end=" + e + ")");
            }
        }
        // start < end
        if (s >= e) {
            fail("startTime=" + s + " >= endTime=" + e);
        }
    }

    /** lower>=upper must be rejected. */
    @Test
    public void construction_rejectsLowerGEUpper() {
        final Date startDate = new Date(15, Month.April, 2026);
        final Date endDate = new Date(15, Month.July, 2026);
        try {
            makeCoupon(startDate, endDate, 0.07, 0.03);
            fail("expected exception when lower >= upper");
        } catch (final Exception expected) {
            // ok
        }
        try {
            makeCoupon(startDate, endDate, 0.05, 0.05);
            fail("expected exception when lower == upper");
        } catch (final Exception expected) {
            // ok
        }
    }
}

/*
 Copyright (C) 2008 Srinivas Hasti

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.time;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ScheduleTest {

    final private Date startDate;

    public ScheduleTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        this.startDate = new Date(20, Month.August, 2007);
    }


    @Test
    public void testSchedule() {
        final Calendar calendar = new Target();
        final Period maturity = new Period(30, TimeUnit.Years);
        final Date maturityDate = startDate.add(maturity);
        final Period accPeriodTenor = new Period(6, TimeUnit.Months);
        final BusinessDayConvention modFollow = BusinessDayConvention.ModifiedFollowing;
        final DateGeneration.Rule dateRule = DateGeneration.Rule.Backward;

        final Schedule firstConstrSchedule = new Schedule(
                startDate, maturityDate, accPeriodTenor,
                calendar, modFollow, modFollow,
                dateRule, false, null, null);

        final List<Date> dates = new ArrayList<Date>();
        dates.add(startDate);
        dates.add(calendar.advance(startDate, new Period(10, TimeUnit.Weeks),modFollow));

        final Schedule secondConstrSchedule = new Schedule(dates, calendar, modFollow);

        testDateAfter(firstConstrSchedule);
        testDateAfter(secondConstrSchedule);

        testNextAndPrevDate(firstConstrSchedule);
        testNextAndPrevDate(secondConstrSchedule);

        testIsRegular(firstConstrSchedule);

    }

    private void testDateAfter(final Schedule schedule) {
        Iterator<Date> dates = schedule.getDatesAfter(startDate);
        while (dates.hasNext()) {
            assertTrue(startDate.lt(dates.next()));
        }

        dates = schedule.getDatesAfter(startDate);
        while (dates.hasNext()) {
            assertTrue(startDate.lt(dates.next()));
        }

    }

    private void testNextAndPrevDate(final Schedule schedule) {
        final Date nextDate = schedule.nextDate(startDate);
        assertTrue(nextDate.ge(startDate));

        final Date prevDate = schedule.previousDate(nextDate);
        assertTrue(nextDate.gt(prevDate));

        assertTrue(prevDate.lt(nextDate));
    }

    private void testIsRegular(final Schedule schedule) {
        for (int i = 0; i < 2; i++) {
            schedule.isRegular(i+1);
        }
    }

    /**
     * Characterization test for the post-BDC dedup behavior in
     * Schedule.java's Backward and Forward date-generation loops
     * (mirrors C++ schedule.cpp:229-233 / :326-330).
     *
     * <p>Without the dedup, a 1-day tenor on a business calendar generates
     * one entry per calendar day; consecutive non-business days then
     * collapse onto the same adjusted date during post-loop BDC
     * application — leaving silent duplicates that inflate the schedule
     * by ~50%. The OvernightIndexedCoupon Schedule(start, end, 1*Day,
     * fixingCal, Following) construction is the first Java consumer of
     * this code path; the bug surfaced through Phase 5e.5b-CFC-c BlackON
     * cap/floor tests with a 6.7e-7 cap-rate drift.
     *
     * <p>Pinned scenario: SOFR calendar (Java's Sofr class uses GOVERNMENTBOND substitute), July 1 2035 (Sun) → Oct 1 2035
     * (Tue), 1-day tenor, Following BDC, Backward generation. C++ at
     * v1.42.1 produces 64 dates (every business day from July 2 to Oct 1
     * inclusive); the pre-fix Java produced 93 (every calendar day).
     */
    @Test
    public void testOneDayTenorBackwardDedupSofr() {
        final Calendar sofr = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Date start = new Date(1, Month.July, 2035);
        final Date end = new Date(1, Month.October, 2035);

        final Schedule sch = new Schedule(
                start, end, new Period(1, TimeUnit.Days),
                sofr, BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Backward, false, null, null);

        // C++ ground truth: 64 valueDates (validated via
        // black_overnight_indexed_coupon_pricer_probe at
        // migration-harness/references/cashflows/black_overnight_indexed_coupon_pricer.json).
        assertEquals("Backward 1-day-tenor schedule on SOFR calendar (Java's Sofr class uses GOVERNMENTBOND substitute) must "
                + "match C++ ground truth (64 business days from July 2 to "
                + "Oct 1, 2035 inclusive)", 64, sch.dates().size());

        // First date must be the BDC-adjusted start (July 1 Sun rolls to
        // July 2 Mon under Following).
        assertEquals(new Date(2, Month.July, 2035), sch.dates().get(0));
        // Last date is Oct 1 2035 (Tue, business day — no rolling needed).
        assertEquals(end, sch.dates().get(sch.dates().size() - 1));

        // No consecutive duplicates after BDC application.
        for (int i = 1; i < sch.dates().size(); ++i) {
            if (sch.dates().get(i).equals(sch.dates().get(i - 1))) {
                fail("Duplicate consecutive dates at index " + (i - 1)
                        + ": " + sch.dates().get(i - 1));
            }
        }
    }

    /**
     * Characterization test for the Forward-generation dedup. Symmetric
     * to {@link #testOneDayTenorBackwardDedupSofr}.
     */
    @Test
    public void testOneDayTenorForwardDedupSofr() {
        final Calendar sofr = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Date start = new Date(1, Month.July, 2035);
        final Date end = new Date(1, Month.October, 2035);

        final Schedule sch = new Schedule(
                start, end, new Period(1, TimeUnit.Days),
                sofr, BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Forward, false, null, null);

        // Forward generation must produce the same business-day count
        // as Backward for this scenario (no irregular front/back stubs).
        assertEquals(64, sch.dates().size());
        assertEquals(new Date(2, Month.July, 2035), sch.dates().get(0));
        assertEquals(end, sch.dates().get(sch.dates().size() - 1));

        for (int i = 1; i < sch.dates().size(); ++i) {
            if (sch.dates().get(i).equals(sch.dates().get(i - 1))) {
                fail("Duplicate consecutive dates at index " + (i - 1)
                        + ": " + sch.dates().get(i - 1));
            }
        }
    }

}

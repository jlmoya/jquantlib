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
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Japan;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedStates;
import org.jquantlib.time.calendars.WeekendsOnly;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    //
    // C++ test-suite/schedule.cpp v1.42.1 ports (Phase 1 certification D5-A)
    //
    // Faithful 1:1 ports of the 28 BOOST_AUTO_TEST_CASE entries from
    // migration-harness/cpp/quantlib/test-suite/schedule.cpp pinned at
    // 099987f0ca2c11c505dc4348cdb9ce01a598e1e5. Helper {@link #checkDates}
    // mirrors C++ {@code check_dates(const Schedule&, const std::vector<Date>&)}
    // at schedule.cpp:44-57.
    //

    private static void checkDates(final Schedule s, final List<Date> expected) {
        if (s.size() != expected.size()) {
            fail("expected " + expected.size() + " dates, found " + s.size()
                    + "\n  expected: " + expected + "\n  actual:   " + s.dates());
        }
        for (int i = 0; i < expected.size(); ++i) {
            if (!s.date(i).equals(expected.get(i))) {
                fail("expected " + expected.get(i) + " at index " + i + ", found " + s.date(i));
            }
        }
    }

    /** Faithful port of {@code test-suite/schedule.cpp:60} {@code BOOST_AUTO_TEST_CASE(testDailySchedule)}.
     *  Daily-frequency MakeSchedule must skip weekend dates (no duplicate Friday entries). */
    @Test
    public void testDailySchedule() {
        final Date start = new Date(17, Month.January, 2012);
        final Schedule s = new MakeSchedule()
                .from(start)
                .to(start.add(7))
                .withCalendar(new Target())
                .withFrequency(Frequency.Daily)
                .withConvention(BusinessDayConvention.Preceding)
                .schedule();

        // The schedule should skip Saturday 21st and Sunday 22nd.
        // Previously, it would adjust them to Friday 20th, resulting
        // in three copies of the same date.
        final List<Date> expected = Arrays.asList(
                new Date(17, Month.January, 2012),
                new Date(18, Month.January, 2012),
                new Date(19, Month.January, 2012),
                new Date(20, Month.January, 2012),
                new Date(23, Month.January, 2012),
                new Date(24, Month.January, 2012));
        checkDates(s, expected);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:86} {@code BOOST_AUTO_TEST_CASE(testEomAdjustment)}.
     *  Three monthly-frequency schedules with EOM and different BDCs must produce the documented date vectors. */
    @Test
    public void testEomAdjustment() {
        final Date startDate = new Date(29, Month.February, 2024);
        final Date endDate = startDate.add(new Period(1, TimeUnit.Years));

        final Schedule s1 = new MakeSchedule()
                .from(startDate).to(endDate)
                .withCalendar(new Target())
                .withFrequency(Frequency.Monthly)
                .withConvention(BusinessDayConvention.Unadjusted)
                .endOfMonth()
                .schedule();
        checkDates(s1, Arrays.asList(
                new Date(29, Month.February, 2024),
                new Date(31, Month.March, 2024),
                new Date(30, Month.April, 2024),
                new Date(31, Month.May, 2024),
                new Date(30, Month.June, 2024),
                new Date(31, Month.July, 2024),
                new Date(31, Month.August, 2024),
                new Date(30, Month.September, 2024),
                new Date(31, Month.October, 2024),
                new Date(30, Month.November, 2024),
                new Date(31, Month.December, 2024),
                new Date(31, Month.January, 2025),
                new Date(28, Month.February, 2025)));

        final Schedule s2 = new MakeSchedule()
                .from(startDate).to(endDate)
                .withCalendar(new Target())
                .withFrequency(Frequency.Monthly)
                .withConvention(BusinessDayConvention.Following)
                .endOfMonth()
                .schedule();
        checkDates(s2, Arrays.asList(
                new Date(29, Month.February, 2024),
                new Date(2, Month.April, 2024),
                new Date(30, Month.April, 2024),
                new Date(31, Month.May, 2024),
                new Date(1, Month.July, 2024),
                new Date(31, Month.July, 2024),
                new Date(2, Month.September, 2024),
                new Date(30, Month.September, 2024),
                new Date(31, Month.October, 2024),
                new Date(2, Month.December, 2024),
                new Date(31, Month.December, 2024),
                new Date(31, Month.January, 2025),
                new Date(28, Month.February, 2025)));

        final Schedule s3 = new MakeSchedule()
                .from(startDate).to(endDate)
                .withCalendar(new Target())
                .withFrequency(Frequency.Monthly)
                .withConvention(BusinessDayConvention.ModifiedPreceding)
                .endOfMonth()
                .schedule();
        checkDates(s3, Arrays.asList(
                new Date(29, Month.February, 2024),
                new Date(28, Month.March, 2024),
                new Date(30, Month.April, 2024),
                new Date(31, Month.May, 2024),
                new Date(28, Month.June, 2024),
                new Date(31, Month.July, 2024),
                new Date(30, Month.August, 2024),
                new Date(30, Month.September, 2024),
                new Date(31, Month.October, 2024),
                new Date(29, Month.November, 2024),
                new Date(31, Month.December, 2024),
                new Date(31, Month.January, 2025),
                new Date(28, Month.February, 2025)));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:163} {@code BOOST_AUTO_TEST_CASE(testEndDateWithEomAdjustment)}.
     *  EOM forward schedule against Japan calendar must produce the documented 7-date sequence ending 15-Jun-2012. */
    @Test
    public void testEndDateWithEomAdjustment() {
        final Schedule s = new MakeSchedule()
                .from(new Date(30, Month.September, 2009))
                .to(new Date(15, Month.June, 2012))
                .withCalendar(new Japan())
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.ModifiedFollowing)
                .withTerminationDateConvention(BusinessDayConvention.ModifiedFollowing)
                .forwards()
                .endOfMonth()
                .schedule();
        checkDates(s, Arrays.asList(
                new Date(30, Month.September, 2009),
                new Date(31, Month.March, 2010),
                new Date(30, Month.September, 2010),
                new Date(31, Month.March, 2011),
                new Date(30, Month.September, 2011),
                new Date(30, Month.March, 2012),
                new Date(15, Month.June, 2012)));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:189}
     *  {@code BOOST_AUTO_TEST_CASE(testDatesPastEndDateWithEomAdjustment)}.
     *  Forward Unadjusted EOM schedule must not emit dates past the end date; final period must be irregular. */
    @Test
    public void testDatesPastEndDateWithEomAdjustment() {
        final Schedule s = new MakeSchedule()
                .from(new Date(28, Month.March, 2013))
                .to(new Date(30, Month.March, 2015))
                .withCalendar(new Target())
                .withTenor(new Period(1, TimeUnit.Years))
                .withConvention(BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .endOfMonth()
                .schedule();
        checkDates(s, Arrays.asList(
                new Date(28, Month.March, 2013),
                new Date(31, Month.March, 2014),
                // March 31st 2015, coming from the EOM adjustment of March 28th,
                // should be discarded as past the end date.
                new Date(30, Month.March, 2015)));

        // also, the last period should not be regular.
        assertFalse("last period should not be regular", s.isRegular(2));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:217}
     *  {@code BOOST_AUTO_TEST_CASE(testDatesSameAsEndDateWithEomAdjustment)}.
     *  Forward Unadjusted EOM schedule must drop a next-to-last date that lands exactly on the end date; final
     *  period stays regular. */
    @Test
    public void testDatesSameAsEndDateWithEomAdjustment() {
        final Schedule s = new MakeSchedule()
                .from(new Date(28, Month.March, 2013))
                .to(new Date(31, Month.March, 2015))
                .withCalendar(new Target())
                .withTenor(new Period(1, TimeUnit.Years))
                .withConvention(BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .endOfMonth()
                .schedule();
        checkDates(s, Arrays.asList(
                new Date(28, Month.March, 2013),
                new Date(31, Month.March, 2014),
                // March 31st 2015, coming from the EOM adjustment of March 28th,
                // should be discarded as the same as the end date.
                new Date(31, Month.March, 2015)));

        // also, the last period should be regular.
        assertTrue("last period should be regular", s.isRegular(2));
    }

}

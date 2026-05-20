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
import java.util.List;

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

    /** Faithful port of {@code test-suite/schedule.cpp:245}
     *  {@code BOOST_AUTO_TEST_CASE(testForwardDatesWithEomAdjustment)}.
     *  Forward EOM schedule on USGovBond must not adjust the last date when termination convention is Unadjusted. */
    @Test
    public void testForwardDatesWithEomAdjustment() {
        final Schedule s = new MakeSchedule()
                .from(new Date(31, Month.August, 1996))
                .to(new Date(15, Month.September, 1997))
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .endOfMonth()
                .schedule();
        checkDates(s, Arrays.asList(
                new Date(31, Month.August, 1996),
                new Date(28, Month.February, 1997),
                new Date(31, Month.August, 1997),
                new Date(15, Month.September, 1997)));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:269}
     *  {@code BOOST_AUTO_TEST_CASE(testBackwardDatesWithEomAdjustment)}.
     *  Backward EOM schedule on USGovBond must not adjust the first date when termination convention is Unadjusted. */
    @Test
    public void testBackwardDatesWithEomAdjustment() {
        final Schedule s = new MakeSchedule()
                .from(new Date(22, Month.August, 1996))
                .to(new Date(31, Month.August, 1997))
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .endOfMonth()
                .schedule();
        checkDates(s, Arrays.asList(
                new Date(22, Month.August, 1996),
                new Date(31, Month.August, 1996),
                new Date(28, Month.February, 1997),
                new Date(31, Month.August, 1997)));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:293}
     *  {@code BOOST_AUTO_TEST_CASE(testDoubleFirstDateWithEomAdjustment)}.
     *  Backward EOM schedule must not duplicate the first date when ModifiedFollowing/Following BDCs are used. */
    @Test
    public void testDoubleFirstDateWithEomAdjustment() {
        final Schedule s = new MakeSchedule()
                .from(new Date(22, Month.August, 1996))
                .to(new Date(31, Month.August, 1997))
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.ModifiedFollowing)
                .withTerminationDateConvention(BusinessDayConvention.Following)
                .backwards()
                .endOfMonth()
                .schedule();
        checkDates(s, Arrays.asList(
                new Date(22, Month.August, 1996),
                new Date(30, Month.August, 1996),
                new Date(28, Month.February, 1997),
                new Date(2, Month.September, 1997)));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:317}
     *  {@code BOOST_AUTO_TEST_CASE(testFirstDateWithEomAdjustment)}.
     *  Forward EOM schedule with explicit firstDate(28-Feb-1997) on USGovBond. */
    @Test
    public void testFirstDateWithEomAdjustment() {
        final Schedule schedule = new MakeSchedule()
                .from(new Date(10, Month.August, 1996))
                .to(new Date(10, Month.August, 1998))
                .withFirstDate(new Date(28, Month.February, 1997))
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.ModifiedFollowing)
                .withTerminationDateConvention(BusinessDayConvention.ModifiedFollowing)
                .forwards()
                .endOfMonth()
                .schedule();
        checkDates(schedule, Arrays.asList(
                new Date(12, Month.August, 1996),
                new Date(28, Month.February, 1997),
                new Date(29, Month.August, 1997),
                new Date(27, Month.February, 1998),
                new Date(10, Month.August, 1998)));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:341}
     *  {@code BOOST_AUTO_TEST_CASE(testNextToLastWithEomAdjustment)}.
     *  Backward EOM schedule with explicit nextToLastDate(28-Feb-1998) on USGovBond. */
    @Test
    public void testNextToLastWithEomAdjustment() {
        final Schedule schedule = new MakeSchedule()
                .from(new Date(10, Month.August, 1996))
                .to(new Date(10, Month.August, 1998))
                .withNextToLastDate(new Date(28, Month.February, 1998))
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.ModifiedFollowing)
                .withTerminationDateConvention(BusinessDayConvention.ModifiedFollowing)
                .backwards()
                .endOfMonth()
                .schedule();
        checkDates(schedule, Arrays.asList(
                new Date(12, Month.August, 1996),
                new Date(30, Month.August, 1996),
                new Date(28, Month.February, 1997),
                new Date(29, Month.August, 1997),
                new Date(27, Month.February, 1998),
                new Date(10, Month.August, 1998)));
    }

    //
    // CDS-rule helpers — mirror C++ schedule.cpp:393-427 (namespace CdsTests).
    //

    private static Schedule makeCdsSchedule(final Date from, final Date to, final DateGeneration.Rule rule) {
        return new MakeSchedule()
                .from(from)
                .to(to)
                .withCalendar(new WeekendsOnly())
                .withTenor(new Period(3, TimeUnit.Months))
                .withConvention(BusinessDayConvention.Following)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .withRule(rule)
                .schedule();
    }

    /**
     * Mirror of C++ schedule.cpp:407-427 CdsTests::testCDSConventions: for each input (tradeDate, tenor) the expected
     * (startDate, endDate) pair must equal both cdsMaturity-derived maturity and schedule.startDate()/endDate().
     */
    private static void runCDSConventions(final List<CdsInput> inputs, final DateGeneration.Rule rule) {
        for (final CdsInput input : inputs) {
            final Date from = input.from;
            final Period tenor = input.tenor;
            final Date maturity = CreditDefaultSwap.cdsMaturity(from, tenor, rule);
            final Date expEnd = input.expEnd;
            assertEquals("cdsMaturity(" + from + ", " + tenor + ", " + rule + ")", expEnd, maturity);
            final Schedule s = makeCdsSchedule(from, maturity, rule);
            final Date expStart = input.expStart;
            assertEquals("startDate (" + from + ", " + tenor + ", " + rule + ")", expStart, s.startDate());
            assertEquals("endDate (" + from + ", " + tenor + ", " + rule + ")", expEnd, s.endDate());
        }
    }

    private static final class CdsInput {
        final Date from;
        final Period tenor;
        final Date expStart;
        final Date expEnd;
        CdsInput(final Date from, final Period tenor, final Date expStart, final Date expEnd) {
            this.from = from; this.tenor = tenor; this.expStart = expStart; this.expEnd = expEnd;
        }
    }

    /** Faithful port of {@code test-suite/schedule.cpp:430} {@code BOOST_AUTO_TEST_CASE(testCDS2015Convention)}.
     *  CDS2015 semi-annual rolling convention — tests cdsMaturity + makeCdsSchedule for trade dates 12-Dec-2016,
     *  1-Mar-2017, 20-Mar-2017 with 5Y tenor. */
    @Test
    public void testCDS2015Convention() {
        final DateGeneration.Rule rule = DateGeneration.Rule.CDS2015;
        final Period tenor = new Period(5, TimeUnit.Years);

        // From September 20th 2016 to March 19th 2017 of the next year, end date is December 20th 2021 for a 5 year CDS.
        Date tradeDate = new Date(12, Month.December, 2016);
        Date maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        Date expStart = new Date(20, Month.September, 2016);
        Date expMaturity = new Date(20, Month.December, 2021);
        assertEquals(expMaturity, maturity);
        Schedule s = makeCdsSchedule(tradeDate, maturity, rule);
        assertEquals(expStart, s.startDate());
        assertEquals(expMaturity, s.endDate());

        // 12 Dec 2016 + 5Y = 12 Dec 2021, constructor uses next allowable CDS date i.e. 20 Dec 2021 — same as above.
        maturity = tradeDate.add(tenor);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        assertEquals(expStart, s.startDate());
        assertEquals(expMaturity, s.endDate());

        // Trade date 1 Mar 2017, cdsMaturity gives the same maturity.
        tradeDate = new Date(1, Month.March, 2017);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        assertEquals(expMaturity, maturity);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expStart = new Date(20, Month.December, 2016);
        assertEquals(expStart, s.startDate());
        assertEquals(expMaturity, s.endDate());

        // 1 Mar 2017 + 5Y = 1 Mar 2022 — constructor uses 20 Mar 2022; update expected.
        maturity = tradeDate.add(tenor);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        assertEquals(expStart, s.startDate());
        expMaturity = new Date(20, Month.March, 2022);
        assertEquals(expMaturity, s.endDate());

        // From 20-Mar-2017 to 19-Sep-2017, end is 20-Jun-2022 for a 5Y CDS.
        tradeDate = new Date(20, Month.March, 2017);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        expStart = new Date(20, Month.March, 2017);
        expMaturity = new Date(20, Month.June, 2022);
        assertEquals(expMaturity, maturity);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        assertEquals(expStart, s.startDate());
        assertEquals(expMaturity, s.endDate());
    }

    /** Faithful port of {@code test-suite/schedule.cpp:487} {@code BOOST_AUTO_TEST_CASE(testCDS2015ConventionGrid)}.
     *  ISDA-spec grid of 72 (tradeDate, tenor) -> (startDate, endDate) inputs for CDS2015. */
    @Test
    public void testCDS2015ConventionGrid() {
        final Period p3M = new Period(3, TimeUnit.Months);
        final Period p6M = new Period(6, TimeUnit.Months);
        final Period p9M = new Period(9, TimeUnit.Months);
        final Period p1Y = new Period(1, TimeUnit.Years);
        final Period p5Y = new Period(5, TimeUnit.Years);
        final Period p0M = new Period(0, TimeUnit.Months);
        final List<CdsInput> inputs = new ArrayList<CdsInput>();
        // 3M tenor block
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p3M, new Date(21, Month.December, 2015), new Date(20, Month.March, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p3M, new Date(21, Month.December, 2015), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p3M, new Date(21, Month.March, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p3M, new Date(21, Month.March, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p3M, new Date(20, Month.June, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p3M, new Date(20, Month.June, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p3M, new Date(20, Month.June, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p3M, new Date(20, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p3M, new Date(20, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p3M, new Date(20, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p3M, new Date(20, Month.December, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p3M, new Date(20, Month.December, 2016), new Date(20, Month.March, 2017)));
        // 6M tenor block
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p6M, new Date(21, Month.December, 2015), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p6M, new Date(21, Month.December, 2015), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p6M, new Date(21, Month.March, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p6M, new Date(21, Month.March, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p6M, new Date(20, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p6M, new Date(20, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p6M, new Date(20, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p6M, new Date(20, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p6M, new Date(20, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p6M, new Date(20, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p6M, new Date(20, Month.December, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p6M, new Date(20, Month.December, 2016), new Date(20, Month.June, 2017)));
        // 9M tenor block
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p9M, new Date(21, Month.December, 2015), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p9M, new Date(21, Month.December, 2015), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p9M, new Date(21, Month.March, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p9M, new Date(21, Month.March, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p9M, new Date(20, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p9M, new Date(20, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p9M, new Date(20, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p9M, new Date(20, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p9M, new Date(20, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p9M, new Date(20, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p9M, new Date(20, Month.December, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p9M, new Date(20, Month.December, 2016), new Date(20, Month.September, 2017)));
        // 1Y tenor block
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p1Y, new Date(21, Month.December, 2015), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p1Y, new Date(21, Month.December, 2015), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p1Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p1Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p1Y, new Date(20, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p1Y, new Date(20, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p1Y, new Date(20, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p1Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p1Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p1Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p1Y, new Date(20, Month.December, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p1Y, new Date(20, Month.December, 2016), new Date(20, Month.December, 2017)));
        // 5Y tenor block
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p5Y, new Date(21, Month.December, 2015), new Date(20, Month.December, 2020)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p5Y, new Date(21, Month.December, 2015), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p5Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p5Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p5Y, new Date(20, Month.June, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p5Y, new Date(20, Month.June, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p5Y, new Date(20, Month.June, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p5Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p5Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p5Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p5Y, new Date(20, Month.December, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p5Y, new Date(20, Month.December, 2016), new Date(20, Month.December, 2021)));
        // 0M tenor block (subset — see C++ schedule.cpp:561-566)
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p0M, new Date(21, Month.December, 2015), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p0M, new Date(21, Month.March, 2016), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p0M, new Date(21, Month.March, 2016), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p0M, new Date(20, Month.September, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p0M, new Date(20, Month.September, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p0M, new Date(20, Month.September, 2016), new Date(20, Month.December, 2016)));
        runCDSConventions(inputs, DateGeneration.Rule.CDS2015);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:572} {@code BOOST_AUTO_TEST_CASE(testCDSConventionGrid)}.
     *  ISDA-spec grid for the pre-2015 CDS convention. */
    @Test
    public void testCDSConventionGrid() {
        final Period p3M = new Period(3, TimeUnit.Months);
        final Period p6M = new Period(6, TimeUnit.Months);
        final Period p9M = new Period(9, TimeUnit.Months);
        final Period p1Y = new Period(1, TimeUnit.Years);
        final Period p5Y = new Period(5, TimeUnit.Years);
        final Period p0M = new Period(0, TimeUnit.Months);
        final List<CdsInput> inputs = new ArrayList<CdsInput>();
        // 3M
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p3M, new Date(21, Month.December, 2015), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p3M, new Date(21, Month.December, 2015), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p3M, new Date(21, Month.March, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p3M, new Date(21, Month.March, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p3M, new Date(20, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p3M, new Date(20, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p3M, new Date(20, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p3M, new Date(20, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p3M, new Date(20, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p3M, new Date(20, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p3M, new Date(20, Month.December, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p3M, new Date(20, Month.December, 2016), new Date(20, Month.June, 2017)));
        // 6M
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p6M, new Date(21, Month.December, 2015), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p6M, new Date(21, Month.December, 2015), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p6M, new Date(21, Month.March, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p6M, new Date(21, Month.March, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p6M, new Date(20, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p6M, new Date(20, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p6M, new Date(20, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p6M, new Date(20, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p6M, new Date(20, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p6M, new Date(20, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p6M, new Date(20, Month.December, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p6M, new Date(20, Month.December, 2016), new Date(20, Month.September, 2017)));
        // 9M
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p9M, new Date(21, Month.December, 2015), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p9M, new Date(21, Month.December, 2015), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p9M, new Date(21, Month.March, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p9M, new Date(21, Month.March, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p9M, new Date(20, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p9M, new Date(20, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p9M, new Date(20, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p9M, new Date(20, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p9M, new Date(20, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p9M, new Date(20, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p9M, new Date(20, Month.December, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p9M, new Date(20, Month.December, 2016), new Date(20, Month.December, 2017)));
        // 1Y
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p1Y, new Date(21, Month.December, 2015), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p1Y, new Date(21, Month.December, 2015), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p1Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p1Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p1Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p1Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p1Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p1Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p1Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p1Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p1Y, new Date(20, Month.December, 2016), new Date(20, Month.March, 2018)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p1Y, new Date(20, Month.December, 2016), new Date(20, Month.March, 2018)));
        // 5Y
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p5Y, new Date(21, Month.December, 2015), new Date(20, Month.March, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p5Y, new Date(21, Month.December, 2015), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p5Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p5Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p5Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p5Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p5Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p5Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p5Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p5Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p5Y, new Date(20, Month.December, 2016), new Date(20, Month.March, 2022)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p5Y, new Date(20, Month.December, 2016), new Date(20, Month.March, 2022)));
        // 0M
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p0M, new Date(21, Month.December, 2015), new Date(20, Month.March, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p0M, new Date(21, Month.December, 2015), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p0M, new Date(21, Month.March, 2016), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p0M, new Date(21, Month.March, 2016), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p0M, new Date(20, Month.June, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p0M, new Date(20, Month.June, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p0M, new Date(20, Month.June, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p0M, new Date(20, Month.September, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p0M, new Date(20, Month.September, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p0M, new Date(20, Month.September, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p0M, new Date(20, Month.December, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p0M, new Date(20, Month.December, 2016), new Date(20, Month.March, 2017)));
        runCDSConventions(inputs, DateGeneration.Rule.CDS);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:663} {@code BOOST_AUTO_TEST_CASE(testOldCDSConventionGrid)}.
     *  ISDA-spec grid for the OldCDS (pre-2009) convention. */
    @Test
    public void testOldCDSConventionGrid() {
        final Period p3M = new Period(3, TimeUnit.Months);
        final Period p6M = new Period(6, TimeUnit.Months);
        final Period p9M = new Period(9, TimeUnit.Months);
        final Period p1Y = new Period(1, TimeUnit.Years);
        final Period p5Y = new Period(5, TimeUnit.Years);
        final List<CdsInput> inputs = new ArrayList<CdsInput>();
        // 3M
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p3M, new Date(19, Month.March, 2016), new Date(20, Month.June, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p3M, new Date(20, Month.March, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p3M, new Date(21, Month.March, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p3M, new Date(19, Month.June, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p3M, new Date(20, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p3M, new Date(21, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p3M, new Date(19, Month.September, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p3M, new Date(20, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p3M, new Date(21, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p3M, new Date(19, Month.December, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p3M, new Date(20, Month.December, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p3M, new Date(21, Month.December, 2016), new Date(20, Month.June, 2017)));
        // 6M
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p6M, new Date(19, Month.March, 2016), new Date(20, Month.September, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p6M, new Date(20, Month.March, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p6M, new Date(21, Month.March, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p6M, new Date(19, Month.June, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p6M, new Date(20, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p6M, new Date(21, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p6M, new Date(19, Month.September, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p6M, new Date(20, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p6M, new Date(21, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p6M, new Date(19, Month.December, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p6M, new Date(20, Month.December, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p6M, new Date(21, Month.December, 2016), new Date(20, Month.September, 2017)));
        // 9M
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p9M, new Date(19, Month.March, 2016), new Date(20, Month.December, 2016)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p9M, new Date(20, Month.March, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p9M, new Date(21, Month.March, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p9M, new Date(19, Month.June, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p9M, new Date(20, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p9M, new Date(21, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p9M, new Date(19, Month.September, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p9M, new Date(20, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p9M, new Date(21, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p9M, new Date(19, Month.December, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p9M, new Date(20, Month.December, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p9M, new Date(21, Month.December, 2016), new Date(20, Month.December, 2017)));
        // 1Y
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p1Y, new Date(19, Month.March, 2016), new Date(20, Month.March, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p1Y, new Date(20, Month.March, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p1Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p1Y, new Date(19, Month.June, 2016), new Date(20, Month.June, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p1Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p1Y, new Date(21, Month.June, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p1Y, new Date(19, Month.September, 2016), new Date(20, Month.September, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p1Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p1Y, new Date(21, Month.September, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p1Y, new Date(19, Month.December, 2016), new Date(20, Month.December, 2017)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p1Y, new Date(20, Month.December, 2016), new Date(20, Month.March, 2018)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p1Y, new Date(21, Month.December, 2016), new Date(20, Month.March, 2018)));
        // 5Y
        inputs.add(new CdsInput(new Date(19, Month.March, 2016), p5Y, new Date(19, Month.March, 2016), new Date(20, Month.March, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.March, 2016), p5Y, new Date(20, Month.March, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.March, 2016), p5Y, new Date(21, Month.March, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.June, 2016), p5Y, new Date(19, Month.June, 2016), new Date(20, Month.June, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.June, 2016), p5Y, new Date(20, Month.June, 2016), new Date(20, Month.September, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.June, 2016), p5Y, new Date(21, Month.June, 2016), new Date(20, Month.September, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.September, 2016), p5Y, new Date(19, Month.September, 2016), new Date(20, Month.September, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.September, 2016), p5Y, new Date(20, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(21, Month.September, 2016), p5Y, new Date(21, Month.September, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(19, Month.December, 2016), p5Y, new Date(19, Month.December, 2016), new Date(20, Month.December, 2021)));
        inputs.add(new CdsInput(new Date(20, Month.December, 2016), p5Y, new Date(20, Month.December, 2016), new Date(20, Month.March, 2022)));
        inputs.add(new CdsInput(new Date(21, Month.December, 2016), p5Y, new Date(21, Month.December, 2016), new Date(20, Month.March, 2022)));
        runCDSConventions(inputs, DateGeneration.Rule.OldCDS);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:742}
     *  {@code BOOST_AUTO_TEST_CASE(testCDS2015ConventionSampleDates)}.
     *  All dates in two sample CDS2015 schedules — across trade dates straddling the 20-Sep-2015 roll. */
    @Test
    public void testCDS2015ConventionSampleDates() {
        final DateGeneration.Rule rule = DateGeneration.Rule.CDS2015;
        final Period tenor = new Period(1, TimeUnit.Years);

        // trade date = Fri 18 Sep 2015.
        Date tradeDate = new Date(18, Month.September, 2015);
        Date maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        Schedule s = makeCdsSchedule(tradeDate, maturity, rule);
        List<Date> expDates = new ArrayList<Date>(Arrays.asList(
                new Date(22, Month.June, 2015), new Date(21, Month.September, 2015),
                new Date(21, Month.December, 2015), new Date(21, Month.March, 2016),
                new Date(20, Month.June, 2016)));
        checkDates(s, expDates);

        // trade date = Sat 19 Sep 2015, no change.
        tradeDate = new Date(19, Month.September, 2015);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        checkDates(s, expDates);

        // trade date = Sun 20 Sep 2015. Roll to new maturity. Trade date still before next coupon payment
        // date of Mon 21 Sep 2015, so keep the first period from 22 Jun 2015 to 21 Sep 2015 in schedule.
        tradeDate = new Date(20, Month.September, 2015);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates.add(new Date(20, Month.September, 2016));
        expDates.add(new Date(20, Month.December, 2016));
        checkDates(s, expDates);

        // trade date = Mon 21 Sep 2015, first period drops out of schedule.
        tradeDate = new Date(21, Month.September, 2015);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates.remove(0);
        checkDates(s, expDates);

        // Another sample trade date, Sat 20 Jun 2009.
        tradeDate = new Date(20, Month.June, 2009);
        maturity = new Date(20, Month.December, 2009);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates = new ArrayList<Date>(Arrays.asList(
                new Date(20, Month.March, 2009), new Date(22, Month.June, 2009),
                new Date(21, Month.September, 2009), new Date(20, Month.December, 2009)));
        checkDates(s, expDates);

        // Move forward to Sun 21 Jun 2009
        tradeDate = new Date(21, Month.June, 2009);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        checkDates(s, expDates);

        // Move forward to Mon 22 Jun 2009
        tradeDate = new Date(22, Month.June, 2009);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates.remove(0);
        checkDates(s, expDates);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:805}
     *  {@code BOOST_AUTO_TEST_CASE(testCDSConventionSampleDates)}.
     *  All dates in two sample CDS schedules — across trade dates straddling the 20-Sep-2015 roll. */
    @Test
    public void testCDSConventionSampleDates() {
        final DateGeneration.Rule rule = DateGeneration.Rule.CDS;
        final Period tenor = new Period(1, TimeUnit.Years);

        // trade date = Fri 18 Sep 2015.
        Date tradeDate = new Date(18, Month.September, 2015);
        Date maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        Schedule s = makeCdsSchedule(tradeDate, maturity, rule);
        List<Date> expDates = new ArrayList<Date>(Arrays.asList(
                new Date(22, Month.June, 2015), new Date(21, Month.September, 2015),
                new Date(21, Month.December, 2015), new Date(21, Month.March, 2016),
                new Date(20, Month.June, 2016), new Date(20, Month.September, 2016)));
        checkDates(s, expDates);

        // trade date = Sat 19 Sep 2015, no change.
        tradeDate = new Date(19, Month.September, 2015);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        checkDates(s, expDates);

        // trade date = Sun 20 Sep 2015. Roll to new maturity. Trade date still before next coupon payment
        // date of Mon 21 Sep 2015, so keep the first period from 22 Jun 2015 to 21 Sep 2015 in schedule.
        tradeDate = new Date(20, Month.September, 2015);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates.add(new Date(20, Month.December, 2016));
        checkDates(s, expDates);

        // trade date = Mon 21 Sep 2015, first period drops out of schedule.
        tradeDate = new Date(21, Month.September, 2015);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDate, tenor, rule);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates.remove(0);
        checkDates(s, expDates);

        // Another sample trade date, Sat 20 Jun 2009.
        tradeDate = new Date(20, Month.June, 2009);
        maturity = new Date(20, Month.December, 2009);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates = new ArrayList<Date>(Arrays.asList(
                new Date(20, Month.March, 2009), new Date(22, Month.June, 2009),
                new Date(21, Month.September, 2009), new Date(20, Month.December, 2009)));
        checkDates(s, expDates);

        // Move forward to Sun 21 Jun 2009
        tradeDate = new Date(21, Month.June, 2009);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        checkDates(s, expDates);

        // Move forward to Mon 22 Jun 2009
        tradeDate = new Date(22, Month.June, 2009);
        s = makeCdsSchedule(tradeDate, maturity, rule);
        expDates.remove(0);
        checkDates(s, expDates);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:865}
     *  {@code BOOST_AUTO_TEST_CASE(testOldCDSConventionSampleDates)}.
     *  All dates in sample OldCDS schedules — including the 30-day stub rule near a coupon payment date. */
    @Test
    public void testOldCDSConventionSampleDates() {
        final DateGeneration.Rule rule = DateGeneration.Rule.OldCDS;
        final Period tenor = new Period(1, TimeUnit.Years);

        // trade date plus 1D = Fri 18 Sep 2015.
        Date tradeDatePlusOne = new Date(18, Month.September, 2015);
        Date maturity = CreditDefaultSwap.cdsMaturity(tradeDatePlusOne, tenor, rule);
        Schedule s = makeCdsSchedule(tradeDatePlusOne, maturity, rule);
        List<Date> expDates = new ArrayList<Date>(Arrays.asList(
                new Date(18, Month.September, 2015), new Date(21, Month.December, 2015),
                new Date(21, Month.March, 2016), new Date(20, Month.June, 2016),
                new Date(20, Month.September, 2016)));
        checkDates(s, expDates);

        // trade date plus 1D = Sat 19 Sep 2015, no change.
        // OldCDS, schedule start date is not adjusted (kept this).
        tradeDatePlusOne = new Date(19, Month.September, 2015);
        expDates.set(0, tradeDatePlusOne);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDatePlusOne, tenor, rule);
        s = makeCdsSchedule(tradeDatePlusOne, maturity, rule);
        checkDates(s, expDates);

        // trade date plus 1D = Sun 20 Sep 2015, roll.
        tradeDatePlusOne = new Date(20, Month.September, 2015);
        expDates.set(0, tradeDatePlusOne);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDatePlusOne, tenor, rule);
        s = makeCdsSchedule(tradeDatePlusOne, maturity, rule);
        expDates.add(new Date(20, Month.December, 2016));
        checkDates(s, expDates);

        // trade date plus 1D = Mon 21 Sep 2015, no change.
        tradeDatePlusOne = new Date(21, Month.September, 2015);
        expDates.set(0, tradeDatePlusOne);
        maturity = CreditDefaultSwap.cdsMaturity(tradeDatePlusOne, tenor, rule);
        s = makeCdsSchedule(tradeDatePlusOne, maturity, rule);
        checkDates(s, expDates);

        // 30 day stub rule: 19 Nov 2015 + 30D = 19 Dec 2015 <= 20 Dec 2015 => short front stub.
        tradeDatePlusOne = new Date(19, Month.November, 2015);
        expDates.set(0, tradeDatePlusOne);
        s = makeCdsSchedule(tradeDatePlusOne, maturity, rule);
        checkDates(s, expDates);

        // 20 Nov 2015 + 30D = 20 Dec 2015 <= 20 Dec 2015 => short front stub.
        tradeDatePlusOne = new Date(20, Month.November, 2015);
        expDates.set(0, tradeDatePlusOne);
        s = makeCdsSchedule(tradeDatePlusOne, maturity, rule);
        checkDates(s, expDates);

        // 21 Nov 2015 + 30D = 21 Dec 2015 > 20 Dec 2015 => long front stub.
        tradeDatePlusOne = new Date(21, Month.November, 2015);
        expDates.set(0, tradeDatePlusOne);
        s = makeCdsSchedule(tradeDatePlusOne, maturity, rule);
        expDates.remove(1);
        checkDates(s, expDates);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:366}
     *  {@code BOOST_AUTO_TEST_CASE(testEffectiveDateWithEomAdjustment)}.
     *  Forward EOM schedule must not move the effective date to end-of-month when effective and first dates are in
     *  the same month. */
    @Test
    public void testEffectiveDateWithEomAdjustment() {
        final Schedule s = new MakeSchedule()
                .from(new Date(16, Month.January, 2023))
                .to(new Date(16, Month.March, 2023))
                .withFirstDate(new Date(31, Month.January, 2023))
                .withCalendar(new NullCalendar())
                .withTenor(new Period(1, TimeUnit.Months))
                .withConvention(BusinessDayConvention.Unadjusted)
                .withTerminationDateConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .endOfMonth()
                .schedule();
        // check that the effective date is not moved at the end of the month
        checkDates(s, Arrays.asList(
                new Date(16, Month.January, 2023),
                new Date(31, Month.January, 2023),
                new Date(28, Month.February, 2023),
                new Date(16, Month.March, 2023)));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:927}
     *  {@code BOOST_AUTO_TEST_CASE(testCDS2015ZeroMonthsMatured)}.
     *  A 0M-tenor CDS2015 must return a null Date() for all sampled trade dates from 20-Dec-2015 through 20-Dec-2016
     *  (i.e. the CDS is matured). */
    @Test
    public void testCDS2015ZeroMonthsMatured() {
        final DateGeneration.Rule rule = DateGeneration.Rule.CDS2015;
        final Period tenor = new Period(0, TimeUnit.Months);
        final List<Date> inputs = Arrays.asList(
                new Date(20, Month.December, 2015),
                new Date(15, Month.February, 2016),
                new Date(19, Month.March, 2016),
                new Date(20, Month.June, 2016),
                new Date(15, Month.August, 2016),
                new Date(19, Month.September, 2016),
                new Date(20, Month.December, 2016));
        for (final Date input : inputs) {
            assertEquals("cdsMaturity for matured 0M CDS at " + input, new Date(),
                    CreditDefaultSwap.cdsMaturity(input, tenor, rule));
        }
    }

    /** Faithful port of {@code test-suite/schedule.cpp:950} {@code BOOST_AUTO_TEST_CASE(testDateConstructor)}.
     *  Schedule(vector<Date>) + Schedule(vector<Date>, cal, conv, termConv, tenor, rule, eom, isRegular) constructors. */
    @Test
    public void testDateConstructor() {
        final List<Date> dates = Arrays.asList(
                new Date(16, Month.May, 2015),
                new Date(18, Month.May, 2015),
                new Date(18, Month.May, 2016),
                new Date(31, Month.December, 2017));

        // schedule without any additional information
        final Schedule schedule1 = new Schedule(dates);
        assertEquals("schedule1 size", dates.size(), schedule1.size());
        for (int i = 0; i < dates.size(); ++i) {
            assertEquals("schedule1 at " + i, dates.get(i), schedule1.date(i));
        }
        assertEquals("schedule1 calendar should be NullCalendar", new NullCalendar().name(),
                schedule1.calendar().name());
        assertEquals("schedule1 convention should be Unadjusted",
                BusinessDayConvention.Unadjusted, schedule1.businessDayConvention());

        // schedule with metadata
        final List<Boolean> regular = Arrays.asList(Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
        final Schedule schedule2 = new Schedule(dates, new Target(),
                BusinessDayConvention.Following, BusinessDayConvention.ModifiedPreceding,
                new Period(1, TimeUnit.Years), DateGeneration.Rule.Backward, true, regular);
        for (int i = 1; i < dates.size(); ++i) {
            assertEquals("schedule2 isRegular at " + i, regular.get(i - 1).booleanValue(), schedule2.isRegular(i));
        }
        assertEquals("schedule2 calendar should be TARGET", new Target().name(), schedule2.calendar().name());
        assertEquals("schedule2 convention should be Following",
                BusinessDayConvention.Following, schedule2.businessDayConvention());
        assertEquals("schedule2 termination convention should be ModifiedPreceding",
                BusinessDayConvention.ModifiedPreceding, schedule2.terminationDateBusinessDayConvention());
        assertEquals("schedule2 tenor should be 1Y",
                new Period(1, TimeUnit.Years), schedule2.tenor());
        assertEquals("schedule2 rule should be Backward",
                DateGeneration.Rule.Backward, schedule2.rule());
        assertTrue("schedule2 end of month flag should be true", schedule2.endOfMonth());
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1007} {@code BOOST_AUTO_TEST_CASE(testFourWeeksTenor)}.
     *  A 4-week tenor must construct without throwing. */
    @Test
    public void testFourWeeksTenor() {
        try {
            new MakeSchedule()
                    .from(new Date(13, Month.January, 2016))
                    .to(new Date(4, Month.May, 2016))
                    .withCalendar(new Target())
                    .withTenor(new Period(4, TimeUnit.Weeks))
                    .withConvention(BusinessDayConvention.Following)
                    .forwards()
                    .schedule();
        } catch (final Exception e) {
            fail("A four-weeks tenor caused an exception: " + e.getMessage());
        }
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1024} {@code BOOST_AUTO_TEST_CASE(testOnceFrequency)}.
     *  A {@link Frequency#Once}-frequency schedule must contain exactly the effective and termination dates. */
    @Test
    public void testOnceFrequency() {
        final Schedule s = new MakeSchedule()
                .from(new Date(13, Month.January, 2016))
                .to(new Date(13, Month.January, 2019))
                .withFrequency(Frequency.Once)
                .forwards()
                .schedule();
        assertEquals("Once-frequency schedule size", 2, s.size());
        assertEquals(new Date(13, Month.January, 2016), s.date(0));
        assertEquals(new Date(13, Month.January, 2019), s.date(1));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1039} {@code BOOST_AUTO_TEST_CASE(testScheduleAlwaysHasAStartDate)}.
     *  MakeSchedule variations (with/without firstDate; effective on/off the grid) must always seat the effective date
     *  as the schedule's first element. */
    @Test
    public void testScheduleAlwaysHasAStartDate() {
        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        Schedule schedule = new MakeSchedule()
                .from(new Date(10, Month.January, 2017))
                .withFirstDate(new Date(31, Month.August, 2017))
                .to(new Date(28, Month.February, 2026))
                .withFrequency(Frequency.Semiannual)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards().endOfMonth(false)
                .schedule();
        assertEquals("The first element should always be the start date",
                new Date(10, Month.January, 2017), schedule.date(0));

        schedule = new MakeSchedule()
                .from(new Date(10, Month.January, 2017))
                .to(new Date(28, Month.February, 2026))
                .withFrequency(Frequency.Semiannual)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards().endOfMonth(false)
                .schedule();
        assertEquals("The first element should always be the start date",
                new Date(10, Month.January, 2017), schedule.date(0));

        schedule = new MakeSchedule()
                .from(new Date(31, Month.August, 2017))
                .to(new Date(28, Month.February, 2026))
                .withFrequency(Frequency.Semiannual)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards().endOfMonth(false)
                .schedule();
        assertEquals("The first element should always be the start date",
                new Date(31, Month.August, 2017), schedule.date(0));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1075} {@code BOOST_AUTO_TEST_CASE(testShortEomSchedule)}.
     *  A 1-week short EOM schedule must construct without throwing (seg-faulted in 1.15) and emit exactly the two
     *  endpoint dates. */
    @Test
    public void testShortEomSchedule() {
        Schedule s = null;
        try {
            s = new MakeSchedule()
                    .from(new Date(21, Month.February, 2019))
                    .to(new Date(28, Month.February, 2019))
                    .withCalendar(new Target())
                    .withTenor(new Period(1, TimeUnit.Years))
                    .withConvention(BusinessDayConvention.ModifiedFollowing)
                    .withTerminationDateConvention(BusinessDayConvention.ModifiedFollowing)
                    .backwards()
                    .endOfMonth(true)
                    .schedule();
        } catch (final Exception e) {
            fail("Short EOM schedule must not throw: " + e.getMessage());
        }
        assertEquals("Short EOM schedule must have 2 dates", 2, s.size());
        assertEquals(new Date(21, Month.February, 2019), s.date(0));
        assertEquals(new Date(28, Month.February, 2019), s.date(1));
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1093} {@code BOOST_AUTO_TEST_CASE(testFirstDateOnMaturity)}.
     *  A Quarterly schedule whose firstDate equals the maturity must produce a 2-date schedule under both Backward
     *  and Forward generation. */
    @Test
    public void testFirstDateOnMaturity() {
        Schedule schedule = new MakeSchedule()
                .from(new Date(20, Month.September, 2016))
                .to(new Date(20, Month.December, 2016))
                .withFirstDate(new Date(20, Month.December, 2016))
                .withFrequency(Frequency.Quarterly)
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .schedule();
        final List<Date> expected = Arrays.asList(
                new Date(20, Month.September, 2016),
                new Date(20, Month.December, 2016));
        checkDates(schedule, expected);

        schedule = new MakeSchedule()
                .from(new Date(20, Month.September, 2016))
                .to(new Date(20, Month.December, 2016))
                .withFirstDate(new Date(20, Month.December, 2016))
                .withFrequency(Frequency.Quarterly)
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withConvention(BusinessDayConvention.Unadjusted)
                .forwards()
                .schedule();
        checkDates(schedule, expected);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1122} {@code BOOST_AUTO_TEST_CASE(testNextToLastDateOnStart)}.
     *  A Quarterly schedule whose nextToLastDate equals the effective date must produce a 2-date schedule under
     *  Backward generation. */
    @Test
    public void testNextToLastDateOnStart() {
        Schedule schedule = new MakeSchedule()
                .from(new Date(20, Month.September, 2016))
                .to(new Date(20, Month.December, 2016))
                .withNextToLastDate(new Date(20, Month.September, 2016))
                .withFrequency(Frequency.Quarterly)
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .schedule();
        final List<Date> expected = Arrays.asList(
                new Date(20, Month.September, 2016),
                new Date(20, Month.December, 2016));
        checkDates(schedule, expected);

        schedule = new MakeSchedule()
                .from(new Date(20, Month.September, 2016))
                .to(new Date(20, Month.December, 2016))
                .withNextToLastDate(new Date(20, Month.September, 2016))
                .withFrequency(Frequency.Quarterly)
                .withCalendar(new UnitedStates(UnitedStates.Market.GOVERNMENTBOND))
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .schedule();
        checkDates(schedule, expected);
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1151} {@code BOOST_AUTO_TEST_CASE(testTruncation)}.
     *  Schedule.until(Date) and Schedule.after(Date) — verifies date-vector trimming + final/leading period
     *  isRegular_ flag updates against the Japan-calendar 30-Sep-2009 -> 15-Jun-2020 forward EOM 6M schedule. */
    @Test
    public void testTruncation() {
        final Schedule s = new MakeSchedule()
                .from(new Date(30, Month.September, 2009))
                .to(new Date(15, Month.June, 2020))
                .withCalendar(new Japan())
                .withTenor(new Period(6, TimeUnit.Months))
                .withConvention(BusinessDayConvention.ModifiedFollowing)
                .withTerminationDateConvention(BusinessDayConvention.ModifiedFollowing)
                .forwards()
                .endOfMonth()
                .schedule();

        // Until
        Schedule t = s.until(new Date(1, Month.January, 2014));
        List<Date> expected = Arrays.asList(
                new Date(30, Month.September, 2009),
                new Date(31, Month.March, 2010),
                new Date(30, Month.September, 2010),
                new Date(31, Month.March, 2011),
                new Date(30, Month.September, 2011),
                new Date(30, Month.March, 2012),
                new Date(28, Month.September, 2012),
                new Date(29, Month.March, 2013),
                new Date(30, Month.September, 2013),
                new Date(1, Month.January, 2014));
        checkDates(t, expected);
        assertFalse("until: last isRegular should be false",
                t.isRegular().get(t.isRegular().size() - 1).booleanValue());

        // Until, with truncation date falling on a schedule date
        t = s.until(new Date(30, Month.September, 2013));
        expected = Arrays.asList(
                new Date(30, Month.September, 2009),
                new Date(31, Month.March, 2010),
                new Date(30, Month.September, 2010),
                new Date(31, Month.March, 2011),
                new Date(30, Month.September, 2011),
                new Date(30, Month.March, 2012),
                new Date(28, Month.September, 2012),
                new Date(29, Month.March, 2013),
                new Date(30, Month.September, 2013));
        checkDates(t, expected);
        assertTrue("until on-grid: last isRegular should be true",
                t.isRegular().get(t.isRegular().size() - 1).booleanValue());

        // After
        t = s.after(new Date(1, Month.January, 2014));
        expected = Arrays.asList(
                new Date(1, Month.January, 2014),
                new Date(31, Month.March, 2014),
                new Date(30, Month.September, 2014),
                new Date(31, Month.March, 2015),
                new Date(30, Month.September, 2015),
                new Date(31, Month.March, 2016),
                new Date(30, Month.September, 2016),
                new Date(31, Month.March, 2017),
                new Date(29, Month.September, 2017),
                new Date(30, Month.March, 2018),
                new Date(28, Month.September, 2018),
                new Date(29, Month.March, 2019),
                new Date(30, Month.September, 2019),
                new Date(31, Month.March, 2020),
                new Date(15, Month.June, 2020));
        checkDates(t, expected);
        assertFalse("after: first isRegular should be false",
                t.isRegular().get(0).booleanValue());

        // After, with truncation date falling on a schedule date
        t = s.after(new Date(28, Month.September, 2018));
        expected = Arrays.asList(
                new Date(28, Month.September, 2018),
                new Date(29, Month.March, 2019),
                new Date(30, Month.September, 2019),
                new Date(31, Month.March, 2020),
                new Date(15, Month.June, 2020));
        checkDates(t, expected);
        assertTrue("after on-grid: first isRegular should be true",
                t.isRegular().get(0).booleanValue());
    }

    /** Faithful port of {@code test-suite/schedule.cpp:1229}
     *  {@code BOOST_AUTO_TEST_CASE(testBackwardRegularFirstPeriodWithFirstDate)}.
     *  Reproduces issue #405: Backward generation with firstDate + endOfMonth incorrectly marked the first period
     *  irregular even when effectiveDate + tenor == firstDate. */
    @Test
    public void testBackwardRegularFirstPeriodWithFirstDate() {
        final NullCalendar calendar = new NullCalendar();

        // Regular first period: Sep 30 + 6M = Mar 31 (with endOfMonth)
        final Schedule backward = new Schedule(
                new Date(30, Month.September, 2017),
                new Date(30, Month.September, 2024),
                new Period(Frequency.Semiannual),
                calendar, BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, true,
                new Date(31, Month.March, 2018), new Date());
        assertTrue("First period should be regular (effectiveDate + 6M == firstDate)", backward.isRegular(1));

        // Forward generation should agree
        final Schedule forward = new Schedule(
                new Date(30, Month.September, 2017),
                new Date(30, Month.September, 2024),
                new Period(Frequency.Semiannual),
                calendar, BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, true,
                new Date(31, Month.March, 2018), new Date());
        assertTrue("Forward first period should also be regular", forward.isRegular(1));

        // Genuinely irregular first period: effective date does NOT align with firstDate by one tenor
        final Schedule irregular = new Schedule(
                new Date(3, Month.September, 2017),
                new Date(30, Month.September, 2024),
                new Period(Frequency.Semiannual),
                calendar, BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Backward, true,
                new Date(31, Month.March, 2018), new Date());
        assertFalse("First period should be irregular (effectiveDate + 6M != firstDate)", irregular.isRegular(1));

        // Forward nextToLastDate: off-grid nextToLastDate triggers the insertion path and should be marked irregular
        final Schedule forwardNTLIrreg = new Schedule(
                new Date(30, Month.September, 2017),
                new Date(30, Month.September, 2024),
                new Period(Frequency.Semiannual),
                calendar, BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, true,
                new Date(), new Date(15, Month.March, 2024));
        final int n = forwardNTLIrreg.size();
        assertFalse("Period ending at off-grid nextToLastDate should be irregular", forwardNTLIrreg.isRegular(n - 2));
    }

}

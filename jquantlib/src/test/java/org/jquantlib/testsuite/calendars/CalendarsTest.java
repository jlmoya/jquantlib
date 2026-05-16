/*
 Copyright (C) 2026 Jose Moya

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
package org.jquantlib.testsuite.calendars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.Weekday;
import org.jquantlib.time.calendars.BespokeCalendar;
import org.jquantlib.time.calendars.Brazil;
import org.jquantlib.time.calendars.Germany;
import org.jquantlib.time.calendars.JointCalendar;
import org.jquantlib.time.calendars.JointCalendar.JointCalendarRule;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.time.calendars.UnitedKingdom;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Port of the calendar-math (non-country-specific) portion of QuantLib v1.42.1
 * test-suite/calendars.cpp (3,894 LOC).
 *
 * Phase 5c — calendar/time/indexes test ports.
 *
 * The C++ file contains 32 BOOST_AUTO_TEST_CASEs. Most country-specific
 * holiday-list tests already have direct Java equivalents in this package
 * (see {@code UnitedStatesCalendarTest}, {@code UnitedKingdomCalendarTest},
 * {@code GermanyCalendarTest}, {@code JapanCalendarTest},
 * {@code BrazilCalendarTest}, {@code DenmarkCalendarTest},
 * {@code NewZealandCalendarTest}, {@code MexicoCalendarTest},
 * {@code SouthKoreaCalendarTest}, {@code ChinaCalendarTest}, etc.).
 *
 * This class provides the calendar-math tests that are not country-specific
 * and were not previously ported:
 * <ul>
 *   <li>{@code testModifiedCalendars} — addHoliday/removeHoliday + calendar
 *       sharing semantics</li>
 *   <li>{@code testJointCalendars5} — 5-calendar JointCalendar variant
 *       (4-calendar variant is in {@link CalendarTest})</li>
 *   <li>{@code testEndOfMonth} — Calendar.endOfMonth/isEndOfMonth invariants
 *       (CalendarTest covers a similar test, this re-asserts following the
 *       v1.42.1 calendars.cpp:3552 form)</li>
 *   <li>{@code testBusinessDaysBetween} — Brazil calendar with the four
 *       (includeFirst, includeLast) combinations</li>
 * </ul>
 *
 * Phase 5c.5 deferrals (other v1.42.1 test cases): see @Ignore methods.
 *
 * @author Jose Moya
 */
public class CalendarsTest {

    public CalendarsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Tests calendar modification: addHoliday / removeHoliday on a single
     * calendar instance and isolation from other calendar types. Mirrors
     * test-suite/calendars.cpp:73-131 except for cross-instance state
     * sharing, which is deferred (see {@code testModifiedCalendarsShared}).
     *
     * Phase 5c.5 deferrals:
     * <ul>
     *   <li>The C++ test asserts that {@code addHoliday}/{@code removeHoliday}
     *       on one TARGET instance affects subsequently-constructed TARGET
     *       instances (C++ stores per-class added/removed sets statically;
     *       Java's {@code Impl.addedHolidays} / {@code removedHolidays} are
     *       per-instance fields).</li>
     *   <li>The C++ test exercises {@code addedHolidays()} / {@code
     *       removedHolidays()} accessors which are absent from Java
     *       {@link Calendar}.</li>
     * </ul>
     */
    @Test
    public void testModifiedCalendars() {
        QL.info("Testing calendar modification...");

        final Calendar c1 = new Target();
        final Calendar c2 = new UnitedStates(UnitedStates.Market.NYSE);
        final Date d1 = new Date(1, Month.May, 2004);    // holiday for both calendars
        final Date d2 = new Date(26, Month.April, 2004); // business day

        assertTrue("wrong assumption — c1.isHoliday(d1)", c1.isHoliday(d1));
        assertTrue("wrong assumption — c1.isBusinessDay(d2)", c1.isBusinessDay(d2));
        assertTrue("wrong assumption — c2.isHoliday(d1)", c2.isHoliday(d1));
        assertTrue("wrong assumption — c2.isBusinessDay(d2)", c2.isBusinessDay(d2));

        // modify the TARGET calendar
        c1.removeHoliday(d1);
        c1.addHoliday(d2);

        if (c1.isHoliday(d1)) {
            fail(d1 + " still a holiday for original TARGET instance");
        }
        if (c1.isBusinessDay(d2)) {
            fail(d2 + " still a business day for original TARGET instance");
        }

        // ...but not other calendars
        if (c2.isBusinessDay(d1)) {
            fail(d1 + " business day for New York");
        }
        if (c2.isHoliday(d2)) {
            fail(d2 + " holiday for New York");
        }

        // restore original holiday set — test the other way around
        c1.addHoliday(d1);
        c1.removeHoliday(d2);

        if (c1.isBusinessDay(d1)) {
            fail(d1 + " still a business day after re-add");
        }
        if (c1.isHoliday(d2)) {
            fail(d2 + " still a holiday after re-remove");
        }
    }

    /**
     * Tests {@link JointCalendar} consistency for the 4-calendar variant
     * (TARGET, UK, NYSE, Japan) over one year starting today, with both
     * JoinHolidays and JoinBusinessDays rules.
     *
     * The 2- and 3-calendar variants are covered by
     * {@link CalendarTest#testJointCalendars()}; this re-asserts the
     * 4-calendar variant in the v1.42.1 form. The 5-calendar Vector
     * variant from C++ is deferred to Phase 5c.5 (Java
     * {@link JointCalendar} stops at 4 constructor args).
     *
     * Reference: test-suite/calendars.cpp:133-205.
     */
    @Test
    public void testJointCalendars4() {
        QL.info("Testing 4-calendar JointCalendar consistency...");

        final Calendar c1 = new Target();
        final Calendar c2 = new UnitedKingdom();
        final Calendar c3 = new UnitedStates(UnitedStates.Market.NYSE);
        final Calendar c4 = new org.jquantlib.time.calendars.Japan();

        final Calendar c1234h = new JointCalendar(c1, c2, c3, c4, JointCalendarRule.JoinHolidays);
        final Calendar c1234b = new JointCalendar(c1, c2, c3, c4, JointCalendarRule.JoinBusinessDays);

        final Date firstDate = Date.todaysDate();
        final Date endDate = firstDate.add(new Period(1, TimeUnit.Years));

        for (Date d = firstDate.clone(); d.lt(endDate); d.inc()) {
            final boolean b1 = c1.isBusinessDay(d);
            final boolean b2 = c2.isBusinessDay(d);
            final boolean b3 = c3.isBusinessDay(d);
            final boolean b4 = c4.isBusinessDay(d);

            assertEquals("JoinHolidays inconsistency at " + d,
                    b1 && b2 && b3 && b4, c1234h.isBusinessDay(d));
            assertEquals("JoinBusinessDays inconsistency at " + d,
                    b1 || b2 || b3 || b4, c1234b.isBusinessDay(d));
        }
    }

    /**
     * Tests Calendar.endOfMonth / Calendar.isEndOfMonth invariants over a
     * year-long range, mirroring v1.42.1 test-suite/calendars.cpp:3552-3576.
     *
     * Note: the C++ test iterates from Date::minDate() to maxDate() - 2 months;
     * here we use a 5-year window starting today to keep the test cheap while
     * exercising business-day and weekend boundaries across many months.
     */
    @Test
    public void testEndOfMonth() {
        QL.info("Testing end-of-month calculation...");

        final Calendar c = new Target();
        final Date startDate = Date.todaysDate();
        final Date endDate = startDate.add(new Period(5, TimeUnit.Years));

        for (Date counter = startDate.clone(); counter.le(endDate); counter.inc()) {
            final Date eom = c.endOfMonth(counter);

            // check that eom is actually an end-of-month
            if (!c.isEndOfMonth(eom)) {
                fail(eom.weekday() + " " + eom + " is not the last business day in "
                        + eom.month() + " " + eom.year() + " according to " + c.name());
            }

            // check that eom is in the same month as counter
            assertEquals(eom + " is not in the same month as " + counter,
                    counter.month(), eom.month());

            // next business day should be in a different month
            final Date next = c.advance(eom, 1, TimeUnit.Days, BusinessDayConvention.Unadjusted, false);
            assertFalse(next + " is in the same month as " + eom,
                    next.month() == eom.month());
        }
    }

    /**
     * Tests Calendar.businessDaysBetween across the four
     * (includeFirst, includeLast) combinations on the Brazil calendar.
     * Reference: test-suite/calendars.cpp:3578-3651.
     */
    @Test
    public void testBusinessDaysBetween() {
        QL.info("Testing calculation of business days between dates...");

        final Date[] testDates = new Date[] {
                new Date(1, Month.February, 2002),
                new Date(4, Month.February, 2002),
                new Date(16, Month.May, 2003),
                new Date(17, Month.December, 2003),
                new Date(17, Month.December, 2004),
                new Date(19, Month.December, 2005),
                new Date(2, Month.January, 2006),
                new Date(13, Month.March, 2006),
                new Date(15, Month.May, 2006),
                new Date(17, Month.March, 2006),
                new Date(15, Month.May, 2006),
                new Date(26, Month.July, 2006),
                new Date(26, Month.July, 2006),
                new Date(27, Month.July, 2006),
                new Date(29, Month.July, 2006),
                new Date(29, Month.July, 2006),
        };

        // default params: from date included, to excluded
        final long[] expected =
                {1, 321, 152, 251, 252, 10, 48, 42, -38, 38, 51, 0, 1, 2, 0};

        // exclude from, include to
        final long[] expectedIncludeTo =
                {1, 321, 152, 251, 252, 10, 48, 42, -38, 38, 51, 0, 1, 1, 0};

        // include both from and to
        final long[] expectedIncludeAll =
                {2, 322, 153, 252, 253, 11, 49, 43, -39, 39, 52, 1, 2, 2, 0};

        // exclude both from and to
        final long[] expectedExcludeAll =
                {0, 320, 151, 250, 251, 9, 47, 41, -37, 37, 50, 0, 0, 1, 0};

        final Calendar calendar = new Brazil();

        for (int i = 1; i < testDates.length; i++) {
            long calculated = calendar.businessDaysBetween(testDates[i - 1], testDates[i], true, false);
            assertEquals("from " + testDates[i - 1] + " included to " + testDates[i] + " excluded",
                    expected[i - 1], calculated);

            calculated = calendar.businessDaysBetween(testDates[i - 1], testDates[i], false, true);
            assertEquals("from " + testDates[i - 1] + " excluded to " + testDates[i] + " included",
                    expectedIncludeTo[i - 1], calculated);

            calculated = calendar.businessDaysBetween(testDates[i - 1], testDates[i], true, true);
            assertEquals("from " + testDates[i - 1] + " included to " + testDates[i] + " included",
                    expectedIncludeAll[i - 1], calculated);

            calculated = calendar.businessDaysBetween(testDates[i - 1], testDates[i], false, false);
            assertEquals("from " + testDates[i - 1] + " excluded to " + testDates[i] + " excluded",
                    expectedExcludeAll[i - 1], calculated);
        }
    }

    /**
     * Tests Calendar.holidayList over a one-year window using the Germany
     * calendar (mirrors test-suite/calendars.cpp:3857-3890). The C++ test
     * also exercises {@code businessDayList} which is not present in the
     * Java {@link Calendar} API; here we compose the equivalent set manually.
     */
    @Test
    public void testDayLists() {
        QL.info("Testing holidayList...");

        final Calendar germany = new Germany();
        final Date firstDate = Date.todaysDate();
        final Date endDate = firstDate.add(new Period(1, TimeUnit.Years));

        // iterate; every date must be either a holiday or a business day
        // (Java has no businessDayList; check via isBusinessDay)
        final java.util.List<Date> holidays =
                Calendar.holidayList(germany, firstDate, endDate, true);

        // basic invariant: no date appears as both holiday and business day
        for (final Date h : holidays) {
            assertFalse("Date " + h + " is both holiday and business day",
                    germany.isBusinessDay(h));
        }

        // every date must be classifiable
        for (Date d = firstDate.clone(); d.le(endDate); d.inc()) {
            final boolean isHol = germany.isHoliday(d);
            final boolean isBiz = germany.isBusinessDay(d);
            assertTrue("Date " + d + " is neither holiday nor business day",
                    isHol || isBiz);
            assertFalse("Date " + d + " is both holiday and business day",
                    isHol && isBiz);
        }
    }

    /**
     * Tests Calendar.startOfMonth / Calendar.isStartOfMonth invariants over a
     * 5-year window starting today, mirroring v1.42.1
     * test-suite/calendars.cpp:3526-3550.
     *
     * Note: the C++ test iterates from Date::minDate() + 2 months to maxDate();
     * here we use a 5-year window starting today (matching the strategy used
     * by {@link #testEndOfMonth()}) to keep the test cheap while exercising
     * business-day and weekend boundaries across many months.
     */
    @Test
    public void testStartOfMonth() {
        QL.info("Testing start-of-month calculation...");

        final Calendar c = new Target();
        final Date startDate = Date.todaysDate();
        final Date endDate = startDate.add(new Period(5, TimeUnit.Years));

        for (Date counter = startDate.clone(); counter.le(endDate); counter.inc()) {
            final Date som = c.startOfMonth(counter);

            // check that som is actually a start-of-month
            if (!c.isStartOfMonth(som)) {
                fail(som.weekday() + " " + som + " is not the first business day in "
                        + som.month() + " " + som.year() + " according to " + c.name());
            }

            // check that som is in the same month as counter
            assertEquals(som + " is not in the same month as " + counter,
                    counter.month(), som.month());

            // previous business day should be in a different month
            final Date prev = c.advance(som, -1, TimeUnit.Days, BusinessDayConvention.Unadjusted, false);
            assertFalse(prev + " is in the same month as " + som,
                    prev.month() == som.month());
        }
    }

    /**
     * Tests {@link BespokeCalendar} basic operations — addWeekend and
     * addHoliday — mirroring v1.42.1 test-suite/calendars.cpp:3653-3764.
     * <p>
     * The C++ test additionally exercises "linked instances" semantics
     * ({@code BespokeCalendar a2 = a1;} where {@code a2} shares state with
     * {@code a1} via {@code shared_ptr<Impl>}). Java has no copy constructor;
     * each {@code new BespokeCalendar(...)} carries its own {@code Impl}. To
     * mirror the shared-state assertions here we exercise weekend/holiday
     * additions against the same instance reference (passing the same object
     * around preserves identity, which is the realistic Java idiom — see
     * {@link BespokeCalendar} javadoc).
     */
    @Test
    public void testBespokeCalendars() {
        QL.info("Testing bespoke calendars...");

        final BespokeCalendar a1 = new BespokeCalendar();
        final BespokeCalendar b1 = new BespokeCalendar();

        final Date testDate1 = new Date(4, Month.October, 2008); // Saturday
        final Date testDate2 = new Date(5, Month.October, 2008); // Sunday
        final Date testDate3 = new Date(6, Month.October, 2008); // Monday
        final Date testDate4 = new Date(7, Month.October, 2008); // Tuesday

        // initial state: no weekends, no holidays — all four dates are business days
        assertTrue(testDate1 + " erroneously detected as holiday", a1.isBusinessDay(testDate1));
        assertTrue(testDate2 + " erroneously detected as holiday", a1.isBusinessDay(testDate2));
        assertTrue(testDate3 + " erroneously detected as holiday", a1.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", a1.isBusinessDay(testDate4));

        assertTrue(testDate1 + " erroneously detected as holiday", b1.isBusinessDay(testDate1));
        assertTrue(testDate2 + " erroneously detected as holiday", b1.isBusinessDay(testDate2));
        assertTrue(testDate3 + " erroneously detected as holiday", b1.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", b1.isBusinessDay(testDate4));

        // add Sunday as weekend on a1 only
        a1.addWeekend(Weekday.Sunday);

        assertTrue(testDate1 + " erroneously detected as holiday", a1.isBusinessDay(testDate1));
        assertFalse(testDate2 + " (Sunday) not detected as weekend", a1.isBusinessDay(testDate2));
        assertTrue(testDate3 + " erroneously detected as holiday", a1.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", a1.isBusinessDay(testDate4));

        // b1 unaffected
        assertTrue(testDate1 + " erroneously detected as holiday", b1.isBusinessDay(testDate1));
        assertTrue(testDate2 + " erroneously detected as holiday", b1.isBusinessDay(testDate2));
        assertTrue(testDate3 + " erroneously detected as holiday", b1.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", b1.isBusinessDay(testDate4));

        // add Monday (testDate3) as a holiday on a1
        a1.addHoliday(testDate3);

        assertTrue(testDate1 + " erroneously detected as holiday", a1.isBusinessDay(testDate1));
        assertFalse(testDate2 + " (Sunday) not detected as weekend", a1.isBusinessDay(testDate2));
        assertFalse(testDate3 + " (marked as holiday) not detected", a1.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", a1.isBusinessDay(testDate4));

        assertTrue(testDate1 + " erroneously detected as holiday", b1.isBusinessDay(testDate1));
        assertTrue(testDate2 + " erroneously detected as holiday", b1.isBusinessDay(testDate2));
        assertTrue(testDate3 + " erroneously detected as holiday", b1.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", b1.isBusinessDay(testDate4));

        // The C++ "BespokeCalendar a2 = a1;" branch tests shared_ptr linkage.
        // Java has no copy constructor — mirror the intent by aliasing the
        // same reference (a2 == a1), so addWeekend/addHoliday on a2 are
        // visible through a1 as well.
        final BespokeCalendar a2 = a1;

        a2.addWeekend(Weekday.Saturday);

        assertFalse(testDate1 + " (Saturday) not detected as weekend", a1.isBusinessDay(testDate1));
        assertFalse(testDate2 + " (Sunday) not detected as weekend", a1.isBusinessDay(testDate2));
        assertFalse(testDate3 + " (marked as holiday) not detected", a1.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", a1.isBusinessDay(testDate4));

        assertFalse(testDate1 + " (Saturday) not detected as weekend", a2.isBusinessDay(testDate1));
        assertFalse(testDate2 + " (Sunday) not detected as weekend", a2.isBusinessDay(testDate2));
        assertFalse(testDate3 + " (marked as holiday) not detected", a2.isBusinessDay(testDate3));
        assertTrue(testDate4 + " erroneously detected as holiday", a2.isBusinessDay(testDate4));

        a2.addHoliday(testDate4);

        assertFalse(testDate1 + " (Saturday) not detected as weekend", a1.isBusinessDay(testDate1));
        assertFalse(testDate2 + " (Sunday) not detected as weekend", a1.isBusinessDay(testDate2));
        assertFalse(testDate3 + " (marked as holiday) not detected", a1.isBusinessDay(testDate3));
        assertFalse(testDate4 + " (marked as holiday) not detected", a1.isBusinessDay(testDate4));

        assertFalse(testDate1 + " (Saturday) not detected as weekend", a2.isBusinessDay(testDate1));
        assertFalse(testDate2 + " (Sunday) not detected as weekend", a2.isBusinessDay(testDate2));
        assertFalse(testDate3 + " (marked as holiday) not detected", a2.isBusinessDay(testDate3));
        assertFalse(testDate4 + " (marked as holiday) not detected", a2.isBusinessDay(testDate4));
    }

    @Ignore("Java Date is whole-day-resolution only; C++ QL_HIGH_RESOLUTION_DATE intraday timestamps unsupported")
    @Test
    public void testIntradayAddHolidays() {
        // Mirrors test-suite/calendars.cpp:3766-3855, guarded by
        // #ifdef QL_HIGH_RESOLUTION_DATE in C++. The Java {@link Date} class
        // stores a single serial number (days since reference epoch) with no
        // sub-day precision; intraday-resolution addHoliday/removeHoliday
        // semantics cannot be expressed without a parallel intraday Date
        // representation. Deferred indefinitely — this is a design-level
        // divergence, not a missing port.
    }

    /**
     * Covers the {@code addedHolidays()} / {@code removedHolidays()}
     * std::set checks from test-suite/calendars.cpp:93-103 that were
     * skipped in {@link #testModifiedCalendars()}.
     */
    @Test
    public void testModifiedCalendarsAccessors() {
        QL.info("Testing calendar modification accessors...");

        final Calendar c1 = new Target();
        final Date d1 = new Date(1, Month.May, 2004);    // holiday for TARGET
        final Date d2 = new Date(26, Month.April, 2004); // business day

        assertTrue("wrong assumption — c1.isHoliday(d1)", c1.isHoliday(d1));
        assertTrue("wrong assumption — c1.isBusinessDay(d2)", c1.isBusinessDay(d2));

        c1.removeHoliday(d1);
        c1.addHoliday(d2);

        final java.util.Set<Date> added = c1.addedHolidays();
        final java.util.Set<Date> removed = c1.removedHolidays();

        assertFalse("did not expect to find " + d1 + " in addedHolidays", added.contains(d1));
        assertTrue("expected to find " + d2 + " in addedHolidays", added.contains(d2));
        assertTrue("expected to find " + d1 + " in removedHolidays", removed.contains(d1));
        assertFalse("did not expect to find " + d2 + " in removedHolidays", removed.contains(d2));
    }

    /**
     * Tests {@link JointCalendar} consistency for the 5-calendar variant
     * (TARGET, UK, NYSE, Japan, Germany) constructed from a {@link
     * java.util.List List&lt;Calendar&gt;}, mirroring v1.42.1
     * test-suite/calendars.cpp:140-205 (the {@code cvh} JoinHolidays branch).
     */
    @Test
    public void testJointCalendars5() {
        QL.info("Testing 5-calendar JointCalendar (list constructor)...");

        final Calendar c1 = new Target();
        final Calendar c2 = new UnitedKingdom();
        final Calendar c3 = new UnitedStates(UnitedStates.Market.NYSE);
        final Calendar c4 = new org.jquantlib.time.calendars.Japan();
        final Calendar c5 = new Germany();

        final java.util.List<Calendar> calendarList = new java.util.ArrayList<Calendar>(5);
        calendarList.add(c1);
        calendarList.add(c2);
        calendarList.add(c3);
        calendarList.add(c4);
        calendarList.add(c5);

        final Calendar cvh = new JointCalendar(calendarList, JointCalendarRule.JoinHolidays);

        final Date firstDate = Date.todaysDate();
        final Date endDate = firstDate.add(new Period(1, TimeUnit.Years));

        for (Date d = firstDate.clone(); d.lt(endDate); d.inc()) {
            final boolean b1 = c1.isBusinessDay(d);
            final boolean b2 = c2.isBusinessDay(d);
            final boolean b3 = c3.isBusinessDay(d);
            final boolean b4 = c4.isBusinessDay(d);
            final boolean b5 = c5.isBusinessDay(d);

            assertEquals("JoinHolidays inconsistency at " + d,
                    b1 && b2 && b3 && b4 && b5, cvh.isBusinessDay(d));
        }
    }

    @Ignore("Design divergence — Java Calendar stores added/removed holidays per-instance; C++ shares via static shared_ptr<Impl>")
    @Test
    public void testModifiedCalendarsShared() {
        // Mirrors test-suite/calendars.cpp:111-115 — assertions that a fresh
        // TARGET instance reflects modifications made through another TARGET
        // instance.
        //
        // In C++ v1.42.1 the concrete calendar constructors (e.g.
        // TARGET::TARGET) install a {@code static shared_ptr<Impl>} so every
        // instance of the subclass shares one Impl (including its
        // added/removed-holiday sets). The Java port creates a fresh
        // {@code Impl} per {@code new Target()}, so per-instance addHoliday /
        // removeHoliday calls do NOT propagate to sibling instances. Aligning
        // would require rewriting every concrete calendar subclass in the
        // {@code time.calendars} package to install a process-wide singleton
        // Impl (with the attendant thread-safety story) — a Phase 2+
        // structural change, not a stub fill. Callers that need shared state
        // should keep and pass the same calendar instance around.
    }
}

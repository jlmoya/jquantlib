/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/
*/

package org.jquantlib.testsuite.time;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.WeekendsOnly;
import org.junit.Test;

/**
 * Smoke test for {@link DateGeneration.Rule#CDS} / {@link DateGeneration.Rule#CDS2015}
 * / {@link DateGeneration.Rule#OldCDS} schedule generation and the related
 * {@link Schedule#previousTwentieth} / {@link Schedule#nextTwentieth} helpers.
 *
 * <p>Expected dates were captured from QuantLib v1.42.1 via the C++ probe
 * {@code migration-harness/cpp/probes/time/cds_schedule_probe.cpp}; the JSON
 * reference lives at {@code migration-harness/references/time/cds_schedule.json}.
 *
 * <p>Phase 3c L0 A.1.
 */
public class CdsScheduleTest {

    public CdsScheduleTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static String iso(final Date d) {
        return String.format("%04d-%02d-%02d", d.year(), d.month().value(), d.dayOfMonth());
    }

    private static void assertScheduleDates(final String[] expected, final Schedule s) {
        assertEquals("schedule size", expected.length, s.size());
        for (int i = 0; i < expected.length; ++i) {
            assertEquals("date[" + i + "]", expected[i], iso(s.date(i)));
        }
    }

    @Test
    public void testScheduleCds5yQuarterly2026() {
        final Calendar cal = new WeekendsOnly();
        final Schedule s = new Schedule(
                new Date(6, Month.March, 2026),
                new Date(20, Month.June, 2031),
                new Period(3, TimeUnit.Months),
                cal,
                BusinessDayConvention.Following,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.CDS,
                false);
        // From cds_schedule_probe.cpp / references/time/cds_schedule.json.
        final String[] expected = {
                "2025-12-22", "2026-03-20", "2026-06-22", "2026-09-21",
                "2026-12-21", "2027-03-22", "2027-06-21", "2027-09-20",
                "2027-12-20", "2028-03-20", "2028-06-20", "2028-09-20",
                "2028-12-20", "2029-03-20", "2029-06-20", "2029-09-20",
                "2029-12-20", "2030-03-20", "2030-06-20", "2030-09-20",
                "2030-12-20", "2031-03-20", "2031-06-20"
        };
        assertScheduleDates(expected, s);
    }

    @Test
    public void testScheduleCds20155yQuarterly2026() {
        final Calendar cal = new WeekendsOnly();
        final Schedule s = new Schedule(
                new Date(6, Month.March, 2026),
                new Date(20, Month.June, 2031),
                new Period(3, TimeUnit.Months),
                cal,
                BusinessDayConvention.Following,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.CDS2015,
                false);
        // CDS2015 yields the same trade-date schedule as CDS for this start.
        final String[] expected = {
                "2025-12-22", "2026-03-20", "2026-06-22", "2026-09-21",
                "2026-12-21", "2027-03-22", "2027-06-21", "2027-09-20",
                "2027-12-20", "2028-03-20", "2028-06-20", "2028-09-20",
                "2028-12-20", "2029-03-20", "2029-06-20", "2029-09-20",
                "2029-12-20", "2030-03-20", "2030-06-20", "2030-09-20",
                "2030-12-20", "2031-03-20", "2031-06-20"
        };
        assertScheduleDates(expected, s);
    }

    @Test
    public void testScheduleOldCds5yQuarterly() {
        final Calendar cal = new WeekendsOnly();
        final Schedule s = new Schedule(
                new Date(15, Month.June, 2008),
                new Date(20, Month.June, 2013),
                new Period(3, TimeUnit.Months),
                cal,
                BusinessDayConvention.Following,
                BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.OldCDS,
                false);
        final String[] expected = {
                "2008-06-15", "2008-09-22", "2008-12-22", "2009-03-20",
                "2009-06-22", "2009-09-21", "2009-12-21", "2010-03-22",
                "2010-06-21", "2010-09-20", "2010-12-20", "2011-03-21",
                "2011-06-20", "2011-09-20", "2011-12-20", "2012-03-20",
                "2012-06-20", "2012-09-20", "2012-12-20", "2013-03-20",
                "2013-06-20"
        };
        assertScheduleDates(expected, s);
    }

    @Test
    public void testPreviousTwentiethCds() {
        // From cds_schedule_probe.cpp.
        assertEquals("2025-12-20",
                iso(Schedule.previousTwentieth(
                        new Date(15, Month.January, 2026), DateGeneration.Rule.CDS)));
        assertEquals("2025-12-20",
                iso(Schedule.previousTwentieth(
                        new Date(31, Month.December, 2025), DateGeneration.Rule.CDS)));
    }

    @Test
    public void testPreviousTwentiethCds2015() {
        assertEquals("2025-12-20",
                iso(Schedule.previousTwentieth(
                        new Date(25, Month.December, 2025), DateGeneration.Rule.CDS2015)));
    }

    @Test
    public void testPreviousTwentiethOldCds() {
        assertEquals("2025-06-20",
                iso(Schedule.previousTwentieth(
                        new Date(5, Month.August, 2025), DateGeneration.Rule.OldCDS)));
    }
}

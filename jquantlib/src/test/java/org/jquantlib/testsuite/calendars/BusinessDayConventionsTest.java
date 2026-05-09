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

import org.jquantlib.QL;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.SouthAfrica;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Port of QuantLib v1.42.1 test-suite/businessdayconventions.cpp
 *
 * Exercises Calendar.advance(date, period, convention, endOfMonth) over the
 * full BusinessDayConvention enum using the SouthAfrica calendar.
 *
 * Phase 5c — calendar/time/indexes test ports.
 *
 * Phase 5c.5 deferral: cases for HalfMonthModifiedFollowing and Nearest are
 * declared in C++ v1.42.1's BusinessDayConvention enum but are absent in the
 * Java enum (org.jquantlib.time.BusinessDayConvention). Adding the enum values
 * requires non-trivial production-code changes to Calendar.adjust dispatch
 * and is deferred to Phase 5c.5.
 *
 * @author Jose Moya
 */
public class BusinessDayConventionsTest {

    public BusinessDayConventionsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final class SingleCase {
        final Calendar calendar;
        final BusinessDayConvention convention;
        final Date start;
        final Period period;
        final boolean endOfMonth;
        final Date result;

        SingleCase(final Calendar calendar,
                   final BusinessDayConvention convention,
                   final Date start,
                   final Period period,
                   final boolean endOfMonth,
                   final Date result) {
            this.calendar = calendar;
            this.convention = convention;
            this.start = start;
            this.period = period;
            this.endOfMonth = endOfMonth;
            this.result = result;
        }
    }

    private static SingleCase sc(final Calendar cal,
                                 final BusinessDayConvention conv,
                                 final Date start,
                                 final Period period,
                                 final boolean eom,
                                 final Date result) {
        return new SingleCase(cal, conv, start, period, eom, result);
    }

    private static void runCases(final SingleCase[] cases) {
        for (int i = 0; i < cases.length; i++) {
            final Calendar cal = cases[i].calendar;
            final Date result = cal.advance(
                    cases[i].start,
                    cases[i].period,
                    cases[i].convention,
                    cases[i].endOfMonth);
            assertEquals(
                    "case " + i + ": start=" + cases[i].start
                            + " calendar=" + cal.name()
                            + " period=" + cases[i].period + " eom=" + cases[i].endOfMonth
                            + " convention=" + cases[i].convention
                            + " expected=" + cases[i].result + " actual=" + result,
                    cases[i].result, result);
        }
    }

    /**
     * Tests Following / ModifiedFollowing / Preceding / ModifiedPreceding /
     * Unadjusted business-day conventions against a curated table of cases
     * from C++ test-suite/businessdayconventions.cpp.
     */
    @Test
    public void testConventions() {
        QL.info("Testing business day conventions...");

        final Calendar sa = new SouthAfrica();

        final SingleCase[] cases = new SingleCase[] {
                // Following
                sc(sa, BusinessDayConvention.Following,
                        new Date(3, Month.February, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(3, Month.March, 2015)),
                sc(sa, BusinessDayConvention.Following,
                        new Date(3, Month.February, 2015), new Period(4, TimeUnit.Days), false,
                        new Date(9, Month.February, 2015)),
                sc(sa, BusinessDayConvention.Following,
                        new Date(31, Month.January, 2015), new Period(1, TimeUnit.Months), true,
                        new Date(27, Month.February, 2015)),
                sc(sa, BusinessDayConvention.Following,
                        new Date(31, Month.January, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(2, Month.March, 2015)),

                // ModifiedFollowing
                sc(sa, BusinessDayConvention.ModifiedFollowing,
                        new Date(3, Month.February, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(3, Month.March, 2015)),
                sc(sa, BusinessDayConvention.ModifiedFollowing,
                        new Date(3, Month.February, 2015), new Period(4, TimeUnit.Days), false,
                        new Date(9, Month.February, 2015)),
                sc(sa, BusinessDayConvention.ModifiedFollowing,
                        new Date(31, Month.January, 2015), new Period(1, TimeUnit.Months), true,
                        new Date(27, Month.February, 2015)),
                sc(sa, BusinessDayConvention.ModifiedFollowing,
                        new Date(31, Month.January, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(27, Month.February, 2015)),
                sc(sa, BusinessDayConvention.ModifiedFollowing,
                        new Date(25, Month.March, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(28, Month.April, 2015)),
                sc(sa, BusinessDayConvention.ModifiedFollowing,
                        new Date(7, Month.February, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(9, Month.March, 2015)),

                // Preceding
                sc(sa, BusinessDayConvention.Preceding,
                        new Date(3, Month.March, 2015), new Period(-1, TimeUnit.Months), false,
                        new Date(3, Month.February, 2015)),
                sc(sa, BusinessDayConvention.Preceding,
                        new Date(3, Month.February, 2015), new Period(-2, TimeUnit.Days), false,
                        new Date(30, Month.January, 2015)),
                sc(sa, BusinessDayConvention.Preceding,
                        new Date(1, Month.March, 2015), new Period(-1, TimeUnit.Months), true,
                        new Date(30, Month.January, 2015)),
                sc(sa, BusinessDayConvention.Preceding,
                        new Date(1, Month.March, 2015), new Period(-1, TimeUnit.Months), false,
                        new Date(30, Month.January, 2015)),

                // ModifiedPreceding
                sc(sa, BusinessDayConvention.ModifiedPreceding,
                        new Date(3, Month.March, 2015), new Period(-1, TimeUnit.Months), false,
                        new Date(3, Month.February, 2015)),
                sc(sa, BusinessDayConvention.ModifiedPreceding,
                        new Date(3, Month.February, 2015), new Period(-2, TimeUnit.Days), false,
                        new Date(30, Month.January, 2015)),
                sc(sa, BusinessDayConvention.ModifiedPreceding,
                        new Date(1, Month.March, 2015), new Period(-1, TimeUnit.Months), true,
                        new Date(2, Month.February, 2015)),
                sc(sa, BusinessDayConvention.ModifiedPreceding,
                        new Date(1, Month.March, 2015), new Period(-1, TimeUnit.Months), false,
                        new Date(2, Month.February, 2015)),

                // Unadjusted
                sc(sa, BusinessDayConvention.Unadjusted,
                        new Date(3, Month.February, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(3, Month.March, 2015)),
                sc(sa, BusinessDayConvention.Unadjusted,
                        new Date(3, Month.February, 2015), new Period(4, TimeUnit.Days), false,
                        new Date(9, Month.February, 2015)),
                sc(sa, BusinessDayConvention.Unadjusted,
                        new Date(31, Month.January, 2015), new Period(1, TimeUnit.Months), true,
                        new Date(28, Month.February, 2015)),
                sc(sa, BusinessDayConvention.Unadjusted,
                        new Date(30, Month.January, 2015), new Period(1, TimeUnit.Months), true,
                        new Date(28, Month.February, 2015)),
                sc(sa, BusinessDayConvention.Unadjusted,
                        new Date(27, Month.February, 2015), new Period(1, TimeUnit.Months), true,
                        new Date(27, Month.March, 2015)),
                sc(sa, BusinessDayConvention.Unadjusted,
                        new Date(31, Month.January, 2015), new Period(1, TimeUnit.Months), false,
                        new Date(28, Month.February, 2015)),
        };

        runCases(cases);
    }

    /**
     * HalfMonthModifiedFollowing convention is in v1.42.1 but missing from the
     * Java BusinessDayConvention enum. Deferred to Phase 5c.5.
     */
    @Ignore("Phase 5c.5: HalfMonthModifiedFollowing enum value missing from Java BusinessDayConvention; needs Calendar.adjust dispatch update")
    @Test
    public void testHalfMonthModifiedFollowing() {
        // Cases: 7 entries in C++ test-suite/businessdayconventions.cpp lines 91-97
    }

    /**
     * Nearest convention is in v1.42.1 but missing from the Java
     * BusinessDayConvention enum. Deferred to Phase 5c.5.
     */
    @Ignore("Phase 5c.5: Nearest enum value missing from Java BusinessDayConvention; needs Calendar.adjust dispatch update")
    @Test
    public void testNearest() {
        // Cases: 6 entries in C++ test-suite/businessdayconventions.cpp lines 100-105
    }
}

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
 */

package org.jquantlib.testsuite.calendars;

import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.September;

import org.jquantlib.QL;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Chile;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link Chile} (Santiago Stock Exchange) calendar, cross-validated
 * against QuantLib v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * @author Jose Moya
 */
public class ChileCalendarTest {

    private final Calendar sse;

    public ChileCalendarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        this.sse = new Chile();
    }

    @Test
    public void testName() {
        Assert.assertEquals("Santiago Stock Exchange", sse.name());
    }

    @Test
    public void testFixedHolidays2022() {
        // New Year (Sat)
        Assert.assertTrue(sse.isHoliday(new Date(1, January, 2022)));
        // Labour Day (Sun)
        Assert.assertTrue(sse.isHoliday(new Date(1, May, 2022)));
        // Navy Day, May 21st (Sat) — weekend anyway.
        Assert.assertTrue(sse.isHoliday(new Date(21, May, 2022)));
        // Independence Day Sep 18 (Sun)
        Assert.assertTrue(sse.isHoliday(new Date(18, September, 2022)));
        // Army Day Sep 19 (Mon) — also Mon-Friday rule for the 17th: 17 Sep 2022=Sat (not holiday).
        Assert.assertTrue(sse.isHoliday(new Date(19, September, 2022)));
        // Christmas (Sun)
        Assert.assertTrue(sse.isHoliday(new Date(25, December, 2022)));
        // New Year's Eve (Sat)
        Assert.assertTrue(sse.isHoliday(new Date(31, December, 2022)));
    }

    @Test
    public void testAboriginalPeopleDay() {
        // C++ table: 2021 → June 21 (Mon), 2022 → June 21 (Tue), 2024 → June 20 (Thu).
        Assert.assertTrue(sse.isHoliday(new Date(21, June, 2021)));
        Assert.assertTrue(sse.isHoliday(new Date(21, June, 2022)));
        Assert.assertTrue(sse.isHoliday(new Date(20, June, 2024)));
        // Not a holiday before 2021.
        Assert.assertFalse(sse.isHoliday(new Date(21, June, 2018)));
    }

    @Test
    public void testIndependenceDayMondayRuleSince2007() {
        // 17 Sep 2007 = Monday (and y >= 2007) — should be holiday.
        Assert.assertTrue(sse.isHoliday(new Date(17, September, 2007)));
        // 20 Sep 2007 = Thursday — should NOT be the Friday-rule holiday.
        Assert.assertFalse(sse.isHoliday(new Date(20, September, 2007)));
    }
}

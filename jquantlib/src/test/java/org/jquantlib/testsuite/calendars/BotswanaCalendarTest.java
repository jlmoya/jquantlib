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
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.October;

import org.jquantlib.QL;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Botswana;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link Botswana} calendar, cross-validated against QuantLib v1.42.1
 * @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * @author Jose Moya
 */
public class BotswanaCalendarTest {

    private final Calendar bw;

    public BotswanaCalendarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        this.bw = new Botswana();
    }

    @Test
    public void testName() {
        Assert.assertEquals("Botswana", bw.name());
    }

    @Test
    public void testFixedHolidays() {
        // New Year 2020 (Wed) is a holiday.
        Assert.assertTrue(bw.isHoliday(new Date(1, January, 2020)));
        // Christmas 2020 (Fri) holiday.
        Assert.assertTrue(bw.isHoliday(new Date(25, December, 2020)));
        // Boxing Day 2020 (Sat) — weekend already.
        Assert.assertTrue(bw.isHoliday(new Date(26, December, 2020)));
        // Sir Seretse Khama Day, July 1 (Wed 2020).
        Assert.assertTrue(bw.isHoliday(new Date(1, July, 2020)));
        // Botswana Day, Oct 1 (Thu 2020).
        Assert.assertTrue(bw.isHoliday(new Date(1, October, 2020)));
    }

    @Test
    public void testPresidentsDayThirdMondayOfJuly() {
        // Per C++: third Monday of July. In 2020 → July 20 (Mon).
        Assert.assertTrue(bw.isHoliday(new Date(20, July, 2020)));
        // 13 July 2020 was a Monday but is the SECOND Monday — should be business.
        Assert.assertFalse(bw.isHoliday(new Date(13, July, 2020)));
        // In 2024, third Monday of July = 15 July (Mon).
        Assert.assertTrue(bw.isHoliday(new Date(15, July, 2024)));
    }

    @Test
    public void testLabourDayMonday2022() {
        // Per C++: Labour Day, May 1st (possibly moved to Monday).
        // May 1 2022 = Sunday → Monday May 2 is a holiday.
        Assert.assertTrue(bw.isHoliday(new Date(2, May, 2022)));
    }
}

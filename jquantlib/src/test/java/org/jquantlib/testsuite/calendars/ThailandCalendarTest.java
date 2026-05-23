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

import static org.jquantlib.time.Month.April;
import static org.jquantlib.time.Month.August;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.February;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.October;

import org.jquantlib.QL;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Thailand;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link Thailand} (SET) calendar, cross-validated against QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * @author Jose Moya
 */
public class ThailandCalendarTest {

    private final Calendar set;

    public ThailandCalendarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        this.set = new Thailand();
    }

    @Test
    public void testName() {
        Assert.assertEquals("Thailand stock exchange", set.name());
    }

    @Test
    public void testFixedHolidays2023() {
        // 6 April 2023 = Chakri Memorial Day (Thursday).
        Assert.assertTrue(set.isHoliday(new Date(6, April, 2023)));
        // 13-15 April 2023 = Songkran (Thu/Fri/Sat). Note 2023 is not a Covid-exclusion year.
        Assert.assertTrue(set.isHoliday(new Date(13, April, 2023)));
        Assert.assertTrue(set.isHoliday(new Date(14, April, 2023)));
        // 1 May 2023 = Labor Day (Monday).
        Assert.assertTrue(set.isHoliday(new Date(1, May, 2023)));
        // H.M. the King's Birthday, Jul 28 (Friday).
        Assert.assertTrue(set.isHoliday(new Date(28, July, 2023)));
        // Constitution Day, Dec 10 (Sunday); the Monday Dec 11 is the holiday substitution.
        Assert.assertTrue(set.isHoliday(new Date(11, December, 2023)));
    }

    @Test
    public void testSongkranCancelled2020() {
        // C++: Songkran cancelled in 2020 due to Covid-19.
        // 13 April 2020 = Monday → would normally be a Songkran holiday. Cancelled.
        Assert.assertFalse(set.isHoliday(new Date(13, April, 2020)));
        Assert.assertFalse(set.isHoliday(new Date(14, April, 2020)));
        Assert.assertFalse(set.isHoliday(new Date(15, April, 2020)));
    }

    @Test
    public void testYearSpecific2025() {
        // Per C++ 2025 block: 12 Feb 2025 = Substitution Makha Bucha Day.
        Assert.assertTrue(set.isHoliday(new Date(12, February, 2025)));
        // 23 Oct 2025 = Chulalongkorn Day (Thu).
        Assert.assertTrue(set.isHoliday(new Date(23, October, 2025)));
    }

    @Test
    public void testQueensBirthdayAug12() {
        // H.M. Queen Sirikit Mother's Day. 12 Aug 2025 = Tuesday → holiday.
        Assert.assertTrue(set.isHoliday(new Date(12, August, 2025)));
        // The Mon-substitute rule: 12 Aug 2024 = Mon → holiday too.
        Assert.assertTrue(set.isHoliday(new Date(12, August, 2024)));
    }
}

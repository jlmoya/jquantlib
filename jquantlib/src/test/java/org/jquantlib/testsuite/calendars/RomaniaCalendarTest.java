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
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;

import org.jquantlib.QL;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Romania;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link Romania} calendar, cross-validated against QuantLib v1.42.1
 * @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * @author Jose Moya
 */
public class RomaniaCalendarTest {

    private final Calendar pub;
    private final Calendar bvb;

    public RomaniaCalendarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        this.pub = new Romania(Romania.Market.Public);
        this.bvb = new Romania(Romania.Market.BVB);
    }

    @Test
    public void testNames() {
        Assert.assertEquals("Romania", pub.name());
        Assert.assertEquals("Bucharest stock exchange", bvb.name());
    }

    @Test
    public void testPublicHolidays2018() {
        final int year = 2018;
        // Orthodox Easter Monday 2018 = Apr 9; Pentecost (Mon) = May 28.
        Assert.assertTrue(pub.isHoliday(new Date(1, January, year)));   // New Year
        Assert.assertTrue(pub.isHoliday(new Date(2, January, year)));   // Day after
        Assert.assertTrue(pub.isHoliday(new Date(24, January, year)));  // Unification
        Assert.assertTrue(pub.isHoliday(new Date(9, April, year)));     // Orthodox Easter Monday
        Assert.assertTrue(pub.isHoliday(new Date(1, May, year)));       // Labour Day
        Assert.assertTrue(pub.isHoliday(new Date(28, May, year)));      // Pentecost Monday
        Assert.assertTrue(pub.isHoliday(new Date(1, June, year)));      // Children's Day (since 2017)
        Assert.assertTrue(pub.isHoliday(new Date(15, August, year)));   // St Mary's Day
        Assert.assertTrue(pub.isHoliday(new Date(30, November, year))); // St Andrew
        Assert.assertTrue(pub.isHoliday(new Date(1, December, year)));  // National Day
        Assert.assertTrue(pub.isHoliday(new Date(25, December, year))); // Christmas
        Assert.assertTrue(pub.isHoliday(new Date(26, December, year))); // Boxing Day
    }

    @Test
    public void testChildrenDayPre2017() {
        // 1 June 2016 was a Wednesday. Pre-2017 → NOT a public holiday.
        Assert.assertFalse(pub.isHoliday(new Date(1, June, 2016)));
        // 1 June 2017 was a Thursday — IS a holiday (rule active).
        Assert.assertTrue(pub.isHoliday(new Date(1, June, 2017)));
    }

    @Test
    public void testBVBOneOffClosures2014() {
        // Per C++ BVBImpl: 24 + 31 Dec 2014 are one-off closing days.
        Assert.assertTrue(bvb.isHoliday(new Date(24, December, 2014)));
        Assert.assertTrue(bvb.isHoliday(new Date(31, December, 2014)));
        // Same dates in 2018 are normal business days (Mon, Mon).
        Assert.assertFalse(bvb.isHoliday(new Date(31, December, 2013)));
    }
}

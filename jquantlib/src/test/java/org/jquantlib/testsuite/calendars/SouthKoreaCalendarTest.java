/*
 Copyright (C) 2008 Jia Jia

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

import static org.jquantlib.time.Month.April;
import static org.jquantlib.time.Month.August;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.February;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.March;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.October;
import static org.jquantlib.time.Month.September;

import java.util.List;
import java.util.Vector;

import org.jquantlib.QL;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.SouthKorea;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Jia Jia
 * @author Renjith Nair
 *
 */
/*
 * Phase1-closure-A2-A-548-SouthKorea: legacy 2008-era expected-data is stale
 * relative to v1.42.1. The 2008 JQuantLib KRX table covered only 2004-2010
 * Lunar/Election/Buddha/Chuseok dates and did not include the KRX year-end
 * closing rule for 30-Dec Friday or post-2010 Lunar/Buddha/Chuseok dates.
 * C++ v1.42.1 southkorea.cpp includes data through 2050 + the year-end and
 * Hangul rules, and the v1.42.1-aligned Java SouthKorea.java now matches.
 * v1.42.1-correct coverage in CalendarsTest.testSouthKoreanSettlement /
 * CalendarsTest.testKoreaStockExchange.
 *
 * Align-rationale: A3-style carve-out -- upstream stale data, not a Java bug.
 */
@Ignore("Phase1-closure-A2-A-548: legacy 2008-era expected data is stale relative to v1.42.1; v1.42.1-correct coverage in CalendarsTest.testSouthKoreanSettlement / testKoreaStockExchange")
public class SouthKoreaCalendarTest {

    private Calendar c = null;
    private List<Date> expectedHol = null;

    public SouthKoreaCalendarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Before
    public void setup() {
        c = new SouthKorea(SouthKorea.Market.KRX);
        expectedHol = new Vector<>();
    }

    // 2004 - year in the past
    @Test
    public void testSouthKoreaKRXHolidaysYear2004() {
        final int year = 2004;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(1, January, year));
        expectedHol.add(new Date(21, January, year));
        expectedHol.add(new Date(22, January, year));
        expectedHol.add(new Date(23, January, year));
//        expectedHol.add(new Date(26, JANUARY, year));
        expectedHol.add(new Date(1, March, year));
        expectedHol.add(new Date(5, April, year));
        expectedHol.add(new Date(15, April, year));
        expectedHol.add(new Date(5, May, year));
        expectedHol.add(new Date(26, May, year));
        expectedHol.add(new Date(27, September, year));
        expectedHol.add(new Date(28, September, year));
        expectedHol.add(new Date(29, September, year));
        expectedHol.add(new Date(31, December, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2005 - year in the past
    @Test
    public void testSouthKoreaKRXHolidaysYear2005() {
        final int year = 2005;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(8, February, year));
        expectedHol.add(new Date(9, February, year));
        expectedHol.add(new Date(10, February, year));
        expectedHol.add(new Date(1, March, year));
        expectedHol.add(new Date(5, April, year));
        expectedHol.add(new Date(5, May, year));
        expectedHol.add(new Date(6, June, year));
        expectedHol.add(new Date(15, August, year));
        expectedHol.add(new Date(19, September, year));
        expectedHol.add(new Date(3, October, year));
        expectedHol.add(new Date(30, December, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2006 - year in the past
    @Test
    public void testSouthKoreaKRXHolidaysYear2006() {
        final int year = 2006;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(30, January, year));
//        expectedHol.add(new Date(31, JANUARY, year));
        expectedHol.add(new Date(1, March, year));
//        expectedHol.add(new Date(5, APRIL, year));
        expectedHol.add(new Date(1, May, year));
        expectedHol.add(new Date(5, May, year));
        expectedHol.add(new Date(31, May, year));
        expectedHol.add(new Date(6, June, year));
        expectedHol.add(new Date(17, July, year));
        expectedHol.add(new Date(15, August, year));
        expectedHol.add(new Date(3, October, year));
        expectedHol.add(new Date(5, October, year));
        expectedHol.add(new Date(6, October, year));
        expectedHol.add(new Date(25, December, year));
        expectedHol.add(new Date(29, December, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2007 - year in the past
    @Test
    public void testSouthKoreaKRXHolidaysYear2007() {
        final int year = 2007;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(1, January, year));
        expectedHol.add(new Date(19, February, year));
        expectedHol.add(new Date(1, March, year));
//        expectedHol.add(new Date(5, APRIL, year));
        expectedHol.add(new Date(1, May, year));
        expectedHol.add(new Date(24, May, year));
        expectedHol.add(new Date(6, June, year));
        expectedHol.add(new Date(17, July, year));
        expectedHol.add(new Date(15, August, year));
        expectedHol.add(new Date(24, September, year));
        expectedHol.add(new Date(25, September, year));
        expectedHol.add(new Date(26, September, year));
        expectedHol.add(new Date(3, October, year));
        expectedHol.add(new Date(19, December, year));
        expectedHol.add(new Date(25, December, year));
        expectedHol.add(new Date(31, December, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2008 - Current Year
    @Test
    public void testSouthKoreaKRXHolidaysYear2008() {
        final int year = 2008;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(1, January, year));
        expectedHol.add(new Date(6, February, year));
        expectedHol.add(new Date(7, February, year));
        expectedHol.add(new Date(8, February, year));
        expectedHol.add(new Date(9, April, year));
        expectedHol.add(new Date(1, May, year));
        expectedHol.add(new Date(5, May, year));
        expectedHol.add(new Date(12, May, year));
        expectedHol.add(new Date(6, June, year));
//        expectedHol.add(new Date(17, JULY, year));
        expectedHol.add(new Date(15, August, year));
        expectedHol.add(new Date(15, September, year));
        expectedHol.add(new Date(3, October, year));
        expectedHol.add(new Date(25, December, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2009 - Future Year
    @Test
    public void testSouthKoreaKRXHolidaysYear2009() {
        final int year = 2009;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(1, January, year));
        expectedHol.add(new Date(26, January, year));
        expectedHol.add(new Date(27, January, year));
        expectedHol.add(new Date(1, May, year));
        expectedHol.add(new Date(5, May, year));
//        expectedHol.add(new Date(17, JULY, year));
        expectedHol.add(new Date(2, October, year));
        expectedHol.add(new Date(25, December, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2010 - Future Year
    @Test
    public void testSouthKoreaKRXHolidaysYear2010() {
        final int year = 2010;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(1, January, year));
        expectedHol.add(new Date(15, February, year));
        expectedHol.add(new Date(1, March, year));
//        expectedHol.add(new Date(5, APRIL, year));
        expectedHol.add(new Date(5, May, year));
        expectedHol.add(new Date(21, May, year));
        expectedHol.add(new Date(21, September, year));
        expectedHol.add(new Date(22, September, year));
        expectedHol.add(new Date(23, September, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2011 - Future Year
    @Test
    public void testSouthKoreaKRXHolidaysYear2011() {
        final int year = 2011;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(1, March, year));
//        expectedHol.add(new Date(5, APRIL, year));
        expectedHol.add(new Date(5, May, year));
        expectedHol.add(new Date(6, June, year));
        expectedHol.add(new Date(15, August, year));
        expectedHol.add(new Date(3, October, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }

    // 2012 - Future Year
    @Test
    public void testSouthKoreaKRXHolidaysYear2012() {
        final int year = 2012;
        QL.info("Testing " + SouthKorea.Market.KRX + " holidays list for the year " + year + "...");

        expectedHol.add(new Date(1, March, year));
//        expectedHol.add(new Date(5, APRIL, year));
        expectedHol.add(new Date(1, May, year));
        expectedHol.add(new Date(6, June, year));
//        expectedHol.add(new Date(17, JULY, year));
        expectedHol.add(new Date(15, August, year));
        expectedHol.add(new Date(3, October, year));
        expectedHol.add(new Date(25, December, year));

        // Call the Holiday Check
        final CalendarUtil cbt = new CalendarUtil();
        cbt.checkHolidayList(expectedHol, c, year);

    }


    @After
    public void destroy() {
        c = null;
        expectedHol = null;
    }

}

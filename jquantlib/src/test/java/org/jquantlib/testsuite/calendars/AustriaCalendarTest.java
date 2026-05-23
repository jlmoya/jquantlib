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
import static org.jquantlib.time.Month.October;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Austria;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link Austria} calendar, cross-validated against QuantLib v1.42.1
 * @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * @author Jose Moya
 */
public class AustriaCalendarTest {

    private final Calendar settlement;
    private final Calendar exchange;

    public AustriaCalendarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        this.settlement = new Austria(Austria.Market.Settlement);
        this.exchange = new Austria(Austria.Market.Exchange);
    }

    @Test
    public void testNames() {
        Assert.assertEquals("Austrian settlement", settlement.name());
        Assert.assertEquals("Vienna stock exchange", exchange.name());
    }

    @Test
    public void testSettlement2020() {
        final int year = 2020;
        QL.info("Testing Austria Settlement holidays for " + year + "...");
        // Easter Monday 2020 = April 13; Ascension = May 21; Whit Monday = June 1; Corpus Christi = June 11.
        final List<Date> expected = new ArrayList<>();
        expected.add(new Date(1, January, year));   // New Year
        expected.add(new Date(6, January, year));   // Epiphany
        expected.add(new Date(13, April, year));    // Easter Monday
        expected.add(new Date(21, May, year));      // Ascension
        expected.add(new Date(1, May, year));       // Labour Day
        expected.add(new Date(15, August, year));   // Assumption (Sat — still tagged as holiday)
        expected.add(new Date(26, October, year));  // National Holiday
        expected.add(new Date(8, December, year));  // Immaculate Conception
        expected.add(new Date(25, December, year)); // Christmas
        for (final Date d : expected) {
            Assert.assertTrue("expected Austria settlement holiday: " + d,
                    settlement.isHoliday(d));
        }
        // Confirm Nov 12 is NOT a holiday in 2020 (outside 1919-1934).
        Assert.assertFalse(settlement.isHoliday(new Date(12, November, 2020)));
    }

    @Test
    public void testExchange2020NewYearsEve() {
        // Per C++ Austria::ExchangeImpl: Dec 31 closure (Exchange Holiday).
        Assert.assertTrue(exchange.isHoliday(new Date(31, December, 2020)));
        // Christmas Eve also closed (Exchange variant).
        Assert.assertTrue(exchange.isHoliday(new Date(24, December, 2020)));
        // Whit Monday closure
        Assert.assertTrue(exchange.isHoliday(new Date(1, June, 2020)));
        // Easter Monday 2020 = April 13
        Assert.assertTrue(exchange.isHoliday(new Date(13, April, 2020)));
        // Good Friday 2020 = April 10
        Assert.assertTrue(exchange.isHoliday(new Date(10, April, 2020)));
    }

    @Test
    public void testNationalHolidaySince1967() {
        // Settlement & Exchange — Oct 26 is a holiday starting in 1967.
        Assert.assertTrue(settlement.isHoliday(new Date(26, October, 1967)));
        Assert.assertTrue(exchange.isHoliday(new Date(26, October, 1967)));
        // Before 1967 Oct 26 was a regular business day if it wasn't a weekend.
        // 26 Oct 1966 is a Wednesday.
        Assert.assertFalse(settlement.isHoliday(new Date(26, October, 1966)));
    }
}

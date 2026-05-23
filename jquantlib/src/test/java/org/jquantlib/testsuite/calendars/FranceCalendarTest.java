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
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;

import org.jquantlib.QL;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.France;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link France} calendar, cross-validated against QuantLib v1.42.1
 * @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * @author Jose Moya
 */
public class FranceCalendarTest {

    private final Calendar settlement;
    private final Calendar exchange;

    public FranceCalendarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        this.settlement = new France(France.Market.Settlement);
        this.exchange = new France(France.Market.Exchange);
    }

    @Test
    public void testNames() {
        Assert.assertEquals("French settlement", settlement.name());
        Assert.assertEquals("Paris stock exchange", exchange.name());
    }

    @Test
    public void testSettlement2025() {
        final int year = 2025;
        // Easter Monday 2025 = Apr 21.
        Assert.assertTrue(settlement.isHoliday(new Date(1, January, year)));   // New Year
        Assert.assertTrue(settlement.isHoliday(new Date(21, April, year)));    // Easter Monday
        Assert.assertTrue(settlement.isHoliday(new Date(1, May, year)));       // Fete du Travail
        Assert.assertTrue(settlement.isHoliday(new Date(8, May, year)));       // Victoire 1945
        Assert.assertTrue(settlement.isHoliday(new Date(10, May, year)));      // Ascension (Sat — still tagged)
        Assert.assertTrue(settlement.isHoliday(new Date(14, July, year)));     // Fete nationale
        Assert.assertTrue(settlement.isHoliday(new Date(15, August, year)));   // Assomption
        Assert.assertTrue(settlement.isHoliday(new Date(1, November, year)));  // Toussaint (Sat — weekend)
        Assert.assertTrue(settlement.isHoliday(new Date(11, November, year))); // Armistice 1918
        Assert.assertTrue(settlement.isHoliday(new Date(25, December, year))); // Noel
    }

    @Test
    public void testExchange2025NoArmisticeClose() {
        // Paris stock exchange does NOT close on Nov 11 (per C++ ExchangeImpl).
        // 11 Nov 2025 = Tuesday (business day).
        Assert.assertFalse(exchange.isHoliday(new Date(11, November, 2025)));
        // But closes on Good Friday + Easter Monday + Christmas Eve + Boxing Day + NYE.
        Assert.assertTrue(exchange.isHoliday(new Date(18, April, 2025))); // Good Friday
        Assert.assertTrue(exchange.isHoliday(new Date(21, April, 2025))); // Easter Monday
        Assert.assertTrue(exchange.isHoliday(new Date(24, December, 2025)));
        Assert.assertTrue(exchange.isHoliday(new Date(26, December, 2025)));
        Assert.assertTrue(exchange.isHoliday(new Date(31, December, 2025)));
    }
}

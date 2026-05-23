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

package org.jquantlib.testsuite.daycounters;

import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.June;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.OneDayCounter;
import org.jquantlib.time.Date;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link OneDayCounter} — faithful port of the C++ 1/1 day count
 * convention from QuantLib v1.42.1 {@code ql/time/daycounters/one.hpp}.
 *
 * @author Jose Moya
 */
public class OneDayCounterTest {

    @Test
    public void testName() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        final DayCounter dc = new OneDayCounter();
        Assert.assertEquals("1/1", dc.name());
    }

    @Test
    public void testYearFractionAlwaysOneOrMinusOne() {
        final DayCounter dc = new OneDayCounter();
        final Date d1 = new Date(1, January, 2025);
        final Date d2 = new Date(1, June, 2025);
        Assert.assertEquals(1.0, dc.yearFraction(d1, d2), 0.0);
        Assert.assertEquals(-1.0, dc.yearFraction(d2, d1), 0.0);
        // Same-date case: d2 >= d1 → +1.
        Assert.assertEquals(1.0, dc.yearFraction(d1, d1), 0.0);
    }

    @Test
    public void testDayCountSign() {
        final DayCounter dc = new OneDayCounter();
        final Date a = new Date(1, January, 2020);
        final Date b = new Date(31, December, 2020);
        Assert.assertEquals(1L, dc.dayCount(a, b));
        Assert.assertEquals(-1L, dc.dayCount(b, a));
    }
}

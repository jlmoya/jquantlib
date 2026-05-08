/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.indexes.inflation.UKRPI;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Smoke tests for {@link org.jquantlib.indexes.ZeroInflationIndex#lastFixingDate()}
 * (Phase 2u L0 A.2).
 *
 * <p>Mirrors C++ v1.42.1 {@code ZeroInflationIndex::lastFixingDate()}
 * ({@code ql/indexes/inflationindex.cpp:190-194}): returns the first day of the
 * inflation period corresponding to the last stored fixing date.
 */
public class ZeroInflationIndexFixingTest {

    public ZeroInflationIndexFixingTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Before
    public void setUp() {
        new Settings().setEvaluationDate(new Date(13, Month.August, 2007));
        // Clear any UKRPI fixing history left by previous tests to ensure isolation.
        final UKRPI idx = new UKRPI(Frequency.Monthly, false, false);
        IndexManager.getInstance().clearHistory(idx.name());
    }

    @After
    public void tearDown() {
        final UKRPI idx = new UKRPI(Frequency.Monthly, false, false);
        IndexManager.getInstance().clearHistory(idx.name());
    }

    /**
     * lastFixingDate() on a single-fixing index returns the first day of that
     * fixing's inflation period.
     *
     * <p>addFixing(2007-01-15, v) floods the whole January 2007 period
     * [2007-01-01, 2007-01-31]. lastFixingDate() must return 2007-01-01
     * (the period start), mirroring C++
     * {@code inflationPeriod(fixings.lastDate(), frequency_).first}.
     */
    @Test
    public void lastFixingDate_singleFixing_returnsPeriodStart() {
        final UKRPI idx = new UKRPI(Frequency.Monthly, false, false);
        idx.addFixing(new Date(15, Month.January, 2007), 189.9, true);

        final Date last = idx.lastFixingDate();
        assertEquals("lastFixingDate for Jan 2007 should be 2007-01-01",
                new Date(1, Month.January, 2007), last);
    }

    /**
     * After adding fixings for multiple months, lastFixingDate() returns the
     * period start of the latest month.
     */
    @Test
    public void lastFixingDate_multipleFixings_returnsLatestPeriodStart() {
        final UKRPI idx = new UKRPI(Frequency.Monthly, false, false);
        idx.addFixing(new Date(1, Month.January, 2007), 189.9, true);
        idx.addFixing(new Date(1, Month.February, 2007), 190.5, true);
        idx.addFixing(new Date(1, Month.March, 2007), 191.1, true);

        final Date last = idx.lastFixingDate();
        assertEquals("lastFixingDate should be start of latest fixing period",
                new Date(1, Month.March, 2007), last);
    }

    /**
     * lastFixingDate() throws when no fixings are stored.
     */
    @Test
    public void lastFixingDate_noFixings_throws() {
        final UKRPI idx = new UKRPI(Frequency.Monthly, false, false);
        // No fixing seeded.
        try {
            idx.lastFixingDate();
            fail("Expected exception for empty fixing history");
        } catch (final RuntimeException e) {
            // Expected: "no fixings stored for ..."
        }
    }
}

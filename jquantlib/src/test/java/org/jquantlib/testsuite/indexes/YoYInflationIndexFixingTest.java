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
import org.jquantlib.indexes.inflation.YYUKRPI;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Smoke tests for {@link org.jquantlib.indexes.YoYInflationIndex#fixing} past-path
 * when {@code ratio_=false} (Phase 2r L0 A.3).
 *
 * <p>Verifies C++ v1.42.1 alignment: for genuine YoY indices ({@code ratio=false})
 * the past-fixing path returns the stored YoY rate directly from the time series,
 * rather than applying the ratio formula {@code pastFixing / previousFixing - 1}.
 *
 * <h3>Setup</h3>
 * <ul>
 *   <li>evalDate = 2007-08-13 (well after the fixing period)</li>
 *   <li>YYUKRPI with {@code ratio=false}, {@code interpolated=false}</li>
 *   <li>availabilityLag = 2 Months → todayMinusLag = 2007-06-13</li>
 *   <li>inflationPeriod(2007-06-13, Monthly) = [2007-06-01, 2007-06-30]</li>
 *   <li>todayMinusLag (boundary) = 2007-07-01</li>
 *   <li>Any fixingDate before 2007-07-01 routes to the past-fixing path</li>
 *   <li>Test date: 2007-01-15 → period = [2007-01-01, 2007-01-31]</li>
 *   <li>Stored YoY rate for Jan 2007: 0.0250</li>
 * </ul>
 *
 * <h3>Tolerance</h3>
 * <p>Exact: stored rate 0.0250 should be returned bit-exactly (no arithmetic).
 */
public class YoYInflationIndexFixingTest {

    private static final double STORED_YOY_RATE = 0.0250;

    public YoYInflationIndexFixingTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Before
    public void setUp() {
        // Set evaluation date well after the test fixing period so that the
        // past-fixing path is taken.
        new Settings().setEvaluationDate(new Date(13, Month.August, 2007));
    }

    @After
    public void tearDown() {
        // Clear the YYUKRPI fixing history to avoid polluting other tests.
        final YYUKRPI idx = new YYUKRPI(Frequency.Monthly, false, false);
        IndexManager.getInstance().clearHistory(idx.name());
    }

    /**
     * When {@code ratio=false} and a past fixing is available, {@code fixing(d)}
     * must return the stored YoY rate directly (not apply ratio formula).
     */
    @Test
    public void ratioFalse_pastFixing_returnsStoredYoYRate() {
        final YYUKRPI idx = new YYUKRPI(Frequency.Monthly, false, false);

        // Seed January 2007 YoY rate. addFixing floods the whole period.
        final Date janFixing = new Date(15, Month.January, 2007);
        idx.addFixing(janFixing, STORED_YOY_RATE, true);

        // This date is well before the availability boundary; past-fixing path taken.
        final double actual = idx.fixing(janFixing);

        // For ratio=false, C++ returns the stored rate (YY0 = ts[periodStart]).
        // periodStart of Jan 2007 with monthly freq = 2007-01-01.
        // addFixing floods 2007-01-01..2007-01-31 with STORED_YOY_RATE.
        // Non-interpolated: return YY0 directly.
        assertEquals("ratio=false past-fixing should return stored YoY rate",
                STORED_YOY_RATE, actual, 1.0e-15);
    }

    /**
     * Verify the fixing at the period start (2007-01-01) also returns the
     * stored rate directly.
     */
    @Test
    public void ratioFalse_pastFixing_periodStartDate() {
        final YYUKRPI idx = new YYUKRPI(Frequency.Monthly, false, false);
        idx.addFixing(new Date(1, Month.January, 2007), STORED_YOY_RATE, true);

        final double actual = idx.fixing(new Date(1, Month.January, 2007));
        assertEquals("ratio=false past-fixing at period start",
                STORED_YOY_RATE, actual, 1.0e-15);
    }

    /**
     * Verify a different month with a different stored rate returns the correct
     * value (not cross-contaminated by the ratio formula).
     */
    @Test
    public void ratioFalse_pastFixing_differentMonths() {
        final YYUKRPI idx = new YYUKRPI(Frequency.Monthly, false, false);

        final double rate_jan = 0.0250;
        final double rate_feb = 0.0270;

        idx.addFixing(new Date(15, Month.January, 2007), rate_jan, true);
        idx.addFixing(new Date(15, Month.February, 2007), rate_feb, true);

        // Each month should return its own stored rate.
        final double actual_jan = idx.fixing(new Date(15, Month.January, 2007));
        final double actual_feb = idx.fixing(new Date(15, Month.February, 2007));

        assertEquals("Jan rate", rate_jan, actual_jan, 1.0e-15);
        assertEquals("Feb rate", rate_feb, actual_feb, 1.0e-15);
    }

    /**
     * Sanity: missing fixing should throw (not silently return NaN or zero).
     */
    @Test
    public void ratioFalse_missingFixing_throws() {
        final YYUKRPI idx = new YYUKRPI(Frequency.Monthly, false, false);
        // No fixing seeded for March 2007.
        try {
            idx.fixing(new Date(15, Month.March, 2007));
            fail("Expected exception for missing fixing");
        } catch (final RuntimeException e) {
            // Expected: "Missing ... fixing for ..."
        }
    }
}

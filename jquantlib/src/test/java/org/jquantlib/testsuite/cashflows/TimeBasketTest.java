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

package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.TimeBasket;
import org.jquantlib.time.Date;
import org.junit.Test;

/**
 * Deterministic tests for {@link TimeBasket}, ported from C++ QuantLib v1.42.1 ql/cashflows/timebasket.hpp/cpp.
 *
 * <p>All expected values are derived by hand-tracing the C++
 * {@code TimeBasket::rebin} algorithm (timebasket.cpp:36-74) over serial-numbered dates, so day-count distances are
 * exact integers and the linear-interpolation weights are exact. EXACT tier — no tolerance beyond IEEE-754 round-off
 * (which is none here; all weights are halves and the inputs are exactly representable).
 */
public class TimeBasketTest {

    public TimeBasketTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // Serial-numbered dates so date differences are obvious integers.
    private static final Date D50 = new Date(50L);
    private static final Date D100 = new Date(100L);
    private static final Date D150 = new Date(150L);
    private static final Date D200 = new Date(200L);
    private static final Date D250 = new Date(250L);
    private static final Date D300 = new Date(300L);
    private static final Date D400 = new Date(400L);

    // ------------------------------------------------------------------
    // construction + map interface
    // ------------------------------------------------------------------

    @Test
    public void testConstructionAndMapInterface() {
        // timebasket.cpp:27-34 — parallel dates/values vectors.
        final List<Date> dates = Arrays.asList(D100, D200, D300);
        final List<Double> values = Arrays.asList(1.0, 2.0, 3.0);
        final TimeBasket tb = new TimeBasket(dates, values);

        assertEquals(3, tb.size());
        assertFalse(tb.isEmpty());

        assertTrue(tb.hasDate(D100));
        assertTrue(tb.hasDate(D200));
        assertTrue(tb.hasDate(D300));
        assertFalse(tb.hasDate(D150));

        assertEquals(1.0, tb.get(D100), 0.0);
        assertEquals(2.0, tb.get(D200), 0.0);
        assertEquals(3.0, tb.get(D300), 0.0);
        // Absent date → 0.0, mirroring std::map<Date,Real>::operator[] value-init.
        assertEquals(0.0, tb.get(D150), 0.0);
    }

    @Test
    public void testSetOverwritesAndIterationIsAscending() {
        final TimeBasket tb = new TimeBasket();
        tb.set(D300, 30.0);
        tb.set(D100, 10.0);
        tb.set(D200, 20.0);
        tb.set(D200, 25.0); // overwrite

        assertEquals(3, tb.size());
        assertEquals(25.0, tb.get(D200), 0.0);

        // entries() must be ascending by date (std::map ordering).
        final List<Long> order = new ArrayList<>();
        for (final var e : tb.entries()) {
            order.add(e.getKey().serialNumber());
        }
        assertEquals(Arrays.asList(100L, 200L, 300L), order);

        // reverseEntries() descending.
        final List<Long> rorder = new ArrayList<>();
        for (final var e : tb.reverseEntries()) {
            rorder.add(e.getKey().serialNumber());
        }
        assertEquals(Arrays.asList(300L, 200L, 100L), rorder);
    }

    @Test(expected = RuntimeException.class)
    public void testConstructorMismatchedSizesThrows() {
        // QL_REQUIRE(dates.size() == values.size(), ...) timebasket.cpp:29-30
        new TimeBasket(Arrays.asList(D100, D200), Arrays.asList(1.0));
    }

    // ------------------------------------------------------------------
    // algebra (timebasket.hpp:81-93)
    // ------------------------------------------------------------------

    @Test
    public void testAddAssignAccumulatesPerDate() {
        final TimeBasket a = new TimeBasket(Arrays.asList(D100, D200), Arrays.asList(1.0, 2.0));
        final TimeBasket b = new TimeBasket(Arrays.asList(D200, D300), Arrays.asList(10.0, 20.0));
        a.addAssign(b);

        assertEquals(3, a.size());
        assertEquals(1.0, a.get(D100), 0.0);
        assertEquals(12.0, a.get(D200), 0.0); // 2 + 10
        assertEquals(20.0, a.get(D300), 0.0);
    }

    @Test
    public void testSubtractAssignAccumulatesPerDate() {
        final TimeBasket a = new TimeBasket(Arrays.asList(D100, D200), Arrays.asList(1.0, 2.0));
        final TimeBasket b = new TimeBasket(Arrays.asList(D200, D300), Arrays.asList(10.0, 20.0));
        a.subtractAssign(b);

        assertEquals(3, a.size());
        assertEquals(1.0, a.get(D100), 0.0);
        assertEquals(-8.0, a.get(D200), 0.0); // 2 - 10
        assertEquals(-20.0, a.get(D300), 0.0); // 0 - 20
    }

    // ------------------------------------------------------------------
    // rebin (timebasket.cpp:36-74) — hand-traced
    // ------------------------------------------------------------------

    /**
     * Three entries spread over three buckets.
     * <p>
     * Hand-trace of C++ {@code rebin([200,100,300] -> sorted [100,200,300])}:
     * <ul>
     *   <li>date=100,v=10 : lower_bound=idx0 (bucket 100); pDate==date → result[100] += 10.</li>
     *   <li>date=150,v=20 : lower_bound=idx1 (bucket 200); nDate=100; pDays=50,nDays=50,tDays=100 →
     *       result[200]+=20*(50/100)=10, result[100]+=20*(50/100)=10.</li>
     *   <li>date=250,v=5  : lower_bound=idx2 (bucket 300); nDate=200; pDays=50,nDays=50,tDays=100 →
     *       result[300]+=5*0.5=2.5, result[200]+=5*0.5=2.5.</li>
     * </ul>
     * Result: {100:20.0, 200:12.5, 300:2.5}; total preserved (= 10+20+5 = 35).
     */
    @Test
    public void testRebinSplitsBetweenBuckets() {
        final TimeBasket tb = new TimeBasket(Arrays.asList(D100, D150, D250), Arrays.asList(10.0, 20.0, 5.0));
        // intentionally unsorted bucket list — rebin sorts internally.
        final TimeBasket out = tb.rebin(Arrays.asList(D200, D100, D300));

        assertEquals(3, out.size());
        assertEquals(20.0, out.get(D100), 0.0);
        assertEquals(12.5, out.get(D200), 0.0);
        assertEquals(2.5, out.get(D300), 0.0);

        double total = 0.0;
        for (final var e : out.entries()) {
            total += e.getValue();
        }
        assertEquals(35.0, total, 0.0);
    }

    /**
     * Edge branches of rebin:
     * <ul>
     *   <li>date=50 before all buckets: lower_bound=idx0 (bucket 100), bi==begin → nDate stays null →
     *       result[100] += value (all to pDate).</li>
     *   <li>date=400 after all buckets: lower_bound==end → pDate=back()=300, nDate stays null →
     *       result[300] += value.</li>
     *   <li>date=200 exactly on a bucket: pDate==date → result[200] += value.</li>
     * </ul>
     */
    @Test
    public void testRebinEdgeBucketsAndExactHit() {
        final TimeBasket tb = new TimeBasket(Arrays.asList(D50, D200, D400), Arrays.asList(6.0, 7.0, 8.0));
        final TimeBasket out = tb.rebin(Arrays.asList(D100, D200, D300));

        assertEquals(3, out.size());
        assertEquals(6.0, out.get(D100), 0.0); // before-all → first bucket
        assertEquals(7.0, out.get(D200), 0.0); // exact hit
        assertEquals(8.0, out.get(D300), 0.0); // after-all → last bucket

        double total = 0.0;
        for (final var e : out.entries()) {
            total += e.getValue();
        }
        assertEquals(21.0, total, 0.0);
    }

    @Test(expected = RuntimeException.class)
    public void testRebinEmptyBucketsThrows() {
        // QL_REQUIRE(!buckets.empty(), "empty bucket structure") timebasket.cpp:37
        final TimeBasket tb = new TimeBasket(Arrays.asList(D100), Arrays.asList(1.0));
        tb.rebin(new ArrayList<Date>());
    }
}

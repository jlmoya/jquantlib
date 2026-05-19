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

package org.jquantlib.testsuite.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.math.IntervalPrice;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Series;
import org.jquantlib.time.TimeSeries;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/timeseries.cpp (Phase 5a).
 *
 * <p>5 BOOST_AUTO_TEST_CASE methods. {@link TimeSeries} in JQuantLib extends
 * {@code Series<Date,V>}; the C++ container exposes inspectors {@code dates()},
 * {@code values()}, {@code firstDate()}, {@code lastDate()}, plus a custom
 * unordered-map container parameter that is mirrored in Java via
 * {@link TimeSeries#TimeSeries(Class, Map)} (any {@link Map} implementation is
 * accepted; entries are copied into the internal sorted TreeMap delegate).
 */
public class TimeSeriesTest {

    public TimeSeriesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testConstruction() {
        QL.info("Testing time series construction...");

        final TimeSeries<Double> ts = new TimeSeries<Double>(Double.class);
        ts.put(new Date(25, Month.March, 2005), 1.2);
        ts.put(new Date(29, Month.March, 2005), 2.3);
        ts.put(new Date(15, Month.March, 2005), 0.3);

        // Series is sorted by date — the smallest date should be first.
        final Map.Entry<Date, Double> first = ts.firstEntry();
        assertEquals(new Date(15, Month.March, 2005), first.getKey());
        assertEquals(0.3, first.getValue(), 1e-12);

        ts.put(new Date(15, Month.March, 2005), 4.0);
        assertEquals(4.0, ts.firstEntry().getValue(), 1e-12);

        ts.put(new Date(15, Month.March, 2005), 3.5);
        assertEquals(3.5, ts.firstEntry().getValue(), 1e-12);
    }

    @Test
    public void testIntervalPrice() {
        QL.info("Testing time series interval price...");

        final Date[] dates = new Date[] {
            new Date(25, Month.March, 2005),
            new Date(29, Month.March, 2005)
        };
        final double[] open  = {1.3, 2.3};
        final double[] close = {2.3, 3.4};
        final double[] high  = {3.4, 3.5};
        final double[] low   = {3.4, 3.2};

        final Series<Date, IntervalPrice> tsiq = IntervalPrice.makeSeries(
                Date.class, dates, open, close, high, low);
        assertNotNull(tsiq);
        assertEquals(2, tsiq.size());
    }

    @Test
    public void testIteratingDefaultContainer() {
        QL.info("Testing iteration of time series with a default container which sorts by date...");

        final Date[] dates = new Date[] {
            new Date(25, Month.March, 2005),
            new Date(29, Month.March, 2005),
            new Date(15, Month.March, 2005)
        };
        final double[] prices = {25, 23, 20};

        final TimeSeries<Double> ts = new TimeSeries<Double>(Double.class);
        for (int i = 0; i < dates.length; i++) {
            ts.put(dates[i], prices[i]);
        }

        // Sorted ascending by date: dates[2] (15-March), dates[0] (25-March),
        // dates[1] (29-March). Same for values.
        final Date[] expectedDates  = {dates[2], dates[0], dates[1]};
        final double[] expectedVals = {prices[2], prices[0], prices[1]};

        int i = 0;
        for (final Map.Entry<Date, Double> e : ts.entrySet()) {
            assertEquals("date[" + i + "]",  expectedDates[i], e.getKey());
            assertEquals("value[" + i + "]", expectedVals[i], e.getValue(), 1e-12);
            i++;
        }
        assertEquals(3, i);
    }

    /**
     * Mirrors v1.42.1 test-suite/timeseries.cpp::testCustomContainer
     * (timeseries.cpp:112-129). The C++ test instantiates
     * {@code TimeSeries<int, boost::unordered_map<Date, int>>} to verify the
     * library supports an arbitrary map-like container (in addition to the
     * default {@code std::map}). Java mirrors the intent via the
     * {@link TimeSeries#TimeSeries(Class, Map)} constructor, which copies
     * entries from any {@link Map} implementation (e.g. {@link HashMap}) into
     * the sorted {@link java.util.TreeMap} delegate.
     */
    @Test
    public void testCustomContainer() {
        QL.info("Testing usage of a custom container for time series data...");

        // populate an unordered (HashMap) container with NYSE business-day
        // dates between d0 and d1, indexed by sequential int.
        final Date d0 = new Date(25, Month.March, 2005);
        final Date d1 = new Date(25, Month.April, 2005);
        final UnitedStates calendar = new UnitedStates(UnitedStates.Market.NYSE);

        final Map<Date, Integer> unordered = new HashMap<Date, Integer>();
        Date d = d0.clone();
        for (int i = 0; d.lt(d1); i++) {
            unordered.put(d.clone(), i);
            d = calendar.advance(d, 1, TimeUnit.Days, BusinessDayConvention.Following, false);
        }

        // copy the unordered map into a TimeSeries (TreeMap delegate)
        final TimeSeries<Integer> ts = new TimeSeries<Integer>(Integer.class, unordered);

        // read back via Date keys — must yield the same sequential int per date
        d = d0.clone();
        for (int i = 0; d.lt(d1); i++) {
            assertEquals("ts[" + d + "]", Integer.valueOf(i), ts.get(d));
            d = calendar.advance(d, 1, TimeUnit.Days, BusinessDayConvention.Following, false);
        }
    }

    @Test
    public void testInspectors() {
        QL.info("Testing inspectors of time series...");

        final Date[] dates = new Date[] {
            new Date(25, Month.March, 2005),
            new Date(29, Month.March, 2005),
            new Date(15, Month.March, 2005)
        };
        final double[] prices = {25, 23, 20};

        final TimeSeries<Double> ts = new TimeSeries<Double>(Double.class);
        for (int i = 0; i < dates.length; i++) {
            ts.put(dates[i], prices[i]);
        }

        // firstDate() / lastDate() in C++ — Java equivalents are
        // firstKey() / lastKey() inherited from NavigableMap.
        assertEquals(new Date(15, Month.March, 2005), ts.firstKey());
        assertEquals(new Date(29, Month.March, 2005), ts.lastKey());
        assertEquals(3, ts.size());
        assertFalse(ts.isEmpty());
    }

    @Test
    public void testUtilities() {
        QL.info("Testing time series utilities...");

        final Date[] dates = new Date[] {
            new Date(25, Month.March, 2005),
            new Date(29, Month.March, 2005),
            new Date(15, Month.March, 2005)
        };
        final double[] prices = {25, 23, 20};

        final TimeSeries<Double> ts = new TimeSeries<Double>(Double.class);
        for (int i = 0; i < dates.length; i++) {
            ts.put(dates[i], prices[i]);
        }

        // Java does not have a 'find' that auto-inserts a missing key; only
        // get(K). The C++ test exercises three known-present lookups and one
        // auto-insert case. Faithfully reproduce only the known-present part
        // (the auto-insert behaviour does not exist in Java).
        assertEquals(20.0, ts.get(new Date(15, Month.March, 2005)), 1e-12);
        assertEquals(25.0, ts.get(new Date(25, Month.March, 2005)), 1e-12);
        assertEquals(23.0, ts.get(new Date(29, Month.March, 2005)), 1e-12);
        assertEquals(3, ts.size());

        // dates() and values() in C++. The Java NavigableMap exposes
        // keySet() and values(); both are sorted ascending by key.
        final Date[] expectedDates  = {dates[2], dates[0], dates[1]};
        final double[] expectedVals = {prices[2], prices[0], prices[1]};

        int i = 0;
        for (final Date d : ts.keySet()) {
            assertEquals("date[" + i + "]", expectedDates[i], d);
            i++;
        }
        i = 0;
        final Iterator<Double> it = ts.values().iterator();
        while (it.hasNext()) {
            assertEquals("value[" + i + "]", expectedVals[i], it.next(), 1e-12);
            i++;
        }
    }
}

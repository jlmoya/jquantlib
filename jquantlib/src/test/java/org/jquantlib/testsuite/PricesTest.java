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

package org.jquantlib.testsuite;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.math.IntervalPrice;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Series;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/prices.cpp (Phase 5a).
 *
 * <p>6 BOOST_AUTO_TEST_CASE methods exercising
 * {@link IntervalPrice} inspectors/modifiers/series helpers, plus
 * {@code midEquivalent} / {@code midSafe} free functions.
 *
 * <p>Phase 5a.5 carry-forward: the C++ free functions
 * {@code QuantLib::midEquivalent(bid,ask,last,close)} and
 * {@code QuantLib::midSafe(bid,ask)} (defined in {@code ql/prices.hpp})
 * have no Java equivalent. Those two test cases are
 * {@code @Ignore}-annotated.
 */
public class PricesTest {

    public PricesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5a.5 carry-forward — Java has no midEquivalent(bid,ask,last,close) helper "
            + "(C++ ql/prices.hpp). To unblock, port org.jquantlib.math.Prices with the "
            + "midEquivalent/midSafe overloads.")
    @Test
    public void testMidEquivalent() {
    }

    @Ignore("Phase 5a.5 carry-forward — Java has no midSafe(bid,ask) helper (C++ ql/prices.hpp). "
            + "Port alongside midEquivalent.")
    @Test
    public void testMidSafe() {
    }

    @Test
    public void testIntervalPriceInspectors() {
        QL.info("Testing IntervalPrice inspectors...");

        final IntervalPrice p = new IntervalPrice(1, 2, 3, 4);

        assertEquals(1.0, p.open(),  1e-12);
        assertEquals(1.0, p.value(IntervalPrice.Type.Open), 1e-12);

        assertEquals(2.0, p.close(), 1e-12);
        assertEquals(2.0, p.value(IntervalPrice.Type.Close), 1e-12);

        assertEquals(3.0, p.high(),  1e-12);
        assertEquals(3.0, p.value(IntervalPrice.Type.High), 1e-12);

        assertEquals(4.0, p.low(),   1e-12);
        assertEquals(4.0, p.value(IntervalPrice.Type.Low), 1e-12);
    }

    private static void assertEqualPrices(final IntervalPrice lhs, final IntervalPrice rhs) {
        for (final IntervalPrice.Type t : IntervalPrice.Type.values()) {
            assertEquals("type=" + t, lhs.value(t), rhs.value(t), 1e-12);
        }
    }

    @Test
    public void testIntervalPriceModifiers() {
        QL.info("Testing IntervalPrice modifiers...");

        final IntervalPrice p = new IntervalPrice(1, 2, 3, 4);

        p.setValue(IntervalPrice.Type.Open, 11);
        assertEqualPrices(p, new IntervalPrice(11, 2, 3, 4));

        p.setValue(IntervalPrice.Type.Close, 12);
        assertEqualPrices(p, new IntervalPrice(11, 12, 3, 4));

        p.setValue(IntervalPrice.Type.High, 13);
        assertEqualPrices(p, new IntervalPrice(11, 12, 13, 4));

        p.setValue(IntervalPrice.Type.Low, 14);
        assertEqualPrices(p, new IntervalPrice(11, 12, 13, 14));

        p.setValues(21, 22, 23, 24);
        assertEqualPrices(p, new IntervalPrice(21, 22, 23, 24));
    }

    private static Series<Date, IntervalPrice> createSeries() {
        final Date[] d = new Date[] {
            new Date(1, Month.January,  2001),
            new Date(3, Month.March,    2003),
            new Date(2, Month.February, 2002)
        };
        final double[] open  = {11, 13, 12};
        final double[] close = {21, 23, 22};
        final double[] high  = {31, 33, 32};
        final double[] low   = {41, 43, 42};

        return IntervalPrice.makeSeries(Date.class, d, open, close, high, low);
    }

    @Test
    public void testIntervalPriceMakeSeries() {
        QL.info("Testing creation of IntervalPrice series...");

        final Series<Date, IntervalPrice> priceSeries = createSeries();

        assertEquals(3, priceSeries.size());
        assertEqualPrices(priceSeries.get(new Date(1, Month.January,  2001)),
                new IntervalPrice(11, 21, 31, 41));
        assertEqualPrices(priceSeries.get(new Date(2, Month.February, 2002)),
                new IntervalPrice(12, 22, 32, 42));
        assertEqualPrices(priceSeries.get(new Date(3, Month.March,    2003)),
                new IntervalPrice(13, 23, 33, 43));
    }

    @Test
    public void testIntervalPriceExtractComponent() {
        QL.info("Testing extraction of IntervalPrice values...");

        final Series<Date, Double> openSeries  =
            IntervalPrice.extractComponent(Date.class, createSeries(), IntervalPrice.Type.Open);
        final Series<Date, Double> closeSeries =
            IntervalPrice.extractComponent(Date.class, createSeries(), IntervalPrice.Type.Close);
        final Series<Date, Double> highSeries  =
            IntervalPrice.extractComponent(Date.class, createSeries(), IntervalPrice.Type.High);
        final Series<Date, Double> lowSeries   =
            IntervalPrice.extractComponent(Date.class, createSeries(), IntervalPrice.Type.Low);

        for (final Series<Date, Double> series : new Series[] { openSeries, closeSeries, highSeries, lowSeries }) {
            assertEquals(3, series.size());
        }

        final Date[] expectedDates = new Date[] {
            new Date(1, Month.January,  2001),
            new Date(2, Month.February, 2002),
            new Date(3, Month.March,    2003)
        };
        final IntervalPrice[] expectedPrices = new IntervalPrice[] {
            new IntervalPrice(11, 21, 31, 41),
            new IntervalPrice(12, 22, 32, 42),
            new IntervalPrice(13, 23, 33, 43)
        };

        final Iterator<Map.Entry<Date, Double>> openIt  = openSeries.entrySet().iterator();
        final Iterator<Map.Entry<Date, Double>> closeIt = closeSeries.entrySet().iterator();
        final Iterator<Map.Entry<Date, Double>> highIt  = highSeries.entrySet().iterator();
        final Iterator<Map.Entry<Date, Double>> lowIt   = lowSeries.entrySet().iterator();

        for (int idx = 0; openIt.hasNext(); idx++) {
            final Map.Entry<Date, Double> openEntry  = openIt.next();
            final Map.Entry<Date, Double> closeEntry = closeIt.next();
            final Map.Entry<Date, Double> highEntry  = highIt.next();
            final Map.Entry<Date, Double> lowEntry   = lowIt.next();

            for (final Date dt : new Date[] {
                openEntry.getKey(), closeEntry.getKey(),
                highEntry.getKey(), lowEntry.getKey()
            }) {
                assertEquals(expectedDates[idx], dt);
            }

            assertEqualPrices(expectedPrices[idx],
                new IntervalPrice(openEntry.getValue(), closeEntry.getValue(),
                                  highEntry.getValue(), lowEntry.getValue()));
        }
    }
}

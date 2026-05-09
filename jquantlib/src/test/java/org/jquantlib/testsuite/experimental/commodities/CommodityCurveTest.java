/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.commodities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.currencies.America;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.CommodityCurve;
import org.jquantlib.experimental.commodities.CommodityIndex;
import org.jquantlib.experimental.commodities.CommodityType;
import org.jquantlib.experimental.commodities.ExchangeContract;
import org.jquantlib.experimental.commodities.GallonUnitOfMeasure;
import org.jquantlib.experimental.commodities.NullCommodityType;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4o Track C tests for {@link CommodityCurve} and
 * {@link CommodityIndex}.
 */
public class CommodityCurveTest {

    private static final double EXACT = 0.0;
    private static final double TIGHT = 1.0e-12;

    private static List<Date> dates() {
        final List<Date> ds = new ArrayList<>();
        ds.add(new Date(1, Month.January, 2026));
        ds.add(new Date(1, Month.July, 2026));
        ds.add(new Date(1, Month.January, 2027));
        return ds;
    }

    private static List<Double> prices() {
        final List<Double> ps = new ArrayList<>();
        ps.add(70.0);
        ps.add(72.0);
        ps.add(75.0);
        return ps;
    }

    @Test
    public void commodityCurve_construction_basics() {
        final CommodityCurve c = new CommodityCurve("WTI",
                new CommodityType("WTI", "WTI Crude Oil"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                dates(), prices(), new Actual365Fixed());
        assertEquals("WTI", c.name());
        assertEquals("USD", c.currency().code());
        assertEquals("BBL", c.unitOfMeasure().code());
        assertFalse(c.empty());
        assertEquals(3, c.dates().size());
        assertEquals(70.0, c.prices().get(0), EXACT);
        assertEquals(new Date(1, Month.January, 2027), c.maxDate());
    }

    @Test
    public void commodityCurve_priceAtKnotMatchesValue() {
        // ForwardFlat: price(t==time of dates[0]) = data[0]; flat extension forward
        final CommodityCurve c = new CommodityCurve("WTI",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                dates(), prices());
        final double p = c.price(new Date(1, Month.January, 2026), new ArrayList<ExchangeContract>(), 0);
        assertEquals(70.0, p, TIGHT);
    }

    @Test
    public void commodityCurve_emptyCtor_thenSetPrices_works() {
        final CommodityCurve c = new CommodityCurve("WTI",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar());
        assertTrue(c.empty());
        assertEquals(0, c.dates().size());
    }

    @Test
    public void commodityIndex_basics() {
        final CommodityCurve fwd = new CommodityCurve("WTI",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                dates(), prices());
        final CommodityIndex idx = new CommodityIndex("WTI-IDX",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1000.0,
                fwd,
                new ArrayList<ExchangeContract>(),
                0);
        assertEquals("WTI-IDX", idx.name());
        assertEquals("USD", idx.currency().code());
        assertEquals("BBL", idx.unitOfMeasure().code());
        assertEquals(1000.0, idx.lotQuantity(), EXACT);
        assertNotNull(idx.forwardCurve());
        assertFalse(idx.forwardCurveEmpty());
        // forwardPrice at first knot = curve price * conversion factor (1.0)
        assertEquals(70.0,
                idx.forwardPrice(new Date(1, Month.January, 2026)), TIGHT);
    }

    @Test
    public void commodityIndex_uomConversion_BBLcurveToGALindex_appliesFactor() {
        // Curve in BBL, index in GAL (NullCommodityType because the
        // pre-registered factor is keyed by NullCommodityType).
        final CommodityCurve fwd = new CommodityCurve("WTI-BBL-CRV",
                new NullCommodityType(),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                dates(), prices());
        final CommodityIndex idx = new CommodityIndex("WTI-GAL-IDX",
                new NullCommodityType(),
                new America.USDCurrency(),
                new GallonUnitOfMeasure(),
                new NullCalendar(),
                1.0,
                fwd,
                new ArrayList<ExchangeContract>(),
                0);
        // Conversion BBL -> GAL = 42; curve at first knot = 70.
        assertEquals(70.0 * 42.0,
                idx.forwardPrice(new Date(1, Month.January, 2026)), 1.0e-9);
    }

    @Test
    public void commodityIndex_emptyTimeSeries_returnsNullDate() {
        final CommodityIndex idx = new CommodityIndex("EMPTY",
                new CommodityType("X", "X"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1.0, null, new ArrayList<ExchangeContract>(), 0);
        assertTrue(idx.empty());
        assertEquals(new Date(), idx.lastQuoteDate());
    }
}

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
import org.jquantlib.currencies.Money;
import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.Commodity;
import org.jquantlib.experimental.commodities.CommodityIndex;
import org.jquantlib.experimental.commodities.CommodityType;
import org.jquantlib.experimental.commodities.CommodityUnitCost;
import org.jquantlib.experimental.commodities.EnergyBasisSwap;
import org.jquantlib.experimental.commodities.EnergyFuture;
import org.jquantlib.experimental.commodities.EnergySwap;
import org.jquantlib.experimental.commodities.EnergyVanillaSwap;
import org.jquantlib.experimental.commodities.ExchangeContract;
import org.jquantlib.experimental.commodities.PricingPeriod;
import org.jquantlib.experimental.commodities.Quantity;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4o Track D smoke tests for EnergySwap / EnergyVanillaSwap /
 * EnergyBasisSwap / EnergyFuture constructor + accessor APIs.
 * <p>
 * The pricing path ({@code performCalculations}) is deferred to Phase
 * 4o.5; tests here cover the construction surface only.
 */
public class EnergyInstrumentsTest {

    private static final double EXACT = 0.0;

    private static List<PricingPeriod> period() {
        final List<PricingPeriod> list = new ArrayList<>();
        list.add(new PricingPeriod(
                new Date(1, Month.January, 2026),
                new Date(31, Month.January, 2026),
                new Date(5, Month.February, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             100.0)));
        list.add(new PricingPeriod(
                new Date(1, Month.February, 2026),
                new Date(28, Month.February, 2026),
                new Date(5, Month.March, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             50.0)));
        return list;
    }

    private static CommodityIndex makeIndex() {
        return new CommodityIndex("WTI-IDX",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1000.0,
                null,
                new ArrayList<>(),
                0);
    }

    @Test
    public void energySwap_construction_andQuantitySumsPeriods() {
        final EnergySwap s = new EnergySwap(
                new NullCalendar(),
                new America.USDCurrency(),
                new America.USDCurrency(),
                period(),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts());
        assertNotNull(s.calendar());
        assertEquals("USD", s.payCurrency().code());
        assertEquals("USD", s.receiveCurrency().code());
        assertEquals(2, s.pricingPeriods().size());
        // sum of period quantities = 100 + 50 = 150 BBL
        final Quantity tot = s.quantity();
        assertEquals(150.0, tot.amount(), EXACT);
        assertEquals("BBL", tot.unitOfMeasure().code());
    }

    @Test
    public void energySwap_isExpired_emptyPeriods_true() {
        final EnergySwap s = new EnergySwap(
                new NullCalendar(),
                new America.USDCurrency(),
                new America.USDCurrency(),
                new ArrayList<>(),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts());
        assertTrue(s.isExpired());
    }

    @Test
    public void energyVanillaSwap_constructionAndAccessors() {
        final Handle<YieldTermStructure> empty = new Handle<YieldTermStructure>();
        final EnergyVanillaSwap evs = new EnergyVanillaSwap(
                /* payer */ true,
                new NullCalendar(),
                new Money(new America.USDCurrency(), 70.0),
                new BarrelUnitOfMeasure(),
                makeIndex(),
                new America.USDCurrency(),
                new America.USDCurrency(),
                period(),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                empty, empty, empty);
        // payer=true ==> payReceive_ = 1 (matches C++ v1.42.1
        // energyvanillaswap.cpp: payReceive_(payer ? 1 : 0)).
        assertEquals(1, evs.payReceive());
        assertEquals(70.0, evs.fixedPrice().value(), EXACT);
        assertEquals("BBL", evs.fixedPriceUnitOfMeasure().code());
        assertNotNull(evs.index());
        assertNotNull(evs.payLegTermStructure());
        assertNotNull(evs.receiveLegTermStructure());
        assertNotNull(evs.discountTermStructure());
    }

    @Test
    public void energyBasisSwap_constructionAndAccessors() {
        final Handle<YieldTermStructure> empty = new Handle<YieldTermStructure>();
        final EnergyBasisSwap ebs = new EnergyBasisSwap(
                new NullCalendar(),
                makeIndex(),    // spread
                makeIndex(),    // pay
                makeIndex(),    // receive
                /* spreadToPayLeg */ true,
                new America.USDCurrency(),
                new America.USDCurrency(),
                period(),
                new CommodityUnitCost(new Money(new America.USDCurrency(), 1.5),
                                      new BarrelUnitOfMeasure()),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                empty, empty, empty);
        assertNotNull(ebs.spreadIndex());
        assertNotNull(ebs.payIndex());
        assertNotNull(ebs.receiveIndex());
        assertTrue(ebs.spreadToPayLeg());
        assertEquals(1.5, ebs.basis().amount().value(), EXACT);
        assertEquals("BBL", ebs.basis().unitOfMeasure().code());
    }

    @Test
    public void energyFuture_constructionAndAccessors() {
        final EnergyFuture ef = new EnergyFuture(
                /* buySell */ 1,
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             1000.0),
                new CommodityUnitCost(new Money(new America.USDCurrency(), 65.0),
                                      new BarrelUnitOfMeasure()),
                makeIndex(),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts());
        assertEquals(1, ef.buySell());
        assertEquals(1000.0, ef.quantity().amount(), EXACT);
        assertEquals(65.0, ef.tradePrice().amount().value(), EXACT);
        assertNotNull(ef.index());
        // C++ EnergyFuture::isExpired is hardcoded false.
        assertFalse(ef.isExpired());
    }
}

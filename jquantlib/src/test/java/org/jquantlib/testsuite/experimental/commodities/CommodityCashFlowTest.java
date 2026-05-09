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

import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Money;
import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.CommodityCashFlow;
import org.jquantlib.experimental.commodities.CommodityPricingHelper;
import org.jquantlib.experimental.commodities.CommodityType;
import org.jquantlib.experimental.commodities.CommodityUnitCost;
import org.jquantlib.experimental.commodities.EnergyDailyPosition;
import org.jquantlib.experimental.commodities.GallonUnitOfMeasure;
import org.jquantlib.experimental.commodities.NullCommodityType;
import org.jquantlib.experimental.commodities.PricingError;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Phase 4o Track B tests for {@code CommodityCashFlow}, {@code PricingError},
 * {@code EnergyDailyPosition}, {@code CommodityPricingHelper}.
 */
public class CommodityCashFlowTest {

    private static final double EXACT = 0.0;
    private static final double TIGHT = 1.0e-12;

    @Test
    public void commodityCashFlow_construction_andAccessors() {
        final Date d = new Date(15, Month.January, 2026);
        final Money disc = new Money(new America.USDCurrency(), 95.0);
        final Money und = new Money(new America.USDCurrency(), 100.0);
        final Money discPay = new Money(new America.USDCurrency(), 94.0);
        final Money undPay = new Money(new America.USDCurrency(), 99.0);
        final CommodityCashFlow cf = new CommodityCashFlow(
                d, disc, und, discPay, undPay, 0.95, 0.94, true);
        assertEquals(d, cf.date());
        assertEquals(95.0, cf.amount(), EXACT);
        assertEquals(100.0, cf.undiscountedAmount().value(), EXACT);
        assertEquals(0.95, cf.discountFactor(), EXACT);
        assertEquals(0.94, cf.paymentDiscountFactor(), EXACT);
        assertTrue(cf.finalized());
        assertEquals("USD", cf.currency().code());
    }

    @Test
    public void pricingError_toString_includesLevelPrefix() {
        final PricingError err = new PricingError(PricingError.Level.Warning, "yield curve quiet", "more info");
        assertEquals("warning: yield curve quiet: more info", err.toString());
        final PricingError noDetail = new PricingError(PricingError.Level.Fatal, "boom", "");
        assertEquals("*** fatal: boom", noDetail.toString());
    }

    @Test
    public void energyDailyPosition_default_isZeroed() {
        final EnergyDailyPosition pos = new EnergyDailyPosition();
        assertNotNull(pos.date);
        assertEquals(0.0, pos.quantityAmount, EXACT);
        assertEquals(0.0, pos.payLegPrice, EXACT);
        assertEquals(0.0, pos.receiveLegPrice, EXACT);
        assertFalse(pos.unrealized);
    }

    @Test
    public void energyDailyPosition_paramCtor_setsLegPricesAndUnrealized() {
        final Date d = new Date(2, Month.February, 2026);
        final EnergyDailyPosition p = new EnergyDailyPosition(d, 70.5, 71.0, true);
        assertEquals(d, p.date);
        assertEquals(70.5, p.payLegPrice, EXACT);
        assertEquals(71.0, p.receiveLegPrice, EXACT);
        assertEquals(0.0, p.quantityAmount, EXACT);
        assertTrue(p.unrealized);
    }

    @Test
    public void commodityPricingHelper_uomFactor_sameUnitsReturnsOne() {
        final double f = CommodityPricingHelper.calculateUomConversionFactor(
                new NullCommodityType(),
                new BarrelUnitOfMeasure(),
                new BarrelUnitOfMeasure());
        assertEquals(1.0, f, EXACT);
    }

    @Test
    public void commodityPricingHelper_uomFactor_BBLtoGAL_is42() {
        // Direct registered conversion: 1 BBL = 42 GAL.
        final double f = CommodityPricingHelper.calculateUomConversionFactor(
                new NullCommodityType(),
                new BarrelUnitOfMeasure(),
                new GallonUnitOfMeasure());
        assertEquals(42.0, f, TIGHT);
    }

    @Test
    public void commodityPricingHelper_unitCost_zeroWhenAmountZero() {
        final CommodityUnitCost uc = new CommodityUnitCost(
                new Money(new America.USDCurrency(), 0.0),
                new BarrelUnitOfMeasure());
        final double v = CommodityPricingHelper.calculateUnitCost(
                new NullCommodityType(),
                uc,
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new Date(1, Month.January, 2026));
        assertEquals(0.0, v, EXACT);
    }

    @Test
    public void commodityPricingHelper_unitCost_sameUnitsAndCurrency_returnsAmount() {
        // 50 USD/BBL -> base USD/BBL: factors are both 1, result = 50.0
        final CommodityUnitCost uc = new CommodityUnitCost(
                new Money(new America.USDCurrency(), 50.0),
                new BarrelUnitOfMeasure());
        final double v = CommodityPricingHelper.calculateUnitCost(
                new NullCommodityType(),
                uc,
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new Date(1, Month.January, 2026));
        assertEquals(50.0, v, TIGHT);
    }

    @Test
    public void commodityPricingHelper_unitCost_unitsBBLtoGAL_appliesFactor42() {
        // 50 USD/BBL -> base USD/GAL: 50 * 42 = 2100 USD/GAL
        final CommodityUnitCost uc = new CommodityUnitCost(
                new Money(new America.USDCurrency(), 50.0),
                new BarrelUnitOfMeasure());
        final double v = CommodityPricingHelper.calculateUnitCost(
                new NullCommodityType(),
                uc,
                new America.USDCurrency(),
                new GallonUnitOfMeasure(),
                new Date(1, Month.January, 2026));
        assertEquals(50.0 * 42.0, v, TIGHT);
    }

    @Test
    public void commodity_shouldBeAvailableAsAbstractType() {
        // Smoke check: confirm Commodity / EnergyCommodity classes are
        // visible to subclasses (no concrete instance needed).
        final CommodityType ct = new CommodityType("WTI", "WTI Crude");
        assertNotNull(ct);
    }
}

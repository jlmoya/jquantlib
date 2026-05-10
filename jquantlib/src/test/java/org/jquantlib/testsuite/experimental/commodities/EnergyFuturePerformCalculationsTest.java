/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.commodities;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.jquantlib.Settings;
import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Money;
import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.Commodity;
import org.jquantlib.experimental.commodities.CommodityIndex;
import org.jquantlib.experimental.commodities.CommoditySettings;
import org.jquantlib.experimental.commodities.CommodityType;
import org.jquantlib.experimental.commodities.CommodityUnitCost;
import org.jquantlib.experimental.commodities.EnergyFuture;
import org.jquantlib.experimental.commodities.ExchangeContract;
import org.jquantlib.experimental.commodities.Quantity;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4o.5 — coverage for {@link EnergyFuture#performCalculations()}.
 * <p>
 * Cross-validates the Java port against C++ v1.42.1 {@code energyfuture.cpp}.
 * <p>
 * The arithmetic in C++ is:
 * <pre>
 *   quantityUomFactor   = uomFactor(qtyUom -> baseUom) * index.lotQuantity()
 *   quantityAmount      = quantity.amount * quantityUomFactor
 *   tradePriceValue     = tradePrice.amount * priceUomFactor * priceFxFactor
 *   quotePriceValue     = quoteValue * indexUomFactor * indexFxFactor
 *   delta               = (quotePriceValue - tradePriceValue)
 *                          * quantityAmount * index.lotQuantity() * buySell
 *   NPV                 = delta - sum(secondaryCostAmounts)
 * </pre>
 *
 * Note that lotQuantity multiplies the volume twice in C++ — once via
 * {@code quantityAmount} and once explicitly in the {@code delta} formula —
 * which the Java port faithfully reproduces.
 */
public class EnergyFuturePerformCalculationsTest {

    private static final double EXACT = 0.0;
    private static final double TIGHT = 1e-12;

    private static CommodityIndex makeIndex() {
        return new CommodityIndex("WTI-FUT-IDX",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1000.0,
                null,
                new ArrayList<ExchangeContract>(),
                0);
    }

    /** Seed an empty history so Index.addFixing finds a non-null TimeSeries. */
    private static void seedHistory(final CommodityIndex idx) {
        org.jquantlib.indexes.IndexManager.getInstance().setHistory(idx.name(),
                new org.jquantlib.time.TimeSeries<Double>(Double.class));
    }

    @Test
    public void buy_quoteAboveTrade_npvIsPositive() {
        // baseCurrency = USD, baseUnitOfMeasure = BBL.
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex idx = makeIndex();
        seedHistory(idx);
        idx.addFixing(eval, 70.0, true);

        final EnergyFuture ef = new EnergyFuture(
                /* buySell */ 1,
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             1000.0),
                new CommodityUnitCost(
                        new Money(new America.USDCurrency(), 65.0),
                        new BarrelUnitOfMeasure()),
                idx,
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts());

        // Reference (C++ formula above):
        //   uomFactor = 1, fxFactor = 1, lotQuantity = 1000.
        //   quantityAmount = 1000 * (1 * 1000) = 1,000,000.
        //   tradePriceValue = 65 * 1 * 1 = 65.
        //   quotePriceValue = 70 * 1 * 1 = 70.
        //   delta = (70 - 65) * 1,000,000 * 1000 * 1 = 5,000,000,000.
        final double npv = ef.NPV();
        assertEquals(5_000_000_000.0, npv, TIGHT * 5_000_000_000.0);
    }

    @Test
    public void sell_quoteAboveTrade_npvIsNegative() {
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex idx = makeIndex();
        seedHistory(idx);
        idx.addFixing(eval, 70.0, true);

        // buySell = -1 (short). Same numbers, opposite sign.
        final EnergyFuture ef = new EnergyFuture(
                /* buySell */ -1,
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             1000.0),
                new CommodityUnitCost(
                        new Money(new America.USDCurrency(), 65.0),
                        new BarrelUnitOfMeasure()),
                idx,
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts());
        assertEquals(-5_000_000_000.0, ef.NPV(), TIGHT * 5_000_000_000.0);
    }

    @Test
    public void buy_quoteEqualsTrade_npvIsZero() {
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex idx = makeIndex();
        seedHistory(idx);
        idx.addFixing(eval, 65.0, true);

        final EnergyFuture ef = new EnergyFuture(
                1,
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             1000.0),
                new CommodityUnitCost(
                        new Money(new America.USDCurrency(), 65.0),
                        new BarrelUnitOfMeasure()),
                idx,
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts());
        assertEquals(0.0, ef.NPV(), EXACT);
    }

    @Test
    public void buy_secondaryCostMoney_subtractedFromNpv() {
        // Verifies that Money-typed secondary costs are converted (no FX
        // here since same currency) and subtracted from the NPV.
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex idx = makeIndex();
        seedHistory(idx);
        idx.addFixing(eval, 70.0, true);

        final Commodity.SecondaryCosts costs = new Commodity.SecondaryCosts();
        costs.put("brokerage", new Money(new America.USDCurrency(), 12_345.0));

        final EnergyFuture ef = new EnergyFuture(
                1,
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             1000.0),
                new CommodityUnitCost(
                        new Money(new America.USDCurrency(), 65.0),
                        new BarrelUnitOfMeasure()),
                idx,
                new CommodityType("WTI", "WTI"),
                costs);
        // delta = 5,000,000,000 minus 12,345 brokerage = 4,999,987,655.
        assertEquals(5_000_000_000.0 - 12_345.0, ef.NPV(),
                TIGHT * 5_000_000_000.0);
        assertEquals(1, ef.secondaryCostAmounts().size());
        assertEquals(12_345.0, ef.secondaryCostAmounts().get("brokerage").value(), EXACT);
    }
}

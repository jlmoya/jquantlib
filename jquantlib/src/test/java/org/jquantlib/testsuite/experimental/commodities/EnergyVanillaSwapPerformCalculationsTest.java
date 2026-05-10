/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.commodities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Money;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.Commodity;
import org.jquantlib.experimental.commodities.CommodityCashFlow;
import org.jquantlib.experimental.commodities.CommodityIndex;
import org.jquantlib.experimental.commodities.CommoditySettings;
import org.jquantlib.experimental.commodities.CommodityType;
import org.jquantlib.experimental.commodities.EnergyVanillaSwap;
import org.jquantlib.experimental.commodities.ExchangeContract;
import org.jquantlib.experimental.commodities.PricingPeriod;
import org.jquantlib.experimental.commodities.Quantity;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeSeries;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4o.5 — coverage for {@link EnergyVanillaSwap#performCalculations()}.
 * <p>
 * Cross-validates against the literal arithmetic in C++ v1.42.1
 * {@code energyvanillaswap.cpp}.
 * <p>
 * The test setup uses {@link NullCalendar} (every day is a business day),
 * USD/BBL = base currency / base UoM (so all UoM and FX factors collapse
 * to 1.0), and a flat 0% forward yield curve (so all discount factors
 * collapse to 1.0). That makes the NPV equal to a hand-computable
 * (fixed - floating) leg combination per period.
 */
public class EnergyVanillaSwapPerformCalculationsTest {

    private static final double EXACT = 0.0;
    private static final double TIGHT = 1e-10;

    private static CommodityIndex makeIndex(final String name) {
        return new CommodityIndex(name,
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1.0,                                 // lotQuantity = 1 to keep sums small
                null,                                // no forward curve
                new ArrayList<ExchangeContract>(),
                0);
    }

    private static void seedHistory(final CommodityIndex idx) {
        IndexManager.getInstance().setHistory(idx.name(),
                new TimeSeries<Double>(Double.class));
    }

    private static Handle<YieldTermStructure> flatZero(final Date eval) {
        return new Handle<YieldTermStructure>(
                new FlatForward(eval, 0.0, new Actual365Fixed()));
    }

    @Test
    public void payer_floatingAboveFixed_npvIsPositive_andCashflowWritten() {
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex idx = makeIndex("WTI-VAN-A");
        seedHistory(idx);
        // 3-day period: Jan 16, 17, 18 — all >= eval+1 so quote falls back to
        // forward... but we have no forward curve. Add fixings instead and
        // bump lastQuoteDate past stepDate.
        idx.addFixing(new Date(16, Month.January, 2026), 70.0, true);
        idx.addFixing(new Date(17, Month.January, 2026), 72.0, true);
        idx.addFixing(new Date(18, Month.January, 2026), 71.0, true);

        // Payment date the next day (eval+4) -> ge(eval+2) -> uses term-structure
        // discount, but with a 0% flat curve the discount is 1.0.
        final List<PricingPeriod> periods = new ArrayList<PricingPeriod>();
        periods.add(new PricingPeriod(
                new Date(16, Month.January, 2026),
                new Date(18, Month.January, 2026),
                new Date(19, Month.January, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(),
                             /* total */ 300.0)));

        final EnergyVanillaSwap evs = new EnergyVanillaSwap(
                /* payer */ true,
                new NullCalendar(),
                new Money(new America.USDCurrency(), /* fixedPrice */ 65.0),
                new BarrelUnitOfMeasure(),
                idx,
                new America.USDCurrency(),
                new America.USDCurrency(),
                periods,
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                flatZero(eval), flatZero(eval), flatZero(eval));

        // Hand calc:
        //   periodDayCount = 3, avgDailyQty = 300 / 3 = 100.
        //   payer -> payLeg = fixed = 65; receiveLeg(t) = quote(t) = 70/72/71.
        //   payLegValue   = -65 * 100 * 3 = -19,500.
        //   receiveLegVal = (70 + 72 + 71) * 100 = 21,300.
        //   discountFactor = 1 (0% flat). dDelta == uDelta.
        //   uDelta = 21,300 + (-19,500) = 1,800.
        //   No secondary costs -> NPV = 1,800.
        final double npv = evs.NPV();
        assertEquals(1_800.0, npv, TIGHT * 1_800.0);

        // Inspect the cashflow.
        assertEquals(1, evs.paymentCashFlows().size());
        final CommodityCashFlow cf = evs.paymentCashFlows().get(
                new Date(19, Month.January, 2026));
        assertNotNull(cf);
        assertEquals(1_800.0, cf.undiscountedAmount().value(), TIGHT * 1_800.0);
        assertEquals(1_800.0, cf.discountedAmount().value(), TIGHT * 1_800.0);
        assertEquals(1.0, cf.discountFactor(), 1e-9);

        // Verify dailyPositions populated for all 3 days.
        assertEquals(3, evs.dailyPositions().size());
        assertEquals(100.0,
                evs.dailyPositions().get(new Date(16, Month.January, 2026)).quantityAmount,
                EXACT);
        assertTrue(evs.dailyPositions().get(new Date(16, Month.January, 2026)).unrealized);

        // additionalResults_ has the dailyPositions snapshot.
        assertNotNull(evs.additionalResult("dailyPositions"));
    }

    @Test
    public void receiver_floatingAboveFixed_npvIsNegative() {
        // Same numbers, payer=false (receiver of fixed -> we receive 65, pay 70/72/71).
        // payReceive_ = 0 (per C++ payer ? 1 : 0).
        // Then payReceive_ > 0 == false, so payLeg = floating, receiveLeg = fixed.
        // payLegValue   = -(70+72+71) * 100 = -21,300.
        // receiveLegVal = 65 * 100 * 3 = 19,500.
        // uDelta = 19,500 + (-21,300) = -1,800.  NPV = -1,800.
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex idx = makeIndex("WTI-VAN-B");
        seedHistory(idx);
        idx.addFixing(new Date(16, Month.January, 2026), 70.0, true);
        idx.addFixing(new Date(17, Month.January, 2026), 72.0, true);
        idx.addFixing(new Date(18, Month.January, 2026), 71.0, true);

        final List<PricingPeriod> periods = new ArrayList<PricingPeriod>();
        periods.add(new PricingPeriod(
                new Date(16, Month.January, 2026),
                new Date(18, Month.January, 2026),
                new Date(19, Month.January, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(), 300.0)));

        final EnergyVanillaSwap evs = new EnergyVanillaSwap(
                /* payer */ false,
                new NullCalendar(),
                new Money(new America.USDCurrency(), 65.0),
                new BarrelUnitOfMeasure(),
                idx,
                new America.USDCurrency(),
                new America.USDCurrency(),
                periods,
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                flatZero(eval), flatZero(eval), flatZero(eval));

        assertEquals(0, evs.payReceive());
        assertEquals(-1_800.0, evs.NPV(), TIGHT * 1_800.0);
    }

    @Test
    public void paymentDateBeforeEvalPlus2_skipsDiscountFactorBranch() {
        // paymentDate = eval+1 -> not ge(eval+2) -> discountFactor stays 1.0
        // even if the term structure would otherwise produce something else.
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex idx = makeIndex("WTI-VAN-C");
        seedHistory(idx);
        // Single-day period to keep the math trivial.
        idx.addFixing(new Date(15, Month.January, 2026), 70.0, true);

        final List<PricingPeriod> periods = new ArrayList<PricingPeriod>();
        periods.add(new PricingPeriod(
                new Date(15, Month.January, 2026),
                new Date(15, Month.January, 2026),
                new Date(16, Month.January, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(), 100.0)));

        // Use a 5% flat curve - if the branch were taken, the discount factor
        // would deflate uDelta. Verify it stays at 1.0.
        final Handle<YieldTermStructure> ts5pct =
                new Handle<YieldTermStructure>(
                        new FlatForward(eval, 0.05, new Actual365Fixed()));
        final EnergyVanillaSwap evs = new EnergyVanillaSwap(
                /* payer */ true,
                new NullCalendar(),
                new Money(new America.USDCurrency(), 65.0),
                new BarrelUnitOfMeasure(),
                idx,
                new America.USDCurrency(),
                new America.USDCurrency(),
                periods,
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                ts5pct, ts5pct, ts5pct);

        // payReceive_=1, dailyAvgQty = 100/1 = 100, fixed=65, floating=70.
        // payLegVal = -65*100 = -6500, receiveLegVal = 70*100 = 7000.
        // uDelta = 500. discountFactor = 1.0 (skipped branch). NPV = 500.
        assertEquals(500.0, evs.NPV(), TIGHT * 500.0);
        final CommodityCashFlow cf = evs.paymentCashFlows().get(
                new Date(16, Month.January, 2026));
        assertEquals(1.0, cf.discountFactor(), EXACT);
    }
}

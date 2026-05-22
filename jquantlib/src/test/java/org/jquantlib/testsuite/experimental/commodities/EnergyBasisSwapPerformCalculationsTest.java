/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.commodities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

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
import org.jquantlib.experimental.commodities.CommodityUnitCost;
import org.jquantlib.experimental.commodities.EnergyBasisSwap;
import org.jquantlib.experimental.commodities.ExchangeContract;
import org.jquantlib.experimental.commodities.PricingPeriod;
import org.jquantlib.experimental.commodities.Quantity;
import org.jquantlib.indexes.IndexManager;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeSeries;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4o.5 — coverage for {@link EnergyBasisSwap#performCalculations()}.
 * <p>
 * Cross-validates against the literal arithmetic in C++ v1.42.1
 * {@code energybasisswap.cpp}. As with the vanilla-swap test we use
 * NullCalendar / USD-base / BBL-base / 0% flat-forward to keep all
 * conversion factors and discount factors at 1.0, leaving a clean
 * hand-checked NPV per period.
 */
public class EnergyBasisSwapPerformCalculationsTest {

    private static final double TIGHT = 1e-10;

    private static CommodityIndex makeIndex(final String name) {
        return new CommodityIndex(name,
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1.0,
                null,
                new ArrayList<>(),
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
    public void receiveAbovePay_zeroBasis_npvIsPositive_andCashflowWritten() {
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex spread = makeIndex("WTI-SPR-A");
        final CommodityIndex pay = makeIndex("WTI-PAY-A");
        final CommodityIndex receive = makeIndex("WTI-RCV-A");
        seedHistory(spread); seedHistory(pay); seedHistory(receive);

        // 2-day period.
        final Date d1 = new Date(16, Month.January, 2026);
        final Date d2 = new Date(17, Month.January, 2026);
        pay.addFixing(d1, 60.0, true); pay.addFixing(d2, 62.0, true);
        receive.addFixing(d1, 70.0, true); receive.addFixing(d2, 72.0, true);

        final List<PricingPeriod> periods = new ArrayList<>();
        periods.add(new PricingPeriod(d1, d2,
                new Date(18, Month.January, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(), 200.0)));

        final EnergyBasisSwap ebs = new EnergyBasisSwap(
                new NullCalendar(),
                spread, pay, receive,
                /* spreadToPayLeg */ true,
                new America.USDCurrency(),
                new America.USDCurrency(),
                periods,
                new CommodityUnitCost(new Money(new America.USDCurrency(), 0.0),
                                      new BarrelUnitOfMeasure()),  // basis = 0
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                flatZero(eval), flatZero(eval), flatZero(eval));

        // Hand calc:
        //   periodDayCount = 2, avgDailyQty = 200/2 = 100.
        //   payLeg = pay quotes = 60 + 62 = 122; receiveLeg = 70 + 72 = 142.
        //   payLegValue   = -122 * 100 = -12,200.
        //   receiveLegVal = 142 * 100 = 14,200.
        //   uDelta = 14,200 - 12,200 = 2,000.
        //   discount = 1, NPV = 2,000.
        assertEquals(2_000.0, ebs.NPV(), TIGHT * 2_000.0);
        final CommodityCashFlow cf = ebs.paymentCashFlows().get(
                new Date(18, Month.January, 2026));
        assertNotNull(cf);
        assertEquals(2_000.0, cf.undiscountedAmount().value(), TIGHT * 2_000.0);
        assertEquals(2_000.0, cf.discountedAmount().value(), TIGHT * 2_000.0);
    }

    @Test
    public void basisAddedToReceiveLeg_increasesNpv() {
        // Same setup as above but with a 5 USD/BBL basis applied to the
        // receive leg (spreadToPayLeg=false). receiveLeg gains 5*100*2 = 1000.
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex spread = makeIndex("WTI-SPR-B");
        final CommodityIndex pay = makeIndex("WTI-PAY-B");
        final CommodityIndex receive = makeIndex("WTI-RCV-B");
        seedHistory(spread); seedHistory(pay); seedHistory(receive);

        final Date d1 = new Date(16, Month.January, 2026);
        final Date d2 = new Date(17, Month.January, 2026);
        pay.addFixing(d1, 60.0, true); pay.addFixing(d2, 62.0, true);
        receive.addFixing(d1, 70.0, true); receive.addFixing(d2, 72.0, true);

        final List<PricingPeriod> periods = new ArrayList<>();
        periods.add(new PricingPeriod(d1, d2,
                new Date(18, Month.January, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(), 200.0)));

        final EnergyBasisSwap ebs = new EnergyBasisSwap(
                new NullCalendar(),
                spread, pay, receive,
                /* spreadToPayLeg */ false,
                new America.USDCurrency(),
                new America.USDCurrency(),
                periods,
                new CommodityUnitCost(new Money(new America.USDCurrency(), 5.0),
                                      new BarrelUnitOfMeasure()),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                flatZero(eval), flatZero(eval), flatZero(eval));

        // basis = 5 added to receive leg:
        //   receiveLeg(t=d1) = 70 + 5 = 75; (t=d2) = 72 + 5 = 77.
        //   receiveLegVal = (75 + 77) * 100 = 15,200.
        //   payLegVal = -(60 + 62) * 100 = -12,200.
        //   uDelta = 3,000. NPV = 3,000.
        assertEquals(3_000.0, ebs.NPV(), TIGHT * 3_000.0);
    }

    @Test
    public void basisAddedToPayLeg_decreasesNpv() {
        // 5 USD/BBL basis on pay leg -> increases pay-leg cost by 1000.
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final CommodityIndex spread = makeIndex("WTI-SPR-C");
        final CommodityIndex pay = makeIndex("WTI-PAY-C");
        final CommodityIndex receive = makeIndex("WTI-RCV-C");
        seedHistory(spread); seedHistory(pay); seedHistory(receive);

        final Date d1 = new Date(16, Month.January, 2026);
        final Date d2 = new Date(17, Month.January, 2026);
        pay.addFixing(d1, 60.0, true); pay.addFixing(d2, 62.0, true);
        receive.addFixing(d1, 70.0, true); receive.addFixing(d2, 72.0, true);

        final List<PricingPeriod> periods = new ArrayList<>();
        periods.add(new PricingPeriod(d1, d2,
                new Date(18, Month.January, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(), 200.0)));

        final EnergyBasisSwap ebs = new EnergyBasisSwap(
                new NullCalendar(),
                spread, pay, receive,
                /* spreadToPayLeg */ true,
                new America.USDCurrency(),
                new America.USDCurrency(),
                periods,
                new CommodityUnitCost(new Money(new America.USDCurrency(), 5.0),
                                      new BarrelUnitOfMeasure()),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                flatZero(eval), flatZero(eval), flatZero(eval));

        // payLeg(t=d1) = 60+5 = 65; (t=d2) = 62+5 = 67.
        // payLegVal = -(65+67) * 100 = -13,200.
        // receiveLegVal = (70+72) * 100 = 14,200.
        // uDelta = 1,000. NPV = 1,000.
        assertEquals(1_000.0, ebs.NPV(), TIGHT * 1_000.0);
    }

    @Test
    public void emptyIndices_emptyForwardCurves_throwsLibraryException() {
        // payIndex has no fixings AND its forward curve is empty -> matches
        // C++ QL_FAIL("index ... does not have any quotes or forward prices").
        CommoditySettings.getInstance().setCurrency(new America.USDCurrency());
        CommoditySettings.getInstance().setUnitOfMeasure(new BarrelUnitOfMeasure());
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        // Build with explicit empty CommodityCurve so forwardCurveEmpty()
        // returns true (matches C++ branch).
        final org.jquantlib.experimental.commodities.CommodityCurve emptyCurve =
                new org.jquantlib.experimental.commodities.CommodityCurve(
                        "EMPTY", new CommodityType("WTI", "WTI"),
                        new America.USDCurrency(),
                        new BarrelUnitOfMeasure(),
                        new NullCalendar());
        final CommodityIndex spread = new CommodityIndex("WTI-SPR-D",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1.0, emptyCurve,
                new ArrayList<>(), 0);
        final CommodityIndex pay = new CommodityIndex("WTI-PAY-D",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1.0, emptyCurve,
                new ArrayList<>(), 0);
        final CommodityIndex receive = new CommodityIndex("WTI-RCV-D",
                new CommodityType("WTI", "WTI"),
                new America.USDCurrency(),
                new BarrelUnitOfMeasure(),
                new NullCalendar(),
                1.0, emptyCurve,
                new ArrayList<>(), 0);
        seedHistory(spread); seedHistory(pay); seedHistory(receive);
        // pay and receive have empty fixings AND empty forward curve.

        final Date d1 = new Date(16, Month.January, 2026);
        final List<PricingPeriod> periods = new ArrayList<>();
        periods.add(new PricingPeriod(d1, d1,
                new Date(17, Month.January, 2026),
                new Quantity(new CommodityType("WTI", "WTI"),
                             new BarrelUnitOfMeasure(), 100.0)));

        final EnergyBasisSwap ebs = new EnergyBasisSwap(
                new NullCalendar(),
                spread, pay, receive,
                true,
                new America.USDCurrency(),
                new America.USDCurrency(),
                periods,
                new CommodityUnitCost(new Money(new America.USDCurrency(), 0.0),
                                      new BarrelUnitOfMeasure()),
                new CommodityType("WTI", "WTI"),
                new Commodity.SecondaryCosts(),
                flatZero(eval), flatZero(eval), flatZero(eval));
        try {
            ebs.NPV();
            fail("expected LibraryException for empty indices without forward curves");
        } catch (final LibraryException e) {
            assertEquals(true, e.getMessage().contains("does not have any quotes"));
        }
    }
}

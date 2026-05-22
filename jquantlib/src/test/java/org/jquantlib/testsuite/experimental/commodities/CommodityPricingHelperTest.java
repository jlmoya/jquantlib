/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.commodities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.experimental.commodities.BarrelUnitOfMeasure;
import org.jquantlib.experimental.commodities.CommodityPricingHelper;
import org.jquantlib.experimental.commodities.CommodityType;
import org.jquantlib.experimental.commodities.EnergyCommodity.DeliverySchedule;
import org.jquantlib.experimental.commodities.EnergyCommodity.QuantityPeriodicity;
import org.jquantlib.experimental.commodities.PaymentTerm;
import org.jquantlib.experimental.commodities.PricingPeriod;
import org.jquantlib.experimental.commodities.Quantity;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4o.5 — coverage for {@link CommodityPricingHelper#createPricingPeriods}.
 * <p>
 * Mirrors the C++ behaviour from QuantLib v1.42.1 {@code commoditypricinghelpers.cpp}:
 * Monthly + PerMonth and Daily + PerDay are the supported combinations; all
 * other combinations (and any other delivery schedule) {@code QL_FAIL}.
 */
public class CommodityPricingHelperTest {

    private static PaymentTerm trailingPaymentTerm() {
        return new PaymentTerm("test-pt",
                PaymentTerm.EventType.PricingDate, 5, new NullCalendar());
    }

    @Test
    public void monthly_perMonth_partitionsByMonthEnd_andPaysFiveDaysAfter() {
        final Date start = new Date(1, Month.January, 2026);
        final Date end = new Date(1, Month.April, 2026);
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final Quantity q = new Quantity(wti, new BarrelUnitOfMeasure(), 1000.0);
        final List<PricingPeriod> out = new ArrayList<>();
        CommodityPricingHelper.createPricingPeriods(start, end, q,
                DeliverySchedule.Monthly, QuantityPeriodicity.PerMonth,
                trailingPaymentTerm(), out);

        // Three months -> three periods.
        assertEquals(3, out.size());
        // First period: Jan 1 .. Jan 31, paid Feb 5.
        final PricingPeriod p0 = out.get(0);
        assertTrue(p0.startDate().eq(new Date(1, Month.January, 2026)));
        assertTrue(p0.endDate().eq(new Date(31, Month.January, 2026)));
        assertTrue(p0.paymentDate().eq(new Date(5, Month.February, 2026)));
        assertEquals(1000.0, p0.quantity().amount(), 0.0);
        // Each Monthly+PerMonth period carries the full quantity.
        assertEquals(1000.0, out.get(1).quantity().amount(), 0.0);
        assertEquals(1000.0, out.get(2).quantity().amount(), 0.0);
        // Periods are contiguous and span [start, end).
        assertTrue(out.get(2).endDate().lt(end));
        assertTrue(out.get(0).endDate().add(1).eq(out.get(1).startDate()));
    }

    @Test
    public void daily_perDay_scalesQuantityByDays() {
        final Date start = new Date(1, Month.January, 2026);
        final Date end = new Date(1, Month.March, 2026);
        final CommodityType wti = new CommodityType("WTI", "WTI");
        // perDay quantity = 100 BBL/day.
        final Quantity dailyQty = new Quantity(wti, new BarrelUnitOfMeasure(), 100.0);
        final List<PricingPeriod> out = new ArrayList<>();
        CommodityPricingHelper.createPricingPeriods(start, end, dailyQty,
                DeliverySchedule.Daily, QuantityPeriodicity.PerDay,
                trailingPaymentTerm(), out);

        // Two month-long periods.
        assertEquals(2, out.size());
        // Period 0 spans Jan 1 .. Jan 31 -> 30 days delta -> 100 * 30 = 3000 BBL.
        // (C++: Quantity = q * (periodEndDate - periodStartDate)).
        final PricingPeriod p0 = out.get(0);
        assertTrue(p0.startDate().eq(new Date(1, Month.January, 2026)));
        assertTrue(p0.endDate().eq(new Date(31, Month.January, 2026)));
        assertEquals(3000.0, p0.quantity().amount(), 0.0);
        // Period 1 spans Feb 1 .. Feb 28 -> 27 days delta -> 100 * 27 = 2700 BBL.
        final PricingPeriod p1 = out.get(1);
        assertTrue(p1.startDate().eq(new Date(1, Month.February, 2026)));
        assertTrue(p1.endDate().eq(new Date(28, Month.February, 2026)));
        assertEquals(2700.0, p1.quantity().amount(), 0.0);
    }

    @Test
    public void monthly_perDay_throws_invalidCombination() {
        final Date start = new Date(1, Month.January, 2026);
        final Date end = new Date(1, Month.April, 2026);
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final Quantity q = new Quantity(wti, new BarrelUnitOfMeasure(), 100.0);
        final List<PricingPeriod> out = new ArrayList<>();
        try {
            CommodityPricingHelper.createPricingPeriods(start, end, q,
                    DeliverySchedule.Monthly, QuantityPeriodicity.PerDay,
                    trailingPaymentTerm(), out);
            fail("expected LibraryException for Monthly+PerDay");
        } catch (final LibraryException e) {
            assertTrue(e.getMessage().toLowerCase().contains("invalid"));
        }
    }

    @Test
    public void daily_perMonth_throws_invalidCombination() {
        final Date start = new Date(1, Month.January, 2026);
        final Date end = new Date(1, Month.April, 2026);
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final Quantity q = new Quantity(wti, new BarrelUnitOfMeasure(), 100.0);
        final List<PricingPeriod> out = new ArrayList<>();
        try {
            CommodityPricingHelper.createPricingPeriods(start, end, q,
                    DeliverySchedule.Daily, QuantityPeriodicity.PerMonth,
                    trailingPaymentTerm(), out);
            fail("expected LibraryException for Daily+PerMonth");
        } catch (final LibraryException e) {
            assertTrue(e.getMessage().toLowerCase().contains("invalid"));
        }
    }

    @Test
    public void unsupportedDeliverySchedule_silentlyReturnsEmpty() {
        // C++ has no else-branch for non-{Monthly, Daily} schedules, so
        // pricingPeriods stays empty. We mirror that behaviour.
        final Date start = new Date(1, Month.January, 2026);
        final Date end = new Date(1, Month.April, 2026);
        final CommodityType wti = new CommodityType("WTI", "WTI");
        final Quantity q = new Quantity(wti, new BarrelUnitOfMeasure(), 1.0);
        final List<PricingPeriod> out = new ArrayList<>();
        CommodityPricingHelper.createPricingPeriods(start, end, q,
                DeliverySchedule.Yearly, QuantityPeriodicity.PerYear,
                trailingPaymentTerm(), out);
        assertEquals(0, out.size());
    }
}

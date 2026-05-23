/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.instruments.ZeroCouponSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Basic smoke tests for {@link ZeroCouponSwap}.
 *
 * <p>Mirrors the pricing-invariant subset of C++
 * {@code test-suite/zerocouponswap.cpp} (v1.42.1):
 * <ul>
 *   <li>{@code testFixedPaymentFromRate}: fixed payment derived from rate
 *       matches {@code N * ((1 + R)^T - 1)} (analytic, TIGHT 1e-10).</li>
 *   <li>{@code testArgumentsValidation}: negative nominal and reversed
 *       start/end raise.</li>
 *   <li>{@code testExpectedCashFlowsInLegs}: fixed leg has 1 cashflow at
 *       paymentDate, floating leg has 1 MultipleResetsCoupon at paymentDate.</li>
 * </ul>
 *
 * <p>Phase 5d.5-ZCS+FB. The full {@code ZeroCouponSwapTest} (in the same
 * package) was un-Ignore'd in a later phase and runs alongside this
 * structural smoke test.
 */
public class ZeroCouponSwapBasicTest {

    private static final double TIGHT = 1.0e-10;

    private Date today;
    private Calendar calendar;
    private DayCounter dayCount;
    private IborIndex usdLibor1M;
    private Handle<YieldTermStructure> ytsHandle;
    private DiscountingSwapEngine engine;

    private void setupCommon() {
        // Use a deterministic past date with adequate fixings buffer
        today = new Date(15, Month.March, 2026);
        new Settings().setEvaluationDate(today);
        calendar = new Target();
        dayCount = new Actual365Fixed();

        // Build a 3% flat forward curve for both forecasting + discounting.
        final FlatForward flat = new FlatForward(today, 0.03,
                new Actual360(),
                Compounding.Compounded, Frequency.Annual);
        ytsHandle = new Handle<YieldTermStructure>(flat);

        usdLibor1M = new USDLibor(new Period(1, TimeUnit.Months), ytsHandle);
        // Backstop fixing for any sub-period that needs a historical fix.
        usdLibor1M.addFixing(calendar.adjust(today.add(-15)), 0.028);

        engine = new DiscountingSwapEngine(ytsHandle);
    }

    @Test
    public void testFixedPaymentFromRate() {
        setupCommon();

        final Date start = new Date(20, Month.March, 2026);
        final Date end = new Date(20, Month.March, 2031);
        final double fixedRate = 0.01;
        final double baseNominal = 1.0e6;

        final ZeroCouponSwap zcs = new ZeroCouponSwap(
                VanillaSwap.Type.Receiver, baseNominal, start, end,
                fixedRate, dayCount, usdLibor1M,
                calendar, BusinessDayConvention.ModifiedFollowing,
                /* paymentDelay */ 0);

        final double actual = zcs.fixedPayment();
        final double T = dayCount.yearFraction(start, end);
        final double expected = baseNominal * (Math.pow(1.0 + fixedRate, T) - 1.0);

        assertEquals("fixed payment from rate must equal N*((1+R)^T - 1)",
                expected, actual, TIGHT);
    }

    @Test
    public void testNegativeNominalIsRejected() {
        setupCommon();
        final Date start = new Date(20, Month.March, 2026);
        final Date end = new Date(20, Month.March, 2031);
        try {
            new ZeroCouponSwap(VanillaSwap.Type.Payer, -1.0e6, start, end,
                    1.0e6, usdLibor1M, calendar,
                    BusinessDayConvention.ModifiedFollowing, 0);
            fail("Expected validation error on negative nominal");
        } catch (final RuntimeException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("nominal"));
        }
    }

    @Test
    public void testStartAfterEndIsRejected() {
        setupCommon();
        final Date start = new Date(20, Month.March, 2026);
        final Date end = new Date(20, Month.March, 2031);
        try {
            // start and end deliberately swapped
            new ZeroCouponSwap(VanillaSwap.Type.Receiver, 1.0e6, end, start,
                    0.01, dayCount, usdLibor1M, calendar,
                    BusinessDayConvention.ModifiedFollowing, 0);
            fail("Expected validation error on start >= end");
        } catch (final RuntimeException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("start"));
        }
    }

    @Test
    public void testExpectedCashFlowsInLegs() {
        setupCommon();
        final Date start = new Date(20, Month.March, 2026);
        final Date end = new Date(20, Month.March, 2031);
        final ZeroCouponSwap zcs = new ZeroCouponSwap(
                VanillaSwap.Type.Receiver, 1.0e6, start, end,
                0.01, dayCount, usdLibor1M,
                calendar, BusinessDayConvention.ModifiedFollowing, 0);

        final Leg fixedLeg = zcs.fixedLeg();
        final Leg floatingLeg = zcs.floatingLeg();

        // Each leg should have exactly one cashflow.
        assertEquals("fixed leg must have one cashflow", 1, fixedLeg.size());
        assertEquals("floating leg must have one cashflow", 1, floatingLeg.size());

        final Date paymentDate = calendar.advance(end, 0, TimeUnit.Days,
                BusinessDayConvention.ModifiedFollowing, false);

        final CashFlow fcf = fixedLeg.get(0);
        final CashFlow ffcf = floatingLeg.get(0);

        assertTrue("fixed leg pays at paymentDate",
                fcf.date().eq(paymentDate));
        assertTrue("floating leg pays at paymentDate",
                ffcf.date().eq(paymentDate));

        // fixed amount should equal the published fixed payment
        assertEquals("fixed cashflow amount matches fixedPayment()",
                zcs.fixedPayment(), fcf.amount(), TIGHT);
    }

    @Test
    public void testInspectorsRoundTrip() {
        setupCommon();
        final Date start = new Date(20, Month.March, 2026);
        final Date end = new Date(20, Month.March, 2031);
        final double baseNominal = 2.5e6;
        final ZeroCouponSwap zcs = new ZeroCouponSwap(
                VanillaSwap.Type.Payer, baseNominal, start, end,
                /* fixedPayment */ 250000.0, usdLibor1M,
                calendar, BusinessDayConvention.ModifiedFollowing, 1);

        assertEquals(VanillaSwap.Type.Payer, zcs.type());
        assertEquals(baseNominal, zcs.baseNominal(), 0.0);
        assertTrue(start.eq(zcs.startDate()));
        assertTrue(end.eq(zcs.maturityDate()));
        assertEquals(250000.0, zcs.fixedPayment(), 0.0);
    }
}

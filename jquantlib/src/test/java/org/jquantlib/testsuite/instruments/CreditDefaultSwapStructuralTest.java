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

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Claim;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.FaceValueClaim;
import org.jquantlib.instruments.Protection;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Phase 3b L0 structural smoke test for {@link CreditDefaultSwap}.
 *
 * <p>Verifies construction with various parameter combinations, basic
 * structural getters, and arguments validation. Engine-driven tests are
 * deferred to Phase 3b Track C (full creditdefaultswap.cpp test port) once
 * Track B's {@code MidPointCdsEngine} lands.
 */
public class CreditDefaultSwapStructuralTest {

    public CreditDefaultSwapStructuralTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static Schedule cdsSchedule(final Calendar calendar) {
        // 5y quarterly schedule starting on a fixed evaluation-window date.
        final Date today = new Date(15, Month.May, 2026);
        new Settings().setEvaluationDate(today);
        final Date start = today;
        final Date end = today.add(new Period(5, TimeUnit.Years));
        return new Schedule(
                start, end,
                new Period(3, TimeUnit.Months),
                calendar,
                BusinessDayConvention.Following,
                BusinessDayConvention.Following,
                DateGeneration.Rule.Forward,
                false);
    }

    @Test
    public void testConstructorRunningSpreadOnly() {
        final Calendar calendar = new UnitedStates(UnitedStates.Market.SETTLEMENT);
        final DayCounter dc = new Actual360();
        final Schedule schedule = cdsSchedule(calendar);

        final CreditDefaultSwap cds = new CreditDefaultSwap(
                Protection.Side.Buyer,
                10_000_000.0,
                0.0150,                       // 150 bps running spread
                schedule,
                BusinessDayConvention.Following,
                dc,
                true,                          // settlesAccrual
                true,                          // paysAtDefaultTime
                schedule.date(0));             // protectionStart == accrual start

        // Side / notional / spread
        assertEquals(Protection.Side.Buyer, cds.side());
        assertEquals(10_000_000.0, cds.notional(), 0.0);
        assertEquals(0.0150, cds.runningSpread(), 0.0);

        // No upfront supplied → upfront() returns null sentinel.
        assertNull("upfront should be null when constructor takes spread only",
                cds.upfront());
        // Upfront payment cash flow is still created with amount 0.
        assertNotNull(cds.upfrontPayment());
        assertEquals(0.0, cds.upfrontPayment().amount(), 0.0);

        // Protection dates
        assertEquals(schedule.date(0), cds.protectionStartDate());
        assertEquals(schedule.dates().get(schedule.dates().size() - 1).serialNumber(),
                cds.protectionEndDate().serialNumber());

        // Defaults
        assertTrue(cds.settlesAccrual());
        assertTrue(cds.paysAtDefaultTime());
        assertTrue("rebatesAccrual default true", cds.rebatesAccrual());
        assertEquals(3, cds.cashSettlementDays());

        // Coupon leg constructed
        assertNotNull(cds.coupons());
        assertSame(cds.coupons(), cds.couponSchedule());
        assertTrue("expected at least 1 premium coupon", cds.coupons().size() > 0);
        assertTrue("first cash flow should be FixedRateCoupon",
                cds.coupons().get(0) instanceof FixedRateCoupon);

        // Default claim is a FaceValueClaim.
        assertNotNull(cds.claim());
        assertTrue("default claim should be FaceValueClaim",
                cds.claim() instanceof FaceValueClaim);

        // Trade date defaults to protectionStart - 1 (pre-Big-Bang branch).
        assertEquals(schedule.date(0).sub(1).serialNumber(),
                cds.tradeDate().serialNumber());
    }

    @Test
    public void testConstructorWithUpfront() {
        final Calendar calendar = new UnitedStates(UnitedStates.Market.SETTLEMENT);
        final DayCounter dc = new Actual360();
        final Schedule schedule = cdsSchedule(calendar);

        final CreditDefaultSwap cds = new CreditDefaultSwap(
                Protection.Side.Seller,
                5_000_000.0,
                0.025,                         // 2.5% upfront
                0.0100,                        // 100 bps running spread
                schedule,
                BusinessDayConvention.Following,
                dc,
                true,
                true,
                schedule.date(0),
                null);                         // upfrontDate computed automatically

        assertEquals(Protection.Side.Seller, cds.side());
        assertEquals(5_000_000.0, cds.notional(), 0.0);
        assertEquals(0.0100, cds.runningSpread(), 0.0);

        // Upfront supplied → upfront() returns 0.025; payment amount = 2.5% * notional.
        assertNotNull("upfront should be non-null after upfront ctor", cds.upfront());
        assertEquals(0.025, cds.upfront().doubleValue(), 0.0);
        final SimpleCashFlow up = cds.upfrontPayment();
        assertNotNull(up);
        assertEquals(0.025 * 5_000_000.0, up.amount(), 0.0);
    }

    @Test
    public void testCustomClaimRetained() {
        final Calendar calendar = new UnitedStates(UnitedStates.Market.SETTLEMENT);
        final DayCounter dc = new Actual360();
        final Schedule schedule = cdsSchedule(calendar);

        final Claim faceValue = new FaceValueClaim();
        final CreditDefaultSwap cds = new CreditDefaultSwap(
                Protection.Side.Buyer,
                1_000_000.0,
                0.0080,
                schedule,
                BusinessDayConvention.Following,
                dc,
                true,
                true,
                schedule.date(0),
                faceValue,                     // explicit claim
                null,                          // lastPeriodDayCounter (ignored — see Javadoc)
                true,                          // rebatesAccrual
                null,                          // tradeDate (auto)
                3);                            // cashSettlementDays

        assertSame("custom claim should be retained", faceValue, cds.claim());

        // FaceValueClaim payoff: notional * (1 - recoveryRate).
        assertEquals(1_000_000.0 * (1.0 - 0.4),
                faceValue.amount(schedule.date(0), 1_000_000.0, 0.4), 0.0);
    }

    @Test
    public void testRebatesAccrualFalseProducesNoRebate() {
        final Calendar calendar = new UnitedStates(UnitedStates.Market.SETTLEMENT);
        final DayCounter dc = new Actual360();
        final Schedule schedule = cdsSchedule(calendar);

        final CreditDefaultSwap cds = new CreditDefaultSwap(
                Protection.Side.Buyer,
                1_000_000.0,
                0.0080,
                schedule,
                BusinessDayConvention.Following,
                dc,
                true,
                true,
                schedule.date(0),
                null,
                null,
                false,                         // rebatesAccrual = false
                null,
                3);

        assertFalse(cds.rebatesAccrual());
        assertNull("accrualRebate should be null when rebatesAccrual=false",
                cds.accrualRebate());
    }

    @Test
    public void testArgumentsValidate() {
        final CreditDefaultSwap.ArgumentsImpl args = new CreditDefaultSwap.ArgumentsImpl();

        // Default-constructed sentinels should fail validation.
        try {
            args.validate();
            fail("expected validate() to fail with default sentinels");
        } catch (final Exception expected) {
            // pass
        }

        // After full setup, validate() should succeed. Use a real CDS to fill
        // the args via setupArguments.
        final Calendar calendar = new UnitedStates(UnitedStates.Market.SETTLEMENT);
        final DayCounter dc = new Actual360();
        final Schedule schedule = cdsSchedule(calendar);

        final CreditDefaultSwap cds = new CreditDefaultSwap(
                Protection.Side.Buyer,
                1_000_000.0,
                0.0080,
                schedule,
                BusinessDayConvention.Following,
                dc);

        // Use an anonymous engine subclass to exercise setupArguments via public API.
        final CreditDefaultSwap.Engine engine = new CreditDefaultSwap.Engine() {
            @Override
            public void calculate() {
                // no-op for the smoke test
            }
        };
        cds.setPricingEngine(engine);

        // Force calculation — populates the engine's arguments and runs the
        // no-op calculate(). Result accessors won't return values (engine is
        // a stub) but the structural plumbing exercises both setupArguments
        // and validate().
        try {
            cds.NPV();
        } catch (final Exception ignored) {
            // The stub engine doesn't populate NPV; that's expected.
            // We only care that setupArguments + validate() ran successfully
            // before calculate() was invoked.
        }

        // Inspect the now-populated arguments DTO.
        final CreditDefaultSwap.ArgumentsImpl populated =
                (CreditDefaultSwap.ArgumentsImpl) engine.getArguments();
        assertEquals(Protection.Side.Buyer, populated.side);
        assertEquals(1_000_000.0, populated.notional, 0.0);
        assertEquals(0.0080, populated.spread, 0.0);
        assertNotNull(populated.leg);
        assertNotNull(populated.upfrontPayment);
        assertNotNull(populated.claim);
        assertNotNull(populated.protectionStart);
        assertNotNull(populated.maturity);
        // validate() must succeed.
        populated.validate();
    }

    @Test
    public void testImpliedHazardRateThrowsUntilEngineLands() {
        // Phase 3b Track B carry-forward — impliedHazardRate / conventionalSpread
        // depend on MidPointCdsEngine which isn't ported in L0.
        final Calendar calendar = new UnitedStates(UnitedStates.Market.SETTLEMENT);
        final DayCounter dc = new Actual360();
        final Schedule schedule = cdsSchedule(calendar);

        final CreditDefaultSwap cds = new CreditDefaultSwap(
                Protection.Side.Buyer,
                1_000_000.0,
                0.0080,
                schedule,
                BusinessDayConvention.Following,
                dc);

        try {
            cds.impliedHazardRate(0.0, null, dc);
            fail("expected UnsupportedOperationException until MidPointCdsEngine lands");
        } catch (final UnsupportedOperationException expected) {
            // pass
        }

        try {
            cds.conventionalSpread(0.4, null, dc);
            fail("expected UnsupportedOperationException until MidPointCdsEngine lands");
        } catch (final UnsupportedOperationException expected) {
            // pass
        }
    }

    @Test
    public void testProtectionEnumValues() {
        assertEquals(2, Protection.Side.values().length);
        assertEquals(Protection.Side.Buyer, Protection.Side.valueOf("Buyer"));
        assertEquals(Protection.Side.Seller, Protection.Side.valueOf("Seller"));
    }
}

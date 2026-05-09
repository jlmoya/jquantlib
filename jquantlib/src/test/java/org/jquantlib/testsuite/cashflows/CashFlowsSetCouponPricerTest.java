/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Tests for CashFlows.setCouponPricer / setCouponPricers static facades
 (Phase 5e.5 WI-2). Mirrors C++ free functions in
 ql/cashflows/couponpricer.{hpp,cpp}.
*/
package org.jquantlib.testsuite.cashflows;

import java.util.Arrays;
import java.util.Collections;

import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.FloatingRateCouponPricer;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.time.Date;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * Smoke tests for {@link CashFlows#setCouponPricer} and
 * {@link CashFlows#setCouponPricers} static facades.
 *
 * <p>These mirror the C++ free functions
 * {@code QuantLib::setCouponPricer} and {@code QuantLib::setCouponPricers}
 * in {@code ql/cashflows/couponpricer.{hpp,cpp}}. The full pricing path is
 * exercised by {@code BondTest}, {@code CapFloorTest}, etc.; this test
 * only verifies the facade routes through {@link
 * org.jquantlib.cashflow.PricerSetter} and surfaces the expected guards.
 */
public class CashFlowsSetCouponPricerTest {

    @Test
    public void setCouponPricer_emptyLeg_isNoOp() {
        // Mirrors C++: setCouponPricer over empty leg loops zero times,
        // does not throw.
        final Leg leg = new Leg();
        CashFlows.setCouponPricer(leg, new BlackIborCouponPricer());
    }

    @Test
    public void setCouponPricer_nonCouponLeg_isNoOp() {
        // SimpleCashFlow is not a coupon; PricerSetter visits it but
        // SimpleCashFlowVisitor performs no work (matches C++
        // PricerSetter::visit(CashFlow&)).
        final Leg leg = new Leg();
        leg.add(new SimpleCashFlow(100.0, new Date(15, org.jquantlib.time.Month.January, 2030)));
        CashFlows.setCouponPricer(leg, new BlackIborCouponPricer());
    }

    @Test(expected = Exception.class)
    public void setCouponPricers_emptyLeg_throws() {
        // Mirrors C++ setCouponPricers QL_REQUIRE(nCashFlows>0, "no cashflows").
        CashFlows.setCouponPricers(new Leg(),
                Collections.<FloatingRateCouponPricer>singletonList(new BlackIborCouponPricer()));
    }

    @Test(expected = Exception.class)
    public void setCouponPricers_morePricersThanCoupons_throws() {
        // Mirrors C++ setCouponPricers QL_REQUIRE(nCashFlows >= nPricers, ...).
        final Leg leg = new Leg();
        leg.add(new SimpleCashFlow(100.0, new Date(15, org.jquantlib.time.Month.January, 2030)));
        CashFlows.setCouponPricers(leg, Arrays.<FloatingRateCouponPricer>asList(
                new BlackIborCouponPricer(),
                new BlackIborCouponPricer(),
                new BlackIborCouponPricer()));
    }

    @Test
    public void setCouponPricers_fewerPricersThanCoupons_reusesLast() {
        // Mirrors C++ behaviour: trailing coupons get pricers[size-1].
        // Here the coupons are SimpleCashFlows so no actual pricer
        // assignment happens, but the loop completes without throwing
        // (covers the i >= nPricers branch).
        final Leg leg = new Leg();
        leg.add(new SimpleCashFlow(100.0, new Date(15, org.jquantlib.time.Month.January, 2030)));
        leg.add(new SimpleCashFlow(100.0, new Date(15, org.jquantlib.time.Month.January, 2031)));
        leg.add(new SimpleCashFlow(100.0, new Date(15, org.jquantlib.time.Month.January, 2032)));
        try {
            CashFlows.setCouponPricers(leg, Collections.<FloatingRateCouponPricer>singletonList(
                    new BlackIborCouponPricer()));
        } catch (final Exception e) {
            fail("trailing-coupon reuse should not throw: " + e.getMessage());
        }
    }
}

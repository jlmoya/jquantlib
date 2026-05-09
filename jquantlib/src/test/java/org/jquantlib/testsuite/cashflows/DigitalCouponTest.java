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

package org.jquantlib.testsuite.cashflows;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/digitalcoupon.cpp (Phase 5e).
 *
 * <p>8 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.cashflow.DigitalCoupon} (asset-or-nothing,
 * cash-or-nothing) under the
 * {@link org.jquantlib.cashflow.DigitalReplication} pricing strategy
 * with a Black-style underlying ibor-coupon pricer.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>JQuant has {@link org.jquantlib.cashflow.DigitalCoupon},
 * {@link org.jquantlib.cashflow.DigitalReplication}, and
 * {@link org.jquantlib.cashflow.IborCouponPricer} (Phase 1/2j) but the
 * tests need:
 *
 * <ul>
 *   <li>{@code BlackIborCouponPricer} concrete pricer to be wired into
 *       {@code DigitalCoupon} via {@code setPricer(IborCouponPricer)}.
 *       JQuant has a partial {@code BlackIborCouponPricer} stub; the
 *       digital-replication path through {@code couponPrice()} +
 *       {@code optionletPrice()} needs cross-validation. See
 *       WI-5e.5-DC-1.</li>
 *
 *   <li>{@code OptionletReplication.Type} enum
 *       (Sub/Central/Super) wired into
 *       {@code DigitalReplication.replicationType()} to drive
 *       {@code testReplicationType}. The Java
 *       {@link org.jquantlib.cashflow.Replication} stub partially exposes
 *       this; need to verify the C++ semantics. See WI-5e.5-DC-2.</li>
 *
 *   <li>{@code RelinkableHandle<OptionletVolatilityStructure>} for the
 *       constant-vol test fixture; JQuant has the underlying
 *       {@link org.jquantlib.quotes.RelinkableHandle} but the
 *       {@code OptionletVolatilityStructure} hierarchy needs the
 *       {@code ConstantOptionletVolatility} concrete subclass exposed at
 *       the right package level. See WI-5e.5-DC-3.</li>
 * </ul>
 */
public class DigitalCouponTest {

    public DigitalCouponTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-1 — needs BlackIborCouponPricer wired into DigitalCoupon.")
    @Test
    public void testAssetOrNothing() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-1 — needs BlackIborCouponPricer wired into DigitalCoupon.")
    @Test
    public void testAssetOrNothingDeepInTheMoney() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-1 — needs BlackIborCouponPricer wired into DigitalCoupon.")
    @Test
    public void testAssetOrNothingDeepOutTheMoney() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-1 — needs BlackIborCouponPricer wired into DigitalCoupon.")
    @Test
    public void testCashOrNothing() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-1 — needs BlackIborCouponPricer wired into DigitalCoupon.")
    @Test
    public void testCashOrNothingDeepInTheMoney() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-1 — needs BlackIborCouponPricer wired into DigitalCoupon.")
    @Test
    public void testCashOrNothingDeepOutTheMoney() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-1 — needs BlackIborCouponPricer wired into DigitalCoupon.")
    @Test
    public void testCallPutParity() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-DC-2 — needs Replication.Type Sub/Central/Super wiring "
            + "in DigitalReplication.")
    @Test
    public void testReplicationType() {
    }
}

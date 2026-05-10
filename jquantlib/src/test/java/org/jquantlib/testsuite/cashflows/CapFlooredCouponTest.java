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
 * Java port of QuantLib v1.42.1 test-suite/capflooredcoupon.cpp (Phase 5e).
 *
 * <p>2 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.cashflow.CappedFlooredCoupon} via large IBOR-based
 * legs and a parity-style decomposition of a capped/floored leg into its
 * underlying floating leg + caplet portfolio - floorlet portfolio.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>JQuant has {@link org.jquantlib.cashflow.CappedFlooredCoupon} (Phase
 * 1) and {@link org.jquantlib.cashflow.IborCoupon} but the test exercises:
 *
 * <ul>
 *   <li>{@code testLargeRates}: Builds a leg of capped/floored ibor coupons
 *       with extreme cap (=100%) / floor (=0) strikes, expects the capped
 *       price to equal the underlying floating price (the cap can never
 *       bind). Requires {@code BlackIborCouponPricer} to be wired into
 *       capped/floored coupons via {@code setCouponPricer(leg, pricer)}.
 *       JQuant has {@link org.jquantlib.cashflow.IborCouponPricer} but
 *       the {@code BlackIborCouponPricer} concrete class is partially
 *       stubbed (Phase 2j touched it) and the leg-wide
 *       {@code setCouponPricer} helper needs a {@code Leg} overload. See
 *       WI-5e.5-CFC-1.</li>
 *
 *   <li>{@code testDecomposition}: Verifies that
 *       {@code NPV(capped/floored leg) ==
 *        NPV(plain leg) + NPV(caps as portfolio) - NPV(floors as portfolio)}.
 *       Requires {@code MakeCapFloor} (see WI-5e.5-CF-1) to build the
 *       decomposition reference. Also exercises {@code CashFlows.npv} with
 *       leg-level cap/floor introspection via {@code IborCouponPricer}. See
 *       WI-5e.5-CFC-2.</li>
 * </ul>
 */
public class CapFlooredCouponTest {

    public CapFlooredCouponTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 WI-5e.5-CFC-1: BlackIborCouponPricer now ported (Phase 5e.5/6); empty test body — "
            + "needs full port from C++ capflooredcoupon.cpp::testLargeRates plus leg-wide setCouponPricer wiring.")
    @Test
    public void testLargeRates() {
    }

    @Ignore("Phase 5e.5 WI-5e.5-CFC-2: MakeCapFloor now ported (commit c1e9cb84); empty test body — "
            + "needs full port from C++ capflooredcoupon.cpp::testDecomposition.")
    @Test
    public void testDecomposition() {
    }
}

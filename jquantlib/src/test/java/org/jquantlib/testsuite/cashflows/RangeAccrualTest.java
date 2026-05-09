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
 * Java port of QuantLib v1.42.1 test-suite/rangeaccrual.cpp (Phase 5e).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods exercising
 * {@code RangeAccrualFloatersCoupon} pricing through the
 * {@code RangeAccrualPricerByBgm} (Brace-Gatarek-Musiela) pricer.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>JQuant has no production code for range-accrual coupons. Both the
 * coupon class and the BGM pricer need to be ported:
 *
 * <ul>
 *   <li>{@code RangeAccrualFloatersCoupon} (in
 *       {@code ql/cashflows/rangeaccrual.hpp}) — coupon class that
 *       integrates an ibor leg's payoff over a strike range. See
 *       WI-5e.5-RA-1.</li>
 *
 *   <li>{@code RangeAccrualPricerByBgm} — Brace-Gatarek-Musiela
 *       BGM-style pricer that computes the range-accrual integral via
 *       analytical formulas with smile-adjusted volatilities. Depends on
 *       {@code SmileSection} (Phase 2j) and the
 *       {@code IborCouponPricer} hierarchy. See WI-5e.5-RA-2.</li>
 *
 *   <li>{@code RangeAccrualLeg} fluent leg builder. See WI-5e.5-RA-3.</li>
 *
 *   <li>{@code testInfiniteRange}: as the strike range expands to
 *       (-inf, +inf) the range-accrual coupon should converge to the
 *       plain ibor coupon. Requires WI-5e.5-RA-1 + WI-5e.5-RA-2 +
 *       BlackIborCouponPricer for the reference value.</li>
 *
 *   <li>{@code testPriceMonotonicityWithRespectToLowerStrike} /
 *       {@code testPriceMonotonicityWithRespectToUpperStrike}: monotonic
 *       behavior of the range-accrual price as one of the strike bounds
 *       sweeps. Requires the same three prereqs.</li>
 * </ul>
 */
public class RangeAccrualTest {

    public RangeAccrualTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-RA-1/2/3 — needs RangeAccrualFloatersCoupon + "
            + "RangeAccrualPricerByBgm + RangeAccrualLeg production port.")
    @Test
    public void testInfiniteRange() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-RA-1/2/3 — same prereqs as testInfiniteRange.")
    @Test
    public void testPriceMonotonicityWithRespectToLowerStrike() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-RA-1/2/3 — same prereqs as testInfiniteRange.")
    @Test
    public void testPriceMonotonicityWithRespectToUpperStrike() {
    }
}

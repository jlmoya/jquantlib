/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/multipleresetscoupons.cpp}
 * v1.42.1 (288 LOC, 5 cases).
 *
 * <p>Exercises multiple-resets coupons — coupons whose payoff depends on
 * the compounded or arithmetic-averaged value of an IBOR fixing taken at
 * multiple sub-period reset dates within a single accrual period. Tests
 * compounded vs averaged variants, ex-coupon cashflow logic, leg
 * consistency checks, and a regression case.
 *
 * <p><strong>All 5 cases deferred to Phase 5d.5</strong> — Java has no
 * multiple-resets coupon family:
 * <ul>
 *   <li>No {@code MultipleResetsCoupon} class
 *       (C++ {@code ql/cashflows/multipleresetscoupon.hpp});
 *   <li>No compounded / averaged sub-period IBOR pricer for this coupon;
 *   <li>No {@code MultipleResetsLeg} builder;
 *   <li>No ex-coupon date treatment for the multiple-resets case.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the entire multiple-resets coupon family
 * belongs to a future production-code phase.
 *
 * <p>Source: {@code test-suite/multipleresetscoupons.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class MultipleResetsCouponsTest {

    private static final String REASON =
            "Phase 5d.5 — requires MultipleResetsCoupon + MultipleResetsLeg + "
          + "compounded/averaged sub-period pricers (no Java equivalent yet)";

    @Ignore(REASON)
    @Test
    public void testCompoundedCouponWithMultipleResets() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testAveragedCouponWithMultipleResets() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testExCouponCashFlow() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMultipleResetsLegConsistencyChecks() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMultipleResetsLegRegression() { fail("not implemented"); }
}

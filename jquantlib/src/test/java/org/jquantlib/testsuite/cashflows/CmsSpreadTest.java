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
 * Java port of QuantLib v1.42.1 test-suite/cmsspread.cpp (Phase 5e).
 *
 * <p>2 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.experimental.coupons.SwapSpreadIndex} and
 * {@link org.jquantlib.experimental.coupons.CmsSpreadCoupon} pricing.
 *
 * <p>Existing Java coverage:
 * {@link org.jquantlib.testsuite.experimental.coupons.CmsSpreadCouponTest}
 * (Phase 4d) already ports both test methods. This class is created to
 * keep the C++ test-suite topology mirrored 1:1 — the existing experimental
 * test covers the same {@code testFixings} and {@code testCouponPricing}
 * cases.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>Both methods are {@code @Ignore}'d here as
 * <em>duplicate-of-existing</em>; see
 * {@link org.jquantlib.testsuite.experimental.coupons.CmsSpreadCouponTest}
 * for the active ports of {@code testFixings} (passing) and
 * {@code testCouponPricing} (carry-forward to Phase 4d.5 / 5e.5 pending
 * {@code LinearTsrPricer}).
 *
 * <p>Phase 5e.5 task: consolidate this skeleton into the experimental
 * file or vice versa once the LinearTsrPricer prereq is resolved
 * (WI-5e.5-CMSS-1).
 */
public class CmsSpreadTest {

    public CmsSpreadTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMSS-1 — duplicate of "
            + "experimental.coupons.CmsSpreadCouponTest.testFixings (Phase 4d, passing). "
            + "Consolidation pending.")
    @Test
    public void testFixings() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMSS-1 — duplicate of "
            + "experimental.coupons.CmsSpreadCouponTest.testCouponPricing (Phase 4d.5 deferred "
            + "pending LinearTsrPricer port).")
    @Test
    public void testCouponPricing() {
    }
}

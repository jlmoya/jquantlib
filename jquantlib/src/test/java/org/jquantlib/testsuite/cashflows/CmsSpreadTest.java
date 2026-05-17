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
import org.jquantlib.testsuite.experimental.coupons.CmsSpreadCouponTest;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 {@code test-suite/cmsspread.cpp} (Phase 5e).
 *
 * <p>Mirrors the 2 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.experimental.coupons.SwapSpreadIndex} and
 * {@link org.jquantlib.experimental.coupons.CmsSpreadCoupon} pricing.
 *
 * <h3>Phase 5e.5b-CFC-d-110 body-fill — delegation wrapper</h3>
 *
 * <p>The full ports of {@code testFixings} and {@code testCouponPricing} live
 * in {@link org.jquantlib.testsuite.experimental.coupons.CmsSpreadCouponTest}
 * (Phase 4d for fixings; Phase 5e.5b-CFC-d-88 for coupon pricing, which
 * required the {@code LinearTsrPricer} port at commit 6fad5c40).
 *
 * <p>This class exists to keep the C++ test-suite topology mirrored 1:1
 * (cmsspread.cpp lives at the top level of test-suite/, hence the
 * cashflows package mirror here in addition to the experimental.coupons
 * mirror). Both test methods now delegate to the experimental
 * implementation rather than {@code @Ignore}'ing, so the smoke is
 * exercised twice — once via the experimental package, once via the
 * topology-mirror here — guaranteeing both entry points remain green.
 *
 * <p>Delegation pattern: instantiate {@link CmsSpreadCouponTest}, manually
 * run its {@code @Before}/{@code @After} lifecycle (each JUnit class has
 * its own annotation lifecycle, so the wrapper must drive setUp/tearDown
 * explicitly), and invoke the corresponding {@code @Test} method.
 *
 * <p>WI-5e.5-CMSS-1 (consolidation tracking) is now resolved: both
 * methods are active and exercised via delegation.
 */
public class CmsSpreadTest {

    public CmsSpreadTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Delegates to
     * {@link CmsSpreadCouponTest#testFixings()} (Phase 4d port of C++
     * {@code testFixings}). Drives the delegate's
     * {@code @Before}/{@code @After} lifecycle manually so the wrapper
     * has the same global-Settings hygiene as a standalone JUnit run.
     */
    @Test
    public void testFixings() throws Exception {
        final CmsSpreadCouponTest delegate = new CmsSpreadCouponTest();
        delegate.setUp();
        try {
            delegate.testFixings();
        } finally {
            delegate.tearDown();
        }
    }

    /**
     * Delegates to
     * {@link CmsSpreadCouponTest#testCouponPricing()} (Phase 5e.5b-CFC-d-88
     * port of C++ {@code testCouponPricing}; un-ignored at commit
     * 6fad5c40 after the {@code LinearTsrPricer} port landed). Same
     * lifecycle-drive pattern as {@link #testFixings()}.
     */
    @Test
    public void testCouponPricing() throws Exception {
        final CmsSpreadCouponTest delegate = new CmsSpreadCouponTest();
        delegate.setUp();
        try {
            delegate.testCouponPricing();
        } finally {
            delegate.tearDown();
        }
    }
}

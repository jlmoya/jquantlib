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
 * Java port of QuantLib v1.42.1 test-suite/cms_normal.cpp (Phase 5e).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.cashflow.CmsCoupon} pricing under a normal
 * (Bachelier) {@code SwaptionVolatilityStructure}, mirroring the trio in
 * {@link CmsTest} (which uses lognormal Black vols).
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>Same blockers as {@link CmsTest} (WI-5e.5-CMS-1, -2, -3) plus:
 *
 * <ul>
 *   <li>The {@code MarketModelFactory}-style construction of a
 *       {@code ConstantSwaptionVolatility} with
 *       {@link org.jquantlib.termstructures.volatilities.VolatilityType#Normal},
 *       which JQuant has but is not exercised in any test today. See
 *       WI-5e.5-CMSN-1.</li>
 *
 *   <li>The Hagan pricer family must accept a
 *       {@code VolatilityType.Normal} swaption surface and switch to the
 *       Bachelier kernel internally. See WI-5e.5-CMSN-2.</li>
 * </ul>
 */
public class CmsNormalTest {

    public CmsNormalTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMSN-1 + WI-5e.5-CMS-1 — needs Hagan pricers "
            + "with normal-vol kernel.")
    @Test
    public void testFairRate() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMSN-1 + WI-5e.5-CMS-3 — same as CmsTest plus normal kernel.")
    @Test
    public void testCmsSwap() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMSN-2 + WI-5e.5-CMS-3 — same as CmsTest plus normal kernel.")
    @Test
    public void testParity() {
    }
}

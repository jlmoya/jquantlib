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
 * Java port of QuantLib v1.42.1 test-suite/cms.cpp (Phase 5e).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods exercising
 * {@link org.jquantlib.cashflow.CmsCoupon} pricing under three lognormal
 * SwaptionVolatilityStructure variants: Hagan analytic, Hagan numerical
 * integration, and Linear-TSR replication. The companion file
 * {@code cms_normal.cpp} repeats the trio against a normal vol surface.
 *
 * <h3>Phase 5e.5 carry-forward rationale</h3>
 *
 * <p>JQuant has {@link org.jquantlib.cashflow.CmsCoupon},
 * {@link org.jquantlib.cashflow.CmsCouponPricer},
 * {@link org.jquantlib.cashflow.CmsLeg}, and the SwaptionVolatilityStructure
 * hierarchy (Phase 2j/2r), but the test exercises:
 *
 * <ul>
 *   <li>{@code AnalyticHaganPricer},
 *       {@code NumericalHaganPricer}, and
 *       {@code LinearTsrPricer} — Hagan-style CMS pricers from
 *       {@code ql/cashflows/conundrumpricer.hpp}. JQuant has
 *       {@code CmsCouponPricer} as an abstract base but the three concrete
 *       Hagan variants are not ported. See WI-5e.5-CMS-1.</li>
 *
 *   <li>{@code SwapIndex} (e.g. {@code EuriborSwapIsdaFixA}) wired with a
 *       {@code SwaptionVolatilityStructure} handle. JQuant has
 *       {@link org.jquantlib.indexes.SwapIndex} (Phase 2) but the
 *       Euribor-swap convenience subclasses (5y/10y/30y) and the
 *       {@code IsdaFix} family need verification. See WI-5e.5-CMS-2.</li>
 *
 *   <li>{@code MakeCms} fluent helper to build CMS swaps for
 *       {@code testCmsSwap}/{@code testParity}. See WI-5e.5-CMS-3.</li>
 * </ul>
 */
public class CmsTest {

    public CmsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5e.5 WI-5e.5-CMS-1: HaganPricer + AnalyticHaganPricer + NumericHaganPricer + "
            + "LinearTsrPricer all now ported (commits 4128cfc4, fee9948f); empty test body — "
            + "needs full port from C++ cms.cpp::testFairRate. EuriborSwapIsdaFixA family still TODO.")
    @Test
    public void testFairRate() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMS-3 — needs MakeCms fluent helper.")
    @Test
    public void testCmsSwap() {
    }

    @Ignore("Phase 5e.5 carry-forward WI-5e.5-CMS-3 — needs MakeCms fluent helper.")
    @Test
    public void testParity() {
    }
}

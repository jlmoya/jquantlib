/*
 Copyright (C) 2026 Jose Moya

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

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Port skeleton for QuantLib v1.42.1 test-suite/sofrfutures.cpp (221 LOC).
 *
 * Phase 5c — calendar/time/indexes test ports.
 *
 * Phase 5c.5 deferral: this test exercises {@code Sofr} (overnight index),
 * {@code OvernightIndexFuture} (instrument), {@code SofrFutureRateHelper}
 * and {@code OvernightIndexFutureRateHelper} (rate helpers), and
 * {@code PiecewiseYieldCurve<Discount, Linear>} bootstrapping with
 * {@code Pillar} support. None of these are present in the Java port. The
 * underlying {@code OvernightIndex} base class and most overnight indexes
 * are also unported. Porting these production classes is deferred to a
 * later phase (likely Phase 5d together with the OIS coupon work).
 *
 * Reference: test-suite/sofrfutures.cpp.
 *
 * @author Jose Moya
 */
public class SofrFuturesTest {

    public SofrFuturesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5c.5: Sofr / OvernightIndexFuture / SofrFutureRateHelper not yet ported from v1.42.1")
    @Test
    public void testBootstrap() {
        // Bootstrap with 13 SOFR future quotes (Oct 2018 - Sep 2020) and
        // verify a Mar 2019 - Jun 2019 future re-prices to within 1e-9 of
        // expected price for both convexity adjustments {0.0, 0.1}.
        // Reference: test-suite/sofrfutures.cpp:45-118.
    }

    @Ignore("Phase 5c.5: Sofr / OvernightIndexFuture not yet ported from v1.42.1")
    @Test
    public void testBootstrapWithJuneteenth() {
        // Bootstrap when the third Wednesday falls on Juneteenth (US holiday
        // since 2021); 5 SOFR future quotes (Jun 2024 - Jun 2025) verifying
        // that the Juneteenth holiday properly extends the futures period.
        // Reference: test-suite/sofrfutures.cpp:121-171.
    }

    @Ignore("Phase 5c.5: SofrFutureRateHelper / OvernightIndexFutureRateHelper / Pillar not yet ported from v1.42.1")
    @Test
    public void testPillarDates() {
        // Verify Pillar::LastRelevantDate / Pillar::MaturityDate /
        // Pillar::CustomDate behavior, plus the after-maturity exception.
        // Reference: test-suite/sofrfutures.cpp:173-217.
    }
}

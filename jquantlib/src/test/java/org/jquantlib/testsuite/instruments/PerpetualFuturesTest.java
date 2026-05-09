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
 * Port skeleton for QuantLib v1.42.1 test-suite/perpetualfutures.cpp (177 LOC).
 *
 * Phase 5c — calendar/time/indexes test ports.
 *
 * Phase 5c.5 deferral: this test exercises the {@code PerpetualFutures}
 * instrument and {@code DiscountingPerpetualFuturesEngine} pricing engine,
 * which are present in C++ v1.42.1 (ql/instruments/perpetualfutures.hpp,
 * ql/pricingengines/futures/discountingperpetualfuturesengine.hpp) but have
 * no Java equivalent yet. Porting both production classes is a Phase 5c.5
 * (or Phase 5d) work item.
 *
 * The C++ test exercises the analytic value of perpetual futures across
 * Linear/Inverse payoff types and FundingWithPreviousSpot/FundingWithCurrentSpot
 * funding types in both discrete-time and continuous-time regimes, against
 * Equations (12), Proposition 2/3/4 of Ackerer-Hugonnier-Jermann (2024).
 *
 * Reference: test-suite/perpetualfutures.cpp:66-173.
 *
 * @author Jose Moya
 */
public class PerpetualFuturesTest {

    public PerpetualFuturesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5c.5: PerpetualFutures instrument and DiscountingPerpetualFuturesEngine not yet ported from v1.42.1")
    @Test
    public void testPerpetualFuturesValues() {
        // 7 cases: Linear/Inverse x FundingWithPreviousSpot/FundingWithCurrentSpot
        // x discrete (3M)/continuous (0M) funding frequency.
        // Reference: test-suite/perpetualfutures.cpp:66-173.
    }
}

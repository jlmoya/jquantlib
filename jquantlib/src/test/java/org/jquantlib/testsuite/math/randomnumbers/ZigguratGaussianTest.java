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

package org.jquantlib.testsuite.math.randomnumbers;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/zigguratgaussian.cpp (Phase 5a).
 *
 * <p>1 BOOST_AUTO_TEST_CASE method. JQuantLib has no
 * {@code ZigguratGaussianRng} or {@code Xoshiro256StarStarUniformRng}
 * (C++ {@code ql/math/randomnumbers/}). Phase 5a.5 carry-forward.
 */
public class ZigguratGaussianTest {

    public ZigguratGaussianTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has neither ZigguratGaussianRng nor "
            + "Xoshiro256StarStarUniformRng (C++ ql/math/randomnumbers/). Port both then enable.")
    @Test
    public void testStatisticsOfNextReal() {
    }
}

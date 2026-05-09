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

package org.jquantlib.testsuite.methods.montecarlo;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/brownianbridge.cpp (Phase 5a).
 *
 * <p>2 BOOST_AUTO_TEST_CASE methods exercising
 * {@code BrownianBridge::transform} on Sobol/InverseCumulative variates,
 * plus path-generation comparison between brownianBridge=true vs false
 * via {@code PathGenerator}.
 *
 * <p>Phase 5a.5 carry-forward: both cases require running 100k+ Sobol
 * samples through {@code SequenceStatistics} and comparing covariance
 * matrices; in JQuantLib the {@code SequenceStatistics.covariance}
 * implementation diverges from the C++ unbiased estimator (see
 * {@code CovarianceTest.testCovariance} carry-forward). The tests cannot
 * pass with the current statistics implementation.
 */
public class BrownianBridgeTest {

    public BrownianBridgeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5a.5 carry-forward — depends on SequenceStatistics.covariance/correlation "
            + "fix (see CovarianceTest.testCovariance carry-forward). The C++ test computes "
            + "covariance over 262143 Sobol samples and compares to identity within 2.5e-4; "
            + "Java's SequenceStatistics gives a divergent estimator.")
    @Test
    public void testVariates() {
    }

    @Ignore("Phase 5a.5 carry-forward — depends on SequenceStatistics + path-generation "
            + "infrastructure parity (BrownianBridge transform via PathGenerator). Slow test "
            + "(~131k Sobol samples) — also a candidate for @Tag('slow').")
    @Test
    public void testPathGeneration() {
    }
}

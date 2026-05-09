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

package org.jquantlib.testsuite.math;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/autocovariances.cpp (Phase 5a).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods. JQuantLib has no
 * {@code QuantLib::convolutions / autocovariances / autocorrelations}
 * (C++ {@code ql/math/autocovariance.hpp}). All three cases are
 * Phase 5a.5 carry-forwards.
 */
public class AutocovariancesTest {

    public AutocovariancesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no convolutions() helper "
            + "(C++ ql/math/autocovariance.hpp). Port the autocovariance utilities then enable.")
    @Test
    public void testConvolutions() {
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no autocovariances() helper.")
    @Test
    public void testAutoCovariances() {
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no autocorrelations() helper.")
    @Test
    public void testAutoCorrelations() {
    }
}

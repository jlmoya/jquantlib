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
 * Java port of QuantLib v1.42.1 test-suite/linearleastsquaresregression.cpp
 * (Phase 5b skeleton).
 *
 * <p>The C++ test exercises {@code LinearRegression} (and the SVD-based
 * {@code GeneralLinearLeastSquares} infrastructure):
 * <ul>
 *   <li>{@code testRegression}: 100k pseudo-random samples regressed against
 *     basis {1, x, x^2, sin(x)} verifying coefficients within 3*standardErrors.
 *     Also exercises detection of redundant basis (duplicate x^2) which yields
 *     huge standard errors on the redundant column.</li>
 *   <li>{@code testMultiDimRegression}: multi-variate regression (2D input).</li>
 *   <li>{@code test1dLinearRegression}: simple 1D y = a + b*x case.</li>
 * </ul>
 *
 * <p>Phase 5b deferred: Java has no
 * {@code org.jquantlib.math.regression.LinearRegression} class. Adding it
 * requires porting {@code linearleastsquaresregression.hpp} (which depends
 * on a working SVD pipeline — Java has SVD but not the regression wrapper).
 * Out of scope for testsuite-only Phase 5b.
 */
@Ignore("Phase 5b.5: LinearRegression production class not yet ported")
public class LinearLeastSquaresRegressionTest {

    public LinearLeastSquaresRegressionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testRegression() {
        // C++ test-suite/linearleastsquaresregression.cpp:35 — fits 100k
        // random samples to {1, x, x^2, sin(x)} basis (3 parameter sets,
        // tolerance 0.05), then with redundant basis {1,x,x^2,sin(x),x^2}.
    }

    @Test
    public void testMultiDimRegression() {
        // C++ test-suite/linearleastsquaresregression.cpp:117 — multi-variate
        // (2D input) regression against {1, x[0], x[1], x[0]*x[1]}.
    }

    @Test
    public void test1dLinearRegression() {
        // C++ test-suite/linearleastsquaresregression.cpp:187 — simple y = a + b*x
        // sanity test using LinearRegression(x, y) one-arg constructor.
    }
}

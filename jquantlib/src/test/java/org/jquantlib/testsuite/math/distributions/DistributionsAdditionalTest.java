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

package org.jquantlib.testsuite.math.distributions;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of the C++ tests in test-suite/distributions.cpp that have no
 * existing Java equivalent (Phase 5b).
 *
 * <p>The C++ file has nine test cases. Java already covers:
 * <ul>
 *   <li>{@code testNormal} -> {@code NormalDistributionTest}, {@code CumulativeNormalDistributionTest}.</li>
 *   <li>{@code testBivariate} -> {@code BivariateNormalDistributionTest}.</li>
 *   <li>{@code testPoisson} / {@code testCumulativePoisson} / {@code testInverseCumulativePoisson}
 *     -> {@code PoissonNormalTest}, {@code CumulativePoissonDistributionTest},
 *     {@code InverseCumulativePoissonTest}.</li>
 *   <li>{@code testBivariateCumulativeStudentVsBivariate} -> partial coverage in
 *     {@code BivariateNormalDistributionTest} for the t -> Normal limit.</li>
 * </ul>
 *
 * <p>Three cases remain:
 * <ul>
 *   <li>{@code testBivariateCumulativeStudent}: requires
 *     {@code BivariateCumulativeStudentDistribution} (no Java production class).</li>
 *   <li>{@code testInvCDFviaStochasticCollocation}: requires
 *     {@code StochasticCollocationInvCDF} (no Java production class).</li>
 *   <li>{@code testSankaranApproximation}: requires
 *     {@code NonCentralCumulativeChiSquareSankaranApprox} (no Java production class).</li>
 * </ul>
 *
 * <p>Phase 5b deferred: production classes pending Phase 5b.5 / 4o-style infra.
 */
@Ignore("Phase 5b.5: BivariateCumulativeStudent / StochasticCollocationInvCDF / SankaranApprox not yet ported")
public class DistributionsAdditionalTest {

    public DistributionsAdditionalTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testBivariateCumulativeStudent() {
        // C++ test-suite/distributions.cpp:439 — drives 14 x values across
        // 20 degree-of-freedom values for rho=+/-0.5 against tabulated
        // expected values from the reference paper. Tolerance 1e-5.
    }

    @Test
    public void testInvCDFviaStochasticCollocation() {
        // C++ test-suite/distributions.cpp:634 — verify
        // StochasticCollocationInvCDF reproduces InverseCumulativeNormal
        // to 1e-5 across 11 quadrature orders + a non-Normal target test.
    }

    @Test
    public void testSankaranApproximation() {
        // C++ test-suite/distributions.cpp:699 — Sankaran approximation
        // for non-central chi-squared CDF. df in {2,2,2,4,4}, ncp in {1..3},
        // x stepping 0.25..10 by 0.1, tolerance 0.01.
    }
}

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.vanilla.qdfp;

import org.jquantlib.math.Constants;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.integrals.Integrator;

/**
 * Gauss-Lobatto {@code (m,n)-eps} iteration scheme for the QD fixed-point American engine.
 *
 * <p>Phase 1 closure A5 port of the test-suite-local {@code QdFpGaussLobattoScheme}
 * class defined inline in v1.42.1 {@code test-suite/americanoption.cpp}
 * (pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}, lines 1548-1576).
 *
 * <p>The C++ test-suite defines this scheme inline rather than in the production
 * header — it is used by {@code testBulkQdFpAmericanEngine} and
 * {@code testQdEngineWithLobattoIntegral} to cross-validate the Lobatto-quadrature
 * branch of the fixed-point engine. We surface it here as a public production class
 * because it is a natural sibling of {@link QdFpTanhSinhIterationScheme}: both wrap
 * a single quadrature rule for both fixed-point and boundary-to-price integration.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code m} — total fixed-point iteration steps. First step is a
 *       partial Jacobi-Newton, the remaining {@code m-1} are naive
 *       Richardson fixed-point iterations.</li>
 *   <li>{@code n} — number of Chebyshev nodes used to interpolate the
 *       exercise boundary.</li>
 *   <li>{@code eps} — relative-tolerance target. The wrapped
 *       {@link GaussLobattoIntegral} uses {@code 100000} max iterations,
 *       absolute accuracy {@code QL_MAX_REAL} (sentinel — disabled), and
 *       relative accuracy {@code 0.1*eps}, matching C++ line-for-line.</li>
 * </ul>
 */
public class QdFpGaussLobattoScheme implements QdFpIterationScheme {

    private final int m_;
    private final int n_;
    private final Integrator integrator_;

    public QdFpGaussLobattoScheme(final int m, final int n, final double eps) {
        this.m_ = m;
        this.n_ = n;
        // C++: GaussLobattoIntegral(100000, QL_MAX_REAL, 0.1*eps)
        // The 3-arg ctor uses the default useConvergenceEstimate=true.
        this.integrator_ = new GaussLobattoIntegral(100000, Constants.QL_MAX_REAL, 0.1 * eps, true);
    }

    @Override
    public int getNumberOfChebyshevInterpolationNodes() {
        return n_;
    }

    @Override
    public int getNumberOfNaiveFixedPointSteps() {
        return m_ - 1;
    }

    @Override
    public int getNumberOfJacobiNewtonFixedPointSteps() {
        return 1;
    }

    @Override
    public Integrator getFixedPointIntegrator() {
        return integrator_;
    }

    @Override
    public Integrator getExerciseBoundaryToPriceIntegrator() {
        return integrator_;
    }
}

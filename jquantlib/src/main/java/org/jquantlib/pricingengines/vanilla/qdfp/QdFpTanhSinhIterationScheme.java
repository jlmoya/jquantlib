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

import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.integrals.TanhSinhIntegral;

/**
 * Tanh-sinh {@code (m,n)-eps} scheme for the QD fixed-point engine.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::QdFpTanhSinhIterationScheme}
 * (v1.42.1 ql/pricingengines/vanilla/qdfpamericanengine.{hpp,cpp}; pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code m} — total fixed-point iteration steps. First step is a
 *       partial Jacobi-Newton, the remaining {@code m-1} are naive
 *       Richardson fixed-point iterations.</li>
 *   <li>{@code n} — number of Chebyshev nodes used to interpolate the
 *       exercise boundary.</li>
 *   <li>{@code eps} — relative tolerance of the tanh-sinh integration
 *       used both for fixed-point steps and the final boundary-to-price
 *       conversion.</li>
 * </ul>
 *
 * <p><b>Boost-fallback note.</b> C++ falls back to
 * {@code GaussLobattoIntegral(100000, QL_MAX_REAL, 0.1*eps)} when Boost
 * tanh-sinh is unavailable. Since Java's {@link TanhSinhIntegral} is a
 * native port and always available, only the primary path is implemented.
 */
public class QdFpTanhSinhIterationScheme implements QdFpIterationScheme {

    private final int m_;
    private final int n_;
    private final Integrator integrator_;

    public QdFpTanhSinhIterationScheme(final int m, final int n, final double eps) {
        this.m_ = m;
        this.n_ = n;
        this.integrator_ = new TanhSinhIntegral(eps);
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

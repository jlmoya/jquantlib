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

import org.jquantlib.QL;
import org.jquantlib.math.integrals.GaussLegendreIntegrator;
import org.jquantlib.math.integrals.Integrator;

/**
 * Gauss-Legendre {@code (l,m,n)-p} scheme for the QD fixed-point engine.
 *
 * <p>Phase 1 closure A1 port of {@code QuantLib::QdFpLegendreScheme}
 * (v1.42.1 ql/pricingengines/vanilla/qdfpamericanengine.{hpp,cpp}; pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code l} — order of Gauss-Legendre integration inside every
 *       fixed-point iteration step.</li>
 *   <li>{@code m} — total fixed-point iteration steps. First step is a
 *       partial Jacobi-Newton, the remaining {@code m-1} are naive
 *       Richardson fixed-point iterations.</li>
 *   <li>{@code n} — number of Chebyshev nodes used to interpolate the
 *       exercise boundary.</li>
 *   <li>{@code p} — order of Gauss-Legendre integration in the final
 *       conversion of the converged exercise boundary into option prices.</li>
 * </ul>
 */
public class QdFpLegendreScheme implements QdFpIterationScheme {

    private final int m_;
    private final int n_;
    private final GaussLegendreIntegrator fpIntegrator_;
    private final GaussLegendreIntegrator exerciseBoundaryIntegrator_;

    public QdFpLegendreScheme(final int l, final int m, final int n, final int p) {
        QL.require(m > 0, "at least one fixed point iteration step is needed");
        QL.require(n > 0, "at least one interpolation point is needed");
        this.m_ = m;
        this.n_ = n;
        this.fpIntegrator_ = new GaussLegendreIntegrator(l);
        this.exerciseBoundaryIntegrator_ = new GaussLegendreIntegrator(p);
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
        return fpIntegrator_;
    }

    @Override
    public Integrator getExerciseBoundaryToPriceIntegrator() {
        return exerciseBoundaryIntegrator_;
    }
}

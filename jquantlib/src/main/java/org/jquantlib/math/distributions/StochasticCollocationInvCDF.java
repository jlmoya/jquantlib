/*
 Copyright (C) 2016 Klaus Spanderen
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
package org.jquantlib.math.distributions;

import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.math.interpolations.LagrangeInterpolation;

/**
 * Stochastic collocation inverse cumulative distribution function.
 *
 * <p>Faithful Java port of QuantLib v1.42.1
 * {@code ql/math/randomnumbers/stochasticcollocationinvcdf.{hpp,cpp}}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The class builds a barycentric Lagrange interpolant of an expensive
 * inverse CDF on the Gauss–Hermite abscissae (rescaled by {@code √2}), with an optional sigma rescaling so that the
 * interpolation grid spans either a user-supplied upper-tail quantile {@code pMax} or lower-tail quantile {@code pMin}.
 * Once constructed, evaluation is O(n) per call.
 *
 * <p>References:
 * <ul>
 *   <li>L.A. Grzelak, J.A.S. Witteveen, M. Suárez-Taboada, C.W. Oosterlee,
 *       <em>The Stochastic Collocation Monte Carlo Sampler: Highly efficient
 *       sampling from "expensive" distributions</em>,
 *       <a href="http://papers.ssrn.com/sol3/papers.cfm?abstract_id=2529691">SSRN 2529691</a>.</li>
 * </ul>
 */
public class StochasticCollocationInvCDF implements Ops.DoubleOp {

    private final double[] x_;
    private final double sigma_;
    private final double[] y_;
    private final LagrangeInterpolation interpl_;

    /**
     * Construct using the default sigma = 1.0 (no rescaling).
     */
    public StochasticCollocationInvCDF(final Ops.DoubleOp invCDF, final int lagrangeOrder) {
        this(invCDF, lagrangeOrder, Double.NaN, Double.NaN);
    }

    /**
     * Construct rescaled so the largest abscissa maps to {@code pMax}.
     *
     * @param invCDF        target inverse CDF, taken as a black box
     * @param lagrangeOrder Gauss–Hermite order (number of nodes)
     * @param pMax          upper-tail probability (use {@code Double.NaN} for the default)
     */
    public StochasticCollocationInvCDF(final Ops.DoubleOp invCDF, final int lagrangeOrder, final double pMax) {
        this(invCDF, lagrangeOrder, pMax, Double.NaN);
    }

    /**
     * Construct rescaled by {@code pMax} (if not NaN), else by {@code pMin} (if not NaN), else sigma = 1.0.
     *
     * <p>Faithful transcription of the C++ constructor:
     * <pre>
     *   x_     = √2 * GaussHermiteIntegration(n).x()
     *   sigma_ = pMax != null ? x_.back()  / InverseCumulativeNormal()(pMax)
     *          : pMin != null ? x_.front() / InverseCumulativeNormal()(pMin)
     *          : 1.0
     *   y_[i]  = invCDF( CumulativeNormalDistribution()( x_[i] / sigma_ ) )
     *   interpl_ = LagrangeInterpolation(x_, y_)
     * </pre>
     */
    public StochasticCollocationInvCDF(final Ops.DoubleOp invCDF, final int lagrangeOrder, final double pMax,
            final double pMin) {
        // x_ = sqrt(2) * GaussHermiteIntegration(n).x()
        final GaussHermiteIntegration gh = new GaussHermiteIntegration(lagrangeOrder);
        final int n = gh.order();
        this.x_ = new double[n];
        for ( int i = 0; i < n; ++i ) {
            x_[i] = Constants.M_SQRT2 * gh.x(i);
        }

        // sigma_ rescaling: priority pMax > pMin > 1.0 (matches C++).
        // C++ Array<.> nodes are stored in ascending order after the
        // tridiagonal QR; x_.back() = x_[n-1], x_.front() = x_[0].
        final InverseCumulativeNormal invNormal = new InverseCumulativeNormal();
        if ( !Double.isNaN(pMax) ) {
            this.sigma_ = x_[n - 1] / invNormal.op(pMax);
        } else if ( !Double.isNaN(pMin) ) {
            this.sigma_ = x_[0] / invNormal.op(pMin);
        } else {
            this.sigma_ = 1.0;
        }

        // y_[i] = invCDF( N( x_[i] / sigma_ ) )
        final CumulativeNormalDistribution normalCDF = new CumulativeNormalDistribution();
        this.y_ = new double[n];
        for ( int i = 0; i < n; ++i ) {
            y_[i] = invCDF.op(normalCDF.op(x_[i] / sigma_));
        }

        this.interpl_ = new LagrangeInterpolation(x_, y_);
    }

    /**
     * Evaluate the collocation interpolant at a standard-normal x-coordinate.
     *
     * <p>Mirrors C++ {@code Real value(Real x) const}:
     * {@code interpl_(x * sigma_, true)} — the trailing {@code true} requests extrapolation, which the Java
     * {@link LagrangeInterpolation} already performs unconditionally via the barycentric formula.
     */
    public double value(final double x) {
        return interpl_.op(x * sigma_, true);
    }

    /**
     * Evaluate the collocation inverse CDF at a uniform {@code u ∈ (0, 1)}.
     *
     * <p>Mirrors C++ {@code Real operator()(Real u) const}:
     * {@code value( InverseCumulativeNormal()(u) )}.
     */
    @Override
    public double op(final double u) {
        return value(new InverseCumulativeNormal().op(u));
    }
}

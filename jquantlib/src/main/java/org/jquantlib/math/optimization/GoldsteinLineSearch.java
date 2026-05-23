/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.optimization;

import org.jquantlib.math.Closeness;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.EndCriteria.Type;

/**
 * Goldstein and Price line search.
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/optimization/goldstein.{hpp,cpp}} (Cheng Li, 2015).
 * Pair to {@link ArmijoLineSearch}; differs in the dual Wolfe-style bracketing.
 *
 * @author Jose Moya
 */
public class GoldsteinLineSearch extends LineSearch {

    private final double alpha_;
    private final double beta_;
    private final double extrapolation_;

    public GoldsteinLineSearch() {
        this(1e-8, 0.05, 0.65, 1.5);
    }

    public GoldsteinLineSearch(final double eps, final double alpha, final double beta, final double extrapolation) {
        super(eps);
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.extrapolation_ = extrapolation;
    }

    @Override
    public double evaluate(final Problem P, final Type[] ecType, final EndCriteria endCriteria, final double t_ini) {
        final Constraint constraint = P.constraint();
        succeed_ = true;
        boolean maxIter = false;
        double t = t_ini;
        int loopNumber = 0;

        final double q0 = P.functionValue();
        final double qp0 = P.gradientNormValue();

        double tl = 0.0;
        double tr = 0.0;

        qt_ = q0;
        qpt_ = (gradient_ == null || gradient_.empty()) ? qp0 : -gradient_.dotProduct(searchDirection_);

        // Initialize gradient
        gradient_ = new Array(P.currentValue().size());
        // Compute new point — clone() to mirror C++ Array's copy semantics
        // (see ArmijoLineSearch for the alias-fix discussion).
        xtd_ = P.currentValue().clone();
        t = update(xtd_, searchDirection_, t, constraint);
        // Compute function value at the new point
        qt_ = P.value(xtd_);

        while ( (qt_ - q0) < -beta_ * t * qpt_ || (qt_ - q0) > -alpha_ * t * qpt_ ) {
            if ( (qt_ - q0) > -alpha_ * t * qpt_ ) {
                tr = t;
            } else {
                tl = t;
            }
            ++loopNumber;

            // calculate the new step
            if ( Closeness.isCloseEnough(tr, 0.0) ) {
                t *= extrapolation_;
            } else {
                t = (tl + tr) / 2.0;
            }

            // New point value
            xtd_ = P.currentValue().clone();
            t = update(xtd_, searchDirection_, t, constraint);

            // Compute function value at the new point
            qt_ = P.value(xtd_);
            P.gradient(gradient_, xtd_);
            // and it squared norm
            maxIter = endCriteria.checkMaxIterations(loopNumber, ecType);

            if ( maxIter ) {
                break;
            }
        }

        if ( maxIter ) {
            succeed_ = false;
        }

        // Compute new gradient
        P.gradient(gradient_, xtd_);
        // and its squared norm
        qpt_ = gradient_.dotProduct(gradient_);

        // Return new step value
        return t;
    }
}

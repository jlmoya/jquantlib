/*
 Copyright (C) 2026 JQuantLib migration contributors

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

/*
 Copyright (C) 2007 Allen Kuo
 Copyright (C) 2010 Alessandro Roveda
*/

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.QL;
import org.jquantlib.math.BSpline;
import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Cubic B-splines fitting method.
 * <p>
 * Fits a discount function to a set of cubic B-splines {@code N_{i,3}(t)}:
 * <pre>
 * d(t) = sum_{i=0..n} c_i * N_{i,3}(t)
 * </pre>
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code CubicBSplinesFitting}
 * (ql/termstructures/yield/nonlinearfittingmethods.{hpp,cpp}).
 *
 * <p>References:
 * <ul>
 * <li>McCulloch, J. 1971, "Measuring the Term Structure of Interest Rates." Journal of Business, 44: 19-31.</li>
 * <li>McCulloch, J. 1975, "The tax adjusted yield curve." Journal of Finance, XXX 811-30.</li>
 * </ul>
 *
 * <p><b>Warning:</b> "The results are extremely sensitive to the number and location of the knot points,
 * and there is no optimal way of selecting them." (James, J. and N. Webber, "Interest Rate Modelling"
 * John Wiley, 2000, pp. 440.)
 *
 * <p>Phase 2 forward closure L2-B.
 */
public class CubicBSplinesFitting extends FittingMethod {

    private final BSpline splines_;
    private final int size_;
    /** N_th basis function coefficient to solve for when d(0)=1. */
    private final int n_;
    private final double[] knots_;

    public CubicBSplinesFitting(final double[] knotVector, final boolean constrainAtZero) {
        this(knotVector, constrainAtZero, new Array(0), null, new Array(0), 0.0, Double.MAX_VALUE, new NoConstraint());
    }

    public CubicBSplinesFitting(final double[] knotVector, final boolean constrainAtZero, final Array weights,
            final OptimizationMethod optimizationMethod, final Array l2, final double minCutoffTime,
            final double maxCutoffTime, final Constraint constraint) {
        super(constrainAtZero, weights, optimizationMethod, l2, minCutoffTime, maxCutoffTime, constraint);

        QL.require(knotVector.length >= 8, "At least 8 knots are required");

        // C++: splines_(3, knots.size() - 5, knots) — cubic (p=3), n = knots.size()-5
        // BSpline ctor requires knots.length == p+n+2 → 3 + (knots-5) + 2 = knots ✓
        this.knots_ = knotVector.clone();
        this.splines_ = new BSpline(3, knotVector.length - 5, this.knots_);

        final int basisFunctions = knotVector.length - 4;

        if ( constrainAtZero ) {
            this.size_ = basisFunctions - 1;
            // Note: A small but nonzero N_th basis function at t=0 may
            // lead to an ill conditioned problem.
            this.n_ = 1;
            QL.require(Math.abs(splines_.valueAt(n_, 0.0)) > Constants.QL_EPSILON,
                    "N_th cubic B-spline must be nonzero at t=0");
        } else {
            this.size_ = basisFunctions;
            this.n_ = 0;
        }
    }

    public CubicBSplinesFitting(final double[] knotVector, final boolean constrainAtZero, final Array weights,
            final Array l2, final double minCutoffTime, final double maxCutoffTime, final Constraint constraint) {
        this(knotVector, constrainAtZero, weights, null, l2, minCutoffTime, maxCutoffTime, constraint);
    }

    /** Cubic B-spline basis functions. */
    public double basisFunction(final int i, final double t) {
        return splines_.valueAt(i, t);
    }

    @Override
    public CubicBSplinesFitting clone() {
        return new CubicBSplinesFitting(knots_, constrainAtZero_, weights(), optimizationMethod(), l2(), 0.0,
                Double.MAX_VALUE, constraint());
    }

    @Override
    public int size() {
        return size_;
    }

    @Override
    protected double discountFunction(final Array x, final double t) {
        double d = 0.0;

        if ( !constrainAtZero_ ) {
            for ( int i = 0; i < size_; ++i ) {
                d += x.get(i) * splines_.valueAt(i, t);
            }
        } else {
            final double T = 0.0;
            double sum = 0.0;
            for ( int i = 0; i < size_; ++i ) {
                if ( i < n_ ) {
                    d += x.get(i) * splines_.valueAt(i, t);
                    sum += x.get(i) * splines_.valueAt(i, T);
                } else {
                    d += x.get(i) * splines_.valueAt(i + 1, t);
                    sum += x.get(i) * splines_.valueAt(i + 1, T);
                }
            }
            double coeff = 1.0 - sum;
            coeff /= splines_.valueAt(n_, T);
            d += coeff * splines_.valueAt(n_, t);
        }

        return d;
    }
}

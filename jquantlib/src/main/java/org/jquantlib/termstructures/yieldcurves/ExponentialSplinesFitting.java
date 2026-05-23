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
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Exponential-splines fitting method.
 * <p>
 * Fits a discount function to the exponential form:
 * <pre>
 * d(t) = sum_{i=1..9} c_i * exp(-kappa_i * t)
 * </pre>
 * where the constants {@code c_i} and {@code kappa} are to be determined.
 *
 * <p>{@code kappa} can be passed a fixed value (via {@link #fixedKappa(double)}),
 * in which case it is excluded from optimization.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code ExponentialSplinesFitting}
 * (ql/termstructures/yield/nonlinearfittingmethods.{hpp,cpp}).
 *
 * <p>{@code Null<Real>} in C++ is represented here as {@link Double#NaN}.
 *
 * <p><b>Warning:</b> convergence may be slow.
 *
 * <p>Reference: Li, B., E. DeWetering, G. Lucas, R. Brenner and A. Shapiro (2001):
 * "Merrill Lynch Exponential Spline Model." Merrill Lynch Working Paper.
 *
 * <p>Phase 2 forward closure L2-B.
 */
public class ExponentialSplinesFitting extends FittingMethod {

    private final int numCoeffs_;
    /** {@link Double#NaN} when "no fixed kappa" (equivalent to C++ {@code Null<Real>()}). */
    private final double fixedKappa_;

    public ExponentialSplinesFitting(final boolean constrainAtZero) {
        this(constrainAtZero, new Array(0), null, new Array(0), 0.0, Double.MAX_VALUE, 9, Double.NaN, new NoConstraint());
    }

    public ExponentialSplinesFitting(final boolean constrainAtZero, final Array weights,
            final OptimizationMethod optimizationMethod, final Array l2, final double minCutoffTime,
            final double maxCutoffTime, final int numCoeffs, final double fixedKappa, final Constraint constraint) {
        super(constrainAtZero, weights, optimizationMethod, l2, minCutoffTime, maxCutoffTime, constraint);
        this.numCoeffs_ = numCoeffs;
        this.fixedKappa_ = fixedKappa;
        QL.require(size() > 0, "At least 1 unconstrained coefficient required");
    }

    public ExponentialSplinesFitting(final boolean constrainAtZero, final Array weights, final Array l2,
            final double minCutoffTime, final double maxCutoffTime, final int numCoeffs, final double fixedKappa,
            final Constraint constraint) {
        this(constrainAtZero, weights, null, l2, minCutoffTime, maxCutoffTime, numCoeffs, fixedKappa, constraint);
    }

    public ExponentialSplinesFitting(final boolean constrainAtZero, final int numCoeffs, final double fixedKappa,
            final Array weights, final Constraint constraint) {
        this(constrainAtZero, weights, null, new Array(0), 0.0, Double.MAX_VALUE, numCoeffs, fixedKappa, constraint);
    }

    @Override
    public ExponentialSplinesFitting clone() {
        return new ExponentialSplinesFitting(constrainAtZero_, weights(), optimizationMethod(), l2(), 0.0,
                Double.MAX_VALUE, numCoeffs_, fixedKappa_, constraint());
    }

    @Override
    public int size() {
        final int N = constrainAtZero_ ? numCoeffs_ : numCoeffs_ + 1;
        // one fewer optimization parameters if kappa is fixed
        return isFixedKappa() ? N - 1 : N;
    }

    @Override
    protected double discountFunction(final Array x, final double t) {
        double d = 0.0;
        final int N = size();
        // Use the internal fixedKappa_ member if set, otherwise take kappa from the passed x[] array.
        final double kappa = isFixedKappa() ? fixedKappa_ : x.get(N - 1);
        double coeff = 0.0;

        if ( !constrainAtZero_ ) {
            for ( int i = 0; i < N - 1; ++i ) {
                d += x.get(i) * Math.exp(-kappa * (i + 1) * t);
            }
        } else {
            // notation:
            // d(t) = coeff * exp(-kappa*1*t) + x[0] * exp(-kappa*2*t) +
            //        x[1] * exp(-kappa*3*t) + .. + x[7] * exp(-kappa*9*t)
            for ( int i = 0; i < N - 1; ++i ) {
                d += x.get(i) * Math.exp(-kappa * (i + 2) * t);
                coeff += x.get(i);
            }
            coeff = 1.0 - coeff;
            d += coeff * Math.exp(-kappa * t);
        }

        return d;
    }

    /** {@code true} iff the {@code kappa} parameter is held fixed (i.e. not {@link Double#NaN}). */
    private boolean isFixedKappa() {
        return !Double.isNaN(fixedKappa_);
    }
}

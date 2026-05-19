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

package org.jquantlib.termstructures.yieldcurves;

import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Svensson fitting method.
 * <p>
 * Fits a discount function {@code d(t) = exp(-r*t)} where the zero rate {@code r} is defined as
 * <pre>
 * r = c0 + (c1 + c2) * (1 - exp(-k*t)) / (k*t) - c2 * exp(-k*t)
 *   + c3 * ((1 - exp(-k1*t)) / (k1*t) - exp(-k1*t))
 * </pre>
 * with parameters {@code (c0, c1, c2, c3, kappa, kappa_1)}.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code SvenssonFitting}
 * (ql/termstructures/yield/nonlinearfittingmethods.{hpp,cpp}).
 *
 * <p>Phase 5d.5-ZCS+FB.
 *
 * <p>Reference: Svensson, L. (1994). "Estimating and interpreting forward
 * interest rates: Sweden 1992-4." Discussion paper, Centre for Economic Policy Research(1051).
 */
public class SvenssonFitting extends FittingMethod {

    private static final double QL_EPSILON = Constants.QL_EPSILON;

    public SvenssonFitting() {
        this(new Array(0), null, new Array(0), 0.0, Double.MAX_VALUE, new NoConstraint());
    }

    public SvenssonFitting(final Array weights) {
        this(weights, null, new Array(0), 0.0, Double.MAX_VALUE, new NoConstraint());
    }

    public SvenssonFitting(final Array weights, final OptimizationMethod optimizationMethod, final Array l2,
            final double minCutoffTime, final double maxCutoffTime, final Constraint constraint) {
        super(true, weights, optimizationMethod, l2, minCutoffTime, maxCutoffTime, constraint);
    }

    public SvenssonFitting(final Array weights, final Array l2, final double minCutoffTime, final double maxCutoffTime,
            final Constraint constraint) {
        this(weights, null, l2, minCutoffTime, maxCutoffTime, constraint);
    }

    @Override
    public SvenssonFitting clone() {
        return new SvenssonFitting(weights(), optimizationMethod(), l2(), 0.0, Double.MAX_VALUE, constraint());
    }

    @Override
    public int size() {
        return 6;
    }

    @Override
    protected double discountFunction(final Array x, final double t) {
        final double kappa = x.get(size() - 2);
        final double kappa1 = x.get(size() - 1);

        final double zeroRate =
                x.get(0) + (x.get(1) + x.get(2)) * (1.0 - Math.exp(-kappa * t)) / ((kappa + QL_EPSILON) * (t
                        + QL_EPSILON)) - x.get(2) * Math.exp(-kappa * t) + x.get(3) * (
                        ((1.0 - Math.exp(-kappa1 * t)) / ((kappa1 + QL_EPSILON) * (t + QL_EPSILON))) - Math.exp(
                                -kappa1 * t));
        return Math.exp(-zeroRate * t);
    }
}

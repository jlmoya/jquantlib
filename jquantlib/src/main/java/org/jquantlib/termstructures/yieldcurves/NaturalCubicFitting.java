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

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.NaturalCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;

/**
 * Natural cubic spline fitting method.
 * <p>
 * Fits a discount function using natural cubic spline interpolation where the parameters are
 * nodal discount values {@code d(t_i)}. The natural boundary condition (second derivative = 0
 * at ends) is used. If {@code constrainAtZero} is true (always true in C++), {@code d(0)} is
 * fixed to 1.0 and the parameter vector {@code x} contains the remaining nodal values.
 *
 * <p>Faithful port of QuantLib v1.42.1 {@code NaturalCubicFitting}
 * (ql/termstructures/yield/nonlinearfittingmethods.{hpp,cpp}).
 *
 * <p>Phase 2 forward closure L2-B.
 */
public class NaturalCubicFitting extends FittingMethod {

    private final double[] knotTimes_;
    private final int size_;

    public NaturalCubicFitting(final double[] knotTimes) {
        this(knotTimes, new Array(0), null, new Array(0), 0.0, Double.MAX_VALUE, new NoConstraint());
    }

    public NaturalCubicFitting(final double[] knotTimes, final Array weights,
            final OptimizationMethod optimizationMethod, final Array l2, final double minCutoffTime,
            final double maxCutoffTime, final Constraint constraint) {
        super(true, weights, optimizationMethod, l2, minCutoffTime, maxCutoffTime, constraint);

        // C++: knotTimes_.push_back(0.0); sort; unique (within 1e-14)
        final double[] tmp = new double[knotTimes.length + 1];
        System.arraycopy(knotTimes, 0, tmp, 0, knotTimes.length);
        tmp[knotTimes.length] = 0.0;
        Arrays.sort(tmp);

        // Deduplicate within 1e-14
        final double[] deduped = new double[tmp.length];
        int outIdx = 0;
        deduped[outIdx++] = tmp[0];
        for ( int i = 1; i < tmp.length; ++i ) {
            if ( Math.abs(tmp[i] - deduped[outIdx - 1]) > 1e-14 ) {
                deduped[outIdx++] = tmp[i];
            }
        }
        this.knotTimes_ = Arrays.copyOf(deduped, outIdx);

        QL.require(knotTimes_.length >= 2, "NaturalCubicFitting: at least two knot times required");

        final int n = knotTimes_.length;
        this.size_ = n - 1;

        for ( int i = 0; i + 1 < n; ++i ) {
            final double h = knotTimes_[i + 1] - knotTimes_[i];
            QL.require(h > 1e-14,
                    "NaturalCubicFitting: knot times must be strictly increasing (non-zero spacing)");
            QL.require(Double.isFinite(h), "NaturalCubicFitting: non-finite knot spacing");
        }
    }

    public NaturalCubicFitting(final double[] knotTimes, final Array weights, final Array l2,
            final double minCutoffTime, final double maxCutoffTime, final Constraint constraint) {
        this(knotTimes, weights, null, l2, minCutoffTime, maxCutoffTime, constraint);
    }

    @Override
    public NaturalCubicFitting clone() {
        // The post-construction stored knotTimes_ already includes the prepended 0.0; passing it again would
        // push another 0.0 then dedupe — same effective vector. Safe for the C++ semantic.
        return new NaturalCubicFitting(knotTimes_, weights(), optimizationMethod(), l2(), 0.0, Double.MAX_VALUE,
                constraint());
    }

    @Override
    public int size() {
        return size_;
    }

    @Override
    protected double discountFunction(final Array x, final double t) {
        final int n = knotTimes_.length;
        final int expected = size();
        QL.require(x.size() == expected,
                "NaturalCubicFitting::discountFunction(): parameter size mismatch: expected " + expected + " got "
                        + x.size());

        final double[] y = new double[n];
        y[0] = 1.0;
        for ( int i = 1; i < n; ++i ) {
            y[i] = x.get(i - 1);
        }

        for ( int i = 0; i < n; ++i ) {
            QL.require(Double.isFinite(y[i]),
                    "NaturalCubicFitting::discountFunction(): non-finite nodal value");
        }

        final Array xs = new Array(knotTimes_);
        final Array ys = new Array(y);
        final NaturalCubicInterpolation spline = new NaturalCubicInterpolation(xs, ys);
        spline.update();
        final double tClamped = Math.min(Math.max(t, knotTimes_[0]), knotTimes_[n - 1]);
        return spline.op(tClamped);
    }
}

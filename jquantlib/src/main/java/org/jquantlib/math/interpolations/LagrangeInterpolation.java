/*
 Copyright (C) 2016 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.math.interpolations;

import org.jquantlib.math.Constants;

/**
 * Barycentric Lagrange interpolation over a fixed set of x-nodes.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/interpolations/lagrangeinterpolation.hpp}.
 *
 * <p>Implements the barycentric form of Lagrange interpolation:
 * <pre>
 *   P(x) = [sum_i lambda_i/(x-x_i) * y_i] / [sum_i lambda_i/(x-x_i)]
 * </pre>
 * where the barycentric weights are:
 * <pre>
 *   lambda_i = 1 / prod_{j!=i} (cM1*(x_i - x_j))
 *   with cM1 = 4 / (x_{n-1} - x_0)
 * </pre>
 *
 * <p>Two usage modes:
 * <ol>
 *   <li>Fixed y: construct with both x and y arrays, call {@link #op(double)}.</li>
 *   <li>Variable y: construct with x array (y values are placeholders), call
 *       {@link #value(double[], double)} with updated y values. This is the
 *       pattern used by {@code NormalCLVModel.MappingFunction}.</li>
 * </ol>
 *
 * <p>References:
 * J.-P. Berrut and L.N. Trefethen, Barycentric Lagrange Interpolation,
 * SIAM Review, 46(3):501–517, 2004.
 *
 * @author Phase 4j port
 */
public class LagrangeInterpolation {

    private final double[] xNodes_;
    private double[] yValues_;
    private final double[] lambda_;   // barycentric weights

    /**
     * Construct with fixed x-nodes and y-values.
     *
     * @param x x-nodes (must be distinct and sorted)
     * @param y y-values at the x-nodes
     */
    public LagrangeInterpolation(final double[] x, final double[] y) {
        this.xNodes_  = x.clone();
        this.yValues_ = y.clone();
        this.lambda_  = computeWeights(x);
    }

    /**
     * Construct with fixed x-nodes; y-values will be supplied dynamically
     * via {@link #value(double[], double)}.
     *
     * @param x x-nodes (must be distinct and sorted)
     */
    public LagrangeInterpolation(final double[] x) {
        this.xNodes_  = x.clone();
        this.yValues_ = new double[x.length];
        this.lambda_  = computeWeights(x);
    }

    /**
     * Update the y-values stored in this interpolation.
     *
     * @param y new y-values (must have the same length as the x-nodes)
     */
    public void setY(final double[] y) {
        System.arraycopy(y, 0, yValues_, 0, y.length);
    }

    /**
     * Evaluate the interpolation at {@code x} using the stored y-values.
     *
     * @param x  query point
     * @param extrapolate if false (unused here, always extrapolates)
     * @return interpolated value
     */
    public double op(final double x, final boolean extrapolate) {
        return _value(yValues_, x);
    }

    /**
     * Evaluate the interpolation at {@code x} using the stored y-values.
     */
    public double op(final double x) {
        return _value(yValues_, x);
    }

    /**
     * Evaluate the interpolation at {@code x} using externally supplied {@code y}-values.
     * This mirrors the C++ {@code LagrangeInterpolation::value(const Array&, Real)} method.
     *
     * @param y y-values (same length as x-nodes)
     * @param x query point
     * @return interpolated value
     */
    public double value(final double[] y, final double x) {
        return _value(y, x);
    }

    // --- private ---

    private static double[] computeWeights(final double[] x) {
        final int n    = x.length;
        final double cM1 = 4.0 / (x[n - 1] - x[0]);
        final double[] lambda = new double[n];

        for (int i = 0; i < n; ++i) {
            lambda[i] = 1.0;
            final double xi = x[i];
            for (int j = 0; j < n; ++j) {
                if (i != j) {
                    lambda[i] *= cM1 * (xi - x[j]);
                }
            }
            lambda[i] = 1.0 / lambda[i];
        }
        return lambda;
    }

    private double _value(final double[] y, final double x) {
        final int n = xNodes_.length;

        // Check if x coincides with a node (within eps).
        // The condition uses `<= eps` (not `< eps`) so that exact equality
        // (e.g. x=0.0 at node 0.0, giving eps=0) is correctly handled.
        final double eps = 10.0 * Constants.QL_EPSILON * Math.abs(x);

        // Binary search for nearest node from below
        int lo = 0, hi = n;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (xNodes_[mid] < x - eps) lo = mid + 1;
            else hi = mid;
        }
        // lo is the first index where xNodes_[lo] >= x - eps
        if (lo < n && xNodes_[lo] - x <= eps) {
            return y[lo];
        }

        // Barycentric formula
        double num = 0.0, den = 0.0;
        for (int i = 0; i < n; ++i) {
            final double alpha = lambda_[i] / (x - xNodes_[i]);
            num += alpha * y[i];
            den += alpha;
        }
        return num / den;
    }
}

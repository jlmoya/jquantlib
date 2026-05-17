/*
 Copyright (C) 2020 Klaus Spanderen
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

package org.jquantlib.math.integrals;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.jquantlib.QL;

/**
 * Tensor-product multi-dimensional Gaussian quadrature.
 *
 * <p>Java port of {@code QuantLib::MultiDimGaussianIntegration} from
 * v1.42.1 {@code ql/math/integrals/gaussianquadratures.{hpp,cpp}}. Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Given a list of per-dimension orders {@code ns[j]} and a factory
 * {@code genQuad(n)} that builds a 1-D {@link GaussianQuadrature} of that
 * order, the constructor builds the tensor-product weights and abscissae
 * laid out in column-major lexicographic order (matching the C++
 * {@code spacing} computation):
 * <pre>
 *   spacing[0] = 1;  spacing[j] = ns[0] * ns[1] * ... * ns[j-1]
 *   for i in [0, N):
 *     for j in [0, m):
 *       nx = (i / spacing[j]) % ns[j]
 *       weights_[i] *= per_dim_w[j][nx]
 *       x_[i][j]     = per_dim_x[j][nx]
 * </pre>
 * Per-dimension quadratures are deduplicated by order via {@code n2x} /
 * {@code n2weights} caches, mirroring the C++ {@code std::map} usage.
 */
public class MultiDimGaussianIntegration {

    private final double[] weights_;
    private final double[][] x_;  // x_[i] is the m-dim point for sample i

    /**
     * @param ns      per-dimension quadrature orders ({@code ns.length} = dimensions)
     * @param genQuad factory producing a 1-D {@link GaussianQuadrature} for an order
     */
    public MultiDimGaussianIntegration(final int[] ns,
                                       final IntFunction<GaussianQuadrature> genQuad) {
        QL.require(ns.length > 0, "MultiDimGaussianIntegration: at least one dimension required");

        final int m = ns.length;
        int n = 1;
        for (int j = 0; j < m; ++j) {
            QL.require(ns[j] > 0, "MultiDimGaussianIntegration: order must be positive");
            n *= ns[j];
        }

        this.weights_ = new double[n];
        this.x_ = new double[n][m];
        for (int i = 0; i < n; ++i) {
            weights_[i] = 1.0;
        }

        // spacing[0] = 1; spacing[j] = prod_{k<j} ns[k]
        final int[] spacing = new int[m];
        spacing[0] = 1;
        for (int j = 1; j < m; ++j) {
            spacing[j] = spacing[j - 1] * ns[j - 1];
        }

        // Cache per-order abscissa / weight tables to avoid rebuilding identical quadratures.
        final Map<Integer, double[]> n2x = new HashMap<>();
        final Map<Integer, double[]> n2weights = new HashMap<>();
        for (final int order : ns) {
            if (!n2x.containsKey(order)) {
                final GaussianQuadrature quad = genQuad.apply(order);
                final double[] xs = new double[quad.order()];
                final double[] ws = new double[quad.order()];
                for (int k = 0; k < quad.order(); ++k) {
                    xs[k] = quad.x(k);
                    ws[k] = quad.weight(k);
                }
                n2x.put(order, xs);
                n2weights.put(order, ws);
            }
        }

        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < m; ++j) {
                final int order = ns[j];
                final int nx = (i / spacing[j]) % ns[j];
                weights_[i] *= n2weights.get(order)[nx];
                x_[i][j] = n2x.get(order)[nx];
            }
        }
    }

    /**
     * Evaluate {@code Σ_i w_i * f(x_i)} over the tensor-product grid.
     *
     * <p>Summation order matches the C++ implementation (ascending sample index).
     */
    public double op(final Function<double[], Double> f) {
        double s = 0.0;
        for (int i = 0; i < x_.length; ++i) {
            s += weights_[i] * f.apply(x_[i]);
        }
        return s;
    }

    /** Number of tensor-product samples. */
    public int size() {
        return weights_.length;
    }

    /** Read-only access to a tensor-product weight. */
    public double weight(final int i) {
        return weights_[i];
    }

    /** Read-only access to a tensor-product abscissa (length = dimensions). */
    public double[] x(final int i) {
        return x_[i].clone();
    }
}

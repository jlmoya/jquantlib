/*
 Copyright (C) 2014 Jose Aparicio
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

import java.util.function.Function;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;

/**
 * Multi-dimensional Gauss-Hermite quadrature, computing
 * {@code ∫_{R^d} f(x_1,...,x_d) dx_1 ... dx_d} as a tensor product of 1D
 * Gauss-Hermite rules along each dimension.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/multidimquadrature.{hpp,cpp}} (Jose Aparicio,
 * 2014). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ implementation uses template recursion over dimension to avoid
 * runtime depth tests; Java replaces this with a single recursive helper
 * dispatched on a mutable buffer index. Algorithmic semantics match the C++
 * cascade {@code scalarIntegrator<intgDepth>}.
 *
 * <p>This implementation provides only the scalar integrand variant
 * ({@code integrate(f) → double}); the vector variant ({@code integrateV})
 * may be added in a follow-up phase if needed by client models.
 *
 * @see GaussHermiteIntegration
 */
public final class GaussianQuadMultidimIntegrator {

    /** Maximum supported dimension; matches C++ {@code maxDimensions_}. */
    public static final int MAX_DIMENSIONS = 15;

    private final GaussHermiteIntegration integral_;
    private final int dimension_;
    /** Buffer for the integration variable; mutated during recursion. */
    private final double[] varBuffer_;

    /**
     * @param dimension number of dimensions of the argument of the integrand
     * @param quadOrder Gauss-Hermite quadrature order applied to each dimension
     * @param mu        weight-function parameter; weight is {@code |x|^{2 mu} e^{-x^2}}
     */
    public GaussianQuadMultidimIntegrator(final int dimension, final int quadOrder, final double mu) {
        QL.require(dimension > 0, "dimension must be positive");
        QL.require(dimension <= MAX_DIMENSIONS, "dimension exceeds MAX_DIMENSIONS");
        QL.require(quadOrder > 0, "quadOrder must be positive");
        this.integral_ = new GaussHermiteIntegration(quadOrder, mu);
        this.dimension_ = dimension;
        this.varBuffer_ = new double[dimension];
    }

    /** Convenience constructor with default {@code mu = 0.0}. */
    public GaussianQuadMultidimIntegrator(final int dimension, final int quadOrder) {
        this(dimension, quadOrder, 0.0);
    }

    /** Number of dimensions. */
    public int dimension() {
        return dimension_;
    }

    /** Quadrature order. */
    public int order() {
        return integral_.order();
    }

    /**
     * Integrate scalar function {@code f: R^d → R} as a tensor product of 1D
     * Gauss-Hermite quadratures.
     *
     * @param f integrand; receives a {@code double[]} of length {@link #dimension()}
     * @return the multi-dimensional integral
     */
    public double integrate(final Function<double[], Double> f) {
        // Top-level entry: integrate over the highest-index dimension.
        return scalarIntegrator(dimension_, f);
    }

    /**
     * Recursive helper. The C++ template depth corresponds to the {@code level}
     * argument here. At {@code level == 1} (terminal) we evaluate the integrand
     * directly; otherwise we wrap a 1D Gauss-Hermite call.
     *
     * <p>The C++ recursion writes {@code varBuffer_[intgDepth-1]} before
     * recursing into {@code intgDepth-1}; this Java method mirrors that
     * indexing.
     */
    private double scalarIntegrator(final int level, final Function<double[], Double> f) {
        if (level == 1) {
            // Terminal: 1D Gauss-Hermite over varBuffer_[0]; mFctr already
            // captured above, but at the top level we still need to do the
            // full 1D integration. Match C++: top-level entry calls
            // integral_(z -> integrationEntries_[dim-1](f, z)) which always
            // performs an outer integration. So the recursion is uniform:
            // at every level we wrap an integral_().
            // Special-case here is when caller invokes the level=1 cascade.
            final Ops.DoubleOp op = (final double z) -> {
                varBuffer_[0] = z;
                return f.apply(varBuffer_);
            };
            return integral_.op(op);
        }
        // Generic level: integrate dimension (level-1), recursing into (level-1).
        final int idx = level - 1;
        final Ops.DoubleOp op = (final double z) -> {
            varBuffer_[idx] = z;
            return scalarIntegrator(idx, f);
        };
        return integral_.op(op);
    }
}

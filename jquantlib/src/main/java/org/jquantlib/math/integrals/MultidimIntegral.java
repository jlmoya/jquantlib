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

import java.util.List;
import java.util.function.Function;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;

/**
 * Integrates a scalar function of vector domain over a hyper-rectangle
 * {@code [a_0,b_0] x ... x [a_{n-1},b_{n-1}]} using a collection of arbitrary
 * 1D integrators along each dimension (one per dimension).
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/experimental/math/multidimintegrator.{hpp,cpp}}
 * by Jose Aparicio (2014). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ implementation uses template recursion over dimension to avoid
 * runtime depth tests; Java replaces this with a single recursive
 * {@code integrateLevel} that dispatches on a mutable buffer index. The
 * algorithmic semantics match the C++ {@code integrate<nT>} cascade.
 *
 * <p>This class generalises to arbitrary N the functionality in
 * {@code TwoDimensionalIntegral}.
 *
 * @see Integrator
 */
public final class MultidimIntegral {

    /** Maximum supported dimension; matches C++ {@code maxDimensions_}. */
    public static final int MAX_DIMENSIONS = 15;

    private final Integrator[] integrators_;
    /** Buffer for the integration variable; mutated during recursion. */
    private final double[] varBuffer_;

    /**
     * @param integrators one Integrator per integration dimension. List size must
     *                    not exceed {@link #MAX_DIMENSIONS}.
     */
    public MultidimIntegral(final List<Integrator> integrators) {
        QL.require(integrators != null, "integrators list must not be null");
        QL.require(integrators.size() <= MAX_DIMENSIONS,
                "Too many dimensions in integration.");
        this.integrators_ = integrators.toArray(new Integrator[0]);
        this.varBuffer_ = new double[this.integrators_.length];
    }

    /**
     * Integrate scalar function {@code f} over the hyper-rectangle
     * {@code [a_i, b_i]} for each dimension {@code i}.
     *
     * @param f integrand; receives a {@code double[]} of length equal to the
     *          number of integration dimensions
     * @param a vector of lower bounds; length must equal the number of integrators
     * @param b vector of upper bounds; length must equal the number of integrators
     * @return the multi-dimensional integral
     */
    public double op(final Function<double[], Double> f, final double[] a, final double[] b) {
        QL.require(a != null && b != null, "a and b must not be null");
        QL.require(a.length == b.length && b.length == integrators_.length,
                "Incompatible integration problem dimensions");
        // Top-level entry: integrate over the highest-index dimension.
        return integrateLevel(integrators_.length - 1, f, a, b);
    }

    /**
     * Recursive helper. Integrates dimension {@code level} using
     * {@code integrators_[level]} with integrand obtained by binding
     * {@code varBuffer_[level] = z} and recursing on {@code level - 1};
     * at level 0 the integrand evaluates {@code f(varBuffer_)}.
     *
     * <p>Mirrors C++ {@code integrate<nT>} via the template-recursion
     * {@code vectorBinder<nT> -> integrate<nT-1>} cascade.
     */
    private double integrateLevel(
            final int level,
            final Function<double[], Double> f,
            final double[] a, final double[] b) {
        final Ops.DoubleOp op = (final double z) -> {
            varBuffer_[level] = z;
            if (level == 0) {
                return f.apply(varBuffer_);
            }
            return integrateLevel(level - 1, f, a, b);
        };
        return integrators_[level].op(op, a[level], b[level]);
    }
}

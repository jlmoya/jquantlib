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

package org.jquantlib.experimental.math;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.Integrator;

import java.util.List;

/**
 * Integrates a scalar function of vector domain.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/multidimintegrator.{hpp,cpp}}.
 *
 * <p>Uses a collection of arbitrary 1D integrators along each of the
 * dimensions. Recursive cross-section integration; the outer integrator iterates over the last variable, while the
 * next-level integrator handles the remaining variables.
 *
 * <p>The C++ template metaprogramming is replaced by direct recursion in this
 * Java port.
 */
public class MultidimIntegral {

    private static final int MAX_DIMENSIONS = 15;
    private final Integrator[] integrators_;
    private final double[] varBuffer_;
    public MultidimIntegral(final List< Integrator > integrators) {
        QL.require(integrators.size() <= MAX_DIMENSIONS, "Too many dimensions in integration.");
        this.integrators_ = integrators.toArray(new Integrator[0]);
        this.varBuffer_ = new double[integrators.size()];
    }

    /**
     * Integrate {@code f} over the box {@code a × b}.
     *
     * @param f scalar integrand of vector domain
     * @param a lower bounds (length == number of integrators)
     * @param b upper bounds (length == number of integrators)
     */
    public double op(final MultiVarOp f, final double[] a, final double[] b) {
        QL.require(a.length == b.length && b.length == integrators_.length,
                "Incompatible integration problem dimensions");
        return integrate(integrators_.length - 1, f, a, b);
    }

    /**
     * Integrate over dimension {@code dim} using the dim-th integrator; recursively integrate the remaining
     * dimensions.
     */
    private double integrate(final int dim, final MultiVarOp f, final double[] a, final double[] b) {
        return integrators_[dim].op(new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                varBuffer_[dim] = x;
                if ( dim == 0 ) {
                    return f.op(varBuffer_);
                } else {
                    return integrate(dim - 1, f, a, b);
                }
            }
        }, a[dim], b[dim]);
    }

    /** Functional interface for the multi-dim integrand. */
    public interface MultiVarOp {
        /** @param args length must equal the number of integrators */
        double op(double[] args);
    }
}

/*
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

/*
 Copyright (C) 2013 Klaus Spanderen
*/

package org.jquantlib.math.integrals;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;

/**
 * Integral of a two-dimensional function.
 *
 * <p>The integral of a two dimensional function {@code f(x,y)}
 * between {@code (a_x, a_y)} and {@code (b_x, b_y)} is calculated by
 * means of two nested integrations: the outer integrator integrates over
 * {@code x in [a_x, b_x]}, and for each {@code x} the inner integrator
 * integrates {@code y in [a_y, b_y]}.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/integrals/twodimensionalintegral.hpp} (Klaus Spanderen,
 * 2013). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Unlike {@link MultidimIntegral} (tensor product with one integrator per
 * dimension as a homogeneous array), {@code TwoDimensionalIntegral} matches
 * the C++ class which takes two distinct {@code outer}/{@code inner}
 * integrator instances explicitly.
 *
 * @author JQuantLib migration contributors
 * @see Integrator
 * @see MultidimIntegral
 */
public final class TwoDimensionalIntegral {

    private final Integrator integratorX_;
    private final Integrator integratorY_;

    /**
     * @param integratorX outer integrator (over the x variable)
     * @param integratorY inner integrator (over the y variable)
     */
    public TwoDimensionalIntegral(final Integrator integratorX,
                                  final Integrator integratorY) {
        QL.require(integratorX != null, "outer (x) integrator must be non-null");
        QL.require(integratorY != null, "inner (y) integrator must be non-null");
        this.integratorX_ = integratorX;
        this.integratorY_ = integratorY;
    }

    /**
     * Evaluate the double integral of {@code f} over
     * {@code [a.first, b.first] x [a.second, b.second]}.
     *
     * <p>Mirrors C++ {@code operator()(const std::function<Real(Real, Real)>&,
     * const std::pair<Real, Real>& a, const std::pair<Real, Real>& b)}.
     *
     * @param f integrand {@code f(x, y)}
     * @param ax lower x bound (C++ {@code a.first})
     * @param ay lower y bound (C++ {@code a.second})
     * @param bx upper x bound (C++ {@code b.first})
     * @param by upper y bound (C++ {@code b.second})
     */
    public double op(final Ops.BinaryDoubleOp f,
                     final double ax, final double ay,
                     final double bx, final double by) {
        QL.require(f != null, "integrand must be non-null");
        // Outer integral over x. The inner integrand g(x) is itself a 1-D
        // integration over y, executed by integratorY_.
        return integratorX_.op(new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                return g(f, x, ay, by);
            }
        }, ax, bx);
    }

    private double g(final Ops.BinaryDoubleOp f,
                     final double x, final double a, final double b) {
        return integratorY_.op(new Ops.DoubleOp() {
            @Override
            public double op(final double y) {
                return f.op(x, y);
            }
        }, a, b);
    }
}

/*
 Copyright (C) 2021 Klaus Spanderen
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

import org.jquantlib.math.Ops;

/**
 * Chebyshev interpolation between discrete Chebyshev nodes.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/interpolations/chebyshevinterpolation.hpp/.cpp}.
 *
 * <p>Reference: S.A. Sarra, <i>Chebyshev Interpolation: An Interactive Tour</i>.
 *
 * <p>The implementation reuses the barycentric {@link LagrangeInterpolation}
 * over the Chebyshev nodes, which is exactly what the C++ implementation does (it instantiates
 * {@code detail::LagrangeInterpolationImpl} on top of the Chebyshev grid).
 *
 * <p>Two flavours of Chebyshev nodes are supported:
 * <ul>
 *   <li>{@link PointsType#FirstKind}: {@code t_i = -cos((i + 0.5) * pi / n)},
 *       {@code i = 0..n-1}.</li>
 *   <li>{@link PointsType#SecondKind}: {@code t_i = -cos(i * pi / (n - 1))},
 *       {@code i = 0..n-1} (includes the endpoints {@code -1, +1}).</li>
 * </ul>
 *
 * <p>Phase 5e.5b-CFC-d-96.
 */
public class ChebyshevInterpolation {

    private final double[] x_;
    private final double[] y_;
    private final LagrangeInterpolation impl_;
    /**
     * Construct from an array of y-values; nodes are generated automatically (second-kind by default to match the C++
     * constructor's default argument).
     */
    public ChebyshevInterpolation(final double[] y) {
        this(y, PointsType.SecondKind);
    }

    /** Construct from an array of y-values with the given node flavour. */
    public ChebyshevInterpolation(final double[] y, final PointsType pointsType) {
        this.x_ = nodes(y.length, pointsType);
        this.y_ = y.clone();
        this.impl_ = new LagrangeInterpolation(this.x_, this.y_);
    }

    /**
     * Construct by sampling {@code f} at the {@code n} Chebyshev nodes (second-kind by default).
     */
    public ChebyshevInterpolation(final int n, final Ops.DoubleOp f) {
        this(n, f, PointsType.SecondKind);
    }

    /** Construct by sampling {@code f} at the {@code n} Chebyshev nodes. */
    public ChebyshevInterpolation(final int n, final Ops.DoubleOp f, final PointsType pointsType) {
        this.x_ = nodes(n, pointsType);
        this.y_ = new double[n];
        for ( int i = 0; i < n; ++i ) {
            this.y_[i] = f.op(this.x_[i]);
        }
        this.impl_ = new LagrangeInterpolation(this.x_, this.y_);
    }

    /**
     * Static helper: generate the {@code n} Chebyshev nodes of the given flavour, on the interval {@code [-1, +1]}.
     */
    public static double[] nodes(final int n, final PointsType pointsType) {
        final double[] t = new double[n];
        switch ( pointsType ) {
        case FirstKind:
            for ( int i = 0; i < n; ++i ) {
                t[i] = -Math.cos((i + 0.5) * Math.PI / n);
            }
            break;
        case SecondKind:
            for ( int i = 0; i < n; ++i ) {
                t[i] = -Math.cos(i * Math.PI / (n - 1));
            }
            break;
        default:
            throw new IllegalArgumentException("unknown Chebyshev interpolation points type: " + pointsType);
        }
        return t;
    }

    /** Evaluate the interpolation at {@code x}. */
    public double op(final double x) {
        return impl_.value(y_, x);
    }

    /**
     * Evaluate the interpolation at {@code x}. The {@code allowExtrapolation} flag is accepted for parity with the C++
     * {@code Interpolation::operator()} but ignored: Lagrange interpolation extrapolates implicitly.
     */
    public double op(final double x, final boolean allowExtrapolation) {
        return impl_.value(y_, x);
    }

    /** Replace the y-values in-place; the new array must have the same length. */
    public void updateY(final double[] y) {
        if ( y.length != y_.length ) {
            throw new IllegalArgumentException(
                    "interpolation override has the wrong length: " + y.length + " (expected " + y_.length + ")");
        }
        System.arraycopy(y, 0, y_, 0, y.length);
        impl_.setY(y_);
    }

    /** Return the Chebyshev nodes for this interpolation (a defensive copy). */
    public double[] nodes() {
        return x_.clone();
    }

    /** Chebyshev node flavour. */
    public enum PointsType {
        /** First-kind nodes: zeros of the Chebyshev polynomial T_n. */
        FirstKind,
        /** Second-kind nodes: extrema of T_{n-1} (includes -1 and +1). */
        SecondKind
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

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
 Copyright (C) 2009 Dirk Eddelbuettel
 Copyright (C) 2006, 2009, 2010 Klaus Spanderen
 Copyright (C) 2010 Kakhkhor Abdijalilov
 Copyright (C) 2010 Slava Mazur
*/

package org.jquantlib.math;

import org.jquantlib.math.matrixutilities.Array;

import java.util.ArrayList;
import java.util.List;

/**
 * Linear regression {@code y_i = a_0 + a_1*x_0 + ... + a_n*x_{n-1} + eps}, solved via SVD-based least squares.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/math/linearleastsquaresregression.hpp::LinearRegression} (Phase 5e.5b-CFC-d-16b). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Convenience wrapper over {@link GeneralLinearLeastSquares} that builds
 * a default basis from the input. The basis layout matches C++ {@code details::LinearFcts}:
 *
 * <ul>
 *   <li>For single-variate ({@code double[] x}, {@code double[] y}):
 *     basis is {@code [intercept_const, identity(x)]} when
 *     {@code intercept != 0}, else just {@code [identity(x)]}.</li>
 *   <li>For multi-variate ({@code Array[] x}, {@code double[] y}): basis is
 *     {@code [intercept_const, x[0], x[1], ..., x[m-1]]} when
 *     {@code intercept != 0}, else {@code [x[0], ..., x[m-1]]}, where
 *     {@code m = x[0].size()}.</li>
 * </ul>
 *
 * <p>Callers that need a custom basis should use the explicit constructors
 * that take a {@code List} of basis functions, or use
 * {@link GeneralLinearLeastSquares} directly.
 *
 * @author JQuantLib
 */
public class LinearRegression extends GeneralLinearLeastSquares {

    /**
     * Single-variate regression {@code y = a_0 + a_1 * x + eps} with unit intercept (default behaviour matching the C++
     * default {@code intercept = 1.0}).
     *
     * @param x sample state values, size {@code n}
     * @param y observed values, size {@code n}
     */
    public LinearRegression(final double[] x, final double[] y) {
        this(x, y, 1.0);
    }

    /**
     * Single-variate regression {@code y = a_0 + a_1 * x + eps} with the given intercept term: pass {@code 0.0} to omit
     * the constant column.
     *
     * @param x         sample state values, size {@code n}
     * @param y         observed values, size {@code n}
     * @param intercept intercept value (0.0 → no constant column)
     */
    public LinearRegression(final double[] x, final double[] y, final double intercept) {
        super(x, y, makeLinearFcts(intercept));
    }

    /**
     * Single-variate regression with explicit basis system.
     *
     * @param x sample state values, size {@code n}
     * @param y observed values, size {@code n}
     * @param v basis system, size {@code m}
     */
    public LinearRegression(final double[] x, final double[] y, final List< ? extends Ops.DoubleOp > v) {
        super(x, y, v);
    }

    /**
     * Multi-variate regression {@code y = a_0 + a_1*x[0] + ... + a_m*x[m-1] + eps} with unit intercept.
     *
     * @param x sample state vectors, size {@code n}
     * @param y observed values, size {@code n}
     */
    public LinearRegression(final Array[] x, final double[] y) {
        this(x, y, 1.0);
    }

    /**
     * Multi-variate regression with the given intercept term.
     *
     * @param x         sample state vectors, size {@code n}
     * @param y         observed values, size {@code n}
     * @param intercept intercept value (0.0 → no constant column)
     */
    public LinearRegression(final Array[] x, final double[] y, final double intercept) {
        super(x, y, makeMultiLinearFcts(x, intercept));
    }

    /**
     * Multi-variate regression with explicit basis system.
     *
     * @param x sample state vectors, size {@code n}
     * @param y observed values, size {@code n}
     * @param v multi-state basis system, size {@code m}
     */
    public LinearRegression(final Array[] x, final double[] y, final List< ? extends Ops.ObjectToDouble< Array > > v) {
        super(x, y, v);
    }

    /**
     * Mirrors C++ {@code details::LinearFcts<arithmetic>} construction: an optional constant column followed by the
     * identity function.
     */
    private static List< Ops.DoubleOp > makeLinearFcts(final double intercept) {
        final List< Ops.DoubleOp > v = new ArrayList<>();
        if ( intercept != 0.0 ) {
            v.add(new ConstFct(intercept));
        }
        v.add(new IdFct());
        return v;
    }

    /**
     * Mirrors C++ {@code details::LinearFcts<Array-like>} construction: an optional constant column followed by one
     * component-extractor per dimension of the input vectors.
     */
    private static List< Ops.ObjectToDouble< Array > > makeMultiLinearFcts(final Array[] x, final double intercept) {
        final List< Ops.ObjectToDouble< Array > > v = new ArrayList<>();
        if ( intercept != 0.0 ) {
            v.add(new ConstArrayFct(intercept));
        }
        if ( x != null && x.length > 0 && x[0] != null ) {
            final int m = x[0].size();
            for ( int i = 0; i < m; ++i ) {
                v.add(new GetItemFct(i));
            }
        }
        return v;
    }

    // --- basis function helpers (named classes — the multi-variate path
    // would otherwise need lambda capture which would compose poorly with
    // Ops.ObjectToDouble) ---

    private static final class ConstFct implements Ops.DoubleOp {
        private final double c;

        ConstFct(final double c) {
            this.c = c;
        }

        @Override
        public double op(final double x) {
            return c;
        }
    }

    private static final class IdFct implements Ops.DoubleOp {
        @Override
        public double op(final double x) {
            return x;
        }
    }

    private static final class ConstArrayFct implements Ops.ObjectToDouble< Array > {
        private final double c;

        ConstArrayFct(final double c) {
            this.c = c;
        }

        @Override
        public double op(final Array x) {
            return c;
        }
    }

    private static final class GetItemFct implements Ops.ObjectToDouble< Array > {
        private final int i;

        GetItemFct(final int i) {
            this.i = i;
        }

        @Override
        public double op(final Array x) {
            return x.get(i);
        }
    }
}

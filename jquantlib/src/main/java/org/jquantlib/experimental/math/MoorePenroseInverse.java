/*
 Copyright (C) 2016 Peter Caspers
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

import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SVD;

/**
 * Moore-Penrose inverse of a real matrix.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/moorepenroseinverse.hpp}.
 *
 * <p>Reference: <a href="http://de.mathworks.com/help/matlab/ref/pinv.html">
 * MATLAB pinv()</a>; <a href="https://en.wikipedia.org/wiki/Moore%E2%80%93Penrose_pseudoinverse"> Wikipedia</a>.
 */
public final class MoorePenroseInverse {

    private MoorePenroseInverse() {
    }

    /** Convenience overload using the default (auto) tolerance. */
    public static Matrix moorePenroseInverse(final Matrix A) {
        return moorePenroseInverse(A, Double.NaN);
    }

    /**
     * Compute the Moore-Penrose pseudoinverse of {@code A}.
     *
     * @param A   input matrix
     * @param tol singular-value tolerance; pass {@link Double#NaN} (or {@link org.jquantlib.lang.annotation.Natural}
     *            {@code Null<Real>()} in C++ parlance) to use the default heuristic {@code max(m,n) * eps * max_sv}.
     */
    public static Matrix moorePenroseInverse(final Matrix A, final double tol) {
        final int m = A.rows();
        final int n = A.columns();

        // SVD destructively modifies its input; pass a defensive copy.
        final SVD svd = new SVD(new Matrix(A));

        double tol0 = tol;
        if ( Double.isNaN(tol0) ) {
            tol0 = Math.max(m, n) * Constants.QL_EPSILON * Math.abs(svd.singularValues().get(0));
        }

        final Matrix sp = new Matrix(n, n);
        for ( int i = 0; i < n; ++i ) {
            final double sv = svd.singularValues().get(i);
            if ( Math.abs(sv) > tol0 ) {
                sp.set(i, i, 1.0 / sv);
            }
        }

        // V * sp * U^T
        return svd.V().mul(sp).mul(svd.U().transpose());
    }
}

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
 Copyright (C) 2013 Klaus Spanderen
*/
package org.jquantlib.math.matrixutilities;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.ode.AdaptiveRungeKutta;

/**
 * Matrix exponential exp(t*M) based on the ordinary-differential-equation method.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/math/matrixutilities/expm.{hpp,cpp}} (Klaus Spanderen, 2013).
 * <p>
 * The implementation mirrors the C++ algorithm exactly: for each unit basis vector {@code e_i}, the ODE
 * {@code y' = M y} is integrated from {@code 0} to {@code t} using {@link AdaptiveRungeKutta}, and the resulting vector
 * becomes column {@code i} of the output matrix {@code exp(t*M)}.
 * <p>
 * References:
 * <ul>
 *   <li>C. Moler, C. Van Loan, 1978,
 *       <i>Nineteen Dubious Ways to Compute the Exponential of a Matrix</i>.</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d-77 carry-forward
 */
public final class Expm {

    private Expm() {
        // utility class
    }

    /**
     * Returns the matrix exponential exp(M) with default t=1.0 and tol=QL_EPSILON.
     */
    public static Matrix expm(final Matrix m) {
        return expm(m, 1.0, Constants.QL_EPSILON);
    }

    /**
     * Returns the matrix exponential exp(t*M) with default tol=QL_EPSILON.
     */
    public static Matrix expm(final Matrix m, final double t) {
        return expm(m, t, Constants.QL_EPSILON);
    }

    /**
     * Returns the matrix exponential exp(t*M).
     *
     * @param m   square matrix
     * @param t   scalar multiplier
     * @param tol RK tolerance (relative)
     * @return exp(t*M)
     */
    public static Matrix expm(final Matrix m, final double t, final double tol) {
        final int n = m.rows();
        QL.require(n == m.columns(), "Expm expects a square matrix");

        // C++ AdaptiveRungeKutta<>(eps=tol, h1=1.0e-4, hmin=0.0).
        // Mirror the exact C++ defaults so reproducibility matches bit-for-bit.
        final AdaptiveRungeKutta rk = new AdaptiveRungeKutta(tol, 1.0e-4, 0.0);

        // MatrixVectorProductFct: y' = M * y
        final double[][] mData = new double[n][n];
        for ( int i = 0; i < n; ++i ) {
            for ( int j = 0; j < n; ++j ) {
                mData[i][j] = m.get(i, j);
            }
        }
        final AdaptiveRungeKutta.OdeFct odeFct = (time, y) -> {
            final double[] result = new double[n];
            for ( int i = 0; i < n; ++i ) {
                double s = 0.0;
                final double[] row = mData[i];
                for ( int j = 0; j < n; ++j ) {
                    s += row[j] * y[j];
                }
                result[i] = s;
            }
            return result;
        };

        final Matrix result = new Matrix(n, n);
        for ( int i = 0; i < n; ++i ) {
            final double[] x0 = new double[n];
            x0[i] = 1.0;
            final double[] r = rk.solve(odeFct, x0, 0.0, t);
            for ( int j = 0; j < n; ++j ) {
                result.set(j, i, r[j]);
            }
        }
        return result;
    }
}

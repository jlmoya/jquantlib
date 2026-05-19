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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2005 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.math.matrixutilities;

import org.jquantlib.QL;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Wilkinson implicit-shift QR eigendecomposition for symmetric tridiagonal matrices (a.k.a. TQR / tridiagonal QR).
 *
 * <p>Port of QuantLib v1.42.1
 * {@code ql/math/matrixutilities/tqreigendecomposition.{hpp,cpp}} (pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Lifted from its former home as a private inner class inside
 * {@link org.jquantlib.math.integrals.GaussianQuadrature} (Phase 2j.5 Track C.1) to this public class (Phase 2k Track
 * C.1) so that it can be reused by arbitrary consumers (Gaussian1D engines, basket generators, custom smile models, …)
 * without depending on the integrals package.
 *
 * <p>References:
 * <ul>
 *   <li>Wilkinson & Reinsch, "Linear Algebra", Handbook for Automatic
 *       Computation, vol. II (Springer, 1971).</li>
 *   <li>Golub & Welsch, "Calculation of Gauss quadrature rule",
 *       Math. Comput. 23 (1986), 221–230.</li>
 * </ul>
 */
public class TqrEigenDecomposition {

    /** Eigenvalues sorted descending; first eigenvector component non-negative. */
    public final double[] d;
    /**
     * Eigenvector rows.
     * <ul>
     *   <li>{@code ev.length == n}  when {@link EigenVectorCalculation#WithEigenVector}</li>
     *   <li>{@code ev.length == 0}  when {@link EigenVectorCalculation#WithoutEigenVector}</li>
     *   <li>{@code ev.length == 1}  when {@link EigenVectorCalculation#OnlyFirstRowEigenVector}</li>
     * </ul>
     */
    public final double[][] ev;

    /**
     * Run the decomposition.
     *
     * @param diag     diagonal of the symmetric tridiagonal matrix (length n)
     * @param sub      sub-diagonal (length n-1); {@code sub[i]} is element (i+1,i)
     * @param calc     how many eigenvector rows to compute
     * @param strategy shift strategy for convergence acceleration
     */
    public TqrEigenDecomposition(final double[] diag, final double[] sub, final EigenVectorCalculation calc,
            final ShiftStrategy strategy) {
        final int n = diag.length;
        QL.require(n == sub.length + 1, "Wrong dimensions");

        this.d = new double[n];
        System.arraycopy(diag, 0, this.d, 0, n);

        final int evRows = (calc == EigenVectorCalculation.WithEigenVector)
                ? n
                : (calc == EigenVectorCalculation.WithoutEigenVector) ? 0 : 1;
        this.ev = new double[evRows][n];

        // e[0] is unused; the C++ code copies sub into e starting at index 1.
        final double[] e = new double[n];
        System.arraycopy(sub, 0, e, 1, n - 1);

        for ( int i = 0; i < evRows; ++i ) {
            ev[i][i] = 1.0;
        }

        for ( int k = n - 1; k >= 1; --k ) {
            while ( !offDiagIsZero(k, e) ) {
                int l = k;
                while ( --l > 0 && !offDiagIsZero(l, e) ) {
                    // walk down to find first zero off-diagonal element
                }

                double q = d[l];
                if ( strategy != ShiftStrategy.NoShift ) {
                    // eigenvalue of 2x2 sub matrix closer to d[k+1]
                    final double t1 = Math.sqrt(
                            0.25 * (d[k] * d[k] + d[k - 1] * d[k - 1]) - 0.5 * d[k - 1] * d[k] + e[k] * e[k]);
                    final double t2 = 0.5 * (d[k] + d[k - 1]);

                    final double lambda = (Math.abs(t2 + t1 - d[k]) < Math.abs(t2 - t1 - d[k])) ? (t2 + t1) : (t2 - t1);

                    if ( strategy == ShiftStrategy.CloseEigenValue ) {
                        q -= lambda;
                    } else {
                        q -= ((k == n - 1) ? 1.25 : 1.0) * lambda;
                    }
                }

                // QR transformation
                double sine = 1.0;
                double cosine = 1.0;
                double u = 0.0;

                boolean recoverUnderflow = false;
                for ( int i = l + 1; i <= k && !recoverUnderflow; ++i ) {
                    final double h = cosine * e[i];
                    final double p = sine * e[i];

                    e[i - 1] = Math.sqrt(p * p + q * q);
                    if ( e[i - 1] != 0.0 ) {
                        sine = p / e[i - 1];
                        cosine = q / e[i - 1];

                        final double g = d[i - 1] - u;
                        final double t = (d[i] - g) * sine + 2.0 * cosine * h;

                        u = sine * t;
                        d[i - 1] = g + u;
                        q = cosine * t - h;

                        for ( int j = 0; j < evRows; ++j ) {
                            final double tmp = ev[j][i - 1];
                            ev[j][i - 1] = sine * ev[j][i] + cosine * tmp;
                            ev[j][i] = cosine * ev[j][i] - sine * tmp;
                        }
                    } else {
                        // recover from underflow
                        d[i - 1] -= u;
                        e[l] = 0.0;
                        recoverUnderflow = true;
                    }
                }

                if ( !recoverUnderflow ) {
                    d[k] -= u;
                    e[k] = q;
                    e[l] = 0.0;
                }
            }
        }

        // Sort (eigenvalues, eigenvectors) descending by eigenvalue.
        // First eigenvector component is forced non-negative (matches C++).
        final Integer[] order = new Integer[n];
        for ( int i = 0; i < n; ++i )
            order[i] = i;
        Arrays.sort(order, new Comparator< Integer >() {
            @Override
            public int compare(final Integer a, final Integer b) {
                return Double.compare(d[b], d[a]);
            }
        });
        final double[] dCopy = d.clone();
        final double[][] evCopy = new double[evRows][n];
        for ( int j = 0; j < evRows; ++j ) {
            System.arraycopy(ev[j], 0, evCopy[j], 0, n);
        }
        for ( int i = 0; i < n; ++i ) {
            final int src = order[i];
            d[i] = dCopy[src];
            double sign = 1.0;
            if ( evRows > 0 && evCopy[0][src] < 0.0 ) {
                sign = -1.0;
            }
            for ( int j = 0; j < evRows; ++j ) {
                ev[j][i] = sign * evCopy[j][src];
            }
        }
    }

    private boolean offDiagIsZero(final int k, final double[] e) {
        // NR-style termination check: |d[k-1]|+|d[k]| == |d[k-1]|+|d[k]|+|e[k]|
        final double a = Math.abs(d[k - 1]) + Math.abs(d[k]);
        return a == a + Math.abs(e[k]);
    }

    /**
     * Controls how many rows of the eigenvector matrix are computed.
     *
     * <p>Matches {@code QuantLib::TqrEigenDecomposition::EigenVectorCalculation}.
     */
    public enum EigenVectorCalculation {
        WithEigenVector, WithoutEigenVector, OnlyFirstRowEigenVector
    }

    /**
     * Shift strategy for the implicit QR iteration.
     *
     * <p>Matches {@code QuantLib::TqrEigenDecomposition::ShiftStrategy}.
     */
    public enum ShiftStrategy {
        NoShift, Overrelaxation, CloseEigenValue
    }
}

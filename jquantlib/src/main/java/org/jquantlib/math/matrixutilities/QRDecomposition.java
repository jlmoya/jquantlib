/*
Copyright (C) 2008 Richard Gomes

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
 Copyright (C) 2008 Klaus Spanderen

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
import org.jquantlib.math.optimization.Minpack;

import java.util.Arrays;

/**
 * QR decompoisition
 * <p>
 * This implementation is based on <a href="http://www1.fpl.fs.fed.us/optimization.html">MINPACK/J</a>
 * <p>
 * This subroutine uses householder transformations with <i>optional</i> column pivoting to compute a QR factorization
 * of the {@latex$ m} by {@latex$ n} matrix {@latex$ A}.
 * <p>
 * That is, Minpack_f77#qrfac determines an orthogonal matrix {@latex$ Q}, a permutation matrix {@latex$ P}, and an
 * upper trapezoidal matrix {@latex$ R} with diagonal elements of nonincreasing magnitude, such that
 * {@latex$ A*P = Q*R}.
 * <p>
 * Return value <code>ipvt</code> is an integer array of length {@latex$ n}, which defines the permutation matrix
 * {@latex$ P} such that {@latex$ A*P = Q*R}.
 * <p>
 * Column {@latex$ j} of {@latex$ P} is column <code>ipvt[j]</code> of the identity matrix.
 * <p>
 * See lmdiff.cpp for further details.
 *
 * @author Richard Gomes
 * @see <a href="http://mathworld.wolfram.com/QRDecomposition.html">MathWorld</a>
 * @see <a href="http://en.wikipedia.org/wiki/QR_decomposition">Wikipedia</a>
 * @see <a href="http://www1.fpl.fs.fed.us/optimization.html">MINPACK/J</a>
 * @see <a href="http://www.netlib.org/minpack">MINPACK</a>
 */
public class QRDecomposition {

    private final int m;
    private final int n;
    private final Matrix A;
    private final Matrix Q;
    private final Matrix R;
    private final Matrix P;
    private final int[] ipvt;
    private final boolean isNonSingular;

    /**
     * Calculates the QR-decomposition of the given matrix.
     *
     * @param A The matrix to decompose.
     */
    public QRDecomposition(final Matrix matrix) {
        this(matrix, false);
    }

    /**
     * Calculates the QR-decomposition of the given matrix.
     *
     * @param M The matrix to decompose.
     */
    public QRDecomposition(final Matrix A, final boolean pivot) {
        this.A = A;
        this.m = A.rows();
        this.n = A.cols();
        this.ipvt = new int[n];

        final Matrix mT = A.clone().toJava().transpose();
        final Array rdiag = new Array(n);
        final Array wa = new Array(n);

        System.out.println("mT (BEFORE) = " + mT);
        Minpack.qrfac(m, n, mT,                      // input/output parameter (sorry for that :~ )
                pivot, ipvt, rdiag, rdiag, wa  // output parameters (sorry for that :~ )
        );

        System.out.println("mT (AFTER)  = " + mT);
        System.out.println("Array ipvt = " + Arrays.toString(ipvt));
        System.out.println("Array rdiag = " + rdiag);
        System.out.println("Array wa = " + wa);

        // obtain R
        final double[][] r = new double[n][n];
        for ( int i = 0; i < n; i++ ) {
            // r[i][i] = rdiag[i];
            r[i][i] = rdiag.get(i);
            if ( i < m ) {
                // std::copy(mT.column_begin(i)+i+1, mT.column_end(i), r.row_begin(i)+i+1);
                for ( int k = i + 1; k < n; k++ ) {
                    // r[i][k] = mT[k][i];
                    r[i][k] = mT.get(k, i);
                }
            }
        }

        // obtain Q
        final double[][] q = new double[m][n];
        final double[] w = new double[m];
        for ( int k = 0; k < m; k++ ) {
            Arrays.fill(w, 0.0);
            w[k] = 1.0;
            for ( int j = 0; j < Math.min(n, m); j++ ) {
                final double t3 = mT.get(j, j);
                if ( t3 != 0.0 ) {
                    // final double t = std::inner_product(mT.row_begin(j)+j, mT.row_end(j), w.begin()+j, 0.0)/t3;
                    double t = 0.0;
                    for ( int p = j; p < m; p++ ) {
                        t += mT.get(j, p) * w[p];
                    }
                    t /= t3;

                    for ( int i = j; i < m; i++ ) {
                        w[i] -= mT.get(j, i) * t;
                    }
                }
                q[k][j] = w[j];
            }
        }

        // reverse column pivoting
        final double[][] p = new double[n][n];
        if ( pivot ) {
            for ( int i = 0; i < n; ++i ) {
                p[ipvt[i]][i] = 1.0;
            }
        } else {
            for ( int i = 0; i < n; ++i ) {
                p[i][i] = 1.0;
            }
        }

        this.isNonSingular = isNonSingular(rdiag.$);

        final boolean fortran = this.A.addr.isFortran();
        this.R = fortran ? new Matrix(r).toFortran() : new Matrix(r);
        this.Q = fortran ? new Matrix(q).toFortran() : new Matrix(q);
        this.P = fortran ? new Matrix(p).toFortran() : new Matrix(p);

        System.out.println("Matrix Q = " + Q.toString());
        System.out.println("Matrix R = " + R.toString());
        System.out.println("Matrix P = " + P.toString());
        System.out.println("Matrix mT = " + mT);
    }

    /**
     * Free-function port of QuantLib v1.42.1 {@code qrSolve} from
     * {@code ql/math/matrixutilities/qrdecomposition.cpp:125-159}. Solves {@code A*x = b} with optional column pivoting
     * and LM damping.
     */
    public static Array qrSolve(final Matrix A, final Array b, final boolean pivot, final Array d) {
        final int m = A.rows();
        final int n = A.cols();
        QL.require(b.size() == m, "dimensions of A and b don't match");
        QL.require(d == null || d.empty() || d.size() == n, "dimensions of A and d don't match");

        final Matrix mT = A.clone().toJava().transpose();
        final int[] lipvt = new int[n];
        final Array rdiag = new Array(n);
        final Array wa = new Array(n);

        Minpack.qrfac(m, n, mT, pivot, lipvt, rdiag, rdiag, wa);

        // build R
        final double[][] rArr = new double[n][n];
        for ( int i = 0; i < n; i++ ) {
            rArr[i][i] = rdiag.get(i);
            if ( i < m ) {
                for ( int k = i + 1; k < n; k++ ) {
                    rArr[i][k] = mT.get(k, i);
                }
            }
        }

        // build Q (mirrors C++ qrdecomposition.cpp:62-110)
        final double[][] qArr = new double[m][n];
        if ( m > n ) {
            final int u = Math.min(n, m);
            for ( int i = 0; i < u; ++i )
                qArr[i][i] = 1.0;
            final double[] v = new double[m];
            for ( int i = u - 1; i >= 0; --i ) {
                if ( Math.abs(mT.get(i, i)) > org.jquantlib.math.Constants.QL_EPSILON ) {
                    final double tau = 1.0 / mT.get(i, i);
                    for ( int k = 0; k < i; ++k )
                        v[k] = 0.0;
                    for ( int k = i; k < m; ++k )
                        v[k] = mT.get(i, k);

                    final double[] w = new double[n];
                    for ( int l = 0; l < n; ++l ) {
                        double s = 0.0;
                        for ( int k = i; k < m; ++k )
                            s += v[k] * qArr[k][l];
                        w[l] = s;
                    }
                    for ( int k = i; k < m; ++k ) {
                        final double a = tau * v[k];
                        for ( int l = 0; l < n; ++l )
                            qArr[k][l] -= a * w[l];
                    }
                }
            }
        } else {
            final double[] w = new double[m];
            for ( int k = 0; k < m; ++k ) {
                for ( int i = 0; i < m; ++i )
                    w[i] = 0.0;
                w[k] = 1.0;
                for ( int j = 0; j < Math.min(n, m); ++j ) {
                    final double t3 = mT.get(j, j);
                    if ( t3 != 0.0 ) {
                        double t = 0.0;
                        for ( int i = j; i < m; ++i )
                            t += mT.get(j, i) * w[i];
                        t /= t3;
                        for ( int i = j; i < m; ++i )
                            w[i] -= mT.get(j, i) * t;
                    }
                    qArr[k][j] = w[j];
                }
            }
        }

        final Matrix Q = new Matrix(qArr);
        final Matrix R = new Matrix(rArr);

        final int[] ipvt = new int[n];
        if ( pivot ) {
            System.arraycopy(lipvt, 0, ipvt, 0, n);
        } else {
            for ( int i = 0; i < n; ++i )
                ipvt[i] = i;
        }

        // qrSolve: rT = transpose(R); qtb = Q^T * b; call qrsolv.
        final Matrix rT = R.transpose();
        final double[] sdiag = new double[n];
        final double[] waArr = new double[n];
        final double[] ld = new double[n];
        if ( d != null && !d.empty() ) {
            for ( int i = 0; i < n; ++i )
                ld[i] = d.get(i);
        }
        final Array qtb = Q.transpose().mul(b);
        final double[] qtbArr = new double[n];
        for ( int i = 0; i < n; ++i )
            qtbArr[i] = qtb.get(i);

        final double[] xArr = new double[n];

        // Build R in column-major layout for Minpack.qrsolv. The Minpack
        // convention is r[i + ldr*j] = r(i, j); C++ passes rT.begin()
        // (row-major rT) which Minpack reinterprets as column-major R via
        // the i,j swap. We construct rFlat in column-major directly from R.
        final double[] rFlat = new double[n * n];
        for ( int j = 0; j < n; ++j ) {
            for ( int i = 0; i < n; ++i ) {
                rFlat[i + n * j] = R.get(i, j);
            }
        }

        Minpack.qrsolv(n, rFlat, n, ipvt, ld, qtbArr, xArr, sdiag, waArr);

        return new Array(xArr);
    }

    public Matrix Q() {
        return Q;
    }

    public Matrix R() {
        return R;
    }

    public Matrix P() {
        return P;
    }

    public boolean isNonSingular() {
        return isNonSingular;
    }

    //
    // public static factories (Phase 5e.5b-CFC-d-52)
    //

    public Array solve(final Array b, final boolean pivot, final Array d) {
        return qrSolve(A, b, pivot, d);
    }

    //
    // private methods
    //

    private boolean isNonSingular(final double[] rdiag) {
        for ( final double diag : rdiag ) {
            if ( diag == 0 )
                return false;
        }
        return true;
    }

}

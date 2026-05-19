/*
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2026 JQuantLib migration contributors

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*! \file sparseilupreconditioner.hpp
    \brief Preconditioner using the Incomplete LU algorithm and sparse matrices.
    Ported from QuantLib v1.42.1 — Phase 5b.5.

    Reference:
    Saad, Yousef. 1996, Iterative methods for sparse linear systems,
    http://www-users.cs.umn.edu/~saad/books.html
*/

package org.jquantlib.math.matrixutilities;

import java.util.TreeSet;

/**
 * Incomplete LU preconditioner with level-of-fill control (ILU(p)) for {@link SparseMatrix}.
 *
 * <p>Direct port of the algorithm in
 * {@code ql/math/matrixutilities/sparseilupreconditioner.cpp} from QuantLib v1.42.1 (originally by Ralph Schreyer,
 * 2009).
 *
 * <p>{@link #apply(Array)} computes {@code (LU)^{-1} b} via forward + backward
 * substitution against the precomputed factors {@code L} (unit lower triangular) and {@code U} (upper triangular).
 * Used as a left preconditioner for {@link BiCGStab} and {@link GMRES}.
 *
 * <p>Phase 5b.5 — JQuantLib migration.
 */
public class SparseILUPreconditioner {

    /** Java equivalent of {@code QL_EPSILON} (machine epsilon for double). */
    private static final double QL_EPSILON = Math.ulp(1.0);

    private final SparseMatrix L_;
    private final SparseMatrix U_;
    private final int[] lBands_;
    private final int[] uBands_;

    /**
     * Build the ILU(lfil) preconditioner for {@code A}.
     *
     * @param A    square sparse matrix
     * @param lfil level of fill ({@code >= 0}); higher retains more of the true LU at the cost of preconditioner
     *             density
     */
    public SparseILUPreconditioner(final SparseMatrix A, final int lfil) {
        if ( A.rows() != A.columns() ) {
            throw new IllegalArgumentException(
                    "SparseILUPreconditioner: requires square matrix (" + A.rows() + "x" + A.columns() + ")");
        }

        final int n = A.rows();
        L_ = new SparseMatrix(n, n);
        U_ = new SparseMatrix(n, n);

        for ( int i = 0; i < n; i++ ) {
            L_.set(i, i, 1.0);
        }

        final TreeSet< Integer > lBandSet = new TreeSet<>();
        final TreeSet< Integer > uBandSet = new TreeSet<>();

        // levs is a sparse matrix of level-of-fill integers (mirrors the C++
        // boost::numeric::ublas::compressed_matrix<Integer> levs(n,n)).
        final SparseIntMatrix levs = new SparseIntMatrix(n, n);

        final int lfilp = lfil + 1;

        for ( int ii = 0; ii < n; ii++ ) {
            final double[] w = new double[n];
            for ( int k = 0; k < n; k++ ) {
                w[k] = A.get(ii, k);
            }

            final int[] levii = new int[n];
            for ( int i = 0; i < n; i++ ) {
                if ( w[i] > QL_EPSILON || w[i] < -QL_EPSILON ) {
                    levii[i] = 1;
                }
            }

            int jj = -1;
            while ( jj < ii ) {
                // Mirror C++: scan k=jj+1..n for next non-zero levii[k]; if
                // found, jj=k.  If no such k exists in range, jj stays at its
                // old value but we break out via the `jj >= ii` check after
                // the search.  In practice the diagonal of A is non-zero so
                // levii[ii] is set and the search always finds at least
                // index ii.
                boolean found = false;
                for ( int k = jj + 1; k < n; k++ ) {
                    if ( levii[k] != 0 ) {
                        jj = k;
                        found = true;
                        break;
                    }
                }
                if ( !found || jj >= ii ) {
                    break;
                }

                final int jlev = levii[jj];
                if ( jlev <= lfilp ) {
                    // Collect non-zero entries on row jj of U (including
                    // diagonal U(jj,jj)).
                    final int[] nonZeros = new int[uBandSet.size() + 1];
                    final double[] nonZeroEntries = new double[uBandSet.size() + 1];
                    int nzCount = 0;

                    final double diag = U_.get(jj, jj);
                    if ( diag > QL_EPSILON || diag < -QL_EPSILON ) {
                        nonZeros[nzCount] = jj;
                        nonZeroEntries[nzCount] = diag;
                        nzCount++;
                    }
                    for ( final Integer band : uBandSet ) {
                        final int colIdx = jj + band;
                        if ( colIdx >= n )
                            continue;
                        final double entry = U_.get(jj, colIdx);
                        if ( entry > QL_EPSILON || entry < -QL_EPSILON ) {
                            nonZeros[nzCount] = colIdx;
                            nonZeroEntries[nzCount] = entry;
                            nzCount++;
                        }
                    }

                    double fact = w[jj];
                    if ( nzCount > 0 ) {
                        fact /= nonZeroEntries[0];
                    }
                    for ( int k = 0; k < nzCount; k++ ) {
                        final int j = nonZeros[k];
                        final int temp = levs.get(jj, j) + jlev;
                        if ( levii[j] == 0 ) {
                            if ( temp <= lfilp ) {
                                w[j] = -fact * nonZeroEntries[k];
                                levii[j] = temp;
                            }
                        } else {
                            w[j] -= fact * nonZeroEntries[k];
                            levii[j] = Math.min(levii[j], temp);
                        }
                    }
                    w[jj] = fact;
                }
            }

            // Collect non-zero entries of w (with their indices) and the
            // corresponding non-zero levii entries (preserving C++ order).
            final int[] wNonZeros = new int[n];
            final double[] wNonZeroEntries = new double[n];
            int wnzCount = 0;
            for ( int i = 0; i < n; i++ ) {
                final double entry = w[i];
                if ( entry > QL_EPSILON || entry < -QL_EPSILON ) {
                    wNonZeros[wnzCount] = i;
                    wNonZeroEntries[wnzCount] = entry;
                    wnzCount++;
                }
            }

            // C++:
            //   for (int entry : levii) if non-zero append to leviiNonZeroEntries
            // (note the C++ code compares against QL_EPSILON, not just != 0;
            //  for an int that's effectively != 0 since |1| > eps).
            final int[] leviiNonZero = new int[n];
            int lvnzCount = 0;
            for ( int i = 0; i < n; i++ ) {
                final int entry = levii[i];
                if ( entry > QL_EPSILON || entry < -QL_EPSILON ) {
                    leviiNonZero[lvnzCount++] = entry;
                }
            }

            for ( int k = 0; k < wnzCount; k++ ) {
                final int j = wNonZeros[k];
                if ( j < ii ) {
                    L_.set(ii, j, wNonZeroEntries[k]);
                    lBandSet.add(ii - j);
                } else {
                    U_.set(ii, j, wNonZeroEntries[k]);
                    if ( k < lvnzCount ) {
                        levs.set(ii, j, leviiNonZero[k]);
                    }
                    if ( j - ii > 0 ) {
                        uBandSet.add(j - ii);
                    }
                }
            }
        }

        // Materialize bands as int[] in ascending order (TreeSet iteration order).
        lBands_ = new int[lBandSet.size()];
        uBands_ = new int[uBandSet.size()];
        int idx = 0;
        for ( final Integer band : lBandSet )
            lBands_[idx++] = band;
        idx = 0;
        for ( final Integer band : uBandSet )
            uBands_[idx++] = band;
    }

    /**
     * Convenience constructor with default {@code lfil = 1}.
     *
     * @param A square sparse matrix
     */
    public SparseILUPreconditioner(final SparseMatrix A) {
        this(A, 1);
    }

    /** @return reference to the unit lower-triangular factor L */
    public SparseMatrix L() {
        return L_;
    }

    /** @return reference to the upper-triangular factor U */
    public SparseMatrix U() {
        return U_;
    }

    /**
     * Apply the preconditioner: {@code (LU)^{-1} b}, computed as {@code U \ (L \ b)}.
     *
     * @param b right-hand side
     * @return preconditioned vector
     */
    public Array apply(final Array b) {
        return backwardSolve(forwardSolve(b));
    }

    // -----------------------------------------------------------------------

    private Array forwardSolve(final Array b) {
        final int n = b.size();
        final double[] y = new double[n];
        y[0] = b.get(0) / L_.get(0, 0);
        for ( int i = 1; i <= n - 1; i++ ) {
            y[i] = b.get(i) / L_.get(i, i);
            for ( int j = lBands_.length - 1; j >= 0 && i - lBands_[j] <= i - 1; j-- ) {
                final int k = i - lBands_[j];
                if ( k >= 0 ) {
                    y[i] -= L_.get(i, k) * y[k] / L_.get(i, i);
                }
            }
        }
        return new Array(y, y.length);
    }

    private Array backwardSolve(final Array y) {
        final int n = y.size();
        final double[] x = new double[n];
        x[n - 1] = y.get(n - 1) / U_.get(n - 1, n - 1);
        for ( int i = n - 2; i >= 0; i-- ) {
            x[i] = y.get(i) / U_.get(i, i);
            for ( int j = 0; j < uBands_.length && i + uBands_[j] <= n - 1; j++ ) {
                x[i] -= U_.get(i, i + uBands_[j]) * x[i + uBands_[j]] / U_.get(i, i);
            }
        }
        return new Array(x, x.length);
    }

    // -----------------------------------------------------------------------
    // Helper class: a sparse int[i,j] matrix (used only internally for level
    // tracking in the ILU build).  Kept package-private inner class to avoid
    // polluting the public API.

    private static final class SparseIntMatrix {
        private final int rows;
        private final int cols;
        // Simple dictionary keyed by (i*cols + j).  Acceptable since only used
        // inside the ILU build for relatively small matrices in practice.
        private final java.util.HashMap< Long, Integer > data = new java.util.HashMap<>();

        SparseIntMatrix(final int rows, final int cols) {
            this.rows = rows;
            this.cols = cols;
        }

        int get(final int i, final int j) {
            final Integer v = data.get(((long) i) * cols + j);
            return v == null ? 0 : v;
        }

        void set(final int i, final int j, final int v) {
            data.put(((long) i) * cols + j, v);
        }
    }
}

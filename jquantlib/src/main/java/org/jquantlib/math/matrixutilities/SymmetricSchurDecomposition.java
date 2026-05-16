/*
 Copyright (C) 2009 Richard Gomes

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

/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl
 Copyright (C) 2004 Ferdinando Ametrano

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
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;

/**
 * Symmetric threshold Jacobi algorithm
 * <p>
 * Given a real symmetric matrix S, the Schur decomposition finds the eigenvalues and eigenvectors of S. If D is the diagonal matrix
 * formed by the eigenvalues and U the unitarian matrix of the eigenvectors we can write the Schur decomposition as {@latex[ S = U
 * \cdot D \cdot U^T } where {@latex$ \cdot } is the standard matrix product and {@latex$ ^T } is the transpose operator.
 * <p>
 * This class implements the Schur decomposition using the symmetric threshold Jacobi algorithm. For details on the different Jacobi
 * transfomations.
 *
 * @see "Matrix computation," second edition, by Golub and Van Loan, The Johns Hopkins University Press
 *
 * @author Richard Gomes
 */
@QualityAssurance(quality = Quality.Q0_UNFINISHED, version = Version.V097, reviewers = { "Richard Gomes" })
public class SymmetricSchurDecomposition {

    private static final double epsPrec = 1e-15;
    private static final int maxIterations = 100;

    private final int size;
    private final Matrix A;
    private final Array diag;


    public SymmetricSchurDecomposition(final Matrix m) {
        QL.require(m.rows() == m.cols(), Matrix.MATRIX_MUST_BE_SQUARE); // QA:[RG]::verified

        this.size = m.rows();
        this.A = new Matrix(m.rows(), m.cols(), m.flags());
        this.diag = new Array(size, m.flags());

        final double tmpDiag[] = new double[size];
        final double tmpSum[] = new double[size];

        final Matrix s = m.clone();
        final int offset = s.offset();

        for (int q = offset; q < size+offset; q++) {
            diag.$[diag.addr.op(q)] = s.$[s.addr.op(q, q)];
            A.$[A.addr.op(q, q)] = 1.0;
        }
        for (int j = 0; j < size; j++) {
            tmpDiag[j] = diag.$[diag.addr.op(j+offset)];
        }

        boolean keeplooping = true;
        int ite = 1;
        double threshold;
        do {
            // main loop
            double sum = 0;
            for (int a = offset; a < size - 1 + offset; a++) {
                for (int b = a + 1; b < size + offset; b++) {
                    sum += Math.abs(s.$[s.addr.op(a, b)]);
                }
            }

            if (sum == 0) {
                keeplooping = false;
            } else {
                /*
                 * To speed up computation a threshold is introduced to make sure it is worthy to perform the Jacobi rotation
                 */
                if (ite < 5) {
                    threshold = 0.2 * sum / (size * size);
                } else {
                    threshold = 0.0;
                }

                int j, k, l;
                for (j = offset; j < size - 1 + offset; j++) {
                    for (k = j + 1; k < size + offset; k++) {
                        double sine, rho, cosin, heig, tang, beta;
                        final double smll = Math.abs(s.$[s.addr.op(j, k)]);
                        if (ite > 5 && smll < epsPrec * Math.abs(diag.$[diag.addr.op(j)])
                                && smll < epsPrec * Math.abs(diag.$[diag.addr.op(k)])) {
                            s.$[s.addr.op(j, k)] = 0;
                        } else if (Math.abs(s.$[s.addr.op(j, k)]) > threshold) {
                            heig = diag.$[diag.addr.op(k)] - diag.$[diag.addr.op(j)];
                            if (smll < epsPrec * Math.abs(heig)) {
                                tang = s.$[s.addr.op(j, k)] / heig;
                            } else {
                                beta = 0.5 * heig / s.$[s.addr.op(j, k)];
                                tang = 1.0 / (Math.abs(beta) + Math.sqrt(1 + beta * beta));
                                if (beta < 0) {
                                    tang = -tang;
                                }
                            }
                            cosin = 1 / Math.sqrt(1 + tang * tang);
                            sine = tang * cosin;
                            rho = sine / (1 + cosin);
                            heig = tang * s.$[s.addr.op(j, k)];
                            tmpSum[j-offset] -= heig;
                            tmpSum[k-offset] += heig;
                            diag.$[diag.addr.op(j)] -= heig;
                            diag.$[diag.addr.op(k)] += heig;
                            s.$[s.addr.op(j, k)] = 0.0;
                            for (l = offset; l + 1 <= j; l++) {
                                jacobiRotate(s, rho, sine, l, j, l, k);
                            }
                            for (l = j + 1; l <= k - 1; l++) {
                                jacobiRotate(s, rho, sine, j, l, l, k);
                            }
                            for (l = k + 1; l < size + offset; l++) {
                                jacobiRotate(s, rho, sine, j, l, k, l);
                            }
                            for (l = offset; l < size + offset; l++) {
                                jacobiRotate(A, rho, sine, l, j, l, k);
                            }
                        }
                    }
                }
                for (k = 0; k < size; k++) {
                    tmpDiag[k] += tmpSum[k];
                    diag.$[diag.addr.op(k+offset)] = tmpDiag[k];
                    tmpSum[k] = 0.0;
                }
            }
        } while (++ite <= maxIterations && keeplooping);

        QL.ensure(ite <= maxIterations, "Too many iterations reached");

        // Phase 5e.5b-CFC-d-27 align: C++ symmetricschurdecomposition.cpp:115-138
        // performs an (eigenvalue, eigenvector) sort in descending eigenvalue order,
        // then normalizes each eigenvector's sign so the first row entry is non-negative.
        // Without this step, Java's PseudoSqrt(Spectral) returns columns in a different
        // order/sign than C++ pseudoSqrt(corr, SalvagingAlgorithm::Spectral), which
        // decorrelates the dz mapping in MultiPathGenerator and breaks the
        // Himalaya/Everest/Pagoda cached MC tests (and any other test that compares
        // against C++ reference values produced from PseudoSqrt(Spectral)).
        //
        // Reference: migration-harness/cpp/probes/math/matrixutilities/pseudosqrt_spectral_probe.cpp
        // + migration-harness/references/math/matrixutilities/pseudosqrt_spectral.json
        sortAndSignNormalize();
    }

    //
    // public methods
    //

    public Matrix eigenvectors() {
        return A.clone();
    }

    public Array eigenvalues() {
        return diag.clone();
    }

    //
    // private methods
    //

    /**
     * This routines implements the Jacobi, a.k.a. Givens, rotation
     */
    private void jacobiRotate(
            final Matrix m,
            final double rot,
            final double dil,
            final int j1,
            final int k1,
            final int j2,
            final int k2) /* @ReadOnly */{
        double x1, x2;
        x1 = m.$[m.addr.op(j1, k1)];
        x2 = m.$[m.addr.op(j2, k2)];
        m.$[m.addr.op(j1, k1)] = x1 - dil * (x2 + x1 * rot);
        m.$[m.addr.op(j2, k2)] = x2 + dil * (x1 - x2 * rot);
    }

    /**
     * Sort (eigenvalues, eigenvectors) so eigenvalues are in descending order,
     * then sign-normalize each eigenvector so its first row entry is non-negative.
     * Mirrors C++ {@code symmetricschurdecomposition.cpp:115-138}.
     *
     * <p>Also rounds any eigenvalue whose magnitude relative to the largest
     * eigenvalue is below 1e-16 down to zero, matching the C++ round-off guard.
     */
    private void sortAndSignNormalize() {
        // Offset-aware indexing: Array/Matrix coordinates are 1-based for fortran flags,
        // 0-based otherwise. Use the same offset as the rest of this class.
        final int aOffset = diag.begin();   // 0 or 1
        final int mOffset = A.offset();     // 0 or 1

        // Snapshot current (eigenvalue, eigenvector-column) pairs in plain 0-based arrays.
        final double[] values = new double[size];
        final double[][] vectors = new double[size][size]; // vectors[col][row]
        for (int col = 0; col < size; ++col) {
            values[col] = diag.get(col + aOffset);
            for (int row = 0; row < size; ++row) {
                vectors[col][row] = A.get(row + mOffset, col + mOffset);
            }
        }

        // Index-based sort, descending by eigenvalue. std::sort with std::greater<>
        // on std::pair compares the .first first; ties are broken by the vectors'
        // lexicographic order — practically not observed for non-degenerate inputs.
        final Integer[] idx = new Integer[size];
        for (int i = 0; i < size; ++i) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> {
            final int c = Double.compare(values[b], values[a]); // descending
            if (c != 0) return c;
            // Lexicographic tie-break on the eigenvector entries, matching
            // std::pair<Real, std::vector<Real>> with std::greater<>.
            for (int r = 0; r < size; ++r) {
                final int cc = Double.compare(vectors[b][r], vectors[a][r]);
                if (cc != 0) return cc;
            }
            return 0;
        });

        final double maxEv = values[idx[0]];
        for (int col = 0; col < size; ++col) {
            final int src = idx[col];
            // Round-off guard: zero out eigenvalues whose magnitude relative to
            // the largest is below machine precision (matches C++ 1e-16 threshold).
            final double ev = values[src];
            final double newEv = (maxEv != 0.0 && Math.abs(ev / maxEv) < 1e-16) ? 0.0 : ev;
            diag.set(col + aOffset, newEv);

            final double sign = (vectors[src][0] < 0.0) ? -1.0 : 1.0;
            for (int row = 0; row < size; ++row) {
                A.set(row + mOffset, col + mOffset, sign * vectors[src][row]);
            }
        }
    }

}

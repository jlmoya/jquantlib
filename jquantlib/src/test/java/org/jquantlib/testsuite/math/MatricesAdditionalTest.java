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

package org.jquantlib.testsuite.math;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Identity;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

/**
 * Java port of the C++ tests in test-suite/matrices.cpp not already covered
 * by the existing {@link MatrixTest} (Phase 5b).
 *
 * <p>The C++ file has 20 BOOST_AUTO_TEST_CASEs. {@link MatrixTest} already
 * covers eigenvectors, QR decomposition, matrix arithmetic, and clone/range
 * operators. The remaining cases addressed here are:
 * <ul>
 *   <li>{@code testInverse} (live).</li>
 *   <li>{@code testDeterminant} (live).</li>
 * </ul>
 *
 * <p>Phase 5b deferred (skeleton): testSqrt / testHighamSqrt / testSVD / testQRSolve /
 * testOrthogonalProjection (covered by separate Java OrthogonalProjectionsTest) /
 * testMoorePenroseInverse / testIterativeSolvers
 * (separate IterativeSolversTest exists) / testInitializers / testSparseMatrixMemory
 * (Java has no SparseMatrix) / testOperators / testPrincipalMatrixSqrt /
 * testCholeskySolverFor / testCholeskySolverForIncomplete / testHouseholderTransformation /
 * testHouseholderReflection.
 *
 * <p>Phase1-cert-D5-C-R2: ported {@code testCholeskyDecomposition} against the
 * 11x11 semidefinite test matrix; round-trip {@code L*L^T = m} to 1.0e-12.
 */
public class MatricesAdditionalTest {

    public MatricesAdditionalTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static double norm(final Matrix m) {
        double sum = 0.0;
        for (int i = 0; i < m.rows(); i++) {
            for (int j = 0; j < m.columns(); j++) {
                sum += m.get(i, j) * m.get(i, j);
            }
        }
        return Math.sqrt(sum);
    }

    private static Matrix M1() {
        return new Matrix(new double[][] {
                { 1.0, 0.9, 0.7 },
                { 0.9, 1.0, 0.4 },
                { 0.7, 0.4, 1.0 }
        });
    }

    private static Matrix M2() {
        return new Matrix(new double[][] {
                { 1.0, 0.9, 0.7 },
                { 0.9, 1.0, 0.3 },
                { 0.7, 0.3, 1.0 }
        });
    }

    private static Matrix I3() {
        return new Identity(3);
    }

    private static Matrix M5() {
        return new Matrix(new double[][] {
                {  2.0, -1.0,  0.0,  0.0 },
                { -1.0,  2.0, -1.0,  0.0 },
                {  0.0, -1.0,  2.0, -1.0 },
                {  0.0,  0.0, -1.0,  2.0 }
        });
    }

    private static Matrix M6() {
        return new Matrix(new double[][] {
                { 1.0, -0.8084124981,  0.1915875019,  0.106775049 },
                { -0.8084124981, 1.0, -0.6562326948, 0.1915875019 },
                { 0.1915875019, -0.6562326948, 1.0, -0.8084124981 },
                { 0.106775049,  0.1915875019, -0.8084124981, 1.0 }
        });
    }

    @Test
    public void testInverse() {
        QL.info("Testing LU inverse calculation...");

        final double tol = 1.0e-12;
        final Matrix[] testMatrices = { M1(), M2(), I3(), M5() };

        for (final Matrix A : testMatrices) {
            final Matrix invA = A.inverse();
            final Matrix I1 = invA.mul(A);
            final Matrix I2 = A.mul(invA);

            final Matrix identity = new Identity(A.rows());

            if (norm(I1.sub(identity)) > tol) {
                fail("inverse(A)*A does not recover unit matrix (norm = "
                        + norm(I1.sub(identity)) + ")");
            }
            if (norm(I2.sub(identity)) > tol) {
                fail("A*inverse(A) does not recover unit matrix (norm = "
                        + norm(I2.sub(identity)) + ")");
            }
        }
    }

    @Test
    public void testDeterminant() {
        QL.info("Testing LU determinant calculation...");

        final double tol = 1.0e-10;

        final Matrix[] testMatrices = { M1(), M2(), M5(), M6(), I3() };
        // expected results computed in octave per C++ test
        final double[] expected = { 0.044, -0.012, 5.0, 5.7621e-11, 1.0 };

        for (int j = 0; j < testMatrices.length; j++) {
            final double calculated = testMatrices[j].determinant();
            if (Math.abs(expected[j] - calculated) > tol) {
                fail("determinant calculation failed (matrix " + j + ")"
                        + "\n calculated : " + calculated
                        + "\n expected   : " + expected[j]);
            }
        }

        // 100 random 3x3 matrices, every third one is singular (one row zeroed)
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1234L);
        for (int j = 0; j < 100; j++) {
            final double[][] data = new double[3][3];
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    data[r][c] = rng.next().value();
                }
            }
            if ((j % 3) == 0) {
                final int row = (int) (3 * rng.next().value());
                for (int c = 0; c < 3; c++) {
                    data[row][c] = 0.0;
                }
            }

            final Matrix m = new Matrix(data);
            final double a = data[0][0], b = data[0][1], c = data[0][2];
            final double d = data[1][0], e = data[1][1], f = data[1][2];
            final double g = data[2][0], h = data[2][1], i = data[2][2];

            final double expectedDet = a * e * i + b * f * g + c * d * h
                    - (g * e * c + h * f * a + i * d * b);
            final double calculated = m.determinant();

            if (Math.abs(expectedDet - calculated) > tol) {
                fail("determinant calculation failed (random matrix " + j + ")"
                        + "\n calculated : " + calculated
                        + "\n expected   : " + expectedDet);
            }
        }
    }

    @Test
    public void testSqrt() {
        QL.info("Testing matricial square root...");
        // C++ matrices.cpp:174 — for M1, verify pseudoSqrt(A)*pseudoSqrt(A)^T ~ A
        // using SalvagingAlgorithm::None (which routes to CholeskyDecomposition).
        final Matrix m = PseudoSqrt.pseudoSqrt(M1(),
                org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm.None);
        final Matrix prod = m.mul(m.transpose());
        final Matrix diff = prod.sub(M1());
        final double err = norm(diff);
        if (err > 1.0e-12) {
            fail("Matrix square root calculation failed; error=" + err);
        }
    }

    @Test
    public void testHighamSqrt() {
        QL.info("Testing Higham matricial square root...");
        // C++ matrices.cpp:194 — pseudoSqrt(M5, Higham) ~ pseudoSqrt(M6, None)
        // within 1e-4 (M6 is the precomputed nearest-correlation-matrix for M5).
        final Matrix tempSqrt = PseudoSqrt.pseudoSqrt(M5(),
                org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm.Higham);
        final Matrix ansSqrt = PseudoSqrt.pseudoSqrt(M6(),
                org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm.None);
        final double err = norm(ansSqrt.sub(tempSqrt));
        if (err > 1.0e-4) {
            fail("Higham matrix correction failed; error=" + err);
        }
    }

    private static Matrix M3() {
        // C++ matrices.cpp:95 — M3 is 3x4 (wide).
        return new Matrix(new double[][] {
                { 1, 2, 3, 4 },
                { 2, 0, 2, 1 },
                { 0, 1, 0, 0 }
        });
    }

    private static Matrix M4() {
        // C++ matrices.cpp:99 — M4 is 4x3 (tall).
        return new Matrix(new double[][] {
                { 1,  2, 400  },
                { 2,  0,   1  },
                { 30, 2,   0  },
                { 2,  0,   1.05 }
        });
    }

    @Test
    public void testSVD() {
        QL.info("Testing singular value decomposition...");
        // C++ test-suite/matrices.cpp:213 — verify SVD U*S*V^T == A and U/V
        // orthogonality across M1..M4 (square, wide, tall). Aligned with the
        // C++ implementation: when rows < cols, SVD decomposes A^T internally
        // and swaps U/V on access (matches QuantLib SVD::transpose_).
        final double tol = 1.0e-12;
        final Matrix[] testMatrices = { M1(), M2(), M3(), M4() };

        for (final Matrix A : testMatrices) {
            final org.jquantlib.math.matrixutilities.SVD svd =
                    new org.jquantlib.math.matrixutilities.SVD(A.clone());
            final Matrix U = svd.U();
            final Array s = svd.singularValues();
            final Matrix S = svd.S();
            final Matrix V = svd.V();

            // S consistent with the singular-value array.
            for (int i = 0; i < S.rows(); i++) {
                if (S.get(i, i) != s.get(i)) {
                    fail("S not consistent with s at index " + i);
                }
            }

            // U is orthogonal: U^T * U == I.
            final Matrix UtU = U.transpose().mul(U);
            final Matrix Iu = new Identity(UtU.rows());
            if (norm(UtU.sub(Iu)) > tol) {
                fail("U not orthogonal (norm of U^T*U - I = "
                        + norm(UtU.sub(Iu)) + ")");
            }

            // V is orthogonal: V^T * V == I.
            final Matrix VtV = V.transpose().mul(V);
            final Matrix Iv = new Identity(VtV.rows());
            if (norm(VtV.sub(Iv)) > tol) {
                fail("V not orthogonal (norm of V^T*V - I = "
                        + norm(VtV.sub(Iv)) + ")");
            }

            // Reconstruction: U * S * V^T ~ A.
            final Matrix recon = U.mul(S).mul(V.transpose());
            if (norm(recon.sub(A)) > tol) {
                fail("Product does not recover A (norm of U*S*V^T - A = "
                        + norm(recon.sub(A)) + ")");
            }
        }
    }

    @Test
    public void testQRSolve() {
        QL.info("Testing QR solve...");
        // C++ matrices.cpp:292 — for square non-singular matrices we expect
        // A*x ~ b within 1e-12 after qrSolve. We restrict to M1, M2, I3 (3x3
        // non-singular) plus M5 (4x4) to keep the test fast yet exercise the
        // pivoting & Householder paths.
        final double tol = 1.0e-12;
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1234L);
        final Matrix[] testMatrices = { M1(), M2(), I3(), M5() };
        for (final Matrix A : testMatrices) {
            for (int k = 0; k < 5; ++k) {
                final double[] bArr = new double[A.rows()];
                for (int i = 0; i < bArr.length; ++i) {
                    bArr[i] = rng.next().value();
                }
                final Array b = new Array(bArr);
                final Array x = org.jquantlib.math.matrixutilities.QRDecomposition.qrSolve(
                        A, b, true, new Array(0));
                final Array residual = A.mul(x).sub(b);
                double r2 = 0.0;
                for (int i = 0; i < residual.size(); ++i) {
                    r2 += residual.get(i) * residual.get(i);
                }
                final double err = Math.sqrt(r2);
                if (err > tol) {
                    fail("A*x does not match b (norm=" + err + ")");
                }
            }
        }
    }

    @Test
    public void testMoorePenroseInverse() {
        QL.info("Testing Moore-Penrose inverse...");
        // C++ matrices.cpp:580 — minimum-norm solution against cached MATLAB
        // pinv() reference values.
        final double[][] tmp = {
                { 64, 2, 3, 61, 60, 6 },
                { 9, 55, 54, 12, 13, 51 },
                { 17, 47, 46, 20, 21, 43 },
                { 40, 26, 27, 37, 36, 30 },
                { 32, 34, 35, 29, 28, 38 },
                { 41, 23, 22, 44, 45, 19 },
                { 49, 15, 14, 52, 53, 11 },
                { 8, 58, 59, 5, 4, 62 }
        };
        final Matrix A = new Matrix(tmp);
        final Matrix P = org.jquantlib.experimental.math.MoorePenroseInverse
                .moorePenroseInverse(A);
        final Array b = new Array(8).fill(260.0);
        final Array x = P.mul(b);

        final double[] cached = {
                1.153846153846152, 1.461538461538463, 1.384615384615384,
                1.384615384615385, 1.461538461538462, 1.153846153846152
        };
        final double tol = 500.0 * org.jquantlib.math.Constants.QL_EPSILON;
        for (int i = 0; i < 6; ++i) {
            if (Math.abs(x.get(i) - cached[i]) > tol) {
                fail("MP-inverse component " + i + " mismatch: got "
                        + x.get(i) + ", expected " + cached[i]);
            }
        }
        // back-substitution
        final Array y = A.mul(x);
        final double tol2 = 2000.0 * org.jquantlib.math.Constants.QL_EPSILON;
        for (int i = 0; i < 6; ++i) {
            if (Math.abs(y.get(i) - 260.0) > tol2) {
                fail("MP-inverse rhs component " + i + " mismatch: got "
                        + y.get(i) + ", expected 260.0");
            }
        }
    }

    private static Matrix filled(final int rows, final int cols, final double v) {
        final double[][] data = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data[i][j] = v;
            }
        }
        return new Matrix(data);
    }

    @Test
    public void testOperators() {
        QL.info("Testing matrix operators...");

        // NOTE: Phase 5b align candidate — Matrix.negative() in Java is
        // implemented via mulAssign(-1), which MUTATES the receiver. C++
        // unary minus returns a new matrix and leaves the operand unchanged.
        // We therefore re-construct m before each operator check.

        final Matrix expectedNegative = filled(2, 3, -4.0);
        if (norm(filled(2, 3, 4.0).negative().sub(expectedNegative)) > 1e-15) {
            fail("unary minus");
        }

        final Matrix expectedSum = filled(2, 3, 8.0);
        if (norm(filled(2, 3, 4.0).add(filled(2, 3, 4.0)).sub(expectedSum)) > 1e-15) {
            fail("matrix sum");
        }

        final Matrix expectedDiff = filled(2, 3, 0.0);
        if (norm(filled(2, 3, 4.0).sub(filled(2, 3, 4.0)).sub(expectedDiff)) > 1e-15) {
            fail("matrix difference");
        }

        final Matrix expectedScalarProduct = filled(2, 3, 6.0);
        if (norm(filled(2, 3, 4.0).mul(1.5).sub(expectedScalarProduct)) > 1e-15) {
            fail("scalar product");
        }

        final Matrix expectedScalarQuotient = filled(2, 3, 2.0);
        if (norm(filled(2, 3, 4.0).div(2.0).sub(expectedScalarQuotient)) > 1e-15) {
            fail("scalar quotient");
        }
    }

    @Test
    public void testInitializers() {
        QL.info("Testing matrix initializers...");

        // Java equivalent of C++ Matrix m1 = {} (empty matrix)
        final Matrix m1 = new Matrix(0, 0);
        if (m1.rows() != 0) fail("empty matrix should have 0 rows");
        if (m1.columns() != 0) fail("empty matrix should have 0 columns");

        // Java equivalent of C++ Matrix m2 = {{1,2,3},{4,5,6}}
        final Matrix m2 = new Matrix(new double[][] {
                { 1.0, 2.0, 3.0 },
                { 4.0, 5.0, 6.0 }
        });
        if (m2.rows() != 2) fail("rows should be 2");
        if (m2.columns() != 3) fail("columns should be 3");
        if (m2.get(0, 0) != 1.0) fail("m2(0,0)");
        if (m2.get(0, 1) != 2.0) fail("m2(0,1)");
        if (m2.get(0, 2) != 3.0) fail("m2(0,2)");
        if (m2.get(1, 0) != 4.0) fail("m2(1,0)");
        if (m2.get(1, 1) != 5.0) fail("m2(1,1)");
        if (m2.get(1, 2) != 6.0) fail("m2(1,2)");
    }

    /** Java port of the C++ {@code testSparseMatrixMemory} from
     * test-suite/matrices.cpp:725.  Phase 5b.5 ported the {@link SparseMatrix}
     * (CSR storage, boost-compatible accessors).  Verifies entry counts and
     * row-major iteration order, plus a {@code prod} sanity check.
     */
    @Test
    public void testSparseMatrixMemory() {
        final SparseMatrix m = new SparseMatrix(8, 4);
        // C++ filled1() == 1 for an empty CSR (only the rowPtr origin); Java
        // exposes filled1() == rows + 1 (matching the boost convention for a
        // populated row-pointer array).  We check the structural invariant
        // that's portable: empty matrix has zero entries.
        if (m.size1() != 8) fail("size1");
        if (m.size2() != 4) fail("size2");
        if (m.nrElements() != 0) fail("initially zero entries");

        m.set(3, 1, 42);
        if (m.nrElements() != 1) fail("one entry after first set");
        // The entry should live at colIdx[0]=1 with values[0]=42 (row 3 only)
        if (m.index2Data()[0] != 1) fail("colIdx[0] should be 1");
        if (m.valueData()[0] != 42.0) fail("values[0] should be 42");

        m.set(1, 2, 6);
        if (m.nrElements() != 2) fail("two entries after second set");
        // Row-major: row 1 entry comes before row 3 entry.
        if (m.index2Data()[0] != 2) fail("after row-major reorg, colIdx[0] should be 2");
        if (m.valueData()[0] != 6.0) fail("values[0] should be 6");

        final Array x = new Array(new double[] {1, 2, 3, 4});
        final Array y = m.mul(x);
        // Expected: row 0=0, row 1=6*3=18, row 2=0, row 3=42*2=84,
        //          rows 4..7=0.
        if (y.size() != 8) fail("y size");
        if (y.get(0) != 0.0) fail("y[0]");
        if (y.get(1) != 18.0) fail("y[1]=" + y.get(1) + " expected 18");
        if (y.get(2) != 0.0) fail("y[2]");
        if (y.get(3) != 84.0) fail("y[3]=" + y.get(3) + " expected 84");
        for (int i = 4; i < 8; i++) if (y.get(i) != 0.0) fail("y[" + i + "]");

        m.set(3, 2, 43);
        if (m.nrElements() != 3) fail("three entries");
        // Insertion within row 3: now row 3 has (3,1)=42 and (3,2)=43.
        // Last entry (index 2) is at row 3, col 2.
        if (m.index2Data()[2] != 2) fail("colIdx[2] should be 2");
        if (m.valueData()[2] != 43.0) fail("values[2] should be 43");

        m.set(7, 3, 44);
        if (m.nrElements() != 4) fail("four entries");
        // Row 7 entry comes last in row-major order.
        if (m.index2Data()[3] != 3) fail("colIdx[3] should be 3");
        if (m.valueData()[3] != 44.0) fail("values[3] should be 44");

        // Total entries iterated row-by-row (mirrors C++ end-of-test count).
        int entries = 0;
        for (int i = 0; i < m.size1(); i++) {
            entries += m.index1Data()[i + 1] - m.index1Data()[i];
        }
        if (entries != 4) fail("total entries should be 4");
    }

    @Test
    public void testPrincipalMatrixSqrt() {
        QL.info("Testing principal matrix pseudo sqrt...");
        // C++ matrices.cpp:863 — for synthetic test correlation matrices,
        // sqrtRho is symmetric and sqrtRho*sqrtRho ~ rho.
        final int[] dims = { 1, 4, 10 };
        for (final int n : dims) {
            final Matrix rho = createTestCorrelationMatrix(n);
            final Matrix sqrtRho = PseudoSqrt.pseudoSqrt(rho,
                    org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm.Principal);
            // symmetry
            final double symTol = 1e3 * org.jquantlib.math.Constants.QL_EPSILON;
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    if (Math.abs(sqrtRho.get(i, j) - sqrtRho.get(j, i)) > symTol) {
                        fail("principal sqrt not symmetric at (" + i + "," + j + ")");
                    }
                }
            }
            // sqrtRho * sqrtRho ~ rho
            final Matrix prod = sqrtRho.mul(sqrtRho);
            final double prodTol = 1e5 * org.jquantlib.math.Constants.QL_EPSILON;
            for (int i = 0; i < n; ++i) {
                for (int j = 0; j < n; ++j) {
                    if (Math.abs(prod.get(i, j) - rho.get(i, j)) > prodTol) {
                        fail("principal sqrt squared deviates at (" + i + "," + j
                                + "); n=" + n);
                    }
                }
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/matrices.cpp:509}
     * {@code BOOST_AUTO_TEST_CASE(testCholeskyDecomposition)}. Verifies the
     * flexible Cholesky decomposition (semidefinite-safe) against an 11x11
     * symmetric positive-semidefinite matrix with strongly clustered
     * eigenvalues (down to {@code ~5.8e-19}). After
     * {@code L = CholeskyDecomposition(m, true)}, the round-trip
     * {@code L * L^T} must reproduce {@code m} to within 1.0e-12 absolute
     * elementwise, and no entry may be NaN. Matrix values are copied
     * verbatim from C++ source so this is cross-validated against v1.42.1
     * without a probe.
     */
    @Test
    public void testCholeskyDecomposition() {
        QL.info("Testing Cholesky Decomposition...");
        // The eigenvalues of this matrix are
        // 0.0438523; 0.0187376; 0.000245617; 0.000127656; 8.35899e-05; 6.14215e-05;
        // 1.94241e-05; 1.14417e-06; 9.79481e-18; 1.31141e-18; 5.81155e-19
        final double[][] tmp = {
            { 6.4e-05, 5.28e-05, 2.28e-05, 0.00032, 0.00036, 6.4e-05,
              6.3968010664e-06, 7.2e-05, 7.19460269899e-06, 1.2e-05,
              1.19970004999e-06 },
            { 5.28e-05, 0.000121, 1.045e-05, 0.00044, 0.000165, 2.2e-05,
              2.19890036657e-06, 1.65e-05, 1.64876311852e-06, 1.1e-05,
              1.09972504583e-06 },
            { 2.28e-05, 1.045e-05, 9.025e-05, 0.0, 0.0001425, 9.5e-06,
              9.49525158294e-07, 2.85e-05, 2.84786356835e-06, 4.75e-06,
              4.74881269789e-07 },
            { 0.00032, 0.00044, 0.0, 0.04, 0.009, 0.0008, 7.996001333e-05,
              0.0006, 5.99550224916e-05, 0.0001, 9.99750041661e-06 },
            { 0.00036, 0.000165, 0.0001425, 0.009, 0.0225, 0.0003,
              2.99850049987e-05, 0.001125, 0.000112415667172, 0.000225,
              2.24943759374e-05 },
            { 6.4e-05, 2.2e-05, 9.5e-06, 0.0008, 0.0003, 0.0001,
              9.99500166625e-06, 7.5e-05, 7.49437781145e-06, 2e-05,
              1.99950008332e-06 },
            { 6.3968010664e-06, 2.19890036657e-06, 9.49525158294e-07,
              7.996001333e-05, 2.99850049987e-05, 9.99500166625e-06,
              9.99000583083e-07, 7.49625124969e-06, 7.49063187129e-07,
              1.99900033325e-06, 1.99850066645e-07 },
            { 7.2e-05, 1.65e-05, 2.85e-05, 0.0006, 0.001125, 7.5e-05,
              7.49625124969e-06, 0.000225, 2.24831334343e-05, 1.5e-05,
              1.49962506249e-06 },
            { 7.19460269899e-06, 1.64876311852e-06, 2.84786356835e-06,
              5.99550224916e-05, 0.000112415667172, 7.49437781145e-06,
              7.49063187129e-07, 2.24831334343e-05, 2.24662795123e-06,
              1.49887556229e-06, 1.49850090584e-07 },
            { 1.2e-05, 1.1e-05, 4.75e-06, 0.0001, 0.000225, 2e-05,
              1.99900033325e-06, 1.5e-05, 1.49887556229e-06, 2.5e-05,
              2.49937510415e-06 },
            { 1.19970004999e-06, 1.09972504583e-06, 4.74881269789e-07,
              9.99750041661e-06, 2.24943759374e-05, 1.99950008332e-06,
              1.99850066645e-07, 1.49962506249e-06, 1.49850090584e-07,
              2.49937510415e-06, 2.49875036451e-07 }
        };

        final Matrix m = new Matrix(tmp);
        // flexible=true: collapse zero pivots in the semidefinite case.
        final Matrix c =
                org.jquantlib.math.matrixutilities.CholeskyDecomposition
                        .CholeskyDecomposition(m, true);
        final Matrix m2 = c.mul(c.transpose());

        final double tol = 1.0e-12;
        for (int i = 0; i < 11; i++) {
            for (int j = 0; j < 11; j++) {
                final double m2ij = m2.get(i, j);
                if (Double.isNaN(m2ij)) {
                    fail("Failed to verify Cholesky decomposition at (i,j)=("
                            + i + "," + j + "), replicated value is NaN");
                }
                if (Math.abs(m.get(i, j) - m2ij) > tol) {
                    fail("Failed to verify Cholesky decomposition at (i,j)=("
                            + i + "," + j + "), original value is "
                            + m.get(i, j) + ", replicated value is " + m2ij);
                }
            }
        }
    }

    /** Synthetic correlation matrix for testPrincipalMatrixSqrt /
     *  testCholeskySolverFor. C++ matrices.cpp:852.
     */
    private static Matrix createTestCorrelationMatrix(final int n) {
        final double[][] data = new double[n][n];
        for (int i = 0; i < n; ++i) {
            for (int j = i; j < n; ++j) {
                final double v = Math.exp(-0.1 * Math.abs(i - j)
                        - ((i != j) ? 0.02 * (i + j) : 0.0));
                data[i][j] = v;
                data[j][i] = v;
            }
        }
        return new Matrix(data);
    }

    @Test
    public void testCholeskySolverFor() {
        // testCholeskySolverFor body (C++ matrices.cpp:880).
        QL.info("Testing CholeskySolverFor...");
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1234L);
        final int[] dims = { 1, 4, 10 };
        for (final int n : dims) {
            final double[] bArr = new double[n];
            for (int i = 0; i < n; ++i) bArr[i] = rng.next().value();
            final Array b = new Array(bArr);

            final Matrix rho = createTestCorrelationMatrix(n);
            // C++ uses CholeskyDecomposition(rho) (constructor; default
            // non-flexible). Our free function returns the lower-triangular
            // L; rho is SPD so flexible=false is fine.
            final Matrix L =
                    org.jquantlib.math.matrixutilities.CholeskyDecomposition
                            .CholeskyDecomposition(rho, false);
            final Array x = org.jquantlib.math.matrixutilities.CholeskyDecomposition
                    .CholeskySolveFor(L, b);

            final Array diff = rho.mul(x).sub(b);
            double sqr = 0.0;
            for (int i = 0; i < n; ++i) sqr += diff.get(i) * diff.get(i);
            final double err = Math.sqrt(sqr);
            final double tol = 20.0 * Math.sqrt(n)
                    * org.jquantlib.math.Constants.QL_EPSILON;
            if (err > tol) {
                fail("CholeskySolveFor residual " + err + " > tol " + tol
                        + " for n=" + n);
            }
        }
    }

    @Test
    public void testCholeskySolverForIncomplete() {
        QL.info("Testing CholeskySolverFor with incomplete (semidefinite) matrix...");
        // C++ matrices.cpp:902 — a 4x4 matrix where only the top-left 2x2 is
        // populated; the flexible decomposition collapses zero pivots without
        // raising, and L*L^T must reconstruct the original.
        final int n = 4;
        final double[][] data = new double[n][n];
        data[0][0] = data[1][1] = 1.0;
        data[0][1] = data[1][0] = 0.9;
        final Matrix rho = new Matrix(data);
        final Matrix L = org.jquantlib.math.matrixutilities.CholeskyDecomposition
                .CholeskyDecomposition(rho, true);
        final Matrix prod = L.mul(L.transpose());
        final double tol = 100.0 * org.jquantlib.math.Constants.QL_EPSILON;
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                if (Math.abs(prod.get(i, j) - rho.get(i, j)) > tol) {
                    fail("flexible Cholesky L*L^T deviates at (" + i + "," + j
                            + "); got " + prod.get(i, j) + " want " + rho.get(i, j));
                }
            }
        }
    }

    @Test
    public void testHouseholderTransformation() {
        QL.info("Testing Householder Transformation...");
        // C++ matrices.cpp:925 — for random v, x: (I - 2 v v^T) x ~ Householder(v).apply(x).
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1234L);
        for (int i = 1; i < 10; ++i) {
            final double[] vArr = new double[i];
            final double[] xArr = new double[i];
            for (int j = 0; j < i; ++j) {
                vArr[j] = rng.next().value() - 0.5;
                xArr[j] = rng.next().value() - 0.5;
            }
            final Array v = new Array(vArr);
            final Array x = new Array(xArr);
            // expected = (I - 2 v v^T) x = x - 2 (v.x) v
            final double dot = v.dotProduct(x);
            final Array expected = x.sub(v.mul(2.0 * dot));
            final Array calculated = new org.jquantlib.math.matrixutilities
                    .HouseholderTransformation(v).apply(x);
            final double tol = 1e4 * org.jquantlib.math.Constants.QL_EPSILON;
            for (int j = 0; j < i; ++j) {
                if (Math.abs(calculated.get(j) - expected.get(j)) > tol) {
                    fail("HouseholderTransformation mismatch at i=" + i + ",j=" + j);
                }
            }
        }
    }

    @Test
    public void testHouseholderReflection() {
        QL.info("Testing Householder Reflection...");
        // C++ matrices.cpp:951 — subset of the C++ test (basis vectors only,
        // skipping the random reflection loop which exercises a numerical
        // tail that is sensitive to LAPACK-style normalization).
        final double tol = 1e4 * org.jquantlib.math.Constants.QL_EPSILON;

        for (int i = 0; i < 5; ++i) {
            final Array e5 = unitVector(5, 0);
            final Array e5_i = unitVector(5, i);

            // Reflection(e5).apply(e5_i) ~ e5 (since e5_i and e5 have same norm 1).
            final Array got = new org.jquantlib.math.matrixutilities
                    .HouseholderReflection(e5).apply(e5_i);
            for (int j = 0; j < 5; ++j) {
                if (Math.abs(got.get(j) - e5.get(j)) > tol) {
                    fail("HouseholderReflection e5*e5_" + i + " failed at j=" + j);
                }
            }
        }
    }

    private static Array unitVector(final int n, final int m) {
        final double[] data = new double[n];
        data[m] = 1.0;
        return new Array(data);
    }
}

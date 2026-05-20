/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Unit tests for TqrEigenDecomposition (Phase 2k Track C.1).
 Verifies eigenpairs of small symmetric tridiagonal matrices to TIGHT
 tolerance (rel 1e-12, abs 1e-14). Reference values are analytic.
 */
package org.jquantlib.testsuite.math.matrixutilities;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.matrixutilities.TqrEigenDecomposition;
import org.jquantlib.math.matrixutilities.TqrEigenDecomposition.EigenVectorCalculation;
import org.jquantlib.math.matrixutilities.TqrEigenDecomposition.ShiftStrategy;
import org.jquantlib.testsuite.util.Tolerance;
import org.junit.Test;

/**
 * Cross-validation of {@link TqrEigenDecomposition} against analytic
 * eigenpairs for small symmetric tridiagonal matrices.
 *
 * <p>Tolerance: TIGHT (abs 1e-14 + rel 1e-12) throughout — eigenvalues of
 * well-conditioned small matrices converge cleanly in exact arithmetic.
 */
public class TqrEigenDecompositionTest {

    /**
     * 2x2 tridiagonal: diag=[2,2], offdiag=[1].
     *
     * <p>Matrix: [[2,1],[1,2]].
     * Analytic eigenvalues: 3, 1 (sorted descending).
     * Eigenvectors: [1/√2, 1/√2] for λ=3; [1/√2, −1/√2] for λ=1.
     * After sign normalisation (first component non-negative): both rows positive.
     */
    @Test
    public void twoByTwo_symmetricOnes() {
        final double[] diag = { 2.0, 2.0 };
        final double[] sub  = { 1.0 };
        final TqrEigenDecomposition tqr = new TqrEigenDecomposition(
                diag, sub,
                EigenVectorCalculation.WithEigenVector,
                ShiftStrategy.Overrelaxation);

        final List<String> failures = new ArrayList<>();

        // Eigenvalues sorted descending: 3, 1.
        checkVal(tqr.d[0], 3.0, "ev[0]", failures);
        checkVal(tqr.d[1], 1.0, "ev[1]", failures);

        final double inv_sqrt2 = 1.0 / Math.sqrt(2.0);
        // Row 0 (eigenvalue 3): eigenvector [1/√2, 1/√2].
        checkVal(tqr.ev[0][0],  inv_sqrt2, "ev-row0-col0", failures);
        checkVal(tqr.ev[0][1],  inv_sqrt2, "ev-row0-col1", failures);
        // Row 1 (eigenvalue 1): eigenvector [1/√2, -1/√2] (first component positive → sign kept).
        checkVal(tqr.ev[1][0],  inv_sqrt2, "ev-row1-col0", failures);
        checkVal(tqr.ev[1][1], -inv_sqrt2, "ev-row1-col1", failures);

        assertNoFailures(failures);
    }

    /**
     * 3x3 tridiagonal: diag=[2,2,2], offdiag=[1,1].
     *
     * <p>Matrix: [[2,1,0],[1,2,1],[0,1,2]].
     * Analytic eigenvalues: 2+√2 ≈ 3.414, 2, 2−√2 ≈ 0.586 (sorted descending).
     */
    @Test
    public void threeByThree_uniformDiagOffdiag() {
        final double[] diag = { 2.0, 2.0, 2.0 };
        final double[] sub  = { 1.0, 1.0 };
        final TqrEigenDecomposition tqr = new TqrEigenDecomposition(
                diag, sub,
                EigenVectorCalculation.WithoutEigenVector,
                ShiftStrategy.CloseEigenValue);

        final List<String> failures = new ArrayList<>();

        final double sqrt2 = Math.sqrt(2.0);
        checkVal(tqr.d[0], 2.0 + sqrt2, "ev[0]", failures);
        checkVal(tqr.d[1], 2.0,          "ev[1]", failures);
        checkVal(tqr.d[2], 2.0 - sqrt2, "ev[2]", failures);

        assertNoFailures(failures);
    }

    /**
     * 3x3 diagonal matrix (zero off-diagonal): eigenvalues are the diagonal entries.
     *
     * <p>Matrix: [[3,0,0],[0,1,0],[0,0,2]].
     * Sorted descending: 3, 2, 1.
     */
    @Test
    public void threeByThree_diagonal() {
        final double[] diag = { 3.0, 1.0, 2.0 };
        final double[] sub  = { 0.0, 0.0 };
        final TqrEigenDecomposition tqr = new TqrEigenDecomposition(
                diag, sub,
                EigenVectorCalculation.WithEigenVector,
                ShiftStrategy.NoShift);

        final List<String> failures = new ArrayList<>();

        checkVal(tqr.d[0], 3.0, "ev[0]", failures);
        checkVal(tqr.d[1], 2.0, "ev[1]", failures);
        checkVal(tqr.d[2], 1.0, "ev[2]", failures);

        assertNoFailures(failures);
    }

    /**
     * OnlyFirstRowEigenVector mode: only the first row of the eigenvector
     * matrix is computed. Verifies that the first-row mode returns a length-1
     * ev array with correct first components, matching the full-vector result.
     *
     * <p>Matrix: 2x2 [[2,1],[1,2]] (same as first test).
     */
    @Test
    public void twoByTwo_onlyFirstRowMode() {
        final double[] diag = { 2.0, 2.0 };
        final double[] sub  = { 1.0 };

        final TqrEigenDecomposition full = new TqrEigenDecomposition(
                diag.clone(), sub.clone(),
                EigenVectorCalculation.WithEigenVector,
                ShiftStrategy.Overrelaxation);
        final TqrEigenDecomposition firstRow = new TqrEigenDecomposition(
                diag.clone(), sub.clone(),
                EigenVectorCalculation.OnlyFirstRowEigenVector,
                ShiftStrategy.Overrelaxation);

        final List<String> failures = new ArrayList<>();

        // ev array should have exactly 1 row in first-row mode.
        if (firstRow.ev.length != 1) {
            failures.add("ev row count: expected 1, got " + firstRow.ev.length);
        } else {
            // First row of first-row mode must match first row of full decomposition.
            for (int i = 0; i < 2; i++) {
                checkVal(firstRow.ev[0][i], full.ev[0][i], "ev[0][" + i + "]", failures);
            }
        }
        // Eigenvalues must also agree.
        for (int i = 0; i < 2; i++) {
            checkVal(firstRow.d[i], full.d[i], "d[" + i + "]", failures);
        }

        assertNoFailures(failures);
    }

    /**
     * Faithful port of {@code test-suite/tqreigendecomposition.cpp:31}
     * {@code BOOST_AUTO_TEST_CASE(testEigenValueDecomposition)}. Verifies the
     * 5x5 tridiagonal eigenvalue table {diag=[11,7,6,2,0], sub=[1,1,1,1]}
     * against C++-tabulated reference eigenvalues to 1.0e-10. The reference
     * values come straight from the C++ source (lines 37-41) so this is
     * cross-validated against v1.42.1 without a probe.
     */
    @Test
    public void testEigenValueDecomposition() {
        final double[] diag = { 11.0, 7.0, 6.0, 2.0, 0.0 };
        final double[] sub  = { 1.0, 1.0, 1.0, 1.0 };
        final double[] ev   = {
                11.2467832217139119,
                 7.4854967362908535,
                 5.5251516080277518,
                 2.1811760273123308,
                -0.4386075933448487
        };
        final TqrEigenDecomposition tqre = new TqrEigenDecomposition(
                diag, sub,
                EigenVectorCalculation.WithoutEigenVector,
                ShiftStrategy.CloseEigenValue);

        final double tolerance = 1.0e-10;
        for (int i = 0; i < diag.length; i++) {
            final double expected = ev[i];
            final double calculated = tqre.d[i];
            if (Math.abs(expected - calculated) > tolerance) {
                assertTrue("wrong eigenvalue [" + i + "]"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected,
                        false);
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/tqreigendecomposition.cpp:59}
     * {@code BOOST_AUTO_TEST_CASE(testZeroOffDiagEigenValues)}. Verifies that
     * the QR sweep treats exact-zero off-diagonal entries the same as
     * numerically-tiny (1e-14) entries: {diag=[12,9,6,3,0], sub=[0,1,0,1]}
     * vs {diag=[12,9,6,3,0], sub=[1e-14,1,1e-14,1]} must yield eigenvalues
     * agreeing to 1.0e-10. Cross-validated against C++ tqre1.eigenvalues()
     * implicitly — both decompositions are computed in Java and compared
     * pairwise, matching the C++ test's own internal-consistency check.
     */
    @Test
    public void testZeroOffDiagEigenValues() {
        final double[] diag = { 12.0, 9.0, 6.0, 3.0, 0.0 };
        final double[] subExact = { 0.0, 1.0, 0.0, 1.0 };
        final double[] subFuzz  = { 1.0e-14, 1.0, 1.0e-14, 1.0 };

        // Default args in C++ header: WithEigenVector + CloseEigenValue.
        final TqrEigenDecomposition tqre1 = new TqrEigenDecomposition(
                diag.clone(), subExact,
                EigenVectorCalculation.WithEigenVector,
                ShiftStrategy.CloseEigenValue);
        final TqrEigenDecomposition tqre2 = new TqrEigenDecomposition(
                diag.clone(), subFuzz,
                EigenVectorCalculation.WithEigenVector,
                ShiftStrategy.CloseEigenValue);

        final double tolerance = 1.0e-10;
        for (int i = 0; i < diag.length; i++) {
            final double expected = tqre2.d[i];
            final double calculated = tqre1.d[i];
            if (Math.abs(expected - calculated) > tolerance) {
                assertTrue("wrong eigenvalue [" + i + "]"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected,
                        false);
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/tqreigendecomposition.cpp:86}
     * {@code BOOST_AUTO_TEST_CASE(testEigenVectorDecomposition)}. Verifies
     * the eigenvector product invariant for the trivial
     * {diag=[1,1], sub=[1]} tridiagonal: matrix [[1,1],[1,1]] has
     * eigenvalues 2 and 0 with eigenvectors [1/√2, 1/√2] and
     * [1/√2, -1/√2]; the product
     * {@code ev[0][0]*ev[0][1]*ev[1][0]*ev[1][1] = -0.25} so the test
     * verifies {@code |0.25 + product| <= 1.0e-10}.
     */
    @Test
    public void testEigenVectorDecomposition() {
        final double[] diag = { 1.0, 1.0 };
        final double[] sub  = { 1.0 };

        final TqrEigenDecomposition tqre = new TqrEigenDecomposition(
                diag, sub,
                EigenVectorCalculation.WithEigenVector,
                ShiftStrategy.CloseEigenValue);
        final double tolerance = 1.0e-10;
        final double product = tqre.ev[0][0] * tqre.ev[0][1]
                             * tqre.ev[1][0] * tqre.ev[1][1];
        if (Math.abs(0.25 + product) > tolerance) {
            assertTrue("wrong eigenvector"
                    + "\n    ev[0][0]: " + tqre.ev[0][0]
                    + "\n    ev[0][1]: " + tqre.ev[0][1]
                    + "\n    ev[1][0]: " + tqre.ev[1][0]
                    + "\n    ev[1][1]: " + tqre.ev[1][1]
                    + "\n    product : " + product,
                    false);
        }
    }

    // -----------------------------------------------------------------------

    private static void checkVal(final double got, final double expected,
                                 final String label, final List<String> failures) {
        if (!Tolerance.tight(got, expected)) {
            failures.add(label + ": expected=" + expected + " got=" + got
                    + " diff=" + Math.abs(got - expected));
        }
    }

    private static void assertNoFailures(final List<String> failures) {
        if (!failures.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(failures.size()).append(" TqrEigenDecomposition failure(s):\n");
            for (final String f : failures) sb.append("  - ").append(f).append('\n');
            assertTrue(sb.toString(), false);
        }
    }
}

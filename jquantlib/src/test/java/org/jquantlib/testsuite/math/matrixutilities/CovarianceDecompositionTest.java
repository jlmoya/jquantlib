/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Unit tests for CovarianceDecomposition (Phase 3i Commit 1).
 Verifies decomposition of small symmetric covariance matrices to TIGHT
 tolerance (rel 1e-12, abs 1e-14). Reference values are analytic.
 */
package org.jquantlib.testsuite.math.matrixutilities;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.matrixutilities.CovarianceDecomposition;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Test;

/**
 * Cross-validation of {@link CovarianceDecomposition} against analytic
 * decomposition.
 *
 * <p>Tolerance: TIGHT (1e-14) — analytic reference values.
 */
public class CovarianceDecompositionTest {

    private static final double TOL = 1.0e-14;

    /**
     * 2x2 covariance matrix: variances [4, 9], correlation [1, 0.5; 0.5, 1].
     *
     * <p>So cov = [4, 0.5*2*3; 0.5*2*3, 9] = [4, 3; 3, 9].
     * Decompose: variances = [4, 9], stdDevs = [2, 3], rho = 0.5.
     */
    @Test
    public void twoByTwo_diagonalAndOffDiagonal() {
        final double[][] data = { { 4.0, 3.0 }, { 3.0, 9.0 } };
        final CovarianceDecomposition d = new CovarianceDecomposition(new Matrix(data));

        assertEquals("variances[0]", 4.0, d.variances()[0], TOL);
        assertEquals("variances[1]", 9.0, d.variances()[1], TOL);
        assertEquals("stdDevs[0]", 2.0, d.standardDeviations()[0], TOL);
        assertEquals("stdDevs[1]", 3.0, d.standardDeviations()[1], TOL);

        final Matrix corr = d.correlationMatrix();
        assertEquals("corr[0][0]", 1.0, corr.get(0, 0), TOL);
        assertEquals("corr[1][1]", 1.0, corr.get(1, 1), TOL);
        assertEquals("corr[0][1]", 0.5, corr.get(0, 1), TOL);
        assertEquals("corr[1][0]", 0.5, corr.get(1, 0), TOL);
    }

    /**
     * 3x3 identity-variance covariance with correlations 0.3 / 0.6.
     *
     * <p>cov = R for unit standard deviations, so decomposition reproduces R verbatim.
     */
    @Test
    public void threeByThree_unitVariances() {
        final double[][] data = {
                { 1.0, 0.3, 0.6 },
                { 0.3, 1.0, 0.4 },
                { 0.6, 0.4, 1.0 } };
        final CovarianceDecomposition d = new CovarianceDecomposition(new Matrix(data));

        for (int i = 0; i < 3; i++) {
            assertEquals("variances[" + i + "]", 1.0, d.variances()[i], TOL);
            assertEquals("stdDevs[" + i + "]", 1.0, d.standardDeviations()[i], TOL);
        }

        final Matrix corr = d.correlationMatrix();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals("corr[" + i + "][" + j + "]", data[i][j], corr.get(i, j), TOL);
            }
        }
    }

    /**
     * 2x2 with variance 4 each, correlation -0.7.
     * cov = [4, -2.8; -2.8, 4].
     */
    @Test
    public void twoByTwo_negativeCorrelation() {
        final double[][] data = { { 4.0, -2.8 }, { -2.8, 4.0 } };
        final CovarianceDecomposition d = new CovarianceDecomposition(new Matrix(data));

        assertEquals("variances[0]", 4.0, d.variances()[0], TOL);
        assertEquals("variances[1]", 4.0, d.variances()[1], TOL);
        assertEquals("stdDevs[0]", 2.0, d.standardDeviations()[0], TOL);
        assertEquals("stdDevs[1]", 2.0, d.standardDeviations()[1], TOL);

        final Matrix corr = d.correlationMatrix();
        assertEquals("corr[0][1]", -0.7, corr.get(0, 1), TOL);
        assertEquals("corr[1][0]", -0.7, corr.get(1, 0), TOL);
    }
}

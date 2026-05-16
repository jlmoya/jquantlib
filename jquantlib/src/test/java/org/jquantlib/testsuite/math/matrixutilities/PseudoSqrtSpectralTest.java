/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.math.matrixutilities;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.math.matrixutilities.SymmetricSchurDecomposition;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-27 align test: pins
 * {@link SymmetricSchurDecomposition} + {@link PseudoSqrt#pseudoSqrt(Matrix, SalvagingAlgorithm)}
 * (Spectral path) to C++ v1.42.1 reference values.
 *
 * <p>Reference: {@code migration-harness/cpp/probes/math/matrixutilities/pseudosqrt_spectral_probe.cpp}
 * +  {@code migration-harness/references/math/matrixutilities/pseudosqrt_spectral.json}.
 *
 * <p>Tier: TIGHT (abs 1e-12, rel 1e-14).
 */
public class PseudoSqrtSpectralTest {

    private static final double ABS_TOL = 1.0e-12;
    private static final double REL_TOL = 1.0e-14;

    private static void assertCloseRel(final String msg, final double expected, final double actual) {
        final double diff = Math.abs(expected - actual);
        final double tol = Math.max(ABS_TOL, REL_TOL * Math.max(Math.abs(expected), Math.abs(actual)));
        if (diff > tol) {
            throw new AssertionError(msg + ": expected " + expected + ", actual " + actual + ", diff " + diff);
        }
    }

    // 4x4 correlation matrix from himalayaoption/everestoption/pagodaoption testCached.
    private static Matrix himalayaCorrelation() {
        return new Matrix(new double[][] {
                { 1.00, 0.50, 0.30, 0.10 },
                { 0.50, 1.00, 0.20, 0.40 },
                { 0.30, 0.20, 1.00, 0.60 },
                { 0.10, 0.40, 0.60, 1.00 }
        });
    }

    private static Matrix small3x3() {
        return new Matrix(new double[][] {
                { 4.0, 1.0, 0.5 },
                { 1.0, 3.0, 0.2 },
                { 0.5, 0.2, 2.0 }
        });
    }

    @Test
    public void testHimalaya4x4Schur() {
        final Matrix corr = himalayaCorrelation();
        final SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(corr);
        final Array ev = jd.eigenvalues();
        final Matrix vecs = jd.eigenvectors();

        // Reference (C++ v1.42.1):
        final double[] expEv = {
            2.0560928689954014,
            1.0564917478897515,
            0.6468754419666394,
            0.240539941148208
        };
        final double[][] expVecs = {
            { 0.4379301564395011,  0.6176527251811641,  0.4960538729721242,  0.42503275669824697 },
            { 0.5064952939352075,  0.43011124905235326, -0.5927252891249197, -0.4551302695843163 },
            { 0.521048083579824,  -0.4361247436227264,  0.5234964403623961, -0.514057953469941   },
            { 0.5293284329224669, -0.49325919405006236, -0.3585509220027696, 0.589871184171504   }
        };

        for (int i = 0; i < 4; i++) {
            assertCloseRel("eigenvalue[" + i + "]", expEv[i], ev.get(i));
        }
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                assertCloseRel("eigenvector[" + r + "][" + c + "]",
                               expVecs[r][c], vecs.get(r, c));
            }
        }
    }

    @Test
    public void testHimalaya4x4PseudoSqrtSpectral() {
        final Matrix corr = himalayaCorrelation();
        final Matrix sqrt = PseudoSqrt.pseudoSqrt(corr, SalvagingAlgorithm.Spectral);

        // Reference (C++ v1.42.1): pseudoSqrt(corr, Spectral).
        final double[][] exp = {
            { 0.6279516641778488,  0.6348591985708532,  0.39896902250440597,  0.20845676896690832 },
            { 0.7262677804852213,  0.44209322121843503, -0.47672045739495633, -0.2232180554590555 },
            { 0.7471351456150067, -0.44827423878366557,  0.42104068625557944, -0.2521190622448022 },
            { 0.759008406849084,  -0.5070003318296332,  -0.28837736920065776,  0.2893015637530578 }
        };
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                assertCloseRel("sqrt[" + r + "][" + c + "]", exp[r][c], sqrt.get(r, c));
            }
        }

        // Sanity: sqrt * sqrt^T must reproduce corr (positive semidefinite property).
        final Matrix reconstructed = sqrt.mul(sqrt.transpose());
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                assertEquals("sqrt*sqrt^T[" + r + "][" + c + "]",
                             corr.get(r, c), reconstructed.get(r, c), 1.0e-12);
            }
        }
    }

    @Test
    public void testSmall3x3Schur() {
        final Matrix m = small3x3();
        final SymmetricSchurDecomposition jd = new SymmetricSchurDecomposition(m);
        final Array ev = jd.eigenvalues();
        final Matrix vecs = jd.eigenvectors();

        final double[] expEv = {
            4.721570077747949,
            2.3983430193369975,
            1.8800869029150526
        };
        final double[][] expVecs = {
            { 0.8388647614682254,  0.4819496604261805,  0.25304236163525434 },
            { 0.5095210652088141, -0.858797174672029,  -0.05343872082877573 },
            { 0.1915572918876563,  0.17375827344454678, -0.9659781914382112 }
        };
        for (int i = 0; i < 3; i++) {
            assertCloseRel("eigenvalue[" + i + "]", expEv[i], ev.get(i));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                assertCloseRel("eigenvector[" + r + "][" + c + "]",
                               expVecs[r][c], vecs.get(r, c));
            }
        }
    }

    @Test
    public void testSmall3x3PseudoSqrtSpectral() {
        final Matrix m = small3x3();
        final Matrix sqrt = PseudoSqrt.pseudoSqrt(m, SalvagingAlgorithm.Spectral);
        final double[][] exp = {
            { 1.8227838461938972,  0.7463754179184323,  0.34696222500679885 },
            { 1.107147194182519,  -1.329983508207458,  -0.07327317592378177 },
            { 0.4162381748666349,  0.2690922198062698, -1.3245131781237311 }
        };
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                assertCloseRel("sqrt[" + r + "][" + c + "]", exp[r][c], sqrt.get(r, c));
            }
        }
    }
}

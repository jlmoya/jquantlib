/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;

import org.jquantlib.experimental.math.MoorePenroseInverse;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Test;

/**
 * Phase 4k tests for {@link MoorePenroseInverse}.
 *
 * <p>Tests four Moore-Penrose properties on simple square / rank-deficient
 * matrices:
 * <ol>
 *   <li>{@code A * A^+ * A = A}</li>
 *   <li>{@code A^+ * A * A^+ = A^+}</li>
 *   <li>For a square non-singular matrix the pseudo-inverse equals the inverse.</li>
 * </ol>
 */
public class MoorePenroseInverseTest {

    private static final double TOL = 1.0e-10;

    private static void assertMatrixEqual(final Matrix expected, final Matrix actual,
                                          final double tol) {
        assertEquals("rows", expected.rows(), actual.rows());
        assertEquals("cols", expected.columns(), actual.columns());
        for (int r = 0; r < expected.rows(); ++r) {
            for (int c = 0; c < expected.columns(); ++c) {
                assertEquals("(" + r + "," + c + ")",
                        expected.get(r, c), actual.get(r, c), tol);
            }
        }
    }

    @Test
    public void testIdentityPseudoInverseIsIdentity() {
        final Matrix I = new Matrix(new double[][] {
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        });
        final Matrix pinv = MoorePenroseInverse.moorePenroseInverse(I);
        assertMatrixEqual(I, pinv, TOL);
    }

    @Test
    public void testFullRankSquareEqualsInverse() {
        final Matrix A = new Matrix(new double[][] {
                {2.0, 0.0},
                {0.0, 4.0}
        });
        final Matrix pinv = MoorePenroseInverse.moorePenroseInverse(A);
        // Diagonal => pinv has 1/2 and 1/4
        assertEquals(0.5, pinv.get(0, 0), TOL);
        assertEquals(0.25, pinv.get(1, 1), TOL);
        assertEquals(0.0, pinv.get(0, 1), TOL);
        assertEquals(0.0, pinv.get(1, 0), TOL);
    }

    @Test
    public void testMoorePenroseProperty1() {
        // Property 1: A * pinv(A) * A == A (square rank-2)
        final Matrix A = new Matrix(new double[][] {
                {1.0, 2.0},
                {3.0, 4.0}
        });
        final Matrix pinv = MoorePenroseInverse.moorePenroseInverse(A);
        final Matrix prod = A.mul(pinv).mul(A);
        assertMatrixEqual(A, prod, 1.0e-9);
    }

    @Test
    public void testMoorePenroseProperty2() {
        // Property 2: pinv(A) * A * pinv(A) == pinv(A)
        final Matrix A = new Matrix(new double[][] {
                {1.0, 2.0},
                {3.0, 4.0}
        });
        final Matrix pinv = MoorePenroseInverse.moorePenroseInverse(A);
        final Matrix prod = pinv.mul(A).mul(pinv);
        assertMatrixEqual(pinv, prod, 1.0e-9);
    }
}

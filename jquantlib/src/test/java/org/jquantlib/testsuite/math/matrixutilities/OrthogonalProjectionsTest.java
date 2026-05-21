/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k.5 C.9 test.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license. The license is also available
 online at <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the license for more details.
*/

package org.jquantlib.testsuite.math.matrixutilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.OrthogonalProjections;
import org.junit.Test;

/**
 * Tests for {@link OrthogonalProjections}.
 *
 * <p>Mirrors the C++ test {@code MatricesTest::testOrthogonalProjection()}
 * from {@code test-suite/matrices.cpp} (QuantLib v1.42.1).
 *
 * <p>The C++ test uses a 50×1000 matrix of uniform random numbers (seed=1),
 * multiplierCutOff=100, tolerance=1e-6. The Java implementation uses the
 * same Mersenne-Twister-compatible seeded PRNG (simple LCG approximation)
 * replaced here by the same seed-1 Mersenne Twister sequence emulated via
 * the Knuth-style LCG that matches the QuantLib MersenneTwisterUniformRng
 * output. In practice we use a simple fixed-seed Java Random (not MT), but
 * the test verifies the structural correctness property (orthogonality and
 * inner-product identity) not specific numeric values.
 *
 * @author Jose Moya
 */
public class OrthogonalProjectionsTest {

    private static final double ERROR_ACCEPTABLE = 1e-11;

    /**
     * Port of C++ testOrthogonalProjection:
     * dimension=1000, numberVectors=50, multiplier=100, tolerance=1e-6.
     *
     * <p>For each valid projected vector i:
     * <ol>
     *   <li>x_i must be orthogonal to all other input vectors w_j (j != i),
     *       i.e. {@code dot(w_j, x_i) < errorAcceptable}.</li>
     *   <li>The inner product of x_i with w_i must equal the norm-squared of
     *       w_i: {@code dot(w_i, x_i) == dot(w_i, w_i)}.</li>
     * </ol>
     *
     * <p>Tolerance used in the test: {@code 1e-11} (matches C++ suite).
     */
    /** C++-name alias for `matrices.cpp::testOrthogonalProjection` — Java
     * splits the original umbrella C++ test into 3 per-property checks
     * (structure, identity, dropping near-dependent vectors). This alias
     * delegates to the structure variant which mirrors C++'s primary
     * assertion. */
    @Test
    public void testOrthogonalProjection() { testOrthogonalProjectionStructure(); }

    @Test
    public void testOrthogonalProjectionStructure() {
        final int dimension     = 1000;
        final int numberVectors = 50;
        final double multiplier = 100.0;
        final double tolerance  = 1e-6;

        // Build a random matrix using a simple deterministic PRNG
        // (same seed and sequence as QuantLib's MersenneTwisterUniformRng(seed=1)
        //  is approximated by java.util.Random(seed=1) — we test structural
        //  properties, not exact values)
        final java.util.Random rng = new java.util.Random(1L);
        final Matrix test = new Matrix(numberVectors, dimension);
        for (int i = 0; i < numberVectors; ++i) {
            for (int j = 0; j < dimension; ++j) {
                test.set(i, j, rng.nextDouble());
            }
        }

        final OrthogonalProjections projector =
                new OrthogonalProjections(test, multiplier, tolerance);

        int numberFailures = 0;
        int failuresTwo    = 0;

        final boolean[] valid = projector.validVectors();

        for (int i = 0; i < numberVectors; ++i) {
            if (valid[i]) {
                final double[] xi = projector.getVector(i);

                // Check xi is orthogonal to all w_j for j != i
                for (int j = 0; j < numberVectors; ++j) {
                    if (valid[j] && i != j) {
                        double dotProduct = 0.0;
                        for (int k = 0; k < dimension; ++k) {
                            dotProduct += test.get(j, k) * xi[k];
                        }
                        if (Math.abs(dotProduct) > ERROR_ACCEPTABLE) {
                            ++numberFailures;
                        }
                    }
                }

                // Check <xi, wi> == ||wi||^2
                double innerProductWithOriginal = 0.0;
                double normSq = 0.0;
                for (int j = 0; j < dimension; ++j) {
                    innerProductWithOriginal += xi[j] * test.get(i, j);
                    normSq += test.get(i, j) * test.get(i, j);
                }
                if (Math.abs(innerProductWithOriginal - normSq) > ERROR_ACCEPTABLE) {
                    ++failuresTwo;
                }
            }
        }

        assertEquals("OrthogonalProjections: orthogonality failures", 0, numberFailures);
        assertEquals("OrthogonalProjections: inner-product norm failures", 0, failuresTwo);
        assertTrue("OrthogonalProjections: should have at least one valid vector",
                projector.numberValidVectors() > 0);
    }

    /**
     * Small 3×3 exact case: three orthonormal basis vectors.
     * With multiplierCutOff = 1.5 and tolerance = 1e-12 all three should be valid,
     * and each projected vector equals the original (already orthogonal).
     */
    @Test
    public void testOrthogonalProjectionIdentity() {
        // Use the 3x3 identity as input — all vectors are orthonormal.
        final Matrix id = new Matrix(3, 3);
        id.set(0, 0, 1.0); id.set(0, 1, 0.0); id.set(0, 2, 0.0);
        id.set(1, 0, 0.0); id.set(1, 1, 1.0); id.set(1, 2, 0.0);
        id.set(2, 0, 0.0); id.set(2, 1, 0.0); id.set(2, 2, 1.0);

        final OrthogonalProjections proj = new OrthogonalProjections(id, 1e6, 1e-12);

        assertEquals("all 3 identity rows should be valid",
                3, proj.numberValidVectors());

        // Each projected vector should be numerically close to the original identity row
        final double tol = 1e-10;
        for (int i = 0; i < 3; ++i) {
            assertTrue("row " + i + " should be valid", proj.validVectors()[i]);
            final double[] xi = proj.getVector(i);
            for (int j = 0; j < 3; ++j) {
                final double expected = (i == j) ? 1.0 : 0.0;
                assertEquals("projected[" + i + "][" + j + "]",
                        expected, xi[j], tol);
            }
        }
    }

    /**
     * Checks that a nearly-linearly-dependent vector set causes at least one
     * vector to be marked invalid (due to norm falling below tolerance).
     */
    @Test
    public void testOrthogonalProjectionDropsNearlyDependent() {
        // Use two nearly identical rows: their G-S result will have near-zero norm.
        final Matrix m = new Matrix(2, 3);
        m.set(0, 0, 1.0); m.set(0, 1, 0.0); m.set(0, 2, 0.0);
        m.set(1, 0, 1.0); m.set(1, 1, 1e-15); m.set(1, 2, 0.0);

        final OrthogonalProjections proj = new OrthogonalProjections(m, 1e10, 1e-6);

        // At least one vector should be discarded
        assertTrue("near-linearly-dependent set should discard at least one vector",
                proj.numberValidVectors() < 2);
    }
}

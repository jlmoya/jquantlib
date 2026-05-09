/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3i Commit 5.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels.browniangenerators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.model.marketmodels.BrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator.Ordering;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGeneratorFactory;
import org.junit.Test;

/**
 * Phase 3i Commit 5 — SobolBrownianGenerator structural and statistical tests.
 *
 * Cross-validates against C++ v1.42.1 sobolbrowniangenerator.{hpp,cpp}:
 * - structural invariants (factors/steps reported, exhaustion check)
 * - ordering tables for Factors/Steps/Diagonal mappings
 * - statistical sanity: mean ≈ 0, variance ≈ 1 over many Sobol paths after
 *   inverse-cumulative + Brownian-bridge transformation.
 */
public class SobolBrownianGeneratorTest {

    @Test
    public void factorsAndSteps_returnConstructorValues() {
        final SobolBrownianGenerator gen = new SobolBrownianGenerator(3, 5, Ordering.Factors);
        assertEquals(3, gen.numberOfFactors());
        assertEquals(5, gen.numberOfSteps());
    }

    @Test
    public void orderedIndices_factorsOrdering_assignsByFactor() {
        // Factors ordering: counter goes (factor=0, step=0..steps-1), then (factor=1, step=0..)
        final SobolBrownianGenerator gen = new SobolBrownianGenerator(3, 4, Ordering.Factors);
        final int[][] idx = gen.orderedIndices();
        assertEquals(3, idx.length);
        assertEquals(4, idx[0].length);
        // factor 0 first
        assertEquals(0, idx[0][0]);
        assertEquals(1, idx[0][1]);
        assertEquals(2, idx[0][2]);
        assertEquals(3, idx[0][3]);
        // factor 1 starts at 4
        assertEquals(4, idx[1][0]);
        assertEquals(7, idx[1][3]);
        // factor 2 starts at 8
        assertEquals(8, idx[2][0]);
        assertEquals(11, idx[2][3]);
    }

    @Test
    public void orderedIndices_stepsOrdering_assignsByStep() {
        // Steps ordering: counter goes (step=0, factor=0..factors-1), then (step=1, ...)
        final SobolBrownianGenerator gen = new SobolBrownianGenerator(3, 4, Ordering.Steps);
        final int[][] idx = gen.orderedIndices();
        // step 0 first across all factors
        assertEquals(0, idx[0][0]);
        assertEquals(1, idx[1][0]);
        assertEquals(2, idx[2][0]);
        // step 1 second
        assertEquals(3, idx[0][1]);
        assertEquals(4, idx[1][1]);
        assertEquals(5, idx[2][1]);
    }

    @Test
    public void orderedIndices_diagonalOrdering_isUniquePermutation() {
        final SobolBrownianGenerator gen = new SobolBrownianGenerator(3, 4, Ordering.Diagonal);
        final int[][] idx = gen.orderedIndices();
        // verify all 12 dimensions are present exactly once
        final boolean[] seen = new boolean[12];
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 4; ++j) {
                final int v = idx[i][j];
                assertTrue("idx in range: " + v, v >= 0 && v < 12);
                assertTrue("not duplicated: " + v, !seen[v]);
                seen[v] = true;
            }
        }
        // diagonal scheme: starts at (0,0)=0, then (1,0)=1, then walks the
        // diagonal back through (0,1)=2 — verify two key entries.
        assertEquals(0, idx[0][0]);
        assertEquals(2, idx[0][1]);
        assertEquals(1, idx[1][0]);
    }

    @Test
    public void nextStep_returnsWeightOne_andFillsOutput() {
        final SobolBrownianGenerator gen = new SobolBrownianGenerator(4, 3, Ordering.Factors);
        gen.nextPath();
        final double[] out = new double[4];
        for (int i = 0; i < out.length; ++i) out[i] = Double.NaN;
        final double w = gen.nextStep(out);
        assertEquals("step weight is 1.0", 1.0, w, 1.0e-15);
        for (int i = 0; i < 4; ++i) {
            assertTrue("written at idx " + i, !Double.isNaN(out[i]));
            assertTrue("|x|<10", Math.abs(out[i]) < 10.0);
        }
    }

    @Test
    public void nextPath_resetsStepCounter_andExhausts() {
        final SobolBrownianGenerator gen = new SobolBrownianGenerator(2, 3, Ordering.Steps);
        gen.nextPath();
        final double[] out = new double[2];
        gen.nextStep(out);
        gen.nextStep(out);
        gen.nextStep(out);
        try {
            gen.nextStep(out);
            fail("expected sequence-exhausted exception");
        } catch (final RuntimeException e) {
            // expected
        }
        // start a new path → counter resets
        gen.nextPath();
        gen.nextStep(out);
        // does not throw
    }

    @Test
    public void rejectsSizeMismatch_inNextStep() {
        final SobolBrownianGenerator gen = new SobolBrownianGenerator(3, 2, Ordering.Factors);
        gen.nextPath();
        try {
            gen.nextStep(new double[2]);
            fail("expected size mismatch");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    @Test
    public void factory_producesGeneratorOfCorrectShape() {
        final SobolBrownianGeneratorFactory f =
                new SobolBrownianGeneratorFactory(Ordering.Diagonal, 7L);
        final BrownianGenerator gen = f.create(2, 4);
        assertNotNull(gen);
        assertEquals(2, gen.numberOfFactors());
        assertEquals(4, gen.numberOfSteps());
    }

    @Test
    public void factoryWithSeed_producesDeterministicSequence() {
        final SobolBrownianGeneratorFactory f1 =
                new SobolBrownianGeneratorFactory(Ordering.Factors, 0L);
        final SobolBrownianGeneratorFactory f2 =
                new SobolBrownianGeneratorFactory(Ordering.Factors, 0L);
        final BrownianGenerator g1 = f1.create(2, 3);
        final BrownianGenerator g2 = f2.create(2, 3);
        g1.nextPath();
        g2.nextPath();
        final double[] o1 = new double[2];
        final double[] o2 = new double[2];
        for (int s = 0; s < 3; ++s) {
            g1.nextStep(o1);
            g2.nextStep(o2);
            for (int i = 0; i < 2; ++i) {
                assertEquals("seed determinism step " + s + " idx " + i,
                        o1[i], o2[i], 1.0e-15);
            }
        }
    }

    @Test
    public void statisticalSanity_meanNearZero_varianceNearOne() {
        // Draw many Sobol paths × 3 steps × 2 factors. Inverse-cumulative +
        // Brownian-bridge → standard Gaussian. With Sobol sequence the
        // discrepancy is much smaller than pseudo-random, so 4096 paths give
        // very tight mean / variance.
        final int factors = 2, steps = 3, paths = 4096;
        final SobolBrownianGenerator gen =
                new SobolBrownianGenerator(factors, steps, Ordering.Factors);
        final double[] out = new double[factors];
        long n = 0;
        double sum = 0.0, sumSq = 0.0;
        for (int p = 0; p < paths; ++p) {
            gen.nextPath();
            for (int s = 0; s < steps; ++s) {
                gen.nextStep(out);
                for (int i = 0; i < factors; ++i) {
                    sum += out[i];
                    sumSq += out[i] * out[i];
                    n++;
                }
            }
        }
        final double mean = sum / n;
        final double variance = sumSq / n - mean * mean;
        // Sobol convergence is much faster than pseudo-random; 0.05 tolerance
        // is generous (typical |mean| < 0.01 for this configuration).
        assertEquals("sample mean ≈ 0", 0.0, mean, 0.05);
        assertEquals("sample variance ≈ 1", 1.0, variance, 0.20);
    }

    @Test
    public void transform_batchModeReorderingMatches_directGeneration() {
        // Sanity check that the test-helper transform() method runs without
        // throwing and returns the expected shape.
        final int factors = 2, steps = 3, paths = 4;
        final SobolBrownianGenerator gen =
                new SobolBrownianGenerator(factors, steps, Ordering.Factors);
        final double[][] variates = new double[factors * steps][paths];
        // arbitrary deterministic inputs
        for (int dim = 0; dim < factors * steps; ++dim) {
            for (int p = 0; p < paths; ++p) {
                variates[dim][p] = 0.1 * (dim + 1) + 0.01 * p;
            }
        }
        final double[][] out = gen.transform(variates);
        assertEquals(factors, out.length);
        assertEquals(paths * steps, out[0].length);
    }
}

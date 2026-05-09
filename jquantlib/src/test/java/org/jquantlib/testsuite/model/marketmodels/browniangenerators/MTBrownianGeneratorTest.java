/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3h Track B.7.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels.browniangenerators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.model.marketmodels.BrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.MTBrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.MTBrownianGeneratorFactory;
import org.junit.Test;

/**
 * Phase 3h B.7 — MTBrownianGenerator tests.
 *
 * Cross-validates against C++ v1.42.1
 * {@code ql/models/marketmodels/browniangenerators/mtbrowniangenerator.cpp}:
 * structural invariants (factors/steps reported, weight=1.0, exhaustion check)
 * and statistical sanity (mean ≈ 0, variance ≈ 1 over many samples).
 */
public class MTBrownianGeneratorTest {

    @Test
    public void numberOfFactorsAndSteps_returnConstructorValues() {
        final MTBrownianGenerator gen = new MTBrownianGenerator(3, 7, 42L);
        assertEquals(3, gen.numberOfFactors());
        assertEquals(7, gen.numberOfSteps());
    }

    @Test
    public void nextStep_returnsWeightOne_andFillsArray() {
        final MTBrownianGenerator gen = new MTBrownianGenerator(4, 5, 1L);
        gen.nextPath();
        final double[] out = new double[4];
        for (int i = 0; i < out.length; i++) {
            out[i] = Double.NaN;
        }
        final double w = gen.nextStep(out);
        assertEquals("step weight is 1.0 for pseudo-random", 1.0, w, 1e-15);
        for (int i = 0; i < out.length; i++) {
            assertTrue("Gaussian was written at index " + i, !Double.isNaN(out[i]));
            // Gaussian variates are within ~6 sigma in a single draw with high prob
            assertTrue("|x|<10", Math.abs(out[i]) < 10.0);
        }
    }

    @Test
    public void nextPath_resetsStepCounter() {
        final MTBrownianGenerator gen = new MTBrownianGenerator(2, 3, 0L);
        gen.nextPath();
        final double[] out = new double[2];
        // exhaust three steps
        gen.nextStep(out);
        gen.nextStep(out);
        gen.nextStep(out);
        // 4th step on same path → must fail
        try {
            gen.nextStep(out);
            fail("expected exhaustion exception");
        } catch (final RuntimeException e) {
            // expected
        }
        // start a new path → counter reset
        gen.nextPath();
        gen.nextStep(out);
        gen.nextStep(out);
        gen.nextStep(out);
        // does not throw
    }

    @Test
    public void factory_producesGeneratorOfCorrectShape() {
        final MTBrownianGeneratorFactory factory = new MTBrownianGeneratorFactory(123L);
        final BrownianGenerator gen = factory.create(5, 10);
        assertEquals(5, gen.numberOfFactors());
        assertEquals(10, gen.numberOfSteps());
    }

    @Test
    public void factoryWithSeed_producesDeterministicSequence() {
        final MTBrownianGeneratorFactory f1 = new MTBrownianGeneratorFactory(7L);
        final MTBrownianGeneratorFactory f2 = new MTBrownianGeneratorFactory(7L);
        final BrownianGenerator g1 = f1.create(3, 4);
        final BrownianGenerator g2 = f2.create(3, 4);
        g1.nextPath();
        g2.nextPath();
        final double[] o1 = new double[3];
        final double[] o2 = new double[3];
        for (int s = 0; s < 4; ++s) {
            g1.nextStep(o1);
            g2.nextStep(o2);
            for (int i = 0; i < 3; i++) {
                assertEquals("seed determinism step " + s + " idx " + i,
                        o1[i], o2[i], 1.0e-15);
            }
        }
    }

    @Test
    public void statisticalSanity_meanNearZero_varianceNearOne() {
        // Draw 10 000 paths × 4 steps × 3 factors = 120 000 Gaussian variates.
        // Sample mean should be near 0 and variance near 1.
        // Standard error of mean ≈ 1/sqrt(N) ≈ 0.0029, so 4 SE ≈ 0.012.
        final int factors = 3, steps = 4, paths = 10_000;
        final MTBrownianGenerator gen = new MTBrownianGenerator(factors, steps, 0L);
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
        assertEquals("sample mean ≈ 0", 0.0, mean, 0.02);
        assertEquals("sample variance ≈ 1", 1.0, variance, 0.02);
    }

    @Test
    public void rejectsSizeMismatch_inNextStep() {
        final MTBrownianGenerator gen = new MTBrownianGenerator(3, 2, 0L);
        gen.nextPath();
        try {
            gen.nextStep(new double[2]);
            fail("expected size mismatch");
        } catch (final RuntimeException e) {
            // expected
        }
    }
}

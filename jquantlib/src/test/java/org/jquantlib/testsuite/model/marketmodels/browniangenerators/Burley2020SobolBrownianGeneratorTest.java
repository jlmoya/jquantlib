/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 2 L4-A.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.model.marketmodels.browniangenerators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.Burley2020SobolRsg;
import org.jquantlib.math.randomnumbers.SobolRsg;
import org.jquantlib.methods.montecarlo.BrownianBridge;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.marketmodels.BrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.Burley2020SobolBrownianGenerator;
import org.jquantlib.model.marketmodels.browniangenerators.Burley2020SobolBrownianGeneratorFactory;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator.Ordering;
import org.junit.Test;

/**
 * Phase 2 L4-A — {@link Burley2020SobolBrownianGenerator} + factory tests.
 *
 * <p>Cross-validation strategy: the algorithm is the same as
 * {@code SobolBrownianGenerator} but with {@link Burley2020SobolRsg} as the uniform source.
 * The underlying {@code Burley2020SobolRsg} is independently C++-bit-exact cross-validated
 * at L1 against {@code burley2020sobolrsg.cpp} v1.42.1 (see {@code Burley2020SobolRsgTest}).
 *
 * <p>Therefore this test bit-exactly reproduces the algorithm in plain Java
 * (Burley2020SobolRsg → InverseCumulativeNormal → BrownianBridge per factor)
 * and asserts that {@link Burley2020SobolBrownianGenerator} produces the same
 * bridged variates. Any divergence means the generator pipeline has drifted
 * from the C++ logic.
 */
public class Burley2020SobolBrownianGeneratorTest {

    private static final double EPS = 1.0e-15;

    @Test
    public void shapeAndStructuralInvariants() {
        final Burley2020SobolBrownianGenerator gen = new Burley2020SobolBrownianGenerator(3, 5, Ordering.Factors);
        assertEquals(3, gen.numberOfFactors());
        assertEquals(5, gen.numberOfSteps());
    }

    @Test
    public void factoryProducesGeneratorOfCorrectShape() {
        final Burley2020SobolBrownianGeneratorFactory f =
                new Burley2020SobolBrownianGeneratorFactory(Ordering.Diagonal, 42L);
        final BrownianGenerator gen = f.create(2, 4);
        assertNotNull(gen);
        assertEquals(2, gen.numberOfFactors());
        assertEquals(4, gen.numberOfSteps());
    }

    @Test
    public void nextStepReturnsWeightOneAndFillsOutput() {
        final Burley2020SobolBrownianGenerator gen = new Burley2020SobolBrownianGenerator(4, 3, Ordering.Factors);
        gen.nextPath();
        final double[] out = new double[4];
        for ( int i = 0; i < out.length; ++i ) {
            out[i] = Double.NaN;
        }
        final double w = gen.nextStep(out);
        assertEquals("step weight is 1.0", 1.0, w, EPS);
        for ( int i = 0; i < 4; ++i ) {
            assertTrue("written at idx " + i, !Double.isNaN(out[i]));
        }
    }

    @Test
    public void nextPathResetsStepCounterAndExhausts() {
        final Burley2020SobolBrownianGenerator gen = new Burley2020SobolBrownianGenerator(2, 3, Ordering.Steps);
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
        gen.nextPath();
        gen.nextStep(out); // does not throw
    }

    @Test
    public void rejectsSizeMismatchInNextStep() {
        final Burley2020SobolBrownianGenerator gen = new Burley2020SobolBrownianGenerator(3, 2, Ordering.Factors);
        gen.nextPath();
        try {
            gen.nextStep(new double[2]);
            fail("expected size mismatch");
        } catch (final RuntimeException e) {
            // expected
        }
    }

    /**
     * Bit-exact algorithmic invariant: the generator's bridged variates must equal those produced by manually running
     * Burley2020SobolRsg → InverseCumulativeNormal → BrownianBridge with the same ordering. Since
     * {@code Burley2020SobolRsg} is C++-bit-exact at L1, this transitively asserts C++ equivalence of the bridge
     * pipeline at seed=42, scrambleSeed=43.
     */
    @Test
    public void seedEquivalenceWithManualBridgePipeline_Factors() {
        assertEquivalentToManualPipeline(2, 4, Ordering.Factors, 42L, 43L, 5);
    }

    @Test
    public void seedEquivalenceWithManualBridgePipeline_Steps() {
        assertEquivalentToManualPipeline(3, 3, Ordering.Steps, 42L, 43L, 4);
    }

    @Test
    public void seedEquivalenceWithManualBridgePipeline_Diagonal() {
        assertEquivalentToManualPipeline(3, 4, Ordering.Diagonal, 42L, 43L, 4);
    }

    @Test
    public void seedEquivalenceWithManualBridgePipeline_NonDefaultSeed() {
        // Confirms seed plumbing flows through correctly.
        assertEquivalentToManualPipeline(2, 3, Ordering.Diagonal, 7L, 13L, 3);
    }

    @Test
    public void factoryWithSeedProducesDeterministicSequence() {
        final Burley2020SobolBrownianGeneratorFactory f1 =
                new Burley2020SobolBrownianGeneratorFactory(Ordering.Factors, 42L);
        final Burley2020SobolBrownianGeneratorFactory f2 =
                new Burley2020SobolBrownianGeneratorFactory(Ordering.Factors, 42L);
        final BrownianGenerator g1 = f1.create(2, 3);
        final BrownianGenerator g2 = f2.create(2, 3);
        g1.nextPath();
        g2.nextPath();
        final double[] o1 = new double[2];
        final double[] o2 = new double[2];
        for ( int s = 0; s < 3; ++s ) {
            g1.nextStep(o1);
            g2.nextStep(o2);
            for ( int i = 0; i < 2; ++i ) {
                assertEquals("seed determinism step " + s + " idx " + i, o1[i], o2[i], EPS);
            }
        }
    }

    @Test
    public void differentScrambleSeedsProduceDifferentSequences() {
        final Burley2020SobolBrownianGenerator g1 =
                new Burley2020SobolBrownianGenerator(2, 3, Ordering.Factors, 42L,
                        SobolRsg.DirectionIntegers.Jaeckel, 43L);
        final Burley2020SobolBrownianGenerator g2 =
                new Burley2020SobolBrownianGenerator(2, 3, Ordering.Factors, 42L,
                        SobolRsg.DirectionIntegers.Jaeckel, 99L);
        g1.nextPath();
        g2.nextPath();
        final double[] o1 = new double[2];
        final double[] o2 = new double[2];
        g1.nextStep(o1);
        g2.nextStep(o2);
        boolean differ = false;
        for ( int i = 0; i < 2; ++i ) {
            if ( o1[i] != o2[i] ) {
                differ = true;
                break;
            }
        }
        assertTrue("different scrambleSeed must produce different sequences", differ);
    }

    @Test
    public void statisticalSanityMeanNearZeroVarianceNearOne() {
        // Burley scrambled Sobol → Gaussian → bridged. Mean ≈ 0, variance ≈ 1.
        final int factors = 2, steps = 3, paths = 4096;
        final Burley2020SobolBrownianGenerator gen =
                new Burley2020SobolBrownianGenerator(factors, steps, Ordering.Factors);
        final double[] out = new double[factors];
        long n = 0;
        double sum = 0.0, sumSq = 0.0;
        for ( int p = 0; p < paths; ++p ) {
            gen.nextPath();
            for ( int s = 0; s < steps; ++s ) {
                gen.nextStep(out);
                for ( int i = 0; i < factors; ++i ) {
                    sum += out[i];
                    sumSq += out[i] * out[i];
                    n++;
                }
            }
        }
        final double mean = sum / n;
        final double variance = sumSq / n - mean * mean;
        assertEquals("sample mean ≈ 0", 0.0, mean, 0.05);
        assertEquals("sample variance ≈ 1", 1.0, variance, 0.20);
    }

    // --- Helpers ---

    /**
     * Manually run Burley2020SobolRsg → ICN → BrownianBridge with the chosen ordering, and assert the generator
     * produces bit-exact identical bridged variates over {@code samples} paths.
     */
    private void assertEquivalentToManualPipeline(final int factors, final int steps, final Ordering ordering,
            final long seed, final long scrambleSeed, final int samples) {
        final Burley2020SobolBrownianGenerator gen =
                new Burley2020SobolBrownianGenerator(factors, steps, ordering, seed,
                        SobolRsg.DirectionIntegers.Jaeckel, scrambleSeed);
        final int[][] orderedIndices = gen.orderedIndices();

        final Burley2020SobolRsg rsg = new Burley2020SobolRsg(factors * steps, seed,
                SobolRsg.DirectionIntegers.Jaeckel, scrambleSeed);
        final InverseCumulativeNormal icn = new InverseCumulativeNormal();
        final BrownianBridge bridge = new BrownianBridge(steps);
        final double[] permuted = new double[steps];
        final double[] bridged = new double[steps];

        final double[] out = new double[factors];

        for ( int p = 0; p < samples; ++p ) {
            // Reference path: replicate algorithm.
            final Sample< double[] > uniform = rsg.nextSequence();
            final double[] u = uniform.value();
            final double[] gaussian = new double[u.length];
            for ( int i = 0; i < u.length; ++i ) {
                gaussian[i] = icn.op(u[i]);
            }
            final double[][] refBridged = new double[factors][steps];
            for ( int i = 0; i < factors; ++i ) {
                for ( int s = 0; s < steps; ++s ) {
                    permuted[s] = gaussian[orderedIndices[i][s]];
                }
                bridge.transform(permuted, bridged);
                System.arraycopy(bridged, 0, refBridged[i], 0, steps);
            }

            // Generator under test.
            gen.nextPath();
            for ( int s = 0; s < steps; ++s ) {
                gen.nextStep(out);
                for ( int i = 0; i < factors; ++i ) {
                    assertEquals(
                            String.format("path=%d step=%d factor=%d", p, s, i),
                            refBridged[i][s], out[i], EPS);
                }
            }
        }
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.math.PolarStudentTRng;
import org.jquantlib.experimental.math.ZigguratRng;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

/**
 * Phase 4k tests for {@link ZigguratRng} and {@link PolarStudentTRng}.
 *
 * <p>Validates basic statistical sanity of the produced variates. Does not
 * attempt bit-exact reproduction of the C++ implementation since that would
 * require matching the full Marsaglia-Tsang constants and the MT19937 32-bit
 * stream byte-for-byte; instead asserts mean ~ 0 and variance ~ 1.
 */
public class ZigguratRngTest {

    @Test
    public void testZigguratMeanAndVarianceAreReasonable() {
        final ZigguratRng rng = new ZigguratRng(12345L);
        final int n = 50000;
        double sum = 0.0;
        double sumSq = 0.0;
        for (int i = 0; i < n; ++i) {
            final double x = rng.next().value();
            sum += x;
            sumSq += x * x;
        }
        final double mean = sum / n;
        final double var = sumSq / n - mean * mean;
        assertTrue("Mean " + mean + " should be near 0", Math.abs(mean) < 0.05);
        assertTrue("Variance " + var + " should be near 1",
                Math.abs(var - 1.0) < 0.05);
    }

    @Test
    public void testZigguratProducesFiniteSamples() {
        final ZigguratRng rng = new ZigguratRng(42L);
        for (int i = 0; i < 1000; ++i) {
            final double x = rng.next().value();
            assertTrue("finite", !Double.isNaN(x) && !Double.isInfinite(x));
        }
    }

    @Test
    public void testPolarStudentTProducesSamples() {
        final MersenneTwisterUniformRng urng = new MersenneTwisterUniformRng(7L);
        final PolarStudentTRng prng = new PolarStudentTRng(5.0, urng);
        for (int i = 0; i < 500; ++i) {
            final double x = prng.next().value();
            assertTrue("finite", !Double.isNaN(x) && !Double.isInfinite(x));
        }
    }

    @Test
    public void testPolarStudentTHighDofResemblesNormal() {
        // For very high degrees of freedom, polar-T should resemble standard normal
        final MersenneTwisterUniformRng urng = new MersenneTwisterUniformRng(7L);
        final PolarStudentTRng prng = new PolarStudentTRng(100.0, urng);
        final int n = 10000;
        double sum = 0.0;
        double sumSq = 0.0;
        for (int i = 0; i < n; ++i) {
            final double x = prng.next().value();
            sum += x;
            sumSq += x * x;
        }
        final double mean = sum / n;
        final double var = sumSq / n - mean * mean;
        assertTrue("Mean " + mean + " should be near 0", Math.abs(mean) < 0.1);
        // T-variance with dof=100 ≈ 100/98 ≈ 1.02
        assertTrue("Variance " + var + " should be near 1.02", Math.abs(var - 1.02) < 0.15);
    }
}

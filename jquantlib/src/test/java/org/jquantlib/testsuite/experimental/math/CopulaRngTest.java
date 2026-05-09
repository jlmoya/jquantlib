/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.experimental.math.ClaytonCopulaRng;
import org.jquantlib.experimental.math.FarlieGumbelMorgensternCopulaRng;
import org.jquantlib.experimental.math.FrankCopulaRng;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.methods.montecarlo.Sample;
import org.junit.Test;

/**
 * Phase 4k smoke tests for the copula random-number generators in
 * {@code org.jquantlib.experimental.math}.
 *
 * <p>Each generator should produce 2-tuples in {@code (0,1)} when fed with a
 * uniform stream. The seeded MT19937 stream is deterministic so the test only
 * exercises basic invariants (bounds, dimension, weight) and parameter
 * validation rather than reproducing C++ bit-exact draws.
 */
public class CopulaRngTest {

    @Test
    public void testClaytonProducesValidSamples() {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(42L);
        final ClaytonCopulaRng clayton = new ClaytonCopulaRng(rng, 2.0);
        for (int i = 0; i < 100; ++i) {
            final Sample<double[]> s = clayton.next();
            assertNotNull(s);
            assertEquals("dim", 2, s.value().length);
            assertTrue("u1 in (0,1)", s.value()[0] > 0.0 && s.value()[0] < 1.0);
            assertTrue("u2 in (0,1)", s.value()[1] > 0.0 && s.value()[1] < 1.0);
            assertTrue("weight positive", s.weight() > 0.0);
        }
    }

    @Test
    public void testClaytonRejectsBadTheta() {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1L);
        try {
            new ClaytonCopulaRng(rng, 0.0);
            fail("Expected exception for theta == 0");
        } catch (final Exception e) {
            // expected
        }
        try {
            new ClaytonCopulaRng(rng, -2.0);
            fail("Expected exception for theta < -1");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void testFrankProducesValidSamples() {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(42L);
        final FrankCopulaRng frank = new FrankCopulaRng(rng, 1.5);
        for (int i = 0; i < 100; ++i) {
            final Sample<double[]> s = frank.next();
            assertEquals(2, s.value().length);
            assertTrue("u1 in (0,1)", s.value()[0] > 0.0 && s.value()[0] < 1.0);
            // Frank copula u2 is in (0,1) for theta != 0 inputs
            assertTrue("u2 finite", !Double.isNaN(s.value()[1]) && !Double.isInfinite(s.value()[1]));
        }
    }

    @Test
    public void testFrankRejectsZeroTheta() {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1L);
        try {
            new FrankCopulaRng(rng, 0.0);
            fail("Expected exception for theta == 0");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void testFgmProducesValidSamples() {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(42L);
        final FarlieGumbelMorgensternCopulaRng fgm =
                new FarlieGumbelMorgensternCopulaRng(rng, 0.5);
        for (int i = 0; i < 100; ++i) {
            final Sample<double[]> s = fgm.next();
            assertEquals(2, s.value().length);
            assertTrue("u1 in (0,1)", s.value()[0] > 0.0 && s.value()[0] < 1.0);
            // FGM second variate by construction lies in [0,1]
            assertTrue("u2 finite", !Double.isNaN(s.value()[1]) && !Double.isInfinite(s.value()[1]));
        }
    }

    @Test
    public void testFgmRejectsBadTheta() {
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1L);
        try {
            new FarlieGumbelMorgensternCopulaRng(rng, 1.5);
            fail("Expected exception for theta > 1");
        } catch (final Exception e) {
            // expected
        }
        try {
            new FarlieGumbelMorgensternCopulaRng(rng, -1.5);
            fail("Expected exception for theta < -1");
        } catch (final Exception e) {
            // expected
        }
    }
}

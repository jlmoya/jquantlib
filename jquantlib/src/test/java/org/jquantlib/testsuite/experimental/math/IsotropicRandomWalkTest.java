/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.experimental.math.IsotropicRandomWalk;
import org.jquantlib.experimental.math.LevyFlightDistribution;
import org.junit.Test;

/**
 * Phase 4k tests for {@link IsotropicRandomWalk}.
 */
public class IsotropicRandomWalkTest {

    @Test
    public void test1DProducesValidStep() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(1.0, 1.5);
        final IsotropicRandomWalk walk =
                new IsotropicRandomWalk(lfd::draw, 1, 42L);
        final double[] step = new double[1];
        for (int i = 0; i < 50; ++i) {
            walk.nextReal(step);
            assertTrue("step finite", !Double.isNaN(step[0]) && !Double.isInfinite(step[0]));
        }
    }

    @Test
    public void test2DProducesValidStep() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(0.5, 1.5);
        final IsotropicRandomWalk walk =
                new IsotropicRandomWalk(lfd::draw, 2, 1L);
        final double[] step = new double[2];
        for (int i = 0; i < 50; ++i) {
            walk.nextReal(step);
            assertTrue("step[0] finite", !Double.isNaN(step[0]) && !Double.isInfinite(step[0]));
            assertTrue("step[1] finite", !Double.isNaN(step[1]) && !Double.isInfinite(step[1]));
        }
    }

    @Test
    public void test3DProducesValidStep() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(0.5, 1.5);
        final IsotropicRandomWalk walk =
                new IsotropicRandomWalk(lfd::draw, 3, 1L);
        final double[] step = new double[3];
        for (int i = 0; i < 50; ++i) {
            walk.nextReal(step);
            for (int j = 0; j < 3; ++j) {
                assertTrue("step[" + j + "] finite",
                        !Double.isNaN(step[j]) && !Double.isInfinite(step[j]));
            }
        }
    }

    @Test
    public void testRejectsBadWeights() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(1.0, 1.5);
        try {
            new IsotropicRandomWalk(lfd::draw, 2, new double[] { 1.0 }, 1L);
            fail("Expected exception for invalid weights size");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void testWeightsApplied() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(1.0, 2.0);
        final IsotropicRandomWalk walk =
                new IsotropicRandomWalk(lfd::draw, 2, new double[] { 0.0, 0.0 }, 1L);
        final double[] step = new double[2];
        walk.nextReal(step);
        // Zero weights should produce zero step in both dimensions
        assertTrue("step[0] zero with zero weight", Math.abs(step[0]) < 1.0e-12);
        assertTrue("step[1] zero with zero weight", Math.abs(step[1]) < 1.0e-12);
    }
}

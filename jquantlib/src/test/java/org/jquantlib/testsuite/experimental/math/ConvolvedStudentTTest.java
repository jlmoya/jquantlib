/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.jquantlib.experimental.math.CumulativeBehrensFisher;
import org.jquantlib.experimental.math.InverseCumulativeBehrensFisher;
import org.junit.Test;

/**
 * Phase 4k tests for {@link CumulativeBehrensFisher} and
 * {@link InverseCumulativeBehrensFisher}.
 *
 * <p>Validates the basic invariants of the convolution of Student-T
 * distributions: monotonicity, symmetry around zero, and roundtrip
 * cumulative-inverse.
 */
public class ConvolvedStudentTTest {

    @Test
    public void testCumulativeAtZeroIsHalf() {
        final CumulativeBehrensFisher c = new CumulativeBehrensFisher(
                Arrays.asList(3, 5),
                Arrays.asList(0.5, 0.5));
        // Symmetric distribution => CDF(0) = 0.5
        assertEquals(0.5, c.op(0.0), 1.0e-12);
    }

    @Test
    public void testCumulativeMonotonicIncreasing() {
        final CumulativeBehrensFisher c = new CumulativeBehrensFisher(
                Arrays.asList(3, 5),
                Arrays.asList(0.5, 0.5));
        double prev = c.op(-5.0);
        for (double x = -4.5; x <= 5.0; x += 0.5) {
            final double cur = c.op(x);
            assertTrue("Monotonic at x=" + x + " prev=" + prev + " cur=" + cur,
                    cur >= prev);
            prev = cur;
        }
    }

    @Test
    public void testCumulativeSymmetric() {
        final CumulativeBehrensFisher c = new CumulativeBehrensFisher(
                Arrays.asList(3, 5),
                Arrays.asList(0.5, 0.5));
        // F(-x) + F(x) = 1 (symmetric distribution)
        for (double x = 0.5; x <= 3.0; x += 0.5) {
            final double sum = c.op(x) + c.op(-x);
            assertEquals("Symmetry at x=" + x, 1.0, sum, 1.0e-10);
        }
    }

    @Test
    public void testDensityNonNegative() {
        final CumulativeBehrensFisher c = new CumulativeBehrensFisher(
                Arrays.asList(3, 5),
                Arrays.asList(0.5, 0.5));
        for (double x = -3.0; x <= 3.0; x += 0.5) {
            assertTrue("density positive at x=" + x, c.density(x) >= 0.0);
        }
    }

    @Test
    public void testRejectsEvenDegree() {
        try {
            new CumulativeBehrensFisher(Arrays.asList(2), Arrays.asList(1.0));
            fail("Expected exception for even degree of freedom");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void testInverseAtMedianIsZero() {
        final InverseCumulativeBehrensFisher inv =
                new InverseCumulativeBehrensFisher(
                        Arrays.asList(3, 5),
                        Arrays.asList(0.5, 0.5));
        assertEquals(0.0, inv.op(0.5), 1.0e-12);
    }

    @Test
    public void testInverseRoundTrip() {
        final CumulativeBehrensFisher c = new CumulativeBehrensFisher(
                Arrays.asList(3, 5),
                Arrays.asList(0.5, 0.5));
        final InverseCumulativeBehrensFisher inv =
                new InverseCumulativeBehrensFisher(
                        Arrays.asList(3, 5),
                        Arrays.asList(0.5, 0.5));
        for (double q = 0.1; q <= 0.91; q += 0.1) {
            final double x = inv.op(q);
            final double back = c.op(x);
            assertEquals("Round-trip at q=" + q, q, back, 1.0e-5);
        }
    }
}

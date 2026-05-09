/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.math.distributions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.math.distributions.CumulativeStudentDistribution;
import org.jquantlib.math.distributions.InverseCumulativeStudent;
import org.jquantlib.math.distributions.StudentDistribution;
import org.junit.Test;

/**
 * Phase 4m.6 tests for {@link StudentDistribution},
 * {@link CumulativeStudentDistribution} and {@link InverseCumulativeStudent}.
 *
 * <p>Cross-validation: closed-form Gamma identities (small-n exact values)
 * + symmetry properties of the Student-T distribution. Reference values:
 * QuantLib v1.42.1 {@code ql/math/distributions/studenttdistribution.{hpp,cpp}}.
 */
public class StudentDistributionTest {

    private static final double TIGHT = 1.0e-12;
    private static final double LOOSE = 1.0e-8;

    @Test
    public void densityAtZeroN5() {
        // f(0; n=5) = Gamma(3) / (sqrt(5*pi) * Gamma(5/2))
        //           = 2 / (sqrt(5*pi) * (3/4)*sqrt(pi))
        //           = 8 / (3 * pi * sqrt(5))
        final StudentDistribution d = new StudentDistribution(5);
        final double expected = 8.0 / (3.0 * Math.PI * Math.sqrt(5.0));
        assertEquals(expected, d.op(0.0), TIGHT);
    }

    @Test
    public void densitySymmetric() {
        final StudentDistribution d = new StudentDistribution(7);
        for (double x = 0.1; x < 5.0; x += 0.7) {
            assertEquals("symmetric x=" + x, d.op(x), d.op(-x), TIGHT);
        }
    }

    @Test
    public void densityRequiresPositiveDof() {
        try {
            new StudentDistribution(0);
            fail("expected exception for n=0");
        } catch (final Exception e) {
            // expected
        }
        try {
            new StudentDistribution(-3);
            fail("expected exception for n<0");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void cumulativeAtZeroIsHalf() {
        // F(0) = 1/2 by symmetry
        for (final int n : new int[] { 1, 3, 5, 10, 30 }) {
            final CumulativeStudentDistribution f = new CumulativeStudentDistribution(n);
            assertEquals("n=" + n, 0.5, f.op(0.0), TIGHT);
        }
    }

    @Test
    public void cumulativeAtTailsApproachesUnitMassAndZero() {
        final CumulativeStudentDistribution f = new CumulativeStudentDistribution(5);
        // F(-inf) -> 0, F(+inf) -> 1; at +/-100 (very deep tail) we should
        // be effectively at 0 / 1. Allow LOOSE tolerance for any iteration noise.
        assertTrue("F(-100) too high: " + f.op(-100.0), f.op(-100.0) < 1.0e-6);
        assertTrue("F(+100) too low: "  + f.op(100.0), f.op(100.0)  > 1.0 - 1.0e-6);
    }

    @Test
    public void cumulativeMonotonic() {
        final CumulativeStudentDistribution f = new CumulativeStudentDistribution(7);
        double prev = f.op(-3.0);
        for (double x = -2.5; x <= 3.0; x += 0.5) {
            final double v = f.op(x);
            assertTrue("not monotonic at x=" + x + " (prev=" + prev + ", v=" + v + ")", v >= prev);
            prev = v;
        }
    }

    @Test
    public void inverseRoundTrip() {
        // Newton-based inverse tolerates 1e-6 by default; look at multiple n.
        for (final int n : new int[] { 3, 5, 9 }) {
            final CumulativeStudentDistribution f = new CumulativeStudentDistribution(n);
            final InverseCumulativeStudent inv = new InverseCumulativeStudent(n, 1.0e-10, 100);
            for (final double x : new double[] { -1.5, -0.5, 0.0, 0.5, 1.5 }) {
                final double p = f.op(x);
                final double back = inv.op(p);
                assertEquals("n=" + n + " x=" + x, x, back, LOOSE);
            }
        }
    }

    @Test
    public void inverseRequiresProbabilityInRange() {
        final InverseCumulativeStudent inv = new InverseCumulativeStudent(5);
        try {
            inv.op(-0.1);
            fail("expected exception for p<0");
        } catch (final Exception e) {
            // expected
        }
        try {
            inv.op(1.1);
            fail("expected exception for p>1");
        } catch (final Exception e) {
            // expected
        }
    }
}

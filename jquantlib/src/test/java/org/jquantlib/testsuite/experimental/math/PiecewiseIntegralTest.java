/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.jquantlib.experimental.math.PiecewiseIntegral;
import org.jquantlib.math.integrals.SimpsonIntegral;
import org.junit.Test;

/**
 * Phase 4k tests for {@link PiecewiseIntegral}.
 */
public class PiecewiseIntegralTest {

    @Test
    public void testNoCriticalPointsBehavesLikeUnderlying() {
        final SimpsonIntegral simpson = new SimpsonIntegral(1.0e-8, 1000);
        final PiecewiseIntegral pwi = new PiecewiseIntegral(simpson, Collections.emptyList());
        // integral of f(x) = x^2 from 0 to 1 is 1/3
        final double result = pwi.op(x -> x * x, 0.0, 1.0);
        assertEquals(1.0 / 3.0, result, 1.0e-6);
    }

    @Test
    public void testCriticalPointSplitsIntegral() {
        // Integrate f(x) = 1 over [-1, 1] with critical point at x=0
        // Result should still be ~2 (integration excludes a tiny eps region around 0)
        final SimpsonIntegral simpson = new SimpsonIntegral(1.0e-8, 1000);
        final PiecewiseIntegral pwi = new PiecewiseIntegral(simpson, Arrays.asList(0.0));
        final double result = pwi.op(x -> 1.0, -1.0, 1.0);
        assertEquals(2.0, result, 1.0e-6);
    }

    @Test
    public void testCriticalPointOutsideIntegrationRange() {
        // f = 1, integrate [0,1], critical point at 5.0
        final SimpsonIntegral simpson = new SimpsonIntegral(1.0e-8, 1000);
        final PiecewiseIntegral pwi = new PiecewiseIntegral(simpson, Arrays.asList(5.0));
        final double result = pwi.op(x -> 1.0, 0.0, 1.0);
        assertEquals(1.0, result, 1.0e-8);
    }
}

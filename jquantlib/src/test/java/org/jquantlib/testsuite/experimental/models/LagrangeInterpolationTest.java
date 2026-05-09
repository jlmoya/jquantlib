/*
 Copyright (C) 2016 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.math.interpolations.LagrangeInterpolation;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Phase 4j tests for {@link LagrangeInterpolation}.
 *
 * <p>Validates barycentric Lagrange interpolation on simple polynomial functions.
 *
 * @author Phase 4j port
 */
public class LagrangeInterpolationTest {

    private static final double TOL = 1e-10;

    /**
     * Lagrange interpolation should reproduce the y-values exactly at nodes.
     */
    @Test
    public void testExactAtNodes() {
        final double[] x = {0.0, 1.0, 2.0, 3.0, 4.0};
        final double[] y = {1.0, 4.0, 9.0, 16.0, 25.0};  // (x+1)^2

        final LagrangeInterpolation interp = new LagrangeInterpolation(x, y);

        for (int i = 0; i < x.length; ++i) {
            assertEquals("Should reproduce y-values at nodes for i=" + i,
                    y[i], interp.op(x[i]), TOL);
        }
    }

    /**
     * Lagrange interpolation of degree-4 polynomial should be exact at all points.
     */
    @Test
    public void testPolynomialInterpolation() {
        // 5 nodes = can exactly represent degree-4 polynomial
        final double[] x = {-2.0, -1.0, 0.0, 1.0, 2.0};
        final double[] y = new double[5];
        for (int i = 0; i < 5; ++i) {
            y[i] = x[i] * x[i] * x[i] - 2.0 * x[i] + 1.0;  // x^3 - 2x + 1
        }

        final LagrangeInterpolation interp = new LagrangeInterpolation(x, y);

        final double tol = 1e-10;
        for (double xi = -1.5; xi <= 1.5; xi += 0.5) {
            final double expected = xi * xi * xi - 2.0 * xi + 1.0;
            final double computed = interp.op(xi);
            assertEquals("Degree-3 polynomial at xi=" + xi, expected, computed, tol);
        }
    }

    /**
     * Test that value(y, x) interface produces same result as standard op(x).
     */
    @Test
    public void testValueWithUpdatedY() {
        final double[] x = {1.0, 2.0, 3.0, 4.0, 5.0};
        final double[] y = {1.0, 4.0, 9.0, 16.0, 25.0};

        final LagrangeInterpolation interp = new LagrangeInterpolation(x, y);

        final double xi = 2.5;
        final double fromOp   = interp.op(xi);
        final double fromValue = interp.value(y, xi);

        assertEquals("value(y, x) should match op(x)", fromOp, fromValue, TOL);
    }

    /**
     * Linear interpolation between 2 points should be exact.
     */
    @Test
    public void testLinearTwoPoints() {
        final double[] x = {0.0, 1.0};
        final double[] y = {3.0, 7.0};
        final LagrangeInterpolation interp = new LagrangeInterpolation(x, y);

        assertEquals("Linear at 0.5", 5.0, interp.op(0.5), TOL);
        assertEquals("Linear at 0.25", 4.0, interp.op(0.25), TOL);
        assertEquals("Linear at 0.75", 6.0, interp.op(0.75), TOL);
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;

import org.jquantlib.experimental.math.PiecewiseFunction;
import org.junit.Test;

/**
 * Phase 4k tests for {@link PiecewiseFunction}.
 */
public class PiecewiseFunctionTest {

    private static final double TOL = 1.0e-12;

    @Test
    public void testEmptyXReturnsY0() {
        assertEquals(7.0, PiecewiseFunction.eval(new double[] {}, new double[] { 7.0 }, 0.0), TOL);
        assertEquals(7.0, PiecewiseFunction.eval(new double[] {}, new double[] { 7.0 }, -10.0), TOL);
        assertEquals(7.0, PiecewiseFunction.eval(new double[] {}, new double[] { 7.0 }, 100.0), TOL);
    }

    @Test
    public void testTypicalUsage() {
        // Three breakpoints: X = [1, 2, 3], Y = [10, 20, 30, 40]
        // intervals: (-inf, 1) -> 10, [1, 2) -> 20, [2, 3) -> 30, [3, inf) -> 40
        final double[] X = { 1.0, 2.0, 3.0 };
        final double[] Y = { 10.0, 20.0, 30.0, 40.0 };
        assertEquals(10.0, PiecewiseFunction.eval(X, Y, 0.0), TOL);
        assertEquals(10.0, PiecewiseFunction.eval(X, Y, 0.5), TOL);
        assertEquals(20.0, PiecewiseFunction.eval(X, Y, 1.0), TOL);
        assertEquals(20.0, PiecewiseFunction.eval(X, Y, 1.5), TOL);
        assertEquals(30.0, PiecewiseFunction.eval(X, Y, 2.0), TOL);
        assertEquals(30.0, PiecewiseFunction.eval(X, Y, 2.5), TOL);
        assertEquals(40.0, PiecewiseFunction.eval(X, Y, 3.0), TOL);
        assertEquals(40.0, PiecewiseFunction.eval(X, Y, 100.0), TOL);
    }

    @Test
    public void testTooFewYValuesUsesLast() {
        // Y.length < X.length+1: last value reused
        final double[] X = { 1.0, 2.0, 3.0 };
        final double[] Y = { 10.0, 20.0 };
        assertEquals(10.0, PiecewiseFunction.eval(X, Y, 0.5), TOL);
        assertEquals(20.0, PiecewiseFunction.eval(X, Y, 1.5), TOL);
        assertEquals(20.0, PiecewiseFunction.eval(X, Y, 2.5), TOL);
        assertEquals(20.0, PiecewiseFunction.eval(X, Y, 3.5), TOL);
    }
}

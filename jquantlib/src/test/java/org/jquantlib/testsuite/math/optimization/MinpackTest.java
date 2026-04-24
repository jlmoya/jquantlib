/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for the Minpack port. See phase2a-design.md §3.2.
 */
package org.jquantlib.testsuite.math.optimization;

import java.lang.reflect.Method;

import org.jquantlib.math.optimization.Minpack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link org.jquantlib.math.optimization.Minpack}. Package-
 * private helpers are accessed via reflection so the port keeps visibility
 * parity with the C++ file-local statics.
 */
public class MinpackTest {

    // --- enorm -------------------------------------------------------------

    @Test
    public void enormZeroVector() throws Exception {
        final double[] x = { 0.0, 0.0, 0.0 };
        assertEquals(0.0, invokeEnorm(3, x), 0.0);
    }

    @Test
    public void enormThreeFourFive() throws Exception {
        // Classic 3-4-5 triangle; |(3,4)| = 5 exactly.
        final double[] x = { 3.0, 4.0 };
        assertEquals(5.0, invokeEnorm(2, x), 0.0);
    }

    @Test
    public void enormSingleValue() throws Exception {
        final double[] x = { 1.5 };
        assertEquals(1.5, invokeEnorm(1, x), 0.0);
    }

    @Test
    public void enormOverflowSafe() throws Exception {
        // Naive sum-of-squares would overflow; the large-component branch
        // scales to keep accumulation finite.
        final double[] x = { 1.0e200, 1.0e200 };
        final double expected = 1.0e200 * Math.sqrt(2.0);
        assertEquals(expected, invokeEnorm(2, x), expected * 1.0e-14);
    }

    @Test
    public void enormUnderflowSafe() throws Exception {
        // Below the intermediate-branch lower threshold (rdwarf = 3.834e-20).
        final double[] x = { 1.0e-25, 1.0e-25 };
        final double expected = 1.0e-25 * Math.sqrt(2.0);
        assertEquals(expected, invokeEnorm(2, x), expected * 1.0e-14);
    }

    @Test
    public void enormMixedMagnitude() throws Exception {
        // One large component dominates; small ones must not destabilise.
        final double[] x = { 1.0e200, 1.0, 1.0e-30 };
        assertEquals(1.0e200, invokeEnorm(3, x), 1.0e186);
    }

    // --- Reflection helpers ------------------------------------------------

    private static double invokeEnorm(int n, double[] x) throws Exception {
        final Method m = Minpack.class.getDeclaredMethod("enorm", int.class, double[].class);
        m.setAccessible(true);
        return (Double) m.invoke(null, n, x);
    }
}

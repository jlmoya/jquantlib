/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.jquantlib.experimental.math.MultidimIntegral;
import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.integrals.SimpsonIntegral;
import org.junit.Test;

/**
 * Phase 4k tests for {@link MultidimIntegral}.
 */
public class MultidimIntegralTest {

    @Test
    public void test1DIntegralOfConstant() {
        // Integral of f(x)=1 over [0,1] = 1
        final Integrator simpson = new SimpsonIntegral(1.0e-8, 100);
        final MultidimIntegral mdi =
                new MultidimIntegral(Arrays.asList(simpson));
        final double result = mdi.op(args -> 1.0,
                new double[] { 0.0 },
                new double[] { 1.0 });
        assertEquals(1.0, result, 1.0e-8);
    }

    @Test
    public void test2DIntegralOfConstant() {
        // Integral of f(x,y)=1 over [0,1]x[0,1] = 1
        final Integrator simpson1 = new SimpsonIntegral(1.0e-8, 100);
        final Integrator simpson2 = new SimpsonIntegral(1.0e-8, 100);
        final MultidimIntegral mdi =
                new MultidimIntegral(Arrays.asList(simpson1, simpson2));
        final double result = mdi.op(args -> 1.0,
                new double[] { 0.0, 0.0 },
                new double[] { 1.0, 1.0 });
        assertEquals(1.0, result, 1.0e-8);
    }

    @Test
    public void test2DIntegralOfProduct() {
        // Integral of f(x,y) = x*y over [0,1]x[0,1] = 0.25
        final Integrator simpson1 = new SimpsonIntegral(1.0e-8, 1000);
        final Integrator simpson2 = new SimpsonIntegral(1.0e-8, 1000);
        final MultidimIntegral mdi =
                new MultidimIntegral(Arrays.asList(simpson1, simpson2));
        final double result = mdi.op(args -> args[0] * args[1],
                new double[] { 0.0, 0.0 },
                new double[] { 1.0, 1.0 });
        assertEquals(0.25, result, 1.0e-7);
    }

    @Test
    public void test3DIntegralOfConstant() {
        // Integral of f(x,y,z)=2 over [0,1]^3 = 2
        final Integrator s1 = new SimpsonIntegral(1.0e-8, 100);
        final Integrator s2 = new SimpsonIntegral(1.0e-8, 100);
        final Integrator s3 = new SimpsonIntegral(1.0e-8, 100);
        final MultidimIntegral mdi = new MultidimIntegral(Arrays.asList(s1, s2, s3));
        final double result = mdi.op(args -> 2.0,
                new double[] { 0.0, 0.0, 0.0 },
                new double[] { 1.0, 1.0, 1.0 });
        assertEquals(2.0, result, 1.0e-8);
    }
}

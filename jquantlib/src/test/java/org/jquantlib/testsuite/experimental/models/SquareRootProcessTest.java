/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.processes.SquareRootProcess;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Phase 4j tests for {@link SquareRootProcess}.
 *
 * @author Phase 4j port
 */
public class SquareRootProcessTest {

    @Test
    public void testAccessors() {
        final double b = 0.09;  // long-run mean
        final double a = 1.0;   // speed of mean reversion
        final double sigma = 0.2;
        final double x0 = 0.05;

        final SquareRootProcess proc = new SquareRootProcess(b, a, sigma, x0);

        assertEquals("b() long-run mean", b, proc.b(), 1e-15);
        assertEquals("a() speed", a, proc.a(), 1e-15);
        assertEquals("sigma() volatility", sigma, proc.sigma(), 1e-15);
        assertEquals("x0() initial value", x0, proc.x0(), 1e-15);
    }

    @Test
    public void testDrift() {
        final double b = 0.09;
        final double a = 1.0;
        final double sigma = 0.2;
        final double x0 = 0.05;

        final SquareRootProcess proc = new SquareRootProcess(b, a, sigma, x0);

        // drift(t, x) = a*(b - x)
        final double x = 0.04;
        assertEquals("drift", a * (b - x), proc.drift(0.0, x), 1e-15);

        // At mean reversion level: drift = 0
        assertEquals("drift at mean = 0", 0.0, proc.drift(0.0, b), 1e-15);
    }

    @Test
    public void testDiffusion() {
        final double b = 0.09;
        final double a = 1.0;
        final double sigma = 0.2;

        final SquareRootProcess proc = new SquareRootProcess(b, a, sigma);

        // diffusion(t, x) = sigma * sqrt(x)
        final double x = 0.04;
        assertEquals("diffusion", sigma * Math.sqrt(x), proc.diffusion(0.0, x), 1e-15);
    }

    @Test
    public void testDiffusionZeroAtZero() {
        final SquareRootProcess proc = new SquareRootProcess(0.09, 1.0, 0.2);
        assertEquals("diffusion at x=0 must be 0", 0.0, proc.diffusion(0.0, 0.0), 1e-15);
    }
}

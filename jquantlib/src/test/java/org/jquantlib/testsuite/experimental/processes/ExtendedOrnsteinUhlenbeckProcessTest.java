/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — ExtendedOrnsteinUhlenbeckProcess smoke tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */
package org.jquantlib.testsuite.experimental.processes;

import org.jquantlib.QL;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess.Discretization;
import org.jquantlib.math.Ops;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Algebraic cross-validation for {@link ExtendedOrnsteinUhlenbeckProcess}.
 *
 * <p>The Java port is a 1:1 transcription of v1.42.1
 * {@code ql/experimental/processes/extendedornsteinuhlenbeckprocess.cpp}.
 * These tests verify that the formulas reduce correctly and that for a
 * constant {@code b(t)} they reproduce the classical
 * {@link OrnsteinUhlenbeckProcess} drift/diffusion/expectation/variance
 * up to the constant-level shift that matches the C++ reference.
 *
 * <p>Tight tolerance (1e-12) — pure floating-point ops within JVM 1-ULP
 * slack of C++ libm.
 */
public class ExtendedOrnsteinUhlenbeckProcessTest {

    private static final double TIGHT = 1e-12;

    public ExtendedOrnsteinUhlenbeckProcessTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void constantBReducesToOrnsteinUhlenbeck() {
        // For b(t) = level, drift = a*(level - x), matches OrnsteinUhlenbeckProcess
        // with the classical mean-reversion level.
        final double speed = 0.5, vol = 0.1, x0 = 0.04, level = 0.03;
        final Ops.DoubleOp b = u -> level;

        final ExtendedOrnsteinUhlenbeckProcess ext =
                new ExtendedOrnsteinUhlenbeckProcess(speed, vol, x0, b);
        // Underlying OU has level = 0 by construction; ExtendedOU adds
        // drift contribution speed*b(t) on top of the OU drift speed*(0 - x).
        final OrnsteinUhlenbeckProcess plain =
                new OrnsteinUhlenbeckProcess(speed, vol, x0, level);

        final double t = 1.0, x = 0.05, dt = 0.25;
        // drift: ext.drift = ouProcess.drift(t,x) + speed*b(t)
        //                  = speed*(0 - x) + speed*level = speed*(level - x) = plain.drift
        assertEquals(plain.drift(t, x), ext.drift(t, x), TIGHT);
        assertEquals(plain.diffusion(t, x), ext.diffusion(t, x), TIGHT);
        assertEquals(plain.variance(t, x, dt), ext.variance(t, x, dt), TIGHT);
        assertEquals(plain.stdDeviation(t, x, dt), ext.stdDeviation(t, x, dt), TIGHT);
        // MidPoint expectation: ouLevel0 + (x0 - 0)*exp(-a*dt) + b * (1 - exp(-a*dt))
        //                     = x0*exp(-a*dt) + level*(1 - exp(-a*dt)) = plain.expectation
        assertEquals(plain.expectation(t, x, dt), ext.expectation(t, x, dt), TIGHT);
    }

    @Test
    public void midPointTrapezoidalGaussLobattoAgreeForConstantB() {
        // For constant b(t), all three discretization schemes must produce the
        // same expectation. (Trapezoidal: limit cancels b(t)-b(0); GaussLobatto:
        // exact integral of constant.)
        final double speed = 0.7, vol = 0.2, x0 = 0.05, level = 0.04;
        final Ops.DoubleOp b = u -> level;
        final ExtendedOrnsteinUhlenbeckProcess mid = new ExtendedOrnsteinUhlenbeckProcess(
                speed, vol, x0, b, Discretization.MidPoint, 1e-4);
        final ExtendedOrnsteinUhlenbeckProcess trap = new ExtendedOrnsteinUhlenbeckProcess(
                speed, vol, x0, b, Discretization.Trapezodial, 1e-4);
        final ExtendedOrnsteinUhlenbeckProcess gl = new ExtendedOrnsteinUhlenbeckProcess(
                speed, vol, x0, b, Discretization.GaussLobatto, 1e-8);

        final double t0 = 0.5, x = 0.05, dt = 0.25;
        final double e1 = mid.expectation(t0, x, dt);
        final double e2 = trap.expectation(t0, x, dt);
        final double e3 = gl.expectation(t0, x, dt);
        assertEquals(e1, e2, 1e-10);
        assertEquals(e1, e3, 1e-8); // GaussLobatto adds adaptive integration noise
    }

    @Test
    public void validatesNegativeSpeedAndVolatility() {
        try {
            new ExtendedOrnsteinUhlenbeckProcess(-0.1, 0.1, 0.0, t -> 0.0);
            assertTrue("expected exception for negative speed", false);
        } catch (Exception e) {
            // ok
        }
        try {
            new ExtendedOrnsteinUhlenbeckProcess(0.1, -0.1, 0.0, t -> 0.0);
            assertTrue("expected exception for negative volatility", false);
        } catch (Exception e) {
            // ok
        }
    }

    @Test
    public void linearBHasExpectedDrift() {
        // b(t) = 0.05 + 0.1*t; speed = 1.0; x0 = 0
        // drift(t, x) = 1.0*(0 - x) + 1.0*(0.05 + 0.1*t) = -x + 0.05 + 0.1*t
        final ExtendedOrnsteinUhlenbeckProcess p = new ExtendedOrnsteinUhlenbeckProcess(
                1.0, 0.2, 0.0, t -> 0.05 + 0.1 * t);
        final double t = 0.8, x = 0.07;
        final double expected = -x + 0.05 + 0.1 * t;
        assertEquals(expected, p.drift(t, x), TIGHT);
    }
}

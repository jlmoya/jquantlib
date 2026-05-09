/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — GemanRoncoroniProcess smoke tests.

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
import org.jquantlib.experimental.processes.GemanRoncoroniProcess;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for {@link GemanRoncoroniProcess}.
 *
 * <p>Algebraic verification of drift, diffusion, and stdDeviation against
 * direct formulas. The process port is line-by-line of v1.42.1
 * {@code ql/experimental/processes/gemanroncoroniprocess.{hpp,cpp}}.
 */
public class GemanRoncoroniProcessTest {

    private static final double TIGHT = 1e-12;

    public GemanRoncoroniProcessTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static GemanRoncoroniProcess build() {
        // Plausible electricity-spot calibration values (from QuantLib test suite).
        return new GemanRoncoroniProcess(
                /*x0*/ 4.0,
                /*alpha*/ 4.0, /*beta*/ 0.05,
                /*gamma*/ 0.5, /*delta*/ 0.1,
                /*eps*/ 0.0, /*zeta*/ 0.0, /*d*/ 1.0,
                /*k*/ 0.5, /*tau*/ 0.0,
                /*sig2*/ 0.04, /*a*/ 1.5, /*b*/ 0.5,
                /*theta1*/ 1.5, /*theta2*/ 100.0, /*theta3*/ 4.0,
                /*psi*/ 1.0);
    }

    @Test
    public void x0Accessor() {
        final GemanRoncoroniProcess p = build();
        assertEquals(4.0, p.x0(), TIGHT);
    }

    @Test
    public void driftAlgebra() {
        // mu(t)     = alpha + beta*t + gamma*cos(eps + 2pi*t) + delta*cos(zeta + 4pi*t)
        // muPrime(t)= beta - gamma*2pi*sin(eps + 2pi*t) - delta*4pi*sin(zeta + 4pi*t)
        // drift     = muPrime + theta1*(mu - x)
        final GemanRoncoroniProcess p = build();
        final double t = 0.5, x = 4.5;
        final double alpha = 4.0, beta = 0.05, gamma = 0.5, delta = 0.1;
        final double eps = 0.0, zeta = 0.0, theta1 = 1.5;
        final double mu = alpha + beta * t + gamma * Math.cos(eps + 2 * Math.PI * t)
                + delta * Math.cos(zeta + 4 * Math.PI * t);
        final double muPrime = beta - gamma * 2 * Math.PI * Math.sin(eps + 2 * Math.PI * t)
                - delta * 4 * Math.PI * Math.sin(zeta + 4 * Math.PI * t);
        final double expected = muPrime + theta1 * (mu - x);
        assertEquals(expected, p.drift(t, x), TIGHT);
    }

    @Test
    public void diffusionAlgebra() {
        // diffusion = sqrt(sig2 + a*cos(pi*t + b)^2)
        final GemanRoncoroniProcess p = build();
        final double t = 0.5, x = 4.0;
        final double sig2 = 0.04, a = 1.5, b = 0.5;
        final double c = Math.cos(Math.PI * t + b);
        final double expected = Math.sqrt(sig2 + a * c * c);
        assertEquals(expected, p.diffusion(t, x), TIGHT);
    }

    @Test
    public void stdDeviationAlgebra() {
        // stdDev = sqrt(sig2t / (2*theta1) * (1 - exp(-2*theta1*dt)))
        final GemanRoncoroniProcess p = build();
        final double t0 = 0.3, x0 = 4.0, dt = 0.1;
        final double sig2 = 0.04, a = 1.5, b = 0.5, theta1 = 1.5;
        final double c = Math.cos(Math.PI * t0 + b);
        final double sig2t = sig2 + a * c * c;
        final double expected = Math.sqrt(sig2t / (2 * theta1) * (1.0 - Math.exp(-2 * theta1 * dt)));
        assertEquals(expected, p.stdDeviation(t0, x0, dt), TIGHT);
    }

    @Test
    public void evolveProducesFiniteValue() {
        // Smoke: evolve must yield a finite scalar (the random part adds nothing
        // controllable here without seeding the RNG via dw, so just check sanity).
        final GemanRoncoroniProcess p = build();
        final double r = p.evolve(0.1, 4.0, 0.05, 0.5);
        assertTrue("evolve produced non-finite value", Double.isFinite(r));
    }
}

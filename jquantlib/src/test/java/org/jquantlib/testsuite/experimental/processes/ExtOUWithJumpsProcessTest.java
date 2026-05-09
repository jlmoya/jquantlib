/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — ExtOUWithJumpsProcess (Kluge model) smoke tests.

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
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for {@link ExtOUWithJumpsProcess} (Kluge model — power
 * spot driven by an extended OU plus exp-jump component).
 *
 * <p>Tight algebraic verification on size, drift, diffusion structure;
 * evolve checks the deterministic Y-decay component.
 */
public class ExtOUWithJumpsProcessTest {

    private static final double TIGHT = 1e-12;

    public ExtOUWithJumpsProcessTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static ExtendedOrnsteinUhlenbeckProcess buildOU() {
        return new ExtendedOrnsteinUhlenbeckProcess(1.0, 0.5, 0.0, t -> 0.0);
    }

    @Test
    public void shapeAndAccessors() {
        final ExtOUWithJumpsProcess p = new ExtOUWithJumpsProcess(
                buildOU(), 0.1, 5.0, 4.0, 1.5);
        assertEquals(2, p.size());
        assertEquals(3, p.factors());
        assertEquals(5.0, p.beta(), TIGHT);
        assertEquals(4.0, p.jumpIntensity(), TIGHT);
        assertEquals(1.5, p.eta(), TIGHT);
        assertTrue(p.getExtendedOrnsteinUhlenbeckProcess() != null);
    }

    @Test
    public void initialValuesContainOuX0AndY0() {
        final ExtendedOrnsteinUhlenbeckProcess ou = new ExtendedOrnsteinUhlenbeckProcess(
                1.0, 0.5, 0.04, t -> 0.0);
        final ExtOUWithJumpsProcess p = new ExtOUWithJumpsProcess(ou, 0.1, 5.0, 4.0, 1.5);
        final Array iv = p.initialValues();
        assertEquals(2, iv.size());
        assertEquals(0.04, iv.get(0), TIGHT);
        assertEquals(0.1, iv.get(1), TIGHT);
    }

    @Test
    public void driftMatchesOuPlusMeanReversion() {
        final ExtendedOrnsteinUhlenbeckProcess ou = new ExtendedOrnsteinUhlenbeckProcess(
                1.5, 0.4, 0.0, t -> 0.02);
        final ExtOUWithJumpsProcess p = new ExtOUWithJumpsProcess(ou, 0.0, 7.0, 3.0, 2.0);
        final Array x = new Array(2);
        x.set(0, 0.05);
        x.set(1, 0.08);
        final Array d = p.drift(0.5, x);
        assertEquals(ou.drift(0.5, 0.05), d.get(0), TIGHT);
        assertEquals(-7.0 * 0.08, d.get(1), TIGHT);
    }

    @Test
    public void diffusionHasOnlyTopLeftEntry() {
        final ExtendedOrnsteinUhlenbeckProcess ou = new ExtendedOrnsteinUhlenbeckProcess(
                1.0, 0.3, 0.0, t -> 0.0);
        final ExtOUWithJumpsProcess p = new ExtOUWithJumpsProcess(ou, 0.0, 1.0, 1.0, 1.0);
        final Array x = new Array(2);
        x.set(0, 0.1);
        x.set(1, 0.0);
        final Matrix m = p.diffusion(0.0, x);
        assertEquals(2, m.rows());
        assertEquals(2, m.columns());
        assertEquals(0.3, m.get(0, 0), TIGHT);
        assertEquals(0.0, m.get(0, 1), TIGHT);
        assertEquals(0.0, m.get(1, 0), TIGHT);
        assertEquals(0.0, m.get(1, 1), TIGHT);
    }

    @Test
    public void evolveYComponentDecaysExponentially() {
        // With a very negative dw[1] (cumNorm ~ 0, but clipped to QL_EPSILON,
        // so log(QL_EPSILON) ~ -36; interarrival = -log(eps)/intensity,
        // which for intensity = 4.0 yields ~9, way larger than dt = 0.1, so
        // no jump fires) the Y component decays as Y0 * exp(-beta * dt).
        final ExtendedOrnsteinUhlenbeckProcess ou = new ExtendedOrnsteinUhlenbeckProcess(
                1.0, 0.5, 0.0, t -> 0.0);
        final ExtOUWithJumpsProcess p = new ExtOUWithJumpsProcess(ou, 0.2, 5.0, 4.0, 1.5);
        final Array x0 = new Array(2);
        x0.set(0, 0.05);
        x0.set(1, 0.2);
        // dw[1] = -8 → cumNorm ~ very small ~ QL_EPSILON ~ 1e-16
        // → -log(eps)/intensity ~ 36/4 = 9, > dt = 0.1, so no jump
        final Array dw = new Array(3);
        dw.set(0, 0.0);
        dw.set(1, -8.0);
        dw.set(2, 0.0);
        final double dt = 0.1;
        final Array result = p.evolve(0.0, x0, dt, dw);
        assertEquals(0.2 * Math.exp(-5.0 * dt), result.get(1), 1e-10);
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — KlugeExtOUProcess smoke tests.

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
import org.jquantlib.experimental.processes.KlugeExtOUProcess;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Smoke tests for {@link KlugeExtOUProcess} (joint Kluge power + extended
 * OU gas process).
 */
public class KlugeExtOUProcessTest {

    private static final double TIGHT = 1e-12;

    public KlugeExtOUProcessTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static KlugeExtOUProcess build(final double rho) {
        final ExtendedOrnsteinUhlenbeckProcess ouPower = new ExtendedOrnsteinUhlenbeckProcess(
                1.0, 0.5, 0.05, t -> 0.0);
        final ExtOUWithJumpsProcess kluge = new ExtOUWithJumpsProcess(
                ouPower, 0.1, 5.0, 4.0, 1.5);
        final ExtendedOrnsteinUhlenbeckProcess ouGas = new ExtendedOrnsteinUhlenbeckProcess(
                0.8, 0.3, 0.04, t -> 0.0);
        return new KlugeExtOUProcess(rho, kluge, ouGas);
    }

    @Test
    public void shapeAndAccessors() {
        final KlugeExtOUProcess p = build(0.4);
        assertEquals(3, p.size());
        assertEquals(4, p.factors());
        assertEquals(0.4, p.rho(), TIGHT);
        assertNotNull(p.getKlugeProcess());
        assertNotNull(p.getExtOUProcess());
    }

    @Test
    public void initialValuesContainsAllThree() {
        final KlugeExtOUProcess p = build(0.0);
        final Array iv = p.initialValues();
        assertEquals(3, iv.size());
        assertEquals(0.05, iv.get(0), TIGHT); // ouPower x0
        assertEquals(0.1, iv.get(1), TIGHT);  // kluge Y0
        assertEquals(0.04, iv.get(2), TIGHT); // ouGas x0
    }

    @Test
    public void diffusionMatrixDimensionsAndRhoBlock() {
        final double rho = 0.6;
        final KlugeExtOUProcess p = build(rho);
        final Array x = new Array(3);
        x.set(0, 0.05);
        x.set(1, 0.0);
        x.set(2, 0.04);
        final Matrix m = p.diffusion(0.0, x);
        assertEquals(3, m.rows());
        assertEquals(4, m.columns());
        // Top-left from kluge process: ouProcess.diffusion = 0.5
        assertEquals(0.5, m.get(0, 0), TIGHT);
        // Last row, col 0 = rho * vol; last row, last col = sqrt(1-rho^2) * vol
        // ouGas.diffusion(0,0) = 0.3
        assertEquals(rho * 0.3, m.get(2, 0), TIGHT);
        assertEquals(Math.sqrt(1 - rho * rho) * 0.3, m.get(2, 3), TIGHT);
    }

    @Test
    public void driftCombinesAllThreeProcesses() {
        final KlugeExtOUProcess p = build(0.0);
        final Array x = new Array(3);
        x.set(0, 0.05);
        x.set(1, 0.08);
        x.set(2, 0.04);
        final Array d = p.drift(0.5, x);
        assertEquals(3, d.size());
        // First entry = ouPower.drift(0.5, 0.05) = 1.0*(0 - 0.05) = -0.05
        assertEquals(-0.05, d.get(0), TIGHT);
        // Second entry = -beta*Y = -5.0*0.08 = -0.4
        assertEquals(-0.4, d.get(1), TIGHT);
        // Third entry = ouGas.drift(0.5, 0.04) = 0.8*(0 - 0.04) = -0.032
        assertEquals(-0.032, d.get(2), TIGHT);
    }
}

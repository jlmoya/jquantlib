/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.ClaytonCopula;
import org.junit.Test;

public class ClaytonCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: ClaytonCopula(0.5)(0.3, 0.6) = 0.22318576009630528
        assertEquals(0.22318576009630528,
                new ClaytonCopula(0.5).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: ClaytonCopula(2.0)(0.3, 0.6) = 0.27854300726557779
        assertEquals(0.27854300726557779,
                new ClaytonCopula(2.0).apply(0.3, 0.6), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        var c = new ClaytonCopula(2.0);
        // C(0.5, 1.0) = 0.5 (probe-verified)
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
        assertEquals(0.5, c.apply(1.0, 0.5), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThetaZeroRejected() {
        new ClaytonCopula(0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThetaBelowMinusOneRejected() {
        new ClaytonCopula(-2.0);
    }
}

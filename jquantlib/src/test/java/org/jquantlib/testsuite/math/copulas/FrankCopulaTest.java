/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.FrankCopula;
import org.junit.Test;

public class FrankCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: FrankCopula(2.0)(0.3, 0.6) = 0.22678330110326492
        assertEquals(0.22678330110326492,
                new FrankCopula(2.0).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: FrankCopula(-2.0)(0.3, 0.6) = 0.13062166033737899
        assertEquals(0.13062166033737899,
                new FrankCopula(-2.0).apply(0.3, 0.6), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        var c = new FrankCopula(2.0);
        // C++ probe: FrankCopula(2.0)(0.5, 1.0) = 0.5
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
        assertEquals(0.5, c.apply(1.0, 0.5), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThetaZeroRejected() {
        new FrankCopula(0.0);
    }
}

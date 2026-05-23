/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.FarlieGumbelMorgensternCopula;
import org.junit.Test;

public class FarlieGumbelMorgensternCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: FarlieGumbelMorgensternCopula(0.5)(0.3, 0.6) = 0.20519999999999999
        assertEquals(0.20519999999999999,
                new FarlieGumbelMorgensternCopula(0.5).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: FarlieGumbelMorgensternCopula(-0.5)(0.3, 0.6) = 0.15479999999999999
        assertEquals(0.15479999999999999,
                new FarlieGumbelMorgensternCopula(-0.5).apply(0.3, 0.6), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        var c = new FarlieGumbelMorgensternCopula(0.5);
        assertEquals(0.0, c.apply(0.5, 0.0), 1.0e-15);
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
        assertEquals(0.0, c.apply(0.0, 0.7), 1.0e-15);
        assertEquals(0.7, c.apply(1.0, 0.7), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThetaOutOfRange() {
        new FarlieGumbelMorgensternCopula(2.0);
    }
}

/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.GumbelCopula;
import org.junit.Test;

public class GumbelCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: GumbelCopula(1.5)(0.3, 0.6) = 0.24252181521175678
        assertEquals(0.24252181521175678,
                new GumbelCopula(1.5).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: GumbelCopula(3.0)(0.3, 0.6) = 0.29116176927653342
        assertEquals(0.29116176927653342,
                new GumbelCopula(3.0).apply(0.3, 0.6), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        var c = new GumbelCopula(1.5);
        // C++ probe: GumbelCopula(1.5)(0.5, 1.0) = 0.5
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
        assertEquals(0.5, c.apply(1.0, 0.5), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThetaBelowOneRejected() {
        new GumbelCopula(0.5);
    }
}

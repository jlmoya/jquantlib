/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.MaxCopula;
import org.junit.Test;

public class MaxCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: MaxCopula()(0.3, 0.6) = 0.29999999999999999 (min(u, v))
        assertEquals(0.29999999999999999,
                new MaxCopula().apply(0.3, 0.6), 1.0e-15);
        // C(u, v) = min(u, v): symmetric, and equals lesser argument
        assertEquals(0.2, new MaxCopula().apply(0.7, 0.2), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        var c = new MaxCopula();
        assertEquals(0.0, c.apply(0.5, 0.0), 1.0e-15);
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
        assertEquals(0.0, c.apply(0.0, 0.7), 1.0e-15);
        assertEquals(0.7, c.apply(1.0, 0.7), 1.0e-15);
    }
}

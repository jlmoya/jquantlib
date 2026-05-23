/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.MinCopula;
import org.junit.Test;

public class MinCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: MinCopula()(0.3, 0.6) = 0 (max(u+v-1, 0))
        assertEquals(0.0, new MinCopula().apply(0.3, 0.6), 1.0e-15);
        // C++ probe: MinCopula()(0.7, 0.6) = 0.29999999999999982 (= 0.7+0.6-1)
        assertEquals(0.29999999999999982,
                new MinCopula().apply(0.7, 0.6), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        var c = new MinCopula();
        assertEquals(0.0, c.apply(0.5, 0.0), 1.0e-15);
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
        assertEquals(0.0, c.apply(0.0, 0.7), 1.0e-15);
        assertEquals(0.7, c.apply(1.0, 0.7), 1.0e-15);
    }
}

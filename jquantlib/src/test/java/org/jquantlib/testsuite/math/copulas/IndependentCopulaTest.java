/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.IndependentCopula;
import org.junit.Test;

public class IndependentCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: IndependentCopula()(0.3, 0.6) = 0.17999999999999999
        assertEquals(0.17999999999999999,
                new IndependentCopula().apply(0.3, 0.6), 1.0e-15);
        // independence copula is simply u*v
        assertEquals(0.5 * 0.5, new IndependentCopula().apply(0.5, 0.5), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        var c = new IndependentCopula();
        assertEquals(0.0, c.apply(0.0, 0.7), 1.0e-15);
        assertEquals(0.7, c.apply(1.0, 0.7), 1.0e-15);
        assertEquals(0.0, c.apply(0.5, 0.0), 1.0e-15);
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
    }
}

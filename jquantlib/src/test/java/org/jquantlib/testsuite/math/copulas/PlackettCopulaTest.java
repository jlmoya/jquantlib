/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.PlackettCopula;
import org.junit.Test;

public class PlackettCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: PlackettCopula(2.0)(0.3, 0.6) = 0.21345400686718818
        assertEquals(0.21345400686718818,
                new PlackettCopula(2.0).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: PlackettCopula(0.5)(0.3, 0.6) = 0.14462219947249022
        assertEquals(0.14462219947249022,
                new PlackettCopula(0.5).apply(0.3, 0.6), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThetaOneRejected() {
        new PlackettCopula(1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeThetaRejected() {
        new PlackettCopula(-1.0);
    }
}

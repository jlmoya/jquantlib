/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.GalambosCopula;
import org.junit.Test;

public class GalambosCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: GalambosCopula(1.0)(0.3, 0.6) = 0.25765238788966743
        assertEquals(0.25765238788966743,
                new GalambosCopula(1.0).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: GalambosCopula(2.5)(0.3, 0.6) = 0.29342498686688184
        assertEquals(0.29342498686688184,
                new GalambosCopula(2.5).apply(0.3, 0.6), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeThetaRejected() {
        new GalambosCopula(-1.0);
    }
}

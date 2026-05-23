/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.HuslerReissCopula;
import org.junit.Test;

public class HuslerReissCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: HuslerReissCopula(1.0)(0.3, 0.6) = 0.22818066071420146
        // Tolerance loosened to 1e-12 to absorb cross-platform erfc-based
        // normal-CDF drift (Java/Apple libm differ at ~1e-15 ULP).
        assertEquals(0.22818066071420146,
                new HuslerReissCopula(1.0).apply(0.3, 0.6), 1.0e-12);
        // C++ probe: HuslerReissCopula(2.5)(0.3, 0.6) = 0.28731070239974565
        assertEquals(0.28731070239974565,
                new HuslerReissCopula(2.5).apply(0.3, 0.6), 1.0e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeThetaRejected() {
        new HuslerReissCopula(-1.0);
    }
}

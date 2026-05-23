/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.MarshallOlkinCopula;
import org.junit.Test;

public class MarshallOlkinCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: MarshallOlkinCopula(0.3, 0.7)(0.3, 0.6) = 0.25737516013322848
        assertEquals(0.25737516013322848,
                new MarshallOlkinCopula(0.3, 0.7).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: MarshallOlkinCopula(0.5, 0.5)(0.3, 0.6) = 0.232379000772445
        assertEquals(0.232379000772445,
                new MarshallOlkinCopula(0.5, 0.5).apply(0.3, 0.6), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeA1Rejected() {
        new MarshallOlkinCopula(-0.5, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeA2Rejected() {
        new MarshallOlkinCopula(0.5, -0.5);
    }
}

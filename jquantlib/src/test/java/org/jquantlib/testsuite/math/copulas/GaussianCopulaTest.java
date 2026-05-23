/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.GaussianCopula;
import org.junit.Test;

/**
 * Cross-validates against a C++ probe that uses
 * {@code BivariateCumulativeNormalDistributionDr78} (matching Java's only
 * available bivariate-normal CDF, the Drezner 1978 algorithm, ~6 decimals).
 * Note: C++ {@code GaussianCopula} normally uses {@code We04DP}; this probe
 * explicitly substitutes Dr78 for apples-to-apples comparison.
 */
public class GaussianCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe (Dr78): GaussianCopula(rho=0.5)(0.3, 0.6) = 0.24651550143938211
        assertEquals(0.24651550143938211,
                new GaussianCopula(0.5).apply(0.3, 0.6), 1.0e-15);
        // C++ probe (Dr78): GaussianCopula(rho=-0.3)(0.3, 0.6) = 0.13842640515659088
        assertEquals(0.13842640515659088,
                new GaussianCopula(-0.3).apply(0.3, 0.6), 1.0e-15);
        // C++ probe (Dr78): GaussianCopula(rho=0.0)(0.3, 0.6) = 0.17999999991890214
        // (independent case ≈ u*v = 0.18; Dr78 has ~1e-10 bias)
        assertEquals(0.17999999991890214,
                new GaussianCopula(0.0).apply(0.3, 0.6), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRhoOutOfRange() {
        new GaussianCopula(1.5);
    }
}

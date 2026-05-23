/*
 Copyright (C) 2026 Jose Moya
 SPDX-License-Identifier: BSD-3-Clause
 */
package org.jquantlib.testsuite.math.copulas;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.copulas.AliMikhailHaqCopula;
import org.junit.Test;

/**
 * Cross-validates the Java {@link AliMikhailHaqCopula} against C++ v1.42.1
 * reference values produced by a one-off probe linking against the pinned
 * QuantLib build (see {@code /tmp/copulas_probe.cpp}).
 *
 * <p>v1.42.1 has no {@code copulas.cpp} test-suite file, so values come from a
 * probe printing {@code AliMikhailHaqCopula(theta)(u, v)} with {@code %.17g}.
 */
public class AliMikhailHaqCopulaTest {

    @Test
    public void testApplyAtKnownPoints() {
        // C++ probe: AliMikhailHaqCopula(0.5)(0.3, 0.6) = 0.20930232558139533
        assertEquals(0.20930232558139533,
                new AliMikhailHaqCopula(0.5).apply(0.3, 0.6), 1.0e-15);
        // C++ probe: AliMikhailHaqCopula(-0.5)(0.3, 0.6) = 0.15789473684210528
        assertEquals(0.15789473684210528,
                new AliMikhailHaqCopula(-0.5).apply(0.3, 0.6), 1.0e-15);
    }

    @Test
    public void testBoundaryConditions() {
        // C(u, 0) = 0 and C(u, 1) = u (uniform marginals via Sklar)
        var c = new AliMikhailHaqCopula(0.5);
        assertEquals(0.0, c.apply(0.5, 0.0), 1.0e-15);
        assertEquals(0.5, c.apply(0.5, 1.0), 1.0e-15);
        assertEquals(0.0, c.apply(0.0, 0.7), 1.0e-15);
        assertEquals(0.7, c.apply(1.0, 0.7), 1.0e-15);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testThetaOutOfRange() {
        new AliMikhailHaqCopula(1.5);
    }
}

/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.math.randomnumbers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.randomnumbers.KnuthUniformRng;
import org.junit.Test;

/**
 * Cross-validation tests for {@link KnuthUniformRng} against C++ QuantLib v1.42.1.
 * <p>
 * Expected values from {@code /tmp/l1c_rng_probe.cpp} running against
 * {@code libQuantLib.1.42.0.dylib} (commit 099987f0).
 */
public class KnuthUniformRngTest {

    @Test
    public void testFirst10OutputsSeed42() {
        final KnuthUniformRng rng = new KnuthUniformRng(42L);
        final double[] expected = {
                0.37353865951700893,
                0.5340107498451816,
                0.79241662590326234,
                0.68876261089152746,
                0.3916902075366393,
                0.34402138963306084,
                0.28510151224157809,
                0.61634074353985824,
                0.77620135025665871,
                0.1975902265750884,
        };
        for ( int i = 0; i < expected.length; ++i ) {
            assertEquals("output[" + i + "]", expected[i], rng.next().value(), 1e-15);
        }
    }

    @Test
    public void testFirst10OutputsSeed123456() {
        final KnuthUniformRng rng = new KnuthUniformRng(123456L);
        final double[] expected = {
                0.66457769931414745,
                0.13130499389165107,
                0.28547371517113906,
                0.30772338171194646,
                0.55025986871972976,
                0.39341403943898223,
                0.49875552715084681,
                0.7051615938685738,
                0.41957565430538879,
                0.14134499258056255,
        };
        for ( int i = 0; i < expected.length; ++i ) {
            assertEquals("output[" + i + "]", expected[i], rng.next().value(), 1e-15);
        }
    }

    @Test
    public void testSumOfFirst100Seed42() {
        final KnuthUniformRng rng = new KnuthUniformRng(42L);
        double sum = 0.0;
        for ( int i = 0; i < 100; ++i ) {
            sum += rng.next().value();
        }
        // C++ reference: 54.23000317831999
        assertEquals(54.23000317831999, sum, 1e-12);
    }

    @Test
    public void testAllOutputsInZeroOne() {
        final KnuthUniformRng rng = new KnuthUniformRng(42L);
        for ( int i = 0; i < 1000; ++i ) {
            final double v = rng.next().value();
            assertTrue("out of [0,1) at i=" + i + ": " + v, v >= 0.0 && v < 1.0);
        }
    }
}

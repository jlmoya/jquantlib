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

import org.jquantlib.math.randomnumbers.LecuyerUniformRng;
import org.junit.Test;

/**
 * Cross-validation tests for {@link LecuyerUniformRng} against C++ QuantLib v1.42.1.
 * <p>
 * Expected values from {@code /tmp/l1c_rng_probe.cpp} running against
 * {@code libQuantLib.1.42.0.dylib} (commit 099987f0).
 */
public class LecuyerUniformRngTest {

    @Test
    public void testFirst10OutputsSeed42() {
        final LecuyerUniformRng rng = new LecuyerUniformRng(42L);
        final double[] expected = {
                0.56412426193736598,
                0.91747187449834744,
                0.11660955562806326,
                0.44048065386752394,
                0.94609570662404185,
                0.34757328896956963,
                0.25148183264581292,
                0.78699414427136172,
                0.16368005327545318,
                0.39711843279891962,
        };
        for ( int i = 0; i < expected.length; ++i ) {
            assertEquals("output[" + i + "]", expected[i], rng.next().value(), 1e-15);
        }
    }

    @Test
    public void testFirst10OutputsSeed123456() {
        final LecuyerUniformRng rng = new LecuyerUniformRng(123456L);
        final double[] expected = {
                0.51470672048165989,
                0.77983693605574755,
                0.22983454565328376,
                0.012642921448987128,
                0.71422762503351467,
                0.65481456539558158,
                0.7834399494307096,
                0.67296292176556227,
                0.4629217038621869,
                0.90737861773333628,
        };
        for ( int i = 0; i < expected.length; ++i ) {
            assertEquals("output[" + i + "]", expected[i], rng.next().value(), 1e-15);
        }
    }

    @Test
    public void testSumOfFirst100Seed42() {
        final LecuyerUniformRng rng = new LecuyerUniformRng(42L);
        double sum = 0.0;
        for ( int i = 0; i < 100; ++i ) {
            sum += rng.next().value();
        }
        // C++ reference: 53.782069429976808
        assertEquals(53.782069429976808, sum, 1e-12);
    }

    @Test
    public void testAllOutputsInZeroOne() {
        final LecuyerUniformRng rng = new LecuyerUniformRng(42L);
        for ( int i = 0; i < 1000; ++i ) {
            final double v = rng.next().value();
            assertTrue("out of (0,1) at i=" + i + ": " + v, v > 0.0 && v < 1.0);
        }
    }
}

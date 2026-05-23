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

import org.jquantlib.math.randomnumbers.BoxMullerGaussianRng;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

/**
 * Cross-validation tests for {@link BoxMullerGaussianRng} against C++ QuantLib v1.42.1.
 * <p>
 * Expected values from {@code /tmp/l1c_rng_probe.cpp} running against
 * {@code libQuantLib.1.42.0.dylib} (commit 099987f0).
 */
public class BoxMullerGaussianRngTest {

    @Test
    public void testFirst8OutputsWithMTSeed42() {
        final MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(42L);
        final BoxMullerGaussianRng< MersenneTwisterUniformRng > bm =
                new BoxMullerGaussianRng< MersenneTwisterUniformRng >(mt);
        final double[] expected = {
                -0.51696416445487181,
                1.2219212173764127,
                0.72133261267083881,
                0.86963581617716534,
                1.6182168832131514,
                1.5885563656499377,
                -1.1883085743351087,
                -0.18712466949524548,
        };
        for ( int i = 0; i < expected.length; ++i ) {
            assertEquals("output[" + i + "]", expected[i], bm.next().value(), 1e-14);
        }
    }

    @Test
    public void testEmpiricalMeanAndStdev() {
        final MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(42L);
        final BoxMullerGaussianRng< MersenneTwisterUniformRng > bm =
                new BoxMullerGaussianRng< MersenneTwisterUniformRng >(mt);
        double sum = 0.0;
        double sumSq = 0.0;
        final int N = 10000;
        for ( int i = 0; i < N; ++i ) {
            final double v = bm.next().value();
            sum += v;
            sumSq += v * v;
        }
        final double mean = sum / N;
        final double variance = sumSq / N - mean * mean;
        assertEquals("empirical mean ≈ 0", 0.0, mean, 0.05);
        assertEquals("empirical variance ≈ 1", 1.0, variance, 0.05);
    }
}

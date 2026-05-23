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

import org.jquantlib.math.randomnumbers.CLGaussianRng;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

/**
 * Cross-validation tests for {@link CLGaussianRng} against C++ QuantLib v1.42.1.
 * <p>
 * Expected values from {@code /tmp/l1c_rng_probe.cpp} running against
 * {@code libQuantLib.1.42.0.dylib} (commit 099987f0).
 */
public class CLGaussianRngTest {

    @Test
    public void testFirst6OutputsWithMTSeed42() {
        final MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(42L);
        final CLGaussianRng< MersenneTwisterUniformRng > cl =
                new CLGaussianRng< MersenneTwisterUniformRng >(mt);
        final double[] expected = {
                -0.1297533770557493,
                -0.41093516885302961,
                -0.59324607462622225,
                -1.6660819859243929,
                -0.0045922216959297657,
                0.60101371211931109,
        };
        for ( int i = 0; i < expected.length; ++i ) {
            assertEquals("output[" + i + "]", expected[i], cl.next().value(), 1e-14);
        }
    }

    @Test
    public void testEmpiricalMeanAndStdev() {
        final MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(42L);
        final CLGaussianRng< MersenneTwisterUniformRng > cl =
                new CLGaussianRng< MersenneTwisterUniformRng >(mt);
        double sum = 0.0;
        double sumSq = 0.0;
        final int N = 10000;
        for ( int i = 0; i < N; ++i ) {
            final double v = cl.next().value();
            sum += v;
            sumSq += v * v;
        }
        final double mean = sum / N;
        final double variance = sumSq / N - mean * mean;
        assertEquals("empirical mean ≈ 0", 0.0, mean, 0.05);
        assertEquals("empirical variance ≈ 1", 1.0, variance, 0.05);
    }
}

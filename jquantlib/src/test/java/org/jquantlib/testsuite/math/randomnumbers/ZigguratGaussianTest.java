/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.math.randomnumbers;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.randomnumbers.Xoshiro256StarStarUniformRng;
import org.jquantlib.math.randomnumbers.ZigguratGaussianRng;
import org.jquantlib.math.statistics.IncrementalStatistics;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/zigguratgaussian.cpp (Phase 5a).
 *
 * <p>Statistical sanity check on
 * {@code ZigguratGaussianRng<Xoshiro256StarStarUniformRng>::nextReal()}: with
 * seed 42 and 10<sup>7</sup> draws, the sample {mean, variance, skewness,
 * kurtosis} should be close to {0, 1, 0, 0}. Tolerances match C++ exactly
 * (0.001 mean, 0.005 variance, 0.001 skewness, 0.03 kurtosis).
 */
public class ZigguratGaussianTest {

    public ZigguratGaussianTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testStatisticsOfNextReal() {
        QL.info("Testing ZigguratGaussianRng<Xoshiro256StarStarUniformRng>::nextReal() for "
                + "mean, variance, skewness and kurtosis...");

        final long seed = 42L;
        final Xoshiro256StarStarUniformRng uniform = new Xoshiro256StarStarUniformRng(seed);
        final ZigguratGaussianRng random = new ZigguratGaussianRng(uniform);

        final IncrementalStatistics randoms = new IncrementalStatistics();
        final int iterations = 10_000_000;
        for (int j = 0; j < iterations; ++j) {
            randoms.add(random.next().value());
        }

        final double mean = randoms.mean();
        final double variance = randoms.variance();
        final double skewness = randoms.skewness();
        final double kurtosis = randoms.kurtosis();

        if (Math.abs(mean) > 0.001) {
            fail("Mean " + mean + " for seed " + seed + " is not close to 0.");
        }
        if (Math.abs(1.0 - variance) > 0.005) {
            fail("Variance " + variance + " for seed " + seed + " is not close to 1.");
        }
        if (Math.abs(skewness) > 0.001) {
            fail("Skewness " + skewness + " for seed " + seed + " is not close to 0.");
        }
        if (Math.abs(kurtosis) > 0.03) {
            fail("Kurtosis " + kurtosis + " for seed " + seed + " is not close to 0.");
        }
    }
}

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
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.randomnumbers.InverseCumulativeRsg;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.randomnumbers.RandomSequenceGenerator;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/rngtraits.cpp (Phase 5a).
 *
 * <p>4 BOOST_AUTO_TEST_CASE methods. {@code testGaussian} is portable;
 * {@code testDefaultPoisson} / {@code testCustomPoisson} require
 * {@code PoissonPseudoRandom} (not in JQuantLib); {@code testRanLux}
 * requires Ranlux3/4 RNG (not in JQuantLib).
 */
public class RngTraitsTest {

    public RngTraitsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testGaussian() {
        QL.info("Testing Gaussian pseudo-random number generation...");

        // Mirror C++ PseudoRandom::make_sequence_generator(100, 1234) using
        // the canonical MersenneTwister + InverseCumulativeNormal stack.
        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(1234);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> rsg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, 100, rng);
        final InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                   InverseCumulativeNormal> gaussian =
                new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                         InverseCumulativeNormal>(rsg, new InverseCumulativeNormal());

        final double[] values = gaussian.nextSequence().value();
        double sum = 0.0;
        for (final double v : values) {
            sum += v;
        }

        final double stored = 4.09916;
        final double tolerance = 1.0e-5;
        if (Math.abs(sum - stored) > tolerance) {
            fail("the sum of the samples does not match the stored value\n"
                    + "    calculated: " + sum
                    + "\n    expected:   " + stored);
        }
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no PoissonPseudoRandom (C++ "
            + "ql/math/randomnumbers/rngtraits.hpp). Port InverseCumulativePoisson then enable.")
    @Test
    public void testDefaultPoisson() {
    }

    @Ignore("Phase 5a.5 carry-forward — depends on PoissonPseudoRandom (see testDefaultPoisson).")
    @Test
    public void testCustomPoisson() {
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no Ranlux3/4 RNG (C++ "
            + "ql/math/randomnumbers/ranluxuniformrng.hpp). Port then enable.")
    @Test
    public void testRanLux() {
    }
}

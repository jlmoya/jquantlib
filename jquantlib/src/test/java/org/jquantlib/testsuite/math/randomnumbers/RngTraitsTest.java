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
import org.jquantlib.math.randomnumbers.RanluxUniformRng;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/rngtraits.cpp (Phase 5a).
 *
 * <p>4 BOOST_AUTO_TEST_CASE methods. {@code testGaussian} is portable;
 * {@code testDefaultPoisson} / {@code testCustomPoisson} require
 * {@code PoissonPseudoRandom} (not in JQuantLib); {@code testRanLux} was
 * un-ignored in Phase 5e.5b-CFC-d-77 once {@link RanluxUniformRng} was ported.
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

    /**
     * Mirrors the C++ {@code testRanLux}: after 10010 discarded samples,
     * the next 10 outputs of Ranlux3 (seed=2938723) and Ranlux4 (seed=4390109)
     * must match the reference C++ values bit-for-bit (close_enough check).
     */
    @Test
    public void testRanLux() {
        QL.info("Testing known RanLux sequence...");

        final RanluxUniformRng ranlux3 = RanluxUniformRng.ranlux3(2938723L);
        final RanluxUniformRng ranlux4 = RanluxUniformRng.ranlux4(4390109L);

        final double[] ranlux3Expected = {
                0.307448851544538826, 0.666313657894363587, 0.698528013702823358,
                0.0217381272445322793, 0.862964516238161394, 0.909193419106014034,
                0.674484308686746914, 0.849607570377191479, 0.054626078713596371,
                0.416474163715683687
        };

        final double[] ranlux4Expected = {
                0.222209169374078641, 0.420181950405986271, 0.0302156663005135329,
                0.0836259809475237148, 0.480549766594993599, 0.723472021829124401,
                0.905819507194266293, 0.54072519936540786, 0.445908421479817463,
                0.651084788437518824
        };

        for (int i = 0; i < 10010; ++i) {
            ranlux3.next();
            ranlux4.next();
        }

        // C++ close_enough uses 42 * ulp(max(|a|,|b|)) by default. With
        // |a|, |b| < 1, that is ~42 * 2.22e-16 ≈ 9.3e-15.
        final double tol = 1.0e-14;

        for (int i = 0; i < 10; ++i) {
            final double v3 = ranlux3.next().value();
            if (Math.abs(v3 - ranlux3Expected[i]) > tol) {
                fail("failed to reproduce ranlux3 numbers... idx=" + i
                        + " expected=" + ranlux3Expected[i]
                        + " actual=" + v3);
            }
            final double v4 = ranlux4.next().value();
            if (Math.abs(v4 - ranlux4Expected[i]) > tol) {
                fail("failed to reproduce ranlux4 numbers... idx=" + i
                        + " expected=" + ranlux4Expected[i]
                        + " actual=" + v4);
            }
        }
    }
}

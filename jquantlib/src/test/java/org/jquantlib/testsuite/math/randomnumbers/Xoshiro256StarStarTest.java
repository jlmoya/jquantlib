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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.randomnumbers.Xoshiro256StarStarUniformRng;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/xoshiro256starstar.cpp.
 *
 * <p>The C++ test exercises three behaviours of {@code Xoshiro256StarStarUniformRng}:
 * <ul>
 *   <li>{@code testMeanAndStdDevOfNextReal}: 10M draws, mean ~ 0.5, var ~ 1/12.</li>
 *   <li>{@code testAgainstReferenceImplementationInC}: byte-exact match against
 *     the public-domain reference {@code xoshiro256starstar.c} for 1000 nextInt64()
 *     calls, exercising both seed-only and (s0,s1,s2,s3) constructors.</li>
 *   <li>{@code testAbsenceOfInteractionBetweenInstances}: independent instances
 *     produce identical sequences when seeded identically.</li>
 * </ul>
 */
public class Xoshiro256StarStarTest {

    public Xoshiro256StarStarTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testMeanAndStdDevOfNextReal() {
        // C++ test-suite/xoshiro256starstar.cpp:161 — 10M draws, mean ~ 0.5,
        // var ~ 1/12, allowing 5e-3 mean error and 5e-5 variance error.
        final Xoshiro256StarStarUniformRng random = new Xoshiro256StarStarUniformRng(1L);
        final int iterations = 10_000_000;
        final double[] randoms = new double[iterations];
        for (int j = 0; j < iterations; ++j) {
            final double next = random.nextReal();
            if (next <= 0.0 || 1.0 <= next) {
                fail("next " + next + " not in range");
            }
            randoms[j] = next;
        }
        double sum = 0.0;
        for (int j = 0; j < iterations; ++j) {
            sum += randoms[j];
        }
        final double mean = sum / iterations;
        final double meanError = Math.abs(0.5 - mean);
        if (meanError > 0.005) {
            fail("Mean " + mean + " for seed 1 is not close to 0.5.");
        }
        double sqSum = 0.0;
        for (int j = 0; j < iterations; ++j) {
            final double d = randoms[j] - mean;
            sqSum += d * d;
        }
        final double stdDev = sqSum / iterations;
        final double stdDevError = Math.abs(1.0 / 12.0 - stdDev);
        if (stdDevError > 0.00005) {
            fail("Standard deviation " + stdDev + " for seed 1 is not close to 1/12.");
        }
    }

    @Test
    public void testAgainstReferenceImplementationInC() {
        // C++ test-suite/xoshiro256starstar.cpp:191 — exact byte-for-byte match
        // against the inlined C reference for 1000 draws from
        // seed=10108360646465513120ULL and explicit state s0/s1/s2/s3.
        final long seed = Long.parseUnsignedLong("10108360646465513120");
        final long s0 = Long.parseUnsignedLong("18274946675476036270");
        final long s1 = Long.parseUnsignedLong("6043068446171522962");
        final long s2 = Long.parseUnsignedLong("96311065249897859");
        final long s3 = Long.parseUnsignedLong("16504445955133574805");

        // Use a local ReferenceXoshiro to mirror C++ `s[4]` global state.
        final ReferenceXoshiro ref = new ReferenceXoshiro(s0, s1, s2, s3);

        final Xoshiro256StarStarUniformRng rngFromSeed = new Xoshiro256StarStarUniformRng(seed);
        final Xoshiro256StarStarUniformRng rngFroms0s1s2s3 =
                new Xoshiro256StarStarUniformRng(s0, s1, s2, s3);

        for (int i = 0; i < 1_000; i++) {
            final long nextRefImpl = ref.next();
            final long nextFromSeed = rngFromSeed.nextInt64();
            final long nextFroms0s1s2s3 = rngFroms0s1s2s3.nextInt64();
            if (nextRefImpl != nextFromSeed) {
                fail("Test failed at index " + i
                        + " (expected from reference implementation: "
                        + Long.toUnsignedString(nextRefImpl)
                        + "ULL, from Xoshiro256StarStarUniformRng("
                        + Long.toUnsignedString(seed) + "ULL): "
                        + Long.toUnsignedString(nextFromSeed) + "ULL)");
            }
            if (nextFroms0s1s2s3 != nextFromSeed) {
                fail("Test failed at index " + i
                        + " (from Xoshiro256StarStarUniformRng("
                        + Long.toUnsignedString(seed) + "): "
                        + Long.toUnsignedString(nextFroms0s1s2s3)
                        + "ULL, from Xoshiro256StarStarUniformRng("
                        + Long.toUnsignedString(s0) + "ULL, "
                        + Long.toUnsignedString(s1) + "ULL, "
                        + Long.toUnsignedString(s2) + "ULL, "
                        + Long.toUnsignedString(s3) + "ULL): "
                        + Long.toUnsignedString(nextFromSeed) + "ULL)");
            }
        }
    }

    @Test
    public void testAbsenceOfInteractionBetweenInstances() {
        // C++ test-suite/xoshiro256starstar.cpp:230 — verifies sequential and
        // parallel uses of independent rng instances seeded identically agree
        // at draw 1000.
        final long seed = Long.parseUnsignedLong("16880566536755896171");
        final Xoshiro256StarStarUniformRng rng = new Xoshiro256StarStarUniformRng(seed);
        for (int i = 0; i < 999; ++i) {
            rng.nextInt64();
        }
        final long referenceValue = rng.nextInt64();

        // sequential use
        final Xoshiro256StarStarUniformRng rng1 = new Xoshiro256StarStarUniformRng(seed);
        final Xoshiro256StarStarUniformRng rng2 = new Xoshiro256StarStarUniformRng(seed);
        for (int i = 0; i < 1_000; i++) {
            rng1.nextInt64();
        }
        for (int i = 0; i < 999; i++) {
            rng2.nextInt64();
        }
        final long seqValue = rng2.nextInt64();
        if (referenceValue != seqValue) {
            fail("Detected interaction between Xoshiro256StarStarUniformRng instances during "
                    + "sequential computation: reference=" + Long.toUnsignedString(referenceValue)
                    + " rng2=" + Long.toUnsignedString(seqValue));
        }

        // parallel use
        final Xoshiro256StarStarUniformRng rng3 = new Xoshiro256StarStarUniformRng(seed);
        final Xoshiro256StarStarUniformRng rng4 = new Xoshiro256StarStarUniformRng(seed);
        for (int i = 0; i < 999; i++) {
            rng3.nextInt64();
            rng4.nextInt64();
        }
        final long par3 = rng3.nextInt64();
        final long par4 = rng4.nextInt64();
        if (referenceValue != par3 || referenceValue != par4) {
            fail("Detected interaction between Xoshiro256StarStarUniformRng instances during "
                    + "parallel computation: reference=" + Long.toUnsignedString(referenceValue)
                    + " rng3=" + Long.toUnsignedString(par3)
                    + " rng4=" + Long.toUnsignedString(par4));
        }
        // No-op assertions to placate static analyzers expecting JUnit asserts.
        assertEquals(referenceValue, seqValue);
        assertEquals(referenceValue, par3);
        assertEquals(referenceValue, par4);
    }

    /**
     * Inlined Java mirror of the public-domain reference C implementation from
     * https://prng.di.unimi.it/xoshiro256starstar.c, used by
     * {@link #testAgainstReferenceImplementationInC()}. Independent of the
     * production {@link Xoshiro256StarStarUniformRng} so both sides can be
     * compared bit-exactly.
     */
    private static final class ReferenceXoshiro {
        private final long[] s = new long[4];

        ReferenceXoshiro(final long s0, final long s1, final long s2, final long s3) {
            s[0] = s0;
            s[1] = s1;
            s[2] = s2;
            s[3] = s3;
        }

        long next() {
            final long result = rotl(s[1] * 5L, 7) * 9L;
            final long t = s[1] << 17;

            s[2] ^= s[0];
            s[3] ^= s[1];
            s[1] ^= s[2];
            s[0] ^= s[3];

            s[2] ^= t;

            s[3] = rotl(s[3], 45);

            return result;
        }

        private static long rotl(final long x, final int k) {
            return (x << k) | (x >>> (64 - k));
        }
    }
}

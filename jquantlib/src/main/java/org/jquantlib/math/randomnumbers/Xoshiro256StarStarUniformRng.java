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

// NOTE: The following copyright notice applies to the
// original C implementation https://prng.di.unimi.it/xoshiro256starstar.c
// and the SplitMix64 seeding implementation
// https://prng.di.unimi.it/splitmix64.c that have been used for this class.

/*  Written in 2018 by David Blackman and Sebastiano Vigna (vigna@acm.org)

To the extent possible under law, the author has dedicated all copyright
and related and neighboring rights to this software to the public domain
worldwide. This software is distributed without any warranty.

See <http://creativecommons.org/publicdomain/zero/1.0/>. */

package org.jquantlib.math.randomnumbers;

import org.jquantlib.methods.montecarlo.Sample;

/**
 * xoshiro256** uniform random number generator (period 2<sup>256</sup>-1).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/randomnumbers/xoshiro256starstaruniformrng.hpp/.cpp}.
 *
 * <p>For more details see <a href="https://prng.di.unimi.it/">https://prng.di.unimi.it/</a>
 * and its reference implementation
 * <a href="https://prng.di.unimi.it/xoshiro256starstar.c">xoshiro256starstar.c</a>.
 *
 * <p>Java {@code long} is signed 64-bit, but the xoshiro256** algorithm is defined
 * over unsigned 64-bit integers. All bit operations ({@code ^}, {@code <<},
 * {@code |}, {@code *}) yield identical results in two's-complement signed
 * arithmetic, so we use {@code long} as a uint64 container. The unsigned right
 * shift {@code >>>} replaces C++ {@code >>} on the unsigned input. Multiplication
 * is also identical modulo 2<sup>64</sup> in signed/unsigned interpretations.
 *
 * @author JQuantLib migration contributors
 */
public class Xoshiro256StarStarUniformRng implements RandomNumberGenerator {

    private long s0_, s1_, s2_, s3_;

    /**
     * If the given seed is 0, a random seed will be chosen based on
     * {@link SeedGenerator}.
     *
     * @param seed the initial seed (uint64; Java {@code long} container).
     */
    public Xoshiro256StarStarUniformRng(final long seed) {
        final long actualSeed = (seed != 0L) ? seed : SeedGenerator.getInstance().get();
        // SplitMix64 seeder, matches C++ v1.42.1 xoshiro256starstaruniformrng.cpp
        long x = actualSeed;
        x = (x + 0x9e3779b97f4a7c15L);
        long z = x;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        s0_ = z ^ (z >>> 31);

        x = (x + 0x9e3779b97f4a7c15L);
        z = x;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        s1_ = z ^ (z >>> 31);

        x = (x + 0x9e3779b97f4a7c15L);
        z = x;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        s2_ = z ^ (z >>> 31);

        x = (x + 0x9e3779b97f4a7c15L);
        z = x;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        s3_ = z ^ (z >>> 31);
    }

    /**
     * Default constructor uses {@link SeedGenerator} for a non-deterministic seed.
     */
    public Xoshiro256StarStarUniformRng() {
        this(0L);
    }

    /**
     * Direct state constructor. Make sure that s0, s1, s2 and s3 are chosen
     * randomly. Otherwise, the results of the first random numbers might not
     * be well distributed. Especially s0 = s1 = s2 = s3 = 0 does not work and
     * will always return 0.
     *
     * @param s0 state word 0 (uint64)
     * @param s1 state word 1 (uint64)
     * @param s2 state word 2 (uint64)
     * @param s3 state word 3 (uint64)
     */
    public Xoshiro256StarStarUniformRng(final long s0, final long s1, final long s2, final long s3) {
        this.s0_ = s0;
        this.s1_ = s1;
        this.s2_ = s2;
        this.s3_ = s3;
    }

    /**
     * Returns a sample with weight 1.0 containing a random number in the
     * (0.0, 1.0) interval.
     */
    @Override
    public Sample<Double> next() {
        return new Sample<Double>(nextReal(), 1.0);
    }

    /**
     * @return a random number in the (0.0, 1.0)-interval.
     */
    public double nextReal() {
        // C++ v1.42.1: (Real(nextInt64() >> 11) + 0.5) * (1.0 / Real(1ULL << 53))
        // The >> on unsigned uint64 in C++ is a logical (zero-fill) shift, so we
        // use Java's >>> here.
        final long raw = nextInt64();
        return ((double) (raw >>> 11) + 0.5) * (1.0 / (double) (1L << 53));
    }

    /**
     * @return a random uint64 in the [0, 0xffffffffffffffffULL] interval
     *         (Java {@code long} container; interpret as unsigned).
     */
    public long nextInt64() {
        final long result = rotl(s1_ * 5L, 7) * 9L;

        final long t = s1_ << 17;

        s2_ ^= s0_;
        s3_ ^= s1_;
        s1_ ^= s2_;
        s0_ ^= s3_;

        s2_ ^= t;

        s3_ = rotl(s3_, 45);

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the high 32 bits of {@link #nextInt64()}, masked to fit in
     * the unsigned-32-in-signed-64 contract of {@link RandomNumberGenerator#nextInt32()}.
     */
    @Override
    public long nextInt32() {
        // Use the high 32 bits of the 64-bit output (better statistical quality
        // than the low 32). Mask to keep it in the unsigned-32 range.
        return (nextInt64() >>> 32) & 0xFFFFFFFFL;
    }

    private static long rotl(final long x, final int k) {
        // C++: (x << k) | (x >> (64 - k))
        // For uint64, C++ >> is a logical shift; use Java >>>.
        return (x << k) | (x >>> (64 - k));
    }
}

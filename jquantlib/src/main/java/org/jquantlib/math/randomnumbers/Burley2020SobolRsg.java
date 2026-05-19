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

/*
 Copyright (C) 2023 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.math.randomnumbers;

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.Sample;

/**
 * Scrambled Sobol sequence following Burley, 2020.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/randomnumbers/burley2020sobolrsg.hpp,cpp}.
 *
 * <p>Reference: Brent Burley, "Practical Hash-based Owen Scrambling",
 * Journal of Computer Graphics Techniques, Vol. 9, No. 4, 2020.
 *
 * <p>The implementation reproduces the C++ semantics exactly:
 * <ol>
 *   <li>Each call to {@link #nextSequence()} (or
 *     {@link #nextInt32Sequence()}) first scrambles its own counter via
 *     {@code nested_uniform_scramble(counter, group4Seeds[0])} to obtain
 *     a Sobol sample index {@code n}, then calls
 *     {@link SobolRsg#skipTo(long)} to retrieve the underlying Sobol
 *     vector at sample {@code n+1}.</li>
 *   <li>The 32-bit Sobol vector entries (extracted from the upper half of
 *     the Java 64-bit direction integers) are then individually scrambled
 *     in groups of four with seeds derived from a Boost-1.83-style
 *     {@code hash_combine} chain seeded by {@code group4Seeds[group]}.</li>
 *   <li>Doubles are normalised as {@code (uint32_value + 0.5) / 2^32},
 *     which guarantees the output is strictly in (0, 1) even when the
 *     scramble maps a coordinate to zero (the test
 *     {@code testBurley2020SobolRsgOutputBounds} verifies this).</li>
 * </ol>
 *
 * <p>Bit-width bridging: Java {@link SobolRsg} stores 64-bit direction
 * integers where the i-th sample's information lives in the upper 32
 * bits (since {@code BITS=64} and direction-integer initialisers are
 * shifted by {@code BITS-j-1}). For all sample indices used by Burley
 * (up to {@code 2^32-1}) the loop in {@link SobolRsg#skipTo(long)}
 * touches only direction indices {@code j < 32}, so every long in the
 * resulting vector has zero lower 32 bits. {@code (int)(longValue >>> 32)}
 * therefore yields the exact C++ {@code uint32_t} value.
 *
 * <p>Java {@code int} arithmetic implements 32-bit modular wrap-around
 * identically to C++ {@code uint32_t}, so all hash arithmetic
 * (multiplications, additions, XORs) is bit-identical when the
 * intermediate values are kept in {@code int}.
 */
public class Burley2020SobolRsg {

    //
    // private fields
    //

    private final int dimensionality;
    private final long seed;
    private final SobolRsg.DirectionIntegers directionIntegers;
    private final int[] group4Seeds;
    private final long[] integerSequence;
    private SobolRsg sobolRsg;
    private Sample< double[] > sequence;
    private long nextSequenceCounter;

    //
    // public constructors
    //

    public Burley2020SobolRsg(final int dimensionality) {
        this(dimensionality, 42L, SobolRsg.DirectionIntegers.Jaeckel, 43L);
    }

    public Burley2020SobolRsg(final int dimensionality, final long seed) {
        this(dimensionality, seed, SobolRsg.DirectionIntegers.Jaeckel, 43L);
    }

    public Burley2020SobolRsg(final int dimensionality, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers) {
        this(dimensionality, seed, directionIntegers, 43L);
    }

    public Burley2020SobolRsg(final int dimensionality, final long seed,
            final SobolRsg.DirectionIntegers directionIntegers, final long scrambleSeed) {
        QL.require(dimensionality > 0, "dimensionality must be greater than 0");

        this.dimensionality = dimensionality;
        this.seed = seed;
        this.directionIntegers = directionIntegers;
        this.integerSequence = new long[dimensionality];
        this.sequence = new Sample< double[] >(new double[dimensionality], 1.0);

        reset();

        // group4Seeds_.resize((dimensionality_ - 1) / 4 + 1);
        // MersenneTwisterUniformRng mt(scrambleSeed);
        // for (auto& s : group4Seeds_) s = static_cast<uint32_t>(mt.nextInt32());
        final int nGroups = (dimensionality - 1) / 4 + 1;
        this.group4Seeds = new int[nGroups];
        final MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(scrambleSeed);
        for ( int i = 0; i < nGroups; i++ ) {
            // mt.nextInt32() returns an unsigned 32-bit value in a long;
            // casting to int preserves the bit pattern exactly.
            this.group4Seeds[i] = (int) mt.nextInt32();
        }
    }

    //
    // public API
    //

    /**
     * Reverse the 32 bits of x. C++ uses a 256-entry lookup table; here we use {@link Integer#reverse(int)} which is
     * HotSpot-intrinsified to a single instruction on common architectures and produces the identical bit pattern.
     */
    private static int reverseBits(final int x) {
        return Integer.reverse(x);
    }

    /**
     * Laine-Karras permutation (Burley 2020, Section 6.1). All multiplications are 32-bit mod-2^32, which Java
     * {@code int} arithmetic provides natively.
     */
    private static int laineKarrasPermutation(int x, final int s) {
        x += s;
        x ^= x * 0x6c50b47c;
        x ^= x * 0xb82f1e52;
        x ^= x * 0xc7afe638;
        x ^= x * 0x8d22f6e6;
        return x;
    }

    private static int nestedUniformScramble(int x, final int s) {
        x = reverseBits(x);
        x = laineKarrasPermutation(x, s);
        x = reverseBits(x);
        return x;
    }

    private static long localHashMix(long x) {
        final long m = 0x0e9846af9b1a615dL;
        x ^= x >>> 32;
        x *= m;
        x ^= x >>> 32;
        x *= m;
        x ^= x >>> 28;
        return x;
    }

    private static long localHash(final long v) {
        long s = 0L;
        s = (v >>> 32) + localHashMix(s);
        s = (v & 0xFFFFFFFFL) + localHashMix(s);
        return s;
    }

    //
    // private helpers
    //

    private static long localHashCombine(final long x, final long v) {
        return localHashMix(x + 0x9e3779b9L + localHash(v));
    }

    // ---- Bit-reverse + Laine-Karras + nested-uniform-scramble ----

    public int dimension() {
        return dimensionality;
    }

    /**
     * Advance the internal counter to the given index and return the scrambled integer Sobol vector at that index.
     * Mirrors C++ {@code Burley2020SobolRsg::skipTo(std::uint32_t n)}: sets {@code nextSequenceCounter_ = n}, calls
     * {@link #nextInt32Sequence()} (which increments the counter), decrements it again, and returns the integer
     * sequence.
     *
     * @param n target sample index (treated as an unsigned 32-bit value)
     * @return reference to the internal integer-sequence buffer
     */
    public long[] skipTo(final long n) {
        nextSequenceCounter = n & 0xFFFFFFFFL;
        nextInt32Sequence();
        --nextSequenceCounter;
        return integerSequence;
    }

    /**
     * Return the next 32-bit Sobol vector with Burley/Owen scrambling applied. Each call advances the internal counter
     * by one.
     *
     * <p>The returned array stores the scrambled values in the lower
     * 32 bits of each long ({@code values are masked to 0xFFFFFFFF}).
     *
     * @return reference to the internal integer-sequence buffer
     */
    public long[] nextInt32Sequence() {
        // n = nested_uniform_scramble(nextSequenceCounter_, group4Seeds_[0])
        final int n = nestedUniformScramble((int) nextSequenceCounter, group4Seeds[0]);

        // const auto& seq = sobolRsg_->skipTo(n);
        // (Java SobolRsg.skipTo takes a long; n is interpreted as unsigned.)
        final long[] seq = sobolRsg.skipTo(n & 0xFFFFFFFFL);

        // std::copy(seq.begin(), seq.end(), integerSequence_.begin());
        // Convert Java 64-bit direction integers to the 32-bit form C++
        // works with: take the upper 32 bits. See class javadoc for why
        // this is exact for sample indices < 2^32.
        for ( int k = 0; k < dimensionality; k++ ) {
            integerSequence[k] = (seq[k] >>> 32) & 0xFFFFFFFFL;
        }

        // Scramble in groups of four with hash_combine'd seeds.
        int i = 0;
        int group = 0;
        do {
            long seedAcc = group4Seeds[group++] & 0xFFFFFFFFL;
            for ( int g = 0; g < 4 && i < dimensionality; ++g, ++i ) {
                seedAcc = localHashCombine(seedAcc, g);
                integerSequence[i] = nestedUniformScramble((int) integerSequence[i], (int) seedAcc) & 0xFFFFFFFFL;
            }
        } while ( i < dimensionality );

        // ++nextSequenceCounter_; QL_REQUIRE(... != 0, "period exceeded");
        nextSequenceCounter = (nextSequenceCounter + 1L) & 0xFFFFFFFFL;
        QL.require(nextSequenceCounter != 0L, "Burley2020SobolRsg::nextInt32Sequence(): period exceeded");
        return integerSequence;
    }

    // ---- Boost 1.83 hash_combine / hash / hash_mix (64-bit) ----
    //
    // We use longs throughout these helpers — Java {@code long} provides
    // exact 64-bit modular multiplication identical to C++
    // {@code uint64_t}.

    /**
     * Return the next vector of doubles in {@code (0, 1)} (strictly). The {@code +0.5} offset before division by
     * {@code 2^32} guarantees both bounds are excluded even when the scramble maps a coordinate to zero.
     */
    public Sample< double[] > nextSequence() {
        final long[] v = nextInt32Sequence();
        final double[] d = new double[dimensionality];
        for ( int k = 0; k < dimensionality; k++ ) {
            d[k] = (((double) (v[k] & 0xFFFFFFFFL)) + 0.5) / 4294967296.0;
        }
        sequence = new Sample< double[] >(d, 1.0);
        return sequence;
    }

    public Sample< double[] > lastSequence() {
        return sequence;
    }

    private void reset() {
        // C++ creates SobolRsg with useGrayCode=false; Java SobolRsg
        // doesn't expose that flag but SobolRsg.skipTo is implemented in
        // a use-Gray-code-agnostic way (it computes the sample at index
        // skip+1 directly from the Gray-code expansion of skip+1), so the
        // two modes agree on skipTo outputs.
        sobolRsg = new SobolRsg(dimensionality, seed, directionIntegers);
        nextSequenceCounter = 0L;
    }
}

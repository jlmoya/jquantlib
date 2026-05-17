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
 Copyright (C) 2009 Klaus Spanderen
*/
package org.jquantlib.math.randomnumbers;

import org.jquantlib.methods.montecarlo.Sample;

/**
 * Luescher's "luxury" uniform random number generator.
 * <p>
 * Java port of QuantLib v1.42.1
 * {@code ql/math/randomnumbers/ranluxuniformrng.hpp} (Klaus Spanderen, 2009).
 * <p>
 * The C++ implementation is a thin wrapper around
 * {@code std::discard_block_engine<std::subtract_with_carry_engine<uint_fast64_t,48,10,24>, P, R>}.
 * This Java port faithfully reproduces both STL engines bit-for-bit so that
 * the C++ reference sequence is matched exactly.
 * <p>
 * Two static factory methods produce the standard luxury levels:
 * <ul>
 *   <li>{@link #ranlux3(long)} — block size {@code P=223}, used count {@code R=24};</li>
 *   <li>{@link #ranlux4(long)} — block size {@code P=389}, used count {@code R=24}
 *       (highest possible luxury).</li>
 * </ul>
 * <p>
 * References:
 * <ul>
 *   <li>M. Luescher, <i>A portable high-quality random number generator for
 *       lattice field theory simulations</i>, Comp. Phys. Comm. 79 (1994) 100.</li>
 *   <li>ISO/IEC 14882 (C++17), §29.6.4.4 (subtract_with_carry_engine) and
 *       §29.6.4.5 (discard_block_engine).</li>
 * </ul>
 *
 * @author Phase 5e.5b-CFC-d-77 carry-forward
 */
public final class RanluxUniformRng implements RandomNumberGenerator {

    /** C++ default Ranlux seed (per ranluxuniformrng.hpp constructor signature). */
    public static final long DEFAULT_SEED = 19780503L;

    /** Word size w = 48 bits → modulus m = 2^48. */
    private static final long M48 = 1L << 48;
    /** Lower-48-bit mask. */
    private static final long MASK48 = M48 - 1L;

    /** Short lag s = 10. */
    private static final int S = 10;
    /** Long lag r = 24. */
    private static final int R = 24;

    /** discard_block_engine "block size" P. */
    private final int p;
    /** discard_block_engine "used count" R (per-block). */
    private final int rUsed;

    /** subtract_with_carry_engine state X[0..R-1] (each X[i] in [0, 2^48)). */
    private final long[] state = new long[R];
    /** subtract_with_carry_engine carry bit (0 or 1). */
    private long carry;
    /** Cyclic index into {@link #state}, in [0, R). */
    private int index;

    /** discard_block_engine usage count within current block. */
    private int blockUsed;

    /**
     * Construct a Ranlux RNG with the given block size, used count, and seed.
     */
    private RanluxUniformRng(final int p, final int rUsed, final long seed) {
        this.p = p;
        this.rUsed = rUsed;
        this.blockUsed = 0;
        seedSubtractWithCarry(seed);
    }

    /** Ranlux3: P=223, R=24 (good correlation properties). */
    public static RanluxUniformRng ranlux3(final long seed) {
        return new RanluxUniformRng(223, 24, seed);
    }

    /** Ranlux4: P=389, R=24 (highest luxury). */
    public static RanluxUniformRng ranlux4(final long seed) {
        return new RanluxUniformRng(389, 24, seed);
    }

    /** Ranlux3 with default seed 19780503 (matches C++). */
    public static RanluxUniformRng ranlux3() {
        return ranlux3(DEFAULT_SEED);
    }

    /** Ranlux4 with default seed 19780503 (matches C++). */
    public static RanluxUniformRng ranlux4() {
        return ranlux4(DEFAULT_SEED);
    }

    /**
     * Per C++ standard (§29.6.4.4): when seeded with value {@code s},
     * the engine uses a {@code linear_congruential_engine<uint_least32_t,
     * 40014u, 0u, 2147483563u>} (a.k.a. {@code minstd_rand0}-style but with
     * the IBM-multiplier 40014 mod 2147483563) seeded with
     * {@code s == 0 ? 19780503u : s mod 2147483563u}. It then generates
     * R*n outputs (n = ceil(w/32) = 2 for w=48); each X[i] is built from
     * two consecutive z values as {@code z0 + z1 * 2^32}, masked to 48 bits.
     * The carry is 1 iff X[R-1] == 0, else 0.
     */
    private void seedSubtractWithCarry(final long seed) {
        final long lcgM = 2147483563L; // modulus
        final long lcgA = 40014L;      // multiplier
        // LCG seed: s != 0 ? s (mod M) : 19780503.
        long lcgState = seed;
        if (lcgState == 0L) {
            lcgState = 19780503L;
        }
        lcgState %= lcgM;
        if (lcgState == 0L) {
            lcgState = 19780503L;
        }

        for (int i = 0; i < R; ++i) {
            // n = ceil(48 / 32) = 2 LCG draws per state word.
            lcgState = (lcgA * lcgState) % lcgM;
            final long z0 = lcgState;
            lcgState = (lcgA * lcgState) % lcgM;
            final long z1 = lcgState;
            // X[i] = (z0 + z1 * 2^32) mod 2^48
            // (z1 << 32) may carry into bits ≥ 48; mask to keep only the low 48.
            final long combined = (z0 + (z1 << 32)) & MASK48;
            state[i] = combined;
        }
        carry = (state[R - 1] == 0L) ? 1L : 0L;
        // Per the standard, index is set so the *next* engine call writes X[0]
        // (i.e. the engine is positioned at i = R-1; the next operator()
        // advances to i = 0). We model this by having index point to the slot
        // we are about to *generate* next: after seeding we want the next
        // generated value to land at index 0.
        index = 0;
    }

    /**
     * Advance the subtract-with-carry recurrence one step and return the
     * newly generated {@code X[i]} (in {@code [0, 2^48)}).
     * <p>
     * Recurrence: {@code Y = X[(i-S+R) mod R] - X[(i-R+R) mod R] - carry},
     * which simplifies (the X[(i-R) mod R] term is just X[i] itself prior
     * to update) to {@code Y = X[(i+R-S) mod R] - X[i] - carry}; if
     * {@code Y >= 0} then {@code X[i] = Y, carry = 0}, else
     * {@code X[i] = Y + 2^48, carry = 1}.
     */
    private long nextSubtractWithCarry() {
        final int i = index;
        final int iS = (i + R - S) % R; // (i - S) mod R
        long y = state[iS] - state[i] - carry;
        if (y < 0L) {
            y += M48;
            carry = 1L;
        } else {
            carry = 0L;
        }
        state[i] = y;
        index = (i + 1) % R;
        return y;
    }

    /**
     * discard_block_engine: returns base.next() but discards (P-R) of every
     * P outputs after returning the first R.
     */
    private long nextDiscardBlock() {
        // If we have already used all R "kept" outputs in the current block,
        // discard the remaining (P-R) and start a new block.
        if (blockUsed >= rUsed) {
            for (int k = rUsed; k < p; ++k) {
                nextSubtractWithCarry();
            }
            blockUsed = 0;
        }
        final long value = nextSubtractWithCarry();
        ++blockUsed;
        return value;
    }

    /**
     * Returns the next uniform sample in {@code (0, 1)}.
     * <p>
     * Mirrors C++ {@code next()}: {@code ranlux_() * (1.0 / 2^48)}, weight 1.0.
     */
    @Override
    public Sample<Double> next() {
        final long x = nextDiscardBlock();
        // C++: nx = 1.0 / (uint_fast64_t(1) << 48)
        final double v = x * (1.0 / (double) M48);
        return new Sample<Double>(v, 1.0);
    }

    /**
     * Not part of the C++ Ranlux API; provided to satisfy the JQuantLib
     * {@link RandomNumberGenerator} interface. Returns the low 32 bits of
     * the next 48-bit raw word.
     */
    @Override
    public long nextInt32() {
        return nextDiscardBlock() & 0xFFFFFFFFL;
    }
}

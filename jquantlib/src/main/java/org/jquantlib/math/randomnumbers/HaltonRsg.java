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

package org.jquantlib.math.randomnumbers;

import org.jquantlib.QL;
import org.jquantlib.math.PrimeNumbers;

/**
 * Halton low-discrepancy sequence generator.
 *
 * <p>Direct port of C++ v1.42.1 {@code ql/math/randomnumbers/haltonrsg.{hpp,cpp}}.
 * For algorithmic details see chapter 8, paragraph 2 of "Monte Carlo Methods in
 * Finance" by Peter J&auml;ckel.
 *
 * <p>Each call to {@link #nextSequence()} returns a {@link Sample} whose
 * {@code value} array contains one van-der-Corput coordinate per dimension
 * (the i-th dimension uses base {@code PrimeNumbers.get(i)}). The optional
 * {@code randomStart} offsets the per-dimension counter by a uniform
 * 32-bit integer drawn from a Mersenne-Twister-seeded RSG; the optional
 * {@code randomShift} adds a uniform [0,1) shift modulo 1 (Cranley-Patterson
 * rotation).
 */
public class HaltonRsg {

    /** Weighted Halton sample (value vector + scalar weight). */
    public static final class Sample {
        public final double[] value;
        public double weight;

        public Sample(final int dim) {
            this.value = new double[dim];
            this.weight = 1.0;
        }
    }

    private final int dimensionality_;
    private long sequenceCounter_;
    private final Sample sequence_;
    private final long[] randomStart_;
    private final double[] randomShift_;
    private final PrimeNumbers primes_;

    /** Convenience overload mirroring the C++ default arguments. */
    public HaltonRsg(final int dimensionality) {
        this(dimensionality, 0L, true, false);
    }

    public HaltonRsg(final int dimensionality, final long seed) {
        this(dimensionality, seed, true, false);
    }

    public HaltonRsg(final int dimensionality, final long seed,
                     final boolean randomStart, final boolean randomShift) {
        QL.require(dimensionality > 0, "dimensionality must be greater than 0");
        this.dimensionality_ = dimensionality;
        this.sequenceCounter_ = 0L;
        this.sequence_ = new Sample(dimensionality);
        this.randomStart_ = new long[dimensionality];
        this.randomShift_ = new double[dimensionality];
        this.primes_ = new PrimeNumbers();

        // Mirror C++ haltonrsg.cpp lines 46-53.
        if (randomStart || randomShift) {
            final RandomSequenceGenerator<MersenneTwisterUniformRng> uniformRsg =
                    new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                            MersenneTwisterUniformRng.class, dimensionality, seed);
            if (randomStart) {
                final long[] starts = uniformRsg.nextInt32Sequence();
                System.arraycopy(starts, 0, this.randomStart_, 0, dimensionality);
            }
            if (randomShift) {
                final double[] shifts = uniformRsg.nextSequence().value();
                System.arraycopy(shifts, 0, this.randomShift_, 0, dimensionality);
            }
        }
    }

    /**
     * Generate the next Halton sample. Mirrors C++ haltonrsg.cpp lines 56-72:
     * van der Corput radical inverse in base {@code p_i = primes(i)} of the
     * scalar counter (offset by the per-dimension {@code randomStart}), with
     * an optional Cranley-Patterson shift modulo 1.
     */
    public Sample nextSequence() {
        ++sequenceCounter_;
        for (int i = 0; i < dimensionality_; ++i) {
            double h = 0.0;
            final long b = primes_.get(i);
            double f = 1.0;
            long k = sequenceCounter_ + randomStart_[i];
            while (k != 0L) {
                f /= b;
                // Use fused multiply-add to match the reference C++ build,
                // which compiles `h += (k%b)*f` to a hardware FMA under the
                // default -ffp-contract=on. Without Math.fma the Java result
                // diverges from the C++ reference by 1 ULP on samples where
                // the unfused two-step rounding picks the other neighbour
                // (observed at sample 22, dim 4 / prime 7).
                h = Math.fma((double) (k % b), f, h);
                k /= b;
            }
            double v = h + randomShift_[i];
            // C++: sequence_.value[i] -= long(sequence_.value[i]);
            // i.e. truncate-to-zero of the *double* value, then subtract.
            v -= (long) v;
            sequence_.value[i] = v;
        }
        return sequence_;
    }

    public Sample lastSequence() {
        return sequence_;
    }

    public int dimension() {
        return dimensionality_;
    }
}

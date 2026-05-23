/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
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

import org.jquantlib.math.Constants;
import org.jquantlib.methods.montecarlo.Sample;

/**
 * L'Ecuyer uniform random-number generator with Bays-Durham shuffle (ran2 in Numerical Recipes in C, 2nd ed., §7.1).
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/randomnumbers/lecuyeruniformrng.{hpp,cpp}}.
 * Two combined linear-congruential streams (m1=2147483563, m2=2147483399) feed a 32-element shuffle table.
 * Cross-validated against C++ output (probe at /tmp/l1c_rng_probe).
 * <p>
 * <b>Cross-validation seeds:</b>
 * <ul>
 *   <li>seed=42: first 10 outputs match C++ to 1e-16 (see {@code LecuyerUniformRngTest}).</li>
 *   <li>seed=123456: first 10 outputs match C++ to 1e-16.</li>
 * </ul>
 *
 * @author Jose Moya
 */
public final class LecuyerUniformRng implements RandomNumberGenerator {

    // Two combined LCGs.
    private static final long M1 = 2147483563L;
    private static final long A1 = 40014L;
    private static final long Q1 = 53668L;
    private static final long R1 = 12211L;

    private static final long M2 = 2147483399L;
    private static final long A2 = 40692L;
    private static final long Q2 = 52774L;
    private static final long R2 = 3791L;

    private static final int BUFFER_SIZE = 32;

    // int(1 + (m1 - 1) / bufferSize) = int(1 + m1 / bufferSize)
    private static final long BUFFER_NORMALIZER = 67108862L;

    private static final double MAX_RANDOM = 1.0 - Constants.QL_EPSILON;

    private long temp1, temp2;
    private long y;
    private final long[] buffer = new long[BUFFER_SIZE];

    public LecuyerUniformRng() {
        this(0L);
    }

    public LecuyerUniformRng(final long seed) {
        temp2 = temp1 = (seed != 0L ? seed : System.currentTimeMillis());
        // Load the shuffle table (8 warm-up draws).
        for ( int j = BUFFER_SIZE + 7; j >= 0; --j ) {
            long k = temp1 / Q1;
            temp1 = A1 * (temp1 - k * Q1) - k * R1;
            if ( temp1 < 0L ) {
                temp1 += M1;
            }
            if ( j < BUFFER_SIZE ) {
                buffer[j] = temp1;
            }
        }
        y = buffer[0];
    }

    @Override
    public Sample< Double > next() {
        long k = temp1 / Q1;
        // Schrage's overflow-safe modular multiplication for stream 1.
        temp1 = A1 * (temp1 - k * Q1) - k * R1;
        if ( temp1 < 0L ) {
            temp1 += M1;
        }
        k = temp2 / Q2;
        // Schrage's method for stream 2.
        temp2 = A2 * (temp2 - k * Q2) - k * R2;
        if ( temp2 < 0L ) {
            temp2 += M2;
        }
        // Shuffle index in [0, bufferSize).
        final int j = (int) (y / BUFFER_NORMALIZER);
        // Combine streams.
        y = buffer[j] - temp2;
        buffer[j] = temp1;
        if ( y < 1L ) {
            y += M1 - 1L;
        }
        double result = (double) y / (double) M1;
        if ( result > MAX_RANDOM ) {
            result = MAX_RANDOM;
        }
        return new Sample< Double >(result, 1.0);
    }

    /**
     * L'Ecuyer's generator is rooted in 31-bit moduli; not naturally a uint32 stream. We re-scale the [0,1)
     * uniform to satisfy the {@link RandomNumberGenerator} interface; consumers needing bit-exact 32-bit
     * output should use {@link MersenneTwisterUniformRng}.
     */
    @Override
    public long nextInt32() {
        return (long) (next().value() * 4294967296.0) & 0xFFFFFFFFL;
    }
}

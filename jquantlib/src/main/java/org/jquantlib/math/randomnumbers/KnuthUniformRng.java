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

import org.jquantlib.methods.montecarlo.Sample;

/**
 * Knuth uniform random-number generator (Seminumerical Algorithms, 3rd ed., §3.6).
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/randomnumbers/knuthuniformrng.{hpp,cpp}}.
 * The {@code KK}/{@code LL}/{@code TT}/{@code QUALITY} constants and the bit-exact bootstrap match
 * the C++ source; cross-validated against C++ output (probe at /tmp/l1c_rng_probe).
 * <p>
 * <b>Cross-validation seeds:</b>
 * <ul>
 *   <li>seed=42: first 10 outputs match C++ to 1e-16 (see {@code KnuthUniformRngTest}).</li>
 *   <li>seed=123456: first 10 outputs match C++ to 1e-16.</li>
 * </ul>
 *
 * @author Jose Moya
 */
public final class KnuthUniformRng implements RandomNumberGenerator {

    private static final int KK = 100;
    private static final int LL = 37;
    private static final int TT = 70;
    private static final int QUALITY = 1009;

    private final double[] ranf_arr_buf;
    private int ranf_arr_ptr;
    private int ranf_arr_sentinel;
    private final double[] ran_u;

    /** Default-seed constructor (currently uses a clock-based fallback like C++ {@code SeedGenerator}). */
    public KnuthUniformRng() {
        this(0L);
    }

    /**
     * Constructor with explicit seed. If {@code seed == 0}, a clock-based seed is used (mirroring C++
     * {@code SeedGenerator::instance().get()}).
     */
    public KnuthUniformRng(final long seed) {
        this.ranf_arr_buf = new double[QUALITY];
        this.ran_u = new double[QUALITY];
        this.ranf_arr_ptr = QUALITY;
        this.ranf_arr_sentinel = QUALITY;
        // Note: when seed==0, C++ uses SeedGenerator::instance().get(); we approximate with currentTimeMillis().
        // For deterministic reference comparisons callers always pass an explicit seed.
        ranf_start(seed != 0L ? seed : System.currentTimeMillis());
    }

    private double mod_sum(final double x, final double y) {
        return (x + y) - (int) (x + y);
    }

    private boolean is_odd(final int s) {
        return (s & 1) != 0;
    }

    private void ranf_start(long seed) {
        int t, s, j;
        final double[] u = new double[KK + KK - 1];
        final double[] ul = new double[KK + KK - 1];
        // C++: double ulp = (1.0/(1L<<30))/(1L<<22); // 2^-52
        final double ulp = (1.0 / (1L << 30)) / (1L << 22);
        double ss = 2.0 * ulp * ((seed & 0x3fffffffL) + 2L);

        for ( j = 0; j < KK; j++ ) {
            u[j] = ss;
            ul[j] = 0.0;
            ss += ss;
            if ( ss >= 1.0 ) {
                ss -= 1.0 - 2.0 * ulp;
            }
        }
        for ( ; j < KK + KK - 1; j++ ) {
            u[j] = 0.0;
            ul[j] = 0.0;
        }
        u[1] += ulp;
        ul[1] = ulp;
        s = (int) (seed & 0x3fffffffL);
        t = TT - 1;
        while ( t != 0 ) {
            for ( j = KK - 1; j > 0; --j ) {
                ul[j + j] = ul[j];
                u[j + j] = u[j];
            }
            for ( j = KK + KK - 2; j > KK - LL; j -= 2 ) {
                ul[KK + KK - 1 - j] = 0.0;
                u[KK + KK - 1 - j] = u[j] - ul[j];
            }
            for ( j = KK + KK - 2; j >= KK; --j ) {
                if ( ul[j] != 0.0 ) {
                    ul[j - (KK - LL)] = ulp - ul[j - (KK - LL)];
                    u[j - (KK - LL)] = mod_sum(u[j - (KK - LL)], u[j]);
                    ul[j - KK] = ulp - ul[j - KK];
                    u[j - KK] = mod_sum(u[j - KK], u[j]);
                }
            }
            if ( is_odd(s) ) {
                for ( j = KK; j > 0; --j ) {
                    ul[j] = ul[j - 1];
                    u[j] = u[j - 1];
                }
                ul[0] = ul[KK];
                u[0] = u[KK];
                if ( ul[KK] != 0.0 ) {
                    ul[LL] = ulp - ul[LL];
                    u[LL] = mod_sum(u[LL], u[KK]);
                }
            }
            if ( s != 0 ) {
                s >>= 1;
            } else {
                t--;
            }
        }
        for ( j = 0; j < LL; j++ ) {
            ran_u[j + KK - LL] = u[j];
        }
        for ( ; j < KK; j++ ) {
            ran_u[j - LL] = u[j];
        }
    }

    private void ranf_array(final double[] aa, final int n) {
        int i, j;
        for ( j = 0; j < KK; j++ ) {
            aa[j] = ran_u[j];
        }
        for ( ; j < n; j++ ) {
            aa[j] = mod_sum(aa[j - KK], aa[j - LL]);
        }
        for ( i = 0; i < LL; i++, j++ ) {
            ran_u[i] = mod_sum(aa[j - KK], aa[j - LL]);
        }
        for ( ; i < KK; i++, j++ ) {
            ran_u[i] = mod_sum(aa[j - KK], ran_u[i - LL]);
        }
    }

    private double ranf_arr_cycle() {
        ranf_array(ranf_arr_buf, QUALITY);
        ranf_arr_ptr = 1;
        ranf_arr_sentinel = 100;
        return ranf_arr_buf[0];
    }

    @Override
    public Sample< Double > next() {
        final double result = (ranf_arr_ptr != ranf_arr_sentinel)
                ? ranf_arr_buf[ranf_arr_ptr++]
                : ranf_arr_cycle();
        return new Sample< Double >(result, 1.0);
    }

    /**
     * Knuth's generator naturally yields a {@code double} in {@code [0, 1)}, not a uint32. We multiply by 2^32
     * to satisfy the {@link RandomNumberGenerator} interface — callers wanting bit-exact 32-bit output should
     * prefer {@link MersenneTwisterUniformRng}. (KnuthUniformRng is rarely consumed via {@code nextInt32()};
     * the C++ class does not expose this method at all.)
     */
    @Override
    public long nextInt32() {
        return (long) (next().value() * 4294967296.0) & 0xFFFFFFFFL;
    }
}

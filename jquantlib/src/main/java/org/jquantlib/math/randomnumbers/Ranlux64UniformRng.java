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

/**
 * Luescher's "luxury" uniform random-number generator template, parametrized by
 * block-size {@code P} and used-count {@code R} (a {@code discard_block_engine}
 * wrapping a {@code subtract_with_carry_engine<uint_fast64_t, 48, 10, 24>}).
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/randomnumbers/ranluxuniformrng.hpp}
 * (Klaus Spanderen, 2009).
 * <p>
 * The C++ source declares this as a class template:
 * {@code template <std::size_t P, std::size_t R> class Ranlux64UniformRng}; the
 * QuantLib-public {@code Ranlux3UniformRng} and {@code Ranlux4UniformRng} are typedefs.
 * Java does not have value-template parameters, so this class is a thin factory facade
 * that delegates to the bit-exact algorithm already implemented in {@link RanluxUniformRng}
 * via its {@code ranlux3()}/{@code ranlux4()} static factory methods.
 *
 * @author Jose Moya
 */
public final class Ranlux64UniformRng {

    private Ranlux64UniformRng() {
        // Static-factory facade; no instances.
    }

    /** Ranlux3 = {@code Ranlux64UniformRng<223, 24>}. */
    public static RandomNumberGenerator ranlux3() {
        return RanluxUniformRng.ranlux3();
    }

    /** Ranlux3 with explicit seed. */
    public static RandomNumberGenerator ranlux3(final long seed) {
        return RanluxUniformRng.ranlux3(seed);
    }

    /** Ranlux4 = {@code Ranlux64UniformRng<389, 24>} (highest luxury). */
    public static RandomNumberGenerator ranlux4() {
        return RanluxUniformRng.ranlux4();
    }

    /** Ranlux4 with explicit seed. */
    public static RandomNumberGenerator ranlux4(final long seed) {
        return RanluxUniformRng.ranlux4(seed);
    }
}

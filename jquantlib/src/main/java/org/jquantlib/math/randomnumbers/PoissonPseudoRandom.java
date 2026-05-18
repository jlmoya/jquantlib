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

import org.jquantlib.math.distributions.InverseCumulativePoisson;

/**
 * Traits for Poisson-distributed pseudo-random number generation.
 * <p>
 * Java port of the C++ typedef
 * <pre>
 *   typedef GenericPseudoRandom&lt;MersenneTwisterUniformRng,
 *                                InverseCumulativePoisson&gt; PoissonPseudoRandom;
 * </pre>
 * defined in {@code ql/math/randomnumbers/rngtraits.hpp} (v1.42.1).
 *
 * <p>The C++ class template carries:
 * <ul>
 *   <li>a static {@code shared_ptr<IC>} {@code icInstance} — if non-null,
 *       used to construct the {@link InverseCumulativeRsg};</li>
 *   <li>a static factory {@code make_sequence_generator(dimension, seed)} that
 *       builds a {@link RandomSequenceGenerator} over a
 *       {@link MersenneTwisterUniformRng} and wraps it in an
 *       {@link InverseCumulativeRsg} parameterised by
 *       {@link InverseCumulativePoisson}.</li>
 * </ul>
 *
 * <p>This class mirrors that contract one-for-one. The legacy
 * {@link GenericPseudoRandom} reflection-based plumbing is bypassed because
 * it is incomplete (the {@code rsgClass} lookup is unfinished); the C++
 * typedef monomorphises the template at the call site, so we do the same.
 *
 * @author JQuantLib migration contributors
 */
public final class PoissonPseudoRandom {

    /**
     * Optional shared inverse-cumulative functor. If {@code null}, each
     * call to {@link #makeSequenceGenerator(int, long)} constructs the
     * default {@link InverseCumulativePoisson} (lambda = 1.0).
     *
     * <p>Mirrors the C++ static member {@code PoissonPseudoRandom::icInstance}.
     */
    public static InverseCumulativePoisson icInstance = null;

    /**
     * Whether this generator type supports an error estimate. C++ sets
     * {@code allowsErrorEstimate = 1} in the {@code GenericPseudoRandom}
     * traits.
     */
    public static final boolean ALLOWS_ERROR_ESTIMATE = true;

    private PoissonPseudoRandom() {
        // utility class — not instantiable
    }

    /**
     * Build an inverse-cumulative random sequence generator over a
     * Mersenne-Twister uniform sequence, mapped through the Poisson
     * inverse CDF.
     *
     * <p>Equivalent to:
     * <pre>
     *   ursg_type g(dimension, seed);
     *   return (icInstance ? rsg_type(g, *icInstance) : rsg_type(g));
     * </pre>
     * in {@code rngtraits.hpp}.
     *
     * @param dimension sequence dimensionality (≥ 1)
     * @param seed      RNG seed
     * @return a properly wired {@link InverseCumulativeRsg}
     */
    public static InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                       InverseCumulativePoisson>
    makeSequenceGenerator(final int dimension, final long seed) {

        final MersenneTwisterUniformRng rng = new MersenneTwisterUniformRng(seed);
        final RandomSequenceGenerator<MersenneTwisterUniformRng> ursg =
                new RandomSequenceGenerator<MersenneTwisterUniformRng>(
                        MersenneTwisterUniformRng.class, dimension, rng);

        if (icInstance != null) {
            return new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                            InverseCumulativePoisson>(ursg, icInstance);
        }
        return new InverseCumulativeRsg<RandomSequenceGenerator<MersenneTwisterUniformRng>,
                                        InverseCumulativePoisson>(ursg, new InverseCumulativePoisson());
    }
}

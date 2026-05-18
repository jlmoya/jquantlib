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

import org.jquantlib.math.distributions.InverseCumulativeNormal;

/**
 * Default traits for low-discrepancy sequence generation.
 *
 * <p>Java port of the C++ typedef
 * <pre>
 *   typedef GenericLowDiscrepancy&lt;SobolRsg,
 *                                  InverseCumulativeNormal&gt; LowDiscrepancy;
 * </pre>
 * defined in {@code ql/math/randomnumbers/rngtraits.hpp} (v1.42.1).
 *
 * <p>C++ {@code GenericLowDiscrepancy} carries:
 * <ul>
 *   <li>a static {@code shared_ptr<IC>} {@code icInstance} — if non-null,
 *       used to construct the {@link InverseCumulativeRsg};</li>
 *   <li>a static factory {@code make_sequence_generator(dimension, seed)} that
 *       builds a {@link SobolRsg} and wraps it in an
 *       {@link InverseCumulativeRsg} parameterised by
 *       {@link InverseCumulativeNormal};</li>
 *   <li>{@code allowsErrorEstimate = 0} — Sobol paths do not produce an
 *       independent-sample error estimate.</li>
 * </ul>
 *
 * <p>This class mirrors that contract one-for-one, following the
 * {@link PoissonPseudoRandom} pattern (standalone final traits class rather
 * than going through the legacy reflection-based {@link GenericLowDiscrepancy}
 * plumbing, which is incomplete).
 *
 * @author JQuantLib migration contributors
 */
public final class LowDiscrepancy {

    /**
     * Optional shared inverse-cumulative functor. If {@code null}, each
     * call to {@link #makeSequenceGenerator(int, long)} constructs the
     * default {@link InverseCumulativeNormal}.
     *
     * <p>Mirrors the C++ static member {@code LowDiscrepancy::icInstance}.
     */
    public static InverseCumulativeNormal icInstance = null;

    /**
     * Whether this generator type supports an error estimate. C++ sets
     * {@code allowsErrorEstimate = 0} in {@code GenericLowDiscrepancy}.
     */
    public static final boolean ALLOWS_ERROR_ESTIMATE = false;

    private LowDiscrepancy() {
        // utility class — not instantiable
    }

    /**
     * Build an inverse-cumulative random sequence generator over a
     * {@link SobolRsg} low-discrepancy sequence, mapped through the standard
     * normal inverse CDF.
     *
     * <p>Equivalent to:
     * <pre>
     *   ursg_type g(dimension, seed);
     *   return (icInstance ? rsg_type(g, *icInstance) : rsg_type(g));
     * </pre>
     * in {@code rngtraits.hpp}.
     *
     * @param dimension sequence dimensionality (&ge; 1)
     * @param seed      Sobol seed
     * @return a properly wired {@link InverseCumulativeRsg} over Sobol +
     *         inverse normal CDF
     */
    public static InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal>
    makeSequenceGenerator(final int dimension, final long seed) {

        final SobolRsg ursg = new SobolRsg(dimension, seed);

        if (icInstance != null) {
            return new InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal>(
                    ursg, icInstance);
        }
        return new InverseCumulativeRsg<SobolRsg, InverseCumulativeNormal>(
                ursg, new InverseCumulativeNormal());
    }
}

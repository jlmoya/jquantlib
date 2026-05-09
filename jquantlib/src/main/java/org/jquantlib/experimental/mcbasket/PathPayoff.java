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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import java.util.List;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.util.PolymorphicVisitable;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Abstract base class for path-dependent option payoffs over a multi-asset
 * path.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/pathpayoff.{hpp,cpp}}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Subclasses must implement {@link #value(Matrix, List, Array, Array, List)}
 * which fills:
 * <ul>
 *   <li>{@code payments[i]} — payment at time index {@code i};</li>
 *   <li>{@code exercises[i]} — exercise payoff at time {@code i} (cancelling
 *       payments after {@code i});</li>
 *   <li>{@code states[i]} — state vector for the LSM regression at time
 *       {@code i}; an empty array signals exercise is not allowed there.</li>
 * </ul>
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>The C++ {@code AcyclicVisitor} is mapped to JQuantLib's
 *       {@link PolymorphicVisitor} (existing project convention).</li>
 *   <li>{@code std::vector<Array>} for {@code states} maps to
 *       {@link java.util.List java.util.List}{@code <Array>} in Java; engines
 *       are expected to pre-allocate it (or pass an empty list to signal
 *       "no states").</li>
 * </ul>
 */
public abstract class PathPayoff implements PolymorphicVisitable {

    /**
     * Mirrors C++ {@code name()}: identifier used for output and equality;
     * not for runtime dispatch.
     */
    public abstract String name();

    /** Mirrors C++ {@code description()}: human-readable label. */
    public abstract String description();

    /**
     * Computes the payments and early-termination payoffs for a single
     * multi-asset path.
     *
     * <p>If the option is cancelled at time index {@code i}, all payments at
     * indices {@code <= i} are taken into account plus the value of
     * {@code exercises[i]}; cancellation at {@code i} does <i>not</i> cancel
     * {@code payments[i]}.
     *
     * <p>Pass an empty {@code states} list to indicate exercise is not
     * possible (in that case {@code exercises} will not be touched).
     *
     * @param path matrix of shape {@code (numAssets, numTimes)}
     * @param forwardTermStructures yield term structure handle at each fixing
     * @param payments output array of size {@code numTimes}
     * @param exercises output array of size {@code numTimes} (may be empty)
     * @param states output list of size {@code numTimes} (may be empty)
     */
    public abstract void value(
            final Matrix path,
            final List<Handle<YieldTermStructure>> forwardTermStructures,
            final Array payments,
            final Array exercises,
            final List<Array> states);

    /**
     * Dimension of the basis-function system. Must equal the size of every
     * non-empty entry of {@code states} produced by
     * {@link #value(Matrix, List, Array, Array, List)}.
     */
    public abstract int basisSystemDimension();

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<PathPayoff> v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            throw new LibraryException("not a path-payoff visitor");
        }
    }
}

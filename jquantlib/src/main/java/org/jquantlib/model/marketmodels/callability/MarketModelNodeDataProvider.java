/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.2.

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
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels.callability;

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;

/**
 * Common base for callability node-data providers (basis systems and
 * parametric exercises).
 *
 * <p>Java port of {@code MarketModelNodeDataProvider}
 * (ql/models/marketmodels/callability/nodedataprovider.hpp v1.42.1).
 *
 * <p>The Java translation uses {@code int[]} for {@code std::vector<Size>} and
 * {@code boolean[]} for {@code std::valarray<bool>}, and uses an in-place
 * {@code values(CurveState, double[])} signature.
 *
 * @see "ql/models/marketmodels/callability/nodedataprovider.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public interface MarketModelNodeDataProvider {

    /** Returns the number of exercise opportunities. */
    int numberOfExercises();

    /** Returns the size of the {@code values} array per exercise. */
    int[] numberOfData();

    /**
     * Returns the evolution description (including any time at which state
     * should be updated).
     */
    EvolutionDescription evolution();

    /** Advances the provider by one simulation step. */
    void nextStep(CurveState currentState);

    /** Resets the provider to the start of a new simulation path. */
    void reset();

    /**
     * Returns, for each evolution time, whether it is an exercise time.
     * Mirrors C++ {@code std::valarray<bool> isExerciseTime() const}.
     */
    boolean[] isExerciseTime();

    /**
     * Fills {@code results} with the basis-function or parametric-variable
     * values for the current state. Length is
     * {@code numberOfData()[currentExerciseIndex]}.
     * Mirrors C++ {@code void values(const CurveState&, std::vector<Real>&)}.
     */
    void values(CurveState currentState, double[] results);

    /**
     * Resizing variant: most concrete implementations need to grow the result
     * array. Default delegates to {@link #values(CurveState, double[])} with
     * the supplied container.
     */
    default double[] values(final CurveState currentState) {
        // helper for callers who want a fresh array; size determined by impl
        throw new UnsupportedOperationException(
                "use values(CurveState, double[]) and pre-size the array");
    }
}

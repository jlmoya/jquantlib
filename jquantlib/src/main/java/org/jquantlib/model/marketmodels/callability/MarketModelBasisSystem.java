/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.3.

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

/**
 * Basis system for Longstaff-Schwartz regression.
 *
 * <p>Java port of {@code MarketModelBasisSystem}
 * (ql/models/marketmodels/callability/marketmodelbasissystem.hpp v1.42.1).
 *
 * <p>A basis system supplies, at each exercise opportunity, a vector of
 * regression-basis function values evaluated at the current curve state. The
 * regression of cumulated cash flows onto these basis functions yields the
 * continuation-value coefficients used by
 * {@link LongstaffSchwartzExerciseStrategy}.
 *
 * <p>Java port note: Java does not support multiple inheritance, so
 * {@link MarketModelBasisSystem} is declared as an interface that
 * <em>extends</em> {@link MarketModelNodeDataProvider}; concrete subclasses
 * (e.g. {@link SwapBasisSystem}) implement both, with
 * {@link #numberOfData()} returning {@link #numberOfFunctions()}.
 *
 * @see "ql/models/marketmodels/callability/marketmodelbasissystem.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public interface MarketModelBasisSystem extends MarketModelNodeDataProvider {

    /**
     * Returns the number of basis functions per exercise opportunity.
     * Possibly different for each exercise.
     */
    int[] numberOfFunctions();

    /**
     * Returns a newly-allocated copy of this basis system.
     * Mirrors C++ {@code std::unique_ptr<MarketModelBasisSystem> clone()}.
     */
    MarketModelBasisSystem clone();

    /** Default implementation returns {@link #numberOfFunctions()}. */
    @Override
    default int[] numberOfData() {
        return numberOfFunctions();
    }
}

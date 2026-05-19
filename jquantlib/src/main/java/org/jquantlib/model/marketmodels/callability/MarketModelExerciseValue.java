/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.1.

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
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;

/**
 * Exercise-value provider for callable market-model products.
 *
 * <p>Java port of {@code MarketModelExerciseValue}
 * (ql/models/marketmodels/callability/exercisevalue.hpp v1.42.1).
 *
 * <p>Encapsulates the exercise value of a callable instrument at each
 * evolution time. Used by {@link LongstaffSchwartzExerciseStrategy} as the rebate component, and by
 * {@code ExerciseAdapter} (Track A) to bridge to {@link MarketModelMultiProduct}.
 *
 * <p>P3K-2: C++ {@code std::valarray<bool>} maps to Java {@code boolean[]}.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/callability/exercisevalue.hpp" v1.42.1
 */
public interface MarketModelExerciseValue {

    /** Returns the number of exercise opportunities. */
    int numberOfExercises();

    /**
     * Returns the evolution description (rate times + evolution times, including any time at which state should be
     * updated).
     */
    EvolutionDescription evolution();

    /** Returns the times at which cash flows can occur. */
    double[] possibleCashFlowTimes();

    /** Advances the provider by one simulation step. */
    void nextStep(CurveState currentState);

    /** Resets the provider to the start of a new simulation path. */
    void reset();

    /**
     * Returns, for each evolution time, whether it is an exercise time. Mirrors C++
     * {@code std::valarray<bool> isExerciseTime() const}.
     */
    boolean[] isExerciseTime();

    /** Returns the current exercise cash flow given the current state. */
    MarketModelMultiProduct.CashFlow value(CurveState currentState);

    /** Returns a newly-allocated copy of this provider. */
    MarketModelExerciseValue clone();
}

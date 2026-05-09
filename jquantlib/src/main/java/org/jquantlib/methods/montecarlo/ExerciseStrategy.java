/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k L0.1.

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

package org.jquantlib.methods.montecarlo;

import org.jquantlib.model.marketmodels.CurveState;

/**
 * Exercise strategy interface for LMM/market-model Monte Carlo pricing.
 * <p>
 * Mirrors C++ {@code ExerciseStrategy<CurveState>} template
 * (ql/methods/montecarlo/exercisestrategy.hpp v1.42.1). The C++ template is
 * always instantiated with {@code CurveState} in the market-model context, so
 * the Java interface is non-generic (per design decision P3K-2).
 * <p>
 * An exercise strategy encapsulates the rule for deciding, at each simulation
 * node, whether the holder of a callable instrument should exercise.
 *
 * @see "ql/methods/montecarlo/exercisestrategy.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public interface ExerciseStrategy {

    /**
     * Returns the sorted list of times at which exercise is possible.
     * Mirrors C++ {@code std::vector<Time> exerciseTimes() const}.
     */
    double[] exerciseTimes();

    /**
     * Returns the sorted list of all times relevant to the strategy
     * (a superset of {@link #exerciseTimes()} — may include observation times).
     * Mirrors C++ {@code std::vector<Time> relevantTimes() const}.
     */
    double[] relevantTimes();

    /**
     * Resets the strategy to the start of a new simulation path.
     * Mirrors C++ {@code void reset()}.
     */
    void reset();

    /**
     * Returns {@code true} if the strategy recommends exercising at the
     * current state.
     * Mirrors C++ {@code bool exercise(const CurveState&) const}.
     *
     * @param currentState the current yield-curve state
     * @return {@code true} to exercise, {@code false} to continue
     */
    boolean exercise(CurveState currentState);

    /**
     * Advances the strategy by one simulation step using the current state.
     * Must be called after {@link #exercise} at each exercise time.
     * Mirrors C++ {@code void nextStep(const CurveState&)}.
     *
     * @param currentState the current yield-curve state
     */
    void nextStep(CurveState currentState);

    /**
     * Returns a newly-allocated copy of itself.
     * Mirrors C++ {@code std::unique_ptr<ExerciseStrategy<State>> clone() const}.
     */
    ExerciseStrategy clone();
}

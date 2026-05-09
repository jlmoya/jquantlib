/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.9.

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

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.ExerciseStrategy;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Naif callable exercise strategy: exercise when the coterminal swap rate
 * exceeds a per-exercise trigger.
 *
 * <p>Java port of {@code SwapRateTrigger}
 * (ql/models/marketmodels/callability/swapratetrigger.{hpp,cpp} v1.42.1).
 *
 * <p>Used by {@code testCallableSwapNaif} as a simple alternative to a
 * Longstaff-Schwartz strategy.
 *
 * @see "ql/models/marketmodels/callability/swapratetrigger.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class SwapRateTrigger implements ExerciseStrategy {

    private final double[] rateTimes_;
    private final double[] swapTriggers_;
    private final double[] exerciseTimes_;
    private final int[] rateIndex_;
    // evolving state - C++ leaves currentIndex_ uninitialised; reset() always
    // sets it to 0 before use.
    private int currentIndex_ = 0;

    public SwapRateTrigger(final double[] rateTimes,
                           final double[] swapTriggers,
                           final double[] exerciseTimes) {
        Utilities.checkIncreasingTimes(rateTimes);
        QL.require(rateTimes.length > 1,
                "Rate times must contain at least two values");
        Utilities.checkIncreasingTimes(exerciseTimes);
        QL.require(swapTriggers.length == exerciseTimes.length,
                "swapTriggers/exerciseTimes mismatch: " + swapTriggers.length
                        + " != " + exerciseTimes.length);

        this.rateTimes_ = rateTimes.clone();
        this.swapTriggers_ = swapTriggers.clone();
        this.exerciseTimes_ = exerciseTimes.clone();
        this.rateIndex_ = new int[exerciseTimes.length];

        int j = 0;
        for (int i = 0; i < exerciseTimes.length; ++i) {
            while (j < rateTimes.length && rateTimes[j] < exerciseTimes[i]) {
                ++j;
            }
            this.rateIndex_[i] = j;
        }
    }

    private SwapRateTrigger(final SwapRateTrigger other) {
        this.rateTimes_ = other.rateTimes_.clone();
        this.swapTriggers_ = other.swapTriggers_.clone();
        this.exerciseTimes_ = other.exerciseTimes_.clone();
        this.rateIndex_ = other.rateIndex_.clone();
        this.currentIndex_ = other.currentIndex_;
    }

    @Override public double[] exerciseTimes() { return exerciseTimes_; }

    @Override public double[] relevantTimes() { return exerciseTimes_; }

    @Override public void reset() { currentIndex_ = 0; }

    @Override public boolean exercise(final CurveState currentState) {
        final int rateIndex = rateIndex_[currentIndex_ - 1];
        final double currentSwapRate = currentState.coterminalSwapRate(rateIndex);
        return swapTriggers_[currentIndex_ - 1] < currentSwapRate;
    }

    @Override public void nextStep(final CurveState currentState) { ++currentIndex_; }

    @Override public SwapRateTrigger clone() { return new SwapRateTrigger(this); }
}

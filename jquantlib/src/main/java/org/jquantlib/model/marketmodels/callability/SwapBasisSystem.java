/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.7.

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

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;

/**
 * Basis system using forward rate + coterminal swap rate per exercise time.
 *
 * <p>Java port of {@code SwapBasisSystem}
 * (ql/models/marketmodels/callability/swapbasissystem.{hpp,cpp} v1.42.1).
 *
 * <p>Per exercise opportunity, supplies basis function values:
 * {@code [1, forward(rateIndex), coterminalSwapRate(rateIndex+1)]} (3
 * functions), except at the last exercise where there is no further
 * coterminal swap rate available, in which case only
 * {@code [1, forward(rateIndex)]} (2 functions) are returned.
 *
 * @see "ql/models/marketmodels/callability/swapbasissystem.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class SwapBasisSystem implements MarketModelBasisSystem {

    private final double[] rateTimes_;
    private final double[] exerciseTimes_;
    private final int[] rateIndex_;
    private final EvolutionDescription evolution_;
    // evolving state - C++ leaves currentIndex_ uninitialised; we explicitly
    // init to 0 (reset() sets it to 0 before any use, so this is safe).
    private int currentIndex_ = 0;

    public SwapBasisSystem(final double[] rateTimes, final double[] exerciseTimes) {
        this.rateTimes_ = rateTimes.clone();
        this.exerciseTimes_ = exerciseTimes.clone();
        this.rateIndex_ = new int[exerciseTimes.length];
        this.evolution_ = new EvolutionDescription(rateTimes, exerciseTimes);
        // Build rateIndex_[i] = lower-bound rate-time index for exerciseTimes[i]
        int j = 0;
        for (int i = 0; i < exerciseTimes.length; ++i) {
            while (j < rateTimes.length && rateTimes[j] < exerciseTimes[i]) {
                ++j;
            }
            this.rateIndex_[i] = j;
        }
    }

    /** Copy constructor. */
    private SwapBasisSystem(final SwapBasisSystem other) {
        this.rateTimes_ = other.rateTimes_.clone();
        this.exerciseTimes_ = other.exerciseTimes_.clone();
        this.rateIndex_ = other.rateIndex_.clone();
        this.evolution_ = other.evolution_;
        this.currentIndex_ = other.currentIndex_;
    }

    @Override public int numberOfExercises() { return exerciseTimes_.length; }

    @Override public int[] numberOfFunctions() {
        final int[] sizes = new int[exerciseTimes_.length];
        Arrays.fill(sizes, 3);
        if (rateIndex_[exerciseTimes_.length - 1] == rateTimes_.length - 2) {
            sizes[sizes.length - 1] = 2;
        }
        return sizes;
    }

    @Override public EvolutionDescription evolution() { return evolution_; }

    @Override public void nextStep(final CurveState s) { ++currentIndex_; }

    @Override public void reset() { currentIndex_ = 0; }

    @Override public boolean[] isExerciseTime() {
        final boolean[] r = new boolean[exerciseTimes_.length];
        Arrays.fill(r, true);
        return r;
    }

    @Override public void values(final CurveState currentState, final double[] results) {
        // C++ semantics: results.reserve(3); results.resize(2); results[0]=1.0;
        //                results[1]=forwardRate(rateIndex);
        //   if (rateIndex < rateTimes.size()-2)
        //     results.push_back(coterminalSwapRate(rateIndex+1));
        // Java port note: we cannot resize a primitive array. The caller must
        // pre-size results to numberOfFunctions()[currentExerciseIndex] before
        // calling. We assert that here.
        final int rateIndex = rateIndex_[currentIndex_ - 1];
        final boolean hasThree = rateIndex < rateTimes_.length - 2;
        final int needed = hasThree ? 3 : 2;
        QL.require(results.length == needed,
                "results array length (" + results.length
                        + ") must equal numberOfFunctions[currentExercise]="
                        + needed);
        results[0] = 1.0;
        results[1] = currentState.forwardRate(rateIndex);
        if (hasThree) {
            results[2] = currentState.coterminalSwapRate(rateIndex + 1);
        }
    }

    @Override public SwapBasisSystem clone() {
        return new SwapBasisSystem(this);
    }
}

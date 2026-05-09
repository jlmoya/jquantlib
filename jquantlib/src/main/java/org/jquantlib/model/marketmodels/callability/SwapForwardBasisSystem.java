/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.8.

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
 Copyright (C) 2009 Mark Joshi
*/

package org.jquantlib.model.marketmodels.callability;

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;

/**
 * Polynomial basis system mixing forward + coterminal swap rates +
 * discount-ratio cross-terms.
 *
 * <p>Java port of {@code SwapForwardBasisSystem}
 * (ql/models/marketmodels/callability/swapforwardbasissystem.{hpp,cpp} v1.42.1).
 *
 * <p>Returns either 10, 6 or 3 basis function values depending on how close
 * the exercise time is to the end of the rate-time grid:
 * <ul>
 *   <li>{@code rateIndex < n-3}: 10 functions:
 *       {@code 1, x, y, z, xy, yz, zx, x^2, y^2, z^2}
 *       where {@code x = forward(i)}, {@code y = coterminalSwapRate(i+1)},
 *       {@code z = discountRatio(i, n-1)}.</li>
 *   <li>{@code rateIndex == n-3}: 6 functions:
 *       {@code 1, x, y, x^2, xy, y^2} where {@code x = forward(i)},
 *       {@code y = forward(i+1)}.</li>
 *   <li>{@code rateIndex == n-2}: 3 functions:
 *       {@code 1, x, x^2} where {@code x = forward(i)}.</li>
 * </ul>
 *
 * @see "ql/models/marketmodels/callability/swapforwardbasissystem.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class SwapForwardBasisSystem implements MarketModelBasisSystem {

    private final double[] rateTimes_;
    private final double[] exerciseTimes_;
    private final int[] rateIndex_;
    private final EvolutionDescription evolution_;
    private int currentIndex_ = 0;

    public SwapForwardBasisSystem(final double[] rateTimes, final double[] exerciseTimes) {
        this.rateTimes_ = rateTimes.clone();
        this.exerciseTimes_ = exerciseTimes.clone();
        this.rateIndex_ = new int[exerciseTimes.length];
        this.evolution_ = new EvolutionDescription(rateTimes, exerciseTimes);
        int j = 0;
        for (int i = 0; i < exerciseTimes.length; ++i) {
            while (j < rateTimes.length && rateTimes[j] < exerciseTimes[i]) {
                ++j;
            }
            this.rateIndex_[i] = j;
        }
    }

    private SwapForwardBasisSystem(final SwapForwardBasisSystem other) {
        this.rateTimes_ = other.rateTimes_.clone();
        this.exerciseTimes_ = other.exerciseTimes_.clone();
        this.rateIndex_ = other.rateIndex_.clone();
        this.evolution_ = other.evolution_;
        this.currentIndex_ = other.currentIndex_;
    }

    @Override public int numberOfExercises() { return exerciseTimes_.length; }

    @Override public int[] numberOfFunctions() {
        final int[] sizes = new int[exerciseTimes_.length];
        Arrays.fill(sizes, 10);
        final int last = exerciseTimes_.length - 1;
        if (rateIndex_[last] == rateTimes_.length - 3) {
            sizes[last] = 6;
        }
        if (rateIndex_[last] == rateTimes_.length - 2) {
            sizes[last] = 3;
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
        final int rateIndex = rateIndex_[currentIndex_ - 1];
        if (rateIndex < rateTimes_.length - 3) {
            QL.require(results.length == 10,
                    "results length (" + results.length
                            + ") must equal 10 for non-tail exercise");
            final double x = currentState.forwardRate(rateIndex);
            final double y = currentState.coterminalSwapRate(rateIndex + 1);
            final double z = currentState.discountRatio(rateIndex, rateTimes_.length - 1);
            results[0] = 1.0;
            results[1] = x;
            results[2] = y;
            results[3] = z;
            results[4] = x * y;
            results[5] = y * z;
            results[6] = z * x;
            results[7] = x * x;
            results[8] = y * y;
            results[9] = z * z;
        } else if (rateIndex == rateTimes_.length - 3) {
            QL.require(results.length == 6,
                    "results length (" + results.length
                            + ") must equal 6 for second-to-last exercise");
            final double x = currentState.forwardRate(rateIndex);
            final double y = currentState.forwardRate(rateIndex + 1);
            results[0] = 1.0;
            results[1] = x;
            results[2] = y;
            results[3] = x * x;
            results[4] = x * y;
            results[5] = y * y;
        } else {
            QL.require(results.length == 3,
                    "results length (" + results.length
                            + ") must equal 3 for last exercise");
            final double x = currentState.forwardRate(rateIndex);
            results[0] = 1.0;
            results[1] = x;
            results[2] = x * x;
        }
    }

    @Override public SwapForwardBasisSystem clone() {
        return new SwapForwardBasisSystem(this);
    }
}

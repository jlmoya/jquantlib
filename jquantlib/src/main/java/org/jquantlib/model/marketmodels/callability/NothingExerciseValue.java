/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.5.

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
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Trivial exercise value provider that always returns zero.
 *
 * <p>Java port of {@code NothingExerciseValue}
 * (ql/models/marketmodels/callability/nothingexercisevalue.{hpp,cpp} v1.42.1).
 *
 * <p>Used as a "null" rebate in callable products where the holder simply
 * loses all future cash flows on exercise (no terminal payoff).
 *
 * @see "ql/models/marketmodels/callability/nothingexercisevalue.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class NothingExerciseValue implements MarketModelExerciseValue {

    private final int numberOfExercises_;
    private final double[] rateTimes_;
    private final boolean[] isExerciseTime_;
    private final EvolutionDescription evolution_;
    // evolving state
    private int currentIndex_ = 0;
    private final MarketModelMultiProduct.CashFlow cf_ = new MarketModelMultiProduct.CashFlow();

    /** Convenience constructor with all-true exercise flags. */
    public NothingExerciseValue(final double[] rateTimes) {
        this(rateTimes, null);
    }

    public NothingExerciseValue(final double[] rateTimes, final boolean[] isExerciseTime) {
        Utilities.checkIncreasingTimes(rateTimes);
        QL.require(rateTimes.length >= 2,
                "Rate times must contain at least two values");
        this.rateTimes_ = rateTimes.clone();
        this.cf_.amount = 0.0;
        // evolutionTimes = rateTimes minus the last entry
        final double[] evolutionTimes = Arrays.copyOf(rateTimes, rateTimes.length - 1);
        this.evolution_ = new EvolutionDescription(this.rateTimes_, evolutionTimes);

        final int n = rateTimes.length - 1;
        if (isExerciseTime == null || isExerciseTime.length == 0) {
            this.isExerciseTime_ = new boolean[n];
            Arrays.fill(this.isExerciseTime_, true);
        } else {
            QL.require(isExerciseTime.length == n,
                    "isExerciseTime (" + isExerciseTime.length
                            + ") must have same size as rateTimes minus 1 (" + n + ")");
            this.isExerciseTime_ = isExerciseTime.clone();
        }
        int count = 0;
        for (final boolean b : this.isExerciseTime_) {
            if (b) ++count;
        }
        this.numberOfExercises_ = count;
    }

    /** Copy constructor (for {@link #clone()}). */
    private NothingExerciseValue(final NothingExerciseValue other) {
        this.numberOfExercises_ = other.numberOfExercises_;
        this.rateTimes_ = other.rateTimes_.clone();
        this.isExerciseTime_ = other.isExerciseTime_.clone();
        this.evolution_ = other.evolution_;
        this.currentIndex_ = other.currentIndex_;
        this.cf_.timeIndex = other.cf_.timeIndex;
        this.cf_.amount = other.cf_.amount;
    }

    @Override public int numberOfExercises() { return numberOfExercises_; }

    @Override public EvolutionDescription evolution() { return evolution_; }

    @Override public double[] possibleCashFlowTimes() { return rateTimes_; }

    @Override public void reset() { currentIndex_ = 0; }

    @Override public void nextStep(final CurveState currentState) {
        cf_.timeIndex = currentIndex_;
        ++currentIndex_;
    }

    @Override public boolean[] isExerciseTime() { return isExerciseTime_; }

    @Override public MarketModelMultiProduct.CashFlow value(final CurveState currentState) {
        return cf_;
    }

    @Override public NothingExerciseValue clone() {
        return new NothingExerciseValue(this);
    }
}

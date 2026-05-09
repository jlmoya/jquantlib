/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.6.

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
import org.jquantlib.instruments.Payoff;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Bermudan-swaption exercise value: at each evolution time, computes
 * {@code annuity * payoff(coterminalSwapRate)}.
 *
 * <p>Java port of {@code BermudanSwaptionExerciseValue}
 * (ql/models/marketmodels/callability/bermudanswaptionexercisevalue.{hpp,cpp}
 * v1.42.1).
 *
 * @see "ql/models/marketmodels/callability/bermudanswaptionexercisevalue.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class BermudanSwaptionExerciseValue implements MarketModelExerciseValue {

    private final int numberOfExercises_;
    private final double[] rateTimes_;
    private final Payoff[] payoffs_;
    private final EvolutionDescription evolution_;
    // evolving state
    private int currentIndex_ = 0;
    private final MarketModelMultiProduct.CashFlow cf_ = new MarketModelMultiProduct.CashFlow();

    public BermudanSwaptionExerciseValue(final double[] rateTimes, final Payoff[] payoffs) {
        Utilities.checkIncreasingTimes(rateTimes);
        this.numberOfExercises_ = rateTimes.length == 0 ? 0 : rateTimes.length - 1;
        QL.require(numberOfExercises_ > 0,
                "Rate times must contain at least two values");
        this.rateTimes_ = rateTimes.clone();
        this.payoffs_ = payoffs.clone();
        final double[] evolveTimes = Arrays.copyOf(this.rateTimes_, this.rateTimes_.length - 1);
        this.evolution_ = new EvolutionDescription(this.rateTimes_, evolveTimes);
    }

    /** Copy constructor for {@link #clone()}. */
    private BermudanSwaptionExerciseValue(final BermudanSwaptionExerciseValue other) {
        this.numberOfExercises_ = other.numberOfExercises_;
        this.rateTimes_ = other.rateTimes_.clone();
        this.payoffs_ = other.payoffs_.clone();
        this.evolution_ = other.evolution_;
        this.currentIndex_ = other.currentIndex_;
        this.cf_.timeIndex = other.cf_.timeIndex;
        this.cf_.amount = other.cf_.amount;
    }

    @Override public int numberOfExercises() { return numberOfExercises_; }

    @Override public EvolutionDescription evolution() { return evolution_; }

    @Override public double[] possibleCashFlowTimes() { return rateTimes_; }

    @Override public void reset() { currentIndex_ = 0; }

    @Override public void nextStep(final CurveState state) {
        final Payoff p = payoffs_[currentIndex_];
        double value = state.coterminalSwapAnnuity(currentIndex_, currentIndex_)
                * p.get(state.coterminalSwapRate(currentIndex_));
        if (value < 0.0) value = 0.0;
        cf_.timeIndex = currentIndex_;
        cf_.amount = value;
        ++currentIndex_;
    }

    @Override public boolean[] isExerciseTime() {
        final boolean[] r = new boolean[numberOfExercises_];
        Arrays.fill(r, true);
        return r;
    }

    @Override public MarketModelMultiProduct.CashFlow value(final CurveState currentState) {
        return cf_;
    }

    @Override public BermudanSwaptionExerciseValue clone() {
        return new BermudanSwaptionExerciseValue(this);
    }
}

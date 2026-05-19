/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.3.

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

package org.jquantlib.model.marketmodels.products.pathwise;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

import java.util.Arrays;

/**
 * Pathwise multi-product wrapping a single LIBOR swap: at each step emits
 * {@code (rate - strike) * accrual * multiplier} together with the partial derivative with respect to the same step's
 * forward rate.
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseSwap}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductswap.{hpp,cpp} v1.42.1).
 *
 * <p>Useful primarily as a building block for breakable swaps; tested against
 * the non-pathwise Swap product in {@code testInverseFloater}.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/products/pathwise/pathwiseproductswap" v1.42.1
 */
public class MarketModelPathwiseSwap extends MarketModelPathwiseMultiProduct {

    private final double[] rateTimes_;
    private final double[] accruals_;
    private final double[] strikes_;
    private final int numberRates_;
    private final double multiplier_;

    private final EvolutionDescription evolution_;

    // path-varying state
    private int currentIndex_;

    public MarketModelPathwiseSwap(final double[] rateTimes, final double[] accruals, final double[] strikes,
            final double multiplier) {
        Utilities.checkIncreasingTimes(rateTimes);
        this.rateTimes_ = rateTimes.clone();
        this.numberRates_ = rateTimes.length - 1;
        this.multiplier_ = multiplier;

        // accruals: broadcast scalar to numberRates_
        if ( accruals.length == 1 ) {
            final double a0 = accruals[0];
            this.accruals_ = new double[numberRates_];
            Arrays.fill(this.accruals_, a0);
        } else {
            this.accruals_ = accruals.clone();
        }
        QL.require(this.accruals_.length == numberRates_, "accruals.size() does not equal numberOfRates or 1");

        // strikes: broadcast scalar to numberRates_
        if ( strikes.length == 1 ) {
            final double s0 = strikes[0];
            this.strikes_ = new double[numberRates_];
            Arrays.fill(this.strikes_, s0);
        } else {
            this.strikes_ = strikes.clone();
        }
        QL.require(this.strikes_.length == numberRates_, "strikes.size() does not equal numberOfRates or 1");

        // evolution times = rateTimes minus the last entry
        final double[] evolTimes = new double[numberRates_];
        System.arraycopy(rateTimes_, 0, evolTimes, 0, numberRates_);
        QL.require(evolTimes.length == numberRates_, "rateTimes.size()<> numberOfRates+1");

        this.evolution_ = new EvolutionDescription(rateTimes_, evolTimes);

        this.currentIndex_ = 0;
    }

    /** Convenience constructor with multiplier = 1.0 (receiver convention). */
    public MarketModelPathwiseSwap(final double[] rateTimes, final double[] accruals, final double[] strikes) {
        this(rateTimes, accruals, strikes, 1.0);
    }

    @Override
    public boolean alreadyDeflated() {
        return false;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final CashFlow[][] cashFlowsGenerated) {
        final double liborRate = currentState.forwardRate(currentIndex_);
        cashFlowsGenerated[0][0].timeIndex = currentIndex_ + 1;

        cashFlowsGenerated[0][0].amount[0] =
                (liborRate - strikes_[currentIndex_]) * accruals_[currentIndex_] * multiplier_;

        numberCashFlowsThisStep[0] = 1;

        for ( int i = 1; i <= numberRates_; ++i ) {
            cashFlowsGenerated[0][0].amount[i] = 0.0;
        }

        cashFlowsGenerated[0][0].amount[currentIndex_ + 1] = accruals_[currentIndex_] * multiplier_;

        ++currentIndex_;
        return currentIndex_ == strikes_.length;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        final MarketModelPathwiseSwap copy = new MarketModelPathwiseSwap(rateTimes_, accruals_, strikes_, multiplier_);
        copy.currentIndex_ = this.currentIndex_;
        return copy;
    }

    @Override
    public int[] suggestedNumeraires() {
        final int[] numeraires = new int[numberRates_];
        for ( int i = 0; i < numberRates_; ++i ) {
            numeraires[i] = i;
        }
        return numeraires;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public double[] possibleCashFlowTimes() {
        // note rateTimes_[0] is not used as a cash flow time but we keep it
        // for index-alignment convenience (matches C++ implementation).
        return rateTimes_.clone();
    }

    @Override
    public int numberOfProducts() {
        return 1;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 1;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }
}

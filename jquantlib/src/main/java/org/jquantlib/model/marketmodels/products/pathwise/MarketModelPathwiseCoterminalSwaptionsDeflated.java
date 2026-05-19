/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.2.

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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels.products.pathwise;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Pathwise co-terminal payer swaptions, deflated. Numberproducts equals the number of rates; product {@code i}
 * corresponds to the swaption struck at {@code strikes[i]} with start time = rate {@code i} reset, all sharing the
 * common terminal date. The analytic adjoint computes ∂value/∂rate via the exact swap-rate × annuity decomposition (no
 * implied vol involved here).
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseCoterminalSwaptionsDeflated}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductswaption.{hpp,cpp} v1.42.1). Tested against the numerical
 * (FD-bumped) variant in C++ {@code testPathwiseVegas}.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/products/pathwise/pathwiseproductswaption" v1.42.1
 */
public class MarketModelPathwiseCoterminalSwaptionsDeflated extends MarketModelPathwiseMultiProduct {

    private final double[] rateTimes_;
    private final double[] strikes_;
    private final int numberRates_;

    private final EvolutionDescription evolution_;

    private int currentIndex_;

    public MarketModelPathwiseCoterminalSwaptionsDeflated(final double[] rateTimes, final double[] strikes) {
        Utilities.checkIncreasingTimes(rateTimes);
        this.rateTimes_ = rateTimes.clone();
        this.strikes_ = strikes.clone();
        this.numberRates_ = rateTimes.length - 1;

        final double[] evolTimes = new double[numberRates_];
        System.arraycopy(rateTimes_, 0, evolTimes, 0, numberRates_);
        QL.require(evolTimes.length == numberRates_, "rateTimes.size()<> numberOfRates+1");
        QL.require(strikes.length == numberRates_, "strikes.size()<> numberOfRates");

        this.evolution_ = new EvolutionDescription(rateTimes_, evolTimes);
        this.currentIndex_ = 0;
    }

    @Override
    public boolean alreadyDeflated() {
        return false;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final CashFlow[][] cashFlowsGenerated) {
        final double swapRate = currentState.coterminalSwapRate(currentIndex_);
        cashFlowsGenerated[currentIndex_][0].timeIndex = currentIndex_;

        final double annuity = currentState.coterminalSwapAnnuity(currentIndex_, currentIndex_);
        cashFlowsGenerated[currentIndex_][0].amount[0] = (swapRate - strikes_[currentIndex_]) * annuity;

        // zero all numberCashFlowsThisStep
        for ( int i = 0; i < numberCashFlowsThisStep.length; ++i ) {
            numberCashFlowsThisStep[i] = 0;
        }

        if ( cashFlowsGenerated[currentIndex_][0].amount[0] > 0 ) {
            numberCashFlowsThisStep[currentIndex_] = 1;
            for ( int i = 1; i <= numberRates_; ++i ) {
                cashFlowsGenerated[currentIndex_][0].amount[i] = 0.0;
            }

            for ( int k = currentIndex_; k < numberRates_; ++k ) {
                cashFlowsGenerated[currentIndex_][0].amount[k + 1] =
                        (rateTimes_[k + 1] - rateTimes_[k]) * currentState.discountRatio(k + 1, currentIndex_);

                final double multiplier = -(rateTimes_[k + 1] - rateTimes_[k]) * currentState.discountRatio(k + 1, k);

                for ( int l = k; l < numberRates_; ++l ) {
                    cashFlowsGenerated[currentIndex_][0].amount[k + 1] +=
                            (currentState.forwardRate(l) - strikes_[currentIndex_]) * (rateTimes_[l + 1]
                                    - rateTimes_[l]) * multiplier * currentState.discountRatio(l + 1, currentIndex_);
                }
            }
        }
        ++currentIndex_;
        return currentIndex_ == strikes_.length;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        final MarketModelPathwiseCoterminalSwaptionsDeflated copy = new MarketModelPathwiseCoterminalSwaptionsDeflated(
                rateTimes_, strikes_);
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
        return rateTimes_.clone();
    }

    @Override
    public int numberOfProducts() {
        return numberRates_;
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

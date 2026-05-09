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
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;

/**
 * Numerical (finite-difference) twin of
 * {@link MarketModelPathwiseCoterminalSwaptionsDeflated}: replaces the
 * analytic ∂value/∂rate adjoint with a central-difference bump of the
 * forward rates.
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseCoterminalSwaptionsNumericalDeflated}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductswaption.{hpp,cpp}
 * v1.42.1). Used in {@code testPathwiseVegas} as the cross-validation
 * reference for the analytic adjoint.
 *
 * @author Jose Moya
 */
public class MarketModelPathwiseCoterminalSwaptionsNumericalDeflated
        extends MarketModelPathwiseMultiProduct {

    private final double[] rateTimes_;
    private final double[] strikes_;
    private final int numberRates_;
    private final double bumpSize_;

    private final EvolutionDescription evolution_;

    // workspace
    private final LMMCurveState up_;
    private final LMMCurveState down_;
    private final double[] forwards_;

    private int currentIndex_;

    public MarketModelPathwiseCoterminalSwaptionsNumericalDeflated(
            final double[] rateTimes,
            final double[] strikes,
            final double bumpSize) {
        Utilities.checkIncreasingTimes(rateTimes);
        this.rateTimes_ = rateTimes.clone();
        this.strikes_ = strikes.clone();
        this.numberRates_ = rateTimes.length - 1;
        this.bumpSize_ = bumpSize;
        this.up_ = new LMMCurveState(rateTimes_);
        this.down_ = new LMMCurveState(rateTimes_);
        this.forwards_ = new double[numberRates_];

        final double[] evolTimes = new double[numberRates_];
        System.arraycopy(rateTimes_, 0, evolTimes, 0, numberRates_);
        QL.require(evolTimes.length == numberRates_,
                "rateTimes.size()<> numberOfRates+1");
        QL.require(strikes.length == numberRates_,
                "strikes.size()<> numberOfRates");

        this.evolution_ = new EvolutionDescription(rateTimes_, evolTimes);
        this.currentIndex_ = 0;
    }

    @Override
    public boolean alreadyDeflated() {
        return false;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final CashFlow[][] cashFlowsGenerated) {
        final double swapRate = currentState.coterminalSwapRate(currentIndex_);
        cashFlowsGenerated[currentIndex_][0].timeIndex = currentIndex_;

        final double annuity = currentState.coterminalSwapAnnuity(currentIndex_, currentIndex_);
        cashFlowsGenerated[currentIndex_][0].amount[0] =
                (swapRate - strikes_[currentIndex_]) * annuity;

        for (int i = 0; i < numberCashFlowsThisStep.length; ++i) {
            numberCashFlowsThisStep[i] = 0;
        }

        if (cashFlowsGenerated[currentIndex_][0].amount[0] > 0) {
            numberCashFlowsThisStep[currentIndex_] = 1;
            for (int i = 1; i <= numberRates_; ++i) {
                cashFlowsGenerated[currentIndex_][0].amount[i] = 0.0;
            }

            for (int k = currentIndex_; k < numberRates_; ++k) {
                final double[] currentForwards = currentState.forwardRates();
                System.arraycopy(currentForwards, 0, forwards_, 0, numberRates_);

                forwards_[k] += bumpSize_;
                up_.setOnForwardRates(forwards_);

                forwards_[k] -= 2 * bumpSize_;
                down_.setOnForwardRates(forwards_);

                final double upSR = up_.coterminalSwapRate(currentIndex_);
                final double upAnnuity = up_.coterminalSwapAnnuity(currentIndex_, currentIndex_);
                final double upValue = (upSR - strikes_[currentIndex_]) * upAnnuity;

                final double downSR = down_.coterminalSwapRate(currentIndex_);
                final double downAnnuity = down_.coterminalSwapAnnuity(currentIndex_, currentIndex_);
                final double downValue = (downSR - strikes_[currentIndex_]) * downAnnuity;

                final double deriv = (upValue - downValue) / (2.0 * bumpSize_);

                cashFlowsGenerated[currentIndex_][0].amount[k + 1] = deriv;
            }
        }
        ++currentIndex_;
        return currentIndex_ == strikes_.length;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        final MarketModelPathwiseCoterminalSwaptionsNumericalDeflated copy =
                new MarketModelPathwiseCoterminalSwaptionsNumericalDeflated(
                        rateTimes_, strikes_, bumpSize_);
        copy.currentIndex_ = this.currentIndex_;
        return copy;
    }

    @Override
    public int[] suggestedNumeraires() {
        final int[] numeraires = new int[numberRates_];
        for (int i = 0; i < numberRates_; ++i) {
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

/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.4.

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

/**
 * Pathwise inverse-floater swap product: at each step the inverse-floating coupon equals
 * {@code max(strike - multiplier * libor, 0) * accrual}, less the conventional floating coupon
 * {@code (libor + spread) * floatAccrual}.
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseInverseFloater}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductinversefloater.{hpp,cpp} v1.42.1). Tested in
 * {@code testInverseFloater}.
 *
 * <p>The path-wise derivative with respect to each forward rate is computed
 * piecewise-analytically: when the inverse-floating coupon is in the money the derivative includes both the
 * inverse-floating leg's contribution and the floating leg's, otherwise only the floating leg's contribution.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/products/pathwise/pathwiseproductinversefloater" v1.42.1
 */
public class MarketModelPathwiseInverseFloater extends MarketModelPathwiseMultiProduct {

    private final double[] rateTimes_;
    private final double[] fixedAccruals_;
    private final double[] floatingAccruals_;
    private final double[] fixedStrikes_;
    private final double[] fixedMultipliers_;
    private final double[] floatingSpreads_;
    private final double[] paymentTimes_;

    private final double multiplier_;
    private final int lastIndex_;

    private final EvolutionDescription evolution_;

    // path-varying state
    private int currentIndex_;

    public MarketModelPathwiseInverseFloater(final double[] rateTimes, final double[] fixedAccruals,
            final double[] floatingAccruals, final double[] fixedStrikes, final double[] fixedMultipliers,
            final double[] floatingSpreads, final double[] paymentTimes, final boolean payer) {
        Utilities.checkIncreasingTimes(paymentTimes);
        this.rateTimes_ = rateTimes.clone();
        this.fixedAccruals_ = fixedAccruals.clone();
        this.floatingAccruals_ = floatingAccruals.clone();
        this.fixedStrikes_ = fixedStrikes.clone();
        this.fixedMultipliers_ = fixedMultipliers.clone();
        this.floatingSpreads_ = floatingSpreads.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.multiplier_ = payer ? -1.0 : 1.0;
        this.lastIndex_ = rateTimes.length - 1;

        QL.require(fixedAccruals_.length == lastIndex_, "Incorrect number of fixedAccruals given");
        QL.require(floatingAccruals.length == lastIndex_, "Incorrect number of floatingAccruals given");
        QL.require(fixedStrikes.length == lastIndex_, "Incorrect number of fixedStrikes given");
        QL.require(fixedMultipliers.length == lastIndex_, "Incorrect number of fixedMultipliers given");
        QL.require(floatingSpreads.length == lastIndex_, "Incorrect number of floatingSpreads given");
        QL.require(paymentTimes.length == lastIndex_, "Incorrect number of paymentTimes given");

        final double[] evolTimes = new double[lastIndex_];
        System.arraycopy(rateTimes_, 0, evolTimes, 0, lastIndex_);

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
        numberCashFlowsThisStep[0] = 1;
        for ( int i = 1; i <= lastIndex_; ++i ) {
            cashFlowsGenerated[0][0].amount[i] = 0.0;
        }

        final double liborRate = currentState.forwardRate(currentIndex_);
        final double inverseFloatingCoupon =
                Math.max(fixedStrikes_[currentIndex_] - fixedMultipliers_[currentIndex_] * liborRate, 0.0)
                        * fixedAccruals_[currentIndex_];
        final double floatingCoupon = (liborRate + floatingSpreads_[currentIndex_]) * floatingAccruals_[currentIndex_];

        cashFlowsGenerated[0][0].timeIndex = currentIndex_;
        cashFlowsGenerated[0][0].amount[0] = multiplier_ * (inverseFloatingCoupon - floatingCoupon);

        if ( inverseFloatingCoupon > 0.0 ) {
            cashFlowsGenerated[0][0].amount[currentIndex_ + 1] =
                    multiplier_ * (-fixedMultipliers_[currentIndex_] * fixedAccruals_[currentIndex_]
                            - floatingAccruals_[currentIndex_]);
        } else {
            cashFlowsGenerated[0][0].amount[currentIndex_ + 1] = -multiplier_ * floatingAccruals_[currentIndex_];
        }

        ++currentIndex_;
        return currentIndex_ == lastIndex_;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        final MarketModelPathwiseInverseFloater copy = new MarketModelPathwiseInverseFloater(rateTimes_, fixedAccruals_,
                floatingAccruals_, fixedStrikes_, fixedMultipliers_, floatingSpreads_, paymentTimes_,
                multiplier_ < 0.0);
        copy.currentIndex_ = this.currentIndex_;
        return copy;
    }

    @Override
    public int[] suggestedNumeraires() {
        final int[] numeraires = new int[lastIndex_];
        for ( int i = 0; i < lastIndex_; ++i ) {
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
        return paymentTimes_.clone();
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

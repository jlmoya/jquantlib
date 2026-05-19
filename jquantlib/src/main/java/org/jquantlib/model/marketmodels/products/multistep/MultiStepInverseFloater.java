/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.8.

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
 Copyright (C) 2006 Giorgio Facchinetti
*/

package org.jquantlib.model.marketmodels.products.multistep;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step Inverse Floater.
 * <p>
 * Mirrors C++ {@code class MultiStepInverseFloater}
 * (ql/models/marketmodels/products/multistep/multistepinversefloater.{hpp,cpp} v1.42.1).
 * <p>
 * Per step emits one cash flow:
 * {@code multiplier * (max(strike - mult*rate, 0)*fixedAccrual - (rate+spread)*floatAccrual)} with the {@code payer}
 * flag flipping sign convention.
 *
 * @author Jose Moya
 */
public class MultiStepInverseFloater extends MultiProductMultiStep {

    private final double[] fixedAccruals_;
    private final double[] floatingAccruals_;
    private final double[] fixedStrikes_;
    private final double[] fixedMultipliers_;
    private final double[] floatingSpreads_;
    private final double[] paymentTimes_;
    private final double multiplier_;
    private final boolean payer_;
    private final int lastIndex_;
    private int currentIndex_;

    public MultiStepInverseFloater(final double[] rateTimes, final double[] fixedAccruals,
            final double[] floatingAccruals, final double[] fixedStrikes, final double[] fixedMultipliers,
            final double[] floatingSpreads, final double[] paymentTimes, final boolean payer) {
        super(rateTimes);
        this.fixedAccruals_ = fixedAccruals.clone();
        this.floatingAccruals_ = floatingAccruals.clone();
        this.fixedStrikes_ = fixedStrikes.clone();
        this.fixedMultipliers_ = fixedMultipliers.clone();
        this.floatingSpreads_ = floatingSpreads.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.payer_ = payer;
        this.multiplier_ = payer ? -1.0 : 1.0;
        this.lastIndex_ = rateTimes.length - 1;
        Utilities.checkIncreasingTimes(this.paymentTimes_);
        QL.require(fixedAccruals_.length == lastIndex_, "Incorrect number of fixedAccruals; expected " + lastIndex_);
        QL.require(floatingAccruals_.length == lastIndex_,
                "Incorrect number of floatingAccruals; expected " + lastIndex_);
        QL.require(fixedStrikes_.length == lastIndex_, "Incorrect number of fixedStrikes; expected " + lastIndex_);
        QL.require(fixedMultipliers_.length == lastIndex_,
                "Incorrect number of fixedMultipliers; expected " + lastIndex_);
        QL.require(floatingSpreads_.length == lastIndex_,
                "Incorrect number of floatingSpreads; expected " + lastIndex_);
        QL.require(paymentTimes_.length == lastIndex_, "Incorrect number of paymentTimes; expected " + lastIndex_);
    }

    public MultiStepInverseFloater(final double[] rateTimes, final double[] fixedAccruals,
            final double[] floatingAccruals, final double[] fixedStrikes, final double[] fixedMultipliers,
            final double[] floatingSpreads, final double[] paymentTimes) {
        this(rateTimes, fixedAccruals, floatingAccruals, fixedStrikes, fixedMultipliers, floatingSpreads, paymentTimes,
                true);
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return paymentTimes_;
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

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        final double liborRate = currentState.forwardRate(currentIndex_);
        final double inverseFloatingCoupon =
                Math.max(fixedStrikes_[currentIndex_] - fixedMultipliers_[currentIndex_] * liborRate, 0.0)
                        * fixedAccruals_[currentIndex_];
        final double floatingCoupon = (liborRate + floatingSpreads_[currentIndex_]) * floatingAccruals_[currentIndex_];

        genCashFlows[0][0].timeIndex = currentIndex_;
        genCashFlows[0][0].amount = multiplier_ * (inverseFloatingCoupon - floatingCoupon);

        numberCashFlowsThisStep[0] = 1;
        ++currentIndex_;
        return currentIndex_ == lastIndex_;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepInverseFloater(rateTimes_, fixedAccruals_, floatingAccruals_, fixedStrikes_,
                fixedMultipliers_, floatingSpreads_, paymentTimes_, payer_);
    }
}

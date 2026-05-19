/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.7.

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

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step Swap.
 * <p>
 * Mirrors C++ {@code class MultiStepSwap} (ql/models/marketmodels/products/multistep/multistepswap.{hpp,cpp} v1.42.1).
 * <p>
 * Per step emits 2 cash flows (fixed-leg and floating-leg) for product 0. The {@code payer} flag determines the sign
 * convention: {@code multiplier_ = payer ? 1.0 : -1.0}.
 *
 * @author Jose Moya
 */
public class MultiStepSwap extends MultiProductMultiStep {

    private final double[] fixedAccruals_;
    private final double[] floatingAccruals_;
    private final double[] paymentTimes_;
    private final double fixedRate_;
    private final double multiplier_;
    private final boolean payer_;
    private final int lastIndex_;
    private int currentIndex_;

    public MultiStepSwap(final double[] rateTimes, final double[] fixedAccruals, final double[] floatingAccruals,
            final double[] paymentTimes, final double fixedRate, final boolean payer) {
        super(rateTimes);
        this.fixedAccruals_ = fixedAccruals.clone();
        this.floatingAccruals_ = floatingAccruals.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.fixedRate_ = fixedRate;
        this.payer_ = payer;
        this.multiplier_ = payer ? 1.0 : -1.0;
        this.lastIndex_ = rateTimes.length - 1;
        Utilities.checkIncreasingTimes(this.paymentTimes_);
    }

    public MultiStepSwap(final double[] rateTimes, final double[] fixedAccruals, final double[] floatingAccruals,
            final double[] paymentTimes, final double fixedRate) {
        this(rateTimes, fixedAccruals, floatingAccruals, paymentTimes, fixedRate, true);
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
        return 2;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        final double liborRate = currentState.forwardRate(currentIndex_);

        genCashFlows[0][0].timeIndex = currentIndex_;
        genCashFlows[0][0].amount = -multiplier_ * fixedRate_ * fixedAccruals_[currentIndex_];

        genCashFlows[0][1].timeIndex = currentIndex_;
        genCashFlows[0][1].amount = multiplier_ * liborRate * floatingAccruals_[currentIndex_];

        numberCashFlowsThisStep[0] = 2;
        ++currentIndex_;
        return currentIndex_ == lastIndex_;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepSwap(rateTimes_, fixedAccruals_, floatingAccruals_, paymentTimes_, fixedRate_, payer_);
    }
}

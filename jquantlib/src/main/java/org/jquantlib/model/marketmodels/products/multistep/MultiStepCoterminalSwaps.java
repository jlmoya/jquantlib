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

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step Co-terminal Swaps.
 * <p>
 * Mirrors C++ {@code class MultiStepCoterminalSwaps}
 * (ql/models/marketmodels/products/multistep/multistepcoterminalswaps.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class MultiStepCoterminalSwaps extends MultiProductMultiStep {

    private final double[] fixedAccruals_;
    private final double[] floatingAccruals_;
    private final double[] paymentTimes_;
    private final double fixedRate_;
    private final int lastIndex_;
    private int currentIndex_;

    public MultiStepCoterminalSwaps(final double[] rateTimes, final double[] fixedAccruals,
            final double[] floatingAccruals, final double[] paymentTimes, final double fixedRate) {
        super(rateTimes);
        this.fixedAccruals_ = fixedAccruals.clone();
        this.floatingAccruals_ = floatingAccruals.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.fixedRate_ = fixedRate;
        Utilities.checkIncreasingTimes(this.paymentTimes_);
        this.lastIndex_ = rateTimes.length - 1;
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return lastIndex_;
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
        for ( int i = 0; i < numberCashFlowsThisStep.length; ++i ) {
            numberCashFlowsThisStep[i] = 0;
        }
        for ( int i = 0; i <= currentIndex_; ++i ) {
            genCashFlows[i][0].timeIndex = currentIndex_;
            genCashFlows[i][0].amount = -fixedRate_ * fixedAccruals_[currentIndex_];

            genCashFlows[i][1].timeIndex = currentIndex_;
            genCashFlows[i][1].amount = liborRate * floatingAccruals_[currentIndex_];

            numberCashFlowsThisStep[i] = 2;
        }
        ++currentIndex_;
        return currentIndex_ == lastIndex_;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepCoterminalSwaps(rateTimes_, fixedAccruals_, floatingAccruals_, paymentTimes_, fixedRate_);
    }
}

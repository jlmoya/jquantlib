/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.3.

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

package org.jquantlib.model.marketmodels.products.onestep;

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductOneStep;

/**
 * One-step Co-terminal Swaps (all swaps share the same end, varying starts).
 * <p>
 * Mirrors C++ {@code class OneStepCoterminalSwaps}
 * (ql/models/marketmodels/products/onestep/onestepcoterminalswaps.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class OneStepCoterminalSwaps extends MultiProductOneStep {

    private final double[] fixedAccruals_;
    private final double[] floatingAccruals_;
    private final double[] paymentTimes_;
    private final double fixedRate_;
    private final int lastIndex_;

    public OneStepCoterminalSwaps(final double[] rateTimes, final double[] fixedAccruals,
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
        return 2 * lastIndex_;
    }

    @Override
    public void reset() { /* nothing to do */ }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        for ( int i = 0; i < numberCashFlowsThisStep.length; ++i ) {
            numberCashFlowsThisStep[i] = 0;
        }

        for ( int indexOfTime = 0; indexOfTime < lastIndex_; ++indexOfTime ) {
            final double liborRate = currentState.forwardRate(indexOfTime);
            for ( int i = 0; i <= indexOfTime; ++i ) {
                genCashFlows[i][(indexOfTime - i) * 2].timeIndex = indexOfTime;
                genCashFlows[i][(indexOfTime - i) * 2].amount = -fixedRate_ * fixedAccruals_[indexOfTime];

                genCashFlows[i][(indexOfTime - i) * 2 + 1].timeIndex = indexOfTime;
                genCashFlows[i][(indexOfTime - i) * 2 + 1].amount = liborRate * floatingAccruals_[indexOfTime];

                numberCashFlowsThisStep[i] += 2;
            }
        }
        return true;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new OneStepCoterminalSwaps(rateTimes_, fixedAccruals_, floatingAccruals_, paymentTimes_, fixedRate_);
    }
}

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
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels.products.onestep;

import org.jquantlib.instruments.Payoff;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductOneStep;

/**
 * One-step Optionlets.
 * <p>
 * Mirrors C++ {@code class OneStepOptionlets} (ql/models/marketmodels/products/onestep/onestepoptionlets.{hpp,cpp}
 * v1.42.1). Each product i emits one cash flow {@code payoff[i](forwardRate(i)) * accrual[i]} (only when the payoff is
 * strictly positive) at paymentTime[i].
 *
 * @author Jose Moya
 */
public class OneStepOptionlets extends MultiProductOneStep {

    private final double[] accruals_;
    private final double[] paymentTimes_;
    private final Payoff[] payoffs_;

    public OneStepOptionlets(final double[] rateTimes, final double[] accruals, final double[] paymentTimes,
            final Payoff[] payoffs) {
        super(rateTimes);
        this.accruals_ = accruals.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.payoffs_ = payoffs.clone();
        Utilities.checkIncreasingTimes(this.paymentTimes_);
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return paymentTimes_;
    }

    @Override
    public int numberOfProducts() {
        return payoffs_.length;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 1;
    }

    @Override
    public void reset() { /* nothing to do */ }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        for ( int i = 0; i < numberCashFlowsThisStep.length; ++i ) {
            numberCashFlowsThisStep[i] = 0;
        }
        for ( int i = 0; i < payoffs_.length; ++i ) {
            final double liborRate = currentState.forwardRate(i);
            final double payoff = payoffs_[i].get(liborRate);
            if ( payoff > 0.0 ) {
                numberCashFlowsThisStep[i] = 1;
                genCashFlows[i][0].timeIndex = i;
                genCashFlows[i][0].amount = payoff * accruals_[i];
            }
        }
        return true;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new OneStepOptionlets(rateTimes_, accruals_, paymentTimes_, payoffs_);
    }
}

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
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels.products.onestep;

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
import org.jquantlib.model.marketmodels.products.MultiProductOneStep;

/**
 * One-step Forward Rate Agreements.
 * <p>
 * Mirrors C++ {@code class OneStepForwards}
 * (ql/models/marketmodels/products/onestep/onestepforwards.{hpp,cpp} v1.42.1).
 * Each product i pays {@code (forwardRate(i) - strike[i]) * accrual[i]} at
 * paymentTime[i] in a single evolution step.
 *
 * @author Jose Moya
 */
public class OneStepForwards extends MultiProductOneStep {

    private final double[] accruals_;
    private final double[] paymentTimes_;
    private final double[] strikes_;

    public OneStepForwards(final double[] rateTimes,
                           final double[] accruals,
                           final double[] paymentTimes,
                           final double[] strikes) {
        super(rateTimes);
        this.accruals_ = accruals.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.strikes_ = strikes.clone();
        Utilities.checkIncreasingTimes(this.paymentTimes_);
    }

    @Override
    public double[] possibleCashFlowTimes() { return paymentTimes_; }

    @Override
    public int numberOfProducts() { return strikes_.length; }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() { return 1; }

    @Override
    public void reset() { /* nothing to do */ }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        for (int i = 0; i < strikes_.length; ++i) {
            final double liborRate = currentState.forwardRate(i);
            genCashFlows[i][0].timeIndex = i;
            genCashFlows[i][0].amount = (liborRate - strikes_[i]) * accruals_[i];
            numberCashFlowsThisStep[i] = 1;
        }
        return true;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new OneStepForwards(rateTimes_, accruals_, paymentTimes_, strikes_);
    }
}

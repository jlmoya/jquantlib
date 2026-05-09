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

import org.jquantlib.QL;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step Swaption — single swaption over a contiguous subset of rates,
 * stepping through every rate up to the swap start.
 * <p>
 * Mirrors C++ {@code class MultiStepSwaption}
 * (ql/models/marketmodels/products/multistep/multistepswaption.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class MultiStepSwaption extends MultiProductMultiStep {

    private final int startIndex_;
    private final int endIndex_;
    private final StrikedTypePayoff payoff_;
    private final double[] paymentTimes_;
    private int currentIndex_;

    public MultiStepSwaption(final double[] rateTimes,
                             final int startIndex,
                             final int endIndex,
                             final StrikedTypePayoff payoff) {
        super(rateTimes);
        QL.require(startIndex < endIndex, "start index must be before end index");
        QL.require(endIndex < rateTimes.length, "end index must be before the end of the rates.");
        this.startIndex_ = startIndex;
        this.endIndex_ = endIndex;
        this.payoff_ = payoff;
        this.paymentTimes_ = new double[] { rateTimes[startIndex] };
    }

    @Override
    public double[] possibleCashFlowTimes() { return paymentTimes_; }

    @Override
    public int numberOfProducts() { return 1; }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() { return 1; }

    @Override
    public void reset() { currentIndex_ = 0; }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        if (currentIndex_ == startIndex_) {
            genCashFlows[0][0].timeIndex = 0;
            final double swapRate = currentState.cmSwapRate(startIndex_, endIndex_ - startIndex_);
            final double annuity = currentState.cmSwapAnnuity(startIndex_, startIndex_, endIndex_ - startIndex_);
            genCashFlows[0][0].amount = payoff_.get(swapRate) * annuity;
            numberCashFlowsThisStep[0] = (genCashFlows[0][0].amount != 0.0) ? 1 : 0;
            return true;
        }
        numberCashFlowsThisStep[0] = 0;
        ++currentIndex_;
        return false;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepSwaption(rateTimes_, startIndex_, endIndex_, payoff_);
    }
}

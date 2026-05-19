/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.5.

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

import java.util.Arrays;

/**
 * Pathwise (adjoint Greeks) multi-caplet product, with payoffs already divided by the numeraire bond (deflated). The
 * deflation factor enters the adjoint via discount-ratio derivatives.
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseMultiDeflatedCaplet}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductcaplet.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class MarketModelPathwiseMultiDeflatedCaplet extends MarketModelPathwiseMultiProduct {

    private final double[] rateTimes_;
    private final double[] accruals_;
    private final double[] paymentTimes_;
    private final double[] strikes_;
    private final int numberRates_;

    private final EvolutionDescription evolution_;

    private int currentIndex_;

    public MarketModelPathwiseMultiDeflatedCaplet(final double[] rateTimes, final double[] accruals,
            final double[] paymentTimes, final double[] strikes) {
        Utilities.checkIncreasingTimes(rateTimes);
        Utilities.checkIncreasingTimes(paymentTimes);
        this.rateTimes_ = rateTimes.clone();
        this.accruals_ = accruals.clone();
        this.paymentTimes_ = paymentTimes.clone();
        this.strikes_ = strikes.clone();
        this.numberRates_ = accruals.length;

        final double[] evolTimes = new double[numberRates_];
        System.arraycopy(rateTimes_, 0, evolTimes, 0, numberRates_);
        QL.require(evolTimes.length == numberRates_, "rateTimes.size()<> numberOfRates+1");
        QL.require(paymentTimes.length == numberRates_, "paymentTimes.size()<> numberOfRates");
        QL.require(accruals.length == numberRates_, "accruals.size()<> numberOfRates");
        QL.require(strikes.length == numberRates_, "strikes.size()<> numberOfRates");

        this.evolution_ = new EvolutionDescription(rateTimes_, evolTimes);
        this.currentIndex_ = 0;
    }

    /** Constructor with a single common strike. */
    public MarketModelPathwiseMultiDeflatedCaplet(final double[] rateTimes, final double[] accruals,
            final double[] paymentTimes, final double strike) {
        this(rateTimes, accruals, paymentTimes, broadcast(strike, accruals.length));
    }

    private static double[] broadcast(final double v, final int n) {
        final double[] arr = new double[n];
        Arrays.fill(arr, v);
        return arr;
    }

    @Override
    public boolean alreadyDeflated() {
        return true;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final CashFlow[][] cashFlowsGenerated) {
        final double liborRate = currentState.forwardRate(currentIndex_);
        cashFlowsGenerated[currentIndex_][0].timeIndex = currentIndex_;
        cashFlowsGenerated[currentIndex_][0].amount[0] =
                (liborRate - strikes_[currentIndex_]) * accruals_[currentIndex_] * currentState.discountRatio(
                        currentIndex_ + 1, 0);

        for ( int i = 0; i < numberCashFlowsThisStep.length; ++i ) {
            numberCashFlowsThisStep[i] = 0;
        }

        if ( cashFlowsGenerated[currentIndex_][0].amount[0] > 0 ) {
            numberCashFlowsThisStep[currentIndex_] = 1;
            for ( int i = 1; i <= numberRates_; ++i ) {
                cashFlowsGenerated[currentIndex_][0].amount[i] = 0.0;
            }

            cashFlowsGenerated[currentIndex_][0].amount[currentIndex_ + 1] =
                    accruals_[currentIndex_] * currentState.discountRatio(currentIndex_ + 1, 0);

            for ( int i = 0; i <= currentIndex_; ++i ) {
                final double stepDF = currentState.discountRatio(i + 1, i);
                cashFlowsGenerated[currentIndex_][0].amount[i + 1] -=
                        accruals_[i] * stepDF * cashFlowsGenerated[currentIndex_][0].amount[0];
            }
        }
        ++currentIndex_;
        return currentIndex_ == strikes_.length;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        final MarketModelPathwiseMultiDeflatedCaplet copy = new MarketModelPathwiseMultiDeflatedCaplet(rateTimes_,
                accruals_, paymentTimes_, strikes_);
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
        return paymentTimes_.clone();
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

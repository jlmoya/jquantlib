/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.1.

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
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Pathwise cash-rebate product: emits per-step deterministic cash amounts whose derivative with respect to every
 * forward rate is zero.
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseCashRebate}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductcashrebate.{hpp,cpp} v1.42.1).
 *
 * <p>Although fairly useless on its own, it becomes the standard "rebate" leg
 * of a {@code CallSpecified} pathwise product when modelling breakable swaps.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/products/pathwise/pathwiseproductcashrebate" v1.42.1
 */
public class MarketModelPathwiseCashRebate extends MarketModelPathwiseMultiProduct {

    private final EvolutionDescription evolution_;
    private final double[] paymentTimes_;
    private final Matrix amounts_;
    private final int numberOfProducts_;

    // path-varying state
    private int currentIndex_;

    public MarketModelPathwiseCashRebate(final EvolutionDescription evolution, final double[] paymentTimes,
            final Matrix amounts, final int numberOfProducts) {
        this.evolution_ = evolution;
        this.paymentTimes_ = paymentTimes.clone();
        this.amounts_ = new Matrix(amounts);
        this.numberOfProducts_ = numberOfProducts;
        this.currentIndex_ = 0;

        Utilities.checkIncreasingTimes(paymentTimes_);

        QL.require(amounts_.rows() == numberOfProducts_,
                "the number of rows in the matrix must equal the number of products");
        QL.require(amounts_.cols() == paymentTimes_.length,
                "the number of columns in the matrix must equal the number of payment times");
        QL.require(evolution_.evolutionTimes().length == paymentTimes_.length,
                "the number of evolution times must equal the number of payment times");
    }

    @Override
    public boolean alreadyDeflated() {
        return false;
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return paymentTimes_.clone();
    }

    @Override
    public int numberOfProducts() {
        return numberOfProducts_;
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
    public int[] suggestedNumeraires() {
        QL.error("not implemented (yet?)");
        return null;
    }

    @Override
    public EvolutionDescription evolution() {
        return evolution_;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final CashFlow[][] cashFlowsGenerated) {
        for ( int i = 0; i < numberOfProducts_; ++i ) {
            numberCashFlowsThisStep[i] = 1;
            cashFlowsGenerated[i][0].timeIndex = currentIndex_;
            cashFlowsGenerated[i][0].amount[0] = amounts_.get(i, currentIndex_);
            for ( int k = 1; k <= evolution_.numberOfRates(); ++k ) {
                cashFlowsGenerated[i][0].amount[k] = 0.0;
            }
        }
        ++currentIndex_;
        return true;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        final MarketModelPathwiseCashRebate copy = new MarketModelPathwiseCashRebate(evolution_, paymentTimes_,
                amounts_, numberOfProducts_);
        copy.currentIndex_ = this.currentIndex_;
        return copy;
    }
}

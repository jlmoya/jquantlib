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
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels.products.multistep;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;
// QL.error not used (we throw directly for the unimplemented numeraire fail)
//

/**
 * Cash-rebate market-model product — pays a fixed schedule of cash amounts
 * once. Useful as a rebate received when another product is cancelled.
 * <p>
 * Mirrors C++ {@code class MarketModelCashRebate}
 * (ql/models/marketmodels/products/multistep/cashrebate.{hpp,cpp} v1.42.1).
 * <p>
 * Note: this class extends {@link MarketModelMultiProduct} <b>directly</b>
 * (not {@link org.jquantlib.model.marketmodels.products.MultiProductMultiStep})
 * because it uses an explicitly-supplied {@link EvolutionDescription} and
 * its {@code suggestedNumeraires()} is unimplemented in C++ — matching the
 * C++ {@code QL_FAIL("not implemented (yet?)")}.
 *
 * @author Jose Moya
 */
public class MarketModelCashRebate extends MarketModelMultiProduct {

    private final EvolutionDescription evolution_;
    private final double[] paymentTimes_;
    private final Matrix amounts_;
    private final int numberOfProducts_;
    private int currentIndex_;

    public MarketModelCashRebate(final EvolutionDescription evolution,
                                 final double[] paymentTimes,
                                 final Matrix amounts,
                                 final int numberOfProducts) {
        this.evolution_ = evolution;
        this.paymentTimes_ = paymentTimes.clone();
        this.amounts_ = amounts;
        this.numberOfProducts_ = numberOfProducts;
        Utilities.checkIncreasingTimes(this.paymentTimes_);
        QL.require(amounts_.rows() == numberOfProducts_,
                "the number of rows in the matrix must equal the number of products");
        QL.require(amounts_.cols() == this.paymentTimes_.length,
                "the number of columns in the matrix must equal the number of payment times");
        QL.require(evolution_.evolutionTimes().length == this.paymentTimes_.length,
                "the number of evolution times must equal the number of payment times");
    }

    @Override
    public int[] suggestedNumeraires() {
        throw new UnsupportedOperationException("not implemented (yet?)");
    }

    @Override
    public EvolutionDescription evolution() { return evolution_; }

    @Override
    public double[] possibleCashFlowTimes() { return paymentTimes_; }

    @Override
    public int numberOfProducts() { return numberOfProducts_; }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() { return 1; }

    @Override
    public void reset() { currentIndex_ = 0; }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        for (int i = 0; i < numberOfProducts_; ++i) {
            numberCashFlowsThisStep[i] = 1;
            genCashFlows[i][0].timeIndex = currentIndex_;
            genCashFlows[i][0].amount = amounts_.get(i, currentIndex_);
        }
        ++currentIndex_;
        return true;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MarketModelCashRebate(evolution_, paymentTimes_, amounts_, numberOfProducts_);
    }
}

/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.12.

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

package org.jquantlib.model.marketmodels.products.multistep;

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;

/**
 * Adapts a {@link MarketModelPathwiseMultiProduct} to the {@link MarketModelMultiProduct} interface — drops the
 * per-rate vectors and exposes only the scalar payoff (amount[0]).
 * <p>
 * Mirrors C++ {@code class MultiProductPathwiseWrapper}
 * (ql/models/marketmodels/products/multistep/multisteppathwisewrapper.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class MultiProductPathwiseWrapper extends MarketModelMultiProduct {

    private final MarketModelPathwiseMultiProduct innerProduct_;
    private final MarketModelPathwiseMultiProduct.CashFlow[][] cashFlowsGenerated_;
    private final int numberOfProducts_;

    public MultiProductPathwiseWrapper(final MarketModelPathwiseMultiProduct innerProduct) {
        this.innerProduct_ = innerProduct.clone();
        this.numberOfProducts_ = innerProduct.numberOfProducts();
        final int n = innerProduct.maxNumberOfCashFlowsPerProductPerStep();
        final int rateCount = 1 + innerProduct.evolution().numberOfRates();
        this.cashFlowsGenerated_ = new MarketModelPathwiseMultiProduct.CashFlow[numberOfProducts_][n];
        for ( int i = 0; i < numberOfProducts_; ++i ) {
            for ( int j = 0; j < n; ++j ) {
                cashFlowsGenerated_[i][j] = new MarketModelPathwiseMultiProduct.CashFlow();
                cashFlowsGenerated_[i][j].amount = new double[rateCount];
            }
        }
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return innerProduct_.possibleCashFlowTimes();
    }

    @Override
    public int numberOfProducts() {
        return innerProduct_.numberOfProducts();
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return innerProduct_.maxNumberOfCashFlowsPerProductPerStep();
    }

    @Override
    public void reset() {
        innerProduct_.reset();
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] cashFlowsGenerated) {
        final boolean done = innerProduct_.nextTimeStep(currentState, numberCashFlowsThisStep, cashFlowsGenerated_);

        // transform the data: pull out amount[0] (the payoff scalar)
        for ( int i = 0; i < numberOfProducts_; ++i ) {
            for ( int j = 0; j < numberCashFlowsThisStep[i]; ++j ) {
                cashFlowsGenerated[i][j].timeIndex = cashFlowsGenerated_[i][j].timeIndex;
                cashFlowsGenerated[i][j].amount = cashFlowsGenerated_[i][j].amount[0];
            }
        }
        return done;
    }

    @Override
    public int[] suggestedNumeraires() {
        return innerProduct_.suggestedNumeraires();
    }

    @Override
    public EvolutionDescription evolution() {
        return innerProduct_.evolution();
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiProductPathwiseWrapper(innerProduct_);
    }
}

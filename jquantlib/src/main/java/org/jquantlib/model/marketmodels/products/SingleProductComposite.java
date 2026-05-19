/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.6.

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
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.products;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;

/**
 * Composition of one or more market-model products presented as a single product (cash flows aggregated into one
 * product slot 0).
 * <p>
 * Mirrors C++ {@code class SingleProductComposite} (ql/models/marketmodels/products/singleproductcomposite.{hpp,cpp}
 * v1.42.1).
 *
 * @author Jose Moya
 */
public class SingleProductComposite extends MarketModelComposite {

    public SingleProductComposite() { /* default-init */ }

    @Override
    public int numberOfProducts() {
        return 1;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        int result = 0;
        for ( final SubProduct sub : components_ ) {
            result += sub.product.maxNumberOfCashFlowsPerProductPerStep();
        }
        return result;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] cashFlowsGenerated) {
        QL.require(finalized_, "composite not finalized");
        boolean done = true;
        int n = 0;
        int totalCashflows = 0;
        for ( final SubProduct sub : components_ ) {
            if ( isInSubset_[n][currentIndex_] && !sub.done ) {
                final boolean thisDone = sub.product.nextTimeStep(currentState, sub.numberOfCashflows, sub.cashflows);
                final int np = sub.product.numberOfProducts();
                for ( int j = 0; j < np; ++j ) {
                    final int offset = totalCashflows;
                    totalCashflows += sub.numberOfCashflows[j];
                    for ( int k = 0; k < sub.numberOfCashflows[j]; ++k ) {
                        final MarketModelMultiProduct.CashFlow from = sub.cashflows[j][k];
                        final MarketModelMultiProduct.CashFlow to = cashFlowsGenerated[0][k + offset];
                        to.timeIndex = sub.timeIndices[from.timeIndex];
                        to.amount = from.amount * sub.multiplier;
                    }
                    numberCashFlowsThisStep[0] = totalCashflows;
                }
                done = done && thisDone;
            }
            ++n;
        }
        ++currentIndex_;
        return done;
    }

    @Override
    public MarketModelMultiProduct clone() {
        final SingleProductComposite c = new SingleProductComposite();
        c.rateTimes_ = (rateTimes_ == null) ? null : rateTimes_.clone();
        c.evolutionTimes_ = (evolutionTimes_ == null) ? null : evolutionTimes_.clone();
        c.evolution_ = evolution_;
        c.finalized_ = finalized_;
        c.currentIndex_ = currentIndex_;
        c.cashflowTimes_ = (cashflowTimes_ == null) ? null : cashflowTimes_.clone();
        if ( isInSubset_ != null ) {
            c.isInSubset_ = new boolean[isInSubset_.length][];
            for ( int i = 0; i < isInSubset_.length; ++i ) {
                c.isInSubset_[i] = isInSubset_[i].clone();
            }
        }
        for ( final double[] t : allEvolutionTimes_ ) {
            c.allEvolutionTimes_.add(t.clone());
        }
        for ( final SubProduct sub : components_ ) {
            c.components_.add(sub.copyDeep());
        }
        return c;
    }
}

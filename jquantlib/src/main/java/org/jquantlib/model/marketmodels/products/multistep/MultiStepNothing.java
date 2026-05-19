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

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Multi-step "Nothing" product — no cash flows; useful as the underlying of a Bermudan swaption (rebate-only product).
 * <p>
 * Mirrors C++ {@code class MultiStepNothing} (ql/models/marketmodels/products/multistep/multistepnothing.{hpp,cpp}
 * v1.42.1).
 *
 * @author Jose Moya
 */
public class MultiStepNothing extends MultiProductMultiStep {

    private final int numberOfProducts_;
    private final int doneIndex_;
    private int currentIndex_;

    public MultiStepNothing(final EvolutionDescription evolution, final int numberOfProducts, final int doneIndex) {
        super(evolution.rateTimes());
        this.numberOfProducts_ = numberOfProducts;
        this.doneIndex_ = doneIndex;
    }

    public MultiStepNothing(final EvolutionDescription evolution) {
        this(evolution, 1, 0);
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return new double[0];
    }

    @Override
    public int numberOfProducts() {
        return numberOfProducts_;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return 0;
    }

    @Override
    public void reset() {
        currentIndex_ = 0;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState, final int[] numberCashFlowsThisStep,
            final MarketModelMultiProduct.CashFlow[][] genCashFlows) {
        for ( int i = 0; i < numberCashFlowsThisStep.length; ++i ) {
            numberCashFlowsThisStep[i] = 0;
        }
        ++currentIndex_;
        return currentIndex_ >= doneIndex_;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new MultiStepNothing(this.evolution_, numberOfProducts_, doneIndex_);
    }
}

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

/**
 * Pathwise multi-deflated-cap product: aggregates several deflated caplet
 * cash flows into "caps" — each cap defined by a {@code [start, end)}
 * caplet-index range. Useful for testing pathwise market vegas.
 *
 * <p>Mirrors C++ {@code MarketModelPathwiseMultiDeflatedCap}
 * (ql/models/marketmodels/products/pathwise/pathwiseproductcaplet.{hpp,cpp}
 * v1.42.1).
 *
 * @author Jose Moya
 */
public class MarketModelPathwiseMultiDeflatedCap extends MarketModelPathwiseMultiProduct {

    /** Inclusive-exclusive range of underlying caplet indices [first, second). */
    public static final class StartAndEnd {
        public final int first;
        public final int second;
        public StartAndEnd(final int first, final int second) {
            this.first = first;
            this.second = second;
        }
    }

    // construction parameters preserved for clone()
    private final double[] origRateTimes_;
    private final double[] origAccruals_;
    private final double[] origPaymentTimes_;
    private final double origStrike_;

    private final MarketModelPathwiseMultiDeflatedCaplet underlyingCaplets_;
    private final int numberRates_;
    private final StartAndEnd[] startsAndEnds_;

    // workspace
    private final int[] innerCashFlowSizes_;
    private final CashFlow[][] innerCashFlowsGenerated_;

    public MarketModelPathwiseMultiDeflatedCap(final double[] rateTimes,
                                               final double[] accruals,
                                               final double[] paymentTimes,
                                               final double strike,
                                               final StartAndEnd[] startsAndEnds) {
        this.origRateTimes_ = rateTimes.clone();
        this.origAccruals_ = accruals.clone();
        this.origPaymentTimes_ = paymentTimes.clone();
        this.origStrike_ = strike;

        this.underlyingCaplets_ = new MarketModelPathwiseMultiDeflatedCaplet(
                rateTimes, accruals, paymentTimes, strike);
        this.numberRates_ = accruals.length;
        this.startsAndEnds_ = startsAndEnds.clone();

        for (int j = 0; j < startsAndEnds_.length; ++j) {
            QL.require(startsAndEnds_[j].first < startsAndEnds_[j].second,
                    "a cap must start before it ends: " + j);
            QL.require(startsAndEnds_[j].second <= accruals.length,
                    "a cap must end within the underlying caplets: " + j);
        }

        this.innerCashFlowSizes_ = new int[accruals.length];
        this.innerCashFlowsGenerated_ =
                new CashFlow[accruals.length][underlyingCaplets_.maxNumberOfCashFlowsPerProductPerStep()];
        for (int i = 0; i < accruals.length; ++i) {
            for (int j = 0; j < underlyingCaplets_.maxNumberOfCashFlowsPerProductPerStep(); ++j) {
                innerCashFlowsGenerated_[i][j] = new CashFlow(0, new double[accruals.length + 1]);
            }
        }
    }

    @Override
    public int[] suggestedNumeraires() {
        return underlyingCaplets_.suggestedNumeraires();
    }

    @Override
    public EvolutionDescription evolution() {
        return underlyingCaplets_.evolution();
    }

    @Override
    public double[] possibleCashFlowTimes() {
        return underlyingCaplets_.possibleCashFlowTimes();
    }

    @Override
    public int numberOfProducts() {
        return startsAndEnds_.length;
    }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return underlyingCaplets_.maxNumberOfCashFlowsPerProductPerStep();
    }

    @Override
    public boolean alreadyDeflated() {
        return underlyingCaplets_.alreadyDeflated();
    }

    @Override
    public void reset() {
        underlyingCaplets_.reset();
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final CashFlow[][] cashFlowsGenerated) {
        final boolean done = underlyingCaplets_.nextTimeStep(
                currentState, innerCashFlowSizes_, innerCashFlowsGenerated_);

        for (int k = 0; k < startsAndEnds_.length; ++k) {
            numberCashFlowsThisStep[k] = 0;
        }

        for (int j = 0; j < numberRates_; ++j) {
            if (innerCashFlowSizes_[j] > 0) {
                for (int k = 0; k < startsAndEnds_.length; ++k) {
                    if (startsAndEnds_[k].first <= j && j < startsAndEnds_[k].second) {
                        for (int l = 0; l < innerCashFlowSizes_[j]; ++l) {
                            cashFlowsGenerated[k][numberCashFlowsThisStep[k]++] =
                                    innerCashFlowsGenerated_[j][l];
                        }
                    }
                }
            }
        }
        return done;
    }

    @Override
    public MarketModelPathwiseMultiProduct clone() {
        return new MarketModelPathwiseMultiDeflatedCap(
                origRateTimes_, origAccruals_, origPaymentTimes_, origStrike_, startsAndEnds_);
    }
}

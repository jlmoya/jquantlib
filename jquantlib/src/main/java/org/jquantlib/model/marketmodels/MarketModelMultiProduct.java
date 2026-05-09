/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.model.marketmodels;

import java.util.List;

/**
 * Market-model product abstract base.
 * <p>
 * Encapsulates the notion of a product: it contains the information that
 * would be in the termsheet of the product. Useful to be able to do several
 * products simultaneously (they must share the same underlying rate times).
 * For each time evolved to, it generates the cash flows associated to that
 * time for the state of the yield curve. Callable products encompass the
 * product and its exercise strategy.
 *
 * @see "ql/models/marketmodels/multiproduct.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public abstract class MarketModelMultiProduct {

    /**
     * A cash flow occurring at a discrete time index with a given amount.
     * Mirrors C++ {@code MarketModelMultiProduct::CashFlow}.
     */
    public static final class CashFlow {
        /** Index into {@link MarketModelMultiProduct#possibleCashFlowTimes()}. */
        public int timeIndex;
        public double amount;

        public CashFlow() {
            this.timeIndex = 0;
            this.amount = 0.0;
        }

        public CashFlow(final int timeIndex, final double amount) {
            this.timeIndex = timeIndex;
            this.amount = amount;
        }
    }

    public abstract int[] suggestedNumeraires();

    public abstract EvolutionDescription evolution();

    public abstract double[] possibleCashFlowTimes();

    public abstract int numberOfProducts();

    public abstract int maxNumberOfCashFlowsPerProductPerStep();

    /** Resets the product to the start of the simulation path. */
    public abstract void reset();

    /**
     * Evolve one step. The product may emit cash flows for one or more
     * sub-products; for product i, {@code numberCashFlowsThisStep[i]} entries
     * of {@code cashFlowsGenerated[i]} are populated.
     *
     * @return {@code true} when the simulation path has finished.
     */
    public abstract boolean nextTimeStep(
            CurveState currentState,
            int[] numberCashFlowsThisStep,
            CashFlow[][] cashFlowsGenerated);

    /** Returns a newly-allocated copy of itself. */
    public abstract MarketModelMultiProduct clone();
}

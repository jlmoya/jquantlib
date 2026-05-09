/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k L0.4.

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
 Copyright (C) 2006, 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

/**
 * Abstract base for pathwise (adjoint-Greeks) market-model multi-products.
 * <p>
 * Mirrors C++ {@code class MarketModelPathwiseMultiProduct}
 * (ql/models/marketmodels/pathwisemultiproduct.hpp v1.42.1).
 * <p>
 * This class differs from {@link MarketModelMultiProduct} in that each cash
 * flow carries a vector of amounts rather than a scalar — one amount per
 * LIBOR rate — encoding both the payoff value (amount[0] by convention in
 * the pathwise products) and the path-wise derivative of the payoff with
 * respect to each forward rate (for Giles-Glasserman adjoint Delta).
 * <p>
 * The inner {@link CashFlow} struct here uses {@code double[] amount}
 * (vector), distinguishing it from the scalar {@code double amount} in
 * {@link MarketModelMultiProduct.CashFlow}.
 *
 * @see MarketModelMultiProduct
 * @see "ql/models/marketmodels/pathwisemultiproduct.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public abstract class MarketModelPathwiseMultiProduct {

    /**
     * A pathwise cash flow occurring at a discrete time index.
     * <p>
     * Mirrors C++ {@code struct MarketModelPathwiseMultiProduct::CashFlow}.
     * <p>
     * Unlike {@link MarketModelMultiProduct.CashFlow}, the {@code amount}
     * field is a {@code double[]} with one entry per forward rate:
     * <ul>
     *   <li>{@code amount[0]} — the cash-flow amount itself (payoff value)</li>
     *   <li>{@code amount[i]} for {@code i > 0} — partial derivative of the
     *       payoff with respect to forward rate {@code i-1} (used for
     *       adjoint Delta calculation)</li>
     * </ul>
     * The exact semantics of each slot depend on the concrete product.
     */
    public static final class CashFlow {

        /** Index into {@link MarketModelPathwiseMultiProduct#possibleCashFlowTimes()}. */
        public int timeIndex;

        /**
         * Per-rate amount vector: {@code amount[0]} is the payoff;
         * {@code amount[i]} (i ≥ 1) is ∂payoff/∂rate_{i-1}.
         * Mirrors C++ {@code std::vector<Real> amount}.
         */
        public double[] amount;

        /** Default constructor — timeIndex = 0, amount is empty. */
        public CashFlow() {
            this.timeIndex = 0;
            this.amount = new double[0];
        }

        /**
         * Constructor with explicit timeIndex and amount vector.
         *
         * @param timeIndex index into possibleCashFlowTimes()
         * @param amount    payoff + derivatives vector (not defensively copied)
         */
        public CashFlow(final int timeIndex, final double[] amount) {
            this.timeIndex = timeIndex;
            this.amount = amount;
        }
    }

    // -------------------------------------------------------------------------
    // Abstract interface — mirrors C++ pure virtual functions
    // -------------------------------------------------------------------------

    /**
     * Returns suggested numeraire indices (one per evolution step).
     * Mirrors C++ {@code std::vector<Size> suggestedNumeraires() const}.
     */
    public abstract int[] suggestedNumeraires();

    /**
     * Returns the evolution description (rate times, step structure).
     * Mirrors C++ {@code const EvolutionDescription& evolution() const}.
     */
    public abstract EvolutionDescription evolution();

    /**
     * Returns the sorted list of times at which cash flows can occur.
     * Mirrors C++ {@code std::vector<Time> possibleCashFlowTimes() const}.
     */
    public abstract double[] possibleCashFlowTimes();

    /**
     * Returns the number of simultaneous products (sub-payoffs).
     * Mirrors C++ {@code Size numberOfProducts() const}.
     */
    public abstract int numberOfProducts();

    /**
     * Returns the maximum number of cash flows per product per time step.
     * Mirrors C++ {@code Size maxNumberOfCashFlowsPerProductPerStep() const}.
     */
    public abstract int maxNumberOfCashFlowsPerProductPerStep();

    /**
     * Returns {@code true} if the cash flows produced by this product have
     * already been deflated by the numeraire; {@code false} if the accounting
     * engine must deflate them.
     * Mirrors C++ {@code bool alreadyDeflated() const}.
     */
    public abstract boolean alreadyDeflated();

    /**
     * Resets the product to the start of a new simulation path.
     * Mirrors C++ {@code void reset()}.
     */
    public abstract void reset();

    /**
     * Evolves one simulation step. Fills {@code numberCashFlowsThisStep[i]}
     * and {@code cashFlowsGenerated[i][0..nCF-1]} for each product {@code i}.
     * <p>
     * Mirrors C++ {@code bool nextTimeStep(const CurveState&,
     * std::vector<Size>&, std::vector<std::vector<CashFlow>>&)}.
     *
     * @param currentState              current yield-curve state
     * @param numberCashFlowsThisStep   output: number of cash flows emitted per
     *                                  product this step; length = numberOfProducts()
     * @param cashFlowsGenerated        output: cash flows per product; outer
     *                                  dimension = numberOfProducts(), inner
     *                                  dimension ≥ maxNumberOfCashFlowsPerProductPerStep()
     * @return {@code true} when the simulation path has finished
     */
    public abstract boolean nextTimeStep(
            CurveState currentState,
            int[] numberCashFlowsThisStep,
            CashFlow[][] cashFlowsGenerated);

    /**
     * Returns a newly-allocated deep copy of itself.
     * Mirrors C++ {@code std::unique_ptr<MarketModelPathwiseMultiProduct> clone() const}.
     */
    public abstract MarketModelPathwiseMultiProduct clone();
}

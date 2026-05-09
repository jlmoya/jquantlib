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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.math.statistics.GenericSequenceStatistics;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct.CashFlow;

/**
 * Engine collecting cash flows along a market-model simulation.
 * <p>
 * Drives a {@link MarketModelEvolver} step by step, asks the
 * {@link MarketModelMultiProduct} for cash flows at each step, and
 * accumulates path NPVs in numeraire units. The result of one path is the
 * vector of products' NPVs scaled by the initial numeraire value.
 *
 * @see "ql/models/marketmodels/accountingengine.{hpp,cpp}" v1.42.1
 *
 * @author Ueli Hofstetter (original empty stub)
 * @author Jose Moya (Phase 3h B.8 — full body)
 */
public class AccountingEngine {

    private final MarketModelEvolver evolver_;
    private final MarketModelMultiProduct product_;
    private final double initialNumeraireValue_;
    private final int numberProducts_;

    // workspace
    private final double[] numerairesHeld_;
    private final int[] numberCashFlowsThisStep_;
    private final CashFlow[][] cashFlowsGenerated_;
    private final MarketModelDiscounter[] discounters_;

    public AccountingEngine(
            final MarketModelEvolver evolver,
            final MarketModelMultiProduct product,
            final double initialNumeraireValue) {
        this.evolver_ = evolver;
        // C++ Clone<MarketModelMultiProduct> performs a deep copy via clone();
        // mirror via product.clone() so subsequent reset() calls don't mutate
        // the caller-supplied product.
        this.product_ = product.clone();
        this.initialNumeraireValue_ = initialNumeraireValue;
        this.numberProducts_ = product_.numberOfProducts();

        this.numerairesHeld_ = new double[numberProducts_];
        this.numberCashFlowsThisStep_ = new int[numberProducts_];
        this.cashFlowsGenerated_ = new CashFlow[numberProducts_][];
        for (int i = 0; i < numberProducts_; ++i) {
            cashFlowsGenerated_[i] = new CashFlow[product_.maxNumberOfCashFlowsPerProductPerStep()];
            for (int k = 0; k < cashFlowsGenerated_[i].length; ++k) {
                cashFlowsGenerated_[i][k] = new CashFlow();
            }
        }

        final double[] cashFlowTimes = product_.possibleCashFlowTimes();
        final double[] rateTimes = product_.evolution().rateTimes();
        this.discounters_ = new MarketModelDiscounter[cashFlowTimes.length];
        for (int i = 0; i < cashFlowTimes.length; ++i) {
            discounters_[i] = new MarketModelDiscounter(cashFlowTimes[i], rateTimes);
        }
    }

    /**
     * Run one path; output the per-product NPVs (in {@code values}) and return
     * the path weight contribution from the evolver.
     * <p>
     * Mirrors C++ {@code AccountingEngine::singlePathValues}.
     */
    private double singlePathValues(final double[] values) {
        for (int i = 0; i < numerairesHeld_.length; ++i) {
            numerairesHeld_[i] = 0.0;
        }
        double weight = evolver_.startNewPath();
        product_.reset();
        double principalInNumerairePortfolio = 1.0;

        boolean done = false;
        do {
            final int thisStep = evolver_.currentStep();
            weight *= evolver_.advanceStep();
            done = product_.nextTimeStep(evolver_.currentState(),
                    numberCashFlowsThisStep_, cashFlowsGenerated_);
            final int numeraire = evolver_.numeraires()[thisStep];

            // for each product...
            for (int i = 0; i < numberProducts_; ++i) {
                final CashFlow[] cashflows = cashFlowsGenerated_[i];
                // ...and each cash flow...
                for (int j = 0; j < numberCashFlowsThisStep_[i]; ++j) {
                    // ...convert the cash flow to numeraires.
                    // This is done by calculating the number of
                    // numeraire bonds corresponding to such cash flow...
                    final MarketModelDiscounter discounter = discounters_[cashflows[j].timeIndex];

                    final double bonds = cashflows[j].amount
                            * discounter.numeraireBonds(evolver_.currentState(), numeraire);

                    // ...and adding the newly bought bonds to the number
                    // of numeraires held.
                    numerairesHeld_[i] += bonds / principalInNumerairePortfolio;
                }
            }

            if (!done) {
                // The numeraire might change between steps. Convert the
                // numeraire bonds for this step into a corresponding amount of
                // numeraire bonds for the next step by changing the principal
                // of the numeraire portfolio.
                final int nextNumeraire = evolver_.numeraires()[thisStep + 1];
                principalInNumerairePortfolio *=
                        evolver_.currentState().discountRatio(numeraire, nextNumeraire);
            }
        } while (!done);

        for (int i = 0; i < numerairesHeld_.length; ++i) {
            values[i] = numerairesHeld_[i] * initialNumeraireValue_;
        }
        return weight;
    }

    /**
     * Accumulate {@code numberOfPaths} path realizations of the per-product
     * NPV vector into {@code stats}.
     * <p>
     * Mirrors C++ {@code AccountingEngine::multiplePathValues}; uses
     * Java {@link GenericSequenceStatistics} (incremental statistics) which
     * mirrors C++ {@code SequenceStatisticsInc} per design P3H-3.
     */
    public void multiplePathValues(final GenericSequenceStatistics stats, final int numberOfPaths) {
        final double[] values = new double[product_.numberOfProducts()];
        for (int i = 0; i < numberOfPaths; ++i) {
            final double weight = singlePathValues(values);
            stats.add(values, weight);
        }
    }
}

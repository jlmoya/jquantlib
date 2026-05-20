/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 1 closure A3-D-548-proxygreek.

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

import org.jquantlib.math.statistics.GenericSequenceStatistics;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct.CashFlow;

/**
 * Engine producing proxy Greeks via constrained bumped Monte-Carlo paths.
 *
 * <p>Faithful Java port of C++
 * {@code QuantLib::ProxyGreekEngine}
 * (ql/models/marketmodels/proxygreekengine.{hpp,cpp} v1.42.1).
 *
 * <p>The "partial proxy simulation" of Giles &amp; Glasserman: the
 * <em>original</em> {@link MarketModelEvolver} produces the unbumped path
 * and records its swap-rate constraints at each evolution step. For each
 * "bump set" (delta, gamma, vega, …) the engine then re-runs the path on
 * a battery of {@link ConstrainedEvolver}s configured with bumped model
 * data and the recorded constraints, so the bumped paths see the same
 * pseudo-random innovations as the unbumped one. Linear combinations of
 * the bumped per-product values produce the proxy Greek estimates.
 *
 * <p>Java mapping notes:
 * <ul>
 *   <li>C++ {@code SequenceStatisticsInc} (typedef of
 *       {@code GenericSequenceStatistics<IncrementalStatistics>}) maps to
 *       Java {@link GenericSequenceStatistics} — see design decision
 *       P3H-3 (the Java template is already incremental).</li>
 *   <li>C++ {@code Clone<MarketModelMultiProduct>} → Java {@code product.clone()}
 *       on construction (matches {@link AccountingEngine}).</li>
 *   <li>C++ {@code std::valarray<bool> constraintsActive_} → Java
 *       {@code boolean[]}.</li>
 * </ul>
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/proxygreekengine.{hpp,cpp}" v1.42.1
 * @see AccountingEngine
 */
public class ProxyGreekEngine {

    private final MarketModelEvolver originalEvolver_;
    private final ConstrainedEvolver[][] constrainedEvolvers_;
    private final double[][][] diffWeights_;
    private final int[] startIndexOfConstraint_;
    private final int[] endIndexOfConstraint_;
    private final MarketModelMultiProduct product_;
    private final double initialNumeraireValue_;
    private final int numberProducts_;

    // workspace
    private final double[] constraints_;
    private final boolean[] constraintsActive_;
    private final double[] numerairesHeld_;
    private final int[] numberCashFlowsThisStep_;
    private final CashFlow[][] cashFlowsGenerated_;
    private final MarketModelDiscounter[] discounters_;

    public ProxyGreekEngine(final MarketModelEvolver evolver,
                            final ConstrainedEvolver[][] constrainedEvolvers,
                            final double[][][] diffWeights,
                            final int[] startIndexOfConstraint,
                            final int[] endIndexOfConstraint,
                            final MarketModelMultiProduct product,
                            final double initialNumeraireValue) {
        this.originalEvolver_ = evolver;
        this.constrainedEvolvers_ = constrainedEvolvers;
        this.diffWeights_ = diffWeights;
        this.startIndexOfConstraint_ = startIndexOfConstraint;
        this.endIndexOfConstraint_ = endIndexOfConstraint;
        // C++ Clone<MarketModelMultiProduct> → deep copy via clone()
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
        for (int j = 0; j < cashFlowTimes.length; ++j) {
            discounters_[j] = new MarketModelDiscounter(cashFlowTimes[j], rateTimes);
        }

        final double[] evolutionTimes = product_.evolution().evolutionTimes();
        this.constraints_ = new double[evolutionTimes.length];
        this.constraintsActive_ = new boolean[evolutionTimes.length];
    }

    /**
     * Run one path through the original evolver (recording constraints) and
     * one path through every constrained evolver (replaying constraints).
     *
     * @param values          [N] output per-product NPVs from the original path
     * @param modifiedValues  [bumpSet][evolverInSet][N] output per-product
     *                        NPVs from the constrained evolvers (rows in
     *                        {@code constrainedEvolvers_})
     */
    public void singlePathValues(final double[] values,
                                 final double[][][] modifiedValues) {
        singleEvolverValues(originalEvolver_, values, true);
        for (int i = 0; i < constrainedEvolvers_.length; ++i) {
            for (int j = 0; j < constrainedEvolvers_[i].length; ++j) {
                constrainedEvolvers_[i][j].setThisConstraint(constraints_, constraintsActive_);
                singleEvolverValues(constrainedEvolvers_[i][j], modifiedValues[i][j], false);
            }
        }
    }

    /**
     * Accumulate {@code numberOfPaths} path realizations into the unbumped
     * statistics and into the per-bump-set Greek statistics.
     *
     * @param stats         [N] sequence-stats for the unbumped per-product NPVs
     * @param modifiedStats [bumpSet][weightIndex] sequence-stats receiving the
     *                      linear combinations defined by {@code diffWeights_}
     * @param numberOfPaths number of MC paths
     */
    public void multiplePathValues(final GenericSequenceStatistics stats,
                                   final GenericSequenceStatistics[][] modifiedStats,
                                   final int numberOfPaths) {
        final int N = product_.numberOfProducts();

        final double[] values = new double[N];
        // modifiedValues[i][j] is the per-product NPV vector from the j-th
        // constrained evolver inside the i-th bump set.
        final double[][][] modifiedValues = new double[constrainedEvolvers_.length][][];
        for (int i = 0; i < modifiedValues.length; ++i) {
            modifiedValues[i] = new double[constrainedEvolvers_[i].length][N];
        }

        final double[] results = new double[N];

        for (int i = 0; i < numberOfPaths; ++i) {
            singlePathValues(values, modifiedValues);
            stats.add(values);

            for (int j = 0; j < diffWeights_.length; ++j) {
                for (int k = 0; k < diffWeights_[j].length; ++k) {
                    final double[] weights = diffWeights_[j][k];
                    for (int l = 0; l < N; ++l) {
                        results[l] = weights[0] * values[l];
                        for (int n = 1; n < weights.length; ++n) {
                            results[l] += weights[n] * modifiedValues[j][n - 1][l];
                        }
                    }
                    modifiedStats[j][k].add(results);
                }
            }
        }
    }

    /**
     * Drive a single evolver to completion, accumulating per-product NPVs in
     * {@code values}. When {@code storeRates} is {@code true} the swap-rate
     * constraint at each evolution step is recorded for later replay through
     * a {@link ConstrainedEvolver}.
     */
    private void singleEvolverValues(final MarketModelEvolver evolver,
                                     final double[] values,
                                     final boolean storeRates) {

        for (int i = 0; i < numerairesHeld_.length; ++i) {
            numerairesHeld_[i] = 0.0;
        }
        double weight = evolver.startNewPath();
        product_.reset();
        double principalInNumerairePortfolio = 1.0;

        if (storeRates) {
            for (int i = 0; i < constraintsActive_.length; ++i) {
                constraintsActive_[i] = false;
            }
        }

        boolean done = false;
        do {
            final int thisStep = evolver.currentStep();
            weight *= evolver.advanceStep();
            done = product_.nextTimeStep(evolver.currentState(),
                                         numberCashFlowsThisStep_,
                                         cashFlowsGenerated_);
            if (storeRates) {
                constraints_[thisStep] = evolver.currentState().swapRate(
                        startIndexOfConstraint_[thisStep],
                        endIndexOfConstraint_[thisStep]);
                constraintsActive_[thisStep] = true;
            }

            final int numeraire = evolver.numeraires()[thisStep];

            // for each product...
            for (int i = 0; i < numberProducts_; ++i) {
                final CashFlow[] cashflows = cashFlowsGenerated_[i];
                // ...and each cash flow...
                for (int j = 0; j < numberCashFlowsThisStep_[i]; ++j) {
                    // ...convert the cash flow to numeraires.
                    final MarketModelDiscounter discounter = discounters_[cashflows[j].timeIndex];
                    final double bonds = cashflows[j].amount
                            * discounter.numeraireBonds(evolver.currentState(), numeraire);
                    // ...and add the newly bought bonds to the number held.
                    numerairesHeld_[i] += weight * bonds / principalInNumerairePortfolio;
                }
            }

            if (!done) {
                // The numeraire might change between steps; rescale the
                // principal of the numeraire portfolio accordingly.
                final int nextNumeraire = evolver.numeraires()[thisStep + 1];
                principalInNumerairePortfolio *=
                        evolver.currentState().discountRatio(numeraire, nextNumeraire);
            }
        } while (!done);

        for (int i = 0; i < numerairesHeld_.length; ++i) {
            values[i] = numerairesHeld_[i] * initialNumeraireValue_;
        }
    }
}

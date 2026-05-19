/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.11.

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

package org.jquantlib.model.marketmodels;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.statistics.SequenceStatistics;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateEuler;

import java.util.ArrayList;
import java.util.List;

/**
 * Accounting engine collecting cash flows along a market-model simulation for doing pathwise computation of Deltas
 * using the Giles-Glasserman smoking-adjoints method. Only works with the displaced LMM evolver, and requires knowledge
 * of pseudo-roots and displacements.
 *
 * <p>Mirrors C++ {@code PathwiseAccountingEngine}
 * (ql/models/marketmodels/pathwiseaccountingengine.{hpp,cpp} v1.42.1). Tested in {@code testPathwiseGreeks}.
 *
 * <p>The {@link #singlePathValues(double[])} routine implements the smoking-
 * adjoint algorithm: forward sweep gathering cash flows; backward sweep propagating per-rate sensitivities (V) using
 * the chain-rule via the pseudoRoot, LIBOR ratios, taus, and step-discounts.
 *
 * <p><b>Note:</b> the companion {@code PathwiseVegasAccountingEngine}
 * (Greek-vega variant) is deferred to Phase 3k.5 — it has the same structure but adds bump-cluster machinery and a
 * {@link org.jquantlib.model.marketmodels.pathwisegreeks.RatePseudoRootJacobian} propagation.
 *
 * @author Jose Moya
 */
public class PathwiseAccountingEngine {

    private final LogNormalFwdRateEuler evolver_;
    private final MarketModelPathwiseMultiProduct product_;
    private final MarketModel pseudoRootStructure_;

    private final double initialNumeraireValue_;
    private final int numberProducts_;
    private final int numberRates_;
    private final int numberCashFlowTimes_;
    private final int numberSteps_;
    private final boolean doDeflation_;
    // workspace
    private final double[] numerairesHeld_;
    private final int[] numberCashFlowsThisStep_;
    private final MarketModelPathwiseMultiProduct.CashFlow[][] cashFlowsGenerated_;
    private final List< MarketModelPathwiseDiscounter > discounters_;
    private final List< Matrix > V_;  // one V per product, dim [numberSteps_+1][numberRates_]
    private final Matrix LIBORRatios_;
    private final Matrix Discounts_;
    private final Matrix StepsDiscountsSquared_;
    private final Matrix LIBORRates_;
    private final Matrix partials_;
    private final double[] deflatorAndDerivatives_;
    private final int[][] numberCashFlowsThisIndex_;
    private final List< Matrix > totalCashFlowsThisIndex_;
    private final List< List< Integer > > cashFlowIndicesThisStep_;
    private double[] currentForwards_;
    private double[] lastForwards_;

    public PathwiseAccountingEngine(final LogNormalFwdRateEuler evolver, final MarketModelPathwiseMultiProduct product,
            final MarketModel pseudoRootStructure, final double initialNumeraireValue) {
        this.evolver_ = evolver;
        this.product_ = product.clone();
        this.pseudoRootStructure_ = pseudoRootStructure;
        this.initialNumeraireValue_ = initialNumeraireValue;
        this.numberProducts_ = this.product_.numberOfProducts();
        this.doDeflation_ = !this.product_.alreadyDeflated();
        this.numerairesHeld_ = new double[numberProducts_];
        this.numberCashFlowsThisStep_ = new int[numberProducts_];
        this.cashFlowsGenerated_ = new MarketModelPathwiseMultiProduct.CashFlow[numberProducts_][];
        this.deflatorAndDerivatives_ = new double[pseudoRootStructure_.numberOfRates() + 1];

        this.numberRates_ = pseudoRootStructure_.numberOfRates();
        this.numberSteps_ = pseudoRootStructure_.numberOfSteps();

        this.Discounts_ = new Matrix(numberSteps_ + 1, numberRates_ + 1);
        for ( int i = 0; i <= numberSteps_; ++i ) {
            Discounts_.set(i, 0, 1.0);
        }

        this.V_ = new ArrayList<>(numberProducts_);
        this.numberCashFlowTimes_ = this.product_.possibleCashFlowTimes().length;
        final int possibleCFCount = this.numberCashFlowTimes_;

        this.numberCashFlowsThisIndex_ = new int[numberProducts_][possibleCFCount];
        this.totalCashFlowsThisIndex_ = new ArrayList<>(numberProducts_);

        for ( int i = 0; i < numberProducts_; ++i ) {
            cashFlowsGenerated_[i] = new MarketModelPathwiseMultiProduct.CashFlow[this.product_.maxNumberOfCashFlowsPerProductPerStep()];
            for ( int j = 0; j < cashFlowsGenerated_[i].length; ++j ) {
                cashFlowsGenerated_[i][j] = new MarketModelPathwiseMultiProduct.CashFlow(0,
                        new double[numberRates_ + 1]);
            }
            V_.add(new Matrix(numberSteps_ + 1, numberRates_));
            totalCashFlowsThisIndex_.add(new Matrix(possibleCFCount, numberRates_ + 1));
        }

        this.LIBORRatios_ = new Matrix(numberSteps_ + 1, numberRates_);
        this.StepsDiscountsSquared_ = new Matrix(numberSteps_ + 1, numberRates_);
        this.LIBORRates_ = new Matrix(numberSteps_ + 1, numberRates_);

        final double[] cashFlowTimes = this.product_.possibleCashFlowTimes();
        final double[] rateTimes = this.product_.evolution().rateTimes();
        final double[] evolutionTimes = this.product_.evolution().evolutionTimes();
        this.discounters_ = new ArrayList<>(cashFlowTimes.length);
        for ( final double cashFlowTime : cashFlowTimes ) {
            discounters_.add(new MarketModelPathwiseDiscounter(cashFlowTime, rateTimes));
        }

        // Allocate cash-flow times to steps: for each step, what cash flow time
        // indices to look at. C++ uses upper_bound-1 to find the last evolution
        // time <= cashFlowTime.
        this.cashFlowIndicesThisStep_ = new ArrayList<>(numberSteps_);
        for ( int s = 0; s < numberSteps_; ++s ) {
            cashFlowIndicesThisStep_.add(new ArrayList<>());
        }
        for ( int i = 0; i < numberCashFlowTimes_; ++i ) {
            int idx = upperBound(evolutionTimes, cashFlowTimes[i]);
            if ( idx > 0 )
                idx--;
            cashFlowIndicesThisStep_.get(idx).add(i);
        }

        this.partials_ = new Matrix(pseudoRootStructure_.numberOfFactors(), numberRates_);
    }

    /**
     * Mirrors {@code std::upper_bound}: returns the first index {@code i} in {@code arr[0..arr.length]} such that
     * {@code arr[i] > value}, or {@code arr.length} if no such index exists.
     */
    private static int upperBound(final double[] arr, final double value) {
        int lo = 0;
        int hi = arr.length;
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( arr[mid] <= value ) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * Runs one path through the evolver, accumulates cash flows and pathwise derivatives, fills {@code values[]} with
     * payoff + per-product Delta vectors, returns the path weight (always 1.0 here because the actual weight is folded
     * into the cash flows during the forward sweep — preserves the C++ low-variance convention).
     */
    public double singlePathValues(final double[] values) {
        final double[] initialForwards = pseudoRootStructure_.initialRates();
        currentForwards_ = initialForwards.clone();

        // clear accumulation variables
        for ( int i = 0; i < numberProducts_; ++i ) {
            numerairesHeld_[i] = 0.0;

            for ( int j = 0; j < numberCashFlowTimes_; ++j ) {
                numberCashFlowsThisIndex_[i][j] = 0;
                for ( int k = 0; k <= numberRates_; ++k ) {
                    totalCashFlowsThisIndex_.get(i).set(j, k, 0.0);
                }
            }

            final Matrix Vi = V_.get(i);
            for ( int l = 0; l < numberRates_; ++l ) {
                for ( int m = 0; m <= numberSteps_; ++m ) {
                    Vi.set(m, l, 0.0);
                }
            }
        }

        double weight = evolver_.startNewPath();
        product_.reset();

        int thisStep;
        boolean done = false;
        do {
            thisStep = evolver_.currentStep();
            final int storeStep = thisStep + 1;
            weight *= evolver_.advanceStep();

            done = product_.nextTimeStep(evolver_.currentState(), numberCashFlowsThisStep_, cashFlowsGenerated_);

            lastForwards_ = currentForwards_;
            currentForwards_ = evolver_.currentState().forwardRates().clone();

            for ( int i = 0; i < numberRates_; ++i ) {
                final double x = evolver_.currentState().discountRatio(i + 1, i);
                StepsDiscountsSquared_.set(storeStep, i, x * x);
                LIBORRatios_.set(storeStep, i, currentForwards_[i] / lastForwards_[i]);
                LIBORRates_.set(storeStep, i, currentForwards_[i]);
                Discounts_.set(storeStep, i + 1, evolver_.currentState().discountRatio(i + 1, 0));
            }

            // Gather cash flows
            for ( int i = 0; i < numberProducts_; ++i ) {
                for ( int j = 0; j < numberCashFlowsThisStep_[i]; ++j ) {
                    final int k = cashFlowsGenerated_[i][j].timeIndex;
                    ++numberCashFlowsThisIndex_[i][k];
                    final Matrix tcf = totalCashFlowsThisIndex_.get(i);
                    for ( int l = 0; l <= numberRates_; ++l ) {
                        tcf.set(k, l, tcf.get(k, l) + cashFlowsGenerated_[i][j].amount[l] * weight);
                    }
                }
            }
        } while ( !done );

        // Backwards sweep to propagate adjoint sensitivities.
        final int factors = pseudoRootStructure_.numberOfFactors();
        final double[] taus = pseudoRootStructure_.evolution().rateTaus();

        boolean flowsFound = false;
        final int finalStepDone = thisStep;

        for ( int currentStep = numberSteps_ - 1; currentStep >= 0; --currentStep ) {
            final int stepToUse = Math.min(currentStep, finalStepDone) + 1;

            for ( int k = 0; k < cashFlowIndicesThisStep_.get(currentStep).size(); ++k ) {
                final int cashFlowIndex = cashFlowIndicesThisStep_.get(currentStep).get(k);

                boolean noFlows = true;
                for ( int l = 0; l < numberProducts_ && noFlows; ++l ) {
                    noFlows = noFlows && (numberCashFlowsThisIndex_[l][cashFlowIndex] == 0);
                }

                flowsFound = flowsFound || !noFlows;

                if ( !noFlows ) {
                    if ( doDeflation_ ) {
                        discounters_.get(cashFlowIndex)
                                .getFactors(LIBORRates_, Discounts_, stepToUse, deflatorAndDerivatives_);
                    }

                    for ( int j = 0; j < numberProducts_; ++j ) {
                        if ( numberCashFlowsThisIndex_[j][cashFlowIndex] > 0 ) {
                            final Matrix tcf = totalCashFlowsThisIndex_.get(j);
                            double deflatedCashFlow = tcf.get(cashFlowIndex, 0);
                            if ( doDeflation_ ) {
                                deflatedCashFlow *= deflatorAndDerivatives_[0];
                            }
                            numerairesHeld_[j] += deflatedCashFlow;

                            final Matrix Vj = V_.get(j);
                            for ( int i = 1; i <= numberRates_; ++i ) {
                                double thisDerivative = tcf.get(cashFlowIndex, i);
                                if ( doDeflation_ ) {
                                    thisDerivative *= deflatorAndDerivatives_[0];
                                    thisDerivative += tcf.get(cashFlowIndex, 0) * deflatorAndDerivatives_[i];
                                }
                                Vj.set(stepToUse, i - 1, Vj.get(stepToUse, i - 1) + thisDerivative);
                            }
                        }
                    }
                }
            }

            // Backwards updating
            if ( flowsFound ) {
                final int nextStepToUse = Math.min(currentStep - 1, finalStepDone);
                final int nextStepIndex = nextStepToUse + 1;
                if ( nextStepIndex != stepToUse ) {
                    final Matrix thisPseudoRoot = pseudoRootStructure_.pseudoRoot(currentStep);

                    for ( int i = 0; i < numberProducts_; ++i ) {
                        final Matrix Vi = V_.get(i);

                        // Compute partials
                        for ( int f = 0; f < factors; ++f ) {
                            final double libor = LIBORRates_.get(stepToUse, numberRates_ - 1);
                            final double V = Vi.get(stepToUse, numberRates_ - 1);
                            final double pseudo = thisPseudoRoot.get(numberRates_ - 1, f);
                            partials_.set(f, numberRates_ - 1, libor * V * pseudo);

                            for ( int r = numberRates_ - 2; r >= 0; --r ) {
                                final double thisPartialTermr =
                                        LIBORRates_.get(stepToUse, r) * Vi.get(stepToUse, r) * thisPseudoRoot.get(r, f);
                                partials_.set(f, r, partials_.get(f, r + 1) + thisPartialTermr);
                            }
                        }

                        for ( int j = 0; j < numberRates_; ++j ) {
                            final double nextV = Vi.get(stepToUse, j) * LIBORRatios_.get(stepToUse, j);
                            Vi.set(nextStepIndex, j, nextV);

                            double summandTerm = 0.0;
                            for ( int f = 0; f < factors; ++f ) {
                                summandTerm += thisPseudoRoot.get(j, f) * partials_.get(f, j);
                            }
                            summandTerm *= taus[j] * StepsDiscountsSquared_.get(stepToUse, j);
                            Vi.set(nextStepIndex, j, Vi.get(nextStepIndex, j) + summandTerm);
                        }
                    }
                }
            }
        }

        // Write answer into values
        for ( int i = 0; i < numberProducts_; ++i ) {
            values[i] = numerairesHeld_[i] * initialNumeraireValue_;
            final Matrix Vi = V_.get(i);
            for ( int j = 0; j < numberRates_; ++j ) {
                values[(i + 1) * numberProducts_ + j] = Vi.get(0, j) * initialNumeraireValue_;
            }
        }

        // Weight already folded into cash flows for variance reduction.
        return 1.0;
    }

    /** Runs many paths and accumulates per-product means + Greeks into stats. */
    public void multiplePathValues(final SequenceStatistics stats, final int numberOfPaths) {
        final double[] values = new double[numberProducts_ * (numberRates_ + 1)];
        for ( int i = 0; i < numberOfPaths; ++i ) {
            final double weight = singlePathValues(values);
            stats.add(values, weight);
        }
    }
}

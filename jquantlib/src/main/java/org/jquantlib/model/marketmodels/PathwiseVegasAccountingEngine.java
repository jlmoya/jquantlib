/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k.5 C.2.

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

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.evolvers.LogNormalFwdRateEuler;
import org.jquantlib.model.marketmodels.pathwisegreeks.RatePseudoRootJacobian;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Accounting engine for pathwise computation of Deltas <em>and</em> Vegas using the Giles-Glasserman smoking-adjoints
 * method.
 *
 * <p>Only works with the displaced LMM evolver. Discretely-compounding
 * money-market measure is required. For each vega, the bump is given as a vector of matrices (one per step); all linear
 * combinations of the bumps are performed as early as possible during the forward sweep. This contrasts with
 * {@link PathwiseVegasOuterAccountingEngine} which performs them as late as possible (after the full path).
 *
 * <p>Mirrors C++ {@code PathwiseVegasAccountingEngine}
 * (ql/models/marketmodels/pathwiseaccountingengine.{hpp,cpp} v1.42.1). Tested in MarketModelTest::testPathwiseVegas.
 *
 * @author Jose Moya
 */
public class PathwiseVegasAccountingEngine {

    private final LogNormalFwdRateEuler evolver_;
    private final MarketModelPathwiseMultiProduct product_;
    private final MarketModel pseudoRootStructure_;
    private final int[] numeraires_;

    private final double initialNumeraireValue_;
    private final int numberProducts_;
    private final int numberRates_;
    private final int numberCashFlowTimes_;
    private final int numberSteps_;
    private final int numberBumps_;

    private final List< RatePseudoRootJacobian > jacobianComputers_;

    private final boolean doDeflation_;
    private final double[] numerairesHeld_;
    private final int[] numberCashFlowsThisStep_;
    private final MarketModelPathwiseMultiProduct.CashFlow[][] cashFlowsGenerated_;
    private final List< MarketModelPathwiseDiscounter > discounters_;
    private final List< Matrix > V_;        // one V per product: [numberSteps+1][numberRates]
    private final Matrix LIBORRatios_;
    private final Matrix Discounts_;
    private final Matrix StepsDiscountsSquared_;
    private final double[] stepsDiscounts_;
    private final Matrix LIBORRates_;
    private final Matrix partials_;
    private final Matrix vegasThisPath_;  // [numberProducts][numberBumps]
    private final List< Matrix > jacobiansThisPaths_; // [numberSteps] each [numberBumps][numberRates]
    private final double[] deflatorAndDerivatives_;
    private final double[] fullDerivatives_;
    private final int[][] numberCashFlowsThisIndex_;
    private final List< Matrix > totalCashFlowsThisIndex_;
    private final List< List< Integer > > cashFlowIndicesThisStep_;
    // workspace
    private double[] currentForwards_;
    private double[] lastForwards_;

    /**
     * @param evolver               LMM log-normal Euler evolver
     * @param product               pathwise multi-product
     * @param pseudoRootStructure   market model supplying pseudo-roots and displacements
     * @param vegaBumps             bump matrices: vegaBumps[step][bump] is a (numberRates × factors) matrix
     * @param initialNumeraireValue initial value of the numeraire
     */
    public PathwiseVegasAccountingEngine(final LogNormalFwdRateEuler evolver,
            final MarketModelPathwiseMultiProduct product, final MarketModel pseudoRootStructure,
            final List< List< Matrix > > vegaBumps, final double initialNumeraireValue) {
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
        this.fullDerivatives_ = new double[numberRates_];
        this.stepsDiscounts_ = new double[numberRates_ + 1];
        stepsDiscounts_[0] = 1.0;

        final EvolutionDescription evolution = pseudoRootStructure_.evolution();
        this.numeraires_ = EvolutionDescription.moneyMarketMeasure(evolution);

        QL.require(vegaBumps.size() == numberSteps_, "we need one vector of vega bumps for each step.");
        this.numberBumps_ = vegaBumps.get(0).size();

        // Build jacobian computers and per-step Jacobian matrices
        this.jacobianComputers_ = new ArrayList<>(numberSteps_);
        this.jacobiansThisPaths_ = new ArrayList<>(numberSteps_);

        for ( int i = 0; i < numberSteps_; ++i ) {
            final List< Matrix > stepBumps = vegaBumps.get(i);
            QL.require(stepBumps.size() == numberBumps_,
                    "We must have precisely the same number of bumps for each step.");
            jacobianComputers_.add(
                    new RatePseudoRootJacobian(pseudoRootStructure_.pseudoRoot(i), evolution.firstAliveRate()[i],
                            numeraires_[i], evolution.rateTaus(), stepBumps, pseudoRootStructure_.displacements()));
            jacobiansThisPaths_.add(new Matrix(numberBumps_, numberRates_));
        }

        // Allocate V
        this.V_ = new ArrayList<>(numberProducts_);
        final int possibleCFCount = this.product_.possibleCashFlowTimes().length;
        this.numberCashFlowTimes_ = possibleCFCount;
        this.numberCashFlowsThisIndex_ = new int[numberProducts_][possibleCFCount];
        this.totalCashFlowsThisIndex_ = new ArrayList<>(numberProducts_);

        this.vegasThisPath_ = new Matrix(numberProducts_, numberBumps_);

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

        this.Discounts_ = new Matrix(numberSteps_ + 1, numberRates_ + 1);
        for ( int i = 0; i <= numberSteps_; ++i ) {
            Discounts_.set(i, 0, 1.0);
        }

        final double[] cashFlowTimes = this.product_.possibleCashFlowTimes();
        final double[] rateTimes = this.product_.evolution().rateTimes();
        final double[] evolutionTimes = this.product_.evolution().evolutionTimes();
        this.discounters_ = new ArrayList<>(cashFlowTimes.length);
        for ( final double t : cashFlowTimes ) {
            discounters_.add(new MarketModelPathwiseDiscounter(t, rateTimes));
        }

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

    // ---- core simulation ----

    private static int upperBound(final double[] arr, final double value) {
        int lo = 0, hi = arr.length;
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( arr[mid] <= value )
                lo = mid + 1;
            else
                hi = mid;
        }
        return lo;
    }

    private double singlePathValues(final double[] values) {
        final double[] initialForwards = pseudoRootStructure_.initialRates();
        currentForwards_ = initialForwards.clone();

        // Clear accumulation variables
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
            for ( int p = 0; p < numberBumps_; ++p ) {
                vegasThisPath_.set(i, p, 0.0);
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
                stepsDiscounts_[i + 1] = x;
                StepsDiscountsSquared_.set(storeStep, i, x * x);
                LIBORRatios_.set(storeStep, i, currentForwards_[i] / lastForwards_[i]);
                LIBORRates_.set(storeStep, i, currentForwards_[i]);
                Discounts_.set(storeStep, i + 1, evolver_.currentState().discountRatio(i + 1, 0));
            }

            // Compute Jacobians for this step
            jacobianComputers_.get(thisStep)
                    .getBumps(lastForwards_, stepsDiscounts_, currentForwards_, evolver_.browniansThisStep(),
                            jacobiansThisPaths_.get(thisStep));

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

        // ---- Backwards sweep ----
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
                    noFlows = (numberCashFlowsThisIndex_[l][cashFlowIndex] == 0);
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
                                    fullDerivatives_[i - 1] = thisDerivative;
                                } else {
                                    fullDerivatives_[i - 1] = thisDerivative;
                                }
                                Vj.set(stepToUse, i - 1, Vj.get(stepToUse, i - 1) + thisDerivative);
                            }

                            // Direct dF/dtheta contribution to vegas (Jacobian * fullDerivatives)
                            final Matrix jac = jacobiansThisPaths_.get(stepToUse - 1);
                            for ( int bump = 0; bump < numberBumps_; ++bump ) {
                                double contribution = 0.0;
                                for ( int i = 0; i < numberRates_; ++i ) {
                                    contribution += fullDerivatives_[i] * jac.get(bump, i);
                                }
                                vegasThisPath_.set(j, bump, vegasThisPath_.get(j, bump) + contribution);
                            }
                        }
                    }
                }
            }

            // Backwards updating of V + indirect vega contribution
            if ( flowsFound ) {
                final int nextStepToUse = Math.min(currentStep - 1, finalStepDone);
                final int nextStepIndex = nextStepToUse + 1;
                if ( nextStepIndex != stepToUse ) {
                    final Matrix thisPseudoRoot = pseudoRootStructure_.pseudoRoot(currentStep);

                    for ( int i = 0; i < numberProducts_; ++i ) {
                        final Matrix Vi = V_.get(i);

                        // Compute partials
                        for ( int f = 0; f < factors; ++f ) {
                            partials_.set(f, numberRates_ - 1,
                                    LIBORRates_.get(stepToUse, numberRates_ - 1) * Vi.get(stepToUse, numberRates_ - 1)
                                            * thisPseudoRoot.get(numberRates_ - 1, f));

                            for ( int r = numberRates_ - 2; r >= 0; --r ) {
                                partials_.set(f, r,
                                        partials_.get(f, r + 1) + LIBORRates_.get(stepToUse, r) * Vi.get(stepToUse, r)
                                                * thisPseudoRoot.get(r, f));
                            }
                        }

                        for ( int j = 0; j < numberRates_; ++j ) {
                            double nextV = Vi.get(stepToUse, j) * LIBORRatios_.get(stepToUse, j);
                            Vi.set(nextStepIndex, j, nextV);

                            double summandTerm = 0.0;
                            for ( int f = 0; f < factors; ++f ) {
                                summandTerm += thisPseudoRoot.get(j, f) * partials_.get(f, j);
                            }
                            summandTerm *= taus[j] * StepsDiscountsSquared_.get(stepToUse, j);
                            Vi.set(nextStepIndex, j, Vi.get(nextStepIndex, j) + summandTerm);
                        }

                        // Indirect vega: V_[i][nextStepIndex] dot Jacobian[nextStepIndex-1]
                        if ( nextStepIndex > 0 ) {
                            final Matrix jac = jacobiansThisPaths_.get(nextStepIndex - 1);
                            for ( int bump = 0; bump < numberBumps_; ++bump ) {
                                double contribution = 0.0;
                                for ( int j = 0; j < numberRates_; ++j ) {
                                    contribution += Vi.get(nextStepIndex, j) * jac.get(bump, j);
                                }
                                vegasThisPath_.set(i, bump, vegasThisPath_.get(i, bump) + contribution);
                            }
                        }
                    }
                }
            }
        }

        // ---- Write answer into values ----
        final int entriesPerProduct = 1 + numberRates_ + numberBumps_;
        for ( int i = 0; i < numberProducts_; ++i ) {
            values[i * entriesPerProduct] = numerairesHeld_[i] * initialNumeraireValue_;
            final Matrix Vi = V_.get(i);
            for ( int j = 0; j < numberRates_; ++j ) {
                values[i * entriesPerProduct + 1 + j] = Vi.get(0, j) * initialNumeraireValue_;
            }
            for ( int bump = 0; bump < numberBumps_; ++bump ) {
                values[i * entriesPerProduct + numberRates_ + bump + 1] =
                        vegasThisPath_.get(i, bump) * initialNumeraireValue_;
            }
        }
        return 1.0;
    }

    // ---- helpers ----

    /**
     * Runs {@code numberOfPaths} paths and returns the sample means and standard errors of the per-product (price +
     * Deltas + Vegas) values.
     *
     * @param means         output: per-path-value means (size = numberOfProducts * (1+numberRates+numberBumps))
     * @param errors        output: per-path-value standard errors
     * @param numberOfPaths number of Monte Carlo paths
     */
    public void multiplePathValues(final double[] means, final double[] errors, final int numberOfPaths) {
        final int sz = numberProducts_ * (1 + numberRates_ + numberBumps_);
        final double[] values = new double[sz];
        final double[] sums = new double[sz];
        final double[] sumsqs = new double[sz];
        Arrays.fill(sums, 0.0);
        Arrays.fill(sumsqs, 0.0);

        for ( int i = 0; i < numberOfPaths; ++i ) {
            singlePathValues(values);
            for ( int j = 0; j < sz; ++j ) {
                sums[j] += values[j];
                sumsqs[j] += values[j] * values[j];
            }
        }

        for ( int j = 0; j < sz; ++j ) {
            means[j] = sums[j] / numberOfPaths;
            final double meanSq = sumsqs[j] / numberOfPaths;
            final double variance = meanSq - means[j] * means[j];
            errors[j] = Math.sqrt(Math.max(0.0, variance / numberOfPaths));
        }
    }
}

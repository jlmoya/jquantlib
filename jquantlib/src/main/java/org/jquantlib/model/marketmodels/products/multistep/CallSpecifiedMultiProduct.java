/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.11.

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.montecarlo.ExerciseStrategy;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Callable market-model product — wraps an underlying {@link MarketModelMultiProduct}
 * with an {@link ExerciseStrategy} and a rebate.
 * <p>
 * Mirrors C++ {@code class CallSpecifiedMultiProduct}
 * (ql/models/marketmodels/products/multistep/callspecifiedmultiproduct.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class CallSpecifiedMultiProduct extends MarketModelMultiProduct {

    private final MarketModelMultiProduct underlying_;
    private final ExerciseStrategy strategy_;
    private MarketModelMultiProduct rebate_;
    private final EvolutionDescription evolution_;
    private final boolean[][] isPresent_;
    private final double[] cashFlowTimes_;
    private final int rebateOffset_;
    private final int[] dummyCashFlowsThisStep_;
    private final MarketModelMultiProduct.CashFlow[][] dummyCashFlowsGenerated_;
    private boolean wasCalled_;
    private int currentIndex_;
    private boolean callable_ = true;

    public CallSpecifiedMultiProduct(final MarketModelMultiProduct underlying,
                                     final ExerciseStrategy strategy,
                                     final MarketModelMultiProduct rebate) {
        this.underlying_ = underlying.clone();
        this.strategy_ = strategy.clone();

        final int products = underlying_.numberOfProducts();
        final EvolutionDescription d1 = underlying_.evolution();
        final double[] rateTimes1 = d1.rateTimes();
        final double[] evolutionTimes1 = d1.evolutionTimes();
        final double[] exerciseTimes = strategy_.exerciseTimes();

        if (rebate != null) {
            final EvolutionDescription d2 = rebate.evolution();
            final double[] rateTimes2 = d2.rateTimes();
            QL.require(rateTimes1.length == rateTimes2.length && Arrays.equals(rateTimes1, rateTimes2),
                    "incompatible rate times");
            this.rebate_ = rebate.clone();
        } else {
            // default rebate: zero MarketModelCashRebate
            final EvolutionDescription description = new EvolutionDescription(rateTimes1, exerciseTimes);
            final Matrix amounts = new Matrix(products, exerciseTimes.length);
            this.rebate_ = new MarketModelCashRebate(description, exerciseTimes, amounts, products);
        }

        // merge evolution times: underlying ∪ exerciseTimes ∪ rebate.evolution ∪ strategy.relevantTimes
        final List<double[]> allEvolutionTimes = new ArrayList<>();
        allEvolutionTimes.add(evolutionTimes1.clone());
        allEvolutionTimes.add(exerciseTimes.clone());
        allEvolutionTimes.add(rebate_.evolution().evolutionTimes().clone());
        allEvolutionTimes.add(strategy_.relevantTimes().clone());

        final Utilities.MergeResult merge = Utilities.mergeTimes(allEvolutionTimes);
        final double[] mergedEvolutionTimes = merge.mergedTimes();
        this.isPresent_ = merge.isPresent();

        this.evolution_ = new EvolutionDescription(rateTimes1, mergedEvolutionTimes);

        // cash-flow times: underlying ++ rebate
        final double[] underlyingCft = underlying_.possibleCashFlowTimes();
        final double[] rebateCft = rebate_.possibleCashFlowTimes();
        this.rebateOffset_ = underlyingCft.length;
        this.cashFlowTimes_ = new double[underlyingCft.length + rebateCft.length];
        System.arraycopy(underlyingCft, 0, cashFlowTimes_, 0, underlyingCft.length);
        System.arraycopy(rebateCft, 0, cashFlowTimes_, underlyingCft.length, rebateCft.length);

        this.dummyCashFlowsThisStep_ = new int[products];
        final int n = rebate_.maxNumberOfCashFlowsPerProductPerStep();
        this.dummyCashFlowsGenerated_ = new MarketModelMultiProduct.CashFlow[products][n];
        for (int i = 0; i < products; ++i) {
            for (int j = 0; j < n; ++j) {
                dummyCashFlowsGenerated_[i][j] = new MarketModelMultiProduct.CashFlow();
            }
        }
    }

    public CallSpecifiedMultiProduct(final MarketModelMultiProduct underlying,
                                     final ExerciseStrategy strategy) {
        this(underlying, strategy, null);
    }

    @Override
    public int[] suggestedNumeraires() { return underlying_.suggestedNumeraires(); }

    @Override
    public EvolutionDescription evolution() { return evolution_; }

    @Override
    public double[] possibleCashFlowTimes() { return cashFlowTimes_; }

    @Override
    public int numberOfProducts() { return underlying_.numberOfProducts(); }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() {
        return Math.max(underlying_.maxNumberOfCashFlowsPerProductPerStep(),
                rebate_.maxNumberOfCashFlowsPerProductPerStep());
    }

    @Override
    public void reset() {
        underlying_.reset();
        rebate_.reset();
        strategy_.reset();
        currentIndex_ = 0;
        wasCalled_ = false;
    }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final MarketModelMultiProduct.CashFlow[][] cashFlowsGenerated) {
        final boolean isUnderlyingTime = isPresent_[0][currentIndex_];
        final boolean isExerciseTime = isPresent_[1][currentIndex_];
        final boolean isRebateTime = isPresent_[2][currentIndex_];
        final boolean isStrategyRelevantTime = isPresent_[3][currentIndex_];

        boolean done = false;

        if (!wasCalled_ && isStrategyRelevantTime) {
            strategy_.nextStep(currentState);
        }
        if (!wasCalled_ && isExerciseTime && callable_) {
            wasCalled_ = strategy_.exercise(currentState);
        }

        if (wasCalled_) {
            if (isRebateTime) {
                done = rebate_.nextTimeStep(currentState, numberCashFlowsThisStep, cashFlowsGenerated);
                for (int i = 0; i < numberCashFlowsThisStep.length; ++i) {
                    for (int j = 0; j < numberCashFlowsThisStep[i]; ++j) {
                        cashFlowsGenerated[i][j].timeIndex += rebateOffset_;
                    }
                }
            }
        } else {
            if (isRebateTime) {
                rebate_.nextTimeStep(currentState, dummyCashFlowsThisStep_, dummyCashFlowsGenerated_);
            }
            if (isUnderlyingTime) {
                done = underlying_.nextTimeStep(currentState, numberCashFlowsThisStep, cashFlowsGenerated);
            }
        }

        ++currentIndex_;
        return done || currentIndex_ == evolution_.evolutionTimes().length;
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new CallSpecifiedMultiProduct(underlying_, strategy_, rebate_);
    }

    public MarketModelMultiProduct underlying() { return underlying_; }

    public ExerciseStrategy strategy() { return strategy_; }

    public MarketModelMultiProduct rebate() { return rebate_; }

    public void enableCallability() { callable_ = true; }

    public void disableCallability() { callable_ = false; }
}

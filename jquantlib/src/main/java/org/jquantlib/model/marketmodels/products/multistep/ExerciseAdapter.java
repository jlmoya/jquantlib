/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.10.

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
import org.jquantlib.model.marketmodels.callability.MarketModelExerciseValue;
import org.jquantlib.model.marketmodels.products.MultiProductMultiStep;

/**
 * Adapts a {@link MarketModelExerciseValue} into a {@link MarketModelMultiProduct},
 * presenting the exercise cash flow as a rebate-style product.
 * <p>
 * Mirrors C++ {@code class ExerciseAdapter}
 * (ql/models/marketmodels/products/multistep/exerciseadapter.{hpp,cpp} v1.42.1).
 *
 * @author Jose Moya
 */
public class ExerciseAdapter extends MultiProductMultiStep {

    private final MarketModelExerciseValue exercise_;
    private final int numberOfProducts_;
    private final boolean[] isExerciseTime_;
    private int currentIndex_;

    public ExerciseAdapter(final MarketModelExerciseValue exercise, final int numberOfProducts) {
        super(exercise.evolution().rateTimes());
        this.exercise_ = exercise.clone();
        this.numberOfProducts_ = numberOfProducts;
        this.isExerciseTime_ = exercise.isExerciseTime().clone();
    }

    public ExerciseAdapter(final MarketModelExerciseValue exercise) {
        this(exercise, 1);
    }

    @Override
    public double[] possibleCashFlowTimes() { return exercise_.possibleCashFlowTimes(); }

    @Override
    public int numberOfProducts() { return numberOfProducts_; }

    @Override
    public int maxNumberOfCashFlowsPerProductPerStep() { return 1; }

    @Override
    public void reset() {
        exercise_.reset();
        currentIndex_ = 0;
    }

    /** Inspector — exposes the wrapped exercise-value provider. */
    public MarketModelExerciseValue exerciseValue() { return exercise_; }

    @Override
    public boolean nextTimeStep(final CurveState currentState,
                                final int[] numberCashFlowsThisStep,
                                final MarketModelMultiProduct.CashFlow[][] generatedCashFlows) {
        for (int i = 0; i < numberCashFlowsThisStep.length; ++i) {
            numberCashFlowsThisStep[i] = 0;
        }
        boolean done = false;

        exercise_.nextStep(currentState);
        if (isExerciseTime_[currentIndex_]) {
            final MarketModelMultiProduct.CashFlow cashflow = exercise_.value(currentState);
            numberCashFlowsThisStep[0] = 1;
            generatedCashFlows[0][0].timeIndex = cashflow.timeIndex;
            generatedCashFlows[0][0].amount = cashflow.amount;
            done = true;
        }
        ++currentIndex_;
        return done || currentIndex_ == isExerciseTime_.length;
    }

    /** ExerciseAdapter overrides evolution() to delegate to the wrapped exercise-value. */
    @Override
    public EvolutionDescription evolution() {
        return exercise_.evolution();
    }

    @Override
    public MarketModelMultiProduct clone() {
        return new ExerciseAdapter(exercise_, numberOfProducts_);
    }
}

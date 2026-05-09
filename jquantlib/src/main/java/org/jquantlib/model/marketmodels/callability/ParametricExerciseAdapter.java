/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.11.

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
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.callability;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.methods.montecarlo.ExerciseStrategy;
import org.jquantlib.model.marketmodels.CurveState;

/**
 * Adapts a {@link MarketModelParametricExercise} to the
 * {@link ExerciseStrategy} interface using pre-calibrated parameter vectors.
 *
 * <p>Java port of {@code ParametricExerciseAdapter}
 * (ql/models/marketmodels/callability/parametricexerciseadapter.{hpp,cpp}
 * v1.42.1).
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@link #reset()} resets the wrapped parametric exercise and both
 *       internal step counters.</li>
 *   <li>{@link #nextStep(CurveState)} forwards to the wrapped exercise and
 *       advances the internal step / exercise indices.</li>
 *   <li>{@link #exercise(CurveState)} extracts the current state variables
 *       via {@code values(state, ...)} and delegates to the parametric rule
 *       with the stored parameter vector for the current exercise.</li>
 * </ul>
 *
 * @see "ql/models/marketmodels/callability/parametricexerciseadapter.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class ParametricExerciseAdapter implements ExerciseStrategy {

    private final MarketModelParametricExercise exercise_;
    private final List<double[]> parameters_;
    private final double[] exerciseTimes_;
    private final boolean[] isExerciseTime_;
    private final int[] numberOfVariables_;
    private double[] variables_; // mutable workspace
    private int currentStep_ = 0;
    private int currentExercise_ = 0;

    public ParametricExerciseAdapter(final MarketModelParametricExercise exercise,
                                     final List<double[]> parameters) {
        this.exercise_ = exercise.clone();
        // Defensive copy of parameters list
        this.parameters_ = new ArrayList<>(parameters.size());
        for (final double[] p : parameters) {
            this.parameters_.add(p.clone());
        }
        this.isExerciseTime_ = exercise_.isExerciseTime().clone();
        this.numberOfVariables_ = exercise_.numberOfVariables().clone();

        final double[] evolutionTimes = exercise_.evolution().evolutionTimes();
        // Build exerciseTimes_ as the subset where isExerciseTime_ is true
        int count = 0;
        for (int i = 0; i < evolutionTimes.length; ++i) {
            if (isExerciseTime_[i]) ++count;
        }
        this.exerciseTimes_ = new double[count];
        int k = 0;
        for (int i = 0; i < evolutionTimes.length; ++i) {
            if (isExerciseTime_[i]) {
                this.exerciseTimes_[k++] = evolutionTimes[i];
            }
        }
        this.variables_ = new double[0];
    }

    private ParametricExerciseAdapter(final ParametricExerciseAdapter other) {
        this.exercise_ = other.exercise_.clone();
        this.parameters_ = new ArrayList<>(other.parameters_.size());
        for (final double[] p : other.parameters_) {
            this.parameters_.add(p.clone());
        }
        this.exerciseTimes_ = other.exerciseTimes_.clone();
        this.isExerciseTime_ = other.isExerciseTime_.clone();
        this.numberOfVariables_ = other.numberOfVariables_.clone();
        this.variables_ = other.variables_.clone();
        this.currentStep_ = other.currentStep_;
        this.currentExercise_ = other.currentExercise_;
    }

    @Override public double[] exerciseTimes() { return exerciseTimes_; }

    @Override public double[] relevantTimes() {
        return exercise_.evolution().evolutionTimes();
    }

    @Override public void reset() {
        exercise_.reset();
        currentStep_ = 0;
        currentExercise_ = 0;
    }

    @Override public void nextStep(final CurveState currentState) {
        exercise_.nextStep(currentState);
        if (isExerciseTime_[currentStep_]) {
            ++currentExercise_;
        }
        ++currentStep_;
    }

    @Override public boolean exercise(final CurveState currentState) {
        final int needed = numberOfVariables_[currentExercise_ - 1];
        if (variables_.length != needed) {
            variables_ = new double[needed];
        }
        exercise_.values(currentState, variables_);
        return exercise_.exercise(currentExercise_ - 1,
                                  parameters_.get(currentExercise_ - 1),
                                  variables_);
    }

    @Override public ParametricExerciseAdapter clone() {
        return new ParametricExerciseAdapter(this);
    }
}

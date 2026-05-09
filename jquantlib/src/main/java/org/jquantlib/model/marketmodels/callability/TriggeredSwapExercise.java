/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.10.

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

import java.util.Arrays;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;

/**
 * Parametric exercise that triggers when the coterminal swap rate exceeds a
 * single per-exercise parameter.
 *
 * <p>Java port of {@code TriggeredSwapExercise}
 * (ql/models/marketmodels/callability/triggeredswapexercise.{hpp,cpp} v1.42.1).
 *
 * <p>Implements {@link MarketModelParametricExercise}. Each exercise has 1
 * variable (the current coterminal swap rate) and 1 parameter (the trigger).
 * Exercise rule: {@code variables[0] >= parameters[0]}. The optimiser uses
 * {@link #guess} to seed parameters at user-provided strikes.
 *
 * @see "ql/models/marketmodels/callability/triggeredswapexercise.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class TriggeredSwapExercise implements MarketModelParametricExercise {

    private final double[] rateTimes_;
    private final double[] exerciseTimes_;
    private final double[] strikes_;
    private final int[] rateIndex_;
    private final EvolutionDescription evolution_;
    private int currentStep_ = 0;

    public TriggeredSwapExercise(final double[] rateTimes,
                                 final double[] exerciseTimes,
                                 final double[] strikes) {
        QL.require(strikes.length == exerciseTimes.length,
                "strikes/exerciseTimes mismatch: " + strikes.length
                        + " != " + exerciseTimes.length);
        this.rateTimes_ = rateTimes.clone();
        this.exerciseTimes_ = exerciseTimes.clone();
        this.strikes_ = strikes.clone();
        this.rateIndex_ = new int[exerciseTimes.length];
        this.evolution_ = new EvolutionDescription(rateTimes, exerciseTimes);
        int j = 0;
        for (int i = 0; i < exerciseTimes.length; ++i) {
            while (j < rateTimes.length && rateTimes[j] < exerciseTimes[i]) {
                ++j;
            }
            this.rateIndex_[i] = j;
        }
    }

    private TriggeredSwapExercise(final TriggeredSwapExercise other) {
        this.rateTimes_ = other.rateTimes_.clone();
        this.exerciseTimes_ = other.exerciseTimes_.clone();
        this.strikes_ = other.strikes_.clone();
        this.rateIndex_ = other.rateIndex_.clone();
        this.evolution_ = other.evolution_;
        this.currentStep_ = other.currentStep_;
    }

    // -- MarketModelNodeDataProvider --

    @Override public int numberOfExercises() { return exerciseTimes_.length; }

    @Override public EvolutionDescription evolution() { return evolution_; }

    @Override public void nextStep(final CurveState s) { ++currentStep_; }

    @Override public void reset() { currentStep_ = 0; }

    @Override public boolean[] isExerciseTime() {
        final boolean[] r = new boolean[numberOfExercises()];
        Arrays.fill(r, true);
        return r;
    }

    @Override public void values(final CurveState state, final double[] results) {
        final int swapIndex = rateIndex_[currentStep_ - 1];
        QL.require(results.length == 1,
                "results length (" + results.length + ") must equal 1");
        results[0] = state.coterminalSwapRate(swapIndex);
    }

    // -- ParametricExercise --

    @Override public int[] numberOfVariables() {
        final int[] r = new int[numberOfExercises()];
        Arrays.fill(r, 1);
        return r;
    }

    @Override public int[] numberOfParameters() {
        final int[] r = new int[numberOfExercises()];
        Arrays.fill(r, 1);
        return r;
    }

    @Override public boolean exercise(final int exerciseNumber,
                                      final double[] parameters,
                                      final double[] variables) {
        return variables[0] >= parameters[0];
    }

    @Override public void guess(final int exerciseIndex, final double[] parameters) {
        QL.require(parameters.length == 1,
                "parameters length (" + parameters.length + ") must equal 1");
        parameters[0] = strikes_[exerciseIndex];
    }

    @Override public TriggeredSwapExercise clone() {
        return new TriggeredSwapExercise(this);
    }
}

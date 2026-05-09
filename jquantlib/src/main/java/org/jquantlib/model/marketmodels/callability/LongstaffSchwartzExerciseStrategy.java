/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.12.

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

package org.jquantlib.model.marketmodels.callability;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.methods.montecarlo.ExerciseStrategy;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelDiscounter;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Longstaff-Schwartz exercise strategy for callable market-model products.
 *
 * <p>Java port of {@code LongstaffSchwartzExerciseStrategy}
 * (ql/models/marketmodels/callability/lsstrategy.{hpp,cpp} v1.42.1).
 *
 * <p>Online phase only: regression coefficients are computed offline (e.g. via
 * {@link CollectNodeData} + a regression) and supplied at construction. During
 * simulation, this strategy:
 * <ol>
 *   <li>computes the deflated exercise value via {@code rebate.value()}
 *       discounted by a {@link MarketModelDiscounter} into the current
 *       numeraire,</li>
 *   <li>evaluates the regression basis at the current curve state,</li>
 *   <li>computes continuation value = controlValue + dot(alphas, basis),</li>
 *   <li>returns true if exerciseValue >= continuationValue.</li>
 * </ol>
 *
 * <p>The {@link #nextStep} method maintains the
 * {@code principalInNumerairePortfolio} via the
 * {@link CurveState#discountRatio(int, int)} between consecutive numeraires.
 *
 * @see "ql/models/marketmodels/callability/lsstrategy.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class LongstaffSchwartzExerciseStrategy implements ExerciseStrategy {

    private final MarketModelBasisSystem basisSystem_;
    private final List<double[]> basisCoefficients_;
    private final MarketModelExerciseValue exercise_;
    private final MarketModelExerciseValue control_;
    private final int[] numeraires_;
    // work variable
    private int currentIndex_;
    private double principalInNumerairePortfolio_;
    private double newPrincipal_;
    private final double[] exerciseTimes_;
    private final double[] relevantTimes_;
    private final boolean[] isBasisTime_;
    private final boolean[] isRebateTime_;
    private final boolean[] isControlTime_;
    private final boolean[] isExerciseTime_;
    private final List<MarketModelDiscounter> rebateDiscounters_;
    private final List<MarketModelDiscounter> controlDiscounters_;
    private final List<double[]> basisValues_; // per-exercise scratch
    private final int[] exerciseIndex_;

    public LongstaffSchwartzExerciseStrategy(
            final MarketModelBasisSystem basisSystem,
            final List<double[]> basisCoefficients,
            final EvolutionDescription evolution,
            final int[] numeraires,
            final MarketModelExerciseValue exercise,
            final MarketModelExerciseValue control) {

        this.basisSystem_ = basisSystem.clone();
        // Defensive copy of coefficients
        this.basisCoefficients_ = new ArrayList<>(basisCoefficients.size());
        for (final double[] c : basisCoefficients) {
            this.basisCoefficients_.add(c.clone());
        }
        this.exercise_ = exercise.clone();
        this.control_ = control.clone();
        this.numeraires_ = numeraires.clone();

        EvolutionDescription.checkCompatibility(evolution, numeraires);
        this.relevantTimes_ = evolution.evolutionTimes().clone();

        this.isBasisTime_ = Utilities.isInSubset(relevantTimes_,
                basisSystem_.evolution().evolutionTimes());
        this.isRebateTime_ = Utilities.isInSubset(relevantTimes_,
                exercise_.evolution().evolutionTimes());
        this.isControlTime_ = Utilities.isInSubset(relevantTimes_,
                control_.evolution().evolutionTimes());

        this.exerciseIndex_ = new int[relevantTimes_.length];
        this.isExerciseTime_ = new boolean[relevantTimes_.length];
        final boolean[] v = exercise_.isExerciseTime();
        int exercises = 0;
        int idx = 0;
        // First pass: build exerciseIndex_ + isExerciseTime_, count exerciseTimes
        final List<Double> exTimes = new ArrayList<>();
        for (int i = 0; i < relevantTimes_.length; ++i) {
            exerciseIndex_[i] = exercises;
            if (isRebateTime_[i]) {
                isExerciseTime_[i] = v[idx++];
                if (isExerciseTime_[i]) {
                    exTimes.add(relevantTimes_[i]);
                    ++exercises;
                }
            }
        }
        this.exerciseTimes_ = new double[exTimes.size()];
        for (int i = 0; i < exTimes.size(); i++) {
            this.exerciseTimes_[i] = exTimes.get(i);
        }

        final double[] rateTimes = evolution.rateTimes();
        final double[] rebateTimes = exercise_.possibleCashFlowTimes();
        this.rebateDiscounters_ = new ArrayList<>(rebateTimes.length);
        for (final double t : rebateTimes) {
            this.rebateDiscounters_.add(new MarketModelDiscounter(t, rateTimes));
        }

        final double[] controlTimes = control_.possibleCashFlowTimes();
        this.controlDiscounters_ = new ArrayList<>(controlTimes.length);
        for (final double t : controlTimes) {
            this.controlDiscounters_.add(new MarketModelDiscounter(t, rateTimes));
        }

        final int[] basisSizes = basisSystem_.numberOfFunctions();
        this.basisValues_ = new ArrayList<>(basisSystem_.numberOfExercises());
        for (int i = 0; i < basisSystem_.numberOfExercises(); i++) {
            this.basisValues_.add(new double[basisSizes[i]]);
        }
    }

    /** Copy constructor for {@link #clone()}. */
    private LongstaffSchwartzExerciseStrategy(final LongstaffSchwartzExerciseStrategy other) {
        this.basisSystem_ = other.basisSystem_.clone();
        this.basisCoefficients_ = new ArrayList<>(other.basisCoefficients_.size());
        for (final double[] c : other.basisCoefficients_) {
            this.basisCoefficients_.add(c.clone());
        }
        this.exercise_ = other.exercise_.clone();
        this.control_ = other.control_.clone();
        this.numeraires_ = other.numeraires_.clone();
        this.currentIndex_ = other.currentIndex_;
        this.principalInNumerairePortfolio_ = other.principalInNumerairePortfolio_;
        this.newPrincipal_ = other.newPrincipal_;
        this.exerciseTimes_ = other.exerciseTimes_.clone();
        this.relevantTimes_ = other.relevantTimes_.clone();
        this.isBasisTime_ = other.isBasisTime_.clone();
        this.isRebateTime_ = other.isRebateTime_.clone();
        this.isControlTime_ = other.isControlTime_.clone();
        this.isExerciseTime_ = other.isExerciseTime_.clone();
        // Discounters are immutable -> share references
        this.rebateDiscounters_ = new ArrayList<>(other.rebateDiscounters_);
        this.controlDiscounters_ = new ArrayList<>(other.controlDiscounters_);
        this.basisValues_ = new ArrayList<>(other.basisValues_.size());
        for (final double[] b : other.basisValues_) {
            this.basisValues_.add(b.clone());
        }
        this.exerciseIndex_ = other.exerciseIndex_.clone();
    }

    @Override public double[] exerciseTimes() { return exerciseTimes_; }

    @Override public double[] relevantTimes() { return relevantTimes_; }

    @Override public void reset() {
        exercise_.reset();
        control_.reset();
        basisSystem_.reset();
        currentIndex_ = 0;
        principalInNumerairePortfolio_ = newPrincipal_ = 1.0;
    }

    @Override public boolean exercise(final CurveState currentState) {
        final int exerciseIndex = exerciseIndex_[currentIndex_ - 1];

        final MarketModelMultiProduct.CashFlow exerciseCF = exercise_.value(currentState);
        final double exerciseValue = exerciseCF.amount
                * rebateDiscounters_.get(exerciseCF.timeIndex)
                        .numeraireBonds(currentState, numeraires_[currentIndex_ - 1])
                / principalInNumerairePortfolio_;

        final MarketModelMultiProduct.CashFlow controlCF = control_.value(currentState);
        final double controlValue = controlCF.amount
                * controlDiscounters_.get(controlCF.timeIndex)
                        .numeraireBonds(currentState, numeraires_[currentIndex_ - 1])
                / principalInNumerairePortfolio_;

        final double[] basis = basisValues_.get(exerciseIndex);
        basisSystem_.values(currentState, basis);

        final double[] alphas = basisCoefficients_.get(exerciseIndex);
        // continuationValue = controlValue + sum(alphas[k] * basis[k])
        // Mirrors C++ std::inner_product(alphas.begin(), alphas.end(),
        //                               basis.begin(), controlValue).
        double continuationValue = controlValue;
        for (int k = 0; k < alphas.length; k++) {
            continuationValue += alphas[k] * basis[k];
        }

        return exerciseValue >= continuationValue;
    }

    @Override public void nextStep(final CurveState currentState) {
        principalInNumerairePortfolio_ = newPrincipal_;

        if (isRebateTime_[currentIndex_]) {
            exercise_.nextStep(currentState);
        }
        if (isControlTime_[currentIndex_]) {
            control_.nextStep(currentState);
        }
        if (isBasisTime_[currentIndex_]) {
            basisSystem_.nextStep(currentState);
        }

        if (currentIndex_ < numeraires_.length - 1) {
            final int numeraire = numeraires_[currentIndex_];
            final int nextNumeraire = numeraires_[currentIndex_ + 1];
            newPrincipal_ *= currentState.discountRatio(numeraire, nextNumeraire);
        }

        ++currentIndex_;
    }

    @Override public LongstaffSchwartzExerciseStrategy clone() {
        return new LongstaffSchwartzExerciseStrategy(this);
    }
}

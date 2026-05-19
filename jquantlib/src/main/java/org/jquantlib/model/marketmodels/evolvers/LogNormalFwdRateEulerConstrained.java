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
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels.evolvers;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.*;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.driftcomputation.LMMDriftCalculator;

/**
 * Constrained Euler log-normal forward-rate evolver.
 * <p>
 * Adds a Fries-Joshi importance-sampling shift to {@link LogNormalFwdRateEuler} so a chosen forward rate at a chosen
 * step can be pinned to a constraint value. The shift is applied along the constrained rate's row of the pseudoroot,
 * with the path weight adjusted by the ratio of densities (shifted vs. original Gaussian).
 * <p>
 * Currently implemented only for forward-rate constraints (i.e. endIndex == startIndex+1 in
 * {@link #setConstraintType}).
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/evolvers/lognormalfwdrateeulerconstrained.{hpp,cpp}" v1.42.1
 */
public class LogNormalFwdRateEulerConstrained extends ConstrainedEvolver {

    private static final CumulativeNormalDistribution PHI = new CumulativeNormalDistribution();
    // inputs
    private final MarketModel marketModel_;
    private final int[] numeraires_;
    private final int initialStep_;
    private final BrownianGenerator generator_;
    // fixed variables
    private final double[][] fixedDrifts_;
    private final double[][] variances_;
    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final LMMCurveState curveState_;
    private final double[] forwards_;
    private final double[] displacements_;
    private final double[] logForwards_;
    private final double[] initialLogForwards_;
    private final double[] drifts1_;
    private final double[] initialDrifts_;
    private final double[] brownians_;
    private final int[] alive_;
    // helper classes
    private final LMMDriftCalculator[] calculators_;
    private int[] startIndexOfSwapRate_;
    private int[] endIndexOfSwapRate_;
    // often-changing inputs
    private double[] rateConstraints_;
    private boolean[] isConstraintActive_;
    // working variables
    private double[][] covariances_; // covariance of constrained rate vs other rates per step
    private int currentStep_;

    public LogNormalFwdRateEulerConstrained(final MarketModel marketModel, final BrownianGeneratorFactory factory,
            final int[] numeraires) {
        this(marketModel, factory, numeraires, 0);
    }

    public LogNormalFwdRateEulerConstrained(final MarketModel marketModel, final BrownianGeneratorFactory factory,
            final int[] numeraires, final int initialStep) {
        this.marketModel_ = marketModel;
        this.numeraires_ = numeraires.clone();
        this.initialStep_ = initialStep;
        this.numberOfRates_ = marketModel.numberOfRates();
        this.numberOfFactors_ = marketModel.numberOfFactors();
        this.curveState_ = new LMMCurveState(marketModel.evolution().rateTimes());
        this.forwards_ = marketModel.initialRates().clone();
        this.displacements_ = marketModel.displacements().clone();
        this.logForwards_ = new double[numberOfRates_];
        this.initialLogForwards_ = new double[numberOfRates_];
        this.drifts1_ = new double[numberOfRates_];
        this.initialDrifts_ = new double[numberOfRates_];
        this.brownians_ = new double[numberOfFactors_];
        this.alive_ = marketModel.evolution().firstAliveRate().clone();

        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires);

        final int steps = marketModel.evolution().numberOfSteps();

        this.generator_ = factory.create(numberOfFactors_, steps - initialStep_);
        this.currentStep_ = initialStep_;

        this.calculators_ = new LMMDriftCalculator[steps];
        this.variances_ = new double[steps][numberOfRates_];
        this.fixedDrifts_ = new double[steps][numberOfRates_];
        for ( int j = 0; j < steps; ++j ) {
            final Matrix A = marketModel.pseudoRoot(j);
            calculators_[j] = new LMMDriftCalculator(A, displacements_, marketModel.evolution().rateTaus(),
                    numeraires[j], alive_[j]);
            for ( int k = 0; k < numberOfRates_; ++k ) {
                double variance = 0.0;
                for ( int f = 0; f < numberOfFactors_; ++f ) {
                    final double a = A.get(k, f);
                    variance += a * a;
                }
                variances_[j][k] = variance;
                fixedDrifts_[j][k] = -0.5 * variance;
            }
        }

        setForwards(marketModel_.initialRates());
    }

    @Override
    public int[] numeraires() {
        return numeraires_;
    }

    private void setForwards(final double[] forwards) {
        QL.require(forwards.length == numberOfRates_, "mismatch between forwards and rateTimes");
        for ( int i = 0; i < numberOfRates_; ++i ) {
            initialLogForwards_[i] = Math.log(forwards[i] + displacements_[i]);
        }
        calculators_[initialStep_].compute(forwards, initialDrifts_);
    }

    @Override
    public void setInitialState(final CurveState cs) {
        setForwards(cs.forwardRates());
    }

    @Override
    public void setConstraintType(final int[] startIndexOfSwapRate, final int[] endIndexOfSwapRate) {
        QL.require(startIndexOfSwapRate.length == numeraires_.length, "Size mismatch in constraint specification.");
        QL.require(endIndexOfSwapRate.length == numeraires_.length, "Size mismatch in constraint specification.");

        this.startIndexOfSwapRate_ = startIndexOfSwapRate.clone();
        this.endIndexOfSwapRate_ = endIndexOfSwapRate.clone();

        this.covariances_ = new double[startIndexOfSwapRate_.length][numberOfRates_];

        for ( int i = 0; i < startIndexOfSwapRate_.length; ++i ) {
            QL.require(startIndexOfSwapRate_[i] + 1 == endIndexOfSwapRate_[i],
                    "constrained euler currently only implemented for forward rates");

            final Matrix A = marketModel_.pseudoRoot(currentStep_);

            for ( int j = 0; j < numberOfRates_; ++j ) {
                double cov = 0.0;
                for ( int k = 0; k < numberOfFactors_; ++k ) {
                    cov += A.get(startIndexOfSwapRate_[i], k) * A.get(j, k);
                }
                covariances_[i][j] = cov;
            }
        }
    }

    @Override
    public void setThisConstraint(final double[] rateConstraints, final boolean[] isConstraintActive) {
        QL.require(rateConstraints.length == numeraires_.length, "wrong number of constraints specified");
        QL.require(isConstraintActive.length == numeraires_.length, "wrong number of isConstraintActive specified");

        this.rateConstraints_ = rateConstraints.clone();
        this.isConstraintActive_ = isConstraintActive.clone();

        for ( int i = 0; i < rateConstraints_.length; i++ ) {
            rateConstraints_[i] = Math.log(rateConstraints_[i] + displacements_[i]);
        }
    }

    @Override
    public double startNewPath() {
        currentStep_ = initialStep_;
        System.arraycopy(initialLogForwards_, 0, logForwards_, 0, numberOfRates_);
        return generator_.nextPath();
    }

    @Override
    public double advanceStep() {
        // we're going from T1 to T2

        // a) compute drifts D1 at T1
        if ( currentStep_ > initialStep_ ) {
            calculators_[currentStep_].compute(forwards_, drifts1_);
        } else {
            System.arraycopy(initialDrifts_, 0, drifts1_, 0, numberOfRates_);
        }

        // b) evolve forwards up to T2 using D1
        double weight = generator_.nextStep(brownians_);
        final Matrix A = marketModel_.pseudoRoot(currentStep_);
        final double[] fixedDrift = fixedDrifts_[currentStep_];

        final int alive = alive_[currentStep_];
        for ( int i = alive; i < numberOfRates_; i++ ) {
            logForwards_[i] += drifts1_[i] + fixedDrift[i];
            double inner = 0.0;
            for ( int f = 0; f < numberOfFactors_; ++f ) {
                inner += A.get(i, f) * brownians_[f];
            }
            logForwards_[i] += inner;
        }

        // check constraint active
        if ( isConstraintActive_ != null && isConstraintActive_[currentStep_] ) {
            final int index = startIndexOfSwapRate_[currentStep_];

            // compute error
            final double requiredShift = rateConstraints_[currentStep_] - logForwards_[index];
            final double multiplier = requiredShift / variances_[currentStep_][index];

            // shift each rate by multiplier * weighting of index rate across the step
            for ( int i = alive; i < numberOfRates_; i++ ) {
                logForwards_[i] += multiplier * covariances_[currentStep_][i];
            }

            // density correction: divide original density by density of shifted normal
            double weightsEffect = 1.0;
            for ( int k = 0; k < numberOfFactors_; k++ ) {
                final double shift = multiplier * A.get(index, k);
                final double originalDensity = PHI.derivative(brownians_[k] + shift);
                final double newDensity = PHI.derivative(brownians_[k]);
                weightsEffect *= originalDensity / newDensity;
            }

            weight *= weightsEffect;
        }

        for ( int i = alive; i < numberOfRates_; i++ ) {
            forwards_[i] = Math.exp(logForwards_[i]) - displacements_[i];
        }

        // c) update curve state
        curveState_.setOnForwardRates(forwards_);

        ++currentStep_;

        return weight;
    }

    @Override
    public int currentStep() {
        return currentStep_;
    }

    @Override
    public CurveState currentState() {
        return curveState_;
    }
}

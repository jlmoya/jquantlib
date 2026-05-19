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
 Copyright (C) 2009 Sun Xiuxin
*/

package org.jquantlib.model.marketmodels.evolvers;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.*;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.driftcomputation.LMMDriftCalculator;

/**
 * Iterative Balland log-normal forward-rate evolver.
 * <p>
 * Combines the iterative-drift backward walk of {@link LogNormalFwdRateIpc} with Balland's geometric-mean correction
 * (forwards replaced by sqrt(initialRates*forwards) before computing the next g_).
 * <p>
 * Runs in terminal measure only (verified at construction).
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/evolvers/lognormalfwdrateiballand.{hpp,cpp}" v1.42.1
 */
public class LogNormalFwdRateiBalland extends MarketModelEvolver {

    // inputs
    private final MarketModel marketModel_;
    private final int[] numeraires_;
    private final int initialStep_;
    private final BrownianGenerator generator_;
    // fixed variables
    private final double[][] fixedDrifts_;
    // working variables
    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final LMMCurveState curveState_;
    private final double[] forwards_;
    private final double[] displacements_;
    private final double[] logForwards_;
    private final double[] initialLogForwards_;
    private final double[] initialDrifts_;
    private final double[] brownians_;
    private final double[] rateTaus_;
    private final int[] alive_;
    // helper classes
    private final LMMDriftCalculator[] calculators_;
    private int currentStep_;

    public LogNormalFwdRateiBalland(final MarketModel marketModel, final BrownianGeneratorFactory factory,
            final int[] numeraires) {
        this(marketModel, factory, numeraires, 0);
    }

    public LogNormalFwdRateiBalland(final MarketModel marketModel, final BrownianGeneratorFactory factory,
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
        this.initialDrifts_ = new double[numberOfRates_];
        this.brownians_ = new double[numberOfFactors_];
        this.rateTaus_ = marketModel.evolution().rateTaus().clone();
        this.alive_ = marketModel.evolution().firstAliveRate().clone();

        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires);
        QL.require(EvolutionDescription.isInTerminalMeasure(marketModel.evolution(), numeraires),
                "terminal measure required for iBalland");

        final int steps = marketModel.evolution().numberOfSteps();

        this.generator_ = factory.create(numberOfFactors_, steps - initialStep_);

        this.currentStep_ = initialStep_;

        this.calculators_ = new LMMDriftCalculator[steps];
        this.fixedDrifts_ = new double[steps][numberOfRates_];
        for ( int j = 0; j < steps; ++j ) {
            final Matrix A = marketModel.pseudoRoot(j);
            calculators_[j] = new LMMDriftCalculator(A, displacements_, marketModel.evolution().rateTaus(),
                    numeraires[j], alive_[j]);
            final Matrix C = marketModel.covariance(j);
            for ( int k = 0; k < numberOfRates_; ++k ) {
                fixedDrifts_[j][k] = -0.5 * C.get(k, k);
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
    public double startNewPath() {
        currentStep_ = initialStep_;
        System.arraycopy(initialLogForwards_, 0, logForwards_, 0, numberOfRates_);
        return generator_.nextPath();
    }

    @Override
    public double advanceStep() {
        final double weight = generator_.nextStep(brownians_);
        final Matrix A = marketModel_.pseudoRoot(currentStep_);
        final Matrix C = marketModel_.covariance(currentStep_);
        final double[] fixedDrift = fixedDrifts_[currentStep_];
        final double[] initialRates = marketModel_.initialRates();

        final int alive = alive_[currentStep_];
        final double[] g = new double[numberOfRates_];

        // step the longest rate first (no drift contribution from later rates)
        int i = numberOfRates_ - 1;
        if ( i >= alive ) {
            logForwards_[i] += fixedDrift[i];
            double inner = 0.0;
            for ( int f = 0; f < numberOfFactors_; ++f ) {
                inner += A.get(i, f) * brownians_[f];
            }
            logForwards_[i] += inner;
            forwards_[i] = Math.exp(logForwards_[i]) - displacements_[i];
            final double blFwd = Math.sqrt(initialRates[i] * forwards_[i]);
            g[i] = rateTaus_[i] * (blFwd + displacements_[i]) / (1.0 + rateTaus_[i] * blFwd);
        }

        // now walk backwards
        for ( i = numberOfRates_ - 2; i >= alive; --i ) {
            double drifts2 = 0.0;
            for ( int j = i + 1; j < numberOfRates_; ++j ) {
                drifts2 -= g[j] * C.get(i, j);
            }
            logForwards_[i] += drifts2 + fixedDrift[i];
            double inner = 0.0;
            for ( int f = 0; f < numberOfFactors_; ++f ) {
                inner += A.get(i, f) * brownians_[f];
            }
            logForwards_[i] += inner;
            forwards_[i] = Math.exp(logForwards_[i]) - displacements_[i];

            final double blFwd = Math.sqrt(initialRates[i] * forwards_[i]);
            g[i] = rateTaus_[i] * (blFwd + displacements_[i]) / (1.0 + rateTaus_[i] * blFwd);
        }

        // update curve state
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

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
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2007 Chiara Fornarola
*/

package org.jquantlib.model.marketmodels.evolvers;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.*;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.driftcomputation.LMMNormalDriftCalculator;

/**
 * Predictor-corrector normal forward-rate evolver.
 * <p>
 * Two-pass scheme: D1 at T1 advances forwards (predictor), D2 at predicted T2 corrects via average ((D1+D2)/2). Unlike
 * the log-normal variants, forwards are added directly (not exponentiated) — so this is the normal/Bachelier dynamics
 * for forward rates.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/evolvers/normalfwdratepc.{hpp,cpp}" v1.42.1
 */
public class NormalFwdRatePc extends MarketModelEvolver {

    // inputs
    private final MarketModel marketModel_;
    private final int[] numeraires_;
    private final int initialStep_;
    private final BrownianGenerator generator_;
    // working variables
    private final int numberOfRates_;
    private final int numberOfFactors_;
    private final LMMCurveState curveState_;
    private final double[] forwards_;
    private final double[] initialForwards_;
    private final double[] drifts1_;
    private final double[] drifts2_;
    private final double[] initialDrifts_;
    private final double[] brownians_;
    private final int[] alive_;
    // helper classes
    private final LMMNormalDriftCalculator[] calculators_;
    private int currentStep_;

    public NormalFwdRatePc(final MarketModel marketModel, final BrownianGeneratorFactory factory,
            final int[] numeraires) {
        this(marketModel, factory, numeraires, 0);
    }

    public NormalFwdRatePc(final MarketModel marketModel, final BrownianGeneratorFactory factory,
            final int[] numeraires, final int initialStep) {
        this.marketModel_ = marketModel;
        this.numeraires_ = numeraires.clone();
        this.initialStep_ = initialStep;
        this.numberOfRates_ = marketModel.numberOfRates();
        this.numberOfFactors_ = marketModel.numberOfFactors();
        this.curveState_ = new LMMCurveState(marketModel.evolution().rateTimes());
        this.forwards_ = marketModel.initialRates().clone();
        this.initialForwards_ = marketModel.initialRates().clone();
        this.drifts1_ = new double[numberOfRates_];
        this.drifts2_ = new double[numberOfRates_];
        this.initialDrifts_ = new double[numberOfRates_];
        this.brownians_ = new double[numberOfFactors_];
        this.alive_ = marketModel.evolution().firstAliveRate().clone();

        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires);

        final int steps = marketModel.evolution().numberOfSteps();

        this.generator_ = factory.create(numberOfFactors_, steps - initialStep_);

        this.currentStep_ = initialStep_;

        this.calculators_ = new LMMNormalDriftCalculator[steps];
        for ( int j = 0; j < steps; ++j ) {
            final Matrix A = marketModel.pseudoRoot(j);
            calculators_[j] = new LMMNormalDriftCalculator(A, marketModel.evolution().rateTaus(), numeraires[j],
                    alive_[j]);
        }

        setForwards(marketModel_.initialRates());
    }

    @Override
    public int[] numeraires() {
        return numeraires_;
    }

    private void setForwards(final double[] forwards) {
        QL.require(forwards.length == numberOfRates_, "mismatch between forwards and rateTimes");
        // C++ has a bare for-loop that does nothing observable, then computes initialDrifts_
        calculators_[initialStep_].compute(forwards, initialDrifts_);
    }

    @Override
    public void setInitialState(final CurveState cs) {
        setForwards(cs.forwardRates());
    }

    @Override
    public double startNewPath() {
        currentStep_ = initialStep_;
        System.arraycopy(initialForwards_, 0, forwards_, 0, numberOfRates_);
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
        final double weight = generator_.nextStep(brownians_);
        final Matrix A = marketModel_.pseudoRoot(currentStep_);

        final int alive = alive_[currentStep_];
        for ( int i = alive; i < numberOfRates_; ++i ) {
            forwards_[i] += drifts1_[i];
            double inner = 0.0;
            for ( int f = 0; f < numberOfFactors_; ++f ) {
                inner += A.get(i, f) * brownians_[f];
            }
            forwards_[i] += inner;
        }

        // c) recompute drifts D2 using the predicted forwards
        calculators_[currentStep_].compute(forwards_, drifts2_);

        // d) correct forwards using both drifts
        for ( int i = alive; i < numberOfRates_; ++i ) {
            forwards_[i] += (drifts2_[i] - drifts1_[i]) / 2.0;
        }

        // e) update curve state
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

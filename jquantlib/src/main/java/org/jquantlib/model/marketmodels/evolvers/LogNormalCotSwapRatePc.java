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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006, 2007 Mark Joshi
*/

package org.jquantlib.model.marketmodels.evolvers;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.*;
import org.jquantlib.model.marketmodels.curvestates.CoterminalSwapCurveState;
import org.jquantlib.model.marketmodels.driftcomputation.SMMDriftCalculator;

/**
 * Predictor-corrector log-normal coterminal swap-rate evolver.
 * <p>
 * Operates on coterminal swap rates (i.e. swaps all ending at the last forward rate's reset time). Two-pass scheme: D1
 * at T1 advances rates, D2 at predicted T2 corrects via average ((D1+D2)/2).
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/evolvers/lognormalcotswapratepc.{hpp,cpp}" v1.42.1
 */
public class LogNormalCotSwapRatePc extends MarketModelEvolver {

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
    private final CoterminalSwapCurveState curveState_;
    private final double[] swapRates_;
    private final double[] displacements_;
    private final double[] logSwapRates_;
    private final double[] initialLogSwapRates_;
    private final double[] drifts1_;
    private final double[] drifts2_;
    private final double[] initialDrifts_;
    private final double[] brownians_;
    private final int[] alive_;
    // helper classes
    private final SMMDriftCalculator[] calculators_;
    private int currentStep_;

    public LogNormalCotSwapRatePc(final MarketModel marketModel, final BrownianGeneratorFactory factory,
            final int[] numeraires) {
        this(marketModel, factory, numeraires, 0);
    }

    public LogNormalCotSwapRatePc(final MarketModel marketModel, final BrownianGeneratorFactory factory,
            final int[] numeraires, final int initialStep) {
        this.marketModel_ = marketModel;
        this.numeraires_ = numeraires.clone();
        this.initialStep_ = initialStep;
        this.numberOfRates_ = marketModel.numberOfRates();
        this.numberOfFactors_ = marketModel.numberOfFactors();
        this.curveState_ = new CoterminalSwapCurveState(marketModel.evolution().rateTimes());
        this.swapRates_ = marketModel.initialRates().clone();
        this.displacements_ = marketModel.displacements().clone();
        this.logSwapRates_ = new double[numberOfRates_];
        this.initialLogSwapRates_ = new double[numberOfRates_];
        this.drifts1_ = new double[numberOfRates_];
        this.drifts2_ = new double[numberOfRates_];
        this.initialDrifts_ = new double[numberOfRates_];
        this.brownians_ = new double[numberOfFactors_];
        this.alive_ = marketModel.evolution().firstAliveRate().clone();

        EvolutionDescription.checkCompatibility(marketModel.evolution(), numeraires);

        final int steps = marketModel.evolution().numberOfSteps();

        this.generator_ = factory.create(numberOfFactors_, steps - initialStep_);

        this.currentStep_ = initialStep_;

        this.calculators_ = new SMMDriftCalculator[steps];
        this.fixedDrifts_ = new double[steps][numberOfRates_];
        for ( int j = 0; j < steps; ++j ) {
            final Matrix A = marketModel.pseudoRoot(j);
            calculators_[j] = new SMMDriftCalculator(A, displacements_, marketModel.evolution().rateTaus(),
                    numeraires[j], alive_[j]);
            for ( int k = 0; k < numberOfRates_; ++k ) {
                double variance = 0.0;
                for ( int f = 0; f < numberOfFactors_; ++f ) {
                    final double a = A.get(k, f);
                    variance += a * a;
                }
                fixedDrifts_[j][k] = -0.5 * variance;
            }
        }

        setCoterminalSwapRates(marketModel_.initialRates());
    }

    @Override
    public int[] numeraires() {
        return numeraires_;
    }

    private void setCoterminalSwapRates(final double[] swapRates) {
        QL.require(swapRates.length == numberOfRates_, "mismatch between swapRates and rateTimes");
        for ( int i = 0; i < numberOfRates_; ++i ) {
            initialLogSwapRates_[i] = Math.log(swapRates[i] + displacements_[i]);
        }
        curveState_.setOnCoterminalSwapRates(swapRates);
        calculators_[initialStep_].compute(curveState_, initialDrifts_);
    }

    @Override
    public void setInitialState(final CurveState cs) {
        if ( !(cs instanceof CoterminalSwapCurveState) ) {
            throw new ClassCastException("expected CoterminalSwapCurveState");
        }
        final CoterminalSwapCurveState cotcs = (CoterminalSwapCurveState) cs;
        setCoterminalSwapRates(cotcs.coterminalSwapRates());
    }

    @Override
    public double startNewPath() {
        currentStep_ = initialStep_;
        System.arraycopy(initialLogSwapRates_, 0, logSwapRates_, 0, numberOfRates_);
        return generator_.nextPath();
    }

    @Override
    public double advanceStep() {
        // we're going from T1 to T2

        // a) compute drifts D1 at T1
        if ( currentStep_ > initialStep_ ) {
            calculators_[currentStep_].compute(curveState_, drifts1_);
        } else {
            System.arraycopy(initialDrifts_, 0, drifts1_, 0, numberOfRates_);
        }

        // b) evolve rates up to T2 using D1
        final double weight = generator_.nextStep(brownians_);
        final Matrix A = marketModel_.pseudoRoot(currentStep_);
        final double[] fixedDrift = fixedDrifts_[currentStep_];

        final int alive = alive_[currentStep_];
        for ( int i = alive; i < numberOfRates_; ++i ) {
            logSwapRates_[i] += drifts1_[i] + fixedDrift[i];
            double inner = 0.0;
            for ( int f = 0; f < numberOfFactors_; ++f ) {
                inner += A.get(i, f) * brownians_[f];
            }
            logSwapRates_[i] += inner;
            swapRates_[i] = Math.exp(logSwapRates_[i]) - displacements_[i];
        }

        // intermediate curve state update
        curveState_.setOnCoterminalSwapRates(swapRates_);

        // c) recompute drifts D2 using the predicted swapRates
        calculators_[currentStep_].compute(curveState_, drifts2_);

        // d) correct rates using both drifts
        for ( int i = alive; i < numberOfRates_; ++i ) {
            logSwapRates_[i] += (drifts2_[i] - drifts1_[i]) / 2.0;
            swapRates_[i] = Math.exp(logSwapRates_[i]) - displacements_[i];
        }

        // e) update curve state
        curveState_.setOnCoterminalSwapRates(swapRates_);

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

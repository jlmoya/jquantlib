/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

/*
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 Mark Joshi
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.SwapForwardMappings;
import org.jquantlib.model.marketmodels.curvestates.CoterminalSwapCurveState;

/**
 * Adapter that maps a coterminal-measure {@link MarketModel} to a forward-measure model.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/cotswaptofwdadapter.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Phase 3j Track B forward-declared: ported here so {@link CTSMMCapletCalibration#calibrate}
 * can use it. Track A may extend with {@code CotSwapToFwdAdapterFactory} (lands as A.4).
 */
public final class CotSwapToFwdAdapter extends MarketModel {

    private final MarketModel coterminalModel_;
    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final double[] initialRates_;
    private final Matrix[] pseudoRoots_;

    public CotSwapToFwdAdapter(final MarketModel coterminalModel) {
        this.coterminalModel_ = coterminalModel;
        this.numberOfFactors_ = coterminalModel.numberOfFactors();
        this.numberOfRates_ = coterminalModel.numberOfRates();
        this.numberOfSteps_ = coterminalModel.numberOfSteps();
        this.pseudoRoots_ = new Matrix[numberOfSteps_];

        // require all displacements equal
        final double[] displacements = coterminalModel.displacements();
        for ( int i = 1; i < displacements.length; ++i ) {
            QL.require(displacements[i] == displacements[0],
                    (i + 1) + "-th displacement (" + displacements[i] + ") not equal to the previous ones ("
                            + displacements[0] + ")");
        }

        final double[] rateTimes = coterminalModel.evolution().rateTimes();
        // ensure we step through all rateTimes
        final double[] evolutionTimes = coterminalModel.evolution().evolutionTimes();
        for ( int i = 0; i < rateTimes.length && rateTimes[i] <= evolutionTimes[evolutionTimes.length - 1]; ++i ) {
            boolean found = false;
            for ( final double t : evolutionTimes ) {
                if ( t == rateTimes[i] ) {
                    found = true;
                    break;
                }
            }
            QL.require(found, "skipping " + (i + 1) + "-th rate time");
        }

        final CoterminalSwapCurveState cs = new CoterminalSwapCurveState(rateTimes);
        cs.setOnCoterminalSwapRates(coterminalModel.initialRates());
        this.initialRates_ = cs.forwardRates();

        final Matrix zedMatrix = SwapForwardMappings.coterminalSwapZedMatrix(cs, displacements[0]);
        final Matrix invertedZedMatrix = zedMatrix.inverse();

        final int[] alive = coterminalModel.evolution().firstAliveRate();
        for ( int k = 0; k < numberOfSteps_; ++k ) {
            pseudoRoots_[k] = invertedZedMatrix.mul(coterminalModel.pseudoRoot(k));
            // zero the rows for "dead" rates
            for ( int i = 0; i < alive[k]; ++i ) {
                for ( int j = 0; j < pseudoRoots_[k].columns(); ++j ) {
                    pseudoRoots_[k].set(i, j, 0.0);
                }
            }
        }
    }

    @Override
    public double[] initialRates() {
        return initialRates_;
    }

    @Override
    public double[] displacements() {
        return coterminalModel_.displacements();
    }

    @Override
    public EvolutionDescription evolution() {
        return coterminalModel_.evolution();
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }

    @Override
    public int numberOfFactors() {
        return numberOfFactors_;
    }

    @Override
    public int numberOfSteps() {
        return numberOfSteps_;
    }

    @Override
    public Matrix pseudoRoot(final int i) {
        return pseudoRoots_[i];
    }
}

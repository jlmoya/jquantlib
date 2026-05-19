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
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.SwapForwardMappings;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;

/**
 * Adapter that maps a forward-measure {@link MarketModel} to a coterminal-swap measure model. Inverse of
 * {@link CotSwapToFwdAdapter}.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/fwdtocotswapadapter.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * @author Jose Moya
 */
public final class FwdToCotSwapAdapter extends MarketModel {

    private final MarketModel fwdModel_;
    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final double[] initialRates_;
    private final Matrix[] pseudoRoots_;

    public FwdToCotSwapAdapter(final MarketModel fwdModel) {
        this.fwdModel_ = fwdModel;
        this.numberOfFactors_ = fwdModel.numberOfFactors();
        this.numberOfRates_ = fwdModel.numberOfRates();
        this.numberOfSteps_ = fwdModel.numberOfSteps();
        this.pseudoRoots_ = new Matrix[numberOfSteps_];

        // require all displacements equal
        final double[] displacements = fwdModel.displacements();
        for ( int i = 1; i < displacements.length; ++i ) {
            QL.require(displacements[i] == displacements[0],
                    (i + 1) + "-th displacement (" + displacements[i] + ") not equal to the previous ones ("
                            + displacements[0] + ")");
        }

        final double[] rateTimes = fwdModel.evolution().rateTimes();
        final double[] evolutionTimes = fwdModel.evolution().evolutionTimes();
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

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(fwdModel.initialRates());
        this.initialRates_ = cs.coterminalSwapRates();

        final Matrix zedMatrix = SwapForwardMappings.coterminalSwapZedMatrix(cs, displacements[0]);

        final int[] alive = fwdModel.evolution().firstAliveRate();
        for ( int k = 0; k < numberOfSteps_; ++k ) {
            pseudoRoots_[k] = zedMatrix.mul(fwdModel.pseudoRoot(k));
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
        return fwdModel_.displacements();
    }

    @Override
    public EvolutionDescription evolution() {
        return fwdModel_.evolution();
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

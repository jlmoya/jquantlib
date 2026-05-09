/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k.5 C.9.

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
 Copyright (C) 2008 Mark Joshi
*/

package org.jquantlib.model.marketmodels.pathwisegreeks;

import java.util.List;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.OrthogonalProjections;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;

/**
 * Given a market model, a list of instruments, and possible vega-bump
 * clusters, produces pseudo-root bump matrices that shift each instrument's
 * implied vol by 1% while leaving the others fixed (via orthogonal projection).
 *
 * <p>If an instrument's contribution is too correlated with the others
 * (relative bump grows beyond {@code multiplierCutOff}), it is discarded.
 *
 * <p>The output of {@link #getVegaBumps()} is precisely the
 * {@code theBumps} vector passed into {@code PathwiseVegasAccountingEngine}.
 *
 * <p>Mirrors C++ {@code OrthogonalizedBumpFinder}
 * (ql/models/marketmodels/pathwisegreeks/bumpinstrumentjacobian.{hpp,cpp}
 * v1.42.1).
 *
 * @author Jose Moya
 */
public class OrthogonalizedBumpFinder {

    private final VolatilityBumpInstrumentJacobian derivativesProducer_;
    private final double multiplierCutOff_;
    private final double tolerance_;

    /**
     * @param bumps           the full vega-bump collection for the model
     * @param swaptions       list of swaption descriptors
     * @param caps            list of cap descriptors
     * @param multiplierCutOff if the projected-vector scaling factor exceeds
     *                         this value the instrument is discarded
     * @param tolerance        if the Gram-Schmidt residual norm is below this
     *                         value the instrument is discarded
     */
    public OrthogonalizedBumpFinder(final VegaBumpCollection bumps,
                                    final List<VolatilityBumpInstrumentJacobian.Swaption> swaptions,
                                    final List<VolatilityBumpInstrumentJacobian.Cap> caps,
                                    final double multiplierCutOff,
                                    final double tolerance) {
        this.derivativesProducer_ = new VolatilityBumpInstrumentJacobian(bumps, swaptions, caps);
        this.multiplierCutOff_    = multiplierCutOff;
        this.tolerance_           = tolerance;
    }

    /**
     * Fills {@code theBumps[step][bumpIndex]} with the bump matrices
     * (dimensions numberRates × factors, zero where not bumped).
     *
     * <p>Precisely the vector to pass into
     * {@code PathwiseVegasAccountingEngine}.
     *
     * @param theBumps output list-of-lists-of-Matrix; resized and filled here
     */
    public void getVegaBumps(final List<List<Matrix>> theBumps) {
        final Matrix allBumps = derivativesProducer_.getAllOnePercentBumps();
        final OrthogonalProjections projector = new OrthogonalProjections(
                allBumps, multiplierCutOff_, tolerance_);

        final int numberRestrictedBumps = projector.numberValidVectors();

        final MarketModel marketModel = derivativesProducer_.getInputBumps().associatedModel();
        final EvolutionDescription evolution = marketModel.evolution();
        final int numberSteps  = evolution.numberOfSteps();
        final int numberRates  = evolution.numberOfRates();
        final int factors      = marketModel.numberOfFactors();

        // Resize outer container
        theBumps.clear();
        final Matrix modelMatrix = new Matrix(numberRates, factors);
        for (int i = 0; i < numberSteps; ++i) {
            final java.util.ArrayList<Matrix> stepList = new java.util.ArrayList<>(numberRestrictedBumps);
            for (int j = 0; j < numberRestrictedBumps; ++j) {
                stepList.add(new Matrix(numberRates, factors));
            }
            theBumps.add(stepList);
        }

        final List<VegaBumpCluster> bumpClusters = derivativesProducer_.getInputBumps().allBumps();
        final boolean[] validVec = projector.validVectors();

        int bumpIndex = 0;
        for (int instrument = 0; instrument < validVec.length; ++instrument) {
            if (validVec[instrument]) {
                for (int cluster = 0; cluster < bumpClusters.size(); ++cluster) {
                    final VegaBumpCluster cl = bumpClusters.get(cluster);
                    final double magnitude = projector.getVector(instrument)[cluster];
                    for (int step = cl.stepBegin(); step < cl.stepEnd(); ++step) {
                        for (int rate = cl.rateBegin(); rate < cl.rateEnd(); ++rate) {
                            for (int factor = cl.factorBegin(); factor < cl.factorEnd(); ++factor) {
                                theBumps.get(step).get(bumpIndex).set(rate, factor, magnitude);
                            }
                        }
                    }
                }
                ++bumpIndex;
            }
        }
    }
}

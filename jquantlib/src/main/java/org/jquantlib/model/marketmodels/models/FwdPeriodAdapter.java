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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.ForwardForwardMappings;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;

/**
 * Adapter that re-bins a fine-grid forward-rate {@link MarketModel} onto a
 * coarser period grid (every {@code period}-th rate, starting at offset).
 *
 * <p>Java port of {@code ql/models/marketmodels/models/fwdperiodadapter.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * @author Jose Moya
 */
public class FwdPeriodAdapter extends MarketModel {

    private final EvolutionDescription evolution_;
    private final int numberOfFactors_;
    private final int numberOfRates_;
    private int numberOfSteps_;
    private final double[] initialRates_;
    private final Matrix[] pseudoRoots_;
    private final double[] displacements_;

    /**
     * @param largeModel        underlying fine-grid MarketModel
     * @param period            number of fine-grid rates per coarse-grid rate ({@code > 0})
     * @param offset            start offset within the fine grid
     * @param newDisplacements  per-coarse-rate displacements; if length 1, broadcast;
     *                          if empty, average across each period
     */
    public FwdPeriodAdapter(final MarketModel largeModel,
                            final int period,
                            final int offset,
                            final double[] newDisplacements) {
        QL.require(period > 0, "period must be greater than zero in fwdperiodadapter");
        QL.require(period > offset, "period must be greater than offset in fwdperiodadapter");

        this.numberOfFactors_ = largeModel.numberOfFactors();
        this.numberOfRates_ = (largeModel.numberOfRates() - offset) / period;

        final double[] largeDisplacements = largeModel.displacements();
        if (newDisplacements != null && newDisplacements.length == 1) {
            this.displacements_ = new double[numberOfRates_];
            Arrays.fill(this.displacements_, newDisplacements[0]);
        } else if (newDisplacements == null || newDisplacements.length == 0) {
            this.displacements_ = new double[numberOfRates_];
            int m = 0;
            for (int k = 0; k < numberOfRates_; ++k) {
                double sum = 0.0;
                for (int l = 0; l < period; ++l, ++m) {
                    sum += largeDisplacements[m];
                }
                this.displacements_[k] = sum / period;
            }
        } else {
            this.displacements_ = newDisplacements.clone();
        }
        QL.require(this.displacements_.length == numberOfRates_,
                "newDisplacements should be empty, 1, or number of new rates in fwdperiodadapter");

        final LMMCurveState largeCS = new LMMCurveState(largeModel.evolution().rateTimes());
        largeCS.setOnForwardRates(largeModel.initialRates());

        final LMMCurveState smallCS = ForwardForwardMappings.restrictCurveState(
                largeCS, period, offset);

        this.initialRates_ = smallCS.forwardRates();

        final double finalReset = smallCS.rateTimes()[smallCS.numberOfRates() - 1];
        final double[] oldEvolutionTimes = largeModel.evolution().evolutionTimes();
        int newCount = 0;
        for (final double t : oldEvolutionTimes) {
            if (t <= finalReset) ++newCount;
            else break;
        }
        final double[] newEvolutionTimes = Arrays.copyOf(oldEvolutionTimes, newCount);

        this.evolution_ = new EvolutionDescription(smallCS.rateTimes(), newEvolutionTimes);
        this.numberOfSteps_ = newEvolutionTimes.length;

        // ensure every (small) rate time except last is in the evolution-times set
        final double[] smallRateTimes = smallCS.rateTimes();
        final Set<Double> setTimes = new HashSet<>();
        for (final double t : evolution_.evolutionTimes()) {
            setTimes.add(t);
        }
        for (int i = 0; i < smallRateTimes.length - 1; ++i) {
            QL.require(setTimes.contains(smallRateTimes[i]),
                    "every new rate time except last must be an evolution time in fwdperiod adapter");
        }

        final Matrix yMatrix = ForwardForwardMappings.yMatrix(
                largeCS, largeDisplacements, this.displacements_, period, offset);

        final int[] alive = evolution_.firstAliveRate();

        this.pseudoRoots_ = new Matrix[numberOfSteps_];
        for (int k = 0; k < numberOfSteps_; ++k) {
            pseudoRoots_[k] = yMatrix.mul(largeModel.pseudoRoot(k));
            // zero rows for "dead" rates
            for (int i = 0; i < alive[k]; ++i) {
                for (int j = 0; j < pseudoRoots_[k].columns(); ++j) {
                    pseudoRoots_[k].set(i, j, 0.0);
                }
            }
        }
    }

    @Override public double[] initialRates() { return initialRates_; }
    @Override public double[] displacements() { return displacements_; }
    @Override public EvolutionDescription evolution() { return evolution_; }
    @Override public int numberOfRates() { return numberOfRates_; }
    @Override public int numberOfFactors() { return numberOfFactors_; }
    @Override public int numberOfSteps() { return numberOfSteps_; }
    @Override public Matrix pseudoRoot(final int i) { return pseudoRoots_[i]; }
}

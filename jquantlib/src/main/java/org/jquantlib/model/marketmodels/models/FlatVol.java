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
 Copyright (C) 2006 Chiara Fornarola
 Copyright (C) 2006, 2007 StatPro Italia srl
 Copyright (C) 2006 Katiuscia Manzoni
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;

/**
 * Flat-volatility market model with arbitrary correlation structure.
 *
 * <p>For each evolution step, builds the per-step covariance matrix from
 * (constant per-rate volatilities) &times; (per-step correlation slice) and
 * decomposes via {@link PseudoSqrt#rankReducedSqrt} to obtain the pseudo-root.
 *
 * <p>Mirrors {@code FlatVol} in
 * {@code ql/models/marketmodels/models/flatvol.{hpp,cpp}} (QuantLib v1.42.1).
 *
 * @see "ql/models/marketmodels/models/flatvol.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public class FlatVol extends MarketModel {

    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final double[] initialRates_;
    private final double[] displacements_;
    private final EvolutionDescription evolution_;
    private final Matrix[] pseudoRoots_;

    /**
     * Builds a FlatVol market model.
     *
     * @param vols              per-rate volatility (size = numberOfRates)
     * @param corr              piecewise-constant correlation structure
     * @param evolution         evolution description
     * @param numberOfFactors   number of stochastic factors
     * @param initialRates      initial forward rates (size = numberOfRates)
     * @param displacements     per-rate displacements (size = numberOfRates)
     */
    public FlatVol(final double[] vols,
                   final PiecewiseConstantCorrelation corr,
                   final EvolutionDescription evolution,
                   final int numberOfFactors,
                   final double[] initialRates,
                   final double[] displacements) {
        this.numberOfFactors_ = numberOfFactors;
        this.numberOfRates_ = initialRates.length;
        this.numberOfSteps_ = evolution.evolutionTimes().length;
        this.initialRates_ = initialRates.clone();
        this.displacements_ = displacements.clone();
        this.evolution_ = evolution;

        final double[] rateTimes = evolution.rateTimes();
        QL.require(numberOfRates_ == rateTimes.length - 1,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and rate times");
        QL.require(numberOfRates_ == displacements.length,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and displacements (" + displacements.length + ")");
        QL.require(numberOfRates_ == vols.length,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and vols (" + vols.length + ")");
        QL.require(numberOfRates_ <= numberOfFactors_ * numberOfSteps_,
                "number of rates (" + numberOfRates_
                        + ") greater than number of factors (" + numberOfFactors_
                        + ") times number of steps (" + numberOfSteps_ + ")");
        QL.require(numberOfFactors <= numberOfRates_,
                "number of factors (" + numberOfFactors
                        + ") cannot be greater than numberOfRates ("
                        + numberOfRates_ + ")");
        QL.require(numberOfFactors > 0,
                "number of factors (" + numberOfFactors
                        + ") must be greater than zero");

        this.pseudoRoots_ = new Matrix[numberOfSteps_];

        // PiecewiseConstantCorrelation provides times() and per-interval correlation matrices
        final java.util.List<Double> corrTimes = corr.times();
        final double[] evolTimes = evolution.evolutionTimes();
        double effStopTime = 0.0;

        int kk = 0;
        for (int k = 0; k < numberOfSteps_; ++k) {
            // one covariance per evolution step
            final Matrix covariance = new Matrix(numberOfRates_, numberOfRates_);
            // Matrix() initializes to 0.0 in JQuantLib

            // there might be more than one correlation matrix in a single evolution step
            for (; corrTimes.get(kk) < evolTimes[k]; ++kk) {
                final double effStartTime = effStopTime;
                effStopTime = corrTimes.get(kk);
                final Matrix corrMatrix = corr.correlation(kk);
                accumulateCovariance(covariance, corrMatrix, vols, rateTimes,
                        effStartTime, effStopTime);
            }
            // last part in the evolution step
            final double effStartTime = effStopTime;
            effStopTime = evolTimes[k];
            final Matrix corrMatrix = corr.correlation(kk);
            accumulateCovariance(covariance, corrMatrix, vols, rateTimes,
                    effStartTime, effStopTime);

            // no more use for the kk-th correlation matrix
            while (kk < corrTimes.size() && corrTimes.get(kk) <= evolTimes[k]) {
                ++kk;
            }
            // guard against running off the end (when last evolTime equals last corrTime)
            if (kk >= corrTimes.size()) {
                kk = corrTimes.size() - 1;
            }

            // make it symmetric
            for (int i = 0; i < numberOfRates_; ++i) {
                for (int j = i + 1; j < numberOfRates_; ++j) {
                    covariance.set(j, i, covariance.get(i, j));
                }
            }

            pseudoRoots_[k] = PseudoSqrt.rankReducedSqrt(covariance,
                    numberOfFactors, 1, SalvagingAlgorithm.None);

            QL.require(pseudoRoots_[k].rows() == numberOfRates_,
                    "step " + k + " flat vol wrong number of rows: "
                            + pseudoRoots_[k].rows() + " instead of " + numberOfRates_);
            QL.require(pseudoRoots_[k].columns() == numberOfFactors,
                    "step " + k + " flat vol wrong number of columns: "
                            + pseudoRoots_[k].columns() + " instead of " + numberOfFactors_);
        }
    }

    /**
     * Accumulates {@code covariance += flatVolCovariance(...) * corrMatrix} for the
     * upper triangle of {@code covariance} over the integration window
     * {@code [effStartTime, effStopTime]}.
     */
    private void accumulateCovariance(final Matrix covariance,
                                      final Matrix corrMatrix,
                                      final double[] vols,
                                      final double[] rateTimes,
                                      final double effStartTime,
                                      final double effStopTime) {
        for (int i = 0; i < numberOfRates_; ++i) {
            for (int j = i; j < numberOfRates_; ++j) {
                final double cov = flatVolCovariance(effStartTime, effStopTime,
                        rateTimes[i], rateTimes[j],
                        vols[i], vols[j]);
                covariance.set(i, j, covariance.get(i, j) + cov * corrMatrix.get(i, j));
            }
        }
    }

    /**
     * Free function {@code flatVolCovariance} from C++ flatvol.cpp:
     * integrated covariance over [t1,t2] for two flat-vol rates fixing at T and S
     * (with vols v1 and v2). Returns 0 if the integration window is fully past
     * the smaller fixing time, otherwise truncates at min(T,S,t2).
     */
    public static double flatVolCovariance(final double t1, final double t2,
                                           final double T, final double S,
                                           final double v1, final double v2) {
        if (t1 > t2) {
            throw new IllegalArgumentException(
                    "integrations bounds (" + t1 + "," + t2 + ") are in reverse order");
        }
        double cutOff = Math.min(S, T);
        if (t1 >= cutOff) {
            return 0.0;
        } else {
            cutOff = Math.min(t2, cutOff);
            return (cutOff - t1) * v1 * v2;
        }
    }

    @Override public double[] initialRates() { return initialRates_; }
    @Override public double[] displacements() { return displacements_; }
    @Override public EvolutionDescription evolution() { return evolution_; }
    @Override public int numberOfRates() { return numberOfRates_; }
    @Override public int numberOfFactors() { return numberOfFactors_; }
    @Override public int numberOfSteps() { return numberOfSteps_; }

    @Override
    public Matrix pseudoRoot(final int i) {
        QL.require(i < numberOfSteps_,
                "the index " + i + " is invalid: it must be less than "
                        + "number of steps (" + numberOfSteps_ + ")");
        return pseudoRoots_[i];
    }
}

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
 Copyright (C) 2006 Mark Joshi
 Copyright (C) 2005, 2006 Klaus Spanderen
*/

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.PseudoSqrt;
import org.jquantlib.math.matrixutilities.PseudoSqrt.SalvagingAlgorithm;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;
import org.jquantlib.termstructures.volatility.AbcdFunction;

/**
 * ABCD-interpolated volatility market model.
 *
 * <p>Per-rate volatility comes from {@code k_i * AbcdFunction(a,b,c,d)(rateTime - t)}.
 * Per-step covariance is built by accumulating {@code k_i*k_j*abcd.covariance(...)}
 * weighted by the (piecewise-constant) correlation matrix slice, then decomposed
 * via {@link PseudoSqrt#rankReducedSqrt}.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/abcdvol.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * @author Jose Moya
 */
public class AbcdVol extends MarketModel {

    private final int numberOfFactors_;
    private final int numberOfRates_;
    private final int numberOfSteps_;
    private final double[] initialRates_;
    private final double[] displacements_;
    private final EvolutionDescription evolution_;
    private final Matrix[] pseudoRoots_;

    public AbcdVol(final double a, final double b, final double c, final double d,
                   final double[] ks,
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
        QL.require(numberOfRates_ == ks.length,
                "mismatch between number of rates (" + numberOfRates_
                        + ") and ks (" + ks.length + ")");
        QL.require(numberOfFactors <= numberOfRates_,
                "number of factors (" + numberOfFactors
                        + ") cannot be greater than numberOfRates ("
                        + numberOfRates_ + ")");
        QL.require(numberOfFactors > 0,
                "number of factors (" + numberOfFactors
                        + ") must be greater than zero");

        this.pseudoRoots_ = new Matrix[numberOfSteps_];

        final AbcdFunction abcd = new AbcdFunction(a, b, c, d);
        final java.util.List<Double> corrTimes = corr.times();
        final double[] evolTimes = evolution.evolutionTimes();
        double effStopTime = 0.0;

        int kk = 0;
        for (int k = 0; k < numberOfSteps_; ++k) {
            final Matrix covariance = new Matrix(numberOfRates_, numberOfRates_);

            for (; corrTimes.get(kk) < evolTimes[k]; ++kk) {
                final double effStartTime = effStopTime;
                effStopTime = corrTimes.get(kk);
                final Matrix corrMatrix = corr.correlation(kk);
                accumulateCovariance(covariance, corrMatrix, abcd, ks, rateTimes,
                        effStartTime, effStopTime);
            }
            final double effStartTime = effStopTime;
            effStopTime = evolTimes[k];
            final Matrix corrMatrix = corr.correlation(kk);
            accumulateCovariance(covariance, corrMatrix, abcd, ks, rateTimes,
                    effStartTime, effStopTime);

            while (kk < corrTimes.size() && corrTimes.get(kk) <= evolTimes[k]) {
                ++kk;
            }
            if (kk >= corrTimes.size()) {
                kk = corrTimes.size() - 1;
            }

            // symmetrize
            for (int i = 0; i < numberOfRates_; ++i) {
                for (int j = i + 1; j < numberOfRates_; ++j) {
                    covariance.set(j, i, covariance.get(i, j));
                }
            }

            pseudoRoots_[k] = PseudoSqrt.rankReducedSqrt(covariance,
                    numberOfFactors, 1, SalvagingAlgorithm.None);

            QL.require(pseudoRoots_[k].rows() == numberOfRates_,
                    "step " + k + " abcd vol wrong number of rows: "
                            + pseudoRoots_[k].rows() + " instead of " + numberOfRates_);
            QL.require(pseudoRoots_[k].columns() == numberOfFactors,
                    "step " + k + " abcd vol wrong number of columns: "
                            + pseudoRoots_[k].columns() + " instead of " + numberOfFactors);
        }
    }

    private void accumulateCovariance(final Matrix covariance,
                                      final Matrix corrMatrix,
                                      final AbcdFunction abcd,
                                      final double[] ks,
                                      final double[] rateTimes,
                                      final double effStartTime,
                                      final double effStopTime) {
        for (int i = 0; i < numberOfRates_; ++i) {
            for (int j = i; j < numberOfRates_; ++j) {
                final double cov = ks[i] * ks[j]
                        * abcd.covariance(effStartTime, effStopTime,
                                rateTimes[i], rateTimes[j]);
                covariance.set(i, j, covariance.get(i, j) + cov * corrMatrix.get(i, j));
            }
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

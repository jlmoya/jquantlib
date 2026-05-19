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
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.model.marketmodels;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Base class for market models.
 * <p>
 * For each time step, generates the pseudo-square root of the covariance matrix for that time step. Derived classes
 * must implement {@link #initialRates()}, {@link #displacements()}, {@link #evolution()}, {@link #numberOfRates()},
 * {@link #numberOfFactors()}, {@link #numberOfSteps()}, and {@link #pseudoRoot(int)}.
 * <p>
 * The {@link #covariance(int)} and {@link #totalCovariance(int)} methods are computed lazily from the pseudo-roots;
 * subclasses may override to provide a more efficient implementation.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/marketmodel.{hpp,cpp}" v1.42.1
 */
public abstract class MarketModel {

    private Matrix[] covariance_;
    private Matrix[] totalCovariance_;

    public abstract double[] initialRates();

    public abstract double[] displacements();

    public abstract EvolutionDescription evolution();

    public abstract int numberOfRates();

    public abstract int numberOfFactors();

    public abstract int numberOfSteps();

    public abstract Matrix pseudoRoot(int i);

    /**
     * Returns the covariance matrix at step {@code i}, computed as {@code pseudoRoot(i) * pseudoRoot(i)^T}.
     */
    public Matrix covariance(final int i) {
        if ( covariance_ == null ) {
            covariance_ = new Matrix[numberOfSteps()];
            for ( int j = 0; j < numberOfSteps(); ++j ) {
                final Matrix root = pseudoRoot(j);
                covariance_[j] = root.mul(root.transpose());
            }
        }
        QL.require(i < covariance_.length,
                "i (" + i + ") must be less than covariance length (" + covariance_.length + ")");
        return covariance_[i];
    }

    /**
     * Returns the cumulative covariance matrix from step 0 through {@code endIndex} inclusive.
     */
    public Matrix totalCovariance(final int endIndex) {
        if ( totalCovariance_ == null ) {
            totalCovariance_ = new Matrix[numberOfSteps()];
            // call to covariance(0) triggers calculation, if necessary
            totalCovariance_[0] = new Matrix(covariance(0));
            for ( int j = 1; j < numberOfSteps(); ++j ) {
                totalCovariance_[j] = totalCovariance_[j - 1].add(covariance_[j]);
            }
        }
        QL.require(endIndex < covariance_.length,
                "endIndex (" + endIndex + ") must be less than covariance length (" + covariance_.length + ")");
        return totalCovariance_[endIndex];
    }

    /**
     * Returns the volatility (sqrt(variance/tau)) over each evolution step for forward-rate index {@code i}.
     */
    public double[] timeDependentVolatility(final int i) {
        QL.require(i < numberOfRates(), "index (" + i + ") must less than number of rates (" + numberOfRates() + ")");

        final double[] result = new double[numberOfSteps()];
        final double[] evolutionTime = evolution().evolutionTimes();

        double lastTime = 0.0;
        for ( int j = 0; j < numberOfSteps(); ++j ) {
            final double tau = evolutionTime[j] - lastTime;
            final double thisVariance = covariance(j).get(i, i);
            final double thisVol = Math.sqrt(thisVariance / tau);
            result[j] = thisVol;
            lastTime = evolutionTime[j];
        }
        return result;
    }
}

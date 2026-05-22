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
 Copyright (C) 2007 Ferdinando Ametrano
 Copyright (C) 2007 Mark Joshi
 Copyright (C) 2007 François du Vignaud
 Copyright (C) 2007 Chiara Fornarola
 Copyright (C) 2007 Katiuscia Manzoni
*/

package org.jquantlib.model.marketmodels.correlations;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.PiecewiseConstantCorrelation;

import java.util.ArrayList;
import java.util.List;

/**
 * Time-homogeneous forward correlation structure built from a single forward-rate correlation matrix. Per step k, the
 * upper-left k diagonal block of the original matrix is preserved and the still-alive entries shift along the
 * diagonal.
 *
 * @author Jose Moya
 * @see "ql/models/marketmodels/correlations/timehomogeneousforwardcorrelation.{hpp,cpp}" v1.42.1
 */
public class TimeHomogeneousForwardCorrelation extends PiecewiseConstantCorrelation {

    private final int numberOfRates_;
    private final Matrix fwdCorrelation_;
    private final List< Double > rateTimes_;
    private final List< Double > times_;
    private final List< Matrix > correlations_;

    public TimeHomogeneousForwardCorrelation(final Matrix fwdCorrelation, final List< Double > rateTimes) {
        this.numberOfRates_ = rateTimes == null || rateTimes.isEmpty() ? 0 : rateTimes.size() - 1;
        this.fwdCorrelation_ = fwdCorrelation;
        this.rateTimes_ = new ArrayList<>(rateTimes);
        this.times_ = new ArrayList<>(numberOfRates_);

        ExponentialForwardCorrelation.checkIncreasingTimes(this.rateTimes_);
        QL.require(numberOfRates_ >= 1, "Rate times must contain at least two values");
        QL.require(numberOfRates_ == fwdCorrelation.rows(),
                "mismatch between number of rates (" + numberOfRates_ + ") and fwdCorrelation rows ("
                        + fwdCorrelation.rows() + ")");
        QL.require(numberOfRates_ == fwdCorrelation.columns(),
                "mismatch between number of rates (" + numberOfRates_ + ") and fwdCorrelation columns ("
                        + fwdCorrelation.columns() + ")");

        // copy rateTimes[0..n-1] into times_
        for ( int i = 0; i < numberOfRates_; ++i ) {
            this.times_.add(this.rateTimes_.get(i));
        }
        this.correlations_ = evolvedMatrices(fwdCorrelation_);
    }

    /**
     * Build the per-step list of correlation matrices from the input forward correlation matrix using the
     * time-homogeneous shift rule.
     */
    public static List< Matrix > evolvedMatrices(final Matrix fwdCorrelation) {
        final int numberOfRates = fwdCorrelation.rows();
        final List< Matrix > correlations = new ArrayList<>(numberOfRates);
        for ( int k = 0; k < numberOfRates; ++k ) {
            correlations.add(new Matrix(numberOfRates, numberOfRates));
        }
        for ( int k = 0; k < correlations.size(); ++k ) {
            final Matrix m = correlations.get(k);
            // proper diagonal values
            for ( int i = k; i < numberOfRates; ++i ) {
                m.set(i, i, 1.0);
            }
            // copy only time-homogeneous values
            for ( int i = k; i < numberOfRates; ++i ) {
                for ( int j = k; j < i; ++j ) {
                    final double v = fwdCorrelation.get(i - k, j - k);
                    m.set(i, j, v);
                    m.set(j, i, v);
                }
            }
        }
        return correlations;
    }

    @Override
    public List< Double > times() {
        return times_;
    }

    @Override
    public List< Double > rateTimes() {
        return rateTimes_;
    }

    @Override
    public List< Matrix > correlations() {
        return correlations_;
    }

    @Override
    public int numberOfRates() {
        return numberOfRates_;
    }
}

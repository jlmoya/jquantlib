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
*/

package org.jquantlib.model.marketmodels;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;

/**
 * Piecewise-constant correlation structure for market-model simulations.
 * <p>
 * This is the abstract interface for correlation structures that are constant
 * on the intervals defined by {@link #times()}. The {@link #correlations()}
 * method returns one matrix per interval.
 * <p>
 * Note: corrTimes must include all rateTimes but the last.
 *
 * @author Jose Moya
 *
 * @see "ql/models/marketmodels/piecewiseconstantcorrelation.hpp" v1.42.1
 */
public abstract class PiecewiseConstantCorrelation {

    /**
     * @return the partition times defining the piecewise constant correlation.
     */
    public abstract List<Double> times();

    /**
     * @return the rate times of the underlying forward-rate set.
     */
    public abstract List<Double> rateTimes();

    /**
     * @return the list of per-interval correlation matrices.
     */
    public abstract List<Matrix> correlations();

    /**
     * @param i the interval index
     * @return the i-th correlation matrix.
     */
    public Matrix correlation(final int i) {
        final List<Matrix> results = correlations();
        QL.require(i < results.size(),
                "index (" + i + ") must be less than correlations vector size ("
                        + results.size() + ")");
        return results.get(i);
    }

    /**
     * @return the number of forward rates carried by the correlation structure.
     */
    public abstract int numberOfRates();
}

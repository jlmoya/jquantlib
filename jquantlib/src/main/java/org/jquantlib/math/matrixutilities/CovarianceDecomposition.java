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
 Copyright (C) 2004, 2007, 2009 Ferdinando Ametrano
*/

package org.jquantlib.math.matrixutilities;

import org.jquantlib.QL;

/**
 * Covariance decomposition into correlation and variances.
 * <p>
 * Extracts the correlation matrix and the vector of variances out of the input covariance matrix.
 * <p>
 * Note: only the lower symmetric part of the covariance matrix is used.
 *
 * @author Jose Moya
 * @see "ql/math/matrixutilities/getcovariance.{hpp,cpp}" v1.42.1
 */
public class CovarianceDecomposition {

    private final double[] variances_;
    private final double[] stdDevs_;
    private final Matrix correlationMatrix_;

    public CovarianceDecomposition(final Matrix cov) {
        this(cov, 1.0e-12);
    }

    public CovarianceDecomposition(final Matrix cov, final double tolerance) {
        final int size = cov.rows();
        QL.require(size == cov.columns(),
                "input covariance matrix must be square, it is [" + size + "x" + cov.columns() + "]");

        this.variances_ = new double[size];
        this.stdDevs_ = new double[size];
        this.correlationMatrix_ = new Matrix(size, size);

        for ( int i = 0; i < size; ++i ) {
            variances_[i] = cov.get(i, i);
            stdDevs_[i] = Math.sqrt(variances_[i]);
            correlationMatrix_.set(i, i, 1.0);
            for ( int j = 0; j < i; ++j ) {
                QL.require(Math.abs(cov.get(i, j) - cov.get(j, i)) <= tolerance,
                        "invalid covariance matrix:" + "\nc[" + i + ", " + j + "] = " + cov.get(i, j) + "\nc[" + j
                                + ", " + i + "] = " + cov.get(j, i));
                final double rho = cov.get(i, j) / (stdDevs_[i] * stdDevs_[j]);
                correlationMatrix_.set(i, j, rho);
                correlationMatrix_.set(j, i, rho);
            }
        }
    }

    /** @return the variances array */
    public double[] variances() {
        return variances_;
    }

    /** @return the standard-deviations array */
    public double[] standardDeviations() {
        return stdDevs_;
    }

    /** @return the correlation matrix */
    public Matrix correlationMatrix() {
        return correlationMatrix_;
    }
}

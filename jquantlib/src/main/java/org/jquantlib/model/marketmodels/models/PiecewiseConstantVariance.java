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

package org.jquantlib.model.marketmodels.models;

import org.jquantlib.QL;

/**
 * Piecewise-constant variance abstract base.
 *
 * <p>Java port of {@code ql/models/marketmodels/models/piecewiseconstantvariance.{hpp,cpp}}
 * (QuantLib v1.42.1).
 *
 * <p>Subclasses must provide:
 * <ul>
 *   <li>{@link #variances()} — per-step variance values</li>
 *   <li>{@link #volatilities()} — per-step volatility values</li>
 *   <li>{@link #rateTimes()} — rate-time grid</li>
 * </ul>
 *
 * <p>Phase 3j L0.1 — forward-declared on Track B.
 */
public abstract class PiecewiseConstantVariance {

    public abstract double[] variances();
    public abstract double[] volatilities();
    public abstract double[] rateTimes();

    /** Variance at step i. */
    public final double variance(final int i) {
        QL.require(i < variances().length, "invalid step index");
        return variances()[i];
    }

    /** Volatility at step i. */
    public final double volatility(final int i) {
        QL.require(i < volatilities().length, "invalid step index");
        return volatilities()[i];
    }

    /** Total accumulated variance through step i (inclusive). */
    public final double totalVariance(final int i) {
        QL.require(i < variances().length, "invalid step index");
        final double[] v = variances();
        double sum = 0.0;
        for (int k = 0; k <= i; ++k) {
            sum += v[k];
        }
        return sum;
    }

    /** Total volatility through step i: sqrt(totalVariance(i)/rateTimes[i]). */
    public final double totalVolatility(final int i) {
        return Math.sqrt(totalVariance(i) / rateTimes()[i]);
    }
}

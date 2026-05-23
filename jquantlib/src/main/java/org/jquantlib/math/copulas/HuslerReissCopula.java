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
 Copyright (C) 2010 Hachemi Benyahia
 Copyright (C) 2010 DeriveXperts SAS
*/

package org.jquantlib.math.copulas;

import org.jquantlib.math.distributions.CumulativeNormalDistribution;

/**
 * Husler-Reiss extreme-value copula.
 *
 * <p>Formula:
 * {@code C(u, v) = u^Phi(1/theta + theta/2 * log(-log u / -log v))
 *                * v^Phi(1/theta + theta/2 * log(-log v / -log u))}
 * for {@code theta >= 0}, where {@code Phi} is the standard-normal CDF.
 *
 * <p>Java port of v1.42.1 {@code ql/math/copulas/huslerreisscopula.{hpp,cpp}}.
 */
public final class HuslerReissCopula implements Copula {

    private final double theta;
    private final CumulativeNormalDistribution cumNormal;

    public HuslerReissCopula(final double theta) {
        if (theta < 0.0) {
            throw new IllegalArgumentException(
                    "theta (" + theta + ") must be greater or equal to 0");
        }
        this.theta = theta;
        this.cumNormal = new CumulativeNormalDistribution();
    }

    @Override
    public double apply(final double u, final double v) {
        if (u < 0.0 || u > 1.0) {
            throw new IllegalArgumentException(
                    "1st argument (" + u + ") must be in [0,1]");
        }
        if (v < 0.0 || v > 1.0) {
            throw new IllegalArgumentException(
                    "2nd argument (" + v + ") must be in [0,1]");
        }
        return Math.pow(u,
                cumNormal.op(1.0 / theta
                        + 0.5 * theta * Math.log(-Math.log(u) / -Math.log(v))))
                * Math.pow(v,
                        cumNormal.op(1.0 / theta + 0.5 * theta
                                * Math.log(-Math.log(v) / -Math.log(u))));
    }
}

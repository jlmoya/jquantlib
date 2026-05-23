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
 Copyright (C) 2008 Marek Glowacki
*/

package org.jquantlib.math.copulas;

/**
 * Gumbel extreme-value Archimedean copula.
 *
 * <p>Formula:
 * {@code C(u, v) = exp(-((-log u)^theta + (-log v)^theta)^(1/theta))}
 * for {@code theta >= 1}.
 *
 * <p>Java port of v1.42.1 {@code ql/math/copulas/gumbelcopula.{hpp,cpp}}.
 */
public final class GumbelCopula implements Copula {

    private final double theta;

    public GumbelCopula(final double theta) {
        if (theta < 1.0) {
            throw new IllegalArgumentException(
                    "theta (" + theta + ") must be greater or equal to 1");
        }
        this.theta = theta;
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
        return Math.exp(-Math.pow(
                Math.pow(-Math.log(u), theta) + Math.pow(-Math.log(v), theta),
                1.0 / theta));
    }
}

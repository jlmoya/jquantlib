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
 * Farlie-Gumbel-Morgenstern copula.
 *
 * <p>Formula: {@code C(u, v) = u*v + theta*u*v*(1-u)*(1-v)} for
 * {@code theta in [-1, 1]}.
 *
 * <p>Java port of v1.42.1
 * {@code ql/math/copulas/farliegumbelmorgensterncopula.{hpp,cpp}}.
 */
public final class FarlieGumbelMorgensternCopula implements Copula {

    private final double theta;

    public FarlieGumbelMorgensternCopula(final double theta) {
        if (theta < -1.0 || theta > 1.0) {
            throw new IllegalArgumentException(
                    "theta (" + theta + ") must be in [-1,1]");
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
        return u * v + theta * u * v * (1.0 - u) * (1.0 - v);
    }
}

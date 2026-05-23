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
 * Marshall-Olkin copula.
 *
 * <p>Formula:
 * {@code C(u, v) = min(v * u^(1-a1), u * v^(1-a2))}
 * for {@code a1, a2 >= 0}. C++ stores {@code a1_ = 1 - a1} and
 * {@code a2_ = 1 - a2}, applied as {@code min(y * x^a1_, x * y^a2_)}.
 *
 * <p>Java port of v1.42.1 {@code ql/math/copulas/marshallolkincopula.{hpp,cpp}}.
 */
public final class MarshallOlkinCopula implements Copula {

    private final double a1;
    private final double a2;

    /**
     * @param a1 first Marshall-Olkin parameter (must be {@code >= 0});
     *           stored internally as {@code 1 - a1} per C++.
     * @param a2 second Marshall-Olkin parameter (must be {@code >= 0});
     *           stored internally as {@code 1 - a2} per C++.
     */
    public MarshallOlkinCopula(final double a1, final double a2) {
        if (a1 < 0.0) {
            throw new IllegalArgumentException(
                    "1st parameter (" + a1 + ") must be non-negative");
        }
        if (a2 < 0.0) {
            throw new IllegalArgumentException(
                    "2nd parameter (" + a2 + ") must be non-negative");
        }
        this.a1 = 1.0 - a1;
        this.a2 = 1.0 - a2;
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
        return Math.min(v * Math.pow(u, a1), u * Math.pow(v, a2));
    }
}

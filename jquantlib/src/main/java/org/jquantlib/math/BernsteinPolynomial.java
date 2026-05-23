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

package org.jquantlib.math;

/**
 * Bernstein polynomials.
 * <p>
 * Faithful port of {@code ql/math/bernsteinpolynomial.{hpp,cpp}} from QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The Bernstein polynomials {@code B_{i,n}(x)} are defined as
 * <pre>
 *   B_{i,n}(x) = C(n,i) * x^i * (1-x)^(n-i)
 * </pre>
 *
 * <p>See <a href="http://mathworld.wolfram.com/BernsteinPolynomial.html">
 * Weisstein, Eric W. "Bernstein Polynomial." From MathWorld -- A Wolfram Web
 * Resource</a>.
 *
 * @author Jose Moya
 */
public final class BernsteinPolynomial {

    private static final Factorial FACTORIAL = new Factorial();

    private BernsteinPolynomial() {
        // utility class
    }

    /**
     * Evaluates the Bernstein basis polynomial {@code B_{i,n}(x)}.
     *
     * @param i the basis index, {@code 0 <= i <= n}
     * @param n the polynomial order
     * @param x the abscissa
     * @return {@code B_{i,n}(x)}
     */
    public static double get(final int i, final int n, final double x) {
        final double coeff = FACTORIAL.get(n)
                / (FACTORIAL.get(n - i) * FACTORIAL.get(i));
        return coeff * Math.pow(x, i) * Math.pow(1.0 - x, n - i);
    }
}

/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.integrals;

/**
 * Gauss-Legendre polynomial — special case of {@link GaussJacobiPolynomial}
 * with {@code alpha = beta = 0}, weight {@code w(x) = 1} on {@code [-1, 1]}.
 *
 * <p>Phase 4a.5 A.5.1 port of {@code QuantLib::GaussLegendrePolynomial}
 * (v1.42.1 ql/math/integrals/gaussianorthogonalpolynomial.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 */
public final class GaussLegendrePolynomial extends GaussJacobiPolynomial {

    public GaussLegendrePolynomial() {
        super(0.0, 0.0);
    }
}

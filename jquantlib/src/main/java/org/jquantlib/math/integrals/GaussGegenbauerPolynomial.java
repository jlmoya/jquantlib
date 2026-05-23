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
 * Gauss-Gegenbauer polynomial — special case of {@link GaussJacobiPolynomial}
 * with {@code alpha = beta = lambda - 0.5}.
 *
 * <p>Faithful Java port of {@code QuantLib::GaussGegenbauerPolynomial}
 * (v1.42.1 {@code ql/math/integrals/gaussianorthogonalpolynomial.hpp}, pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Phase 2 L1-D port.
 */
public final class GaussGegenbauerPolynomial extends GaussJacobiPolynomial {

    public GaussGegenbauerPolynomial(final double lambda) {
        super(lambda - 0.5, lambda - 0.5);
    }
}

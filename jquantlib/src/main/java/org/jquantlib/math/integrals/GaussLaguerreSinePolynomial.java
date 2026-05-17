/*
 Copyright (C) 2020 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.math.integrals;

/**
 * Gauss-Laguerre-Sine quadrature polynomial.
 *
 * <p>Java port of the C++ template
 * {@code GaussLaguerreSinePolynomial<mp_real>} from QuantLib v1.42.1
 * {@code ql/math/integrals/gausslaguerrecosinepolynomial.hpp}. Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Performs a 1-dimensional Gauss-Laguerre-Sine integration on
 * {@code [0, infty)} with weighting function:
 * <pre>
 *   w(x; u) = exp(-x) * (1 + sin(u * x)) / m0
 * </pre>
 * where {@code m0 = 1 + u/(1+u*u)} so that {@code mu_0 = 1}.
 *
 * <p>The arbitrary-precision template parameter {@code mp_real} in C++ is not
 * needed in the Java port; {@code double} is used throughout. See
 * {@link MomentBasedGaussianPolynomial} for the precision rationale.
 */
public class GaussLaguerreSinePolynomial extends GaussLaguerreTrigonometricBase {

    private final double m0_;

    /**
     * @param u oscillation parameter for {@code sin(u * x)}
     */
    public GaussLaguerreSinePolynomial(final double u) {
        super(u);
        this.m0_ = 1.0 + u / (1.0 + u * u);
    }

    @Override
    public double moment(final int n) {
        return (moment_(n) + fact(n)) / m0_;
    }

    @Override
    public double w(final double x) {
        return Math.exp(-x) * (1.0 + Math.sin(u_ * x)) / m0_;
    }

    @Override
    protected double m0() {
        return u_ / (1.0 + u_ * u_);
    }

    @Override
    protected double m1() {
        final double d = 1.0 + u_ * u_;
        return 2.0 * u_ / (d * d);
    }
}

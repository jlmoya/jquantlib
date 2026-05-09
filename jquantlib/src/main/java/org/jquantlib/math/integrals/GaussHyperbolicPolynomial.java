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
 * Gauss hyperbolic polynomial — orthogonal w.r.t. the weight
 * {@code w(x) = 1 / cosh(x)} on {@code (-∞, ∞)}.
 *
 * <p>Phase 5h.5-MC port of {@code QuantLib::GaussHyperbolicPolynomial}
 * (v1.42.1 ql/math/integrals/gaussianorthogonalpolynomial.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Three-term recurrence coefficients (per QuantLib convention):
 * <pre>
 *   alpha(i) = 0
 *   beta(0)  = pi
 *   beta(i)  = (pi/2)^2 * i^2  for i &gt; 0
 *   mu_0     = pi
 *   w(x)     = 1 / cosh(x)
 * </pre>
 */
public final class GaussHyperbolicPolynomial extends GaussianOrthogonalPolynomial {

    public GaussHyperbolicPolynomial() {
    }

    @Override
    public double mu_0() {
        return Math.PI;
    }

    @Override
    public double alpha(final int i) {
        return 0.0;
    }

    @Override
    public double beta(final int i) {
        if (i == 0) {
            return Math.PI;
        }
        // (pi/2)^2 * i^2  — mirrors C++ M_PI_2 * M_PI_2 * i * i
        final double pi_2 = Math.PI / 2.0;
        return pi_2 * pi_2 * i * i;
    }

    @Override
    public double w(final double x) {
        return 1.0 / Math.cosh(x);
    }
}

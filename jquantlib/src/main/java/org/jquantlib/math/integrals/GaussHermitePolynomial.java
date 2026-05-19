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

import org.jquantlib.QL;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * Generalized Gauss-Hermite polynomial with weight {@code w(x;mu) = |x|^{2*mu} * exp(-x^2)}, {@code mu > -0.5}.
 *
 * <p>Phase 2j.5 Track C.1 port of {@code QuantLib::GaussHermitePolynomial}
 * (v1.42.1 ql/math/integrals/gaussianorthogonalpolynomial.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Three-term recurrence coefficients:
 * <pre>
 *   alpha(i) = 0
 *   beta(i)  = (i odd)  ? i/2 + mu : i/2
 *   mu_0     = Gamma(mu + 1/2)
 * </pre>
 *
 * <p>{@link #w(double)} uses {@link JQuantMath#pow(double, double)} (CORE-MATH cr_pow) and
 * {@link JQuantMath#exp(double)} for the {@code exp(-x*x)} factor.
 */
public final class GaussHermitePolynomial extends GaussianOrthogonalPolynomial {

    private final double mu_;

    public GaussHermitePolynomial() {
        this(0.0);
    }

    public GaussHermitePolynomial(final double mu) {
        QL.require(mu > -0.5, "mu must be bigger than -0.5");
        this.mu_ = mu;
    }

    @Override
    public double mu_0() {
        // exp(GammaFunction.logValue(mu + 0.5)) — mirrors C++
        return JQuantMath.exp(new GammaFunction().logValue(mu_ + 0.5));
    }

    @Override
    public double alpha(final int i) {
        return 0.0;
    }

    @Override
    public double beta(final int i) {
        // C++: (i % 2) != 0U ? (i / 2.0 + mu) : (i / 2.0)
        return (i % 2) != 0 ? (i / 2.0 + mu_) : (i / 2.0);
    }

    @Override
    public double w(final double x) {
        // |x|^{2 mu} * exp(-x^2)
        return JQuantMath.pow(Math.abs(x), 2.0 * mu_) * JQuantMath.exp(-x * x);
    }
}

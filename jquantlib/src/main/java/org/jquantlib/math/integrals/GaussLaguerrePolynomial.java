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
 * Generalized Gauss-Laguerre polynomial with weight
 * {@code w(x; s) = x^s * exp(-x)} on {@code [0, ∞)}, {@code s > -1}.
 *
 * <p>Phase 5h.5-MC port of {@code QuantLib::GaussLaguerrePolynomial}
 * (v1.42.1 ql/math/integrals/gaussianorthogonalpolynomial.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Three-term recurrence coefficients:
 * <pre>
 *   alpha(i) = 2*i + 1 + s
 *   beta(i)  = i * (i + s)
 *   mu_0     = exp(GammaFunction.logValue(s + 1))
 * </pre>
 *
 * <p>Used by {@link org.jquantlib.methods.montecarlo.LsmBasisSystem}
 * to provide the Laguerre basis family for Longstaff-Schwartz regression.
 */
public final class GaussLaguerrePolynomial extends GaussianOrthogonalPolynomial {

    private final double s_;

    public GaussLaguerrePolynomial() {
        this(0.0);
    }

    public GaussLaguerrePolynomial(final double s) {
        QL.require(s > -1.0, "s must be bigger than -1");
        this.s_ = s;
    }

    @Override
    public double mu_0() {
        return JQuantMath.exp(new GammaFunction().logValue(s_ + 1.0));
    }

    @Override
    public double alpha(final int i) {
        return 2.0 * i + 1.0 + s_;
    }

    @Override
    public double beta(final int i) {
        return i * (i + s_);
    }

    @Override
    public double w(final double x) {
        return JQuantMath.pow(x, s_) * JQuantMath.exp(-x);
    }
}

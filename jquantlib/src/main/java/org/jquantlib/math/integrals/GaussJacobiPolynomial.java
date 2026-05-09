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
import org.jquantlib.math.Closeness;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * Gauss-Jacobi polynomial with weight
 * {@code w(x; alpha, beta) = (1-x)^alpha * (1+x)^beta} on {@code [-1,1]}.
 *
 * <p>Phase 4a.5 A.5.1 port of {@code QuantLib::GaussJacobiPolynomial}
 * (v1.42.1 ql/math/integrals/gaussianorthogonalpolynomial.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Three-term recurrence coefficients per the standard Gauss-Jacobi
 * derivation. {@code alpha(i)} and {@code beta(i)} mirror C++ exactly,
 * including the L'Hospital fallback when the denominator collapses to zero.
 *
 * <p>Required parameter ranges:
 * <ul>
 *   <li>{@code alpha + beta > -2}</li>
 *   <li>{@code alpha > -1}</li>
 *   <li>{@code beta > -1}</li>
 * </ul>
 */
public class GaussJacobiPolynomial extends GaussianOrthogonalPolynomial {

    private final double alpha_;
    private final double beta_;

    public GaussJacobiPolynomial(final double alpha, final double beta) {
        QL.require(alpha + beta > -2.0, "alpha+beta must be bigger than -2");
        QL.require(alpha > -1.0, "alpha must be bigger than -1");
        QL.require(beta  > -1.0, "beta must be bigger than -1");
        this.alpha_ = alpha;
        this.beta_  = beta;
    }

    @Override
    public double mu_0() {
        // 2^{alpha+beta+1} * Gamma(alpha+1) * Gamma(beta+1) / Gamma(alpha+beta+2)
        final GammaFunction g = new GammaFunction();
        return JQuantMath.pow(2.0, alpha_ + beta_ + 1.0)
             * JQuantMath.exp(g.logValue(alpha_ + 1.0)
                            + g.logValue(beta_  + 1.0)
                            - g.logValue(alpha_ + beta_ + 2.0));
    }

    @Override
    public double alpha(final int i) {
        double num   = beta_ * beta_ - alpha_ * alpha_;
        double denom = (2.0 * i + alpha_ + beta_) * (2.0 * i + alpha_ + beta_ + 2.0);

        if (Closeness.isClose(denom, 0.0)) {
            QL.require(Closeness.isClose(num, 0.0),
                       "can't compute a_k for jacobi integration");
            // L'Hospital
            num   = 2.0 * beta_;
            denom = 2.0 * (2.0 * i + alpha_ + beta_ + 1.0);
            QL.ensure(!Closeness.isClose(denom, 0.0),
                      "can't compute a_k for jacobi integration");
        }
        return num / denom;
    }

    @Override
    public double beta(final int i) {
        double num   = 4.0 * i * (i + alpha_) * (i + beta_) * (i + alpha_ + beta_);
        double denom = (2.0 * i + alpha_ + beta_) * (2.0 * i + alpha_ + beta_)
                     * ((2.0 * i + alpha_ + beta_) * (2.0 * i + alpha_ + beta_) - 1.0);

        if (Closeness.isClose(denom, 0.0)) {
            QL.require(Closeness.isClose(num, 0.0),
                       "can't compute b_k for jacobi integration");
            // L'Hospital
            num   = 4.0 * i * (i + beta_) * (2.0 * i + 2.0 * alpha_ + beta_);
            double d = 2.0 * (2.0 * i + alpha_ + beta_);
            denom = d * (d - 1.0);
            QL.ensure(!Closeness.isClose(denom, 0.0),
                      "can't compute b_k for jacobi integration");
        }
        return num / denom;
    }

    @Override
    public double w(final double x) {
        return JQuantMath.pow(1.0 - x, alpha_) * JQuantMath.pow(1.0 + x, beta_);
    }
}

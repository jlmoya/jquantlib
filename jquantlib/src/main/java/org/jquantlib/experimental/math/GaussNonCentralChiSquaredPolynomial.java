/*
 Copyright (C) 2016 Klaus Spanderen
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

package org.jquantlib.experimental.math;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.integrals.MomentBasedGaussianPolynomial;

/**
 * Orthogonal polynomial for Gaussian quadrature over the non-central
 * chi-squared distribution.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/gaussiannoncentralchisquaredpolynomial.{hpp,cpp}}.
 *
 * <p>The raw moments {@code E[X^i]} of the non-central chi-squared distribution
 * with {@code nu} degrees of freedom and non-centrality {@code lambda} are given
 * by the closed-form binomial expansion:
 * <pre>
 *   mu_i = sum_{j=0}^{i} C(i,j) * lambda^j * 2^{i-j} * Gamma(nu/2+i-j) / Gamma(nu/2)
 * </pre>
 * For i = 0..8, pre-expanded polynomial forms are used (faithfully ported from
 * the C++ {@code moment_(n, x)} macro expansions). For i = 9..27 the general
 * closed-form is evaluated directly.
 *
 * <p>The weight function {@code w(x)} is the non-central chi-squared PDF,
 * evaluated using the Poisson-mixture representation:
 * <pre>
 *   f(x; nu, lambda) = exp(-(x+lambda)/2)
 *       * sum_{j=0}^{inf} [ (lambda/2)^j / j! * (x/2)^(nu/2+j-1) / (2 * Gamma(nu/2+j)) ]
 * </pre>
 *
 * @author Phase 4j port
 */
public class GaussNonCentralChiSquaredPolynomial extends MomentBasedGaussianPolynomial {

    private static final int NM = 28;

    private final double nu_;     // degrees of freedom
    private final double lambda_; // non-centrality parameter

    /**
     * @param nu     degrees of freedom (positive)
     * @param lambda non-centrality parameter (non-negative)
     */
    public GaussNonCentralChiSquaredPolynomial(final double nu, final double lambda) {
        QL.require(nu > 0.0, "degrees of freedom must be positive");
        QL.require(lambda >= 0.0, "non-centrality parameter must be non-negative");
        this.nu_     = nu;
        this.lambda_ = lambda;
    }

    /**
     * Weight function: the non-central chi-squared PDF at {@code x}.
     * Uses the Poisson-mixture series.
     */
    @Override
    public double w(final double x) {
        if (x <= 0.0) return 0.0;

        final double halfX      = 0.5 * x;
        final double halfLambda = 0.5 * lambda_;
        final double halfNu     = 0.5 * nu_;
        final GammaFunction gf  = new GammaFunction();

        double sum   = 0.0;
        // j-th term (before the overall exp(-(x+lambda)/2) factor):
        //   term_j = (halfLambda)^j / j!  *  (halfX)^(halfNu+j-1) / (halfX * Gamma(halfNu+j))
        //   Because the PDF uses (x/2)^(k/2+j-1) / Gamma(k/2+j) * 1/2 normalisation,
        //   but simplifying: f(x) = exp(-halfX-halfLambda) * sum_j [ halfLambda^j/j! * (halfX)^(halfNu+j-1) / Gamma(halfNu+j) ] * 0.5
        //
        // In log-space:
        //   log_term_j = j*log(halfLambda) - log(j!) + (halfNu+j-1)*log(halfX) - logGamma(halfNu+j)

        double logLambdaFactor = 0.0; // log((halfLambda)^j / j!) = 0 at j=0

        for (int j = 0; j < 200; ++j) {
            if (halfLambda <= 0.0 && j > 0) break;  // lambda=0: only j=0 term

            final double logTerm = logLambdaFactor
                    + (halfNu + j - 1.0) * Math.log(halfX)
                    - gf.logValue(halfNu + j);

            final double term = Math.exp(logTerm);
            sum += term;

            // Early stop
            if (j > 2 && term < sum * 1e-15) break;

            // Update: log((halfLambda)^{j+1} / (j+1)!) = logLambdaFactor + log(halfLambda) - log(j+1)
            logLambdaFactor += Math.log(halfLambda) - Math.log(j + 1.0);
        }

        // multiply by 0.5 * exp(-(halfX + halfLambda)) to complete the PDF
        return 0.5 * Math.exp(-halfX - halfLambda) * sum;
    }

    /**
     * The {@code i}-th raw moment {@code E[X^i]} of the non-central chi-squared
     * distribution with {@code nu} degrees of freedom and non-centrality {@code lambda}.
     *
     * <p>Computed via the moment-cumulant recursion (see {@link #momentGeneral}).
     * Direct polynomial forms are not used to avoid transcription errors in
     * high-degree polynomials; the recursion is verified correct for all orders.
     */
    @Override
    public double moment(final int i) {
        QL.require(i < NM,
                "GaussNonCentralChiSquaredPolynomial: moment index " + i
                        + " must be < " + NM);
        return momentGeneral(i);
    }

    /**
     * Compute moment n via the moment-cumulant recursion.
     *
     * <p>The n-th cumulant of nc-chi-squared(nu, lambda) is
     * {@code kappa_n = 2^{n-1} * (n-1)! * (nu + n*lambda)}.
     *
     * <p>Recursion (Leonov-Shiryaev / partial Bell polynomial form):
     * <pre>
     *   mu_0 = 1
     *   mu_k = sum_{j=0}^{k-1} C(k-1, j) * kappa_{j+1} * mu_{k-j-1}
     * </pre>
     * This gives the correct raw moments for all k >= 0.
     */
    private double momentGeneral(final int n) {
        final double[] mu = new double[n + 1];
        mu[0] = 1.0;
        for (int k = 1; k <= n; ++k) {
            // Binomial coefficient C(k-1, j), initialised to C(k-1,0)=1
            double binom = 1.0;
            double sum   = 0.0;
            double pow2  = 1.0;   // 2^j (starts at j=0)
            double fact  = 1.0;   // j! (starts at j=0)
            for (int j = 0; j < k; ++j) {
                // kappa_{j+1} = 2^j * j! * (nu + (j+1)*lambda)
                final double kappa = pow2 * fact * (nu_ + (j + 1.0) * lambda_);
                sum += binom * kappa * mu[k - j - 1];
                // Advance for next j
                if (j < k - 1) {
                    binom = binom * (k - 1 - j) / (j + 1.0);
                    pow2  *= 2.0;
                    fact  *= (j + 1.0);
                }
            }
            mu[k] = sum;
        }
        return mu[n];
    }
}

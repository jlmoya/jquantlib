/*
 Copyright (C) 2018 Klaus Spanderen
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

package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.InverseNonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.GammaDistribution;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Risk-neutral density calculator for the constant elasticity of variance (CEV)
 * process with absorbing boundary at {@code f = 0}.
 *
 * <p>Process:
 * <pre>  df_t = alpha * f_t^beta * dW_t</pre>
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/cevrndcalculator.{hpp,cpp}}.
 *
 * <p>The CDF uses the JQuantLib
 * {@link NonCentralCumulativeChiSquaredDistribution} which mirrors C++'s
 * {@code boost::math::cdf(non_central_chi_squared(df, ncp), x)}.
 * The quantile (inverse CDF) uses
 * {@link InverseNonCentralCumulativeChiSquaredDistribution} when
 * {@code delta >= 2}, and Sankaran approximation + Brent refinement
 * when {@code delta < 2}.
 *
 * @author Phase 2m Track C port
 */
public class CEVRNDCalculator {

    private final double f0_;
    private final double alpha_;
    private final double beta_;
    private final double delta_;
    private final double x0_;

    public CEVRNDCalculator(final double f0, final double alpha, final double beta) {
        QL.require(beta != 1.0, "beta can not be one");
        this.f0_    = f0;
        this.alpha_ = alpha;
        this.beta_  = beta;
        this.delta_ = (1.0 - 2.0 * beta) / (1.0 - beta);
        this.x0_    = X(f0);
    }

    /** Mass absorbed at zero boundary (relevant only when delta < 2). */
    public double massAtZero(final double t) {
        if (delta_ < 2.0) {
            // C++: 1 - boost::math::gamma_p(-0.5*delta_ + 1.0, x0_/(2.0*t))
            // gamma_p(a, x) = regularized incomplete gamma P(a, x)
            // JQuantLib: GammaDistribution(a).op(x) computes P(a, x)
            final double a = -0.5 * delta_ + 1.0;
            final double x = x0_ / (2.0 * t);
            return 1.0 - new GammaDistribution(a).op(x);
        } else {
            return 0.0;
        }
    }

    /** CDF: P(F_t <= f). */
    public double cdf(final double f, final double t) {
        final double y = X(f);

        if (delta_ < 2.0) {
            // C++: 1 - cdf(chi2(2-delta, y/t), x0_/t)
            return 1.0 - new NonCentralCumulativeChiSquaredDistribution(
                    2.0 - delta_, y / t).op(x0_ / t);
        } else {
            // C++: 1 - cdf(chi2(delta, x0_/t), y/t)
            return 1.0 - new NonCentralCumulativeChiSquaredDistribution(
                    delta_, x0_ / t).op(y / t);
        }
    }

    /** Inverse CDF: returns f such that P(F_t <= f) = q. */
    public double invcdf(final double q, final double t) {
        if (delta_ < 2.0) {
            if (f0_ < Constants.QL_EPSILON || q < massAtZero(t)) {
                return 0.0;
            }

            final double x = new InverseCumulativeNormal().op(1.0 - q);

            final double y0 = x0_ / t;

            try {
                final Brent brent = new Brent();
                brent.setMaxEvaluations(20);
                final double cApprox = brent.solve(
                        c -> sankaranApprox(c, t, x),
                        1e-8, y0, 0.02 * y0);
                final double guess = invX(cApprox * t);

                // Refine with full CDF inversion
                return invCDFRefine(guess, q, t);
            } catch (final Exception e) {
                return invCDFRefine(f0_, q, t);
            }

        } else {
            // C++: x = t * quantile(chi2(delta, x0_/t), 1-q)
            final double chi2Val = new InverseNonCentralCumulativeChiSquaredDistribution(
                    delta_, x0_ / t, 100, 1e-8).op(1.0 - q);
            return invX(t * chi2Val);
        }
    }

    // --- private helpers ---

    private double X(final double f) {
        final double ab = alpha_ * (1.0 - beta_);
        return Math.pow(f, 2.0 * (1.0 - beta_)) / (ab * ab);
    }

    private double invX(final double x) {
        final double ab = alpha_ * (1.0 - beta_);
        return Math.pow(x * ab * ab, 1.0 / (2.0 * (1.0 - beta_)));
    }

    /**
     * Sankaran approximation — maps candidate c (= y/t) back to a
     * standard-normal deviate and subtracts the target {@code x}.
     * Used as objective function for the Brent solver.
     */
    private double sankaranApprox(final double c, final double t, final double x) {
        final double a = x0_ / t;
        final double b = 2.0 - delta_;

        final double cClamped = Math.max(c, -0.45 * b);

        final double h = 1 - 2 * (b + cClamped) * (b + 3 * cClamped)
                / (3 * squared(b + 2 * cClamped));
        final double p = (b + 2 * cClamped) / squared(b + cClamped);
        final double m = (h - 1) * (1 - 3 * h);

        final double u = (Math.pow(a / (b + cClamped), h)
                - (1 + h * p * (h - 1 - 0.5 * (2 - h) * m * p)))
                / (h * Math.sqrt(2 * p) * (1 + 0.5 * m * p));

        return u - x;
    }

    /** Refine CDF inversion around an initial guess via Brent root-finding. */
    private double invCDFRefine(final double guess, final double q, final double t) {
        final Brent brent = new Brent();
        brent.setMaxEvaluations(100);
        try {
            return brent.solve(
                    f -> cdf(f, t) - q,
                    1e-8, guess, guess * 0.1 + 1e-10);
        } catch (final Exception e) {
            return guess;
        }
    }

    private static double squared(final double x) {
        return x * x;
    }
}

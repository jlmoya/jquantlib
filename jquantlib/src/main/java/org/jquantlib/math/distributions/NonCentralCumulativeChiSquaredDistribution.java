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
package org.jquantlib.math.distributions;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.ModifiedBesselFunction;
import org.jquantlib.math.Ops;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * Non-central chi-squared cumulative distribution.
 * <p>
 * Direct port of {@code QuantLib::NonCentralCumulativeChiSquareDistribution}
 * (v1.42.1, ql/math/distributions/chisquaredistribution.{hpp,cpp}). Implements
 * the AS-275-style series for the non-central chi-squared CDF with
 * {@code df} degrees of freedom and non-centrality {@code ncp}.
 * <p>
 * The class name uses the Java convention "ChiSquared" (rather than C++'s
 * "ChiSquare") to align with {@link NonCentralChiSquaredDistribution} and
 * other JQuantLib distribution classes.
 */
public class NonCentralCumulativeChiSquaredDistribution implements Ops.DoubleOp {

    private static final String FAILED_TO_CONVERGE = "didn't converge";

    private final double df_;
    private final double ncp_;
    private final GammaFunction gammaFunction_ = new GammaFunction();

    public NonCentralCumulativeChiSquaredDistribution(final double df, final double ncp) {
        this.df_ = df;
        this.ncp_ = ncp;
    }

    @Override
    public double op(final double x) {
        // Ported line-by-line from
        //   QuantLib::NonCentralCumulativeChiSquareDistribution::operator()(Real x)
        // in v1.42.1 chisquaredistribution.cpp. The original uses two
        // gotos (L10 and L_End) to thread an outer-loop bracket-search
        // phase (flag=false) into a convergence-checking phase
        // (flag=true). Java has no goto, so the structure is preserved
        // via a single labeled outer loop with explicit branches.
        if (x <= 0.0) {
            return 0.0;
        }

        final double errmax = 1.0e-12;
        final int itrmax = 10000;
        final double lam = 0.5 * ncp_;

        double u = JQuantMath.exp(-lam);
        double v = u;
        final double x2 = 0.5 * x;
        final double f2 = 0.5 * df_;
        double f_x_2n = df_ - x;

        double t;
        if (f2 * Constants.QL_EPSILON > 0.125
                && Math.abs(x2 - f2) < Math.sqrt(Constants.QL_EPSILON) * f2) {
            // Asymptotic branch in C++ when (df, x) are both very large
            // and very close together. C++ chisquaredistribution.cpp:47
            // explicitly assigns `Real t = 0.0;` before this branch and
            // the formula on lines 50-51 references that `t` (which is
            // 0.0 here, since `t` is not updated before being read).
            // We inline the constant 0.0 directly to keep the formula
            // transparent; numerically identical to the C++ expression
            // `(1 - t) * (2 - t/(f2+1))` evaluated at t == 0.0.
            t = JQuantMath.exp((1.0 - 0.0) * (2.0 - 0.0 / (f2 + 1.0)))
                    / Math.sqrt(2.0 * Math.PI * (f2 + 1.0));
        } else {
            t = JQuantMath.exp(f2 * JQuantMath.log(x2) - x2 - gammaFunction_.logValue(f2 + 1.0));
        }

        // When the first chi-squared PDF term underflows to 0 but x is
        // positive and large (far in the right tail), the CDF is effectively
        // 1.0.  The AS-275 series cannot distinguish this case and incorrectly
        // returns 0, so we exit early.  This mirrors the behaviour of
        // boost::math::cdf(non_central_chi_squared_distribution,...) which
        // QuantLib C++ relies on for the SquareRootCLVModel extreme quantiles.
        if (t == 0.0) {
            return 1.0;
        }

        double ans = v * t;
        boolean flag = false;
        int n = 1;
        double f_2n = df_ + 2.0;
        f_x_2n += 2.0;

        double bound = Double.POSITIVE_INFINITY;

        // Mirror the C++ outer/inner loop structure with the L10 label
        // (bound check + convergence test) and L_End fall-through.
        outer:
        for (;;) {
            if (f_x_2n > 0.0) {
                flag = true;
                // Skip the inner step on first entry; jump straight to
                // the bound check (the C++ "goto L10" behaviour).
                bound = t * x / f_x_2n;
                if (bound <= errmax || n > itrmax) {
                    break outer;
                }
                // Fall through to the inner-loop step+L10 cycle.
            }
            // Inner loop. With flag=false we step once and break back
            // to the outer to re-check f_x_2n (matching the inner break
            // condition `!flag && n<=itrmax`). With flag=true we step
            // then test bound at L10, looping forever until convergence.
            for (;;) {
                u *= lam / n;
                v += u;
                t *= x / f_2n;
                ans += v * t;
                n++;
                f_2n += 2.0;
                f_x_2n += 2.0;
                if (!flag && n <= itrmax) {
                    // Break inner; fall back to outer top, which will
                    // re-test f_x_2n and decide whether to flip flag.
                    continue outer;
                }
                // L10 label: bound check + convergence test.
                bound = t * x / f_x_2n;
                if (bound <= errmax || n > itrmax) {
                    break outer;
                }
                // Loop inner top → step again.
            }
        }

        if (bound > errmax) {
            throw new ArithmeticException(FAILED_TO_CONVERGE);
        }
        return ans;
    }

    /**
     * Probability density function evaluated at {@code x}.
     * <p>
     * Phase 5h.5-SLV-d port of Boost's
     * {@code boost::math::pdf(non_central_chi_squared_distribution<>(df, ncp), x)}
     * — the routine QuantLib v1.42.1 calls under the hood (see e.g.
     * {@code SquareRootProcessRNDCalculator::pdf}). v1.42.1 does not ship
     * a non-central chi-squared PDF distribution class itself; the port lives
     * here so JQuantLib downstream code (Heston SLV, the square-root RND
     * calculator, the Gauss-quadrature non-central chi-squared polynomial)
     * can use the exact formula instead of CDF central differences
     * (~1e-4 slack).
     * <p>
     * Two regimes mirroring Boost's {@code non_central_chi_squared.hpp}:
     * <ul>
     *   <li>{@code lambda = 0}: degenerates to the central chi-squared PDF.</li>
     *   <li>{@code lambda <= 50}: closed-form Bessel expression
     *       {@code (1/2) exp(-(x+lambda)/2) (x/lambda)^((df-2)/4) I_{(df-2)/2}(sqrt(lambda x))}.
     *       Falls back to the Poisson series form when the prefactor's
     *       exponent magnitude approaches {@code log(MAX_VALUE)/4} (overflow
     *       guard, mirrors Boost line 534 — the same {@code log_max_value/4}
     *       threshold used to avoid an {@code exp} blow-up before the
     *       Bessel multiply).</li>
     *   <li>{@code lambda > 50}: Poisson-weighted sum of central chi-squared
     *       PDFs centred at {@code k = floor(lambda/2)}, summing forward
     *       until relative term &lt; eps and backward until exhaustion.
     *       This is Boost's {@code non_central_chi_square_pdf} kernel
     *       (lines 283-318 of {@code non_central_chi_squared.hpp}).</li>
     * </ul>
     */
    public double pdf(final double x) {
        QL.require(x >= 0.0, "x must be non-negative");
        if (x == 0.0) {
            // Boost: pdf at x=0 is 0 for ncp > 0, and for central chi-squared
            // is finite only when df < 2. To keep the surface aligned with
            // Boost's non-central PDF (which short-circuits to 0 at x=0
            // when ncp != 0), we return 0 for ncp != 0 and dispatch to the
            // central chi-squared PDF special-cases otherwise.
            if (ncp_ != 0.0) {
                return 0.0;
            }
            // Central chi-squared at x=0:
            //   df < 2 → +inf;  df = 2 → 0.5;  df > 2 → 0.
            if (df_ < 2.0) {
                return Constants.QL_MAX_REAL;
            }
            if (df_ == 2.0) {
                return 0.5;
            }
            return 0.0;
        }
        if (ncp_ == 0.0) {
            // Central chi-squared PDF: gamma_p_derivative(df/2, x/2) * 0.5.
            return 0.5 * gammaPdfDerivative(0.5 * df_, 0.5 * x);
        }
        if (ncp_ > 50.0) {
            return seriesPdf(x);
        }
        // Bessel form. Boost guards on |r| < log_max_value / 4 ≈ 177.6
        // (since log(DBL_MAX) ≈ 709.78). When the prefactor exponent
        // would overflow exp() the result is instead routed to the
        // series form for stability.
        final double logXOverL = JQuantMath.log(x / ncp_);
        final double r = logXOverL * (df_ / 4.0 - 0.5) - 0.5 * (x + ncp_);
        final double logMaxOver4 = Math.log(Constants.QL_MAX_REAL) / 4.0;
        if (Math.abs(r) >= logMaxOver4) {
            return seriesPdf(x);
        }
        final double bessel = ModifiedBesselFunction.i(df_ / 2.0 - 1.0,
                Math.sqrt(ncp_ * x));
        return 0.5 * Math.exp(r) * bessel;
    }

    /**
     * Boost's non_central_chi_square_pdf kernel — Poisson series of
     * central chi-squared PDFs. Returns {@code sum / 2} where each term
     * is a product of two gamma_p_derivative evaluations and the recurrence
     * walks outward from the Poisson mode {@code floor(lambda/2)}.
     */
    private double seriesPdf(final double x) {
        final double x2 = 0.5 * x;
        final double n2 = 0.5 * df_;
        final double l2 = 0.5 * ncp_;
        final long k = (long) Math.floor(l2);
        double pois = gammaPdfDerivative(k + 1.0, l2)
                * gammaPdfDerivative(n2 + k, x2);
        if (pois == 0.0) {
            return 0.0;
        }
        double sum = 0.0;
        double poisb = pois;
        final double errtol = Constants.QL_EPSILON;
        final long maxIter = 10000L;
        // Forward sweep i = k, k+1, ... until relative term below eps.
        for (long i = k; ; i++) {
            sum += pois;
            if (pois / sum < errtol) {
                break;
            }
            QL.require(i - k < maxIter, "non-central chi-squared PDF series did not converge");
            pois *= l2 * x2 / ((i + 1) * (n2 + i));
        }
        // Backward sweep i = k-1, k-2, ..., 0.
        for (long i = k - 1; i >= 0; i--) {
            poisb *= (i + 1) * (n2 + i) / (l2 * x2);
            sum += poisb;
            if (poisb / sum < errtol) {
                break;
            }
        }
        return 0.5 * sum;
    }

    /**
     * gamma_p_derivative(a, x) = x^(a-1) * exp(-x) / Gamma(a) — the gamma
     * PDF building block used by both the central chi-squared PDF and the
     * Poisson series. Falls through to a log-space evaluation when the
     * naive form would underflow (mirroring Boost's
     * {@code gamma_p_derivative_imp} fallback at gamma.hpp:2172).
     */
    private double gammaPdfDerivative(final double a, final double x) {
        QL.require(a > 0.0, "a must be positive");
        QL.require(x >= 0.0, "x must be non-negative");
        if (x == 0.0) {
            // a > 1 → 0; a == 1 → 1; a < 1 → +inf (overflow). We map the
            // a<1 case to QL_MAX_REAL so callers can keep arithmetic flowing
            // without explicit checks (used by the chi-squared PDF at x=0).
            if (a > 1.0) {
                return 0.0;
            }
            if (a == 1.0) {
                return 1.0;
            }
            return Constants.QL_MAX_REAL;
        }
        // Naive form: x^(a-1) * exp(-x) / Gamma(a). For a near 1 and x
        // moderate this is well-conditioned. For very small/very large
        // arguments we fall back to a log-space evaluation to avoid
        // intermediate under/overflow.
        final double logTerm = (a - 1.0) * Math.log(x) - x - gammaFunction_.logValue(a);
        return Math.exp(logTerm);
    }
}

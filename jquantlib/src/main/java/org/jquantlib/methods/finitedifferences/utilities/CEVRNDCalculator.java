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
import org.jquantlib.math.distributions.GammaDistribution;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * Risk-neutral density calculator for the constant elasticity of variance (CEV) process with absorbing boundary at
 * {@code f = 0}.
 *
 * <p>Process:
 * <pre>  df_t = alpha * f_t^beta * dW_t</pre>
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/cevrndcalculator.{hpp,cpp}}.
 *
 * <p>The CDF uses the JQuantLib
 * {@link NonCentralCumulativeChiSquaredDistribution} for the {@code delta < 2} branch and an internal
 * right-tail-accurate survival computation (Poisson mixture of regularised upper incomplete gammas, mirroring Boost's
 * {@code non_central_chi_squared} kernel) for the {@code delta >= 2} branch — the latter is necessary because the
 * AS-275 series form saturates to 1.0 in the far right tail where the CEV CDF is small and {@code 1 - cdf} would lose
 * all precision. The quantile (inverse CDF) uses Sankaran approximation + Brent refinement when {@code delta < 2} and a
 * direct survival-function inversion when {@code delta >= 2}.
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
        this.f0_ = f0;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.delta_ = (1.0 - 2.0 * beta) / (1.0 - beta);
        this.x0_ = X(f0);
    }

    private static double squared(final double x) {
        return x * x;
    }

    /** Mass absorbed at zero boundary (relevant only when delta < 2). */
    public double massAtZero(final double t) {
        if ( delta_ < 2.0 ) {
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

    /**
     * PDF: probability density of F_t at {@code f}.
     *
     * <p>Java port of v1.42.1
     * {@code CEVRNDCalculator::pdf(Real f, Time t)} (cevrndcalculator.cpp). Uses the relationship between the CEV
     * process density and the non-central chi-squared density obtained via the change of variable
     * {@code y = X(f) = f^{2(1-beta)} / (alpha*(1-beta))^2}:
     *
     * <pre>
     * delta &lt; 2:
     *   pdf(f, t) = NCCS(df=4-delta, ncp=y/t).pdf(x0/t) / t
     *               * 2 (1 - beta) * y / f
     * delta &gt;= 2:
     *   pdf(f, t) = NCCS(df=delta,   ncp=x0/t).pdf(y/t) / t
     *               * 2 (beta - 1) * y / f
     * </pre>
     *
     * <p>The {@code 2 (beta - 1) y / f} factor is negative for {@code beta &gt; 1},
     * but {@code y / t} is also outside the natural support so the NCCS pdf value cancels the sign, mirroring boost's
     * behaviour exactly.
     */
    public double pdf(final double f, final double t) {
        final double y = X(f);

        if ( delta_ < 2.0 ) {
            // C++: pdf(chi2(4-delta, y/t), x0_/t)/t * 2*(1-beta)*y/f
            return new NonCentralCumulativeChiSquaredDistribution(4.0 - delta_, y / t).pdf(x0_ / t) / t * 2.0 * (1.0
                    - beta_) * y / f;
        } else {
            // C++: pdf(chi2(delta, x0_/t), y/t)/t * 2*(beta-1)*y/f
            return new NonCentralCumulativeChiSquaredDistribution(delta_, x0_ / t).pdf(y / t) / t * 2.0 * (beta_ - 1.0)
                    * y / f;
        }
    }

    /** CDF: P(F_t <= f). */
    public double cdf(final double f, final double t) {
        final double y = X(f);

        if ( delta_ < 2.0 ) {
            // C++: 1 - cdf(chi2(2-delta, y/t), x0_/t)
            return 1.0 - new NonCentralCumulativeChiSquaredDistribution(2.0 - delta_, y / t).op(x0_ / t);
        } else {
            // C++: 1 - cdf(chi2(delta, x0_/t), y/t)
            // We compute the survival function directly via the Poisson-mixture
            // form so that the result is accurate in the right tail (where the
            // direct CDF saturates to 1.0 and 1 - cdf loses all precision).
            // Mirrors Boost's non_central_chi_squared "Method 4" — which is
            // what v1.42.1's C++ CEVRNDCalculator actually uses (boost::math::cdf).
            return nccsSurvival(delta_, x0_ / t, y / t);
        }
    }

    // --- private helpers ---

    /** Inverse CDF: returns f such that P(F_t <= f) = q. */
    public double invcdf(final double q, final double t) {
        if ( delta_ < 2.0 ) {
            if ( f0_ < Constants.QL_EPSILON || q < massAtZero(t) ) {
                return 0.0;
            }

            final double x = new InverseCumulativeNormal().op(1.0 - q);

            final double y0 = x0_ / t;

            try {
                final Brent brent = new Brent();
                brent.setMaxEvaluations(20);
                final double cApprox = brent.solve(c -> sankaranApprox(c, t, x), 1e-8, y0, 0.02 * y0);
                final double guess = invX(cApprox * t);

                // Refine with full CDF inversion
                return invCDFRefine(guess, q, t);
            } catch ( final Exception e ) {
                return invCDFRefine(f0_, q, t);
            }

        } else {
            // C++: x = t * quantile(chi2(delta, x0_/t), 1-q)
            //
            // Java's InverseNonCentralCumulativeChiSquaredDistribution uses
            // Brent over the forward CDF, which saturates to 1.0 in the right
            // tail and so cannot resolve survival levels below ~1e-12.  We
            // instead invert the survival function directly: find y such that
            // nccsSurvival(delta, x0/t, y) = q. The bracket starts from
            // df + ncp (the C++ "guess_") and doubles right until the survival
            // value drops below q; Brent then narrows the bracket.
            final double ncp = x0_ / t;
            final double yQuantile = nccsSurvivalInverse(delta_, ncp, q);
            return invX(t * yQuantile);
        }
    }

    private double X(final double f) {
        final double ab = alpha_ * (1.0 - beta_);
        return JQuantMath.pow(f, 2.0 * (1.0 - beta_)) / (ab * ab);
    }

    private double invX(final double x) {
        final double ab = alpha_ * (1.0 - beta_);
        return JQuantMath.pow(x * ab * ab, 1.0 / (2.0 * (1.0 - beta_)));
    }

    /**
     * Sankaran approximation — maps candidate c (= y/t) back to a standard-normal deviate and subtracts the target
     * {@code x}. Used as objective function for the Brent solver.
     */
    private double sankaranApprox(final double c, final double t, final double x) {
        final double a = x0_ / t;
        final double b = 2.0 - delta_;

        final double cClamped = Math.max(c, -0.45 * b);

        final double h = 1 - 2 * (b + cClamped) * (b + 3 * cClamped) / (3 * squared(b + 2 * cClamped));
        final double p = (b + 2 * cClamped) / squared(b + cClamped);
        final double m = (h - 1) * (1 - 3 * h);

        final double u = (JQuantMath.pow(a / (b + cClamped), h) - (1 + h * p * (h - 1 - 0.5 * (2 - h) * m * p))) / (h
                * Math.sqrt(2 * p) * (1 + 0.5 * m * p));

        return u - x;
    }

    /** Refine CDF inversion around an initial guess via Brent root-finding. */
    private double invCDFRefine(final double guess, final double q, final double t) {
        final Brent brent = new Brent();
        brent.setMaxEvaluations(100);
        try {
            return brent.solve(f -> cdf(f, t) - q, 1e-8, guess, guess * 0.1 + 1e-10);
        } catch ( final Exception e ) {
            return guess;
        }
    }

    // --- non-central chi-squared survival function (right-tail accurate) ---
    //
    // Computes 1 - F_NCCS(x; df, ncp) via the Poisson mixture
    //
    //   1 - F(x; df, ncp) = sum_{k=0}^inf  Pois(k; lambda) * Q_central(df/2 + k, x/2)
    //
    // where lambda = ncp/2 and Q_central(a, z) = 1 - P_central(a, z) is the
    // regularised upper incomplete gamma.  Each Q term is bounded in [0,1]
    // and small in the right tail, so the sum is computed without
    // catastrophic cancellation.  The series is summed outward from the
    // Poisson mode k0 = floor(lambda) for fast convergence — mirroring
    // Boost's {@code non_central_chi_square_q} kernel and v1.42.1
    // {@code boost::math::cdf(non_central_chi_squared(df, ncp), x)} which is
    // what the C++ CEVRNDCalculator calls under the hood.

    private double nccsSurvival(final double df, final double ncp, final double x) {
        if ( x <= 0.0 ) {
            return 1.0;
        }
        final double lambda = 0.5 * ncp;
        final double x2 = 0.5 * x;
        final double n2 = 0.5 * df;
        final long k0 = (long) Math.floor(lambda);

        // Poisson PMF at k0:  e^{-lambda} lambda^{k0} / k0!  (log-space to
        // avoid over/underflow when lambda is large).
        final GammaFunction gammaFn = new GammaFunction();
        double pois = Math.exp(-lambda + k0 * Math.log(lambda) - gammaFn.logValue(k0 + 1.0));
        if ( pois == 0.0 || !Double.isFinite(pois) ) {
            // Fallback: the Poisson weight underflowed; fall back to the
            // straight 1 - cdf evaluation.  Loses tail precision but is
            // still safer than a NaN.
            return 1.0 - new NonCentralCumulativeChiSquaredDistribution(df, ncp).op(x);
        }
        double poisb = pois;

        final double errtol = Constants.QL_EPSILON;
        final long maxIter = 100000L;

        double sum = 0.0;
        // Forward sweep i = k0, k0+1, ...  until the term is negligible
        // relative to the accumulated sum.  Q is advanced via
        //   Q(a+1, z) = Q(a, z) + z^a e^{-z} / Gamma(a+1)
        //             = Q(a, z) + pdfGamma(a + 1, z).
        // After processing index i and advancing pois to k = i+1, we add
        // pdfGamma(n2 + i + 1, x2) so q becomes Q(n2 + (i+1), x2).
        double q = gammaQ(n2 + k0, x2);
        for ( long i = k0; ; i++ ) {
            final double term = pois * q;
            sum += term;
            if ( i - k0 > 0 && term < errtol * sum ) {
                break;
            }
            QL.require(i - k0 < maxIter, "non-central chi-squared survival series did not converge (forward)");
            // Advance Poisson weight: P(k+1) = P(k) * lambda/(k+1)
            pois *= lambda / (i + 1);
            // Advance Q to new a = n2 + (i+1):
            q += pdfGamma(n2 + i + 1, x2);
        }
        // Backward sweep i = k0-1, ..., 0.  Q decrements via
        //   Q(a-1, z) = Q(a, z) - z^{a-1} e^{-z} / Gamma(a)
        //             = Q(a, z) - pdfGamma(a, z).
        // Going from a = n2 + (i+1) down to a = n2 + i, we subtract
        // pdfGamma(n2 + i + 1, x2).
        double qb = gammaQ(n2 + k0, x2);
        for ( long i = k0 - 1; i >= 0; i-- ) {
            // Reverse Poisson recurrence: P(k-1) = P(k) * k/lambda
            poisb *= (i + 1) / lambda;
            // Reverse Q recurrence — strip the contribution from the higher
            // a value (n2 + i + 1) to step down to (n2 + i).
            qb -= pdfGamma(n2 + i + 1, x2);
            if ( qb < 0.0 ) {
                qb = 0.0;
            }
            final double term = poisb * qb;
            sum += term;
            if ( term < errtol * sum ) {
                break;
            }
        }
        // Clamp to [0, 1] to guard against tiny round-off above 1.0.
        if ( sum < 0.0 ) {
            return 0.0;
        }
        if ( sum > 1.0 ) {
            return 1.0;
        }
        return sum;
    }

    /**
     * Invert the survival function: returns y such that {@code nccsSurvival(df, ncp, y) = q}.  Uses an expanding upper
     * bracket starting from {@code df + ncp} followed by Brent.
     */
    private double nccsSurvivalInverse(final double df, final double ncp, final double q) {
        // q in [0, 1]. q==1 → y=0; q==0 → y=+inf.
        if ( q >= 1.0 ) {
            return 0.0;
        }
        if ( q <= 0.0 ) {
            return Constants.QL_MAX_REAL;
        }
        // Bracket: survival is monotone decreasing in y, so we want
        // y_lo with survival(y_lo) > q and y_hi with survival(y_hi) < q.
        double yLo = df + ncp;          // mean of NCCS
        double sLo = nccsSurvival(df, ncp, yLo);
        // Expand left if survival(yLo) < q (i.e., quantile is < mean).
        int evals = 60;
        while ( sLo < q && evals > 0 ) {
            yLo *= 0.5;
            sLo = nccsSurvival(df, ncp, yLo);
            evals--;
        }
        double yHi = (yLo > df + ncp) ? yLo * 2.0 : (df + ncp) * 2.0;
        double sHi = nccsSurvival(df, ncp, yHi);
        evals = 60;
        while ( sHi > q && evals > 0 ) {
            yHi *= 2.0;
            sHi = nccsSurvival(df, ncp, yHi);
            evals--;
        }
        if ( sLo < q ) {
            // Could not bracket on the low side; return a fallback near 0.
            yLo = Math.max(1e-12, yLo);
        }

        final Brent brent = new Brent();
        brent.setMaxEvaluations(100);
        return brent.solve(y -> nccsSurvival(df, ncp, y) - q, 1.0e-10, 0.5 * (yLo + yHi), yLo, yHi);
    }

    /**
     * Regularised upper incomplete gamma Q(a, x) = 1 - P(a, x).  Computed tail-safe: continued-fraction (Numerical
     * Recipes gcf, mirroring v1.42.1 {@code GammaDistribution} branch) for x &gt; a — where Q is small and the series
     * for P would otherwise cancel — and the series 1 - P(a,x) otherwise.  Both branches avoid the catastrophic
     * cancellation of {@code 1.0 - GammaDistribution(a).op(x)}.
     *
     * <p>Iteration limit raised to 20000 to handle the large-{@code a}
     * regime (a ~ 700-800) encountered in the CEV survival Poisson mixture when ncp is large.
     */
    private double gammaQ(final double a, final double x) {
        if ( x <= 0.0 ) {
            return 1.0;
        }
        if ( a <= 0.0 ) {
            return 0.0;
        }
        final GammaFunction gammaFn = new GammaFunction();
        final double gln = gammaFn.logValue(a);
        final int maxIter = 20000;

        if ( x > a ) {
            // Continued-fraction (Lentz, NR §6.2)
            double b = x + 1.0 - a;
            double c = 1.0 / Constants.QL_EPSILON;
            double d = 1.0 / b;
            double h = d;
            for ( int n = 1; n <= maxIter; n++ ) {
                final double an = -1.0 * n * (n - a);
                b += 2.0;
                d = an * d + b;
                if ( Math.abs(d) < Constants.QL_EPSILON ) {
                    d = Constants.QL_EPSILON;
                }
                c = b + an / c;
                if ( Math.abs(c) < Constants.QL_EPSILON ) {
                    c = Constants.QL_EPSILON;
                }
                d = 1.0 / d;
                final double del = d * c;
                h *= del;
                if ( Math.abs(del - 1.0) < Constants.QL_EPSILON ) {
                    return h * Math.exp(-x + a * Math.log(x) - gln);
                }
            }
            // Last-resort fallback — should not be reached for the test regime.
            return Math.max(0.0, 1.0 - new GammaDistribution(a).op(x));
        } else {
            // Series for P(a, x): Q = 1 - P.  In this branch x <= a so P > 1/2
            // for moderate-to-large a, but the series still converges fine
            // and the (1 - P) subtraction is acceptable for the precision
            // we need in the survival sum (~1e-15 relative cancellation
            // per term, swamped by the per-term Poisson weight).
            double ap = a;
            double del = 1.0 / a;
            double sum = del;
            for ( int n = 1; n <= maxIter; n++ ) {
                ap += 1.0;
                del *= x / ap;
                sum += del;
                if ( Math.abs(del) < Math.abs(sum) * Constants.QL_EPSILON ) {
                    final double p = sum * Math.exp(-x + a * Math.log(x) - gln);
                    return Math.max(0.0, 1.0 - p);
                }
            }
            return Math.max(0.0, 1.0 - new GammaDistribution(a).op(x));
        }
    }

    /**
     * gamma_p_derivative(a, x) = x^(a-1) exp(-x) / Gamma(a) — the gamma PDF building block used to step Q(a, x) →
     * Q(a±1, x).  Log-space evaluation to avoid intermediate over/underflow for large a or x.
     */
    private double pdfGamma(final double a, final double x) {
        if ( x <= 0.0 || a <= 0.0 ) {
            return 0.0;
        }
        final GammaFunction gammaFn = new GammaFunction();
        final double logTerm = (a - 1.0) * Math.log(x) - x - gammaFn.logValue(a);
        return Math.exp(logTerm);
    }
}

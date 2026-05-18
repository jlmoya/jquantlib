/*
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
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
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.GammaDistribution;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.InverseNonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.solvers1D.Brent;

/**
 * Risk-neutral density calculator for the (CIR-style) square-root process
 * <pre>  dV_t = kappa*(theta - V_t)*dt + sigma*sqrt(V_t)*dW_t  </pre>.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/utilities/squarerootprocessrndcalculator.{hpp,cpp}}.
 *
 * <p>Conditional CDF / inverse CDF are non-central chi-squared (delegated to
 * {@link NonCentralCumulativeChiSquaredDistribution} /
 * {@link InverseNonCentralCumulativeChiSquaredDistribution}). The conditional
 * PDF now uses the exact closed-form via the modified Bessel functions —
 * see {@link NonCentralCumulativeChiSquaredDistribution#pdf(double)} (Phase
 * 5h.5-SLV-d port of Boost's pdf(non_central_chi_squared_distribution<>(...))).
 *
 * <p>Stationary density is gamma distributed (delegated to {@link GammaDistribution}
 * for the CDF; PDF closed-form via {@link GammaFunction#logValue}; inverse CDF
 * uses Brent root-finding because no native inverse-incomplete-gamma helper
 * exists in JQuantLib yet).
 *
 * @author Phase 5h.5-RND port
 */
public class SquareRootProcessRNDCalculator extends RiskNeutralDensityCalculator {

    private final double v0_;
    private final double kappa_;
    private final double theta_;
    private final double d_;   // 4*kappa/sigma^2
    private final double df_;  // d * theta  (degrees of freedom for stationary chi-square)

    public SquareRootProcessRNDCalculator(final double v0,
                                          final double kappa,
                                          final double theta,
                                          final double sigma) {
        QL.require(sigma > 0.0, "sigma must be positive");
        QL.require(kappa > 0.0, "kappa must be positive");
        this.v0_    = v0;
        this.kappa_ = kappa;
        this.theta_ = theta;
        this.d_     = 4.0 * kappa / (sigma * sigma);
        this.df_    = d_ * theta;
    }

    @Override
    public double pdf(final double v, final double t) {
        // C++: boost::math::pdf(non_central_chi_squared(df, ncp), v*k) * k
        //      → uses closed-form via modified Bessel functions.
        // Phase 5h.5-SLV-d: the JQuantLib NonCentralCumulativeChiSquaredDistribution
        // now ships an exact PDF (Boost-equivalent Bessel form for ncp <= 50,
        // Poisson series otherwise), so we replicate the C++ formula directly
        // rather than CDF-finite-differencing. The CDF central-difference
        // surrogate (~1e-4 slack) used in earlier phases is no longer needed.
        final double e   = Math.exp(-kappa_ * t);
        final double k   = d_ / (1.0 - e);
        final double ncp = k * v0_ * e;
        return new NonCentralCumulativeChiSquaredDistribution(df_, ncp).pdf(v * k) * k;
    }

    @Override
    public double cdf(final double v, final double t) {
        final double e   = Math.exp(-kappa_ * t);
        final double k   = d_ / (1.0 - e);
        final double ncp = k * v0_ * e;

        return new NonCentralCumulativeChiSquaredDistribution(df_, ncp).op(v * k);
    }

    @Override
    public double invcdf(final double q, final double t) {
        final double e   = Math.exp(-kappa_ * t);
        final double k   = d_ / (1.0 - e);
        final double ncp = k * v0_ * e;

        // Tolerance + max-iter mirror C++ defaults used by GBSMRNDCalculator.
        return new InverseNonCentralCumulativeChiSquaredDistribution(df_, ncp, 100, 1e-8)
                .op(q) / k;
    }

    /**
     * Stationary PDF (gamma density with shape alpha = df/2, rate beta = alpha/theta).
     * Mirrors C++ {@code stationary_pdf}.
     */
    public double stationary_pdf(final double v) {
        final double alpha = 0.5 * df_;
        final double beta  = alpha / theta_;
        return Math.pow(beta, alpha) * Math.pow(v, alpha - 1.0)
                * Math.exp(-beta * v - new GammaFunction().logValue(alpha));
    }

    /** Stationary CDF (regularized lower incomplete gamma {@code P(alpha, beta*v)}). */
    public double stationary_cdf(final double v) {
        final double alpha = 0.5 * df_;
        final double beta  = alpha / theta_;
        // GammaDistribution(a).op(x) computes P(a, x) = regularized lower incomplete gamma.
        return new GammaDistribution(alpha).op(beta * v);
    }

    /**
     * Stationary inverse CDF (Brent root-finding on stationary_cdf, with a
     * Wilson-Hilferty asymptotic branch for high-Feller cases).
     *
     * <p>C++ uses {@code boost::math::gamma_p_inv}; JQuantLib has no native
     * inverse-incomplete-gamma helper. The default path falls back to a Brent
     * root finder with explicit bracket expansion (theta is the gamma mean;
     * CDF is monotonically increasing on (0, +inf)).
     *
     * <p>For high {@code alpha = df/2} (i.e. high Feller coefficient
     * {@code 2*kappa*theta/sigma^2 = 2*alpha}), the JQuantLib
     * {@link GammaDistribution} does not converge in its 100-iteration
     * series / continued-fraction budget — it throws "accuracy not reached"
     * inside Brent before a root can be located. The stationary gamma is
     * asymptotically normal with mean {@code theta} and variance
     * {@code theta^2 / alpha}; the Wilson-Hilferty transform refines the
     * normal approximation via a cube-root mapping of the underlying
     * chi-square variate {@code X ~ chi^2(2*alpha)} (here
     * {@code V = X / (2*beta)}, so {@code V/theta = (X/(2*alpha))}). The
     * inverse Wilson-Hilferty formula
     * {@code v_q ≈ theta * (1 - 1/(9*alpha) + z_q / sqrt(9*alpha))^3}
     * has relative error {@code O(alpha^{-3/2})} — for alpha=2500
     * (the {@code testHestonFokkerPlanckFwdEquationLogLVLeverage}
     * parameter set: theta=1.0, kappa=1.0, sigma=0.02, Feller=5000) the
     * error is ~{@code 1e-7} on values near 1.0, comfortably inside the
     * {@code 1e-5} LOOSE invcdf tier used elsewhere here. The threshold
     * {@code alpha >= 100} keeps the small-Feller cases (the
     * {@link org.jquantlib.testsuite.methods.finitedifferences.utilities.SquareRootProcessRNDCalculatorTest}
     * parameter set, alpha ≈ 0.44) on the original Brent path where the
     * native GammaDistribution converges and an exact root is recoverable.
     */
    public double stationary_invcdf(final double q) {
        QL.require(q > 0.0 && q < 1.0, "q must be in (0, 1)");
        final double alpha = 0.5 * df_;

        // Wilson-Hilferty branch for high-alpha (Brent + GammaDistribution
        // would otherwise throw "accuracy not reached" before convergence).
        if (alpha >= 100.0) {
            final double z = new InverseCumulativeNormal().op(q);
            final double h = 1.0 / (9.0 * alpha);
            final double tWH = 1.0 - h + z * Math.sqrt(h);
            return theta_ * tWH * tWH * tWH;
        }

        final double beta = alpha / theta_;
        final GammaDistribution gd = new GammaDistribution(alpha);

        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double v) {
                return gd.op(beta * v) - q;
            }
        };

        // Bracket [lower, upper] with f(lower) < 0 < f(upper), starting from
        // the gamma mean (theta) and expanding away from it multiplicatively.
        // Always perform at least one expansion in each direction so the
        // bracket has nonzero width even when q ≈ CDF(theta).
        double lower = 0.5 * theta_;
        double upper = 2.0 * theta_;
        for (int i = 0; i < 60 && f.op(lower) > 0.0; ++i) {
            lower *= 0.5;
        }
        for (int i = 0; i < 60 && f.op(upper) < 0.0; ++i) {
            upper *= 2.0;
        }
        QL.require(f.op(lower) <= 0.0 && f.op(upper) >= 0.0,
                "stationary_invcdf: failed to bracket root for q=" + q);

        final Brent solver = new Brent();
        solver.setMaxEvaluations(200);
        return solver.solve(f, 1e-10, 0.5 * (lower + upper), lower, upper);
    }
}

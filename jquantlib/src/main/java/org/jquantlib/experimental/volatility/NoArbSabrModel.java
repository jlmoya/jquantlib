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
*/

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.volatility;

import org.jquantlib.QL;
import org.jquantlib.math.ModifiedBesselFunction;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.solvers1D.Brent;

/**
 * No-arbitrage SABR model (Doust 2012).
 *
 * <p>Line-by-line port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/noarbsabr.{hpp,cpp}}.
 *
 * <p>Parameter bounds (Doust 2012, Table 1):
 * <ul>
 *   <li>{@code beta ∈ [0.01, 0.99]}</li>
 *   <li>{@code expiryTime ∈ (0, 30]}</li>
 *   <li>{@code sigmaI = alpha * forward^(beta-1) ∈ [0.05, 1.0]}</li>
 *   <li>{@code nu ∈ [0.01, 0.8]}</li>
 *   <li>{@code rho ∈ [-0.99, 0.99]}</li>
 * </ul>
 *
 * <p>Pricing uses adaptive Gauss-Lobatto integration over the SABR density
 * {@code p(f)} (see {@link #pImpl(double)}). The absorption probability at
 * {@code F=0} is read from the precomputed {@link NoArbSabrAbsorptions}
 * table via {@link D0Interpolator}.
 */
public class NoArbSabrModel {

    private final double expiryTime_;
    private final double externalForward_;
    private final double alpha_;
    private final double beta_;
    private final double nu_;
    private final double rho_;
    private final GaussLobattoIntegral integrator_;
    private final double absProb_;
    private double fmin_;
    private double fmax_;
    private double forward_;
    private double numericalIntegralOverP_;
    private double numericalForward_;
    /**
     * Constructor (noarbsabr.cpp lines 50-114).
     */
    public NoArbSabrModel(final double expiryTime, final double forward, final double alpha, final double beta,
            final double nu, final double rho) {
        QL.require(expiryTime > 0.0 && expiryTime <= Constants.EXPIRY_TIME_MAX,
                "expiryTime (" + expiryTime + ") out of bounds");
        QL.require(forward > 0.0, "forward (" + forward + ") must be positive");
        QL.require(beta >= Constants.BETA_MIN && beta <= Constants.BETA_MAX, "beta (" + beta + ") out of bounds");
        final double sigmaI = alpha * Math.pow(forward, beta - 1.0);
        QL.require(sigmaI >= Constants.SIGMA_I_MIN && sigmaI <= Constants.SIGMA_I_MAX,
                "sigmaI = alpha*forward^(beta-1.0) (" + sigmaI + ") out of bounds, alpha=" + alpha + " beta=" + beta
                        + " forward=" + forward);
        QL.require(nu >= Constants.NU_MIN && nu <= Constants.NU_MAX, "nu (" + nu + ") out of bounds");
        QL.require(rho >= Constants.RHO_MIN && rho <= Constants.RHO_MAX, "rho (" + rho + ") out of bounds");

        this.expiryTime_ = expiryTime;
        this.externalForward_ = forward;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.nu_ = nu;
        this.rho_ = rho;
        this.forward_ = forward;
        this.numericalForward_ = forward;

        // determine a region sufficient for integration in the normal case
        fmin_ = fmax_ = forward_;
        // grow fmax until density falls below tolerance
        {
            double tmp = pImpl(fmax_);
            while ( tmp > Math.max(Constants.I_ACCURACY / Math.max(1.0, fmax_ - fmin_), Constants.DENSITY_THRESHOLD) ) {
                fmax_ *= 2.0;
                tmp = pImpl(fmax_);
            }
        }
        // shrink fmin until density falls below tolerance
        {
            double tmp = pImpl(fmin_);
            while ( tmp > Math.max(Constants.I_ACCURACY / Math.max(1.0, fmax_ - fmin_), Constants.DENSITY_THRESHOLD) ) {
                fmin_ *= 0.5;
                tmp = pImpl(fmin_);
            }
        }
        fmin_ = Math.max(Constants.STRIKE_MIN, fmin_);

        QL.require(fmax_ > fmin_, "could not find a reasonable integration domain");

        this.integrator_ = new GaussLobattoIntegral(Constants.I_MAX_ITERATIONS, Constants.I_ACCURACY);

        final D0Interpolator d0 = new D0Interpolator(forward_, expiryTime_, alpha_, beta_, nu_, rho_);
        absProb_ = d0.value();

        try {
            final Brent b = new Brent();
            final double start = Math.sqrt(externalForward_ - Constants.STRIKE_MIN);
            final double step = Math.min(Constants.FORWARD_SEARCH_STEP, start / 2.0);
            final Ops.DoubleOp fe = new Ops.DoubleOp() {
                @Override
                public double op(final double x) {
                    return forwardError(x);
                }
            };
            final double tmp = b.solve(fe, Constants.FORWARD_ACCURACY, start, step);
            forward_ = tmp * tmp + Constants.STRIKE_MIN;
        } catch ( final Exception ignored ) {
            // fall back to unadjusted forward
            forward_ = externalForward_;
        }

        final double d = forwardError(Math.sqrt(forward_ - Constants.STRIKE_MIN));
        numericalForward_ = d + externalForward_;
    }

    public double forward() {
        return externalForward_;
    }

    public double numericalForward() {
        return numericalForward_;
    }

    public double expiryTime() {
        return expiryTime_;
    }

    public double alpha() {
        return alpha_;
    }

    public double beta() {
        return beta_;
    }

    public double nu() {
        return nu_;
    }

    public double rho() {
        return rho_;
    }

    public double absorptionProbability() {
        return absProb_;
    }

    /**
     * Option (call) price (noarbsabr.cpp 116-123).
     */
    public double optionPrice(final double strike) {
        if ( pImpl(Math.max(forward_, strike)) < Constants.DENSITY_THRESHOLD ) {
            return 0.0;
        }
        final double upper = Math.max(fmax_, 2.0 * strike);
        final Ops.DoubleOp integrand = new Ops.DoubleOp() {
            @Override
            public double op(final double f) {
                return Math.max(f - strike, 0.0) * pImpl(f);
            }
        };
        return (1.0 - absProb_) * (integrator_.op(integrand, strike, upper) / numericalIntegralOverP_);
    }

    /**
     * Digital (call) price (noarbsabr.cpp 125-134).
     */
    public double digitalOptionPrice(final double strike) {
        if ( strike < Double.MIN_NORMAL ) {
            return 1.0;
        }
        if ( pImpl(Math.max(forward_, strike)) < Constants.DENSITY_THRESHOLD ) {
            return 0.0;
        }
        final double upper = Math.max(fmax_, 2.0 * strike);
        final Ops.DoubleOp pIntegrand = new Ops.DoubleOp() {
            @Override
            public double op(final double f) {
                return pImpl(f);
            }
        };
        return (1.0 - absProb_) * (integrator_.op(pIntegrand, strike, upper) / numericalIntegralOverP_);
    }

    /**
     * Density at strike (noarbsabr.hpp 107-109): {@code p(strike) * (1 - absProb) / numericalIntegralOverP}.
     */
    public double density(final double strike) {
        return pImpl(strike) * (1.0 - absProb_) / numericalIntegralOverP_;
    }

    /**
     * Forward-error (noarbsabr.cpp 136-141). Side-effect: updates {@code forward_} and
     * {@code numericalIntegralOverP_}.
     */
    private double forwardError(final double forward) {
        forward_ = forward * forward + Constants.STRIKE_MIN;
        final Ops.DoubleOp pIntegrand = new Ops.DoubleOp() {
            @Override
            public double op(final double f) {
                return pImpl(f);
            }
        };
        numericalIntegralOverP_ = integrator_.op(pIntegrand, fmin_, fmax_);
        return optionPrice(0.0) - externalForward_;
    }

    /**
     * Density {@code p(f)} (noarbsabr.cpp 143-182). Uses Hagan/Doust analytic approximation plus the
     * exponentially-weighted modified Bessel function.
     */
    private double pImpl(final double f) {
        if ( f < Constants.DENSITY_LOWER_BOUND || forward_ < Constants.DENSITY_LOWER_BOUND ) {
            return 0.0;
        }

        final double fOmB = Math.pow(f, 1.0 - beta_);
        final double FOmB = Math.pow(forward_, 1.0 - beta_);

        final double zf = fOmB / (alpha_ * (1.0 - beta_));
        final double zF = FOmB / (alpha_ * (1.0 - beta_));
        final double z = zF - zf;

        final double Jmzf = Math.sqrt(1.0 + 2.0 * rho_ * nu_ * zf + nu_ * nu_ * zf * zf);
        final double Jz = Math.sqrt(1.0 - 2.0 * rho_ * nu_ * z + nu_ * nu_ * z * z);

        final double xz = Math.log((Jz - rho_ + nu_ * z) / (1.0 - rho_)) / nu_;
        final double Bp_B = beta_ / FOmB;
        final double kappa1 = 0.125 * nu_ * nu_ * (2.0 - 3.0 * rho_ * rho_) - 0.25 * rho_ * nu_ * alpha_ * Bp_B;
        final double gamma = 1.0 / (2.0 * (1.0 - beta_));
        final double sqrtOmR = Math.sqrt(1.0 - rho_ * rho_);

        final double h = 0.5 * beta_ * rho_ / ((1.0 - beta_) * Jmzf * Jmzf) * (nu_ * zf * Math.log(zf * Jz / zF)
                + (1.0 + rho_ * nu_ * zf) / sqrtOmR * (Math.atan((nu_ * z - rho_) / sqrtOmR) + Math.atan(
                rho_ / sqrtOmR)));

        return Math.pow(Jz, -1.5) / (alpha_ * Math.pow(f, beta_) * expiryTime_) * Math.pow(zf, 1.0 - gamma) * Math.pow(
                zF, gamma) * Math.exp(-(xz * xz) / (2.0 * expiryTime_) + (h + kappa1 * expiryTime_))
                * ModifiedBesselFunction.iExpWeighted(gamma, zF * zf / expiryTime_);
    }

    /**
     * Parameter bounds and integration constants (mirrors C++ {@code detail::NoArbSabrModel} namespace, noarbsabr.hpp
     * lines 61-98).
     */
    public static final class Constants {
        public static final double BETA_MIN = 0.01;
        public static final double BETA_MAX = 0.99;
        public static final double EXPIRY_TIME_MAX = 30.0;
        public static final double SIGMA_I_MIN = 0.05;
        public static final double SIGMA_I_MAX = 1.00;
        public static final double NU_MIN = 0.01;
        public static final double NU_MAX = 0.80;
        public static final double RHO_MIN = -0.99;
        public static final double RHO_MAX = 0.99;
        /** {@code phi(d0)/tau} cutoff: at beta=0.99, d0 below 1E-14 above this. */
        public static final double PHI_BY_TAU_CUTOFF = 124.587;
        /** Number of Monte Carlo simulations in the tabulated absorption probabilities. */
        public static final double NSIM = 2_500_000.0;
        /** Small probability used for extrapolation of beta towards 1. */
        public static final double TINY_PROB = 1.0e-5;
        /** Minimum strike for normal-case integration. */
        public static final double STRIKE_MIN = 1.0e-6;
        /** Numerical-integration target accuracy. */
        public static final double I_ACCURACY = 1.0e-7;
        /** Numerical-integration max iterations. */
        public static final int I_MAX_ITERATIONS = 10_000;
        /** Forward-search target accuracy. */
        public static final double FORWARD_ACCURACY = 1.0e-6;
        /** Forward-search Newton step. */
        public static final double FORWARD_SEARCH_STEP = 0.0010;
        /** Density evaluation lower bound. */
        public static final double DENSITY_LOWER_BOUND = 1.0e-50;
        /** Threshold used to identify a zero density. */
        public static final double DENSITY_THRESHOLD = 1.0e-100;

        private Constants() {
        }
    }
}

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

/**
 * No-arbitrage SABR model (Doust 2012).
 *
 * <p>Constants + parameter accessors port of QuantLib v1.42.1
 * {@code ql/experimental/volatility/noarbsabr.{hpp,cpp}}. The full pricing
 * logic (option price, digital price, density) is deferred to Phase 4f.5
 * because the model relies on:
 * <ul>
 *   <li>A pre-computed 1,209,600-entry absorption-probability table
 *       ({@code noarbsabrabsprobs.cpp}, ~10K LOC of generated data).
 *       Strategy: convert to a binary resource on the classpath.</li>
 *   <li>Boost {@code gamma_q} / {@code gamma_q_inv} (incomplete gamma
 *       complement and its inverse — not yet ported to JQuantMath).</li>
 *   <li>{@code modifiedBesselFunction_i_exponentiallyWeighted} (already
 *       provided by JQuantLib's {@code ModifiedBesselFunction}, but the
 *       D0 interpolator is non-trivial).</li>
 * </ul>
 *
 * <p>This skeleton only validates parameter bounds (Doust 2012, Table 1)
 * and exposes accessors so that {@link NoArbSabrSmileSection} can construct
 * a model instance. All pricing/density methods throw
 * {@link UnsupportedOperationException}.
 *
 * <p>See also: {@link Constants} (parameter bounds, {@code phiByTau_cutoff},
 * etc.).
 */
public class NoArbSabrModel {

    /** Parameter bounds and integration constants (mirrors C++
     * {@code detail::NoArbSabrModel} namespace, noarbsabr.hpp lines 61-98). */
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

        private Constants() {}
    }

    private final double expiryTime_;
    private final double externalForward_;
    private final double alpha_;
    private final double beta_;
    private final double nu_;
    private final double rho_;

    /**
     * Constructor (noarbsabr.cpp lines 50-114, parameter-validation prefix).
     *
     * <p>Note: full constructor body (model-implied forward adjustment via
     * Brent root-finding, integrator setup, D0Interpolator) is deferred to
     * Phase 4f.5 because it depends on the absorption-probability table and
     * the Boost gamma functions.
     */
    public NoArbSabrModel(final double expiryTime, final double forward,
            final double alpha, final double beta, final double nu, final double rho) {
        QL.require(expiryTime > 0.0 && expiryTime <= Constants.EXPIRY_TIME_MAX,
                "expiryTime (" + expiryTime + ") out of bounds");
        QL.require(forward > 0.0,
                "forward (" + forward + ") must be positive");
        QL.require(beta >= Constants.BETA_MIN && beta <= Constants.BETA_MAX,
                "beta (" + beta + ") out of bounds");
        final double sigmaI = alpha * Math.pow(forward, beta - 1.0);
        QL.require(sigmaI >= Constants.SIGMA_I_MIN && sigmaI <= Constants.SIGMA_I_MAX,
                "sigmaI = alpha*forward^(beta-1.0) (" + sigmaI
                        + ") out of bounds, alpha=" + alpha
                        + " beta=" + beta + " forward=" + forward);
        QL.require(nu >= Constants.NU_MIN && nu <= Constants.NU_MAX,
                "nu (" + nu + ") out of bounds");
        QL.require(rho >= Constants.RHO_MIN && rho <= Constants.RHO_MAX,
                "rho (" + rho + ") out of bounds");

        this.expiryTime_ = expiryTime;
        this.externalForward_ = forward;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.nu_ = nu;
        this.rho_ = rho;
    }

    public double forward()    { return externalForward_; }
    public double expiryTime() { return expiryTime_; }
    public double alpha()      { return alpha_; }
    public double beta()       { return beta_; }
    public double nu()         { return nu_; }
    public double rho()        { return rho_; }

    /** Phase 4f.5 — needs absorption table + integrator. */
    public double optionPrice(final double strike) {
        throw new UnsupportedOperationException(
                "NoArbSabrModel.optionPrice deferred to Phase 4f.5 "
                        + "(requires 1.2M-entry absorption-probability table from "
                        + "noarbsabrabsprobs.cpp + Boost gamma_q functions).");
    }

    public double digitalOptionPrice(final double strike) {
        throw new UnsupportedOperationException(
                "NoArbSabrModel.digitalOptionPrice deferred to Phase 4f.5.");
    }

    public double density(final double strike) {
        throw new UnsupportedOperationException(
                "NoArbSabrModel.density deferred to Phase 4f.5.");
    }

    /** Numerical model-implied forward (deferred Phase 4f.5). */
    public double numericalForward() {
        throw new UnsupportedOperationException(
                "NoArbSabrModel.numericalForward deferred to Phase 4f.5.");
    }

    /** Absorption probability at expiry (deferred Phase 4f.5). */
    public double absorptionProbability() {
        throw new UnsupportedOperationException(
                "NoArbSabrModel.absorptionProbability deferred to Phase 4f.5.");
    }
}

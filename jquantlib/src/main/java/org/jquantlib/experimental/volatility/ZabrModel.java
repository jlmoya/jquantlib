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
import org.jquantlib.math.Closeness;
import org.jquantlib.termstructures.volatilities.Sabr;

/**
 * ZABR model (Andreasen-Huge ZABR — Expansions for the Masses, 2011).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/zabr.{hpp,cpp}}.
 *
 * <p><b>Phase 4f.5 partial port:</b>
 * <ul>
 *   <li>{@link #lognormalVolatility(double)} and
 *       {@link #normalVolatility(double)} are <em>fully implemented for the
 *       {@code gamma == 1.0} case</em> (closed-form, zabr.cpp lines 332-338).
 *       This is the case used by {@code ZabrShortMaturityLognormal /Normal}
 *       evaluations on simple smile sections.</li>
 *   <li>For {@code gamma != 1.0} both methods throw — they need the
 *       adaptive Runge-Kutta ODE solver (zabr.cpp lines 339-358) which is
 *       deferred to Phase 4f.5+ (RK port).</li>
 *   <li>{@link #localVolatility(double)}, {@link #fdPrice(double)},
 *       {@link #fullFdPrice(double)} require the FD machinery in
 *       {@code experimental.finitedifferences} and are deferred to Phase 4n.5.</li>
 * </ul>
 */
public class ZabrModel {

    private final double expiryTime_;
    private final double forward_;
    private final double alpha_;
    private final double beta_;
    /** {@code nu_} stored after the C++ transformation {@code nu * alpha^(1-gamma)}. */
    private final double nu_;
    private final double rho_;
    private final double gamma_;

    /**
     * Constructor (zabr.cpp lines 42-56). Validates SABR parameters and the
     * extra ZABR-specific {@code gamma >= 0} bound.
     *
     * <p>Note: the stored {@code nu_} is the C++ transformed value
     * {@code nu * alpha^(1 - gamma)} (matches C++ field semantics so that
     * downstream formulas can use {@code nu_} directly).
     */
    public ZabrModel(final double expiryTime, final double forward,
            final double alpha, final double beta, final double nu,
            final double rho, final double gamma) {
        // Phase 4f scaffold: validate inputs without fully porting the formula.
        new Sabr().validateSabrParameters(alpha, beta, nu, rho);
        QL.require(gamma >= 0.0, "gamma must be non negative: " + gamma + " not allowed");
        QL.require(forward >= 0.0, "forward must be non negative: " + forward + " not allowed");
        QL.require(expiryTime > 0.0, "expiry time must be positive: " + expiryTime + " not allowed");

        this.expiryTime_ = expiryTime;
        this.forward_ = forward;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.nu_ = nu * Math.pow(alpha, 1.0 - gamma); // C++ transformation
        this.rho_ = rho;
        this.gamma_ = gamma;
    }

    public double forward()    { return forward_; }
    public double expiryTime() { return expiryTime_; }
    public double alpha()      { return alpha_; }
    public double beta()       { return beta_; }
    public double nu()         { return nu_; }
    public double rho()        { return rho_; }
    public double gamma()      { return gamma_; }

    /**
     * ZABR-equivalent Black (lognormal) volatility — mirrors C++ v1.42.1
     * {@code ZabrModel::lognormalVolatility(strike)} → {@code lognormalVolatilityHelper}.
     *
     * <p>Implemented for the {@code gamma == 1.0} closed-form case. For
     * {@code gamma != 1.0} the C++ code uses an adaptive Runge-Kutta ODE
     * solver — deferred to Phase 4f.5+.
     */
    public double lognormalVolatility(final double strike) {
        final double xs = x(strike);
        return lognormalVolatilityHelper(strike, xs);
    }

    /**
     * Normal (Bachelier-equivalent) ZABR volatility — mirrors C++ v1.42.1
     * {@code ZabrModel::normalVolatility(strike)} → {@code normalVolatilityHelper}.
     *
     * <p>Implemented for the {@code gamma == 1.0} closed-form case.
     */
    public double normalVolatility(final double strike) {
        final double xs = x(strike);
        return normalVolatilityHelper(strike, xs);
    }

    /**
     * Local volatility (deferred Phase 4f.5+ — needs FD/RK machinery).
     */
    public double localVolatility(final double f) {
        throw new UnsupportedOperationException(
                "ZabrModel.localVolatility deferred to Phase 4f.5+.");
    }

    /**
     * FD price under the Dupire local-vol PDE (deferred Phase 4n.5).
     */
    public double fdPrice(final double strike) {
        throw new UnsupportedOperationException(
                "ZabrModel.fdPrice deferred to Phase 4n.5 "
                        + "(requires FdmDupire1dOp + Concentrating1dMesher).");
    }

    /**
     * Full FD price under the ZABR 2-factor PDE (deferred Phase 4n.5).
     */
    public double fullFdPrice(final double strike) {
        throw new UnsupportedOperationException(
                "ZabrModel.fullFdPrice deferred to Phase 4n.5 "
                        + "(requires FdmZabrOp).");
    }

    // ------------------------------------------------------------------
    // Private helpers (mirror C++ ZabrModel:: helpers)
    // ------------------------------------------------------------------

    /**
     * Mirrors C++ {@code lognormalVolatilityHelper} (zabr.cpp lines 58-64).
     */
    private double lognormalVolatilityHelper(final double strike, final double x) {
        if (Closeness.isClose(strike, forward_))
            return Math.pow(forward_, beta_ - 1.0) * alpha_;
        else
            return Math.log(forward_ / strike) / x;
    }

    /**
     * Mirrors C++ {@code normalVolatilityHelper} (zabr.cpp lines 78-83).
     */
    private double normalVolatilityHelper(final double strike, final double x) {
        if (Closeness.isClose(strike, forward_))
            return Math.pow(forward_, beta_) * alpha_;
        else
            return (forward_ - strike) / x;
    }

    /**
     * Mirrors C++ {@code ZabrModel::x(strike)} (zabr.cpp lines 312-361, scalar
     * dispatch through the vector form). Implemented for {@code gamma == 1.0}
     * via the closed form (zabr.cpp lines 332-338).
     *
     * @throws UnsupportedOperationException for {@code gamma != 1.0} (needs
     *         adaptive Runge-Kutta — deferred).
     */
    private double x(final double strike) {
        if (beta_ >= 1.0) {
            QL.require(strike > 0.0,
                    "strike must be positive (" + strike + ") if beta = 1");
        }
        final double yy = y(strike);
        if (Closeness.isClose(gamma_, 1.0)) {
            // Closed form — zabr.cpp lines 333-338
            final double J = Math.sqrt(1.0 + nu_ * nu_ * yy * yy
                    - 2.0 * rho_ * nu_ * yy);
            return Math.log((J + nu_ * yy - rho_) / (1.0 - rho_)) / nu_;
        } else {
            throw new UnsupportedOperationException(
                    "ZabrModel.x(strike) for gamma != 1.0 needs adaptive Runge-Kutta "
                            + "(deferred to Phase 4f.5+ RK port). gamma=" + gamma_);
        }
    }

    /**
     * Mirrors C++ {@code ZabrModel::y(strike)} (zabr.cpp lines 363-375).
     */
    private double y(final double strike) {
        if (Closeness.isClose(beta_, 1.0)) {
            return Math.log(forward_ / strike) * Math.pow(alpha_, gamma_ - 2.0);
        } else {
            final double term = (strike < 0.0)
                    ? Math.pow(forward_, 1.0 - beta_) + Math.pow(-strike, 1.0 - beta_)
                    : Math.pow(forward_, 1.0 - beta_) - Math.pow(strike, 1.0 - beta_);
            return term * Math.pow(alpha_, gamma_ - 2.0) / (1.0 - beta_);
        }
    }
}

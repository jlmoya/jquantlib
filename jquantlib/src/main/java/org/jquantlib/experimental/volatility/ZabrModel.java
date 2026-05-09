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
import org.jquantlib.termstructures.volatilities.Sabr;

/**
 * ZABR model (Andreasen-Huge ZABR — Expansions for the Masses, 2011).
 *
 * <p>Constructor + parameter accessors port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/zabr.{hpp,cpp}}. The full evaluation
 * helpers ({@code lognormalVolatility}, {@code normalVolatility},
 * {@code localVolatility}, {@code fdPrice}, {@code fullFdPrice}) require:
 * <ul>
 *   <li>An adaptive Runge-Kutta ODE solver (for the {@code x(strike)}
 *       integral — not yet ported).</li>
 *   <li>The {@code Concentrating1dMesher} + {@code FdmDupire1dOp} +
 *       {@code FdmZabrOp} FD machinery (in {@code experimental.finitedifferences},
 *       deferred to Phase 4n.5).</li>
 * </ul>
 *
 * <p>Phase 4f.5 carry-forward: the evaluation methods throw
 * {@link UnsupportedOperationException} until the dependencies above are
 * ported. See {@code docs/migration/phase4f-progress.md}.
 *
 * <p>This skeleton lets ZabrSmileSection and ZabrInterpolation compile and
 * be referenced by callers; full pricing requires the deferred work.
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
     * Hagan SABR-like lognormal vol (deferred Phase 4f.5 — needs RK ODE solver).
     */
    public double lognormalVolatility(final double strike) {
        throw new UnsupportedOperationException(
                "ZabrModel.lognormalVolatility deferred to Phase 4f.5 "
                        + "(requires adaptive Runge-Kutta ODE solver for x(strike)).");
    }

    /**
     * Normal (Bachelier) implied vol (deferred Phase 4f.5).
     */
    public double normalVolatility(final double strike) {
        throw new UnsupportedOperationException(
                "ZabrModel.normalVolatility deferred to Phase 4f.5.");
    }

    /**
     * Local volatility (deferred Phase 4f.5).
     */
    public double localVolatility(final double f) {
        throw new UnsupportedOperationException(
                "ZabrModel.localVolatility deferred to Phase 4f.5.");
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
}

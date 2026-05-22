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
package org.jquantlib.experimental.forward;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Complex;
import org.jquantlib.math.ModifiedBesselFunction;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Analytic Heston engine for forward-starting European options.
 *
 * <p>Phase 4a.5 A.5.3 port of {@code QuantLib::AnalyticHestonForwardEuropeanEngine}
 * (v1.42.1 ql/experimental/forward/analytichestonforwardeuropeanengine.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Implements the Kruse (2003) analytical solution for forward-starting
 * strike-reset options under the Heston stochastic volatility model. The pricer performs nested Gaussian-Legendre
 * integrations:
 * <ul>
 *   <li>Inner: P1, P2 from chF (Gauss-Legendre n=128)</li>
 *   <li>Outer: P̂1, P̂2 = ∫ propagator(ν) · (½ + p1Integral/π) dν
 *       (Gauss-Legendre n=128)</li>
 * </ul>
 *
 * <p>The {@code propagator(t, vt)} is the noncentral chi-squared transition
 * density of the variance process from {@code t=0} to {@code resetTime},
 * expressed via {@link ModifiedBesselFunction#i(double, double)}.
 *
 * <p>Same {@code sigma > 0.1} restriction as C++: low vol-of-vol causes
 * numerical issues in the propagator; the Monte-Carlo
 * {@code MCForwardEuropeanHestonEngine} is the recommended fallback in
 * that regime (deferred to a follow-up Phase).
 *
 * <p>Depends on {@link AnalyticHestonEngine#chF(Complex, double)} (Phase
 * 4a.5 A.5.2) and {@link GaussLegendreIntegration} (Phase 4a.5 A.5.1).
 */
public class AnalyticHestonForwardEuropeanEngine extends ForwardVanillaOption.EngineImpl {

    private final HestonProcess process_;
    private final int integrationOrder_;

    // Cached process parameters
    private final double v0_;
    private final double rho_;
    private final double kappa_;
    private final double theta_;
    private final double sigma_;
    private final Handle< YieldTermStructure > dividendYield_;
    private final Handle< YieldTermStructure > riskFreeRate_;
    private final Handle< Quote > s0_;

    // Intermediate constants for the propagator
    private final double kappaHat_;
    private final double thetaHat_;
    private final double R_;

    // Outer integrator (over nu)
    private final GaussLegendreIntegration outerIntegrator_;

    public AnalyticHestonForwardEuropeanEngine(final HestonProcess process) {
        // C++ default is 144, but Java GaussLaguerreIntegration supports
        // only n=128 (see Phase 2f WI-3 C.2 design note); use n=128 here.
        this(process, 128);
    }

    public AnalyticHestonForwardEuropeanEngine(final HestonProcess process, final int integrationOrder) {
        super();
        this.process_ = process;
        this.integrationOrder_ = integrationOrder;
        this.outerIntegrator_ = new GaussLegendreIntegration(128);

        this.v0_ = process_.v0().currentLink().value();
        this.rho_ = process_.rho().currentLink().value();
        this.kappa_ = process_.kappa().currentLink().value();
        this.theta_ = process_.theta().currentLink().value();
        this.sigma_ = process_.sigma().currentLink().value();
        this.s0_ = process_.s0();

        QL.require(sigma_ > 0.1, "Very low values (<~10%) for Heston Vol-of-Vol cause numerical issues "
                + "in this implementation of the propagator function, try using "
                + "MCForwardEuropeanHestonEngine Monte-Carlo engine instead");

        this.riskFreeRate_ = process_.riskFreeRate();
        this.dividendYield_ = process_.dividendYield();

        // Constant intermediate variables
        this.kappaHat_ = kappa_ - rho_ * sigma_;
        this.thetaHat_ = kappa_ * theta_ / kappaHat_;
        this.R_ = 4.0 * kappaHat_ * thetaHat_ / (sigma_ * sigma_);
    }

    @Override
    public void calculate() {
        final ForwardVanillaOption.ArgumentsImpl args = (ForwardVanillaOption.ArgumentsImpl) arguments_;

        QL.require(args.exercise.type() == Exercise.Type.European, "not an European option");
        QL.require(args.payoff instanceof PlainVanillaPayoff, "non plain vanilla payoff given");

        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args.payoff;

        final double resetTime = process_.time(args.resetDate);
        final double expiryTime = process_.time(args.exercise.lastDate());
        final double tenor = expiryTime - resetTime;
        final double moneyness = args.moneyness;

        final double expiryDcf = riskFreeRate_.currentLink().discount(expiryTime);
        final double resetDcf = riskFreeRate_.currentLink().discount(resetTime);
        final double expiryDividendDiscount = dividendYield_.currentLink().discount(expiryTime);
        final double resetDividendDiscount = dividendYield_.currentLink().discount(resetTime);
        final double expiryRatio = expiryDcf / expiryDividendDiscount;
        final double resetRatio = resetDcf / resetDividendDiscount;

        QL.require(resetTime >= 0.0, "Reset Date cannot be in the past");
        QL.require(expiryTime >= 0.0, "Expiry Date cannot be in the past");

        final double phiRightLimit = 100.0;
        final double nuRightLimit = Math.max(2.0,
                10.0 * (1.0 + Math.max(0.0, rho_)) * sigma_ * Math.sqrt(resetTime * Math.max(v0_, theta_)));

        // Short-reset fall-back: vanilla Heston P1/P2 at spot
        final double[] p1p2hat;
        if ( resetTime <= 1e-3 ) {
            final Handle< Quote > tempQuote = new Handle< Quote >(new SimpleQuote(s0_.currentLink().value()));
            p1p2hat = calculateP1P2(tenor, tempQuote, moneyness * s0_.currentLink().value(), expiryRatio,
                    phiRightLimit);
        } else {
            p1p2hat = calculateP1P2Hat(tenor, resetTime, moneyness, expiryRatio / resetRatio, phiRightLimit,
                    nuRightLimit);
        }

        final double F = s0_.currentLink().value() / expiryRatio;
        final double value = switch (payoff.optionType()) {
            case Call -> expiryDcf * (F * p1p2hat[0] - moneyness * s0_.currentLink().value() * p1p2hat[1] / resetRatio);
            case Put -> expiryDcf * (moneyness * s0_.currentLink().value() * (1.0 - p1p2hat[1]) / resetRatio - F * (1.0
                    - p1p2hat[0]));
            default -> throw new IllegalArgumentException("unknown option type");
        };

        final ForwardVanillaOption.ResultsImpl res = (ForwardVanillaOption.ResultsImpl) results_;
        res.value = value;
    }

    /**
     * Forward-evolution propagator: noncentral chi-squared density of the variance process from {@code t=0} to
     * {@code resetTime}.
     *
     * <p>Equation (18) in Kruse (2003); uses
     * {@link ModifiedBesselFunction#i(double, double)} for the modified Bessel function of the first kind.
     */
    public double propagator(final double resetTime, final double varReset) {
        final double B = 4.0 * kappaHat_ / (sigma_ * sigma_ * (1.0 - Math.exp(-kappaHat_ * resetTime)));
        final double Lambda = B * Math.exp(-kappaHat_ * resetTime) * v0_;

        final double term1 = Math.exp(-0.5 * (B * varReset + Lambda)) * B / 2.0;
        final double term2 = Math.pow(B * varReset / Lambda, 0.5 * (R_ / 2.0 - 1.0));
        final double term3 = ModifiedBesselFunction.i(R_ / 2.0 - 1.0, Math.sqrt(Lambda * B * varReset));
        return term1 * term2 * term3;
    }

    /**
     * Build a vanilla-Heston engine for the conditional Heston dynamics starting at ({@code spotReset},
     * {@code varReset}).
     */
    public AnalyticHestonEngine forwardChF(final Handle< Quote > spotReset, final double varReset) {
        final HestonProcess process = new HestonProcess(riskFreeRate_, dividendYield_, spotReset, varReset, kappa_,
                theta_, sigma_, rho_);
        final HestonModel model = new HestonModel(process);
        return new AnalyticHestonEngine(model, process, integrationOrder_);
    }

    private double[] calculateP1P2Hat(final double tenor, final double resetTime, final double moneyness,
            final double ratio, final double phiRightLimit, final double nuRightLimit) {
        final Handle< Quote > unitQuote = new Handle< Quote >(new SimpleQuote(1.0));
        final double logMoneyness = Math.log(moneyness * ratio);

        final P12HatIntegrand p1Hat = new P12HatIntegrand(tenor, resetTime, unitQuote, logMoneyness, true,
                phiRightLimit, nuRightLimit);
        final P12HatIntegrand p2Hat = new P12HatIntegrand(tenor, resetTime, unitQuote, logMoneyness, false,
                phiRightLimit, nuRightLimit);

        final double p1HatIntegral = 0.5 * nuRightLimit * outerIntegrator_.op(p1Hat);
        final double p2HatIntegral = 0.5 * nuRightLimit * outerIntegrator_.op(p2Hat);
        return new double[] { p1HatIntegral, p2HatIntegral };
    }

    private double[] calculateP1P2(final double tenor, final Handle< Quote > St, final double K, final double ratio,
            final double phiRightLimit) {
        final AnalyticHestonEngine engine = forwardChF(St, v0_);
        final double logK = Math.log(K * ratio / St.currentLink().value());

        final GaussLegendreIntegration integrator = new GaussLegendreIntegration(128);
        final P12Integrand p1 = new P12Integrand(engine, logK, tenor, true, phiRightLimit);
        final P12Integrand p2 = new P12Integrand(engine, logK, tenor, false, phiRightLimit);

        final double p1Integral = integrator.op(p1);
        final double p2Integral = integrator.op(p2);
        return new double[] { 0.5 + p1Integral / Math.PI, 0.5 + p2Integral / Math.PI };
    }

    /** Inner: P12 integrand over phi. Mirrors C++ {@code P12Integrand}. */
    private static final class P12Integrand implements Ops.DoubleOp {
        private final AnalyticHestonEngine engine_;
        private final double logK_, phiRightLimit_, tenor_;
        private final Complex i_ = Complex.I;
        private final Complex adj_;

        P12Integrand(final AnalyticHestonEngine engine, final double logK, final double tenor, final boolean p1,
                final double phiRightLimit) {
            this.engine_ = engine;
            this.logK_ = logK;
            this.tenor_ = tenor;
            this.phiRightLimit_ = phiRightLimit;
            this.adj_ = p1 ? new Complex(0.0, -1.0) : Complex.ZERO;
        }

        @Override
        public double op(final double phi) {
            final double phiDash = (0.5 + 1e-8 + 0.5 * phi) * phiRightLimit_;
            // (exp(-phiDash*logK*i) / (phiDash*i)) * engine.chF(phiDash + adj, tenor)
            final Complex z = Complex.real(phiDash).add(adj_);
            final Complex chF = engine_.chF(z, tenor_);
            final Complex num = i_.mul(-phiDash * logK_).exp();
            final Complex den = i_.mul(phiDash);
            return 0.5 * phiRightLimit_ * num.div(den).mul(chF).real();
        }
    }

    /** Outer: P12Hat integrand over nu. Mirrors C++ {@code P12HatIntegrand}. */
    private final class P12HatIntegrand implements Ops.DoubleOp {
        private final double tenor_, resetTime_;
        private final Handle< Quote > s0Inner_;
        private final boolean p1_;
        private final double logK_, phiRightLimit_, nuRightLimit_;
        private final GaussLegendreIntegration innerIntegrator_;

        P12HatIntegrand(final double tenor, final double resetTime, final Handle< Quote > s0Inner, final double logK,
                final boolean p1, final double phiRightLimit, final double nuRightLimit) {
            this.tenor_ = tenor;
            this.resetTime_ = resetTime;
            this.s0Inner_ = s0Inner;
            this.p1_ = p1;
            this.logK_ = logK;
            this.phiRightLimit_ = phiRightLimit;
            this.nuRightLimit_ = nuRightLimit;
            this.innerIntegrator_ = new GaussLegendreIntegration(128);
        }

        @Override
        public double op(final double nu) {
            final double nuDash = nuRightLimit_ * (0.5 * nu + 0.5 + 1e-8);
            final AnalyticHestonEngine engine = forwardChF(s0Inner_, nuDash);
            final P12Integrand p = new P12Integrand(engine, logK_, tenor_, p1_, phiRightLimit_);
            final double p1Integral = innerIntegrator_.op(p);
            final double prop = propagator(resetTime_, nuDash);
            return prop * (0.5 + p1Integral / Math.PI);
        }
    }
}

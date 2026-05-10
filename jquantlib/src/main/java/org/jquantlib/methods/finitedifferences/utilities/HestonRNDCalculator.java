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
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Risk-neutral terminal density calculator for the Heston stochastic-volatility
 * model.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/methods/finitedifferences/utilities/hestonrndcalculator.{hpp,cpp}}.
 *
 * <p>Reference: A. Dragulescu, V. Yakovenko, 2002. "Probability distribution of
 * returns in the Heston model with stochastic volatility."
 * <a href="http://arxiv.org/pdf/cond-mat/0203046.pdf">arXiv:cond-mat/0203046</a>.
 *
 * <p>The pdf and cdf are computed by Gauss-Lobatto integration of a Fourier-
 * inversion kernel with a {@code u_x = -log(x) / c_inf} substitution to map
 * the half-line to (0,1). The {@code invcdf} uses a Brent root-finder seeded
 * with a BSM lognormal guess at an effective expected variance.
 *
 * @author Phase 5h.5-RND port
 */
public class HestonRNDCalculator extends RiskNeutralDensityCalculator {

    private final HestonProcess hestonProcess_;
    private final double x0_;                          // ln(S0)
    private final double integrationEps_;
    private final int    maxIntegrationIterations_;

    public HestonRNDCalculator(final HestonProcess hestonProcess) {
        this(hestonProcess, 1.0e-6, 10000);
    }

    public HestonRNDCalculator(final HestonProcess hestonProcess,
                               final double integrationEps,
                               final int maxIntegrationIterations) {
        QL.require(hestonProcess != null, "hestonProcess must not be null");
        this.hestonProcess_            = hestonProcess;
        this.x0_                       = Math.log(hestonProcess.s0().currentLink().value());
        this.integrationEps_           = integrationEps;
        this.maxIntegrationIterations_ = maxIntegrationIterations;
    }

    /** Drift-corrected log-spot for time t (matches C++ {@code x_t}). */
    private double xt(final double x, final double t) {
        final double dr = hestonProcess_.riskFreeRate().currentLink().discount(t);
        final double dq = hestonProcess_.dividendYield().currentLink().discount(t);
        return x - x0_ + Math.log(dr / dq);
    }

    @Override
    public double pdf(final double x, final double t) {
        final HestonParams p = HestonParams.of(hestonProcess_);
        final CpxPvHelper helper = new CpxPvHelper(p, xt(x, t), t);

        final GaussLobattoIntegral integ = new GaussLobattoIntegral(
                maxIntegrationIterations_, 0.1 * integrationEps_);
        return integ.op(helper, 0.0, 1.0) / Constants.M_TWOPI;
    }

    @Override
    public double cdf(final double x, final double t) {
        final HestonParams p = HestonParams.of(hestonProcess_);
        final CpxPvHelper helper = new CpxPvHelper(p, xt(x, t), t);

        final GaussLobattoIntegral integ = new GaussLobattoIntegral(
                maxIntegrationIterations_, 0.1 * integrationEps_);
        final Ops.DoubleOp p0 = new Ops.DoubleOp() {
            @Override
            public double op(final double px) {
                return helper.p0(px);
            }
        };
        return integ.op(p0, 0.0, 1.0) / Constants.M_TWOPI + 0.5;
    }

    @Override
    public double invcdf(final double q, final double t) {
        final double v0    = hestonProcess_.v0().currentLink().value();
        final double kappa = hestonProcess_.kappa().currentLink().value();
        final double theta = hestonProcess_.theta().currentLink().value();

        // Effective lognormal vol: theta + (v0-theta)*(1-exp(-kappa*t))/(kappa*t).
        final double expVol = Math.sqrt(theta
                + (v0 - theta) * (1.0 - Math.exp(-kappa * t)) / (t * kappa));

        // Build a constant-vol BSM process to compute a lognormal guess.
        final BlackScholesMertonProcess bsmProcess = new BlackScholesMertonProcess(
                hestonProcess_.s0(),
                hestonProcess_.dividendYield(),
                hestonProcess_.riskFreeRate(),
                new Handle<BlackVolTermStructure>(
                        new BlackConstantVol(
                                hestonProcess_.riskFreeRate().currentLink().referenceDate(),
                                new NullCalendar(),
                                new Handle<Quote>(new SimpleQuote(expVol)),
                                new Actual365Fixed())));

        final double guess = new BSMRNDCalculator(bsmProcess).invcdf(q, t);

        return new InvCDFHelper(this, guess,
                0.1 * integrationEps_, maxIntegrationIterations_).inverseCDF(q, t);
    }

    // -- private helpers ----------------------------------------------------

    /** Read-only snapshot of Heston parameters. */
    private static final class HestonParams {
        final double v0, kappa, theta, sigma, rho;
        private HestonParams(final double v0, final double kappa, final double theta,
                             final double sigma, final double rho) {
            this.v0 = v0; this.kappa = kappa; this.theta = theta;
            this.sigma = sigma; this.rho = rho;
        }
        static HestonParams of(final HestonProcess p) {
            return new HestonParams(
                    p.v0().currentLink().value(),
                    p.kappa().currentLink().value(),
                    p.theta().currentLink().value(),
                    p.sigma().currentLink().value(),
                    p.rho().currentLink().value());
        }
    }

    /**
     * Fourier-inversion integrand for pdf / cdf, matching C++
     * {@code CpxPv_Helper}.
     */
    private static final class CpxPvHelper implements Ops.DoubleOp {
        private final HestonParams p_;
        private final double t_;
        private final double x_;
        private final double cInf_;

        CpxPvHelper(final HestonParams p, final double x, final double t) {
            this.p_ = p; this.t_ = t; this.x_ = x;
            // c_inf = clip01[sqrt(1 - rho^2) / sigma] * (v0 + kappa*theta*t)
            final double rho2 = p.rho * p.rho;
            this.cInf_ = Math.min(10.0, Math.max(1.0e-4,
                            Math.sqrt(1.0 - rho2) / p.sigma))
                    * (p.v0 + p.kappa * p.theta * t);
        }

        /** pdf integrand: real part of transformPhi(x). */
        @Override
        public double op(final double x) {
            return transformPhi(x).real();
        }

        /** cdf integrand: {@code Re[ phi(u_x) / (px * c_inf * I * u_x) ]}. */
        double p0(final double px) {
            if (px < Constants.QL_EPSILON) return 0.0;
            final double ux = Math.max(Constants.QL_EPSILON, -Math.log(px) / cInf_);
            // phi(ux) / (px * cInf * (i * ux))
            final Complex denom = new Complex(0.0, ux).mul(px * cInf_);
            return phi(ux).div(denom).real();
        }

        /** Transformed pdf integrand: {@code phi(u_x) / (x * c_inf)} where {@code u_x = -ln(x)/c_inf}. */
        private Complex transformPhi(final double x) {
            if (x < Constants.QL_EPSILON) return Complex.ZERO;
            final double ux = -Math.log(x) / cInf_;
            return phi(ux).div(x * cInf_);
        }

        /**
         * Heston characteristic function for the (drift-corrected) log-return,
         * as in Dragulescu-Yakovenko eq. (12).
         */
        private Complex phi(final double px) {
            final double sigma2 = p_.sigma * p_.sigma;
            final Complex g = gamma(p_, px);
            final Complex o = omega(p_, px);
            final Complex gMinusO = g.sub(o);
            final Complex gPlusO  = g.add(o);
            final Complex gammaC = gMinusO.div(gPlusO);

            // exp(-o*t)
            final Complex eOt = o.neg().mul(t_).exp();
            // i*p_x*x  -  v0*(p_x^2, -p_x) / (g + o*(1+e^-ot)/(1-e^-ot))
            //         + (kappa*theta/sigma2) * ( (g-o)*t - 2*log( (1-gamma*e^-ot)/(1-gamma) ) )
            final Complex term1 = new Complex(0.0, px * x_);

            // (g + o*(1+e^-ot)/(1-e^-ot))
            final Complex coth = Complex.ONE.add(eOt).div(Complex.ONE.sub(eOt));
            final Complex denom = g.add(o.mul(coth));
            final Complex term2 = new Complex(px * px, -px).mul(p_.v0).div(denom).neg();

            // log term
            final Complex log_inner = Complex.ONE.sub(gammaC.mul(eOt))
                    .div(Complex.ONE.sub(gammaC));
            final Complex term3 = gMinusO.mul(t_)
                    .sub(log_inner.log().mul(2.0))
                    .mul(p_.kappa * p_.theta / sigma2);

            return term1.add(term2).add(term3).exp().mul(2.0);
        }

        private static Complex gamma(final HestonParams p, final double px) {
            return new Complex(p.kappa, p.rho * p.sigma * px);
        }

        private static Complex omega(final HestonParams p, final double px) {
            final Complex g = gamma(p, px);
            // sqrt(g^2 + sigma^2 * (px^2, -px))
            return g.mul(g).add(new Complex(px * px, -px).mul(p.sigma * p.sigma)).sqrt();
        }
    }
}

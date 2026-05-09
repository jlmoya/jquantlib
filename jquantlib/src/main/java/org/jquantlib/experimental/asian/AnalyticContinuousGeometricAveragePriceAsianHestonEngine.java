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
package org.jquantlib.experimental.asian;

import java.util.HashMap;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.ContinuousAveragingAsianOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Analytic engine for European continuous geometric average-price Asian
 * options under the Heston stochastic-vol model.
 *
 * <p>Phase 4a.5 A.5.3 port of
 * {@code QuantLib::AnalyticContinuousGeometricAveragePriceAsianHestonEngine}
 * (v1.42.1 ql/experimental/asian/analytic_cont_geom_av_price_heston.{hpp,cpp}).
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Implements the Kim & Wee (2014) analytical solution
 * (<i>Pricing of geometric Asian options under Heston's stochastic
 * volatility model</i>, Quant. Finance 14:10, 1795-1809).
 *
 * <p>Equations referenced are from the paper. Inner classes
 * {@link Integrand} (eq. 29 over xi) and {@link DcfIntegrand} (the
 * non-constant rate/dividend correction integral) are integrated with
 * Gauss-Legendre n=128. The recursive {@code f(z1,z2,z3,z4,n,tau)} is
 * memoized in {@link #fLookupTable_} to keep the {@code O(cutoff)}
 * summation tractable.
 */
public class AnalyticContinuousGeometricAveragePriceAsianHestonEngine
        extends ContinuousAveragingAsianOption.EngineImpl {

    private final HestonProcess process_;

    // Process parameters
    private final double v0_, rho_, kappa_, theta_, sigma_;
    private final Handle<YieldTermStructure> dividendYield_;
    private final Handle<YieldTermStructure> riskFreeRate_;
    private final Handle<Quote> s0_;

    // Constant intermediate values
    private final double a1_, a2_;
    private double a3_ = 0.0, a4_ = 0.0, a5_ = 0.0;

    // Memoization table for f(...)
    private final Map<Integer, Complex> fLookupTable_ = new HashMap<>();

    // Integration controls
    private final int summationCutoff_;
    private final double xiRightLimit_;

    // Integrator for eq. 29 (also used for the DCF correction integral)
    private final GaussLegendreIntegration integrator_;

    public AnalyticContinuousGeometricAveragePriceAsianHestonEngine(final HestonProcess process) {
        this(process, 50, 100.0);
    }

    public AnalyticContinuousGeometricAveragePriceAsianHestonEngine(
            final HestonProcess process,
            final int summationCutoff,
            final double xiRightLimit) {
        super();
        this.process_         = process;
        this.summationCutoff_ = summationCutoff;
        this.xiRightLimit_    = xiRightLimit;
        this.integrator_      = new GaussLegendreIntegration(128);

        this.v0_    = process_.v0().currentLink().value();
        this.rho_   = process_.rho().currentLink().value();
        this.kappa_ = process_.kappa().currentLink().value();
        this.theta_ = process_.theta().currentLink().value();
        this.sigma_ = process_.sigma().currentLink().value();
        this.s0_    = process_.s0();

        this.riskFreeRate_  = process_.riskFreeRate();
        this.dividendYield_ = process_.dividendYield();

        this.a1_ = 2.0 * v0_ / (sigma_ * sigma_);
        this.a2_ = 2.0 * kappa_ * theta_ / (sigma_ * sigma_);
    }

    /**
     * The {@code Phi} function (eq. 25 in Kim & Wee). Public so the
     * {@link Integrand} inner class can call into it.
     */
    public Complex Phi(final Complex s, final Complex w,
                       final double T, final double t,
                       final int cutoff) {
        final double tau = T - t;
        final Complex z1 = z1_f(s, w, T);
        final Complex z2 = z2_f(s, w, T);
        final Complex z3 = z3_f(s, w, T);
        final Complex z4 = z4_f(s, w);

        // Reset memo before this Phi evaluation.
        fLookupTable_.clear();

        final Complex[] FF_tilde = F_F_tilde(z1, z2, z3, z4, tau, cutoff);
        final Complex F      = FF_tilde[0];
        final Complex Ftilde = FF_tilde[1];

        // exp(-a1 * F_tilde / F - a2 * log(F) + a3*s + a4*w + a5)
        final Complex term = Ftilde.div(F).mul(-a1_)
                .add(F.log().mul(-a2_))
                .add(s.mul(a3_))
                .add(w.mul(a4_))
                .add(a5_);
        return term.exp();
    }

    @Override
    public void calculate() {
        final ContinuousAveragingAsianOption.ArgumentsImpl args =
                (ContinuousAveragingAsianOption.ArgumentsImpl) arguments_;

        QL.require(args.averageType == AverageType.Geometric,
                   "not a geometric average option");
        QL.require(args.exercise.type() == Exercise.Type.European,
                   "not an European Option");
        QL.require(args.payoff instanceof PlainVanillaPayoff,
                   "non-plain payoff given");

        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args.payoff;
        final double strike = payoff.strike();
        final Date exerciseDate = args.exercise.lastDate();
        final double expiryTime = process_.time(exerciseDate);
        QL.require(expiryTime >= 0.0, "Expiry Date cannot be in the past");

        final double expiryDcf = riskFreeRate_.currentLink().discount(expiryTime);

        final double startTime = 0.0;
        final double t = startTime;
        final double T = expiryTime;
        final double tau = T - t;
        final double logS0 = Math.log(s0_.currentLink().value());

        // Integrated DCF correction
        final double dcf  = riskFreeRate_.currentLink().discount(T)
                          / riskFreeRate_.currentLink().discount(t);
        final double qdcf = dividendYield_.currentLink().discount(T)
                          / dividendYield_.currentLink().discount(t);
        final DcfIntegrand dcfIntegrand = new DcfIntegrand(t, T, riskFreeRate_, dividendYield_);
        final double integratedDcf = integrator_.op(dcfIntegrand);

        a3_ = (tau * logS0 + integratedDcf) / T
              - kappa_ * theta_ * rho_ * tau * tau / (2.0 * sigma_ * T)
              - rho_ * tau * v0_ / (sigma_ * T);
        a4_ = logS0 * qdcf / dcf - rho_ * v0_ / sigma_
              + rho_ * kappa_ * theta_ * tau / sigma_;
        a5_ = (kappa_ * v0_ + kappa_ * kappa_ * theta_ * tau) / (sigma_ * sigma_);

        // Term 1 — eq. 29 first term (asian forward at s=1, w=0)
        final Complex phiOne = Phi(Complex.ONE, Complex.ZERO, T, t, summationCutoff_);
        final double term1 = 0.5 * (phiOne.real() - strike);

        // Term 2 — Gauss-Legendre integral over xi
        final Integrand integrand = new Integrand(T, summationCutoff_, strike, xiRightLimit_);
        final double term2 = integrator_.op(integrand) / Math.PI;

        final double value;
        switch (payoff.optionType()) {
            case Call: value = expiryDcf * (term1 + term2);  break;
            case Put:  value = expiryDcf * (-term1 + term2); break;
            default:   throw new IllegalArgumentException("unknown option type");
        }

        final ContinuousAveragingAsianOption.ResultsImpl res =
                (ContinuousAveragingAsianOption.ResultsImpl) results_;
        res.value = value;
    }

    // ----- Equations (13) -----

    private Complex z1_f(final Complex s, final Complex w, final double T) {
        return s.mul(s).mul((1.0 - rho_ * rho_) / (2.0 * T * T));
    }

    private Complex z2_f(final Complex s, final Complex w, final double T) {
        // s*(2 rho kappa - sigma)/(2 sigma T) + s*w*(1-rho^2)/T
        return s.mul((2.0 * rho_ * kappa_ - sigma_) / (2.0 * sigma_ * T))
                .add(s.mul(w).mul((1.0 - rho_ * rho_) / T));
    }

    private Complex z3_f(final Complex s, final Complex w, final double T) {
        // s*rho/(sigma T) + 0.5*w*(2 rho kappa - sigma)/sigma + 0.5*w*w*(1-rho^2)
        return s.mul(rho_ / (sigma_ * T))
                .add(w.mul(0.5 * (2.0 * rho_ * kappa_ - sigma_) / sigma_))
                .add(w.mul(w).mul(0.5 * (1.0 - rho_ * rho_)));
    }

    private Complex z4_f(final Complex s, final Complex w) {
        return w.mul(rho_ / sigma_);
    }

    // ----- Equation (21): recursive f(...) with memoization -----

    private Complex f(final Complex z1, final Complex z2, final Complex z3, final Complex z4,
                      final int n, final double tau) {
        Complex result;

        if (n < 2) {
            if (n < 0)       result = Complex.ZERO;
            else if (n == 0) result = Complex.ONE;
            else             result = z4.mul(-sigma_ * sigma_).add(kappa_).mul(0.5 * tau);
        } else {
            final Complex[] fMinus = new Complex[4];
            final double prefactor = -0.5 * sigma_ * sigma_ * tau * tau / (n * (n - 1));
            for (int offset = 1; offset <= 4; offset++) {
                final int location = n - offset;
                final Complex cached = fLookupTable_.get(location);
                if (cached != null) {
                    fMinus[offset - 1] = cached;
                } else {
                    fMinus[offset - 1] = f(z1, z2, z3, z4, location, tau);
                }
            }
            // result = prefactor * (z1*tau^2*fMinus[3] + z2*tau*fMinus[2]
            //                       + (z3 - 0.5 kappa^2/sigma^2) * fMinus[1])
            final Complex t1 = z1.mul(tau * tau).mul(fMinus[3]);
            final Complex t2 = z2.mul(tau).mul(fMinus[2]);
            final Complex t3 = z3.sub(0.5 * kappa_ * kappa_ / (sigma_ * sigma_)).mul(fMinus[1]);
            result = t1.add(t2).add(t3).mul(prefactor);
        }

        fLookupTable_.put(n, result);
        return result;
    }

    // ----- Equations (19), (20): F and F_tilde sums -----

    private Complex[] F_F_tilde(final Complex z1, final Complex z2, final Complex z3, final Complex z4,
                                final double tau, final int cutoff) {
        Complex sum1 = Complex.ZERO;
        Complex sum2 = Complex.ZERO;
        for (int i = 0; i < cutoff; i++) {
            final Complex fi = f(z1, z2, z3, z4, i, tau);
            sum1 = sum1.add(fi);
            sum2 = sum2.add(fi.mul(((double) i) / tau));
        }
        return new Complex[] { sum1, sum2 };
    }

    // ----- Inner integrand classes -----

    private final class Integrand implements Ops.DoubleOp {
        private final double T_, K_, logK_, xiRightLimit_;
        private final int cutoff_;

        Integrand(final double T, final int cutoff, final double K, final double xiRightLimit) {
            this.T_    = T;
            this.K_    = K;
            this.logK_ = Math.log(K);
            this.cutoff_ = cutoff;
            this.xiRightLimit_ = xiRightLimit;
        }

        @Override
        public double op(final double xi) {
            final double xiDash = (0.5 + 1e-8 + 0.5 * xi) * xiRightLimit_;
            final Complex i_ = Complex.I;
            // inner1 = Phi(1 + xiDash*i, 0, T, 0, cutoff)
            // inner2 = -K * Phi(xiDash*i, 0, T, 0, cutoff)
            final Complex inner1 = Phi(Complex.ONE.add(i_.mul(xiDash)), Complex.ZERO, T_, 0.0, cutoff_);
            final Complex inner2 = Phi(i_.mul(xiDash), Complex.ZERO, T_, 0.0, cutoff_).mul(-K_);

            // 0.5 * xiRightLimit * Re( (inner1 + inner2) * exp(-xiDash*logK*i) / (xiDash*i) )
            final Complex sum = inner1.add(inner2);
            final Complex num = i_.mul(-xiDash * logK_).exp();
            final Complex den = i_.mul(xiDash);
            return 0.5 * xiRightLimit_ * sum.mul(num).div(den).real();
        }
    }

    private static final class DcfIntegrand implements Ops.DoubleOp {
        private final double t_, T_, denominator_;
        private final Handle<YieldTermStructure> riskFreeRate_;
        private final Handle<YieldTermStructure> dividendYield_;

        DcfIntegrand(final double t, final double T,
                     final Handle<YieldTermStructure> riskFreeRate,
                     final Handle<YieldTermStructure> dividendYield) {
            this.t_ = t;
            this.T_ = T;
            this.riskFreeRate_  = riskFreeRate;
            this.dividendYield_ = dividendYield;
            this.denominator_ = Math.log(riskFreeRate_.currentLink().discount(t_))
                              - Math.log(dividendYield_.currentLink().discount(t_));
        }

        @Override
        public double op(final double u) {
            final double uDash = (0.5 + 1e-8 + 0.5 * u) * (T_ - t_) + t_;
            return 0.5 * (T_ - t_) * (
                    -Math.log(riskFreeRate_.currentLink().discount(uDash))
                    + Math.log(dividendYield_.currentLink().discount(uDash))
                    + denominator_);
        }
    }
}

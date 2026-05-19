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

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

import java.util.*;

/**
 * Analytic engine for European discrete geometric average-price Asian options under the Heston stochastic-vol model.
 *
 * <p>Phase 4a.5 A.5.3 port of
 * {@code QuantLib::AnalyticDiscreteGeometricAveragePriceAsianHestonEngine} (v1.42.1
 * ql/experimental/asian/analytic_discr_geom_av_price_heston.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Implements the Kim, Kim, Kim & Wee (2016) recursive analytical
 * solution (<i>A Recursive Method for Discretely Monitored Geometric Asian Option Prices</i>, Bull. Korean Math. Soc.
 * 53, 733-749). Uses Gauss-Legendre n=128 for the Fourier integral (eq. 23/24) and memoizes {@code omega_tilde(k)} in
 * {@link #omegaTildeLookupTable_} to keep the recursion tractable.
 *
 * <p>Note (matching C++): this engine does <i>not</i> assert {@code
 * averageType == Geometric} because it can also be used as a control variate for the arithmetic version. When called as
 * control variate the running accumulator and past fixings are forced to identity values ({@code runningLog=0},
 * {@code pastFixings=0}).
 */
public class AnalyticDiscreteGeometricAveragePriceAsianHestonEngine extends DiscreteAveragingAsianOption.EngineImpl {

    private final HestonProcess process_;

    // Process parameters
    private final double v0_, rho_, kappa_, theta_, sigma_, logS0_;
    private final Handle< YieldTermStructure > dividendYield_;
    private final Handle< YieldTermStructure > riskFreeRate_;
    private final Handle< Quote > s0_;

    // Memoization for omega_tilde(k) within a single Phi() evaluation
    private final Map< Integer, Complex > omegaTildeLookupTable_ = new HashMap<>();

    // Cutoff for Fourier integral
    private final double xiRightLimit_;

    // Integrator (eq. 23/24)
    private final GaussLegendreIntegration integrator_;

    // Mutable state set in calculate() and used by a(...)
    private double tr_t_, Tr_T_;
    private double[] tkr_tk_;

    public AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(final HestonProcess process) {
        this(process, 100.0);
    }

    public AnalyticDiscreteGeometricAveragePriceAsianHestonEngine(final HestonProcess process,
            final double xiRightLimit) {
        super();
        this.process_ = process;
        this.xiRightLimit_ = xiRightLimit;
        this.integrator_ = new GaussLegendreIntegration(128);

        this.v0_ = process_.v0().currentLink().value();
        this.rho_ = process_.rho().currentLink().value();
        this.kappa_ = process_.kappa().currentLink().value();
        this.theta_ = process_.theta().currentLink().value();
        this.sigma_ = process_.sigma().currentLink().value();
        this.s0_ = process_.s0();
        this.logS0_ = Math.log(s0_.currentLink().value());

        this.riskFreeRate_ = process_.riskFreeRate();
        this.dividendYield_ = process_.dividendYield();
    }

    private static double[] toArray(final List< Double > in) {
        final double[] out = new double[in.size()];
        for ( int i = 0; i < out.length; i++ )
            out[i] = in.get(i);
        return out;
    }

    /** Complex cosh(z) = (exp(z) + exp(-z))/2. */
    private static Complex cosh(final Complex z) {
        return z.exp().add(z.neg().exp()).mul(0.5);
    }

    /** Complex sinh(z) = (exp(z) - exp(-z))/2. */
    private static Complex sinh(final Complex z) {
        return z.exp().sub(z.neg().exp()).mul(0.5);
    }

    // ----- Equation (11): F and F_tilde -----

    /**
     * Equation (21) of Kim, Kim, Kim & Wee. Public so the Integrand can call into it.
     */
    public Complex Phi(final Complex s, final Complex w, final double t, final double T, final int kStar,
            final double[] t_n, final double[] tauK) {
        omegaTildeLookupTable_.clear();

        final int n = t_n.length;
        final Complex aTerm = a(s, w, t, T, kStar, t_n);
        final Complex omegaTerm = omega_tilde(s, w, kStar, kStar, n, tauK).mul(v0_);
        final double term3 = kappa_ * kappa_ * theta_ * (T - t) / (sigma_ * sigma_);

        Complex summation = Complex.ZERO;
        for ( int i = kStar + 1; i <= n + 1; i++ ) {
            final double dTau = tauK[i] - tauK[i - 1];
            final Complex z_k = z(s, w, i, n);
            final Complex omt = omega_tilde(s, w, i, kStar, n, tauK);
            summation = summation.add(F(z_k, omt, dTau).log());
        }
        final Complex term4 = summation.mul(2.0 * kappa_ * theta_ / (sigma_ * sigma_));

        return aTerm.add(omegaTerm).add(term3).sub(term4).exp();
    }

    @Override
    public void calculate() {
        final DiscreteAveragingAsianOption.ArgumentsImpl args = (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;

        QL.require(args.exercise.type() == Exercise.Type.European, "not an European Option");

        final double runningLog;
        final int pastFixings;
        if ( args.averageType == AverageType.Geometric ) {
            QL.require(args.runningAccumulator > 0.0,
                    "positive running product required: " + args.runningAccumulator + " not allowed");
            runningLog = Math.log(args.runningAccumulator);
            pastFixings = args.pastFixings;
        } else {
            // Used as control variate
            runningLog = 0.0;
            pastFixings = 0;
        }

        QL.require(args.payoff instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args.payoff;

        final double strike = payoff.strike();
        final Date exerciseDate = args.exercise.lastDate();
        final double expiryTime = process_.time(exerciseDate);
        QL.require(expiryTime >= 0.0, "Expiry Date cannot be in the past");

        final double expiryDcf = riskFreeRate_.currentLink().discount(expiryTime);
        final double startTime = 0.0;

        // Fixing times sorted ascending
        final List< Double > fixingTimesList = new ArrayList<>();
        for ( final Date d : args.fixingDates ) {
            fixingTimesList.add(process_.time(d));
        }
        Collections.sort(fixingTimesList);

        // tauK = sorted future fixing times with t pushed front and T pushed back
        final List< Double > tauKList = new ArrayList<>(fixingTimesList);
        tauKList.add(0, startTime);
        tauKList.add(expiryTime);

        // Past fixings dummy entries at -1
        for ( int i = 0; i < pastFixings; i++ ) {
            fixingTimesList.add(0, -1.0);
            tauKList.add(0, -1.0);
        }
        final int kStar = pastFixings;

        // Convert to arrays
        final double[] fixingTimes = toArray(fixingTimesList);
        final double[] tauK = toArray(tauKList);

        // Cached r-adjusted DCF logs for a(...)
        tr_t_ = -Math.log(
                riskFreeRate_.currentLink().discount(startTime) / dividendYield_.currentLink().discount(startTime));
        Tr_T_ = -Math.log(
                riskFreeRate_.currentLink().discount(expiryTime) / dividendYield_.currentLink().discount(expiryTime));
        tkr_tk_ = new double[fixingTimes.length];
        for ( int i = 0; i < fixingTimes.length; i++ ) {
            if ( fixingTimes[i] < 0.0 ) {
                tkr_tk_[i] = 1.0;
            } else {
                tkr_tk_[i] = -Math.log(
                        riskFreeRate_.currentLink().discount(fixingTimes[i]) / dividendYield_.currentLink()
                                .discount(fixingTimes[i]));
            }
        }

        // Adjusted strike for seasoning
        final double prefactor = Math.exp(runningLog / fixingTimes.length);
        final double adjustedStrike = strike / prefactor;

        // Term 1 — Phi(1, 0)
        final Complex phiOne = Phi(Complex.ONE, Complex.ZERO, startTime, expiryTime, kStar, fixingTimes, tauK);
        final double term1 = 0.5 * (phiOne.real() - adjustedStrike);

        // Term 2 — Fourier integral
        final Integrand integrand = new Integrand(startTime, expiryTime, kStar, fixingTimes, tauK, adjustedStrike,
                xiRightLimit_);
        final double term2 = integrator_.op(integrand) / Math.PI;

        final double value;
        switch ( payoff.optionType() ) {
        case Call:
            value = expiryDcf * prefactor * (term1 + term2);
            break;
        case Put:
            value = expiryDcf * prefactor * (-term1 + term2);
            break;
        default:
            throw new IllegalArgumentException("unknown option type");
        }

        final DiscreteAveragingAsianOption.ResultsImpl res = (DiscreteAveragingAsianOption.ResultsImpl) results_;
        res.value = value;
    }

    private Complex F(final Complex z1, final Complex z2, final double tau) {
        final Complex temp = z1.mul(-2.0 * sigma_ * sigma_).add(kappa_ * kappa_).sqrt();
        if ( Math.abs(kappa_ * kappa_ - 2.0 * sigma_ * sigma_) < 1e-8 ) {
            return Complex.ONE.add(z2.mul(-sigma_ * sigma_).add(kappa_).mul(0.5));
        } else {
            // cosh(0.5*tau*temp) + (kappa - z2*sigma^2) * sinh(0.5*tau*temp) / temp
            final Complex half = temp.mul(0.5 * tau);
            final Complex coshHalf = cosh(half);
            final Complex sinhHalf = sinh(half);
            final Complex factor = z2.mul(-sigma_ * sigma_).add(kappa_);
            return coshHalf.add(factor.mul(sinhHalf).div(temp));
        }
    }

    private Complex F_tilde(final Complex z1, final Complex z2, final double tau) {
        final Complex temp = z1.mul(-2.0 * sigma_ * sigma_).add(kappa_ * kappa_).sqrt();
        final Complex half = temp.mul(0.5 * tau);
        final Complex coshHalf = cosh(half);
        final Complex sinhHalf = sinh(half);
        // 0.5 * temp * sinh(half) + 0.5 * (kappa - z2*sigma^2) * cosh(half)
        return temp.mul(sinhHalf).mul(0.5).add(z2.mul(-sigma_ * sigma_).add(kappa_).mul(coshHalf).mul(0.5));
    }

    // ----- Equation (14): z(s, w, k, n) -----

    private Complex z(final Complex s, final Complex w, final int k, final int n) {
        final double k_ = k;
        final double n_ = n;
        // (2 rho kappa - sigma) * ((n-k+1) s + n w) / (2 sigma n)
        final Complex inner = s.mul(n_ - k_ + 1.0).add(w.mul(n_));
        final Complex term1 = inner.mul((2.0 * rho_ * kappa_ - sigma_) / (2.0 * sigma_ * n_));
        // (1 - rho^2) * ((n-k+1) s + n w)^2 / (2 n^2)
        final Complex term2 = inner.mul(inner).mul((1.0 - rho_ * rho_) / (2.0 * n_ * n_));
        return term1.add(term2);
    }

    // ----- Equation (15): omega(s, w, k, kStar, n) -----

    private Complex omega(final Complex s, final Complex w, final int k, final int kStar, final int n) {
        if ( k == kStar ) {
            return Complex.ZERO;
        } else if ( k == n + 1 ) {
            return w.mul(rho_ / sigma_);
        } else {
            return s.mul(rho_ / (sigma_ * n));
        }
    }

    // ----- Equation (16): a(s, w, t, T, kStar, t_n) — modified for non-constant rates -----

    private Complex a(final Complex s, final Complex w, final double t, final double T, final int kStar,
            final double[] t_n) {
        final double kStar_ = kStar;
        final double n_ = t_n.length;
        final double temp = -rho_ * kappa_ * theta_ / sigma_;

        double summation = 0.0;
        double summation2 = 0.0;
        for ( int i = kStar + 1; i <= t_n.length; i++ ) {
            summation += t_n[i - 1];
            summation2 += tkr_tk_[i - 1];
        }
        // term1 = (s*(n-kStar)/n + w) * (logS0 - rho v0/sigma - t*temp - tr_t)
        final Complex factor1 = s.mul((n_ - kStar_) / n_).add(w);
        final Complex term1 = factor1.mul(logS0_ - rho_ * v0_ / sigma_ - t * temp - tr_t_);
        // term2 = temp*(s*summation/n + w*T) + w*Tr_T + summation2*s/n
        final Complex term2 = s.mul(summation / n_).add(w.mul(T)).mul(temp).add(w.mul(Tr_T_))
                .add(s.mul(summation2 / n_));
        return term1.add(term2);
    }

    // ----- Equation (19): omega_tilde with memoization -----

    private Complex omega_tilde(final Complex s, final Complex w, final int k, final int kStar, final int n,
            final double[] tauK) {
        final Complex omega_k = omega(s, w, k, kStar, n);
        if ( k == n + 1 ) {
            return omega_k;
        }
        final double dTauk = tauK[k + 1] - tauK[k];
        final Complex z_kp1 = z(s, w, k + 1, n);

        final Complex omega_kp1;
        final Complex cached = omegaTildeLookupTable_.get(k + 1);
        if ( cached != null ) {
            omega_kp1 = cached;
        } else {
            omega_kp1 = omega_tilde(s, w, k + 1, kStar, n, tauK);
        }

        final Complex ratio = F_tilde(z_kp1, omega_kp1, dTauk).div(F(z_kp1, omega_kp1, dTauk));
        final Complex result = omega_k.add(kappa_ / (sigma_ * sigma_)).sub(ratio.mul(2.0 / (sigma_ * sigma_)));
        omegaTildeLookupTable_.put(k, result);
        return result;
    }

    // ----- Inner integrand -----

    private final class Integrand implements Ops.DoubleOp {
        private final double t_, T_, K_, logK_, xiRightLimit_;
        private final int kStar_;
        private final double[] t_n_, tauK_;

        Integrand(final double t, final double T, final int kStar, final double[] t_n, final double[] tauK,
                final double K, final double xiRightLimit) {
            this.t_ = t;
            this.T_ = T;
            this.kStar_ = kStar;
            this.t_n_ = t_n;
            this.tauK_ = tauK;
            this.K_ = K;
            this.logK_ = Math.log(K);
            this.xiRightLimit_ = xiRightLimit;
        }

        @Override
        public double op(final double xi) {
            final double xiDash = (0.5 + 1e-8 + 0.5 * xi) * xiRightLimit_;
            final Complex i_ = Complex.I;
            final Complex inner1 = Phi(Complex.ONE.add(i_.mul(xiDash)), Complex.ZERO, t_, T_, kStar_, t_n_, tauK_);
            final Complex inner2 = Phi(i_.mul(xiDash), Complex.ZERO, t_, T_, kStar_, t_n_, tauK_).mul(-K_);
            final Complex sum = inner1.add(inner2);
            final Complex num = i_.mul(-xiDash * logK_).exp();
            final Complex den = i_.mul(xiDash);
            return 0.5 * xiRightLimit_ * sum.mul(num).div(den).real();
        }
    }
}

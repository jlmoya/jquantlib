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
/*
 Copyright (C) 2017 Klaus Spanderen
 Copyright (C) 2022 Ignacio Anguita

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Complex;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.time.Date;

/**
 * COS-method Heston engine based on the Fang-Oosterlee Fourier-Cosine series
 * expansion of European option prices.
 *
 * <p>Phase 5h.5 port of {@code QuantLib::COSHestonEngine}
 * (v1.42.1 ql/pricingengines/vanilla/coshestonengine.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * <ul>
 *   <li>F. Fang, C.W. Oosterlee — <i>A Novel Pricing Method for European
 *       Options based on Fourier-Cosine Series Expansions.</i></li>
 *   <li>F. Le Floc'h — <i>Fourier Integration and Stochastic Volatility
 *       Calibration.</i> SSRN 2362968.</li>
 * </ul>
 *
 * <p>The engine integrates the Heston characteristic function over a
 * symmetric truncation interval {@code [a, b]} of {@code 2 L w} centered on
 * {@code mu + cum1}, expanded in {@code N} cosine basis functions. Only the
 * first two cumulants {@code c1} and {@code c2} are used to determine the
 * truncation interval (the {@code c4} contribution is left commented out in
 * C++ and similarly omitted here). All four cumulants are exposed as public
 * accessors for diagnostic / calibration use.
 *
 * <p>The Java {@link HestonModel} does not currently expose a {@code process()}
 * accessor; the engine therefore takes the {@link HestonProcess} as an
 * explicit constructor argument, mirroring the convention of
 * {@link AnalyticHestonEngine}.
 */
public class COSHestonEngine
        extends GenericModelEngine<HestonModel,
                                   OneAssetOption.Arguments,
                                   OneAssetOption.Results> {

    private final HestonProcess process_;
    private final double L_;
    private final int N_;

    private double kappa_, theta_, sigma_, rho_, v0_;

    /** Convenience constructor: {@code L=16, N=200} per C++ defaults. */
    public COSHestonEngine(final HestonModel model, final HestonProcess process) {
        this(model, process, 16.0, 200);
    }

    /**
     * @param model   Heston model (provides v0/kappa/theta/sigma/rho)
     * @param process Heston process (provides s0/discount/div/time)
     * @param L       cosine-truncation half-width multiplier (C++ default 16)
     * @param N       number of cosine terms (C++ default 200)
     */
    public COSHestonEngine(final HestonModel model, final HestonProcess process,
                           final double L, final int N) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        this.process_ = process;
        this.L_       = L;
        this.N_       = N;
        refreshFromModel();
    }

    /** Refresh cached parameters from the model — called from update(). */
    private void refreshFromModel() {
        this.kappa_ = model.kappa();
        this.theta_ = model.theta();
        this.sigma_ = model.sigma();
        this.rho_   = model.rho();
        this.v0_    = model.v0();
    }

    @Override
    public void update() {
        refreshFromModel();
        super.update();
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args =
                (OneAssetOption.ArgumentsImpl) arguments_;

        QL.require(args.exercise.type() == Exercise.Type.European,
                   "not an European option");
        QL.require(args.payoff instanceof PlainVanillaPayoff,
                   "non plain vanilla payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args.payoff;

        final Date maturityDate = args.exercise.lastDate();
        final double maturity = process_.time(maturityDate);

        final double cum1 = c1(maturity);
        final double w = Math.sqrt(Math.abs(c2(maturity)));
        // C++ also documents an optional + sqrt(|c4|) contribution, but it's
        // commented out — kept omitted here for fidelity.

        final double k = payoff.strike();
        final double spot = process_.s0().currentLink().value();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double df = process_.riskFreeRate().currentLink().discount(maturityDate);
        final double qf = process_.dividendYield().currentLink().discount(maturityDate);
        final double fwd = spot * qf / df;
        final double x = Math.log(fwd / k);

        final double a = x + cum1 - L_ * w;
        final double b = x + cum1 + L_ * w;

        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;

        // Truncation-bound bypass: when the log-moneyness falls outside the
        // half-interval, return intrinsic discounted bound.
        if (x >= b / 2.0 || x <= a / 2.0) {
            if (payoff.optionType() == Option.Type.Put) {
                res.value = Math.max(-spot * qf + k * df, 0.0);
            } else if (payoff.optionType() == Option.Type.Call) {
                res.value = Math.max(spot * qf - k * df, 0.0);
            } else {
                throw new IllegalStateException("unknown payoff type");
            }
            return;
        }

        final double d = 1.0 / (b - a);
        final double expA = Math.exp(a);

        // n=0 term
        double s = chF(0.0, maturity).real() * (expA - 1.0 - a) * d;

        for (int n = 1; n < N_; ++n) {
            final double r = n * Math.PI * d;
            final double U_n = 2.0 * d
                    * (1.0 / (1.0 + r * r)
                       * (expA + r * Math.sin(r * a) - Math.cos(r * a))
                       - 1.0 / r * Math.sin(r * a));

            // chF(r, t) * exp(i * r * (x - a)) — take the real part
            final Complex phi = chF(r, maturity);
            final double angle = r * (x - a);
            final double cos = Math.cos(angle);
            final double sin = Math.sin(angle);
            // (a + b i)(cos + sin i).real = a*cos - b*sin
            final double realPart = phi.real() * cos - phi.imag() * sin;
            s += U_n * realPart;
        }

        if (payoff.optionType() == Option.Type.Put) {
            res.value = k * df * s;
        } else if (payoff.optionType() == Option.Type.Call) {
            res.value = spot * qf - k * df * (1.0 - s);
        } else {
            throw new IllegalStateException("unknown payoff type");
        }
    }

    /** Drift contribution to {@code c1}: {@code log(qf/df)}. */
    public double muT(final double t) {
        return Math.log(process_.dividendYield().currentLink().discount(t)
                       / process_.riskFreeRate().currentLink().discount(t));
    }

    /**
     * Normalized characteristic function (Heston, Gatheral form). Mirrors
     * C++ {@code COSHestonEngine::chF(u, t)}.
     *
     * <p>Note: the C++ computes {@code D = sqrt((kappa - i rho sigma u)^2
     * + (u^2 + i u) sigma^2)}, then {@code G = (g - D)/(g + D)}, then
     * exp(...) of the Heston integrand. This is Gatheral's discontinuity-free
     * formulation.
     */
    public Complex chF(final double u, final double t) {
        final double sigma2 = sigma_ * sigma_;

        // g = kappa - i*rho*sigma*u (real = kappa, imag = -rho*sigma*u)
        final Complex g = new Complex(kappa_, -rho_ * sigma_ * u);

        // D = sqrt(g*g + (u*u + i*u)*sigma2)
        // (u*u + i*u)*sigma2 = (u*u*sigma2, u*sigma2)
        final Complex inner = new Complex(u * u * sigma2, u * sigma2);
        final Complex D = g.mul(g).add(inner).sqrt();

        final Complex gMinusD = g.sub(D);
        final Complex gPlusD  = g.add(D);
        final Complex G = gMinusD.div(gPlusD);

        // expDt = exp(-D*t)
        final Complex expDt = D.mul(-t).exp();

        // term1 = v0/sigma2 * (1 - exp(-D*t)) / (1 - G*exp(-D*t)) * (g - D)
        final Complex oneMinusExpDt = Complex.ONE.sub(expDt);
        final Complex oneMinusGExpDt = Complex.ONE.sub(G.mul(expDt));
        final Complex term1 = oneMinusExpDt.div(oneMinusGExpDt)
                .mul(gMinusD)
                .mul(v0_ / sigma2);

        // term2 = kappa*theta/sigma2 * ( (g - D)*t - 2*log( (1 - G*exp(-D*t)) / (1 - G) ) )
        final Complex oneMinusG = Complex.ONE.sub(G);
        final Complex logArg = oneMinusGExpDt.div(oneMinusG);
        final Complex term2 = gMinusD.mul(t)
                .sub(logArg.log().mul(2.0))
                .mul(kappa_ * theta_ / sigma2);

        return term1.add(term2).exp();
    }

    /** First Heston cumulant (mean of log-spot). Mathematica-emitted formula. */
    public double c1(final double t) {
        return (-theta_ + Math.exp(kappa_ * t)
                * (theta_ - kappa_ * t * theta_ - v0_) + v0_)
                / (2.0 * Math.exp(kappa_ * t) * kappa_);
    }

    /** Second Heston cumulant (variance of log-spot). */
    public double c2(final double t) {
        final double sigma2 = sigma_ * sigma_;
        final double kappa2 = kappa_ * kappa_;
        final double kappa3 = kappa2 * kappa_;
        final double ekt    = Math.exp(kappa_ * t);
        final double e2kt   = Math.exp(2.0 * kappa_ * t);

        return (sigma2 * (theta_ - 2.0 * v0_)
                + e2kt * (8.0 * kappa3 * t * theta_
                         - 8.0 * kappa2 * (theta_ + rho_ * sigma_ * t * theta_ - v0_)
                         + sigma2 * (-5.0 * theta_ + 2.0 * v0_)
                         + 2.0 * kappa_ * sigma_ * (8.0 * rho_ * theta_
                                                    + sigma_ * t * theta_
                                                    - 4.0 * rho_ * v0_))
                + 4.0 * ekt * (sigma2 * theta_
                              - 2.0 * kappa2 * (-1.0 + rho_ * sigma_ * t) * (theta_ - v0_)
                              + kappa_ * sigma_ * (sigma_ * t * (theta_ - v0_)
                                                   + 2.0 * rho_ * (-2.0 * theta_ + v0_))))
                / (8.0 * e2kt * kappa3);
    }

    /** Third Heston cumulant. */
    public double c3(final double t) {
        final double sigma2 = sigma_ * sigma_;
        final double sigma3 = sigma2 * sigma_;
        final double kappa2 = kappa_ * kappa_;
        final double kappa3 = kappa2 * kappa_;
        final double kappa4 = kappa3 * kappa_;
        final double rho2   = rho_ * rho_;
        final double ekt    = Math.exp(kappa_ * t);
        final double e2kt   = Math.exp(2.0 * kappa_ * t);
        final double e3kt   = Math.exp(3.0 * kappa_ * t);

        return -(sigma_ * (sigma3 * (theta_ - 3.0 * v0_)
                + e3kt * (2.0 * (-11.0 * sigma3
                                  - 24.0 * kappa4 * rho_ * t
                                  + 3.0 * kappa_ * sigma2 * (20.0 * rho_ + sigma_ * t)
                                  - 6.0 * kappa2 * sigma_ * (5.0 + 3.0 * rho_ * (4.0 * rho_ + sigma_ * t))
                                  + 12.0 * kappa3 * (sigma_ * t + 2.0 * rho_ * (2.0 + rho_ * sigma_ * t))) * theta_
                          - 6.0 * (2.0 * kappa_ * rho_ - sigma_)
                                * (4.0 * kappa2 - 4.0 * kappa_ * rho_ * sigma_ + sigma2) * v0_)
                + 6.0 * ekt * sigma_ * (-2.0 * kappa2 * (-1.0 + rho_ * sigma_ * t) * (theta_ - 2.0 * v0_)
                                       + sigma2 * (theta_ - v0_)
                                       + kappa_ * sigma_ * (-4.0 * rho_ * theta_
                                                           + sigma_ * t * theta_
                                                           + 6.0 * rho_ * v0_
                                                           - 2.0 * sigma_ * t * v0_))
                + 3.0 * e2kt * (2.0 * kappa_ * sigma2 * (-16.0 * rho_ * theta_
                                                       + sigma_ * t * (3.0 * theta_ - v0_))
                              + 8.0 * kappa4 * rho_ * t * (-2.0 + rho_ * sigma_ * t) * (theta_ - v0_)
                              + sigma3 * (5.0 * theta_ + v0_)
                              + 8.0 * kappa3 * (-(rho_ * (4.0 + sigma2 * t * t) * theta_)
                                               + 2.0 * sigma_ * t * (theta_ - v0_)
                                               + 2.0 * rho2 * sigma_ * t * (2.0 * theta_ - v0_)
                                               + rho_ * (2.0 + sigma2 * t * t) * v0_)
                              + 2.0 * kappa2 * sigma_ * ((8.0 + 24.0 * rho2
                                                          - 16.0 * rho_ * sigma_ * t
                                                          + sigma2 * t * t) * theta_
                                                         - (8.0 * rho2 - 8.0 * rho_ * sigma_ * t
                                                            + sigma2 * t * t) * v0_))))
                / (16.0 * e3kt * kappa_ * kappa4);
    }

    /** Fourth Heston cumulant. */
    public double c4(final double t) {
        final double sigma2 = sigma_ * sigma_;
        final double sigma3 = sigma2 * sigma_;
        final double sigma4 = sigma2 * sigma2;
        final double kappa2 = kappa_ * kappa_;
        final double kappa3 = kappa2 * kappa_;
        final double kappa4 = kappa2 * kappa2;
        final double kappa5 = kappa2 * kappa3;
        final double kappa6 = kappa3 * kappa3;
        final double kappa7 = kappa4 * kappa3;
        final double rho2   = rho_ * rho_;
        final double rho3   = rho2 * rho_;
        final double t2     = t * t;
        final double t3     = t2 * t;
        final double ekt    = Math.exp(kappa_ * t);
        final double e2kt   = Math.exp(2.0 * kappa_ * t);
        final double e3kt   = Math.exp(3.0 * kappa_ * t);
        final double e4kt   = Math.exp(4.0 * kappa_ * t);

        return (sigma2 * (3.0 * sigma4 * (theta_ - 4.0 * v0_)
                + 3.0 * e4kt * ((-93.0 * sigma4
                                + 64.0 * kappa5 * (t + 4.0 * rho2 * t)
                                + 4.0 * kappa_ * sigma3 * (176.0 * rho_ + 5.0 * sigma_ * t)
                                - 32.0 * kappa2 * sigma2 * (11.0 + 50.0 * rho2
                                                            + 5.0 * rho_ * sigma_ * t)
                                + 32.0 * kappa3 * sigma_ * (3.0 * sigma_ * t
                                                            + 4.0 * rho_ * (10.0 + 8.0 * rho2
                                                                           + 3.0 * rho_ * sigma_ * t))
                                - 32.0 * kappa4 * (5.0 + 4.0 * rho_ * (6.0 * rho_
                                                                       + (3.0 + 2.0 * rho2) * sigma_ * t))) * theta_
                              + 4.0 * (4.0 * kappa2 - 4.0 * kappa_ * rho_ * sigma_ + sigma2)
                                    * (4.0 * kappa2 * (1.0 + 4.0 * rho2)
                                      - 20.0 * kappa_ * rho_ * sigma_ + 5.0 * sigma2) * v0_)
                + 24.0 * ekt * sigma2 * (-2.0 * kappa2 * (-1.0 + rho_ * sigma_ * t) * (theta_ - 3.0 * v0_)
                                        + sigma2 * (theta_ - 2.0 * v0_)
                                        + kappa_ * sigma_ * (-4.0 * rho_ * theta_
                                                            + sigma_ * t * theta_
                                                            + 10.0 * rho_ * v0_
                                                            - 3.0 * sigma_ * t * v0_))
                + 12.0 * e2kt * (sigma4 * (7.0 * theta_ - 4.0 * v0_)
                              + 8.0 * kappa4 * (1.0 + 2.0 * rho_ * sigma_ * t * (-2.0 + rho_ * sigma_ * t)) * (theta_ - 2.0 * v0_)
                              + 2.0 * kappa_ * sigma3 * (-24.0 * rho_ * theta_
                                                       + 5.0 * sigma_ * t * theta_
                                                       + 20.0 * rho_ * v0_
                                                       - 6.0 * sigma_ * t * v0_)
                              + 4.0 * kappa2 * sigma2 * ((6.0 + 20.0 * rho2
                                                          - 14.0 * rho_ * sigma_ * t
                                                          + sigma2 * t2) * theta_
                                                         - 2.0 * (3.0 + 12.0 * rho2
                                                                  - 10.0 * rho_ * sigma_ * t
                                                                  + sigma2 * t2) * v0_)
                              + 8.0 * kappa3 * sigma_ * ((3.0 * sigma_ * t
                                                          + 2.0 * rho_ * (-4.0 + sigma_ * t * (4.0 * rho_ - sigma_ * t))) * theta_
                                                         + 2.0 * (-3.0 * sigma_ * t
                                                                  + 2.0 * rho_ * (3.0 + sigma_ * t * (-3.0 * rho_ + sigma_ * t))) * v0_))
                - 8.0 * e3kt * (16.0 * kappa6 * rho2 * t2 * (-3.0 + rho_ * sigma_ * t) * (theta_ - v0_)
                              - 3.0 * sigma4 * (7.0 * theta_ + 2.0 * v0_)
                              + 2.0 * kappa3 * sigma_ * ((192.0 * (rho_ + rho3)
                                                          - 6.0 * (9.0 + 40.0 * rho2) * sigma_ * t
                                                          + 42.0 * rho_ * sigma2 * t2
                                                          - sigma3 * t3) * theta_
                                                         + (-48.0 * rho3
                                                            + 18.0 * (1.0 + 4.0 * rho2) * sigma_ * t
                                                            - 24.0 * rho_ * sigma2 * t2
                                                            + sigma3 * t3) * v0_)
                              + 12.0 * kappa4 * ((-4.0 - 24.0 * rho2
                                                  + 8.0 * rho_ * (4.0 + 3.0 * rho2) * sigma_ * t
                                                  - (3.0 + 14.0 * rho2) * sigma2 * t2
                                                  + rho_ * sigma3 * t3) * theta_
                                                 + (8.0 * rho2
                                                    - 8.0 * rho_ * (2.0 + rho2) * sigma_ * t
                                                    + (3.0 + 8.0 * rho2) * sigma2 * t2
                                                    - rho_ * sigma3 * t3) * v0_)
                              - 6.0 * kappa2 * sigma2 * ((15.0 + 80.0 * rho2
                                                          - 35.0 * rho_ * sigma_ * t
                                                          + 2.0 * sigma2 * t2) * theta_
                                                         + (3.0 + sigma_ * t * (7.0 * rho_ - sigma_ * t)) * v0_)
                              + 24.0 * kappa5 * t * ((-2.0 + rho_ * (4.0 * sigma_ * t
                                                                    + rho_ * (-8.0 + sigma_ * t
                                                                              * (4.0 * rho_ - sigma_ * t)))) * theta_
                                                     + (2.0 + rho_ * (-4.0 * sigma_ * t
                                                                      + rho_ * (4.0 + sigma_ * t
                                                                                * (-2.0 * rho_ + sigma_ * t)))) * v0_)
                              + 3.0 * kappa_ * sigma3 * (sigma_ * t * (-9.0 * theta_ + v0_)
                                                       + 10.0 * rho_ * (6.0 * theta_ + v0_)))))
                / (64.0 * e4kt * kappa7);
    }

    /** Mean of log-spot — alias for {@link #c1(double)}. */
    public double mu(final double t) { return c1(t); }
    /** Variance of log-spot — alias for {@link #c2(double)}. */
    public double var(final double t) { return c2(t); }
    /** Skewness of log-spot. */
    public double skew(final double t) { return c3(t) / Math.pow(c2(t), 1.5); }
    /** Excess kurtosis (relative to normal) of log-spot. */
    public double kurtosis(final double t) {
        final double v = c2(t);
        return c4(t) / (v * v);
    }
}

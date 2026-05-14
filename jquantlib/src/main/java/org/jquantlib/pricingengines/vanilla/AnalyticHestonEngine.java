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
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLaguerreIntegration;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.time.Date;

/**
 * Analytic Heston-model engine based on Fourier transform.
 *
 * <p>Phase 4a.5 A.5.2 port of {@code QuantLib::AnalyticHestonEngine}
 * (v1.42.1 ql/pricingengines/vanilla/analytichestonengine.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p><b>Scope of this Java port (Gatheral-only minimal subset).</b> This
 * port covers the subset of the C++ engine that is the ground-truth
 * computation path used by the Phase 4a forward and Asian engines:
 * <ul>
 *   <li>Single constructor (HestonModel + HestonProcess + integration order)
 *       defaulting to Gauss-Laguerre quadrature with order 144.</li>
 *   <li>{@code Gatheral} complex-log formula (Gatheral 2005 / Albrecher Trap)
 *       — discontinuity-free; default and recommended.</li>
 *   <li>{@link #calculate()} via {@link #priceVanillaPayoff} computes the
 *       European call/put NPV through the Fj_Helper integrand pair (P1, P2)
 *       integrated with Gauss-Laguerre.</li>
 *   <li>{@link #chF}/{@link #lnChF} normalized characteristic functions
 *       used by control-variate / asymptotic schemes elsewhere.</li>
 *   <li>{@link #addOnTerm} hook for Bates-style jump-diffusion extensions.</li>
 * </ul>
 *
 * <p><b>Deferred to a follow-up Phase</b> (see C++ for these features):
 * <ul>
 *   <li>BranchCorrection, AndersenPiterbarg(OptCV), AsymptoticChF,
 *       AngledContour(NoCV), OptimalCV complex-log formulas.</li>
 *   <li>{@code Integration} configurator with non-Gauss-Laguerre adaptive
 *       schemes (GaussLobatto, GaussKronrod, Simpson, Trapezoid, ExpSinh,
 *       DiscreteSimpson, DiscreteTrapezoid).</li>
 *   <li>{@code AP_Helper}, {@code OptimalAlpha}, and the
 *       {@code andersenPiterbargIntegrationLimit} support routines.</li>
 *   <li>The deprecated {@code doCalculation} static entry point.</li>
 * </ul>
 *
 * <p>The Java {@link HestonModel} does not currently expose a {@code process()}
 * accessor (unlike C++); the engine therefore takes the {@link HestonProcess}
 * as an explicit constructor argument, mirroring the Java pattern already used
 * in {@link FdHestonHullWhiteVanillaEngine}. The model is the Calibrated-Model
 * source of v0/kappa/theta/sigma/rho; the process supplies s0, riskFreeRate,
 * dividendYield, and the day-counter for {@code time(maturityDate)}.
 *
 * <p>References:
 * <ul>
 *   <li>Heston (1993), <i>A Closed-Form Solution for Options with Stochastic
 *       Volatility...</i>, Review of Financial Studies, 6(2), 327-343.</li>
 *   <li>J. Gatheral (2005), <i>The Volatility Surface: A Practitioner's Guide</i>.</li>
 *   <li>Albrecher, Mayer, Schoutens, Tistaert, <i>The Little Heston Trap</i>.</li>
 * </ul>
 */
public class AnalyticHestonEngine
        extends GenericModelEngine<HestonModel,
                                   OneAssetOption.Arguments,
                                   OneAssetOption.Results> {

    /**
     * Complex-log formula choice. Only {@link #Gatheral} is implemented in
     * this Java port; the other values are placeholders for future ports
     * matching the C++ enum verbatim.
     */
    public enum ComplexLogFormula {
        /** Gatheral's discontinuity-free formulation (default). */
        Gatheral,
        /** Heston's original branch-correction formulation. Not implemented. */
        BranchCorrection,
        /** Gatheral form with Andersen-Piterbarg control variate. Not implemented. */
        AndersenPiterbarg,
        /** Same as AndersenPiterbarg but with slightly better CV. Not implemented. */
        AndersenPiterbargOptCV,
        /** Gatheral form with asymptotic-expansion control variate. Not implemented. */
        AsymptoticChF,
        /** Angled contour shift integral with control variate. Not implemented. */
        AngledContour,
        /** Angled contour shift integral without control variate. Not implemented. */
        AngledContourNoCV,
        /** Auto-selection from the above. Not implemented (would default to Gatheral). */
        OptimalCV
    }

    private final HestonProcess process_;
    private final ComplexLogFormula cpxLog_;
    private final GaussLaguerreIntegration integration_;
    private int evaluations_;

    /**
     * Convenience constructor: Gatheral formula with Gauss-Laguerre
     * quadrature of order 144 (the C++ default).
     */
    public AnalyticHestonEngine(final HestonModel model,
                                final HestonProcess process) {
        this(model, process, 144);
    }

    /**
     * Constructor using Gauss-Laguerre integration of the requested order
     * and Gatheral's version of the complex log.
     *
     * @param model           calibrated Heston model (provides v0/kappa/theta/sigma/rho)
     * @param process         the Heston process (provides s0/discount/div/time)
     * @param integrationOrder Gauss-Laguerre quadrature order, 1..192 (Phase
     *                        5h.5-Integration: GaussLaguerreIntegration now
     *                        supports arbitrary orders via Golub-Welsch). The
     *                        C++ default is 144.
     */
    public AnalyticHestonEngine(final HestonModel model,
                                final HestonProcess process,
                                final int integrationOrder) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        this.process_     = process;
        this.cpxLog_      = ComplexLogFormula.Gatheral;
        this.integration_ = new GaussLaguerreIntegration(integrationOrder);
        this.evaluations_ = 0;
    }

    /**
     * Number of integrand evaluations consumed by the most recent
     * {@link #calculate()} call. For Gauss-Laguerre this is just
     * {@code 2 * integrationOrder} (one pass per Fj integrand, j=1,2).
     */
    public int numberOfEvaluations() {
        return evaluations_;
    }

    /** {@link #process_} accessor for downstream forward / Asian engines. */
    public HestonProcess process() {
        return process_;
    }

    /** Configured complex-log formula (always {@link ComplexLogFormula#Gatheral} in this port). */
    public ComplexLogFormula complexLogFormula() {
        return cpxLog_;
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

        final Date exerciseDate = args.exercise.lastDate();

        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;
        res.value = priceVanillaPayoff(payoff, exerciseDate);
    }

    /**
     * Price a plain-vanilla payoff for the supplied {@code maturity} date.
     * Mirrors C++ {@code AnalyticHestonEngine::priceVanillaPayoff(payoff, Date)}.
     */
    public double priceVanillaPayoff(final PlainVanillaPayoff payoff,
                                     final Date maturity) {
        final double t = process_.time(maturity);
        final double fwd = process_.s0().currentLink().value()
                * process_.dividendYield().currentLink().discount(maturity)
                / process_.riskFreeRate().currentLink().discount(maturity);
        return priceVanillaPayoff(payoff, t, fwd);
    }

    /**
     * Price a plain-vanilla payoff for the supplied {@code maturity} time
     * (year fraction). Mirrors C++ {@code AnalyticHestonEngine::priceVanillaPayoff(payoff, Time)}.
     */
    public double priceVanillaPayoff(final PlainVanillaPayoff payoff,
                                     final double maturity) {
        final double fwd = process_.s0().currentLink().value()
                * process_.dividendYield().currentLink().discount(maturity)
                / process_.riskFreeRate().currentLink().discount(maturity);
        return priceVanillaPayoff(payoff, maturity, fwd);
    }

    /**
     * Internal: price under the Gatheral integration scheme (the only
     * complex-log formula implemented in this port).
     */
    private double priceVanillaPayoff(final PlainVanillaPayoff payoff,
                                      final double maturity,
                                      final double fwd) {
        final double dr = process_.riskFreeRate().currentLink().discount(maturity);

        final double strike = payoff.strike();
        final double spot   = process_.s0().currentLink().value();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double df = spot / fwd;
        final double dd = dr / df;

        final double kappa = model.kappa();
        final double sigma = model.sigma();
        final double theta = model.theta();
        final double rho   = model.rho();
        final double v0    = model.v0();

        evaluations_ = 0;

        // Gatheral: integrate Fj_Helper(j=1, 2). c_inf bound on the change of
        // variable is unused for Gauss-Laguerre (which integrates [0, ∞)
        // directly via the e^{-x} weight) but kept for fidelity with C++.
        @SuppressWarnings("unused")
        final double c_inf = Math.min(0.2, Math.max(0.0001,
                Math.sqrt(1.0 - rho * rho) / sigma)) * (v0 + kappa * theta * maturity);

        final Fj_Helper f1 = new Fj_Helper(kappa, theta, sigma, v0, spot, rho,
                this, cpxLog_, maturity, strike, df, 1);
        final Fj_Helper f2 = new Fj_Helper(kappa, theta, sigma, v0, spot, rho,
                this, cpxLog_, maturity, strike, df, 2);

        final double p1 = integration_.op(f1) / Math.PI;
        evaluations_ += integration_.order();
        final double p2 = integration_.op(f2) / Math.PI;
        evaluations_ += integration_.order();

        final double value;
        switch (payoff.optionType()) {
            case Call:
                value = spot * dd * (p1 + 0.5) - strike * dr * (p2 + 0.5);
                break;
            case Put:
                value = spot * dd * (p1 - 0.5) - strike * dr * (p2 - 0.5);
                break;
            default:
                throw new IllegalArgumentException("unknown option type");
        }
        return value;
    }

    /**
     * Normalized characteristic function {@code φ(z, t)} of the log-spot
     * (Gatheral form). Mirrors C++ {@code AnalyticHestonEngine::chF(z, t)},
     * including the small-sigma truncation at {@code sigma <= 1e-6 &&
     * kappa >= 1e-8} that uses the Taylor expansion in {@code sigma} to
     * avoid catastrophic cancellation.
     */
    public Complex chF(final Complex z, final double t) {
        if (model.sigma() > 1e-6 || model.kappa() < 1e-8) {
            return lnChF(z, t).exp();
        }
        // Small-sigma Taylor series — matches C++ verbatim.
        final double kappa = model.kappa();
        final double sigma = model.sigma();
        final double theta = model.theta();
        final double rho   = model.rho();
        final double v0    = model.v0();

        final double sigma2 = sigma * sigma;

        final double kt   = kappa * t;
        final double ekt  = Math.exp(kt);
        final double e2kt = Math.exp(2.0 * kt);
        final double rho2 = rho * rho;
        final Complex zpi = z.add(Complex.I);

        final Complex term1Arg = z.mul(zpi).mul(
                -(theta - v0 + ekt * ((-1.0 + kt) * theta + v0)) / ekt
        ).div(2.0 * kappa);
        final Complex term1 = term1Arg.exp();

        final Complex term2Arg = z.mul(zpi).mul(
                -(theta - v0 + ekt * ((-1.0 + kt) * theta + v0)) / (2.0 * ekt * kappa)
        ).add(-kt);
        final double term2Coef = rho * (2.0 * theta + kt * theta - v0 - kt * v0
                + ekt * ((-2.0 + kt) * theta + v0));
        // (1 - i*z) — note (-z.imag(), z.real()) is i*z so 1-i*z = 1 - (-z.imag()+z.real()*i)
        final Complex iz = new Complex(-z.imag(), z.real());
        final Complex one_minus_iz = Complex.ONE.sub(iz);
        final Complex term2 = term2Arg.exp()
                .mul(term2Coef)
                .mul(one_minus_iz)
                .mul(z.mul(z))
                .div(2.0 * kappa * kappa)
                .mul(sigma);

        final Complex term3Arg = z.mul(zpi).mul(
                -(theta - v0 + ekt * ((-1.0 + kt) * theta + v0)) / (2.0 * ekt * kappa)
        ).add(-2.0 * kt);
        final double thetaTermSq = squared(2.0 * theta + kt * theta - v0 - kt * v0
                + ekt * ((-2.0 + kt) * theta + v0));
        // -2*rho2*sq(...)*z*z*zpi
        final Complex p1 = z.mul(z).mul(zpi).mul(-2.0 * rho2 * thetaTermSq);
        // 2*kappa*v0*( -zpi + e2kt*(zpi + 4*rho2*z) - 2*ekt*(2*rho2*z + kt*(zpi + rho2*(2+kt)*z)) )
        final Complex p2a = zpi.neg();
        final Complex p2b = zpi.add(z.mul(4.0 * rho2)).mul(e2kt);
        final Complex p2c = z.mul(2.0 * rho2).add(zpi.add(z.mul(rho2 * (2.0 + kt))).mul(kt)).mul(2.0 * ekt);
        final Complex p2 = p2a.add(p2b).sub(p2c).mul(2.0 * kappa * v0);
        // kappa*theta*( zpi + e2kt*(-5*zpi - 24*rho2*z + 2*kt*(zpi + 4*rho2*z))
        //             + 4*ekt*(zpi + 6*rho2*z + kt*(zpi + rho2*(4+kt)*z)) )
        final Complex p3a = zpi;
        final Complex p3b = zpi.mul(-5.0).sub(z.mul(24.0 * rho2))
                .add(zpi.add(z.mul(4.0 * rho2)).mul(2.0 * kt)).mul(e2kt);
        final Complex p3c = zpi.add(z.mul(6.0 * rho2))
                .add(zpi.add(z.mul(rho2 * (4.0 + kt))).mul(kt)).mul(4.0 * ekt);
        final Complex p3 = p3a.add(p3b).add(p3c).mul(kappa * theta);
        final Complex term3 = term3Arg.exp().mul(z.mul(z).mul(zpi))
                .mul(p1.add(p2).add(p3))
                .div(16.0 * squared(squared(kappa)))
                .mul(sigma2);

        return term1.add(term2).add(term3);
    }

    /** Helper: scalar squared. */
    private static double squared(final double x) { return x * x; }

    /**
     * Logarithm of the normalized characteristic function (Gatheral /
     * Andersen-Lake form). Mirrors C++ {@code AnalyticHestonEngine::lnChF(z, t)}.
     * Uses {@code expm1} for the {@code D=0} corner case stability (here we
     * just check explicitly because Java has no complex {@code expm1}).
     */
    public Complex lnChF(final Complex z, final double t) {
        final double kappa = model.kappa();
        final double sigma = model.sigma();
        final double theta = model.theta();
        final double rho   = model.rho();
        final double v0    = model.v0();

        final double sigma2 = sigma * sigma;

        // g = kappa + rho*sigma*(im*z, -re*z) — i.e. + i*(rho*sigma)*z*(-i)
        // C++: kappa + rho*sigma * std::complex<Real>(z.imag(), -z.real())
        final Complex g = new Complex(kappa + rho * sigma * z.imag(),
                                      -rho * sigma * z.real());

        // D = sqrt(g*g + (z*z + (-z.imag(), z.real()))*sigma2)
        final Complex iz = new Complex(-z.imag(), z.real());
        final Complex inner = z.mul(z).add(iz).mul(sigma2);
        final Complex D = g.mul(g).add(inner).sqrt();

        // r = g - D, but use cancellation-safe form when g*conj(D) > 0
        Complex r = g.sub(D);
        if (g.real() * D.real() + g.imag() * D.imag() > 0.0) {
            // r = -sigma2*z*(z.real(), z.imag()+1)/(g+D)
            final Complex zPlusI = new Complex(z.real(), z.imag() + 1.0);
            r = z.mul(-sigma2).mul(zPlusI).div(g.add(D));
        }

        // y = (exp(-D*t) - 1) / (2 D), or -t/2 if D == 0
        final Complex y;
        if (D.real() != 0.0 || D.imag() != 0.0) {
            // expm1(-D*t) ≈ exp(-D*t) - 1; for complex D we just compute it directly.
            // No special expm1 is needed here in practice for AnalyticHestonEngine
            // calculate() inputs (D never numerically vanishes for real Heston params).
            final Complex em = D.mul(-t).exp().sub(1.0);
            y = em.div(D.mul(2.0));
        } else {
            y = Complex.real(-0.5 * t);
        }

        // A = kappa*theta/sigma2 * ( r*t - 2*log1p(-r*y) )
        // log1p(-r*y) = log(1 - r*y) since 1 - r*y is well-clear of 0.
        final Complex log1p_neg_ry = Complex.ONE.sub(r.mul(y)).log();
        final Complex A = r.mul(t).sub(log1p_neg_ry.mul(2.0)).mul(kappa * theta / sigma2);

        // B = z*(z.real(), z.imag()+1)*y/(1 - r*y)
        final Complex zPlusI2 = new Complex(z.real(), z.imag() + 1.0);
        final Complex B = z.mul(zPlusI2).mul(y).div(Complex.ONE.sub(r.mul(y)));

        return A.add(B.mul(v0));
    }

    /**
     * Hook for extended SV+jump-diffusion engines (Bates et al.). Returns
     * zero in the base AnalyticHestonEngine.
     */
    protected Complex addOnTerm(final double phi, final double t, final int j) {
        return Complex.ZERO;
    }

    /** Helper: complex squared. */
    private static Complex squared(final Complex x) { return x.mul(x); }

    /**
     * Internal Fj integrand (j=1, 2) for the Gatheral complex-log
     * formulation. Matches C++ {@code AnalyticHestonEngine::Fj_Helper}
     * including the {@code phi==0} l'Hospital limit branches.
     */
    private static final class Fj_Helper implements Ops.DoubleOp {
        private final int    j_;
        private final double kappa_;
        private final double theta_;
        private final double sigma_;
        private final double v0_;
        private final ComplexLogFormula cpxLog_;
        private final double term_;
        private final double x_, sx_, dd_;
        private final double sigma2_, rsigma_;
        private final double t0_;
        private final AnalyticHestonEngine engine_;

        Fj_Helper(final double kappa, final double theta, final double sigma,
                  final double v0, final double s0, final double rho,
                  final AnalyticHestonEngine engine,
                  final ComplexLogFormula cpxLog,
                  final double term, final double strike, final double ratio,
                  final int j) {
            this.j_       = j;
            this.kappa_   = kappa;
            this.theta_   = theta;
            this.sigma_   = sigma;
            this.v0_      = v0;
            this.cpxLog_  = cpxLog;
            this.term_    = term;
            this.x_       = Math.log(s0);
            this.sx_      = Math.log(strike);
            this.dd_      = x_ - Math.log(ratio);
            this.sigma2_  = sigma * sigma;
            this.rsigma_  = rho * sigma;
            this.t0_      = kappa - ((j == 1) ? rho * sigma : 0.0);
            this.engine_  = engine;
        }

        @Override
        public double op(final double phi) {
            QL.require(cpxLog_ == ComplexLogFormula.Gatheral,
                       "AnalyticHestonEngine: only Gatheral complex-log is implemented");
            final double rpsig = rsigma_ * phi;
            final Complex t1 = new Complex(t0_, -rpsig);
            // d = sqrt(t1*t1 - sigma2*phi*(-phi, j==1?1:-1))
            final Complex inner = new Complex(-phi, (j_ == 1) ? 1.0 : -1.0).mul(sigma2_ * phi);
            final Complex d  = t1.mul(t1).sub(inner).sqrt();
            final Complex ex = d.mul(-term_).exp();
            final Complex addOn = engine_ != null
                    ? engine_.addOnTerm(phi, term_, j_)
                    : Complex.ZERO;

            if (phi != 0.0) {
                if (sigma_ > 1e-5) {
                    // Standard Gatheral path
                    final Complex p = t1.sub(d).div(t1.add(d));
                    // g = log( (1 - p*ex) / (1 - p) )
                    final Complex g = Complex.ONE.sub(p.mul(ex)).div(Complex.ONE.sub(p)).log();
                    // result = exp( v0*(t1-d)*(1-ex)/(sigma2*(1-ex*p))
                    //              + kappa*theta/sigma2 * ( (t1-d)*term - 2g )
                    //              + i*phi*(dd-sx)
                    //              + addOn ).imag() / phi
                    final Complex one_minus_ex = Complex.ONE.sub(ex);
                    final Complex one_minus_exp = Complex.ONE.sub(ex.mul(p));
                    final Complex termA = t1.sub(d).mul(v0_).mul(one_minus_ex)
                            .div(one_minus_exp.mul(sigma2_));
                    final Complex termB = t1.sub(d).mul(term_).sub(g.mul(2.0))
                            .mul(kappa_ * theta_ / sigma2_);
                    final Complex termC = new Complex(0.0, phi * (dd_ - sx_));
                    final Complex sum = termA.add(termB).add(termC).add(addOn);
                    return sum.exp().imag() / phi;
                } else {
                    // Small-sigma: avoid p ≈ 1 cancellation
                    final Complex td = new Complex(-phi, (j_ == 1) ? 1.0 : -1.0)
                            .mul(phi)
                            .div(t1.mul(2.0));
                    final Complex p  = td.mul(sigma2_).div(t1.add(d));
                    final Complex g  = p.mul(Complex.ONE.sub(ex));
                    final Complex one_minus_ex = Complex.ONE.sub(ex);
                    final Complex one_minus_p_ex = Complex.ONE.sub(p.mul(ex));
                    final Complex termA = td.mul(v0_).mul(one_minus_ex).div(one_minus_p_ex);
                    final Complex termB = td.mul(term_).sub(g.mul(2.0 / sigma2_))
                            .mul(kappa_ * theta_);
                    final Complex termC = new Complex(0.0, phi * (dd_ - sx_));
                    return termA.add(termB).add(termC).add(addOn).exp().imag() / phi;
                }
            } else {
                // l'Hospital limit phi -> 0
                if (j_ == 1) {
                    final double kmr = rsigma_ - kappa_;
                    if (Math.abs(kmr) > 1e-7) {
                        return dd_ - sx_
                                + (Math.exp(kmr * term_) * kappa_ * theta_
                                   - kappa_ * theta_ * (kmr * term_ + 1.0)) / (2.0 * kmr * kmr)
                                - v0_ * (1.0 - Math.exp(kmr * term_)) / (2.0 * kmr);
                    } else {
                        // kappa = rho * sigma
                        return dd_ - sx_
                                + 0.25 * kappa_ * theta_ * term_ * term_
                                + 0.5 * v0_ * term_;
                    }
                } else {
                    return dd_ - sx_
                            - (Math.exp(-kappa_ * term_) * kappa_ * theta_
                               + kappa_ * theta_ * (kappa_ * term_ - 1.0)) / (2.0 * kappa_ * kappa_)
                            - v0_ * (1.0 - Math.exp(-kappa_ * term_)) / (2.0 * kappa_);
                }
            }
        }
    }
}

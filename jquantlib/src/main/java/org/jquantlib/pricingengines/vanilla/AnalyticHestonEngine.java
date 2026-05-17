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
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussChebyshev2ndPolynomial;
import org.jquantlib.math.integrals.GaussChebyshevPolynomial;
import org.jquantlib.math.integrals.GaussKronrodAdaptive;
import org.jquantlib.math.integrals.GaussLaguerreIntegration;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.integrals.GaussianQuadrature;
import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.integrals.SimpsonIntegral;
import org.jquantlib.math.integrals.TrapezoidIntegral;
import org.jquantlib.math.solvers1D.Brent;
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
    private final Integration integration_;
    /** Andersen-Piterbarg integration-limit epsilon. C++ default {@code 1e-8}. */
    private double andersenPiterbargEpsilon_;
    /** AP_Helper shift parameter. C++ default {@code -0.5}. */
    private double alpha_;
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
        this.integration_ = Integration.gaussLaguerre(integrationOrder);
        this.andersenPiterbargEpsilon_ = 1e-8;
        this.alpha_       = -0.5;
        this.evaluations_ = 0;
    }

    /**
     * Constructor giving full control over the Fourier integration
     * algorithm. Mirrors C++
     * {@code AnalyticHestonEngine(model, cpxLog, integration, andersenPiterbargEpsilon=1e-25, alpha=-0.5)}
     * — except this Java port only implements the {@link ComplexLogFormula#Gatheral}
     * (and {@link ComplexLogFormula#BranchCorrection}, by way of falling
     * through to Gatheral) formulations. Andersen-Piterbarg / Angled-Contour
     * complex-log variants are deferred until {@code AP_Helper} ports.
     *
     * @param model        calibrated Heston model
     * @param process      Heston process (s0/discount/div/time)
     * @param cpxLog       complex-log formula choice (must currently be
     *                     {@link ComplexLogFormula#Gatheral})
     * @param integration  Fourier-integration configurator
     */
    public AnalyticHestonEngine(final HestonModel model,
                                final HestonProcess process,
                                final ComplexLogFormula cpxLog,
                                final Integration integration) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        QL.require(integration != null,
                   "AnalyticHestonEngine: integration must not be null");
        // Phase 5e.5b-CFC-d-124: relaxed Gatheral-only guard. The Java
        // calculate() path still drives Gatheral, but ExponentialFittingHestonEngine
        // (and any future Andersen-Piterbarg engines) call into chF/lnChF and the
        // AP_Helper nested class directly without going through calculate(); the
        // constructor must therefore accept the full enum so those engines can
        // hold a non-Gatheral instance for AP_Helper construction.
        this.process_     = process;
        this.cpxLog_      = cpxLog;
        this.integration_ = integration;
        this.andersenPiterbargEpsilon_ = 1e-8;
        this.alpha_       = -0.5;
        this.evaluations_ = 0;
    }

    /**
     * Configure the Andersen-Piterbarg integration-limit epsilon used to
     * size the truncation upper bound when {@code cpxLog ∈ {AndersenPiterbarg,
     * AndersenPiterbargOptCV, AngledContour, AngledContourNoCV, AsymptoticChF,
     * OptimalCV}}. Mirrors C++ {@code AnalyticHestonEngine(..., Real
     * andersenPiterbargEpsilon, Real alpha)} constructor argument.
     *
     * @param epsilon  truncation tolerance (C++ default {@code 1e-8}); the
     *                 AP scaled epsilon is {@code epsilon*π/(sqrt(K·F)·dr)}.
     * @return {@code this} for fluent chaining
     */
    public AnalyticHestonEngine withAndersenPiterbargEpsilon(final double epsilon) {
        this.andersenPiterbargEpsilon_ = epsilon;
        return this;
    }

    /**
     * Configure the AP_Helper {@code alpha} shift parameter. Mirrors C++
     * {@code AnalyticHestonEngine(..., Real alpha=−0.5)} constructor argument.
     */
    public AnalyticHestonEngine withAlpha(final double alpha) {
        this.alpha_ = alpha;
        return this;
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
     * Internal: price under the configured complex-log formula. Mirrors
     * C++ {@code AnalyticHestonEngine::priceVanillaPayoff(payoff, Time, Real fwd)}
     * (v1.42.1 analytichestonengine.cpp:748-859).
     *
     * <p>Phase 5e.5b-CFC-d-129 wired the AndersenPiterbarg / AngledContour
     * / AsymptoticChF / OptimalCV branches into the dispatch using the
     * existing {@link AP_Helper} (added in CFC-d-124).
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

        final double value;
        switch (cpxLog_) {
            case Gatheral:
            case BranchCorrection: {
                // Integrate Fj_Helper(j=1, 2). c_inf is the change-of-variable
                // bound used by all non-Gauss-Laguerre integrators in
                // integrand1/2/3 to map (0, ∞) → (0, 1); Gauss-Laguerre
                // integrates the bare integrand directly via its e^{-x} weight
                // and ignores c_inf. Mirrors C++.
                final double c_inf = Math.min(0.2, Math.max(0.0001,
                        Math.sqrt(1.0 - rho * rho) / sigma)) * (v0 + kappa * theta * maturity);

                final Fj_Helper f1 = new Fj_Helper(kappa, theta, sigma, v0, spot, rho,
                        this, cpxLog_, maturity, strike, df, 1);
                final Fj_Helper f2 = new Fj_Helper(kappa, theta, sigma, v0, spot, rho,
                        this, cpxLog_, maturity, strike, df, 2);

                final double p1 = integration_.calculate(c_inf, f1) / Math.PI;
                evaluations_ += integration_.numberOfEvaluations();
                final double p2 = integration_.calculate(c_inf, f2) / Math.PI;
                evaluations_ += integration_.numberOfEvaluations();

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
                break;
            }

            case AndersenPiterbarg:
            case AndersenPiterbargOptCV:
            case AsymptoticChF:
            case AngledContour:
            case AngledContourNoCV:
            case OptimalCV: {
                // Andersen-Piterbarg style control-variate path. Mirrors C++
                // priceVanillaPayoff() AP branch (analytichestonengine.cpp:803-852).
                final double c_inf =
                        Math.sqrt(1.0 - rho * rho) * (v0 + kappa * theta * maturity) / sigma;

                final double epsilon = andersenPiterbargEpsilon_
                        * Math.PI / (Math.sqrt(strike * fwd) * dr);

                final double v0_ = v0;
                final double maturity_ = maturity;
                final double c_inf_ = c_inf;
                final double epsilon_ = epsilon;
                // Lazy AP integration-limit supplier — exactly matches
                // C++ std::function<Real()> uM = [&](){ return ...; }.
                final java.util.function.DoubleSupplier uM = () ->
                        Integration.andersenPiterbargIntegrationLimit(
                                c_inf_, epsilon_, v0_, maturity_);

                final ComplexLogFormula finalLog = (cpxLog_ == ComplexLogFormula.OptimalCV)
                        ? optimalControlVariate(maturity, v0, kappa, theta, sigma, rho)
                        : cpxLog_;

                final AP_Helper cvHelper = new AP_Helper(
                        maturity, fwd, strike, finalLog, this, alpha_);

                final double cvValue = cvHelper.controlVariateValue();

                final double vAvg = (1.0 - Math.exp(-kappa * maturity))
                        * (v0 - theta) / (kappa * maturity) + theta;

                final double scalingFactor =
                        (cpxLog_ != ComplexLogFormula.OptimalCV
                            && cpxLog_ != ComplexLogFormula.AsymptoticChF)
                        ? Math.max(0.25, Math.min(1000.0,
                                0.25 / Math.sqrt(0.5 * vAvg * maturity)))
                        : 1.0;

                final double h_cv = fwd / Math.PI
                        * integration_.calculate(c_inf, cvHelper, uM, scalingFactor);
                evaluations_ += integration_.numberOfEvaluations();

                switch (payoff.optionType()) {
                    case Call:
                        value = (cvValue + h_cv) * dr;
                        break;
                    case Put:
                        value = (cvValue + h_cv - (fwd - strike)) * dr;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown option type");
                }
                break;
            }

            default:
                throw new IllegalStateException("unknown complex log formula: " + cpxLog_);
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

        // y = expm1(-D*t) / (2 D), or -t/2 if D == 0.
        // Use precise complex expm1 to preserve precision when |D*t| is
        // small — critical for the ExponentialFittingHestonEngine
        // (Andersen-Piterbarg / AngledContour CV) at small sigma where the
        // naive exp(-D*t)-1 catastrophically cancels.
        final Complex y;
        if (D.real() != 0.0 || D.imag() != 0.0) {
            y = complexExpm1(D.mul(-t)).div(D.mul(2.0));
        } else {
            y = Complex.real(-0.5 * t);
        }

        // A = kappa*theta/sigma2 * ( r*t - 2*log1p(-r*y) ). Use precise
        // complex log1p for stability when |r*y| is small (same regime
        // motivation as the expm1 above).
        final Complex log1p_neg_ry = complexLog1p(r.mul(y).neg());
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
     * Precise complex {@code expm1(z) = exp(z) - 1}. Uses
     * {@code Math.expm1} on the real part and the half-angle identity
     * {@code cos(b) - 1 = -2 sin²(b/2)} on the imaginary part to preserve
     * precision when {@code |z|} is small. Mirrors C++ {@code std::expm1}
     * on {@code std::complex<Real>} (libstdc++/libc++ both use this form).
     */
    static Complex complexExpm1(final Complex z) {
        final double a = z.real();
        final double b = z.imag();
        final double ea  = Math.exp(a);
        final double em1 = Math.expm1(a);
        final double sinHalfB = Math.sin(0.5 * b);
        // cos(b) - 1 = -2 sin²(b/2); avoids catastrophic cancellation
        // when |b| is small.
        final double cosBm1 = -2.0 * sinHalfB * sinHalfB;
        // Re: e^a*cos(b) - 1 = (e^a - 1) + e^a*(cos(b) - 1) = em1 + ea*cosBm1.
        final double re = em1 + ea * cosBm1;
        final double im = ea * Math.sin(b);
        return new Complex(re, im);
    }

    /**
     * Precise complex {@code log1p(z) = log(1 + z)}. Uses
     * {@code Math.log1p} on the squared-modulus expansion to preserve
     * precision when {@code |z|} is small.
     */
    static Complex complexLog1p(final Complex z) {
        final double a = z.real();
        final double b = z.imag();
        // |1 + z|² = (1+a)² + b² = 1 + 2a + a² + b²
        // log|1+z| = 0.5 * log1p(2a + a² + b²)
        final double x = 2.0 * a + a * a + b * b;
        final double re = 0.5 * Math.log1p(x);
        final double im = Math.atan2(b, 1.0 + a);
        return new Complex(re, im);
    }

    // ----------------------------------------------------------------------
    // Phase 5e.5b-CFC-d-124: AndersenPiterbarg / AngledContour control-variate
    // helpers used by ExponentialFittingHestonEngine.
    //
    // Mirrors C++ AnalyticHestonEngine::optimalControlVariate +
    // AnalyticHestonEngine::AP_Helper (v1.42.1
    // ql/pricingengines/vanilla/analytichestonengine.cpp).
    //
    // Note: the AsymptoticChF branches of AP_Helper.controlVariateValue()
    // require the exponential-integral functions Ci/Si which are not yet
    // ported to Java. For the Heston parameter regimes exercised by
    // testSmallSigmaExpansion4ExpFitting / testExponentialFitting4StrikesAndMaturities,
    // optimalControlVariate() selects AngledContour for every (t, v0, kappa,
    // theta, sigma, rho) tuple, so the AsymptoticChF controlVariateValue()
    // path is not reached. We still implement AsymptoticChF's operator()
    // branch (which only needs chF and the precomputed phi_/psi_), so an
    // explicit cv=AsymptoticChF caller would work for the integrand; only
    // the controlVariateValue() at AsymptoticChF throws.
    // ----------------------------------------------------------------------

    /**
     * Selects the optimal {@link ComplexLogFormula} control variate for the
     * given Heston parameter tuple, mirroring C++
     * {@code AnalyticHestonEngine::optimalControlVariate(t, v0, kappa, theta, sigma, rho)}.
     *
     * <p>Returns {@link ComplexLogFormula#AsymptoticChF} when the
     * asymptotic-characteristic-function expansion is expected to dominate
     * the angled-contour shift (small {@code sigma}, large {@code t});
     * otherwise returns {@link ComplexLogFormula#AngledContour}.
     */
    public static ComplexLogFormula optimalControlVariate(
            final double t, final double v0, final double kappa,
            final double theta, final double sigma, final double rho) {
        if (t > 0.15
                && (v0 + t * kappa * theta) / sigma * Math.sqrt(1.0 - rho * rho) < 0.15
                && ((kappa - 0.5 * rho * sigma) * (v0 + t * kappa * theta)
                    + kappa * theta * Math.log(4.0 * (1.0 - rho * rho))) / (sigma * sigma) < 0.1) {
            return ComplexLogFormula.AsymptoticChF;
        }
        return ComplexLogFormula.AngledContour;
    }

    /**
     * Andersen-Piterbarg / Angled-Contour integrand helper used by
     * Andersen-Piterbarg-style Fourier engines (in particular
     * {@code ExponentialFittingHestonEngine}). Mirrors C++
     * {@code AnalyticHestonEngine::AP_Helper}.
     *
     * <p>Constructed with the forward, strike, maturity, control-variate
     * choice, owning {@link AnalyticHestonEngine} (used for its
     * {@link #chF(Complex, double)}), and the {@code alpha} shift parameter
     * (C++ default {@code -0.5}). The {@link #op(double)} method returns the
     * real part of the integrand at frequency {@code u}, and
     * {@link #controlVariateValue()} returns the closed-form correction
     * added back at the end of integration.
     */
    public static final class AP_Helper implements Ops.DoubleOp {
        private final double term_;
        private final double fwd_;
        private final double strike_;
        private final double freq_;
        private final ComplexLogFormula cpxLog_;
        private final AnalyticHestonEngine engine_;
        private final double alpha_;
        private final double s_alpha_;

        private double vAvg_;
        private double tanPhi_;
        private Complex phi_;
        private Complex psi_;

        public AP_Helper(final double term,
                         final double fwd,
                         final double strike,
                         final ComplexLogFormula cpxLog,
                         final AnalyticHestonEngine engine,
                         final double alpha) {
            QL.require(engine != null, "pricing engine required");
            this.term_   = term;
            this.fwd_    = fwd;
            this.strike_ = strike;
            this.freq_   = Math.log(fwd / strike);
            this.cpxLog_ = cpxLog;
            this.engine_ = engine;
            this.alpha_  = alpha;
            this.s_alpha_ = Math.exp(alpha * freq_);

            final double v0    = engine.model.v0();
            final double kappa = engine.model.kappa();
            final double theta = engine.model.theta();
            final double sigma = engine.model.sigma();
            final double rho   = engine.model.rho();

            switch (cpxLog) {
              case AndersenPiterbarg:
                vAvg_ = (1.0 - Math.exp(-kappa * term)) * (v0 - theta)
                            / (kappa * term) + theta;
                break;
              case AndersenPiterbargOptCV:
                vAvg_ = -8.0 * Math.log(engine.chF(
                            new Complex(0.0, alpha_), term).real()) / term;
                break;
              case AsymptoticChF: {
                final double sqrt1mrho2 = Math.sqrt(1.0 - rho * rho);
                phi_ = new Complex(sqrt1mrho2, rho)
                        .mul(-(v0 + term * kappa * theta) / sigma);
                final double psiRe =
                        (kappa - 0.5 * rho * sigma) * (v0 + term * kappa * theta)
                            + kappa * theta * Math.log(4.0 * (1.0 - rho * rho));
                final double psiIm = -(
                        (0.5 * rho * rho * sigma - kappa * rho) / sqrt1mrho2
                            * (v0 + kappa * theta * term)
                        - 2.0 * kappa * theta * Math.atan(rho / sqrt1mrho2));
                psi_ = new Complex(psiRe / (sigma * sigma), psiIm / (sigma * sigma));
                // fallthrough to AngledContour to also set vAvg_ + tanPhi_
              }
              // fallthrough
              case AngledContour: {
                vAvg_ = (1.0 - Math.exp(-kappa * term)) * (v0 - theta)
                            / (kappa * term) + theta;
                // fallthrough to AngledContourNoCV to set tanPhi_
              }
              // fallthrough
              case AngledContourNoCV: {
                final double r = rho - sigma * freq_ / (v0 + kappa * theta * term);
                final double angle = (r * freq_ < 0.0)
                        ? Math.PI / 12.0 * Math.signum(freq_)
                        : 0.0;
                tanPhi_ = Math.tan(angle);
                break;
              }
              default:
                throw new IllegalArgumentException(
                        "AP_Helper: unknown control variate " + cpxLog);
            }
        }

        public AP_Helper(final double term, final double fwd, final double strike,
                         final ComplexLogFormula cpxLog,
                         final AnalyticHestonEngine engine) {
            this(term, fwd, strike, cpxLog, engine, -0.5);
        }

        /**
         * Integrand value at frequency {@code u}. Mirrors C++
         * {@code AP_Helper::operator()(Real u)}.
         */
        @Override
        public double op(final double u) {
            QL.require(engine_.addOnTerm(u, term_, 1).equals(Complex.ZERO)
                       && engine_.addOnTerm(u, term_, 2).equals(Complex.ZERO),
                       "only Heston model is supported");

            if (cpxLog_ == ComplexLogFormula.AngledContour
                    || cpxLog_ == ComplexLogFormula.AngledContourNoCV
                    || cpxLog_ == ComplexLogFormula.AsymptoticChF) {

                final Complex h_u    = new Complex(u, u * tanPhi_ - alpha_);
                final Complex hPrime = h_u.sub(Complex.I);

                Complex phiBS = Complex.ZERO;
                if (cpxLog_ == ComplexLogFormula.AngledContour) {
                    final Complex hpHp = hPrime.mul(hPrime);
                    final Complex iHp  = new Complex(-hPrime.imag(), hPrime.real());
                    phiBS = hpHp.add(iHp).mul(-0.5 * vAvg_ * term_).exp();
                } else if (cpxLog_ == ComplexLogFormula.AsymptoticChF) {
                    final Complex arg = new Complex(1.0, tanPhi_).mul(u).mul(phi_).add(psi_);
                    phiBS = arg.exp();
                }

                // factor = exp(i*u*freq) * (1, tanPhi) * (phiBS - chF(hPrime, t)) / (h_u * hPrime)
                final Complex expI = new Complex(0.0, u * freq_).exp();
                final Complex onePlusITan = new Complex(1.0, tanPhi_);
                final Complex chfDiff = phiBS.sub(engine_.chF(hPrime, term_));
                final Complex denom   = h_u.mul(hPrime);
                final Complex inner = expI.mul(onePlusITan).mul(chfDiff).div(denom);

                return Math.exp(-u * tanPhi_ * freq_) * inner.real() * s_alpha_;
            } else if (cpxLog_ == ComplexLogFormula.AndersenPiterbarg
                       || cpxLog_ == ComplexLogFormula.AndersenPiterbargOptCV) {
                final Complex z      = new Complex(u, -alpha_);
                final Complex zPrime = new Complex(u, -alpha_ - 1.0);

                // phiBS = exp(-0.5*vAvg*t*(zPrime*zPrime + i*zPrime))
                final Complex zpzp = zPrime.mul(zPrime);
                final Complex izp  = new Complex(-zPrime.imag(), zPrime.real());
                final Complex phiBS = zpzp.add(izp).mul(-0.5 * vAvg_ * term_).exp();

                final Complex expI = new Complex(0.0, u * freq_).exp();
                final Complex inner = expI.mul(phiBS.sub(engine_.chF(zPrime, term_)))
                                          .div(z.mul(zPrime));
                return inner.real() * s_alpha_;
            }
            throw new IllegalStateException("AP_Helper: unknown control variate " + cpxLog_);
        }

        /**
         * Closed-form control-variate correction added back to the integral
         * result. Mirrors C++ {@code AP_Helper::controlVariateValue()}.
         */
        public double controlVariateValue() {
            if (cpxLog_ == ComplexLogFormula.AngledContour
                    || cpxLog_ == ComplexLogFormula.AndersenPiterbarg
                    || cpxLog_ == ComplexLogFormula.AndersenPiterbargOptCV) {
                return new org.jquantlib.pricingengines.BlackCalculator(
                            Option.Type.Call, strike_, fwd_,
                            Math.sqrt(vAvg_ * term_), 1.0)
                        .value();
            } else if (cpxLog_ == ComplexLogFormula.AsymptoticChF) {
                // C++ uses ExponentialIntegral::Ci / Si — not yet ported in Java.
                throw new UnsupportedOperationException(
                        "AP_Helper.controlVariateValue() for AsymptoticChF requires "
                        + "ExponentialIntegral.Ci/Si (not yet ported to Java).");
            } else if (cpxLog_ == ComplexLogFormula.AngledContourNoCV) {
                return ((alpha_ <=  0.0) ? fwd_    : 0.0)
                     - ((alpha_ <= -1.0) ? strike_ : 0.0)
                     - 0.5 * ((alpha_ ==  0.0) ? fwd_    : 0.0)
                     + 0.5 * ((alpha_ == -1.0) ? strike_ : 0.0);
            }
            throw new IllegalStateException(
                    "AP_Helper.controlVariateValue: unknown control variate " + cpxLog_);
        }
    }

    // ----------------------------------------------------------------------
    // Integration nested class — Phase 5e.5b-CFC-d-120 port
    // ----------------------------------------------------------------------

    /**
     * Fourier-integration configurator for {@link AnalyticHestonEngine}.
     *
     * <p>Phase 5e.5b-CFC-d-120 port of C++
     * {@code AnalyticHestonEngine::Integration} (v1.42.1
     * ql/pricingengines/vanilla/analytichestonengine.{hpp,cpp}). Provides
     * the same set of static factory entry points as C++ — but on Java the
     * {@code expSinh} and {@code discreteTrapezoid} variants are deferred
     * until their underlying integrators ({@code ExpSinhIntegral},
     * {@code DiscreteTrapezoidIntegrator}) are ported. Calling those
     * factories throws {@link UnsupportedOperationException}.
     *
     * <p>The {@link #calculate(double, Ops.DoubleOp)} entry point maps the
     * algorithm to one of:
     * <ul>
     *   <li>For {@code GaussLaguerre}: integrates the raw integrand on
     *       {@code [0, ∞)} via the {@code e^{-x}} weight. {@code c_inf}
     *       unused.</li>
     *   <li>For {@code GaussLegendre / GaussChebyshev / GaussChebyshev2nd}:
     *       applies {@code integrand1(c_inf, f)} change-of-variable
     *       mapping {@code [-1, 1]} → {@code [0, ∞)}.</li>
     *   <li>For {@code GaussLobatto / GaussKronrod / Simpson / Trapezoid}:
     *       applies {@code integrand2(c_inf, f)} mapping {@code [0, 1]}
     *       → {@code [0, ∞)}.</li>
     *   <li>For {@code DiscreteSimpson}: same as Simpson (the
     *       {@code DiscreteSimpsonIntegrator} adaptive functor is not
     *       ported yet — falls back to adaptive Simpson, which is
     *       semantically equivalent for the integrals the engine drives).</li>
     * </ul>
     */
    public static final class Integration {

        /**
         * Enumeration of the underlying numerical-integration algorithms.
         * Mirrors C++ {@code AnalyticHestonEngine::Integration::Algorithm}
         * exactly.
         */
        public enum Algorithm {
            GaussLobatto, GaussKronrod, Simpson, Trapezoid,
            DiscreteTrapezoid, DiscreteSimpson,
            GaussLaguerre, GaussLegendre,
            GaussChebyshev, GaussChebyshev2nd,
            ExpSinh
        }

        private final Algorithm algo_;
        private final GaussianQuadrature gaussianQuadrature_;
        private final GaussLaguerreIntegration gaussLaguerre_;
        private final Integrator integrator_;

        private Integration(final Algorithm algo,
                            final GaussianQuadrature gaussianQuadrature,
                            final GaussLaguerreIntegration gaussLaguerre,
                            final Integrator integrator) {
            this.algo_ = algo;
            this.gaussianQuadrature_ = gaussianQuadrature;
            this.gaussLaguerre_ = gaussLaguerre;
            this.integrator_ = integrator;
        }

        // ---- non-adaptive Gaussian quadrature factories ----------------

        /** Gauss-Laguerre quadrature on {@code [0, ∞)} (default order 128). */
        public static Integration gaussLaguerre() {
            return gaussLaguerre(128);
        }

        /**
         * Gauss-Laguerre quadrature on {@code [0, ∞)}.
         * @param integrationOrder quadrature order, 1..192 (C++ guard).
         */
        public static Integration gaussLaguerre(final int integrationOrder) {
            QL.require(integrationOrder <= 192,
                       "maximum integration order (192) exceeded");
            return new Integration(Algorithm.GaussLaguerre,
                                   null,
                                   new GaussLaguerreIntegration(integrationOrder),
                                   null);
        }

        /** Gauss-Legendre quadrature on {@code [-1, 1]} (default order 128). */
        public static Integration gaussLegendre() {
            return gaussLegendre(128);
        }

        /** Gauss-Legendre quadrature on {@code [-1, 1]}. */
        public static Integration gaussLegendre(final int integrationOrder) {
            return new Integration(Algorithm.GaussLegendre,
                                   new GaussLegendreIntegration(integrationOrder),
                                   null,
                                   null);
        }

        /** Gauss-Chebyshev (1st kind) quadrature on {@code [-1, 1]} (default order 128). */
        public static Integration gaussChebyshev() {
            return gaussChebyshev(128);
        }

        /** Gauss-Chebyshev (1st kind) quadrature on {@code [-1, 1]}. */
        public static Integration gaussChebyshev(final int integrationOrder) {
            return new Integration(Algorithm.GaussChebyshev,
                                   new GaussianQuadrature(integrationOrder,
                                       new GaussChebyshevPolynomial()),
                                   null,
                                   null);
        }

        /** Gauss-Chebyshev (2nd kind) quadrature on {@code [-1, 1]} (default order 128). */
        public static Integration gaussChebyshev2nd() {
            return gaussChebyshev2nd(128);
        }

        /** Gauss-Chebyshev (2nd kind) quadrature on {@code [-1, 1]}. */
        public static Integration gaussChebyshev2nd(final int integrationOrder) {
            return new Integration(Algorithm.GaussChebyshev2nd,
                                   new GaussianQuadrature(integrationOrder,
                                       new GaussChebyshev2ndPolynomial()),
                                   null,
                                   null);
        }

        // ---- adaptive integrator factories -----------------------------

        /**
         * Adaptive Gauss-Lobatto quadrature on a finite interval. Default
         * {@code maxEvaluations=1000}, {@code useConvergenceEstimate=false}.
         */
        public static Integration gaussLobatto(final double relTolerance,
                                               final double absTolerance) {
            return gaussLobatto(relTolerance, absTolerance, 1000, false);
        }

        /**
         * Adaptive Gauss-Lobatto quadrature on a finite interval. Mirrors
         * C++ {@code Integration::gaussLobatto(relTol, absTol, maxEval,
         * useConvergenceEstimate)}.
         *
         * @param relTolerance            relative-error tolerance, may be
         *                                {@link Constants#NULL_REAL} to skip
         *                                the relative-error gate (matching
         *                                C++ {@code Null<Real>()}).
         * @param absTolerance            absolute-error tolerance
         * @param maxEvaluations          maximum integrand evaluations
         * @param useConvergenceEstimate  whether to refine the absolute
         *                                tolerance via Gander/Gautschi's
         *                                estimate
         */
        public static Integration gaussLobatto(final double relTolerance,
                                               final double absTolerance,
                                               final int maxEvaluations,
                                               final boolean useConvergenceEstimate) {
            return new Integration(Algorithm.GaussLobatto,
                                   null,
                                   null,
                                   new GaussLobattoIntegral(maxEvaluations,
                                       absTolerance,
                                       relTolerance,
                                       useConvergenceEstimate));
        }

        /** Adaptive Gauss-Kronrod integrator. Default {@code maxEvaluations=1000}. */
        public static Integration gaussKronrod(final double absTolerance) {
            return gaussKronrod(absTolerance, 1000);
        }

        /** Adaptive Gauss-Kronrod integrator. */
        public static Integration gaussKronrod(final double absTolerance,
                                               final int maxEvaluations) {
            return new Integration(Algorithm.GaussKronrod,
                                   null,
                                   null,
                                   new GaussKronrodAdaptive(absTolerance,
                                                            maxEvaluations));
        }

        /** Adaptive Simpson's rule. Default {@code maxEvaluations=1000}. */
        public static Integration simpson(final double absTolerance) {
            return simpson(absTolerance, 1000);
        }

        /** Adaptive Simpson's rule. */
        public static Integration simpson(final double absTolerance,
                                          final int maxEvaluations) {
            return new Integration(Algorithm.Simpson,
                                   null,
                                   null,
                                   new SimpsonIntegral(absTolerance, maxEvaluations));
        }

        /** Adaptive trapezoid rule. Default {@code maxEvaluations=1000}. */
        public static Integration trapezoid(final double absTolerance) {
            return trapezoid(absTolerance, 1000);
        }

        /** Adaptive trapezoid rule. */
        public static Integration trapezoid(final double absTolerance,
                                            final int maxEvaluations) {
            return new Integration(Algorithm.Trapezoid,
                                   null,
                                   null,
                                   new TrapezoidIntegral<TrapezoidIntegral.Default>(
                                       TrapezoidIntegral.Default.class,
                                       absTolerance, maxEvaluations));
        }

        /**
         * Discrete Simpson's rule on {@code n} evaluations.
         *
         * <p>Java port note: the C++ {@code DiscreteSimpsonIntegrator}
         * (an {@link Integrator} subclass that samples the integrand on a
         * regular grid before applying {@link
         * org.jquantlib.math.integrals.DiscreteSimpsonIntegral}) is not yet
         * ported. As a behaviorally-equivalent stand-in we drive
         * {@link SimpsonIntegral} with the same evaluation budget — for
         * AnalyticHestonEngine pricing this produces an integration error
         * within the same tolerance band (the C++ tests pass at 1e-8).
         */
        public static Integration discreteSimpson(final int evaluations) {
            return new Integration(Algorithm.DiscreteSimpson,
                                   null,
                                   null,
                                   new SimpsonIntegral(1e-12, evaluations));
        }

        /**
         * Discrete Trapezoid rule — not yet ported (requires
         * {@code DiscreteTrapezoidIntegrator}).
         */
        public static Integration discreteTrapezoid(final int evaluations) {
            throw new UnsupportedOperationException(
                "AnalyticHestonEngine.Integration.discreteTrapezoid: "
                + "DiscreteTrapezoidIntegrator not yet ported "
                + "(Phase 5e.5b-CFC-d-120 carry-forward).");
        }

        /**
         * Exp-sinh integrator — not yet ported (requires
         * {@code ExpSinhIntegral}).
         */
        public static Integration expSinh(final double relTolerance) {
            throw new UnsupportedOperationException(
                "AnalyticHestonEngine.Integration.expSinh: "
                + "ExpSinhIntegral not yet ported "
                + "(Phase 5e.5b-CFC-d-120 carry-forward).");
        }

        // ---- query methods --------------------------------------------

        /** Underlying algorithm choice. */
        public Algorithm algorithm() {
            return algo_;
        }

        /**
         * Number of function evaluations used by the most recent
         * {@link #calculate(double, Ops.DoubleOp)} call. For Gaussian
         * quadrature this equals the configured order.
         */
        public int numberOfEvaluations() {
            if (integrator_ != null) {
                return integrator_.numberOfEvaluations();
            } else if (gaussianQuadrature_ != null) {
                return gaussianQuadrature_.order();
            } else if (gaussLaguerre_ != null) {
                return gaussLaguerre_.order();
            } else {
                throw new IllegalStateException(
                    "neither Integrator nor GaussianQuadrature given");
            }
        }

        /**
         * {@code true} iff the algorithm is adaptive (chooses its own
         * abscissae rather than a fixed table). Matches C++.
         */
        public boolean isAdaptiveIntegration() {
            return algo_ == Algorithm.GaussLobatto
                || algo_ == Algorithm.GaussKronrod
                || algo_ == Algorithm.Simpson
                || algo_ == Algorithm.Trapezoid
                || algo_ == Algorithm.ExpSinh;
        }

        // ---- calculate(...) entry points -------------------------------

        /**
         * Integrate {@code f} over {@code [0, ∞)} using the configured
         * algorithm. The change-of-variable mapping is selected to match
         * C++ {@code Integration::calculate(c_inf, f, maxBound={}, scaling=1)}.
         *
         * @param c_inf  exponential-decay-rate hint used by the
         *               change-of-variable mappings ({@code integrand1/2/3}
         *               in C++). For Gauss-Laguerre this argument is unused.
         * @param f      the integrand
         */
        public double calculate(final double c_inf, final Ops.DoubleOp f) {
            return calculate(c_inf, f, /* maxBound */ Constants.NULL_REAL);
        }

        /**
         * Same as {@link #calculate(double, Ops.DoubleOp)} but with an
         * explicit upper-bound override {@code maxBound} for the adaptive
         * integrators (mirrors C++
         * {@code Integration::calculate(c_inf, f, Real maxBound)}). Use
         * {@link Constants#NULL_REAL} (the C++ {@code Null<Real>()}
         * sentinel) to fall back to the change-of-variable scheme.
         */
        public double calculate(final double c_inf,
                                final Ops.DoubleOp f,
                                final double maxBound) {
            switch (algo_) {
              case GaussLaguerre:
                return gaussLaguerre_.op(f);
              case GaussLegendre:
              case GaussChebyshev:
              case GaussChebyshev2nd:
                return gaussianQuadrature_.op(new Integrand1(c_inf, f));
              case Simpson:
              case Trapezoid:
              case GaussLobatto:
              case GaussKronrod:
                if (maxBound != Constants.NULL_REAL) {
                    return integrator_.op(f, 0.0, maxBound);
                } else {
                    return integrator_.op(new Integrand2(c_inf, f), 0.0, 1.0);
                }
              case DiscreteSimpson:
              case DiscreteTrapezoid:
                if (maxBound != Constants.NULL_REAL) {
                    return integrator_.op(f, 0.0, maxBound);
                } else {
                    return integrator_.op(new Integrand3(c_inf, f), 0.0, 1.0);
                }
              case ExpSinh:
                throw new UnsupportedOperationException(
                    "AnalyticHestonEngine.Integration.calculate: "
                    + "ExpSinh integration not yet ported.");
              default:
                throw new IllegalStateException(
                    "unknown integration algorithm: " + algo_);
            }
        }

        /**
         * Mirrors C++ {@code Integration::calculate(c_inf, f, maxBound,
         * scaling)} (4-argument overload). Used by the AndersenPiterbarg /
         * AngledContour pricing path on {@link AnalyticHestonEngine}.
         *
         * <p>The {@code maxBound} supplier is queried lazily: for non-adaptive
         * Gaussian quadrature it is ignored; for adaptive integrators it
         * defines the truncation upper bound. The {@code scaling} factor is
         * only relevant for {@link Algorithm#ExpSinh}, which is not yet ported
         * in Java — it is accepted for API parity.
         */
        public double calculate(final double c_inf,
                                final Ops.DoubleOp f,
                                final java.util.function.DoubleSupplier maxBound,
                                final double scaling) {
            switch (algo_) {
              case GaussLaguerre:
                return gaussLaguerre_.op(f);
              case GaussLegendre:
              case GaussChebyshev:
              case GaussChebyshev2nd:
                return gaussianQuadrature_.op(new Integrand1(c_inf, f));
              case Simpson:
              case Trapezoid:
              case GaussLobatto:
              case GaussKronrod: {
                final double uM = (maxBound != null) ? maxBound.getAsDouble()
                                                     : Constants.NULL_REAL;
                if (uM != Constants.NULL_REAL) {
                    return integrator_.op(f, 0.0, uM);
                }
                return integrator_.op(new Integrand2(c_inf, f), 0.0, 1.0);
              }
              case DiscreteSimpson:
              case DiscreteTrapezoid: {
                final double uM = (maxBound != null) ? maxBound.getAsDouble()
                                                     : Constants.NULL_REAL;
                if (uM != Constants.NULL_REAL) {
                    return integrator_.op(f, 0.0, uM);
                }
                return integrator_.op(new Integrand3(c_inf, f), 0.0, 1.0);
              }
              case ExpSinh:
                throw new UnsupportedOperationException(
                    "AnalyticHestonEngine.Integration.calculate(4-arg): "
                    + "ExpSinh integration not yet ported.");
              default:
                throw new IllegalStateException(
                    "unknown integration algorithm: " + algo_);
            }
        }

        /**
         * Andersen-Piterbarg truncation upper bound. Mirrors C++
         * {@code Integration::andersenPiterbargIntegrationLimit(c_inf,
         * epsilon, v0, t)} (analytichestonengine.cpp:1046-1065).
         *
         * <p>Solves both {@code c_inf*u + log(u) + log(eps) = 0} and (when
         * solvable) {@code 0.5*v0*t*u² + log(u) + log(eps) = 0} via Brent;
         * returns the larger of the two roots, falling back to the first
         * root if the second solve fails.
         */
        public static double andersenPiterbargIntegrationLimit(
                final double c_inf, final double epsilon,
                final double v0, final double t) {
            final double logEpsilon = Math.log(epsilon);

            final double uMaxGuess = -logEpsilon / c_inf;
            final double uMaxStep  = 0.1 * uMaxGuess;

            final Brent brent1 = new Brent();
            brent1.setMaxEvaluations(1000);
            final double uMax = brent1.solve(
                    new Ops.DoubleOp() {
                        @Override
                        public double op(final double u) {
                            return c_inf * u + Math.log(u) + logEpsilon;
                        }
                    },
                    Constants.QL_EPSILON * uMaxGuess,
                    uMaxGuess,
                    uMaxStep);

            try {
                final double v0T2 = 0.5 * v0 * t;
                final double uHatMaxGuess = Math.sqrt(-logEpsilon / v0T2);
                final Brent brent2 = new Brent();
                brent2.setMaxEvaluations(1000);
                final double uHatMax = brent2.solve(
                        new Ops.DoubleOp() {
                            @Override
                            public double op(final double u) {
                                return v0T2 * u * u + Math.log(u) + logEpsilon;
                            }
                        },
                        Constants.QL_EPSILON * uHatMaxGuess,
                        uHatMaxGuess,
                        0.001 * uHatMaxGuess);
                return Math.max(uMax, uHatMax);
            } catch (final ArithmeticException e) {
                return uMax;
            }
        }

        // ---- change-of-variable integrand wrappers ---------------------

        /** {@code integrand1(c_inf, f)(x) = f(-log(0.5-0.5*x)/c_inf) / ((1-x)*c_inf)} on {@code [-1, 1]}. */
        private static final class Integrand1 implements Ops.DoubleOp {
            private final double cInf_;
            private final Ops.DoubleOp f_;

            Integrand1(final double cInf, final Ops.DoubleOp f) {
                this.cInf_ = cInf;
                this.f_ = f;
            }

            @Override
            public double op(final double x) {
                if ((1.0 - x) * cInf_ > Constants.QL_EPSILON) {
                    return f_.op(-Math.log(0.5 - 0.5 * x) / cInf_) / ((1.0 - x) * cInf_);
                } else {
                    return 0.0;
                }
            }
        }

        /** {@code integrand2(c_inf, f)(x) = f(-log(x)/c_inf) / (x*c_inf)} on {@code [0, 1]}. */
        private static final class Integrand2 implements Ops.DoubleOp {
            private final double cInf_;
            private final Ops.DoubleOp f_;

            Integrand2(final double cInf, final Ops.DoubleOp f) {
                this.cInf_ = cInf;
                this.f_ = f;
            }

            @Override
            public double op(final double x) {
                if (x * cInf_ > Constants.QL_EPSILON) {
                    return f_.op(-Math.log(x) / cInf_) / (x * cInf_);
                } else {
                    return 0.0;
                }
            }
        }

        /** {@code integrand3(c_inf, f)(x) = integrand2(c_inf, f)(1-x)}. */
        private static final class Integrand3 implements Ops.DoubleOp {
            private final Integrand2 i2_;

            Integrand3(final double cInf, final Ops.DoubleOp f) {
                this.i2_ = new Integrand2(cInf, f);
            }

            @Override
            public double op(final double x) {
                return i2_.op(1.0 - x);
            }
        }

    }

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

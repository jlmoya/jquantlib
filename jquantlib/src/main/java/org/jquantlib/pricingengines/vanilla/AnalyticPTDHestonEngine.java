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
import org.jquantlib.model.equity.PiecewiseTimeDependentHestonModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.TimeGrid;

/**
 * Analytic piecewise time-dependent Heston-model engine.
 *
 * <p>Phase 5e.5b-CFC-d-125 port of {@code QuantLib::AnalyticPTDHestonEngine}
 * (v1.42.1 ql/pricingengines/vanilla/analyticptdhestonengine.{hpp,cpp}).
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The engine prices European vanilla options written on a
 * {@link PiecewiseTimeDependentHestonModel} via the Heston (1993) Fourier
 * representation, with each Heston parameter (kappa, theta, sigma, rho) read
 * piecewise from the model's {@code TimeGrid}. The integrand is built up by
 * walking the time grid in reverse, accumulating the complex {@code C} and
 * {@code D} coefficients exactly as the C++ implementation does — see
 * Elices (2007) for the recursive piecewise-constant derivation.
 *
 * <p><b>Scope of this Java port (Gatheral subset).</b> Mirrors the C++
 * surface but only implements the {@link ComplexLogFormula#Gatheral}
 * complex-log formulation; the {@link ComplexLogFormula#AndersenPiterbarg}
 * branch is left unsupported pending the {@code AP_Helper} /
 * {@code andersenPiterbargIntegrationLimit} ports tracked as Phase
 * 5e.5b-CFC-d-AP carry-forward. The constructors that take an
 * {@link AnalyticHestonEngine.Integration} configurator + Andersen-Piterbarg
 * epsilon are accepted at construction time (so existing test patterns and
 * downstream callers continue to compile) but {@link #calculate()} will
 * throw {@link UnsupportedOperationException} if asked to use
 * Andersen-Piterbarg.
 *
 * <p>References:
 * <ul>
 *   <li>Heston, Steven L. (1993). <i>A Closed-Form Solution for Options with
 *       Stochastic Volatility with Applications to Bond and Currency
 *       Options.</i> Review of Financial Studies, 6(2), 327-343.</li>
 *   <li>A. Elices. <i>Models with time-dependent parameters using transform
 *       methods: application to Heston's model.</i> arXiv 0708.2020.</li>
 *   <li>J. Gatheral (2005), <i>The Volatility Surface: A Practitioner's Guide</i>.</li>
 * </ul>
 *
 * @see AnalyticHestonEngine
 * @see PiecewiseTimeDependentHestonModel
 */
public class AnalyticPTDHestonEngine
        extends GenericModelEngine<PiecewiseTimeDependentHestonModel,
                                   OneAssetOption.Arguments,
                                   OneAssetOption.Results> {

    /**
     * Complex-log formula choice. Mirrors C++
     * {@code AnalyticPTDHestonEngine::ComplexLogFormula}.
     */
    public enum ComplexLogFormula {
        /** Gatheral's discontinuity-free formulation (default). */
        Gatheral,
        /** Andersen-Piterbarg control-variate scheme. Not yet implemented. */
        AndersenPiterbarg
    }

    private final ComplexLogFormula cpxLog_;
    private final AnalyticHestonEngine.Integration integration_;
    private final double andersenPiterbargEpsilon_;
    private int evaluations_;

    /**
     * Simple constructor: adaptive Gauss-Lobatto integration with the
     * supplied tolerance and Gatheral's complex-log formula. Mirrors
     * C++ {@code AnalyticPTDHestonEngine(model, relTolerance, maxEvaluations)}.
     *
     * @param model           piecewise-time-dependent Heston model
     * @param relTolerance    relative-error tolerance for the adaptive
     *                        Gauss-Lobatto integrator
     * @param maxEvaluations  maximum integrand evaluations (be aware:
     *                        Gauss-Lobatto is recursive, so very large
     *                        budgets can blow the stack)
     */
    public AnalyticPTDHestonEngine(final PiecewiseTimeDependentHestonModel model,
                                   final double relTolerance,
                                   final int maxEvaluations) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        this.cpxLog_                   = ComplexLogFormula.Gatheral;
        this.integration_              = AnalyticHestonEngine.Integration
                .gaussLobatto(relTolerance, Constants.NULL_REAL,
                              maxEvaluations, false);
        this.andersenPiterbargEpsilon_ = Constants.NULL_REAL;
        this.evaluations_              = 0;
    }

    /**
     * Convenience constructor: Gauss-Laguerre integration of order 144
     * with Gatheral's complex-log formula. Mirrors C++
     * {@code AnalyticPTDHestonEngine(model, integrationOrder=144)}.
     */
    public AnalyticPTDHestonEngine(final PiecewiseTimeDependentHestonModel model) {
        this(model, 144);
    }

    /**
     * Constructor using Gauss-Laguerre quadrature of the requested order
     * and Gatheral's complex-log formula. Mirrors C++
     * {@code AnalyticPTDHestonEngine(model, integrationOrder)}.
     */
    public AnalyticPTDHestonEngine(final PiecewiseTimeDependentHestonModel model,
                                   final int integrationOrder) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        this.cpxLog_                   = ComplexLogFormula.Gatheral;
        this.integration_              = AnalyticHestonEngine.Integration
                .gaussLaguerre(integrationOrder);
        this.andersenPiterbargEpsilon_ = Constants.NULL_REAL;
        this.evaluations_              = 0;
    }

    /**
     * Full-control constructor giving the caller the complex-log formula
     * and the Fourier-integration configurator. Mirrors C++
     * {@code AnalyticPTDHestonEngine(model, cpxLog, itg, andersenPiterbargEpsilon)}.
     *
     * <p>In this Java port, only {@link ComplexLogFormula#Gatheral} is
     * actually implemented; passing {@link ComplexLogFormula#AndersenPiterbarg}
     * is accepted but the corresponding pricing path in {@link #calculate()}
     * throws {@link UnsupportedOperationException}.
     *
     * @param model                      piecewise time-dependent Heston model
     * @param cpxLog                     complex-log formula choice
     * @param integration                Fourier-integration configurator
     * @param andersenPiterbargEpsilon   AP control-variate tolerance (default
     *                                   in C++: 1e-8); ignored by Gatheral
     */
    public AnalyticPTDHestonEngine(final PiecewiseTimeDependentHestonModel model,
                                   final ComplexLogFormula cpxLog,
                                   final AnalyticHestonEngine.Integration integration,
                                   final double andersenPiterbargEpsilon) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        QL.require(integration != null,
                   "AnalyticPTDHestonEngine: integration must not be null");
        this.cpxLog_                   = cpxLog;
        this.integration_              = integration;
        this.andersenPiterbargEpsilon_ = andersenPiterbargEpsilon;
        this.evaluations_              = 0;
    }

    /**
     * Three-argument variant defaulting {@code andersenPiterbargEpsilon=1e-8}
     * — matches the C++ default-argument signature.
     */
    public AnalyticPTDHestonEngine(final PiecewiseTimeDependentHestonModel model,
                                   final ComplexLogFormula cpxLog,
                                   final AnalyticHestonEngine.Integration integration) {
        this(model, cpxLog, integration, 1e-8);
    }

    /**
     * Number of integrand evaluations consumed by the most recent
     * {@link #calculate()} call. For Gauss-Laguerre with the Gatheral
     * complex-log formula this is just {@code 2 * integrationOrder} (one
     * pass per Fj integrand, j=1, 2).
     */
    public int numberOfEvaluations() {
        return evaluations_;
    }

    /** Configured complex-log formula. */
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
                   "non-striked payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args.payoff;

        final double v0        = model.v0();
        final double spotPrice = model.s0();
        QL.require(spotPrice > 0.0, "negative or null underlying given");

        final double strike = payoff.strike();
        final double term = model.riskFreeRate().currentLink().dayCounter()
                .yearFraction(
                        model.riskFreeRate().currentLink().referenceDate(),
                        args.exercise.lastDate());

        final TimeGrid timeGrid = model.timeGrid();
        QL.require(timeGrid.size() > 1, "at least two model points needed");
        // C++: QL_REQUIRE(term < timeGrid.back() || close_enough(...))
        QL.require(term <= timeGrid.back() + 1e-14 * Math.max(1.0, timeGrid.back()),
                   "maturity (" + term + ") is too large, time grid is bounded by "
                   + timeGrid.back());

        final double riskFreeDiscount = model.riskFreeRate().currentLink()
                .discount(args.exercise.lastDate());
        final double dividendDiscount = model.dividendYield().currentLink()
                .discount(args.exercise.lastDate());

        // Average values across the time grid (used to set the change-of-variable
        // c_inf hint and, in the AP branch, the BS control-variate variance).
        final int n = timeGrid.size() - 1;
        double kappaAvg = 0.0, thetaAvg = 0.0, sigmaAvg = 0.0, rhoAvg = 0.0;
        for (int i = 1; i <= n; ++i) {
            final double t = 0.5 * (timeGrid.at(i - 1) + timeGrid.at(i));
            kappaAvg += model.kappa(t);
            thetaAvg += model.theta(t);
            sigmaAvg += model.sigma(t);
            rhoAvg   += model.rho(t);
        }
        kappaAvg /= n;
        thetaAvg /= n;
        sigmaAvg /= n;
        rhoAvg   /= n;

        evaluations_ = 0;

        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;

        switch (cpxLog_) {
            case Gatheral: {
                final double c_inf = Math.min(0.2, Math.max(0.0001,
                        Math.sqrt(1.0 - rhoAvg * rhoAvg) / sigmaAvg))
                        * (v0 + kappaAvg * thetaAvg * term);

                final double p1 = integration_.calculate(c_inf,
                        new FjHelper(model, term, strike, 1)) / Math.PI;
                evaluations_ += integration_.numberOfEvaluations();

                final double p2 = integration_.calculate(c_inf,
                        new FjHelper(model, term, strike, 2)) / Math.PI;
                evaluations_ += integration_.numberOfEvaluations();

                final double value;
                switch (payoff.optionType()) {
                    case Call:
                        value = spotPrice * dividendDiscount * (p1 + 0.5)
                                - strike * riskFreeDiscount * (p2 + 0.5);
                        break;
                    case Put:
                        value = spotPrice * dividendDiscount * (p1 - 0.5)
                                - strike * riskFreeDiscount * (p2 - 0.5);
                        break;
                    default:
                        throw new IllegalArgumentException("unknown option type");
                }
                res.value = value;
                break;
            }
            case AndersenPiterbarg:
                throw new UnsupportedOperationException(
                    "AnalyticPTDHestonEngine: Andersen-Piterbarg complex-log "
                    + "formula is not yet ported (Phase 5e.5b-CFC-d-AP "
                    + "carry-forward — requires AP_Helper and "
                    + "andersenPiterbargIntegrationLimit).");
            default:
                throw new IllegalStateException(
                    "unknown complex log formula: " + cpxLog_);
        }
    }

    /**
     * Normalized characteristic function {@code phi(z, T) = exp(lnChF(z, T))}
     * of the log spot, using the piecewise-time-dependent extension of the
     * Gatheral/Andersen-Lake form. Mirrors C++
     * {@code AnalyticPTDHestonEngine::chF(z, T)}.
     */
    public Complex chF(final Complex z, final double T) {
        return lnChF(z, T).exp();
    }

    /**
     * Logarithm of the normalized characteristic function. Mirrors C++
     * {@code AnalyticPTDHestonEngine::lnChF(z, T)} verbatim, including the
     * reverse-walk accumulation of the {@code C} and {@code D} complex
     * coefficients across the time grid.
     */
    public Complex lnChF(final Complex z, final double T) {
        final double v0 = model.v0();

        Complex D = Complex.ZERO;
        Complex C = Complex.ZERO;

        final TimeGrid timeGrid = model.timeGrid();
        final double lastModelTime = timeGrid.back();

        QL.require(T <= lastModelTime + 1e-14 * Math.max(1.0, lastModelTime),
                   "maturity (" + T + ") is too large, time grid is bounded by "
                   + lastModelTime);

        // lastI = lower_bound(timeGrid, T) — index of first grid point >= T.
        int lastI = 0;
        for (int i = 0; i < timeGrid.size(); ++i) {
            if (timeGrid.at(i) >= T) {
                lastI = i;
                break;
            }
            lastI = i + 1;
        }

        for (int i = lastI - 1; i >= 0; --i) {
            final double begin = timeGrid.at(i);
            final double end = Math.min(T, timeGrid.at(i + 1));
            final double tau = end - begin;

            final double t = 0.5 * (end + begin);
            final double kappa = model.kappa(t);
            final double sigma = model.sigma(t);
            final double theta = model.theta(t);
            final double rho   = model.rho(t);

            final double sigma2 = sigma * sigma;

            // k = kappa + rho*sigma*(z.imag(), -z.real())
            final Complex k = new Complex(kappa + rho * sigma * z.imag(),
                                          -rho * sigma * z.real());

            // d = sqrt(k*k + (z*z + (-z.imag(), z.real()))*sigma2)
            final Complex iz = new Complex(-z.imag(), z.real());
            final Complex inner = z.mul(z).add(iz).mul(sigma2);
            final Complex d = k.mul(k).add(inner).sqrt();

            final Complex g  = k.sub(d).div(k.add(d));
            final Complex gt = k.sub(d).sub(D.mul(sigma2))
                    .div(k.add(d).sub(D.mul(sigma2)));

            // C += kappa*theta/sigma2 * ( (k-d)*tau - 2*log( (1 - gt*exp(-d*tau)) / (1 - gt) ) )
            final Complex expMDt = d.mul(-tau).exp();
            final Complex logTerm = Complex.ONE.sub(gt.mul(expMDt))
                    .div(Complex.ONE.sub(gt)).log();
            C = C.add(k.sub(d).mul(tau).sub(logTerm.mul(2.0))
                    .mul(kappa * theta / sigma2));

            // D = (k+d)/sigma2 * (g - gt*exp(-d*tau)) / (1 - gt*exp(-d*tau))
            D = k.add(d).div(sigma2)
                    .mul(g.sub(gt.mul(expMDt)))
                    .div(Complex.ONE.sub(gt.mul(expMDt)));
        }

        return D.mul(v0).add(C);
    }

    // -----------------------------------------------------------------
    // Fj integrand for the Gatheral complex-log formulation. Mirrors C++
    // {@code AnalyticPTDHestonEngine::Fj_Helper}.
    // -----------------------------------------------------------------

    private static final class FjHelper implements Ops.DoubleOp {

        private static final double FLOAT_EPS = 1.1920929e-07; // float epsilon

        private final int j_;
        private final double term_;
        private final double v0_;
        private final double x_;
        private final double sx_;
        private final double[] r_;
        private final double[] q_;
        private final PiecewiseTimeDependentHestonModel model_;
        private final TimeGrid timeGrid_;

        FjHelper(final PiecewiseTimeDependentHestonModel model,
                 final double term,
                 final double strike,
                 final int j) {
            this.j_        = j;
            this.term_     = term;
            this.v0_       = model.v0();
            this.x_        = Math.log(model.s0());
            this.sx_       = Math.log(strike);
            this.model_    = model;
            this.timeGrid_ = model.timeGrid();

            this.r_ = new double[timeGrid_.size() - 1];
            this.q_ = new double[timeGrid_.size() - 1];

            for (int i = 0; i < timeGrid_.size() - 1; ++i) {
                final double begin = Math.min(term_, timeGrid_.at(i));
                final double end   = Math.min(term_, timeGrid_.at(i + 1));
                final InterestRate fwdR = model.riskFreeRate().currentLink()
                        .forwardRate(begin, end,
                                Compounding.Continuous,
                                Frequency.NoFrequency);
                final InterestRate fwdQ = model.dividendYield().currentLink()
                        .forwardRate(begin, end,
                                Compounding.Continuous,
                                Frequency.NoFrequency);
                r_[i] = fwdR.rate();
                q_[i] = fwdQ.rate();
            }
        }

        @Override
        public double op(double phi) {
            // Avoid numeric overflow for phi -> 0 (matches C++ float-epsilon clamp).
            phi = Math.max(FLOAT_EPS, phi);

            Complex D = Complex.ZERO;
            Complex C = Complex.ZERO;

            for (int i = timeGrid_.size() - 1; i > 0; --i) {
                final double begin = timeGrid_.at(i - 1);
                if (begin < term_) {
                    final double end = Math.min(term_, timeGrid_.at(i));
                    final double tau = end - begin;
                    final double t   = 0.5 * (end + begin);

                    final double rho   = model_.rho(t);
                    final double sigma = model_.sigma(t);
                    final double kappa = model_.kappa(t);
                    final double theta = model_.theta(t);

                    final double sigma2 = sigma * sigma;
                    final double t0     = kappa - ((j_ == 1) ? (rho * sigma) : 0.0);
                    final double rpsig  = rho * sigma * phi;

                    final Complex t1 = new Complex(t0, -rpsig);
                    // d = sqrt( t1*t1 - sigma2*phi*(-phi, j==1?1:-1) )
                    final Complex inner = new Complex(-phi,
                            (j_ == 1) ? 1.0 : -1.0).mul(sigma2 * phi);
                    final Complex d = t1.mul(t1).sub(inner).sqrt();

                    final Complex g  = t1.sub(d).div(t1.add(d));
                    final Complex gt = t1.sub(d).sub(D.mul(sigma2))
                            .div(t1.add(d).sub(D.mul(sigma2)));

                    final Complex expMDt = d.mul(-tau).exp();

                    // D_{i-1} = (t1+d)/sigma2 * (g - gt*exp(-d*tau)) / (1 - gt*exp(-d*tau))
                    D = t1.add(d).div(sigma2)
                            .mul(g.sub(gt.mul(expMDt)))
                            .div(Complex.ONE.sub(gt.mul(expMDt)));

                    // lng = log( (1 - gt*exp(-d*tau)) / (1 - gt) )
                    final Complex lng = Complex.ONE.sub(gt.mul(expMDt))
                            .div(Complex.ONE.sub(gt)).log();

                    // C += kappa*theta/sigma2 * ( (t1-d)*tau - 2 lng )
                    //      + i*phi*(r-q)*tau
                    C = t1.sub(d).mul(tau).sub(lng.mul(2.0))
                            .mul(kappa * theta / sigma2)
                            .add(new Complex(0.0, phi * (r_[i - 1] - q_[i - 1]) * tau))
                            .add(C);
                }
            }

            // Result: exp( v0*D + C + i*phi*(x - sx) ).imag() / phi
            final Complex sum = D.mul(v0_).add(C)
                    .add(new Complex(0.0, phi * (x_ - sx_)));
            return sum.exp().imag() / phi;
        }
    }
}

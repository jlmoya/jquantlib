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
 Copyright (C) 2012 Klaus Spanderen
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.math.Complex;
import org.jquantlib.math.distributions.GammaFunction;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.processes.HestonProcess;

/**
 * Analytic Heston-Hull-White engine based on the H1-HW (Grzelak-Oosterlee) approximation.
 *
 * <p>Phase 5h.5-HHW WI-4 port of {@code QuantLib::AnalyticH1HWEngine}
 * (v1.42.1 ql/pricingengines/vanilla/analytich1hwengine.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Prices a European option on the joint dynamics where the equity rate
 * correlation is non-zero (extending the basic Heston-HW engine which assumes that correlation is zero):
 * <pre>
 *   dS = (r - d) S dt + sqrt(v) S dW1
 *   dv = kappa (theta - v) dt + sigma sqrt(v) dW2
 *   dr = (theta(t) - a r) dt + eta dW3
 *   dW1 dW2 = rho_{S,v} dt
 *   dW1 dW3 = rho_{S,r} dt    (>= 0; required for stability of the H1-HW
 *                              Fourier integration)
 *   dW2 dW3 = 0
 * </pre>
 *
 * <p>Engine extends {@link AnalyticHestonHullWhiteEngine} and overrides
 * {@link #addOnTerm} to add a Grzelak-Oosterlee {@code I4} expansion term computed by the inner {@link FjHelper}. The
 * Fj helper uses {@link GammaFunction#logValue} for the {@code Lambda} infinite-series approximation.
 *
 * <p>References:
 * <ul>
 *   <li>L.A. Grzelak, C.W. Oosterlee, <i>On The Heston Model with
 *       Stochastic Interest Rates</i>.</li>
 *   <li>L.A. Grzelak, <i>Equity and Foreign Exchange Hybrid Models for
 *       Pricing Long-Maturity Financial Derivatives</i>.</li>
 * </ul>
 *
 * @category vanillaengines
 */
public class AnalyticH1HWEngine extends AnalyticHestonHullWhiteEngine {

    private final double rhoSr_;

    public AnalyticH1HWEngine(final HestonModel model, final HestonProcess hestonProcess,
            final HullWhite hullWhiteModel, final double rhoSr) {
        this(model, hestonProcess, hullWhiteModel, rhoSr, 128);
    }

    public AnalyticH1HWEngine(final HestonModel model, final HestonProcess hestonProcess,
            final HullWhite hullWhiteModel, final double rhoSr, final int integrationOrder) {
        super(model, hestonProcess, hullWhiteModel, integrationOrder);
        QL.require(rhoSr >= 0.0,
                "Fourier integration is not stable if " + "the equity interest rate correlation is negative");
        this.rhoSr_ = rhoSr;
    }

    @Override
    protected Complex addOnTerm(final double u, final double t, final int j) {
        final Complex base = super.addOnTerm(u, t, j);
        // Grzelak-Oosterlee H1-HW correction. The strike argument is unused
        // by the helper (mirrors the C++ signature).
        final FjHelper helper = new FjHelper(this, rhoSr_, t, 0.0, j);
        return base.add(helper.value(u));
    }

    /** Helper accessor for unit tests. */
    protected double rhoSr() {
        return rhoSr_;
    }

    /**
     * Inner integration helper for the Grzelak-Oosterlee H1-HW expansion. Mirrors C++
     * {@code AnalyticH1HWEngine::Fj_Helper}.
     */
    private static final class FjHelper {

        private static final GammaFunction GAMMA = new GammaFunction();
        private static final int MAX_ITER = 1000;

        private final int j_;
        private final double lambda_, eta_;
        private final double v0_, kappa_, theta_, gamma_;
        private final double d_;
        private final double rhoSr_;
        private final double term_;

        FjHelper(final AnalyticH1HWEngine engine, final double rhoSr, final double term,
                @SuppressWarnings( "unused" ) final double strike, final int j) {
            this.j_ = j;
            // Use the cached HW parameters from the parent engine.
            this.lambda_ = engine.aHW();
            this.eta_ = engine.sigmaHW();
            // Heston model accessors are direct scalars.
            this.v0_ = engine.model.v0();
            this.kappa_ = engine.model.kappa();
            this.theta_ = engine.model.theta();
            this.gamma_ = engine.model.sigma();
            this.d_ = 4.0 * kappa_ * theta_ / (gamma_ * gamma_);
            this.rhoSr_ = rhoSr;
            this.term_ = term;
        }

        /** {@code c(t) = gamma^2/(4 kappa) * (1 - exp(-kappa*t))}. */
        private double c(final double t) {
            return gamma_ * gamma_ / (4.0 * kappa_) * (1.0 - Math.exp(-kappa_ * t));
        }

        /** {@code lambda(t) = 4 kappa v0 exp(-kappa t) / (gamma^2 (1 - exp(-kappa t)))}. */
        private double lambda(final double t) {
            return 4.0 * kappa_ * v0_ * Math.exp(-kappa_ * t) / (gamma_ * gamma_ * (1.0 - Math.exp(-kappa_ * t)));
        }

        /** Closed-form approximation to {@code Lambda(t)}. */
        private double lambdaApprox(final double t) {
            return Math.sqrt(c(t) * (lambda(t) - 1.0) + c(t) * d_ * (1.0 + 1.0 / (2.0 * (d_ + lambda(t)))));
        }

        /** Series expansion of {@code Lambda(t)} via the GammaFunction. */
        private double lambdaSeries(final double t) {
            final double lambdaT = lambda(t);
            int i = 0;
            double retVal = 0.0;
            double s;
            do {
                final double k = i;
                s = Math.exp(
                        k * Math.log(0.5 * lambdaT) + GAMMA.logValue(0.5 * (1.0 + d_) + k) - GAMMA.logValue(k + 1.0)
                                - GAMMA.logValue(0.5 * d_ + k));
                retVal += s;
                ++i;
            } while ( s > Math.ulp(1.0f) && i < MAX_ITER );

            QL.require(i < MAX_ITER, "can not calculate Lambda");
            retVal *= Math.sqrt(2.0 * c(t)) * Math.exp(-0.5 * lambdaT);
            return retVal;
        }

        /** {@code I4(u)} contribution to the Heston Fj integrand. */
        Complex value(final double u) {
            final double gamma2 = gamma_ * gamma_;
            final double a, b, c;

            if ( 8.0 * kappa_ * theta_ / gamma2 > 1.0 ) {
                a = Math.sqrt(theta_ - gamma2 / (8.0 * kappa_));
                b = Math.sqrt(v0_) - a;
                c = -Math.log((lambdaApprox(1.0) - a) / b);
            } else {
                a = Math.sqrt(gamma2 / (2.0 * kappa_)) * Math.exp(
                        GAMMA.logValue(0.5 * (d_ + 1.0)) - GAMMA.logValue(0.5 * d_));
                final double t1 = 0.0;
                final double t2 = 1.0 / kappa_;
                final double lambdaT1 = Math.sqrt(v0_);
                final double lambdaT2 = lambdaSeries(t2);
                c = Math.log((lambdaT2 - a) / (lambdaT1 - a)) / (t1 - t2);
                b = Math.exp(c * t1) * (lambdaT1 - a);
            }

            // I4 = -1/lambda * (u^2, ((j==1) ? -u : u))
            //         * ( b/c*(1 - exp(-c*term))
            //           + a*term + a/lambda*(exp(-lambda*term)-1)
            //           + b/(c-lambda)*exp(-c*term)*(1 - exp(-term*(lambda-c))) )
            final double imagPart = ((j_ == 1) ? -u : u);
            final Complex coeff = new Complex(u * u, imagPart);

            final double bracket =
                    b / c * (1.0 - Math.exp(-c * term_)) + a * term_ + a / lambda_ * (Math.exp(-lambda_ * term_) - 1.0)
                            + b / (c - lambda_) * Math.exp(-c * term_) * (1.0 - Math.exp(-term_ * (lambda_ - c)));

            final Complex i4 = coeff.mul(-1.0 / lambda_).mul(bracket);
            return i4.mul(eta_ * rhoSr_);
        }
    }
}

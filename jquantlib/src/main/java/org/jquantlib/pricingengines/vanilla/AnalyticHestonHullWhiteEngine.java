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
 Copyright (C) 2007 Klaus Spanderen
 Copyright (C) 2007 StatPro Italia srl
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Constants;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.processes.HestonProcess;

/**
 * Analytic Heston engine including stochastic interest rates (Hull-White).
 *
 * <p>Phase 5h.5-HHW WI-3 port of {@code QuantLib::AnalyticHestonHullWhiteEngine}
 * (v1.42.1 ql/pricingengines/vanilla/analytichestonhullwhiteengine.{hpp,cpp}).
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Prices a European option on the joint dynamics
 * <pre>
 *   dS = (r - d) S dt + sqrt(v) S dW1
 *   dv = kappa (theta - v) dt + sigma sqrt(v) dW2
 *   dr = (theta(t) - a r) dt + eta dW3
 *   dW1 dW2 = rho dt,   dW1 dW3 = 0,   dW2 dW3 = 0
 * </pre>
 *
 * <p>The engine extends {@link AnalyticHestonEngine} (Gauss-Laguerre /
 * Gatheral) and overrides {@link #addOnTerm} to inject the Hull-White
 * convexity correction
 *
 * <p>{@code addOn(u, t, j) = (-m * u^2, u * (m - 2 m (j - 1)))}
 *
 * <p>where {@code m} is precomputed in {@link #calculate()} from the
 * Hull-White parameters and the integration cap-off as in
 * Sepp 2003 / In't Hout-Bierkens-Ploeg-Panhuis.
 *
 * <p>References:
 * <ul>
 *   <li>K. in't Hout, J. Bierkens, A. von der Ploeg, J. in't Panhuis,
 *       <i>A semi closed-form analytic pricing formula for call options in a
 *       hybrid Heston-Hull-White model</i>.</li>
 *   <li>A. Sepp, <i>Pricing European-Style Options under Jump Diffusion
 *       Processes with Stochastic Volatility: Applications of Fourier
 *       Transform</i>.</li>
 * </ul>
 *
 * @category vanillaengines
 */
public class AnalyticHestonHullWhiteEngine extends AnalyticHestonEngine {

    protected final HullWhite hullWhiteModel_;

    /** Mutable: recomputed at each {@link #calculate()} call. */
    private double m_;
    /** Mutable: cached Hull-White parameters synced via {@link #setParameters()}. */
    private double a_;
    private double sigma_;

    /**
     * Default Gauss-Laguerre quadrature constructor.
     *
     * <p>C++ defaults to {@code integrationOrder = 144}; this Java port
     * uses 128 because that is the only order with an embedded
     * Gauss-Laguerre table in {@link org.jquantlib.math.integrals.GaussLaguerreIntegration}
     * (see Phase 4a.5 A.5.2 design note in {@link AnalyticHestonEngine}).
     * For Gatheral-form Heston integrands convergence is well achieved
     * past order 64.
     */
    public AnalyticHestonHullWhiteEngine(final HestonModel hestonModel,
                                         final HestonProcess hestonProcess,
                                         final HullWhite hullWhiteModel) {
        this(hestonModel, hestonProcess, hullWhiteModel, 128);
    }

    public AnalyticHestonHullWhiteEngine(final HestonModel hestonModel,
                                         final HestonProcess hestonProcess,
                                         final HullWhite hullWhiteModel,
                                         final int integrationOrder) {
        super(hestonModel, hestonProcess, integrationOrder);
        QL.require(hullWhiteModel != null, "no Hull-White model specified");
        this.hullWhiteModel_ = hullWhiteModel;
        setParameters();
        this.hullWhiteModel_.addObserver(this);
    }

    @Override
    public void update() {
        setParameters();
        super.update();
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args =
                (OneAssetOption.ArgumentsImpl) arguments_;
        final double t = process().time(args.exercise.lastDate());
        if (a_ * t > Math.pow(Constants.QL_EPSILON, 0.25)) {
            m_ = sigma_ * sigma_ / (2.0 * a_ * a_)
                    * (t + 2.0 / a_ * Math.exp(-a_ * t)
                            - 1.0 / (2.0 * a_) * Math.exp(-2.0 * a_ * t)
                            - 3.0 / (2.0 * a_));
        } else {
            // low-a algebraic limit (matches C++ verbatim)
            m_ = 0.5 * sigma_ * sigma_ * t * t * t
                    * (1.0 / 3.0 - 0.25 * a_ * t + 7.0 / 60.0 * a_ * a_ * t * t);
        }
        super.calculate();
    }

    /**
     * Hull-White correction term injected into the Gatheral integrand.
     * Mirrors C++ {@code AnalyticHestonHullWhiteEngine::addOnTerm}:
     * <pre>
     *   addOn(u, t, j) = (-m * u^2,  u * (m - 2 m (j-1)))
     * </pre>
     */
    @Override
    protected Complex addOnTerm(final double u, final double t, final int j) {
        return new Complex(-m_ * u * u, u * (m_ - 2.0 * m_ * (j - 1)));
    }

    /** Hull-White {@code a} accessor (test hook). */
    protected double aHW() { return a_; }

    /** Hull-White {@code sigma} accessor (test hook). */
    protected double sigmaHW() { return sigma_; }

    /** {@code m} precomputed at the most recent calculate() (test hook). */
    protected double m() { return m_; }

    private void setParameters() {
        a_ = hullWhiteModel_.params().get(0);
        sigma_ = hullWhiteModel_.params().get(1);
    }
}

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
 Copyright (C) 2010 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/
package org.jquantlib.model.equity;

import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.CalibratedModel;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.model.Parameter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.TimeGrid;

/**
 * Piecewise-time-dependent Heston model.
 *
 * <p>Phase 5h.5 port of {@code QuantLib::PiecewiseTimeDependentHestonModel}
 * (v1.42.1 ql/models/equity/piecewisetimedependenthestonmodel.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * <ul>
 *   <li>Heston, Steven L. (1993) — <i>A Closed-Form Solution for Options
 *       with Stochastic Volatility with Applications to Bond and Currency
 *       Options.</i> Review of Financial Studies, 6(2), 327-343.</li>
 *   <li>A. Elices — <i>Models with time-dependent parameters using transform
 *       methods: application to Heston's model.</i> arXiv 0708.2020.</li>
 * </ul>
 *
 * <p>Unlike the standard {@link HestonModel} which carries scalar Heston
 * parameters, this model wraps each of {@code theta}, {@code kappa},
 * {@code sigma}, {@code rho} as a {@link Parameter} that can be queried at
 * any time {@code t}. The variance {@code v0} remains a constant.
 *
 * <p>Used as the model argument for {@code AnalyticPTDHestonEngine} (Phase
 * 5h.5b carry-forward — engine port deferred).
 */
public class PiecewiseTimeDependentHestonModel extends CalibratedModel {

    private final Handle< Quote > s0_;
    private final Handle< YieldTermStructure > riskFreeRate_;
    private final Handle< YieldTermStructure > dividendYield_;
    private final TimeGrid timeGrid_;

    /**
     * @param riskFreeRate  handle to the risk-free yield curve
     * @param dividendYield handle to the dividend yield curve
     * @param s0            handle to the spot quote
     * @param v0            initial variance (constant)
     * @param theta         time-dependent long-term variance level
     * @param kappa         time-dependent variance mean-reversion speed
     * @param sigma         time-dependent vol-of-vol
     * @param rho           time-dependent spot/variance correlation
     * @param timeGrid      time grid over which the parameters are piecewise constant
     */
    public PiecewiseTimeDependentHestonModel(final Handle< YieldTermStructure > riskFreeRate,
            final Handle< YieldTermStructure > dividendYield, final Handle< Quote > s0, final double v0,
            final Parameter theta, final Parameter kappa, final Parameter sigma, final Parameter rho,
            final TimeGrid timeGrid) {
        super(5);
        this.s0_ = s0;
        this.riskFreeRate_ = riskFreeRate;
        this.dividendYield_ = dividendYield;
        this.timeGrid_ = timeGrid;

        arguments_.set(0, theta);
        arguments_.set(1, kappa);
        arguments_.set(2, sigma);
        arguments_.set(3, rho);
        arguments_.set(4, new ConstantParameter(v0, new PositiveConstraint()));

        // Mirror C++ registerWith calls: notify on s0/riskFree/dividend changes.
        s0.addObserver(this);
        riskFreeRate.addObserver(this);
        dividendYield.addObserver(this);
    }

    /** variance long-term level at time {@code t}. */
    public double theta(final double t) {
        return arguments_.get(0).get(t);
    }

    /** variance mean-reversion speed at time {@code t}. */
    public double kappa(final double t) {
        return arguments_.get(1).get(t);
    }

    /** vol-of-vol at time {@code t}. */
    public double sigma(final double t) {
        return arguments_.get(2).get(t);
    }

    /** spot/variance correlation at time {@code t}. */
    public double rho(final double t) {
        return arguments_.get(3).get(t);
    }

    /** spot variance (constant). */
    public double v0() {
        return arguments_.get(4).get(0.0);
    }

    /** spot price. */
    public double s0() {
        return s0_.currentLink().value();
    }

    /** time grid over which parameters are piecewise constant. */
    public TimeGrid timeGrid() {
        return timeGrid_;
    }

    /** dividend yield handle. */
    public Handle< YieldTermStructure > dividendYield() {
        return dividendYield_;
    }

    /** risk-free rate handle. */
    public Handle< YieldTermStructure > riskFreeRate() {
        return riskFreeRate_;
    }

    /** Spot quote handle. */
    public Handle< Quote > s0Handle() {
        return s0_;
    }

    @Override
    protected void generateArguments() {
        // No-op: parameters are passed in directly and don't need linking
        // (unlike HestonModel which links into a HestonProcess). Subclasses
        // can override.
    }
}

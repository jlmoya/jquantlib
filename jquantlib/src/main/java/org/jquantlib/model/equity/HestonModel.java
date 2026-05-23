/*
 Copyright (C) 2009 Ueli Hofstetter

 This source code is release under the BSD License.

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

package org.jquantlib.model.equity;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.CalibratedModel;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;

//! Heston model for the stochastic volatility of an asset
/*! References:

 Heston, Steven L., 1993. A Closed-Form Solution for Options
 with Stochastic Volatility with Applications to Bond and
 Currency Options.  The review of Financial Studies, Volume 6,
 Issue 2, 327-343.

 \test calibration is tested against known good values.
 */

/**
 * Implementation of the Heston Model, see http://en.wikipedia.org/wiki/Heston_model
 */

// TODO: code review :: license, class comments, comments for access modifiers, comments for @Override
public class HestonModel extends CalibratedModel {

    /** Calibration target — needed by SLV calibrators (Phase 5h.5-SLV-b). */
    private final HestonProcess process_;
    protected RelinkableHandle< Quote > v0_, kappa_, theta_, sigma_, rho_;

    public HestonModel(final HestonProcess process) {
        super(5);
        this.process_ = process;
        this.v0_ = process.v0();
        this.kappa_ = process.kappa();
        this.theta_ = process.theta();
        this.sigma_ = process.sigma();
        this.rho_ = process.rho();
        arguments_.set(0, new ConstantParameter(process.theta().currentLink().value(), new PositiveConstraint()));
        arguments_.set(1, new ConstantParameter(process.kappa().currentLink().value(), new PositiveConstraint()));
        arguments_.set(2, new ConstantParameter(process.sigma().currentLink().value(), new PositiveConstraint()));
        arguments_.set(3,
                new ConstantParameter(process.rho().currentLink().value(), new BoundaryConstraint(-1.0, 1.0)));
        arguments_.set(4, new ConstantParameter(process.v0().currentLink().value(), new PositiveConstraint()));

    }

    /**
     * Returns the underlying {@link HestonProcess} held by this calibrated model. Mirrors the C++
     * {@code HestonModel::process()} accessor. Required by Heston SLV calibrators (Phase 5h.5-SLV-b).
     */
    public HestonProcess process() {
        return process_;
    }

    @Override
    public void generateArguments() {
        v0_.linkTo(new SimpleQuote((SimpleQuote) v0_.currentLink()));
        kappa_.linkTo(new SimpleQuote((SimpleQuote) kappa_.currentLink()));
        theta_.linkTo(new SimpleQuote((SimpleQuote) theta_.currentLink()));
        sigma_.linkTo(new SimpleQuote((SimpleQuote) sigma_.currentLink()));
        rho_.linkTo(new SimpleQuote((SimpleQuote) rho_.currentLink()));
    }

    // variance mean version level
    public double theta() {
        return arguments_.get(0).get(0.0);
    }

    // variance mean reversion speed
    public double kappa() {
        return arguments_.get(1).get(0.0);
    }

    // volatility of the volatility
    public double sigma() {
        return arguments_.get(2).get(0.0);
    }

    // correlation
    public double rho() {
        return arguments_.get(3).get(0.0);
    }

    // spot variance
    public double v0() {
        return arguments_.get(4).get(0.0);
    }

    //
    // public nested classes
    //

    /**
     * Feller-condition constraint for Heston-model calibration: requires {@code sigma >= 0} and
     * {@code sigma * sigma < 2 * kappa * theta}.
     *
     * <p>Mirrors C++ v1.42.1 {@code HestonModel::FellerConstraint} (nested class declared in
     * {@code ql/models/equity/hestonmodel.hpp}).
     */
    public static class FellerConstraint extends Constraint {

        public FellerConstraint() {
            super.impl = new Impl();
        }

        private class Impl extends Constraint.Impl {

            @Override
            public boolean test(final Array params) {
                final double theta = params.get(0);
                final double kappa = params.get(1);
                final double sigma = params.get(2);
                return (sigma >= 0.0 && sigma * sigma < 2.0 * kappa * theta);
            }
        }
    }
}

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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

/*
 Copyright (C) 2008 Yee Man Chan

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.model.equity;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.BoundaryConstraint;
import org.jquantlib.math.optimization.CompositeConstraint;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.CalibratedModel;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.processes.GjrGarchProcess;

/**
 * GJR-GARCH model for the stochastic volatility of an asset.
 *
 * <p>Faithful Java port of C++ QuantLib v1.42.1
 * {@code ql/models/equity/gjrgarchmodel.{hpp,cpp}}.
 *
 * <p>Reference: Glosten, L., Jagannathan, R., Runkle, D., 1993.
 * <i>Relationship between the expected value and the volatility of the
 * nominal excess return on stocks.</i> Journal of Finance 48, 1779-1801.
 *
 * <p>The six calibratable arguments are: {@code omega} (positive),
 * {@code alpha} in [0, 1], {@code beta} in [0, 1], {@code gamma} in
 * [-1, 1], {@code lambda} (unconstrained), {@code v0} (positive). An
 * additional composite {@code VolatilityConstraint} enforces
 * {@code beta + gamma >= 0}.
 */
public class GjrGarchModel extends CalibratedModel {

    private GjrGarchProcess process_;

    public GjrGarchModel(final GjrGarchProcess process) {
        super(6);
        this.process_ = process;
        arguments_.set(0, new ConstantParameter(process.omega(),  new PositiveConstraint()));
        arguments_.set(1, new ConstantParameter(process.alpha(),  new BoundaryConstraint( 0.0, 1.0)));
        arguments_.set(2, new ConstantParameter(process.beta(),   new BoundaryConstraint( 0.0, 1.0)));
        arguments_.set(3, new ConstantParameter(process.gamma(),  new BoundaryConstraint(-1.0, 1.0)));
        arguments_.set(4, new ConstantParameter(process.lambda(), new NoConstraint()));
        arguments_.set(5, new ConstantParameter(process.v0(),     new PositiveConstraint()));

        // Pre-existing constraint_ (PrivateConstraint validating each
        // argument's own Constraint) plus a VolatilityConstraint enforcing
        // beta + gamma >= 0. Mirrors C++ v1.42.1 gjrgarchmodel.cpp:58-60.
        this.constraint_ = new CompositeConstraint(this.constraint_, new VolatilityConstraint());

        generateArguments();

        process.riskFreeRate().addObserver(this);
        process.dividendYield().addObserver(this);
        process.s0().addObserver(this);
    }

    /** Underlying process; rebuilt by {@link #generateArguments()} after each set. */
    public GjrGarchProcess process() {
        return process_;
    }

    // variance mean reversion level multiplied by the proportion not
    // accounted for by alpha, beta and gamma
    public double omega()  { return arguments_.get(0).get(0.0); }

    // proportion attributed to the impact of all innovations
    public double alpha()  { return arguments_.get(1).get(0.0); }

    // proportion attributed to the impact of previous variance
    public double beta()   { return arguments_.get(2).get(0.0); }

    // proportion attributed to the impact of negative innovations
    public double gamma()  { return arguments_.get(3).get(0.0); }

    // market price of risk
    public double lambda() { return arguments_.get(4).get(0.0); }

    // spot variance
    public double v0()     { return arguments_.get(5).get(0.0); }

    @Override
    public void generateArguments() {
        // Mirrors C++ gjrgarchmodel.cpp:68-76: rebuild the process from the
        // freshly-set argument values so that downstream engines see the
        // current parameter vector.
        process_ = new GjrGarchProcess(
                process_.riskFreeRate(),
                process_.dividendYield(),
                process_.s0(),
                v0(), omega(),
                alpha(), beta(),
                gamma(), lambda(),
                process_.daysPerYear());
    }

    /**
     * Composite-extension constraint enforcing {@code beta + gamma >= 0}
     * over the full 6-argument vector. Mirrors C++
     * {@code GJRGARCHModel::VolatilityConstraint} (gjrgarchmodel.cpp:26-41).
     */
    public static final class VolatilityConstraint extends Constraint {
        public VolatilityConstraint() {
            // The PositiveConstraint / BoundaryConstraint pattern: assign
            // the inner Impl directly after the implicit super() call. The
            // enclosing-instance ($this) of Constraint is already bound.
            super.impl = new Impl();
        }
        private class Impl extends Constraint.Impl {
            @Override
            public boolean test(final Array params) {
                final double beta = params.get(2);
                final double gamma = params.get(3);
                return beta + gamma >= 0.0;
            }
        }
    }
}

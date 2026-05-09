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
 */
package org.jquantlib.model.equity;

import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.model.ConstantParameter;
import org.jquantlib.model.NullParameter;
import org.jquantlib.processes.HestonProcess;

/**
 * BatesModel variant with deterministic jump intensity.
 *
 * <p>Phase 5h.5-Bates promotion of the previously private/buggy nested
 * {@code BatesModel.BatesDetJumpModel} to a public top-level class
 * matching the C++ class layout. Mirrors v1.42.1
 * {@code ql/models/equity/batesmodel.hpp} BatesDetJumpModel.
 */
public class BatesDetJumpModel extends BatesModel {

    public BatesDetJumpModel(final HestonProcess process) {
        this(process, 0.1, 0.0, 0.1, 1.0, 0.1);
    }

    public BatesDetJumpModel(final HestonProcess process,
                             final double lambda, final double nu, final double delta,
                             final double kappaLambda, final double thetaLambda) {
        super(process, lambda, nu, delta);
        // Match C++ arguments_.resize(10): extend by 2 NullParameter slots.
        while (arguments_.size() < 10) {
            arguments_.add(new NullParameter());
        }
        arguments_.set(8, new ConstantParameter(kappaLambda, new PositiveConstraint()));
        arguments_.set(9, new ConstantParameter(thetaLambda, new PositiveConstraint()));
    }

    public double kappaLambda() {
        return arguments_.get(8).get(0.0);
    }

    public double thetaLambda() {
        return arguments_.get(9).get(0.0);
    }
}

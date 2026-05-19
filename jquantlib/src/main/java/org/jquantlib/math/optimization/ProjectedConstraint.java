/*
 Copyright (C) 2013 Peter Caspers
 Copyright (C) 2026 Jose Moya (Java port)

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.optimization;

import org.jquantlib.math.matrixutilities.Array;

/**
 * Projected constraint — wraps another {@link Constraint} so it operates on the projected (free-only) parameter space.
 *
 * <p>Faithful port of QuantLib C++ v1.42.1
 * {@code ql/math/optimization/projectedconstraint.hpp}. Used by {@code CalibratedModel::calibrate} when a
 * {@code fixParameters} mask (or a {@code Projection}) is supplied to freeze some calibration parameters.
 */
public class ProjectedConstraint extends Constraint {

    //-- ProjectedConstraint(const Constraint& constraint,
    //--                     const Array& parameterValues,
    //--                     const std::vector<bool>& fixParameters);
    //-- in ql/math/optimization/projectedconstraint.hpp:62
    public ProjectedConstraint(final Constraint constraint, final Array parameterValues,
            final boolean[] fixParameters) {
        super.impl = new Impl(constraint, new Projection(parameterValues, fixParameters));
    }

    //-- ProjectedConstraint(const Constraint& constraint,
    //--                     const Projection& projection);
    //-- in ql/math/optimization/projectedconstraint.hpp:69
    public ProjectedConstraint(final Constraint constraint, final Projection projection) {
        super.impl = new Impl(constraint, projection);
    }

    //
    // private inner classes
    //

    private final class Impl extends Constraint.Impl {

        private final Constraint constraint_;
        private final Projection projection_;

        private Impl(final Constraint constraint, final Projection projection) {
            this.constraint_ = constraint;
            this.projection_ = projection;
        }

        @Override
        public boolean test(final Array params) /* @ReadOnly */ {
            return constraint_.test(projection_.include(params));
        }

        @Override
        public Array upperBound(final Array params) /* @ReadOnly */ {
            return projection_.project(constraint_.upperBound(projection_.include(params)));
        }

        @Override
        public Array lowerBound(final Array params) /* @ReadOnly */ {
            return projection_.project(constraint_.lowerBound(projection_.include(params)));
        }
    }
}

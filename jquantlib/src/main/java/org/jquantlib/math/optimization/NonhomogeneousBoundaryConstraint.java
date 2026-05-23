/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.optimization;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Constraint imposing the i-th argument to be in {@code [low_i, high_i]} for all i.
 * <p>
 * Faithful port of QuantLib v1.42.1 {@code ql/math/optimization/constraint.hpp}
 * ({@code NonhomogeneousBoundaryConstraint}).
 *
 * @author Jose Moya
 */
public class NonhomogeneousBoundaryConstraint extends Constraint {

    public NonhomogeneousBoundaryConstraint(final Array low, final Array high) {
        super.impl = new Impl(low, high);
    }

    private class Impl extends Constraint.Impl {

        private final Array low_;
        private final Array high_;

        private Impl(final Array low, final Array high) {
            QL.ensure(low.size() == high.size(),
                    "Upper and lower boundaries sizes are inconsistent.");
            this.low_ = low;
            this.high_ = high;
        }

        @Override
        public boolean test(final Array params) /* @ReadOnly */ {
            QL.ensure(params.size() == low_.size(),
                    "Number of parameters and boundaries sizes are inconsistent.");
            for ( int i = 0; i < params.size(); ++i ) {
                if ( (params.get(i) < low_.get(i)) || (params.get(i) > high_.get(i)) ) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Array upperBound(final Array params) /* @ReadOnly */ {
            return high_;
        }

        @Override
        public Array lowerBound(final Array params) /* @ReadOnly */ {
            return low_;
        }
    }
}

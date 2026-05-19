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
 Copyright (C) 2010 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.math.interpolations.factories;

import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.MixedLinearCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Mixed linear/cubic interpolation factory and traits.
 * <p>
 * Mirrors C++ {@code MixedLinearCubic} in {@code ql/math/interpolations/mixedinterpolation.hpp} (v1.42.1, lines
 * 96-130).
 *
 * @see MixedLinearCubicInterpolation
 */
public class MixedLinearCubic implements Interpolation.Interpolator {

    private final int n;
    private final MixedLinearCubicInterpolation.Behavior behavior;
    private final CubicInterpolation.DerivativeApprox da;
    private final boolean monotonic;
    private final CubicInterpolation.BoundaryCondition leftType;
    private final double leftValue;
    private final CubicInterpolation.BoundaryCondition rightType;
    private final double rightValue;

    public MixedLinearCubic(final int n, final MixedLinearCubicInterpolation.Behavior behavior,
            final CubicInterpolation.DerivativeApprox da) {
        this(n, behavior, da, true, CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
    }

    public MixedLinearCubic(final int n, final MixedLinearCubicInterpolation.Behavior behavior,
            final CubicInterpolation.DerivativeApprox da, final boolean monotonic,
            final CubicInterpolation.BoundaryCondition leftCondition, final double leftConditionValue,
            final CubicInterpolation.BoundaryCondition rightCondition, final double rightConditionValue) {
        this.n = n;
        this.behavior = behavior;
        this.da = da;
        this.monotonic = monotonic;
        this.leftType = leftCondition;
        this.leftValue = leftConditionValue;
        this.rightType = rightCondition;
        this.rightValue = rightConditionValue;
    }

    @Override
    public final boolean global() {
        return true;
    }

    @Override
    public final int requiredPoints() {
        return 3;
    }

    @Override
    public final Interpolation interpolate(final Array vx, final Array vy) /* @ReadOnly */ {
        return new MixedLinearCubicInterpolation(vx, vy, n, behavior, da, monotonic, leftType, leftValue, rightType,
                rightValue);
    }
}

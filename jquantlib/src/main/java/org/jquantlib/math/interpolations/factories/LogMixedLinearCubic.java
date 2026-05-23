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

package org.jquantlib.math.interpolations.factories;

import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.LogMixedLinearCubicInterpolation;
import org.jquantlib.math.interpolations.MixedLinearCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Log-mixed-linear-cubic interpolation factory and traits.
 * <p>
 * Mirrors C++ {@code LogMixedLinearCubic} in
 * {@code ql/math/interpolations/loginterpolation.hpp} (v1.42.1, lines 269-302).
 *
 * @see LogMixedLinearCubicInterpolation
 */
public class LogMixedLinearCubic implements Interpolation.Interpolator {

    private final int n;
    private final MixedLinearCubicInterpolation.Behavior behavior;
    private final CubicInterpolation.DerivativeApprox da;
    private final boolean monotonic;
    private final CubicInterpolation.BoundaryCondition leftType;
    private final double leftValue;
    private final CubicInterpolation.BoundaryCondition rightType;
    private final double rightValue;

    public LogMixedLinearCubic(final int n, final MixedLinearCubicInterpolation.Behavior behavior,
            final CubicInterpolation.DerivativeApprox da) {
        this(n, behavior, da, true, CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
    }

    public LogMixedLinearCubic(final int n, final MixedLinearCubicInterpolation.Behavior behavior,
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
        return new LogMixedLinearCubicInterpolation(vx, vy, n, behavior, da, monotonic, leftType, leftValue, rightType,
                rightValue);
    }
}

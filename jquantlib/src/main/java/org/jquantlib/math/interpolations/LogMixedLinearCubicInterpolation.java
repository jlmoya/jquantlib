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

package org.jquantlib.math.interpolations;

import org.jquantlib.math.interpolations.factories.MixedLinearCubic;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Log-mixed-linear-cubic interpolation between discrete points.
 * <p>
 * Applies a {@link MixedLinearCubicInterpolation} on the natural logarithm of {@code y}.
 * <p>
 * Mirrors C++ {@code LogMixedLinearCubicInterpolation} in
 * {@code ql/math/interpolations/loginterpolation.hpp} (v1.42.1, lines 245-265).
 *
 * @author JQuantLib migration contributors
 */
public class LogMixedLinearCubicInterpolation extends AbstractInterpolation {

    public LogMixedLinearCubicInterpolation(final Array vx, final Array vy, final int n,
            final MixedLinearCubicInterpolation.Behavior behavior, final CubicInterpolation.DerivativeApprox da,
            final boolean monotonic, final CubicInterpolation.BoundaryCondition leftCondition,
            final double leftConditionValue, final CubicInterpolation.BoundaryCondition rightCondition,
            final double rightConditionValue) {
        super.impl = new AbstractInterpolation.LogInterpolationImpl(vx, vy,
                new MixedLinearCubic(n, behavior, da, monotonic, leftCondition, leftConditionValue, rightCondition,
                        rightConditionValue));
        super.impl.update();
    }
}

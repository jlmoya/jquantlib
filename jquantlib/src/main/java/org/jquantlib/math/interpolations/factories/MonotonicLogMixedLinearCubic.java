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
import org.jquantlib.math.interpolations.MixedLinearCubicInterpolation;

/**
 * Monotonic Spline log-mixed-linear-cubic interpolation factory.
 * <p>
 * Mirrors C++ {@code MonotonicLogMixedLinearCubic} in
 * {@code ql/math/interpolations/loginterpolation.hpp} (v1.42.1, lines 319-328).
 *
 * @author JQuantLib migration contributors
 */
public class MonotonicLogMixedLinearCubic extends LogMixedLinearCubic {

    public MonotonicLogMixedLinearCubic(final int n) {
        this(n, MixedLinearCubicInterpolation.Behavior.ShareRanges);
    }

    public MonotonicLogMixedLinearCubic(final int n, final MixedLinearCubicInterpolation.Behavior behavior) {
        super(n, behavior, CubicInterpolation.DerivativeApprox.Spline, true,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
    }
}

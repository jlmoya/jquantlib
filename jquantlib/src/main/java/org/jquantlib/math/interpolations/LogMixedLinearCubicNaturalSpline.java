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

import org.jquantlib.math.matrixutilities.Array;

/**
 * Log-mixed-linear-cubic natural spline (Spline derivative, non-monotonic) over the log of y.
 * <p>
 * Mirrors C++ {@code LogMixedLinearCubicNaturalSpline} in
 * {@code ql/math/interpolations/loginterpolation.hpp} (v1.42.1, lines 342-354).
 *
 * @author JQuantLib migration contributors
 */
public class LogMixedLinearCubicNaturalSpline extends LogMixedLinearCubicInterpolation {

    public LogMixedLinearCubicNaturalSpline(final Array vx, final Array vy, final int n) {
        this(vx, vy, n, MixedLinearCubicInterpolation.Behavior.ShareRanges);
    }

    public LogMixedLinearCubicNaturalSpline(final Array vx, final Array vy, final int n,
            final MixedLinearCubicInterpolation.Behavior behavior) {
        super(vx, vy, n, behavior, CubicInterpolation.DerivativeApprox.Spline, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
    }
}

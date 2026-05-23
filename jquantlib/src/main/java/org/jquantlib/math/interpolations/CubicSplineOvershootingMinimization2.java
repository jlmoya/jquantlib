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
 * Cubic spline with overshooting minimization on the 2nd derivative.
 * <p>
 * Convenience subclass of {@link CubicInterpolation} configured for the
 * {@link CubicInterpolation.DerivativeApprox#SplineOM2 SplineOM2} non-local derivative scheme
 * with second-derivative natural boundary conditions and Hyman monotonicity filtering disabled.
 * <p>
 * Mirrors C++ {@code CubicSplineOvershootingMinimization2} in
 * {@code ql/math/interpolations/cubicinterpolation.hpp} (v1.42.1, lines 245-256).
 *
 * @author JQuantLib migration contributors
 */
public class CubicSplineOvershootingMinimization2 extends CubicInterpolation {

    /**
     * @pre the {@code x} values must be sorted.
     */
    public CubicSplineOvershootingMinimization2(final Array vx, final Array vy) {
        super(vx, vy, DerivativeApprox.SplineOM2, false, BoundaryCondition.SecondDerivative, 0.0,
                BoundaryCondition.SecondDerivative, 0.0);
    }
}

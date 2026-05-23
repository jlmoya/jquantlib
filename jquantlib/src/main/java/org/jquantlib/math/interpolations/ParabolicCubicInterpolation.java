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
 * Parabolic cubic interpolation between discrete points.
 * <p>
 * Convenience subclass of {@link CubicInterpolation} configured for the Parabolic derivative approximation
 * (local, non-monotonic, linear) with second-derivative natural boundary conditions
 * and Hyman monotonicity filtering disabled.
 * <p>
 * Mirrors C++ {@code Parabolic} in {@code ql/math/interpolations/cubicinterpolation.hpp}
 * (v1.42.1, lines 310-321). Named {@code ParabolicCubicInterpolation} in Java to avoid
 * collision with the {@link CubicInterpolation.DerivativeApprox#Parabolic} enum constant.
 *
 * @author JQuantLib migration contributors
 */
public class ParabolicCubicInterpolation extends CubicInterpolation {

    /**
     * @pre the {@code x} values must be sorted.
     */
    public ParabolicCubicInterpolation(final Array vx, final Array vy) {
        super(vx, vy, DerivativeApprox.Parabolic, false, BoundaryCondition.SecondDerivative, 0.0,
                BoundaryCondition.SecondDerivative, 0.0);
    }
}

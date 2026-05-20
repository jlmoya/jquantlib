/*
 Copyright (C) 2026 JQuantLib Migration

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

import org.jquantlib.math.interpolations.CubicInterpolation.BoundaryCondition;
import org.jquantlib.math.interpolations.CubicInterpolation.DerivativeApprox;

/**
 * MonotonicLogCubic interpolation factory and traits.
 * <p>
 * Convenience subclass of {@link LogCubic} that fixes the derivative-approximation
 * scheme to {@code Spline} with monotonicity enforced and symmetric
 * second-derivative boundary conditions equal to zero. This mirrors the
 * QuantLib C++ {@code MonotonicLogCubic} class in
 * {@code ql/math/interpolations/loginterpolation.hpp} (v1.42.1).
 */
public class MonotonicLogCubic extends LogCubic {

    public MonotonicLogCubic() {
        super(DerivativeApprox.Spline, true,
              BoundaryCondition.SecondDerivative, 0.0,
              BoundaryCondition.SecondDerivative, 0.0);
    }

}

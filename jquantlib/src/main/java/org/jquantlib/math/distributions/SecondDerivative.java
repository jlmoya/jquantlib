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

package org.jquantlib.math.distributions;

/**
 * Functor that exposes both first and second derivatives.
 *
 * <p>Java port of the implicit C++ requirement on functors used by
 * {@code QuantLib v1.42.1 ql/math/solvers1d/halley.hpp}: the functor
 * must implement {@code Real operator()(Real)},
 * {@code Real derivative(Real)}, and {@code Real secondDerivative(Real)}.
 *
 * <p>Mirrors {@link Derivative} (which adds {@code derivative(double)} on top
 * of {@link org.jquantlib.math.Ops.DoubleOp}) and adds the second-derivative
 * accessor needed by Halley's method.
 *
 * @author JQuantLib
 */
public interface SecondDerivative extends Derivative {

    /**
     * Computes the second derivative of the function at {@code x}.
     *
     * @param x evaluation point
     * @return f''(x)
     */
    public double secondDerivative(final double x) /* ReadOnly */;
}

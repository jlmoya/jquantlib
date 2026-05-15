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

package org.jquantlib.math.solvers1D;

import org.jquantlib.math.AbstractSolver1D;
import org.jquantlib.math.distributions.Derivative;
import org.jquantlib.math.distributions.SecondDerivative;

/**
 * Halley 1-D solver.
 *
 * <p>Java port of {@code QuantLib v1.42.1 ql/math/solvers1d/halley.hpp}
 * (Phase 5e.5b-CFC-d-16a). The Halley update uses both the first and second
 * derivatives of the function:
 *
 * <pre>
 *   L_f(x) = f(x) * f''(x) / (f'(x))^2
 *   step  = (1 / (1 - 0.5 * L_f(x))) * f(x) / f'(x)
 *   x_new = x - step
 * </pre>
 *
 * <p>Like {@link Newton}, when the iterate jumps out of the bracket
 * {@code [xMin, xMax]} the solver hands off to {@link NewtonSafe} for
 * the remainder of the budget.
 *
 * @see Book: <i>Press, Teukolsky, Vetterling, and Flannery,
 *      "Numerical Recipes in C", 2nd edition, Cambridge University Press</i>
 *
 * @author JQuantLib
 */
public class Halley extends AbstractSolver1D<SecondDerivative> {

    /**
     * Computes the roots of a function by using Halley's method.
     *
     * @param f the function (must expose first and second derivatives)
     * @param xAccuracy the provided accuracy
     * @return the root
     */
    @Override
    protected double solveImpl(final SecondDerivative f, final double xAccuracy) {

        while (++evaluationNumber <= getMaxEvaluations()) {
            final double fx = f.op(root);
            final double fPrime = f.derivative(root);
            final double lf = fx * f.secondDerivative(root) / (fPrime * fPrime);
            final double step = 1.0 / (1.0 - 0.5 * lf) * fx / fPrime;
            root -= step;

            // jumped out of brackets, switch to NewtonSafe
            if ((xMin - root) * (root - xMax) < 0.0) {
                final NewtonSafe s = new NewtonSafe();
                s.setMaxEvaluations(getMaxEvaluations() - evaluationNumber);
                return s.solve((Derivative) f, xAccuracy, root + step, xMin, xMax);
            }

            if (Math.abs(step) < xAccuracy) {
                f.op(root);
                ++evaluationNumber;
                return root;
            }
        }
        throw new ArithmeticException("maximum number of function evaluations ("
                + getMaxEvaluations() + ") exceeded");
    }
}

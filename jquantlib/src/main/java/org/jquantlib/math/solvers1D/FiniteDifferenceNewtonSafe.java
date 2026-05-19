/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Ported from C++ v1.42.1:
 QuantLib/ql/math/solvers1d/finitedifferencenewtonsafe.hpp
 Copyright (C) 2011 Ferdinando Ametrano

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
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Ops;

/**
 * Safe (bracketed) Newton 1-D solver with finite-difference derivatives.
 *
 * <p>Direct Java port of C++ v1.42.1
 * {@code QuantLib::FiniteDifferenceNewtonSafe} in {@code ql/math/solvers1d/finitedifferencenewtonsafe.hpp}.
 *
 * <p>The algorithm is a hybrid bisection / Newton method:
 * <ul>
 *   <li>The Newton step uses a finite-difference approximation for the
 *       derivative (using the closer bracket endpoint).</li>
 *   <li>If the Newton step would leave the bracket, or if convergence is
 *       too slow (|2f| &gt; |f' * dx_prev|), bisection is used instead.</li>
 *   <li>The bracket is updated after each step to maintain a sign change.</li>
 * </ul>
 *
 * <p>This solver is used in C++ {@code IterativeBootstrap::calculate} for
 * inflation-curve bootstrap iterations &ge; 1, after an initial Brent pass.
 * Adopted in {@code PiecewiseZeroInflationCurve} and
 * {@code PiecewiseYoYInflationCurve} (Phase 2r L0 A.2).
 *
 * @see org.jquantlib.math.AbstractSolver1D
 * @see <a href="https://quantlib.org">QuantLib v1.42.1</a>
 */
public class FiniteDifferenceNewtonSafe extends AbstractSolver1D< Ops.DoubleOp > {

    /**
     * Computes the root of function {@code f} using the bracketed Newton-with-finite-differences algorithm.
     *
     * <p>Pre-conditions (verified by {@link AbstractSolver1D#solve} before
     * calling this method):
     * <ul>
     *   <li>{@code xMin < xMax}</li>
     *   <li>{@code fxMin * fxMax < 0} (root is bracketed)</li>
     *   <li>{@code xMin < root < xMax} (initial guess is inside bracket)</li>
     * </ul>
     *
     * @param f         the function whose root is sought
     * @param xAccuracy the required accuracy on {@code x}
     * @return the estimated root
     */
    @Override
    protected double solveImpl(final Ops.DoubleOp f, final double xAccuracy) {

        // Orient the search so that f(xl) < 0.
        double xh, xl;
        if ( fxMin < 0.0 ) {
            xl = xMin;
            xh = xMax;
        } else {
            xh = xMin;
            xl = xMax;
        }

        double froot = f.op(root);
        evaluationNumber++;

        // First-order finite-difference derivative:
        // pick the closer bracket endpoint to minimise extrapolation error.
        // (xMax - xMin > 0 guaranteed by AbstractSolver1D).
        double dfroot = (xMax - root < root - xMin) ? (fxMax - froot) / (xMax - root) : (fxMin - froot) / (xMin - root);

        double dx = xMax - xMin;

        while ( evaluationNumber <= getMaxEvaluations() ) {
            // Snapshot start-of-iteration values (C++: frootold, rootold, dxold).
            double frootold = froot;
            double rootold = root;
            final double dxold = dx;

            // Bisect if the Newton step is out-of-range or not decreasing fast enough.
            if ( (((root - xh) * dfroot - froot) * ((root - xl) * dfroot - froot) > 0.0) || (Math.abs(2.0 * froot)
                    > Math.abs(dxold * dfroot)) ) {

                dx = (xh - xl) / 2.0;
                root = xl + dx;

                // C++ comment: "if the root estimate just computed is close to the
                // previous one, we should calculate dfroot at root and xh rather than
                // root and rootold (xl instead of xh would be just as good)".
                // When close, overwrite rootold/frootold with xh/f(xh) so the
                // secant formula at the end uses (f(xh)-f(root))/(xh-root).
                if ( Closeness.isClose(root, rootold, 2500) ) {
                    rootold = xh;
                    frootold = f.op(xh);
                    evaluationNumber++;
                }

            } else {
                // Newton step: dx = f/f'  →  root -= dx.
                dx = froot / dfroot;
                root -= dx;
            }

            // Convergence criterion.
            if ( Math.abs(dx) < xAccuracy )
                return root;

            froot = f.op(root);
            evaluationNumber++;
            // Secant approximation of the derivative for the next iteration.
            dfroot = (frootold - froot) / (rootold - root);

            // Update bracket.
            if ( froot < 0.0 )
                xl = root;
            else
                xh = root;
        }

        throw new ArithmeticException("maximum number of function evaluations (" + getMaxEvaluations() + ") exceeded");
    }
}

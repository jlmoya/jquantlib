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

package org.jquantlib.testsuite.math.solvers1D;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.FiniteDifferenceNewtonSafe;
import org.junit.Test;

/**
 * Smoke tests for {@link FiniteDifferenceNewtonSafe}.
 *
 * <p>Phase 2r L0 A.1 — verifies convergence on synthetic functions with known
 * analytic roots (matches C++ v1.42.1 QuantLib solver test suite).
 *
 * <h3>Tolerance</h3>
 * <p>TIGHT: absolute 1e-10 on x (much tighter than the 1e-8 LOOSE tier).
 * Root-finding with |dx| &lt; 1e-12 accuracy gives x-accuracy far below TIGHT.
 */
public class FiniteDifferenceNewtonSafeTest {

    public FiniteDifferenceNewtonSafeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double ACCURACY  = 1.0e-12;
    private static final double X_TOL     = 1.0e-10;

    // f(x) = x^3 - 2x - 5;  real root ≈ 2.09455148154233.
    // This is the classic Wallis cubic used in many numerical analysis texts.
    private static final double CUBIC_ROOT = 2.09455148154233;
    private static final Ops.DoubleOp CUBIC = x -> x * x * x - 2.0 * x - 5.0;

    // f(x) = log(x) - 1;  root = e ≈ 2.71828182845905.
    private static final double LOG_ROOT = Math.E;
    private static final Ops.DoubleOp LOG_MINUS_1 = x -> Math.log(x) - 1.0;

    // f(x) = x^2 - 4;  root = 2.0 (bracketed on [0,3]).
    private static final Ops.DoubleOp SQUARE_MINUS_4 = x -> x * x - 4.0;

    // ------------------------------------------------------------------
    // bracket + guess variant: solve(f, acc, guess, xMin, xMax)
    // ------------------------------------------------------------------

    @Test
    public void cubicRoot_bracketedGuess() {
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final double root = solver.solve(CUBIC, ACCURACY, 2.5, 1.0, 3.0);
        if (Math.abs(root - CUBIC_ROOT) > X_TOL) {
            fail(String.format("cubicRoot_bracketedGuess: expected %.15f got %.15f diff=%.3e",
                    CUBIC_ROOT, root, Math.abs(root - CUBIC_ROOT)));
        }
    }

    @Test
    public void logMinusOne_bracketedGuess() {
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final double root = solver.solve(LOG_MINUS_1, ACCURACY, 2.5, 1.0, 4.0);
        if (Math.abs(root - LOG_ROOT) > X_TOL) {
            fail(String.format("logMinusOne_bracketedGuess: expected %.15f got %.15f diff=%.3e",
                    LOG_ROOT, root, Math.abs(root - LOG_ROOT)));
        }
    }

    @Test
    public void squareMinusFour_bracketedGuess() {
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final double root = solver.solve(SQUARE_MINUS_4, ACCURACY, 2.5, 0.0, 3.0);
        if (Math.abs(root - 2.0) > X_TOL) {
            fail(String.format("squareMinusFour_bracketedGuess: expected 2.0 got %.15f diff=%.3e",
                    root, Math.abs(root - 2.0)));
        }
    }

    // ------------------------------------------------------------------
    // step scan variant: solve(f, acc, guess, step)
    // ------------------------------------------------------------------

    @Test
    public void cubicRoot_stepScan() {
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final double root = solver.solve(CUBIC, ACCURACY, 2.0, 0.1);
        if (Math.abs(root - CUBIC_ROOT) > X_TOL) {
            fail(String.format("cubicRoot_stepScan: expected %.15f got %.15f diff=%.3e",
                    CUBIC_ROOT, root, Math.abs(root - CUBIC_ROOT)));
        }
    }

    @Test
    public void logMinusOne_stepScan() {
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        final double root = solver.solve(LOG_MINUS_1, ACCURACY, 2.0, 0.1);
        if (Math.abs(root - LOG_ROOT) > X_TOL) {
            fail(String.format("logMinusOne_stepScan: expected %.15f got %.15f diff=%.3e",
                    LOG_ROOT, root, Math.abs(root - LOG_ROOT)));
        }
    }

    // ------------------------------------------------------------------
    // Max evaluations not exceeded
    // ------------------------------------------------------------------

    @Test
    public void cubicRoot_evaluationCountReasonable() {
        final FiniteDifferenceNewtonSafe solver = new FiniteDifferenceNewtonSafe();
        solver.solve(CUBIC, ACCURACY, 2.5, 1.0, 3.0);
        // FDNewtonSafe should find root well within 30 evaluations.
        if (solver.getNumEvaluations() > 30) {
            fail("evaluationCountReasonable: unexpectedly many evaluations: "
                    + solver.getNumEvaluations());
        }
    }
}

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

package org.jquantlib.testsuite.math;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Expm;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.ode.AdaptiveRungeKutta;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/ode.cpp (Phase 5a).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods. {@link AdaptiveRungeKutta} in Java
 * supports only the {@code double[]}/Real-vector overload (ode #3 in C++).
 * The 1D real (#1), complex 1D (#2), and complex vector (#4) overloads are
 * Phase 5a.5 carry-forwards. {@code testMatrixExponential} and
 * {@code testMatrixExponentialOfZero} were un-ignored in
 * Phase 5e.5b-CFC-d-77 once {@link Expm} was ported.
 */
public class OdeTest {

    public OdeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testAdaptiveRungeKutta() {
        QL.info("Testing adaptive Runge Kutta...");

        final AdaptiveRungeKutta rkReal = new AdaptiveRungeKutta(1e-12, 1e-4, 0.0);
        final double tol3 = 2e-12;

        // f''=-f, f(0)=0, f'(0)=1 — a 2-D ODE for [f, f']
        // matches C++ ode3 + y30 = {0.0, 1.0}; expected solution f(x) = sin(x).
        final AdaptiveRungeKutta.OdeFct ode3 = (t, y) -> new double[] { y[1], -y[0] };
        final double[] y30 = { 0.0, 1.0 };

        for (double x = 0.01; x <= 5.0; x += 0.01) {
            final double[] y3 = rkReal.solve(ode3, y30, 0.0, x);
            final double exact3 = Math.sin(x);
            if (Math.abs(exact3 - y3[0]) > tol3) {
                fail("Error in ode #3: exact solution at x=" + x
                        + " is " + exact3
                        + ", numerical solution is " + y3[0]
                        + " difference " + Math.abs(exact3 - y3[0])
                        + " outside tolerance " + tol3);
            }
        }
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib AdaptiveRungeKutta has no 1D-Real or "
            + "complex overloads (C++ template variants for ode #1, #2, #4). The 2D-Real "
            + "case is exercised by testAdaptiveRungeKutta above.")
    @Test
    public void testAdaptiveRungeKutta1dAndComplex() {
    }

    /**
     * Mirrors the C++ {@code testMatrixExponential} from
     * {@code test-suite/ode.cpp}. Verifies {@link Expm#expm(Matrix, double, double)}
     * against the closed-form solution
     * <pre>
     * exp(t * M) for M = [[ 5, -6, -6],
     *                    [-1,  4,  2],
     *                    [ 3, -6, -4]]
     * </pre>
     * across a logarithmically-spaced range of t in (0.01, 10.24). Reference
     * values come from
     * http://www.millersville.edu/~bikenaga/linear-algebra/matrix-exponential/matrix-exponential.html
     * Also verifies that {@code Expm(-M, -t) == Expm(M, t)}.
     */
    @Test
    public void testMatrixExponential() {
        QL.info("Testing matrix exponential based on ode...");

        final Matrix m = new Matrix(3, 3);
        m.set(0, 0,  5); m.set(0, 1, -6); m.set(0, 2, -6);
        m.set(1, 0, -1); m.set(1, 1,  4); m.set(1, 2,  2);
        m.set(2, 0,  3); m.set(2, 1, -6); m.set(2, 2, -4);

        final double tol = 1e-12;

        for (double t = 0.01; t < 11; t += t) {
            final Matrix calculated = Expm.expm(m, t, tol);

            final Matrix expected = new Matrix(3, 3);
            final double et  = Math.exp(t);
            final double e2t = Math.exp(2 * t);
            expected.set(0, 0, -3 * et + 4 * e2t);
            expected.set(0, 1,  6 * et - 6 * e2t);
            expected.set(0, 2,  6 * et - 6 * e2t);
            expected.set(1, 0,       et -     e2t);
            expected.set(1, 1, -2 * et + 3 * e2t);
            expected.set(1, 2, -2 * et + 2 * e2t);
            expected.set(2, 0, -3 * et + 3 * e2t);
            expected.set(2, 1,  6 * et - 6 * e2t);
            expected.set(2, 2,  6 * et - 5 * e2t);

            double relDiffNorm = relFrobeniusDiffNorm(calculated, expected);
            if (Math.abs(relDiffNorm) > 100 * tol) {
                fail("Failed to reproduce expected matrix exponential."
                        + "\n rel. difference norm: " + relDiffNorm
                        + "\n tolerance           : " + (100 * tol));
            }

            final Matrix negativeTime = Expm.expm(m.mul(-1.0), -t, tol);
            relDiffNorm = relFrobeniusDiffNorm(negativeTime, expected);
            if (Math.abs(relDiffNorm) > 100 * tol) {
                fail("Failed to reproduce expected matrix exponential."
                        + "\n rel. difference norm: " + relDiffNorm
                        + "\n tolerance           : " + (100 * tol));
            }
        }
    }

    /**
     * Mirrors the C++ {@code testMatrixExponentialOfZero}: exp(0) should be I.
     */
    @Test
    public void testMatrixExponentialOfZero() {
        QL.info("Testing matrix exponential of a zero matrix based on ode...");

        final Matrix m = new Matrix(3, 3);
        // Matrix constructor zero-initializes.

        final double tol = 100 * Math.ulp(1.0);
        final double t = 1.0;
        final Matrix calculated = Expm.expm(m, t);

        for (int i = 0; i < calculated.rows(); ++i) {
            for (int j = 0; j < calculated.columns(); ++j) {
                final double kroneckerDelta = (i == j) ? 1.0 : 0.0;
                if (Math.abs(calculated.get(i, j) - kroneckerDelta) > tol) {
                    fail("Failed to reproduce expected matrix exponential."
                            + "\n tolerance           : " + tol);
                }
            }
        }
    }

    /**
     * Computes {@code ||A - B||_F / ||B||_F} using the QuantLib definition
     * {@code ||X||_F = sqrt(sum of diag(X * X^T))}.
     */
    private static double relFrobeniusDiffNorm(final Matrix a, final Matrix b) {
        final Matrix diff = a.sub(b);
        return frobeniusNorm(diff) / frobeniusNorm(b);
    }

    private static double frobeniusNorm(final Matrix m) {
        // ||M||_F^2 = sum_{i,j} M[i,j]^2 = sum_i (M * M^T)[i][i] = sum_i sum_j M[i][j]^2
        double sumSq = 0.0;
        for (int i = 0; i < m.rows(); ++i) {
            for (int j = 0; j < m.columns(); ++j) {
                final double v = m.get(i, j);
                sumSq += v * v;
            }
        }
        return Math.sqrt(sumSq);
    }
}

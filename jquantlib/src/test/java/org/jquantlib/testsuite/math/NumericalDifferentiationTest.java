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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.function.DoubleUnaryOperator;

import org.jquantlib.QL;
import org.jquantlib.math.Factorial;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.operators.NumericalDifferentiation;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/numericaldifferentiation.cpp.
 *
 * <p>Phase 5e.5b-CFC-d-84: production class
 * {@link NumericalDifferentiation} is in place; tests are body-filled here.
 */
public class NumericalDifferentiationTest {

    public NumericalDifferentiationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // Mirrors C++ helper:
    //   constexpr double eps = 500 * QL_EPSILON;
    //   if (|b| < QL_EPSILON) return |a| < eps; else return |(a-b)/b| < eps;
    private static final double QL_EPSILON = 2.2204460492503131e-16;
    private static final double EPS = 500.0 * QL_EPSILON;

    private static boolean isTheSame(final double a, final double b) {
        if (Math.abs(b) < QL_EPSILON) {
            return Math.abs(a) < EPS;
        }
        return Math.abs((a - b) / b) < EPS;
    }

    private static void checkTwoArraysAreTheSame(final Array calculated, final Array expected) {
        boolean correct = calculated.size() == expected.size();
        if (correct) {
            for (int i = 0; i < calculated.size(); ++i) {
                if (!isTheSame(calculated.get(i), expected.get(i))) {
                    correct = false;
                    break;
                }
            }
        }
        if (!correct) {
            final StringBuilder sb = new StringBuilder("Failed to reproduce expected array");
            sb.append("\n    calculated: ").append(arrayToString(calculated));
            sb.append("\n    expected:   ").append(arrayToString(expected));
            fail(sb.toString());
        }
    }

    private static String arrayToString(final Array a) {
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.size(); ++i) {
            if (i > 0) sb.append(", ");
            sb.append(a.get(i));
        }
        return sb.append("]").toString();
    }

    private static void singleValueTest(final String comment, final double calculated,
                                        final double expected, final double tol) {
        if (Math.abs(calculated - expected) > tol) {
            fail("Failed to reproduce " + comment + " order derivative"
                    + "\n    calculated: " + calculated
                    + "\n      expected: " + expected
                    + "\n     tolerance: " + tol
                    + "\n    difference: " + (expected - calculated));
        }
    }

    @Test
    public void testTabulatedCentralScheme() {
        // C++ test-suite/numericaldifferentiation.cpp:72
        final DoubleUnaryOperator f = null;
        final NumericalDifferentiation.Scheme central = NumericalDifferentiation.Scheme.Central;

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 1.0, 3, central).weights(),
                new Array(new double[]{-0.5, 0.0, 0.5}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 0.5, 3, central).weights(),
                new Array(new double[]{-1.0, 0.0, 1.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 0.25, 7, central).weights(),
                new Array(new double[]{-4/60.0, 12/20.0, -12/4.0, 0.0, 12/4.0, -12/20.0, 4/60.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 4, Math.pow(0.5, 0.25), 9, central).weights(),
                new Array(new double[]{14/240.0, -4/5.0, 338/60.0, -244/15.0, 182/8.0,
                        -244/15.0, 338/60.0, -4/5.0, 14/240.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 0.5, 7, central).offsets(),
                new Array(new double[]{-1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5}));
    }

    @Test
    public void testTabulatedBackwardScheme() {
        // C++ test-suite/numericaldifferentiation.cpp:102
        final DoubleUnaryOperator f = null;
        final NumericalDifferentiation.Scheme backward = NumericalDifferentiation.Scheme.Backward;

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 1.0, 2, backward).weights(),
                new Array(new double[]{1.0, -1.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 2, 2.0, 4, backward).weights(),
                new Array(new double[]{2/4.0, -5/4.0, 4/4.0, -1.0/4.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 4, 1.0, 6, backward).weights(),
                new Array(new double[]{3.0, -14.0, 26.0, -24.0, 11.0, -2.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 2, 0.5, 4, backward).offsets(),
                new Array(new double[]{0.0, -0.5, -1.0, -1.5}));
    }

    @Test
    public void testTabulatedForwardScheme() {
        // C++ test-suite/numericaldifferentiation.cpp:128
        final DoubleUnaryOperator f = null;
        final NumericalDifferentiation.Scheme forward = NumericalDifferentiation.Scheme.Forward;

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 1.0, 2, forward).weights(),
                new Array(new double[]{-1.0, 1.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 0.5, 3, forward).weights(),
                new Array(new double[]{-6/2.0, 4.0, -2/2.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, 0.5, 7, forward).weights(),
                new Array(new double[]{-98/20.0, 12.0, -30/2.0, 40/3.0, -30/4.0, 12/5.0, -2/6.0}));

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 2, 0.5, 4, forward).offsets(),
                new Array(new double[]{0.0, 0.5, 1.0, 1.5}));
    }

    @Test
    public void testIrregularSchemeFirstOrder() {
        // C++ test-suite/numericaldifferentiation.cpp:154
        final DoubleUnaryOperator f = null;

        final double h1 = 5e-7;
        final double h2 = 3e-6;

        final double alpha = -h2 / (h1 * (h1 + h2));
        final double gamma = h1 / (h2 * (h1 + h2));
        final double beta = -alpha - gamma;

        final Array offsets = new Array(new double[]{-h1, 0.0, h2});

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 1, offsets).weights(),
                new Array(new double[]{alpha, beta, gamma}));
    }

    @Test
    public void testIrregularSchemeSecondOrder() {
        // C++ test-suite/numericaldifferentiation.cpp:173
        final DoubleUnaryOperator f = null;

        final double h1 = 2e-7;
        final double h2 = 8e-8;

        final double alpha = 2.0 / (h1 * (h1 + h2));
        final double gamma = 2.0 / (h2 * (h1 + h2));
        final double beta = -alpha - gamma;

        final Array offsets = new Array(new double[]{-h1, 0.0, h2});

        checkTwoArraysAreTheSame(
                new NumericalDifferentiation(f, 2, offsets).weights(),
                new Array(new double[]{alpha, beta, gamma}));
    }

    @Test
    public void testDerivativesOfSineFunction() {
        // C++ test-suite/numericaldifferentiation.cpp:192
        final DoubleUnaryOperator f = (double x) -> Math.sin(x);

        final NumericalDifferentiation df_central = new NumericalDifferentiation(
                f, 1, Math.sqrt(QL_EPSILON), 3, NumericalDifferentiation.Scheme.Central);
        final NumericalDifferentiation df_backward = new NumericalDifferentiation(
                f, 1, Math.sqrt(QL_EPSILON), 3, NumericalDifferentiation.Scheme.Backward);
        final NumericalDifferentiation df_forward = new NumericalDifferentiation(
                f, 1, Math.sqrt(QL_EPSILON), 3, NumericalDifferentiation.Scheme.Forward);

        for (double x = 0.0; x < 5.0; x += 0.1) {
            final double calculatedCentral = df_central.evaluate(x);
            final double calculatedBackward = df_backward.evaluate(x);
            final double calculatedForward = df_forward.evaluate(x);
            final double expected = Math.cos(x);
            singleValueTest("central first", calculatedCentral, expected, 1e-8);
            singleValueTest("backward first", calculatedBackward, expected, 1e-6);
            singleValueTest("forward first", calculatedForward, expected, 1e-6);
        }

        final NumericalDifferentiation df4_central = new NumericalDifferentiation(
                f, 4, 1e-2, 7, NumericalDifferentiation.Scheme.Central);
        final NumericalDifferentiation df4_backward = new NumericalDifferentiation(
                f, 4, 1e-2, 7, NumericalDifferentiation.Scheme.Backward);
        final NumericalDifferentiation df4_forward = new NumericalDifferentiation(
                f, 4, 1e-2, 7, NumericalDifferentiation.Scheme.Forward);

        for (double x = 0.0; x < 5.0; x += 0.1) {
            final double calculatedCentral = df4_central.evaluate(x);
            final double calculatedBackward = df4_backward.evaluate(x);
            final double calculatedForward = df4_forward.evaluate(x);
            final double expected = Math.sin(x);
            singleValueTest("central 4th", calculatedCentral, expected, 1e-4);
            singleValueTest("backward 4th", calculatedBackward, expected, 1e-4);
            singleValueTest("forward 4th", calculatedForward, expected, 1e-4);
        }

        final Array offsets = new Array(new double[]{-0.01, -0.02, 0.03, 0.014, 0.041});
        final NumericalDifferentiation df3_irregular = new NumericalDifferentiation(f, 3, offsets);

        checkTwoArraysAreTheSame(df3_irregular.offsets(), offsets);

        for (double x = 0.0; x < 5.0; x += 0.1) {
            final double calculatedIrregular = df3_irregular.evaluate(x);
            final double expected = -Math.cos(x);
            singleValueTest("irregular 3th", calculatedIrregular, expected, 5e-5);
        }
    }

    @Test
    public void testCoefficientBasedOnVandermonde() {
        // C++ test-suite/numericaldifferentiation.cpp:275
        final DoubleUnaryOperator f = null;
        final Factorial factorial = new Factorial();

        for (int order = 0; order < 5; ++order) {
            for (int nGridPoints = order + 1; nGridPoints < order + 3; ++nGridPoints) {

                final double[] gp = new double[nGridPoints];
                for (int i = 0; i < nGridPoints; ++i) {
                    final double p = i;
                    gp[i] = Math.sin(p) + Math.cos(p);
                }
                final Array gridPoints = new Array(gp);

                final double x = 0.3902842;
                final Array weightsVandermonde = vandermondeCoefficients(order, x, gridPoints,
                        factorial);
                final Array offsets = gridPoints.sub(x);
                final NumericalDifferentiation nd = new NumericalDifferentiation(f, order, offsets);

                checkTwoArraysAreTheSame(gridPoints, nd.offsets().add(x));
                checkTwoArraysAreTheSame(weightsVandermonde, nd.weights());
            }
        }
    }

    /**
     * Vandermonde-matrix solver for finite-difference weights, mirroring
     * C++ helper in test-suite/numericaldifferentiation.cpp:256.
     */
    private static Array vandermondeCoefficients(final int order, final double x,
                                                 final Array gridPoints,
                                                 final Factorial factorial) {
        final Array q = gridPoints.sub(x);
        final int n = gridPoints.size();
        final double[][] data = new double[n][n];
        for (int j = 0; j < n; ++j) {
            data[0][j] = 1.0;
        }
        for (int i = 1; i < n; ++i) {
            final double fact = factorial.get(i);
            for (int j = 0; j < n; ++j) {
                data[i][j] = Math.pow(q.get(j), i) / fact;
            }
        }
        final Matrix m = new Matrix(data);

        final double[] b = new double[n];
        b[order] = 1.0;
        final Array bArr = new Array(b);
        return m.inverse().mul(bArr);
    }

    // Cheap unused-assertion guard for static analyzers.
    @SuppressWarnings("unused")
    private static void unusedAssertEqualsAlias() { assertEquals(1, 1); }
}

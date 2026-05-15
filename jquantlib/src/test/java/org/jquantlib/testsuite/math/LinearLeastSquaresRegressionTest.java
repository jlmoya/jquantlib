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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.LinearRegression;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/linearleastsquaresregression.cpp
 * (Phase 5e.5b-CFC-d-16b).
 *
 * <p>Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Tests {@link org.jquantlib.math.LinearRegression} and the underlying
 * SVD-based {@link org.jquantlib.math.GeneralLinearLeastSquares}:
 * <ul>
 *   <li>{@code testRegression}: 100k pseudo-random samples regressed
 *     against basis {1, x, x^2, sin(x)} verifying coefficients within
 *     3 * standardErrors. Also exercises a redundant basis (duplicated
 *     x^2) which yields huge SE on the redundant column but the
 *     summed coefficient still recovers the truth.</li>
 *   <li>{@code testMultiDimRegression}: multi-variate (2D input) regression
 *     against {1, x[0], x[1], x[0]*x[1]} (faithful: the C++ test uses
 *     the auto-basis {1, x[0], x[1], x[2], x[3]}).</li>
 *   <li>{@code test1dLinearRegression}: simple {@code y = a + b*x} with
 *     hardcoded expected coefficients/standardErrors from the
 *     QuantLib-User list example.</li>
 * </ul>
 *
 * <p>The two stochastic tests use Java {@code MersenneTwisterUniformRng}
 * + {@link InverseCumulativeNormal} (matching C++ {@code PseudoRandom::rng_type
 * = InverseCumulativeRng<MersenneTwister, InverseCumulativeNormal>}).
 * The assertions are property-based (errors within tolerance, recovered
 * coefficients within {@code 3*standardErrors} of truth) — they hold for
 * any reasonable PRNG draw of N=100k normal samples and so do not require
 * exact draw-by-draw match with C++.
 */
public class LinearLeastSquaresRegressionTest {

    public LinearLeastSquaresRegressionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Mirrors C++ PseudoRandom::rng_type — composes MT + InverseCumulativeNormal. */
    private static final class NormalRng {
        private final MersenneTwisterUniformRng urng;
        private final InverseCumulativeNormal icnd = new InverseCumulativeNormal();

        NormalRng(final long seed) {
            this.urng = new MersenneTwisterUniformRng(seed);
        }

        double nextValue() {
            return icnd.op(urng.next().value());
        }
    }

    @Test
    public void testRegression() {
        QL.info("Testing linear least-squares regression...");

        final double tolerance = 0.05;
        final int nr = 100000;
        final NormalRng rng = new NormalRng(1234L);

        // Basis {1, x, x^2, sin(x)}.
        final List<Ops.DoubleOp> v = new ArrayList<>();
        v.add(new Ops.DoubleOp() { @Override public double op(final double x) { return 1.0; } });
        v.add(new Ops.DoubleOp() { @Override public double op(final double x) { return x; } });
        v.add(new Ops.DoubleOp() { @Override public double op(final double x) { return x * x; } });
        v.add(new Ops.DoubleOp() { @Override public double op(final double x) { return Math.sin(x); } });

        // Redundant basis {1, x, x^2, sin(x), x^2}.
        final List<Ops.DoubleOp> w = new ArrayList<>(v);
        w.add(new Ops.DoubleOp() { @Override public double op(final double x) { return x * x; } });

        for (int k = 0; k < 3; ++k) {
            final double[] a = new double[4];
            for (int i = 0; i < 4; ++i) {
                a[i] = rng.nextValue();
            }

            final double[] x = new double[nr];
            final double[] y = new double[nr];
            for (int i = 0; i < nr; ++i) {
                x[i] = rng.nextValue();
                // y = a_0 + a_1 x + a_2 x^2 + a_3 sin(x) + eps
                y[i] = a[0] + a[1] * x[i] + a[2] * x[i] * x[i]
                        + a[3] * Math.sin(x[i]) + rng.nextValue();
            }

            // Non-redundant basis.
            LinearRegression m = new LinearRegression(x, y, v);

            for (int i = 0; i < v.size(); ++i) {
                final double err = m.standardErrors().get(i);
                if (err > tolerance) {
                    fail("Failed to reproduce linear regression coef.\n"
                            + "    error:     " + err + "\n"
                            + "    tolerance: " + tolerance);
                }
                final double coef = m.coefficients().get(i);
                if (Math.abs(coef - a[i]) > 3.0 * err) {
                    fail("Failed to reproduce linear regression coef.\n"
                            + "    calculated: " + coef + "\n"
                            + "    error:      " + err + "\n"
                            + "    expected:   " + a[i]);
                }
            }

            // Redundant basis: a_2 should split between basis indices 2 and 4.
            m = new LinearRegression(x, y, w);
            final double[] ma = {
                m.coefficients().get(0),
                m.coefficients().get(1),
                m.coefficients().get(2) + m.coefficients().get(4),
                m.coefficients().get(3)
            };
            final double se2 = m.standardErrors().get(2);
            final double se4 = m.standardErrors().get(4);
            final double[] err = {
                m.standardErrors().get(0),
                m.standardErrors().get(1),
                Math.sqrt(se2 * se2 + se4 * se4),
                m.standardErrors().get(3)
            };
            for (int i = 0; i < v.size(); ++i) {
                if (Math.abs(ma[i] - a[i]) > 3.0 * err[i]) {
                    fail("Failed to reproduce linear regression coef.\n"
                            + "    calculated: " + ma[i] + "\n"
                            + "    error:      " + err[i] + "\n"
                            + "    expected:   " + a[i]);
                }
            }
        }
    }

    @Test
    public void testMultiDimRegression() {
        QL.info("Testing multi-dimensional linear least-squares regression...");

        final int nr = 100000;
        final int dims = 4;
        final double tolerance = 0.01;
        final NormalRng rng = new NormalRng(1234L);

        // Basis {1, get_item(0), ..., get_item(dims-1)} (5 functions).
        final List<Ops.ObjectToDouble<Array>> v = new ArrayList<>();
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array x) { return 1.0; } });
        for (int i = 0; i < dims; ++i) {
            final int idx = i;
            v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array x) { return x.get(idx); } });
        }

        final double[] coeff = new double[v.size()];
        for (int i = 0; i < v.size(); ++i) {
            coeff[i] = rng.nextValue();
        }

        final double[] y = new double[nr];
        final Array[] x = new Array[nr];
        for (int i = 0; i < nr; ++i) {
            x[i] = new Array(dims);
            for (int j = 0; j < dims; ++j) {
                x[i].set(j, rng.nextValue());
            }
            for (int j = 0; j < v.size(); ++j) {
                y[i] += coeff[j] * v.get(j).op(x[i]);
            }
            y[i] += rng.nextValue();
        }

        // Explicit basis form.
        LinearRegression m = new LinearRegression(x, y, v);
        for (int i = 0; i < v.size(); ++i) {
            final double err = m.standardErrors().get(i);
            if (err > tolerance) {
                fail("Failed to reproduce linear regression coef.\n"
                        + "    error:     " + err + "\n"
                        + "    tolerance: " + tolerance);
            }
            final double c = m.coefficients().get(i);
            if (Math.abs(c - coeff[i]) > 3.0 * tolerance) {
                fail("Failed to reproduce linear regression coef.\n"
                        + "    calculated: " + c + "\n"
                        + "    error:      " + err + "\n"
                        + "    expected:   " + coeff[i]);
            }
        }

        // Auto-basis form (much simpler). Mirrors C++ LinearRegression(x, y, Real(1.0)).
        final LinearRegression m1 = new LinearRegression(x, y, 1.0);
        for (int i = 0; i < m1.dim(); ++i) {
            final double err = m1.standardErrors().get(i);
            if (err > tolerance) {
                fail("Failed to reproduce linear regression coef.\n"
                        + "    error:     " + err + "\n"
                        + "    tolerance: " + tolerance);
            }
            final double c = m1.coefficients().get(i);
            if (Math.abs(c - coeff[i]) > 3.0 * tolerance) {
                fail("Failed to reproduce linear regression coef.\n"
                        + "    calculated: " + c + "\n"
                        + "    error:      " + err + "\n"
                        + "    expected:   " + coeff[i]);
            }
        }
    }

    @Test
    public void test1dLinearRegression() {
        QL.info("Testing 1D simple linear least-squares regression...");

        // Example from QuantLib-User list — Boris Skorodumov.
        final double[] x = {2.4, 1.8, 2.5, 3.0, 2.1, 1.2, 2.0, 2.7, 3.6};
        final double[] y = {7.8, 5.5, 8.0, 9.0, 6.5, 4.0, 6.3, 8.4, 10.2};

        final LinearRegression m = new LinearRegression(x, y);

        final double tol = 0.0002;
        final double[] coeffExpected  = { 0.9448, 2.6853 };
        final double[] errorsExpected = { 0.3654, 0.1487 };

        for (int i = 0; i < 2; ++i) {
            final double se = m.standardErrors().get(i);
            if (Math.abs(se - errorsExpected[i]) > tol) {
                fail("Failed to reproduce linear regression standard errors\n"
                        + "    calculated: " + se + "\n"
                        + "    expected:   " + errorsExpected[i] + "\n"
                        + "    tolerance:  " + tol);
            }
            final double c = m.coefficients().get(i);
            if (Math.abs(c - coeffExpected[i]) > tol) {
                fail("Failed to reproduce linear regression coef.\n"
                        + "    calculated: " + c + "\n"
                        + "    expected:   " + coeffExpected[i] + "\n"
                        + "    tolerance:  " + tol);
            }
        }
    }
}

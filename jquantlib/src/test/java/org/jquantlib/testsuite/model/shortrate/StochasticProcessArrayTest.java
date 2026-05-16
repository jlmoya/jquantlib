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
*/
package org.jquantlib.testsuite.model.shortrate;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.processes.StochasticProcess1D;
import org.junit.Assert;
import org.junit.Test;

/**
 * Regression tests for {@link StochasticProcessArray}.
 *
 * <p>Pins the C++ QuantLib v1.42.1 invariant that {@code diffusion()} and
 * {@code stdDeviation()} must not mutate the internal {@code sqrtCorrelation_}
 * matrix. The C++ source ({@code ql/processes/stochasticprocessarray.cpp})
 * creates a fresh {@code Matrix tmp = sqrtCorrelation_;} on each call and
 * scales the rows of the copy. Without the per-call copy the row-scaling
 * compounds across calls and corrupts the stored sqrt-correlation.
 *
 * <p>Phase 5e.5b-CFC-d-30.
 */
public class StochasticProcessArrayTest {

    /**
     * Minimal {@link StochasticProcess1D} test double: diffusion=sigma*x,
     * stdDeviation=sigma*x*sqrt(dt). Bypasses the need for a Discretization1D
     * (which the upstream {@code GeometricBrownianMotionProcess} doesn't set).
     */
    private static final class TestProcess1D extends StochasticProcess1D {
        private final double x0_;
        private final double sigma_;
        TestProcess1D(final double x0, final double sigma) {
            super();
            this.x0_ = x0;
            this.sigma_ = sigma;
        }
        @Override public double x0() { return x0_; }
        @Override public double drift(final double t, final double x) { return 0.0; }
        @Override public double diffusion(final double t, final double x) { return sigma_ * x; }
        @Override public double expectation(final double t0, final double x0, final double dt) { return x0; }
        @Override public double stdDeviation(final double t0, final double x0, final double dt) {
            return sigma_ * x0 * Math.sqrt(dt);
        }
        @Override public double variance(final double t0, final double x0, final double dt) {
            final double s = sigma_ * x0;
            return s * s * dt;
        }
    }

    private static StochasticProcessArray buildArray() {
        final List<StochasticProcess1D> processes = new ArrayList<>();
        processes.add(new TestProcess1D(100.0, 0.20));
        processes.add(new TestProcess1D(120.0, 0.30));
        processes.add(new TestProcess1D( 80.0, 0.25));

        // symmetric positive-definite 3x3 correlation
        final Matrix correlation = new Matrix(new double[][] {
                { 1.0, 0.5, 0.3 },
                { 0.5, 1.0, 0.4 },
                { 0.3, 0.4, 1.0 }
        });
        return new StochasticProcessArray(processes, correlation);
    }

    private static void assertMatricesEqual(final String msg, final Matrix a, final Matrix b) {
        Assert.assertEquals(msg + " rows", a.rows(), b.rows());
        Assert.assertEquals(msg + " cols", a.cols(), b.cols());
        for (int r = 0; r < a.rows(); r++) {
            for (int c = 0; c < a.cols(); c++) {
                Assert.assertEquals(
                        msg + " [" + r + "," + c + "]",
                        a.get(r, c), b.get(r, c), 0.0);
            }
        }
    }

    /**
     * Calling {@code diffusion()} twice with the same arguments must yield
     * identical matrices. Previously the second call returned a matrix whose
     * rows had been multiplied by {@code sigma} twice (the in-place mutation
     * bug).
     */
    @Test
    public void testDiffusionDoesNotMutateSqrtCorrelation() {
        final StochasticProcessArray sp = buildArray();
        final double t = 0.5;
        final Array x = new Array(new double[] { 100.0, 120.0, 80.0 });

        // snapshot of sqrt-correlation before any diffusion call.
        // We obtain it through correlation() which is sqrtCorrelation * sqrtCorrelation^T.
        final Matrix corrBefore = sp.correlation();

        final Matrix d1 = new Matrix(sp.diffusion(t, x));
        final Matrix d2 = new Matrix(sp.diffusion(t, x));

        assertMatricesEqual("diffusion repeatability", d1, d2);

        // sqrt-correlation must be unchanged: corrBefore == corrAfter (bit-exact).
        final Matrix corrAfter = sp.correlation();
        assertMatricesEqual("sqrtCorrelation_ invariance after diffusion", corrBefore, corrAfter);
    }

    /**
     * Same invariance check for {@code stdDeviation()}.
     */
    @Test
    public void testStdDeviationDoesNotMutateSqrtCorrelation() {
        final StochasticProcessArray sp = buildArray();
        final double t0 = 0.0;
        final double dt = 0.25;
        final Array x0 = new Array(new double[] { 100.0, 120.0, 80.0 });

        final Matrix corrBefore = sp.correlation();

        final Matrix s1 = new Matrix(sp.stdDeviation(t0, x0, dt));
        final Matrix s2 = new Matrix(sp.stdDeviation(t0, x0, dt));

        assertMatricesEqual("stdDeviation repeatability", s1, s2);

        final Matrix corrAfter = sp.correlation();
        assertMatricesEqual("sqrtCorrelation_ invariance after stdDeviation", corrBefore, corrAfter);
    }

    /**
     * Sanity check: doubling x doubles sigma (geometric BM: diffusion = sigma * x);
     * each row of the returned diffusion matrix must therefore double exactly.
     * This pins the row-scaling semantics directly.
     */
    @Test
    public void testDiffusionRowScaling() {
        final StochasticProcessArray sp = buildArray();
        final double t = 0.5;
        final Array x  = new Array(new double[] { 100.0, 120.0,  80.0 });
        final Array x2 = new Array(new double[] { 200.0, 240.0, 160.0 });

        final Matrix d1 = new Matrix(sp.diffusion(t, x));
        final Matrix d2 = sp.diffusion(t, x2);

        for (int i = 0; i < d1.rows(); i++) {
            for (int j = 0; j < d1.cols(); j++) {
                Assert.assertEquals(
                        "row " + i + " col " + j + " must double",
                        2.0 * d1.get(i, j), d2.get(i, j), 1e-14);
            }
        }
    }
}

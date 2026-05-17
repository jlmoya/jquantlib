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

/*
 Copyright (C) 2004 Ferdinando Ametrano
 Copyright (C) 2007 Giorgio Facchinetti
 Copyright (C) 2009-2024 multiple contributors

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.math.interpolations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.jquantlib.QL;
import org.jquantlib.math.BSpline;
import org.jquantlib.math.Constants;
import org.jquantlib.math.GaussianKernel;
import org.jquantlib.math.KernelFunction;
import org.jquantlib.math.Ops;
import org.jquantlib.math.RichardsonExtrapolation;
import org.jquantlib.experimental.math.LaplaceInterpolation;
import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.interpolations.ChebyshevInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.FlatExtrapolator2D;
import org.jquantlib.math.interpolations.FritschButlandCubic;
import org.jquantlib.math.interpolations.Interpolation2D;
import org.jquantlib.math.interpolations.KernelInterpolation;
import org.jquantlib.math.interpolations.KernelInterpolation2D;
import org.jquantlib.math.interpolations.LagrangeInterpolation;
import org.jquantlib.math.interpolations.MixedLinearCubicInterpolation;
import org.jquantlib.math.interpolations.factories.Bilinear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/interpolations.cpp (Phase 5g).
 *
 * <p>The C++ file has 38 test cases — the largest in Phase 5g — covering
 * cubic spline endpoint conditions, Hyman filter, multi-spline, functor
 * adapter, Fritsch-Butland, BackwardFlat / ForwardFlat, mixed
 * linear-cubic, SABR / NoArbSABR / FlochKennedy, Kernel, BicubicSpline,
 * Richardson extrapolation, Lagrange, B-Splines, Chebyshev,
 * LaplaceInterpolation, FlatExtrapolation.
 *
 * <p><b>Existing Java coverage</b> ({@link InterpolationTest} sibling class):
 * 12 tests already cover testInterpolateWithoutUpdate (via testAsFunctor),
 * testSplineErrorOnGaussianValues, testSplineOnGaussianValues,
 * testSplineOnGenericValues, testSimmetricEndConditions,
 * testDerivativeEndConditions, testNonRestrictiveHymanFilter,
 * testMultiSpline, testAsFunctor, testBackwardFlat, testForwardFlat,
 * testSabrInterpolation. {@link FlatForwardInterpolationTest},
 * {@link LinearInterpolationTest}, {@link BackwardInterpolationTest},
 * {@link BilinearInterpolationTest} and the SABR-focused tests
 * ({@code SABRInterpolationTest}, {@code SABRInterpolationConstructionTest},
 * {@code XABRInterpolationImplTest}) provide additional coverage.
 *
 * <p>This class adds two Lagrange-interpolation tests that are not yet
 * covered (Java {@link LagrangeInterpolation} class is present but
 * untested), plus deferral markers for the C++ tests that require
 * production classes still missing in JQuantLib (BSpline,
 * ChebyshevInterpolation, LaplaceInterpolation, RichardsonExtrapolation,
 * KernelInterpolation, BicubicSpline, Fritsch-Butland,
 * FlochKennedy SABR, Mixed Linear-Cubic, NoArbSABR interpolation, and
 * the SABR transformations / single-cases tests).
 */
public class InterpolationsTest {

    public InterpolationsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static double lagrangeTestFct(final double x) {
        // C++ interpolations.cpp lines 237-239.
        return Math.abs(x) + 0.5 * x - x * x;
    }

    /**
     * C++ interpolations.cpp lines 232-234 — Richardson example function
     * {@code f(h) = (1 + h)^(1/h)} (converges to {@code e} as {@code h -> 0}).
     */
    private static final Ops.DoubleOp RICHARDSON_F = new Ops.DoubleOp() {
        @Override
        public double op(final double h) {
            return Math.pow(1.0 + h, 1.0 / h);
        }
    };

    /**
     * C++ interpolations.cpp lines 215-226 — functor
     * {@code GF(h) = pi + factor * h^exponent + (factor*h)^(exponent+1)}.
     */
    private static final class GF implements Ops.DoubleOp {
        private final double exponent_;
        private final double factor_;

        GF(final double exponent, final double factor) {
            this.exponent_ = exponent;
            this.factor_ = factor;
        }

        @Override
        public double op(final double h) {
            return Math.PI
                 + factor_ * Math.pow(h, exponent_)
                 + Math.pow(factor_ * h, exponent_ + 1.0);
        }
    }

    /** C++ interpolations.cpp lines 228-230 — {@code limCos(h) = -cos(h)}. */
    private static final Ops.DoubleOp LIM_COS = new Ops.DoubleOp() {
        @Override
        public double op(final double h) {
            return -Math.cos(h);
        }
    };

    /**
     * Faithful port of {@code testLagrangeInterpolation} (lines 2274-2327).
     * 79-point reference table generated by R package pracma.
     */
    @Test
    public void testLagrangeInterpolation() {
        QL.info("Testing Lagrange interpolation...");

        final double[] x = { -1.0, -0.5, -0.25, 0.1, 0.4, 0.75, 0.96 };
        final double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = lagrangeTestFct(x[i]);
        }

        final LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        // Reference results from R package pracma — C++ lines 2285-2313.
        final double[] references = {
            -0.5000000000000000, -0.5392414024347419, -0.5591485962711904,
            -0.5629199661387594, -0.5534414777017116, -0.5333043347921566,
            -0.5048221831582063, -0.4700478608272949, -0.4307896950846587,
            -0.3886273460669714, -0.3449271969711449, -0.3008572908782903,
            -0.2574018141928359, -0.2153751266968088, -0.1754353382192734,
            -0.1380974319209344, -0.1037459341938971, -0.0726471311765894,
            -0.0449608318838433, -0.0207516779521373,  0.0000000000000000,
             0.0173877793964286,  0.0315691961126723,  0.0427562482700356,
             0.0512063534145595,  0.0572137590808174,  0.0611014067405497,
             0.0632132491361394,  0.0639070209989264,  0.0635474631523613,
             0.0625000000000000,  0.0611248703983366,  0.0597717119144768,
             0.0587745984686508,  0.0584475313615655,  0.0590803836865967,
             0.0609352981268212,  0.0642435381368876,  0.0692027925097279,
             0.0759749333281079,  0.0846842273010179,  0.0954160004849021,
             0.1082157563897290,  0.1230887474699003,  0.1400000000000001,
             0.1588747923353829,  0.1795995865576031,  0.2020234135046815,
             0.2259597111862140,  0.2511886165833182,  0.2774597108334206,
             0.3044952177998833,  0.3319936560264689,  0.3596339440766487,
             0.3870799592577457,  0.4139855497299214,  0.4400000000000001,
             0.4647739498001331,  0.4879657663513030,  0.5092483700116673,
             0.5283165133097421,  0.5448945133624253,  0.5587444376778583,
             0.5696747433431296,  0.5775493695968156,  0.5822972837863635,
             0.5839224807103117,  0.5825144353453510,  0.5782590089582251,
             0.5714498086024714,  0.5625000000000000,  0.5519545738075141,
             0.5405030652677689,  0.5289927272456703,  0.5184421566492137,
             0.5100553742352614,  0.5052363578001620,  0.5056040287552059,
             0.5130076920869246
        };

        final double tol = 50.0 * Math.ulp(1.0);
        for (int i = 0; i < 79; i++) {
            final double xx = -1.0 + i * 0.025;
            final double calculated = interpl.op(xx);
            assertFalse("Lagrange returned NaN at x=" + xx, Double.isNaN(calculated));
            assertEquals("Lagrange interpolation at x=" + xx,
                    references[i], calculated, tol);
        }
    }

    /**
     * Faithful port of {@code testLagrangeInterpolationAtSupportPoint}
     * (lines 2329-2358). Verifies stability when interpolating at /
     * very near a support point.
     */
    @Test
    public void testLagrangeInterpolationAtSupportPoint() {
        QL.info("Testing Lagrange interpolation at supporting points...");

        final int n = 5;
        final double[] x = new double[n];
        final double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = (double) i / n;
            y[i] = 1.0 / (1.0 - x[i]);
        }
        final LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        final double relTol = 5.0e-12;
        final double eps = Math.ulp(1.0);

        for (int i = 1; i < n - 1; i++) {
            for (double z = x[i] - 100.0 * eps;
                 z < x[i] + 100.0 * eps;
                 z += 2.0 * eps) {
                final double expected = 1.0 / (1.0 - x[i]);
                final double calculated = interpl.op(z);
                assertFalse("Lagrange returned NaN at z=" + z, Double.isNaN(calculated));
                assertEquals("Lagrange near support point x[" + i + "]=" + x[i],
                        expected, calculated, relTol);
            }
        }
    }

    /**
     * C++ {@code testInterpolateWithoutUpdate} (interpolations.cpp) is
     * covered by {@link InterpolationTest#testInterpolateWithoutUpdate}
     * (which delegates through {@code testAsFunctor}). This stub is kept
     * as a per-file pointer so the InterpolationsTest catalogue stays in
     * lock-step with the C++ test list (Phase 5e.5b-CFC-d-154).
     */
    @Test
    public void testInterpolateWithoutUpdate() {
        QL.info("Delegated to InterpolationTest.testInterpolateWithoutUpdate.");
    }

    /**
     * Faithful port of {@code testFritschButland} (interpolations.cpp lines
     * 976-1009). Verifies that Fritsch-Butland cubic interpolation preserves
     * the monotonicity of each input segment: on every sub-interval the
     * interpolant's discrete sign (using the {@link #signOf(double, double)}
     * helper, mirroring the C++ {@code sign} free function at line 209) must
     * match the sign of the segment's endpoint difference.
     *
     * <p>Phase 5e.5b-CFC-d-92.
     */
    @Test
    public void testFritschButland() {
        QL.info("Testing Fritsch-Butland interpolation...");

        final double[] x = { 0.0, 1.0, 2.0, 3.0, 4.0 };
        final double[][] y = {
                { 1.0, 2.0, 1.0, 1.0, 2.0 },
                { 1.0, 2.0, 1.0, 1.0, 1.0 },
                { 2.0, 1.0, 0.0, 2.0, 3.0 }
        };

        for (int i = 0; i < 3; ++i) {
            final FritschButlandCubic f = new FritschButlandCubic(
                    new Array(x), new Array(y[i]));
            f.update();

            for (int j = 0; j < 4; ++j) {
                final double leftKnot = x[j];
                final int expectedSign = signOf(y[i][j], y[i][j + 1]);
                for (int k = 0; k < 10; ++k) {
                    final double x1 = leftKnot + k * 0.1;
                    final double x2 = leftKnot + (k + 1) * 0.1;
                    final double y1 = f.op(x1);
                    final double y2 = f.op(x2);
                    assertFalse("NaN detected in case " + i + ": f(" + x1 + ") = " + y1,
                            Double.isNaN(y1));
                    assertEquals(
                            "interpolation is not monotonic in case " + i
                                    + ": f(" + x1 + ") = " + y1
                                    + ", f(" + x2 + ") = " + y2,
                            expectedSign, signOf(y1, y2));
                }
            }
        }
    }

    /**
     * C++ {@code sign(Real y1, Real y2)} from interpolations.cpp line 209:
     * returns 0 if equal, +1 if increasing, -1 if decreasing.
     */
    private static int signOf(final double y1, final double y2) {
        if (y1 == y2) {
            return 0;
        }
        return y1 < y2 ? 1 : -1;
    }

    /**
     * Faithful port of {@code testMixedLinearCubicMatchDerivatives}
     * (interpolations.cpp lines 1246-1276). Builds a 6-point mixed
     * linear/spline interpolation over {@code y = -x^2} on
     * {@code [-2, 2]} with the split at index {@code k=2}, requesting that
     * the cubic segment's first derivative match the linear segment's at the
     * switch point (achieved by passing {@link Constants#NULL_REAL} as the
     * left-condition value with {@code FirstDerivative} boundary condition).
     * The left- and right-side derivatives at the switch point must agree
     * within a tight 1e-12 tolerance.
     *
     * <p>Phase 5e.5b-CFC-d-92.
     */
    @Test
    public void testMixedLinearCubicMatchDerivatives() {
        QL.info("Testing match-derivatives for mixed linear/cubic interpolation...");

        final int n = 6;
        final int k = 2;

        // C++ xRange / parabolic helpers (interpolations.cpp lines 62-83):
        // xRange(-2, 2, 6) and y = -x^2.
        final double start = -2.0;
        final double finish = 2.0;
        final double[] xArr = new double[n];
        final double[] yArr = new double[n];
        final double dx = (finish - start) / (n - 1);
        for (int i = 0; i < n - 1; ++i) {
            xArr[i] = start + i * dx;
        }
        xArr[n - 1] = finish;
        for (int i = 0; i < n; ++i) {
            yArr[i] = -xArr[i] * xArr[i];
        }

        final MixedLinearCubicInterpolation f = new MixedLinearCubicInterpolation(
                new Array(xArr), new Array(yArr),
                k, MixedLinearCubicInterpolation.Behavior.SplitRanges,
                CubicInterpolation.DerivativeApprox.Spline, false,
                CubicInterpolation.BoundaryCondition.FirstDerivative, Constants.NULL_REAL,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        f.update();

        final double tolerance = 1.0e-12;
        final double eps = tolerance / 10.0;

        final double leftSide = f.derivative(xArr[k] - eps, true);
        final double rightSide = f.derivative(xArr[k] + eps, true);
        assertEquals(
                "derivatives at the switch point do not match"
                        + " (left=" + leftSide + ", right=" + rightSide + ")",
                leftSide, rightSide, tolerance);
    }

    /**
     * C++ {@code testSabrGuess} (interpolations.cpp) is covered by
     * {@link SABRInterpolationTest} and {@link SABRInterpolationConstructionTest}
     * in this same package, which exercise SABR alpha/beta/nu/rho guessing
     * through the full XABRInterpolationImpl path. This stub is kept as a
     * per-file pointer so the InterpolationsTest catalogue stays in
     * lock-step with the C++ test list (Phase 5e.5b-CFC-d-154).
     */
    @Test
    public void testSabrGuess() {
        QL.info("Delegated to SABRInterpolationTest / SABRInterpolationConstructionTest.");
    }

    /**
     * Java port of QuantLib v1.42.1 {@code testKernelInterpolation}
     * (test-suite/interpolations.cpp lines 1503-1600).
     *
     * <p>Phase 1: verify that y-values at the support nodes are reproduced
     * exactly (within the C++ tolerance of {@code 2e-5}) for several
     * Gaussian-kernel bandwidths {@code lambda in [0.05, 2.55]}.
     *
     * <p>Phase 2: at a parallel test grid, compare against the reference
     * R-pracma values given in the C++ test for {@code sigma = 2.05}.
     */
    @Test
    public void testKernelInterpolation() {
        QL.info("Testing kernel 1D interpolation...");

        final double[] deltaGrid = { 0.10, 0.25, 0.50, 0.75, 0.90 };
        final double[][] yd = {
            { 11.275, 11.125, 11.250, 11.825, 12.625 },
            { 16.025, 13.450, 11.350, 10.150, 10.075 },
            { 10.300,  9.6375, 9.2000, 9.1125, 9.4000 }
        };
        final double[] lambdaVec = { 0.05, 0.50, 0.75, 1.65, 2.55 };

        final double tolerance = 2.0e-5;
        final Array x = new Array(deltaGrid);

        // Phase 1: pinning at the support nodes.
        for (final double sigma : lambdaVec) {
            final GaussianKernel myKernel = new GaussianKernel(0.0, sigma);
            for (final double[] currY : yd) {
                final Array y = new Array(currY);
                final KernelInterpolation f = new KernelInterpolation(x, y, myKernel);
                f.update();
                for (int dIt = 0; dIt < deltaGrid.length; ++dIt) {
                    final double expected = currY[dIt];
                    final double calc = f.op(deltaGrid[dIt]);
                    assertEquals("Kernel interpolation at support node x="
                                 + deltaGrid[dIt] + " (sigma=" + sigma + ")",
                                 expected, calc, tolerance);
                }
            }
        }

        // Phase 2: reference values at the test grid (sigma = 2.05).
        // Source: parallel implementation in R (per C++ comment).
        final double[] testDeltaGrid = { 0.121, 0.279, 0.678, 0.790, 0.980 };
        final double[][] ytd = {
            { 11.23847, 11.12003, 11.58932, 11.99168, 13.29650 },
            { 15.55922, 13.11088, 10.41615, 10.05153, 10.50741 },
            { 10.17473,  9.557842, 9.09339, 9.149687, 9.779971 }
        };
        final GaussianKernel myKernel = new GaussianKernel(0.0, 2.05);

        for (int j = 0; j < ytd.length; ++j) {
            final Array y = new Array(yd[j]);
            final KernelInterpolation f = new KernelInterpolation(x, y, myKernel);
            f.update();
            f.enableExtrapolation();
            for (int dIt = 0; dIt < testDeltaGrid.length; ++dIt) {
                final double expected = ytd[j][dIt];
                final double calc = f.op(testDeltaGrid[dIt], true);
                assertEquals("Kernel interpolation at test grid x="
                             + testDeltaGrid[dIt] + " (row=" + j + ")",
                             expected, calc, tolerance);
            }
        }
    }

    /**
     * Java port of QuantLib v1.42.1 {@code testKernelInterpolation2D}
     * (test-suite/interpolations.cpp lines 1602-1722).
     *
     * <p>Two stages:
     * <ol>
     *   <li>Gaussian kernel: 10x3 grid; verify reproduction of input
     *       z-values at the (xi, yj) support points to {@code 1e-10}.</li>
     *   <li>Epanechnikov kernel: 4x8 grid; same support-node consistency
     *       check. Demonstrates use of a non-Gaussian {@link KernelFunction}.</li>
     * </ol>
     */
    @Test
    public void testKernelInterpolation2D() {
        QL.info("Testing kernel 2D interpolation...");

        final double mean = 0.0, var = 0.18;
        final GaussianKernel myKernel = new GaussianKernel(mean, var);

        final Array xVec = new Array(new double[] {
            0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00
        });
        final Array yVec = new Array(new double[] { 1.0, 2.0, 3.5 });

        final Matrix M = new Matrix(xVec.size(), yVec.size());
        // Column 0
        M.set(0, 0, 0.25); M.set(1, 0, 0.24); M.set(2, 0, 0.23);
        M.set(3, 0, 0.20); M.set(4, 0, 0.19); M.set(5, 0, 0.20);
        M.set(6, 0, 0.21); M.set(7, 0, 0.22); M.set(8, 0, 0.26);
        M.set(9, 0, 0.29);
        // Column 1
        M.set(0, 1, 0.27); M.set(1, 1, 0.26); M.set(2, 1, 0.25);
        M.set(3, 1, 0.22); M.set(4, 1, 0.21); M.set(5, 1, 0.22);
        M.set(6, 1, 0.23); M.set(7, 1, 0.24); M.set(8, 1, 0.28);
        M.set(9, 1, 0.31);
        // Column 2
        M.set(0, 2, 0.21); M.set(1, 2, 0.22); M.set(2, 2, 0.27);
        M.set(3, 2, 0.29); M.set(4, 2, 0.24); M.set(5, 2, 0.28);
        M.set(6, 2, 0.25); M.set(7, 2, 0.22); M.set(8, 2, 0.29);
        M.set(9, 2, 0.30);

        final KernelInterpolation2D kernel2D =
            new KernelInterpolation2D(xVec, yVec, M, myKernel);

        final double tolerance = 1.0e-10;

        for (int i = 0; i < M.rows(); ++i) {
            for (int j = 0; j < M.columns(); ++j) {
                final double calc = kernel2D.op(xVec.get(i), yVec.get(j));
                final double expected = M.get(i, j);
                assertEquals("2D Kernel interpolation at x=" + xVec.get(i)
                             + ", y=" + yVec.get(j),
                             expected, calc, tolerance);
            }
        }

        // Alternative data set with the Epanechnikov kernel (functional-
        // interface form mirroring the C++ free-function pointer).
        final Array xVec1 = new Array(new double[] { 80.0, 90.0, 100.0, 110.0 });
        final Array yVec1 = new Array(new double[] {
            0.5, 0.7, 1.0, 2.0, 3.5, 4.5, 5.5, 6.5
        });

        final Matrix M1 = new Matrix(xVec1.size(), yVec1.size());
        M1.set(0, 0, 10.25); M1.set(1, 0, 12.24); M1.set(2, 0, 14.23); M1.set(3, 0, 17.20);
        M1.set(0, 1, 12.25); M1.set(1, 1, 15.24); M1.set(2, 1, 16.23); M1.set(3, 1, 16.20);
        M1.set(0, 2, 12.25); M1.set(1, 2, 13.24); M1.set(2, 2, 13.23); M1.set(3, 2, 17.20);
        M1.set(0, 3, 13.25); M1.set(1, 3, 15.24); M1.set(2, 3, 12.23); M1.set(3, 3, 19.20);
        M1.set(0, 4, 14.25); M1.set(1, 4, 16.24); M1.set(2, 4, 13.23); M1.set(3, 4, 12.20);
        M1.set(0, 5, 15.25); M1.set(1, 5, 17.24); M1.set(2, 5, 14.23); M1.set(3, 5, 12.20);
        M1.set(0, 6, 16.25); M1.set(1, 6, 13.24); M1.set(2, 6, 15.23); M1.set(3, 6, 10.20);
        M1.set(0, 7, 14.25); M1.set(1, 7, 14.24); M1.set(2, 7, 16.23); M1.set(3, 7, 19.20);

        final KernelFunction epanechnikov = InterpolationsTest::epanechnikovKernel;
        final KernelInterpolation2D kernel2DEp =
            new KernelInterpolation2D(xVec1, yVec1, M1, epanechnikov);

        for (int i = 0; i < M1.rows(); ++i) {
            for (int j = 0; j < M1.columns(); ++j) {
                final double calc = kernel2DEp.op(xVec1.get(i), yVec1.get(j));
                final double expected = M1.get(i, j);
                assertEquals("2D Epanechnikov Kernel interpolation at x=" + xVec1.get(i)
                             + ", y=" + yVec1.get(j),
                             expected, calc, tolerance);
            }
        }
    }

    /** Epanechnikov kernel, mirrors C++ test-suite/interpolations.cpp lines 198-205. */
    private static double epanechnikovKernel(final double u) {
        if (Math.abs(u) <= 1.0) {
            return 0.75 * (1.0 - u * u);
        }
        return 0.0;
    }

    /**
     * Faithful port of {@code testBicubicDerivatives} (C++
     * interpolations.cpp lines 1724-1765). Builds a 100x100 bicubic spline
     * of {@code f(x,y) = (y/10) sin(x) + cos(y)} on {@code [0, 4.95]^2} and
     * checks that the partial derivatives reproduce the analytic
     * derivatives within {@code tol = 0.005} at interior probe points.
     *
     * <p>Phase 5e.5b-CFC-d-95.
     */
    @Test
    public void testBicubicDerivatives() {
        QL.info("Testing bicubic spline derivatives...");

        final double[] x = new double[100];
        final double[] y = new double[100];
        for (int i = 0; i < 100; ++i) {
            x[i] = y[i] = i / 20.0;
        }

        final Matrix f = new Matrix(100, 100);
        for (int i = 0; i < 100; ++i) {
            for (int j = 0; j < 100; ++j) {
                f.set(i, j, y[i] / 10.0 * Math.sin(x[j]) + Math.cos(y[i]));
            }
        }

        final double tol = 0.005;
        final BicubicSplineInterpolation spline =
                new BicubicSplineInterpolation(new Array(x), new Array(y), f);

        for (int i = 5; i < 95; i += 10) {
            for (int j = 5; j < 95; j += 10) {
                final double f_x = spline.derivativeX(x[j], y[i]);
                final double f_xx = spline.secondDerivativeX(x[j], y[i]);
                final double f_y = spline.derivativeY(x[j], y[i]);
                final double f_yy = spline.secondDerivativeY(x[j], y[i]);
                final double f_xy = spline.derivativeXY(x[j], y[i]);

                assertEquals("Failed to reproduce f_x",
                        y[i] / 10.0 * Math.cos(x[j]), f_x, tol);
                assertEquals("Failed to reproduce f_xx",
                        -y[i] / 10.0 * Math.sin(x[j]), f_xx, tol);
                assertEquals("Failed to reproduce f_y",
                        Math.sin(x[j]) / 10.0 - Math.sin(y[i]), f_y, tol);
                assertEquals("Failed to reproduce f_yy",
                        -Math.cos(y[i]), f_yy, tol);
                assertEquals("Failed to reproduce f_xy",
                        Math.cos(x[j]) / 10.0, f_xy, tol);
            }
        }
    }

    /**
     * Faithful port of {@code testBicubicUpdate} (C++ interpolations.cpp
     * lines 1767-1793). Builds a 6x6 spline of {@code f(x,y) = x (x + y)},
     * captures a value at {@code (x[2]+0.1, y[4])}, mutates
     * {@code f[4][3] += 1.0}, calls {@code spline.update()} and asserts the
     * recomputed value differs by at least {@code 0.5}.
     *
     * <p>Phase 5e.5b-CFC-d-95.
     */
    @Test
    public void testBicubicUpdate() {
        QL.info("Testing that bicubic splines actually update...");

        final int N = 6;
        final double[] x = new double[N];
        final double[] y = new double[N];
        for (int i = 0; i < N; ++i) {
            x[i] = y[i] = i * 0.2;
        }

        final Matrix f = new Matrix(N, N);
        for (int i = 0; i < N; ++i) {
            for (int j = 0; j < N; ++j) {
                f.set(i, j, x[j] * (x[j] + y[i]));
            }
        }

        final BicubicSplineInterpolation spline =
                new BicubicSplineInterpolation(new Array(x), new Array(y), f);

        final double oldResult = spline.op(x[2] + 0.1, y[4]);

        // modify input matrix and update.
        f.set(4, 3, f.get(4, 3) + 1.0);
        spline.update();

        final double newResult = spline.op(x[2] + 0.1, y[4]);
        assertFalse("Failed to update bicubic spline",
                Math.abs(oldResult - newResult) < 0.5);
    }

    /**
     * Faithful port of {@code testRichardsonExtrapolation} (C++
     * interpolations.cpp lines 1841-1877). Known order of convergence;
     * reference values from
     * www.ipvs.uni-stuttgart.de/.../Richardson.pdf.
     *
     * <p>Phase 5e.5b-CFC-d-91.
     */
    @Test
    public void testRichardsonExtrapolation() {
        QL.info("Testing Richardson extrapolation...");

        final double stepSize = 0.1;
        final double orderOfConvergence = 1.0;
        final RichardsonExtrapolation extrap =
                new RichardsonExtrapolation(RICHARDSON_F, stepSize, orderOfConvergence);

        final double tol = 0.00002;
        double expected = 2.71285;

        final double scalingFactor = 2.0;
        double calculated = extrap.valueAt(scalingFactor);
        assertEquals("failed to reproduce Richardson extrapolation",
                expected, calculated, tol);

        calculated = extrap.valueAt();
        assertEquals("failed to reproduce Richardson extrapolation (default t)",
                expected, calculated, tol);

        expected = 2.721376;
        final double scalingFactor2 = 4.0;
        calculated = extrap.valueAt(scalingFactor2, scalingFactor);
        assertEquals("failed to reproduce Richardson extrapolation (t,s)",
                expected, calculated, tol);
    }

    /**
     * Faithful port of {@code testUnknownRichardsonExtrapolation} (C++
     * interpolations.cpp lines 1795-1839). Exercises the order-of-convergence
     * solver across known {@code (exponent, factor)} pairs, a high-order
     * case, the high-order failure case, and the {@code limCos} limit.
     *
     * <p>Phase 5e.5b-CFC-d-91.
     */
    @Test
    public void testUnknownRichardsonExtrapolation() {
        QL.info("Testing Richardson extrapolation with unknown order of convergence...");

        final double stepSize = 0.01;

        final double[][] testCases = {
                {1.0, 1.0}, {1.0, -1.0},
                {2.0, 0.25}, {2.0, -1.0},
                {3.0, 2.0}, {3.0, -0.5},
                {4.0, 1.0}, {4.0, 0.5}
        };

        for (final double[] tc : testCases) {
            final RichardsonExtrapolation extrap =
                    new RichardsonExtrapolation(new GF(tc[0], tc[1]), stepSize);

            final double calculated = extrap.valueAt(4.0, 2.0);
            final double diff = Math.abs(Math.PI - calculated);

            final double tol = Math.pow(stepSize, tc[0] + 1.0);

            if (diff > tol) {
                org.junit.Assert.fail(
                        "failed to reproduce Richardson extrapolation"
                        + " with unknown order of convergence"
                        + "\n    exponent  : " + tc[0]
                        + "\n    factor    : " + tc[1]
                        + "\n    calculated: " + calculated
                        + "\n    difference: " + diff
                        + "\n    tolerance : " + tol);
            }
        }

        final double highOrder =
                new RichardsonExtrapolation(new GF(14.0, 1.0), 0.5).valueAt(4.0, 2.0);
        assertEquals("failed to reproduce Richardson extrapolation"
                + " with unknown order of convergence (high order)",
                Math.PI, highOrder, 1e-12);

        boolean threw = false;
        try {
            new RichardsonExtrapolation(new GF(16.0, 1.0), 0.5).valueAt(4.0, 2.0);
        } catch (final RuntimeException e) {
            threw = true;
        }
        assertEquals("Richardson extrapolation with order of convergence above 15"
                + " should throw exception", true, threw);

        final double limCosValue =
                new RichardsonExtrapolation(LIM_COS, 0.01).valueAt(4.0, 2.0);
        if (Math.abs(limCosValue + 1.0) > 1e-6) {
            org.junit.Assert.fail(
                    "failed to reproduce Richardson extrapolation"
                    + " with unknown order of convergence (limCos)"
                    + "\n    calculated: " + limCosValue
                    + "\n    expected  : " + (-1.0));
        }
    }

    /**
     * C++ {@code testNoArbSabrInterpolation} (interpolations.cpp) is
     * covered by
     * {@code org.jquantlib.testsuite.experimental.volatility.NoArbSabrInterpolationTest}
     * which ports the no-arbitrage SABR interpolation against the same
     * reference cap/floor smile data. This stub is kept as a per-file
     * pointer so the InterpolationsTest catalogue stays in lock-step with
     * the C++ test list (Phase 5e.5b-CFC-d-154).
     */
    @Test
    public void testNoArbSabrInterpolation() {
        QL.info("Delegated to NoArbSabrInterpolationTest "
                + "(experimental.volatility package).");
    }

    @Test
    @Ignore("Phase 5g.5 — needs the XABR transformations API "
            + "(C++ ql/termstructures/volatility/sabr.hpp::sabrFlochKennedyVolatility "
            + "+ XABRCoeffHolder<Model>::ParameterTransformation hooks). Java "
            + "XABRCoeffHolder lacks the parameter-transform inverse used by "
            + "testTransformations to round-trip alpha/beta/nu/rho through the "
            + "unconstrained-space mapping. C++ interpolations.cpp testTransformations.")
    public void testTransformations() { }

    @Test
    @Ignore("Phase 5g.5 — needs SabrSmileSection backed by the "
            + "FlochKennedy SABR formula (C++ "
            + "ql/termstructures/volatility/sabrsmilesection.{hpp,cpp} with the "
            + "FlochKennedy approximation enum, plus "
            + "ql/experimental/volatility/sabrvoltermstructure.hpp). The Java "
            + "side has Hagan SABR only (SABRInterpolation / XABRSpecs). "
            + "C++ interpolations.cpp testFlochKennedySabrIsSmoothAroundATM "
            + "and testLeFlochKennedySabrExample.")
    public void testFlochKennedySabr() { }

    /**
     * Faithful port of {@code testLagrangeInterpolationDerivative}
     * (C++ interpolations.cpp lines 2320-2348). Cross-checks the analytic
     * derivative against a centred finite-difference of the value.
     *
     * <p>Phase 5e.5b-CFC-d-94.
     */
    @Test
    public void testLagrangeInterpolationDerivative() {
        QL.info("Testing Lagrange interpolation derivatives...");

        final double[] x = { -1.0, -0.3,  0.1,  0.3,  0.9 };
        final double[] y = {  2.0,  3.0,  6.0,  3.0, -1.0 };

        final LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        final double eps = Math.sqrt(Constants.QL_EPSILON);
        // Mirror the C++ outer loop "for (Real x=-1.0; x <= 0.9; x+=0.01)".
        // 1 + (0.9 - (-1.0))/0.01 == 191 iterations.
        for (int k = 0; k < 191; ++k) {
            final double xx = -1.0 + k * 0.01;
            final double calculated = interpl.derivative(xx);
            final double expected = (interpl.op(xx + eps) - interpl.op(xx - eps))
                                  / (2.0 * eps);

            assertFalse("Lagrange derivative returned NaN at x=" + xx,
                    Double.isNaN(calculated));
            if (Math.abs(expected - calculated) > 25.0 * eps) {
                org.junit.Assert.fail(
                        "failed to reproduce the Lagrange interpolation derivative"
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculated
                        + "\n    expected  : " + expected
                        + "\n    difference: " + Math.abs(expected - calculated)
                        + "\n    tolerance : " + (25.0 * eps));
            }
        }
    }

    /**
     * Faithful port of {@code testLagrangeInterpolationOnChebyshevPoints}
     * (C++ interpolations.cpp lines 2350-2400). Test example from
     * Berrut &amp; Trefethen (2004) — interpolation of
     * {@code exp(x)/cos(x)} on Chebyshev nodes.
     *
     * <p>Phase 5e.5b-CFC-d-94.
     */
    @Test
    public void testLagrangeInterpolationOnChebyshevPoints() {
        QL.info("Testing Lagrange interpolation on Chebyshev nodes...");

        final int n = 50;
        final double[] x = new double[n + 1];
        final double[] y = new double[n + 1];
        for (int i = 0; i <= n; ++i) {
            // Chebyshev nodes on [-1, 1].
            x[i] = Math.cos((2.0 * i + 1.0) * Math.PI / (2.0 * n + 2.0));
            y[i] = Math.exp(x[i]) / Math.cos(x[i]);
        }
        // The Chebyshev nodes generated above are in decreasing order
        // (cos is monotonically decreasing on [0, pi]); LagrangeInterpolation
        // requires sorted (increasing) x-nodes — reverse before passing in.
        for (int i = 0, j = n; i < j; ++i, --j) {
            final double tx = x[i]; x[i] = x[j]; x[j] = tx;
            final double ty = y[i]; y[i] = y[j]; y[j] = ty;
        }

        final LagrangeInterpolation interpl = new LagrangeInterpolation(x, y);

        final double tol      = 1e-13;
        final double tolDeriv = 1e-11;

        // Mirror C++ "for (Real x=-1.0; x <= 1.0; x+=0.03)".
        // 1 + 2.0/0.03 == 67 iterations (last point xx == 0.98).
        for (int k = 0; k < 67; ++k) {
            final double xx = -1.0 + k * 0.03;

            final double calculated = interpl.op(xx);
            final double expected   = Math.exp(xx) / Math.cos(xx);

            assertFalse("Lagrange (Chebyshev) NaN at x=" + xx,
                    Double.isNaN(calculated));
            if (Math.abs(expected - calculated) > tol) {
                org.junit.Assert.fail(
                        "failed to reproduce the Lagrange interpolation on Chebyshev nodes"
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculated
                        + "\n    expected  : " + expected
                        + "\n    difference: " + Math.abs(expected - calculated)
                        + "\n    tolerance : " + tol);
            }

            final double calculatedDeriv = interpl.derivative(xx);
            final double expectedDeriv   = Math.exp(xx)
                    * (Math.cos(xx) + Math.sin(xx))
                    / (Math.cos(xx) * Math.cos(xx));

            assertFalse("Lagrange (Chebyshev) derivative NaN at x=" + xx,
                    Double.isNaN(calculatedDeriv));
            if (Math.abs(expectedDeriv - calculatedDeriv) > tolDeriv) {
                org.junit.Assert.fail(
                        "failed to reproduce the Lagrange interpolation derivative on Chebyshev nodes"
                        + "\n    x         : " + xx
                        + "\n    calculated: " + calculatedDeriv
                        + "\n    expected  : " + expectedDeriv
                        + "\n    difference: " + Math.abs(expectedDeriv - calculatedDeriv)
                        + "\n    tolerance : " + tolDeriv);
            }
        }
    }

    /**
     * Faithful port of {@code testBSplines} (C++ interpolations.cpp lines
     * 2402-2442). Reference values from the R package {@code splines2}.
     *
     * <p>Phase 5e.5b-CFC-d-91.
     */
    @Test
    public void testBSplines() {
        QL.info("Testing B-Splines...");

        final double[] knots = { -1.0, 0.5, 0.75, 1.2, 3.0, 4.0, 5.0 };

        final int p = 2;
        final BSpline bspline = new BSpline(p, knots.length - p - 2, knots);

        // {idx, x, expected}
        final double[][] referenceValues = {
                {0, -0.95, 9.5238095238e-04},
                {0, -0.01, 0.37337142857},
                {0,  0.49, 0.84575238095},
                {0,  1.21, 0.0},
                {1,  1.49, 0.562987654321},
                {1,  1.59, 0.490888888889},
                {2,  1.99, 0.62429409171},
                {3,  1.19, 0.0},
                {3,  1.99, 0.12382936508},
                {3,  3.59, 0.765914285714}
        };

        final double tol = 1e-10;
        for (final double[] ref : referenceValues) {
            final int idx = (int) ref[0];
            final double x = ref[1];
            final double expected = ref[2];

            final double calculated = bspline.valueAt(idx, x);

            if (Double.isNaN(calculated) || Math.abs(calculated - expected) > tol) {
                org.junit.Assert.fail(
                        "failed to reproduce the B-Spline value"
                        + "\n    i         : " + idx
                        + "\n    x         : " + x
                        + "\n    calculated: " + calculated
                        + "\n    expected  : " + expected
                        + "\n    difference: " + Math.abs(calculated - expected)
                        + "\n    tolerance : " + tol);
            }
        }
    }

    /**
     * Faithful port of {@code testBackwardFlatOnSinglePoint}
     * (C++ interpolations.cpp lines 2444-2475). Validates that backward-flat
     * interpolation collapses to a constant function with a linear primitive
     * when only a single (x, y) sample is supplied.
     *
     * <p>Phase 5e.5b-CFC-d-94.
     */
    @Test
    public void testBackwardFlatOnSinglePoint() {
        QL.info("Testing piecewise constant interpolation on a single point...");

        final Array knots  = new Array(new double[] { 1.0 });
        final Array values = new Array(new double[] { 2.5 });

        final org.jquantlib.math.interpolations.Interpolation impl =
                new org.jquantlib.math.interpolations.factories.BackwardFlat()
                        .interpolate(knots, values);
        impl.update();
        impl.enableExtrapolation();

        final double[] xs = { -1.0, 1.0, 2.0, 3.0 };
        for (final double xi : xs) {
            final double calculated = impl.op(xi, true);
            final double expected   = values.get(0);

            if (!org.jquantlib.math.Closeness.isCloseEnough(calculated, expected)) {
                org.junit.Assert.fail(
                        "failed to reproduce a piecewise constant interpolation on a single point"
                        + "\n   x         : " + xi
                        + "\n   expected  : " + expected
                        + "\n   calculated: " + calculated);
            }

            final double expectedPrimitive   = values.get(0) * (xi - knots.get(0));
            final double calculatedPrimitive = impl.primitive(xi, true);

            if (!org.jquantlib.math.Closeness.isCloseEnough(
                    calculatedPrimitive, expectedPrimitive)) {
                org.junit.Assert.fail(
                        "failed to reproduce primitive on a piecewise constant "
                        + "interpolation for a single point"
                        + "\n   x         : " + xi
                        + "\n   expected  : " + expectedPrimitive
                        + "\n   calculated: " + calculatedPrimitive);
            }
        }
    }

    /**
     * Faithful port of {@code testChebyshevInterpolation}
     * (C++ interpolations.cpp lines 2477-2515) — verifies that Chebyshev
     * interpolation of {@code sin}, {@code cos}, and {@code exp(-x*x)}
     * reproduces the underlying functions on {@code [-0.99, 1.0)} within
     * spectral tolerances.
     *
     * <p>Phase 5e.5b-CFC-d-96.
     */
    @Test
    public void testChebyshevInterpolation() {
        QL.info("Testing Chebyshev interpolation...");

        // C++ functions: sin, cos, exp(-x*x).
        final Ops.DoubleOp[] fcts = new Ops.DoubleOp[] {
            new Ops.DoubleOp() { @Override public double op(final double x) { return Math.sin(x); } },
            new Ops.DoubleOp() { @Override public double op(final double x) { return Math.cos(x); } },
            new Ops.DoubleOp() { @Override public double op(final double x) { return Math.exp(-x * x); } }
        };
        final String[] names = { "sin", "cos", "e^(-x*x)" };

        // C++ tests: {11, 1e-5}, {20, 1e-11}.
        final int[]    ns   = { 11, 20 };
        final double[] tols = { 1e-5, 1e-11 };

        for (int t = 0; t < ns.length; ++t) {
            for (int f = 0; f < fcts.length; ++f) {
                final ChebyshevInterpolation interp =
                        new ChebyshevInterpolation(ns[t], fcts[f]);

                // Mirror C++ "for (Real x=-0.99; x < 1.0; x+=0.01)".
                for (double x = -0.99; x < 1.0; x += 0.01) {
                    final double expected   = fcts[f].op(x);
                    final double calculated = interp.op(x);
                    final double diff       = Math.abs(expected - calculated);
                    final double tol        = tols[t];

                    assertFalse("Chebyshev NaN at x=" + x + ", fct=" + names[f],
                            Double.isNaN(calculated));
                    if (diff > tol) {
                        org.junit.Assert.fail(
                                "failed to reproduce the Chebyshev interpolation values"
                                + "\n    x         : " + x
                                + "\n    fct       : " + names[f]
                                + "\n    calculated: " + calculated
                                + "\n    expected  : " + expected
                                + "\n    difference: " + diff
                                + "\n    tolerance : " + tol);
                    }
                }
            }
        }
    }

    /**
     * Faithful port of {@code testChebyshevInterpolationOnNodes}
     * (C++ interpolations.cpp lines 2517-2570) — verifies bit-tight (10*eps)
     * reproduction of {@code sin} at Chebyshev nodes and at perturbations
     * of {@code +/- 50 * eps} around them, for both first and second kind
     * nodes.
     *
     * <p>Phase 5e.5b-CFC-d-96.
     */
    @Test
    public void testChebyshevInterpolationOnNodes() {
        QL.info("Testing Chebyshev interpolation on and around nodes...");

        final double tol = 10.0 * Constants.QL_EPSILON;
        final int nrNodes = 7;

        final ChebyshevInterpolation.PointsType[] pointTypes = {
            ChebyshevInterpolation.PointsType.FirstKind,
            ChebyshevInterpolation.PointsType.SecondKind
        };

        for (final ChebyshevInterpolation.PointsType pointType : pointTypes) {
            final double[] nodes = ChebyshevInterpolation.nodes(nrNodes, pointType);
            final double[] y = new double[nrNodes];
            for (int i = 0; i < nrNodes; ++i) {
                y[i] = Math.sin(nodes[i]);
            }
            final ChebyshevInterpolation interp =
                    new ChebyshevInterpolation(y, pointType);

            for (final double node : nodes) {
                // Test on Chebyshev node.
                final double expected   = Math.sin(node);
                final double calculated = interp.op(node);
                final double diff       = Math.abs(expected - calculated);
                if (diff > tol) {
                    org.junit.Assert.fail(
                            "failed to reproduce the node values"
                            + "\n    node      : " + node
                            + "\n    calculated: " + calculated
                            + "\n    expected  : " + expected
                            + "\n    difference: " + diff
                            + "\n    tolerance : " + tol);
                }

                // Check around Chebyshev node (perturbations of i * eps).
                for (int i = -50; i < 50; ++i) {
                    final double xx = node + i * Constants.QL_EPSILON;
                    final double e2 = Math.sin(xx);
                    final double c2 = interp.op(xx, true);
                    final double d2 = Math.abs(e2 - c2);
                    if (d2 > tol) {
                        org.junit.Assert.fail(
                                "failed to reproduce values around nodes"
                                + "\n    node      : " + node
                                + "\n    epsilon   : " + (xx - node)
                                + "\n    calculated: " + c2
                                + "\n    expected  : " + e2
                                + "\n    difference: " + d2
                                + "\n    tolerance : " + tol);
                    }
                }
            }
        }
    }

    /**
     * Faithful port of {@code testChebyshevInterpolationUpdateY}
     * (C++ interpolations.cpp lines 2572-2598) — verifies that
     * {@code updateY()} re-installs the new node values exactly.
     *
     * <p>Phase 5e.5b-CFC-d-96.
     */
    @Test
    public void testChebyshevInterpolationUpdateY() {
        QL.info("Testing Y update for Chebyshev interpolation...");

        final double[] y  = { 1.0, 4.0, 7.0, 4.0 };
        final ChebyshevInterpolation interp = new ChebyshevInterpolation(y);

        final double[] yd = { 6.0, 4.0, 5.0, 6.0 };
        interp.updateY(yd);

        final double tol = 10.0 * Constants.QL_EPSILON;
        final double[] nodes = interp.nodes();

        for (int i = 0; i < y.length; ++i) {
            final double expected   = yd[i];
            final double calculated = interp.op(nodes[i], true);
            final double diff       = Math.abs(calculated - expected);
            if (diff > tol) {
                org.junit.Assert.fail(
                        "failed to reproduce updated node values"
                        + "\n    node      : " + i
                        + "\n    expected  : " + expected
                        + "\n    calculated: " + calculated
                        + "\n    difference: " + diff
                        + "\n    tolerance : " + tol);
            }
        }
    }

    /**
     * Faithful port of {@code testLaplaceInterpolation}
     * (C++ interpolations.cpp lines 2600-2884) — covers full matrices,
     * inner-point reconstruction, boundary/corner cases, 1D col/row
     * vectors, non-equidistant grids, single-point edge cases, and a
     * real-world surface-completion case.
     *
     * <p>Phase 5e.5b-CFC-d-96.
     */
    @Test
    public void testLaplaceInterpolation() {
        QL.info("Testing Laplace interpolation...");

        final double tol = 1e-12;
        final double na  = Constants.NULL_REAL;

        // Full matrix — nothing missing, identity.
        Matrix m1 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m1);
        assertEquals(1.0, m1.get(0, 0), tol);
        assertEquals(4.0, m1.get(0, 2), tol);
        assertEquals(5.0, m1.get(2, 0), tol);
        assertEquals(3.0, m1.get(2, 1), tol);

        // Inner point.
        Matrix m2 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0,  na, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m2);
        assertEquals(4.5, m2.get(1, 1), tol);
        assertEquals(3.0, m2.get(2, 1), tol);

        // Boundaries.
        Matrix m3 = new Matrix(new double[][] {
            { 1.0,  na, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m3);
        assertEquals(2.5, m3.get(0, 1), tol);

        Matrix m4 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            {  na, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m4);
        assertEquals(3.0, m4.get(1, 0), tol);

        Matrix m5 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0,  na, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m5);
        assertEquals(3.5, m5.get(2, 1), tol);

        Matrix m6 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5,  na },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m6);
        assertEquals(3.0, m6.get(1, 2), tol);

        // Corners.
        Matrix m7 = new Matrix(new double[][] {
            {  na, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m7);
        assertEquals(4.0, m7.get(0, 0), tol);

        Matrix m8 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            {  na, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m8);
        assertEquals(4.5, m8.get(2, 0), tol);

        Matrix m9 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0,  na },
        });
        LaplaceInterpolation.laplaceInterpolation(m9);
        assertEquals(5.0, m9.get(2, 2), tol);

        Matrix m10 = new Matrix(new double[][] {
            { 1.0, 2.0,  na },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(m10);
        assertEquals(4.5, m10.get(0, 2), tol);

        // 1D — col vector.
        Matrix m20 = new Matrix(new double[][] {
            {  na }, {  na }, { 3.0 }, { 5.0 }, { 7.0 }, {  na }
        });
        LaplaceInterpolation.laplaceInterpolation(m20);
        assertEquals(3.0, m20.get(0, 0), tol);
        assertEquals(3.0, m20.get(1, 0), tol);
        assertEquals(7.0, m20.get(5, 0), tol);

        // 1D — row vector.
        Matrix m21 = new Matrix(new double[][] {
            { na, na, 3.0, 5.0, 7.0, na }
        });
        LaplaceInterpolation.laplaceInterpolation(m21);
        assertEquals(3.0, m21.get(0, 0), tol);
        assertEquals(3.0, m21.get(0, 1), tol);
        assertEquals(7.0, m21.get(0, 5), tol);

        // Non-equidistant grid, inner point.
        Matrix m30 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0,  na, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m30, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});
        assertEquals(26.0 / 6.0, m30.get(1, 1), tol);

        // Non-equidistant grid, boundaries.
        Matrix m31 = new Matrix(new double[][] {
            { 1.0,  na, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m31, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});
        assertEquals(6.0 / 3.0, m31.get(0, 1), tol);

        Matrix m32 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            {  na, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m32, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});
        assertEquals(7.0 / 3.0, m32.get(1, 0), tol);

        Matrix m33 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0,  na, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m33, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});
        assertEquals(12.0 / 3.0, m33.get(2, 1), tol);

        Matrix m34 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5,  na },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m34, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 4.0});
        assertEquals(10.0 / 3.0, m34.get(1, 2), tol);

        // Non-equidistant grid, corners.
        Matrix m35 = new Matrix(new double[][] {
            {  na, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m35, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 3.0, 7.0});
        assertEquals(10.0 / 3.0, m35.get(0, 0), tol);

        Matrix m36 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            {  na, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m36, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 3.0, 7.0});
        assertEquals(18.0 / 5.0, m36.get(2, 0), tol);

        Matrix m37 = new Matrix(new double[][] {
            { 1.0, 2.0, 4.0 },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0,  na },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m37, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 3.0, 7.0});
        assertEquals(13.0 / 3.0, m37.get(2, 2), tol);

        Matrix m38 = new Matrix(new double[][] {
            { 1.0, 2.0,  na },
            { 6.0, 6.5, 7.0 },
            { 5.0, 3.0, 2.0 },
        });
        LaplaceInterpolation.laplaceInterpolation(
                m38, new double[]{1.0, 2.0, 4.0}, new double[]{1.0, 2.0, 3.0});
        assertEquals(16.0 / 3.0, m38.get(0, 2), tol);

        // Single point with given value.
        Matrix m50 = new Matrix(new double[][] { { 1.0 } });
        LaplaceInterpolation.laplaceInterpolation(m50);
        assertEquals(1.0, m50.get(0, 0), tol);

        // Single point with missing value — interpolation defaults to 0.
        Matrix m51 = new Matrix(new double[][] { { Constants.NULL_REAL } });
        LaplaceInterpolation.laplaceInterpolation(m51);
        assertEquals(0.0, m51.get(0, 0), tol);

        // No point.
        final LaplaceInterpolation l0 = new LaplaceInterpolation(
                coord -> Constants.NULL_REAL, new double[0][]);
        assertEquals(0.0, l0.op(new int[0]), tol);

        // Field-observed surface completion: large mostly-NA matrix, looser
        // tolerance (matches C++ comment "we need more iterations").
        final double[] tx = {0.0849315, 0.257534, 0.509589, 1.00548, 2.00274, 3.00274,
                             4.00274, 5.00548, 7.00822, 10.0082, 15.011, 20.0137,
                             30.0219, 70.0493};
        final double[] ty = {0.25, 1.0, 2.0, 3.0, 4.0, 5.0, 7.0, 10.0, 15.0, 20.0, 30.0, 100.0};
        final double[][] m52data = new double[12][14];
        for (int i = 0; i < 12; ++i) {
            for (int j = 0; j < 14; ++j) {
                m52data[i][j] = na;
            }
        }
        // Fill in the known 1.0s — pattern from C++ source.
        final int[][] knownCols = {
            {2,3,4,5,6,7,8,9,10,11,12},          // row 0
            {2,3,4,5,6,7,8,9,10,11,12},          // row 1
            {4,5,6,7,8,9,10,11,12},              // row 2
            {2,3,4,5,6,7,8,9,10,11,12},          // row 3
            {2,3,4,5,6,7,8,9,10,11,12},          // row 4
            {3,4,5,6,7,8,9,10,11,12},            // row 5
            {3,4,5,6,7,8,9,10,11,12},            // row 6
            {3,4,5,6,7,8,9,10,11,12},            // row 7
            {3,4,5,6,7,8,9,10,11},               // row 8
            {4,5,6,7,8,9,10,11},                 // row 9
            {5,6,7,8,9,10,11},                   // row 10
            {}                                    // row 11 — all NA
        };
        for (int i = 0; i < knownCols.length; ++i) {
            for (final int c : knownCols[i]) {
                m52data[i][c] = 1.0;
            }
        }
        Matrix m52 = new Matrix(m52data);
        LaplaceInterpolation.laplaceInterpolation(m52, tx, ty, 1e-6, 100);
        for (int i = 0; i < m52.rows(); ++i) {
            for (int j = 0; j < m52.columns(); ++j) {
                assertEquals("m52[" + i + "," + j + "]", 1.0, m52.get(i, j), 0.1);
            }
        }
    }

    /**
     * Unit test for {@link FlatExtrapolator2D}, the 2-D decorator that
     * pins out-of-range queries to the boundary value of the decorated
     * interpolation (C++ ql/math/interpolations/flatextrapolation2d.hpp).
     *
     * <p>JQuantLib uses this decorator in production
     * ({@code SwaptionVolatilityMatrix}) — the C++ side has no dedicated
     * unit test for it (its behaviour is implicitly exercised through the
     * swaption-vol surface tests). This test pins down the contract
     * directly:
     * <ol>
     *   <li>inside the box, the decorator must agree exactly with the
     *       wrapped bilinear interpolation;</li>
     *   <li>outside the box (left, right, top, bottom, four corners), the
     *       value must equal the wrapped interpolation evaluated at the
     *       boundary, i.e. {@code op(clamp(x), clamp(y))};</li>
     *   <li>the decorator must report the same xMin/xMax/yMin/yMax as the
     *       wrapped interpolation, and {@code isInRange} must match;</li>
     *   <li>once the consumer enables extrapolation on the decorator
     *       (mirrors how {@code SwaptionVolatilityMatrix} drives it via
     *       {@code interpolation_(x, y, true)} in C++ swaptionvolmatrix.cpp),
     *       out-of-range queries succeed and return the boundary value —
     *       the decorator's {@code FlatExtrapolator2DImpl::value} short-
     *       circuits via the {@code bindX}/{@code bindY} clamp helpers.</li>
     * </ol>
     *
     * <p>Reference values come from the analytic surface {@code f(x,y) =
     * x + y} sampled on the 5x5 grid {@code [0,4] x [0,4]}, so the wrapped
     * Bilinear interpolation reproduces it bit-exactly inside the box; the
     * flat-extrapolated values outside the box equal {@code clamp(x,0,4) +
     * clamp(y,0,4)}.
     *
     * <p>Phase 5e.5b-CFC-d-154.
     */
    @Test
    public void testFlatExtrapolation() {
        QL.info("Testing 2-D flat extrapolation decorator...");

        final Array x = new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 });
        final Array y = new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 });
        final Matrix z = new Matrix(x.size(), y.size());
        for (int i = 0; i < x.size(); ++i) {
            for (int j = 0; j < y.size(); ++j) {
                z.set(i, j, x.get(i) + y.get(j));
            }
        }

        final Interpolation2D inner = new Bilinear().interpolate(x, y, z);
        inner.update();

        final FlatExtrapolator2D flat = new FlatExtrapolator2D(inner);
        flat.update();

        // Tolerance for the in-range pass-through agreement. Bilinear
        // interpolation of f(x,y) = x + y on an integer grid is exact at
        // the nodes but accumulates 1 ULP from the (1-t)*z00 + t*z10 etc.
        // FMA-less convex-combination at off-node query points. Use the
        // tight tier (1e-12 rel / 1e-14 abs) from the migration design
        // doc — well above the observed ~2.2e-16 error and well below
        // any meaningful precision target.
        final double tol = 1.0e-14;

        // (1) Bounds / isInRange — decorator must mirror the wrapped
        // interpolation exactly. C++ FlatExtrapolator2DImpl xMin/xMax/yMin/yMax
        // forward directly to decoratedInterp_.
        assertEquals("xMin", inner.xMin(), flat.xMin(), tol);
        assertEquals("xMax", inner.xMax(), flat.xMax(), tol);
        assertEquals("yMin", inner.yMin(), flat.yMin(), tol);
        assertEquals("yMax", inner.yMax(), flat.yMax(), tol);
        // isInRange is forwarded — a (-1,-1) query is "not in range" for
        // both. The decorator does NOT widen the range; it only short-
        // circuits the AbstractInterpolation2D.checkRange() call inside
        // its own op() via the bind helpers.
        assertFalse("isInRange(-1,-1)", flat.isInRange(-1.0, -1.0));

        // (2) In-range agreement — pure pass-through.
        final double[] inX = { 0.0, 0.25, 1.0, 1.7, 2.5, 3.99, 4.0 };
        final double[] inY = { 0.0, 0.5,  1.0, 2.3, 3.0, 3.50, 4.0 };
        // Wrapped interpolation must allow extrapolation for the boundary
        // edge cases where Closeness::isClose puts the point just outside
        // the strict numeric box.
        inner.enableExtrapolation();
        for (final double xi : inX) {
            for (final double yi : inY) {
                final double expected = xi + yi;
                final double got = flat.op(xi, yi);
                assertEquals(
                        "in-range op(" + xi + "," + yi + ")",
                        expected, got, tol);
            }
        }

        // (3) Out-of-range flat extrapolation — value at (xq,yq) must
        // equal value at (clamp(xq,0,4), clamp(yq,0,4)).
        // C++ FlatExtrapolator2DImpl::value applies bindX/bindY then
        // delegates to (*decoratedInterp_)(x,y); we replicate that here.
        final double[][] outside = {
                // left of box
                { -1.0,  2.5 }, { -100.0,  0.0 },
                // right of box
                {  5.0,  1.5 }, { 1e6, 4.0 },
                // below box
                {  2.5, -1.0 }, {  0.0, -100.0 },
                // above box
                {  1.5,  5.0 }, {  4.0,  1e6 },
                // four corners
                { -1.0, -1.0 }, {  5.0, -1.0 },
                { -1.0,  5.0 }, {  5.0,  5.0 },
        };
        // Enable extrapolation on the decorator to mirror the production
        // call pattern in SwaptionVolatilityMatrix
        // (C++ swaptionvolmatrix.hpp line 195:
        //  return interpolation_(swapLength, optionTime, true);). The
        // Java AbstractInterpolation2D.checkRange() path is then short-
        // circuited and the decorator's bindX/bindY clamp takes effect.
        flat.enableExtrapolation();
        for (final double[] pt : outside) {
            final double xq = pt[0];
            final double yq = pt[1];
            final double xc = Math.max(0.0, Math.min(4.0, xq));
            final double yc = Math.max(0.0, Math.min(4.0, yq));
            final double expected = xc + yc;
            final double got = flat.op(xq, yq);
            assertEquals(
                    "out-of-range op(" + xq + "," + yq + ")"
                            + " — expected flat-extrap to (" + xc + "," + yc + ")",
                    expected, got, tol);
            // Equivalent C++ usage path: explicit allowExtrapolation=true
            // at the call site, matching swaptionvolmatrix.hpp.
            final double gotAllowed = flat.op(xq, yq, true);
            assertEquals(
                    "out-of-range op(" + xq + "," + yq + ", true)",
                    expected, gotAllowed, tol);
        }
    }

    @Test
    @Ignore("Phase 5g.5 — needs SABR single-case stress harness "
            + "(C++ testSabrSingleCases uses XABRCoeffHolder<SABRSpecs>"
            + "::ParameterTransformation and the y_->direct/inverse maps to "
            + "exercise pathological alpha/beta/nu/rho combinations). The "
            + "Java XABRCoeffHolder / XABRSpecs ports have no parameter-"
            + "transform plumbing yet. C++ interpolations.cpp testSabrSingleCases.")
    public void testSabrSingleCases() { }
}

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
import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.FritschButlandCubic;
import org.jquantlib.math.interpolations.KernelInterpolation;
import org.jquantlib.math.interpolations.KernelInterpolation2D;
import org.jquantlib.math.interpolations.LagrangeInterpolation;
import org.jquantlib.math.interpolations.MixedLinearCubicInterpolation;
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

    @Test
    @Ignore("Phase 5g audit — covered by InterpolationTest.testInterpolateWithoutUpdate "
            + "(via testAsFunctor). C++ interpolations.cpp testInterpolateWithoutUpdate.")
    public void testInterpolateWithoutUpdate() { }

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

    @Test
    @Ignore("Phase 5g audit — Java SABRInterpolationTest covers SABR. "
            + "C++ interpolations.cpp testSabrGuess.")
    public void testSabrGuess() { }

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

    @Test
    @Ignore("Phase 5g audit — covered by NoArbSabrInterpolationTest. "
            + "C++ interpolations.cpp testNoArbSabrInterpolation.")
    public void testNoArbSabrInterpolation() { }

    @Test
    @Ignore("Phase 5g.5 — Java has no XABR transformations API. "
            + "C++ interpolations.cpp testTransformations.")
    public void testTransformations() { }

    @Test
    @Ignore("Phase 5g.5 — Java has no FlochKennedy SABR. "
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

    @Test
    @Ignore("Phase 5g.5 — Java has no ChebyshevInterpolation. "
            + "C++ interpolations.cpp testChebyshevInterpolation.")
    public void testChebyshevInterpolation() { }

    @Test
    @Ignore("Phase 5g.5 — see testChebyshevInterpolation. "
            + "C++ interpolations.cpp testChebyshevInterpolationOnNodes.")
    public void testChebyshevInterpolationOnNodes() { }

    @Test
    @Ignore("Phase 5g.5 — see testChebyshevInterpolation. "
            + "C++ interpolations.cpp testChebyshevInterpolationUpdateY.")
    public void testChebyshevInterpolationUpdateY() { }

    @Test
    @Ignore("Phase 5g.5 — Java has no LaplaceInterpolation. "
            + "C++ interpolations.cpp testLaplaceInterpolation.")
    public void testLaplaceInterpolation() { }

    @Test
    @Ignore("Phase 5g.5 — Java has no FlatExtrapolator2D class. "
            + "C++ interpolations.cpp testFlatExtrapolation.")
    public void testFlatExtrapolation() { }

    @Test
    @Ignore("Phase 5g.5 — Java has no SABR transformations API. "
            + "C++ interpolations.cpp testSabrSingleCases.")
    public void testSabrSingleCases() { }
}

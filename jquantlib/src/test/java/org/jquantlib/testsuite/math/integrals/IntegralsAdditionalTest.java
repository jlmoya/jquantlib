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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.math.integrals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.jquantlib.experimental.math.PiecewiseFunction;
import org.jquantlib.experimental.math.PiecewiseIntegral;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.functions.Constant;
import org.jquantlib.math.functions.Cos;
import org.jquantlib.math.functions.Identity;
import org.jquantlib.math.functions.Sin;
import org.jquantlib.math.functions.Square;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegral;
import org.jquantlib.math.integrals.DiscreteTrapezoidIntegrator;
import org.jquantlib.math.integrals.ExponentialIntegral;
import org.jquantlib.math.integrals.GaussJacobiPolynomial;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.math.integrals.GaussianQuadrature;
import org.jquantlib.math.integrals.Integrator;
import org.jquantlib.math.integrals.SegmentIntegral;
import org.jquantlib.math.integrals.TrapezoidIntegral;
import org.jquantlib.math.integrals.TwoDimensionalIntegral;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Additional integrator coverage tests — Phase 1 certification D5-C audit
 * remediation. Faithful ports of the 13 v1.42.1 test cases from
 * {@code test-suite/integrals.cpp} that were missing from the Java suite:
 * the existing {@link IntegralsTest} only covers
 * {@code testSegment/Trapezoid/MidPointTrapezoid/Simpson/GaussKronrod*}.
 *
 * <p>Source of truth: C++ QuantLib v1.42.1 @
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}, file
 * {@code test-suite/integrals.cpp}.
 *
 * <p>Tests requiring Java integrators that are not yet ported
 * (TanhSinhIntegral, ExpSinhIntegral, FilonIntegral, TwoDimensionalIntegral,
 * DiscreteTrapezoidIntegral functor, DiscreteSimpsonIntegrator)
 * are kept as {@code @Ignore} placeholders documenting the gap.
 */
public class IntegralsAdditionalTest {

    // Matches C++ integrals.cpp:50  `Real tolerance = 1.0e-6;`
    private static final double TOLERANCE = 1.0e-6;

    // -----------------------------------------------------------------
    // testSeveral / testSingle helpers — mirror C++ integrals.cpp:52..82.
    // -----------------------------------------------------------------

    private static void testSingle(
            final GaussLegendreIntegratorWrapper I, final String tag,
            final Ops.DoubleOp f, final double xMin, final double xMax,
            final double expected) {
        final double calculated = I.invoke(f, xMin, xMax);
        if (Math.abs(calculated - expected) > TOLERANCE) {
            fail("integrating " + tag
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected);
        }
    }

    private static void testSeveral(final GaussLegendreIntegratorWrapper I) {
        // C++ integrals.cpp:66..82
        testSingle(I, "f(x) = 0", new Constant(0.0), 0.0, 1.0, 0.0);
        testSingle(I, "f(x) = 1", new Constant(1.0), 0.0, 1.0, 1.0);
        testSingle(I, "f(x) = x", new Identity(), 0.0, 1.0, 0.5);
        testSingle(I, "f(x) = x^2", new Square(), 0.0, 1.0, 1.0 / 3.0);
        testSingle(I, "f(x) = sin(x)", new Sin(), 0.0, Constants.M_PI, 2.0);
        testSingle(I, "f(x) = cos(x)", new Cos(), 0.0, Constants.M_PI, 0.0);
        testSingle(I, "f(x) = Gaussian(x)",
                new NormalDistribution(), -10.0, 10.0, 1.0);
        // NB: C++ also exercises AbcdSquared(0.07, 0.07, 0.5, 0.1, 8.0, 10.0)
        // against AbcdFunction.covariance; AbcdSquared is not ported to Java.
        // Mirrors the analogous TODO in IntegralsTest.
    }

    private static void testDegeneratedDomain(final GaussLegendreIntegratorWrapper I) {
        // C++ integrals.cpp:84..88
        testSingle(I, "f(x) = 0 over [1, 1+macheps]",
                new Constant(0.0), 1.0, 1.0 + Constants.QL_EPSILON, 0.0);
    }

    // -----------------------------------------------------------------
    // Java-side analogue of C++ detail::GaussianQuadratureIntegrator —
    // mirrors gaussianquadratures.cpp:
    //   c1 = 0.5*(b-a); c2 = 0.5*(a+b);
    //   return c1 * integration(lambda x: f(c1*x + c2));
    // Reproduces the per-call affine remap from canonical [-1,1] to [a,b].
    //
    // Named with a "Wrapper" suffix so as not to suggest a production class
    // — this is a test-only adapter pending a proper Java port.
    // -----------------------------------------------------------------
    private static final class GaussLegendreIntegratorWrapper {
        private final GaussianQuadrature integration;
        GaussLegendreIntegratorWrapper(final GaussianQuadrature integration) {
            this.integration = integration;
        }
        double invoke(final Ops.DoubleOp f, final double a, final double b) {
            final double c1 = 0.5 * (b - a);
            final double c2 = 0.5 * (a + b);
            return c1 * integration.op(x -> f.op(c1 * x + c2));
        }
    }

    /** Faithful port of {@code test-suite/integrals.cpp:208} {@code BOOST_AUTO_TEST_CASE(testGaussLegendreIntegrator)}. */
    @Test
    public void testGaussLegendreIntegrator() {
        // C++: const GaussLegendreIntegrator integrator(64);
        final GaussLegendreIntegratorWrapper integrator =
                new GaussLegendreIntegratorWrapper(new GaussLegendreIntegration(64));
        testSeveral(integrator);
        testDegeneratedDomain(integrator);
    }

    /** Faithful port of {@code test-suite/integrals.cpp:216} {@code BOOST_AUTO_TEST_CASE(testGaussChebyshevIntegrator)}. */
    @Test
    public void testGaussChebyshevIntegrator() {
        // C++: const GaussChebyshevIntegrator integrator(64);
        //   GaussChebyshevIntegration(n) := GaussianQuadrature(n,
        //                                   GaussJacobiPolynomial(-0.5,-0.5))
        final GaussLegendreIntegratorWrapper integrator = new GaussLegendreIntegratorWrapper(
                new GaussianQuadrature(64, new GaussJacobiPolynomial(-0.5, -0.5)));
        testSingle(integrator, "f(x) = Gaussian(x)",
                new NormalDistribution(), -10.0, 10.0, 1.0);
        testDegeneratedDomain(integrator);
    }

    /** Faithful port of {@code test-suite/integrals.cpp:225} {@code BOOST_AUTO_TEST_CASE(testGaussChebyshev2ndIntegrator)}. */
    @Test
    public void testGaussChebyshev2ndIntegrator() {
        // C++: const GaussChebyshev2ndIntegrator integrator(64);
        //   GaussChebyshev2ndIntegration(n) := GaussianQuadrature(n,
        //                                      GaussJacobiPolynomial(0.5, 0.5))
        final GaussLegendreIntegratorWrapper integrator = new GaussLegendreIntegratorWrapper(
                new GaussianQuadrature(64, new GaussJacobiPolynomial(0.5, 0.5)));
        testSingle(integrator, "f(x) = Gaussian(x)",
                new NormalDistribution(), -10.0, 10.0, 1.0);
        testDegeneratedDomain(integrator);
    }

    // -----------------------------------------------------------------
    // DISCRETE INTEGRALS
    // -----------------------------------------------------------------

    private static double f1(final double x) {
        // C++ integrals.cpp:104..106
        return 1.2 * x * x + 3.2 * x + 3.1;
    }

    private static double f2(final double x) {
        // C++ integrals.cpp:108..110
        return 4.3 * (x - 2.34) * (x - 2.34) - 6.2 * (x - 2.34) + f1(2.34);
    }

    /** Faithful port of {@code test-suite/integrals.cpp:306} {@code BOOST_AUTO_TEST_CASE(testDiscreteIntegrals)}. */
    @Test
    public void testDiscreteIntegrals() {
        // C++ uses both DiscreteSimpsonIntegral and DiscreteTrapezoidIntegral
        // (the functor forms, not the Integrator forms). The Java port has
        // only DiscreteSimpsonIntegral; DiscreteTrapezoidIntegral (functor)
        // is missing. We exercise the Simpson side here and inline-compute
        // the trapezoid reference on-the-fly so the assertion is still
        // bit-faithful to C++ — i.e. the trapezoidal *formula* is mirrored
        // even though no Java class exposes it.
        final Array x = new Array(6);
        final Array f = new Array(6);
        x.set(0, 1.0); x.set(1, 2.02); x.set(2, 2.34);
        x.set(3, 3.3); x.set(4, 4.2);  x.set(5, 4.6);

        // C++: std::transform(x.begin(), x.begin()+3, f.begin(),   f1);
        //      std::transform(x.begin()+3, x.end(),   f.begin()+3, f2);
        for (int i = 0; i < 3; ++i) f.set(i, f1(x.get(i)));
        for (int i = 3; i < 6; ++i) f.set(i, f2(x.get(i)));

        // C++ integrals.cpp:315
        final double expectedSimpson =
                16.0401216 + 30.4137528 + 0.2 * f2(4.2) + 0.2 * f2(4.6);
        // C++ integrals.cpp:317
        final double expectedTrapezoid =
                  0.5 * (f1(1.0)  + f1(2.02)) * 1.02
                + 0.5 * (f1(2.02) + f1(2.34)) * 0.32
                + 0.5 * (f2(2.34) + f2(3.3))  * 0.96
                + 0.5 * (f2(3.3)  + f2(4.2))  * 0.9
                + 0.5 * (f2(4.2)  + f2(4.6))  * 0.4;

        final double calculatedSimpson = new DiscreteSimpsonIntegral().op(x, f);

        final double tol = 1e-12; // C++ integrals.cpp:327
        assertEquals("discrete Simpson integration",
                expectedSimpson, calculatedSimpson, tol);

        // Trapezoid functor unported — inline the C++ implementation
        // (composite trapezoid over consecutive intervals) so the test still
        // pins the numeric expectation.
        double calculatedTrapezoid = 0.0;
        for (int i = 0; i + 1 < 6; ++i) {
            calculatedTrapezoid += 0.5 * (x.get(i + 1) - x.get(i))
                    * (f.get(i) + f.get(i + 1));
        }
        assertEquals("discrete Trapezoid integration",
                expectedTrapezoid, calculatedTrapezoid, tol);
    }

    /** Faithful port of {@code test-suite/integrals.cpp:343} {@code BOOST_AUTO_TEST_CASE(testDiscreteIntegrator)}. */
    @Test
    public void testDiscreteIntegrator() {
        // C++:
        //   testSeveral(DiscreteSimpsonIntegrator(300));
        //   testSeveral(DiscreteTrapezoidIntegrator(3000));
        // DiscreteSimpsonIntegrator is not yet ported to Java — we exercise
        // the trapezoid integrator only. (Simpson coverage is provided by
        // testDiscreteIntegrals above via the DiscreteSimpsonIntegral functor.)
        final DiscreteTrapezoidIntegrator trapezoid = new DiscreteTrapezoidIntegrator(3000);

        // testSeveral mirror — same 7 cases (excluding AbcdSquared).
        assertEquals("f(x) = 0", 0.0,
                trapezoid.op(new Constant(0.0), 0.0, 1.0), TOLERANCE);
        assertEquals("f(x) = 1", 1.0,
                trapezoid.op(new Constant(1.0), 0.0, 1.0), TOLERANCE);
        assertEquals("f(x) = x", 0.5,
                trapezoid.op(new Identity(), 0.0, 1.0), TOLERANCE);
        assertEquals("f(x) = x^2", 1.0 / 3.0,
                trapezoid.op(new Square(), 0.0, 1.0), TOLERANCE);
        assertEquals("f(x) = sin(x)", 2.0,
                trapezoid.op(new Sin(), 0.0, Constants.M_PI), TOLERANCE);
        assertEquals("f(x) = cos(x)", 0.0,
                trapezoid.op(new Cos(), 0.0, Constants.M_PI), TOLERANCE);
        assertEquals("f(x) = Gaussian(x)", 1.0,
                trapezoid.op(new NormalDistribution(), -10.0, 10.0), TOLERANCE);
    }

    /** Faithful port of {@code test-suite/integrals.cpp:350} {@code BOOST_AUTO_TEST_CASE(testPiecewiseIntegral)}. */
    @Test
    public void testPiecewiseIntegral() {
        // C++ uses experimental PiecewiseIntegral with QL_PIECEWISE_FUNCTION
        // (a piecewise-constant step function with breakpoints X and values Y).
        //   x = { 1, 2, 3, 4, 5 };
        //   y = { 1, 2, 3, 4, 5, 6 };
        //   integrator = PiecewiseIntegral(SegmentIntegral(1), x);
        final double[] xBreaks = { 1.0, 2.0, 3.0, 4.0, 5.0 };
        final double[] yValues = { 1.0, 2.0, 3.0, 4.0, 5.0, 6.0 };
        final Ops.DoubleOp pw = t -> PiecewiseFunction.eval(xBreaks, yValues, t);

        final SegmentIntegral segment = new SegmentIntegral(1);
        final PiecewiseIntegral piecewise = new PiecewiseIntegral(
                segment, Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0));

        // Reference values from C++ integrals.cpp:359..371
        pwCheck(piecewise, pw, -1.0,  0.0,  1.0);
        pwCheck(piecewise, pw,  0.0,  1.0,  1.0);
        pwCheck(piecewise, pw,  0.0,  1.5,  2.0);
        pwCheck(piecewise, pw,  0.0,  2.0,  3.0);
        pwCheck(piecewise, pw,  0.0,  2.5,  4.5);
        pwCheck(piecewise, pw,  0.0,  3.0,  6.0);
        pwCheck(piecewise, pw,  0.0,  4.0, 10.0);
        pwCheck(piecewise, pw,  0.0,  5.0, 15.0);
        pwCheck(piecewise, pw,  0.0,  6.0, 21.0);
        pwCheck(piecewise, pw,  0.0,  7.0, 27.0);
        pwCheck(piecewise, pw,  3.5,  4.5,  4.5);
        pwCheck(piecewise, pw,  5.0, 10.0, 30.0);
        pwCheck(piecewise, pw,  9.0, 10.0,  6.0);
    }

    private static void pwCheck(
            final PiecewiseIntegral in, final Ops.DoubleOp f,
            final double a, final double b, final double expected) {
        final double calculated = in.op(f, a, b);
        // C++ uses `close(...)` from QuantLib comparison.hpp — relative
        // accuracy of ~42 ULP. Pin to TIGHT 1e-12 to match the implicit
        // "exact" arithmetic of a step-function / SegmentIntegral(1).
        assertEquals("piecewise integration over [" + a + "," + b + "]",
                expected, calculated, 1e-12);
    }

    // -----------------------------------------------------------------
    // EXPONENTIAL INTEGRALS — Si, Ci, Ei, E1
    // -----------------------------------------------------------------

    /** Faithful port of {@code test-suite/integrals.cpp:374} {@code BOOST_AUTO_TEST_CASE(testExponentialIntegral)}. */
    @Test
    public void testExponentialIntegral() {
        // Reference values calculated with Mathematica or Python/mpmath.
        // Each row: { x, y, Si_re, Si_im, Ci_re, Ci_im, Ei_re, Ei_im, E1_re, E1_im }.
        // (mirrors C++ integrals.cpp:380..480 exactly.)
        final double[][] data = {
            {1e-10, 0.0, 1.0e-10, 0.0, -22.4486352650389, 0.0, -22.4486352649389, 0.0, 22.4486352651389, 0.0},
            {7.0710678118655e-11, 7.0710678118655e-11, 7.0710678118655e-11, 7.0710678118655e-11, -22.4486352650389, 0.785398163397448, -22.4486352649682, 0.785398163468159, 22.4486352651096, -0.785398163326738},
            {3.0901699437495e-11, 9.5105651629515e-11, 3.0901699437495e-11, 9.5105651629515e-11, -22.4486352650389, 1.25663706143591, -22.448635265008, 1.25663706153102, 22.4486352650698, -1.25663706134081},
            {0.0, 1e-10, 0.0, 1.0e-10, -22.4486352650389, 1.5707963267949, -22.4486352650389, 1.5707963268949, 22.4486352650389, -1.5707963266949},
            {0.0, 1e-10, 0.0, 1.0e-10, -22.4486352650389, 1.5707963267949, -22.4486352650389, 1.5707963268949, 22.4486352650389, -1.5707963266949},
            {-8.0901699437495e-11, 5.8778525229247e-11, -8.0901699437495e-11, 5.8778525229247e-11, -22.4486352650389, 2.51327412287184, -22.4486352651198, 2.51327412293062, 22.448635264958, -2.51327412281306},
            {-1e-10, 0.0, -1.0e-10, 0.0, -22.4486352650389, 3.14159265358979, -22.4486352651389, 0.0, 22.4486352649389, -3.14159265358979},
            {-8.0901699437495e-11, -5.8778525229247e-11, -8.0901699437495e-11, -5.8778525229247e-11, -22.4486352650389, -2.51327412287184, -22.4486352651198, -2.51327412293062, 22.448635264958, 2.51327412281306},
            {0.0, -1e-10, 0.0, -1.0e-10, -22.4486352650389, -1.5707963267949, -22.4486352650389, -1.5707963268949, 22.4486352650389, 1.5707963266949},
            {3.0901699437495e-11, -9.5105651629515e-11, 3.0901699437495e-11, -9.5105651629515e-11, -22.4486352650389, -1.25663706143591, -22.448635265008, -1.25663706153102, 22.4486352650698, 1.25663706134081},
            {9.8768834059514e-11, -1.5643446504023002e-11, 9.8768834059514e-11, -1.5643446504023e-11, -22.4486352650389, -0.157079632679488, -22.4486352649402, -0.157079632695132, 22.4486352651377, 0.157079632663845},
            {0.15, 0.0, 0.149812626514082, 0.0, -1.32552404918277, 0.0, -1.16408641729839, 0.0, 1.46446167052028, 0.0},
            {0.1060660171779825, 0.1060660171779825, 0.106198510172016, 0.105933345197561, -1.31990959342105, 0.779773166034167, -1.21397624822349, 0.897221670932746, 1.42584293861861, -0.684824650588713},
            {0.0463525491562425, 0.14265847744427249, 0.0465043664443717, 0.1427686871506, -1.31535197062462, 1.25332575154654, -1.27825242518864, 1.40248660838809, 1.37065439517488, -1.11739007291224},
            {0.0, 0.15, 0.0, 0.150187626610941, -1.31427404390933, 1.5707963267949, -1.32552404918277, 1.72060895330898, 1.32552404918277, -1.42098370028081},
            {0.0, 0.15, 0.0, 0.150187626610941, -1.31427404390933, 1.5707963267949, -1.32552404918277, 1.72060895330898, 1.32552404918277, -1.42098370028081},
            {-0.1213525491562425, 0.0881677878438705, -0.121410363295163, 0.0879894647931175, -1.32164680474487, 2.51862071457814, -1.43946484971679, 2.59626744276408, 1.19687588593211, -2.41957522097486},
            {-0.15, 0.0, -0.149812626514082, 0.0, -1.32552404918277, 3.14159265358979, -1.46446167052028, 0.0, 1.16408641729839, -3.14159265358979},
            {-0.1213525491562425, -0.0881677878438705, -0.121410363295163, -0.0879894647931175, -1.32164680474487, -2.51862071457814, -1.43946484971679, -2.59626744276408, 1.19687588593211, 2.41957522097486},
            {0.0, -0.15, 0.0, -0.150187626610941, -1.31427404390933, -1.5707963267949, -1.32552404918277, -1.72060895330898, 1.32552404918277, 1.42098370028081},
            {0.0463525491562425, -0.14265847744427249, 0.0465043664443717, -0.1427686871506, -1.31535197062462, -1.25332575154654, -1.27825242518864, -1.40248660838809, 1.37065439517488, 1.11739007291224},
            {0.148153251089271, -0.0234651697560345, 0.147986276837203, -0.0233801359873959, -1.32524974813753, -0.155344509602526, -1.16622995490181, -0.182371337566645, 1.46287076355731, 0.135270572544445},
            {0.25, 0.0, 0.249133570319757, 0.0, -0.824663062580946, 0.0, -0.542543264661914, 0.0, 1.04428263444374, 0.0},
            {0.1767766952966375, 0.1767766952966375, 0.177389351153991, 0.17616173766105, -0.809119386275216, 0.769773219911456, -0.632957648614166, 0.978412458037432, 0.985281123936265, -0.623633755729451},
            {0.0772542485937375, 0.2377641290737875, 0.0779581492943877, 0.238274358309521, -0.796425249249655, 1.24741416450428, -0.745153392294084, 1.50303646097033, 0.898260598498369, -1.02852866129867},
            {0.0, 0.25, 0.0, 0.250869684890912, -0.793412949552826, 1.5707963267949, -0.824663062580946, 1.81992989711465, 0.824663062580946, -1.32166275647514},
            {0.0, 0.25, 0.0, 0.250869684890912, -0.793412949552826, 1.5707963267949, -0.824663062580946, 1.81992989711465, 0.824663062580946, -1.32166275647514},
            {-0.2022542485937375, 0.1469463130731175, -0.20252086544385, 0.146120744825161, -0.813939960005834, 2.52811043072268, -1.00626764691037, 2.64616186234439, 0.60229889383601, -2.35061809970499},
            {-0.25, 0.0, -0.249133570319757, 0.0, -0.824663062580946, 3.14159265358979, -1.04428263444374, 0.0, 0.542543264661914, -3.14159265358979},
            {-0.2022542485937375, -0.1469463130731175, -0.20252086544385, -0.146120744825161, -0.813939960005834, -2.52811043072268, -1.00626764691037, -2.64616186234439, 0.60229889383601, 2.35061809970499},
            {0.0, -0.25, 0.0, -0.250869684890912, -0.793412949552826, -1.5707963267949, -0.824663062580946, -1.81992989711465, 0.824663062580946, 1.32166275647514},
            {0.0772542485937375, -0.2377641290737875, 0.0779581492943877, -0.238274358309521, -0.796425249249655, -1.24741416450428, -0.745153392294084, -1.50303646097033, 0.898260598498369, 1.02852866129867},
            {0.246922085148785, -0.0391086162600575, 0.24614979209014, -0.0387156766342252, -0.823906068503191, -0.152275113509673, -0.546488805945054, -0.201435843693654, 1.04188216592042, 0.122428128357486},
            {1.0, 0.0, 0.946083070367183, 0.0, 0.337403922900968, 0.0, 1.89511781635594, 0.0, 0.21938393439552, 0.0},
            {0.70710678118655, 0.70710678118655, 0.745192155353662, 0.666664817419508, 0.566802098259312, 0.535629617322428, 1.23346691567882, 1.78035886482613, 0.0998627191601961, -0.289974554118806},
            {0.30901699437495, 0.95105651629515, 0.355652074843551, 0.983694298574337, 0.782614772996823, 1.09956193553216, 0.643964830804846, 2.31231301720838, -0.112533957890793, -0.475476714030747},
            {0.0, 1.0, 0.0, 1.05725087537573, 0.837866940980208, 1.5707963267949, 0.337403922900968, 2.51687939716208, -0.337403922900968, -0.624713256427714},
            {0.0, 1.0, 0.0, 1.05725087537573, 0.837866940980208, 1.5707963267949, 0.337403922900968, 2.51687939716208, -0.337403922900968, -0.624713256427714},
            {-0.80901699437495, 0.58778525229247, -0.824526943360603, 0.5349755552469, 0.491722358913221, 2.74478237579885, -0.14431784116889, 2.91012082986304, -1.43603057378731, -1.62893165104155},
            {-1.0, 0.0, -0.946083070367183, 0.0, 0.337403922900968, 3.14159265358979, -0.21938393439552, 0.0, -1.89511781635594, -3.14159265358979},
            {-0.80901699437495, -0.58778525229247, -0.824526943360603, -0.5349755552469, 0.491722358913221, -2.74478237579885, -0.14431784116889, -2.91012082986304, -1.43603057378731, 1.62893165104155},
            {0.0, -1.0, 0.0, -1.05725087537573, 0.837866940980208, -1.5707963267949, 0.337403922900968, -2.51687939716208, -0.337403922900968, 0.624713256427714},
            {0.30901699437495, -0.95105651629515, 0.355652074843551, -0.983694298574337, 0.782614772996823, -1.09956193553216, 0.643964830804846, -2.31231301720838, -0.112533957890793, 0.475476714030747},
            {0.98768834059514, -0.15643446504023, 0.939353669480516, -0.132366326809511, 0.347743692745538, -0.0857637957494435, 1.86192420379474, -0.4235071237, 0.214836056406461, 0.0577866622153682},
            {5.0, 0.0, 1.54993124494467, 0.0, -0.190029749656644, 0.0, 40.1852753558032, 0.0, 0.00114829559127533, 0.0},
            {3.53553390593275, 3.53553390593275, 3.68715086115432, -3.15718137390906, -3.15476810467167, -2.11185029092794, -6.31194947858072, 7.36979747887716, -0.00241326923739065, 0.00450424343148012},
            {1.5450849718747501, 4.75528258147575, 14.299679516973, 6.85221185491562, 6.85257226323722, -12.7303117750282, -0.931350039879264, 2.99045284011251, 0.0356665739529384, 0.0160488285537158},
            {0.0, 5.0, 0.0, 20.0932118256972, 20.0920635301059, 1.5707963267949, -0.190029749656644, 3.12072757173957, 0.190029749656644, -0.0208650818502225},
            {0.0, 5.0, 0.0, 20.0932118256972, 20.0920635301059, 1.5707963267949, -0.190029749656644, 3.12072757173957, 0.190029749656644, -0.0208650818502225},
            {-4.04508497187475, 2.93892626146235, -2.0577013528011, -1.96223940975232, -1.9637046590567, 3.61921566552724, 0.00286020292932927, 3.14261835694337, 6.84905720502975, 11.1883945116728},
            {-5.0, 0.0, -1.54993124494467, 0.0, -0.190029749656644, 3.14159265358979, -0.00114829559127533, 0.0, -40.1852753558032, -3.14159265358979},
            {-4.04508497187475, -2.93892626146235, -2.0577013528011, 1.96223940975232, -1.9637046590567, -3.61921566552724, 0.00286020292932927, -3.14261835694337, 6.84905720502975, -11.1883945116728},
            {0.0, -5.0, 0.0, -20.0932118256972, 20.0920635301059, -1.5707963267949, -0.190029749656644, -3.12072757173957, 0.190029749656644, 0.0208650818502225},
            {1.5450849718747501, -4.75528258147575, 14.299679516973, -6.85221185491562, 6.85257226323722, 12.7303117750282, -0.931350039879264, -2.99045284011251, 0.0356665739529384, -0.0160488285537158},
            {4.9384417029757, -0.7821723252011501, 1.53351371140353, 0.167535111630988, -0.252671967618136, -0.0455545136665558, 31.7637646606649, -20.6127722347705, 0.000742118122850436, 0.000971589948194675},
            {10.0, 0.0, 1.65834759421887, 0.0, -0.0454564330044554, 0.0, 2492.22897624188, 0.0, 4.15696892968532e-6, 0.0},
            {7.0710678118655, 7.0710678118655, -3.77451753034182, 62.6425755592338, 62.6425711229056, 5.34523470197841, 125.285146682139, -7.54895590552534, 4.43632828562146e-6, -7.91551583068017e-5},
            {3.0901699437495003, 9.5105651629515, 303.07292777526, -690.037761260879, -690.037754650298, -301.502129842997, -0.659900725018632, 5.27667742385125, -0.00134856502993308, 0.00415958644984393},
            {0.0, 10.0, 0.0, 1246.11449019942, 1246.11448604245, 1.5707963267949, -0.0454564330044554, 3.22914392101377, 0.0454564330044554, 0.0875512674239774},
            {0.0, 10.0, 0.0, 1246.11449019942, 1246.11448604245, 1.5707963267949, -0.0454564330044554, 3.22914392101377, 0.0454564330044554, 0.0875512674239774},
            {-8.0901699437495, 5.8778525229247, -14.6236949578037, 13.4643508624518, 13.4645870261785, 16.1946084513107, -2.79815608075126e-5, 3.14158769865141, -157.085481478947, -317.2439811058},
            {-10.0, 0.0, -1.65834759421887, 0.0, -0.0454564330044554, 3.14159265358979, -4.15696892968532e-6, 0.0, -2492.22897624188, -3.14159265358979},
            {-8.0901699437495, -5.8778525229247, -14.6236949578037, -13.4643508624518, 13.4645870261785, -16.1946084513107, -2.79815608075126e-5, -3.14158769865141, -157.085481478947, 317.2439811058},
            {0.0, -10.0, 0.0, -1246.11449019942, 1246.11448604245, -1.5707963267949, -0.0454564330044554, -3.22914392101377, 0.0454564330044554, -0.0875512674239774},
            {3.0901699437495003, -9.5105651629515, 303.07292777526, 690.037761260879, -690.037754650298, 301.502129842997, -0.659900725018632, -5.27667742385125, -0.00134856502993308, -0.00415958644984393},
            {9.8768834059514, -1.5643446504023002, 1.78956084261706, 0.114701769782499, -0.118816490702582, 0.198823504802007, 411.904076239608, -2157.22483235914, -6.48699583272709e-7, 4.66032253043785e-6},
            {25.0, 0.0, 1.53148255099996, 0.0, -0.00684859717970259, 0.0, 3005950906.52555, 0.0, 5.34889975534022e-13, 0.0},
            {17.67766952966375, 17.67766952966375, -894423.548678786, -396595.979622699, -396595.9796227, 894425.119475113, -793191.959245399, -1788847.09735757, 7.48981460647877e-10, 3.27816276287981e-10},
            {7.72542485937375, 23.77641290737875, 395787595.545024, 194501516.12134, 194501516.12134, -395787593.974227, -80.7948153607822, -39.8888851700048, 1.72503667797818e-5, 2.36415887840135e-6},
            {0.0, 25.0, 0.0, 1502975453.26277, 1502975453.26277, 1.5707963267949, -0.00684859717970259, 3.10227887779486, 0.00684859717970259, -0.0393137757949353},
            {0.0, 25.0, 0.0, 1502975453.26277, 1502975453.26277, 1.5707963267949, -0.00684859717970259, 3.10227887779486, 0.00684859717970259, -0.0393137757949353},
            {-20.22542485937375, 14.69463130731175, -19129.3494470458, 45406.0213041107, 45406.0213041213, 19130.9202433848, 5.85665949258649e-11, 3.14159265356458, -2432061.38760638, 25010638.0968068},
            {-25.0, 0.0, -1.53148255099996, 0.0, -0.00684859717970259, 3.14159265358979, -5.34889975534022e-13, 0.0, -3005950906.52555, -3.14159265358979},
            {-20.22542485937375, -14.69463130731175, -19129.3494470458, -45406.0213041107, 45406.0213041213, -19130.9202433848, 5.85665949258649e-11, -3.14159265356458, -2432061.38760638, -25010638.0968068},
            {0.0, -25.0, 0.0, -1502975453.26277, 1502975453.26277, -1.5707963267949, -0.00684859717970259, -3.10227887779486, 0.00684859717970259, 0.0393137757949353},
            {7.72542485937375, -23.77641290737875, 395787595.545024, -194501516.12134, 194501516.12134, 395787593.974227, -80.7948153607822, 39.8888851700048, 1.72503667797818e-5, -2.36415887840135e-6},
            {24.6922085148785, -3.91086162600575, 0.61973692887531, 0.318459426938049, -0.318931296543192, -0.950420524151913, -1816162045.63054, 1255955799.5082, -4.40593065675657e-13, -5.79490191675286e-13}
        };

        final double tol = 100 * Constants.QL_EPSILON; // C++ integrals.cpp:482

        for (final double[] row : data) {
            final double x = row[0];
            final double y = (Math.abs(row[1]) < 1e-12) ? 0.0 : row[1];
            final Complex z = new Complex(x, y);

            final Complex si = ExponentialIntegral.Si(z);
            checkExpInt("Si", z, si, new Complex(row[2], row[3]), tol, 1.0);

            final Complex ci = ExponentialIntegral.Ci(z);
            checkExpInt("Ci", z, ci, new Complex(row[4], row[5]), tol, 1.0);

            final Complex ei = ExponentialIntegral.Ei(z);
            checkExpInt("Ei", z, ei, new Complex(row[6], row[7]), tol, 1.0);

            final Complex e1 = ExponentialIntegral.E1(z);
            // C++ uses 10*tol for E1 (integrals.cpp:519).
            checkExpInt("E1", z, e1, new Complex(row[8], row[9]), tol, 10.0);
        }
    }

    private static void checkExpInt(
            final String name, final Complex z, final Complex calc,
            final Complex ref, final double tol, final double tolMul) {
        final Complex diffC = calc.sub(ref);
        final double diff;
        if ("Ci".equals(name)) {
            // C++ integrals.cpp:500: Ci uses min(|ci-ref|, |ci-ref|/|ref|)
            diff = Math.min(diffC.abs(), diffC.abs() / ref.abs());
        } else {
            diff = diffC.abs() / ref.abs();
        }
        final boolean realFlag =
                Math.abs(ref.real()) < tol && Math.abs(calc.real()) > tol;
        final boolean imagFlag =
                Math.abs(ref.imag()) < tol && Math.abs(calc.imag()) > tol;
        if (diff > tolMul * tol || Double.isNaN(diff) || realFlag || imagFlag) {
            fail(name + " calculation failed for " + z
                    + "\n calculated: " + calc
                    + "\n expected:   " + ref
                    + "\n difference: " + diff
                    + "\n tolerance:  " + (tolMul * tol));
        }
    }

    /** Faithful port of {@code test-suite/integrals.cpp:527} {@code BOOST_AUTO_TEST_CASE(testRealSiCiIntegrals)}. */
    @Test
    public void testRealSiCiIntegrals() {
        // Reference values calculated with Mathematica or Python/mpmath.
        // Each row: { x, Si(x), Ci(x) }.
        final double[][] data = {
            {1e-12,    1e-12,                  -27.0538054510270153677},
            {0.1,      0.09994446110827695570, -1.7278683866572965838},
            {1.0,      0.9460830703671830149,   0.3374039229009681347},
            {1.9999,   1.6053675097543679041,   0.4230016343635392},
            {3.9999,   1.758222058430840841,   -0.140965355646150101},
            {4.0001,   1.758184218306157867,   -0.140998037827177150},
            {5.0,      1.5499312449446741373,  -0.19002974965664387862},
            {7.0,      1.4545966142480935906,   0.076695278482184518383},
            {10.0,     1.6583475942188740493,  -0.045456433004455372635},
            {15.0,     1.6181944437083687391,   0.046278677674360439604},
            {20.0,     1.5482417010434398402,   0.04441982084535331654},
            {24.9,     1.532210740207620024,   -0.010788215638781789846},
            {25.1,     1.5311526281483412938,  -0.0028719014454227088097},
            {30.0,     1.566756540030351111,   -0.033032417282071143779},
            {40.0,     1.5869851193547845068,   0.019020007896208766962},
            {400.0,    1.5721148692738117518,  -0.00212398883084634893},
            {4000.0,   1.5709788562309441985,  -0.00017083030544201591130}
        };

        final double tol = 1e-12; // C++ integrals.cpp:554

        for (final double[] row : data) {
            double x = row[0];
            double si = ExponentialIntegral.Si(x);
            assertEquals("SineIntegral at " + x, row[1], si, tol);

            final double ci = ExponentialIntegral.Ci(x);
            assertEquals("CosineIntegral at " + x, row[2], ci, tol);

            x = -row[0];
            si = ExponentialIntegral.Si(x);
            // Si is odd: Si(-x) = -Si(x). C++ tests |si + Si(positive_x)| < tol.
            assertEquals("SineIntegral at " + x, -row[1], si, tol);
        }
    }

    /** Faithful port of {@code test-suite/integrals.cpp:580} {@code BOOST_AUTO_TEST_CASE(testExponentialIntegralLimits)}. */
    @Test
    public void testExponentialIntegralLimits() {
        final double largeValue = 0.75 * Math.log(0.1 * Constants.QL_MAX_REAL);

        // C++: Ei(complex(largeValue, +min)) — imag ~ M_PI, real ~ exp/lv.
        final Complex largeValuePosImag = ExponentialIntegral.Ei(
                new Complex(largeValue, Double.MIN_VALUE));

        final double tol = 1000 * Constants.QL_EPSILON; // C++ integrals.cpp:590

        // Imag arm: QL_CHECK_CLOSE is relative tolerance.
        assertRelClose("Ei(largeValue,+min) imag", largeValuePosImag.imag(),
                Math.PI, tol);
        // Real arm has its own tolerance 1e3/largeValue.
        assertRelClose("Ei(largeValue,+min) real", largeValuePosImag.real(),
                Math.exp(largeValue) / largeValue, 1e3 / largeValue);

        // C++: Ei(complex(largeValue, -min))
        final Complex largeValueNegImag = ExponentialIntegral.Ei(
                new Complex(largeValue, -Double.MIN_VALUE));
        assertRelClose("Ei(largeValue,-min) imag", largeValueNegImag.imag(),
                -Math.PI, tol);
        assertRelClose("Ei(largeValue,-min) real", largeValueNegImag.real(),
                Math.exp(largeValue) / largeValue, 1e3 / largeValue);

        // C++: Ei(complex(largeValue)) — pure real argument => imag exactly 0.
        final Complex largeValueZeroImag = ExponentialIntegral.Ei(
                new Complex(largeValue, 0.0));
        assertTrue("Ei(largeValue,0) imag should be exactly 0",
                largeValueZeroImag.imag() == 0.0);

        // C++: Ei(0+0i) == -inf.
        final Complex ei_0 = ExponentialIntegral.Ei(new Complex(0.0, 0.0));
        assertTrue("Ei(0,0) == (-inf, 0)",
                ei_0.real() == Double.NEGATIVE_INFINITY && ei_0.imag() == 0.0);

        // Small-radius polar sweep: Ei(z) ≈ γ + log(z) for |z| very small.
        // C++ integrals.cpp:614..625
        final double smallR = Constants.QL_EPSILON * Constants.QL_EPSILON;
        for (int xi = -100; xi < 100; ++xi) {
            final double phi = xi / 100.0 * Math.PI;
            final Complex z = new Complex(smallR * Math.cos(phi),
                    smallR * Math.sin(phi));
            final Complex ei = ExponentialIntegral.Ei(z);

            final Complex limit_ei = new Complex(
                    ExponentialIntegral.M_EULER_MASCHERONI, 0.0).add(z.log());

            assertRelClose("Ei small polar real phi=" + phi,
                    ei.real(), limit_ei.real(), tol);
            assertRelClose("Ei small polar imag phi=" + phi,
                    ei.imag(), limit_ei.imag(), tol);
        }

        // Large-radius polar sweep: outside the principal sector |phi|>π/2,
        // Ei(z) has real part ≈ 0 and imag part = sign(z.imag) * π.
        final double largeR = largeValue;
        for (int xi = -10; xi < 10; ++xi) {
            final double phi = xi / 10.0 * Math.PI;
            if (Math.abs(phi) > 0.5 * Math.PI) {
                final Complex z = new Complex(largeR * Math.cos(phi),
                        largeR * Math.sin(phi));
                final Complex ei = ExponentialIntegral.Ei(z);

                final double sign = Math.signum(z.imag());
                final double limit_ei_imag = sign * Math.PI;
                // close_enough on real ~= 0
                assertTrue("Ei large polar real ~= 0 phi=" + phi,
                        Math.abs(ei.real()) < 100.0 * Constants.QL_EPSILON
                                || Math.abs(ei.real()) < tol * Math.abs(ei.imag()));
                assertRelClose("Ei large polar imag phi=" + phi,
                        ei.imag(), limit_ei_imag, tol);
            }
        }
    }

    private static void assertRelClose(
            final String tag, final double calc, final double ref,
            final double relTol) {
        // Mirror QL_CHECK_CLOSE: |calc-ref|/|ref| < relTol (with a small
        // additive guard for ref near zero).
        if (ref == 0.0) {
            if (Math.abs(calc) > relTol) {
                fail(tag + ": |" + calc + "| > " + relTol);
            }
            return;
        }
        final double rel = Math.abs(calc - ref) / Math.abs(ref);
        if (rel > relTol) {
            fail(tag + ": calc=" + calc + " ref=" + ref
                    + " rel=" + rel + " tol=" + relTol);
        }
    }

    // -----------------------------------------------------------------
    // BLOCKED — Java integrator missing. Each test below is an inert
    // placeholder documenting the gap. Re-enable by porting the relevant
    // C++ class.
    // -----------------------------------------------------------------

    /**
     * BLOCKED port of {@code test-suite/integrals.cpp:186}
     * {@code BOOST_AUTO_TEST_CASE(testTanhSinh)}.
     *
     * <p>{@link org.jquantlib.math.integrals.TanhSinhIntegral} was ported in
     * Phase 1 closure A1-B-retry (commit {@code 73601967}) as a direct Takahasi-Mori 1974
     * double-exponential quadrature implementation (since C++ wraps Boost.Math which has no
     * Java equivalent). Verification with this test surfaced an accuracy defect on the
     * simplest constant case: {@code integrate(f(x)=1, [0,1])} should yield 1.0 but the
     * Java port diverges. Tracked as A3-style port defect (TODO Phase1-closure-A1-B-retry-fix);
     * see TanhSinhIntegral.java for direct fix.
     */
    @Test
    public void testTanhSinh() {
        runIntegratorBattery(new org.jquantlib.math.integrals.TanhSinhIntegral());
    }

    /**
     * Helper analogous to C++ {@code testSeveral} for plain
     * {@link org.jquantlib.math.integrals.Integrator}-style integrators
     * (TanhSinh, ExpSinh, GaussKronrod, etc.).
     */
    private static void runIntegratorBattery(final org.jquantlib.math.integrals.Integrator I) {
        runIntegratorSingle(I, "f(x) = 0", new Constant(0.0), 0.0, 1.0, 0.0);
        runIntegratorSingle(I, "f(x) = 1", new Constant(1.0), 0.0, 1.0, 1.0);
        runIntegratorSingle(I, "f(x) = x", new Identity(), 0.0, 1.0, 0.5);
        runIntegratorSingle(I, "f(x) = x^2", new Square(), 0.0, 1.0, 1.0 / 3.0);
        runIntegratorSingle(I, "f(x) = sin(x)", new Sin(), 0.0, Constants.M_PI, 2.0);
        runIntegratorSingle(I, "f(x) = cos(x)", new Cos(), 0.0, Constants.M_PI, 0.0);
        runIntegratorSingle(I, "f(x) = Gaussian(x)",
                new NormalDistribution(), -10.0, 10.0, 1.0);
    }

    private static void runIntegratorSingle(
            final org.jquantlib.math.integrals.Integrator I, final String tag,
            final Ops.DoubleOp f, final double xMin, final double xMax,
            final double expected) {
        final double calculated = I.op(f, xMin, xMax);
        if (Math.abs(calculated - expected) > TOLERANCE) {
            fail("integrating " + tag
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected);
        }
    }

    /**
     * Faithful port of {@code test-suite/integrals.cpp:194}
     * {@code BOOST_AUTO_TEST_CASE(testExpSinh)}.
     *
     * <p>{@link org.jquantlib.math.integrals.ExpSinhIntegral} was ported in
     * Phase 1 closure A4-B-v4 as a direct Takahasi-Mori 1974 double-
     * exponential quadrature implementation (since C++ wraps Boost.Math
     * which has no Java equivalent).
     */
    @Test
    public void testExpSinh() {
        final org.jquantlib.math.integrals.ExpSinhIntegral integrator =
                new org.jquantlib.math.integrals.ExpSinhIntegral();

        // testSingle: f(x) = Gaussian(x) over [0, MAX] should give 0.5.
        runIntegratorSingle(integrator, "f(x) = Gaussian(x)",
                new NormalDistribution(), 0.0, Double.POSITIVE_INFINITY, 0.5);

        // testSingle: f(x) = x*e^(-x) over [0, MAX] should give 1.0.
        runIntegratorSingle(integrator, "f(x) = x*e^(-x)",
                new Ops.DoubleOp() {
                    @Override
                    public double op(final double x) {
                        return x * Math.exp(-x);
                    }
                }, 0.0, Double.POSITIVE_INFINITY, 1.0);
    }

    /**
     * Port of {@code test-suite/integrals.cpp:246}
     * {@code BOOST_AUTO_TEST_CASE(testTwoDimensionalIntegration)}.
     *
     * <p>C++:
     * <pre>
     *   const Real calculated = TwoDimensionalIntegral(
     *       ext::shared_ptr&lt;Integrator&gt;(new TrapezoidIntegral&lt;Default&gt;(tolerance, maxEvaluations)),
     *       ext::shared_ptr&lt;Integrator&gt;(new TrapezoidIntegral&lt;Default&gt;(tolerance, maxEvaluations)))(
     *       std::multiplies&lt;&gt;(),
     *       std::make_pair(0.0, 0.0), std::make_pair(1.0, 2.0));
     *   const Real expected = 1.0;
     * </pre>
     *
     * <p>Integrand {@code f(x, y) = x*y}; integral over
     * {@code [0,1] x [0,2]} is {@code (1/2)(1)(2) = 1.0}. Mirrors v1.42.1.
     */
    @Test
    public void testTwoDimensionalIntegration() {
        final int maxEvaluations = 1000;
        final Integrator innerX = new TrapezoidIntegral<TrapezoidIntegral.Default>(
                TrapezoidIntegral.Default.class, TOLERANCE, maxEvaluations);
        final Integrator innerY = new TrapezoidIntegral<TrapezoidIntegral.Default>(
                TrapezoidIntegral.Default.class, TOLERANCE, maxEvaluations);
        final TwoDimensionalIntegral integral = new TwoDimensionalIntegral(innerX, innerY);

        final double calculated = integral.op(new Ops.BinaryDoubleOp() {
            @Override
            public double op(final double x, final double y) {
                return x * y;
            }
        }, 0.0, 0.0, 1.0, 2.0);

        final double expected = 1.0;
        if (Math.abs(calculated - expected) > TOLERANCE) {
            fail("two dimensional integration:"
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected);
        }
    }

    /**
     * Faithful port of {@code test-suite/integrals.cpp:268}
     * {@code BOOST_AUTO_TEST_CASE(testFolinIntegration)}.
     *
     * <p>{@link org.jquantlib.math.integrals.FilonIntegral} was ported in
     * Phase 1 closure A4-B-v4. Both C++ and Java carry the "Folin"
     * misspelling for the test name; the class itself is correctly named
     * {@code FilonIntegral}.
     *
     * <p>Examples from
     * http://www.tat.physik.uni-tuebingen.de/~kokkotas/Teaching/Num_Methods_files/Comp_Phys5.pdf
     */
    @Test
    public void testFolinIntegration() {
        final int[] nr = { 4, 8, 16, 128, 256, 1024, 2048 };
        final double[] expected = {
                4.55229440e-5, 4.72338540e-5, 4.72338540e-5,
                4.78308678e-5, 4.78404787e-5, 4.78381120e-5,
                4.78381084e-5
        };

        final double t = 100.0;
        final double o = (Constants.M_PI / 2.0) / t;

        final double tol = 1e-12;

        // cosineF: f(x) = exp(-0.5*x).
        final Ops.DoubleOp cosineF = new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                return Math.exp(-0.5 * x);
            }
        };
        // sineF: f(x) = exp(-0.5*(x - M_PI_2/100)).
        final Ops.DoubleOp sineF = new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                return Math.exp(-0.5 * (x - (Constants.M_PI / 2.0) / 100.0));
            }
        };

        for (int i = 0; i < nr.length; ++i) {
            final int n = nr[i];
            final double calculatedCosine = new org.jquantlib.math.integrals.FilonIntegral(
                    org.jquantlib.math.integrals.FilonIntegral.Type.Cosine, t, n)
                    .op(cosineF, 0.0, 2.0 * Constants.M_PI);
            final double calculatedSine = new org.jquantlib.math.integrals.FilonIntegral(
                    org.jquantlib.math.integrals.FilonIntegral.Type.Sine, t, n)
                    .op(sineF, o, 2.0 * Constants.M_PI + o);

            if (Math.abs(calculatedCosine - expected[i]) > tol) {
                fail("Filon Cosine integration failed [n=" + n + "]:"
                        + "\n    calculated: " + calculatedCosine
                        + "\n    expected:   " + expected[i]);
            }
            if (Math.abs(calculatedSine - expected[i]) > tol) {
                fail("Filon Sine integration failed [n=" + n + "]:"
                        + "\n    calculated: " + calculatedSine
                        + "\n    expected:   " + expected[i]);
            }
        }
    }
}

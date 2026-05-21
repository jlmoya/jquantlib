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

package org.jquantlib.testsuite.math.integrals;

import static org.junit.Assert.fail;

import java.util.function.Function;

import org.jquantlib.QL;
import org.jquantlib.experimental.math.GaussNonCentralChiSquaredPolynomial;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NonCentralChiSquaredDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.math.integrals.GaussHyperbolicPolynomial;
import org.jquantlib.math.integrals.GaussJacobiPolynomial;
import org.jquantlib.math.integrals.GaussLaguerreCosinePolynomial;
import org.jquantlib.math.integrals.GaussLaguerrePolynomial;
import org.jquantlib.math.integrals.GaussLaguerreSinePolynomial;
import org.jquantlib.math.integrals.GaussLaguerreIntegration;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.math.integrals.GaussianQuadrature;
import org.jquantlib.math.integrals.MomentBasedGaussianPolynomial;
import org.jquantlib.math.integrals.MultiDimGaussianIntegration;
import org.jquantlib.math.integrals.TabulatedGaussLegendre;
import org.junit.Test;

/**
 * Java port of the C++ tests in test-suite/gaussianquadratures.cpp that have
 * no existing Java equivalent (Phase 5b).
 *
 * <p>The C++ file has 10 test cases. Java already covers the foundational
 * Hermite/Laguerre/Tabulated path:
 * <ul>
 *   <li>{@code testHermite} -> {@code GaussHermiteIntegrationTest} (extended
 *       cross-validation). A faithful name-aliased port now also lives
 *       in this class — Round A8-E-quad.</li>
 *   <li>{@code testLaguerre} (basic) -> {@code GaussLaguerreIntegrationTest}.
 *       Same: name-aliased port added in Round A8-E-quad.</li>
 *   <li>{@code testTabulated} -> {@code TabulatedGaussLegendreTest::testPolynomials}.
 *       Same: name-aliased port added in Round A8-E-quad.</li>
 * </ul>
 *
 * <p>Phase 5e.5b-CFC-d-90: un-ignored {@code testJacobi},
 * {@code testHyperbolic} and {@code testMomentBasedGaussianPolynomial}
 * — they only need the already-ported
 * {@code Gauss{Jacobi,Hyperbolic,Laguerre}Polynomial} and
 * {@code MomentBasedGaussianPolynomial}, no new production classes required.
 *
 * <p>Phase 5e.5b-CFC-d-168: un-ignored
 * {@code testGaussLaguerreCosinePolynomial},
 * {@code testNonCentralChiSquared},
 * {@code testNonCentralChiSquaredSumOfNodes} and
 * {@code testMultiDimensionalGaussIntegration} after porting
 * {@link GaussLaguerreCosinePolynomial},
 * {@link GaussLaguerreSinePolynomial} and
 * {@link MultiDimGaussianIntegration}, and wiring the existing
 * {@link GaussNonCentralChiSquaredPolynomial} into the Gaussian quadrature
 * test harness.
 */
public class GaussianQuadraturesAdditionalTest {

    public GaussianQuadraturesAdditionalTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1.0e-4; // C++ test-suite default

    // QL_EPSILON parity with C++ <ql/types.hpp>; used by the multi-dimensional
    // quadrature test's 1e4*QL_EPSILON tolerance.
    private static final double QL_EPSILON = 2.2204460492503131e-16;

    // ---- C++ test-helper analogues ------------------------------------------------

    private static void testSingle(final GaussianQuadrature I, final String tag,
                                   final Ops.DoubleOp f, final double expected) {
        final double calculated = I.op(f);
        if (Math.abs(calculated - expected) > TOL) {
            fail("integrating " + tag
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected);
        }
    }

    // Overload for GaussLaguerreIntegration (does NOT extend GaussianQuadrature
    // in the Java port — see Phase 2j.5 Track C.1 note).
    private static void testSingle(final GaussLaguerreIntegration I, final String tag,
                                   final Ops.DoubleOp f, final double expected) {
        final double calculated = I.op(f);
        if (Math.abs(calculated - expected) > TOL) {
            fail("integrating " + tag
                    + "\n    calculated: " + calculated
                    + "\n    expected:   " + expected);
        }
    }

    private static void testSingleLaguerre(final GaussLaguerreIntegration I) {
        // Mirror of testSingleLaguerre() in test-suite/gaussianquadratures.cpp:119.
        testSingle(I, "f(x) = exp(-x)",   x -> Math.exp(-x),     1.0);
        testSingle(I, "f(x) = x*exp(-x)", x -> x * Math.exp(-x), 1.0);
        final NormalDistribution n = new NormalDistribution();
        testSingle(I, "f(x) = Gaussian(x)", n::op, 0.5);
    }

    private static void testSingleJacobi(final GaussianQuadrature I) {
        testSingle(I, "f(x) = 1",   x -> 1.0, 2.0);
        testSingle(I, "f(x) = x",   x -> x,   0.0);
        testSingle(I, "f(x) = x^2", x -> x * x, 2.0 / 3.0);
        testSingle(I, "f(x) = sin(x)", Math::sin, 0.0);
        testSingle(I, "f(x) = cos(x)", Math::cos, Math.sin(1.0) - Math.sin(-1.0));

        final NormalDistribution n = new NormalDistribution();
        final CumulativeNormalDistribution cn = new CumulativeNormalDistribution();
        testSingle(I, "f(x) = Gaussian(x)", n::op, cn.op(1.0) - cn.op(-1.0));
    }

    // ---- ports of remaining C++ BOOST_AUTO_TEST_CASE methods ----------------------

    @Test
    public void testLaguerre() {
        QL.info("Testing Gauss-Laguerre integration...");

        // C++ test-suite/gaussianquadratures.cpp:169 — verbatim port.
        testSingleLaguerre(new GaussLaguerreIntegration(16));
        testSingleLaguerre(new GaussLaguerreIntegration(150, 0.01));

        testSingle(new GaussLaguerreIntegration(16, 1.0), "f(x) = x*exp(-x)",
                   x -> x * Math.exp(-x), 1.0);
        testSingle(new GaussLaguerreIntegration(32, 0.9), "f(x) = x*exp(-x)",
                   x -> x * Math.exp(-x), 1.0);
    }

    @Test
    public void testHermite() {
        QL.info("Testing Gauss-Hermite integration...");

        // C++ test-suite/gaussianquadratures.cpp:181 — verbatim port.
        final NormalDistribution n = new NormalDistribution();
        testSingle(new GaussHermiteIntegration(16), "f(x) = Gaussian(x)",
                   n::op, 1.0);
        testSingle(new GaussHermiteIntegration(16, 0.5), "f(x) = x*Gaussian(x)",
                   x -> x * n.op(x), 0.0);
        testSingle(new GaussHermiteIntegration(64, 0.9), "f(x) = x*x*Gaussian(x)",
                   x -> x * x * n.op(x), 1.0);
    }

    @Test
    public void testTabulated() {
        QL.info("Testing tabulated Gauss-Legendre integration...");

        // C++ test-suite/gaussianquadratures.cpp:201 — verbatim port.
        testSingleTabulated(x -> x,             "f(x) = x",   0.0,       1.0e-13);
        testSingleTabulated(x -> x * x,         "f(x) = x^2", 2.0 / 3.0, 1.0e-13);
        testSingleTabulated(x -> x * x * x,     "f(x) = x^3", 0.0,       1.0e-13);
        testSingleTabulated(x -> x * x * x * x, "f(x) = x^4", 2.0 / 5.0, 1.0e-13);
    }

    private static void testSingleTabulated(final Ops.DoubleOp f, final String tag,
                                            final double expected, final double tolerance) {
        final int[] order = { 6, 7, 12, 20 };
        final TabulatedGaussLegendre quad = new TabulatedGaussLegendre();
        for (final int i : order) {
            quad.setOrder(i);
            final double realised = quad.evaluate(f);
            if (Math.abs(realised - expected) > tolerance) {
                fail(" integrating " + tag
                        + "\n    order " + i
                        + "\n    realised: " + realised
                        + "\n    expected: " + expected);
            }
        }
    }

    @Test
    public void testJacobi() {
        QL.info("Testing Gauss-Jacobi integration...");

        // C++ test-suite/gaussianquadratures.cpp:160. The Java port wraps
        // GaussianQuadrature around the existing polynomial classes:
        //
        //   GaussLegendreIntegration(16)
        //     = GaussianQuadrature(16, GaussJacobiPolynomial(0.0, 0.0))
        //   GaussChebyshevIntegration(130)
        //     = GaussianQuadrature(130, GaussJacobiPolynomial(-0.5, -0.5))
        //   GaussChebyshev2ndIntegration(130)
        //     = GaussianQuadrature(130, GaussJacobiPolynomial(0.5, 0.5))
        //   GaussGegenbauerIntegration(50, lambda=0.55)
        //     = GaussianQuadrature(50, GaussJacobiPolynomial(0.05, 0.05))
        //     (Gegenbauer relation: GaussJacobiPolynomial(lambda-0.5, lambda-0.5))

        testSingleJacobi(new GaussLegendreIntegration(16));
        testSingleJacobi(new GaussianQuadrature(130, new GaussJacobiPolynomial(-0.5, -0.5)));
        testSingleJacobi(new GaussianQuadrature(130, new GaussJacobiPolynomial(0.5, 0.5)));
        testSingleJacobi(new GaussianQuadrature(50, new GaussJacobiPolynomial(0.05, 0.05)));
    }

    @Test
    public void testHyperbolic() {
        QL.info("Testing Gauss hyperbolic integration...");

        // C++ test-suite/gaussianquadratures.cpp:192. The Java port wraps
        //   GaussHyperbolicIntegration(16)
        //     = GaussianQuadrature(16, GaussHyperbolicPolynomial())
        // — weight w(x) = 1/cosh(x); ∫ 1/cosh(x) dx = pi, ∫ x/cosh(x) dx = 0.
        final GaussianQuadrature q = new GaussianQuadrature(16, new GaussHyperbolicPolynomial());
        testSingle(q, "f(x) = 1/cosh(x)", x -> 1.0 / Math.cosh(x), Math.PI);
        testSingle(q, "f(x) = x/cosh(x)", x -> x / Math.cosh(x), 0.0);
    }

    @Test
    public void testMomentBasedGaussianPolynomial() {
        QL.info("Testing moment-based Gaussian polynomials...");

        // C++ test-suite/gaussianquadratures.cpp:214 — verify that
        // MomentBasedGaussLaguerrePolynomial reproduces the Laguerre
        // alpha/beta coefficients. Java port uses double (not boost mp_float
        // multiprecision); we therefore use a looser tolerance (1e-8) than
        // the C++ 1e-12 to absorb the loss of precision in the moment-based
        // Chebyshev recursion. Cross-validation tier: LOOSE per CLAUDE.md
        // §7 — justified inline because the Java port intentionally uses
        // double precision while the C++ template parameter is mp_float.
        final GaussLaguerrePolynomial g = new GaussLaguerrePolynomial();
        final MomentBasedGaussianPolynomial k = new MomentBasedGaussLaguerrePolynomial();
        final double tol = 1e-8;
        for (int i = 0; i < 10; ++i) {
            final double diffAlpha = Math.abs(k.alpha(i) - g.alpha(i));
            final double diffBeta = Math.abs(k.beta(i) - g.beta(i));
            if (diffAlpha > tol) {
                fail("failed to reproduce alpha for Laguerre quadrature"
                        + "\n    calculated: " + k.alpha(i)
                        + "\n    expected  : " + g.alpha(i)
                        + "\n    diff      : " + diffAlpha);
            }
            if (i > 0 && diffBeta > tol) {
                fail("failed to reproduce beta for Laguerre quadrature"
                        + "\n    calculated: " + k.beta(i)
                        + "\n    expected  : " + g.beta(i)
                        + "\n    diff      : " + diffBeta);
            }
        }
    }

    @Test
    public void testGaussLaguerreCosinePolynomial() {
        QL.info("Testing Gauss-Laguerre-Cosine quadrature...");

        // C++ test-suite/gaussianquadratures.cpp:251.
        final GaussianQuadrature quadCosine = new GaussianQuadrature(
                16, new GaussLaguerreCosinePolynomial(0.2));
        testSingle(quadCosine, "f(x) = exp(-x)",   x -> Math.exp(-x),     1.0);
        testSingle(quadCosine, "f(x) = x*exp(-x)", x -> x * Math.exp(-x), 1.0);

        final GaussianQuadrature quadSine = new GaussianQuadrature(
                16, new GaussLaguerreSinePolynomial(0.2));
        testSingle(quadSine,   "f(x) = exp(-x)",   x -> Math.exp(-x),     1.0);
        testSingle(quadSine,   "f(x) = x*exp(-x)", x -> x * Math.exp(-x), 1.0);
    }

    @Test
    public void testNonCentralChiSquared() {
        QL.info("Testing Gauss non-central chi-squared integration...");

        // C++ test-suite/gaussianquadratures.cpp:271.
        // f(x) = x^2 * pdf(nonCentralChiSquared(4, 1))(x).
        final NonCentralChiSquaredDistribution nccs41 =
                new NonCentralChiSquaredDistribution(4.0, 1.0);
        testSingle(
                new GaussianQuadrature(2, new GaussNonCentralChiSquaredPolynomial(4.0, 1.0)),
                "f(x) = x^2 * nonCentralChiSquared(4, 1)(x)",
                x -> x * x * nccs41.pdf(x),
                37.0);

        // f(x) = x * sin(0.1*x) * exp(0.3*x) * pdf(nonCentralChiSquared(1, 1))(x).
        final NonCentralChiSquaredDistribution nccs11 =
                new NonCentralChiSquaredDistribution(1.0, 1.0);
        testSingle(
                new GaussianQuadrature(14, new GaussNonCentralChiSquaredPolynomial(1.0, 1.0)),
                "f(x) = x * sin(0.1*x) * exp(0.3*x) * nonCentralChiSquared(1, 1)(x)",
                x -> x * Math.sin(0.1 * x) * Math.exp(0.3 * x) * nccs11.pdf(x),
                17.408092);
    }

    @Test
    public void testNonCentralChiSquaredSumOfNodes() {
        QL.info("Testing Gauss non-central chi-squared sum of nodes...");

        // C++ test-suite/gaussianquadratures.cpp:286.
        //
        // Walter Gautschi, "How and How not to check Gaussian Quadrature
        // Formulae", https://www.cs.purdue.edu/homes/wxg/selected_works/section_08/084.pdf
        //
        // Expected results computed in multi precision following test #4 in
        // the paper above. The JQuantLib port uses double precision
        // throughout (see MomentBasedGaussianPolynomial); the C++ test note
        // explicitly says "QuantLib's own determinant function will not work
        // here as it supports only double precision" — yet the test still
        // passes at 1e-5 tolerance because the abscissae sum is sufficiently
        // well-conditioned. We keep the same tolerance here.
        final double[] expected = {
                47.53491786730293,
                70.6103295419633383,
                98.0593406849441607,
                129.853401537905341,
                165.96963582663912,
                206.389183233992043
        };

        final double nu = 4.0;
        final double lambda = 1.0;
        final GaussNonCentralChiSquaredPolynomial orthPoly =
                new GaussNonCentralChiSquaredPolynomial(nu, lambda);

        final double tol = 1e-5;
        for (int n = 4; n < 10; ++n) {
            final GaussianQuadrature q = new GaussianQuadrature(n, orthPoly);
            double calculated = 0.0;
            for (int i = 0; i < q.order(); ++i) {
                calculated += q.x(i);
            }
            if (Math.abs(calculated - expected[n - 4]) > tol) {
                fail("failed to reproduce rule of sum"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected[n - 4]
                        + "\n    diff    :   " + (calculated - expected[n - 4]));
            }
        }
    }

    @Test
    public void testMultiDimensionalGaussIntegration() {
        QL.info("Testing multi-dimensional Gaussian quadrature...");

        // C++ test-suite/gaussianquadratures.cpp:328 (first sub-test only).
        //
        // The C++ test has three sub-tests; the latter two require
        // multi-precision matrix inverse/determinant on randomly-generated
        // SPD matrices using MersenneTwisterUniformRng-seeded entries. Those
        // sub-tests are intrinsically tied to QuantLib's RNG sequence and
        // mp_float matrix algebra; porting them faithfully would require
        // additional infrastructure outside the scope of this WI. The first
        // sub-test exercises the full MultiDimGaussianIntegration path
        // end-to-end and is what fails first if the tensor-product assembly
        // is wrong.
        //
        //   ∫_{R^n} exp(-<x, x>) dx = pi^{n/2}
        final Function<double[], Double> normal = x -> {
            double s = 0.0;
            for (final double xi : x) {
                s += xi * xi;
            }
            return Math.exp(-s);
        };
        for (int n = 1; n < 5; ++n) {
            final int[] ns = new int[n];
            for (int i = 0; i < n; ++i) {
                ns[i] = i + 1;
            }

            final MultiDimGaussianIntegration quad =
                    new MultiDimGaussianIntegration(ns, GaussHermiteIntegration::new);

            final double tol = 1.0e4 * QL_EPSILON;
            final double calculated = quad.op(normal);
            final double expected = Math.sqrt(Math.pow(Math.PI, n));
            final double diff = Math.abs(expected - calculated);
            if (diff > tol) {
                fail("failed to reproduce multi dimensional Gaussian quadrature"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    diff:       " + diff);
            }
        }
    }

    // ---- test-only helpers --------------------------------------------------------

    /**
     * Test-only port of the inline C++ template specialization
     * {@code MomentBasedGaussLaguerrePolynomial<Real>} from
     * test-suite/gaussianquadratures.cpp. The Java port has no template
     * parameter — see the Phase 4j {@link MomentBasedGaussianPolynomial}
     * port note for the double-precision rationale.
     *
     * <p>Moments of the weight {@code w(x) = exp(-x)} on {@code [0, infty)}:
     * <pre>
     *   moment(0) = Gamma(1) = 1
     *   moment(i) = Gamma(i+1) = i * moment(i-1)  for i > 0
     * </pre>
     */
    private static final class MomentBasedGaussLaguerrePolynomial
            extends MomentBasedGaussianPolynomial {

        @Override
        public double moment(final int i) {
            if (i == 0) {
                return 1.0;
            }
            return i * moment(i - 1);
        }

        @Override
        public double w(final double x) {
            return Math.exp(-x);
        }
    }
}

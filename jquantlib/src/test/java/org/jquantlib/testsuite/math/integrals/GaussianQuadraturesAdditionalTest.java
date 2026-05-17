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

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.integrals.GaussHyperbolicPolynomial;
import org.jquantlib.math.integrals.GaussJacobiPolynomial;
import org.jquantlib.math.integrals.GaussLaguerrePolynomial;
import org.jquantlib.math.integrals.GaussLegendreIntegration;
import org.jquantlib.math.integrals.GaussianQuadrature;
import org.jquantlib.math.integrals.MomentBasedGaussianPolynomial;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of the C++ tests in test-suite/gaussianquadratures.cpp that have
 * no existing Java equivalent (Phase 5b).
 *
 * <p>The C++ file has 10 test cases. Java already covers the foundational
 * Hermite/Laguerre/Tabulated path:
 * <ul>
 *   <li>{@code testHermite} -> {@code GaussHermiteIntegrationTest}.</li>
 *   <li>{@code testLaguerre} (basic) -> {@code GaussLaguerreIntegrationTest}.</li>
 *   <li>{@code testTabulated} -> {@code TabulatedGaussLegendreTest}.</li>
 * </ul>
 *
 * <p>Phase 5e.5b-CFC-d-90: un-ignored {@code testJacobi},
 * {@code testHyperbolic} and {@code testMomentBasedGaussianPolynomial}
 * — they only need the already-ported
 * {@code Gauss{Jacobi,Hyperbolic,Laguerre}Polynomial} and
 * {@code MomentBasedGaussianPolynomial}, no new production classes required.
 *
 * <p>Remaining cases still skipped pending production-class ports:
 * <ul>
 *   <li>{@code testGaussLaguerreCosinePolynomial}: needs
 *     {@code GaussLaguerreCosinePolynomial} / {@code GaussLaguerreSinePolynomial}.</li>
 *   <li>{@code testNonCentralChiSquared} / {@code testNonCentralChiSquaredSumOfNodes}:
 *     needs {@code GaussNonCentralChiSquaredPolynomial}
 *     (experimental/math/gaussiannoncentralchisquaredpolynomial.hpp).</li>
 *   <li>{@code testMultiDimensionalGaussIntegration}: needs
 *     {@code MultiDimGaussianIntegration} class wrapper.</li>
 * </ul>
 */
public class GaussianQuadraturesAdditionalTest {

    public GaussianQuadraturesAdditionalTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1.0e-4; // C++ test-suite default

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

    // ---- still deferred (need production-class ports) -----------------------------

    @Ignore("Phase 5b.5: needs GaussLaguerreCosinePolynomial / GaussLaguerreSinePolynomial port "
            + "(ql/math/integrals/gausslaguerrecosinepolynomial.hpp).")
    @Test
    public void testGaussLaguerreCosinePolynomial() {
        // C++ test-suite/gaussianquadratures.cpp:251.
    }

    @Ignore("Phase 5b.5: needs GaussNonCentralChiSquaredPolynomial port "
            + "(ql/experimental/math/gaussiannoncentralchisquaredpolynomial.hpp).")
    @Test
    public void testNonCentralChiSquared() {
        // C++ test-suite/gaussianquadratures.cpp:271.
    }

    @Ignore("Phase 5b.5: needs GaussNonCentralChiSquaredPolynomial port "
            + "(ql/experimental/math/gaussiannoncentralchisquaredpolynomial.hpp).")
    @Test
    public void testNonCentralChiSquaredSumOfNodes() {
        // C++ test-suite/gaussianquadratures.cpp:286.
    }

    @Ignore("Phase 5b.5: needs MultiDimGaussianIntegration class wrapper "
            + "(ql/math/integrals/gaussianquadratures.hpp). "
            + "Java has GaussianQuadMultidimIntegrator but with a different API surface.")
    @Test
    public void testMultiDimensionalGaussIntegration() {
        // C++ test-suite/gaussianquadratures.cpp:328.
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

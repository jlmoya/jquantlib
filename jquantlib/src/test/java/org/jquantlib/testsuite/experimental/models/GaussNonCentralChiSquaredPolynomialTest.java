/*
 Copyright (C) 2016 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.models;

import org.jquantlib.experimental.math.GaussNonCentralChiSquaredPolynomial;
import org.jquantlib.math.integrals.GaussianQuadrature;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Phase 4j tests for {@link GaussNonCentralChiSquaredPolynomial}.
 *
 * <p>Validates moments against known closed-form values and checks that
 * the Gaussian quadrature produces correct integrals of polynomial functions.
 *
 * @author Phase 4j port
 */
public class GaussNonCentralChiSquaredPolynomialTest {

    private static final double TOL = 1e-10;

    /**
     * moment(0) must be 1.0 (normalised distribution).
     */
    @Test
    public void testMomentZero() {
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(4.0, 2.0);
        assertEquals("moment(0) must be 1.0", 1.0, poly.moment(0), TOL);
    }

    /**
     * moment(1) = nu + lambda (first moment of nc-chi2).
     */
    @Test
    public void testMomentOne() {
        final double nu = 4.0, la = 2.0;
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(nu, la);
        assertEquals("moment(1) must be nu+lambda", nu + la, poly.moment(1), TOL);
    }

    /**
     * moment(2) = la^2 + 2*la*(2+nu) + nu*(2+nu).
     */
    @Test
    public void testMomentTwo() {
        final double nu = 4.0, la = 2.0;
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(nu, la);
        final double expected = la * la + 2.0 * la * (2.0 + nu) + nu * (2.0 + nu);
        assertEquals("moment(2) polynomial", expected, poly.moment(2), TOL);
    }

    /**
     * The quadrature with the {@code / w(x)} normalisation integrates against
     * the unweighted measure, so the weighted moment must be recovered by
     * multiplying each weight by the PDF: sum_i w_i * w(x_i) * x_i = moment(1).
     */
    @Test
    public void testQuadratureFirstMoment() {
        final double nu = 4.0, la = 2.0;
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(nu, la);

        final int n = 8;
        final GaussianQuadrature gq = new GaussianQuadrature(n, poly);

        // gq.weight(i) = mu_0 * v_{0,i}^2 / w(x_i), so:
        // sum w_i * w(x_i) * x_i ≈ integral x * w(x) dx = moment(1)
        double sum = 0.0;
        for (int i = 0; i < n; ++i) {
            sum += gq.weight(i) * poly.w(gq.x(i)) * gq.x(i);
        }
        assertEquals("quadrature first moment", nu + la, sum, 1e-8);
    }

    /**
     * Quadrature second moment: sum_i w_i * w(x_i) * x_i^2 = moment(2).
     */
    @Test
    public void testQuadratureSecondMoment() {
        final double nu = 4.0, la = 2.0;
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(nu, la);

        final int n = 8;
        final GaussianQuadrature gq = new GaussianQuadrature(n, poly);

        double sum = 0.0;
        for (int i = 0; i < n; ++i) {
            final double xi = gq.x(i);
            sum += gq.weight(i) * poly.w(xi) * xi * xi;
        }
        final double expected = poly.moment(2);
        assertEquals("quadrature second moment", expected, sum, 1e-5);
    }

    /**
     * Quadrature nodes should all be positive (nc-chi2 support is [0, +inf)).
     */
    @Test
    public void testQuadratureNodesPositive() {
        final double nu = 6.0, la = 4.0;
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(nu, la);

        final GaussianQuadrature gq = new GaussianQuadrature(10, poly);
        for (int i = 0; i < gq.order(); ++i) {
            assertTrue("Quadrature node[" + i + "] must be positive: " + gq.x(i),
                    gq.x(i) > 0.0);
        }
    }

    /**
     * Weight function w(x) should be non-negative for positive x.
     */
    @Test
    public void testWeightFunctionPositive() {
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(4.0, 2.0);
        for (double x = 0.1; x <= 20.0; x += 0.5) {
            assertTrue("w(" + x + ") must be >= 0", poly.w(x) >= 0.0);
        }
        assertEquals("w(0) must be 0", 0.0, poly.w(0.0), TOL);
    }

    /**
     * Test with lambda=0: nc-chi2(nu, 0) = central chi2(nu).
     * moment(1) = nu, moment(2) = nu*(nu+2).
     */
    @Test
    public void testLambdaZero() {
        final double nu = 5.0;
        final GaussNonCentralChiSquaredPolynomial poly =
                new GaussNonCentralChiSquaredPolynomial(nu, 0.0);
        assertEquals("moment(1) with lambda=0 should be nu", nu, poly.moment(1), TOL);
        assertEquals("moment(2) with lambda=0 should be nu*(nu+2)",
                nu * (nu + 2.0), poly.moment(2), TOL);
    }
}

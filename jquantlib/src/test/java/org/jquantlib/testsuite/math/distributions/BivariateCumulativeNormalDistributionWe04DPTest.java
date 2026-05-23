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
 */
package org.jquantlib.testsuite.math.distributions;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.distributions.BivariateCumulativeNormalDistributionWe04DP;
import org.junit.Test;

/**
 * Tests for {@link BivariateCumulativeNormalDistributionWe04DP} mirroring v1.42.1
 * {@code test-suite/distributions.cpp} {@code checkBivariateAtZero<...>} and
 * {@code checkBivariateTail<...>} called with the West-2004 template parameter.
 *
 * <p>Phase 2 L1-D port.
 */
public class BivariateCumulativeNormalDistributionWe04DPTest {

    public BivariateCumulativeNormalDistributionWe04DPTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of v1.42.1 {@code checkBivariateAtZero<BivariateCumulativeNormalDistributionWe04DP>(
     * "West 2004", 1.0e-15)}.
     *
     * <p>Closed-form identity {@code BVN(0,0,rho) = 1/4 + arcsin(rho)/(2*pi)}.
     */
    @Test
    public void testBivariateAtZero() {
        final double[] rho = { 0.0, 0.1, 0.2, 0.3, 0.4, 0.5,
                0.6, 0.7, 0.8, 0.9, 0.99999 };
        final double x = 0.0;
        final double y = 0.0;
        final double tolerance = 1.0e-15;
        for ( final double element : rho ) {
            for ( int sgn = -1; sgn < 2; sgn += 2 ) {
                final BivariateCumulativeNormalDistributionWe04DP bvn =
                        new BivariateCumulativeNormalDistributionWe04DP(sgn * element);
                final double expected = 0.25 + Math.asin(sgn * element) / (2.0 * Math.PI);
                final double realised = bvn.op(x, y);
                assertEquals("rho=" + (sgn * element), expected, realised, tolerance);
            }
        }
    }

    /**
     * Faithful port of v1.42.1 {@code checkBivariateTail<BivariateCumulativeNormalDistributionWe04DP>(
     * "West 2004", 1.0e-6)} and {@code 1.0e-8} variants.
     */
    @Test
    public void testBivariateTail() {
        runTailCheck(1.0e-6);
        runTailCheck(1.0e-8);
    }

    private void runTailCheck(final double tolerance) {
        final double x = -6.9;
        double y = 6.9;
        final double corr = -0.999;
        final BivariateCumulativeNormalDistributionWe04DP bvn =
                new BivariateCumulativeNormalDistributionWe04DP(corr);
        for ( int i = 0; i < 10; i++ ) {
            final double cdf0 = bvn.op(x, y);
            y = y + tolerance;
            final double cdf1 = bvn.op(x, y);
            if ( cdf0 > cdf1 ) {
                throw new AssertionError("cdf must be decreasing in the tails: cdf0="
                        + cdf0 + " cdf1=" + cdf1 + " x=" + x + " y=" + y + " rho=" + corr);
            }
        }
    }

    /**
     * Sanity: at zero with rho=0.5, expected = 1/4 + asin(0.5)/(2*pi) =
     * 1/4 + (pi/6)/(2*pi) = 1/4 + 1/12 = 1/3.
     */
    @Test
    public void testKnownValueAtZero() {
        final BivariateCumulativeNormalDistributionWe04DP bvn =
                new BivariateCumulativeNormalDistributionWe04DP(0.5);
        assertEquals("BVN(0,0,0.5)", 1.0 / 3.0, bvn.op(0.0, 0.0), 1.0e-15);
    }

    /**
     * Sanity check on the cross-validation tier: at rho=0 the bivariate
     * factorizes into the product of marginals. So BVN(x, y, 0) = N(x)*N(y).
     */
    @Test
    public void testZeroCorrelationFactorizes() {
        final BivariateCumulativeNormalDistributionWe04DP bvn =
                new BivariateCumulativeNormalDistributionWe04DP(0.0);
        final double[][] cases = {
                { 0.5, 0.5 },
                { 1.0, -1.0 },
                { -2.0, 2.0 },
                { 1.5, 0.5 },
        };
        final org.jquantlib.math.distributions.CumulativeNormalDistribution cdf =
                new org.jquantlib.math.distributions.CumulativeNormalDistribution();
        for ( final double[] xy : cases ) {
            final double expected = cdf.op(xy[0]) * cdf.op(xy[1]);
            final double realised = bvn.op(xy[0], xy[1]);
            assertEquals("rho=0 BVN(" + xy[0] + "," + xy[1] + ")", expected, realised, 1.0e-14);
        }
    }
}

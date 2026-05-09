/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.math;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.experimental.math.GaussianCopulaPolicy;
import org.jquantlib.experimental.math.LevyFlightDistribution;
import org.junit.Test;

/**
 * Phase 4k tests for {@link GaussianCopulaPolicy} and
 * {@link LevyFlightDistribution}.
 */
public class GaussianCopulaPolicyTest {

    private static final double TOL = 1.0e-12;

    @Test
    public void testGaussianCopulaPolicyConstruction() {
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.3);
        row.add(0.4);
        weights.add(row);
        final GaussianCopulaPolicy policy = new GaussianCopulaPolicy(weights);
        // numFactors_ = factorWeights.size() + factorWeights[0].size() = 1 + 2
        assertEquals(3, policy.numFactors());
        assertNotNull(policy.getInitTraits());
    }

    @Test
    public void testGaussianCopulaPolicyRejectsNonNormalised() {
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.9);
        row.add(0.9);  // sum-of-squares = 1.62 > 1
        weights.add(row);
        try {
            new GaussianCopulaPolicy(weights);
            fail("Expected exception for non-normalised factor weights");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void testGaussianCumulativeAtZero() {
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.1);
        weights.add(row);
        final GaussianCopulaPolicy policy = new GaussianCopulaPolicy(weights);
        // Standard normal CDF(0) = 0.5
        assertEquals(0.5, policy.cumulativeY(0.0, 0), 1.0e-10);
        assertEquals(0.5, policy.cumulativeZ(0.0), 1.0e-10);
    }

    @Test
    public void testGaussianInverseRoundTrip() {
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.1);
        weights.add(row);
        final GaussianCopulaPolicy policy = new GaussianCopulaPolicy(weights);
        // Round-trip: inverse(cumulative(x)) = x for x in (mu - 5sigma, mu + 5sigma)
        for (double x = -2.0; x <= 2.0; x += 0.5) {
            final double p = policy.cumulativeY(x, 0);
            final double back = policy.inverseCumulativeY(p, 0);
            assertEquals("round-trip x=" + x, x, back, 1.0e-6);
        }
    }

    @Test
    public void testAllFactorCumulInverter() {
        final List<List<Double>> weights = new ArrayList<>();
        final List<Double> row = new ArrayList<>();
        row.add(0.1);
        weights.add(row);
        final GaussianCopulaPolicy policy = new GaussianCopulaPolicy(weights);
        final double[] probs = { 0.5, 0.5, 0.5 };
        final double[] result = policy.allFactorCumulInverter(probs);
        // inverse-CDF(0.5) = 0 for all
        assertArrayEquals(new double[] { 0.0, 0.0, 0.0 }, result, 1.0e-10);
    }

    @Test
    public void testLevyFlightDistributionParameters() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(2.0, 1.5);
        assertEquals(2.0, lfd.xm(), TOL);
        assertEquals(1.5, lfd.alpha(), TOL);
        assertEquals(2.0, lfd.min(), TOL);
        assertEquals(Double.MAX_VALUE, lfd.max(), 0.0);
    }

    @Test
    public void testLevyFlightPdfBelowSupportIsZero() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(1.0, 1.5);
        assertEquals(0.0, lfd.op(0.5), TOL);
    }

    @Test
    public void testLevyFlightPdfClosedForm() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(1.0, 2.0);
        // For xm=1, alpha=2: p(x) = 2 * (1/x)^2 / x = 2/x^3
        // At x=2: p(2) = 2/8 = 0.25
        assertEquals(0.25, lfd.op(2.0), TOL);
        // At x=1 (the boundary): p(1) = 2/1 = 2
        assertEquals(2.0, lfd.op(1.0), TOL);
    }

    @Test
    public void testLevyFlightDrawInverseTransform() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution(1.0, 2.0);
        // For u close to 1, draw() ~ xm = 1
        assertEquals(1.0, lfd.draw(1.0), TOL);
        // For xm=1, alpha=2: x = u^{-1/2}
        // At u=0.25: x = 2.0
        assertEquals(2.0, lfd.draw(0.25), TOL);
    }

    @Test
    public void testLevyFlightAlphaMustBePositive() {
        try {
            new LevyFlightDistribution(1.0, 0.0);
            fail("Expected exception for alpha <= 0");
        } catch (final Exception e) {
            // expected
        }
        try {
            new LevyFlightDistribution(1.0, -1.0);
            fail("Expected exception for alpha < 0");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void testLevyFlightParamRoundTrip() {
        final LevyFlightDistribution lfd = new LevyFlightDistribution();
        final LevyFlightDistribution.ParamType p = new LevyFlightDistribution.ParamType(3.0, 2.5);
        lfd.param(p);
        assertEquals(3.0, lfd.xm(), TOL);
        assertEquals(2.5, lfd.alpha(), TOL);
        final LevyFlightDistribution.ParamType q = lfd.param();
        assertEquals(3.0, q.xm(), TOL);
        assertEquals(2.5, q.alpha(), TOL);
    }
}

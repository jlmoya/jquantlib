/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite.model.marketmodels.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantAbcdVariance;
import org.jquantlib.termstructures.volatility.AbcdFunction;
import org.junit.Test;

/**
 * Tests for {@link PiecewiseConstantAbcdVariance} — Phase 3j B.2.
 */
public class PiecewiseConstantAbcdVarianceTest {

    public PiecewiseConstantAbcdVarianceTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    /**
     * Verify the variance and volatility against the underlying AbcdFunction directly
     * (cross-validation: pcabcd.variances[i] == abcd.variance(start, end, T_reset)).
     */
    @Test
    public void testSelfConsistencyWithAbcd() {
        final double a = 0.1, b = 0.2, c = 1.5, d = 0.1;
        final int resetIndex = 2;
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};

        final PiecewiseConstantAbcdVariance v =
                new PiecewiseConstantAbcdVariance(a, b, c, d, resetIndex, rateTimes);
        final AbcdFunction abcd = new AbcdFunction(a, b, c, d);

        // Verify variances[i] for i=0..resetIndex
        final double T = rateTimes[resetIndex];
        // i=0: integration on (0, rateTimes[0])
        assertEquals(abcd.variance(0.0, rateTimes[0], T), v.variances()[0], TOL);
        // i=1: integration on (rateTimes[0], rateTimes[1])
        assertEquals(abcd.variance(rateTimes[0], rateTimes[1], T), v.variances()[1], TOL);
        // i=2: integration on (rateTimes[1], rateTimes[2])
        assertEquals(abcd.variance(rateTimes[1], rateTimes[2], T), v.variances()[2], TOL);

        // Volatilities
        final double v0 = v.variances()[0] / (rateTimes[0] - 0.0);
        final double v1 = v.variances()[1] / (rateTimes[1] - rateTimes[0]);
        final double v2 = v.variances()[2] / (rateTimes[2] - rateTimes[1]);
        assertEquals(Math.sqrt(v0), v.volatilities()[0], TOL);
        assertEquals(Math.sqrt(v1), v.volatilities()[1], TOL);
        assertEquals(Math.sqrt(v2), v.volatilities()[2], TOL);
    }

    /** Total variance / volatility cross-checks. */
    @Test
    public void testTotalVariance() {
        final double[] rateTimes = {0.25, 0.5, 0.75, 1.0};
        final PiecewiseConstantAbcdVariance v =
                new PiecewiseConstantAbcdVariance(0.05, 0.1, 0.5, 0.05, 2, rateTimes);

        // Variances are all >= 0
        for (int i = 0; i < 3; ++i) {
            assertTrue("variances[" + i + "] >= 0", v.variances()[i] >= 0.0);
            assertTrue("volatilities[" + i + "] >= 0", v.volatilities()[i] >= 0.0);
        }
        // totalVariance(2) = sum of all
        final double sum = v.variances()[0] + v.variances()[1] + v.variances()[2];
        assertEquals(sum, v.totalVariance(2), TOL);
    }

    /** Verify getABCD round-trip. */
    @Test
    public void testGetAbcd() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final PiecewiseConstantAbcdVariance v =
                new PiecewiseConstantAbcdVariance(0.11, 0.22, 0.33, 0.44, 0, rateTimes);
        final double[] out = new double[4];
        v.getABCD(out);
        assertEquals(0.11, out[0], 0);
        assertEquals(0.22, out[1], 0);
        assertEquals(0.33, out[2], 0);
        assertEquals(0.44, out[3], 0);
    }
}

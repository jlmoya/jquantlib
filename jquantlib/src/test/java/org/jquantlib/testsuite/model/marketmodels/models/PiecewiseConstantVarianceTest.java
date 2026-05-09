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

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantVariance;
import org.junit.Test;

/**
 * Tests for {@link PiecewiseConstantVariance} — Phase 3j L0.1 (Track B).
 */
public class PiecewiseConstantVarianceTest {

    public PiecewiseConstantVarianceTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Anonymous subclass with constant variances. */
    private static PiecewiseConstantVariance pcv(final double[] vars,
                                                  final double[] vols,
                                                  final double[] times) {
        return new PiecewiseConstantVariance() {
            @Override public double[] variances() { return vars; }
            @Override public double[] volatilities() { return vols; }
            @Override public double[] rateTimes() { return times; }
        };
    }

    @Test
    public void testVarianceAndTotalVariance() {
        final double[] variances = {0.04, 0.05, 0.06};
        final double[] vols = {0.2, 0.22360679774997896, 0.24494897427831780};
        // rateTimes — used by totalVolatility for normalization
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0};

        final PiecewiseConstantVariance v = pcv(variances, vols, rateTimes);

        assertEquals(0.04, v.variance(0), 1e-15);
        assertEquals(0.05, v.variance(1), 1e-15);
        assertEquals(0.06, v.variance(2), 1e-15);

        // totalVariance: cumulative sum
        assertEquals(0.04, v.totalVariance(0), 1e-15);
        assertEquals(0.09, v.totalVariance(1), 1e-15);
        assertEquals(0.15, v.totalVariance(2), 1e-15);

        // totalVolatility = sqrt(totalVariance / rateTimes[i])
        assertEquals(Math.sqrt(0.04 / 1.0), v.totalVolatility(0), 1e-15);
        assertEquals(Math.sqrt(0.09 / 2.0), v.totalVolatility(1), 1e-15);
        assertEquals(Math.sqrt(0.15 / 3.0), v.totalVolatility(2), 1e-15);
    }
}

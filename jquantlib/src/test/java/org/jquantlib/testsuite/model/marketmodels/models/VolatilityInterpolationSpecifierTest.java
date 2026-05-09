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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.models.PiecewiseConstantAbcdVariance;
import org.jquantlib.model.marketmodels.models.VolatilityInterpolationSpecifierAbcd;
import org.junit.Test;

/**
 * Smoke tests for {@link VolatilityInterpolationSpecifierAbcd} — Phase 3j B.8 + B.9.
 */
public class VolatilityInterpolationSpecifierTest {

    public VolatilityInterpolationSpecifierTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Smoke: construct and verify dimensions. */
    @Test
    public void testConstructAndDimensions() {
        // 3 big rates with period=2, offset=1 → 7 small rates (so timesForSmallRates length = 8)
        // small rates indices 0..6; offset=1 means big rate i corresponds to small rate index 1 + i*2
        // bigRateTimes[i] should == timesForSmallRates[offset + i*period] = timesForSmallRates[1, 3, 5]
        final double[] timesForSmallRates = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};
        // big rate j has rateTimes containing the small rates at indices offset + 0*period .. offset + j*period
        // i.e., big rate j has rateTimes of length j+1 ending at timesForSmallRates[offset + j*period]
        // Per C++: rateTimes for variance j must equal timesForSmallRates[offset + 0*period], ..., offset + j*period
        // So big rate j has rateTimes of length j+1 (j=0,1,2 → lengths 1, 2, 3 — wait, that's invalid: AbcdVariance needs > 1).
        // The C++ check is: for (j=0..rateTimes.size()) — rateTimes[j] must == timesForSmallRates[offset + j*period].
        // So rateTimes should be a subset of timesForSmallRates at evenly-spaced indices starting from offset.
        // For 3 big rates and period=2, offset=1, bigRateTimes per rate = [timesForSmallRates[1, 3, 5, ...]]
        // => big rate i has rateTimes = [1.0, 2.0, 3.0, 4.0] (4 values, allowing resetIndex 0..3-1=2)
        // BUT we need each big rate variance with resetIndex i in 0..2 → all big rates share same rateTimes
        final double[] bigRateTimes = {1.0, 2.0, 3.0, 4.0};

        final List<PiecewiseConstantAbcdVariance> originals = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            originals.add(new PiecewiseConstantAbcdVariance(0.05, 0.1, 0.5, 0.05, i, bigRateTimes));
        }

        final VolatilityInterpolationSpecifierAbcd spec = new VolatilityInterpolationSpecifierAbcd(
                2, 1, originals, timesForSmallRates);
        assertNotNull(spec);
        assertEquals(2, spec.getPeriod());
        assertEquals(1, spec.getOffset());
        assertEquals(3, spec.getNoBigRates());
        assertEquals(7, spec.getNoSmallRates());
        assertEquals(7, spec.interpolatedVariances().size());
        assertEquals(3, spec.originalVariances().size());

        // Each interpolated variance should be a PiecewiseConstantAbcdVariance
        for (int i = 0; i < spec.getNoSmallRates(); ++i) {
            assertNotNull(spec.interpolatedVariances().get(i));
            assertTrue("variances[" + i + "][0] >= 0",
                    spec.interpolatedVariances().get(i).variances()[0] >= 0.0);
        }
    }

    /** Smoke: setScalingFactors recomputes variances. */
    @Test
    public void testSetScalingFactors() {
        final double[] timesForSmallRates = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};
        final double[] bigRateTimes = {1.0, 2.0, 3.0, 4.0};
        final List<PiecewiseConstantAbcdVariance> originals = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            originals.add(new PiecewiseConstantAbcdVariance(0.05, 0.1, 0.5, 0.05, i, bigRateTimes));
        }
        final VolatilityInterpolationSpecifierAbcd spec = new VolatilityInterpolationSpecifierAbcd(
                2, 1, originals, timesForSmallRates);

        // Capture variance before
        final double v0Before = spec.interpolatedVariances().get(0).variances()[0];
        spec.setScalingFactors(new double[]{2.0, 2.0, 2.0});
        final double v0After = spec.interpolatedVariances().get(0).variances()[0];
        // After scaling a, b, d by 2 (c unchanged), the variance changes
        assertTrue("variance should change with scaling factors", v0Before != v0After);
    }
}

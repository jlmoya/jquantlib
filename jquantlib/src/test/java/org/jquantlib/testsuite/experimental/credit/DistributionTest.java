/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.experimental.credit.Distribution;
import org.junit.Test;

/**
 * Phase 4m foundation tests for {@link Distribution}.
 *
 * <p>Cross-validation: hand-computed reference values driven from
 * the C++ source ({@code ql/experimental/credit/distribution.{hpp,cpp}}
 * v1.42.1) — uniform-bucket scenarios make all per-cell values
 * reproducible analytically.
 */
public class DistributionTest {

    @Test
    public void emptyConstructor() {
        final Distribution d = new Distribution();
        assertEquals(0, d.size());
    }

    @Test
    public void bucketLayout() {
        final Distribution d = new Distribution(10, 0.0, 1.0);
        assertEquals(10, d.size());
        assertEquals(0.0, d.x(0), 0.0);
        // dx is uniform 0.1 (last cell may be tweaked to match domain exactly).
        assertEquals(0.1, d.dx(0), 1.0e-15);
        // Last cell is right-closed at xmax.
        assertEquals(0.9, d.x(9), 1.0e-12);
        // Last dx is computed to match the domain exactly (xmax - x[size-1]).
        assertEquals(0.1, d.dx(9), 1.0e-12);
    }

    @Test
    public void locateBoundaries() {
        final Distribution d = new Distribution(10, 0.0, 1.0);
        assertEquals(0, d.locate(0.0));
        // x = 0.5 → cells x[0]=0..x[5]=0.5; first cell with x>0.5 is index 6, so locate returns 5.
        assertEquals(5, d.locate(0.5));
        // x at upper bound returns last index.
        assertEquals(9, d.locate(1.0));
    }

    @Test
    public void addBucketCounts() {
        final Distribution d = new Distribution(10, 0.0, 1.0);
        // Add 100 samples spread across all cells.
        for (int i = 0; i < 100; i++) {
            d.add((i + 0.5) / 100.0);
        }
        // After normalization, density should integrate to ~1.0.
        double total = 0;
        for (int i = 0; i < d.size(); i++) {
            total += d.density(i) * d.dx(i);
        }
        assertEquals(1.0, total, 1.0e-12);
    }

    @Test
    public void densitySumsToUnitWithDirectAddDensity() {
        final Distribution d = new Distribution(5, 0.0, 1.0);
        // Equal density 2.0 in each cell — integrates to 2 * 0.2 * 5 = 2.0. Pre-normalize.
        for (int i = 0; i < 5; i++) {
            d.addDensity(i, 2.0);
        }
        // density.get(i) is 2.0 (no sample-count normalization since count_ == 0).
        assertEquals(2.0, d.density(0), 1.0e-12);
        // cumulative = 2 * 0.2 + 2 * 0.2 + ... — cumulative(4) = 2.0.
        assertEquals(2.0, d.cumulative(4), 1.0e-12);
    }

    @Test
    public void addDensityOutOfRangeRejected() {
        final Distribution d = new Distribution(5, 0.0, 1.0);
        try {
            d.addDensity(5, 1.0);
            fail("Expected exception for out-of-range bucket");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("out of range"));
        }
        try {
            d.addDensity(-1, 1.0);
            fail("Expected exception for negative bucket");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("out of range"));
        }
    }

    @Test
    public void confidenceLevelMonotone() {
        final Distribution d = new Distribution(10, 0.0, 1.0);
        // Uniform density: cumulative(i) = (i+1)/10.
        for (int i = 0; i < 10; i++) {
            d.addDensity(i, 1.0); // density = 1.0 per cell, cumulative grows by 0.1
        }
        // confidenceLevel(0.5) returns first x[i]+dx[i] with cumulative > 0.5.
        // After cumulative(5) = 0.6 > 0.5, return x(5)+dx(5) = 0.5 + 0.1 = 0.6.
        assertEquals(0.6, d.confidenceLevel(0.5), 1.0e-12);
    }

    @Test
    public void expectedValueUniform() {
        final Distribution d = new Distribution(10, 0.0, 1.0);
        for (int i = 0; i < 10; i++) {
            d.addDensity(i, 1.0);
        }
        // E[X] for uniform on [0,1] ≈ 0.5 (midpoint approx).
        assertEquals(0.5, d.expectedValue(), 1.0e-12);
    }

    @Test
    public void normalizeIdempotent() {
        final Distribution d = new Distribution(5, 0.0, 1.0);
        for (int i = 0; i < 5; i++) {
            d.addDensity(i, 1.0);
        }
        d.normalize();
        final double e1 = d.expectedValue();
        d.normalize();
        final double e2 = d.expectedValue();
        assertEquals(e1, e2, 0.0);
    }

    @Test
    public void convolveTwoUniformDistributions() {
        final Distribution d1 = new Distribution(5, 0.0, 1.0);
        final Distribution d2 = new Distribution(5, 0.0, 1.0);
        for (int i = 0; i < 5; i++) {
            d1.addDensity(i, 1.0);
            d2.addDensity(i, 1.0);
        }
        d1.normalize();
        d2.normalize();
        final Distribution conv = Distribution.convolve(d1, d2);
        assertEquals(d1.size() + d2.size() - 1, conv.size());
        for (int i = 0; i < conv.size(); i++) {
            assertTrue("density[" + i + "] should be ≥ 0", conv.density(i) >= -1.0e-12);
        }
    }

    @Test
    public void convolveBucketSizeMismatchRejected() {
        final Distribution d1 = new Distribution(5, 0.0, 1.0);
        // Different bucket size: smaller domain with same #buckets.
        final Distribution d2 = new Distribution(5, 0.0, 0.5);
        try {
            Distribution.convolve(d1, d2);
            fail("Expected exception for bucket-size mismatch");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("bucket"));
        }
    }

    @Test
    public void cumulativeExcessProbabilityForEmptyDistribution() {
        final Distribution d = new Distribution(10, 0.0, 1.0);
        // With no samples, normalize() initializes excessProbability[0]=1.0, then
        // recursively cumulativeExcessProbability[i] = excessProbability[i-1]*dx[i-1] + ...
        // Since count=0, density=0, so excess[i]=1.0 for all i, and cumExcess[i] = i*dx.
        // cumulativeExcessProbability(0.1,0.5) = cumExcess[5] - cumExcess[1] = 5*0.1 - 1*0.1 = 0.4.
        assertEquals(0.4, d.cumulativeExcessProbability(0.1, 0.5), 1.0e-12);
    }
}

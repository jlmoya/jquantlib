/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.math.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.statistics.GeneralStatistics;
import org.junit.Test;

/**
 * Direct construction + cross-checks for the (now concrete)
 * {@link GeneralStatistics} class.
 *
 * <p>Phase 4m.7c WI-2: GeneralStatistics was previously declared
 * {@code abstract} purely as an inheritance scaffold. C++ v1.42.1 declares
 * it as a concrete class (see {@code ql/math/statistics/generalstatistics.hpp}),
 * and the random loss models (RandomDefaultLM, RandomLossLM) instantiate it
 * directly to back per-name VaR-split statistics. This test confirms the
 * direct-instantiation paths produce the analytic mean/variance/percentile
 * values expected from a small known sample.
 *
 * <p>Tolerance tier: TIGHT (1e-12 abs).
 */
public class GeneralStatisticsTest {

    private static final double TIGHT = 1.0e-12;

    @Test
    public void emptyConstructorAllowsAddAndReset() {
        final GeneralStatistics s = new GeneralStatistics();
        assertEquals(0, s.samples());
        s.reset();
        assertEquals(0, s.samples());
    }

    @Test
    public void uniformSampleStatistics() {
        final GeneralStatistics s = new GeneralStatistics();
        final double[] data = {1.0, 2.0, 3.0, 4.0, 5.0};
        for (final double d : data) s.add(d);
        assertEquals(5, s.samples());
        assertEquals(5.0, s.weightSum(), TIGHT);
        assertEquals(3.0, s.mean(), TIGHT);
        // sample variance = sum((x_i - mean)^2)*N/((N-1)*N) = (4+1+0+1+4)/4 = 2.5
        assertEquals(2.5, s.variance(), TIGHT);
        assertEquals(Math.sqrt(2.5), s.standardDeviation(), TIGHT);
        assertEquals(1.0, s.min(), TIGHT);
        assertEquals(5.0, s.max(), TIGHT);
    }

    @Test
    public void weightedAddition() {
        final GeneralStatistics s = new GeneralStatistics();
        s.add(1.0, 2.0);
        s.add(2.0, 1.0);
        s.add(3.0, 1.0);
        assertEquals(3, s.samples());
        assertEquals(4.0, s.weightSum(), TIGHT);
        // weighted mean = (2*1 + 1*2 + 1*3) / 4 = 7/4 = 1.75
        assertEquals(1.75, s.mean(), TIGHT);
    }

    @Test
    public void percentileMatchesCppDefinition() {
        final GeneralStatistics s = new GeneralStatistics();
        final double[] data = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};
        for (final double d : data) s.add(d);
        // C++ percentile: smallest x s.t. sum-of-weights up-through-x >= p*sumW.
        // For uniform weights 1.0, sumW=10. p=0.5 -> target=5; cumulative
        // weight reaches 5 at index 4 (value 5.0).
        assertEquals(5.0, s.percentile(0.5), TIGHT);
        assertEquals(1.0, s.percentile(0.1), TIGHT);
        assertEquals(10.0, s.percentile(1.0), TIGHT);
        // topPercentile mirrors but from the top.
        assertEquals(6.0, s.topPercentile(0.5), TIGHT);
    }

    @Test
    public void reserveDoesNotChangeContents() {
        final GeneralStatistics s = new GeneralStatistics();
        s.add(1.0);
        s.reserve(1000);
        assertEquals(1, s.samples());
        assertEquals(1.0, s.mean(), TIGHT);
    }

    @Test
    public void sortPreservesValues() {
        final GeneralStatistics s = new GeneralStatistics();
        s.add(3.0);
        s.add(1.0);
        s.add(2.0);
        s.sort();
        // After sort, the data is in ascending order; data() exposes a list.
        assertEquals(1.0, s.data().get(0).first(), TIGHT);
        assertEquals(2.0, s.data().get(1).first(), TIGHT);
        assertEquals(3.0, s.data().get(2).first(), TIGHT);
    }

    @Test
    public void emptyMeanThrows() {
        final GeneralStatistics s = new GeneralStatistics();
        try {
            s.mean();
            assertTrue("expected exception", false);
        } catch (final RuntimeException expected) {
            // ok
        }
    }
}

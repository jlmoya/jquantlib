/*
Copyright (C) 2026 Jose Moya

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
 */

package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.Utilities;
import org.junit.Test;

/**
 * Tests for {@link Utilities} — Phase 3h A.1.
 *
 * <p>These tests cross-validate against the C++ algorithms in
 * {@code ql/models/marketmodels/utilities.cpp} (QuantLib v1.42.1). For
 * deterministic functions (mergeTimes, isInSubset, checkIncreasing*),
 * expected values are derived from the algorithms themselves applied to
 * canonical test inputs (5-rate grid, identity tau spacing, etc.).
 */
public class MarketModelUtilitiesTest {

    public MarketModelUtilitiesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    @Test
    public void testMergeTimesEmpty() {
        final Utilities.MergeResult r = Utilities.mergeTimes(java.util.Collections.emptyList());
        assertEquals(0, r.mergedTimes().length);
        assertEquals(0, r.isPresent().length);
    }

    @Test
    public void testMergeTimesSingle() {
        final double[] a = {1.0, 2.0, 3.0};
        final Utilities.MergeResult r = Utilities.mergeTimes(java.util.Collections.singletonList(a));
        assertArrayEquals(a, r.mergedTimes(), TOL);
        assertEquals(1, r.isPresent().length);
        assertEquals(3, r.isPresent()[0].length);
        assertTrue(r.isPresent()[0][0]);
        assertTrue(r.isPresent()[0][1]);
        assertTrue(r.isPresent()[0][2]);
    }

    @Test
    public void testMergeTimesTwoOverlapping() {
        // Two grids: {1, 2, 3} and {2, 3, 4} — merged should be {1, 2, 3, 4}.
        final double[] a = {1.0, 2.0, 3.0};
        final double[] b = {2.0, 3.0, 4.0};
        final Utilities.MergeResult r = Utilities.mergeTimes(Arrays.asList(a, b));
        final double[] expected = {1.0, 2.0, 3.0, 4.0};
        assertArrayEquals(expected, r.mergedTimes(), TOL);
        // a is present at 1,2,3 (indices 0,1,2) but NOT at 4 (index 3)
        final boolean[] aIsPresent = {true, true, true, false};
        assertArrayEquals(aIsPresent, r.isPresent()[0]);
        // b is present at 2,3,4 (indices 1,2,3) but NOT at 1 (index 0)
        final boolean[] bIsPresent = {false, true, true, true};
        assertArrayEquals(bIsPresent, r.isPresent()[1]);
    }

    @Test
    public void testMergeTimesThreeWithDuplicates() {
        final double[] a = {0.5, 1.0, 1.5};
        final double[] b = {1.0, 2.0};
        final double[] c = {0.5, 2.0, 3.0};
        final Utilities.MergeResult r = Utilities.mergeTimes(Arrays.asList(a, b, c));
        final double[] expected = {0.5, 1.0, 1.5, 2.0, 3.0};
        assertArrayEquals(expected, r.mergedTimes(), TOL);
    }

    @Test
    public void testIsInSubsetEmpty() {
        final double[] set = {1.0, 2.0, 3.0};
        final double[] sub = {};
        final boolean[] r = Utilities.isInSubset(set, sub);
        assertEquals(3, r.length);
        for (final boolean v : r) {
            assertTrue(!v);
        }
    }

    @Test
    public void testIsInSubsetMatch() {
        // set strictly increasing, subset strictly increasing
        final double[] set = {1.0, 2.0, 3.0, 4.0, 5.0};
        final double[] sub = {2.0, 4.0};
        final boolean[] r = Utilities.isInSubset(set, sub);
        final boolean[] expected = {false, true, false, true, false};
        assertArrayEquals(expected, r);
    }

    /**
     * Mirrors C++ {@code test-suite/marketmodel.cpp::testIsInSubset} (line 4534).
     *
     * <p>C++ smoke/performance test: dim=100, set=[0,100), subset=[100,200). The C++
     * version asserts nothing explicitly (printReport_ is false in the test suite), but
     * by construction every set element is strictly less than every subset element, so
     * the deterministic semantics require all-false output. Java port adds that
     * assertion to keep the test meaningful in CI.
     */
    @Test
    public void testIsInSubset() {
        final int dim = 100;
        final double[] set = new double[dim];
        final double[] subset = new double[dim];
        for (int i = 0; i < dim; ++i) {
            set[i] = i * 1.0;
            subset[i] = dim + i * 1.0;
        }
        final boolean[] result = Utilities.isInSubset(set, subset);
        assertEquals(dim, result.length);
        for (int i = 0; i < dim; ++i) {
            assertTrue("set[" + i + "]=" + set[i] + " unexpectedly reported in subset", !result[i]);
        }
    }

    @Test
    public void testCheckIncreasingTimesValid() {
        Utilities.checkIncreasingTimes(new double[]{0.5, 1.0, 1.5, 2.0});
        // no exception → pass
    }

    @Test
    public void testCheckIncreasingTimesEmpty() {
        try {
            Utilities.checkIncreasingTimes(new double[0]);
            fail("expected exception for empty times");
        } catch (final RuntimeException expected) {
            // OK
        }
    }

    @Test
    public void testCheckIncreasingTimesNonIncreasing() {
        try {
            Utilities.checkIncreasingTimes(new double[]{0.5, 1.0, 1.0, 2.0});
            fail("expected exception for non-increasing times");
        } catch (final RuntimeException expected) {
            // OK
        }
    }

    @Test
    public void testCheckIncreasingTimesAndCalculateTaus() {
        final double[] times = {0.5, 1.0, 2.0, 3.5};
        final double[] taus = Utilities.checkIncreasingTimesAndCalculateTaus(times, null);
        assertEquals(3, taus.length);
        assertEquals(0.5, taus[0], TOL);
        assertEquals(1.0, taus[1], TOL);
        assertEquals(1.5, taus[2], TOL);
    }

    @Test
    public void testCheckIncreasingTimesAndCalculateTausInPlace() {
        final double[] times = {0.5, 1.0, 2.0, 3.5};
        final double[] taus = new double[3];
        final double[] result = Utilities.checkIncreasingTimesAndCalculateTaus(times, taus);
        // Same backing array (length matches → in-place)
        assertTrue(result == taus);
        assertEquals(0.5, taus[0], TOL);
    }

    @Test
    public void testMergeTimesBoxed() {
        final List<List<Double>> times = Arrays.asList(
                Arrays.asList(1.0, 2.0),
                Arrays.asList(2.0, 3.0));
        final Utilities.MergeResult r = Utilities.mergeTimesBoxed(times);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, r.mergedTimes(), TOL);
    }
}

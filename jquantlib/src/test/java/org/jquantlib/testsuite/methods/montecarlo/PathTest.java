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

package org.jquantlib.testsuite.methods.montecarlo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.time.TimeGrid;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 ql/methods/montecarlo/path.hpp structural
 * tests (Phase 5h.5-MC-INFRA WI-1). Cross-validates the Path container API
 * — front/back/get/at/value/set/length/empty/timeGrid — against the
 * inline definitions of {@code QuantLib::Path}.
 *
 * <p>TIGHT tier: structural identities are bit-exact (no FP arithmetic
 * mediates them).
 */
public class PathTest {

    public PathTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testConstructFromTimeGridZerosValues() {
        final TimeGrid grid = new TimeGrid(1.0, 4); // 4 steps -> 5 points
        final Path p = new Path(grid);

        assertEquals(5, p.length());
        assertFalse("path with non-empty grid is not empty", p.empty());
        for (int i = 0; i < p.length(); i++) {
            assertEquals("default-zero value at " + i, 0.0, p.get(i), 0.0);
        }
        assertEquals(0.0, p.front(), 0.0);
        assertEquals(0.0, p.back(), 0.0);
        assertEquals(0.0, p.time(0), 0.0);
        assertEquals(1.0, p.time(4), 0.0);
    }

    @Test
    public void testConstructFromValuesPreservesData() {
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final double[] values = {100.0, 101.5, 99.7, 102.1, 105.3};
        final Path p = new Path(grid, values);

        assertEquals(5, p.length());
        assertEquals(100.0, p.front(), 0.0);
        assertEquals(105.3, p.back(), 0.0);
        assertEquals(99.7, p.get(2), 0.0);
        assertEquals(99.7, p.value(2), 0.0);
        assertEquals(99.7, p.at(2), 0.0);
    }

    @Test
    public void testSetMutatesValues() {
        final TimeGrid grid = new TimeGrid(2.0, 3); // 3 steps -> 4 points
        final Path p = new Path(grid);

        p.set(0, 50.0);
        p.set(1, 55.0);
        p.set(2, 60.0);
        p.set(3, 65.0);

        assertEquals(50.0, p.front(), 0.0);
        assertEquals(65.0, p.back(), 0.0);
        assertEquals(55.0, p.get(1), 0.0);
        assertEquals(60.0, p.value(2), 0.0);

        p.setFront(42.0);
        p.setBack(77.0);
        assertEquals(42.0, p.front(), 0.0);
        assertEquals(42.0, p.get(0), 0.0);
        assertEquals(77.0, p.back(), 0.0);
        assertEquals(77.0, p.get(3), 0.0);
    }

    @Test
    public void testValuesReturnsBackingArray() {
        final TimeGrid grid = new TimeGrid(1.0, 2);
        final double[] values = {10.0, 20.0, 30.0};
        final Path p = new Path(grid, values);
        assertArrayEquals(values, p.values(), 0.0);
        // mutation through values() must reflect in get() (matches C++ ref
        // semantics where values_ is a member Array).
        p.values()[1] = 999.0;
        assertEquals(999.0, p.get(1), 0.0);
    }

    @Test
    public void testAtThrowsOutOfRange() {
        final TimeGrid grid = new TimeGrid(1.0, 2);
        final Path p = new Path(grid);
        try {
            p.at(99);
            fail("expected IndexOutOfBoundsException for out-of-range index");
        } catch (final IndexOutOfBoundsException expected) {
            // ok
        }
    }

    @Test
    public void testTimeGridAccessor() {
        final TimeGrid grid = new TimeGrid(2.0, 4);
        final Path p = new Path(grid);
        assertTrue("timeGrid() returns the same grid reference",
                p.timeGrid() == grid);
        assertEquals(grid.size(), p.length());
    }

    @Test
    public void testMismatchedSizeThrows() {
        final TimeGrid grid = new TimeGrid(1.0, 4); // 5 points expected
        try {
            new Path(grid, new double[] {1.0, 2.0, 3.0});
            fail("expected IllegalArgumentException for mismatched values length");
        } catch (final IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void testTimeAccessor() {
        final TimeGrid grid = new TimeGrid(1.0, 4); // 5 points 0,0.25,0.5,0.75,1.0
        final Path p = new Path(grid);
        assertEquals(0.00, p.time(0), 1e-15);
        assertEquals(0.25, p.time(1), 1e-15);
        assertEquals(0.50, p.time(2), 1e-15);
        assertEquals(0.75, p.time(3), 1e-15);
        assertEquals(1.00, p.time(4), 1e-15);
    }
}

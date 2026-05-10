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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.MultiPath;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.time.TimeGrid;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 ql/methods/montecarlo/multipath.hpp
 * structural tests (Phase 5h.5-MC-INFRA WI-2). TIGHT tier — structural
 * identities only.
 */
public class MultiPathTest {

    public MultiPathTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testConstructFromAssetCountAndGrid() {
        final TimeGrid grid = new TimeGrid(1.0, 4); // 5 points
        final MultiPath mp = new MultiPath(3, grid);

        assertEquals(3, mp.assetNumber());
        assertEquals(5, mp.pathSize());

        for (int j = 0; j < mp.assetNumber(); j++) {
            final Path p = mp.get(j);
            assertEquals(5, p.length());
            assertEquals("default-zero front", 0.0, p.front(), 0.0);
        }
    }

    @Test
    public void testConstructFromExplicitList() {
        final TimeGrid grid = new TimeGrid(1.0, 2); // 3 points
        final Path p1 = new Path(grid, new double[] {1.0, 2.0, 3.0});
        final Path p2 = new Path(grid, new double[] {10.0, 20.0, 30.0});
        final List<Path> paths = Arrays.asList(p1, p2);

        final MultiPath mp = new MultiPath(paths);
        assertEquals(2, mp.assetNumber());
        assertEquals(3, mp.pathSize());
        assertSame(p1, mp.get(0));
        assertSame(p2, mp.get(1));
    }

    @Test
    public void testEmptyDefaultConstructor() {
        final MultiPath mp = new MultiPath();
        assertEquals(0, mp.assetNumber());
    }

    @Test
    public void testSetReplacesSubPath() {
        final TimeGrid grid = new TimeGrid(1.0, 2);
        final MultiPath mp = new MultiPath(2, grid);

        final Path replacement = new Path(grid, new double[] {7.0, 8.0, 9.0});
        mp.set(1, replacement);

        assertSame(replacement, mp.get(1));
        assertEquals(7.0, mp.get(1).front(), 0.0);
        assertEquals(9.0, mp.get(1).back(), 0.0);
    }

    @Test
    public void testZeroAssetThrows() {
        final TimeGrid grid = new TimeGrid(1.0, 2);
        try {
            new MultiPath(0, grid);
            fail("expected IllegalArgumentException for nAsset=0");
        } catch (final IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void testAtThrowsOutOfRange() {
        final TimeGrid grid = new TimeGrid(1.0, 2);
        final MultiPath mp = new MultiPath(2, grid);
        try {
            mp.at(99);
            fail("expected IndexOutOfBoundsException for at(99)");
        } catch (final IndexOutOfBoundsException expected) {
            // ok
        }
    }

    @Test
    public void testMutationOfSubPathVisibleViaGet() {
        final TimeGrid grid = new TimeGrid(1.0, 4);
        final MultiPath mp = new MultiPath(2, grid);
        // mutate via get() (sub-path returned by reference, like C++)
        mp.get(0).set(2, 42.0);
        mp.get(1).set(4, 17.0);
        assertEquals(42.0, mp.get(0).get(2), 0.0);
        assertEquals(17.0, mp.get(1).back(), 0.0);
    }
}

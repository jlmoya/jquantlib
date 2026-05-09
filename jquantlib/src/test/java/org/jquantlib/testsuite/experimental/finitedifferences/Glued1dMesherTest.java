/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — Glued1dMesher smoke tests.

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
package org.jquantlib.testsuite.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.Glued1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for {@link Glued1dMesher}.
 */
public class Glued1dMesherTest {

    private static final double TIGHT = 1e-12;

    public Glued1dMesherTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void disjointMeshesAreSimplyConcatenated() {
        // Left: [0, 0.5, 1.0], Right: [2.0, 3.0]
        final Uniform1dMesher left = new Uniform1dMesher(0.0, 1.0, 3);
        final Uniform1dMesher right = new Uniform1dMesher(2.0, 3.0, 2);
        final Glued1dMesher g = new Glued1dMesher(left, right);
        assertEquals(5, g.size());
        assertEquals(0.0, g.location(0), TIGHT);
        assertEquals(0.5, g.location(1), TIGHT);
        assertEquals(1.0, g.location(2), TIGHT);
        assertEquals(2.0, g.location(3), TIGHT);
        assertEquals(3.0, g.location(4), TIGHT);
    }

    @Test
    public void overlappingMeshesShareCommonPoint() {
        // Left: [0, 0.5, 1.0], Right: [1.0, 1.5, 2.0]
        final Uniform1dMesher left = new Uniform1dMesher(0.0, 1.0, 3);
        final Uniform1dMesher right = new Uniform1dMesher(1.0, 2.0, 3);
        final Glued1dMesher g = new Glued1dMesher(left, right);
        // Total = 3 + 3 - 1 = 5 (common point absorbed)
        assertEquals(5, g.size());
        assertEquals(0.0, g.location(0), TIGHT);
        assertEquals(0.5, g.location(1), TIGHT);
        assertEquals(1.0, g.location(2), TIGHT);
        assertEquals(1.5, g.location(3), TIGHT);
        assertEquals(2.0, g.location(4), TIGHT);
    }

    @Test
    public void deltaSpacingsArePopulated() {
        final Uniform1dMesher left = new Uniform1dMesher(0.0, 1.0, 3);
        final Uniform1dMesher right = new Uniform1dMesher(2.0, 4.0, 3);
        final Glued1dMesher g = new Glued1dMesher(left, right);
        // dplus[i] = locations[i+1] - locations[i]
        assertEquals(0.5, g.dplus(0), TIGHT);
        assertEquals(0.5, g.dplus(1), TIGHT);
        assertEquals(1.0, g.dplus(2), TIGHT); // jump from 1.0 to 2.0
        assertEquals(1.0, g.dplus(3), TIGHT); // 2.0 to 3.0
        // dminus mirrors
        assertEquals(0.5, g.dminus(1), TIGHT);
        assertEquals(1.0, g.dminus(3), TIGHT);
        // Sentinels
        assertTrue(Double.isNaN(g.dminus(0)));
        assertTrue(Double.isNaN(g.dplus(g.size() - 1)));
    }

    @Test
    public void rejectsLeftRightmostGreaterThanRightLeftmost() {
        // Left ends at 2.0, right starts at 1.0 — should throw
        final Uniform1dMesher left = new Uniform1dMesher(0.0, 2.0, 3);
        final Uniform1dMesher right = new Uniform1dMesher(1.0, 3.0, 3);
        try {
            new Glued1dMesher(left, right);
            assertTrue("expected exception for overlapping (left > right) range", false);
        } catch (Exception e) {
            // ok
        }
    }
}

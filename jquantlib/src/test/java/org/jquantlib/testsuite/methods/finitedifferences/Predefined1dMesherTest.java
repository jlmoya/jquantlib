/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-RND-b — Predefined1dMesher tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link Predefined1dMesher}.
 *
 * @author Phase 5h.5-RND-b
 */
public class Predefined1dMesherTest {

    private static final double TIGHT = 1.0e-15;

    public Predefined1dMesherTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void preservesSuppliedLocations() {
        final double[] x = {-1.0, 0.0, 0.5, 1.5, 4.0};
        final Predefined1dMesher m = new Predefined1dMesher(x);
        assertEquals(x.length, m.size());
        for (int i = 0; i < x.length; ++i) {
            assertEquals(x[i], m.location(i), TIGHT);
        }
    }

    @Test
    public void computesAdjacentDifferences() {
        final double[] x = {0.0, 0.25, 0.7, 1.5};
        final Predefined1dMesher m = new Predefined1dMesher(x);
        // dplus[i] = x[i+1] - x[i]
        assertEquals(0.25, m.dplus(0), TIGHT);
        assertEquals(0.45, m.dplus(1), TIGHT);
        assertEquals(0.80, m.dplus(2), TIGHT);
        // dminus[i] = x[i] - x[i-1]
        assertEquals(0.25, m.dminus(1), TIGHT);
        assertEquals(0.45, m.dminus(2), TIGHT);
        assertEquals(0.80, m.dminus(3), TIGHT);
    }

    @Test
    public void hasNaNSentinelsAtEnds() {
        final double[] x = {0.0, 1.0, 2.0};
        final Predefined1dMesher m = new Predefined1dMesher(x);
        assertTrue("dplus at last index should be NaN",  Double.isNaN(m.dplus(x.length - 1)));
        assertTrue("dminus at first index should be NaN", Double.isNaN(m.dminus(0)));
    }

    @Test
    public void defensiveCopyOfInput() {
        final double[] x = {0.0, 1.0, 2.0};
        final Predefined1dMesher m = new Predefined1dMesher(x);
        // Mutate caller's array post-construction; mesher must not see the change.
        x[1] = 99.0;
        assertEquals(1.0, m.location(1), TIGHT);
    }
}

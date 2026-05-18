/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5e.5b-CFC-d-216 — Concentrating1dMesher multi-cPoint ctor tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.methods.finitedifferences.meshers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher.CPointSpec;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Smoke tests for the multi-cPoint constructor variant of
 * {@link Concentrating1dMesher} (Phase 5e.5b-CFC-d-216 infra port).
 *
 * <p>Validates structural invariants every mesh produced by the
 * ODE-integration constructor must satisfy:
 * <ul>
 *   <li>Endpoints match {@code start} and {@code end} exactly.</li>
 *   <li>Mesh is strictly monotone increasing.</li>
 *   <li>{@code dplus[i]} and {@code dminus[i+1]} match the adjacent
 *       location difference; sentinel {@code NaN} at the ends.</li>
 *   <li>When {@code requireCPoint} is {@code true} for an interior critical
 *       point, that point is an exact grid node.</li>
 * </ul>
 */
public class Concentrating1dMesherTest {

    private static final double TIGHT = 1.0e-12;
    /** Loose tolerance for nodes returned by the Brent-pinned cPoints. */
    private static final double PIN = 1.0e-6;

    public Concentrating1dMesherTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static List<CPointSpec> spec(final CPointSpec... s) {
        return new ArrayList<CPointSpec>(Arrays.asList(s));
    }

    private static void assertMonotoneAndSpacing(final Concentrating1dMesher m) {
        final int n = m.size();
        for (int i = 1; i < n; ++i) {
            assertTrue("mesh must be monotone at i=" + i
                    + " (loc[i-1]=" + m.location(i - 1)
                    + ", loc[i]=" + m.location(i) + ")",
                    m.location(i) > m.location(i - 1));
        }
        for (int i = 0; i < n - 1; ++i) {
            final double expected = m.location(i + 1) - m.location(i);
            assertEquals("dplus[" + i + "]", expected, m.dplus(i), TIGHT);
            assertEquals("dminus[" + (i + 1) + "]", expected, m.dminus(i + 1), TIGHT);
        }
        assertTrue("dplus[size-1] sentinel", Double.isNaN(m.dplus(n - 1)));
        assertTrue("dminus[0] sentinel", Double.isNaN(m.dminus(0)));
    }

    /** A single interior cPoint with requireCPoint=true must land on a node. */
    @Test
    public void singlePointPinned() {
        final double start = -2.0;
        final double end   = 4.0;
        final int    size  = 41;
        final List<CPointSpec> cps = spec(new CPointSpec(1.0, 0.05, true));

        final Concentrating1dMesher m = new Concentrating1dMesher(start, end, size, cps);

        assertEquals(start, m.location(0), TIGHT);
        assertEquals(end,   m.location(size - 1), TIGHT);
        assertMonotoneAndSpacing(m);

        // verify the critical point lands on (or very close to) a grid node
        double minDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < size; ++i) {
            minDist = Math.min(minDist, Math.abs(m.location(i) - 1.0));
        }
        assertTrue("cPoint=1.0 must be pinned to a grid node (min dist=" + minDist + ")",
                minDist < PIN);
    }

    /**
     * Two interior critical points pinned. Both must be grid nodes and the
     * mesh must remain monotone.
     */
    @Test
    public void twoPointsPinned() {
        final double start = 0.0;
        final double end   = 10.0;
        final int    size  = 81;
        final List<CPointSpec> cps = spec(
                new CPointSpec(2.0, 0.05, true),
                new CPointSpec(7.0, 0.05, true));

        final Concentrating1dMesher m = new Concentrating1dMesher(start, end, size, cps);

        assertEquals(start, m.location(0), TIGHT);
        assertEquals(end,   m.location(size - 1), TIGHT);
        assertMonotoneAndSpacing(m);

        for (final double target : new double[] { 2.0, 7.0 }) {
            double minDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < size; ++i) {
                minDist = Math.min(minDist, Math.abs(m.location(i) - target));
            }
            assertTrue("cPoint=" + target + " must be pinned (min dist=" + minDist + ")",
                    minDist < PIN);
        }
    }

    /**
     * Without {@code requireCPoint}, the multi-cPoint variant still produces a
     * valid monotone mesh with the right endpoints.
     */
    @Test
    public void twoPointsNoPin() {
        final double start = -1.0;
        final double end   = 5.0;
        final int    size  = 51;
        final List<CPointSpec> cps = spec(
                new CPointSpec(0.5, 0.1, false),
                new CPointSpec(3.0, 0.1, false));

        final Concentrating1dMesher m = new Concentrating1dMesher(start, end, size, cps);

        assertEquals(start, m.location(0), TIGHT);
        assertEquals(end,   m.location(size - 1), TIGHT);
        assertMonotoneAndSpacing(m);
    }

    /**
     * Empty cPoints list must be rejected (mirrors C++ which would divide by
     * zero in the {@code aInit} averaging step).
     */
    @Test
    public void rejectsEmptyCPoints() {
        try {
            new Concentrating1dMesher(0.0, 1.0, 11,
                    new ArrayList<CPointSpec>());
            fail("expected IllegalArgumentException on empty cPoints");
        } catch (final IllegalArgumentException expected) {
            // expected
        } catch (final RuntimeException expected) {
            // QL.require throws RuntimeException — also acceptable
        }
    }
}

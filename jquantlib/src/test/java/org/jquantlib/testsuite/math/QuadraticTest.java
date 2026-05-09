/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.math.Quadratic;
import org.junit.Test;

/**
 * Tests for {@link Quadratic} — Phase 3j L0.3.
 */
public class QuadraticTest {

    public QuadraticTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-14;

    /** x^2 - 5x + 6 = 0 → roots 2 and 3. */
    @Test
    public void testRealRoots() {
        final Quadratic q = new Quadratic(1.0, -5.0, 6.0);
        // discriminant = 25 - 24 = 1
        assertEquals(1.0, q.discriminant(), TOL);
        // turningPoint = 5/2 = 2.5
        assertEquals(2.5, q.turningPoint(), TOL);
        // valueAtTurningPoint = 2.5*2.5 - 5*2.5 + 6 = -0.25
        assertEquals(-0.25, q.valueAtTurningPoint(), TOL);

        final double[] out = new double[2];
        assertTrue(q.roots(out));
        assertEquals(2.0, out[0], TOL);
        assertEquals(3.0, out[1], TOL);
    }

    /** x^2 + x + 1 → no real roots. */
    @Test
    public void testNoRealRoots() {
        final Quadratic q = new Quadratic(1.0, 1.0, 1.0);
        // discriminant = 1 - 4 = -3
        assertEquals(-3.0, q.discriminant(), TOL);
        final double[] out = new double[2];
        assertFalse(q.roots(out));
        // both filled with turning point = -0.5
        assertEquals(-0.5, out[0], TOL);
        assertEquals(-0.5, out[1], TOL);
    }

    /** Verify apply(x) for known x. */
    @Test
    public void testApply() {
        final Quadratic q = new Quadratic(2.0, 3.0, 1.0);
        // f(0)=1, f(1)=6, f(-1)=0, f(2)=15
        assertEquals(1.0, q.apply(0.0), TOL);
        assertEquals(6.0, q.apply(1.0), TOL);
        assertEquals(0.0, q.apply(-1.0), TOL);
        assertEquals(15.0, q.apply(2.0), TOL);
    }
}

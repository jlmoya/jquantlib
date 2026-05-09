/*
 Copyright (C) 2026 Jose Moya

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.
 */

package org.jquantlib.testsuite.math.matrixutilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.BasisIncompleteOrdered;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Test;

/**
 * Tests for {@link BasisIncompleteOrdered} — Phase 3j Track B align.
 */
public class BasisIncompleteOrderedTest {

    public BasisIncompleteOrderedTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    /** Verify e1, e2 in 2D produce expected orthonormal basis. */
    @Test
    public void testStandardBasis2D() {
        final BasisIncompleteOrdered b = new BasisIncompleteOrdered(2);
        assertTrue(b.addVector(new double[]{1.0, 0.0}));
        assertTrue(b.addVector(new double[]{0.0, 1.0}));
        assertEquals(2, b.basisSize());
        // adding a third linearly-dependent vector must fail
        assertFalse(b.addVector(new double[]{1.0, 1.0}));

        final Matrix m = b.getBasisAsRowsInMatrix();
        assertEquals(2, m.rows());
        assertEquals(2, m.columns());
        assertEquals(1.0, m.get(0, 0), TOL);
        assertEquals(0.0, m.get(0, 1), TOL);
        assertEquals(0.0, m.get(1, 0), TOL);
        assertEquals(1.0, m.get(1, 1), TOL);
    }

    /** Adding zero vector must return false. */
    @Test
    public void testZeroVectorRejected() {
        final BasisIncompleteOrdered b = new BasisIncompleteOrdered(3);
        assertFalse(b.addVector(new double[]{0.0, 0.0, 0.0}));
        assertEquals(0, b.basisSize());
    }

    /** Verify Gram-Schmidt orthonormalization on linearly independent vectors. */
    @Test
    public void testGramSchmidt() {
        final BasisIncompleteOrdered b = new BasisIncompleteOrdered(3);
        assertTrue(b.addVector(new double[]{1.0, 0.0, 0.0}));
        // (1,1,0) has projection (1,0,0); orthogonal part = (0,1,0) → normalized
        assertTrue(b.addVector(new double[]{1.0, 1.0, 0.0}));
        final Matrix m = b.getBasisAsRowsInMatrix();
        assertEquals(2, m.rows());
        assertEquals(0.0, m.get(1, 0), TOL);
        assertEquals(1.0, m.get(1, 1), TOL);
        assertEquals(0.0, m.get(1, 2), TOL);
    }

    /** Dimension mismatch must throw. */
    @Test(expected = RuntimeException.class)
    public void testDimensionMismatch() {
        final BasisIncompleteOrdered b = new BasisIncompleteOrdered(2);
        b.addVector(new double[]{1.0, 2.0, 3.0});
    }
}

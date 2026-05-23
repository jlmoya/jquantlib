/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.NonhomogeneousBoundaryConstraint;
import org.junit.Test;

/**
 * Tests for {@link NonhomogeneousBoundaryConstraint}.
 */
public class NonhomogeneousBoundaryConstraintTest {

    @Test
    public void testWithinBounds() {
        final Array lo = new Array(new double[] { 0.0, -1.0, 2.0 });
        final Array hi = new Array(new double[] { 1.0, 1.0, 3.0 });
        final NonhomogeneousBoundaryConstraint c = new NonhomogeneousBoundaryConstraint(lo, hi);
        assertTrue(c.test(new Array(new double[] { 0.5, 0.0, 2.5 })));
        assertTrue(c.test(new Array(new double[] { 0.0, -1.0, 2.0 }))); // boundary
        assertTrue(c.test(new Array(new double[] { 1.0, 1.0, 3.0 })));  // boundary
    }

    @Test
    public void testOutsideBounds() {
        final Array lo = new Array(new double[] { 0.0, -1.0, 2.0 });
        final Array hi = new Array(new double[] { 1.0, 1.0, 3.0 });
        final NonhomogeneousBoundaryConstraint c = new NonhomogeneousBoundaryConstraint(lo, hi);
        assertFalse(c.test(new Array(new double[] { -0.1, 0.0, 2.5 })));
        assertFalse(c.test(new Array(new double[] { 0.5, 2.0, 2.5 })));
        assertFalse(c.test(new Array(new double[] { 0.5, 0.0, 3.5 })));
    }

    @Test
    public void testBoundsAccessors() {
        final Array lo = new Array(new double[] { 0.0, -1.0, 2.0 });
        final Array hi = new Array(new double[] { 1.0, 1.0, 3.0 });
        final NonhomogeneousBoundaryConstraint c = new NonhomogeneousBoundaryConstraint(lo, hi);
        final Array p = new Array(new double[] { 0.5, 0.0, 2.5 });
        final Array ub = c.upperBound(p);
        final Array lb = c.lowerBound(p);
        assertEquals(3, ub.size());
        for ( int i = 0; i < 3; ++i ) {
            assertEquals(hi.get(i), ub.get(i), 0.0);
            assertEquals(lo.get(i), lb.get(i), 0.0);
        }
    }
}

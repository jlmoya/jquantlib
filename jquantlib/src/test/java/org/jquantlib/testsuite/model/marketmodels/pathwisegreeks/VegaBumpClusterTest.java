/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C C.10 test.

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

package org.jquantlib.testsuite.model.marketmodels.pathwisegreeks;

import org.jquantlib.model.marketmodels.pathwisegreeks.VegaBumpCluster;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link VegaBumpCluster} (Phase 3k Track C C.10).
 *
 * <p>Verifies the half-open range constructor preconditions and the
 * doesIntersect predicate; full {@link
 * org.jquantlib.model.marketmodels.pathwisegreeks.VegaBumpCollection} tests
 * are deferred until a small MarketModel can be cheaply built (Phase 3l).
 */
public class VegaBumpClusterTest {

    @Test
    public void testNonIntersectingFactorRanges() {
        final VegaBumpCluster a = new VegaBumpCluster(0, 1, 0, 5, 0, 5);
        final VegaBumpCluster b = new VegaBumpCluster(1, 2, 0, 5, 0, 5);
        Assert.assertFalse(a.doesIntersect(b));
        Assert.assertFalse(b.doesIntersect(a));
    }

    @Test
    public void testIntersectingFactorRanges() {
        final VegaBumpCluster a = new VegaBumpCluster(0, 2, 0, 5, 0, 5);
        final VegaBumpCluster b = new VegaBumpCluster(1, 3, 0, 5, 0, 5);
        Assert.assertTrue(a.doesIntersect(b));
        Assert.assertTrue(b.doesIntersect(a));
    }

    @Test
    public void testNonIntersectingRateOrStep() {
        final VegaBumpCluster a = new VegaBumpCluster(0, 1, 0, 2, 0, 2);
        final VegaBumpCluster b = new VegaBumpCluster(0, 1, 2, 4, 0, 2);
        Assert.assertFalse(a.doesIntersect(b));

        final VegaBumpCluster c = new VegaBumpCluster(0, 1, 0, 2, 2, 4);
        Assert.assertFalse(a.doesIntersect(c));
    }

    @Test(expected = RuntimeException.class)
    public void testRejectsEmptyFactorRange() {
        new VegaBumpCluster(2, 2, 0, 1, 0, 1);
    }

    @Test
    public void testAccessors() {
        final VegaBumpCluster c = new VegaBumpCluster(1, 3, 5, 10, 7, 9);
        Assert.assertEquals(1, c.factorBegin());
        Assert.assertEquals(3, c.factorEnd());
        Assert.assertEquals(5, c.rateBegin());
        Assert.assertEquals(10, c.rateEnd());
        Assert.assertEquals(7, c.stepBegin());
        Assert.assertEquals(9, c.stepEnd());
    }
}

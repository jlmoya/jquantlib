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

 JQuantLib is based on QuantLib. http://quantlib.org/
 */

package org.jquantlib.testsuite.math;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.PascalTriangle;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link PascalTriangle} — faithful port of
 * {@code ql/math/pascaltriangle.{hpp,cpp}} from QuantLib v1.42.1.
 *
 * @author Jose Moya
 */
public class PascalTriangleTest {

    @Test
    public void testBootstrapRows() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        Assert.assertEquals(List.of(1L), PascalTriangle.get(0));
        Assert.assertEquals(List.of(1L, 1L), PascalTriangle.get(1));
        Assert.assertEquals(List.of(1L, 2L, 1L), PascalTriangle.get(2));
        Assert.assertEquals(List.of(1L, 3L, 3L, 1L), PascalTriangle.get(3));
    }

    @Test
    public void testRow5() {
        // C(5,k) = 1,5,10,10,5,1
        Assert.assertEquals(List.of(1L, 5L, 10L, 10L, 5L, 1L), PascalTriangle.get(5));
    }

    @Test
    public void testRow10() {
        // C(10,k) = 1,10,45,120,210,252,210,120,45,10,1
        Assert.assertEquals(
                List.of(1L, 10L, 45L, 120L, 210L, 252L, 210L, 120L, 45L, 10L, 1L),
                PascalTriangle.get(10));
    }

    @Test
    public void testRecurrenceMonotonicGrowth() {
        // Verify Pascal's rule: C(n,k) = C(n-1,k-1) + C(n-1,k) for several rows.
        for (int n = 1; n <= 12; ++n) {
            final List<Long> prev = PascalTriangle.get(n - 1);
            final List<Long> curr = PascalTriangle.get(n);
            Assert.assertEquals(n + 1, curr.size());
            Assert.assertEquals(1L, (long) curr.get(0));
            Assert.assertEquals(1L, (long) curr.get(n));
            for (int k = 1; k < n; ++k) {
                final long expected = prev.get(k - 1) + prev.get(k);
                Assert.assertEquals("Pascal at n=" + n + ", k=" + k,
                        expected, (long) curr.get(k));
            }
        }
    }
}

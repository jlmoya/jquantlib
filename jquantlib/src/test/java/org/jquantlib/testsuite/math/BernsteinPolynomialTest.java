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

import org.jquantlib.QL;
import org.jquantlib.math.BernsteinPolynomial;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link BernsteinPolynomial} — faithful port of
 * {@code ql/math/bernsteinpolynomial.{hpp,cpp}} from QuantLib v1.42.1.
 *
 * <p>Cross-validated against the closed-form definition
 * {@code B_{i,n}(x) = C(n,i) x^i (1-x)^(n-i)}.
 *
 * @author Jose Moya
 */
public class BernsteinPolynomialTest {

    private static final double TIGHT = 1e-12;

    @Test
    public void testZerothOrder() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
        // B_{0,0}(x) = 1 for any x.
        Assert.assertEquals(1.0, BernsteinPolynomial.get(0, 0, 0.0), TIGHT);
        Assert.assertEquals(1.0, BernsteinPolynomial.get(0, 0, 0.5), TIGHT);
        Assert.assertEquals(1.0, BernsteinPolynomial.get(0, 0, 1.0), TIGHT);
    }

    @Test
    public void testLinearBasis() {
        // n=1: B_{0,1}=1-x, B_{1,1}=x.
        Assert.assertEquals(0.7, BernsteinPolynomial.get(0, 1, 0.3), TIGHT);
        Assert.assertEquals(0.3, BernsteinPolynomial.get(1, 1, 0.3), TIGHT);
    }

    @Test
    public void testCubicBasisAtHalf() {
        // For x=0.5, n=3: each basis = C(3,i) * 0.5^3 = {1,3,3,1}/8.
        Assert.assertEquals(0.125, BernsteinPolynomial.get(0, 3, 0.5), TIGHT);
        Assert.assertEquals(0.375, BernsteinPolynomial.get(1, 3, 0.5), TIGHT);
        Assert.assertEquals(0.375, BernsteinPolynomial.get(2, 3, 0.5), TIGHT);
        Assert.assertEquals(0.125, BernsteinPolynomial.get(3, 3, 0.5), TIGHT);
    }

    @Test
    public void testPartitionOfUnity() {
        // sum_{i=0..n} B_{i,n}(x) = 1 for all x in [0,1].
        final int n = 6;
        for (final double x : new double[]{0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0}) {
            double sum = 0.0;
            for (int i = 0; i <= n; ++i) {
                sum += BernsteinPolynomial.get(i, n, x);
            }
            Assert.assertEquals("partition-of-unity at x=" + x, 1.0, sum, TIGHT);
        }
    }
}

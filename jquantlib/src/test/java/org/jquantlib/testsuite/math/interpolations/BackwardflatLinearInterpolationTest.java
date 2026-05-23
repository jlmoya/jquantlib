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

package org.jquantlib.testsuite.math.interpolations;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.BackwardflatLinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.junit.Test;

/**
 * Tests for {@link BackwardflatLinearInterpolation} cross-validated against C++
 * {@code BackwardflatLinearInterpolation} in
 * {@code ql/math/interpolations/backwardflatlinearinterpolation.hpp} (v1.42.1).
 */
public class BackwardflatLinearInterpolationTest {

    private static final double TIGHT = 1.0e-12;

    public BackwardflatLinearInterpolationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testValueOnSimpleGrid() {
        // 3x3 grid: x = {1, 2, 3}, y = {0, 1, 2}
        // z[j][i]:
        //   y=0:  10, 20, 30
        //   y=1:  11, 21, 31
        //   y=2:  12, 22, 32
        final Array vx = new Array(new double[] { 1.0, 2.0, 3.0 });
        final Array vy = new Array(new double[] { 0.0, 1.0, 2.0 });
        final Matrix mz = new Matrix(new double[][] {
                { 10.0, 20.0, 30.0 },
                { 11.0, 21.0, 31.0 },
                { 12.0, 22.0, 32.0 } });
        final BackwardflatLinearInterpolation bfl = new BackwardflatLinearInterpolation(vx, vy, mz);

        // x=1.5 is in cell i=0 (between x[0]=1, x[1]=2). x != x[0], so z = z[j][i+1] = z[j][1].
        // y=0.5 -> j=0, u=0.5: result = 0.5*z[0][1] + 0.5*z[1][1] = 0.5*20 + 0.5*21 = 20.5
        assertEquals(20.5, bfl.op(1.5, 0.5), TIGHT);

        // x exactly equals x[0]: z=z[j][0] branch via leftmost
        // x=1.0 (== xBegin[0]) hits the "x <= xBegin[0]" branch.
        // y=0.5: u=0.5 -> 0.5*z[0][0] + 0.5*z[1][0] = 0.5*10 + 0.5*11 = 10.5
        assertEquals(10.5, bfl.op(1.0, 0.5), TIGHT);

        // x exactly equals x[1]=2.0 (locateX caps at i==1 boundary), x == vx[i]: use z[j][i].
        // y=1.5 -> j=1, u=0.5: result = 0.5*z[1][1] + 0.5*z[2][1] = 0.5*21 + 0.5*22 = 21.5
        assertEquals(21.5, bfl.op(2.0, 1.5), TIGHT);
    }
}

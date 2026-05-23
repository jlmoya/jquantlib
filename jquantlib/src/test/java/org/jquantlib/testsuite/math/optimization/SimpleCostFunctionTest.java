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

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.SimpleCostFunction;
import org.junit.Test;

/**
 * Tests for {@link SimpleCostFunction}.
 */
public class SimpleCostFunctionTest {

    @Test
    public void testIdentity() {
        // values(x) = x  → value(x) = sqrt(sum(x_i^2)/N) (RMS via parent CostFunction)
        final SimpleCostFunction f = new SimpleCostFunction(x -> x);
        final Array x = new Array(new double[] { 3.0, 4.0 });
        // RMS of [3,4] = sqrt((9+16)/2) = sqrt(12.5) = 3.5355339059327378
        assertEquals(Math.sqrt(12.5), f.value(x), 1e-15);
        final Array v = f.values(x);
        assertEquals(2, v.size());
        assertEquals(3.0, v.get(0), 0.0);
        assertEquals(4.0, v.get(1), 0.0);
    }

    @Test
    public void testQuadratic() {
        // values(x) = [x_0^2, x_1^2 - 1, x_2 + 1]; trivial smoke test of lambda wiring
        final SimpleCostFunction f = new SimpleCostFunction(x -> {
            final double[] out = new double[x.size()];
            out[0] = x.get(0) * x.get(0);
            out[1] = x.get(1) * x.get(1) - 1.0;
            out[2] = x.get(2) + 1.0;
            return new Array(out);
        });
        final Array x = new Array(new double[] { 2.0, 3.0, -2.0 });
        final Array v = f.values(x);
        assertEquals(4.0, v.get(0), 0.0);
        assertEquals(8.0, v.get(1), 0.0);
        assertEquals(-1.0, v.get(2), 0.0);
        // RMS of [4, 8, -1] = sqrt((16+64+1)/3) = sqrt(81/3) = sqrt(27)
        assertEquals(Math.sqrt(27.0), f.value(x), 1e-15);
    }
}

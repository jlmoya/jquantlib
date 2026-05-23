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

package org.jquantlib.testsuite.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.jquantlib.QL;
import org.jquantlib.math.AbcdMathFunction;
import org.junit.Test;

/**
 * Tests for {@link AbcdMathFunction}, cross-validated against the closed-form formulas
 * defined by C++ {@code ql/math/abcdmathfunction.cpp} (v1.42.1).
 */
public class AbcdMathFunctionTest {

    private static final double TIGHT = 1.0e-12;

    public AbcdMathFunctionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testFunctionValue() {
        // a=0.1, b=0.5, c=2.0, d=0.3
        final AbcdMathFunction f = new AbcdMathFunction(0.1, 0.5, 2.0, 0.3);

        // f(0) = (0.1 + 0)*1 + 0.3 = 0.4
        assertEquals(0.4, f.op(0.0), TIGHT);

        // f(1) = (0.1 + 0.5)*exp(-2) + 0.3 = 0.6 * 0.135335283236612691893... + 0.3
        final double f1 = 0.6 * Math.exp(-2.0) + 0.3;
        assertEquals(f1, f.op(1.0), TIGHT);

        // f(t<0) = 0
        assertEquals(0.0, f.op(-0.5), TIGHT);

        // f(+large) -> d
        assertEquals(0.3, f.op(1000.0), TIGHT);
    }

    @Test
    public void testDerivative() {
        // a=0.1, b=0.5, c=2.0, d=0.3
        // f'(t) = ((b - c*a) + (-c*b)*t)*exp(-c*t) = ((0.5-0.2) + (-1)*t)*exp(-2t) = (0.3 - t)*exp(-2t)
        final AbcdMathFunction f = new AbcdMathFunction(0.1, 0.5, 2.0, 0.3);
        assertEquals(0.3, f.derivative(0.0), TIGHT);
        assertEquals((0.3 - 1.0) * Math.exp(-2.0), f.derivative(1.0), TIGHT);
        assertEquals(0.0, f.derivative(-1.0), TIGHT);
    }

    @Test
    public void testPrimitive() {
        // a=0.1, b=0.5, c=2.0, d=0.3
        // primitive at t=0:
        //   pa = -(a + b/c)/c = -(0.1 + 0.25)/2 = -0.175
        //   pb = -b/c = -0.25
        //   primitive(0) = pa*1 + 0 + d*0 = -0.175
        final AbcdMathFunction f = new AbcdMathFunction(0.1, 0.5, 2.0, 0.3);
        assertEquals(-0.175, f.primitive(0.0), TIGHT);

        // primitive(1) = (-0.175 - 0.25*1)*exp(-2) + 0.3*1 = (-0.425)*exp(-2) + 0.3
        final double p1 = (-0.425) * Math.exp(-2.0) + 0.3;
        assertEquals(p1, f.primitive(1.0), TIGHT);
    }

    @Test
    public void testDefiniteIntegralEqualsRiemann() {
        // numerical integration sanity check
        final AbcdMathFunction f = new AbcdMathFunction(0.1, 0.5, 2.0, 0.3);
        final double t1 = 0.5, t2 = 3.5;
        final int n = 200000;
        final double dt = (t2 - t1) / n;
        double sum = 0.0;
        for (int i = 0; i < n; ++i) {
            final double tm = t1 + (i + 0.5) * dt;
            sum += f.op(tm) * dt;
        }
        assertEquals(sum, f.definiteIntegral(t1, t2), 1.0e-7);
    }

    @Test
    public void testMaximumLocation() {
        // a=0.1, b=0.5, c=2.0 → max at 1/c - a/b = 0.5 - 0.2 = 0.3
        final AbcdMathFunction f = new AbcdMathFunction(0.1, 0.5, 2.0, 0.3);
        assertEquals(0.3, f.maximumLocation(), TIGHT);
        // f(0.3) = (0.1 + 0.15)*exp(-0.6) + 0.3 = 0.25*exp(-0.6) + 0.3
        assertEquals(0.25 * Math.exp(-0.6) + 0.3, f.maximumValue(), TIGHT);

        // b=0, a>=0: max at 0
        final AbcdMathFunction g = new AbcdMathFunction(0.2, 0.0, 1.0, 0.1);
        assertEquals(0.0, g.maximumLocation(), TIGHT);
    }

    @Test
    public void testCoefficientsAndDerivativeCoefficients() {
        final AbcdMathFunction f = new AbcdMathFunction(0.1, 0.5, 2.0, 0.3);
        final double[] abcd = f.coefficients();
        assertEquals(0.1, abcd[0], TIGHT);
        assertEquals(0.5, abcd[1], TIGHT);
        assertEquals(2.0, abcd[2], TIGHT);
        assertEquals(0.3, abcd[3], TIGHT);

        // dabcd = [b - c*a, -c*b, c, 0] = [0.3, -1.0, 2.0, 0.0]
        final double[] dabcd = f.derivativeCoefficients();
        assertEquals(0.3, dabcd[0], TIGHT);
        assertEquals(-1.0, dabcd[1], TIGHT);
        assertEquals(2.0, dabcd[2], TIGHT);
        assertEquals(0.0, dabcd[3], TIGHT);
    }

    @Test
    public void testValidateRejectsBadCoefficients() {
        // c <= 0 must fail
        assertThrows(Throwable.class, () -> new AbcdMathFunction(0.1, 0.5, -2.0, 0.3));
        // d < 0 must fail
        assertThrows(Throwable.class, () -> new AbcdMathFunction(0.1, 0.5, 2.0, -0.3));
        // a+d < 0 must fail
        assertThrows(Throwable.class, () -> new AbcdMathFunction(-1.0, 0.5, 2.0, 0.3));
    }

    @Test
    public void testFourArgArrayConstructor() {
        final AbcdMathFunction f = new AbcdMathFunction(new double[] { 0.1, 0.5, 2.0, 0.3 });
        assertEquals(0.4, f.op(0.0), TIGHT);
        assertEquals(0.1, f.a(), TIGHT);
        assertEquals(0.5, f.b(), TIGHT);
        assertEquals(2.0, f.c(), TIGHT);
        assertEquals(0.3, f.d(), TIGHT);
    }
}

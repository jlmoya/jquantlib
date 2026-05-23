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
import org.jquantlib.math.PolynomialFunction;
import org.junit.Test;

/**
 * Tests for {@link PolynomialFunction}, cross-validated against C++
 * {@code ql/math/polynomialmathfunction.cpp} (v1.42.1) closed-form formulas.
 */
public class PolynomialFunctionTest {

    private static final double TIGHT = 1.0e-12;

    public PolynomialFunctionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testValueDerivativePrimitive() {
        // p(t) = 1 + 2t + 3t^2 (coeffs [1, 2, 3])
        final PolynomialFunction p = new PolynomialFunction(new double[] { 1.0, 2.0, 3.0 });
        assertEquals(3, p.order());

        // p(0) = 1, p(1) = 6, p(2) = 17
        assertEquals(1.0, p.op(0.0), TIGHT);
        assertEquals(6.0, p.op(1.0), TIGHT);
        assertEquals(17.0, p.op(2.0), TIGHT);

        // p'(t) = 2 + 6t
        assertEquals(2.0, p.derivative(0.0), TIGHT);
        assertEquals(8.0, p.derivative(1.0), TIGHT);

        // primitive(t) = t + t^2 + t^3 (with K=0)
        assertEquals(0.0, p.primitive(0.0), TIGHT);
        assertEquals(3.0, p.primitive(1.0), TIGHT);
        assertEquals(2.0 + 4.0 + 8.0, p.primitive(2.0), TIGHT);

        // definite integral [0, 2] = primitive(2) - primitive(0) = 14
        assertEquals(14.0, p.definiteIntegral(0.0, 2.0), TIGHT);
    }

    @Test
    public void testCoefficientsAndDerivativeCoefficients() {
        final PolynomialFunction p = new PolynomialFunction(new double[] { 5.0, 4.0, 3.0, 2.0 });
        final double[] c = p.coefficients();
        assertEquals(5.0, c[0], TIGHT);
        assertEquals(2.0, c[3], TIGHT);
        // derC = [c1*1, c2*2, c3*3] = [4, 6, 6]
        final double[] derC = p.derivativeCoefficients();
        assertEquals(4.0, derC[0], TIGHT);
        assertEquals(6.0, derC[1], TIGHT);
        assertEquals(6.0, derC[2], TIGHT);
        // prC = [c0/1, c1/2, c2/3, c3/4] = [5, 2, 1, 0.5]
        final double[] prC = p.primitiveCoefficients();
        assertEquals(5.0, prC[0], TIGHT);
        assertEquals(2.0, prC[1], TIGHT);
        assertEquals(1.0, prC[2], TIGHT);
        assertEquals(0.5, prC[3], TIGHT);
    }

    @Test
    public void testRejectsEmptyVector() {
        assertThrows(Throwable.class, () -> new PolynomialFunction(new double[0]));
    }

    @Test
    public void testDefiniteIntegralCoefficientsConsistency() {
        // For p(t) = 1 + 2t + 3t^2, definite integral over [t, t+tau] yields a polynomial
        // in t whose evaluation must equal definiteIntegral(t, t+tau).
        final PolynomialFunction p = new PolynomialFunction(new double[] { 1.0, 2.0, 3.0 });
        final double t = 0.5, t2 = 1.7;
        final double[] cdef = p.definiteIntegralCoefficients(t, t2);
        // build a polynomial from cdef and evaluate at t — must equal p.definiteIntegral(t, t2)
        double value = 0.0;
        double tPower = 1.0;
        for (int i = 0; i < cdef.length; ++i) {
            value += cdef[i] * tPower;
            tPower *= t;
        }
        assertEquals(p.definiteIntegral(t, t2), value, 1.0e-10);
    }
}

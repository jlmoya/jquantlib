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

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.ode.AdaptiveRungeKutta;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/ode.cpp (Phase 5a).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods. {@link AdaptiveRungeKutta} in Java
 * supports only the {@code double[]}/Real-vector overload (ode #3 in C++).
 * The 1D real (#1), complex 1D (#2), and complex vector (#4) overloads are
 * Phase 5a.5 carry-forwards. {@code testMatrixExponential} requires
 * {@code QuantLib::Expm} which is not yet ported to Java.
 */
public class OdeTest {

    public OdeTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testAdaptiveRungeKutta() {
        QL.info("Testing adaptive Runge Kutta...");

        final AdaptiveRungeKutta rkReal = new AdaptiveRungeKutta(1e-12, 1e-4, 0.0);
        final double tol3 = 2e-12;

        // f''=-f, f(0)=0, f'(0)=1 — a 2-D ODE for [f, f']
        // matches C++ ode3 + y30 = {0.0, 1.0}; expected solution f(x) = sin(x).
        final AdaptiveRungeKutta.OdeFct ode3 = (t, y) -> new double[] { y[1], -y[0] };
        final double[] y30 = { 0.0, 1.0 };

        for (double x = 0.01; x <= 5.0; x += 0.01) {
            final double[] y3 = rkReal.solve(ode3, y30, 0.0, x);
            final double exact3 = Math.sin(x);
            if (Math.abs(exact3 - y3[0]) > tol3) {
                fail("Error in ode #3: exact solution at x=" + x
                        + " is " + exact3
                        + ", numerical solution is " + y3[0]
                        + " difference " + Math.abs(exact3 - y3[0])
                        + " outside tolerance " + tol3);
            }
        }
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib AdaptiveRungeKutta has no 1D-Real or "
            + "complex overloads (C++ template variants for ode #1, #2, #4). The 2D-Real "
            + "case is exercised by testAdaptiveRungeKutta above.")
    @Test
    public void testAdaptiveRungeKutta1dAndComplex() {
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no Expm matrix-exponential class "
            + "(C++ ql/math/matrixutilities/expm.hpp). Port then enable.")
    @Test
    public void testMatrixExponential() {
    }

    @Ignore("Phase 5a.5 carry-forward — depends on Expm port (see testMatrixExponential).")
    @Test
    public void testMatrixExponentialOfZero() {
    }
}

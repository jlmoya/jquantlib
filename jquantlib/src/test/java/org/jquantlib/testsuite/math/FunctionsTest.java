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
import org.jquantlib.math.Constants;
import org.jquantlib.math.Factorial;
import org.jquantlib.math.distributions.GammaFunction;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/functions.cpp (Phase 5a).
 *
 * <p>6 BOOST_AUTO_TEST_CASE methods. Java exposes
 * {@link Factorial} and {@link GammaFunction} which cover the first
 * three. The remaining three test
 * {@code modifiedBesselFunction_i/_k} (real and complex) and the
 * complex {@code expm1} / {@code log1p} helpers; those are not
 * available in JQuantLib and are {@code @Ignore}-d as Phase 5a.5
 * carry-forwards.
 */
public class FunctionsTest {

    public FunctionsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testFactorial() {
        QL.info("Testing factorial numbers...");

        final Factorial fact = new Factorial();
        double expected = 1.0;
        double calculated = fact.get(0);
        if (calculated != expected) {
            fail("Factorial(0) = " + calculated);
        }

        for (int i = 1; i < 171; ++i) {
            expected *= i;
            calculated = fact.get(i);
            if (Math.abs(calculated - expected) / expected > 1.0e-9) {
                fail("Factorial(" + i + ")"
                        + "\n calculated: " + calculated
                        + "\n   expected: " + expected
                        + "\n rel. error: " + (Math.abs(calculated - expected) / expected));
            }
        }
    }

    @Test
    public void testGammaFunction() {
        QL.info("Testing Gamma function...");

        double expected = 0.0;
        double calculated = new GammaFunction().logValue(1);
        if (Math.abs(calculated) > 1.0e-15) {
            fail("GammaFunction(1)\n"
                    + "    calculated: " + calculated
                    + "\n    expected:   " + expected);
        }

        for (int i = 2; i < 9000; i++) {
            expected += Math.log((double) i);
            calculated = new GammaFunction().logValue((double) (i + 1));
            if (Math.abs(calculated - expected) / expected > 1.0e-9) {
                fail("GammaFunction(" + i + ")"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    rel. error: " + (Math.abs(calculated - expected) / expected));
            }
        }
    }

    @Test
    public void testGammaValues() {
        QL.info("Testing Gamma values...");

        // reference results are calculated with R
        final double[][] tasks = {
            { 0.0001,    9999.422883231624,         1e3 },
            { 1.2,       0.9181687423997607,        1e3 },
            { 7.3,       1271.4236336639089586,     1e3 },
            {-1.1,       9.7148063829028946,        1e3 },
            {-4.001,    -41.6040228304425312,       1e3 },
            {-4.999,    -8.347576090315059,         1e3 },
            {-19.000001, 8.220610833201313e-12,     1e8 },
            {-19.5,      5.811045977502255e-18,     1e3 },
            {-21.000001, 1.957288098276488e-14,     1e8 },
            {-21.5,      1.318444918321553e-20,     1e6 }
        };

        for (final double[] task : tasks) {
            final double x = task[0];
            final double expected = task[1];
            final double calculated = new GammaFunction().value(x);
            final double tol = task[2] * Constants.QL_EPSILON * Math.abs(expected);
            if (Math.abs(calculated - expected) > tol) {
                fail("GammaFunction(" + x + ")"
                        + "\n    calculated: " + calculated
                        + "\n    expected:   " + expected
                        + "\n    rel. error: " + (Math.abs(calculated - expected) / expected));
            }
        }
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no modifiedBesselFunction_i / _k "
            + "(real and complex). Port from C++ ql/math/modifiedbessel.hpp.")
    @Test
    public void testModifiedBesselFunctions() {
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no modifiedBesselFunction_i / _k "
            + "exponentially-weighted variants.")
    @Test
    public void testWeightedModifiedBesselFunctions() {
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no complex expm1 (C++ ql/math/expm1.hpp). "
            + "Port alongside log1p.")
    @Test
    public void testExpm1() {
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no complex log1p. Port from "
            + "C++ ql/math/expm1.hpp.")
    @Test
    public void testLog1p() {
    }
}

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

package org.jquantlib.testsuite.operators;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.TridiagonalOperator;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/operators.cpp (Phase 5a).
 *
 * <p>2 BOOST_AUTO_TEST_CASE methods. {@code testTridiagonal} exercises
 * applyTo + solveFor inverse property (and SOR alternate form, which is
 * commented out in JQuantLib). {@code testConsistency} (DZero/DPlusDMinus)
 * is covered by the existing {@link OperatorTest} class — we keep that
 * authoritative.
 */
public class OperatorsTest {

    public OperatorsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testTridiagonal() {
        QL.info("Testing tridiagonal operator...");

        final int n = 8; // can use 3 for easier debugging

        final TridiagonalOperator T = new TridiagonalOperator(n);
        T.setFirstRow(1.0, 2.0);
        T.setMidRows(0.0, 2.0, 0.0);
        T.setLastRow(2.0, 1.0);

        final Array original = new Array(n).fill(1.0);

        final Array intermediate = T.applyTo(original);

        // Single-arg solveFor(rhs) — must invert applyTo exactly.
        Array result = T.solveFor(intermediate);
        for (int i = 0; i < n; ++i) {
            if (result.get(i) != original.get(i)) {
                fail("\n applyTo + solveFor does not equal identity:"
                        + "\n            original vector: " + original
                        + "\n         transformed vector: " + intermediate
                        + "\n inverse transformed vector: " + result);
            }
        }

        // 2-arg solveFor(rhs, result) — must not alter rhs (when distinct
        // arrays) and must still invert applyTo exactly. Mirrors the C++
        // testTridiagonal block that calls T.solveFor(temp, final).
        Array out = new Array(n).fill(0.0);
        final Array temp = intermediate.clone();
        T.solveFor(temp, out);
        for (int i = 0; i < n; ++i) {
            if (temp.get(i) != intermediate.get(i)) {
                fail("\n solveFor altered rhs:"
                        + "\n            original vector: " + original
                        + "\n         transformed vector: " + intermediate
                        + "\n altered transformed vector: " + temp
                        + "\n inverse transformed vector: " + out);
            }
        }
        for (int i = 0; i < n; ++i) {
            if (out.get(i) != original.get(i)) {
                fail("\n applyTo + solveFor does not equal identity:"
                        + "\n            original vector: " + original
                        + "\n         transformed vector: " + intermediate
                        + "\n inverse transformed vector: " + out);
            }
        }

        // single-arg solveFor again on temp (which should still be
        // intermediate) — also must not alter temp.
        out = T.solveFor(temp);
        for (int i = 0; i < n; ++i) {
            if (temp.get(i) != intermediate.get(i)) {
                fail("\n solveFor altered rhs:"
                        + "\n            original vector: " + original
                        + "\n         transformed vector: " + intermediate
                        + "\n altered transformed vector: " + temp
                        + "\n inverse transformed vector: " + out);
            }
        }
        for (int i = 0; i < n; ++i) {
            if (out.get(i) != original.get(i)) {
                fail("\n applyTo + solveFor does not equal identity:"
                        + "\n            original vector: " + original
                        + "\n         transformed vector: " + intermediate
                        + "\n inverse transformed vector: " + out);
            }
        }
    }

    /**
     * Java port of the SOR block at the end of C++ testTridiagonal
     * (test-suite/operators.cpp, lines ~98-117). Verifies that the SOR
     * iterative solver converges to within the requested tolerance and does
     * not mutate the supplied rhs.
     */
    @Test
    public void testTridiagonalSOR() {
        QL.info("Testing tridiagonal SOR solver...");

        final int n = 8;

        final TridiagonalOperator T = new TridiagonalOperator(n);
        T.setFirstRow(1.0, 2.0);
        T.setMidRows(0.0, 2.0, 0.0);
        T.setLastRow(2.0, 1.0);

        final Array original = new Array(n).fill(1.0);
        final Array intermediate = T.applyTo(original);
        final Array temp = intermediate.clone();

        final double tolerance = 1.0e-9;
        final Array result = T.SOR(temp, tolerance);

        double error = 0.0;
        for (int i = 0; i < n; ++i) {
            final double delta = result.get(i) - original.get(i);
            error += delta * delta;
            if (temp.get(i) != intermediate.get(i)) {
                fail("\n SOR altered rhs:"
                        + "\n            original vector: " + original
                        + "\n         transformed vector: " + intermediate
                        + "\n altered transformed vector: " + temp
                        + "\n inverse transformed vector: " + result);
            }
        }
        if (error > tolerance) {
            fail("\n applyTo + SOR does not equal identity:"
                    + "\n            original vector: " + original
                    + "\n         transformed vector: " + intermediate
                    + "\n inverse transformed vector: " + result
                    + "\n                      error: " + error
                    + "\n                  tolerance: " + tolerance);
        }
    }

    /**
     * The DZero/DPlusDMinus differential-operator consistency test from
     * C++ {@code testConsistency} is already covered by the existing
     * {@link OperatorTest#testConsistency} class. Pointer kept here for
     * traceability.
     */
    @Test
    public void testConsistencyCovered() {
        QL.info("Testing differential operators (covered by existing OperatorTest.testConsistency)...");
        // No-op marker; see OperatorTest for the actual assertions.
    }
}

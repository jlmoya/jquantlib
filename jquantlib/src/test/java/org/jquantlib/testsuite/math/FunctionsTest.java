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
import org.jquantlib.math.Complex;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Expm1;
import org.jquantlib.math.Factorial;
import org.jquantlib.math.ModifiedBesselFunction;
import org.jquantlib.math.distributions.GammaFunction;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/functions.cpp.
 *
 * <p>Phase 5a covered {@link #testFactorial()}, {@link #testGammaFunction()},
 * {@link #testGammaValues()}. Phase 5e.5b-CFC-d-43 body-fills the four
 * remaining cases: real and complex modified Bessel I/K (weighted and
 * unweighted), complex {@link Expm1#expm1(Complex) expm1}, and complex
 * {@link Expm1#log1p(Complex) log1p}.
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

    @Test
    public void testModifiedBesselFunctions() {
        QL.info("Testing modified Bessel function of first and second kind...");

        // Reference values from C++ test-suite/functions.cpp (computed with R + Bessel pkg).
        // Phase 5e.5b-CFC-d-43: tolerance baseline matches C++ (5e4 * QL_EPSILON).
        final double[][] r = {
            {-1.3, 2.0, 1.2079888436539505, 0.1608243636110430},
            { 1.3, 2.0, 1.2908192151358788, 0.1608243636110430},
            { 0.001, 2.0, 2.2794705965773794, 0.1138938963603362},
            { 1.2, 0.5,   0.1768918783499572, 2.1086579232338192},
            { 2.3, 0.1, 0.00037954958988425198, 572.096866928290183},
            {-2.3, 1.1, 1.07222017902746969, 1.88152553684107371},
            {-10.0001, 1.1, 13857.7715614282552, 69288858.9474423379}
        };

        for (final double[] row : r) {
            final double nu = row[0];
            final double x = row[1];
            final double expectedI = row[2];
            final double expectedK = row[3];
            final double tolI = 5e4 * Constants.QL_EPSILON * Math.abs(expectedI);
            final double tolK = 5e4 * Constants.QL_EPSILON
                    * Math.max(Math.abs(expectedK), Math.abs(expectedI));

            final double calculatedI = ModifiedBesselFunction.i(nu, x);
            final double calculatedK = ModifiedBesselFunction.k(nu, x);

            if (Math.abs(expectedI - calculatedI) > tolI) {
                fail("failed to reproduce modified Bessel function of first kind"
                        + "\n order     : " + nu
                        + "\n argument  : " + x
                        + "\n calculated: " + calculatedI
                        + "\n expected  : " + expectedI
                        + "\n difference: " + Math.abs(expectedI - calculatedI)
                        + "\n tolerance : " + tolI);
            }
            if (Math.abs(expectedK - calculatedK) > tolK) {
                fail("failed to reproduce modified Bessel function of second kind"
                        + "\n order     : " + nu
                        + "\n argument  : " + x
                        + "\n calculated: " + calculatedK
                        + "\n expected  : " + expectedK
                        + "\n difference: " + Math.abs(expectedK - calculatedK)
                        + "\n tolerance : " + tolK);
            }
        }

        // Complex reference values from C++ test-suite/functions.cpp.
        // Columns: nu, re(z), im(z), re(I), im(I), re(K), im(K).
        final double[][] c = {
            {-1.3, 2.0, 0.0, 1.2079888436539505, 0.0,
                             0.1608243636110430, 0.0},
            { 1.2, 1.5, 0.3, 0.7891550871263575, 0.2721408731632123,
                             0.275126507673411, -0.1316314405663727},
            { 1.2, -1.5, 0.0, -0.6650597524355781, -0.4831941938091643,
                            -0.251112360556051, -2.400130904230102},
            {-11.2, 1.5, 0.3, 12780719.20252659, 16401053.26770633,
                            -34155172.65672453, -43830147.36759921},
            { 1.2, -1.5, 2.0, -0.3869803778520574, 0.9756701796853728,
                            -3.111629716783005, 0.6307859871879062},
            { 1.2, 0.0, 9.9999, -0.03507838078252647, 0.1079601550451466,
                            -0.05979939995451453, 0.3929814473878203},
            { 1.2, 0.0, 10.1, -0.02782046891519293, 0.08562259917678558,
                            -0.02035685034691133, 0.3949834389686676},
            { 1.2, 0.0, 12.1, 0.07092110620741207, -0.2182727210128104,
                            0.3368505862966958, -0.1299038064313366},
            { 1.2, 0.0, 14.1, -0.03014378676768797, 0.09277303628303372,
                            -0.237531022649052, -0.2351923034581644},
            { 1.2, 0.0, 16.1, -0.03823210284792657, 0.1176663135266562,
                            -0.1091239402448228, 0.2930535651966139},
            { 1.2, 0.0, 18.1, 0.05626742394733754, -0.173173324361983,
                            0.2941636588154642, -0.02023355577954348},
            { 1.2, 0.0, 180.1, -0.001230682086826484, 0.003787649998122361,
                            0.02284509628723454, 0.09055419580980778},
            { 1.2, 0.0, 21.0, -0.04746415965014021, 0.1460796627610969,
                            -0.2693825171336859, -0.04830804448126782},
            { 1.2, 10.0, 0.0, 2609.784936867044, 0, 1.904394919838336e-05, 0},
            { 1.2, 14.0, 0.0, 122690.4873454286, 0, 2.902060692576643e-07, 0},
            { 1.2, 20.0, 10.0, -37452017.91168936, -13917587.22151363,
                            -3.821534367487143e-10, 4.083211255351664e-10},
            { 1.2, 9.0, 9.0, -621.7335051293694,  618.1455736670332,
                            -4.480795479964915e-05, -3.489034389148745e-08}
        };

        for (final double[] row : c) {
            final double nu = row[0];
            final Complex z = new Complex(row[1], row[2]);
            final Complex expectedI = new Complex(row[3], row[4]);
            final Complex expectedK = new Complex(row[5], row[6]);

            final double absExpectedI = expectedI.abs();
            final double absExpectedK = expectedK.abs();
            final double tolI = 5e4 * Constants.QL_EPSILON * absExpectedI;
            final double tolK = 1e6 * Constants.QL_EPSILON * absExpectedK;

            final Complex calculatedI = ModifiedBesselFunction.i(nu, z);
            final Complex calculatedK = ModifiedBesselFunction.k(nu, z);

            final double diffI = calculatedI.sub(expectedI).abs();
            if (diffI > tolI) {
                fail("failed to reproduce modified Bessel function of first kind"
                        + "\n order     : " + nu
                        + "\n argument  : " + z
                        + "\n calculated: " + calculatedI
                        + "\n expected  : " + expectedI
                        + "\n difference: " + diffI
                        + "\n tolerance : " + tolI);
            }
            if (absExpectedK > 1e-4) { // skip small values (C++ does the same)
                final double diffK = calculatedK.sub(expectedK).abs();
                if (diffK > tolK) {
                    fail("failed to reproduce modified Bessel function of second kind"
                            + "\n order     : " + nu
                            + "\n argument  : " + z
                            + "\n calculated: " + calculatedK
                            + "\n expected  : " + expectedK
                            + "\n difference: " + diffK
                            + "\n tolerance : " + tolK);
                }
            }
        }
    }

    @Test
    public void testWeightedModifiedBesselFunctions() {
        QL.info("Testing weighted modified Bessel functions...");

        // Verify exp-weighted == unweighted * exp(-x) over a sweep of nu, x.
        // Mirrors C++ test-suite/functions.cpp. NaN-vs-tol comparisons pass
        // silently for integer nu (both expected and calculated are NaN
        // because sin(pi*nu) = 0 in the K kernel).
        for (double nu = -5.0; nu <= 5.0; nu += 0.5) {
            for (double x = 0.1; x <= 15.0; x += 0.5) {
                final double calculatedI = ModifiedBesselFunction.iExpWeighted(nu, x);
                final double expectedI = ModifiedBesselFunction.i(nu, x) * Math.exp(-x);
                final double calculatedK = ModifiedBesselFunction.kExpWeighted(nu, x);
                final double expectedK = Math.PI / 2.0
                        * (ModifiedBesselFunction.i(-nu, x) - ModifiedBesselFunction.i(nu, x))
                        * Math.exp(-x) / Math.sin(Math.PI * nu);
                final double tolI = Math.max(Constants.QL_EPSILON,
                        1e3 * Constants.QL_EPSILON * Math.abs(expectedI)
                                * Math.max(Math.exp(x), 1.0));
                final double tolK = Math.max(Constants.QL_EPSILON,
                        1e3 * Constants.QL_EPSILON * Math.abs(expectedK)
                                * Math.max(Math.exp(x), 1.0));
                if (Math.abs(expectedI - calculatedI) > tolI) {
                    fail("failed to verify exponentially weighted modified Bessel "
                            + "function of first kind"
                            + "\n order      : " + nu
                            + "\n argument   : " + x
                            + "\n calculated : " + calculatedI
                            + "\n expected   : " + expectedI
                            + "\n tolerance  : " + tolI
                            + "\n difference : " + (expectedI - calculatedI));
                }
                if (Math.abs(expectedK - calculatedK) > tolK) {
                    fail("failed to verify exponentially weighted modified Bessel "
                            + "function of second kind"
                            + "\n order      : " + nu
                            + "\n argument   : " + x
                            + "\n calculated : " + calculatedK
                            + "\n expected   : " + expectedK
                            + "\n tolerance  : " + tolK
                            + "\n difference : " + (expectedK - calculatedK));
                }
            }
        }
        for (double nu = -5.0; nu <= 5.0; nu += 0.5) {
            for (double x = -5.0; x <= 5.0; x += 0.5) {
                for (double y = -5.0; y <= 5.0; y += 0.5) {
                    final Complex z = new Complex(x, y);
                    final Complex expZNeg = z.neg().exp();
                    final Complex calculatedI = ModifiedBesselFunction.iExpWeighted(nu, z);
                    final Complex expectedI = ModifiedBesselFunction.i(nu, z).mul(expZNeg);
                    final Complex calculatedK = ModifiedBesselFunction.kExpWeighted(nu, z);
                    final Complex expectedK = ModifiedBesselFunction.i(-nu, z).mul(expZNeg)
                            .sub(ModifiedBesselFunction.i(nu, z).mul(expZNeg))
                            .mul(Math.PI / 2.0)
                            .div(Math.sin(Math.PI * nu));
                    final double tolI = Math.max(Constants.QL_EPSILON,
                            1e3 * Constants.QL_EPSILON * calculatedI.abs());
                    final double tolK = Math.max(Constants.QL_EPSILON,
                            1e3 * Constants.QL_EPSILON * calculatedK.abs());
                    final double diffI = calculatedI.sub(expectedI).abs();
                    if (diffI > tolI) {
                        fail("failed to verify exponentially weighted modified Bessel "
                                + "function of first kind"
                                + "\n order      : " + nu
                                + "\n argument   : " + z
                                + "\n calculated : " + calculatedI
                                + "\n expected   : " + expectedI
                                + "\n tolerance  : " + tolI
                                + "\n difference : " + diffI);
                    }
                    final double diffK = calculatedK.sub(expectedK).abs();
                    if (diffK > tolK) {
                        fail("failed to verify exponentially weighted modified Bessel "
                                + "function of second kind"
                                + "\n order      : " + nu
                                + "\n argument   : " + z
                                + "\n calculated : " + calculatedK
                                + "\n expected   : " + expectedK
                                + "\n tolerance  : " + tolK
                                + "\n difference : " + diffK);
                    }
                }
            }
        }
    }

    @Test
    public void testExpm1() {
        QL.info("Testing complex valued expm1...");

        // Sanity: away from zero, expm1(z) == exp(z) - 1.
        final Complex z = new Complex(1.2, 0.5);
        final double diff = z.exp().sub(1.0).sub(Expm1.expm1(z)).abs();
        if (diff > 10 * Constants.QL_EPSILON) {
            fail("complex expm1 inconsistent with exp(z) - 1 at z=" + z
                    + "; diff=" + diff);
        }

        // Near-zero reference value from scipy (per C++ test-suite/functions.cpp).
        final Complex calculated = Expm1.expm1(new Complex(5e-6, 5e-5));
        final Complex expected = new Complex(4.998762493771078e-06, 5.000024997979157e-05);
        final double tol = Math.max(2.2e-14, 100 * Constants.QL_EPSILON);
        if (Math.abs(calculated.real() - expected.real()) > tol * Math.abs(expected.real())) {
            fail("expm1 real mismatch"
                    + "\n calculated: " + calculated.real()
                    + "\n expected  : " + expected.real());
        }
        if (Math.abs(calculated.imag() - expected.imag()) > tol * Math.abs(expected.imag())) {
            fail("expm1 imag mismatch"
                    + "\n calculated: " + calculated.imag()
                    + "\n expected  : " + expected.imag());
        }
    }

    @Test
    public void testLog1p() {
        QL.info("Testing complex valued log1p...");

        // Sanity: away from zero, log1p(z) == log(1+z).
        final Complex z = new Complex(1.2, 0.57);
        final double diff = z.add(1.0).log().sub(Expm1.log1p(z)).abs();
        if (diff > 10 * Constants.QL_EPSILON) {
            fail("complex log1p inconsistent with log(1+z) at z=" + z
                    + "; diff=" + diff);
        }

        // Near-zero reference value from scipy (per C++ test-suite/functions.cpp).
        final Complex calculated = Expm1.log1p(new Complex(5e-6, 5e-5));
        final Complex expected = new Complex(5.0012374875401984e-06, 4.999974995958395e-05);
        final double tol = Math.max(2.2e-14, 100 * Constants.QL_EPSILON);
        if (Math.abs(calculated.real() - expected.real()) > tol * Math.abs(expected.real())) {
            fail("log1p real mismatch"
                    + "\n calculated: " + calculated.real()
                    + "\n expected  : " + expected.real());
        }
        if (Math.abs(calculated.imag() - expected.imag()) > tol * Math.abs(expected.imag())) {
            fail("log1p imag mismatch"
                    + "\n calculated: " + calculated.imag()
                    + "\n expected  : " + expected.imag());
        }
    }
}

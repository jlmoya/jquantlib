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
import org.jquantlib.math.Autocovariance;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/autocovariances.cpp (Phase 5a).
 *
 * <p>3 BOOST_AUTO_TEST_CASE methods. Bodies filled in
 * Phase 5e.5b-CFC-d-77 once {@link Autocovariance} was ported.
 */
public class AutocovariancesTest {

    public AutocovariancesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testConvolutions() {
        QL.info("Testing convolutions...");

        // Array x(10, 1, 1) constructs {1,2,3,4,5,6,7,8,9,10}
        final double[] x = arithmeticSeq(10, 1.0, 1.0);
        final double[] conv = Autocovariance.convolutions(x, 5);
        final double[] expected = { 385, 330, 276, 224, 175, 130 };
        if (dotProductDiff(conv, expected) > 1.0e-6) {
            fail("Convolution: \n"
                    + "    calculated:   " + java.util.Arrays.toString(conv) + "\n"
                    + "    expected:     " + java.util.Arrays.toString(expected));
        }
    }

    @Test
    public void testAutoCovariances() {
        QL.info("Testing auto-covariances...");

        final double[] x = arithmeticSeq(10, 1.0, 1.0);
        final double[] meanOut = new double[1];
        final double[] acovf = Autocovariance.autocovariances(x, 5, false, meanOut);
        final double[] expected = { 8.25, 6.416667, 4.25, 1.75, -1.08333, -4.25 };

        if (Math.abs(meanOut[0] - 5.5) > 1.0e-6) {
            fail("Mean: \n"
                    + "    calculated:   " + meanOut[0] + "\n"
                    + "    expected:     " + 5.5);
        }
        if (dotProductDiff(acovf, expected) > 1.0e-6) {
            fail("Autocovariances: \n"
                    + "    calculated:   " + java.util.Arrays.toString(acovf) + "\n"
                    + "    expected:     " + java.util.Arrays.toString(expected));
        }
    }

    @Test
    public void testAutoCorrelations() {
        QL.info("Testing auto-correlations...");

        final double[] x = arithmeticSeq(10, 1.0, 1.0);
        final double[] meanOut = new double[1];
        final double[] acorf = Autocovariance.autocorrelations(x, 5, true, meanOut);
        final double[] expected = {
                9.166667, 0.77777778, 0.51515152,
                0.21212121, -0.13131313, -0.51515152
        };

        if (Math.abs(meanOut[0] - 5.5) > 1.0e-6) {
            fail("Mean: \n"
                    + "    calculated:   " + meanOut[0] + "\n"
                    + "    expected:     " + 5.5);
        }
        if (dotProductDiff(acorf, expected) > 1.0e-6) {
            fail("Autocovariances: \n"
                    + "    calculated:   " + java.util.Arrays.toString(acorf) + "\n"
                    + "    expected:     " + java.util.Arrays.toString(expected));
        }

        // Verify reuse=true wrote the centered data back into x.
        // Array(10, -4.5, 1) constructs {-4.5, -3.5, -2.5, ..., 4.5}.
        final double[] centered = arithmeticSeq(10, -4.5, 1.0);
        if (dotProductDiff(x, centered) > 1.0e-6) {
            fail("Centering: \n"
                    + "    calculated:   " + java.util.Arrays.toString(x) + "\n"
                    + "    expected:     " + java.util.Arrays.toString(centered));
        }
    }

    /** Mirrors C++ {@code Array(n, first, step)} = {first, first+step, ..., first+(n-1)*step}. */
    private static double[] arithmeticSeq(final int n, final double first, final double step) {
        final double[] a = new double[n];
        for (int i = 0; i < n; ++i) {
            a[i] = first + i * step;
        }
        return a;
    }

    /** Returns {@code (a - b) . (a - b)}. */
    private static double dotProductDiff(final double[] a, final double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; ++i) {
            final double d = a[i] - b[i];
            s += d * d;
        }
        return s;
    }
}

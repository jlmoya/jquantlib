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
import org.jquantlib.math.FastFourierTransform;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/fastfouriertransform.cpp (Phase 5a).
 *
 * <p>Mirrors C++ {@code BOOST_AUTO_TEST_CASE(testSimple)} and
 * {@code BOOST_AUTO_TEST_CASE(testInverse)}. Tolerances match C++ exactly
 * (1e-2 for the truncated tabulated expected values in testSimple, 1e-10 for
 * the analytic convolution identity in testInverse).
 */
public class FastFourierTransformTest {

    public FastFourierTransformTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testSimple() {
        QL.info("Testing complex direct FFT...");

        // a = { 0+0i, 1+1i, 3+3i, 4+4i, 4+4i, 3+3i, 1+1i, 0+0i }
        final double[] aRe = { 0.0, 1.0, 3.0, 4.0, 4.0, 3.0, 1.0, 0.0 };
        final double[] aIm = { 0.0, 1.0, 3.0, 4.0, 4.0, 3.0, 1.0, 0.0 };
        final double[] bRe = new double[8];
        final double[] bIm = new double[8];

        final FastFourierTransform fft = new FastFourierTransform(3);
        fft.transform(aRe, aIm, bRe, bIm);

        final double[] expRe = { 16.0, -4.8284, 0.0, -0.3431,
                                  0.0,   0.8284, 0.0, -11.6569 };
        final double[] expIm = { 16.0, -11.6569, 0.0, 0.8284,
                                  0.0,  -0.3431, 0.0, -4.8284 };

        for (int i = 0; i < 8; ++i) {
            if (Math.abs(bRe[i] - expRe[i]) > 1.0e-2
                    || Math.abs(bIm[i] - expIm[i]) > 1.0e-2) {
                fail(String.format(
                        "Convolution(%d)%n    calculated: (%g, %g)%n    expected:   (%g, %g)",
                        i, bRe[i], bIm[i], expRe[i], expIm[i]));
            }
        }
    }

    @Test
    public void testInverse() {
        QL.info("Testing convolution via inverse FFT...");

        // x = [1, 2, 3] — real input.
        final double[] x = { 1.0, 2.0, 3.0 };

        // C++ uses min_order(3) + 1 = 2 + 1 = 3, i.e. nFrq = 8.
        final int order = FastFourierTransform.minOrder(x.length) + 1;
        final FastFourierTransform fft = new FastFourierTransform(order);
        final int nFrq = fft.outputSize();

        final double[] ftRe = new double[nFrq];
        final double[] ftIm = new double[nFrq];

        // Step 1: ft = inverse FFT of x (zero-padded).
        fft.inverseTransformReal(x, ftRe, ftIm);

        // Step 2: tmp[i] = norm(ft[i]) = re^2 + im^2. Then re-run inverse FFT on tmp.
        final double[] tmp = new double[nFrq];
        for (int i = 0; i < nFrq; ++i) {
            tmp[i] = ftRe[i] * ftRe[i] + ftIm[i] * ftIm[i];
            ftRe[i] = 0.0;
            ftIm[i] = 0.0;
        }
        fft.inverseTransformReal(tmp, ftRe, ftIm);

        // The unnormalized identity (inverse_FFT_of_|FFT(x)|^2)/N is the auto-
        // correlation of x at integer lags. With x = [1, 2, 3] padded with
        // zeros to length 8:
        //   lag 0: x0*x0 + x1*x1 + x2*x2 = 14
        //   lag 1: x0*x1 + x1*x2 = 8
        //   lag 2: x0*x2 = 3

        final double tol = 1.0e-10;

        double calculated = ftRe[0] / nFrq;
        double expected = x[0] * x[0] + x[1] * x[1] + x[2] * x[2];
        if (Math.abs(calculated - expected) > tol) {
            fail(String.format("Convolution(0)%n    calculated: %.16e%n    expected:   %.16e",
                    calculated, expected));
        }

        calculated = ftRe[1] / nFrq;
        expected = x[0] * x[1] + x[1] * x[2];
        if (Math.abs(calculated - expected) > tol) {
            fail(String.format("Convolution(1)%n    calculated: %.16e%n    expected:   %.16e",
                    calculated, expected));
        }

        calculated = ftRe[2] / nFrq;
        expected = x[0] * x[2];
        if (Math.abs(calculated - expected) > tol) {
            fail(String.format("Convolution(2)%n    calculated: %.16e%n    expected:   %.16e",
                    calculated, expected));
        }
    }
}

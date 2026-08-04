/*
 Copyright (C) 2026 Jose Moya

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

import org.jquantlib.QL;
import org.jquantlib.math.FastFourierTransform;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegral;
import org.jquantlib.math.integrals.DiscreteTrapezoidIntegral;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Java equivalents of the two degenerate-input regression tests added to C++ QuantLib's test suite in v1.43:
 * {@code testDiscreteIntegralsWithFewPoints} ({@code test-suite/integrals.cpp}) and {@code testTrivialOrder}
 * ({@code test-suite/fastfouriertransform.cpp}).
 * <p>
 * Both are out-of-bounds bugs on inputs nobody thinks to try. In C++ the culprit was unsigned wrap-around; in Java the
 * arithmetic is signed, so the loops were already safe — but the same two edges bit anyway, for slightly different
 * reasons, which is why porting the tests was worth more than reasoning about them.
 *
 * @author Jose Moya
 */
public class DegenerateInputsV143Test {

    /**
     * With fewer than two nodes there is no interval to integrate. Java's Simpson rule fell through to its even-{@code n}
     * trailing term on an empty array and indexed {@code x[-1]}; the trapezoid rule already returned zero.
     */
    @Test
    public void testDiscreteIntegralsWithFewPoints() {
        QL.info("Testing discrete integrals on degenerate grids...");

        for ( int n = 0; n < 2; ++n ) {
            final double[] xs = new double[n];
            final double[] fs = new double[n];
            for ( int i = 0; i < n; ++i ) {
                xs[i] = i;
                fs[i] = 1.0;
            }
            final Array x = new Array(xs);
            final Array f = new Array(fs);

            assertEquals("trapezoid over " + n + " point(s)", 0.0,
                    new DiscreteTrapezoidIntegral().evaluate(x, f), 0.0);
            assertEquals("Simpson over " + n + " point(s)", 0.0, new DiscreteSimpsonIntegral().op(x, f), 0.0);
        }
    }

    /**
     * {@code minOrder(1)} is 0, so order 0 is reachable from ordinary code — but the constructor rejected it, making a
     * size-1 transform impossible. A size-1 FFT is just a copy of its single element.
     */
    @Test
    public void testTrivialOrder() {
        QL.info("Testing FFT of size 1 (order 0)...");

        assertEquals("minOrder(1)", 0, FastFourierTransform.minOrder(1));

        final FastFourierTransform fft = new FastFourierTransform(0);
        assertEquals("output size of an order-0 transform", 1, fft.outputSize());

        final double[] inRe = { 2.5 };
        final double[] inIm = { -1.5 };
        final double[] outRe = new double[1];
        final double[] outIm = new double[1];
        fft.transform(inRe, inIm, outRe, outIm);

        assertEquals("size-1 FFT real part", inRe[0], outRe[0], 1.0e-12);
        assertEquals("size-1 FFT imaginary part", inIm[0], outIm[0], 1.0e-12);
    }
}

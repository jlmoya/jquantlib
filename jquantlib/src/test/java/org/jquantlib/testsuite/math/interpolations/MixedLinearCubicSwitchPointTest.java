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

package org.jquantlib.testsuite.math.interpolations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.MixedLinearCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Java equivalent of {@code testMixedLinearCubicSwitchPoint}, added to C++ QuantLib's test suite in v1.43
 * ({@code test-suite/interpolations.cpp}).
 * <p>
 * The switch point {@code x[n]} is dereferenced while updating and while evaluating, so a switch index equal to the
 * number of points walks one past the end. C++ read it; Java allowed the construction and then failed with an index
 * error on the next line. Either way the caller learned nothing useful — it must be rejected with the reason.
 *
 * @author Jose Moya
 */
public class MixedLinearCubicSwitchPointTest {

    /** {@code y = x²} on the same grid the upstream test uses. */
    private static Array parabolic(final Array x) {
        final double[] y = new double[x.size()];
        for ( int i = 0; i < y.length; ++i ) {
            y[i] = x.get(i) * x.get(i);
        }
        return new Array(y);
    }

    private static Array xRange(final double from, final double to, final int points) {
        final double[] x = new double[points];
        final double step = (to - from) / (points - 1);
        for ( int i = 0; i < points; ++i ) {
            x[i] = from + i * step;
        }
        return new Array(x);
    }

    @Test
    public void testSwitchIndexAtTheEndIsRejected() {
        QL.info("Testing switch-point bounds of mixed linear/cubic interpolation...");

        final int n = 5;
        final Array x = xRange(-2.0, 2.0, n);
        final Array y = parabolic(x);

        try {
            new MixedLinearCubicInterpolation.MixedLinearCubicNaturalSpline(x, y, n);
            fail("a switch index equal to the number of points must be rejected");
        } catch ( final RuntimeException e ) {
            final String message = String.valueOf(e.getMessage());
            assertTrue("the error must say what is wrong, got: " + message, message.contains("n is too large"));
        }
    }

    @Test
    public void testLargestValidSwitchIndexReproducesTheKnots() {
        QL.info("Testing the largest valid mixed linear/cubic switch index...");

        final int n = 5;
        final Array x = xRange(-2.0, 2.0, n);
        final Array y = parabolic(x);

        final MixedLinearCubicInterpolation.MixedLinearCubicNaturalSpline f =
                new MixedLinearCubicInterpolation.MixedLinearCubicNaturalSpline(x, y, n - 1);

        for ( int i = 0; i < n; ++i ) {
            assertEquals("failed to reproduce knot " + i, y.get(i), f.op(x.get(i)), 1.0e-12);
        }
    }
}

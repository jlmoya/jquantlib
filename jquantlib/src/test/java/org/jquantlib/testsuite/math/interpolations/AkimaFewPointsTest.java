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
import org.jquantlib.math.interpolations.AkimaCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Java equivalent of {@code testAkimaWithFewPoints}, added to C++ QuantLib's test suite in v1.43
 * ({@code test-suite/interpolations.cpp}).
 * <p>
 * The Akima scheme indexes {@code S[2]} and {@code S[n-4]} while estimating the end derivatives. With three points
 * {@code S} has size two and both indices are out of range: C++ read past the end of the vector, and Java threw an
 * array-index error from deep inside the derivative loop. Either way the caller learned nothing about what was
 * actually wrong. Construction must fail with the reason.
 *
 * @author Jose Moya
 */
public class AkimaFewPointsTest {

    @Test
    public void testAkimaRejectsFewerThanFourPoints() {
        QL.info("Testing Akima interpolation with too few points...");

        final Array x3 = new Array(new double[] { 0.0, 1.0, 2.0 });
        final Array y3 = new Array(new double[] { 1.0, 2.0, 0.5 });

        try {
            new AkimaCubicInterpolation(x3, y3);
            fail("Akima with three points must be rejected");
        } catch ( final RuntimeException e ) {
            final String message = String.valueOf(e.getMessage());
            assertTrue("the error must name the actual requirement, got: " + message,
                    message.contains("Akima approximation requires at least 4 points"));
        }
    }

    @Test
    public void testAkimaWithFourPointsReproducesTheKnots() {
        QL.info("Testing that four-point Akima interpolation reproduces its knots...");

        final double[] xs = { 0.0, 1.0, 2.0, 3.0 };
        final double[] ys = { 1.0, 2.0, 0.5, 1.5 };
        final AkimaCubicInterpolation f = new AkimaCubicInterpolation(new Array(xs), new Array(ys));

        for ( int i = 0; i < xs.length; ++i ) {
            assertEquals("Akima must reproduce knot " + i, ys[i], f.op(xs[i]), 1.0e-12);
        }
    }
}

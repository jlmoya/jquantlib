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

package org.jquantlib.testsuite.math.interpolations;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.AkimaCubicInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.KrugerCubic;
import org.jquantlib.math.interpolations.MonotonicParabolicCubicInterpolation;
import org.jquantlib.math.interpolations.ParabolicCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Equivalence tests for the cubic factory subclasses (Akima, Kruger, Parabolic,
 * MonotonicParabolic). Each subclass must produce the same interpolated values
 * as a directly-configured {@link CubicInterpolation} with the matching
 * {@link CubicInterpolation.DerivativeApprox} and natural second-derivative
 * boundary conditions. Mirrors C++ v1.42.1 cubicinterpolation.hpp factory wrappers.
 */
public class CubicFactoriesTest {

    private static final double TIGHT = 1.0e-12;

    private static final Array X = new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 });
    private static final Array Y = new Array(new double[] { 0.0, 1.0, 0.5, 2.0, 1.5 });
    private static final double[] PROBES = { 0.25, 0.5, 1.5, 2.5, 3.5 };

    public CubicFactoriesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static void assertEquivalent(final CubicInterpolation reference, final CubicInterpolation wrapper) {
        for (final double t : PROBES) {
            assertEquals(reference.op(t), wrapper.op(t), TIGHT);
        }
    }

    @Test
    public void testAkimaEquivalent() {
        final CubicInterpolation reference = new CubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Akima, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(reference, new AkimaCubicInterpolation(X, Y));
    }

    @Test
    public void testKrugerEquivalent() {
        final CubicInterpolation reference = new CubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Kruger, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(reference, new KrugerCubic(X, Y));
    }

    @Test
    public void testParabolicEquivalent() {
        final CubicInterpolation reference = new CubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Parabolic, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(reference, new ParabolicCubicInterpolation(X, Y));
    }

    @Test
    public void testMonotonicParabolicEquivalent() {
        final CubicInterpolation reference = new CubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Parabolic, true,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(reference, new MonotonicParabolicCubicInterpolation(X, Y));
    }

    @Test
    public void testInterpolatesAtKnots() {
        final AkimaCubicInterpolation akima = new AkimaCubicInterpolation(X, Y);
        for (int i = 0; i < X.size(); ++i) {
            assertEquals(Y.get(i), akima.op(X.get(i)), TIGHT);
        }
        final KrugerCubic kruger = new KrugerCubic(X, Y);
        for (int i = 0; i < X.size(); ++i) {
            assertEquals(Y.get(i), kruger.op(X.get(i)), TIGHT);
        }
    }
}

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
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.Interpolation;
import org.jquantlib.math.interpolations.LogMixedLinearCubicInterpolation;
import org.jquantlib.math.interpolations.LogMixedLinearCubicNaturalSpline;
import org.jquantlib.math.interpolations.MixedLinearCubicInterpolation;
import org.jquantlib.math.interpolations.factories.KrugerLogMixedLinearCubic;
import org.jquantlib.math.interpolations.factories.LogMixedLinearCubic;
import org.jquantlib.math.interpolations.factories.MonotonicLogMixedLinearCubic;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Equivalence tests for the log-mixed-linear-cubic interpolation family. Mirrors C++
 * v1.42.1 loginterpolation.hpp LogMixedLinearCubicInterpolation,
 * LogMixedLinearCubicNaturalSpline, LogMixedLinearCubic, MonotonicLogMixedLinearCubic,
 * KrugerLogMixedLinearCubic.
 */
public class LogMixedLinearCubicTest {

    private static final double TIGHT = 1.0e-12;

    private static final Array X = new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0, 5.0 });
    private static final Array Y = new Array(new double[] { 1.0, 0.95, 0.90, 0.85, 0.80, 0.78 });
    private static final int N_SWITCH = 3;
    private static final double[] PROBES = { 0.25, 1.5, 3.5, 4.5 };

    public LogMixedLinearCubicTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testNaturalSplineFactoryEquivalent() {
        final LogMixedLinearCubicNaturalSpline factory = new LogMixedLinearCubicNaturalSpline(X, Y, N_SWITCH);
        // direct ctor with same parameters
        final LogMixedLinearCubicInterpolation direct = new LogMixedLinearCubicInterpolation(X, Y, N_SWITCH,
                MixedLinearCubicInterpolation.Behavior.ShareRanges, CubicInterpolation.DerivativeApprox.Spline, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        for (final double t : PROBES) {
            assertEquals(direct.op(t), factory.op(t), TIGHT);
        }
    }

    @Test
    public void testInterpolatesAtKnots() {
        final LogMixedLinearCubicNaturalSpline factory = new LogMixedLinearCubicNaturalSpline(X, Y, N_SWITCH);
        for (int i = 0; i < X.size(); ++i) {
            assertEquals(Y.get(i), factory.op(X.get(i)), TIGHT);
        }
    }

    @Test
    public void testMonotonicLogMixedLinearCubicFactoryEquivalence() {
        final Interpolation built = new MonotonicLogMixedLinearCubic(N_SWITCH).interpolate(X, Y);
        // expected: Spline, monotonic=true, SecondDerivative 0 on both ends
        final Interpolation direct = new LogMixedLinearCubic(N_SWITCH, MixedLinearCubicInterpolation.Behavior.ShareRanges,
                CubicInterpolation.DerivativeApprox.Spline, true,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0).interpolate(X, Y);
        for (final double t : PROBES) {
            assertEquals(direct.op(t), built.op(t), TIGHT);
        }
    }

    @Test
    public void testKrugerLogMixedLinearCubicFactoryEquivalence() {
        final Interpolation built = new KrugerLogMixedLinearCubic(N_SWITCH).interpolate(X, Y);
        final Interpolation direct = new LogMixedLinearCubic(N_SWITCH, MixedLinearCubicInterpolation.Behavior.ShareRanges,
                CubicInterpolation.DerivativeApprox.Kruger, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0).interpolate(X, Y);
        for (final double t : PROBES) {
            assertEquals(direct.op(t), built.op(t), TIGHT);
        }
    }
}

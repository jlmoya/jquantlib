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
import org.jquantlib.math.interpolations.FritschButlandLogCubic;
import org.jquantlib.math.interpolations.KrugerLogCubic;
import org.jquantlib.math.interpolations.LogCubicInterpolation;
import org.jquantlib.math.interpolations.LogCubicNaturalSpline;
import org.jquantlib.math.interpolations.LogParabolic;
import org.jquantlib.math.interpolations.MonotonicLogCubicNaturalSpline;
import org.jquantlib.math.interpolations.MonotonicLogParabolic;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Equivalence tests for the log-cubic factory subclasses (LogCubicNaturalSpline,
 * MonotonicLogCubicNaturalSpline, KrugerLogCubic, FritschButlandLogCubic, LogParabolic,
 * MonotonicLogParabolic). Each subclass must produce the same interpolated values as a
 * directly-configured {@link LogCubicInterpolation} with the matching settings.
 * Mirrors C++ v1.42.1 loginterpolation.hpp factory wrappers.
 */
public class LogCubicFactoriesTest {

    private static final double TIGHT = 1.0e-12;

    private static final Array X = new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 });
    private static final Array Y = new Array(new double[] { 1.0, 0.95, 0.90, 0.80, 0.78 });
    private static final double[] PROBES = { 0.25, 0.5, 1.5, 2.5, 3.5 };

    public LogCubicFactoriesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static void assertEquivalent(final LogCubicInterpolation reference,
            final LogCubicInterpolation wrapper) {
        for (final double t : PROBES) {
            assertEquals(reference.op(t), wrapper.op(t), TIGHT);
        }
    }

    @Test
    public void testLogCubicNaturalSplineEquivalent() {
        final LogCubicInterpolation ref = new LogCubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Spline, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(ref, new LogCubicNaturalSpline(X, Y));
    }

    @Test
    public void testMonotonicLogCubicNaturalSplineEquivalent() {
        final LogCubicInterpolation ref = new LogCubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Spline, true,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(ref, new MonotonicLogCubicNaturalSpline(X, Y));
    }

    @Test
    public void testKrugerLogCubicEquivalent() {
        final LogCubicInterpolation ref = new LogCubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Kruger, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(ref, new KrugerLogCubic(X, Y));
    }

    @Test
    public void testFritschButlandLogCubicEquivalent() {
        final LogCubicInterpolation ref = new LogCubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.FritschButland, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(ref, new FritschButlandLogCubic(X, Y));
    }

    @Test
    public void testLogParabolicEquivalent() {
        final LogCubicInterpolation ref = new LogCubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Parabolic, false,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(ref, new LogParabolic(X, Y));
    }

    @Test
    public void testMonotonicLogParabolicEquivalent() {
        final LogCubicInterpolation ref = new LogCubicInterpolation(X, Y,
                CubicInterpolation.DerivativeApprox.Parabolic, true,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0,
                CubicInterpolation.BoundaryCondition.SecondDerivative, 0.0);
        assertEquivalent(ref, new MonotonicLogParabolic(X, Y));
    }

    @Test
    public void testInterpolatesAtKnots() {
        final LogCubicNaturalSpline lcns = new LogCubicNaturalSpline(X, Y);
        for (int i = 0; i < X.size(); ++i) {
            assertEquals(Y.get(i), lcns.op(X.get(i)), TIGHT);
        }
    }
}

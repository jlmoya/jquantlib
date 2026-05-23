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
 */

package org.jquantlib.testsuite.math.interpolations;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.CubicInterpolation;
import org.jquantlib.math.interpolations.CubicInterpolation.BoundaryCondition;
import org.jquantlib.math.interpolations.CubicInterpolation.DerivativeApprox;
import org.jquantlib.math.interpolations.CubicSplineOvershootingMinimization1;
import org.jquantlib.math.interpolations.CubicSplineOvershootingMinimization2;
import org.jquantlib.math.interpolations.HarmonicCubic;
import org.jquantlib.math.interpolations.HarmonicLogCubic;
import org.jquantlib.math.interpolations.LogCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.junit.Test;

/**
 * Cross-validation tests for the newly-ported CubicInterpolation derivative variants
 * (Harmonic, SplineOM1, SplineOM2) and their factory wrappers.
 * <p>
 * Reference values produced by {@code cubic_new_variants_probe.cpp} linked against system
 * QuantLib 1.42.1 (Homebrew) — see
 * {@code migration-harness/references/math/interpolations/cubic_new_variants.txt}.
 *
 * @author JQuantLib migration contributors
 */
public class CubicInterpolationNewVariantsTest {

    private static final double TIGHT = 1.0e-12;

    // Grid A: 5-point monotonic increasing
    private static final Array X_A = new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 });
    private static final Array Y_A = new Array(new double[] { 1.0, 1.5, 2.5, 4.0, 6.0 });

    // Grid B: 7-point non-monotonic (sign changes -> Harmonic edge cases)
    private static final Array X_B =
            new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0 });
    private static final Array Y_B =
            new Array(new double[] { 0.5, 0.9, 1.3, 1.2, 1.0, 1.4, 1.7 });

    // Grid C: 5-point positive decreasing (for log-cubic)
    private static final Array X_C = new Array(new double[] { 0.0, 1.0, 2.0, 3.0, 4.0 });
    private static final Array Y_C = new Array(new double[] { 1.0, 0.95, 0.90, 0.80, 0.78 });

    public CubicInterpolationNewVariantsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // -----------------------------------------------------------------------------------------
    //  Harmonic
    // -----------------------------------------------------------------------------------------

    @Test
    public void testHarmonicGridAReferenceValues() {
        final HarmonicCubic interp = new HarmonicCubic(X_A, Y_A);
        // Coefficients (segment 0..3)
        final double[] expA = { 0.25, 0.66666666666666663, 1.2, 1.7142857142857142 };
        final double[] expB = { 0.33333333333333337, 0.46666666666666679, 0.38571428571428568,
                0.32142857142857162 };
        final double[] expC = { -0.08333333333333337, -0.1333333333333333, -0.085714285714285854,
                -0.035714285714285587 };
        for (int i = 0; i < 4; ++i) {
            assertEquals("Harmonic-A a[" + i + "]", expA[i], interp.aCoefficients().get(i), TIGHT);
            assertEquals("Harmonic-A b[" + i + "]", expB[i], interp.bCoefficients().get(i), TIGHT);
            assertEquals("Harmonic-A c[" + i + "]", expC[i], interp.cCoefficients().get(i), TIGHT);
        }
        // Values
        assertEquals(1.08203125, interp.op(0.25), TIGHT);
        assertEquals(1.1979166666666667, interp.op(0.5), TIGHT);
        assertEquals(1.9333333333333333, interp.op(1.5), TIGHT);
        assertEquals(3.1857142857142859, interp.op(2.5), TIGHT);
        assertEquals(4.9330357142857144, interp.op(3.5), TIGHT);
        // Derivatives
        assertEquals(0.40104166666666669, interp.derivative(0.25), TIGHT);
        assertEquals(0.52083333333333337, interp.derivative(0.5), TIGHT);
        assertEquals(1.0333333333333334, interp.derivative(1.5), TIGHT);
        assertEquals(1.5214285714285714, interp.derivative(2.5), TIGHT);
        assertEquals(2.0089285714285716, interp.derivative(3.5), TIGHT);
    }

    @Test
    public void testHarmonicGridBSignChangesReferenceValues() {
        // Grid B includes a flat-on-the-left edge case where the first segment is constant
        // (y0=0.5, y1=0.9, S[0]=0.4) followed by a sign-changing slope sequence.
        final HarmonicCubic interp = new HarmonicCubic(X_B, Y_B);
        // The Harmonic end-point clipper sets a[0] = 0.4 (matches S[0]) -> linear segment.
        assertEquals(0.4, interp.aCoefficients().get(0), TIGHT);
        assertEquals(0.0, interp.bCoefficients().get(0), TIGHT);
        assertEquals(0.0, interp.cCoefficients().get(0), TIGHT);
        // Values across the grid
        assertEquals(0.69999999999999996, interp.op(0.5), TIGHT);
        assertEquals(1.1500000000000001, interp.op(1.5), TIGHT);
        assertEquals(1.2666666666666666, interp.op(2.5), TIGHT);
        assertEquals(1.0833333333333333, interp.op(3.5), TIGHT);
        assertEquals(1.157142857142857, interp.op(4.5), TIGHT);
        assertEquals(1.5616071428571427, interp.op(5.5), TIGHT);
        // Sign-change point: middle interpolation should produce derivative 0 at internal
        // points where slopes flip; we just verify a representative derivative.
        assertEquals(-0.11666666666666678, interp.derivative(2.5), TIGHT);
        assertEquals(0.51428571428571412, interp.derivative(4.5), TIGHT);
    }

    @Test
    public void testHarmonicEnumAndFactoryEquivalent() {
        final HarmonicCubic factory = new HarmonicCubic(X_A, Y_A);
        final CubicInterpolation direct = new CubicInterpolation(X_A, Y_A,
                DerivativeApprox.Harmonic, false,
                BoundaryCondition.SecondDerivative, 0.0,
                BoundaryCondition.SecondDerivative, 0.0);
        for (double t : new double[] { 0.1, 0.5, 1.7, 2.3, 3.9 }) {
            assertEquals("op @ " + t, direct.op(t), factory.op(t), TIGHT);
            assertEquals("derivative @ " + t, direct.derivative(t), factory.derivative(t), TIGHT);
            assertEquals("secondDerivative @ " + t, direct.secondDerivative(t),
                    factory.secondDerivative(t), TIGHT);
        }
    }

    // -----------------------------------------------------------------------------------------
    //  SplineOM1
    // -----------------------------------------------------------------------------------------

    @Test
    public void testSplineOM1GridAReferenceValues() {
        final CubicSplineOvershootingMinimization1 interp =
                new CubicSplineOvershootingMinimization1(X_A, Y_A);
        // Coefficients
        final double[] expA = { 0.49999999999999972, 0.68749999999999978, 1.2500000000000009,
                1.8124999999999998 };
        final double[] expB = { -0.18749999999999922, 0.37499999999999956, 0.18749999999999822,
                0.375 };
        final double[] expC = { 0.18749999999999956, -0.062499999999999334, 0.062500000000000888,
                -0.1875 };
        for (int i = 0; i < 4; ++i) {
            assertEquals("OM1-A a[" + i + "]", expA[i], interp.aCoefficients().get(i), TIGHT);
            assertEquals("OM1-A b[" + i + "]", expB[i], interp.bCoefficients().get(i), TIGHT);
            assertEquals("OM1-A c[" + i + "]", expC[i], interp.cCoefficients().get(i), TIGHT);
        }
        // Values
        assertEquals(1.1162109375, interp.op(0.25), TIGHT);
        assertEquals(1.2265625, interp.op(0.5), TIGHT);
        assertEquals(1.9296875, interp.op(1.5), TIGHT);
        assertEquals(3.1796875, interp.op(2.5), TIGHT);
        assertEquals(4.9765625, interp.op(3.5), TIGHT);
        // Derivative + second derivative
        assertEquals(0.44140625, interp.derivative(0.25), TIGHT);
        assertEquals(0.56250000000000111, interp.secondDerivative(1.5), TIGHT);
    }

    @Test
    public void testSplineOM1GridBReferenceValues() {
        final CubicSplineOvershootingMinimization1 interp =
                new CubicSplineOvershootingMinimization1(X_B, Y_B);
        assertEquals(0.69810843151693669, interp.op(0.5), TIGHT);
        assertEquals(1.1319357511045656, interp.op(1.5), TIGHT);
        assertEquals(1.3116485640648012, interp.op(2.5), TIGHT);
        assertEquals(1.0464699926362298, interp.op(3.5), TIGHT);
        assertEquals(1.1649714653902798, interp.op(4.5), TIGHT);
        assertEquals(1.5561441458026508, interp.op(5.5), TIGHT);
    }

    // -----------------------------------------------------------------------------------------
    //  SplineOM2
    // -----------------------------------------------------------------------------------------

    @Test
    public void testSplineOM2GridAReferenceValues() {
        final CubicSplineOvershootingMinimization2 interp =
                new CubicSplineOvershootingMinimization2(X_A, Y_A);
        // Coefficients
        final double[] expA = { 0.39285714285714274, 0.71428571428571419, 1.2500000000000002,
                1.7857142857142856 };
        final double[] expB = { 3.3306690738754696e-16, 0.3214285714285714, 0.21428571428571397,
                0.32142857142857162 };
        final double[] expC = { 0.10714285714285698, -0.035714285714285587, 0.035714285714285587,
                -0.10714285714285721 };
        for (int i = 0; i < 4; ++i) {
            assertEquals("OM2-A a[" + i + "]", expA[i], interp.aCoefficients().get(i), TIGHT);
            assertEquals("OM2-A b[" + i + "]", expB[i], interp.bCoefficients().get(i), TIGHT);
            assertEquals("OM2-A c[" + i + "]", expC[i], interp.cCoefficients().get(i), TIGHT);
        }
        // Values
        assertEquals(1.0998883928571428, interp.op(0.25), TIGHT);
        assertEquals(1.2098214285714286, interp.op(0.5), TIGHT);
        assertEquals(1.9330357142857142, interp.op(1.5), TIGHT);
        assertEquals(3.1830357142857144, interp.op(2.5), TIGHT);
        assertEquals(4.9598214285714288, interp.op(3.5), TIGHT);
    }

    @Test
    public void testSplineOM2GridBReferenceValues() {
        final CubicSplineOvershootingMinimization2 interp =
                new CubicSplineOvershootingMinimization2(X_B, Y_B);
        assertEquals(0.68846153846153846, interp.op(0.5), TIGHT);
        assertEquals(1.1346153846153846, interp.op(1.5), TIGHT);
        assertEquals(1.3105769230769231, interp.op(2.5), TIGHT);
        assertEquals(1.0480769230769231, interp.op(3.5), TIGHT);
        assertEquals(1.1596153846153845, interp.op(4.5), TIGHT);
        assertEquals(1.5759615384615384, interp.op(5.5), TIGHT);
    }

    // -----------------------------------------------------------------------------------------
    //  HarmonicLogCubic
    // -----------------------------------------------------------------------------------------

    @Test
    public void testHarmonicLogCubicGridCReferenceValues() {
        final HarmonicLogCubic interp = new HarmonicLogCubic(X_C, Y_C);
        assertEquals(0.98751363013383275, interp.op(0.25), TIGHT);
        assertEquals(0.97501300381969069, interp.op(0.5), TIGHT);
        assertEquals(0.92714695300764116, interp.op(1.5), TIGHT);
        assertEquals(0.84509470729349778, interp.op(2.5), TIGHT);
        assertEquals(0.7858321313842469, interp.op(3.5), TIGHT);

        // Derivative
        assertEquals(-0.049979273428089312, interp.derivative(0.25), TIGHT);
        assertEquals(-0.045811770013157581, interp.derivative(1.5), TIGHT);
    }

    @Test
    public void testHarmonicLogCubicEquivalentToDirectConstruction() {
        final HarmonicLogCubic factory = new HarmonicLogCubic(X_C, Y_C);
        final LogCubicInterpolation direct = new LogCubicInterpolation(X_C, Y_C,
                DerivativeApprox.Harmonic, false,
                BoundaryCondition.SecondDerivative, 0.0,
                BoundaryCondition.SecondDerivative, 0.0);
        for (double t : new double[] { 0.1, 0.5, 1.7, 2.3, 3.9 }) {
            assertEquals("logOp @ " + t, direct.op(t), factory.op(t), TIGHT);
            assertEquals("logDerivative @ " + t, direct.derivative(t), factory.derivative(t),
                    TIGHT);
        }
    }
}

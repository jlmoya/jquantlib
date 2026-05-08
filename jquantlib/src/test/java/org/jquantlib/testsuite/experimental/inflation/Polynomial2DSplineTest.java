/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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

package org.jquantlib.testsuite.experimental.inflation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.experimental.inflation.Polynomial2DSpline;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Cross-validated test for {@link Polynomial2DSpline} against
 * C++ v1.42.1 {@code QuantLib::Polynomial2DSpline} reference values.
 *
 * <p>Tolerance tiers:
 * <ul>
 *   <li>Grid points: TIGHT ({@code 1e-12} rel / {@code 1e-14} abs) — the spline
 *       passes through the data points exactly.</li>
 *   <li>Interior (off-grid) points: LOOSE ({@code 1e-8}) — polynomial/spline
 *       evaluation accumulates floating-point rounding.</li>
 * </ul>
 *
 * @author JQuantLib migration contributors (Phase 2s L0)
 */
public class Polynomial2DSplineTest {

    private static ReferenceReader READER;
    private static Array VX;
    private static Array VY;
    private static Matrix MZ;
    private static Polynomial2DSpline SPLINE;

    @BeforeClass
    public static void setUp() {
        READER = ReferenceReader.load("experimental/inflation/polynomial_2d_spline");

        // Reconstruct the grid from the reference inputs of the first case.
        // All cases share the same grid so we read it once.
        final Case first = READER.getCase("grid_x0_y0");
        final JSONObject in = first.inputs();

        VX = new Array(new double[]{
            in.getJSONArray("xs").getDouble(0),
            in.getJSONArray("xs").getDouble(1),
            in.getJSONArray("xs").getDouble(2),
            in.getJSONArray("xs").getDouble(3)
        });
        VY = new Array(new double[]{
            in.getJSONArray("ys").getDouble(0),
            in.getJSONArray("ys").getDouble(1),
            in.getJSONArray("ys").getDouble(2)
        });

        // z matrix: 3 rows (y) x 4 cols (x)
        MZ = new Matrix(3, 4);
        MZ.set(0, 0, in.getDouble("z00")); MZ.set(0, 1, in.getDouble("z01"));
        MZ.set(0, 2, in.getDouble("z02")); MZ.set(0, 3, in.getDouble("z03"));
        MZ.set(1, 0, in.getDouble("z10")); MZ.set(1, 1, in.getDouble("z11"));
        MZ.set(1, 2, in.getDouble("z12")); MZ.set(1, 3, in.getDouble("z13"));
        MZ.set(2, 0, in.getDouble("z20")); MZ.set(2, 1, in.getDouble("z21"));
        MZ.set(2, 2, in.getDouble("z22")); MZ.set(2, 3, in.getDouble("z23"));

        SPLINE = new Polynomial2DSpline(VX, VY, MZ);
        SPLINE.enableExtrapolation();
    }

    // -------------------------------------------------------------------------
    // Grid-point evaluations — TIGHT tier
    // -------------------------------------------------------------------------

    @Test
    public void gridPoint_x0_y0_isTight() {
        assertTight("grid_x0_y0",
                VX.get(0), VY.get(0));
    }

    @Test
    public void gridPoint_x1_y0_isTight() {
        assertTight("grid_x1_y0",
                VX.get(1), VY.get(0));
    }

    @Test
    public void gridPoint_x2_y0_isTight() {
        assertTight("grid_x2_y0",
                VX.get(2), VY.get(0));
    }

    @Test
    public void gridPoint_x3_y0_isTight() {
        assertTight("grid_x3_y0",
                VX.get(3), VY.get(0));
    }

    @Test
    public void gridPoint_x0_y1_isTight() {
        assertTight("grid_x0_y1",
                VX.get(0), VY.get(1));
    }

    @Test
    public void gridPoint_x1_y1_isTight() {
        assertTight("grid_x1_y1",
                VX.get(1), VY.get(1));
    }

    @Test
    public void gridPoint_x2_y1_isTight() {
        assertTight("grid_x2_y1",
                VX.get(2), VY.get(1));
    }

    @Test
    public void gridPoint_x3_y1_isTight() {
        assertTight("grid_x3_y1",
                VX.get(3), VY.get(1));
    }

    @Test
    public void gridPoint_x0_y2_isTight() {
        assertTight("grid_x0_y2",
                VX.get(0), VY.get(2));
    }

    @Test
    public void gridPoint_x1_y2_isTight() {
        assertTight("grid_x1_y2",
                VX.get(1), VY.get(2));
    }

    @Test
    public void gridPoint_x2_y2_isTight() {
        assertTight("grid_x2_y2",
                VX.get(2), VY.get(2));
    }

    @Test
    public void gridPoint_x3_y2_isTight() {
        assertTight("grid_x3_y2",
                VX.get(3), VY.get(2));
    }

    // -------------------------------------------------------------------------
    // Interior (off-grid) evaluations — LOOSE tier
    // -------------------------------------------------------------------------

    @Test
    public void interior_x15_y150_isLoose() {
        assertLoose("interior_x15_y150", 1.5, 15.0);
    }

    @Test
    public void interior_x25_y200_isLoose() {
        assertLoose("interior_x25_y200", 2.5, 20.0);
    }

    @Test
    public void interior_x30_y250_isLoose() {
        assertLoose("interior_x30_y250", 3.0, 25.0);
    }

    @Test
    public void interior_x20_y150_isLoose() {
        assertLoose("interior_x20_y150", 2.0, 15.0);
    }

    @Test
    public void interior_x35_y220_isLoose() {
        assertLoose("interior_x35_y220", 3.5, 22.0);
    }


    // -------------------------------------------------------------------------
    // Structural smoke test
    // -------------------------------------------------------------------------

    @Test
    public void factoryProducesSameResults() {
        // The Polynomial factory should produce an identical interpolation.
        final Polynomial2DSpline.Polynomial factory = new Polynomial2DSpline.Polynomial();
        final org.jquantlib.math.interpolations.Interpolation2D interp =
                factory.interpolate(VX, VY, MZ);
        interp.enableExtrapolation();

        final Case c = READER.getCase("grid_x1_y1");
        final double cpp = c.expectedDouble();
        final double java = interp.op(VX.get(1), VY.get(1), true);
        assertTrue("Polynomial factory value at grid (x1,y1) should be tight vs C++ reference",
                Tolerance.tight(java, cpp));
    }


    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void assertTight(final String caseName, final double x, final double y) {
        final Case c = READER.getCase(caseName);
        final double cpp = c.expectedDouble();
        final double java = SPLINE.op(x, y, true);
        if (!Tolerance.tight(java, cpp)) {
            fail(caseName + ": java=" + java + " cpp=" + cpp
                    + " diff=" + Math.abs(java - cpp)
                    + " (TIGHT: abs=1e-14 rel=1e-12)");
        }
    }

    private void assertLoose(final String caseName, final double x, final double y) {
        final Case c = READER.getCase(caseName);
        final double cpp = c.expectedDouble();
        final double java = SPLINE.op(x, y, true);
        if (!Tolerance.loose(java, cpp)) {
            fail(caseName + ": java=" + java + " cpp=" + cpp
                    + " diff=" + Math.abs(java - cpp)
                    + " (LOOSE: abs=1e-8 rel=1e-8)");
        }
    }

}

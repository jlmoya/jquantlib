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

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.MultiCubicSpline;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates {@link MultiCubicSpline} against the
 * {@code math/interpolations/multicubicspline} probe reference.
 * <p>
 * C++ assembles the N-dimensional spline from three template scaffolds in
 * {@code ql/math/interpolations/multicubicspline.hpp} —
 * {@code detail::DataTable} (:48, the recursively nested value table),
 * {@code detail::Point} (:122, the compile-time cons-list encoding the
 * argument/result/dimension tuples) and {@code detail::Int2Type} (:372, the
 * integer-to-type dispatch selecting the recursion depth). JQuantLib replaces
 * all three with a flat row-major {@code double[]} plus a stride table and
 * runtime recursion.
 * <p>
 * The pre-existing coverage, {@code InterpolationTest#testMultiSpline}, checks
 * the spline against the analytic function it was tabulated from at 1.7e-4 for
 * off-node points. That is a self-consistency check: a differently-wired stride
 * table can still interpolate something smooth to 1.7e-4. Here the expected
 * values are C++'s own spline output, so a stride or axis-order mistake fails
 * outright.
 * <p>
 * Tolerance tier: tight — 1e-12 relative, 1e-14 absolute near zero. Both sides
 * run the identical Thomas-algorithm recurrence and the identical cubic basis
 * over the identical doubles, so only floating-point association order can
 * differ, and that is far below 1e-12 for these magnitudes.
 *
 * @author Jose Moya
 */
public class MultiCubicSplineCrossValidationTest {

    private static final String GROUP = "math/interpolations/multicubicspline";
    private static final double REL = 1.0e-12;
    private static final double ABS = 1.0e-14;

    public MultiCubicSplineCrossValidationTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static void assertTight(final String where, final double expected, final double actual) {
        final double tol = Math.max(ABS, REL * Math.abs(expected));
        assertEquals(where, expected, actual, tol);
    }

    private static void checkCase(final String caseName) {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final ReferenceReader.Case c = ref.getCase(caseName);
        final JSONObject inputs = c.inputs();

        final JSONArray gridJson = inputs.getJSONArray("grid");
        final double[][] grid = new double[gridJson.length()][];
        for (int i = 0; i < gridJson.length(); i++) {
            final JSONArray ax = gridJson.getJSONArray(i);
            grid[i] = new double[ax.length()];
            for (int k = 0; k < ax.length(); k++) {
                grid[i][k] = ax.getDouble(k);
            }
        }

        final JSONArray valuesJson = inputs.getJSONArray("values");
        final double[] values = new double[valuesJson.length()];
        for (int i = 0; i < valuesJson.length(); i++) {
            values[i] = valuesJson.getDouble(i);
        }

        final MultiCubicSpline spline = new MultiCubicSpline(grid, values, new boolean[0]);

        final JSONArray rows = ((JSONObject) c.expectedRaw()).getJSONArray("rows");
        assertTrue(caseName + ": probe produced no rows", rows.length() > 0);
        for (int r = 0; r < rows.length(); r++) {
            final JSONObject row = rows.getJSONObject(r);
            final JSONArray xJson = row.getJSONArray("x");
            final double[] x = new double[xJson.length()];
            for (int i = 0; i < xJson.length(); i++) {
                x[i] = xJson.getDouble(i);
            }
            assertTight(caseName + " row " + r + " x=" + xJson, row.getDouble("value"), spline.op(x));
        }
    }

    /** 2-D: non-separable test function, so an axis swap in the strides cannot pass. */
    @Test
    public void testTwoDimensions() {
        QL.info("Testing 2-D MultiCubicSpline against C++ v1.43...");
        checkCase("dim2");
    }

    /** 3-D. */
    @Test
    public void testThreeDimensions() {
        QL.info("Testing 3-D MultiCubicSpline against C++ v1.43...");
        checkCase("dim3");
    }

    /**
     * 5-D, on the same grid, offsets and function as the upstream
     * {@code test-suite/interpolations.cpp:879 testMultiSpline} case, and
     * covering both interior knots (where the spline must reproduce the table)
     * and off-knot points (the actual tensor-product evaluation).
     */
    @Test
    public void testFiveDimensions() {
        QL.info("Testing 5-D MultiCubicSpline against C++ v1.43...");
        checkCase("dim5");
    }
}

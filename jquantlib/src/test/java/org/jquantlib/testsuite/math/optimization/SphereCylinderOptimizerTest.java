/*
 Copyright (C) 2026 Jose Moya

 This source code is released under the BSD License.
 JQuantLib is based on QuantLib. http://quantlib.org/
 */
package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.optimization.SphereCylinderOptimizer;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class SphereCylinderOptimizerTest {

    private static final ReferenceReader REF =
            ReferenceReader.load("math/optimization/spherecylinder");

    private static void assertArrayTight(final String ctx, final double[] actual,
                                          final JSONArray expected) {
        assertEquals(ctx + " size", expected.length(), actual.length);
        for (int i = 0; i < expected.length(); i++) {
            assertTrue(ctx + "[" + i + "] expected=" + expected.getDouble(i)
                            + " actual=" + actual[i],
                    Tolerance.tight(actual[i], expected.getDouble(i)));
        }
    }

    /**
     * Brent golden-section minimization accumulates FP-ordering noise that
     * differs between C++ and Java by ~1e-11 on iterates that are themselves
     * near 1e-7 in magnitude. Loose tolerance (1e-8 relative) justified: the
     * Brent algorithm's convergence parameter is 1e-12 here, so tolerance
     * should be at least 1e-10 by design §4.2 (convergence × 10).
     */
    private static void assertArrayLoose(final String ctx, final double[] actual,
                                          final JSONArray expected) {
        assertEquals(ctx + " size", expected.length(), actual.length);
        for (int i = 0; i < expected.length(); i++) {
            assertTrue(ctx + "[" + i + "] expected=" + expected.getDouble(i)
                            + " actual=" + actual[i],
                    Tolerance.loose(actual[i], expected.getDouble(i)));
        }
    }

    @Test
    public void isIntersectionNonEmpty_easy() {
        final SphereCylinderOptimizer opt =
                new SphereCylinderOptimizer(1.0, 0.3, 0.8, 0.2, 0.0, 0.5);
        assertEquals(((Boolean) REF.getCase("isIntersectionNonEmpty_easy").expectedRaw()).booleanValue(),
                opt.isIntersectionNonEmpty());
    }

    @Test
    public void isIntersectionNonEmpty_emptyFarCyl() {
        final SphereCylinderOptimizer opt =
                new SphereCylinderOptimizer(1.0, 0.1, 2.0, 0.0, 0.0, 0.0);
        assertEquals(((Boolean) REF.getCase("isIntersectionNonEmpty_emptyFarCyl").expectedRaw()).booleanValue(),
                opt.isIntersectionNonEmpty());
    }

    @Test
    public void findByProjection_easy() {
        final SphereCylinderOptimizer opt =
                new SphereCylinderOptimizer(1.0, 0.3, 0.8, 0.2, 0.0, 0.5);
        final double[] y = new double[3];
        final boolean found = opt.findByProjection(y);
        final JSONObject expected = (JSONObject) REF.getCase("findByProjection_easy").expectedRaw();
        assertEquals("found flag", expected.getBoolean("found"), found);
        assertArrayTight("findByProjection_easy.y", y, expected.getJSONArray("y"));
    }

    @Test
    public void findClosest_easy() {
        final SphereCylinderOptimizer opt =
                new SphereCylinderOptimizer(1.0, 0.3, 0.8, 0.2, 0.0, 0.5);
        final double[] y = new double[3];
        opt.findClosest(100, 1e-12, y);
        final JSONObject expected = (JSONObject) REF.getCase("findClosest_easy").expectedRaw();
        assertArrayLoose("findClosest_easy.y", y, expected.getJSONArray("y"));
    }

    @Test
    public void helperClosest_byProjection() {
        final double[] y = SphereCylinderOptimizer.sphereCylinderOptimizerClosest(
                1.0, 0.3, 0.8, 0.2, 0.0, 0.5, 0, 1e-10);
        assertArrayTight("helperClosest_byProjection", y,
                REF.getCase("helperClosest_byProjection").expectedArray());
    }

    @Test
    public void helperClosest_fullIter() {
        final double[] y = SphereCylinderOptimizer.sphereCylinderOptimizerClosest(
                1.0, 0.3, 0.8, 0.2, 0.0, 0.5, 100, 1e-12);
        // loose tolerance: Brent iteration converges to values that differ by
        // ~1e-11 in FP-ordering noise between C++ and Java; see assertArrayLoose doc.
        assertArrayLoose("helperClosest_fullIter", y,
                REF.getCase("helperClosest_fullIter").expectedArray());
    }
}

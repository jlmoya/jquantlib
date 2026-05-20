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

    /**
     * Port of v1.42.1 {@code test-suite/marketmodel_smmcaplethomocalibration.cpp}
     * {@code BOOST_AUTO_TEST_CASE(testSphereCylinder)} (lines 512-604).
     *
     * <p>Verifies {@link SphereCylinderOptimizer#findClosest} and {@link SphereCylinderOptimizer#findByProjection}
     * on two configurations:
     * <ul>
     *   <li>Case 1: degenerate exact case where (R=1, S=0.5, alpha=1.5, Z=(1/sqrt(3))*3) →
     *       both methods return (1, 0, 0) with errorTol 1e-12.</li>
     *   <li>Case 2: (R=5, S=1, alpha=1, Z=(1, 2, sqrt(20))) →
     *       findClosest returns (1.03306, 0.999453, 4.78893) with errorTol 1e-4,
     *       findByProjection returns (1, 1, sqrt(23)) with errorTol 1e-4.</li>
     * </ul>
     * Java implementation reproduces all six expected values within the C++ tolerances.
     */
    @Test
    public void testSphereCylinder() {
        // Case 1: degenerate exact case — projection point lies on the intersection circle.
        {
            final double R = 1.0;
            final double S = 0.5;
            final double alpha = 1.5;
            final double inv3 = 1.0 / Math.sqrt(3.0);
            final SphereCylinderOptimizer opt =
                    new SphereCylinderOptimizer(R, S, alpha, inv3, inv3, inv3);
            final int maxIterations = 100;
            final double tolerance = 1e-8;
            final double[] y = new double[3];
            opt.findClosest(maxIterations, tolerance, y);

            final double errorTol = 1e-12;
            assertEquals("Case1 findClosest y1", 1.0, y[0], errorTol);
            assertEquals("Case1 findClosest y2", 0.0, y[1], errorTol);
            assertEquals("Case1 findClosest y3", 0.0, y[2], errorTol);

            opt.findByProjection(y);
            assertEquals("Case1 findByProjection y1", 1.0, y[0], errorTol);
            assertEquals("Case1 findByProjection y2", 0.0, y[1], errorTol);
            assertEquals("Case1 findByProjection y3", 0.0, y[2], errorTol);
        }

        // Case 2: generic — C++ test uses errorTol = 1e-4 (loose by 1bp tier).
        {
            final double R = 5.0;
            final double S = 1.0;
            final double alpha = 1.0;
            final double Z1 = 1.0;
            final double Z2 = 2.0;
            final double Z3 = Math.sqrt(20.0);
            final SphereCylinderOptimizer opt =
                    new SphereCylinderOptimizer(R, S, alpha, Z1, Z2, Z3);
            final int maxIterations = 100;
            final double tolerance = 1e-8;
            final double[] y = new double[3];
            opt.findClosest(maxIterations, tolerance, y);

            // matches v1.42.1 expected values 1.03306, 0.999453, 4.78893 within 1e-4.
            // Loose-tier tolerance justified inline: C++ test prescribes 1e-4
            // (golden-section convergence at 1e-8 limits attainable precision).
            final double errorTol = 1e-4;
            assertEquals("Case2 findClosest y1", 1.03306, y[0], errorTol);
            assertEquals("Case2 findClosest y2", 0.999453, y[1], errorTol);
            assertEquals("Case2 findClosest y3", 4.78893, y[2], errorTol);

            opt.findByProjection(y);
            assertEquals("Case2 findByProjection y1", 1.0, y[0], errorTol);
            assertEquals("Case2 findByProjection y2", 1.0, y[1], errorTol);
            assertEquals("Case2 findByProjection y3", Math.sqrt(23.0), y[2], errorTol);
        }
    }
}

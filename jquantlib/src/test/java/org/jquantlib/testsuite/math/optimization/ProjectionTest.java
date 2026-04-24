/*
 Copyright (C) 2026 Jose Moya

 This source code is released under the BSD License.

 JQuantLib is based on QuantLib. http://quantlib.org/
 */
package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.ProjectedCostFunction;
import org.jquantlib.math.optimization.Projection;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.junit.Test;

/**
 * Cross-validation of {@link Projection} + {@link ProjectedCostFunction} against
 * QuantLib C++ v1.42.1. Reference from
 * {@code migration-harness/cpp/probes/math/optimization/projection_probe.cpp}.
 */
public class ProjectionTest {

    private static final ReferenceReader REF =
            ReferenceReader.load("math/optimization/projection");

    private static Array arr(final double... values) {
        final Array a = new Array(values.length);
        for (int i = 0; i < values.length; i++) {
            a.set(i, values[i]);
        }
        return a;
    }

    private static void assertArrayTight(final String case_, final Array actual) {
        final JSONArray expected = REF.getCase(case_).expectedArray();
        assertEquals(case_ + " size mismatch", expected.length(), actual.size());
        for (int i = 0; i < expected.length(); i++) {
            assertTrue(case_ + "[" + i + "] mismatch: expected=" + expected.getDouble(i)
                            + " actual=" + actual.get(i),
                    Tolerance.tight(actual.get(i), expected.getDouble(i)));
        }
    }

    // A trivial CostFunction for deterministic testing — mirror of the C++ probe's
    // SumCostFunction: value = sum of parameters.
    private static final class SumCostFunction extends CostFunction {
        @Override public double value(final Array x) {
            double s = 0.0;
            for (int i = 0; i < x.size(); i++) s += x.get(i);
            return s;
        }
        @Override public Array values(final Array x) { return x; }
    }

    @Test
    public void project_4params_2fixed() {
        final Array params = arr(1.0, 2.0, 3.0, 4.0);
        final boolean[] fix = { false, true, false, true };
        final Projection p = new Projection(params, fix);
        assertArrayTight("project_4params_2fixed", p.project(params));
    }

    @Test
    public void include_4params_2fixed() {
        final Array params = arr(1.0, 2.0, 3.0, 4.0);
        final boolean[] fix = { false, true, false, true };
        final Projection p = new Projection(params, fix);
        final Array free = arr(10.0, 30.0);
        assertArrayTight("include_4params_2fixed", p.include(free));
    }

    @Test
    public void project_allFree() {
        final Array params = arr(10.0, 20.0, 30.0);
        final boolean[] fix = { false, false, false };
        final Projection p = new Projection(params, fix);
        assertArrayTight("project_allFree", p.project(params));
    }

    @Test
    public void project_defaultFix_nullArg() {
        // Passing null as fixParameters → behaves like empty C++ vector →
        // defaults to all-free.
        final Array params = arr(5.0, 7.0);
        final Projection p = new Projection(params, null);
        assertArrayTight("project_defaultFix", p.project(params));
    }

    @Test
    public void project_defaultFix_emptyArray() {
        // Passing an empty boolean[] → same behavior as null.
        final Array params = arr(5.0, 7.0);
        final Projection p = new Projection(params, new boolean[0]);
        assertArrayTight("project_defaultFix", p.project(params));
    }

    @Test
    public void projectedCostFunction_value() {
        final Array params = arr(1.0, 2.0, 3.0, 4.0);
        final boolean[] fix = { false, true, false, true };
        final ProjectedCostFunction pcf =
                new ProjectedCostFunction(new SumCostFunction(), params, fix);
        final Array free = arr(100.0, 300.0);
        final double v = pcf.value(free);
        assertTrue("projectedCostFunction_value: " + v,
                Tolerance.tight(v, REF.getCase("projectedCostFunction_value").expectedDouble()));
    }
}

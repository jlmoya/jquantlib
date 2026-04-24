/*
 Copyright (C) 2026 Jose Moya

 This source code is released under the BSD License.
 JQuantLib is based on QuantLib. http://quantlib.org/
 */
package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.Constraint;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LineSearch;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.PositiveConstraint;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link LineSearch#update} against QuantLib C++ v1.42.1.
 * Reference from
 * {@code migration-harness/cpp/probes/math/optimization/linesearch_probe.cpp}.
 */
public class LineSearchTest {

    private static final ReferenceReader REF =
            ReferenceReader.load("math/optimization/linesearch");

    /** Minimal concrete subclass — {@code evaluate} is unused by these tests. */
    private static final class StubLineSearch extends LineSearch {
        @Override
        public double evaluate(final Problem P, final EndCriteria.Type[] ecType,
                               final EndCriteria endCriteria, final double t_ini) {
            return 0.0;
        }
    }

    private static Array arr(final double... values) {
        final Array a = new Array(values.length);
        for (int i = 0; i < values.length; i++) {
            a.set(i, values[i]);
        }
        return a;
    }

    private static void assertArrayTight(final String ctx, final Array actual,
                                          final JSONArray expected) {
        assertEquals(ctx + ": size", expected.length(), actual.size());
        for (int i = 0; i < expected.length(); i++) {
            assertTrue(ctx + "[" + i + "] expected=" + expected.getDouble(i)
                            + " actual=" + actual.get(i),
                    Tolerance.tight(actual.get(i), expected.getDouble(i)));
        }
    }

    @Test
    public void update_noConstraint_mutatesParams() {
        final LineSearch ls = new StubLineSearch();
        final Array params = arr(1.0, 2.0, 3.0);
        final Array direction = arr(0.1, 0.2, 0.3);
        final Constraint nc = new NoConstraint();
        final double diff = ls.update(params, direction, 2.0, nc);

        final JSONObject expected = (JSONObject) REF.getCase("update_noConstraint").expectedRaw();
        assertTrue("diff mismatch", Tolerance.tight(diff, expected.getDouble("diff")));
        assertArrayTight("update_noConstraint.paramsOut", params, expected.getJSONArray("paramsOut"));
    }

    @Test
    public void update_positiveConstraint_halvesUntilValid() {
        final LineSearch ls = new StubLineSearch();
        final Array params = arr(1.0, 1.0);
        final Array direction = arr(-1.0, -1.0);
        final Constraint pc = new PositiveConstraint();
        final double diff = ls.update(params, direction, 3.0, pc);

        final JSONObject expected =
                (JSONObject) REF.getCase("update_positiveConstraint").expectedRaw();
        assertTrue("diff expected=" + expected.getDouble("diff") + " actual=" + diff,
                Tolerance.tight(diff, expected.getDouble("diff")));
        assertArrayTight("update_positiveConstraint.paramsOut",
                params, expected.getJSONArray("paramsOut"));
    }

    @Test
    public void succeedDefaultsToTrue() {
        final LineSearch ls = new StubLineSearch();
        assertEquals(true, ls.succeed());
    }
}

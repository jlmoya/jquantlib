/*
 Copyright (C) 2026 Jose Moya

 This source code is released under the BSD License.
 JQuantLib is based on QuantLib. http://quantlib.org/
 */
package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.optimization.LeastSquareFunction;
import org.jquantlib.math.optimization.LeastSquareProblem;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link LeastSquareFunction} against QuantLib C++ v1.42.1.
 * Reference from
 * {@code migration-harness/cpp/probes/math/optimization/leastsquare_probe.cpp}.
 */
public class LeastSquareTest {

    private static final ReferenceReader REF =
            ReferenceReader.load("math/optimization/leastsquare");

    /** Mirrors the C++ probe's TinyProblem exactly. */
    private static final class TinyProblem extends LeastSquareProblem {
        @Override public int size() { return 2; }

        @Override
        public void targetAndValue(final Array x, final Array target, final Array fct2fit) {
            target.set(0, 1.0); target.set(1, 2.0);
            fct2fit.set(0, 2.0 * x.get(0));
            fct2fit.set(1, x.get(0) + x.get(1));
        }

        @Override
        public void targetValueAndGradient(final Array x, final Matrix grad_fct2fit,
                                           final Array target, final Array fct2fit) {
            targetAndValue(x, target, fct2fit);
            grad_fct2fit.set(0, 0, 2.0); grad_fct2fit.set(0, 1, 0.0);
            grad_fct2fit.set(1, 0, 1.0); grad_fct2fit.set(1, 1, 1.0);
        }
    }

    private static Array arr(final double... values) {
        final Array a = new Array(values.length);
        for (int i = 0; i < values.length; i++) a.set(i, values[i]);
        return a;
    }

    private static void assertArrayTight(final String ctx, final Array actual,
                                          final JSONArray expected) {
        assertEquals(ctx + " size", expected.length(), actual.size());
        for (int i = 0; i < expected.length(); i++) {
            assertTrue(ctx + "[" + i + "] expected=" + expected.getDouble(i)
                            + " actual=" + actual.get(i),
                    Tolerance.tight(actual.get(i), expected.getDouble(i)));
        }
    }

    @Test
    public void value_perfectFit() {
        final LeastSquareFunction lsf = new LeastSquareFunction(new TinyProblem());
        final double v = lsf.value(arr(0.5, 1.5));
        assertTrue("value at perfect fit should be 0",
                Tolerance.tight(v, REF.getCase("value_perfectFit").expectedDouble()));
    }

    @Test
    public void value_atOrigin() {
        final LeastSquareFunction lsf = new LeastSquareFunction(new TinyProblem());
        final double v = lsf.value(arr(0.0, 0.0));
        assertTrue("value at origin = 1+4 = 5",
                Tolerance.tight(v, REF.getCase("value_atOrigin").expectedDouble()));
    }

    @Test
    public void values_atOrigin() {
        final LeastSquareFunction lsf = new LeastSquareFunction(new TinyProblem());
        final Array vs = lsf.values(arr(0.0, 0.0));
        assertArrayTight("values_atOrigin", vs, REF.getCase("values_atOrigin").expectedArray());
    }

    @Test
    public void gradient_atOrigin() {
        final LeastSquareFunction lsf = new LeastSquareFunction(new TinyProblem());
        final Array g = new Array(2);
        lsf.gradient(g, arr(0.0, 0.0));
        assertArrayTight("gradient_atOrigin", g, REF.getCase("gradient_atOrigin").expectedArray());
    }

    @Test
    public void valueAndGradient_atOneTenth() {
        final LeastSquareFunction lsf = new LeastSquareFunction(new TinyProblem());
        final Array g = new Array(2);
        final double v = lsf.valueAndGradient(g, arr(0.1, 0.1));
        final JSONObject expected =
                (JSONObject) REF.getCase("valueAndGradient_atOneTenth").expectedRaw();
        assertTrue("value mismatch",
                Tolerance.tight(v, expected.getDouble("value")));
        assertArrayTight("valueAndGradient.gradient", g, expected.getJSONArray("gradient"));
    }
}

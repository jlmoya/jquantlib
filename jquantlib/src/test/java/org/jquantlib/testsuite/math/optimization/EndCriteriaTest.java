/*
 Copyright (C) 2026 Jose Moya

 This source code is released under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.math.optimization;

import static org.junit.Assert.assertEquals;

import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.EndCriteria.Type;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.junit.Test;

/**
 * Cross-validation of {@link EndCriteria} against QuantLib C++ v1.42.1.
 * Reference values from
 * {@code migration-harness/cpp/probes/math/optimization/endcriteria_probe.cpp}.
 *
 * <p>Test-shape pattern: {@code Type[]} one-element arrays wrap the mutable
 * ecType that C++ passes by reference; Java's pass-by-value for enums means
 * we emulate the reference by mutating the array element.
 */
public class EndCriteriaTest {

    private static final ReferenceReader REF =
            ReferenceReader.load("math/optimization/endcriteria");

    /** Standard EndCriteria mirroring the probe's {@code make()} helper. */
    private static EndCriteria make() {
        return new EndCriteria(100, 10, 1e-8, 1e-8, 1e-8);
    }

    private static Type typeFromOrdinal(final int o) {
        return Type.values()[o];
    }

    // ---- checkMaxIterations ----

    @Test
    public void checkMaxIterations_below() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkMaxIterations(5, t);
        assertExpectedBoolAndType("checkMaxIterations_below", result, t[0]);
    }

    @Test
    public void checkMaxIterations_atLimit() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkMaxIterations(100, t);
        assertExpectedBoolAndType("checkMaxIterations_atLimit", result, t[0]);
    }

    @Test
    public void checkMaxIterations_above() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkMaxIterations(200, t);
        assertExpectedBoolAndType("checkMaxIterations_above", result, t[0]);
    }

    // ---- checkStationaryPoint ----

    @Test
    public void checkStationaryPoint_largeDiff() {
        final EndCriteria ec = make();
        final int[] stat = { 5 };
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryPoint(1.0, 2.0, stat, t);
        assertExpectedBoolStatAndType("checkStationaryPoint_largeDiff", result, stat[0], t[0]);
    }

    @Test
    public void checkStationaryPoint_accumulates() {
        final EndCriteria ec = make();
        final int[] stat = { 5 };
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryPoint(1.0, 1.0 + 1e-10, stat, t);
        assertExpectedBoolStatAndType("checkStationaryPoint_accumulates", result, stat[0], t[0]);
    }

    @Test
    public void checkStationaryPoint_triggers() {
        final EndCriteria ec = make();
        final int[] stat = { 10 };
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryPoint(1.0, 1.0 + 1e-10, stat, t);
        assertExpectedBoolStatAndType("checkStationaryPoint_triggers", result, stat[0], t[0]);
    }

    // ---- checkStationaryFunctionValue ----

    @Test
    public void checkStationaryFunctionValue_largeDiff() {
        final EndCriteria ec = make();
        final int[] stat = { 3 };
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryFunctionValue(5.0, 2.0, stat, t);
        assertExpectedBoolStatAndType("checkStationaryFunctionValue_largeDiff", result, stat[0], t[0]);
    }

    @Test
    public void checkStationaryFunctionValue_triggers() {
        final EndCriteria ec = make();
        final int[] stat = { 10 };
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryFunctionValue(2.0, 2.0 + 1e-10, stat, t);
        assertExpectedBoolStatAndType("checkStationaryFunctionValue_triggers", result, stat[0], t[0]);
    }

    // ---- checkStationaryFunctionAccuracy ----

    @Test
    public void checkStationaryFunctionAccuracy_notPositive() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryFunctionAccuracy(1e-20, false, t);
        assertExpectedBoolAndType("checkStationaryFunctionAccuracy_notPositive", result, t[0]);
    }

    @Test
    public void checkStationaryFunctionAccuracy_largeF() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryFunctionAccuracy(1.0, true, t);
        assertExpectedBoolAndType("checkStationaryFunctionAccuracy_largeF", result, t[0]);
    }

    @Test
    public void checkStationaryFunctionAccuracy_triggers() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkStationaryFunctionAccuracy(1e-20, true, t);
        assertExpectedBoolAndType("checkStationaryFunctionAccuracy_triggers", result, t[0]);
    }

    // ---- checkZeroGradientNorm ----

    @Test
    public void checkZeroGradientNorm_aboveEps() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkZeroGradientNorm(1.0, t);
        assertExpectedBoolAndType("checkZeroGradientNorm_aboveEps", result, t[0]);
    }

    @Test
    public void checkZeroGradientNorm_belowEps() {
        final EndCriteria ec = make();
        final Type[] t = { Type.None };
        final boolean result = ec.checkZeroGradientNorm(1e-20, t);
        assertExpectedBoolAndType("checkZeroGradientNorm_belowEps", result, t[0]);
    }

    // ---- operator() / get ----

    @Test
    public void operator_call_noTrigger() {
        final EndCriteria ec = make();
        final int[] stat = { 0 };
        final Type[] t = { Type.None };
        final boolean result = ec.get(5, stat, false, 5.0, 2.0, 2.0, 1.0, t);
        assertExpectedBoolStatAndType("operator_call_noTrigger", result, stat[0], t[0]);
    }

    @Test
    public void operator_call_maxIter() {
        final EndCriteria ec = make();
        final int[] stat = { 0 };
        final Type[] t = { Type.None };
        final boolean result = ec.get(100, stat, false, 5.0, 2.0, 2.0, 1.0, t);
        assertExpectedBoolStatAndType("operator_call_maxIter", result, stat[0], t[0]);
    }

    // ---- succeeded ----

    @Test
    public void succeeded_StationaryPoint() {
        assertEquals(
                ((Boolean) REF.getCase("succeeded_StationaryPoint").expectedRaw()).booleanValue(),
                EndCriteria.succeeded(Type.StationaryPoint));
    }

    @Test
    public void succeeded_MaxIterations() {
        assertEquals(
                ((Boolean) REF.getCase("succeeded_MaxIterations").expectedRaw()).booleanValue(),
                EndCriteria.succeeded(Type.MaxIterations));
    }

    @Test
    public void succeeded_StationaryFunctionAccuracy() {
        assertEquals(
                ((Boolean) REF.getCase("succeeded_StationaryFunctionAccuracy").expectedRaw())
                        .booleanValue(),
                EndCriteria.succeeded(Type.StationaryFunctionAccuracy));
    }

    @Test
    public void succeeded_None() {
        assertEquals(
                ((Boolean) REF.getCase("succeeded_None").expectedRaw()).booleanValue(),
                EndCriteria.succeeded(Type.None));
    }

    // ---- enum ordinal cross-check ----

    @Test
    public void enum_order_matchesCpp() {
        final org.json.JSONArray expected = REF.getCase("enum_order").expectedArray();
        assertEquals(Type.values().length, expected.length());
        for (int i = 0; i < expected.length(); i++) {
            assertEquals("enum ordinal at index " + i,
                    expected.getInt(i), Type.values()[i].ordinal());
        }
    }

    // ---- helpers ----

    private static void assertExpectedBoolAndType(final String caseName,
                                                  final boolean actualResult,
                                                  final Type actualType) {
        final org.json.JSONObject expected =
                (org.json.JSONObject) REF.getCase(caseName).expectedRaw();
        assertEquals(caseName + ".result", expected.getBoolean("result"), actualResult);
        assertEquals(caseName + ".ecType",
                typeFromOrdinal(expected.getInt("ecTypeOut")), actualType);
    }

    private static void assertExpectedBoolStatAndType(final String caseName,
                                                      final boolean actualResult,
                                                      final int actualStat,
                                                      final Type actualType) {
        final org.json.JSONObject expected =
                (org.json.JSONObject) REF.getCase(caseName).expectedRaw();
        assertEquals(caseName + ".result", expected.getBoolean("result"), actualResult);
        assertEquals(caseName + ".stat", expected.getInt("statOut"), actualStat);
        assertEquals(caseName + ".ecType",
                typeFromOrdinal(expected.getInt("ecTypeOut")), actualType);
    }
}

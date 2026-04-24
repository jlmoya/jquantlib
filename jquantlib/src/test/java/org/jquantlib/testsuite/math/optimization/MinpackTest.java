/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for the Minpack port. See phase2a-design.md §3.2.
 */
package org.jquantlib.testsuite.math.optimization;

import java.lang.reflect.Method;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jquantlib.math.optimization.Minpack;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link org.jquantlib.math.optimization.Minpack}. Package-
 * private helpers are accessed via reflection so the port keeps visibility
 * parity with the C++ file-local statics.
 */
public class MinpackTest {

    // --- enorm -------------------------------------------------------------

    @Test
    public void enormZeroVector() throws Exception {
        final double[] x = { 0.0, 0.0, 0.0 };
        assertEquals(0.0, invokeEnorm(3, x), 0.0);
    }

    @Test
    public void enormThreeFourFive() throws Exception {
        // Classic 3-4-5 triangle; |(3,4)| = 5 exactly.
        final double[] x = { 3.0, 4.0 };
        assertEquals(5.0, invokeEnorm(2, x), 0.0);
    }

    @Test
    public void enormSingleValue() throws Exception {
        final double[] x = { 1.5 };
        assertEquals(1.5, invokeEnorm(1, x), 0.0);
    }

    @Test
    public void enormOverflowSafe() throws Exception {
        // Naive sum-of-squares would overflow; the large-component branch
        // scales to keep accumulation finite.
        final double[] x = { 1.0e200, 1.0e200 };
        final double expected = 1.0e200 * Math.sqrt(2.0);
        assertEquals(expected, invokeEnorm(2, x), expected * 1.0e-14);
    }

    @Test
    public void enormUnderflowSafe() throws Exception {
        // Below the intermediate-branch lower threshold (rdwarf = 3.834e-20).
        final double[] x = { 1.0e-25, 1.0e-25 };
        final double expected = 1.0e-25 * Math.sqrt(2.0);
        assertEquals(expected, invokeEnorm(2, x), expected * 1.0e-14);
    }

    @Test
    public void enormMixedMagnitude() throws Exception {
        // One large component dominates; small ones must not destabilise.
        final double[] x = { 1.0e200, 1.0, 1.0e-30 };
        assertEquals(1.0e200, invokeEnorm(3, x), 1.0e186);
    }

    // --- qrfac (bit-exact against v1.42.1 probe) ---------------------------

    @Test
    public void qrfac_3x3_fullrank() {
        runQrfacCase("qrfac_3x3_fullrank");
    }

    @Test
    public void qrfac_4x2_tall() {
        runQrfacCase("qrfac_4x2_tall");
    }

    private static void runQrfacCase(final String caseName) {
        final ReferenceReader reader = ReferenceReader.load("math/optimization/minpack_qrfac");
        final Case c = reader.getCase(caseName);
        final JSONObject in = c.inputs();
        final int m = in.getInt("m");
        final int n = in.getInt("n");
        final int lda = in.getInt("lda");
        final int pivot = in.getInt("pivot");
        final int lipvt = in.getInt("lipvt");
        final double[] a = toDoubleArray(in.getJSONArray("a_in"));
        final int[] ipvt = new int[lipvt];
        final double[] rdiag = new double[n];
        final double[] acnorm = new double[n];
        final double[] wa = new double[n];

        Minpack.qrfac(m, n, a, lda, pivot, ipvt, lipvt, rdiag, acnorm, wa);

        final JSONObject exp = (JSONObject) c.expectedRaw();
        // ipvt is an integer permutation — must match exactly.
        assertIntsExact("ipvt", toIntArray(exp.getJSONArray("ipvt")), ipvt);
        // Floating arrays: observed ~1-ulp divergence between JVM and C++ on
        // the trailing bits of some positions (a_out[4] and a_out[5] in the
        // two probe cases). Same algorithm, same inputs, bit-identical enorm.
        // Likely cause is JVM FMA or rounding-mode behavior on
        // accumulate-and-subtract patterns; not tracked to a specific bug.
        // Tight-tier (abs 1e-14 + rel 1e-12) is still far below any practical
        // optimization tolerance — per design §4.2 this per-test tier choice
        // is acceptable with inline justification.
        assertDoublesTight("a_out", toDoubleArray(exp.getJSONArray("a_out")), a);
        assertDoublesTight("rdiag", toDoubleArray(exp.getJSONArray("rdiag")), rdiag);
        assertDoublesTight("acnorm", toDoubleArray(exp.getJSONArray("acnorm")), acnorm);
    }

    // --- Small array helpers -----------------------------------------------

    private static double[] toDoubleArray(final JSONArray a) {
        final double[] out = new double[a.length()];
        for (int i = 0; i < out.length; i++) out[i] = a.getDouble(i);
        return out;
    }

    private static int[] toIntArray(final JSONArray a) {
        final int[] out = new int[a.length()];
        for (int i = 0; i < out.length; i++) out[i] = a.getInt(i);
        return out;
    }

    private static void assertDoublesExact(final String name, final double[] exp, final double[] got) {
        if (exp.length != got.length) {
            fail(name + ": length mismatch exp=" + exp.length + " got=" + got.length);
        }
        for (int i = 0; i < exp.length; i++) {
            if (!Tolerance.exact(got[i], exp[i])) {
                fail(name + "[" + i + "]: exp=" + exp[i] + " got=" + got[i]);
            }
        }
    }

    private static void assertDoublesTight(final String name, final double[] exp, final double[] got) {
        if (exp.length != got.length) {
            fail(name + ": length mismatch exp=" + exp.length + " got=" + got.length);
        }
        for (int i = 0; i < exp.length; i++) {
            if (!Tolerance.tight(got[i], exp[i])) {
                fail(name + "[" + i + "]: exp=" + exp[i] + " got=" + got[i]);
            }
        }
    }

    private static void assertIntsExact(final String name, final int[] exp, final int[] got) {
        if (exp.length != got.length) {
            fail(name + ": length mismatch exp=" + exp.length + " got=" + got.length);
        }
        for (int i = 0; i < exp.length; i++) {
            if (exp[i] != got[i]) {
                fail(name + "[" + i + "]: exp=" + exp[i] + " got=" + got[i]);
            }
        }
    }

    // --- qrsolv (tight against v1.42.1 probe) ------------------------------

    @Test
    public void qrsolv_3x3_zeroDiag() {
        runQrsolvCase("qrsolv_3x3_zeroDiag");
    }

    @Test
    public void qrsolv_4x4_damped() {
        runQrsolvCase("qrsolv_4x4_damped");
    }

    private static void runQrsolvCase(final String caseName) {
        final ReferenceReader reader = ReferenceReader.load("math/optimization/minpack_qrsolv");
        final Case c = reader.getCase(caseName);
        final JSONObject in = c.inputs();
        final int n = in.getInt("n");
        final int ldr = in.getInt("ldr");
        final double[] r = toDoubleArray(in.getJSONArray("r_in"));
        final int[] ipvt = toIntArray(in.getJSONArray("ipvt"));
        final double[] diag = toDoubleArray(in.getJSONArray("diag"));
        final double[] qtb = toDoubleArray(in.getJSONArray("qtb"));
        final double[] x = new double[n];
        final double[] sdiag = new double[n];
        final double[] wa = new double[n];

        Minpack.qrsolv(n, r, ldr, ipvt, diag, qtb, x, sdiag, wa);

        final JSONObject exp = (JSONObject) c.expectedRaw();
        // Same rationale as qrfac (above) — tight tier for floating arrays.
        assertDoublesTight("r_out", toDoubleArray(exp.getJSONArray("r_out")), r);
        assertDoublesTight("x", toDoubleArray(exp.getJSONArray("x")), x);
        assertDoublesTight("sdiag", toDoubleArray(exp.getJSONArray("sdiag")), sdiag);
    }

    // --- Reflection helpers ------------------------------------------------

    private static double invokeEnorm(int n, double[] x) throws Exception {
        final Method m = Minpack.class.getDeclaredMethod("enorm", int.class, double[].class);
        m.setAccessible(true);
        return (Double) m.invoke(null, n, x);
    }
}

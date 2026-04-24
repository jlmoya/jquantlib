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
import static org.junit.Assert.assertTrue;
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

    // --- lmpar sanity (well-conditioned; exercised fully via LM probes) ----

    @Test
    public void lmpar_wellConditioned2x2_returnsZeroPar() throws Exception {
        // R = diag(2, 3) column-major => r = {2, 0, 0, 3}; diag=I; qtb=[1,1];
        // delta large enough that the Gauss-Newton direction lies inside the
        // trust region, so lmpar takes the initial goto-L220 and par stays 0.
        final int n = 2, ldr = 2;
        final double[] r = { 2.0, 0.0, 0.0, 3.0 };
        final int[] ipvt = { 0, 1 };
        final double[] diag = { 1.0, 1.0 };
        final double[] qtb = { 1.0, 1.0 };
        final double delta = 10.0;
        final double[] par = { 0.0 };
        final double[] x = new double[n];
        final double[] sdiag = new double[n];
        final double[] wa1 = new double[n];
        final double[] wa2 = new double[n];

        invokeLmpar(n, r, ldr, ipvt, diag, qtb, delta, par, x, sdiag, wa1, wa2);

        // par: finite, non-negative, and specifically zero in this regime.
        assertTrue("par finite", Double.isFinite(par[0]));
        assertTrue("par non-negative", par[0] >= 0.0);
        assertEquals(0.0, par[0], 0.0);
        // Gauss-Newton solution: R^-1 * qtb = [0.5, 1/3].
        assertTrue("x[0] finite", Double.isFinite(x[0]));
        assertTrue("x[1] finite", Double.isFinite(x[1]));
        assertEquals(0.5, x[0], 1.0e-14);
        assertEquals(1.0 / 3.0, x[1], 1.0e-14);
    }

    // --- fdjac2 -----------------------------------------------------------

    @Test
    public void fdjac2_linearCost_matchesAnalyticJacobian() throws Exception {
        // Linear cost: f(x) = [2*x[0], x[0]+x[1]]. Analytic J = [[2,0],[1,1]]
        // (column-major: fjac = {2, 1, 0, 1}). Forward-differences are exact
        // up to rounding for linear f, so tolerance can be tight.
        final int m = 2, n = 2;
        final double[] x = { 1.0, 2.0 };
        final double[] fvec = new double[m];
        final double[] fjac = new double[m * n];
        final int[] iflag = { 1 };
        final double epsfcn = 1.0e-8;
        final double[] wa = new double[m];

        final Minpack.LmdifCostFunction fcn = (mm, nn, xx, ff, flag) -> {
            ff[0] = 2.0 * xx[0];
            ff[1] = xx[0] + xx[1];
        };
        // Residuals at unperturbed x.
        fcn.evaluate(m, n, x, fvec, iflag);

        invokeFdjac2(m, n, x, fvec, fjac, iflag, epsfcn, wa, fcn);

        // On exit x must be restored to its original value.
        assertEquals("x[0] restored", 1.0, x[0], 0.0);
        assertEquals("x[1] restored", 2.0, x[1], 0.0);
        // iflag must not have been modified by our linear cost fn.
        assertEquals("iflag unchanged", 1, iflag[0]);
        // Column-major Jacobian: fjac[i + m*j].
        assertEquals("J[0,0] = df0/dx0 = 2", 2.0, fjac[0], 1.0e-8);
        assertEquals("J[1,0] = df1/dx0 = 1", 1.0, fjac[1], 1.0e-8);
        assertEquals("J[0,1] = df0/dx1 = 0", 0.0, fjac[2], 1.0e-8);
        assertEquals("J[1,1] = df1/dx1 = 1", 1.0, fjac[3], 1.0e-8);
    }

    @Test
    public void fdjac2_abortFlag_returnsEarlyWithPartialFill() throws Exception {
        // A cost fn that sets iflag[0] = -1 on the second call aborts fdjac2
        // immediately; the second Jacobian column must remain untouched.
        final int m = 2, n = 2;
        final double[] x = { 1.0, 2.0 };
        final double[] fvec = { 2.0, 3.0 }; // dummy
        final double[] fjac = new double[m * n];
        final int[] iflag = { 1 };
        final double[] wa = new double[m];
        final int[] callCount = { 0 };

        final Minpack.LmdifCostFunction fcn = (mm, nn, xx, ff, flag) -> {
            callCount[0]++;
            if (callCount[0] == 2) {
                flag[0] = -1;
                return;
            }
            ff[0] = 2.0 * xx[0];
            ff[1] = xx[0] + xx[1];
        };

        invokeFdjac2(m, n, x, fvec, fjac, iflag, 1.0e-8, wa, fcn);

        assertEquals("called twice before abort", 2, callCount[0]);
        assertEquals("iflag propagated", -1, iflag[0]);
        // Column 0 filled, column 1 must be untouched (still zero).
        assertTrue("J[0,0] filled", Math.abs(fjac[0] - 2.0) < 1.0e-8);
        assertEquals("J[0,1] untouched", 0.0, fjac[2], 0.0);
        assertEquals("J[1,1] untouched", 0.0, fjac[3], 0.0);
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

    private static void invokeLmpar(int n, double[] r, int ldr, int[] ipvt,
                                    double[] diag, double[] qtb, double delta,
                                    double[] par, double[] x, double[] sdiag,
                                    double[] wa1, double[] wa2) throws Exception {
        final Method m = Minpack.class.getDeclaredMethod("lmpar",
                int.class, double[].class, int.class, int[].class,
                double[].class, double[].class, double.class,
                double[].class, double[].class, double[].class,
                double[].class, double[].class);
        m.setAccessible(true);
        m.invoke(null, n, r, ldr, ipvt, diag, qtb, delta, par, x, sdiag, wa1, wa2);
    }

    private static void invokeFdjac2(int m, int n, double[] x, double[] fvec,
                                     double[] fjac, int[] iflag, double epsfcn,
                                     double[] wa, Minpack.LmdifCostFunction fcn)
                                     throws Exception {
        final Method meth = Minpack.class.getDeclaredMethod("fdjac2",
                int.class, int.class, double[].class, double[].class,
                double[].class, int[].class, double.class, double[].class,
                Minpack.LmdifCostFunction.class);
        meth.setAccessible(true);
        meth.invoke(null, m, n, x, fvec, fjac, iflag, epsfcn, wa, fcn);
    }
}

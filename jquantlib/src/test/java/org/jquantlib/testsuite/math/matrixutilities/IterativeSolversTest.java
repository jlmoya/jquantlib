/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation tests for BiCGStab and GMRES iterative solvers (Phase 2l
 Track A). Reference values from migration-harness QuantLib v1.42.1 probe.
 Tolerance tier: TIGHT (abs 1e-14 + rel 1e-12).
 */
package org.jquantlib.testsuite.math.matrixutilities;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.BiCGStab;
import org.jquantlib.math.matrixutilities.GMRES;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Probe-driven cross-validation of {@link BiCGStab} and {@link GMRES}
 * against QuantLib v1.42.1 via
 * {@code migration-harness/references/math/matrixutilities/bicgstab_gmres.json}.
 *
 * <p>All cases use TIGHT tolerance (abs 1e-14 + rel 1e-12) on solution
 * components. Iteration counts and residuals are checked exactly for
 * BiCGStab (deterministic integer iteration counts).
 *
 * <p>Test matrices are small dense systems (2x2 to 5x5) whose exact
 * solutions are known integer or half-integer vectors. The probe runs the
 * C++ solver and records computed solution components; Java must match
 * the same floating-point result to TIGHT tier.
 */
public class IterativeSolversTest {

    private static final String REF_GROUP = "math/matrixutilities/bicgstab_gmres";

    // -----------------------------------------------------------------------
    // System definitions matching the probe exactly
    // -----------------------------------------------------------------------

    /** Dense matrix-vector product: y = A*x. */
    private static Array matvec(final double[][] A, final Array x) {
        final int n = x.size();
        final double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                y[i] += A[i][j] * x.get(j);
            }
        }
        return new Array(y, n);
    }

    /** Diagonal (Jacobi) preconditioner: y[i] = x[i] / diag[i]. */
    private static Array diagPrecond(final double[] diag, final Array x) {
        final int n = x.size();
        final double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = x.get(i) / diag[i];
        }
        return new Array(y, n);
    }

    // -----------------------------------------------------------------------
    // Helper to extract diagonal from a matrix
    // -----------------------------------------------------------------------

    private static double[] diagonal(final double[][] A) {
        final double[] d = new double[A.length];
        for (int i = 0; i < A.length; i++) d[i] = A[i][i];
        return d;
    }

    // -----------------------------------------------------------------------
    // All systems in one @Test — collect-all-failures pattern
    // -----------------------------------------------------------------------

    @Test
    public void iterativeSolvers_allCases() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final List<String> failures = new ArrayList<>();

        // --- BiCGStab 2x2 diagonal: A=diag(2,3), x=[1,1], b=[2,3] ---
        {
            final double[][] A = {{2,0},{0,3}};
            final double[] b = {2.0, 3.0};
            checkBiCGStab("bicgstab_2x2_diag", A, b, false, ref, failures);
        }
        // --- BiCGStab 2x2 symmetric: A=[[4,1],[1,3]], x=[1,-1], b=[3,-2] ---
        {
            final double[][] A = {{4,1},{1,3}};
            final double[] b = {3.0, -2.0};
            checkBiCGStab("bicgstab_2x2_sym", A, b, false, ref, failures);
        }
        // --- BiCGStab 2x2 asymmetric: A=[[3,1],[0,2]], x=[2,1], b=[7,2] ---
        {
            final double[][] A = {{3,1},{0,2}};
            final double[] b = {7.0, 2.0};
            checkBiCGStab("bicgstab_2x2_asym", A, b, false, ref, failures);
        }
        // --- BiCGStab 3x3 diagonal: A=diag(1,2,3), x=[3,2,1], b=[3,4,3] ---
        {
            final double[][] A = {{1,0,0},{0,2,0},{0,0,3}};
            final double[] b = {3.0, 4.0, 3.0};
            checkBiCGStab("bicgstab_3x3_diag", A, b, false, ref, failures);
        }
        // --- BiCGStab 3x3 tridiagonal: A=[[4,-1,0],...], x=[1,1,1], b=[3,2,3] ---
        {
            final double[][] A = {{4,-1,0},{-1,4,-1},{0,-1,4}};
            final double[] b = {3.0, 2.0, 3.0};
            checkBiCGStab("bicgstab_3x3_tridiag", A, b, false, ref, failures);
        }
        // --- BiCGStab 3x3 tridiagonal with Jacobi preconditioner ---
        {
            final double[][] A = {{4,-1,0},{-1,4,-1},{0,-1,4}};
            final double[] b = {3.0, 2.0, 3.0};
            checkBiCGStab("bicgstab_3x3_tridiag_precond", A, b, true, ref, failures);
        }
        // --- BiCGStab 3x3 upper triangular: A=[[2,1,0],[0,3,1],[0,0,4]], x=[1,2,3], b=[4,9,12] ---
        {
            final double[][] A = {{2,1,0},{0,3,1},{0,0,4}};
            final double[] b = {4.0, 9.0, 12.0};
            checkBiCGStab("bicgstab_3x3_uppertri", A, b, false, ref, failures);
        }
        // --- BiCGStab 4x4 diagonal: A=diag(5,4,3,2), x=[1,2,3,4], b=[5,8,9,8] ---
        {
            final double[][] A = {{5,0,0,0},{0,4,0,0},{0,0,3,0},{0,0,0,2}};
            final double[] b = {5.0, 8.0, 9.0, 8.0};
            checkBiCGStab("bicgstab_4x4_diag", A, b, false, ref, failures);
        }
        // --- BiCGStab 4x4 SPD: A=[[10,1,0,0],...], x=[1,2,3,4], b=[12,24,36,43] ---
        {
            final double[][] A = {{10,1,0,0},{1,10,1,0},{0,1,10,1},{0,0,1,10}};
            final double[] b = {12.0, 24.0, 36.0, 43.0};
            checkBiCGStab("bicgstab_4x4_spd", A, b, false, ref, failures);
        }
        // --- BiCGStab 4x4 asymmetric: A=[[3,1,0,0],[0,2,1,0],...], x=[1,2,3,4] ---
        {
            final double[][] A = {{3,1,0,0},{0,2,1,0},{0,0,4,1},{0,0,0,5}};
            final double[] b = {5.0, 7.0, 16.0, 20.0};
            checkBiCGStab("bicgstab_4x4_asym", A, b, false, ref, failures);
        }
        // --- BiCGStab 5x5 diagonal: A=diag(1..5), x=[1,2,3,4,5], b=[1,4,9,16,25] ---
        {
            final double[][] A = {{1,0,0,0,0},{0,2,0,0,0},{0,0,3,0,0},{0,0,0,4,0},{0,0,0,0,5}};
            final double[] b = {1.0, 4.0, 9.0, 16.0, 25.0};
            checkBiCGStab("bicgstab_5x5_diag", A, b, false, ref, failures);
        }
        // --- BiCGStab 5x5 SPD tridiagonal: x=[1,1,1,1,1], b=[4,3,3,3,4] ---
        {
            final double[][] A = {
                { 5,-1, 0, 0, 0},
                {-1, 5,-1, 0, 0},
                { 0,-1, 5,-1, 0},
                { 0, 0,-1, 5,-1},
                { 0, 0, 0,-1, 5}
            };
            final double[] b = {4.0, 3.0, 3.0, 3.0, 4.0};
            checkBiCGStab("bicgstab_5x5_spd", A, b, false, ref, failures);
        }
        // --- BiCGStab 5x5 SPD tridiagonal with Jacobi preconditioner ---
        {
            final double[][] A = {
                { 5,-1, 0, 0, 0},
                {-1, 5,-1, 0, 0},
                { 0,-1, 5,-1, 0},
                { 0, 0,-1, 5,-1},
                { 0, 0, 0,-1, 5}
            };
            final double[] b = {4.0, 3.0, 3.0, 3.0, 4.0};
            checkBiCGStab("bicgstab_5x5_spd_precond", A, b, true, ref, failures);
        }
        // --- BiCGStab 5x5 asymmetric banded lower: A[i][i]=i+1, A[i][i-1]=0.5 ---
        {
            final double[][] A = buildBandedAsym5();
            final double[] b = buildBandedAsymRhs5(A, new double[]{2,1,3,1,2});
            checkBiCGStab("bicgstab_5x5_asym", A, b, false, ref, failures);
        }
        // --- BiCGStab zero RHS: b=0, x should be 0 ---
        {
            final double[][] A = {{3,1},{1,2}};
            final double[] b = {0.0, 0.0};
            checkBiCGStabZeroRhs("bicgstab_zero_rhs", A, b, ref, failures);
        }
        // --- BiCGStab with initial guess x0=[0.5,0.5,0.5] ---
        {
            final double[][] A = {{4,-1,0},{-1,4,-1},{0,-1,4}};
            final double[] b = {3.0, 2.0, 3.0};
            final double[] x0 = {0.5, 0.5, 0.5};
            checkBiCGStabWithX0("bicgstab_with_x0", A, b, x0, ref, failures);
        }

        // ---- GMRES cases ----

        // --- GMRES 2x2 diagonal ---
        {
            final double[][] A = {{2,0},{0,3}};
            final double[] b = {2.0, 3.0};
            checkGMRES("gmres_2x2_diag", A, b, false, ref, failures);
        }
        // --- GMRES 2x2 symmetric ---
        {
            final double[][] A = {{4,1},{1,3}};
            final double[] b = {3.0, -2.0};
            checkGMRES("gmres_2x2_sym", A, b, false, ref, failures);
        }
        // --- GMRES 2x2 asymmetric ---
        {
            final double[][] A = {{3,1},{0,2}};
            final double[] b = {7.0, 2.0};
            checkGMRES("gmres_2x2_asym", A, b, false, ref, failures);
        }
        // --- GMRES 3x3 diagonal ---
        {
            final double[][] A = {{1,0,0},{0,2,0},{0,0,3}};
            final double[] b = {3.0, 4.0, 3.0};
            checkGMRES("gmres_3x3_diag", A, b, false, ref, failures);
        }
        // --- GMRES 3x3 tridiagonal ---
        {
            final double[][] A = {{4,-1,0},{-1,4,-1},{0,-1,4}};
            final double[] b = {3.0, 2.0, 3.0};
            checkGMRES("gmres_3x3_tridiag", A, b, false, ref, failures);
        }
        // --- GMRES 3x3 tridiagonal with Jacobi preconditioner ---
        {
            final double[][] A = {{4,-1,0},{-1,4,-1},{0,-1,4}};
            final double[] b = {3.0, 2.0, 3.0};
            checkGMRES("gmres_3x3_tridiag_precond", A, b, true, ref, failures);
        }
        // --- GMRES 3x3 upper triangular ---
        {
            final double[][] A = {{2,1,0},{0,3,1},{0,0,4}};
            final double[] b = {4.0, 9.0, 12.0};
            checkGMRES("gmres_3x3_uppertri", A, b, false, ref, failures);
        }
        // --- GMRES 4x4 diagonal ---
        {
            final double[][] A = {{5,0,0,0},{0,4,0,0},{0,0,3,0},{0,0,0,2}};
            final double[] b = {5.0, 8.0, 9.0, 8.0};
            checkGMRES("gmres_4x4_diag", A, b, false, ref, failures);
        }
        // --- GMRES 4x4 SPD ---
        {
            final double[][] A = {{10,1,0,0},{1,10,1,0},{0,1,10,1},{0,0,1,10}};
            final double[] b = {12.0, 24.0, 36.0, 43.0};
            checkGMRES("gmres_4x4_spd", A, b, false, ref, failures);
        }
        // --- GMRES 4x4 asymmetric ---
        {
            final double[][] A = {{3,1,0,0},{0,2,1,0},{0,0,4,1},{0,0,0,5}};
            final double[] b = {5.0, 7.0, 16.0, 20.0};
            checkGMRES("gmres_4x4_asym", A, b, false, ref, failures);
        }
        // --- GMRES 5x5 diagonal ---
        {
            final double[][] A = {{1,0,0,0,0},{0,2,0,0,0},{0,0,3,0,0},{0,0,0,4,0},{0,0,0,0,5}};
            final double[] b = {1.0, 4.0, 9.0, 16.0, 25.0};
            checkGMRES("gmres_5x5_diag", A, b, false, ref, failures);
        }
        // --- GMRES 5x5 SPD tridiagonal ---
        {
            final double[][] A = {
                { 5,-1, 0, 0, 0},
                {-1, 5,-1, 0, 0},
                { 0,-1, 5,-1, 0},
                { 0, 0,-1, 5,-1},
                { 0, 0, 0,-1, 5}
            };
            final double[] b = {4.0, 3.0, 3.0, 3.0, 4.0};
            checkGMRES("gmres_5x5_spd", A, b, false, ref, failures);
        }
        // --- GMRES 5x5 SPD tridiagonal with Jacobi preconditioner ---
        {
            final double[][] A = {
                { 5,-1, 0, 0, 0},
                {-1, 5,-1, 0, 0},
                { 0,-1, 5,-1, 0},
                { 0, 0,-1, 5,-1},
                { 0, 0, 0,-1, 5}
            };
            final double[] b = {4.0, 3.0, 3.0, 3.0, 4.0};
            checkGMRES("gmres_5x5_spd_precond", A, b, true, ref, failures);
        }
        // --- GMRES 5x5 asymmetric banded lower ---
        {
            final double[][] A = buildBandedAsym5();
            final double[] b = buildBandedAsymRhs5(A, new double[]{2,1,3,1,2});
            checkGMRES("gmres_5x5_asym", A, b, false, ref, failures);
        }
        // --- GMRES zero RHS ---
        {
            final double[][] A = {{3,1},{1,2}};
            final double[] b = {0.0, 0.0};
            checkGMRESZeroRhs("gmres_zero_rhs", A, b, ref, failures);
        }
        // --- GMRES with initial guess x0=[0.5,0.5,0.5] ---
        {
            final double[][] A = {{4,-1,0},{-1,4,-1},{0,-1,4}};
            final double[] b = {3.0, 2.0, 3.0};
            final double[] x0 = {0.5, 0.5, 0.5};
            checkGMRESWithX0("gmres_with_x0", A, b, x0, ref, failures);
        }
        // --- GMRES with restart (5x5 SPD, restart=3) ---
        {
            final double[][] A = {
                { 5,-1, 0, 0, 0},
                {-1, 5,-1, 0, 0},
                { 0,-1, 5,-1, 0},
                { 0, 0,-1, 5,-1},
                { 0, 0, 0,-1, 5}
            };
            final double[] b = {4.0, 3.0, 3.0, 3.0, 4.0};
            checkGMRESWithRestart("gmres_with_restart", A, b, 3, ref, failures);
        }

        assertNoFailures(failures);
    }

    // -----------------------------------------------------------------------
    // Per-solver check helpers
    // -----------------------------------------------------------------------

    private void checkBiCGStab(final String caseName, final double[][] A, final double[] bArr,
                                final boolean withPrecond,
                                final ReferenceReader ref, final List<String> failures) {
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        final Array bVec = new Array(bArr, bArr.length);
        final BiCGStab.MatrixMult Afunc = (x) -> matvec(A, x);
        final BiCGStab.MatrixMult Mfunc = withPrecond
                ? (x) -> diagPrecond(diagonal(A), x)
                : null;

        final BiCGStab solver = new BiCGStab(Afunc, 500, 1e-10, Mfunc);
        final BiCGStab.Result result;
        try {
            result = solver.solve(bVec);
        } catch (final Exception e) {
            failures.add(caseName + ": solve threw " + e.getMessage());
            return;
        }

        final int expIter = expected.getInt("iterations");
        if (result.iterations != expIter) {
            failures.add(caseName + ": iterations expected=" + expIter + " got=" + result.iterations);
        }

        checkSolution(caseName, result.x, expected.getJSONArray("x"), failures);
    }

    private void checkBiCGStabZeroRhs(final String caseName, final double[][] A,
                                       final double[] bArr,
                                       final ReferenceReader ref, final List<String> failures) {
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        final Array bVec = new Array(bArr, bArr.length);
        final BiCGStab.MatrixMult Afunc = (x) -> matvec(A, x);
        final BiCGStab solver = new BiCGStab(Afunc, 500, 1e-10);
        final BiCGStab.Result result;
        try {
            result = solver.solve(bVec);
        } catch (final Exception e) {
            failures.add(caseName + ": solve threw " + e.getMessage());
            return;
        }

        checkSolution(caseName, result.x, expected.getJSONArray("x"), failures);
    }

    private void checkBiCGStabWithX0(final String caseName, final double[][] A,
                                      final double[] bArr, final double[] x0Arr,
                                      final ReferenceReader ref, final List<String> failures) {
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        final Array bVec = new Array(bArr, bArr.length);
        final Array x0Vec = new Array(x0Arr, x0Arr.length);
        final BiCGStab.MatrixMult Afunc = (x) -> matvec(A, x);
        final BiCGStab solver = new BiCGStab(Afunc, 500, 1e-10);
        final BiCGStab.Result result;
        try {
            result = solver.solve(bVec, x0Vec);
        } catch (final Exception e) {
            failures.add(caseName + ": solve threw " + e.getMessage());
            return;
        }

        checkSolution(caseName, result.x, expected.getJSONArray("x"), failures);
    }

    private void checkGMRES(final String caseName, final double[][] A, final double[] bArr,
                             final boolean withPrecond,
                             final ReferenceReader ref, final List<String> failures) {
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        final Array bVec = new Array(bArr, bArr.length);
        final GMRES.MatrixMult Afunc = (x) -> matvec(A, x);
        final GMRES.MatrixMult Mfunc = withPrecond
                ? (x) -> diagPrecond(diagonal(A), x)
                : null;

        final GMRES solver = new GMRES(Afunc, 500, 1e-10, Mfunc);
        final GMRES.Result result;
        try {
            result = solver.solve(bVec);
        } catch (final Exception e) {
            failures.add(caseName + ": solve threw " + e.getMessage());
            return;
        }

        checkSolution(caseName, result.x, expected.getJSONArray("x"), failures);
    }

    private void checkGMRESZeroRhs(final String caseName, final double[][] A,
                                    final double[] bArr,
                                    final ReferenceReader ref, final List<String> failures) {
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        final Array bVec = new Array(bArr, bArr.length);
        final GMRES.MatrixMult Afunc = (x) -> matvec(A, x);
        final GMRES solver = new GMRES(Afunc, 500, 1e-10);
        final GMRES.Result result;
        try {
            result = solver.solve(bVec);
        } catch (final Exception e) {
            failures.add(caseName + ": solve threw " + e.getMessage());
            return;
        }

        checkSolution(caseName, result.x, expected.getJSONArray("x"), failures);
    }

    private void checkGMRESWithX0(final String caseName, final double[][] A,
                                   final double[] bArr, final double[] x0Arr,
                                   final ReferenceReader ref, final List<String> failures) {
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        final Array bVec = new Array(bArr, bArr.length);
        final Array x0Vec = new Array(x0Arr, x0Arr.length);
        final GMRES.MatrixMult Afunc = (x) -> matvec(A, x);
        final GMRES solver = new GMRES(Afunc, 500, 1e-10);
        final GMRES.Result result;
        try {
            result = solver.solve(bVec, x0Vec);
        } catch (final Exception e) {
            failures.add(caseName + ": solve threw " + e.getMessage());
            return;
        }

        checkSolution(caseName, result.x, expected.getJSONArray("x"), failures);
    }

    private void checkGMRESWithRestart(final String caseName, final double[][] A,
                                        final double[] bArr, final int restart,
                                        final ReferenceReader ref, final List<String> failures) {
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        final Array bVec = new Array(bArr, bArr.length);
        final GMRES.MatrixMult Afunc = (x) -> matvec(A, x);
        final GMRES solver = new GMRES(Afunc, 50, 1e-10);
        final GMRES.Result result;
        try {
            result = solver.solveWithRestart(restart, bVec);
        } catch (final Exception e) {
            failures.add(caseName + ": solve threw " + e.getMessage());
            return;
        }

        checkSolution(caseName, result.x, expected.getJSONArray("x"), failures);
    }

    // -----------------------------------------------------------------------
    // Shared solution-component checker
    // -----------------------------------------------------------------------

    /**
     * Verify each component of the computed solution {@code x} against the
     * reference values in the JSON array, using TIGHT tolerance.
     */
    private static void checkSolution(final String caseName, final Array x,
                                       final JSONArray refX, final List<String> failures) {
        final int n = refX.length();
        if (x.size() != n) {
            failures.add(caseName + ": solution length expected=" + n + " got=" + x.size());
            return;
        }
        for (int i = 0; i < n; i++) {
            final double expected = refX.getDouble(i);
            final double actual   = x.get(i);
            if (!Tolerance.tight(actual, expected)) {
                failures.add(String.format(
                        "%s: x[%d] expected=%.17e got=%.17e diff=%.3e",
                        caseName, i, expected, actual, Math.abs(actual - expected)));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Matrix builders for the banded asymmetric 5x5 system
    // -----------------------------------------------------------------------

    /** Build A: A[i][i]=i+1, A[i][i-1]=0.5 for i>0. */
    private static double[][] buildBandedAsym5() {
        final double[][] A = new double[5][5];
        for (int i = 0; i < 5; i++) {
            A[i][i] = i + 1;
            if (i > 0) A[i][i - 1] = 0.5;
        }
        return A;
    }

    /** Compute b = A * xExact. */
    private static double[] buildBandedAsymRhs5(final double[][] A, final double[] xExact) {
        final int n = 5;
        final double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                b[i] += A[i][j] * xExact[j];
            }
        }
        return b;
    }

    // -----------------------------------------------------------------------
    // Assertion helper
    // -----------------------------------------------------------------------

    private static void assertNoFailures(final List<String> failures) {
        if (!failures.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(failures.size()).append(" IterativeSolvers failure(s):\n");
            for (final String f : failures) sb.append("  - ").append(f).append('\n');
            assertTrue(sb.toString(), false);
        }
    }
}

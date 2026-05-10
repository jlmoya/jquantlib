/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation tests for SparseMatrix (CSR) and SparseILUPreconditioner
 (Phase 5b.5). Reference values from migration-harness QuantLib v1.42.1
 sparsematrix_probe (boost::numeric::ublas::compressed_matrix). Tolerance
 tier: TIGHT (abs 1e-14 + rel 1e-12) for prod and ILU factor entries; LOOSE
 (1e-8) for ILU(p) apply (since p<full level admits some ILU error and
 numerical accumulation through forward/back substitution).
 */
package org.jquantlib.testsuite.math.matrixutilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.SparseILUPreconditioner;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.Tolerance;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation tests for {@link SparseMatrix} and
 * {@link SparseILUPreconditioner} against
 * {@code migration-harness/references/math/matrixutilities/sparsematrix.json}.
 *
 * <p>Phase 5b.5.
 */
public class SparseMatrixTest {

    private static final String REF_GROUP = "math/matrixutilities/sparsematrix";

    /** Reconstruct a SparseMatrix from a JSON entry list. */
    private static SparseMatrix matrixFromJson(final JSONObject jm) {
        final int rows = jm.getInt("rows");
        final int cols = jm.getInt("columns");
        final SparseMatrix m = new SparseMatrix(rows, cols);
        final JSONArray entries = jm.getJSONArray("entries");
        for (int k = 0; k < entries.length(); k++) {
            final JSONObject e = entries.getJSONObject(k);
            m.set(e.getInt("i"), e.getInt("j"), e.getDouble("v"));
        }
        return m;
    }

    /** Convert JSON array to Array. */
    private static Array arrayFromJson(final JSONArray a) {
        final double[] d = new double[a.length()];
        for (int i = 0; i < a.length(); i++) d[i] = a.getDouble(i);
        return new Array(d, d.length);
    }

    /** Sum of squared deltas between two SparseMatrix at every (i,j). */
    private static void assertMatricesTight(final SparseMatrix expected,
                                            final SparseMatrix actual,
                                            final String tag) {
        assertEquals(tag + ": rows mismatch", expected.rows(), actual.rows());
        assertEquals(tag + ": cols mismatch", expected.columns(), actual.columns());
        for (int i = 0; i < expected.rows(); i++) {
            for (int j = 0; j < expected.columns(); j++) {
                final double e = expected.get(i, j);
                final double a = actual.get(i, j);
                assertTrue(tag + " entry (" + i + "," + j + "): expected="
                                + e + " actual=" + a,
                        Tolerance.tight(a, e));
            }
        }
    }

    private static void assertArraysTight(final Array expected, final Array actual,
                                          final String tag) {
        assertEquals(tag + ": size mismatch", expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            final double e = expected.get(i);
            final double a = actual.get(i);
            assertTrue(tag + " [" + i + "]: expected=" + e + " actual=" + a,
                    Tolerance.tight(a, e));
        }
    }

    private static void assertArraysLoose(final Array expected, final Array actual,
                                          final String tag) {
        assertEquals(tag + ": size mismatch", expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            final double e = expected.get(i);
            final double a = actual.get(i);
            assertTrue(tag + " [" + i + "]: expected=" + e + " actual=" + a,
                    Tolerance.loose(a, e));
        }
    }

    // -----------------------------------------------------------------------
    // Direct unit tests (no reference file dependency)

    @Test
    public void testEmptyMatrix() {
        final SparseMatrix m = new SparseMatrix(5, 5);
        assertEquals(5, m.rows());
        assertEquals(5, m.columns());
        assertEquals(0, m.nrElements());
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                assertEquals("empty cell", 0.0, m.get(i, j), 0.0);
            }
        }
    }

    @Test
    public void testGetSet() {
        final SparseMatrix m = new SparseMatrix(3, 3);
        m.set(0, 0, 1.0);
        m.set(1, 1, 2.0);
        m.set(2, 2, 3.0);
        m.set(0, 2, 5.0);
        m.set(2, 0, 7.0);
        assertEquals(1.0, m.get(0, 0), 0.0);
        assertEquals(2.0, m.get(1, 1), 0.0);
        assertEquals(3.0, m.get(2, 2), 0.0);
        assertEquals(5.0, m.get(0, 2), 0.0);
        assertEquals(7.0, m.get(2, 0), 0.0);
        assertEquals(0.0, m.get(0, 1), 0.0);
        assertEquals(0.0, m.get(1, 0), 0.0);
        assertEquals(5, m.nrElements());
    }

    @Test
    public void testZeroAssignment() {
        // Mirrors the C++ testSparseMatrixZeroAssignment behavior — zero
        // assignments still allocate entries.
        final SparseMatrix m = new SparseMatrix(5, 5);
        assertEquals("initial entry count", 0, m.nrElements());

        m.set(0, 0, 0.0);
        m.set(1, 2, 0.0);
        assertEquals("after two zero assignments", 2, m.nrElements());

        m.set(1, 3, 1.0);
        assertEquals("after one non-zero assignment", 3, m.nrElements());

        m.set(1, 3, 0.0);
        assertEquals("overwrite with zero must NOT remove entry", 3, m.nrElements());
    }

    @Test
    public void testCopyConstructor() {
        final SparseMatrix a = new SparseMatrix(3, 3);
        a.set(0, 0, 1.0); a.set(1, 1, 2.0); a.set(2, 2, 3.0);
        final SparseMatrix b = new SparseMatrix(a);
        // Mutations to b do not affect a
        b.set(0, 1, 99.0);
        assertEquals(0.0, a.get(0, 1), 0.0);
        assertEquals(99.0, b.get(0, 1), 0.0);
    }

    @Test
    public void testAddAt() {
        final SparseMatrix m = new SparseMatrix(3, 3);
        m.addAt(0, 0, 1.0);
        m.addAt(0, 0, 2.0);
        m.addAt(1, 2, 5.0);
        assertEquals(3.0, m.get(0, 0), 0.0);
        assertEquals(5.0, m.get(1, 2), 0.0);
        assertEquals(2, m.nrElements());
    }

    @Test
    public void testAddAssign() {
        // Mirrors testSpareMatrixReference accumulation: build several refs
        // independently then sum into one, and check matches the in-place
        // accumulation.
        final SparseMatrix expected = new SparseMatrix(3, 3);
        expected.addAt(0, 0, 1.0);
        expected.addAt(0, 0, 2.0);
        expected.addAt(1, 2, 5.0);
        expected.addAt(2, 1, 7.0);

        final SparseMatrix m1 = new SparseMatrix(3, 3);
        m1.addAt(0, 0, 1.0);
        m1.addAt(1, 2, 5.0);

        final SparseMatrix m2 = new SparseMatrix(3, 3);
        m2.addAt(0, 0, 2.0);
        m2.addAt(2, 1, 7.0);

        final SparseMatrix sum = m1.add(m2);

        assertMatricesTight(expected, sum, "addAssign");
    }

    @Test
    public void testProdShapeMismatch() {
        final SparseMatrix m = new SparseMatrix(3, 5);
        try {
            m.mul(new Array(new double[] {1.0, 2.0, 3.0}));
            org.junit.Assert.fail("expected IllegalArgumentException for shape mismatch");
        } catch (final IllegalArgumentException ok) {
            // expected
        }
    }

    // -----------------------------------------------------------------------
    // Reference-driven tests

    @Test
    public void testProd5x5SPD() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final ReferenceReader.Case c = ref.getCase("prod_5x5_spd");
        final SparseMatrix A = matrixFromJson(c.inputs().getJSONObject("matrix"));
        final Array x = arrayFromJson(c.inputs().getJSONArray("x"));
        final Array y = A.mul(x);
        final Array expected = arrayFromJson(((JSONObject) c.expectedRaw()).getJSONArray("y"));
        assertArraysTight(expected, y, "prod_5x5_spd");
    }

    @Test
    public void testProd5x5Asym() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final ReferenceReader.Case c = ref.getCase("prod_5x5_asym");
        final SparseMatrix A = matrixFromJson(c.inputs().getJSONObject("matrix"));
        final Array x = arrayFromJson(c.inputs().getJSONArray("x"));
        final Array y = A.mul(x);
        final Array expected = arrayFromJson(((JSONObject) c.expectedRaw()).getJSONArray("y"));
        assertArraysTight(expected, y, "prod_5x5_asym");
    }

    @Test
    public void testILU1Factors() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final ReferenceReader.Case c = ref.getCase("ilu1_5x5_spd_factors");
        final SparseMatrix A = matrixFromJson(c.inputs().getJSONObject("matrix"));
        final SparseILUPreconditioner ilu = new SparseILUPreconditioner(A, 1);
        final JSONObject expected = (JSONObject) c.expectedRaw();
        final SparseMatrix expL = matrixFromJson(expected.getJSONObject("L"));
        final SparseMatrix expU = matrixFromJson(expected.getJSONObject("U"));
        assertMatricesTight(expL, ilu.L(), "ilu1_5x5_spd_factors L");
        assertMatricesTight(expU, ilu.U(), "ilu1_5x5_spd_factors U");
    }

    @Test
    public void testILU1ApplySPD() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final ReferenceReader.Case c = ref.getCase("ilu1_5x5_spd_apply");
        final SparseMatrix A = matrixFromJson(c.inputs().getJSONObject("matrix"));
        final Array b = arrayFromJson(c.inputs().getJSONArray("b"));
        final SparseILUPreconditioner ilu = new SparseILUPreconditioner(A, 1);
        final Array y = ilu.apply(b);
        final Array expected = arrayFromJson(((JSONObject) c.expectedRaw()).getJSONArray("y"));
        assertArraysTight(expected, y, "ilu1_5x5_spd_apply");
    }

    @Test
    public void testILU4ApplySPD() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final ReferenceReader.Case c = ref.getCase("ilu4_5x5_spd_apply");
        final SparseMatrix A = matrixFromJson(c.inputs().getJSONObject("matrix"));
        final Array b = arrayFromJson(c.inputs().getJSONArray("b"));
        final SparseILUPreconditioner ilu = new SparseILUPreconditioner(A, 4);
        final Array y = ilu.apply(b);
        final Array expected = arrayFromJson(((JSONObject) c.expectedRaw()).getJSONArray("y"));
        assertArraysTight(expected, y, "ilu4_5x5_spd_apply");
    }

    @Test
    public void testILU1ApplyAsym() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final ReferenceReader.Case c = ref.getCase("ilu1_5x5_asym_apply");
        final SparseMatrix A = matrixFromJson(c.inputs().getJSONObject("matrix"));
        final Array b = arrayFromJson(c.inputs().getJSONArray("b"));
        final SparseILUPreconditioner ilu = new SparseILUPreconditioner(A, 1);
        final Array y = ilu.apply(b);
        final Array expected = arrayFromJson(((JSONObject) c.expectedRaw()).getJSONArray("y"));
        // Banded lower triangular admits exact ILU(1) solution → tight.
        assertArraysTight(expected, y, "ilu1_5x5_asym_apply");
    }

    @Test
    public void testZeroAssignmentReference() {
        // Cross-check against the probe's recorded counts (sanity check of
        // both sides agreeing on the boost compressed_matrix semantics).
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);
        final JSONObject expected = (JSONObject) ref.getCase("zero_assignment").expectedRaw();

        final SparseMatrix m = new SparseMatrix(5, 5);
        assertEquals("count_initial", expected.getInt("count_initial"), m.nrElements());

        m.set(0, 0, 0.0);
        m.set(1, 2, 0.0);
        assertEquals("count_after_two_zeros", expected.getInt("count_after_two_zeros"),
                m.nrElements());

        m.set(1, 3, 1.0);
        assertEquals("count_after_one_value", expected.getInt("count_after_one_value"),
                m.nrElements());

        m.set(1, 3, 0.0);
        assertEquals("count_after_overwrite_with_zero",
                expected.getInt("count_after_overwrite_with_zero"), m.nrElements());
    }

    // -----------------------------------------------------------------------
    // Smoke test: BiCGStab + GMRES with SparseMatrix + ILU preconditioner
    // (mirrors the testBiCGstab and testGMRES path exercised by FdmLinearOpTest)

    @Test
    public void testBiCGStabWithSparseILU() {
        // Build the same kind of test matrix the C++ FdmLinearOpTest uses,
        // but at smaller size to keep the test fast.  Reuses the 5x5 SPD
        // tridiagonal and verifies BiCGStab+ILU converges below tol.
        final SparseMatrix A = new SparseMatrix(5, 5);
        A.set(0, 0, 5.0); A.set(0, 1, -1.0);
        A.set(1, 0, -1.0); A.set(1, 1, 5.0); A.set(1, 2, -1.0);
        A.set(2, 1, -1.0); A.set(2, 2, 5.0); A.set(2, 3, -1.0);
        A.set(3, 2, -1.0); A.set(3, 3, 5.0); A.set(3, 4, -1.0);
        A.set(4, 3, -1.0); A.set(4, 4, 5.0);

        final Array b = new Array(new double[] {4.0, 3.0, 3.0, 3.0, 4.0});
        final Array xExact = new Array(new double[] {1.0, 1.0, 1.0, 1.0, 1.0});

        final SparseILUPreconditioner ilu = new SparseILUPreconditioner(A, 4);

        final org.jquantlib.math.matrixutilities.BiCGStab solver
                = new org.jquantlib.math.matrixutilities.BiCGStab(
                        x -> A.mul(x), 50, 1.0e-10, x -> ilu.apply(x));
        final org.jquantlib.math.matrixutilities.BiCGStab.Result res = solver.solve(b);

        // Exact solution is [1,1,1,1,1].
        for (int i = 0; i < 5; i++) {
            final double err = Math.abs(res.x.get(i) - xExact.get(i));
            assertTrue("BiCGStab+ILU x[" + i + "]=" + res.x.get(i) + " (err=" + err + ")",
                    err < 1.0e-9);
        }
    }

    @Test
    public void testGMRESWithSparseILU() {
        // Same SPD tridiagonal setup, GMRES path.
        final SparseMatrix A = new SparseMatrix(5, 5);
        A.set(0, 0, 5.0); A.set(0, 1, -1.0);
        A.set(1, 0, -1.0); A.set(1, 1, 5.0); A.set(1, 2, -1.0);
        A.set(2, 1, -1.0); A.set(2, 2, 5.0); A.set(2, 3, -1.0);
        A.set(3, 2, -1.0); A.set(3, 3, 5.0); A.set(3, 4, -1.0);
        A.set(4, 3, -1.0); A.set(4, 4, 5.0);

        final Array b = new Array(new double[] {4.0, 3.0, 3.0, 3.0, 4.0});
        final Array xExact = new Array(new double[] {1.0, 1.0, 1.0, 1.0, 1.0});

        final SparseILUPreconditioner ilu = new SparseILUPreconditioner(A, 4);

        final org.jquantlib.math.matrixutilities.GMRES solver
                = new org.jquantlib.math.matrixutilities.GMRES(
                        x -> A.mul(x), 50, 1.0e-10, x -> ilu.apply(x));
        final org.jquantlib.math.matrixutilities.GMRES.Result res = solver.solve(b);

        for (int i = 0; i < 5; i++) {
            final double err = Math.abs(res.x.get(i) - xExact.get(i));
            assertTrue("GMRES+ILU x[" + i + "]=" + res.x.get(i) + " (err=" + err + ")",
                    err < 1.0e-9);
        }
    }
}

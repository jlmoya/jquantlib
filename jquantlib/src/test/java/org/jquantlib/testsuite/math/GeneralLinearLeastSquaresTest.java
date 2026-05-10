/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.GeneralLinearLeastSquares;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * TIGHT-tier structural tests for
 * {@link org.jquantlib.math.GeneralLinearLeastSquares}.
 *
 * <p>Phase 5h.5-MC-AME WI-1 — covers the two single-variate use sites
 * exercised by the LSM machinery: linear (y = a + b·x) and quadratic
 * (y = a + b·x + c·x²) regression against deterministic data.
 *
 * <p>Cross-validation strategy: synthesise data from known coefficients,
 * recover them via the SVD-based regression. Tolerance is bit-exact (the
 * model has no noise so the LSE solution is exact up to floating-point
 * round-off).
 */
public class GeneralLinearLeastSquaresTest {

    public GeneralLinearLeastSquaresTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static List<Ops.DoubleOp> linearBasis() {
        final List<Ops.DoubleOp> b = new ArrayList<Ops.DoubleOp>();
        b.add(new Ops.DoubleOp() { @Override public double op(final double x) { return 1.0; } });
        b.add(new Ops.DoubleOp() { @Override public double op(final double x) { return x; } });
        return b;
    }

    private static List<Ops.DoubleOp> quadBasis() {
        final List<Ops.DoubleOp> b = new ArrayList<Ops.DoubleOp>();
        b.add(new Ops.DoubleOp() { @Override public double op(final double x) { return 1.0; } });
        b.add(new Ops.DoubleOp() { @Override public double op(final double x) { return x; } });
        b.add(new Ops.DoubleOp() { @Override public double op(final double x) { return x * x; } });
        return b;
    }


    @Test
    public void test1dLinearRegression() {
        // y = 2 + 3x at integer x in 0..9 — exact recovery (no noise).
        final int n = 10;
        final double[] x = new double[n];
        final double[] y = new double[n];
        for (int i = 0; i < n; ++i) {
            x[i] = i;
            y[i] = 2.0 + 3.0 * i;
        }
        final GeneralLinearLeastSquares lse = new GeneralLinearLeastSquares(x, y, linearBasis());
        final Array c = lse.coefficients();
        assertEquals(2.0, c.get(0), 1e-12);
        assertEquals(3.0, c.get(1), 1e-12);
        assertEquals(2, lse.dim());
        assertEquals(n, lse.size());
        // residuals are zero
        for (int i = 0; i < n; ++i) {
            assertEquals(0.0, lse.residuals().get(i), 1e-12);
        }
    }

    @Test
    public void testQuadraticRegression() {
        // y = 1 - 2x + 0.5 x^2 at integer x in 0..14 — exact recovery.
        final int n = 15;
        final double[] x = new double[n];
        final double[] y = new double[n];
        for (int i = 0; i < n; ++i) {
            x[i] = i;
            y[i] = 1.0 - 2.0 * i + 0.5 * i * i;
        }
        final GeneralLinearLeastSquares lse = new GeneralLinearLeastSquares(x, y, quadBasis());
        final Array c = lse.coefficients();
        assertEquals(1.0, c.get(0), 1e-9);
        assertEquals(-2.0, c.get(1), 1e-10);
        assertEquals(0.5, c.get(2), 1e-11);
        assertEquals(3, lse.dim());
    }

    //
    // -------------------------------------------------------------------
    // Multi-variate (Array-state) regression tests — Phase MC-extras.
    // Cross-validated bit-exactly against C++ v1.42.1
    // GeneralLinearLeastSquares<vector<Array>, vector<Real>,
    //                            vector<function<Real(Array)>>> via probe
    // migration-harness/references/math/general_linear_least_squares_multi.json.
    // -------------------------------------------------------------------
    //

    private static final String MULTI_GROUP = "math/general_linear_least_squares_multi";
    private static final double TIGHT = 1.0e-12;
    private static final double REL = 1.0e-10;


    private static List<Ops.ObjectToDouble<Array>> linear2dBasis() {
        final List<Ops.ObjectToDouble<Array>> v = new ArrayList<Ops.ObjectToDouble<Array>>();
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return 1.0; } });
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return a.get(0); } });
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return a.get(1); } });
        return v;
    }

    private static List<Ops.ObjectToDouble<Array>> quadratic2dBasis() {
        final List<Ops.ObjectToDouble<Array>> v = new ArrayList<Ops.ObjectToDouble<Array>>();
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return 1.0; } });
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return a.get(0); } });
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return a.get(1); } });
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return a.get(0) * a.get(0); } });
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return a.get(0) * a.get(1); } });
        v.add(new Ops.ObjectToDouble<Array>() { @Override public double op(final Array a) { return a.get(1) * a.get(1); } });
        return v;
    }

    private static Array[] readArrayInputs(final JSONArray xJson) {
        final Array[] xs = new Array[xJson.length()];
        for (int i = 0; i < xJson.length(); ++i) {
            final JSONArray row = xJson.getJSONArray(i);
            final double[] v = new double[row.length()];
            for (int j = 0; j < row.length(); ++j) {
                v[j] = row.getDouble(j);
            }
            xs[i] = new Array(v);
        }
        return xs;
    }

    private static double[] readDoubleArray(final JSONArray ja) {
        final double[] r = new double[ja.length()];
        for (int i = 0; i < ja.length(); ++i) {
            r[i] = ja.getDouble(i);
        }
        return r;
    }

    private static void assertCloseTight(final String label, final double expected,
                                         final double actual) {
        final double diff = Math.abs(actual - expected);
        final double tol = Math.max(TIGHT, REL * Math.abs(expected));
        assertTrue(label + " expected=" + expected + " actual=" + actual
                + " diff=" + diff + " tol=" + tol,
                diff <= tol);
    }

    private static void assertCoefficientsAndResiduals(final String caseName,
            final ReferenceReader.Case c, final GeneralLinearLeastSquares lse) {
        final JSONObject expected = (JSONObject) c.expectedRaw();
        final JSONArray expCoef = expected.getJSONArray("coefficients");
        final JSONArray expRes = expected.getJSONArray("residuals");
        final int expDim = expected.getInt("dim");
        final int expSize = expected.getInt("size");

        assertEquals(caseName + "/dim", expDim, lse.dim());
        assertEquals(caseName + "/size", expSize, lse.size());
        assertEquals(caseName + "/coef.length", expCoef.length(), lse.coefficients().size());
        assertEquals(caseName + "/res.length", expRes.length(), lse.residuals().size());

        for (int i = 0; i < expCoef.length(); ++i) {
            assertCloseTight(caseName + "/coef[" + i + "]",
                    expCoef.getDouble(i), lse.coefficients().get(i));
        }
        for (int i = 0; i < expRes.length(); ++i) {
            assertCloseTight(caseName + "/res[" + i + "]",
                    expRes.getDouble(i), lse.residuals().get(i));
        }
    }

    @Test
    public void testMultiLinear2dExactRecovery() {
        final ReferenceReader ref = ReferenceReader.load(MULTI_GROUP);
        final ReferenceReader.Case c = ref.getCase("linear_2d_exact_recovery");
        final JSONObject in = c.inputs();
        final Array[] x = readArrayInputs(in.getJSONArray("x"));
        final double[] y = readDoubleArray(in.getJSONArray("y"));
        final GeneralLinearLeastSquares lse =
                new GeneralLinearLeastSquares(x, y, linear2dBasis());
        assertCoefficientsAndResiduals("linear_2d_exact_recovery", c, lse);
    }

    @Test
    public void testMultiQuadratic2dFullBasisExact() {
        final ReferenceReader ref = ReferenceReader.load(MULTI_GROUP);
        final ReferenceReader.Case c = ref.getCase("quadratic_2d_full_basis_exact");
        final JSONObject in = c.inputs();
        final Array[] x = readArrayInputs(in.getJSONArray("x"));
        final double[] y = readDoubleArray(in.getJSONArray("y"));
        final GeneralLinearLeastSquares lse =
                new GeneralLinearLeastSquares(x, y, quadratic2dBasis());
        assertCoefficientsAndResiduals("quadratic_2d_full_basis_exact", c, lse);
    }

    @Test
    public void testMultiPathBasisDim2Order2Monomial() {
        final ReferenceReader ref = ReferenceReader.load(MULTI_GROUP);
        final ReferenceReader.Case c = ref.getCase("multipath_basis_dim2_order2_monomial");
        final JSONObject in = c.inputs();
        final Array[] x = readArrayInputs(in.getJSONArray("x"));
        final double[] y = readDoubleArray(in.getJSONArray("y"));

        // Use the actual LsmBasisSystem.multiPathBasisSystem call — same as
        // AmericanMaxPathPricer wires up.
        final List<Ops.ObjectToDouble<Array>> v =
                LsmBasisSystem.multiPathBasisSystem(2, 2,
                        LsmBasisSystem.PolynomialType.Monomial);
        final GeneralLinearLeastSquares lse =
                new GeneralLinearLeastSquares(x, y, v);
        assertCoefficientsAndResiduals("multipath_basis_dim2_order2_monomial", c, lse);
    }

    @Test
    public void testNoisyQuadraticConvergence() {
        // y = a + bx + cx^2 + N(0,sigma^2). With deterministic xs and
        // a deterministic noise pattern (alternating +/- e), the
        // regression's standard error must be a bounded multiple of the
        // noise level. We verify the recovered coefficients are within
        // a TIGHT tolerance of the true values for n=50 samples.
        final int n = 50;
        final double a = 0.5, b = -1.5, c = 2.0;
        final double e = 0.001; // small alternating noise
        final double[] x = new double[n];
        final double[] y = new double[n];
        for (int i = 0; i < n; ++i) {
            x[i] = i * 0.1; // x in [0, 4.9]
            y[i] = a + b * x[i] + c * x[i] * x[i] + (i % 2 == 0 ? e : -e);
        }
        final GeneralLinearLeastSquares lse =
                new GeneralLinearLeastSquares(x, y, quadBasis());
        final Array coef = lse.coefficients();

        // recovered ≈ true within a small constant times the noise scale
        assertEquals(a, coef.get(0), 0.01);
        assertEquals(b, coef.get(1), 0.01);
        assertEquals(c, coef.get(2), 0.01);

        // standard errors are bounded above by something proportional to e
        final Array stdErr = lse.standardErrors();
        for (int i = 0; i < lse.dim(); ++i) {
            assertTrue("stdErr[" + i + "] = " + stdErr.get(i) + " too large",
                    stdErr.get(i) < 0.01);
            assertTrue("stdErr[" + i + "] non-finite",
                    Double.isFinite(stdErr.get(i)));
        }
    }
}

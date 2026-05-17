/*
 Copyright (C) 2018 Klaus Spanderen (C++ source-of-truth)
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FirstDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.NthOrderDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.SecondOrderMixedDerivativeOp;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5b.5b body-fill of {@code test-suite/nthorderderivativeop.cpp}
 * v1.42.1.  Tests covering {@code testHigherOrderHestonOptionPricing},
 * {@code testHigherOrderAndRichardsonExtrapolation}, and
 * {@code testCompare*} (which exercise FdHestonVanillaEngine + Richardson
 * extrapolation, both Phase 5j.5+ scope) remain {@code @Ignore}'d.
 *
 * <p>Tolerance: tier TIGHT — {@code 1e-12} absolute (matches C++
 * {@code close_enough} which is 42 * QL_EPSILON ~ 9.32e-15).  Java picks
 * {@code 1e-12} to absorb the dense-conversion {@code Matrix.get} round-trip
 * without losing the Fornberg-coefficient bit-pattern check.
 *
 * <p>Source: {@code test-suite/nthorderderivativeop.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
public class NthOrderDerivativeOpTest {

    private static final double TOL = 1e-12;

    @Test
    public void testSparseMatrixApply() {
        // Java port of C++ testSparseMatrixApply.
        final SparseMatrix sm = new SparseMatrix(5, 7);
        assertEquals("rows", 5, sm.size1());
        assertEquals("cols", 7, sm.size2());

        sm.set(1, 3, 3.0);

        // Array(7, 0.0, 1.0) in C++ = sequence {0, 1, 2, 3, 4, 5, 6}.
        final double[] xData = new double[7];
        for (int i = 0; i < 7; ++i) xData[i] = i;
        final Array x = new Array(xData);

        final Array y = SparseMatrix.prod(sm, x);
        assertEquals("y[0]", 0.0, y.get(0), TOL);
        assertEquals("y[1]", 9.0, y.get(1), TOL);   // 3.0 * 3.0
        assertEquals("y[2]", 0.0, y.get(2), TOL);
        assertEquals("y[3]", 0.0, y.get(3), TOL);
        assertEquals("y[4]", 0.0, y.get(4), TOL);
    }

    @Test
    public void testFirstOrder2PointsApply() {
        // Java port: f(x)=x on [0,1], 6 points; uniform spacing dx=1/5.
        // First-derivative operator with 3-point stencil should give f'(x)=1
        // i.e. 1/dx after the discrete operator action (which scales by 1/dx).
        final double dx = 1.0 / 5.0;
        final NthOrderDerivativeOp op =
            new NthOrderDerivativeOp(0, 1, 3,
                new FdmMesherComposite(new Uniform1dMesher(0.0, 1.0, 6)));

        // Array(6, 0.0, 1.0) = {0, 1, 2, 3, 4, 5}.
        final double[] xBuf = new double[6];
        for (int i = 0; i < 6; ++i) xBuf[i] = i;
        final Array x = new Array(xBuf);
        final Array y = op.apply(x);

        for (int i = 0; i < x.size(); ++i) {
            assertEquals("y[" + i + "]", 1.0 / dx, y.get(i), TOL);
        }
    }

    @Test
    public void testFirstOrder3PointsOnUniformGrid() {
        final double ddx = 1.0 / 0.2;
        final SparseMatrix m =
            new NthOrderDerivativeOp(0, 1, 3,
                new FdmMesherComposite(new Uniform1dMesher(0.0, 1.0, 6))).toSparseMatrix();

        // Interior central row.
        assertEquals(0.0,         m.get(2, 0), TOL);
        assertEquals(-0.5 * ddx,  m.get(2, 1), TOL);
        assertTrue("m(2,2) ~ 0 (structural)", Math.abs(m.get(2, 2)) < 42 * 2.2204460492503131e-16);
        assertEquals( 0.5 * ddx,  m.get(2, 3), TOL);
        assertEquals(0.0,         m.get(2, 4), TOL);
        assertEquals(0.0,         m.get(2, 5), TOL);

        // Forward-biased boundary row.
        assertEquals(-1.5 * ddx,  m.get(0, 0), TOL);
        assertEquals( 2.0 * ddx,  m.get(0, 1), TOL);
        assertEquals(-0.5 * ddx,  m.get(0, 2), TOL);
        assertEquals(0.0,         m.get(0, 3), TOL);
        assertEquals(0.0,         m.get(0, 4), TOL);
        assertEquals(0.0,         m.get(0, 5), TOL);

        // Backward-biased boundary row.
        assertEquals(0.0,         m.get(5, 0), TOL);
        assertEquals(0.0,         m.get(5, 1), TOL);
        assertEquals(0.0,         m.get(5, 2), TOL);
        assertEquals( 0.5 * ddx,  m.get(5, 3), TOL);
        assertEquals(-2.0 * ddx,  m.get(5, 4), TOL);
        assertEquals( 1.5 * ddx,  m.get(5, 5), TOL);
    }

    @Test
    public void testFirstOrder5PointsOnUniformGrid() {
        final double ddx = 1.0 / 0.4;
        final SparseMatrix m =
            new NthOrderDerivativeOp(0, 1, 5,
                new FdmMesherComposite(new Uniform1dMesher(0.0, 2.0, 6))).toSparseMatrix();

        assertEquals( 1.0 / 12.0 * ddx, m.get(2, 0), TOL);
        assertEquals(-2.0 /  3.0 * ddx, m.get(2, 1), TOL);
        assertTrue("m(2,2) ~ 0", Math.abs(m.get(2, 2)) < 42 * 2.2204460492503131e-16);
        assertEquals( 2.0 /  3.0 * ddx, m.get(2, 3), TOL);
        assertEquals(-1.0 / 12.0 * ddx, m.get(2, 4), TOL);
        assertEquals(0.0,               m.get(2, 5), TOL);

        // Forward-biased boundary.
        assertEquals(-25.0 / 12.0 * ddx, m.get(0, 0), TOL);
        assertEquals(  4.0       * ddx, m.get(0, 1), TOL);
        assertEquals( -3.0       * ddx, m.get(0, 2), TOL);
        assertEquals(  4.0 /  3.0 * ddx, m.get(0, 3), TOL);
        assertEquals(-0.25       * ddx, m.get(0, 4), TOL);
        assertEquals(0.0,                m.get(0, 5), TOL);

        // Slid-toward-edge row 1.
        assertEquals(-0.25         * ddx, m.get(1, 0), TOL);
        assertEquals(-5.0 /  6.0   * ddx, m.get(1, 1), TOL);
        assertEquals( 3.0 /  2.0   * ddx, m.get(1, 2), TOL);
        assertEquals(-0.5          * ddx, m.get(1, 3), TOL);
        assertEquals( 1.0 / 12.0   * ddx, m.get(1, 4), TOL);
        assertEquals(0.0,                  m.get(1, 5), TOL);

        // Slid row 4 (mirror of row 1).
        assertEquals( 0.25         * ddx, m.get(4, 5), TOL);
        assertEquals( 5.0 /  6.0   * ddx, m.get(4, 4), TOL);
        assertEquals(-3.0 /  2.0   * ddx, m.get(4, 3), TOL);
        assertEquals( 0.5          * ddx, m.get(4, 2), TOL);
        assertEquals(-1.0 / 12.0   * ddx, m.get(4, 1), TOL);
        assertEquals(0.0,                  m.get(4, 0), TOL);

        // Backward-biased boundary (mirror of row 0).
        assertEquals(0.0,                m.get(5, 0), TOL);
        assertEquals( 0.25       * ddx, m.get(5, 1), TOL);
        assertEquals(-4.0 /  3.0 * ddx, m.get(5, 2), TOL);
        assertEquals( 3.0       * ddx, m.get(5, 3), TOL);
        assertEquals(-4.0       * ddx, m.get(5, 4), TOL);
        assertEquals(25.0 / 12.0 * ddx, m.get(5, 5), TOL);
    }

    @Test
    public void testFirstOrder2PointsOnUniformGrid() {
        final double ddx = 1.0 / 0.2;
        final SparseMatrix m =
            new NthOrderDerivativeOp(0, 1, 2,
                new FdmMesherComposite(new Uniform1dMesher(0.0, 0.6, 4))).toSparseMatrix();

        assertEquals(-ddx, m.get(0, 0), TOL);
        assertEquals( ddx, m.get(0, 1), TOL);
        assertEquals(0.0,  m.get(0, 2), TOL);
        assertEquals(0.0,  m.get(0, 3), TOL);

        assertEquals(-ddx, m.get(1, 0), TOL);
        assertEquals( ddx, m.get(1, 1), TOL);
        assertEquals(0.0,  m.get(1, 2), TOL);
        assertEquals(0.0,  m.get(1, 3), TOL);

        assertEquals(0.0,  m.get(2, 0), TOL);
        assertEquals(-ddx, m.get(2, 1), TOL);
        assertEquals( ddx, m.get(2, 2), TOL);
        assertEquals(0.0,  m.get(2, 3), TOL);

        assertEquals(0.0,  m.get(3, 0), TOL);
        assertEquals(0.0,  m.get(3, 1), TOL);
        assertEquals(-ddx, m.get(3, 2), TOL);
        assertEquals( ddx, m.get(3, 3), TOL);
    }

    @Test
    public void testFirstOrder4PointsOnUniformGrid() {
        final double ddx = 1.0 / 0.2;
        final SparseMatrix m =
            new NthOrderDerivativeOp(0, 1, 4,
                new FdmMesherComposite(new Uniform1dMesher(0.0, 0.6, 4))).toSparseMatrix();

        assertEquals(-11.0 / 6.0 * ddx, m.get(0, 0), TOL);
        assertEquals( 3.0       * ddx, m.get(0, 1), TOL);
        assertEquals(-1.5       * ddx, m.get(0, 2), TOL);
        assertEquals( 1.0 / 3.0 * ddx, m.get(2, 3), TOL);

        assertEquals(-1.0 / 3.0 * ddx, m.get(1, 0), TOL);
        assertEquals(-0.5       * ddx, m.get(1, 1), TOL);
        assertEquals( ddx,            m.get(1, 2), TOL);
        assertEquals(-1.0 / 6.0 * ddx, m.get(1, 3), TOL);

        assertEquals( 1.0 / 6.0 * ddx, m.get(2, 0), TOL);
        assertEquals(-ddx,            m.get(2, 1), TOL);
        assertEquals( 0.5       * ddx, m.get(2, 2), TOL);
        assertEquals( 1.0 / 3.0 * ddx, m.get(2, 3), TOL);

        assertEquals(-1.0 / 3.0 * ddx, m.get(3, 0), TOL);
        assertEquals( 1.5       * ddx, m.get(3, 1), TOL);
        assertEquals(-3.0       * ddx, m.get(3, 2), TOL);
        assertEquals(11.0 / 6.0 * ddx, m.get(3, 3), TOL);
    }

    @Test
    public void testFirstOrder2PointsOn2DimUniformGrid() {
        final double ddx = 1.0 / 0.2;
        final int xGrid = 4;

        final FdmMesherComposite mesher = new FdmMesherComposite(
                new Uniform1dMesher(0.0, 1.0, xGrid),
                new Uniform1dMesher(0.0, 0.4, 3));

        final SparseMatrix m =
            new NthOrderDerivativeOp(1, 1, 2, mesher).toSparseMatrix();

        // Note: the corresponding C++ test reads m(i, i+2*xGrid) for ix in
        // {0, 1} which can be a column == columns() (out-of-bounds in our
        // bound-checked SparseMatrix; boost's compressed_matrix silently
        // returns 0).  We Java-port by guarding the out-of-bounds reads —
        // they're structural zeros by construction (the stencil width is 2
        // and the operator only writes m(i, i) and m(i, i±xGrid)).
        final int n = m.columns();
        for (final FdmLinearOpIterator iter : mesher.layout()) {
            final int i  = iter.index();
            final int ix = iter.coordinates()[1];
            switch (ix) {
              case 0:
                assertEquals(-ddx, m.get(i, i),             TOL);
                assertEquals( ddx, m.get(i, i +     xGrid), TOL);
                if (i + 2 * xGrid < n) {
                    assertEquals( 0.0, m.get(i, i + 2 * xGrid), TOL);
                }
                break;
              case 1:
                assertEquals(-ddx, m.get(i, i -     xGrid), TOL);
                assertEquals( ddx, m.get(i, i),             TOL);
                if (i + 2 * xGrid < n) {
                    assertEquals( 0.0, m.get(i, i + 2 * xGrid), TOL);
                }
                break;
              case 2:
                if (i - 2 * xGrid >= 0) {
                    assertEquals( 0.0, m.get(i, i - 2 * xGrid), TOL);
                }
                assertEquals(-ddx, m.get(i, i -     xGrid), TOL);
                assertEquals( ddx, m.get(i, i),             TOL);
                break;
              default:
                fail("inconsistent coordinate");
            }
        }
    }

    @Test
    public void testSecondOrder3PointsNonUniformGrid() {
        final double[] xValues = { 0.5, 1.0, 2.0, 4.0 };
        final SparseMatrix m =
            new NthOrderDerivativeOp(0, 2, 3,
                new FdmMesherComposite(new Predefined1dMesher(xValues))).toSparseMatrix();

        assertEquals( 8.0 / 3.0, m.get(0, 0), TOL);
        assertEquals(-4.0,       m.get(0, 1), TOL);
        assertEquals( 4.0 / 3.0, m.get(0, 2), TOL);
        assertEquals( 0.0,       m.get(0, 3), TOL);

        assertEquals( 8.0 / 3.0, m.get(1, 0), TOL);
        assertEquals(-4.0,       m.get(1, 1), TOL);
        assertEquals( 4.0 / 3.0, m.get(1, 2), TOL);
        assertEquals( 0.0,       m.get(1, 3), TOL);

        assertEquals( 0.0,       m.get(2, 0), TOL);
        assertEquals( 2.0 / 3.0, m.get(2, 1), TOL);
        assertEquals(-1.0,       m.get(2, 2), TOL);
        assertEquals( 1.0 / 3.0, m.get(2, 3), TOL);

        assertEquals( 0.0,       m.get(3, 0), TOL);
        assertEquals( 2.0 / 3.0, m.get(3, 1), TOL);
        assertEquals(-1.0,       m.get(3, 2), TOL);
        assertEquals( 1.0 / 3.0, m.get(3, 3), TOL);
    }

    @Test
    public void testSecondOrder4PointsNonUniformGrid() {
        final double[] xValues = { 0.5, 1.0, 2.0, 4.0, 8.0 };
        final SparseMatrix m =
            new NthOrderDerivativeOp(0, 2, 4,
                new FdmMesherComposite(new Predefined1dMesher(xValues))).toSparseMatrix();

        assertEquals( 88.0 / 21.0, m.get(0, 0), TOL);
        assertEquals(-140.0 / 21.0, m.get(0, 1), TOL);
        assertEquals( 56.0 / 21.0, m.get(0, 2), TOL);
        assertEquals(-4.0 / 21.0,   m.get(0, 3), TOL);
        assertEquals( 0.0,          m.get(0, 4), TOL);

        assertEquals( 64.0 / 21.0,  m.get(1, 0), TOL);
        assertEquals(-98.0 / 21.0,  m.get(1, 1), TOL);
        assertEquals( 35.0 / 21.0,  m.get(1, 2), TOL);
        assertEquals(-1.0 / 21.0,   m.get(1, 3), TOL);
        assertEquals( 0.0,          m.get(1, 4), TOL);

        assertEquals( 16.0 / 21.0,  m.get(2, 0), TOL);
        assertEquals(-2.0 /  3.0,   m.get(2, 1), TOL);
        assertEquals(-1.0 /  3.0,   m.get(2, 2), TOL);
        assertEquals( 5.0 / 21.0,   m.get(2, 3), TOL);
        assertEquals( 0.0,          m.get(2, 4), TOL);

        assertEquals( 0.0,          m.get(3, 0), TOL);
        assertEquals( 4.0 / 21.0,   m.get(3, 1), TOL);
        assertEquals(-1.0 /  6.0,   m.get(3, 2), TOL);
        assertEquals(-1.0 / 12.0,   m.get(3, 3), TOL);
        assertEquals( 5.0 / 84.0,   m.get(3, 4), TOL);

        assertEquals( 0.0,          m.get(4, 0), TOL);
        assertEquals(-20.0 / 21.0,  m.get(4, 1), TOL);
        assertEquals( 11.0 /  6.0,  m.get(4, 2), TOL);
        assertEquals(-13.0 / 12.0,  m.get(4, 3), TOL);
        assertEquals( 17.0 / 84.0,  m.get(4, 4), TOL);
    }

    @Test
    public void testThirdOrder4PointsUniformGrid() {
        final SparseMatrix m =
            new NthOrderDerivativeOp(0, 3, 4,
                new FdmMesherComposite(new Uniform1dMesher(0.0, 0.6, 4))).toSparseMatrix();

        for (int i = 0; i < 4; ++i) {
            assertEquals("m(" + i + ",0)", -125.0, m.get(i, 0), TOL);
            assertEquals("m(" + i + ",1)",  375.0, m.get(i, 1), TOL);
            assertEquals("m(" + i + ",2)", -375.0, m.get(i, 2), TOL);
            assertEquals("m(" + i + ",3)",  125.0, m.get(i, 3), TOL);
        }
    }

    /**
     * Sanity check that {@link NthOrderDerivativeOp#toMatrix()} (dense)
     * agrees with {@link NthOrderDerivativeOp#toSparseMatrix()} entry-for-entry.
     */
    @Test
    public void testDenseMatrixMatchesSparse() {
        final NthOrderDerivativeOp op = new NthOrderDerivativeOp(0, 1, 3,
            new FdmMesherComposite(new Uniform1dMesher(0.0, 1.0, 6)));
        final Matrix dense = op.toMatrix();
        final SparseMatrix sparse = op.toSparseMatrix();
        assertEquals(dense.rows(), sparse.rows());
        assertEquals(dense.cols(), sparse.columns());
        for (int i = 0; i < dense.rows(); ++i) {
            for (int j = 0; j < dense.cols(); ++j) {
                assertEquals("(" + i + "," + j + ")", dense.get(i, j), sparse.get(i, j), TOL);
            }
        }
    }

    // ----------------------------------------------------------------------
    // Body-filled in Phase 5e.5b-CFC-d-122 — compare ops + 9-point mixed.

    /**
     * Java port of C++ {@code testCompareFirstDerivativeOpNonUniformGrid}.
     * <p>Verifies that the 3-point {@link NthOrderDerivativeOp} agrees with
     * {@link FirstDerivativeOp} entry-for-entry on interior rows of a
     * non-uniform predefined grid (boundary rows use different one-sided
     * stencils, so they are excluded — mirrors C++).
     */
    @Test
    public void testCompareFirstDerivativeOpNonUniformGrid() {
        // xValues = exp(0, 0.1, 0.2, ..., 0.6) — C++ {@code Exp(Array(7, 0, 0.1))}.
        final double[] xValues = new double[7];
        for (int i = 0; i < 7; ++i) {
            xValues[i] = Math.exp(i * 0.1);
        }

        final Fdm1dMesher m = new Predefined1dMesher(xValues);
        final FdmMesher m1d = new FdmMesherComposite(m);

        final FirstDerivativeOp fx = new FirstDerivativeOp(0, m1d);
        final NthOrderDerivativeOp dx = new NthOrderDerivativeOp(0, 1, 3, m1d);

        final SparseMatrix fm = fx.toSparseMatrix();
        final SparseMatrix dm = dx.toSparseMatrix();

        // Interior rows only (boundaries differ — upwind vs Fornberg).
        for (int i = 1; i < m.size() - 1; ++i) {
            for (int j = 0; j < m.size(); ++j) {
                assertEquals("(" + i + "," + j + ")",
                        fm.get(i, j), dm.get(i, j), TOL);
            }
        }
    }

    /**
     * Java port of C++ {@code testCompareFirstDerivativeOp2dUniformGrid}.
     * <p>Same equivalence check on a 2D uniform grid for both axis
     * directions (direction = 0 then direction = 1).
     */
    @Test
    public void testCompareFirstDerivativeOp2dUniformGrid() {
        final Fdm1dMesher m1 = new Uniform1dMesher(0.0, 0.6, 5);
        final Fdm1dMesher m2 = new Uniform1dMesher(0.0, 1.6, 6);

        final FdmMesher mc = new FdmMesherComposite(m1, m2);

        final int n = mc.layout().dim()[0];
        final int mDim = mc.layout().dim()[1];

        // Direction 0
        SparseMatrix fm = new FirstDerivativeOp(0, mc).toSparseMatrix();
        SparseMatrix dm = new NthOrderDerivativeOp(0, 1, 3, mc).toSparseMatrix();

        for (int k = 0; k < mDim; ++k) {
            final int idx = k * n;
            for (int i = 1; i < n - 1; ++i) {
                for (int j = 0; j < n * mDim; ++j) {
                    assertEquals("dir0 (" + (idx + i) + "," + j + ")",
                            fm.get(idx + i, j), dm.get(idx + i, j), TOL);
                }
            }
        }

        // Direction 1
        fm = new FirstDerivativeOp(1, mc).toSparseMatrix();
        dm = new NthOrderDerivativeOp(1, 1, 3, mc).toSparseMatrix();

        for (int i = n; i < n * (mDim - 1); ++i) {
            for (int j = 0; j < n * mDim; ++j) {
                assertEquals("dir1 (" + i + "," + j + ")",
                        fm.get(i, j), dm.get(i, j), TOL);
            }
        }
    }

    /**
     * Java port of C++ {@code testMixedSecondOrder9PointsOnUniformGrid}.
     * <p>Verifies that the dedicated {@link SecondOrderMixedDerivativeOp}
     * (9-point stencil) agrees with the matrix product of two 3-point
     * {@link NthOrderDerivativeOp} first-derivative operators on the
     * interior block (boundaries differ).
     */
    @Test
    public void testMixedSecondOrder9PointsOnUniformGrid() {
        final Fdm1dMesher m = new Uniform1dMesher(0.0, 0.6, 5);
        final FdmMesher mc = new FdmMesherComposite(m, m);

        // cc = dx_0 * dx_1 — composed first-derivative ops (dense product).
        final Matrix dx0 = new NthOrderDerivativeOp(0, 1, 3, mc).toMatrix();
        final Matrix dx1 = new NthOrderDerivativeOp(1, 1, 3, mc).toMatrix();
        final Matrix cc  = dx0.mul(dx1);

        // mm = direct 9-point mixed-derivative operator.
        final SparseMatrix mm = new SecondOrderMixedDerivativeOp(0, 1, mc).toSparseMatrix();

        final int n = m.size();
        for (int i = 1; i < n - 1; ++i) {
            for (int j = 1; j < n - 1; ++j) {
                final int idx = i * n + j;
                for (int k = 1; k < n - 1; ++k) {
                    for (int l = 1; l < n - 1; ++l) {
                        final int kdx = k * n + l;
                        assertEquals("idx=" + idx + " kdx=" + kdx,
                                cc.get(idx, kdx), mm.get(idx, kdx), TOL);
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // Deferred — require a non-trivial inline FdmHestonNthOrderOp composite
    // operator + FdmBackwardSolver + AnalyticHestonEngine wiring + Levenberg-
    // Marquardt convergence assessment.  AnalyticHestonEngine is in-flight
    // (Phase 5e.5b-CFC-d agent), and FdHestonVanillaEngine is read-only in
    // this work item — these two tests stay {@code @Ignore}'d for a later
    // dedicated sub-task.

    private static final String FDH_REASON =
            "Phase 5j.5+ — needs FdmHestonNthOrderOp composite + FdmBackwardSolver + "
            + "AnalyticHestonEngine + LevenbergMarquardt wiring (heavy harness)";

    @Ignore(FDH_REASON)
    @Test
    public void testHigherOrderHestonOptionPricing() { fail("not implemented"); }

    @Ignore(FDH_REASON)
    @Test
    public void testHigherOrderAndRichardsonExtrapolation() { fail("not implemented"); }
}

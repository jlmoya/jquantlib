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

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Ops;
import org.jquantlib.math.RichardsonExtrapolation;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.interpolations.BicubicSplineInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.matrixutilities.SparseMatrix;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.LevenbergMarquardt;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.Problem;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmHestonVarianceMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmHestonNthOrderOp;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FirstDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.NthOrderDerivativeOp;
import org.jquantlib.methods.finitedifferences.operators.SecondOrderMixedDerivativeOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmBackwardSolver;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
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
    // Phase 5e.5b-CFC-d-277 — body-fill the high-order Heston tests with the
    // FdmHestonNthOrderOp composite + FdmBackwardSolver + AnalyticHestonEngine
    // wiring.  See the inline Java helpers below (GridSetup, AvgPayoffFct,
    // priceReport, FdmMispricingCostFunction) for the C++ pricing harness port.

    /**
     * Grid-setup record (Java port of the C++ {@code GridSetup} struct).
     */
    private static final class GridSetup {
        final double alpha;
        final double density;
        final boolean cellAvg;
        final boolean midPoint;
        final int nPoints;
        final int tGrid;
        final int yGrid;
        final int vGrid;
        final FdmSchemeDesc scheme;

        GridSetup(final double alpha, final double density,
                  final boolean cellAvg, final boolean midPoint,
                  final int nPoints, final int tGrid, final int yGrid,
                  final int vGrid, final FdmSchemeDesc scheme) {
            this.alpha = alpha;
            this.density = density;
            this.cellAvg = cellAvg;
            this.midPoint = midPoint;
            this.nPoints = nPoints;
            this.tGrid = tGrid;
            this.yGrid = yGrid;
            this.vGrid = vGrid;
            this.scheme = scheme;
        }
    }

    /**
     * Cell-averaged payoff function (Java port of C++ {@code AvgPayoffFct}).
     */
    private static final class AvgPayoffFct implements Ops.DoubleOp {
        private final PlainVanillaPayoff payoff;
        private final double vol2;
        private final double growthFactor;

        AvgPayoffFct(final PlainVanillaPayoff payoff, final double vol,
                     final double T, final double growthFactor) {
            this.payoff = payoff;
            this.vol2 = 0.5 * vol * vol * T;
            this.growthFactor = growthFactor;
        }

        @Override
        public double op(final double x) {
            return payoff.get(Math.exp(x - vol2) * growthFactor);
        }
    }

    /**
     * Java port of the C++ helper {@code priceReport(GridSetup, strikes)}.
     * <p>Returns the per-strike vector of {@code (analyticHeston - FDM)}
     * mispricing diffs.  See C++ test-suite/nthorderderivativeop.cpp lines
     * 498-639 for the source-of-truth.
     */
    private static Array priceReport(final GridSetup setup, final Array strikes) {
        final Date today = new Date(2, Month.May, 2018);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Date maturity = today.add(new Period(1, TimeUnit.Years));
        final double T = dc.yearFraction(today, maturity);

        final YieldTermStructure rTSObj = new FlatForward(
                today, new Handle<Quote>(new SimpleQuote(0.05)), dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure qTSObj = new FlatForward(
                today, new Handle<Quote>(new SimpleQuote(0.0)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(rTSObj);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(qTSObj);

        final double S = 100.0;
        final double vol = 0.2;
        final double v0 = vol * vol;
        final double kappa = 1.0;
        final double theta = vol * vol;
        final double sig = 0.2;
        final double rho = -0.75;

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final HestonProcess hestonProcess = new HestonProcess(
                rTS, qTS, spot, v0, kappa, theta, sig, rho);

        final double stdDev = vol * Math.sqrt(T);
        final double df = qTSObj.discount(maturity) / rTSObj.discount(maturity);

        final double y = Math.log(S);
        final double ymin = y - setup.alpha * stdDev;
        final double ymax = y + setup.alpha * stdDev;

        final int yGrid = setup.yGrid;
        final int vGrid = setup.vGrid;

        final Array diffs = new Array(strikes.size());

        // Pre-construct the Heston model + analytic engine once.
        final HestonModel hestonModel = new HestonModel(hestonProcess);

        for (int k = 0; k < strikes.size(); ++k) {
            final double strike = strikes.get(k);
            final double specialPoint = Math.log(strike / df) + 0.5 * vol * vol * T;

            final Fdm1dMesher mesher1d = new Concentrating1dMesher(
                    ymin, ymax, yGrid, specialPoint, setup.density);

            // Copy mesh locations into a mutable array for the optional
            // midpoint shift.
            final double[] loc = new double[mesher1d.size()];
            for (int i = 0; i < loc.length; ++i) {
                loc[i] = mesher1d.location(i);
            }

            if (setup.midPoint) {
                for (int i = 0; i < loc.length - 1; ++i) {
                    if (loc[i] < specialPoint && loc[i + 1] >= specialPoint) {
                        final double d = loc[i + 1] - loc[i];
                        final double offset = (specialPoint - 0.5 * d) - loc[i];
                        for (int j = 0; j < loc.length; ++j) {
                            loc[j] += offset;
                        }
                        break;
                    }
                }
            }

            final FdmMesherComposite mesher = new FdmMesherComposite(
                    new Predefined1dMesher(loc),
                    new FdmHestonVarianceMesher(vGrid, hestonProcess, 1.0));

            final Array g = mesher.locations(0);
            // sT = exp(g - 0.5*vol*vol*T) * df
            final double shift = -0.5 * vol * vol * T;
            final double[] sTBuf = new double[g.size()];
            for (int i = 0; i < g.size(); ++i) {
                sTBuf[i] = Math.exp(g.get(i) + shift) * df;
            }
            final Array sT = new Array(sTBuf);

            final Array rhs = new Array(mesher.layout().size());

            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(
                    Option.Type.Put, strike);

            for (final FdmLinearOpIterator iter : mesher.layout()) {
                final int idx = iter.index();
                final int idxm1 = mesher.layout().neighbourhood(iter, 0, -1);
                final int idxp1 = mesher.layout().neighbourhood(iter, 0, +1);

                final int nx = iter.coordinates()[0];

                if (nx != 0 && nx != yGrid - 1
                        && setup.cellAvg
                        && ((sT.get(idx) < strike && sT.get(idxp1) >= strike)
                            || (sT.get(idxm1) < strike && sT.get(idx) >= strike))) {

                    final double gMin = 0.5 * (g.get(idxm1) + g.get(idx));
                    final double gMax = 0.5 * (g.get(idxp1) + g.get(idx));

                    final AvgPayoffFct f = new AvgPayoffFct(payoff, vol, T, df);
                    final double integral =
                            new GaussLobattoIntegral(1000, 1e-12).op(f, gMin, gMax);
                    rhs.set(idx, integral / (gMax - gMin));
                } else {
                    rhs.set(idx, payoff.get(sT.get(idx)));
                }
            }

            final FdmHestonNthOrderOp heatEqn = new FdmHestonNthOrderOp(
                    setup.nPoints, hestonProcess, mesher);

            final FdmBackwardSolver solver = new FdmBackwardSolver(
                    heatEqn, new FdmBoundaryConditionSet(), null, setup.scheme);

            solver.rollback(rhs, T, 0.0, setup.tGrid, 1);

            // rhs *= rTS->discount(maturity)
            final double rDiscount = rTSObj.discount(maturity);
            for (int i = 0; i < rhs.size(); ++i) {
                rhs.set(i, rhs.get(i) * rDiscount);
            }

            // Build the bicubic interpolation surface.
            final double[] xLoc = ((Predefined1dMesher) mesher.getFdm1dMeshers().get(0))
                    .locations();
            final double[] vLoc = ((FdmHestonVarianceMesher) mesher.getFdm1dMeshers().get(1))
                    .locations();

            final int dx0 = mesher.layout().dim()[0];
            final int dx1 = mesher.layout().dim()[1];

            // boost matrix: resultValues_(dim[1], dim[0]) row-major copy of rhs.
            // Java BicubicSpline expects mz.rows() == y.size(), mz.cols() == x.size().
            final Matrix resultValues = new Matrix(dx1, dx0);
            for (int j = 0; j < dx1; ++j) {
                for (int i = 0; i < dx0; ++i) {
                    resultValues.set(j, i, rhs.get(i + j * dx0));
                }
            }

            final BicubicSplineInterpolation interp =
                    new BicubicSplineInterpolation(
                            new Array(xLoc), new Array(vLoc), resultValues);

            final double fdmPrice = interp.op(y, hestonProcess.v0().currentLink().value());

            // Analytic Heston reference.
            final VanillaOption option = new VanillaOption(
                    new PlainVanillaPayoff(Option.Type.Put, strike),
                    new EuropeanExercise(maturity));
            option.setPricingEngine(new AnalyticHestonEngine(hestonModel, hestonProcess, 192));
            final double npv = option.NPV();

            diffs.set(k, npv - fdmPrice);
        }

        return diffs;
    }

    /**
     * Levenberg-Marquardt cost function (Java port of C++
     * {@code FdmMispricingCostFunction}).
     */
    private static final class FdmMispricingCostFunction extends CostFunction {
        private final GridSetup setup;
        private final Array strikes;

        FdmMispricingCostFunction(final GridSetup setup, final Array strikes) {
            this.setup = setup;
            this.strikes = strikes;
        }

        @Override
        public Array values(final Array x) {
            final GridSetup g = new GridSetup(
                    x.get(0), x.get(1),
                    setup.cellAvg, setup.midPoint,
                    setup.nPoints, setup.tGrid, setup.yGrid, setup.vGrid,
                    setup.scheme);
            try {
                return priceReport(g, strikes);
            } catch (final Exception e) {
                final Array q = new Array(2).fill(1000.0);
                return q;
            }
        }

        @Override
        public double value(final Array x) {
            final Array v = values(x);
            double sum = 0.0;
            for (int i = 0; i < v.size(); ++i) {
                sum += v.get(i) * v.get(i);
            }
            return Math.sqrt(sum / v.size());
        }
    }

    /**
     * Java port of C++ {@code testHigherOrderHestonOptionPricing}.
     *
     * <p>Verifies that the high-order ({@code nPoints=5}) Heston PDE
     * discretisation reaches convergence order &gt;= 3.6 against the
     * analytic Heston engine after a Levenberg-Marquardt mesh-density
     * calibration over (alpha, density).
     *
     * <p>Phase 5e.5b-CFC-d-277.
     */
    @Test
    public void testHigherOrderHestonOptionPricing() {
        final Array strikes = new Array(new double[]{
                50.0, 75.0, 90.0, 100.0, 110.0, 125.0, 150.0, 200.0});

        final GridSetup initSetup = new GridSetup(
                3.87773, 0.043847,
                true, false,
                5, 21, 20, 11, FdmSchemeDesc.CrankNicolson());

        final Array initialValues = new Array(new double[]{
                initSetup.alpha, initSetup.density});

        final FdmMispricingCostFunction costFct =
                new FdmMispricingCostFunction(initSetup, strikes);
        final NoConstraint noConstraint = new NoConstraint();

        final Problem prob = new Problem(costFct, noConstraint, initialValues);

        new LevenbergMarquardt().minimize(
                prob, new EndCriteria(400, 40, 1.0e-4, 1.0e-4, 1.0e-4));

        final GridSetup optimalSetup = new GridSetup(
                prob.currentValue().get(0), prob.currentValue().get(1),
                initSetup.cellAvg, initSetup.midPoint,
                initSetup.nPoints,
                initSetup.tGrid,
                initSetup.yGrid / 2,
                initSetup.vGrid,
                initSetup.scheme);

        final Array q = priceReport(optimalSetup, strikes);
        final double ac = Math.sqrt(q.dotProduct(q) / q.size());

        final Array p = priceReport(initSetup, strikes);
        final double ap = Math.sqrt(p.dotProduct(p) / p.size());

        // convergence = log2(ac/ap)  (M_LOG2E = 1/ln(2))
        final double convergence = Math.log(ac / ap) / Math.log(2.0);

        if (convergence < 3.6) {
            fail("convergence order is too low"
                    + "\n expected convergence: 4.0"
                    + "\n measured convergence: " + convergence
                    + "\n tolerance           : 0.4");
        }
    }

    /**
     * Java port of C++ {@code priceQuality(h)} — single-strike (K=100)
     * absolute mispricing at mesh refinement {@code h = 1/yGrid}.
     */
    private static double priceQuality(final double h) {
        final Array strikes = new Array(new double[]{100.0});
        final int yGrid = (int) (1.0 / h);
        final GridSetup setup = new GridSetup(
                5.50966, 0.0130581,
                true, false,
                5, 401, yGrid, 21,
                FdmSchemeDesc.CrankNicolson());

        return Math.abs(priceReport(setup, strikes).get(0));
    }

    /**
     * Java port of C++ {@code testHigherOrderAndRichardsonExtrapolation}.
     *
     * <p>Verifies that {@link RichardsonExtrapolation} on top of the high-order
     * Heston FDM (nPoints=5) attains convergence order &gt;= 4.9 against the
     * analytic engine.
     *
     * <p>Phase 5e.5b-CFC-d-277.
     */
    @Test
    public void testHigherOrderAndRichardsonExtrapolation() {
        final double n1 = priceQuality(1.0 / 25);
        final double n3 = Math.abs(new RichardsonExtrapolation(
                NthOrderDerivativeOpTest::priceQuality, 1.0 / 25, 4.0).valueAt(2.0));

        final double r2 = Math.log(n1 / n3) / Math.log(2.0);

        if (r2 < 4.9) {
            fail("convergence order is too low using Richardson extrapolation"
                    + "\n expected convergence: 5.0"
                    + "\n measured convergence: " + r2
                    + "\n tolerance           : 0.1");
        }
    }
}

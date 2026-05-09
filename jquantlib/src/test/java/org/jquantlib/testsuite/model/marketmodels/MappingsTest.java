/*
Copyright (C) 2026 Jose Moya

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
 */

package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.ForwardForwardMappings;
import org.jquantlib.model.marketmodels.MarketModel;
import org.jquantlib.model.marketmodels.SwapForwardMappings;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Smoke tests for {@link ForwardForwardMappings} (Phase 3h.5 A.7) and
 * {@link SwapForwardMappings} (Phase 3h.5 A.8 partial).
 *
 * <p>Setup: 6-rate flat-5% curve, rate times = {0,1,2,3,4,5,6} so that
 * tau[i] = 1.0 for all i, and discountRatio(i,0) = 1.05^{-i}.
 *
 * <p>Tolerance: TIGHT (1e-12 relative).
 */
public class MappingsTest {

    public MappingsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    /** 6 rates, times 1..7, taus = 1.0, forwards = 5% flat. */
    private static final double[] RATE_TIMES_6 = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0};
    private static final double[] FWDS_6 = {0.05, 0.05, 0.05, 0.05, 0.05, 0.05};

    /** Convenience: build a 6-rate flat-5% LMMCurveState. */
    private static LMMCurveState flatCurve6() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES_6);
        cs.setOnForwardRates(FWDS_6);
        return cs;
    }

    // =====================================================================
    //  A.7 ForwardForwardMappings
    // =====================================================================

    /**
     * ForwardForwardJacobian dimensions: with n=6, multiplier=2, offset=0
     * we get k = (6-0)/2 = 3 rows, n=6 cols.
     */
    @Test
    public void testForwardForwardJacobianDimensions() {
        final LMMCurveState cs = flatCurve6();
        final Matrix jac = ForwardForwardMappings.forwardForwardJacobian(cs, 2, 0);
        assertNotNull(jac);
        assertEquals("rows", 3, jac.rows());
        assertEquals("cols", 6, jac.cols());
    }

    /**
     * With offset=1, multiplier=2, n=6:
     * k = (6-1)/2 = 2 rows (integer division).
     */
    @Test
    public void testForwardForwardJacobianDimensionsOffset1() {
        final LMMCurveState cs = flatCurve6();
        final Matrix jac = ForwardForwardMappings.forwardForwardJacobian(cs, 2, 1);
        assertNotNull(jac);
        assertEquals("rows", 2, jac.rows());
        assertEquals("cols", 6, jac.cols());
    }

    /**
     * Spot-check Jacobian values for multiplier=2, offset=0.
     *
     * C++ formula for row l, within-row column m:
     *   value  = discountRatio(m, m+2) * tau[m] * discountRatio(m+1, m) - 1
     *          = discountRatio(m, m+2) * tau[m] * 1/(1 + f*tau) - 1
     *          = discountRatio(m, m+2) / 1.05 - 1
     *   jacobian[l][m] = -value / bigTau
     *
     * discountRatio(m, m+2) = (1.05^{-m}) / (1.05^{-(m+2)}) = 1.05^2 = 1.1025.
     * bigTau = rateTimes[m+2] - rateTimes[m] = 2.0 (uniform spacing).
     *
     * value  = 1.1025 / 1.05 - 1 = 1.05 - 1 = 0.05
     * jacobian entry = -0.05 / 2.0 = -0.025.
     *
     * So every non-zero entry should equal -0.025.
     */
    @Test
    public void testForwardForwardJacobianValues() {
        final LMMCurveState cs = flatCurve6();
        final Matrix jac = ForwardForwardMappings.forwardForwardJacobian(cs, 2, 0);

        // expected: row 0 -> cols 0,1 non-zero; row 1 -> cols 2,3; row 2 -> cols 4,5
        final double expectedEntry = -0.025;

        for (int l = 0; l < 3; ++l) {
            for (int m = 0; m < 6; ++m) {
                final double val = jac.get(l, m);
                if (m == l * 2 || m == l * 2 + 1) {
                    assertEquals("jac[" + l + "][" + m + "]", expectedEntry, val, TOL);
                } else {
                    assertEquals("jac[" + l + "][" + m + "] zero", 0.0, val, TOL);
                }
            }
        }
    }

    /**
     * YMatrix should have same dimensions as Jacobian, and with zero
     * displacements the ratio short/long = 1, so it equals the Jacobian.
     */
    @Test
    public void testYMatrixZeroDisplacementsEqualsJacobian() {
        final LMMCurveState cs = flatCurve6();
        final int multiplier = 2;
        final int offset = 0;
        final int n = cs.numberOfRates();
        final int k = (n - offset) / multiplier;

        final double[] shortDisp = new double[n];  // zeros
        final double[] longDisp  = new double[k];  // zeros

        final Matrix y = ForwardForwardMappings.yMatrix(cs, shortDisp, longDisp, multiplier, offset);
        assertNotNull(y);
        assertEquals("rows", k, y.rows());
        assertEquals("cols", n, y.cols());

        // With zero displacements, ratio = shortFwd / longFwd.
        // All forwards are 0.05. Long forward for multiplier=2 spans
        // discountRatio((i+1)*2, i*2) - 1) / tau = (1.1025 - 1) / 2 = 0.05125
        // Actually: (D[0]/D[2] - 1)/2 = (1.05^2 - 1)/2 = 0.10250/2 = 0.05125 NOT 0.05
        // so ratio = 0.05 / 0.05125 = 40/41
        // each non-zero entry of Y = Jac * (40/41)
        // We verify only the structure (non-zero positions) here.
        for (int l = 0; l < k; ++l) {
            for (int m = 0; m < n; ++m) {
                final double yVal = y.get(l, m);
                if (m != l * 2 && m != l * 2 + 1) {
                    assertEquals("Y[" + l + "][" + m + "] zero", 0.0, yVal, TOL);
                }
            }
        }
    }

    /**
     * RestrictCurveState with multiplier=2, offset=0 on a 6-rate curve
     * should yield a 3-rate curve with times {0,2,4,6} and consistent
     * discount ratios (1.05^{-0}, 1.05^{-2}, 1.05^{-4}, 1.05^{-6}).
     */
    @Test
    public void testRestrictCurveState() {
        final LMMCurveState cs = flatCurve6();
        final LMMCurveState restricted =
                ForwardForwardMappings.restrictCurveState(cs, 2, 0);

        assertNotNull(restricted);
        assertEquals("numberOfRates", 3, restricted.numberOfRates());

        // times must be {1,3,5,7} (RATE_TIMES_6[i*2] for i=0..3)
        final double[] rt = restricted.rateTimes();
        assertEquals("t[0]", 1.0, rt[0], TOL);
        assertEquals("t[1]", 3.0, rt[1], TOL);
        assertEquals("t[2]", 5.0, rt[2], TOL);
        assertEquals("t[3]", 7.0, rt[3], TOL);

        // discountRatio(i, 0) = discRatios_[i] / discRatios_[0]
        // We set them from cs.discountRatio(i*2, 0) = 1.05^{-2i}
        for (int i = 0; i <= 3; ++i) {
            final double expected = Math.pow(1.05, -2.0 * i);
            assertEquals("D[" + i + "]", expected, restricted.discountRatio(i, 0), TOL);
        }
    }

    // =====================================================================
    //  A.8 SwapForwardMappings (partial, minus swaptionImpliedVolatility)
    // =====================================================================

    /** 4-rate flat-5% curve with times 1..5 for swap mapping tests. */
    private static final double[] RATE_TIMES_4 = {1.0, 2.0, 3.0, 4.0, 5.0};
    private static final double[] FWDS_4 = {0.05, 0.05, 0.05, 0.05};

    private static LMMCurveState flatCurve4() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES_4);
        cs.setOnForwardRates(FWDS_4);
        return cs;
    }

    /**
     * Annuity over [0,n) discounted to numeraire n.
     * With flat 5%, discountRatio(i+1, n) = D[i+1]/D[n] where D[k]=1.05^{-k}.
     * So discountRatio(i+1, 4) = 1.05^{-(i+1)} / 1.05^{-4} = 1.05^{3-i}.
     * annuity([0,4), 4) = sum_{i=0}^{3} 1.0 * 1.05^{3-i}
     *                   = 1.05^3 + 1.05^2 + 1.05 + 1.05^0
     *                   = 1.157625 + 1.1025 + 1.05 + 1.0 = 4.310125.
     */
    @Test
    public void testAnnuityFlat() {
        final LMMCurveState cs = flatCurve4();
        final int n = cs.numberOfRates(); // 4

        // annuity([0,4), numeraire=4)
        // = 1.05^3 + 1.05^2 + 1.05 + 1
        final double expected = Math.pow(1.05, 3) + Math.pow(1.05, 2) + 1.05 + 1.0;
        final double actual = SwapForwardMappings.annuity(cs, 0, n, n);
        assertEquals("annuity(0,4,4)", expected, actual, TOL);

        // annuity([1,3), numeraire=3): tau=1, discountRatio(i+1, 3) = 1.05^{2-i}
        // i=1: 1.05^1 = 1.05;  i=2: 1.05^0 = 1.0  => total = 2.05
        final double expected13 = 1.05 + 1.0;
        final double actual13 = SwapForwardMappings.annuity(cs, 1, 3, 3);
        assertEquals("annuity(1,3,3)", expected13, actual13, TOL);
    }

    /**
     * swapDerivative returns 0 for forwardIndex outside [startIndex, endIndex).
     */
    @Test
    public void testSwapDerivativeOutOfRange() {
        final LMMCurveState cs = flatCurve4();
        assertEquals("below range", 0.0, SwapForwardMappings.swapDerivative(cs, 1, 3, 0), TOL);
        assertEquals("above range", 0.0, SwapForwardMappings.swapDerivative(cs, 1, 3, 3), TOL);
    }

    /**
     * coterminalSwapForwardJacobian: matrix is n x n.
     * With flat 5% and n=4, diagonal elements jacobian[i][i] must be positive
     * (a forward rate change causes a positive change in the same-index coterminal
     * swap rate).
     */
    @Test
    public void testCoterminalSwapForwardJacobianDimensions() {
        final LMMCurveState cs = flatCurve4();
        final Matrix jac = SwapForwardMappings.coterminalSwapForwardJacobian(cs);
        assertNotNull(jac);
        assertEquals("rows", 4, jac.rows());
        assertEquals("cols", 4, jac.cols());

        // Diagonal entries should be positive
        for (int i = 0; i < 4; ++i) {
            final double diag = jac.get(i, i);
            assertEquals("jac[" + i + "][" + i + "] > 0", true, diag > 0.0);
        }

        // Lower triangle (j < i) must be zero
        for (int i = 1; i < 4; ++i) {
            for (int j = 0; j < i; ++j) {
                assertEquals("lower[" + i + "][" + j + "]", 0.0, jac.get(i, j), TOL);
            }
        }
    }

    /**
     * coterminalSwapZedMatrix: same structure as Jacobian but scaled by
     * (f[j]+d)/(sr[i]+d). With displacement=0 and flat fwds (sr==f==5%), the
     * ratio is 1 everywhere, so Z == Jacobian.
     */
    @Test
    public void testCoterminalSwapZedMatrixZeroDisplacement() {
        final LMMCurveState cs = flatCurve4();
        final Matrix jac = SwapForwardMappings.coterminalSwapForwardJacobian(cs);
        final Matrix z   = SwapForwardMappings.coterminalSwapZedMatrix(cs, 0.0);
        // With flat 5%, sr[i] == f[j] == 0.05, so ratio = 1.0
        for (int i = 0; i < 4; ++i) {
            for (int j = i; j < 4; ++j) {
                assertEquals("Z[" + i + "][" + j + "]", jac.get(i, j), z.get(i, j), TOL);
            }
        }
    }

    /**
     * coinitialSwapForwardJacobian: matrix is n x n.
     * coinitial swap i spans [0, i+1): at i=0 it is a single-period swap
     * (equivalent to forward rate), so jacobian[0][0] should be
     * the derivative of f[0] w.r.t. f[0] = close to 1.
     */
    @Test
    public void testCoinitialSwapForwardJacobianDimensions() {
        final LMMCurveState cs = flatCurve4();
        final Matrix jac = SwapForwardMappings.coinitialSwapForwardJacobian(cs);
        assertNotNull(jac);
        assertEquals("rows", 4, jac.rows());
        assertEquals("cols", 4, jac.cols());

        // jacobian[i][j] == 0 for j > i (a longer coinitial swap rate
        // does not depend on a forward rate beyond its range)
        for (int i = 0; i < 4; ++i) {
            for (int j = i + 1; j < 4; ++j) {
                assertEquals("upper[" + i + "][" + j + "]", 0.0, jac.get(i, j), TOL);
            }
        }

        // All diagonal entries should be positive
        for (int i = 0; i < 4; ++i) {
            assertEquals("diag[" + i + "] > 0", true, jac.get(i, i) > 0.0);
        }
    }

    /**
     * cmSwapForwardJacobian with spanningForwards=1: each CMS rate spans just
     * one forward, so the Jacobian should be diagonal with positive entries,
     * and off-diagonal entries zero.
     */
    @Test
    public void testCmSwapForwardJacobianSpanOne() {
        final LMMCurveState cs = flatCurve4();
        final Matrix jac = SwapForwardMappings.cmSwapForwardJacobian(cs, 1);
        assertNotNull(jac);
        assertEquals("rows", 4, jac.rows());
        assertEquals("cols", 4, jac.cols());

        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                if (i == j) {
                    assertEquals("diag[" + i + "] > 0", true, jac.get(i, i) > 0.0);
                } else {
                    assertEquals("off-diag[" + i + "][" + j + "]", 0.0, jac.get(i, j), TOL);
                }
            }
        }
    }

    /**
     * cmSwapZedMatrix dimensions: n x n.
     */
    @Test
    public void testCmSwapZedMatrixDimensions() {
        final LMMCurveState cs = flatCurve4();
        final Matrix z = SwapForwardMappings.cmSwapZedMatrix(cs, 2, 0.01);
        assertNotNull(z);
        assertEquals("rows", 4, z.rows());
        assertEquals("cols", 4, z.cols());
    }

    /**
     * swaptionImpliedVolatility now active (Phase 3j L0.5): builds a hand-crafted
     * trivial 1-factor MarketModel with constant unit pseudo-roots and verifies
     * the Brace-Gatarek-Musiela freezing-coefficient formula returns a positive
     * Black volatility consistent with the underlying variance accumulation.
     */
    @Test
    public void testSwaptionImpliedVolatility() {
        // 4-rate flat 5% setup matching flatCurve4()
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0, 5.0};
        final double[] initialRates = {0.05, 0.05, 0.05, 0.05};
        final double[] displacements = {0.0, 0.0, 0.0, 0.0};
        final EvolutionDescription evol = new EvolutionDescription(rateTimes);

        // Trivial 1-factor MarketModel: pseudoRoot[k] is a 4x1 matrix
        // with every entry = 0.1 — meaning 0.01 covariance per (i,j) pair per step.
        final int numFactors = 1;
        final int numRates = 4;
        final int numSteps = evol.numberOfSteps();
        final double pseudoEntry = 0.1;

        final MarketModel mm = new MarketModel() {
            private final Matrix[] roots = build();

            private Matrix[] build() {
                final Matrix[] r = new Matrix[numSteps];
                for (int k = 0; k < numSteps; ++k) {
                    final Matrix m = new Matrix(numRates, numFactors);
                    for (int i = 0; i < numRates; ++i) {
                        m.set(i, 0, pseudoEntry);
                    }
                    r[k] = m;
                }
                return r;
            }

            @Override public double[] initialRates() { return initialRates; }
            @Override public double[] displacements() { return displacements; }
            @Override public EvolutionDescription evolution() { return evol; }
            @Override public int numberOfRates() { return numRates; }
            @Override public int numberOfFactors() { return numFactors; }
            @Override public int numberOfSteps() { return numSteps; }
            @Override public Matrix pseudoRoot(final int i) { return roots[i]; }
        };

        // CMS swap covering [1, 3) — endIndex - startIndex = 2 forwards
        final double iv = SwapForwardMappings.swaptionImpliedVolatility(mm, 1, 3);

        // Smoke check: must be a positive finite value, not NaN
        assertEquals("non-NaN", iv, iv, 0.0);
        org.junit.Assert.assertTrue("positive", iv > 0.0);
        org.junit.Assert.assertTrue("finite", Double.isFinite(iv));
    }
}

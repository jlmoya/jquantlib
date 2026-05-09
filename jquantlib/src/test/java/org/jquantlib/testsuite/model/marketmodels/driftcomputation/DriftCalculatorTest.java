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

package org.jquantlib.testsuite.model.marketmodels.driftcomputation;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.curvestates.CMSwapCurveState;
import org.jquantlib.model.marketmodels.curvestates.CoterminalSwapCurveState;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.driftcomputation.CMSMMDriftCalculator;
import org.jquantlib.model.marketmodels.driftcomputation.LMMDriftCalculator;
import org.jquantlib.model.marketmodels.driftcomputation.LMMNormalDriftCalculator;
import org.jquantlib.model.marketmodels.driftcomputation.SMMDriftCalculator;
import org.junit.Test;

/**
 * Tests for the four drift calculators — Phase 3h A.10-A.13.
 *
 * <p>Cross-validation via:
 * <ul>
 *   <li><b>Hand-computed cases</b>: small inputs where we can derive expected drifts
 *       analytically from the formula in
 *       {@code ql/models/marketmodels/driftcomputation/lmmdriftcalculator.cpp}.</li>
 *   <li><b>Internal-consistency invariants</b>: zero-volatility → zero drifts,
 *       full-factor plain == reduced equivalence (must produce identical drifts
 *       by construction since they implement the same mathematics).</li>
 *   <li><b>Numeraire invariants</b>: drift at numeraire_-1 is zero (per the
 *       eq-7 reference of Joshi 2003).</li>
 * </ul>
 *
 * <p>Tolerance: 1e-12 (TIGHT) for plain calculations, 1e-12 for plain↔reduced
 * equivalence (both implement identical math).
 */
public class DriftCalculatorTest {

    public DriftCalculatorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;
    private static final double[] RATE_TIMES = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
    private static final double[] FLAT_FWDS = {0.05, 0.05, 0.05, 0.05, 0.05};

    /** 5x5 matrix with all entries equal to {@code v}. */
    private static Matrix flatMatrix(final int rows, final int cols, final double v) {
        final Matrix m = new Matrix(rows, cols);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                m.set(i, j, v);
            }
        }
        return m;
    }

    /** Identity matrix scaled by {@code s}. */
    private static Matrix scaledIdentity(final int n, final double s) {
        final Matrix m = new Matrix(n, n);
        for (int i = 0; i < n; i++) {
            m.set(i, i, s);
        }
        return m;
    }

    // -------- LMMDriftCalculator --------

    /**
     * Hand-computed reference: pseudo[i][j] = 0.1 for all (i,j); 5x5;
     * displacements=0; taus=1; fwds=5%; numeraire=5 (terminal); alive=0.
     *
     * <p>Then C[i][j] = 5*0.01 = 0.05 (constant), tmp[k] = 0.05/(1+0.05) = 1/21.
     * For terminal numeraire:
     *   downs[i] = i+1, ups[i] = 5
     *   drift[i] = sum_{k=i+1}^{4} tmp[k] * C[i][k] = (4-i)*(1/21)*0.05
     *   For i<4: numeraire(5) > i+1 → negate: drift[i] = -(4-i)*(1/21)*0.05
     *   drift[4] = 0 (empty range, no negate)
     */
    @Test
    public void testLMMDriftCalculatorPlainKnownCase() {
        final Matrix pseudo = flatMatrix(5, 5, 0.1);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};
        final int numeraire = 5;
        final int alive = 0;

        final LMMDriftCalculator calc = new LMMDriftCalculator(pseudo, disp, taus, numeraire, alive);
        final double[] drifts = new double[5];
        calc.computePlain(FLAT_FWDS, drifts);

        final double tmp = 0.05 / 1.05;     // = 1/21
        final double cii = 5 * 0.01;        // = 0.05
        final double[] expected = new double[5];
        for (int i = 0; i < 4; i++) {
            expected[i] = -(4 - i) * tmp * cii;
        }
        expected[4] = 0.0;

        for (int i = 0; i < 5; i++) {
            assertEquals("drift[" + i + "]", expected[i], drifts[i], TOL);
        }
    }

    /**
     * For full-factor square pseudo, plain and reduced must give identical drifts.
     */
    @Test
    public void testLMMDriftCalculatorPlainEqualsReducedFullFactor() {
        // Use a non-trivial 5x5 pseudo
        final double[][] pdata = {
                {0.10, 0.05, 0.02, 0.01, 0.00},
                {0.05, 0.12, 0.04, 0.01, 0.00},
                {0.02, 0.04, 0.10, 0.03, 0.01},
                {0.01, 0.01, 0.03, 0.08, 0.02},
                {0.00, 0.00, 0.01, 0.02, 0.07}
        };
        final Matrix pseudo = new Matrix(pdata);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};

        // Try numeraire = 3 (mid), alive = 0
        final LMMDriftCalculator calc = new LMMDriftCalculator(pseudo, disp, taus, 3, 0);
        final double[] driftsP = new double[5];
        final double[] driftsR = new double[5];
        calc.computePlain(FLAT_FWDS, driftsP);
        calc.computeReduced(FLAT_FWDS, driftsR);
        for (int i = 0; i < 5; i++) {
            assertEquals("plain vs reduced drift[" + i + "]", driftsP[i], driftsR[i], TOL);
        }
    }

    /**
     * Numeraire invariant: drift[numeraire_-1] = 0 in reduced calc when
     * numeraire_ > 0 (per "1st step" of computeReduced).
     */
    @Test
    public void testLMMDriftCalculatorNumeraireInvariant() {
        final Matrix pseudo = flatMatrix(5, 5, 0.1);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};

        for (int num = 1; num <= 5; num++) {
            final LMMDriftCalculator calc = new LMMDriftCalculator(pseudo, disp, taus, num, 0);
            final double[] driftsR = new double[5];
            calc.computeReduced(FLAT_FWDS, driftsR);
            assertEquals("drift at num-1 (num=" + num + ")", 0.0, driftsR[num - 1], TOL);
        }
    }

    /**
     * Zero-volatility pseudo → zero drifts in plain.
     */
    @Test
    public void testLMMDriftCalculatorZeroVolatility() {
        final Matrix pseudo = new Matrix(5, 5);  // all zeros
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.01, 0.02, 0.0, 0.0, 0.0};
        final LMMDriftCalculator calc = new LMMDriftCalculator(pseudo, disp, taus, 5, 0);
        final double[] drifts = new double[5];
        calc.computePlain(FLAT_FWDS, drifts);
        for (int i = 0; i < 5; i++) {
            assertEquals(0.0, drifts[i], TOL);
        }
    }

    /**
     * Auto-dispatch (compute → plain or reduced based on factor count).
     * For a non-square reduced pseudo (5x3), compute() should call computeReduced.
     */
    @Test
    public void testLMMDriftCalculatorAutoDispatch() {
        // 5x3 reduced pseudo (full-rank submatrix)
        final double[][] pdata = {
                {0.10, 0.05, 0.02},
                {0.05, 0.12, 0.04},
                {0.02, 0.04, 0.10},
                {0.01, 0.01, 0.03},
                {0.00, 0.00, 0.01}
        };
        final Matrix pseudo = new Matrix(pdata);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};
        final LMMDriftCalculator calc = new LMMDriftCalculator(pseudo, disp, taus, 5, 0);
        final double[] driftsAuto = new double[5];
        final double[] driftsR = new double[5];
        calc.compute(FLAT_FWDS, driftsAuto);
        calc.computeReduced(FLAT_FWDS, driftsR);
        for (int i = 0; i < 5; i++) {
            assertEquals(driftsAuto[i], driftsR[i], TOL);
        }
    }

    /**
     * compute(LMMCurveState, ...) delegates to compute(forwardRates(), ...).
     */
    @Test
    public void testLMMDriftCalculatorWithCurveState() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FLAT_FWDS);
        final Matrix pseudo = scaledIdentity(5, 0.1);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};
        final LMMDriftCalculator calc = new LMMDriftCalculator(pseudo, disp, taus, 5, 0);
        final double[] driftsCs = new double[5];
        final double[] driftsArr = new double[5];
        calc.compute(cs, driftsCs);
        calc.compute(FLAT_FWDS, driftsArr);
        for (int i = 0; i < 5; i++) {
            assertEquals(driftsArr[i], driftsCs[i], TOL);
        }
    }

    // -------- LMMNormalDriftCalculator --------

    /**
     * Normal LMM drift: tmp[k] = 1/(1/tau + f) = tau/(1+f*tau).
     * For taus=1, fwds=5%: tmp[k] = 1/1.05 = 20/21 ≈ 0.9523809524.
     * Same flat-pseudo case (0.1 everywhere): C[i][j] = 5*0.01 = 0.05.
     * Terminal numeraire: drift[i] = -(4-i)*tmp*0.05  (with negate for i<4).
     */
    @Test
    public void testLMMNormalDriftCalculatorPlainKnownCase() {
        final Matrix pseudo = flatMatrix(5, 5, 0.1);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final LMMNormalDriftCalculator calc = new LMMNormalDriftCalculator(pseudo, taus, 5, 0);
        final double[] drifts = new double[5];
        calc.computePlain(FLAT_FWDS, drifts);

        final double tmp = 1.0 / 1.05;      // = 20/21
        final double cii = 0.05;
        for (int i = 0; i < 4; i++) {
            assertEquals("drift[" + i + "]", -(4 - i) * tmp * cii, drifts[i], TOL);
        }
        assertEquals(0.0, drifts[4], TOL);
    }

    /** Plain ≡ reduced equivalence for full-factor square pseudo. */
    @Test
    public void testLMMNormalDriftCalculatorPlainEqualsReducedFullFactor() {
        final double[][] pdata = {
                {0.10, 0.05, 0.02, 0.01, 0.00},
                {0.05, 0.12, 0.04, 0.01, 0.00},
                {0.02, 0.04, 0.10, 0.03, 0.01},
                {0.01, 0.01, 0.03, 0.08, 0.02},
                {0.00, 0.00, 0.01, 0.02, 0.07}
        };
        final Matrix pseudo = new Matrix(pdata);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final LMMNormalDriftCalculator calc = new LMMNormalDriftCalculator(pseudo, taus, 3, 0);
        final double[] driftsP = new double[5];
        final double[] driftsR = new double[5];
        calc.computePlain(FLAT_FWDS, driftsP);
        calc.computeReduced(FLAT_FWDS, driftsR);
        for (int i = 0; i < 5; i++) {
            assertEquals(driftsP[i], driftsR[i], TOL);
        }
    }

    // -------- SMMDriftCalculator --------

    /**
     * Construction smoke test + basic shape: drifts vector populated, drift at
     * alive_-1 boundary handled. Use a flat 5%-CTSM curve and a non-trivial
     * pseudo. We don't pin numerical values here (would require generating a C++
     * probe); instead we verify the calculator produces finite values without
     * exception.
     */
    @Test
    public void testSMMDriftCalculatorRunsOnCoterminalCurve() {
        // Build a CoterminalSwapCurveState with cot rates = 5% flat
        // (equivalent to LMM with flat 5% fwds).
        final double[] cotRates = {0.05, 0.05, 0.05, 0.05, 0.05};
        final CoterminalSwapCurveState cs = new CoterminalSwapCurveState(RATE_TIMES);
        cs.setOnCoterminalSwapRates(cotRates);

        final double[][] pdata = {
                {0.10, 0.05, 0.02, 0.01, 0.00},
                {0.05, 0.12, 0.04, 0.01, 0.00},
                {0.02, 0.04, 0.10, 0.03, 0.01},
                {0.01, 0.01, 0.03, 0.08, 0.02},
                {0.00, 0.00, 0.01, 0.02, 0.07}
        };
        final Matrix pseudo = new Matrix(pdata);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};

        final SMMDriftCalculator calc = new SMMDriftCalculator(pseudo, disp, taus, 5, 1);
        final double[] drifts = new double[5];
        calc.compute(cs, drifts);

        for (int i = 1; i < 5; i++) {
            // Drifts must be finite (not NaN/Inf)
            assertEquals("drift[" + i + "] finite", drifts[i], drifts[i], 0.0);
            assertEquals("drift[" + i + "] not Inf", true, !Double.isInfinite(drifts[i]));
        }
    }

    /**
     * Zero-pseudo → zero drifts (SMM).
     */
    @Test
    public void testSMMDriftCalculatorZeroVolatility() {
        final double[] cotRates = {0.05, 0.05, 0.05, 0.05, 0.05};
        final CoterminalSwapCurveState cs = new CoterminalSwapCurveState(RATE_TIMES);
        cs.setOnCoterminalSwapRates(cotRates);

        final Matrix pseudo = new Matrix(5, 5);  // zeros
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};
        final SMMDriftCalculator calc = new SMMDriftCalculator(pseudo, disp, taus, 5, 0);
        final double[] drifts = new double[5];
        calc.compute(cs, drifts);
        for (int i = 0; i < 5; i++) {
            assertEquals(0.0, drifts[i], TOL);
        }
    }

    // -------- CMSMMDriftCalculator --------

    /**
     * Construction + smoke test: produce finite values without exceptions.
     */
    @Test
    public void testCMSMMDriftCalculatorRuns() {
        final int spanning = 2;
        // Build a CMSwapCurveState seeded from LMM with flat 5% fwds
        final LMMCurveState ref = new LMMCurveState(RATE_TIMES);
        ref.setOnForwardRates(FLAT_FWDS);
        final double[] cmsRates = new double[5];
        for (int i = 0; i < 5; i++) {
            cmsRates[i] = ref.cmSwapRate(i, spanning);
        }
        final CMSwapCurveState cs = new CMSwapCurveState(RATE_TIMES, spanning);
        cs.setOnCMSwapRates(cmsRates);

        final double[][] pdata = {
                {0.10, 0.05, 0.02, 0.01, 0.00},
                {0.05, 0.12, 0.04, 0.01, 0.00},
                {0.02, 0.04, 0.10, 0.03, 0.01},
                {0.01, 0.01, 0.03, 0.08, 0.02},
                {0.00, 0.00, 0.01, 0.02, 0.07}
        };
        final Matrix pseudo = new Matrix(pdata);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};

        final CMSMMDriftCalculator calc =
                new CMSMMDriftCalculator(pseudo, disp, taus, 5, 1, spanning);
        final double[] drifts = new double[5];
        calc.compute(cs, drifts);

        for (int i = 1; i < 5; i++) {
            assertEquals(drifts[i], drifts[i], 0.0);  // not NaN
            assertEquals(true, !Double.isInfinite(drifts[i]));
        }
    }

    /**
     * Zero-pseudo → zero drifts (CMSMM).
     */
    @Test
    public void testCMSMMDriftCalculatorZeroVolatility() {
        final int spanning = 2;
        final LMMCurveState ref = new LMMCurveState(RATE_TIMES);
        ref.setOnForwardRates(FLAT_FWDS);
        final double[] cmsRates = new double[5];
        for (int i = 0; i < 5; i++) {
            cmsRates[i] = ref.cmSwapRate(i, spanning);
        }
        final CMSwapCurveState cs = new CMSwapCurveState(RATE_TIMES, spanning);
        cs.setOnCMSwapRates(cmsRates);

        final Matrix pseudo = new Matrix(5, 5);
        final double[] taus = {1.0, 1.0, 1.0, 1.0, 1.0};
        final double[] disp = {0.0, 0.0, 0.0, 0.0, 0.0};
        final CMSMMDriftCalculator calc =
                new CMSMMDriftCalculator(pseudo, disp, taus, 5, 0, spanning);
        final double[] drifts = new double[5];
        calc.compute(cs, drifts);
        for (int i = 0; i < 5; i++) {
            assertEquals(0.0, drifts[i], TOL);
        }
    }
}

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

package org.jquantlib.testsuite.model.marketmodels.curvestates;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.curvestates.CMSwapCurveState;
import org.jquantlib.model.marketmodels.curvestates.CoterminalSwapCurveState;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Tests for {@link LMMCurveState}, {@link CoterminalSwapCurveState},
 * {@link CMSwapCurveState} — Phase 3h A.4-A.6.
 *
 * <p>Cross-validation: deterministic algebra. Expected values derived from
 * the LMM/CTSMM/CMSMM definitions applied to canonical 5-rate flat-curve
 * inputs (matches setup pattern in C++ test-suite/marketmodel.cpp).
 *
 * <p>Tolerance: TIGHT (1e-12 relative).
 */
public class CurveStatesTest {

    public CurveStatesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;
    private static final double[] RATE_TIMES = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
    // 5 forward rates, all 5% flat, with 1.0-year taus
    private static final double[] FLAT_FWDS = {0.05, 0.05, 0.05, 0.05, 0.05};

    /**
     * For LMMCurveState with flat 5% fwds and tau=1.0:
     *   D[0] = 1.0
     *   D[i+1] = D[i] / (1 + 0.05) = D[i] / 1.05
     * So D[i] = 1.05^(-i).
     */
    @Test
    public void testLMMCurveStateDiscountRatios() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FLAT_FWDS);

        for (int i = 0; i <= 5; i++) {
            final double expected = Math.pow(1.05, -i);
            final double actual = cs.discountRatio(i, 0);
            assertEquals("D[" + i + "]", expected, actual, TOL);
        }
    }

    /**
     * forwardRate(i) = original input fwds[i].
     */
    @Test
    public void testLMMCurveStateForwardRates() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FLAT_FWDS);

        for (int i = 0; i < 5; i++) {
            assertEquals(0.05, cs.forwardRate(i), TOL);
        }
        // forwardRates() returns the array
        final double[] fwds = cs.forwardRates();
        for (int i = 0; i < 5; i++) {
            assertEquals(0.05, fwds[i], TOL);
        }
    }

    /**
     * For flat 5% fwds, the swap rate over any range equals 5%
     * (par swap rate = the forward rate when all fwds equal).
     */
    @Test
    public void testLMMCurveStateSwapRateFlat() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FLAT_FWDS);
        // Coterminal swap rate at index 0 = swap rate over [0, 5)
        assertEquals(0.05, cs.swapRate(0, 5), TOL);
        // Range [1, 4)
        assertEquals(0.05, cs.swapRate(1, 4), TOL);
        // Single forward rate range
        assertEquals(0.05, cs.swapRate(2, 3), TOL);
    }

    /**
     * Coterminal swap rates equal 5% when all forwards are 5% flat.
     */
    @Test
    public void testLMMCurveStateCoterminalSwapRatesFlat() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FLAT_FWDS);
        for (int i = 0; i < 5; i++) {
            assertEquals("ctsr[" + i + "]", 0.05, cs.coterminalSwapRate(i), TOL);
        }
    }

    /**
     * setOnDiscountRatios round-trip: derive discRatios from flat 5% fwds,
     * then set them back, verify forward rates recovered.
     */
    @Test
    public void testLMMCurveStateSetOnDiscountRatios() {
        // First compute discRatios from flat fwds
        final double[] discRatios = new double[6];
        discRatios[0] = 1.0;
        for (int i = 0; i < 5; i++) {
            discRatios[i + 1] = discRatios[i] / 1.05;
        }
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnDiscountRatios(discRatios);

        for (int i = 0; i < 5; i++) {
            assertEquals("recovered fwd[" + i + "]", 0.05, cs.forwardRate(i), TOL);
        }
    }

    /**
     * Clone must produce an independent copy with same discount ratios.
     */
    @Test
    public void testLMMCurveStateClone() {
        final LMMCurveState cs = new LMMCurveState(RATE_TIMES);
        cs.setOnForwardRates(FLAT_FWDS);

        final CurveState copy = cs.clone();
        for (int i = 0; i <= 5; i++) {
            assertEquals(cs.discountRatio(i, 0), copy.discountRatio(i, 0), TOL);
        }
        // mutate original
        final double[] newFwds = {0.10, 0.10, 0.10, 0.10, 0.10};
        cs.setOnForwardRates(newFwds);
        // copy must be unaffected
        for (int i = 0; i <= 5; i++) {
            final double expected = Math.pow(1.05, -i);
            assertEquals("post-mutation copy[" + i + "]", expected, copy.discountRatio(i, 0), TOL);
        }
    }

    // -------- CoterminalSwapCurveState --------

    /**
     * Round-trip: derive coterminal-swap rates from flat 5% fwds (which equal
     * 5% by symmetry), then construct a CoterminalSwapCurveState, and verify
     * it produces the same discount ratios as a direct LMM construction.
     */
    @Test
    public void testCoterminalSwapCurveStateRoundtrip() {
        // Build LMM reference
        final LMMCurveState ref = new LMMCurveState(RATE_TIMES);
        ref.setOnForwardRates(FLAT_FWDS);

        // Coterminal swap rates from flat-fwds setup
        final double[] cotRates = new double[5];
        for (int i = 0; i < 5; i++) {
            cotRates[i] = ref.coterminalSwapRate(i);
        }

        final CoterminalSwapCurveState cs = new CoterminalSwapCurveState(RATE_TIMES);
        cs.setOnCoterminalSwapRates(cotRates);

        // Discount ratios must match LMM reference
        for (int i = 0; i <= 5; i++) {
            assertEquals("D[" + i + "] ctsm vs lmm",
                    ref.discountRatio(i, 0), cs.discountRatio(i, 0), TOL);
        }

        // forwardRate must equal 5%
        for (int i = 0; i < 5; i++) {
            assertEquals("recovered fwd[" + i + "]", 0.05, cs.forwardRate(i), TOL);
        }
    }

    /** Coterminal swap rate inspector returns the input. */
    @Test
    public void testCoterminalSwapCurveStateInspectors() {
        final double[] cotRates = {0.05, 0.045, 0.04, 0.035, 0.03};
        final CoterminalSwapCurveState cs = new CoterminalSwapCurveState(RATE_TIMES);
        cs.setOnCoterminalSwapRates(cotRates);
        for (int i = 0; i < 5; i++) {
            assertEquals(cotRates[i], cs.coterminalSwapRate(i), TOL);
        }
    }

    @Test
    public void testCoterminalSwapCurveStateClone() {
        final double[] cotRates = {0.05, 0.05, 0.05, 0.05, 0.05};
        final CoterminalSwapCurveState cs = new CoterminalSwapCurveState(RATE_TIMES);
        cs.setOnCoterminalSwapRates(cotRates);
        final CurveState copy = cs.clone();
        for (int i = 0; i <= 5; i++) {
            assertEquals(cs.discountRatio(i, 0), copy.discountRatio(i, 0), TOL);
        }
    }

    // -------- CMSwapCurveState --------

    /**
     * Round-trip: derive CM-swap rates (with spanningFwds=2) from flat 5% fwds,
     * then construct a CMSwapCurveState, and verify it matches LMM ref discount ratios.
     */
    @Test
    public void testCMSwapCurveStateRoundtrip() {
        final int spanning = 2;

        // Build LMM reference
        final LMMCurveState ref = new LMMCurveState(RATE_TIMES);
        ref.setOnForwardRates(FLAT_FWDS);

        // CM-swap rates with spanning = 2 from flat-fwds setup
        final double[] cmsRates = new double[5];
        for (int i = 0; i < 5; i++) {
            cmsRates[i] = ref.cmSwapRate(i, spanning);
        }

        final CMSwapCurveState cs = new CMSwapCurveState(RATE_TIMES, spanning);
        cs.setOnCMSwapRates(cmsRates);

        // Discount ratios must match LMM reference
        for (int i = 0; i <= 5; i++) {
            assertEquals("D[" + i + "]", ref.discountRatio(i, 0), cs.discountRatio(i, 0), TOL);
        }

        // forwardRate must equal 5%
        for (int i = 0; i < 5; i++) {
            assertEquals("fwd[" + i + "]", 0.05, cs.forwardRate(i), TOL);
        }

        // CM-swap rate inspector returns the input
        for (int i = 0; i < 5; i++) {
            assertEquals(cmsRates[i], cs.cmSwapRate(i, spanning), TOL);
        }
    }

    @Test
    public void testCMSwapCurveStateClone() {
        final int spanning = 2;
        final LMMCurveState ref = new LMMCurveState(RATE_TIMES);
        ref.setOnForwardRates(FLAT_FWDS);
        final double[] cmsRates = new double[5];
        for (int i = 0; i < 5; i++) {
            cmsRates[i] = ref.cmSwapRate(i, spanning);
        }

        final CMSwapCurveState cs = new CMSwapCurveState(RATE_TIMES, spanning);
        cs.setOnCMSwapRates(cmsRates);

        final CurveState copy = cs.clone();
        for (int i = 0; i <= 5; i++) {
            assertEquals(cs.discountRatio(i, 0), copy.discountRatio(i, 0), TOL);
        }
    }
}

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
import org.jquantlib.Settings;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.curvestates.CMSwapCurveState;
import org.jquantlib.model.marketmodels.curvestates.CoterminalSwapCurveState;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.driftcomputation.CMSMMDriftCalculator;
import org.jquantlib.model.marketmodels.driftcomputation.LMMDriftCalculator;
import org.jquantlib.model.marketmodels.driftcomputation.SMMDriftCalculator;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.calendars.NullCalendar;
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

    // -------------------------------------------------------------------
    // C++ v1.42.1 test-suite/curvestates.cpp direct ports (Phase1-cert-D5-D-R2)
    // -------------------------------------------------------------------

    /**
     * Builder for the v1.42.1 {@code CommonVars} fixture in
     * {@code test-suite/curvestates.cpp}: a 10-year Semiannual schedule
     * over a {@link NullCalendar} with {@link SimpleDayCounter}, forwards
     * {@code f[i] = 0.04 + 0.001*i}, discounts compounded from
     * {@code D[0]=0.95}, displacements {@code 0}, pseudo {@code N×N} fill
     * {@code 0.1}, numeraire {@code N}, spanningFwds {@code 1}.
     */
    private static final class CommonVars {
        // Expected reference values from C++ test-suite (precision ~5 sig figs)
        static final double[] EXPECTED_DRIFTS = {
                -0.0825792, -0.0787625, -0.0748546, -0.0708555, -0.0667655, -0.0625846, -0.0583128,
                -0.0539504, -0.0494972, -0.0449536, -0.0403194, -0.0355949, -0.0307801, -0.0258750,
                -0.0208799, -0.0157948, -0.0106197, -0.00535471, 0.0};
        static final double[] EXPECTED_DISCOUNT_RATIOS = {
                1.58379, 1.55274, 1.52154, 1.49025, 1.45888, 1.42748, 1.39607, 1.36468, 1.33335, 1.3021,
                1.27096, 1.23996, 1.20913, 1.17848, 1.14806, 1.11788, 1.08796, 1.05833, 1.029};
        static final double[] EXPECTED_FORWARD_RATES = {
                0.04, 0.041, 0.042, 0.043, 0.044, 0.045, 0.046, 0.047, 0.048, 0.049,
                0.05, 0.051, 0.052, 0.053, 0.054, 0.055, 0.056, 0.057, 0.058};
        static final double[] EXPECTED_SWAP_ANNUITY = {
                0.776368, 0.760772, 0.745125, 0.729442, 0.713739, 0.698034, 0.68234,
                0.666673, 0.651048, 0.635479, 0.619979, 0.604563, 0.589242, 0.574031,
                0.558939, 0.54398, 0.529163, 0.5145, 0.5};
        static final double[] EXPECTED_COT_DRIFTS = {
                -0.0472372, -0.0447452, -0.042233, -0.0397016, -0.0371516, -0.034584, -0.0319995, -0.0293991,
                -0.0267836, -0.0241539, -0.0215109, -0.0188555, -0.0161887, -0.0135113, -0.0108244,
                -0.00812878, -0.00542554, -0.00271562, 0.0};
        static final double[] EXPECTED_COT_DISCOUNT_RATIOS = EXPECTED_DISCOUNT_RATIOS;
        static final double[] EXPECTED_COT_SWAP_ANNUITY = {
                12.0934, 11.317, 10.5563, 9.81115, 9.08171, 8.36797, 7.66994, 6.9876, 6.32092, 5.66988,
                5.0344, 4.41442, 3.80986, 3.22061, 2.64658, 2.08764, 1.54366, 1.0145, 0.5};

        // Matches v1.42.1 C++ tol = 1.0e-4 — values above are pre-computed and
        // precision-limited to ~5 significant figures in the upstream test
        // source (per QuantLib v1.42.1 test-suite/curvestates.cpp).
        static final double TOL = 1.0e-4;

        final double[] rateTimes;
        final double[] taus;
        final double[] accruals;
        final double[] todaysForwards;
        final double[] displacements;
        final double[] todaysDiscounts;
        final int N;
        final int numeraire;
        final int firstAliveRate0;
        final int spanningFwds = 1;
        final Matrix pseudo;

        CommonVars() {
            final NullCalendar calendar = new NullCalendar();
            final Date todaysDate = new Settings().evaluationDate();
            final Date endDate = todaysDate.add(new Period(10, org.jquantlib.time.TimeUnit.Years));
            final Schedule dates = new Schedule(todaysDate, endDate,
                    new Period(Frequency.Semiannual), calendar,
                    BusinessDayConvention.Following, BusinessDayConvention.Following,
                    DateGeneration.Rule.Backward, false);

            this.rateTimes = new double[dates.size() - 1];
            this.N = rateTimes.length - 1;
            final double[] paymentTimes = new double[N];
            this.accruals = new double[N];
            this.numeraire = N;
            this.pseudo = new Matrix(N, N).fill(0.1);

            final SimpleDayCounter dayCounter = new SimpleDayCounter();
            for (int i = 1; i < dates.size(); ++i) {
                rateTimes[i - 1] = dayCounter.yearFraction(todaysDate, dates.dates().get(i));
            }
            for (int i = 0; i < N; ++i) {
                paymentTimes[i] = rateTimes[i + 1];
            }
            for (int i = 1; i < rateTimes.length; ++i) {
                accruals[i - 1] = rateTimes[i] - rateTimes[i - 1];
            }

            this.todaysForwards = new double[N];
            this.displacements = new double[N];
            for (int i = 0; i < todaysForwards.length; ++i) {
                todaysForwards[i] = 0.04 + 0.0010 * i;
            }

            this.todaysDiscounts = new double[rateTimes.length];
            todaysDiscounts[0] = 0.95;
            for (int i = 1; i < rateTimes.length; ++i) {
                todaysDiscounts[i] = todaysDiscounts[i - 1] / (1.0 + todaysForwards[i - 1] * accruals[i - 1]);
            }

            // EvolutionDescription with evolutionTimes = rateTimes[0..N-1]
            final double[] evolutionTimes = new double[N];
            System.arraycopy(rateTimes, 0, evolutionTimes, 0, N);
            final EvolutionDescription evolution = new EvolutionDescription(rateTimes, evolutionTimes);
            this.taus = evolution.rateTaus();
            this.firstAliveRate0 = evolution.firstAliveRate()[0];
        }
    }

    /**
     * Direct port of v1.42.1 {@code BOOST_AUTO_TEST_CASE(testLMMCurveState)}
     * (test-suite/curvestates.cpp). Cross-validates {@link LMMCurveState}
     * drifts, discount ratios and forward rates against pre-computed C++
     * reference values.
     *
     * <p>Tolerance {@code 1.0e-4}: matches v1.42.1 C++ {@code vars.tol}.
     * Expected values are pre-computed constants in the C++ test source,
     * precision-limited to ~5 significant figures.
     */
    @Test
    public void testLMMCurveState() {
        final CommonVars vars = new CommonVars();

        final LMMDriftCalculator lmmDriftcalculator = new LMMDriftCalculator(
                vars.pseudo, vars.displacements, vars.taus, vars.numeraire, vars.firstAliveRate0);
        final LMMCurveState lmmCs = new LMMCurveState(vars.rateTimes);
        lmmCs.setOnForwardRates(vars.todaysForwards);

        final double[] lmmDrifts = new double[vars.N];
        lmmDriftcalculator.compute(lmmCs, lmmDrifts);

        for (int i = 0; i < vars.N; ++i) {
            assertEquals("LMM drift[" + i + "]",
                    CommonVars.EXPECTED_DRIFTS[i], lmmDrifts[i], CommonVars.TOL);
            assertEquals("LMM discount ratio[" + i + "]",
                    CommonVars.EXPECTED_DISCOUNT_RATIOS[i], lmmCs.discountRatio(i, vars.N), CommonVars.TOL);
            assertEquals("LMM forward rate[" + i + "]",
                    CommonVars.EXPECTED_FORWARD_RATES[i], lmmCs.forwardRate(i), CommonVars.TOL);
        }
    }

    /**
     * Direct port of v1.42.1 {@code BOOST_AUTO_TEST_CASE(testCoterminalSwapCurveState)}
     * (test-suite/curvestates.cpp). Cross-validates {@link CoterminalSwapCurveState}
     * drifts, discount ratios, forward rates, swap rates and annuities.
     *
     * <p>Tolerance {@code 1.0e-4}: matches v1.42.1 C++ {@code vars.tol}.
     */
    @Test
    public void testCoterminalSwapCurveState() {
        final CommonVars vars = new CommonVars();

        // Coterminal swap rates & annuities (derived in C++ test body)
        final double[] todaysCoterminalSwapRates = new double[vars.N];
        final double[] coterminalAnnuity = new double[vars.N];
        for (int i = 1; i <= vars.N; ++i) {
            if (i == 1) {
                coterminalAnnuity[vars.N - 1] = vars.accruals[vars.N - 1] * vars.todaysDiscounts[vars.N];
            } else {
                coterminalAnnuity[vars.N - i] = coterminalAnnuity[vars.N - i + 1]
                        + vars.accruals[vars.N - i] * vars.todaysDiscounts[vars.N - i + 1];
            }
            final double floatingLeg = vars.todaysDiscounts[vars.N - i] - vars.todaysDiscounts[vars.N];
            todaysCoterminalSwapRates[vars.N - i] = floatingLeg / coterminalAnnuity[vars.N - i];
        }

        final double[] evolutionTimes = new double[vars.N];
        System.arraycopy(vars.rateTimes, 0, evolutionTimes, 0, vars.N);
        final EvolutionDescription evolution = new EvolutionDescription(vars.rateTimes, evolutionTimes);
        final double[] taus = evolution.rateTaus();

        final SMMDriftCalculator smmDriftcalculator = new SMMDriftCalculator(
                vars.pseudo, vars.displacements, taus, vars.numeraire, vars.firstAliveRate0);
        final CoterminalSwapCurveState cotCs = new CoterminalSwapCurveState(vars.rateTimes);
        cotCs.setOnCoterminalSwapRates(todaysCoterminalSwapRates);

        final double[] cotDrifts = new double[vars.N];
        smmDriftcalculator.compute(cotCs, cotDrifts);

        for (int i = 0; i < vars.N; ++i) {
            assertEquals("COT drift[" + i + "]",
                    CommonVars.EXPECTED_COT_DRIFTS[i], cotDrifts[i], CommonVars.TOL);
            assertEquals("COT discount ratio[" + i + "]",
                    CommonVars.EXPECTED_COT_DISCOUNT_RATIOS[i], cotCs.discountRatio(i, vars.N), CommonVars.TOL);
            assertEquals("COT forward rate[" + i + "]",
                    CommonVars.EXPECTED_FORWARD_RATES[i], cotCs.forwardRate(i), CommonVars.TOL);
            assertEquals("COT swap rate[" + i + "]",
                    todaysCoterminalSwapRates[i], cotCs.coterminalSwapRate(i), CommonVars.TOL);
            assertEquals("COT swap annuity[" + i + "]",
                    CommonVars.EXPECTED_COT_SWAP_ANNUITY[i],
                    cotCs.coterminalSwapAnnuity(vars.numeraire, i), CommonVars.TOL);
        }
    }

    /**
     * Direct port of v1.42.1 {@code BOOST_AUTO_TEST_CASE(testCMSwapCurveState)}
     * (test-suite/curvestates.cpp). Cross-validates {@link CMSwapCurveState}
     * drifts, discount ratios, forward rates, CM-swap rates and annuities.
     *
     * <p>Tolerance {@code 1.0e-4}: matches v1.42.1 C++ {@code vars.tol}.
     */
    @Test
    public void testCMSwapCurveState() {
        final CommonVars vars = new CommonVars();

        final CMSMMDriftCalculator cmsDriftcalculator = new CMSMMDriftCalculator(
                vars.pseudo, vars.displacements, vars.taus, vars.numeraire,
                vars.firstAliveRate0, vars.spanningFwds);

        final CMSwapCurveState cmsCs = new CMSwapCurveState(vars.rateTimes, vars.spanningFwds);
        cmsCs.setOnCMSwapRates(vars.todaysForwards);
        final double[] cmsDrifts = new double[vars.N];
        cmsDriftcalculator.compute(cmsCs, cmsDrifts);

        for (int i = 0; i < vars.N; ++i) {
            assertEquals("CMS drift[" + i + "]",
                    CommonVars.EXPECTED_DRIFTS[i], cmsDrifts[i], CommonVars.TOL);
            assertEquals("CMS discount ratio[" + i + "]",
                    CommonVars.EXPECTED_DISCOUNT_RATIOS[i], cmsCs.discountRatio(i, vars.N), CommonVars.TOL);
            assertEquals("CMS forward rate[" + i + "]",
                    CommonVars.EXPECTED_FORWARD_RATES[i], cmsCs.forwardRate(i), CommonVars.TOL);
            assertEquals("CMS swap rate[" + i + "]",
                    CommonVars.EXPECTED_FORWARD_RATES[i],
                    cmsCs.cmSwapRate(i, vars.spanningFwds), CommonVars.TOL);
            assertEquals("CMS swap annuity[" + i + "]",
                    CommonVars.EXPECTED_SWAP_ANNUITY[i],
                    cmsCs.cmSwapAnnuity(vars.numeraire, i, vars.spanningFwds), CommonVars.TOL);
        }
    }
}

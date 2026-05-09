/*
Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.8.

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

package org.jquantlib.testsuite.model.marketmodels.products.multistep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoinitialSwaps;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaps;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepCoterminalSwaptions;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepInverseFloater;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepRatchet;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepTarn;
import org.junit.Test;

/**
 * Tests for the complex-multistep products in
 * {@code org.jquantlib.model.marketmodels.products.multistep} (batch 2).
 */
public class MultiStepComplexProductsTest {

    public MultiStepComplexProductsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    private static MarketModelMultiProduct.CashFlow[][] allocate(final int p, final int n) {
        final MarketModelMultiProduct.CashFlow[][] cf = new MarketModelMultiProduct.CashFlow[p][n];
        for (int i = 0; i < p; ++i) {
            for (int j = 0; j < n; ++j) {
                cf[i][j] = new MarketModelMultiProduct.CashFlow();
            }
        }
        return cf;
    }

    @Test
    public void testMultiStepCoinitialSwapsFirstStep() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] fa = {0.5, 0.5, 0.5, 0.5};
        final double[] fla = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double fixedRate = 0.04;
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final MultiStepCoinitialSwaps p = new MultiStepCoinitialSwaps(
                rateTimes, fa, fla, paymentTimes, fixedRate);
        assertEquals(4, p.numberOfProducts());
        assertEquals(2, p.maxNumberOfCashFlowsPerProductPerStep());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        // step 0: i in [0..3] all get 2 cashflows from indexOfTime=0
        final int[] nCF = new int[4];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 2);
        final boolean done0 = p.nextTimeStep(state, nCF, cf);
        assertFalse(done0);

        // each product i (0..3) has 2 cashflows
        for (int i = 0; i < 4; ++i) {
            assertEquals(2, nCF[i]);
            assertEquals(0, cf[i][0].timeIndex);
            assertEquals(-fixedRate * fa[0], cf[i][0].amount, TOL);
            assertEquals(forwardRates[0] * fla[0], cf[i][1].amount, TOL);
        }
    }

    @Test
    public void testMultiStepCoterminalSwapsFinalStep() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] fa = {0.5, 0.5, 0.5, 0.5};
        final double[] fla = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double fixedRate = 0.04;
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final MultiStepCoterminalSwaps p = new MultiStepCoterminalSwaps(
                rateTimes, fa, fla, paymentTimes, fixedRate);
        assertEquals(4, p.numberOfProducts());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        // run through all 4 steps; on step k, i in [0..k] all emit 2 cf
        for (int step = 0; step < 4; ++step) {
            final int[] nCF = new int[4];
            final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 2);
            final boolean done = p.nextTimeStep(state, nCF, cf);
            for (int i = 0; i <= step; ++i) {
                assertEquals(2, nCF[i]);
                assertEquals(step, cf[i][0].timeIndex);
                assertEquals(-fixedRate * fa[step], cf[i][0].amount, TOL);
                assertEquals(forwardRates[step] * fla[step], cf[i][1].amount, TOL);
            }
            for (int i = step + 1; i < 4; ++i) {
                assertEquals(0, nCF[i]);
            }
            if (step == 3) {
                assertTrue(done);
            }
        }
    }

    @Test
    public void testMultiStepCoterminalSwaptions() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final StrikedTypePayoff[] payoffs = new StrikedTypePayoff[4];
        for (int i = 0; i < 4; ++i) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.04);
        }
        final double[] forwardRates = {0.05, 0.05, 0.05, 0.05};

        final MultiStepCoterminalSwaptions p = new MultiStepCoterminalSwaptions(
                rateTimes, paymentTimes, payoffs);
        assertEquals(4, p.numberOfProducts());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        final int[] nCF = new int[4];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 1);
        final boolean done = p.nextTimeStep(state, nCF, cf);
        // currentIndex_ now=1 (lastIndex_=4); not done
        assertFalse(done);
        // product 0 emits the first swaption cashflow
        assertEquals(1, nCF[0]);
        // amount = payoff(swapRate)*annuity (positive since fwd>strike)
        assertTrue(cf[0][0].amount > 0);
    }

    @Test
    public void testMultiStepInverseFloaterPayer() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] fa = {0.5, 0.5, 0.5, 0.5};
        final double[] fla = {0.5, 0.5, 0.5, 0.5};
        final double[] strikes = {0.10, 0.10, 0.10, 0.10};
        final double[] mult = {1.0, 1.0, 1.0, 1.0};
        final double[] spread = {0.0, 0.0, 0.0, 0.0};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final MultiStepInverseFloater p = new MultiStepInverseFloater(
                rateTimes, fa, fla, strikes, mult, spread, paymentTimes, true);

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        // step 0
        final int[] nCF = new int[1];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 1);
        p.nextTimeStep(state, nCF, cf);
        // payer multiplier=-1; inverse=max(0.10-1*0.04,0)*0.5=0.03; floating=(0.04+0)*0.5=0.02
        // amount = -1 * (0.03 - 0.02) = -0.01
        assertEquals(1, nCF[0]);
        assertEquals(-0.01, cf[0][0].amount, TOL);
    }

    @Test
    public void testMultiStepRatchetCouponMonotone() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        // gearings=1, spreads=0, initial floor=0; floor grows to max(prev_floor, libor)
        final MultiStepRatchet p = new MultiStepRatchet(
                rateTimes, accruals, paymentTimes,
                1.0, 1.0, 0.0, 0.0, 0.0);
        final double[] forwardRates = {0.04, 0.05, 0.045, 0.055};

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        final double[] expectedFloor = {0.04, 0.05, 0.05, 0.055};
        for (int step = 0; step < 4; ++step) {
            final int[] nCF = new int[1];
            final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 1);
            p.nextTimeStep(state, nCF, cf);
            assertEquals(1, nCF[0]);
            // amount = +1 * accrual * coupon = 0.5 * coupon
            // expected coupon = max(prev_floor, libor); with prev_floor=expected[step-1] (or initial 0)
            assertEquals(0.5 * expectedFloor[step], cf[0][0].amount, TOL);
        }
    }

    @Test
    public void testMultiStepTarnTerminatesAtTotalCoupon() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] accrualsF = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] paymentTimesF = {1.0, 1.5, 2.0, 2.5};
        // strike=0.10, mult=1, libor=0.04 → obviousCoupon = max(0.06,0)*0.5 = 0.03 per step
        // totalCoupon=0.05 → terminates at step 1 (after first 0.03 paid, second adds to 0.06)
        final double totalCoupon = 0.05;
        final double[] strikes = {0.10, 0.10, 0.10, 0.10};
        final double[] multipliers = {1.0, 1.0, 1.0, 1.0};
        final double[] spreads = {0.0, 0.0, 0.0, 0.0};
        final double[] forwardRates = {0.04, 0.04, 0.04, 0.04};

        final MultiStepTarn p = new MultiStepTarn(rateTimes, accruals, accrualsF,
                paymentTimes, paymentTimesF, totalCoupon, strikes, multipliers, spreads);

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        // step 0: couponPaid=0.03, < 0.05, currentIndex=1 < lastIndex=4 → false
        final int[] nCF = new int[1];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 2);
        boolean done = p.nextTimeStep(state, nCF, cf);
        assertFalse(done);
        assertEquals(2, nCF[0]);
        // floating: (0.04+0)*0.5 = 0.02
        assertEquals(0.02, cf[0][0].amount, TOL);
        // first inverse coupon: -0.03
        assertEquals(-0.03, cf[0][1].amount, TOL);

        // step 1: couponPaid becomes 0.06 ≥ 0.05 → terminate; truncate so exactly 0.05 paid
        // remaining = 0.05 - 0.06 = -0.01; coupon = obviousCoupon (0.03) + (-0.01) = 0.02
        // genCashFlows[0][1].amount = -coupon = -0.02
        done = p.nextTimeStep(state, nCF, cf);
        assertTrue(done);
        assertEquals(-0.02, cf[0][1].amount, TOL);
    }
}

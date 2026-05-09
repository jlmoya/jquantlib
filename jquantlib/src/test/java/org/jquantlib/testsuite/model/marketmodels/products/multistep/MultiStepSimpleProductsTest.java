/*
Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.7.

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
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.products.multistep.MarketModelCashRebate;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepForwards;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepNothing;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepOptionlets;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepSwap;
import org.jquantlib.model.marketmodels.products.multistep.MultiStepSwaption;
import org.junit.Test;

/**
 * Tests for the simple-multistep products in
 * {@code org.jquantlib.model.marketmodels.products.multistep}.
 */
public class MultiStepSimpleProductsTest {

    public MultiStepSimpleProductsTest() {
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
    public void testMultiStepForwardsAcrossAllSteps() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] strikes = {0.04, 0.04, 0.04, 0.04};
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final MultiStepForwards p = new MultiStepForwards(rateTimes, accruals, paymentTimes, strikes);
        assertEquals(4, p.numberOfProducts());
        assertEquals(1, p.maxNumberOfCashFlowsPerProductPerStep());
        assertEquals(4, p.evolution().numberOfSteps());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        for (int step = 0; step < 4; ++step) {
            final int[] nCF = new int[4];
            final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 1);
            final boolean done = p.nextTimeStep(state, nCF, cf);
            // only product `step` should emit
            for (int i = 0; i < 4; ++i) {
                if (i == step) {
                    assertEquals(1, nCF[i]);
                    final double expected = (forwardRates[i] - strikes[i]) * accruals[i];
                    assertEquals(expected, cf[i][0].amount, TOL);
                } else {
                    assertEquals(0, nCF[i]);
                }
            }
            if (step == 3) {
                assertTrue(done);
            } else {
                assertFalse(done);
            }
        }
    }

    @Test
    public void testMultiStepOptionletsAcrossAllSteps() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[4];
        for (int i = 0; i < 4; ++i) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.045);
        }

        final MultiStepOptionlets p = new MultiStepOptionlets(rateTimes, accruals, paymentTimes, payoffs);
        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        for (int step = 0; step < 4; ++step) {
            final int[] nCF = new int[4];
            final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 1);
            p.nextTimeStep(state, nCF, cf);
            assertEquals(1, nCF[step]);
            final double payoff = Math.max(forwardRates[step] - 0.045, 0.0);
            assertEquals(payoff * accruals[step], cf[step][0].amount, TOL);
        }
    }

    @Test
    public void testMultiStepSwapPayer() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] fa = {0.5, 0.5, 0.5, 0.5};
        final double[] fla = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double fixedRate = 0.04;
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final MultiStepSwap p = new MultiStepSwap(rateTimes, fa, fla, paymentTimes, fixedRate);
        assertEquals(1, p.numberOfProducts());
        assertEquals(2, p.maxNumberOfCashFlowsPerProductPerStep());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        // step 0..3 — the lastIndex_=4 condition stops at step=3 (returns true when
        // currentIndex_==lastIndex_=4, which happens after the step that increments to 4)
        for (int step = 0; step < 4; ++step) {
            final int[] nCF = new int[1];
            final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 2);
            final boolean done = p.nextTimeStep(state, nCF, cf);
            assertEquals(2, nCF[0]);
            // payer: multiplier=+1 → fixed leg negative, floating leg positive
            assertEquals(-fixedRate * fa[step], cf[0][0].amount, TOL);
            assertEquals(forwardRates[step] * fla[step], cf[0][1].amount, TOL);
            if (step == 3) {
                assertTrue(done);
            }
        }
    }

    @Test
    public void testMultiStepSwapReceiver() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] fa = {0.5, 0.5, 0.5, 0.5};
        final double[] fla = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double fixedRate = 0.04;
        final double[] forwardRates = {0.05, 0.05, 0.05, 0.05};

        final MultiStepSwap p = new MultiStepSwap(rateTimes, fa, fla, paymentTimes, fixedRate, false);

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        final int[] nCF = new int[1];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 2);
        p.nextTimeStep(state, nCF, cf);
        // receiver: multiplier=-1 → fixed leg positive, floating leg negative
        assertEquals(+fixedRate * fa[0], cf[0][0].amount, TOL);
        assertEquals(-forwardRates[0] * fla[0], cf[0][1].amount, TOL);
    }

    @Test
    public void testMultiStepNothingNoCashFlows() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final EvolutionDescription evo = new EvolutionDescription(rateTimes);
        final MultiStepNothing p = new MultiStepNothing(evo, 2, 4);
        assertEquals(2, p.numberOfProducts());
        assertEquals(0, p.maxNumberOfCashFlowsPerProductPerStep());

        p.reset();
        for (int step = 0; step < 4; ++step) {
            final int[] nCF = new int[2];
            final MarketModelMultiProduct.CashFlow[][] cf = allocate(2, 1);
            final boolean done = p.nextTimeStep(null, nCF, cf);
            assertEquals(0, nCF[0]);
            assertEquals(0, nCF[1]);
            // currentIndex_ becomes step+1; done when currentIndex_ >= doneIndex_=4
            // step=0: currentIndex_=1, not done. ... step=3: currentIndex_=4, done.
            if (step == 3) {
                assertTrue(done);
            } else {
                assertFalse(done);
            }
        }
    }

    @Test
    public void testCashRebateFixedAmounts() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final EvolutionDescription evo = new EvolutionDescription(rateTimes);
        final double[] paymentTimes = evo.evolutionTimes(); // {0.5, 1.0, 1.5, 2.0}
        // 1 product × 4 payment times → 4 amounts
        final Matrix amounts = new Matrix(1, 4);
        amounts.set(0, 0, 1.0);
        amounts.set(0, 1, 2.0);
        amounts.set(0, 2, 3.0);
        amounts.set(0, 3, 4.0);

        final MarketModelCashRebate p = new MarketModelCashRebate(evo, paymentTimes, amounts, 1);
        assertEquals(1, p.numberOfProducts());
        assertEquals(1, p.maxNumberOfCashFlowsPerProductPerStep());

        p.reset();
        for (int step = 0; step < 4; ++step) {
            final int[] nCF = new int[1];
            final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 1);
            p.nextTimeStep(null, nCF, cf);
            assertEquals(1, nCF[0]);
            assertEquals(step, cf[0][0].timeIndex);
            assertEquals((double)(step + 1), cf[0][0].amount, TOL);
        }
    }

    @Test
    public void testMultiStepSwaptionEmitsAtStartIndex() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final int startIndex = 1;
        final int endIndex = 3;
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 0.045);
        final double[] forwardRates = {0.04, 0.05, 0.05, 0.05};

        final MultiStepSwaption p = new MultiStepSwaption(rateTimes, startIndex, endIndex, payoff);
        assertEquals(1, p.numberOfProducts());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        p.reset();
        // first call (step 0): currentIndex==0 != startIndex, so 0 cashflows, return false
        final int[] nCF = new int[1];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 1);
        final boolean done0 = p.nextTimeStep(state, nCF, cf);
        assertFalse(done0);
        assertEquals(0, nCF[0]);
        // second call (currentIndex==1 == startIndex): emit and return true
        final boolean done1 = p.nextTimeStep(state, nCF, cf);
        assertTrue(done1);
        // payoff > 0 since fwds > strike, so 1 cashflow
        assertEquals(1, nCF[0]);
    }
}

/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k Track C tests.

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

package org.jquantlib.testsuite.model.marketmodels.products.pathwise;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct.CashFlow;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseCashRebate;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseInverseFloater;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseSwap;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the simpler pathwise products (Phase 3k Track C C.1, C.3, C.4).
 *
 * <p>Verifies the {@code amount[]} vector entries (payoff at index 0,
 * derivatives at indices ≥ 1) against analytic expressions derived directly
 * from the C++ implementation. Tolerance: 1e-12 (deterministic, no MC noise).
 */
public class PathwiseCashRebateAndSwapTest {

    private static final double TOL = 1e-12;

    private static CashFlow[][] makeCashFlowGrid(final int numProducts,
                                                 final int maxFlowsPerProductPerStep,
                                                 final int numRates) {
        final CashFlow[][] grid = new CashFlow[numProducts][maxFlowsPerProductPerStep];
        for (int i = 0; i < numProducts; ++i) {
            for (int j = 0; j < maxFlowsPerProductPerStep; ++j) {
                grid[i][j] = new CashFlow(0, new double[numRates + 1]);
            }
        }
        return grid;
    }

    @Test
    public void testCashRebateBasic() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] paymentTimes = {0.5, 1.0, 1.5, 2.0};
        final double[] evolTimes = {0.5, 1.0, 1.5, 2.0};
        final EvolutionDescription evol = new EvolutionDescription(rateTimes, evolTimes);

        // 2 products × 4 payment times — known amounts
        final Matrix amounts = new Matrix(2, 4);
        amounts.set(0, 0, 1.0); amounts.set(0, 1, 2.0); amounts.set(0, 2, 3.0); amounts.set(0, 3, 4.0);
        amounts.set(1, 0, 10.0); amounts.set(1, 1, 20.0); amounts.set(1, 2, 30.0); amounts.set(1, 3, 40.0);

        final MarketModelPathwiseCashRebate product = new MarketModelPathwiseCashRebate(
                evol, paymentTimes, amounts, 2);

        Assert.assertEquals(2, product.numberOfProducts());
        Assert.assertEquals(1, product.maxNumberOfCashFlowsPerProductPerStep());
        Assert.assertEquals(false, product.alreadyDeflated());

        // Step through path
        final int[] nFlows = new int[2];
        final CashFlow[][] flows = makeCashFlowGrid(2, 1, 4);

        product.reset();
        for (int step = 0; step < 4; ++step) {
            // currentState is unused by CashRebate; pass an LMM stub
            final LMMCurveState cs = new LMMCurveState(rateTimes);
            cs.setOnForwardRates(new double[]{0.04, 0.04, 0.04, 0.04});
            product.nextTimeStep(cs, nFlows, flows);
            Assert.assertEquals(1, nFlows[0]);
            Assert.assertEquals(1, nFlows[1]);
            Assert.assertEquals(step, flows[0][0].timeIndex);
            Assert.assertEquals((step + 1) * 1.0, flows[0][0].amount[0], TOL);
            Assert.assertEquals((step + 1) * 10.0, flows[1][0].amount[0], TOL);
            // derivatives all zero
            for (int k = 1; k <= 4; ++k) {
                Assert.assertEquals(0.0, flows[0][0].amount[k], TOL);
                Assert.assertEquals(0.0, flows[1][0].amount[k], TOL);
            }
        }
    }

    @Test
    public void testSwapBasic() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] strikes = {0.04, 0.04, 0.04, 0.04};
        final double[] forwards = {0.045, 0.05, 0.055, 0.06};

        final MarketModelPathwiseSwap product = new MarketModelPathwiseSwap(
                rateTimes, accruals, strikes, 1.0);

        Assert.assertEquals(1, product.numberOfProducts());
        Assert.assertEquals(1, product.maxNumberOfCashFlowsPerProductPerStep());

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(forwards);

        final int[] nFlows = new int[1];
        final CashFlow[][] flows = makeCashFlowGrid(1, 1, 4);

        product.reset();
        for (int step = 0; step < 4; ++step) {
            product.nextTimeStep(cs, nFlows, flows);
            Assert.assertEquals(1, nFlows[0]);
            Assert.assertEquals(step + 1, flows[0][0].timeIndex);
            // amount[0] = (libor - strike) * accrual = (forwards[step] - 0.04) * 0.5
            final double expected = (forwards[step] - 0.04) * 0.5;
            Assert.assertEquals(expected, flows[0][0].amount[0], TOL);
            // derivative w.r.t. forward[step] = accrual = 0.5; rest zero
            for (int k = 1; k <= 4; ++k) {
                final double expDeriv = (k == step + 1) ? 0.5 : 0.0;
                Assert.assertEquals(expDeriv, flows[0][0].amount[k], TOL);
            }
        }
    }

    @Test
    public void testSwapPayer() {
        // multiplier = -1.0 (payer)
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final double[] accruals = {0.5, 0.5};
        final double[] strikes = {0.04, 0.04};
        final double[] forwards = {0.05, 0.06};

        final MarketModelPathwiseSwap product = new MarketModelPathwiseSwap(
                rateTimes, accruals, strikes, -1.0);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(forwards);

        final int[] nFlows = new int[1];
        final CashFlow[][] flows = makeCashFlowGrid(1, 1, 2);

        product.reset();
        product.nextTimeStep(cs, nFlows, flows);
        // amount[0] = -(0.05 - 0.04)*0.5 = -0.005
        Assert.assertEquals(-0.005, flows[0][0].amount[0], TOL);
        // derivative w.r.t. forward[0] = -0.5
        Assert.assertEquals(-0.5, flows[0][0].amount[1], TOL);
        Assert.assertEquals(0.0, flows[0][0].amount[2], TOL);
    }

    @Test
    public void testInverseFloaterBasic() {
        // 3-rate IF: strike=0.06, mult=2.0; floating spread=0.001, payer
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final double[] fixedAcc = {0.5, 0.5, 0.5};
        final double[] floatAcc = {0.5, 0.5, 0.5};
        final double[] strikes = {0.06, 0.06, 0.06};
        final double[] mults = {2.0, 2.0, 2.0};
        final double[] spreads = {0.001, 0.001, 0.001};
        final double[] payTimes = {0.5, 1.0, 1.5};

        // forwards in-the-money for inverse floater (libor < strike/mult = 0.03)
        // Pick: 0.02 → IF coupon = (0.06 - 2*0.02)*0.5 = 0.01; floating = (0.02 + 0.001)*0.5 = 0.0105
        // amount[0] (payer) = -1*(0.01 - 0.0105) = 0.0005
        // derivative w.r.t. forward[0] (in-money branch) = -1 * (-2*0.5 - 0.5) = 1.5
        final MarketModelPathwiseInverseFloater product = new MarketModelPathwiseInverseFloater(
                rateTimes, fixedAcc, floatAcc, strikes, mults, spreads, payTimes, true);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.02, 0.02, 0.02});

        final int[] nFlows = new int[1];
        final CashFlow[][] flows = makeCashFlowGrid(1, 1, 3);

        product.reset();
        product.nextTimeStep(cs, nFlows, flows);
        Assert.assertEquals(1, nFlows[0]);
        Assert.assertEquals(0, flows[0][0].timeIndex);
        Assert.assertEquals(0.0005, flows[0][0].amount[0], TOL);
        Assert.assertEquals(1.5, flows[0][0].amount[1], TOL);
        Assert.assertEquals(0.0, flows[0][0].amount[2], TOL);
        Assert.assertEquals(0.0, flows[0][0].amount[3], TOL);
    }

    @Test
    public void testInverseFloaterOutOfMoney() {
        // forwards out-of-the-money for inverse floater (libor > strike/mult)
        // strike=0.06, mult=1.0, libor=0.08 → IF coupon = max(0.06 - 0.08, 0)*0.5 = 0
        // floating = (0.08 + 0)*0.5 = 0.04
        // amount[0] (receiver) = +(0 - 0.04) = -0.04
        // derivative w.r.t. forward[0] (out-money branch) = -(-1)*0.5 = wrong... let me re-derive
        // multiplier_ for receiver (payer=false) = +1.0
        // out-of-money: amount[k+1] = -multiplier_*floatAcc[k] = -1*0.5 = -0.5
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final double[] fixedAcc = {0.5, 0.5};
        final double[] floatAcc = {0.5, 0.5};
        final double[] strikes = {0.06, 0.06};
        final double[] mults = {1.0, 1.0};
        final double[] spreads = {0.0, 0.0};
        final double[] payTimes = {0.5, 1.0};

        final MarketModelPathwiseInverseFloater product = new MarketModelPathwiseInverseFloater(
                rateTimes, fixedAcc, floatAcc, strikes, mults, spreads, payTimes, false);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.08, 0.08});

        final int[] nFlows = new int[1];
        final CashFlow[][] flows = makeCashFlowGrid(1, 1, 2);

        product.reset();
        product.nextTimeStep(cs, nFlows, flows);
        Assert.assertEquals(-0.04, flows[0][0].amount[0], TOL);
        Assert.assertEquals(-0.5, flows[0][0].amount[1], TOL);
        Assert.assertEquals(0.0, flows[0][0].amount[2], TOL);
    }

    @Test
    public void testSwapCloneRetainsState() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final double[] accruals = {0.5, 0.5};
        final double[] strikes = {0.04, 0.04};
        final MarketModelPathwiseSwap p = new MarketModelPathwiseSwap(
                rateTimes, accruals, strikes, 1.0);

        // step once
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.06});
        final int[] nFlows = new int[1];
        final CashFlow[][] flows = makeCashFlowGrid(1, 1, 2);
        p.nextTimeStep(cs, nFlows, flows);

        final MarketModelPathwiseMultiProduct copy = p.clone();
        copy.nextTimeStep(cs, nFlows, flows);
        // copy proceeds from step 1 of original
        Assert.assertEquals(2, flows[0][0].timeIndex);
    }
}

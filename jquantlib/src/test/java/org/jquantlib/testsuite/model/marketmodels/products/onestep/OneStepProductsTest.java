/*
Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.3.

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

package org.jquantlib.testsuite.model.marketmodels.products.onestep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.products.onestep.OneStepCoinitialSwaps;
import org.jquantlib.model.marketmodels.products.onestep.OneStepCoterminalSwaps;
import org.jquantlib.model.marketmodels.products.onestep.OneStepForwards;
import org.jquantlib.model.marketmodels.products.onestep.OneStepOptionlets;
import org.junit.Test;

/**
 * Tests for the four one-step products in
 * {@code org.jquantlib.model.marketmodels.products.onestep}.
 *
 * <p>Each test feeds a known set of forward rates via {@link LMMCurveState}
 * and verifies the cash-flow amounts against the C++ implementation in
 * {@code ql/models/marketmodels/products/onestep/} (v1.42.1).
 */
public class OneStepProductsTest {

    public OneStepProductsTest() {
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

    /**
     * 4-rate grid: rateTimes={0.5,1.0,1.5,2.0,2.5}, accruals=0.5, strikes=0.04.
     * Verifies that OneStepForwards emits one cash flow per product
     * with amount = (rate - strike) * accrual.
     */
    @Test
    public void testOneStepForwards() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] strikes = {0.04, 0.04, 0.04, 0.04};
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final OneStepForwards p = new OneStepForwards(rateTimes, accruals, paymentTimes, strikes);
        assertEquals(4, p.numberOfProducts());
        assertEquals(1, p.maxNumberOfCashFlowsPerProductPerStep());
        assertEquals(1, p.evolution().numberOfSteps());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        final int[] nCF = new int[4];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 1);
        final boolean done = p.nextTimeStep(state, nCF, cf);
        assertTrue(done);

        for (int i = 0; i < 4; ++i) {
            assertEquals(1, nCF[i]);
            assertEquals(i, cf[i][0].timeIndex);
            final double expected = (forwardRates[i] - strikes[i]) * accruals[i];
            assertEquals(expected, cf[i][0].amount, TOL);
        }

        // verify clone
        final OneStepForwards p2 = (OneStepForwards) p.clone();
        assertEquals(4, p2.numberOfProducts());
    }

    /**
     * 4-rate grid; all-call payoffs; verify payoff > 0 → cashflow emitted.
     */
    @Test
    public void testOneStepOptionlets() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[4];
        for (int i = 0; i < 4; ++i) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.045);
        }

        final OneStepOptionlets p = new OneStepOptionlets(rateTimes, accruals, paymentTimes, payoffs);
        assertEquals(4, p.numberOfProducts());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        final int[] nCF = new int[4];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 1);
        final boolean done = p.nextTimeStep(state, nCF, cf);
        assertTrue(done);

        // i=0: rate=0.04 → payoff=max(0.04-0.045,0)=0 → no cashflow
        assertEquals(0, nCF[0]);
        // i=1: rate=0.045 → payoff=0 → no cashflow
        assertEquals(0, nCF[1]);
        // i=2: rate=0.05 → payoff=0.005 → cashflow=0.005*0.5=0.0025
        assertEquals(1, nCF[2]);
        assertEquals(0.0025, cf[2][0].amount, TOL);
        // i=3: rate=0.055 → payoff=0.01 → cashflow=0.005
        assertEquals(1, nCF[3]);
        assertEquals(0.005, cf[3][0].amount, TOL);
    }

    /**
     * OneStepCoinitialSwaps: lastIndex=4, so 4 products, each receiving
     * 2*lastIndex=8 cash flows max. Each product i gets 2*(i+1) cash flows
     * (for indexOfTime in [0..i]).
     * Wait — re-reading C++: for indexOfTime in [0..lastIndex), for i in
     * [indexOfTime..lastIndex), each product i accumulates 2 cash flows per
     * indexOfTime <= i. So product 0 gets 2 (indexOfTime=0); product 1 gets
     * 4 (indexOfTime=0,1); product 3 gets 8 (indexOfTime=0..3).
     */
    @Test
    public void testOneStepCoinitialSwaps() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] fixedAccruals = {0.5, 0.5, 0.5, 0.5};
        final double[] floatingAccruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double fixedRate = 0.04;
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final OneStepCoinitialSwaps p = new OneStepCoinitialSwaps(
                rateTimes, fixedAccruals, floatingAccruals, paymentTimes, fixedRate);
        assertEquals(4, p.numberOfProducts());
        assertEquals(8, p.maxNumberOfCashFlowsPerProductPerStep());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        final int[] nCF = new int[4];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 8);
        final boolean done = p.nextTimeStep(state, nCF, cf);
        assertTrue(done);

        // For each indexOfTime t and each i >= t (within lastIndex), 2 cf added.
        // product 0: t=0 only → 2
        // product 1: t=0,1 → 4
        // product 2: t=0,1,2 → 6
        // product 3: t=0,1,2,3 → 8
        assertEquals(2, nCF[0]);
        assertEquals(4, nCF[1]);
        assertEquals(6, nCF[2]);
        assertEquals(8, nCF[3]);

        // Spot-check: product 3, indexOfTime=2 → cf[3][4]/cf[3][5]
        assertEquals(2, cf[3][4].timeIndex);
        assertEquals(-fixedRate * fixedAccruals[2], cf[3][4].amount, TOL);
        assertEquals(2, cf[3][5].timeIndex);
        assertEquals(forwardRates[2] * floatingAccruals[2], cf[3][5].amount, TOL);
    }

    /**
     * OneStepCoterminalSwaps: 4 products, each ending at the last rate.
     * For each indexOfTime t, for i in [0..t], 2 cash flows added.
     * Product 0 gets 2 cash flows for each of t=0,1,2,3 → 8 total.
     * Product 1 gets 2 for each of t=1,2,3 → 6.
     * Product 3 gets 2 for t=3 → 2.
     */
    @Test
    public void testOneStepCoterminalSwaps() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] fixedAccruals = {0.5, 0.5, 0.5, 0.5};
        final double[] floatingAccruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double fixedRate = 0.04;
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final OneStepCoterminalSwaps p = new OneStepCoterminalSwaps(
                rateTimes, fixedAccruals, floatingAccruals, paymentTimes, fixedRate);
        assertEquals(4, p.numberOfProducts());
        assertEquals(8, p.maxNumberOfCashFlowsPerProductPerStep());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        final int[] nCF = new int[4];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 8);
        final boolean done = p.nextTimeStep(state, nCF, cf);
        assertTrue(done);

        // product 0: t=0,1,2,3 → 8
        // product 1: t=1,2,3 → 6
        // product 2: t=2,3 → 4
        // product 3: t=3 → 2
        assertEquals(8, nCF[0]);
        assertEquals(6, nCF[1]);
        assertEquals(4, nCF[2]);
        assertEquals(2, nCF[3]);

        // Spot check product 3 at the only indexOfTime=3, slot (3-3)*2=0
        assertEquals(3, cf[3][0].timeIndex);
        assertEquals(-fixedRate * fixedAccruals[3], cf[3][0].amount, TOL);
        assertEquals(3, cf[3][1].timeIndex);
        assertEquals(forwardRates[3] * floatingAccruals[3], cf[3][1].amount, TOL);
    }
}

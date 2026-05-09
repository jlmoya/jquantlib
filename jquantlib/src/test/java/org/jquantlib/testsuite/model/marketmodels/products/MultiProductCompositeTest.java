/*
Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k A.4-A.6.

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

package org.jquantlib.testsuite.model.marketmodels.products;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.products.MultiProductComposite;
import org.jquantlib.model.marketmodels.products.SingleProductComposite;
import org.jquantlib.model.marketmodels.products.onestep.OneStepForwards;
import org.jquantlib.model.marketmodels.products.onestep.OneStepOptionlets;
import org.junit.Test;

/**
 * Tests for {@link MultiProductComposite} and
 * {@link SingleProductComposite}. Mirrors the C++ test setup of
 * {@code testOneStepForwardsAndOptionlets}.
 */
public class MultiProductCompositeTest {

    public MultiProductCompositeTest() {
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
    public void testMultiCompositeForwardsAndOptionlets() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] strikes = {0.04, 0.04, 0.04, 0.04};
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final OneStepForwards fwds = new OneStepForwards(rateTimes, accruals, paymentTimes, strikes);
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[4];
        for (int i = 0; i < 4; ++i) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.045);
        }
        final OneStepOptionlets opts = new OneStepOptionlets(rateTimes, accruals, paymentTimes, payoffs);

        final MultiProductComposite c = new MultiProductComposite();
        c.add(fwds);
        c.add(opts);
        c.finalizeComposite();

        // 4 forwards + 4 optionlets
        assertEquals(8, c.numberOfProducts());
        // both share the same single evolution time
        assertEquals(1, c.evolution().numberOfSteps());
        // payment times merged + dedup → still 4 times
        assertEquals(4, c.possibleCashFlowTimes().length);

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        final int[] nCF = new int[8];
        // OneStepCoinitial allows up to 8 cashflows per product, but our
        // composite uses max(1,1)=1, since both sub-products produce 1 cf
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(8, 1);
        final boolean done = c.nextTimeStep(state, nCF, cf);
        assertTrue(done);

        // first 4 products are forwards: 1 cashflow each
        for (int i = 0; i < 4; ++i) {
            assertEquals(1, nCF[i]);
            final double expected = (forwardRates[i] - strikes[i]) * accruals[i];
            assertEquals(expected, cf[i][0].amount, TOL);
        }
        // last 4 products are optionlets: only positive payoffs emit
        // i=4 (rate=0.04, strike=0.045) → 0; i=5 (0.045) → 0;
        // i=6 (0.05) → 0.0025; i=7 (0.055) → 0.005
        assertEquals(0, nCF[4]);
        assertEquals(0, nCF[5]);
        assertEquals(1, nCF[6]);
        assertEquals(0.0025, cf[6][0].amount, TOL);
        assertEquals(1, nCF[7]);
        assertEquals(0.005, cf[7][0].amount, TOL);
    }

    @Test
    public void testSingleProductCompositeAggregatesCashFlows() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] strikes = {0.04, 0.04, 0.04, 0.04};
        final double[] forwardRates = {0.04, 0.045, 0.05, 0.055};

        final OneStepForwards fwds = new OneStepForwards(rateTimes, accruals, paymentTimes, strikes);

        final SingleProductComposite c = new SingleProductComposite();
        c.add(fwds);
        c.finalizeComposite();

        assertEquals(1, c.numberOfProducts());
        assertEquals(1, c.maxNumberOfCashFlowsPerProductPerStep());

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        final int[] nCF = new int[1];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(1, 4);
        final boolean done = c.nextTimeStep(state, nCF, cf);
        assertTrue(done);

        // All 4 sub-product cash flows aggregated into product 0
        assertEquals(4, nCF[0]);
        for (int i = 0; i < 4; ++i) {
            final double expected = (forwardRates[i] - strikes[i]) * accruals[i];
            assertEquals(expected, cf[0][i].amount, TOL);
        }
    }

    @Test
    public void testSubtractFlipsSign() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] accruals = {0.5, 0.5, 0.5, 0.5};
        final double[] paymentTimes = {1.0, 1.5, 2.0, 2.5};
        final double[] strikes = {0.04, 0.04, 0.04, 0.04};
        final double[] forwardRates = {0.05, 0.05, 0.05, 0.05};

        final OneStepForwards fwds = new OneStepForwards(rateTimes, accruals, paymentTimes, strikes);

        final MultiProductComposite c = new MultiProductComposite();
        c.subtract(fwds);
        c.finalizeComposite();

        assertEquals(-1.0, c.multiplier(0), 0.0);

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(forwardRates);

        final int[] nCF = new int[4];
        final MarketModelMultiProduct.CashFlow[][] cf = allocate(4, 1);
        c.nextTimeStep(state, nCF, cf);

        // amounts should be negative since multiplier=-1
        for (int i = 0; i < 4; ++i) {
            assertTrue(cf[i][0].amount < 0);
        }
    }
}

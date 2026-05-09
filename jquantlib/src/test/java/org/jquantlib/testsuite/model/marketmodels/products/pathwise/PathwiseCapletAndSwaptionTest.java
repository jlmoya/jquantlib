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

import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct.CashFlow;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseCoterminalSwaptionsDeflated;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseCoterminalSwaptionsNumericalDeflated;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseMultiCaplet;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseMultiDeflatedCap;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseMultiDeflatedCaplet;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the more complex pathwise products (Phase 3k Track C C.2 + C.5).
 *
 * <p>For caplets we verify the ITM/OTM payoff branch and the cross-validation
 * of analytic Swaptions against the Numerical (FD-bumped) twin.
 */
public class PathwiseCapletAndSwaptionTest {

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
    public void testMultiCapletInTheMoney() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final double[] accruals = {0.5, 0.5, 0.5};
        final double[] paymentTimes = {0.5, 1.0, 1.5};
        final double[] strikes = {0.04, 0.04, 0.04};
        final MarketModelPathwiseMultiCaplet caplet = new MarketModelPathwiseMultiCaplet(
                rateTimes, accruals, paymentTimes, strikes);
        Assert.assertEquals(3, caplet.numberOfProducts());
        Assert.assertEquals(false, caplet.alreadyDeflated());

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05});

        final int[] nFlows = new int[3];
        final CashFlow[][] flows = makeCashFlowGrid(3, 1, 3);

        caplet.reset();
        // Step 0: caplet 0 is ITM (libor=0.05 > strike=0.04)
        caplet.nextTimeStep(cs, nFlows, flows);
        Assert.assertEquals(1, nFlows[0]);
        Assert.assertEquals(0, nFlows[1]);
        Assert.assertEquals(0, nFlows[2]);
        // amount[0] = (0.05 - 0.04) * 0.5 = 0.005
        Assert.assertEquals(0.005, flows[0][0].amount[0], TOL);
        // derivative = accrual = 0.5 in slot 1 (forward[0])
        Assert.assertEquals(0.5, flows[0][0].amount[1], TOL);
        Assert.assertEquals(0.0, flows[0][0].amount[2], TOL);
        Assert.assertEquals(0.0, flows[0][0].amount[3], TOL);
    }

    @Test
    public void testMultiCapletOutOfMoney() {
        final double[] rateTimes = {0.5, 1.0};
        final double[] accruals = {0.5};
        final double[] paymentTimes = {0.5};
        final double[] strikes = {0.06};
        final MarketModelPathwiseMultiCaplet caplet = new MarketModelPathwiseMultiCaplet(
                rateTimes, accruals, paymentTimes, strikes);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04});

        final int[] nFlows = new int[1];
        final CashFlow[][] flows = makeCashFlowGrid(1, 1, 1);

        caplet.reset();
        caplet.nextTimeStep(cs, nFlows, flows);
        // OTM: numberCashFlows = 0
        Assert.assertEquals(0, nFlows[0]);
        // amount[0] is set to negative value but discarded (no flow)
        Assert.assertEquals((0.04 - 0.06) * 0.5, flows[0][0].amount[0], TOL);
    }

    @Test
    public void testMultiDeflatedCapletITM() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final double[] accruals = {0.5, 0.5};
        final double[] paymentTimes = {0.5, 1.0};
        final MarketModelPathwiseMultiDeflatedCaplet defCap = new MarketModelPathwiseMultiDeflatedCaplet(
                rateTimes, accruals, paymentTimes, 0.04);

        Assert.assertEquals(true, defCap.alreadyDeflated());

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05});

        final int[] nFlows = new int[2];
        final CashFlow[][] flows = makeCashFlowGrid(2, 1, 2);

        defCap.reset();
        defCap.nextTimeStep(cs, nFlows, flows);
        Assert.assertEquals(1, nFlows[0]);
        Assert.assertEquals(0, nFlows[1]);
        // amount[0] = (libor - strike) * accrual * P(t_0,t_1)/P(t_0,t_0) = ...
        // P(0,t_1) = 1 / (1 + 0.05*0.5) ≈ 0.97561; payoff = 0.005*0.97561
        final double dr = cs.discountRatio(1, 0);
        final double expected = 0.005 * dr;
        Assert.assertEquals(expected, flows[0][0].amount[0], TOL);
    }

    @Test
    public void testCoterminalSwaptionAnalyticVsNumerical() {
        // Cross-validation of analytic adjoint vs central-difference twin.
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final double[] strikes = {0.04, 0.04, 0.04};

        final MarketModelPathwiseCoterminalSwaptionsDeflated analytic =
                new MarketModelPathwiseCoterminalSwaptionsDeflated(rateTimes, strikes);
        final MarketModelPathwiseCoterminalSwaptionsNumericalDeflated numerical =
                new MarketModelPathwiseCoterminalSwaptionsNumericalDeflated(rateTimes, strikes, 1e-5);

        Assert.assertEquals(3, analytic.numberOfProducts());
        Assert.assertEquals(3, numerical.numberOfProducts());

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05});

        final int[] nA = new int[3];
        final int[] nN = new int[3];
        final CashFlow[][] flowsA = makeCashFlowGrid(3, 1, 3);
        final CashFlow[][] flowsN = makeCashFlowGrid(3, 1, 3);

        analytic.reset();
        numerical.reset();
        analytic.nextTimeStep(cs, nA, flowsA);
        numerical.nextTimeStep(cs, nN, flowsN);

        Assert.assertEquals(nN[0], nA[0]);
        Assert.assertEquals(nN[1], nA[1]);
        Assert.assertEquals(nN[2], nA[2]);

        // amount[0] (price) must match exactly
        Assert.assertEquals(flowsN[0][0].amount[0], flowsA[0][0].amount[0], TOL);

        // amount[k+1] (derivative) must match within FD step quality
        if (nA[0] > 0) {
            for (int k = 0; k < 3; ++k) {
                Assert.assertEquals("deriv slot " + (k + 1),
                        flowsN[0][0].amount[k + 1],
                        flowsA[0][0].amount[k + 1],
                        1e-6);
            }
        }
    }

    @Test
    public void testMultiDeflatedCapAggregatesCaplets() {
        // Cap aggregates 2 underlying caplets; expect numberOfProducts = 1
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final double[] accruals = {0.5, 0.5, 0.5};
        final double[] paymentTimes = {0.5, 1.0, 1.5};
        final MarketModelPathwiseMultiDeflatedCap.StartAndEnd[] ranges = {
            new MarketModelPathwiseMultiDeflatedCap.StartAndEnd(0, 2)
        };
        final MarketModelPathwiseMultiDeflatedCap cap = new MarketModelPathwiseMultiDeflatedCap(
                rateTimes, accruals, paymentTimes, 0.04, ranges);

        Assert.assertEquals(1, cap.numberOfProducts());
        Assert.assertEquals(true, cap.alreadyDeflated());

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05});

        final int[] nF = new int[1];
        final CashFlow[][] flows = makeCashFlowGrid(1, 1, 3);

        cap.reset();
        // Step 0: caplet 0 ITM → cap fires once
        cap.nextTimeStep(cs, nF, flows);
        Assert.assertEquals(1, nF[0]);
    }
}

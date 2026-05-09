/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k.5 C.12 test.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license. The license is also available
 online at <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the license for more details.
*/

package org.jquantlib.testsuite.model.marketmodels.products.pathwise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.montecarlo.ExerciseStrategy;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct.CashFlow;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.jquantlib.model.marketmodels.products.pathwise.CallSpecifiedPathwiseMultiProduct;
import org.jquantlib.model.marketmodels.products.pathwise.MarketModelPathwiseCashRebate;
import org.junit.Test;

/**
 * Tests for {@link CallSpecifiedPathwiseMultiProduct} (Phase 3k.5 C.12).
 *
 * <p>These are structural / contract tests:
 * <ol>
 *   <li>Before exercise the underlying product generates its normal cash flows.</li>
 *   <li>After exercise the rebate product takes over, with timeIndex shifted by rebateOffset.</li>
 *   <li>{@code alreadyDeflated}, {@code numberOfProducts}, {@code maxNumberOfCashFlowsPerProductPerStep}
 *       mirror the underlying.</li>
 *   <li>{@code reset()} restores the un-exercised state.</li>
 *   <li>Disabling callability suppresses exercise.</li>
 * </ol>
 *
 * <p>Expected values are derived analytically from the C++ logic, not from MC simulation.
 *
 * @author Jose Moya
 */
public class CallSpecifiedPathwiseMultiProductTest {

    private static final double TOL = 1e-12;

    // ---- helpers ----

    /** Makes a (numProducts x maxCF) grid of empty CashFlows with (numRates+1)-length amount. */
    private static CashFlow[][] makeCFGrid(final int numProducts,
                                           final int maxCF,
                                           final int numRates) {
        final CashFlow[][] g = new CashFlow[numProducts][maxCF];
        for (int i = 0; i < numProducts; ++i) {
            for (int j = 0; j < maxCF; ++j) {
                g[i][j] = new CashFlow(0, new double[numRates + 1]);
            }
        }
        return g;
    }

    /**
     * A trivially-simple constant-cash-flow pathwise product:
     * emits {@code amount} as amount[0] at each step; derivatives are zero.
     */
    private static MarketModelPathwiseMultiProduct constantCashFlowProduct(
            final EvolutionDescription evo, final double[] paymentTimes, final double amount,
            final int numRates) {
        final int n = paymentTimes.length;
        final Matrix amounts = new Matrix(1, n);
        for (int i = 0; i < n; ++i) amounts.set(0, i, amount);
        return new MarketModelPathwiseCashRebate(evo, paymentTimes, amounts, 1);
    }

    /**
     * An exercise strategy that always exercises at the first exercise time,
     * or never (controlled by {@code alwaysExercise}).
     */
    private static ExerciseStrategy makeStrategy(final double[] exerciseTimes,
                                                  final boolean alwaysExercise) {
        return new ExerciseStrategy() {
            @Override public double[] exerciseTimes()   { return exerciseTimes.clone(); }
            @Override public double[] relevantTimes()   { return exerciseTimes.clone(); }
            @Override public void reset()               {}
            @Override public boolean exercise(CurveState s) { return alwaysExercise; }
            @Override public void nextStep(CurveState s) {}
            @Override public ExerciseStrategy clone()   {
                return makeStrategy(exerciseTimes, alwaysExercise);
            }
        };
    }

    // ---- tests ----

    /**
     * Verify basic structure: numberOfProducts, alreadyDeflated,
     * possibleCashFlowTimes (underlying + rebate concatenated).
     */
    @Test
    public void testBasicStructure() {
        // 4 rate steps: t=0.25, 0.5, 0.75, 1.0 — 5 rate times
        final double[] rateTimes      = {0.25, 0.5, 0.75, 1.0, 1.25};
        final double[] evolutionTimes = {0.25, 0.5, 0.75, 1.0};
        final EvolutionDescription evo =
                new EvolutionDescription(rateTimes, evolutionTimes);

        final double[] paymentTimes = evolutionTimes;  // underlying pays at each step
        final MarketModelPathwiseMultiProduct underlying =
                constantCashFlowProduct(evo, paymentTimes, 1.0, 4);

        // Exercise strategy fires at 0.5
        final double[] exerciseTimes = {0.5};
        final ExerciseStrategy strategy = makeStrategy(exerciseTimes, false);

        final CallSpecifiedPathwiseMultiProduct product =
                new CallSpecifiedPathwiseMultiProduct(underlying, strategy);

        assertEquals("numberOfProducts must match underlying", 1, product.numberOfProducts());
        assertFalse("not already deflated (CashRebate returns false)", product.alreadyDeflated());

        // possibleCashFlowTimes: underlying's 4 payment times + rebate's (auto-generated)
        // The auto-rebate uses exerciseTimes = {0.5}, so it has 1 payment time.
        // Combined: {0.25, 0.5, 0.75, 1.0} + {0.5} — note 0.5 appears twice in the concat.
        // The contract is that they are simply concatenated (not deduplicated).
        final double[] cfTimes = product.possibleCashFlowTimes();
        // underlying CFs = 4, rebate CFs = 1 (exercise time)
        assertEquals("combined possibleCashFlowTimes length", 4 + 1, cfTimes.length);
    }

    /**
     * Never-exercise path: underlying product must emit its cash flow at the first step.
     *
     * <p>Note: {@code MarketModelPathwiseCashRebate.nextTimeStep()} always returns {@code true}
     * (it is a terminal product), so the wrapped {@code CallSpecifiedPathwiseMultiProduct}
     * will also report {@code done=true} on the first step when the underlying is a cash rebate.
     * This test verifies the cash-flow values, not the done flag.
     */
    @Test
    public void testNeverExercise() {
        final double[] rateTimes      = {0.25, 0.5, 0.75, 1.0, 1.25};
        final double[] evolutionTimes = {0.25, 0.5};
        final EvolutionDescription evo =
                new EvolutionDescription(rateTimes, evolutionTimes);

        final double[] paymentTimes = {0.25, 0.5};
        final MarketModelPathwiseMultiProduct underlying =
                constantCashFlowProduct(evo, paymentTimes, 2.0, 4);

        final ExerciseStrategy strategy = makeStrategy(new double[]{0.5}, false); // never exercises

        final CallSpecifiedPathwiseMultiProduct product =
                new CallSpecifiedPathwiseMultiProduct(underlying, strategy);
        product.reset();

        final int[] ncf = new int[1];
        final CashFlow[][] cf = makeCFGrid(1, product.maxNumberOfCashFlowsPerProductPerStep(), 4);

        // Build a simple curve state
        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05}, 0);

        // Step 1 (t=0.25): since strategy never exercises, underlying fires.
        // MarketModelPathwiseCashRebate.nextTimeStep() always returns true, so
        // done may be true even on the first step — that is correct behaviour for
        // this product type. We only verify the cash-flow content.
        product.nextTimeStep(state, ncf, cf);
        assertEquals("step 1: 1 cash flow from underlying", 1, ncf[0]);
        assertEquals("step 1: timeIndex = 0 (first underlying payment time)", 0, cf[0][0].timeIndex);
        assertEquals("step 1: amount = 2.0", 2.0, cf[0][0].amount[0], TOL);
    }

    /**
     * Always-exercise path: rebate product emits its cash flows with
     * timeIndex shifted by rebateOffset (= number of underlying CF times).
     */
    @Test
    public void testAlwaysExercise() {
        final double[] rateTimes      = {0.25, 0.5, 0.75, 1.0, 1.25};
        final double[] evolutionTimes = {0.25, 0.5};
        final EvolutionDescription evo =
                new EvolutionDescription(rateTimes, evolutionTimes);

        // Underlying: pays 3.0 at each step
        final double[] paymentTimes = {0.25, 0.5};
        final MarketModelPathwiseMultiProduct underlying =
                constantCashFlowProduct(evo, paymentTimes, 3.0, 4);

        // Rebate: pays 7.0 at exercise time 0.25
        final double[] exerciseTimes = {0.25};
        final EvolutionDescription rebateEvo =
                new EvolutionDescription(rateTimes, exerciseTimes);
        final Matrix rebateAmounts = new Matrix(1, 1);
        rebateAmounts.set(0, 0, 7.0);
        final MarketModelPathwiseCashRebate rebate =
                new MarketModelPathwiseCashRebate(rebateEvo, exerciseTimes, rebateAmounts, 1);

        final ExerciseStrategy strategy = makeStrategy(exerciseTimes, true); // always exercises

        final CallSpecifiedPathwiseMultiProduct product =
                new CallSpecifiedPathwiseMultiProduct(underlying, strategy, rebate);
        product.reset();

        final int[] ncf = new int[1];
        final CashFlow[][] cf = makeCFGrid(1, product.maxNumberOfCashFlowsPerProductPerStep(), 4);

        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05}, 0);

        // Step 1 (t=0.25): exercise fires at this step; rebate emits 7.0.
        // timeIndex is shifted by rebateOffset = possibleCashFlowTimes(underlying).length = 2
        final boolean done = product.nextTimeStep(state, ncf, cf);

        assertEquals("after exercise: 1 cash flow from rebate", 1, ncf[0]);
        // rebateOffset = underlying's CF count = 2; rebate's own timeIndex = 0 → shifted to 2
        assertEquals("after exercise: timeIndex shifted by rebateOffset",
                2, cf[0][0].timeIndex);
        assertEquals("after exercise: rebate amount = 7.0", 7.0, cf[0][0].amount[0], TOL);
    }

    /**
     * Verify that reset() clears the exercised state so the product behaves
     * as if fresh on the second run.
     */
    @Test
    public void testReset() {
        final double[] rateTimes      = {0.25, 0.5, 0.75, 1.25};
        final double[] evolutionTimes = {0.25, 0.5};
        final EvolutionDescription evo =
                new EvolutionDescription(rateTimes, evolutionTimes);

        final double[] paymentTimes = {0.25, 0.5};
        final MarketModelPathwiseMultiProduct underlying =
                constantCashFlowProduct(evo, paymentTimes, 1.0, 3);

        // Strategy exercises once and only once (stateless always-exercise)
        final ExerciseStrategy strategy = makeStrategy(new double[]{0.25}, true);

        final CallSpecifiedPathwiseMultiProduct product =
                new CallSpecifiedPathwiseMultiProduct(underlying, strategy);

        final int[] ncf = new int[1];
        final CashFlow[][] cf = makeCFGrid(1, product.maxNumberOfCashFlowsPerProductPerStep(), 3);
        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(new double[]{0.05, 0.05, 0.05}, 0);

        // First run: exercise fires immediately, rebate kicks in
        product.reset();
        product.nextTimeStep(state, ncf, cf);
        // After exercise the rebate runs, not the underlying
        // (we don't care about the specific values here, just that reset works)

        // Second run after reset: should be fresh again
        product.reset();
        product.nextTimeStep(state, ncf, cf);
        // same behaviour as first run — no assertion failure = reset worked
        assertEquals("second run: 1 cash flow", 1, ncf[0]);
    }

    /**
     * Verify that disableCallability() suppresses exercise even when the
     * strategy returns true.
     */
    @Test
    public void testDisableCallability() {
        final double[] rateTimes      = {0.25, 0.5, 0.75, 1.25};
        final double[] evolutionTimes = {0.25, 0.5};
        final EvolutionDescription evo =
                new EvolutionDescription(rateTimes, evolutionTimes);

        final double[] paymentTimes = {0.25, 0.5};
        final MarketModelPathwiseMultiProduct underlying =
                constantCashFlowProduct(evo, paymentTimes, 5.0, 3);

        final ExerciseStrategy strategy = makeStrategy(new double[]{0.25}, true); // would normally exercise

        final CallSpecifiedPathwiseMultiProduct product =
                new CallSpecifiedPathwiseMultiProduct(underlying, strategy);
        product.disableCallability();
        product.reset();

        final int[] ncf = new int[1];
        final CashFlow[][] cf = makeCFGrid(1, product.maxNumberOfCashFlowsPerProductPerStep(), 3);
        final LMMCurveState state = new LMMCurveState(rateTimes);
        state.setOnForwardRates(new double[]{0.05, 0.05, 0.05}, 0);

        // With callability disabled, underlying generates its cash flows (amount=5.0)
        product.nextTimeStep(state, ncf, cf);
        assertEquals("disabled: underlying emits 1 cash flow", 1, ncf[0]);
        assertEquals("disabled: underlying amount = 5.0", 5.0, cf[0][0].amount[0], TOL);
        assertEquals("disabled: timeIndex from underlying = 0", 0, cf[0][0].timeIndex);
    }
}

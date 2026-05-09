/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.13.

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 */

package org.jquantlib.testsuite.model.marketmodels.callability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.NodeData;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelEvolver;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.callability.BermudanSwaptionExerciseValue;
import org.jquantlib.model.marketmodels.callability.CollectNodeData;
import org.jquantlib.model.marketmodels.callability.NothingExerciseValue;
import org.jquantlib.model.marketmodels.callability.SwapBasisSystem;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3k B.13 — CollectNodeData.
 *
 * <p>Functional / structural test using a deterministic stub evolver that
 * advances through evolution steps holding a constant curve state. The test
 * verifies that {@code collect()} produces grids of the expected shape, that
 * exercise values for an ITM Bermudan swaption are positive at every
 * exercise step, and that the static helper makes correct use of the rebate
 * provider's {@link MarketModelMultiProduct.CashFlow}.
 */
public class CollectNodeDataTest {

    @Test
    public void collectShapeAndExerciseValues() {
        // 4 forward rates, all evolution times are exercise opportunities.
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] evolutionTimes = Arrays.copyOf(rateTimes, rateTimes.length - 1);
        final EvolutionDescription evolution = new EvolutionDescription(rateTimes, evolutionTimes);
        final int[] numeraires = EvolutionDescription.terminalMeasure(evolution);

        // Single product: trivial product with no cash flows (Nothing-style),
        // implemented inline below.
        final MarketModelMultiProduct product = new SingleNothingProduct(rateTimes, evolutionTimes);

        // Rebate: Bermudan ITM swaption  (positive value at every exercise)
        final int n = rateTimes.length - 1;
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[n];
        for (int i = 0; i < n; i++) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.04);
        }
        final BermudanSwaptionExerciseValue rebate =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);

        // Control: zero
        final NothingExerciseValue control = new NothingExerciseValue(rateTimes);

        // Basis: 3 functions per exercise (last drops to 2)
        final SwapBasisSystem basis = new SwapBasisSystem(rateTimes, evolutionTimes);

        // Stub evolver: deterministic, constant ITM curve state through all steps
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05}); // ITM at strike 0.04
        final MarketModelEvolver evolver = new ConstantStateEvolver(cs, numeraires, evolutionTimes.length);

        final int paths = 3;
        final List<List<NodeData>> data =
                CollectNodeData.collect(evolver, product, basis, rebate, control, paths);

        // Shape: exercises+1 = n+1 = 5 rows, each with `paths` entries
        assertEquals(n + 1, data.size());
        for (final List<NodeData> row : data) {
            assertEquals(paths, row.size());
        }

        // Exercise values at every exercise opportunity should be > 0 (ITM)
        // and isValid should be true.
        for (int e = 1; e <= n; e++) {
            for (int p = 0; p < paths; p++) {
                final NodeData nd = data.get(e).get(p);
                assertTrue("exercise " + e + " path " + p + " should be valid",
                        nd.isValid);
                assertTrue("exercise " + e + " path " + p
                        + " value=" + nd.exerciseValue + " should be > 0",
                        nd.exerciseValue > 0.0);
                // Basis values were also written (length 3 or 2)
                assertTrue("basis values populated",
                        nd.values.length >= 2 && nd.values.length <= 3);
            }
        }
    }

    // -- Stub product: single product, no cash flows ever --
    private static final class SingleNothingProduct extends MarketModelMultiProduct {
        private final double[] rateTimes_;
        private final double[] evolutionTimes_;
        private final EvolutionDescription evolution_;
        private int currentStep_;

        SingleNothingProduct(final double[] rateTimes, final double[] evolutionTimes) {
            this.rateTimes_ = rateTimes.clone();
            this.evolutionTimes_ = evolutionTimes.clone();
            this.evolution_ = new EvolutionDescription(rateTimes, evolutionTimes);
            this.currentStep_ = 0;
        }

        @Override public int[] suggestedNumeraires() {
            final int[] n = new int[evolutionTimes_.length];
            Arrays.fill(n, rateTimes_.length - 1);
            return n;
        }

        @Override public EvolutionDescription evolution() { return evolution_; }

        @Override public double[] possibleCashFlowTimes() { return rateTimes_; }

        @Override public int numberOfProducts() { return 1; }

        @Override public int maxNumberOfCashFlowsPerProductPerStep() { return 1; }

        @Override public void reset() { currentStep_ = 0; }

        @Override public boolean nextTimeStep(final CurveState s, final int[] n,
                                              final CashFlow[][] cf) {
            n[0] = 0;
            ++currentStep_;
            return currentStep_ >= evolutionTimes_.length;
        }

        @Override public MarketModelMultiProduct clone() {
            return new SingleNothingProduct(rateTimes_, evolutionTimes_);
        }
    }

    // -- Stub evolver: constant state, deterministic stepping --
    private static final class ConstantStateEvolver extends MarketModelEvolver {
        private final CurveState cs_;
        private final int[] numeraires_;
        private final int numberOfSteps_;
        private int currentStep_;

        ConstantStateEvolver(final CurveState cs, final int[] numeraires, final int numberOfSteps) {
            this.cs_ = cs;
            this.numeraires_ = numeraires.clone();
            this.numberOfSteps_ = numberOfSteps;
            this.currentStep_ = 0;
        }

        @Override public int[] numeraires() { return numeraires_; }
        @Override public double startNewPath() { currentStep_ = 0; return 1.0; }
        @Override public double advanceStep() { ++currentStep_; return 1.0; }
        @Override public int currentStep() { return currentStep_; }
        @Override public CurveState currentState() { return cs_; }
        @Override public void setInitialState(final CurveState s) { /* no-op for stub */ }
    }
}

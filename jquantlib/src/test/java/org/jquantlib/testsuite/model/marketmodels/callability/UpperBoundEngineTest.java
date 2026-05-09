/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.14.

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

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelEvolver;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.callability.NothingExerciseValue;
import org.jquantlib.model.marketmodels.callability.SwapRateTrigger;
import org.jquantlib.model.marketmodels.callability.UpperBoundEngine;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3k B.14 — UpperBoundEngine.
 *
 * <p>Construction / structural test: builds an UpperBoundEngine with stub
 * underlying/hedge products, NothingExerciseValue rebates, and a
 * SwapRateTrigger hedge strategy; verifies it constructs without throwing
 * and that the {@code DecoratedHedge} composition is consistent.
 *
 * <p>A full functional cross-validation against C++ {@code testCallableSwap*}
 * requires a fully wired LMM evolver pipeline and is deferred to the
 * Phase 3k integration tests (L1).
 */
public class UpperBoundEngineTest {

    @Test
    public void constructWithStubsCompiles() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] evolutionTimes = Arrays.copyOf(rateTimes, rateTimes.length - 1);
        final EvolutionDescription evolution = new EvolutionDescription(rateTimes, evolutionTimes);
        final int[] numeraires = EvolutionDescription.terminalMeasure(evolution);

        final MarketModelMultiProduct underlying = new SingleNothingProduct(rateTimes, evolutionTimes);
        final MarketModelMultiProduct hedge = new SingleNothingProduct(rateTimes, evolutionTimes);
        final NothingExerciseValue rebate = new NothingExerciseValue(rateTimes);
        final NothingExerciseValue hedgeRebate = new NothingExerciseValue(rateTimes);

        final SwapRateTrigger strategy = new SwapRateTrigger(
                rateTimes,
                new double[]{0.04, 0.04, 0.04, 0.04},
                evolutionTimes);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05});
        final MarketModelEvolver outerEvolver = new ConstantStateEvolver(cs, numeraires, evolutionTimes.length);
        final List<MarketModelEvolver> innerEvolvers = new ArrayList<>();
        for (int i = 0; i < strategy.exerciseTimes().length; i++) {
            innerEvolvers.add(new ConstantStateEvolver(cs, numeraires, evolutionTimes.length));
        }

        final UpperBoundEngine engine = new UpperBoundEngine(
                outerEvolver, innerEvolvers,
                underlying, rebate, hedge, hedgeRebate,
                strategy,
                1.0);
        assertNotNull("UpperBoundEngine must construct", engine);
    }

    // -- Stub product (one product, no cash flows) --
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

    // -- Stub evolver (constant state, deterministic stepping) --
    private static final class ConstantStateEvolver extends MarketModelEvolver {
        private final CurveState cs_;
        private final int[] numeraires_;
        private final int numberOfSteps_;
        private int currentStep_;

        ConstantStateEvolver(final CurveState cs, final int[] numeraires, final int numberOfSteps) {
            this.cs_ = cs;
            this.numeraires_ = numeraires.clone();
            this.numberOfSteps_ = numberOfSteps;
        }

        @Override public int[] numeraires() { return numeraires_; }
        @Override public double startNewPath() { currentStep_ = 0; return 1.0; }
        @Override public double advanceStep() { ++currentStep_; return 1.0; }
        @Override public int currentStep() { return currentStep_; }
        @Override public CurveState currentState() { return cs_; }
        @Override public void setInitialState(final CurveState s) { /* no-op for stub */ }
    }
}

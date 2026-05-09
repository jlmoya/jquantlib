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
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2006 Mark Joshi
*/

package org.jquantlib.model.marketmodels.callability;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.methods.montecarlo.NodeData;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelDiscounter;
import org.jquantlib.model.marketmodels.MarketModelEvolver;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.Utilities;

/**
 * Offline pass that walks Monte-Carlo paths to populate per-exercise
 * {@link NodeData} grids used by Longstaff-Schwartz regression.
 *
 * <p>Java port of {@code collectNodeData}
 * (ql/models/marketmodels/callability/collectnodedata.{hpp,cpp}, 245 LOC,
 * v1.42.1). The C++ free function becomes a Java static method on this
 * utility class.
 *
 * @see "ql/models/marketmodels/callability/collectnodedata.hpp" v1.42.1
 *
 * @author Jose Moya
 */
public final class CollectNodeData {

    private CollectNodeData() {
        // utility class - no instances
    }

    /**
     * Mirrors C++ {@code void collectNodeData(MarketModelEvolver&,
     *   MarketModelMultiProduct&, MarketModelNodeDataProvider&,
     *   MarketModelExerciseValue& rebate, MarketModelExerciseValue& control,
     *   Size numberOfPaths,
     *   std::vector<std::vector<NodeData>>& collectedData)}.
     *
     * <p>Returns the populated node data grid as a {@code List<List<NodeData>>}
     * with shape {@code [exercises+1][numberOfPaths]}. The leading row holds
     * pre-exercise cumulated cash flows; subsequent rows hold per-exercise
     * data (exerciseValue, controlValue, cumulatedCashFlows from that node
     * onward, and the basis-function values from {@code dataProvider}).
     */
    public static List<List<NodeData>> collect(
            final MarketModelEvolver evolver,
            final MarketModelMultiProduct product,
            final MarketModelNodeDataProvider dataProvider,
            final MarketModelExerciseValue rebate,
            final MarketModelExerciseValue control,
            final int numberOfPaths) {

        QL.require(product.numberOfProducts() == 1,
                "a single product is required");

        // workspace
        final int[] numberCashFlowsThisStep = new int[1];
        final MarketModelMultiProduct.CashFlow[][] cashFlowsGenerated =
                new MarketModelMultiProduct.CashFlow[1][];
        cashFlowsGenerated[0] =
                new MarketModelMultiProduct.CashFlow[product.maxNumberOfCashFlowsPerProductPerStep()];
        for (int i = 0; i < cashFlowsGenerated[0].length; i++) {
            cashFlowsGenerated[0][i] = new MarketModelMultiProduct.CashFlow();
        }

        final double[] rateTimes = product.evolution().rateTimes();
        final double[] cashFlowTimes = product.possibleCashFlowTimes();
        final double[] rebateTimes = rebate.possibleCashFlowTimes();
        final double[] controlTimes = control.possibleCashFlowTimes();

        final List<MarketModelDiscounter> productDiscounters = new ArrayList<>(cashFlowTimes.length);
        for (final double t : cashFlowTimes) {
            productDiscounters.add(new MarketModelDiscounter(t, rateTimes));
        }
        final List<MarketModelDiscounter> rebateDiscounters = new ArrayList<>(rebateTimes.length);
        for (final double t : rebateTimes) {
            rebateDiscounters.add(new MarketModelDiscounter(t, rateTimes));
        }
        final List<MarketModelDiscounter> controlDiscounters = new ArrayList<>(controlTimes.length);
        for (final double t : controlTimes) {
            controlDiscounters.add(new MarketModelDiscounter(t, rateTimes));
        }

        final EvolutionDescription evolution = product.evolution();
        final int[] numeraires = evolver.numeraires();
        final double[] evolutionTimes = evolution.evolutionTimes();

        final boolean[] isProductTime = Utilities.isInSubset(evolutionTimes,
                product.evolution().evolutionTimes());
        final boolean[] isRebateTime = Utilities.isInSubset(evolutionTimes,
                rebate.evolution().evolutionTimes());
        final boolean[] isControlTime = Utilities.isInSubset(evolutionTimes,
                control.evolution().evolutionTimes());
        final boolean[] isBasisTime = Utilities.isInSubset(evolutionTimes,
                dataProvider.evolution().evolutionTimes());

        final boolean[] isExerciseTime = new boolean[evolutionTimes.length];
        final boolean[] v = rebate.isExerciseTime();
        int exercises = 0;
        int idx = 0;
        for (int i = 0; i < evolutionTimes.length; ++i) {
            if (isRebateTime[i]) {
                if (v[idx++]) {
                    isExerciseTime[i] = true;
                    ++exercises;
                }
            }
        }

        final List<List<NodeData>> collectedData = new ArrayList<>(exercises + 1);
        for (int i = 0; i < exercises + 1; i++) {
            final List<NodeData> row = new ArrayList<>(numberOfPaths);
            for (int j = 0; j < numberOfPaths; j++) {
                row.add(new NodeData());
            }
            collectedData.add(row);
        }

        for (int i = 0; i < numberOfPaths; ++i) {
            evolver.startNewPath();
            product.reset();
            rebate.reset();
            control.reset();
            dataProvider.reset();
            double principalInNumerairePortfolio = 1.0;

            boolean done = false;
            int nextExercise = 0;
            collectedData.get(0).get(i).cumulatedCashFlows = 0.0;

            do {
                final int currentStep = evolver.currentStep();
                evolver.advanceStep();
                final CurveState currentState = evolver.currentState();
                final int numeraire = numeraires[currentStep];

                if (isRebateTime[currentStep]) rebate.nextStep(currentState);
                if (isControlTime[currentStep]) control.nextStep(currentState);
                if (isBasisTime[currentStep]) dataProvider.nextStep(currentState);

                if (isExerciseTime[currentStep]) {
                    final NodeData data = collectedData.get(nextExercise + 1).get(i);

                    final MarketModelMultiProduct.CashFlow exerciseValue =
                            rebate.value(currentState);
                    data.exerciseValue = exerciseValue.amount
                            * rebateDiscounters.get(exerciseValue.timeIndex)
                                    .numeraireBonds(currentState, numeraire)
                            / principalInNumerairePortfolio;

                    // Resize values[] to match numberOfData()[nextExercise]
                    final int needed = dataProvider.numberOfData()[nextExercise];
                    if (data.values.length != needed) {
                        data.values = new double[needed];
                    }
                    dataProvider.values(currentState, data.values);

                    final MarketModelMultiProduct.CashFlow controlValue =
                            control.value(currentState);
                    data.controlValue = controlValue.amount
                            * controlDiscounters.get(controlValue.timeIndex)
                                    .numeraireBonds(currentState, numeraire)
                            / principalInNumerairePortfolio;

                    data.cumulatedCashFlows = 0.0;
                    data.isValid = true;

                    ++nextExercise;
                }

                if (isProductTime[currentStep]) {
                    done = product.nextTimeStep(currentState,
                                                numberCashFlowsThisStep,
                                                cashFlowsGenerated);
                    for (int j = 0; j < numberCashFlowsThisStep[0]; ++j) {
                        final MarketModelMultiProduct.CashFlow cf =
                                cashFlowsGenerated[0][j];
                        collectedData.get(nextExercise).get(i).cumulatedCashFlows +=
                                cf.amount
                                * productDiscounters.get(cf.timeIndex)
                                        .numeraireBonds(currentState, numeraire)
                                / principalInNumerairePortfolio;
                    }
                }

                if (!done) {
                    final int nextNumeraire = numeraires[currentStep + 1];
                    principalInNumerairePortfolio *=
                            currentState.discountRatio(numeraire, nextNumeraire);
                }
            } while (!done);

            // Fill un-collected exercises with null/invalid sentinels
            for (int j = nextExercise; j < exercises; ++j) {
                final NodeData data = collectedData.get(j + 1).get(i);
                data.exerciseValue = 0.0;
                data.controlValue = 0.0;
                data.cumulatedCashFlows = 0.0;
                data.isValid = false;
            }
        }
        return collectedData;
    }
}

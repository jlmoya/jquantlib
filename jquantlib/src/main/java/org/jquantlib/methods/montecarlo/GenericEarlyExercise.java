/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k L0.3.

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
 Copyright (C) 2006 StatPro Italia srl
*/

package org.jquantlib.methods.montecarlo;

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.optimization.CostFunction;
import org.jquantlib.math.optimization.EndCriteria;
import org.jquantlib.math.optimization.NoConstraint;
import org.jquantlib.math.optimization.OptimizationMethod;
import org.jquantlib.math.optimization.Problem;

/**
 * Generic parametric early-exercise optimisation utility.
 * <p>
 * Ports the C++ free function {@code genericEarlyExerciseOptimization}
 * (ql/methods/montecarlo/parametricexercise.hpp/.cpp v1.42.1) as a static
 * method per design decision P3K-8.
 * <p>
 * The optimisation sweeps backwards through the exercise dates. At each date
 * it fits the exercise boundary by minimising the expected payoff loss over
 * the Monte Carlo paths collected by
 * {@code CollectNodeData.collect()} (Track B.13). After the fit the
 * continuation values in {@code simulationData[i-1]} are updated with the
 * optimal exercise/hold decision.
 * <p>
 * <strong>simulationData layout</strong><br>
 * {@code simulationData} has {@code n+1} elements for {@code n} exercise dates:
 * <ul>
 *   <li>{@code simulationData.get(0)} — cash-flows up to the first exercise (the
 *       {@code cumulatedCashFlows} field of each {@link NodeData} is the only
 *       field used here; all other fields are unused/unusable).</li>
 *   <li>{@code simulationData.get(i+1)} — {@code i}-th exercise data (all
 *       {@link NodeData} fields populated).</li>
 * </ul>
 *
 * @see ParametricExercise
 * @see NodeData
 * @see "ql/methods/montecarlo/parametricexercise.cpp" v1.42.1
 *
 * @author Jose Moya
 */
public final class GenericEarlyExercise {

    // non-instantiable utility class
    private GenericEarlyExercise() {}

    /**
     * Optimises the parametric exercise boundary and returns the biased
     * estimate of the instrument value obtained during the optimisation pass.
     * <p>
     * Mirrors C++ {@code Real genericEarlyExerciseOptimization(...)}.
     *
     * @param simulationData data collected per exercise date (size = n+1);
     *                       element 0 holds initial cash-flow data; elements
     *                       1..n hold exercise-date data. The
     *                       {@code cumulatedCashFlows} field of
     *                       {@code simulationData.get(i-1)} is updated
     *                       in-place as the backward sweep proceeds.
     * @param exercise       the parametric exercise strategy whose parameters
     *                       are being optimised
     * @param parameters     output: {@code parameters.get(i)} is set to the
     *                       optimal parameter vector for exercise {@code i};
     *                       resized to {@code n} elements
     * @param endCriteria    stopping criterion for the optimiser
     * @param method         optimisation algorithm (e.g. Simplex,
     *                       ConjugateGradient)
     * @return biased estimate of the instrument NPV obtained while optimising
     */
    public static double optimize(
            final List<List<NodeData>> simulationData,
            final ParametricExercise exercise,
            final List<double[]> parameters,
            final EndCriteria endCriteria,
            final OptimizationMethod method) {

        final int steps = simulationData.size();

        // resize parameters list to n = steps-1 entries
        while (parameters.size() < steps - 1) {
            parameters.add(new double[0]);
        }
        while (parameters.size() > steps - 1) {
            parameters.remove(parameters.size() - 1);
        }

        // backward sweep over exercise dates (i = steps-1 down to 1)
        for (int i = steps - 1; i != 0; --i) {
            final List<NodeData> exerciseData = simulationData.get(i);
            final int exerciseIndex = i - 1; // 0-based exercise index

            final int nParams = exercise.numberOfParameters()[exerciseIndex];
            final double[] paramArr = new double[nParams];
            parameters.set(exerciseIndex, paramArr);

            // obtain initial guess from the exercise strategy
            exercise.guess(exerciseIndex, paramArr);

            // build cost function wrapping the exercise strategy and data
            final CostFunction f = new ValueEstimate(exerciseData, exercise, exerciseIndex);

            // run optimisation
            final Array guess = new Array(paramArr.clone());
            final Problem p = new Problem(f, new NoConstraint(), guess);
            method.minimize(p, endCriteria);

            // extract result back into paramArr
            final Array result = p.currentValue();
            for (int k = 0; k < nParams; ++k) {
                paramArr[k] = result.get(k);
            }

            // update continuation values in the previous layer
            final List<NodeData> previousData = simulationData.get(i - 1);
            for (int j = 0; j < previousData.size(); ++j) {
                final NodeData exNode = exerciseData.get(j);
                if (exNode.isValid) {
                    if (exercise.exercise(exerciseIndex, paramArr, exNode.values)) {
                        previousData.get(j).cumulatedCashFlows += exNode.exerciseValue;
                    } else {
                        previousData.get(j).cumulatedCashFlows += exNode.cumulatedCashFlows;
                    }
                }
            }
        }

        // compute biased NPV estimate from initial layer
        final List<NodeData> initialData = simulationData.get(0);
        double sum = 0.0;
        for (final NodeData nd : initialData) {
            sum += nd.cumulatedCashFlows;
        }
        return sum / initialData.size();
    }


    // -------------------------------------------------------------------------
    // Private inner CostFunction (mirrors C++ anonymous ValueEstimate class)
    // -------------------------------------------------------------------------

    /**
     * Cost function that evaluates the negative expected payoff for a given
     * parameter vector, over all valid paths at one exercise date.
     * Mirrors C++ anonymous {@code class ValueEstimate : public CostFunction}.
     */
    private static final class ValueEstimate extends CostFunction {

        private final List<NodeData>        simulationData_;
        private final ParametricExercise    exercise_;
        private final int                   exerciseIndex_;
        /** Mutable working buffer for parameter conversion. */
        private final double[]              parameters_;

        ValueEstimate(final List<NodeData> simulationData,
                      final ParametricExercise exercise,
                      final int exerciseIndex) {
            this.simulationData_ = simulationData;
            this.exercise_       = exercise;
            this.exerciseIndex_  = exerciseIndex;
            this.parameters_     = new double[exercise.numberOfParameters()[exerciseIndex]];

            // verify at least one valid path exists
            boolean hasValid = false;
            for (final NodeData nd : simulationData) {
                if (nd.isValid) {
                    hasValid = true;
                    break;
                }
            }
            QL.require(hasValid, "no valid paths");
        }

        /**
         * Returns the negative expected payoff (negated because the optimiser
         * minimises; we want to maximise the payoff).
         * Mirrors C++ {@code Real ValueEstimate::value(const Array&)}.
         */
        @Override
        public double value(final Array parameters) {
            // copy Array → double[]
            for (int k = 0; k < parameters_.length; ++k) {
                parameters_[k] = parameters.get(k);
            }
            double sum = 0.0;
            int n = 0;
            for (final NodeData nd : simulationData_) {
                if (nd.isValid) {
                    ++n;
                    if (exercise_.exercise(exerciseIndex_, parameters_, nd.values)) {
                        sum += nd.exerciseValue;
                    } else {
                        sum += nd.cumulatedCashFlows;
                    }
                }
            }
            return -sum / n; // negative: minimiser maximises payoff
        }

        /**
         * Not implemented — mirrors C++ {@code QL_FAIL("values method not implemented")}.
         */
        @Override
        public Array values(final Array x) {
            throw new UnsupportedOperationException(
                    "GenericEarlyExercise.ValueEstimate.values: not implemented");
        }
    }
}

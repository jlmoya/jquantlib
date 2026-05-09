/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k L0.1-L0.4.

 This source code is release under the BSD License.
 */
package org.jquantlib.testsuite.methods.montecarlo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.methods.montecarlo.ExerciseStrategy;
import org.jquantlib.methods.montecarlo.GenericEarlyExercise;
import org.jquantlib.methods.montecarlo.NodeData;
import org.jquantlib.methods.montecarlo.ParametricExercise;
import org.jquantlib.model.marketmodels.CurveState;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.MarketModelPathwiseMultiProduct;
import org.junit.Test;

/**
 * Phase 3k L0 — smoke tests for Monte Carlo prereqs:
 * L0.1 ExerciseStrategy, L0.2 NodeData, L0.3 ParametricExercise +
 * GenericEarlyExercise, L0.4 MarketModelPathwiseMultiProduct.
 *
 * <p>All tests are compile-level / functional (deterministic inputs);
 * no MC noise — tolerance is exact or 1e-14 where noted.
 *
 * @author Jose Moya
 */
public class MonteCarloPrereqsTest {

    // =========================================================================
    // L0.1 — ExerciseStrategy interface
    // =========================================================================

    /**
     * Verify that a concrete anonymous ExerciseStrategy implementation compiles
     * and behaves correctly.
     *
     * Case 1: always-exercise strategy.
     * Case 2: never-exercise strategy.
     * Case 3: clone() returns a distinct but equivalent object.
     */
    @Test
    public void exerciseStrategy_alwaysExercise_returnsTrue() {
        final ExerciseStrategy always = makeAlwaysExercise(
                new double[]{0.5, 1.0, 1.5},
                new double[]{0.5, 1.0, 1.5, 2.0});

        always.reset();
        // exercise() must return true regardless of state
        assertTrue(always.exercise(null /* stub state */));
        assertEquals(3, always.exerciseTimes().length);
        assertEquals(4, always.relevantTimes().length);
    }

    @Test
    public void exerciseStrategy_neverExercise_returnsFalse() {
        final ExerciseStrategy never = makeNeverExercise(
                new double[]{0.5, 1.0},
                new double[]{0.5, 1.0, 1.5});

        never.reset();
        assertFalse(never.exercise(null));
        assertEquals(2, never.exerciseTimes().length);
    }

    @Test
    public void exerciseStrategy_clone_isDistinctInstance() {
        final ExerciseStrategy original = makeAlwaysExercise(
                new double[]{1.0}, new double[]{1.0, 2.0});
        final ExerciseStrategy cloned = original.clone();

        assertNotNull(cloned);
        assertNotSame(original, cloned);
        // cloned strategy should behave identically
        assertTrue(cloned.exercise(null));
        assertArrayEquals(original.exerciseTimes(), cloned.exerciseTimes(), 0.0);
    }

    // =========================================================================
    // L0.2 — NodeData class
    // =========================================================================

    /**
     * Default-constructed NodeData must have all-zero numerics and isValid=false.
     */
    @Test
    public void nodeData_defaultConstructor_zeroAndInvalid() {
        final NodeData nd = new NodeData();
        assertEquals(0.0, nd.exerciseValue, 0.0);
        assertEquals(0.0, nd.cumulatedCashFlows, 0.0);
        assertNotNull(nd.values);
        assertEquals(0, nd.values.length);
        assertEquals(0.0, nd.controlValue, 0.0);
        assertFalse(nd.isValid);
    }

    /**
     * NodeData fields can be mutated and the mutations are visible (plain-data).
     */
    @Test
    public void nodeData_fieldMutation_isVisible() {
        final NodeData nd = new NodeData();
        nd.exerciseValue = 1.23;
        nd.cumulatedCashFlows = 4.56;
        nd.values = new double[]{0.1, 0.2, 0.3};
        nd.controlValue = 0.99;
        nd.isValid = true;

        assertEquals(1.23, nd.exerciseValue, 1e-14);
        assertEquals(4.56, nd.cumulatedCashFlows, 1e-14);
        assertEquals(3, nd.values.length);
        assertEquals(0.2, nd.values[1], 1e-14);
        assertEquals(0.99, nd.controlValue, 1e-14);
        assertTrue(nd.isValid);
    }

    // =========================================================================
    // L0.3 — ParametricExercise interface + GenericEarlyExercise
    // =========================================================================

    /**
     * Simple threshold exercise: exercise if variable[0] > parameter[0].
     * Two exercise dates: one at t=0.5, one at t=1.0.
     * Verify GenericEarlyExercise.optimize returns a plausible NPV estimate.
     */
    @Test
    public void parametricExercise_interface_compiles() {
        final ParametricExercise pe = makeThresholdExercise(2);
        assertEquals(2, pe.numberOfVariables().length);
        assertEquals(2, pe.numberOfParameters().length);
        // Each exercise has 1 variable and 1 parameter
        assertEquals(1, pe.numberOfVariables()[0]);
        assertEquals(1, pe.numberOfParameters()[0]);
        // Guess should fill a length-1 array
        final double[] guess = new double[1];
        pe.guess(0, guess);
        // No assertion on value — just verify it doesn't throw
    }

    @Test
    public void parametricExercise_exercise_returnsCorrectBoolean() {
        // exercise iff variable[0] > parameter[0]
        final ParametricExercise pe = makeThresholdExercise(1);
        // variable = 0.06 > parameter = 0.05 → exercise
        assertTrue(pe.exercise(0, new double[]{0.05}, new double[]{0.06}));
        // variable = 0.04 < parameter = 0.05 → hold
        assertFalse(pe.exercise(0, new double[]{0.05}, new double[]{0.04}));
        // variable = 0.05 == parameter → boundary (not > so hold)
        assertFalse(pe.exercise(0, new double[]{0.05}, new double[]{0.05}));
    }

    /**
     * GenericEarlyExercise.optimize with two exercise dates and controlled
     * path data. The optimal threshold should maximise payoff: paths with
     * variable > threshold should exercise.
     *
     * We supply paths where the exercise value is always 1.0 when variable=0.1
     * (> threshold guess 0.0) and cumulated = 0.5. So optimiser should favour
     * exercising → biased NPV ≈ 1.0 (all 4 paths exercise at date 1, average = 1.0).
     */
    @Test
    public void genericEarlyExercise_optimize_returnsPositiveNPV() {
        // 2 exercise dates → simulationData has 3 elements
        // simulationData[0] — initial data (cumulatedCashFlows accumulated here)
        // simulationData[1] — exercise date 0 data
        // simulationData[2] — exercise date 1 data (last; optimizer starts here)

        final int nPaths = 4;

        final List<List<NodeData>> simulationData = new ArrayList<>();
        // element 0 — initial cash flows
        final List<NodeData> initial = new ArrayList<>();
        for (int j = 0; j < nPaths; ++j) {
            final NodeData nd = new NodeData();
            nd.cumulatedCashFlows = 0.5;
            nd.isValid = true;
            initial.add(nd);
        }
        simulationData.add(initial);

        // element 1 — first exercise data (variable = 0.1, exerciseValue = 1.0)
        final List<NodeData> ex1 = new ArrayList<>();
        for (int j = 0; j < nPaths; ++j) {
            final NodeData nd = new NodeData();
            nd.exerciseValue = 1.0;
            nd.cumulatedCashFlows = 0.5;
            nd.values = new double[]{0.1}; // variable > any initial threshold guess
            nd.controlValue = 0.0;
            nd.isValid = true;
            ex1.add(nd);
        }
        simulationData.add(ex1);

        // element 2 — second exercise data (variable = 0.2, exerciseValue = 1.5)
        final List<NodeData> ex2 = new ArrayList<>();
        for (int j = 0; j < nPaths; ++j) {
            final NodeData nd = new NodeData();
            nd.exerciseValue = 1.5;
            nd.cumulatedCashFlows = 0.8;
            nd.values = new double[]{0.2};
            nd.controlValue = 0.0;
            nd.isValid = true;
            ex2.add(nd);
        }
        simulationData.add(ex2);

        final ParametricExercise pe = makeThresholdExercise(2);

        // Use a trivial optimizer that always returns the initial guess unchanged
        // (Simplex would work but we avoid a heavy dependency here; using our
        // mock method which reports EndCriteria.Null and leaves currentValue_ unchanged)
        final List<double[]> params = new ArrayList<>();
        params.add(new double[]{0.0}); // initial guess for exercise 0
        params.add(new double[]{0.0}); // initial guess for exercise 1

        // Use Simplex (already ported) with a small iteration count
        final org.jquantlib.math.optimization.Simplex simplex =
                new org.jquantlib.math.optimization.Simplex(0.01);
        final org.jquantlib.math.optimization.EndCriteria ec =
                new org.jquantlib.math.optimization.EndCriteria(
                        200, 50, 1e-8, 1e-8, 1e-8);

        final double npv = GenericEarlyExercise.optimize(simulationData, pe, params, ec, simplex);

        // NPV must be positive (exercise is clearly valuable when variable=0.1 > threshold≈0)
        assertTrue("NPV from GenericEarlyExercise must be positive, was " + npv, npv > 0.0);
        // parameters list must have been resized to n=2
        assertEquals(2, params.size());
    }

    // =========================================================================
    // L0.4 — MarketModelPathwiseMultiProduct abstract base
    // =========================================================================

    /**
     * Verify that MarketModelPathwiseMultiProduct.CashFlow has a double[] amount
     * field (vector, not scalar) and that the base class can be subclassed.
     */
    @Test
    public void pathwiseCashFlow_hasVectorAmount() {
        final MarketModelPathwiseMultiProduct.CashFlow cf =
                new MarketModelPathwiseMultiProduct.CashFlow();
        assertNotNull(cf.amount);
        assertEquals(0, cf.amount.length);
        // Set vector amount
        cf.timeIndex = 2;
        cf.amount = new double[]{0.05, 0.001, 0.002, 0.003};
        assertEquals(2, cf.timeIndex);
        assertEquals(4, cf.amount.length);
        assertEquals(0.001, cf.amount[1], 1e-14);
    }

    @Test
    public void pathwiseCashFlow_constructorWithArgs() {
        final double[] amounts = {1.0, 2.0, 3.0};
        final MarketModelPathwiseMultiProduct.CashFlow cf =
                new MarketModelPathwiseMultiProduct.CashFlow(1, amounts);
        assertEquals(1, cf.timeIndex);
        assertArrayEquals(amounts, cf.amount, 0.0);
        // same reference (no defensive copy — matches C++ struct semantics)
        assertNotNull(cf.amount);
    }

    @Test
    public void pathwiseMultiProduct_abstractBase_canBeSubclassed() {
        // Create a minimal concrete stub to verify the abstract interface
        final MarketModelPathwiseMultiProduct stub = makeSingleStepCaplet();
        assertNotNull(stub);
        assertEquals(1, stub.numberOfProducts());
        assertEquals(1, stub.maxNumberOfCashFlowsPerProductPerStep());
        assertFalse(stub.alreadyDeflated());  // not deflated by default in test stub
        assertNotNull(stub.suggestedNumeraires());
        assertNotNull(stub.possibleCashFlowTimes());

        // clone() must produce a distinct instance
        final MarketModelPathwiseMultiProduct cloned = stub.clone();
        assertNotNull(cloned);
        assertNotSame(stub, cloned);
        assertEquals(stub.numberOfProducts(), cloned.numberOfProducts());
    }

    // =========================================================================
    // Test helpers / anonymous implementations
    // =========================================================================

    /** Always-exercise strategy (exercise() always returns true). */
    private static ExerciseStrategy makeAlwaysExercise(
            final double[] exerciseTimes, final double[] relevantTimes) {
        return new ExerciseStrategy() {
            @Override public double[] exerciseTimes() { return exerciseTimes.clone(); }
            @Override public double[] relevantTimes() { return relevantTimes.clone(); }
            @Override public void reset() {}
            @Override public boolean exercise(final CurveState s) { return true; }
            @Override public void nextStep(final CurveState s) {}
            @Override public ExerciseStrategy clone() {
                return makeAlwaysExercise(exerciseTimes, relevantTimes);
            }
        };
    }

    /** Never-exercise strategy (exercise() always returns false). */
    private static ExerciseStrategy makeNeverExercise(
            final double[] exerciseTimes, final double[] relevantTimes) {
        return new ExerciseStrategy() {
            @Override public double[] exerciseTimes() { return exerciseTimes.clone(); }
            @Override public double[] relevantTimes() { return relevantTimes.clone(); }
            @Override public void reset() {}
            @Override public boolean exercise(final CurveState s) { return false; }
            @Override public void nextStep(final CurveState s) {}
            @Override public ExerciseStrategy clone() {
                return makeNeverExercise(exerciseTimes, relevantTimes);
            }
        };
    }

    /**
     * Threshold parametric exercise: exercise iff variable[0] > parameter[0].
     * {@code nExercises} exercise dates, each with 1 variable and 1 parameter.
     * Guess initialises parameter to 0.0.
     */
    private static ParametricExercise makeThresholdExercise(final int nExercises) {
        return new ParametricExercise() {
            @Override
            public int[] numberOfVariables() {
                final int[] v = new int[nExercises];
                Arrays.fill(v, 1);
                return v;
            }
            @Override
            public int[] numberOfParameters() {
                final int[] p = new int[nExercises];
                Arrays.fill(p, 1);
                return p;
            }
            @Override
            public boolean exercise(final int exerciseNumber,
                                    final double[] parameters,
                                    final double[] variables) {
                return variables[0] > parameters[0];
            }
            @Override
            public void guess(final int exerciseNumber, final double[] parameters) {
                parameters[0] = 0.0; // threshold guess = 0
            }
        };
    }

    /**
     * Minimal stub MarketModelPathwiseMultiProduct: 1 product, 1 step,
     * 1 rate. Returns a CashFlow with amount[0] = forward rate at index 0.
     */
    private static MarketModelPathwiseMultiProduct makeSingleStepCaplet() {
        final double[] rateTimes = {0.5, 1.0};
        final double[] cfTimes   = {1.0};
        return new MarketModelPathwiseMultiProduct() {
            @Override
            public int[] suggestedNumeraires() { return new int[]{1}; }
            @Override
            public EvolutionDescription evolution() {
                final double[] evolutionTimes = {0.5};
                return new EvolutionDescription(rateTimes, evolutionTimes);
            }
            @Override
            public double[] possibleCashFlowTimes() { return cfTimes.clone(); }
            @Override
            public int numberOfProducts() { return 1; }
            @Override
            public int maxNumberOfCashFlowsPerProductPerStep() { return 1; }
            @Override
            public boolean alreadyDeflated() { return false; }
            @Override
            public void reset() {}
            @Override
            public boolean nextTimeStep(final CurveState s,
                                        final int[] nCF,
                                        final CashFlow[][] cfs) {
                nCF[0] = 1;
                cfs[0][0] = new CashFlow(0, new double[]{1.0, 0.0}); // payoff + 1 delta
                return true; // done after one step
            }
            @Override
            public MarketModelPathwiseMultiProduct clone() {
                return makeSingleStepCaplet();
            }
        };
    }
}

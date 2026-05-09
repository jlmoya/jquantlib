/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.10-B.11.

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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.model.marketmodels.callability.ParametricExerciseAdapter;
import org.jquantlib.model.marketmodels.callability.TriggeredSwapExercise;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3k B.10-B.11 — TriggeredSwapExercise + ParametricExerciseAdapter.
 */
public class ParametricExerciseTest {

    @Test
    public void triggeredSwapExerciseStructure() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {1.0, 1.5};
        final double[] strikes = {0.04, 0.05};
        final TriggeredSwapExercise tex =
                new TriggeredSwapExercise(rateTimes, exerciseTimes, strikes);
        assertEquals(2, tex.numberOfExercises());
        assertArrayEquals(new int[]{1, 1}, tex.numberOfVariables());
        assertArrayEquals(new int[]{1, 1}, tex.numberOfParameters());
        // numberOfData() default routes to numberOfVariables()
        assertArrayEquals(new int[]{1, 1}, tex.numberOfData());
        for (final boolean b : tex.isExerciseTime()) {
            assertTrue(b);
        }
    }

    @Test
    public void triggeredSwapExerciseGuess() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final double[] exerciseTimes = {1.0};
        final double[] strikes = {0.04};
        final TriggeredSwapExercise tex =
                new TriggeredSwapExercise(rateTimes, exerciseTimes, strikes);
        final double[] params = new double[1];
        tex.guess(0, params);
        assertEquals(0.04, params[0], 0.0);
    }

    @Test
    public void triggeredSwapExerciseRule() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final double[] exerciseTimes = {1.0};
        final TriggeredSwapExercise tex =
                new TriggeredSwapExercise(rateTimes, exerciseTimes, new double[]{0.04});
        // exercise rule: variables[0] >= parameters[0]
        assertTrue(tex.exercise(0, new double[]{0.04}, new double[]{0.05}));
        assertTrue(tex.exercise(0, new double[]{0.04}, new double[]{0.04})); // equal -> true
        assertFalse(tex.exercise(0, new double[]{0.04}, new double[]{0.03}));
    }

    @Test
    public void parametricExerciseAdapterFiresWhenSwapAboveTrigger() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {1.0, 1.5};
        final TriggeredSwapExercise tex =
                new TriggeredSwapExercise(rateTimes, exerciseTimes, new double[]{0.04, 0.04});
        // pre-calibrated parameters: low triggers -> always fire
        final List<double[]> params = new ArrayList<>();
        params.add(new double[]{0.01});
        params.add(new double[]{0.01});
        final ParametricExerciseAdapter adapter = new ParametricExerciseAdapter(tex, params);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.045, 0.05, 0.055});

        adapter.reset();
        // After first nextStep, currentExercise_ should be 1 (since first
        // evolution time is an exercise time per default).
        adapter.nextStep(cs);
        assertTrue(adapter.exercise(cs));
    }

    @Test
    public void parametricExerciseAdapterDoesNotFire() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {1.0, 1.5};
        final TriggeredSwapExercise tex =
                new TriggeredSwapExercise(rateTimes, exerciseTimes, new double[]{0.04, 0.04});
        final List<double[]> params = new ArrayList<>();
        params.add(new double[]{0.99}); // very high trigger -> no exercise
        params.add(new double[]{0.99});
        final ParametricExerciseAdapter adapter = new ParametricExerciseAdapter(tex, params);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.045, 0.05, 0.055});

        adapter.reset();
        adapter.nextStep(cs);
        assertFalse(adapter.exercise(cs));
    }

    @Test
    public void parametricExerciseAdapterCloneIndependence() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final double[] exerciseTimes = {1.0};
        final TriggeredSwapExercise tex =
                new TriggeredSwapExercise(rateTimes, exerciseTimes, new double[]{0.04});
        final List<double[]> params = new ArrayList<>();
        params.add(new double[]{0.05});
        final ParametricExerciseAdapter adapter = new ParametricExerciseAdapter(tex, params);
        final ParametricExerciseAdapter copy = adapter.clone();
        assertArrayEquals(adapter.exerciseTimes(), copy.exerciseTimes(), 0.0);
    }

    @Test
    public void exerciseTimesIncludesAllEvolutionWhenAllExercisable() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {1.0, 1.5};
        final TriggeredSwapExercise tex =
                new TriggeredSwapExercise(rateTimes, exerciseTimes, new double[]{0.04, 0.04});
        final List<double[]> params = new ArrayList<>();
        params.add(new double[]{0.05});
        params.add(new double[]{0.05});
        final ParametricExerciseAdapter adapter = new ParametricExerciseAdapter(tex, params);
        // isExerciseTime is all-true for TriggeredSwapExercise -> exerciseTimes()
        // equals evolutionTimes (which are exerciseTimes from the input)
        assertArrayEquals(exerciseTimes, adapter.exerciseTimes(), 0.0);
        assertArrayEquals(exerciseTimes, adapter.relevantTimes(), 0.0);
    }
}

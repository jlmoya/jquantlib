/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.9.

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.model.marketmodels.callability.SwapRateTrigger;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3k B.9 — SwapRateTrigger ExerciseStrategy.
 */
public class SwapRateTriggerTest {

    @Test
    public void firesWhenSwapRateAboveTrigger() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {1.0, 1.5};
        final double[] triggers = {0.04, 0.04};
        final SwapRateTrigger trig = new SwapRateTrigger(rateTimes, triggers, exerciseTimes);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        // forwards way above 0.04 -> coterminal swap rate also above
        cs.setOnForwardRates(new double[]{0.10, 0.10, 0.10, 0.10});

        trig.reset();
        trig.nextStep(cs);
        assertTrue("trigger should fire when swap rate > trigger",
                trig.exercise(cs));
    }

    @Test
    public void doesNotFireWhenSwapRateBelowTrigger() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {1.0, 1.5};
        final double[] triggers = {0.10, 0.10};
        final SwapRateTrigger trig = new SwapRateTrigger(rateTimes, triggers, exerciseTimes);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.04, 0.04, 0.04});

        trig.reset();
        trig.nextStep(cs);
        assertFalse("trigger should not fire when swap rate < trigger",
                trig.exercise(cs));
    }

    @Test
    public void exerciseAndRelevantTimesEqual() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final double[] exerciseTimes = {1.0, 1.5};
        final double[] triggers = {0.04, 0.04};
        final SwapRateTrigger trig = new SwapRateTrigger(rateTimes, triggers, exerciseTimes);
        assertArrayEquals(exerciseTimes, trig.exerciseTimes(), 0.0);
        assertArrayEquals(exerciseTimes, trig.relevantTimes(), 0.0);
    }

    @Test
    public void cloneIndependence() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0};
        final SwapRateTrigger trig = new SwapRateTrigger(
                rateTimes, new double[]{0.04, 0.04}, new double[]{1.0, 1.5});
        final SwapRateTrigger copy = trig.clone();
        assertArrayEquals(trig.exerciseTimes(), copy.exerciseTimes(), 0.0);
    }

    @Test(expected = LibraryException.class)
    public void mismatchedTriggerSize() {
        new SwapRateTrigger(
                new double[]{0.5, 1.0, 1.5},
                new double[]{0.04},                // size 1
                new double[]{1.0, 1.5});           // size 2
    }
}

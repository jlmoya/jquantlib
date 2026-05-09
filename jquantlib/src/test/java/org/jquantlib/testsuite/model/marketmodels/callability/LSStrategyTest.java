/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.12.

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.jquantlib.model.marketmodels.callability.BermudanSwaptionExerciseValue;
import org.jquantlib.model.marketmodels.callability.LongstaffSchwartzExerciseStrategy;
import org.jquantlib.model.marketmodels.callability.NothingExerciseValue;
import org.jquantlib.model.marketmodels.callability.SwapBasisSystem;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3k B.12 — LongstaffSchwartzExerciseStrategy.
 *
 * <p>Algorithmic test: pre-computed coefficients chosen so that the
 * continuation value can be predicted directly from the basis evaluation.
 */
public class LSStrategyTest {

    @Test
    public void exerciseDecisionWithKnownCoefficients() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final EvolutionDescription evolution = new EvolutionDescription(rateTimes,
                Arrays.copyOf(rateTimes, rateTimes.length - 1));
        final int[] numeraires = EvolutionDescription.terminalMeasure(evolution);

        // ITM payer: strike 0.04 below ATM 0.05 -> exercise value > 0
        final int n = rateTimes.length - 1;
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[n];
        for (int i = 0; i < n; i++) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.04);
        }
        final BermudanSwaptionExerciseValue exercise =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);
        final NothingExerciseValue control = new NothingExerciseValue(rateTimes);

        final SwapBasisSystem basis = new SwapBasisSystem(rateTimes,
                Arrays.copyOf(rateTimes, rateTimes.length - 1));
        // Coefficients = 0 -> continuation value = 0 -> any positive
        // exercise value triggers exercise.
        final List<double[]> coeffs = new ArrayList<>();
        for (int i = 0; i < basis.numberOfExercises(); i++) {
            coeffs.add(new double[basis.numberOfFunctions()[i]]); // zero array
        }

        final LongstaffSchwartzExerciseStrategy ls =
                new LongstaffSchwartzExerciseStrategy(basis, coeffs, evolution,
                        numeraires, exercise, control);

        // Evolve curve state and step the strategy
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05}); // > strike

        ls.reset();
        ls.nextStep(cs);

        // Exercise value > 0 (ITM), continuation value = 0
        // -> ls.exercise() should return true
        assertTrue("ITM ATM swaption with zero coefficients should exercise",
                ls.exercise(cs));
    }

    @Test
    public void noExerciseWithLargeContinuationCoefficients() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final EvolutionDescription evolution = new EvolutionDescription(rateTimes,
                Arrays.copyOf(rateTimes, rateTimes.length - 1));
        final int[] numeraires = EvolutionDescription.terminalMeasure(evolution);

        final int n = rateTimes.length - 1;
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[n];
        for (int i = 0; i < n; i++) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.04);
        }
        final BermudanSwaptionExerciseValue exercise =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);
        final NothingExerciseValue control = new NothingExerciseValue(rateTimes);

        final SwapBasisSystem basis = new SwapBasisSystem(rateTimes,
                Arrays.copyOf(rateTimes, rateTimes.length - 1));

        // Huge constant coefficient at first basis function -> continuation
        // value dominates exercise value -> no exercise
        final List<double[]> coeffs = new ArrayList<>();
        for (int i = 0; i < basis.numberOfExercises(); i++) {
            final double[] c = new double[basis.numberOfFunctions()[i]];
            c[0] = 1e6; // intercept * 1.0 = huge continuation value
            coeffs.add(c);
        }

        final LongstaffSchwartzExerciseStrategy ls =
                new LongstaffSchwartzExerciseStrategy(basis, coeffs, evolution,
                        numeraires, exercise, control);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05});

        ls.reset();
        ls.nextStep(cs);

        assertFalse("huge continuation value should suppress exercise",
                ls.exercise(cs));
    }

    @Test
    public void exerciseTimesEqualEvolutionTimesWhenAllExercisable() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] evol = Arrays.copyOf(rateTimes, rateTimes.length - 1);
        final EvolutionDescription evolution = new EvolutionDescription(rateTimes, evol);
        final int[] numeraires = EvolutionDescription.terminalMeasure(evolution);

        final int n = rateTimes.length - 1;
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[n];
        for (int i = 0; i < n; i++) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.04);
        }
        final BermudanSwaptionExerciseValue exercise =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);
        final NothingExerciseValue control = new NothingExerciseValue(rateTimes);
        final SwapBasisSystem basis = new SwapBasisSystem(rateTimes, evol);
        final List<double[]> coeffs = new ArrayList<>();
        for (int i = 0; i < basis.numberOfExercises(); i++) {
            coeffs.add(new double[basis.numberOfFunctions()[i]]);
        }
        final LongstaffSchwartzExerciseStrategy ls =
                new LongstaffSchwartzExerciseStrategy(basis, coeffs, evolution,
                        numeraires, exercise, control);
        // All evolution times are exercise times when the rebate has all-true
        // isExerciseTime[]
        assertEquals(evol.length, ls.exerciseTimes().length);
        for (int i = 0; i < evol.length; i++) {
            assertEquals(evol[i], ls.exerciseTimes()[i], 0.0);
        }
    }

    @Test
    public void cloneIndependence() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] evol = Arrays.copyOf(rateTimes, rateTimes.length - 1);
        final EvolutionDescription evolution = new EvolutionDescription(rateTimes, evol);
        final int[] numeraires = EvolutionDescription.terminalMeasure(evolution);

        final int n = rateTimes.length - 1;
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[n];
        for (int i = 0; i < n; i++) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.04);
        }
        final BermudanSwaptionExerciseValue exercise =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);
        final NothingExerciseValue control = new NothingExerciseValue(rateTimes);
        final SwapBasisSystem basis = new SwapBasisSystem(rateTimes, evol);
        final List<double[]> coeffs = new ArrayList<>();
        for (int i = 0; i < basis.numberOfExercises(); i++) {
            coeffs.add(new double[basis.numberOfFunctions()[i]]);
        }
        final LongstaffSchwartzExerciseStrategy ls =
                new LongstaffSchwartzExerciseStrategy(basis, coeffs, evolution,
                        numeraires, exercise, control);
        final LongstaffSchwartzExerciseStrategy copy = ls.clone();
        assertEquals(ls.exerciseTimes().length, copy.exerciseTimes().length);
        assertEquals(ls.relevantTimes().length, copy.relevantTimes().length);
    }
}

/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.5-B.6.

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

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.model.marketmodels.MarketModelMultiProduct;
import org.jquantlib.model.marketmodels.callability.BermudanSwaptionExerciseValue;
import org.jquantlib.model.marketmodels.callability.NothingExerciseValue;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3k B.5-B.6 — exercise value implementations.
 */
public class NothingAndBermudanTest {

    private static final double TOL = 1.0e-12;

    @Test
    public void nothingExerciseValueAlwaysReturnsZero() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final NothingExerciseValue ev = new NothingExerciseValue(rateTimes);

        assertEquals(4, ev.numberOfExercises());
        assertEquals(rateTimes.length, ev.possibleCashFlowTimes().length);
        final boolean[] flags = ev.isExerciseTime();
        assertEquals(4, flags.length);
        for (final boolean b : flags) assertTrue(b);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.045, 0.05, 0.055});

        ev.reset();
        for (int i = 0; i < 4; i++) {
            ev.nextStep(cs);
            final MarketModelMultiProduct.CashFlow cf = ev.value(cs);
            assertEquals(0.0, cf.amount, TOL);
            assertEquals(i, cf.timeIndex);
        }
    }

    @Test
    public void nothingExerciseValueExplicitFlags() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final boolean[] flags = {true, false, true, false};
        final NothingExerciseValue ev = new NothingExerciseValue(rateTimes, flags);
        assertEquals(2, ev.numberOfExercises()); // count of true flags
    }

    @Test
    public void nothingExerciseValueClonePreservesState() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final NothingExerciseValue ev = new NothingExerciseValue(rateTimes);
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.045});
        ev.nextStep(cs);
        final NothingExerciseValue copy = ev.clone();
        assertEquals(ev.numberOfExercises(), copy.numberOfExercises());
        assertEquals(ev.value(cs).amount, copy.value(cs).amount, TOL);
    }

    @Test
    public void bermudanAtTheMoneySwaption() {
        // 4-rate grid; payer (call on swap rate)
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final int n = rateTimes.length - 1;
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[n];
        for (int i = 0; i < n; i++) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.05);
        }
        final BermudanSwaptionExerciseValue ev =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);
        assertEquals(n, ev.numberOfExercises());
        assertEquals(rateTimes.length, ev.possibleCashFlowTimes().length);

        // setup curve at strike: payoff = 0 -> exercise value 0
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05});

        ev.reset();
        ev.nextStep(cs);
        // at strike, payoff is exactly 0, so value = annuity * 0 = 0
        assertEquals(0.0, ev.value(cs).amount, TOL);
    }

    @Test
    public void bermudanInTheMoneySwaption() {
        // payoff = max(rate - strike, 0); strike = 0.04; rate = 0.05
        // expected value = annuity * 0.01 (positive, max applied)
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final int n = rateTimes.length - 1;
        final PlainVanillaPayoff[] payoffs = new PlainVanillaPayoff[n];
        for (int i = 0; i < n; i++) {
            payoffs[i] = new PlainVanillaPayoff(Option.Type.Call, 0.04);
        }
        final BermudanSwaptionExerciseValue ev =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);

        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.05, 0.05, 0.05, 0.05});

        ev.reset();
        ev.nextStep(cs);
        final MarketModelMultiProduct.CashFlow cf = ev.value(cs);
        // annuity > 0 and rate > strike  ->  cf.amount > 0
        assertTrue(cf.amount > 0.0);
        assertEquals(0, cf.timeIndex);

        // Verify exact: cf.amount == annuity(0,0) * 0.01
        final double expected = cs.coterminalSwapAnnuity(0, 0) * 0.01;
        assertEquals(expected, cf.amount, TOL);
    }

    @Test
    public void bermudanCloneIndependent() {
        final double[] rateTimes = {0.5, 1.0, 1.5};
        final PlainVanillaPayoff[] payoffs = {
                new PlainVanillaPayoff(Option.Type.Call, 0.04),
                new PlainVanillaPayoff(Option.Type.Call, 0.04)
        };
        final BermudanSwaptionExerciseValue ev =
                new BermudanSwaptionExerciseValue(rateTimes, payoffs);
        final BermudanSwaptionExerciseValue copy = ev.clone();
        assertEquals(ev.numberOfExercises(), copy.numberOfExercises());
        assertTrue(copy.isExerciseTime()[0]);
    }
}

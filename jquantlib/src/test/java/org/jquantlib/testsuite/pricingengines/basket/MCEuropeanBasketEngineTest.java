/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 */

package org.jquantlib.testsuite.pricingengines.basket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.MaxBasketPayoff;
import org.jquantlib.instruments.MinBasketPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.basket.MCEuropeanBasketEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Integration test for {@link MCEuropeanBasketEngine} (Phase 4i.5 WI-1).
 *
 * <p>Cross-validates the MC NPV against simple analytic identities for
 * basket options on independent (uncorrelated) GBM assets and the
 * single-asset degenerate case.
 *
 * <p>Tier: LOOSE — Monte Carlo convergence is O(1/√N). With N=10000
 * samples, antithetic on, the standard error is ~1-2% of the basket
 * price; we use a 5-10% relative tolerance.
 */
public class MCEuropeanBasketEngineTest {

    public MCEuropeanBasketEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static GeneralizedBlackScholesProcess makeBsm(
            final Date today, final double S, final double r, final double q,
            final double vol, final DayCounter dc, final Calendar cal) {
        final Handle<? extends Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, r, dc));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, q, dc));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, vol, dc));
        return new GeneralizedBlackScholesProcess(spot, qTS, rTS, volTS);
    }

    /**
     * Sanity check: a single-asset basket with AverageBasketPayoff and
     * weight=1 must reproduce the standard vanilla MC price. Cross-check
     * is via a deterministic seed; we test that the engine produces a
     * positive, finite NPV near the analytic ATM call value
     * (~9.227 for S=100, vol=20%, r=5%, q=2%, T=1y).
     */
    @Test
    public void testAverageBasketSingleAssetReproducesVanilla() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess process =
                makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);

        // single-asset "array" with 1x1 correlation = [[1]]
        final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>();
        processes.add(process);
        final Matrix corr = new Matrix(new double[][] { { 1.0 } });
        final StochasticProcessArray array = new StochasticProcessArray(processes, corr);

        final Date exDate = today.add(365);
        final Exercise exercise = new EuropeanExercise(exDate);

        // average-basket payoff on a single asset = vanilla payoff
        final PlainVanillaPayoff plain = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final BasketPayoff payoff = new AverageBasketPayoff(plain, new double[] { 1.0 });
        final BasketOption option = new BasketOption(payoff, exercise);

        final MCEuropeanBasketEngine mc = new MCEuropeanBasketEngine(
                array, /* timeSteps */ 1, McSimulation.NULL_SAMPLES,
                /* brownianBridge */ false, /* antithetic */ true,
                /* requiredSamples */ 10000,
                McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, /* seed */ 42L);
        option.setPricingEngine(mc);
        final double npv = option.NPV();

        // ATM vanilla call ~9.227; tolerate ~6% MC noise + <1% averaging-effect
        assertEquals("single-asset average basket vs vanilla closed form",
                9.227, npv, 0.6);
        assertTrue("error estimate must be positive", option.errorEstimate() > 0.0);
    }

    /**
     * Two uncorrelated identical assets, max-of-basket call, OTM strike.
     * Sanity: the price is positive and bounded by 2× the max single-
     * asset analytic value (since {@code max(S1,S2) - K <= max((S1-K)+, (S2-K)+) + max(S1,S2 - K)}).
     */
    @Test
    public void testMaxBasketTwoIndependentAssetsPositiveAndBounded() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess p1 = makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final GeneralizedBlackScholesProcess p2 = makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);

        final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>();
        processes.add(p1);
        processes.add(p2);
        // identity correlation (independent assets)
        final double[][] data = new double[][] { { 1.0, 0.0 }, { 0.0, 1.0 } };
        final Matrix corr = new Matrix(data);
        final StochasticProcessArray array = new StochasticProcessArray(processes, corr);

        final Date exDate = today.add(365);
        final Exercise exercise = new EuropeanExercise(exDate);

        final PlainVanillaPayoff plain = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final BasketPayoff payoff = new MaxBasketPayoff(plain);
        final BasketOption option = new BasketOption(payoff, exercise);

        final MCEuropeanBasketEngine mc = new MCEuropeanBasketEngine(
                array, 1, McSimulation.NULL_SAMPLES, false, true,
                10000, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 42L);
        option.setPricingEngine(mc);
        final double npv = option.NPV();

        // Single-asset analytic call ~9.227. Max of two independent assets
        // strictly dominates each, so price must be > single-asset and < 2x.
        assertTrue("max basket on 2 indep assets > single-asset call",
                npv > 9.0);
        assertTrue("max basket on 2 indep assets < 2x single-asset call",
                npv < 2.0 * 9.227);
    }

    /**
     * Two perfectly-correlated identical assets, min-of-basket call.
     * With ρ=1, the assets move together so {@code min(S1,S2) = S1}, and
     * the basket reduces to a vanilla call on a single asset.
     */
    @Test
    public void testMinBasketPerfectCorrelationReducesToVanilla() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess p1 = makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final GeneralizedBlackScholesProcess p2 = makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);

        final List<StochasticProcess1D> processes = new ArrayList<StochasticProcess1D>();
        processes.add(p1);
        processes.add(p2);
        final double[][] data = new double[][] { { 1.0, 1.0 }, { 1.0, 1.0 } };
        final Matrix corr = new Matrix(data);
        final StochasticProcessArray array = new StochasticProcessArray(processes, corr);

        final Date exDate = today.add(365);
        final Exercise exercise = new EuropeanExercise(exDate);

        final PlainVanillaPayoff plain = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final BasketPayoff payoff = new MinBasketPayoff(plain);
        final BasketOption option = new BasketOption(payoff, exercise);

        final MCEuropeanBasketEngine mc = new MCEuropeanBasketEngine(
                array, 1, McSimulation.NULL_SAMPLES, false, true,
                10000, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 42L);
        option.setPricingEngine(mc);
        final double npv = option.NPV();

        // ρ=1 ⇒ min basket = vanilla call ~9.227. Tolerate 7% MC noise.
        assertEquals("min basket with ρ=1 vs vanilla", 9.227, npv,
                0.07 * 9.227);
    }
}

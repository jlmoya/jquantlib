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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.vanilla.MCEuropeanEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
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
 * Integration test for {@link MCEuropeanEngine} (Phase 5h.5-MC-INFRA
 * WI-8). Cross-validates the MC NPV against
 * {@link AnalyticEuropeanEngine} (closed-form Black-Scholes).
 *
 * <p>Tier: LOOSE — Monte Carlo convergence is O(1/√N). With N=10000
 * samples, antithetic on, the standard error is ~1% of the price for
 * ATM options at vol=20% / T=1y; we use a 5% relative tolerance with
 * a 0.05 absolute floor for OTM low-price cases.
 *
 * <p>Mirrors the C++ test pattern in
 * {@code QuantLib/test-suite/europeanoption.cpp::testEngineConsistency}
 * (engine=PseudoMonteCarlo).
 */
public class MCEuropeanEngineTest {

    public MCEuropeanEngineTest() {
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

    private static double npv(final GeneralizedBlackScholesProcess process,
                              final Option.Type type, final double strike,
                              final Date exerciseDate,
                              final org.jquantlib.pricingengines.PricingEngine engine) {
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(engine);
        return option.NPV();
    }

    @Test
    public void testAtmCallVsAnalytic() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess process =
                makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final Date exDate = today.add(365);

        final double analyticNpv = npv(process, Option.Type.Call, 100.0,
                exDate, new AnalyticEuropeanEngine(process));

        final MCEuropeanEngine mc = new MCEuropeanEngine(
                process,
                /* timeSteps */ 1,
                /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                /* brownianBridge */ false,
                /* antithetic */ true,
                /* requiredSamples */ 10000,
                /* requiredTolerance */ McSimulation.NULL_TOLERANCE,
                /* maxSamples */ McSimulation.NULL_SAMPLES,
                /* seed */ 42L);
        final double mcNpv = npv(process, Option.Type.Call, 100.0, exDate, mc);

        // ATM call ~ 9.227; tolerate ~5% of analytic
        final double tol = 0.05 * analyticNpv;
        assertEquals("ATM call MC vs analytic", analyticNpv, mcNpv, tol);
    }

    @Test
    public void testAtmPutVsAnalytic() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess process =
                makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final Date exDate = today.add(365);

        final double analyticNpv = npv(process, Option.Type.Put, 100.0,
                exDate, new AnalyticEuropeanEngine(process));

        final MCEuropeanEngine mc = new MCEuropeanEngine(
                process, 1, McSimulation.NULL_SAMPLES, false, true,
                10000, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 42L);
        final double mcNpv = npv(process, Option.Type.Put, 100.0, exDate, mc);

        final double tol = 0.05 * analyticNpv;
        assertEquals("ATM put MC vs analytic", analyticNpv, mcNpv, tol);
    }

    @Test
    public void testItmAndOtmCalls() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess process =
                makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final Date exDate = today.add(365);

        // ITM
        final double itmRef = npv(process, Option.Type.Call, 90.0, exDate,
                new AnalyticEuropeanEngine(process));
        final double itmMc = npv(process, Option.Type.Call, 90.0, exDate,
                new MCEuropeanEngine(process, 1, McSimulation.NULL_SAMPLES,
                        false, true, 10000,
                        McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 42L));
        assertEquals("ITM call MC vs analytic", itmRef, itmMc, 0.05 * itmRef + 0.05);

        // OTM
        final double otmRef = npv(process, Option.Type.Call, 110.0, exDate,
                new AnalyticEuropeanEngine(process));
        final double otmMc = npv(process, Option.Type.Call, 110.0, exDate,
                new MCEuropeanEngine(process, 1, McSimulation.NULL_SAMPLES,
                        false, true, 10000,
                        McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 42L));
        assertEquals("OTM call MC vs analytic", otmRef, otmMc, 0.05 * otmRef + 0.05);
    }

    @Test
    public void testMultipleStepsConvergeToAnalytic() {
        // With many time steps, the Euler discretisation converges to
        // the exact GBM dynamics — MC NPV must still match the analytic
        // closed-form (which is grid-independent).
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess process =
                makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final Date exDate = today.add(365);

        final double analyticNpv = npv(process, Option.Type.Call, 100.0,
                exDate, new AnalyticEuropeanEngine(process));

        final MCEuropeanEngine mc = new MCEuropeanEngine(
                process, 12, McSimulation.NULL_SAMPLES, false, true,
                5000, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 99L);
        final double mcNpv = npv(process, Option.Type.Call, 100.0, exDate, mc);

        assertEquals("12-step MC ATM call", analyticNpv, mcNpv, 0.07 * analyticNpv);
    }

    @Test
    public void testErrorEstimatePositive() {
        final Date today = new Date(15, Month.January, 2026);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new NullCalendar();
        final GeneralizedBlackScholesProcess process =
                makeBsm(today, 100.0, 0.05, 0.02, 0.20, dc, cal);
        final Date exDate = today.add(365);

        final MCEuropeanEngine mc = new MCEuropeanEngine(
                process, 1, McSimulation.NULL_SAMPLES, false, false,
                4096, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 7L);
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final Exercise exercise = new EuropeanExercise(exDate);
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(mc);
        option.NPV();
        assertTrue("MC error estimate must be positive",
                option.errorEstimate() > 0.0);
    }
}

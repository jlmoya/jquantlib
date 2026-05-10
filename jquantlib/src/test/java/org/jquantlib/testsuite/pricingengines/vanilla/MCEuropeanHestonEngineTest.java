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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.pricingengines.vanilla.MCEuropeanHestonEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Integration test for {@link MCEuropeanHestonEngine} (Phase
 * 5h.5-Bates-b).
 *
 * <p>Cross-validates the multi-variate MC NPV against
 * {@link AnalyticHestonEngine} (Gatheral semi-analytic): the MC engine
 * must converge to the closed-form Heston price as N → ∞.
 *
 * <p>Tier: LOOSE — Monte Carlo convergence is O(1/√N). With moderate
 * sample counts and antithetic on, residuals up to ~10% of the
 * reference are expected for these stochastic-volatility settings.
 *
 * <p>Mirrors the C++ test pattern in
 * {@code QuantLib/test-suite/batesmodel.cpp::testAnalyticVsMCPricing}
 * (parameter ranges follow that fixture's spirit).
 */
public class MCEuropeanHestonEngineTest {

    public MCEuropeanHestonEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static HestonProcess makeProcess(
            final Date today, final double S, final double r, final double q,
            final double v0, final double kappa, final double theta,
            final double sigma, final double rho,
            final DayCounter dc) {
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(S));
        final YieldTermStructure rTS = new FlatForward(today, r, dc);
        final YieldTermStructure qTS = new FlatForward(today, q, dc);
        final HestonProcess p = new HestonProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0, kappa, theta, sigma, rho);
        // The Java HestonProcess does not call update() in its constructor;
        // initialValues() returns [s0v_, v0v_] which start at 0 until
        // update() explicitly populates them. Mirrors the BatesModelTest
        // boilerplate.
        p.update();
        return p;
    }

    private static double npv(final HestonProcess process,
                              final Option.Type type, final double strike,
                              final Date exerciseDate,
                              final org.jquantlib.pricingengines.PricingEngine engine) {
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(engine);
        return option.NPV();
    }

    /**
     * Cross-validates ATM MC vs. AnalyticHestonEngine on a textbook
     * Heston parameter set. With antithetic + 4000 samples and a
     * 16-step time grid the MC error is dominated by the Euler
     * discretisation bias and the sampling error.
     */
    @Test
    public void testAtmCallVsAnalytic() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final HestonProcess process = makeProcess(
                today, 100.0, 0.05, 0.02,
                /* v0 */     0.04,
                /* kappa */  1.0,
                /* theta */  0.04,
                /* sigma */  0.20,
                /* rho */   -0.5,
                dc);
        final Date exDate = today.add(365);

        final HestonModel model = new HestonModel(process);
        final double analyticNpv = npv(process, Option.Type.Call, 100.0,
                exDate, new AnalyticHestonEngine(model, process, 128));

        final MCEuropeanHestonEngine mc = new MCEuropeanHestonEngine(
                process,
                /* timeSteps */ 16,
                /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                /* antitheticVariate */ true,
                /* requiredSamples */ 4000,
                /* requiredTolerance */ McSimulation.NULL_TOLERANCE,
                /* maxSamples */ McSimulation.NULL_SAMPLES,
                /* seed */ 1234L);
        final double mcNpv = npv(process, Option.Type.Call, 100.0, exDate, mc);

        // ATM Heston call ~ 9-10; MC sampling error + Euler bias ≲ 10%
        // for these settings. Tighten only after the SDE discretisation
        // is upgraded to QuadraticExponentialMartingale.
        assertEquals("ATM Heston call MC vs analytic",
                analyticNpv, mcNpv, 0.10 * analyticNpv);
    }

    /** Put-call symmetry check via two MC runs and analytic reference. */
    @Test
    public void testAtmPutVsAnalytic() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final HestonProcess process = makeProcess(
                today, 100.0, 0.05, 0.02,
                0.04, 1.0, 0.04, 0.20, -0.5, dc);
        final Date exDate = today.add(365);

        final HestonModel model = new HestonModel(process);
        final double analyticNpv = npv(process, Option.Type.Put, 100.0,
                exDate, new AnalyticHestonEngine(model, process, 128));

        final MCEuropeanHestonEngine mc = new MCEuropeanHestonEngine(
                process, 16, McSimulation.NULL_SAMPLES, true,
                4000, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 1234L);
        final double mcNpv = npv(process, Option.Type.Put, 100.0, exDate, mc);

        assertEquals("ATM Heston put MC vs analytic",
                analyticNpv, mcNpv, 0.10 * analyticNpv + 0.05);
    }

    /**
     * Sanity: the engine must publish a positive error estimate when
     * antithetic is OFF (analytic standard error of the sample mean).
     */
    @Test
    public void testErrorEstimatePositive() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final HestonProcess process = makeProcess(
                today, 100.0, 0.05, 0.02,
                0.04, 1.0, 0.04, 0.20, -0.5, dc);
        final Date exDate = today.add(365);

        final MCEuropeanHestonEngine mc = new MCEuropeanHestonEngine(
                process, 8, McSimulation.NULL_SAMPLES, false,
                2048, McSimulation.NULL_TOLERANCE, McSimulation.NULL_SAMPLES, 7L);
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final Exercise exercise = new EuropeanExercise(exDate);
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(mc);
        option.NPV();
        assertTrue("MC Heston error estimate must be positive",
                option.errorEstimate() > 0.0);
    }
}

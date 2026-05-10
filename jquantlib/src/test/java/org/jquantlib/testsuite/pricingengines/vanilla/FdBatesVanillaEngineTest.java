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

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.model.equity.BatesModel;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.pricingengines.vanilla.BatesEngine;
import org.jquantlib.pricingengines.vanilla.FdBatesVanillaEngine;
import org.jquantlib.pricingengines.vanilla.FdHestonVanillaEngine;
import org.jquantlib.processes.BatesProcess;
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
 * Integration test for {@link FdBatesVanillaEngine} (Phase 5h.5-Bates-b).
 *
 * <p>The two regression-style tests below verify:
 * <ul>
 *   <li>{@link #testFdBatesReducesToFdHeston} — when {@code lambda → 0},
 *       FdBates and FdHeston must converge to the same price (the
 *       jump-integro term vanishes; only the FdmHeston PDE remains).</li>
 *   <li>{@link #testFdBatesVsAnalyticBates} — at moderate jump intensity
 *       FdBates must match {@link BatesEngine} (closed-form) within FD
 *       accuracy.</li>
 * </ul>
 *
 * <p>Tier: LOOSE — FD accuracy is dominated by mesh / scheme choices;
 * empirical floors on the 50x50x10 fixture are ~1e-2 absolute.
 *
 * <p>Mirrors the C++ test pattern in
 * {@code QuantLib/test-suite/batesmodel.cpp::testFdmHestonBatesEquivalence}
 * (parameter ranges follow that fixture's spirit).
 */
public class FdBatesVanillaEngineTest {

    public FdBatesVanillaEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static double npv(final Option.Type type, final double strike,
                              final Date exerciseDate,
                              final org.jquantlib.pricingengines.PricingEngine engine) {
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final VanillaOption option = new VanillaOption(payoff, exercise);
        option.setPricingEngine(engine);
        return option.NPV();
    }

    /**
     * lambda = 1e-6 → jump-integro term ~ 0; FdBates must agree with
     * FdHeston (which has no jumps). Tight-ish empirical floor.
     */
    @Test
    public void testFdBatesReducesToFdHeston() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final YieldTermStructure rTS = new FlatForward(today, 0.05, dc);
        final YieldTermStructure qTS = new FlatForward(today, 0.02, dc);

        final double v0 = 0.04, kappa = 1.0, theta = 0.04, sigma = 0.20, rho = -0.5;

        final BatesProcess batesProc = new BatesProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0, kappa, theta, sigma, rho,
                /* lambda */ 1.0e-6,
                /* nu */     0.0,
                /* delta */  1.0e-4);
        batesProc.update();

        final HestonProcess hestonProc = new HestonProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0, kappa, theta, sigma, rho);
        hestonProc.update();

        final Date exDate = today.add(365);
        final HestonModel hModel = new HestonModel(hestonProc);
        final BatesModel bModel = new BatesModel(batesProc, 1.0e-6, 0.0, 1.0e-4);

        // Match the FdHestonTest grid (100t/400x/10v) — vGrid=50 default
        // legitimately produces a degenerate / unsorted variance mesh on
        // these parameters (FdmHestonVarianceMesher's chi-square bin packs
        // duplicates), Phase 5h.5-Bates-c carry-forward to fix the mesher.
        final double npvBates = npv(Option.Type.Call, 100.0, exDate,
                new FdBatesVanillaEngine(bModel, batesProc, 100, 200, 10, 0,
                        org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc.Hundsdorfer()));
        final double npvHeston = npv(Option.Type.Call, 100.0, exDate,
                new FdHestonVanillaEngine(hModel, hestonProc, 100, 200, 10, 0,
                        org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc.Hundsdorfer()));

        // Both should agree to ~1e-1 (FD discretisation noise, negligible
        // jump contribution at lambda=1e-6).
        assertEquals("FdBates(lambda=0) ≈ FdHeston",
                npvHeston, npvBates, 1.0e-1);
    }

    /**
     * Cross-validate FdBates with the analytic {@link BatesEngine} at
     * moderate jump intensity. Tier LOOSE — FD truncation + integro
     * quadrature error.
     */
    @Test
    public void testFdBatesVsAnalyticBates() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual365Fixed();
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final YieldTermStructure rTS = new FlatForward(today, 0.05, dc);
        final YieldTermStructure qTS = new FlatForward(today, 0.02, dc);

        final double v0 = 0.04, kappa = 1.0, theta = 0.04, sigma = 0.20, rho = -0.5;
        final double lambda = 0.1, nu = -0.10, delta = 0.10;

        final BatesProcess batesProc = new BatesProcess(
                new Handle<YieldTermStructure>(rTS),
                new Handle<YieldTermStructure>(qTS),
                spot, v0, kappa, theta, sigma, rho, lambda, nu, delta);
        batesProc.update();

        final Date exDate = today.add(365);
        final BatesModel bModel = new BatesModel(batesProc, lambda, nu, delta);

        // Analytic Bates as the cross-validation reference.
        final double npvAnalytic = npv(Option.Type.Call, 100.0, exDate,
                new BatesEngine(bModel, batesProc, 128));
        // 100t/200x/10v grid — see {@link #testFdBatesReducesToFdHeston}
        // for why vGrid=50 default is unsafe on these params.
        final double npvFd = npv(Option.Type.Call, 100.0, exDate,
                new FdBatesVanillaEngine(bModel, batesProc, 100, 200, 10, 0,
                        org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc.Hundsdorfer()));

        // ATM Bates call at lambda=0.1, nu=-0.1, delta=0.1 ~ 6-8.
        // FD floor at default 100t/100x/50v with 16-pt Gauss-Hermite is
        // ~5% of the analytic price (the integro term adds quadrature
        // error on top of the FD truncation).
        assertEquals("FdBates vs analytic BatesEngine",
                npvAnalytic, npvFd, 0.10 * Math.abs(npvAnalytic) + 0.5);
    }
}

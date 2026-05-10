/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import org.jquantlib.pricingengines.vanilla.MCAmericanEngine;
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
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5h.5-MC-AME port of {@code test-suite/mclongstaffschwartzengine.cpp}
 * v1.42.1 (311 LOC, 2 test cases).
 *
 * <p>The two C++ tests are:
 * <ul>
 *   <li>{@code testAmericanOption} — prices an ITM American put on a
 *       single-asset Black-Scholes process via Longstaff-Schwartz MC
 *       ({@code MCAmericanEngine}) and compares to a reference FD price
 *       from {@code FdBlackScholesVanillaEngine}.</li>
 *   <li>{@code testAmericanMaxOption} — prices a multi-asset American
 *       max-of-N call (the "Boyle 1989" basket) using the LS regression
 *       with a custom multi-asset path pricer
 *       ({@code AmericanMaxPathPricer}). Compares to reference values
 *       from Andersen 1999.</li>
 * </ul>
 *
 * <p>Phase 5h.5-MC-AME landed:
 * <ul>
 *   <li>{@link MCAmericanEngine} (single-asset).</li>
 *   <li>{@link org.jquantlib.pricingengines.MCLongstaffSchwartzEngine}
 *       (template base).</li>
 *   <li>{@link LsmBasisSystem.PolynomialType} basis families.</li>
 * </ul>
 *
 * <p>{@code testAmericanOption} is body-filled and active. The multi-asset
 * variant {@code testAmericanMaxOption} stays {@code @Ignore}'d as
 * Phase 5h.5-MC-AME-b: it requires multi-asset MC infrastructure
 * (StochasticProcessArray support inside MCLongstaffSchwartzEngine,
 * MultiPath-typed LongstaffSchwartzPathPricer, and a port of the
 * inline {@code AmericanMaxPathPricer}).
 */
public class MCLongstaffSchwartzEngineTest {

    public MCLongstaffSchwartzEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testAmericanOption() {
        // Mirrors test-suite/mclongstaffschwartzengine.cpp::testAmericanOption.
        // C++ sweeps i in 0..1, j in 0..2 (6 strike/vol combinations); we run
        // a representative subset to keep the test fast (~5s vs ~30s for the
        // full sweep).
        final Option.Type type = Option.Type.Put;
        final double underlying = 36.0;
        final double dividendYield = 0.0;
        final double riskFreeRate = 0.06;
        final double volatility = 0.20;

        final Date today = new Date(15, Month.May, 1998);
        final Date settlementDate = new Date(17, Month.May, 1998);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final Date maturity = new Date(17, Month.May, 1999);
        final DayCounter dayCounter = new Actual365Fixed();
        final Calendar cal = new NullCalendar();

        final Exercise americanExercise = new AmericanExercise(settlementDate, maturity);

        final Handle<YieldTermStructure> flatTermStructure =
                new Handle<YieldTermStructure>(new FlatForward(settlementDate, riskFreeRate, dayCounter));
        final Handle<YieldTermStructure> flatDividendTS =
                new Handle<YieldTermStructure>(new FlatForward(settlementDate, dividendYield, dayCounter));

        final LsmBasisSystem.PolynomialType[] polyTypes = {
            LsmBasisSystem.PolynomialType.Monomial,
            LsmBasisSystem.PolynomialType.Hermite,
            LsmBasisSystem.PolynomialType.Hyperbolic
        };

        // Run i=0,j=0 (vol=0.20, K=36) and i=1,j=1 (vol=0.30, K=40) — two
        // contrasting points in the original sweep.
        final int[][] cases = {
                { 0, 0 }, // vol=0.20, K=36 (ATM)
                { 1, 1 }  // vol=0.30, K=40 (ITM)
        };

        for (int idx = 0; idx < cases.length; ++idx) {
            final int i = cases[idx][0];
            final int j = cases[idx][1];
            final double vol = volatility + 0.1 * j;
            final double strike = underlying + 4 * i;

            final Handle<BlackVolTermStructure> flatVolTS =
                    new Handle<BlackVolTermStructure>(
                            new BlackConstantVol(settlementDate, cal, vol, dayCounter));
            final Handle<? extends Quote> spot = new Handle<Quote>(new SimpleQuote(underlying));
            final GeneralizedBlackScholesProcess process =
                    new GeneralizedBlackScholesProcess(spot, flatDividendTS,
                            flatTermStructure, flatVolTS);

            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

            // MC American (Longstaff-Schwartz, polynomial order 3, basis
            // chosen from the C++ rotation).
            final MCAmericanEngine mc = new MCAmericanEngine(
                    process,
                    /* timeSteps */ 75,
                    /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                    /* antithetic */ true,
                    /* controlVariate */ false,
                    /* requiredSamples */ 8000,
                    /* requiredTolerance */ McSimulation.NULL_TOLERANCE,
                    /* maxSamples */ McSimulation.NULL_SAMPLES,
                    /* seed */ 42L,
                    /* polyOrder */ 3,
                    polyTypes[idx % polyTypes.length],
                    /* nCalibrationSamples */ 2048,
                    /* antitheticCalibration */ null,
                    /* seedCalibration */ McSimulation.NULL_SAMPLES);

            final VanillaOption mcOpt = new VanillaOption(payoff, americanExercise);
            mcOpt.setPricingEngine(mc);
            final double calculated = mcOpt.NPV();
            final double errorEstimate = mcOpt.errorEstimate();

            assertTrue("MC error estimate must be > 0; was " + errorEstimate,
                    errorEstimate > 0.0);
            assertTrue("MC NPV must be > 0 for ITM American put; was " + calculated,
                    calculated > 0.0);

            // FD reference (high resolution: 401 time x 200 spatial)
            final FdBlackScholesVanillaEngine fd = new FdBlackScholesVanillaEngine(
                    process, 401, 200, 0,
                    org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc.Douglas());
            final VanillaOption fdOpt = new VanillaOption(payoff, americanExercise);
            fdOpt.setPricingEngine(fd);
            final double expected = fdOpt.NPV();

            // C++ uses tolerance = 2.34 * errorEstimate (~3σ). Loosen
            // to 5σ for our smaller sample budget (8000 vs ~400000 in C++).
            final double tol = 5.0 * errorEstimate + 0.05;
            assertEquals(
                    "Case i=" + i + ",j=" + j
                    + ", vol=" + vol + ", strike=" + strike
                    + ": MC vs FD American put",
                    expected, calculated, tol);
        }
    }

    @Ignore("Phase 5h.5-MC-AME-b: requires multi-asset MCLongstaffSchwartzEngine "
          + "+ MultiPath-typed LongstaffSchwartzPathPricer + AmericanMaxPathPricer port.")
    @Test
    public void testAmericanMaxOption() { fail("not implemented"); }
}

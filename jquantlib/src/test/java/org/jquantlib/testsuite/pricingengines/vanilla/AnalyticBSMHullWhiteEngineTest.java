/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticBSMHullWhiteEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Cross-validation tests for {@link AnalyticBSMHullWhiteEngine}.
 *
 * <p>The C++ test {@code testBsmHullWhiteEngine}
 * (test-suite/hybridhestonhullwhiteprocess.cpp::testBsmHullWhiteEngine,
 * v1.42.1 lines 63-155) checks five correlations against a pre-published
 * implied-volatility table. That test depends on {@code Date::todaysDate()}
 * to set the 20Y maturity, so reproducing the literal table requires
 * running the C++ probe on the same wall-clock date — a minor amount of
 * drift in the value comes from the {@code Actual365Fixed} year-fraction
 * shifting between runs.
 *
 * <p>To remove date-dependence and stay deterministic, this Java port
 * directly verifies the variance-offset closed form documented in
 * {@code AnalyticBSMHullWhiteEngine::calculate()}: pricing the option
 * with the BSM-HW engine should equal pricing under a vanilla
 * {@link AnalyticEuropeanEngine} whose Black variance is uniformly
 * shifted by {@code varianceOffset(rho, a, sigma, eta, T)}.
 *
 * <p>Phase 5h.5-HHW WI-2 (carry-forward of Phase 5h skeleton).
 */
public class AnalyticBSMHullWhiteEngineTest {

    private static final double TIGHT_NPV = 1.0e-12;
    private static final double LOOSE_NPV = 1.0e-9;

    @Test
    public void testCorrelationZeroReducesToSimpleVarianceShift() {
        // rho = 0 makes the cross-term mu vanish; only the v offset remains.
        verifyVarianceShift(0.0);
    }

    @Test
    public void testPositiveCorrelationAddsCrossTerm() {
        verifyVarianceShift(0.25);
        verifyVarianceShift(0.75);
    }

    @Test
    public void testNegativeCorrelationSubtractsCrossTerm() {
        verifyVarianceShift(-0.25);
        verifyVarianceShift(-0.75);
    }

    @Test
    public void testSmallATaylorBranch() {
        // Use a so small the engine takes the low-a Taylor branch.
        // Threshold is QL_EPSILON^0.25 ~ 1.22e-4 over T years.
        // T = 20 → require a*T < 1.22e-4 → a < 6.1e-6.
        final double a = 1.0e-7;
        final double sigma = 0.005;
        verifyVarianceShiftCustom(0.5, a, sigma);
    }

    /** Engine == manual reference (variance-offset reduction) at all 5 corrs. */
    @Test
    public void testFiveCorrelationsFromCppTestSuite() {
        for (final double rho : new double[]{-0.75, -0.25, 0.0, 0.25, 0.75}) {
            verifyVarianceShift(rho);
        }
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private void verifyVarianceShift(final double rho) {
        verifyVarianceShiftCustom(rho, 0.00883, 0.00526);
    }

    private void verifyVarianceShiftCustom(final double rho,
                                           final double a,
                                           final double sigma) {
        // ------- Setup mirroring C++ testBsmHullWhiteEngine ----------
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.July, 2026);
        new Settings().setEvaluationDate(today);

        final Date maturity = today.add(20 * 365); // approx 20Y; dc converts back

        final SimpleQuote spotQ = new SimpleQuote(100.0);
        final Handle<Quote> spot = new Handle<Quote>(spotQ);

        final SimpleQuote qRate = new SimpleQuote(0.04);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate), dc));
        final SimpleQuote rRate = new SimpleQuote(0.0525);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(rRate), dc));
        final double volValue = 0.25;
        final SimpleQuote volQ = new SimpleQuote(volValue);
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(), new Handle<Quote>(volQ), dc));

        final HullWhite hwModel = new HullWhite(rTS, a, sigma);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                spot, qTS, rTS, volTS);

        final EuropeanExercise exercise = new EuropeanExercise(maturity);
        final double fwd = spotQ.value()
                * qTS.currentLink().discount(maturity)
                / rTS.currentLink().discount(maturity);
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, fwd);

        // ------- Engine under test ----------
        final PricingEngine bsmhw = new AnalyticBSMHullWhiteEngine(rho, stochProcess, hwModel);
        final EuropeanOption option = new EuropeanOption(payoff, exercise);
        option.setPricingEngine(bsmhw);
        final double npvBsmHw = option.NPV();

        // ------- Reference: equivalent vanilla BSM with shifted vol ----------
        // Closed-form variance offset from analyticbsmhullwhiteengine.cpp
        // lines 96-110.
        final double t = dc.yearFraction(today, maturity);
        final double eta = volValue; // flat surface
        final double varianceOffset;
        final double cutoff = Math.pow(2.220446049250313e-16 /* QL_EPSILON */, 0.25);
        if (a * t > cutoff) {
            final double v = sigma * sigma / (a * a)
                    * (t + 2.0 / a * Math.exp(-a * t)
                            - 1.0 / (2.0 * a) * Math.exp(-2.0 * a * t)
                            - 3.0 / (2.0 * a));
            final double mu = 2.0 * rho * sigma * eta / a
                    * (t - 1.0 / a * (1.0 - Math.exp(-a * t)));
            varianceOffset = v + mu;
        } else {
            final double v = sigma * sigma * t * t * t
                    * (1.0 / 3.0 - 0.25 * a * t + 7.0 / 60.0 * a * a * t * t);
            final double mu = rho * sigma * eta * t * t
                    * (1.0 - a * t / 3.0 + a * a * t * t / 12.0);
            varianceOffset = v + mu;
        }
        final double shiftedVol = Math.sqrt((volValue * volValue * t + varianceOffset) / t);
        assertTrue("shifted vol should be positive (rho=" + rho + ")", shiftedVol > 0);

        final SimpleQuote volShQ = new SimpleQuote(shiftedVol);
        final Handle<BlackVolTermStructure> volShTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(), new Handle<Quote>(volShQ), dc));
        final BlackScholesMertonProcess shiftedProc = new BlackScholesMertonProcess(
                spot, qTS, rTS, volShTS);
        final PricingEngine bsm = new AnalyticEuropeanEngine(shiftedProc);

        final EuropeanOption refOpt = new EuropeanOption(payoff, exercise);
        refOpt.setPricingEngine(bsm);
        final double npvRef = refOpt.NPV();

        // The two prices must agree exactly modulo the floating-point
        // residual of (vol^2 * t + offset) -> sqrt -> blackVariance round-trip.
        final double absErr = Math.abs(npvBsmHw - npvRef);
        final double relErr = absErr / Math.max(Math.abs(npvRef), 1e-30);
        assertTrue(
                "BSM-HW vs shifted-BSM rho=" + rho
                        + " a=" + a + " sigma=" + sigma
                        + ": npvBsmHw=" + npvBsmHw + " npvRef=" + npvRef
                        + " relErr=" + relErr,
                relErr < LOOSE_NPV);
        assertEquals("absolute NPV match", npvRef, npvBsmHw, TIGHT_NPV * Math.max(npvRef, 1.0));
    }
}

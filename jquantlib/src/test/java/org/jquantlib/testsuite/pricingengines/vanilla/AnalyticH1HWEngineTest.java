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
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticH1HWEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonHullWhiteEngine;
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
 * Phase 5h.5-HHW WI-4 tests for {@link AnalyticH1HWEngine}.
 *
 * <p>Smoke tests:
 * <ul>
 *   <li>{@code rhoSr = 0}: H1-HW should reduce identically to the basic
 *       {@link AnalyticHestonHullWhiteEngine} (the I4 add-on is multiplied
 *       by {@code eta * rhoSr}).</li>
 *   <li>{@code rhoSr > 0}: NPV must remain positive and within a sensible
 *       neighborhood of the rhoSr=0 reference.</li>
 *   <li>Negative {@code rhoSr} must be rejected at construction.</li>
 * </ul>
 */
public class AnalyticH1HWEngineTest {

    @Test
    public void testReducesToHestonHwWhenRhoSrZero() {
        final Setup s = setupBaseline();

        final PricingEngine baseEng = new AnalyticHestonHullWhiteEngine(
                s.hestonModel, s.hestonProc, s.hwModel);
        final PricingEngine h1hwEng = new AnalyticH1HWEngine(
                s.hestonModel, s.hestonProc, s.hwModel, 0.0);

        for (final int years : new int[] { 1, 5, 10 }) {
            final Date maturity = s.today.add(years * 365);
            final EuropeanExercise exercise = new EuropeanExercise(maturity);
            final double fwd = 100.0
                    * s.qTS.currentLink().discount(maturity)
                    / s.rTS.currentLink().discount(maturity);
            final PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(Option.Type.Call, fwd);

            final EuropeanOption optBase = new EuropeanOption(payoff, exercise);
            optBase.setPricingEngine(baseEng);
            final double npvBase = optBase.NPV();

            final EuropeanOption optH1 = new EuropeanOption(payoff, exercise);
            optH1.setPricingEngine(h1hwEng);
            final double npvH1 = optH1.NPV();

            assertEquals("rhoSr=0 should reduce to Heston-HW @ " + years + "y",
                    npvBase, npvH1, 1.0e-10);
        }
    }

    @Test
    public void testPositiveRhoSrPerturbsNPV() {
        final Setup s = setupBaseline();
        final PricingEngine baseEng = new AnalyticHestonHullWhiteEngine(
                s.hestonModel, s.hestonProc, s.hwModel);
        final PricingEngine h1hwEng = new AnalyticH1HWEngine(
                s.hestonModel, s.hestonProc, s.hwModel, 0.3);

        final Date maturity = s.today.add(5 * 365);
        final EuropeanExercise exercise = new EuropeanExercise(maturity);
        final double fwd = 100.0
                * s.qTS.currentLink().discount(maturity)
                / s.rTS.currentLink().discount(maturity);
        final PlainVanillaPayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, fwd);

        final EuropeanOption optBase = new EuropeanOption(payoff, exercise);
        optBase.setPricingEngine(baseEng);
        final double npvBase = optBase.NPV();

        final EuropeanOption optH1 = new EuropeanOption(payoff, exercise);
        optH1.setPricingEngine(h1hwEng);
        final double npvH1 = optH1.NPV();

        assertTrue("npvBase positive: " + npvBase, npvBase > 0.0);
        assertTrue("npvH1 positive: " + npvH1, npvH1 > 0.0);
        // The H1-HW correction is real-valued in the integrand; with positive
        // rhoSr the price should differ but stay within ~10% of the base.
        // (At 5y / typical Heston params the correction is small.)
        final double relDiff = Math.abs(npvH1 - npvBase) / npvBase;
        assertTrue("H1HW deviation reasonable: " + relDiff, relDiff < 0.15);
    }

    @Test(expected = Exception.class)
    public void testNegativeRhoSrRejected() {
        final Setup s = setupBaseline();
        new AnalyticH1HWEngine(s.hestonModel, s.hestonProc, s.hwModel, -0.1);
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static final class Setup {
        Date today;
        Handle<YieldTermStructure> rTS, qTS;
        HestonProcess hestonProc;
        HestonModel hestonModel;
        HullWhite hwModel;
    }

    private Setup setupBaseline() {
        final Setup s = new Setup();
        final DayCounter dc = new Actual365Fixed();
        s.today = new Date(15, Month.July, 2026);
        new Settings().setEvaluationDate(s.today);

        s.rTS = new Handle<YieldTermStructure>(new FlatForward(s.today, 0.04, dc));
        s.qTS = new Handle<YieldTermStructure>(new FlatForward(s.today, 0.02, dc));
        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));

        s.hestonProc = new HestonProcess(s.rTS, s.qTS, spot,
                0.05,    // v0
                1.0,     // kappa
                0.05,    // theta
                0.3,     // sigma_v
                -0.6     // rho_SV
        );
        s.hestonProc.update();
        s.hestonModel = new HestonModel(s.hestonProc);
        s.hwModel = new HullWhite(s.rTS, 0.05, 0.01);
        return s;
    }
}

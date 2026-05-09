/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.forward;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.forward.AnalyticHestonForwardEuropeanEngine;
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Phase 4a.5 A.5.3 smoke tests for {@link AnalyticHestonForwardEuropeanEngine}.
 *
 * <p>The fixture mirrors the C++ {@code testHestonMCVsAnalyticPrices} test
 * in {@code test-suite/forwardoption.cpp}: 1y maturity, 6M reset,
 * S=100, r=0.005, q=0.03, Heston(v0=0.09, kappa=11.35, theta=0.022,
 * sigma=0.618, rho=-0.5). Reference moneyness values are 0.8, 1.0, 1.2.
 *
 * <p>Smoke checks:
 * <ul>
 *   <li>Call NPV positive and within a reasonable range (sanity).</li>
 *   <li>Put-call parity-like ordering: ATM call > ATM put for q < r? Skip
 *       — q=0.03 > r=0.005 here, so put-call parity is non-trivial.
 *       Instead verify call price monotonically decreases with moneyness.</li>
 *   <li>Sub-ms reset path (resetTime ≤ 1e-3) falls back to vanilla Heston
 *       and yields a positive sensible price.</li>
 * </ul>
 */
public class AnalyticHestonForwardEuropeanEngineTest {

    @Test
    public void atmCallPositiveAndMonotonic() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual360();

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.005)), dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.03)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(flatQ);

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));

        // Heston: v0=0.09, kappa=11.35, theta=0.022, sigma=0.618, rho=-0.5
        final HestonProcess hp = new HestonProcess(rTS, qTS, spot,
                0.09, 11.35, 0.022, 0.618, -0.5);

        final AnalyticHestonForwardEuropeanEngine engine =
                new AnalyticHestonForwardEuropeanEngine(hp);

        final Date reset = today.add(180);   // 6 months
        final Date exDate = today.add(365);  // 1 year
        final Exercise exercise = new EuropeanExercise(exDate);
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 0.0);

        // Call NPVs at moneyness 0.8, 1.0, 1.2 should be monotonically decreasing.
        final double[] mns = { 0.8, 1.0, 1.2 };
        final double[] npvs = new double[mns.length];
        for (int i = 0; i < mns.length; i++) {
            final ForwardVanillaOption opt = new ForwardVanillaOption(mns[i], reset, payoff, exercise);
            opt.setPricingEngine(engine);
            npvs[i] = opt.NPV();
            assertTrue("call NPV positive at m=" + mns[i] + " (npv=" + npvs[i] + ")",
                       npvs[i] > 0.0);
            // Sanity: NPV should be a small fraction of spot
            assertTrue("call NPV reasonable magnitude m=" + mns[i] + " npv=" + npvs[i],
                       npvs[i] < 50.0);
        }
        // Monotonicity (ITM > ATM > OTM)
        assertTrue("ITM (m=0.8) > ATM (m=1.0)", npvs[0] > npvs[1]);
        assertTrue("ATM (m=1.0) > OTM (m=1.2)", npvs[1] > npvs[2]);
    }

    @Test
    public void shortResetFallback() {
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final DayCounter dc = new Actual360();

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.005)), dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.03)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(flatQ);

        final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(100.0));
        final HestonProcess hp = new HestonProcess(rTS, qTS, spot,
                0.09, 11.35, 0.022, 0.618, -0.5);
        final AnalyticHestonForwardEuropeanEngine engine =
                new AnalyticHestonForwardEuropeanEngine(hp);

        // Reset = today (resetTime == 0 -> falls back to vanilla path)
        // To stay > evaluationDate per validate(), use today exactly which is OK
        // (validate accepts >=). Then exercise = today + 1y.
        final Date exDate = today.add(365);
        final Exercise exercise = new EuropeanExercise(exDate);
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 0.0);

        final ForwardVanillaOption opt = new ForwardVanillaOption(1.0, today, payoff, exercise);
        opt.setPricingEngine(engine);
        final double npv = opt.NPV();
        assertTrue("short-reset (vanilla fallback) call NPV positive: " + npv, npv > 0.0);
        assertTrue("short-reset call NPV reasonable: " + npv, npv < 30.0);
    }

    @Test
    public void propagatorIsPositive() {
        // Sanity: the noncentral-chisq propagator should be a valid PDF
        // (positive at sensible varReset values).
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);
        final DayCounter dc = new Actual360();
        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.005)), dc,
                Compounding.Continuous, Frequency.Annual);
        final YieldTermStructure flatQ = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.03)), dc,
                Compounding.Continuous, Frequency.Annual);
        final HestonProcess hp = new HestonProcess(
                new Handle<YieldTermStructure>(flatR),
                new Handle<YieldTermStructure>(flatQ),
                new Handle<Quote>(new SimpleQuote(100.0)),
                0.09, 11.35, 0.022, 0.618, -0.5);
        final AnalyticHestonForwardEuropeanEngine engine =
                new AnalyticHestonForwardEuropeanEngine(hp);

        // 6-month reset, three sample variance values
        final double tReset = 0.5;
        for (final double v : new double[] { 0.005, 0.05, 0.5 }) {
            final double p = engine.propagator(tReset, v);
            assertTrue("propagator positive at v=" + v + ": " + p, p > 0.0);
            assertTrue("propagator finite at v=" + v + ": " + p, !Double.isNaN(p) && !Double.isInfinite(p));
        }
    }
}

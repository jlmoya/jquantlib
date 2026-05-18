/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.GapPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticDigitalAmericanEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticDigitalAmericanKOEngine;
import org.jquantlib.pricingengines.vanilla.MCDigitalEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5i skeleton port of {@code test-suite/digitaloption.cpp} v1.42.1.
 *
 * <p>Phase 5e.5b-CFC-d-53: 4 American value-validation cases body-filled
 * and un-ignored (cash/asset, at-hit/at-expiry, knock-in/knock-out).
 */
public class DigitalOptionTest {

    private static final String REASON_GREEKS =
            "Phase 5i.5 — requires Greeks bumping harness for digital payoffs";

    private static final class DigitalOptionData {
        final Option.Type type;
        final double strike, s, q, r, t, v, result, tol;
        final boolean knockin;

        DigitalOptionData(final Option.Type type, final double strike,
                          final double s, final double q, final double r,
                          final double t, final double v, final double result,
                          final double tol, final boolean knockin) {
            this.type = type; this.strike = strike; this.s = s; this.q = q;
            this.r = r; this.t = t; this.v = v; this.result = result;
            this.tol = tol; this.knockin = knockin;
        }
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    @Test
    public void testCashOrNothingEuropeanValues() {
        QL.info("Testing European cash-or-nothing digital option...");
        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Put, 80.00, 100.0, 0.06, 0.06, 0.75, 0.35, 2.6710, 1e-4, true)
        };
        runEuropeanCashOrNothing(values, 10.0);
    }

    @Test
    public void testAssetOrNothingEuropeanValues() {
        QL.info("Testing European asset-or-nothing digital option...");
        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Put, 65.00, 70.0, 0.05, 0.07, 0.50, 0.27, 20.2069, 1e-4, true)
        };
        runEuropeanAssetOrNothing(values);
    }

    @Test
    public void testGapEuropeanValues() {
        QL.info("Testing European gap digital option...");
        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Call, 50.00, 50.0, 0.00, 0.09, 0.50, 0.20, -0.0053, 1e-4, true)
        };
        runEuropeanGap(values, 57.00);
    }

    private void runEuropeanCashOrNothing(final DigitalOptionData[] values, final double cashPayoff) {
        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();
        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);
        for (final DigitalOptionData value : values) {
            final StrikedTypePayoff payoff = new CashOrNothingPayoff(value.type, value.strike, cashPayoff);
            checkEuropeanValue(payoff, value, today, spot, qRate, rRate, vol, qTS, rTS, volTS);
        }
    }

    private void runEuropeanAssetOrNothing(final DigitalOptionData[] values) {
        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();
        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);
        for (final DigitalOptionData value : values) {
            final StrikedTypePayoff payoff = new AssetOrNothingPayoff(value.type, value.strike);
            checkEuropeanValue(payoff, value, today, spot, qRate, rRate, vol, qTS, rTS, volTS);
        }
    }

    private void runEuropeanGap(final DigitalOptionData[] values, final double secondStrike) {
        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();
        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);
        for (final DigitalOptionData value : values) {
            final StrikedTypePayoff payoff = new GapPayoff(value.type, value.strike, secondStrike);
            checkEuropeanValue(payoff, value, today, spot, qRate, rRate, vol, qTS, rTS, volTS);
        }
    }

    private void checkEuropeanValue(final StrikedTypePayoff payoff, final DigitalOptionData value,
            final Date today, final SimpleQuote spot, final SimpleQuote qRate,
            final SimpleQuote rRate, final SimpleQuote vol,
            final YieldTermStructure qTS, final YieldTermStructure rTS,
            final BlackVolTermStructure volTS) {

        final Date exDate = today.add(timeToDays(value.t));
        final Exercise exercise = new EuropeanExercise(exDate);

        spot.setValue(value.s);
        qRate.setValue(value.q);
        rRate.setValue(value.r);
        vol.setValue(value.v);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final PricingEngine engine = new AnalyticEuropeanEngine(stochProcess);
        final VanillaOption opt = new VanillaOption(payoff, exercise);
        opt.setPricingEngine(engine);

        final double calculated = opt.NPV();
        final double error = Math.abs(calculated - value.result);
        if (error > value.tol) {
            fail(exercise + " " + payoff.optionType() + " expected=" + value.result
                    + " calculated=" + calculated + " error=" + error + " tol=" + value.tol);
        }
    }

    @Test
    public void testCashAtHitOrNothingAmericanValues() {
        QL.info("Testing American cash-(at-hit)-or-nothing digital option...");
        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.00, 0.10, 0.5, 0.20,  9.7264, 1e-4,  true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.00, 0.10, 0.5, 0.20, 11.6553, 1e-4,  true),
            new DigitalOptionData(Option.Type.Call, 100.00, 105.00, 0.00, 0.10, 0.5, 0.20, 15.0000, 1e-16, true),
            new DigitalOptionData(Option.Type.Put,  100.00,  95.00, 0.00, 0.10, 0.5, 0.20, 15.0000, 1e-16, true),
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.20, 0.10, 0.5, 0.20, 12.2715, 1e-4,  true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.20, 0.10, 0.5, 0.20,  8.9109, 1e-4,  true),
            new DigitalOptionData(Option.Type.Call, 100.00, 105.00, 0.20, 0.10, 0.5, 0.20, 15.0000, 1e-16, true),
            new DigitalOptionData(Option.Type.Put,  100.00,  95.00, 0.20, 0.10, 0.5, 0.20, 15.0000, 1e-16, true)
        };
        runAmerican(values, 15.00, true, false);
    }

    @Test
    public void testAssetAtHitOrNothingAmericanValues() {
        QL.info("Testing American asset-(at-hit)-or-nothing digital option...");
        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.00, 0.10, 0.5, 0.20, 64.8426, 1e-04, true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.00, 0.10, 0.5, 0.20, 77.7017, 1e-04, true),
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.01, 0.10, 0.5, 0.20, 65.7811, 1e-04, true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.01, 0.10, 0.5, 0.20, 76.8858, 1e-04, true),
            new DigitalOptionData(Option.Type.Call, 100.00, 105.00, 0.00, 0.10, 0.5, 0.20, 105.0000, 1e-16, true),
            new DigitalOptionData(Option.Type.Put,  100.00,  95.00, 0.00, 0.10, 0.5, 0.20,  95.0000, 1e-16, true),
            new DigitalOptionData(Option.Type.Call, 100.00, 105.00, 0.01, 0.10, 0.5, 0.20, 105.0000, 1e-16, true),
            new DigitalOptionData(Option.Type.Put,  100.00,  95.00, 0.01, 0.10, 0.5, 0.20,  95.0000, 1e-16, true)
        };
        runAmerican(values, 0.0, false, false);
    }

    @Test
    public void testCashAtExpiryOrNothingAmericanValues() {
        QL.info("Testing American cash-(at-expiry)-or-nothing digital option...");
        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.00, 0.10, 0.5, 0.20,  9.3604, 1e-4, true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.00, 0.10, 0.5, 0.20, 11.2223, 1e-4, true),
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.00, 0.10, 0.5, 0.20,  4.9081, 1e-4, false),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.00, 0.10, 0.5, 0.20,  3.0461, 1e-4, false),
            new DigitalOptionData(Option.Type.Call, 100.00, 105.00, 0.00, 0.10, 0.5, 0.20, 15.0000 * Math.exp(-0.05), 1e-12, true),
            new DigitalOptionData(Option.Type.Put,  100.00,  95.00, 0.00, 0.10, 0.5, 0.20, 15.0000 * Math.exp(-0.05), 1e-12, true),
            new DigitalOptionData(Option.Type.Call,   2.37,   2.33, 0.07, 0.43, 0.19, 0.005,  0.0000, 1e-4, false)
        };
        runAmerican(values, 15.0, true, true);
    }

    @Test
    public void testAssetAtExpiryOrNothingAmericanValues() {
        QL.info("Testing American asset-(at-expiry)-or-nothing digital option...");
        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.00, 0.10, 0.5, 0.20, 64.8426, 1e-04, true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.00, 0.10, 0.5, 0.20, 77.7017, 1e-04, true),
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.00, 0.10, 0.5, 0.20, 40.1574, 1e-04, false),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.00, 0.10, 0.5, 0.20, 17.2983, 1e-04, false),
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.01, 0.10, 0.5, 0.20, 65.5291, 1e-04, true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.01, 0.10, 0.5, 0.20, 76.5951, 1e-04, true),
            new DigitalOptionData(Option.Type.Call, 100.00, 105.00, 0.00, 0.10, 0.5, 0.20, 105.0000, 1e-12, true),
            new DigitalOptionData(Option.Type.Put,  100.00,  95.00, 0.00, 0.10, 0.5, 0.20,  95.0000, 1e-12, true),
            new DigitalOptionData(Option.Type.Call, 100.00, 105.00, 0.01, 0.10, 0.5, 0.20, 105.0000 * Math.exp(-0.005), 1e-12, true),
            new DigitalOptionData(Option.Type.Put,  100.00,  95.00, 0.01, 0.10, 0.5, 0.20,  95.0000 * Math.exp(-0.005), 1e-12, true)
        };
        runAmerican(values, 0.0, false, true);
    }

    private void runAmerican(final DigitalOptionData[] values, final double cashPayoff,
                             final boolean useCashOrNothing, final boolean payoffAtExpiry) {
        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.01);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.25);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        for (final DigitalOptionData value : values) {
            final StrikedTypePayoff payoff = useCashOrNothing
                ? new CashOrNothingPayoff(value.type, value.strike, cashPayoff)
                : new AssetOrNothingPayoff(value.type, value.strike);

            final Date exDate = today.add(timeToDays(value.t));
            final Exercise amExercise = new AmericanExercise(today, exDate, payoffAtExpiry);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine engine = value.knockin
                ? new AnalyticDigitalAmericanEngine(stochProcess)
                : new AnalyticDigitalAmericanKOEngine(stochProcess);

            final VanillaOption opt = new VanillaOption(payoff, amExercise);
            opt.setPricingEngine(engine);

            final double calculated = opt.NPV();
            final double error = Math.abs(calculated - value.result);
            if (error > value.tol) {
                fail(amExercise + " " + payoff.optionType() + " " + payoff
                        + " spot=" + value.s + " strike=" + payoff.strike()
                        + " q=" + value.q + " r=" + value.r + " t=" + value.t
                        + " v=" + value.v + " expected=" + value.result
                        + " calculated=" + calculated + " error=" + error
                        + " tol=" + value.tol + " knock_in=" + value.knockin);
            }
        }
    }

    @Ignore(REASON_GREEKS + " — cash-at-hit American")
    @Test
    public void testCashAtHitOrNothingAmericanGreeks() { fail("not implemented"); }

    /**
     * Java port of {@code test-suite/digitaloption.cpp::testMCCashAtHit}
     * (Phase 5e.5b-CFC-d-181). The C++ test uses LowDiscrepancy (Sobol)
     * + 16383 samples; this Java MC infrastructure is currently
     * specialised to PseudoRandom (Mersenne-Twister), so we use 524287
     * samples to keep the empirical absolute error within the C++
     * tolerance of {@code 1e-2}.
     */
    @Test
    public void testMCCashAtHit() {
        QL.info("Testing Monte Carlo cash-(at-hit)-or-nothing American engine...");

        final DigitalOptionData[] values = {
            new DigitalOptionData(Option.Type.Put,  100.00, 105.00, 0.20, 0.10, 0.5, 0.20, 12.2715, 1e-2, true),
            new DigitalOptionData(Option.Type.Call, 100.00,  95.00, 0.20, 0.10, 0.5, 0.20,  8.9109, 1e-2, true)
        };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final int timeStepsPerYear = 90;
        final int maxSamples = 1_000_000;
        final long seed = 1L;
        // Sobol parity needs ~2^14-1 samples; MT needs ~2^19-1 for the
        // same 1e-2 absolute tolerance. Both probe-validated empirically.
        final int requiredSamples = (1 << 19) - 1; // 524287

        for (final DigitalOptionData value : values) {
            final StrikedTypePayoff payoff = new CashOrNothingPayoff(value.type, value.strike, 15.0);
            final Date exDate = today.add(timeToDays(value.t));
            final Exercise amExercise = new AmericanExercise(today, exDate);

            spot.setValue(value.s);
            qRate.setValue(value.q);
            rRate.setValue(value.r);
            vol.setValue(value.v);

            final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                    new Handle<Quote>(spot),
                    new Handle<YieldTermStructure>(qTS),
                    new Handle<YieldTermStructure>(rTS),
                    new Handle<BlackVolTermStructure>(volTS));

            final PricingEngine mcEngine = new MCDigitalEngine(
                    stochProcess,
                    /* timeSteps */ McSimulation.NULL_SAMPLES,
                    /* timeStepsPerYear */ timeStepsPerYear,
                    /* brownianBridge */ true,
                    /* antitheticVariate */ false,
                    requiredSamples,
                    /* requiredTolerance */ McSimulation.NULL_TOLERANCE,
                    maxSamples,
                    seed);

            final VanillaOption opt = new VanillaOption(payoff, amExercise);
            opt.setPricingEngine(mcEngine);

            final double calculated = opt.NPV();
            final double error = Math.abs(calculated - value.result);
            if (error > value.tol) {
                fail(amExercise + " " + payoff.optionType() + " " + payoff
                        + " spot=" + value.s + " strike=" + payoff.strike()
                        + " q=" + value.q + " r=" + value.r + " t=" + value.t
                        + " v=" + value.v + " expected=" + value.result
                        + " calculated=" + calculated + " error=" + error
                        + " tol=" + value.tol);
            }
        }
    }
}

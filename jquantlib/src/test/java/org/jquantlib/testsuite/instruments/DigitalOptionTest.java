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
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AssetOrNothingPayoff;
import org.jquantlib.instruments.CashOrNothingPayoff;
import org.jquantlib.instruments.GapPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
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
 * Phase 5i skeleton port of {@code test-suite/digitaloption.cpp} v1.42.1
 * (733 LOC, 8 cases).
 *
 * <p>Exercises European and American digital option pricing with
 * cash-or-nothing, asset-or-nothing, and gap payoffs, plus the MC
 * cash-at-hit American engine.
 *
 * <p><strong>Phase Body-Fill (2026-05-09)</strong> — the 3 European cases
 * (cash-or-nothing, asset-or-nothing, gap) are now body-filled and
 * un-ignored.  They exercise the {@link AnalyticEuropeanEngine}
 * digital-payoff branch end-to-end against Haug 1998 reference values.
 *
 * <p>The remaining 6 cases stay deferred to Phase 5i.5:
 * <ul>
 *   <li>American at-hit / at-expiry tests need
 *       {@code AnalyticDigitalAmericanEngine} (no Java port yet);
 *   <li>{@code MCDigitalEngine} is not yet ported;
 *   <li>The Greeks numerical-derivative cross-check requires bumping
 *       infrastructure that exists for vanilla but is not wired for the
 *       digital payoff hierarchy.
 * </ul>
 *
 * <p>Source: {@code test-suite/digitaloption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class DigitalOptionTest {

    private static final String REASON_AMERICAN_AT_HIT =
            "Phase 5i.5 — requires AnalyticDigitalAmericanEngine "
          + "(at-hit branch) + reference-value cross-validation";

    private static final String REASON_AMERICAN_AT_EXPIRY =
            "Phase 5i.5 — requires AnalyticDigitalAmericanEngine "
          + "(at-expiry branch via AmericanPayoffAtExpiry helper) + "
          + "reference-value cross-validation";

    private static final String REASON_GREEKS =
            "Phase 5i.5 — requires Greeks bumping harness wired for the "
          + "digital payoff hierarchy (vanilla harness exists; digital not "
          + "yet adapted)";

    private static final String REASON_MC =
            "Phase 5i.5 — requires MCDigitalEngine port (path-dependent "
          + "cash-at-hit MC engine; no Java equivalent yet)";

    private static final class DigitalOptionData {
        final Option.Type type;
        final double strike;
        final double s;        // spot
        final double q;        // dividend
        final double r;        // risk-free rate
        final double t;        // time to maturity
        final double v;        // volatility
        final double result;   // expected result
        final double tol;      // tolerance
        final boolean knockin; // true if knock-in

        DigitalOptionData(final Option.Type type,
                          final double strike, final double s,
                          final double q, final double r,
                          final double t, final double v,
                          final double result, final double tol,
                          final boolean knockin) {
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
            this.knockin = knockin;
        }
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    @Test
    public void testCashOrNothingEuropeanValues() {
        QL.info("Testing European cash-or-nothing digital option...");

        // "Option pricing formulas", E.G. Haug, McGraw-Hill 1998 - pag 88
        final DigitalOptionData[] values = {
            //          type, strike,  spot,    q,    r,    t,  vol,  value, tol
            new DigitalOptionData(Option.Type.Put, 80.00, 100.0,
                                  0.06, 0.06, 0.75, 0.35, 2.6710, 1e-4, true)
        };

        runEuropeanCashOrNothing(values, 10.0);
    }

    @Test
    public void testAssetOrNothingEuropeanValues() {
        QL.info("Testing European asset-or-nothing digital option...");

        // "Option pricing formulas", E.G. Haug, McGraw-Hill 1998 - pag 90
        final DigitalOptionData[] values = {
            //          type, strike, spot,    q,    r,    t,  vol,   value, tol
            new DigitalOptionData(Option.Type.Put, 65.00, 70.0,
                                  0.05, 0.07, 0.50, 0.27, 20.2069, 1e-4, true)
        };

        runEuropeanAssetOrNothing(values);
    }

    @Test
    public void testGapEuropeanValues() {
        QL.info("Testing European gap digital option...");

        // "Option pricing formulas", E.G. Haug, McGraw-Hill 1998 - pag 88
        final DigitalOptionData[] values = {
            //          type, strike, spot,    q,    r,    t,  vol,   value, tol
            new DigitalOptionData(Option.Type.Call, 50.00, 50.0,
                                  0.00, 0.09, 0.50, 0.20, -0.0053, 1e-4, true)
        };

        runEuropeanGap(values, 57.00);
    }

    private void runEuropeanCashOrNothing(final DigitalOptionData[] values,
                                          final double cashPayoff) {
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
            final StrikedTypePayoff payoff =
                    new CashOrNothingPayoff(value.type, value.strike, cashPayoff);
            checkValue(payoff, value, today, spot, qRate, rRate, vol,
                       qTS, rTS, volTS);
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
            final StrikedTypePayoff payoff =
                    new AssetOrNothingPayoff(value.type, value.strike);
            checkValue(payoff, value, today, spot, qRate, rRate, vol,
                       qTS, rTS, volTS);
        }
    }

    private void runEuropeanGap(final DigitalOptionData[] values,
                                final double secondStrike) {
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
            final StrikedTypePayoff payoff =
                    new GapPayoff(value.type, value.strike, secondStrike);
            checkValue(payoff, value, today, spot, qRate, rRate, vol,
                       qTS, rTS, volTS);
        }
    }

    private void checkValue(final StrikedTypePayoff payoff,
                            final DigitalOptionData value,
                            final Date today,
                            final SimpleQuote spot, final SimpleQuote qRate,
                            final SimpleQuote rRate, final SimpleQuote vol,
                            final YieldTermStructure qTS,
                            final YieldTermStructure rTS,
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
            fail(exercise + " " + payoff.optionType() + " option with " + payoff + " payoff:\n"
                    + "    spot value:       " + value.s + "\n"
                    + "    strike:           " + payoff.strike() + "\n"
                    + "    dividend yield:   " + value.q + "\n"
                    + "    risk-free rate:   " + value.r + "\n"
                    + "    reference date:   " + today + "\n"
                    + "    maturity:         " + value.t + "\n"
                    + "    volatility:       " + value.v + "\n\n"
                    + "    expected:         " + value.result + "\n"
                    + "    calculated:       " + calculated + "\n"
                    + "    error:            " + error + "\n"
                    + "    tolerance:        " + value.tol + "\n"
                    + "    knock_in:         " + value.knockin);
        }
    }

    @Ignore(REASON_AMERICAN_AT_HIT + " — cash-at-hit")
    @Test
    public void testCashAtHitOrNothingAmericanValues() { fail("not implemented"); }

    @Ignore(REASON_AMERICAN_AT_HIT + " — asset-at-hit")
    @Test
    public void testAssetAtHitOrNothingAmericanValues() { fail("not implemented"); }

    @Ignore(REASON_AMERICAN_AT_EXPIRY + " — cash-at-expiry")
    @Test
    public void testCashAtExpiryOrNothingAmericanValues() { fail("not implemented"); }

    @Ignore(REASON_AMERICAN_AT_EXPIRY + " — asset-at-expiry")
    @Test
    public void testAssetAtExpiryOrNothingAmericanValues() { fail("not implemented"); }

    @Ignore(REASON_GREEKS + " — cash-at-hit American")
    @Test
    public void testCashAtHitOrNothingAmericanGreeks() { fail("not implemented"); }

    @Ignore(REASON_MC)
    @Test
    public void testMCCashAtHit() { fail("not implemented"); }
}

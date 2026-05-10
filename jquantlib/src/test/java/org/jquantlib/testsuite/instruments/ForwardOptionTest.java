/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.ForwardVanillaOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.forward.ForwardPerformanceVanillaEngine;
import org.jquantlib.pricingengines.forward.ForwardVanillaEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase Body-Fill-5 port of {@code test-suite/forwardoption.cpp} v1.42.1
 * (805 LOC, 7 cases).
 *
 * <p>Exercises forward-start vanilla options under both the standard
 * Black-Scholes-driven analytic engine ({@code ForwardEngine} /
 * {@code ForwardPerformanceEngine}) and the Heston-driven analytic and
 * MC engines.
 *
 * <p><strong>Body-fills (Phase Body-Fill-5):</strong>
 * <ul>
 *   <li>{@link #testValues()} — Haug pag.37 reference values, BS analytic.
 *   <li>{@link #testPerformanceValues()} — Haug pag.37 performance variant.
 * </ul>
 *
 * <p><strong>Carry-forward to Phase 5i.5</strong>:
 * <ul>
 *   <li>{@code testGreeks} / {@code testPerformanceGreeks} — needs Greeks-perturbation
 *       harness (numerical-derivative cross-check). Tractable but bulky.
 *   <li>{@code testGreeksInitialization} — needs binomial inner engine
 *       (TestBinomialEngine adaptor not yet present in Java).
 *   <li>{@code testMCPrices} / {@code testHestonMCPrices} —
 *       {@code MCForwardEuropeanBSEngine} and {@code MCForwardEuropeanHestonEngine}
 *       not yet ported.
 *   <li>{@code testHestonAnalyticalVsMCPrices} — Heston engine sits under
 *       experimental.forward and has its own dedicated
 *       AnalyticHestonForwardEuropeanEngineTest.
 * </ul>
 *
 * <p>Source: {@code test-suite/forwardoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ForwardOptionTest {

    public ForwardOptionTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_GREEKS =
            "Phase 5i.5: ForwardVanillaEngine + ForwardPerformanceVanillaEngine ported; "
          + "Greeks numerical-derivative harness still needs body fill from C++ "
          + "forwardoption.cpp::testForwardGreeks (bulky perturbation matrix).";

    private static final String REASON_GREEKS_INIT =
            "Phase 5i.5: needs a Java BinomialVanillaEngine adapter (CoxRossRubinstein, "
          + "fixed steps) for the Greeks-initialization regression — adapter not yet ported.";

    private static final String REASON_MC_BS =
            "Phase 5i.5 — requires MCForwardEuropeanBSEngine port "
          + "(no Java equivalent yet)";

    private static final String REASON_MC_HESTON =
            "Phase 5i.5 — requires MCForwardEuropeanHestonEngine port "
          + "(no Java equivalent yet)";

    private static final String REASON_ANALYTIC_HESTON =
            "Phase 5i.5 — AnalyticHestonForwardEuropeanEngine exists under "
          + "experimental.forward and has dedicated coverage there; "
          + "in-instruments-package wrapper test deferred";

    /**
     * Forward-option value test data — port of the {@code ForwardOptionData}
     * struct in C++ {@code forwardoption.cpp::testValues}, Haug pag.37 +
     * VBA companion code.
     */
    private static final class ForwardOptionData {
        final Option.Type type;
        final double moneyness;
        final double s;     // spot
        final double q;     // dividend yield
        final double r;     // risk-free rate
        final double start; // time to reset (years)
        final double t;     // time to maturity (years)
        final double v;     // volatility
        final double result;
        final double tol;

        ForwardOptionData(final Option.Type type, final double moneyness,
                          final double s, final double q, final double r,
                          final double start, final double t, final double v,
                          final double result, final double tol) {
            this.type = type;
            this.moneyness = moneyness;
            this.s = s;
            this.q = q;
            this.r = r;
            this.start = start;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    /** Convert a year-fraction (under Actual/360) into days. */
    private static int timeToDays(final double t) {
        return (int) (t * 360.0 + 0.5);
    }

    /**
     * Port of C++ {@code forwardoption.cpp::testValues}.
     *
     * <p>"The data below are from Option pricing formulas, E.G. Haug,
     * McGraw-Hill 1998" (pag.37 + VBA companion code).
     */
    @Test
    public void testValues() {
        final ForwardOptionData[] values = new ForwardOptionData[] {
            // type, moneyness, spot, div, rate, start, t, vol, result, tol
            new ForwardOptionData(Option.Type.Call, 1.1, 60.0, 0.04, 0.08, 0.25, 1.0, 0.30, 4.4064, 1.0e-4),
            new ForwardOptionData(Option.Type.Put,  1.1, 60.0, 0.04, 0.08, 0.25, 1.0, 0.30, 8.2971, 1.0e-4)
        };

        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote vol   = new SimpleQuote(0.0);

        final Handle<? extends Quote> spotH = new Handle<SimpleQuote>(spot);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(rRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Calendar cal = new NullCalendar();
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, new Handle<Quote>(vol), dc));

        final GeneralizedBlackScholesProcess process =
                new GeneralizedBlackScholesProcess(spotH, qTS, rTS, volTS);
        final ForwardVanillaEngine engine = new ForwardVanillaEngine(process);

        for (final ForwardOptionData v : values) {
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(v.type, 0.0);
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new EuropeanExercise(exDate);
            final Date reset = today.add(timeToDays(v.start));

            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);

            final ForwardVanillaOption option =
                    new ForwardVanillaOption(v.moneyness, reset, payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);
            final double tolerance = 1.0e-4;
            assertTrue("forward " + v.type + " value: expected=" + v.result
                    + " calculated=" + calculated
                    + " error=" + error + " tolerance=" + tolerance,
                    error <= tolerance);
        }
    }

    /**
     * Port of C++ {@code forwardoption.cpp::testPerformanceValues}.
     *
     * <p>"The data below are the performance equivalent of the forward
     * options tested above" — same Haug values rescaled
     * {@code result/spot * exp(-q*start)} for the performance-variant
     * payoff {@code (S_T - moneyness*S_reset) / S_reset * df}.
     */
    @Test
    public void testPerformanceValues() {
        final ForwardOptionData[] values = new ForwardOptionData[] {
            new ForwardOptionData(Option.Type.Call, 1.1, 60.0, 0.04, 0.08, 0.25, 1.0, 0.30,
                    4.4064 / 60.0 * Math.exp(-0.04 * 0.25), 1.0e-4),
            new ForwardOptionData(Option.Type.Put,  1.1, 60.0, 0.04, 0.08, 0.25, 1.0, 0.30,
                    8.2971 / 60.0 * Math.exp(-0.04 * 0.25), 1.0e-4)
        };

        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote vol   = new SimpleQuote(0.0);

        final Handle<? extends Quote> spotH = new Handle<SimpleQuote>(spot);
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(qRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<Quote>(rRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Calendar cal = new NullCalendar();
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, new Handle<Quote>(vol), dc));

        final GeneralizedBlackScholesProcess process =
                new GeneralizedBlackScholesProcess(spotH, qTS, rTS, volTS);
        final ForwardPerformanceVanillaEngine engine =
                new ForwardPerformanceVanillaEngine(process);

        for (final ForwardOptionData v : values) {
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(v.type, 0.0);
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new EuropeanExercise(exDate);
            final Date reset = today.add(timeToDays(v.start));

            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);

            final ForwardVanillaOption option =
                    new ForwardVanillaOption(v.moneyness, reset, payoff, exercise);
            option.setPricingEngine(engine);

            final double calculated = option.NPV();
            final double error = Math.abs(calculated - v.result);
            final double tolerance = 1.0e-4;
            assertTrue("forward performance " + v.type + " value: expected=" + v.result
                    + " calculated=" + calculated
                    + " error=" + error + " tolerance=" + tolerance,
                    error <= tolerance);
        }
    }

    @Ignore(REASON_GREEKS)
    @Test
    public void testGreeks() { fail("not implemented"); }

    @Ignore(REASON_GREEKS + " — performance-style variant")
    @Test
    public void testPerformanceGreeks() { fail("not implemented"); }

    @Ignore(REASON_GREEKS_INIT)
    @Test
    public void testGreeksInitialization() { fail("not implemented"); }

    @Ignore(REASON_MC_BS)
    @Test
    public void testMCPrices() { fail("not implemented"); }

    @Ignore(REASON_MC_HESTON + " — MC vs Heston-analytic cross-check")
    @Test
    public void testHestonMCPrices() { fail("not implemented"); }

    @Ignore(REASON_ANALYTIC_HESTON + " + " + REASON_MC_HESTON)
    @Test
    public void testHestonAnalyticalVsMCPrices() { fail("not implemented"); }
}

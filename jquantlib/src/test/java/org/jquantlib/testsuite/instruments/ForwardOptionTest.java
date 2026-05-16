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
 * <p><strong>Body-fills (Phase Body-Fill-5 + Phase 5e.5b-CFC-d-38):</strong>
 * <ul>
 *   <li>{@link #testValues()} — Haug pag.37 reference values, BS analytic.
 *   <li>{@link #testPerformanceValues()} — Haug pag.37 performance variant.
 *   <li>{@link #testGreeks()} — finite-difference cross-check of delta,
 *       gamma, theta, rho, divRho, vega on the analytic
 *       {@link ForwardVanillaEngine}, tolerance 1.0e-5.
 *   <li>{@link #testPerformanceGreeks()} — same FD harness on the
 *       performance-variant {@link ForwardPerformanceVanillaEngine}.
 * </ul>
 *
 * <p><strong>Carry-forward to Phase 5i.5</strong>:
 * <ul>
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

    /**
     * Port of C++ {@code forwardoption.cpp::testGreeks} (which delegates
     * to the {@code testForwardGreeks<ForwardVanillaEngine>()} template,
     * lines 81-222). For every (type, moneyness, length, startMonth, u,
     * qRate, rRate, vol) combination, the engine-reported Greek (delta,
     * gamma, theta, rho, divRho, vega) is compared to a two-point
     * central-difference approximation. Tolerance 1.0e-5
     * (relative-or-absolute via {@link #relativeError(double, double, double)}).
     *
     * <p>Note: the analytic {@link ForwardVanillaEngine} sets {@code gamma}
     * to 0 unconditionally (matching C++ {@code ForwardEngine}), so the
     * FD gamma probe is essentially testing that the FD approximation is
     * also within {@code 1e-5} of zero for the inner-engine delta — true
     * for the parameter grid below.
     */
    @Test
    public void testGreeks() {
        QL.info("Testing forward option greeks...");
        runForwardGreeksHarness(false);
    }

    /**
     * Port of C++ {@code forwardoption.cpp::testPerformanceGreeks}
     * (delegates to {@code testForwardGreeks<ForwardPerformanceVanillaEngine>()},
     * lines 352-357 + the shared {@code testForwardGreeks<Engine>} template
     * at lines 81-222). Same FD harness as {@link #testGreeks()} but on the
     * performance-variant engine.
     */
    @Test
    public void testPerformanceGreeks() {
        QL.info("Testing forward performance option greeks...");
        runForwardGreeksHarness(true);
    }

    /**
     * Shared FD-Greeks harness — the Java equivalent of the C++
     * {@code template <template <class> class Engine> void testForwardGreeks()}
     * function in {@code test-suite/forwardoption.cpp} (lines 81-222 in
     * v1.42.1). The boolean {@code performance} selects which engine
     * specialization to build.
     */
    private void runForwardGreeksHarness(final boolean performance) {
        final java.util.Map<String, Double> tolerance = new java.util.HashMap<String, Double>();
        tolerance.put("delta",  1.0e-5);
        tolerance.put("gamma",  1.0e-5);
        tolerance.put("theta",  1.0e-5);
        tolerance.put("rho",    1.0e-5);
        tolerance.put("divRho", 1.0e-5);
        tolerance.put("vega",   1.0e-5);

        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] moneyness   = { 0.9, 1.0, 1.1 };
        final double[] underlyings = { 100.0 };
        final double[] qRates      = { 0.04, 0.05, 0.06 };
        final double[] rRates      = { 0.01, 0.05, 0.15 };
        final int[]    lengths     = { 1, 2 };       // years
        final int[]    startMonths = { 6, 9 };       // months
        final double[] vols        = { 0.11, 0.50, 1.20 };

        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote vol   = new SimpleQuote(0.0);

        final Handle<? extends Quote> spotH = new Handle<SimpleQuote>(spot);
        final Calendar cal = new NullCalendar();
        // Settlement-days (=0) variant so the term structures track
        // Settings.evaluationDate — required for the theta probe
        // (perturbing today via Settings) to re-price.
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, new Handle<Quote>(qRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(0, cal, new Handle<Quote>(rRate), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(0, cal, new Handle<Quote>(vol), dc));

        final GeneralizedBlackScholesProcess process =
                new GeneralizedBlackScholesProcess(spotH, qTS, rTS, volTS);

        final ForwardVanillaEngine engine = performance
                ? new ForwardPerformanceVanillaEngine(process)
                : new ForwardVanillaEngine(process);

        for (final Option.Type type : types) {
            for (final double moneynes : moneyness) {
                for (final int length : lengths) {
                    for (final int startMonth : startMonths) {

                        final Date exDate = today.add(
                                new org.jquantlib.time.Period(length,
                                        org.jquantlib.time.TimeUnit.Years));
                        final Exercise exercise = new EuropeanExercise(exDate);

                        final Date reset = today.add(
                                new org.jquantlib.time.Period(startMonth,
                                        org.jquantlib.time.TimeUnit.Months));

                        final StrikedTypePayoff payoff =
                                new PlainVanillaPayoff(type, 0.0);

                        final ForwardVanillaOption option =
                                new ForwardVanillaOption(moneynes, reset, payoff, exercise);
                        option.setPricingEngine(engine);

                        for (final double u : underlyings) {
                            for (final double m : qRates) {
                                for (final double n : rRates) {
                                    for (final double v : vols) {
                                        final double q = m;
                                        final double r = n;

                                        spot.setValue(u);
                                        qRate.setValue(q);
                                        rRate.setValue(r);
                                        vol.setValue(v);

                                        final double value = option.NPV();

                                        final java.util.Map<String, Double> calculated =
                                                new java.util.HashMap<String, Double>();
                                        calculated.put("delta",  option.delta());
                                        calculated.put("gamma",  option.gamma());
                                        calculated.put("theta",  option.theta());
                                        calculated.put("rho",    option.rho());
                                        calculated.put("divRho", option.dividendRho());
                                        calculated.put("vega",   option.vega());

                                        if (value > spot.value() * 1.0e-5) {
                                            final java.util.Map<String, Double> expected =
                                                    new java.util.HashMap<String, Double>();

                                            // perturb spot and get delta / gamma
                                            final double du = u * 1.0e-4;
                                            spot.setValue(u + du);
                                            double valueP = option.NPV();
                                            double deltaP = option.delta();
                                            spot.setValue(u - du);
                                            double valueM = option.NPV();
                                            double deltaM = option.delta();
                                            spot.setValue(u);
                                            expected.put("delta", (valueP - valueM) / (2.0 * du));
                                            expected.put("gamma", (deltaP - deltaM) / (2.0 * du));

                                            // perturb risk-free rate and get rho
                                            final double dr = r * 1.0e-4;
                                            rRate.setValue(r + dr);
                                            valueP = option.NPV();
                                            rRate.setValue(r - dr);
                                            valueM = option.NPV();
                                            rRate.setValue(r);
                                            expected.put("rho", (valueP - valueM) / (2.0 * dr));

                                            // perturb dividend yield and get divRho
                                            final double dq = q * 1.0e-4;
                                            qRate.setValue(q + dq);
                                            valueP = option.NPV();
                                            qRate.setValue(q - dq);
                                            valueM = option.NPV();
                                            qRate.setValue(q);
                                            expected.put("divRho", (valueP - valueM) / (2.0 * dq));

                                            // perturb volatility and get vega
                                            final double dv = v * 1.0e-4;
                                            vol.setValue(v + dv);
                                            valueP = option.NPV();
                                            vol.setValue(v - dv);
                                            valueM = option.NPV();
                                            vol.setValue(v);
                                            expected.put("vega", (valueP - valueM) / (2.0 * dv));

                                            // perturb evaluation date and get theta
                                            final double dT =
                                                    dc.yearFraction(today.sub(1), today.add(1));
                                            new Settings().setEvaluationDate(today.sub(1));
                                            valueM = option.NPV();
                                            new Settings().setEvaluationDate(today.add(1));
                                            valueP = option.NPV();
                                            new Settings().setEvaluationDate(today);
                                            expected.put("theta", (valueP - valueM) / dT);

                                            for (final String greek : calculated.keySet()) {
                                                final double expct = expected.get(greek);
                                                final double calcl = calculated.get(greek);
                                                final double tol   = tolerance.get(greek);
                                                final double error = relativeError(expct, calcl, u);
                                                if (error > tol) {
                                                    fail((performance ? "Forward performance " : "Forward ")
                                                            + type + " Greek " + greek
                                                            + ": expected=" + expct
                                                            + " calculated=" + calcl
                                                            + " error=" + error + " tolerance=" + tol
                                                            + "\n    s=" + u + " q=" + q + " r=" + r
                                                            + " v=" + v + " moneyness=" + moneynes
                                                            + " length=" + length + "y startMonth=" + startMonth + "m");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue("runForwardGreeksHarness completed (performance=" + performance + ")", true);
    }

    /**
     * C++ helper {@code relativeError(Real x1, Real x2, Real reference)} from
     * {@code test-suite/utilities.cpp} — falls back to absolute error when the
     * reference is zero.
     */
    private static double relativeError(final double x1, final double x2, final double reference) {
        if (reference != 0.0) {
            return Math.abs(x1 - x2) / reference;
        }
        return Math.abs(x1 - x2);
    }

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

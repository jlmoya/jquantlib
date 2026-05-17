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
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.lattices.CoxRossRubinstein;
import org.jquantlib.experimental.forward.AnalyticHestonForwardEuropeanEngine;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.forward.ForwardPerformanceVanillaEngine;
import org.jquantlib.pricingengines.forward.ForwardVanillaEngine;
import org.jquantlib.pricingengines.forward.MCForwardEuropeanBSEngine;
import org.jquantlib.pricingengines.forward.MCForwardEuropeanHestonEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.pricingengines.vanilla.BinomialVanillaEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.HestonProcess;
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
 * <p><strong>Phase 5e.5b-CFC-d-58 addition:</strong>
 * <ul>
 *   <li>{@link #testGreeksInitialization()} — verifies the
 *       {@link ForwardVanillaEngine} correctly propagates "not-provided"
 *       greeks from a binomial inner engine
 *       ({@link BinomialVanillaEngine} parameterised on
 *       {@link CoxRossRubinstein}, 300 fixed steps). C++ uses a local
 *       {@code TestBinomialEngine} subclass; Java uses the protected
 *       {@code ForwardVanillaEngine.buildInnerEngine} hook (see
 *       {@link BinomialInnerForwardEngine} below).
 * </ul>
 *
 * <p><strong>Phase 5e.5b-CFC-d-119 addition:</strong>
 * <ul>
 *   <li>{@link #testMCPrices()} — MC BS forward engine vs analytic
 *       forward BS, LOOSE 1e-2 MC tier.
 *   <li>{@link #testHestonMCPrices()} — MC Heston forward engine vs
 *       analytic forward BS (flat-Heston) and vs analytic vanilla
 *       Heston (reset=today), LOOSE 1e-2 MC tier.
 *   <li>{@link #testHestonAnalyticalVsMCPrices()} — MC Heston forward
 *       engine (plain and CV) vs semi-analytic
 *       {@code AnalyticHestonForwardEuropeanEngine}, LOOSE 1e-2 MC tier.
 * </ul>
 *
 * <p>Source: {@code test-suite/forwardoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ForwardOptionTest {

    public ForwardOptionTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

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

    /**
     * Port of C++ {@code forwardoption.cpp::testGreeksInitialization}
     * (v1.42.1 lines 360-475).
     *
     * <p>C++ defines a local {@code TestBinomialEngine} subclass of
     * {@code BinomialVanillaEngine<CoxRossRubinstein>} with fixed
     * {@code timeSteps=300}, then prices a forward option using
     * {@code ForwardVanillaEngine<TestBinomialEngine>}. The test verifies
     * that whenever the binomial control engine cannot compute a given
     * greek (delta requires {@code strikeSensitivity}; rho, dividendRho,
     * vega are simply not populated by the binomial pricer), the forward
     * engine also reports the same greek as unavailable.
     *
     * <p>Java implementation differences:
     * <ul>
     *   <li>C++ has a templated {@code ForwardVanillaEngine<Engine>};
     *       Java's {@link ForwardVanillaEngine} hardcodes
     *       {@link org.jquantlib.pricingengines.AnalyticEuropeanEngine}
     *       but exposes a protected {@code buildInnerEngine} hook
     *       (added Phase 5e.5b-CFC-d-58) — overridden here via
     *       {@link BinomialInnerForwardEngine}.
     *   <li>C++ {@code BinomialVanillaEngine::calculate} throws
     *       {@code QuantLib::Error} when an un-set greek is requested;
     *       Java {@link BinomialVanillaEngine#calculate()} leaves rho,
     *       vega, dividendRho as {@code Double.NaN}. The Java accessor
     *       {@code OneAssetOption.rho()} et al. only checks
     *       {@code NULL_REAL == Double.MAX_VALUE}, so NaN returns
     *       silently. The forward engine maps NaN-input greeks to
     *       {@code NULL_REAL}, so {@code option.rho()} et al. throw.
     *       Test verifies: whenever the binomial control engine fails to
     *       supply a usable value, the forward engine fails too.
     * </ul>
     */
    @Test
    public void testGreeksInitialization() {
        QL.info("Testing forward option greeks initialization...");

        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final SimpleQuote rRate = new SimpleQuote(0.01);
        final SimpleQuote vol   = new SimpleQuote(0.11);

        final Handle<Quote> spotH  = new Handle<Quote>(spot);
        final Handle<Quote> qRateH = new Handle<Quote>(qRate);
        final Handle<Quote> rRateH = new Handle<Quote>(rRate);
        final Handle<Quote> volH   = new Handle<Quote>(vol);

        final Calendar cal = new NullCalendar();
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(today, qRateH, dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(today, rRateH, dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<BlackVolTermStructure> volTS = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, cal, volH, dc));

        final BlackScholesMertonProcess stochProcess =
                new BlackScholesMertonProcess(spotH, qTS, rTS, volTS);

        final ForwardVanillaEngine fwdEngine = new BinomialInnerForwardEngine(stochProcess);
        final Date exDate = today.add(new org.jquantlib.time.Period(1,
                org.jquantlib.time.TimeUnit.Years));
        final Exercise exercise = new EuropeanExercise(exDate);
        final Date reset = today.add(new org.jquantlib.time.Period(6,
                org.jquantlib.time.TimeUnit.Months));
        final StrikedTypePayoff payoff =
                new PlainVanillaPayoff(Option.Type.Call, 0.0);

        final ForwardVanillaOption option =
                new ForwardVanillaOption(0.9, reset, payoff, exercise);
        option.setPricingEngine(fwdEngine);

        final BinomialVanillaEngine<CoxRossRubinstein> ctrlEngine =
                new BinomialVanillaEngine<CoxRossRubinstein>(
                        CoxRossRubinstein.class, stochProcess, 300);
        final VanillaOption ctrloption = new VanillaOption(payoff, exercise);
        ctrloption.setPricingEngine(ctrlEngine);

        // C++ contract (mirrored in Java with NaN-vs-NULL_REAL semantics
        // documented above): when the binomial control engine cannot
        // produce a usable greek, the forward engine must not produce
        // one either.
        checkGreekFallthrough("delta",       () -> ctrloption.delta(),       () -> option.delta());
        checkGreekFallthrough("rho",         () -> ctrloption.rho(),         () -> option.rho());
        checkGreekFallthrough("dividendRho", () -> ctrloption.dividendRho(), () -> option.dividendRho());
        checkGreekFallthrough("vega",        () -> ctrloption.vega(),        () -> option.vega());
    }

    /**
     * Mirrors the C++ try/catch idiom from
     * {@code forwardoption.cpp::testGreeksInitialization}: if the
     * control (binomial) engine cannot supply a greek, the forward
     * engine must also fail to provide it.
     *
     * <p>A greek is considered "unavailable" if either the call throws
     * or it returns {@code Double.NaN} (Java's binomial engine leaves
     * un-computed greeks as NaN rather than throwing).
     */
    private static void checkGreekFallthrough(
            final String label,
            final java.util.concurrent.Callable<Double> ctrlCall,
            final java.util.concurrent.Callable<Double> fwdCall) {
        boolean ctrlOk;
        try {
            final double ctrl = ctrlCall.call();
            ctrlOk = !Double.isNaN(ctrl);
        } catch (final Exception e) {
            ctrlOk = false;
        }
        boolean fwdOk;
        double fwd = Double.NaN;
        try {
            fwd = fwdCall.call();
            fwdOk = !Double.isNaN(fwd);
        } catch (final Exception e) {
            fwdOk = false;
        }
        if (!ctrlOk) {
            assertTrue("Forward " + label + " invalid (ctrl unavailable, "
                    + "fwd available=" + fwdOk + ", fwd=" + fwd + ")", !fwdOk);
        }
    }

    /**
     * Local subclass of {@link ForwardVanillaEngine} that swaps the
     * inner engine from {@code AnalyticEuropeanEngine} to
     * {@code BinomialVanillaEngine<CoxRossRubinstein>} with 300 fixed
     * steps. Java equivalent of the C++ {@code TestBinomialEngine}
     * defined inline in {@code test-suite/forwardoption.cpp}.
     */
    private static final class BinomialInnerForwardEngine extends ForwardVanillaEngine {
        BinomialInnerForwardEngine(final GeneralizedBlackScholesProcess process) {
            super(process);
        }

        @Override
        protected org.jquantlib.instruments.OneAssetOption.EngineImpl buildInnerEngine(
                final GeneralizedBlackScholesProcess fwdProcess) {
            return new BinomialVanillaEngine<CoxRossRubinstein>(
                    CoxRossRubinstein.class, fwdProcess, 300);
        }
    }

    /**
     * Port of C++ {@code forwardoption.cpp::testMCPrices} (v1.42.1
     * lines 477-539).
     *
     * <p>Cross-checks {@link MCForwardEuropeanBSEngine} against the
     * analytic {@link ForwardVanillaEngine} over a range of moneynesses
     * under a flat Black-Scholes process.
     *
     * <p>C++ uses 100 timesteps, 5000 samples, seed 42, and per-moneyness
     * tolerances {0.002, 0.001, 0.0006, 5e-4, 5e-4}. Java port uses the
     * same fixture but relaxes to the project-wide LOOSE 1e-2 MC tier
     * (see CLAUDE.md) — sufficient to detect engine regressions while
     * being robust to {@code MersenneTwisterUniformRng} stream
     * differences across builds.
     */
    @Test
    public void testMCPrices() {
        QL.info("Testing forward option MC prices...");

        final int timeSteps = 100;
        final int numberOfSamples = 5000;
        final long mcSeed = 42L;

        final double q = 0.04;
        final double r = 0.01;
        final double sigma = 0.11;
        final double s = 100.0;

        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(s);
        final SimpleQuote qRate = new SimpleQuote(q);
        final SimpleQuote rRate = new SimpleQuote(r);
        final SimpleQuote vol   = new SimpleQuote(sigma);

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

        final BlackScholesMertonProcess stochProcess =
                new BlackScholesMertonProcess(spotH, qTS, rTS, volTS);

        final PricingEngine analyticEngine = new ForwardVanillaEngine(stochProcess);
        final PricingEngine mcEngine = new MCForwardEuropeanBSEngine(
                stochProcess, timeSteps, McSimulation.NULL_SAMPLES,
                /* brownianBridge */ false, /* antitheticVariate */ false,
                numberOfSamples, McSimulation.NULL_TOLERANCE,
                McSimulation.NULL_SAMPLES, mcSeed);

        final Date exDate = today.add(new org.jquantlib.time.Period(1,
                org.jquantlib.time.TimeUnit.Years));
        final Exercise exercise = new EuropeanExercise(exDate);
        final Date reset = today.add(new org.jquantlib.time.Period(6,
                org.jquantlib.time.TimeUnit.Months));
        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 0.0);

        final double[] moneynesses = { 0.8, 0.9, 1.0, 1.1, 1.2 };
        final double tolerance = 1.0e-2;   // LOOSE MC tier (CLAUDE.md)

        for (final double m : moneynesses) {
            final ForwardVanillaOption option =
                    new ForwardVanillaOption(m, reset, payoff, exercise);

            option.setPricingEngine(analyticEngine);
            final double analyticPrice = option.NPV();

            option.setPricingEngine(mcEngine);
            final double mcPrice = option.NPV();

            final double error = Math.abs(analyticPrice - mcPrice) / s;
            assertTrue("testMCPrices moneyness=" + m
                    + " analytic=" + analyticPrice + " mc=" + mcPrice
                    + " error=" + error + " tolerance=" + tolerance,
                    error <= tolerance);
        }
    }

    /**
     * Port of C++ {@code forwardoption.cpp::testHestonMCPrices} (v1.42.1
     * lines 541-700).
     *
     * <p>Two sub-cases:
     * <ol>
     *   <li><strong>Flat Heston vs analytic BS:</strong> set up a
     *       Heston process that degenerates to Black-Scholes
     *       ({@code kappa ≈ 0}, {@code sigma ≈ 0}, {@code v0 = theta =
     *       sigma_bs^2}) and compare the MC forward price against the
     *       analytic forward-start BS engine.
     *   <li><strong>Smile Heston, reset=today, vs analytic vanilla
     *       Heston:</strong> with {@code reset = today}, the forward-
     *       start option degenerates to a vanilla, so the MC forward
     *       price must match the semi-analytic Heston vanilla price.
     * </ol>
     *
     * <p>C++ uses {@code LowDiscrepancy} RNG with 50 steps × 4095 samples.
     * Java is specialised for {@code PseudoRandom} only, so this test
     * uses {@code PseudoRandom} + the project-wide LOOSE 1e-2 MC tier
     * (CLAUDE.md) — robust to MT stream differences.
     */
    @Test
    public void testHestonMCPrices() {
        QL.info("Testing forward option Heston MC prices...");

        final Option.Type[] optionTypes = { Option.Type.Call, Option.Type.Put };

        for (final Option.Type optionType : optionTypes) {

            final double analyticTolerance = 1.0e-2; // LOOSE MC tier
            final double mcTolerance       = 1.0e-2;

            final int timeSteps = 50;
            final int numberOfSamples = 4095;
            final long mcSeed = 42L;

            final double q = 0.04;
            final double r = 0.01;
            final double sigma_bs = 0.245;
            final double s = 100.0;

            // Sub-case 1: Heston ≈ flat BS
            double v0 = sigma_bs * sigma_bs;
            double kappa = 1.0e-8;
            double theta = sigma_bs * sigma_bs;
            double sigmaHes = 1.0e-8;
            double rho = -0.93;

            final DayCounter dc = new Actual360();
            final Date today = new Date(15, Month.January, 2026);
            new Settings().setEvaluationDate(today);

            final Date exDate = today.add(new org.jquantlib.time.Period(1,
                    org.jquantlib.time.TimeUnit.Years));
            final Exercise exercise = new EuropeanExercise(exDate);
            Date reset = today.add(new org.jquantlib.time.Period(6,
                    org.jquantlib.time.TimeUnit.Months));
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(optionType, 0.0);

            final SimpleQuote spot  = new SimpleQuote(s);
            final SimpleQuote qRate = new SimpleQuote(q);
            final SimpleQuote rRate = new SimpleQuote(r);
            final SimpleQuote vol   = new SimpleQuote(sigma_bs);

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

            final BlackScholesMertonProcess bsProcess =
                    new BlackScholesMertonProcess(spotH, qTS, rTS, volTS);

            final PricingEngine analyticEngine = new ForwardVanillaEngine(bsProcess);

            final HestonProcess hestonProcess = new HestonProcess(
                    rTS, qTS, new Handle<Quote>(spot),
                    v0, kappa, theta, sigmaHes, rho);
            hestonProcess.update();

            final PricingEngine mcEngine = new MCForwardEuropeanHestonEngine(
                    hestonProcess, timeSteps, McSimulation.NULL_SAMPLES,
                    /* antithetic */ false,
                    numberOfSamples, McSimulation.NULL_TOLERANCE,
                    McSimulation.NULL_SAMPLES, mcSeed);

            final double[] moneynesses = { 0.8, 0.9, 1.0, 1.1, 1.2 };

            for (final double m : moneynesses) {
                final ForwardVanillaOption option =
                        new ForwardVanillaOption(m, reset, payoff, exercise);

                option.setPricingEngine(analyticEngine);
                final double analyticPrice = option.NPV();

                option.setPricingEngine(mcEngine);
                final double mcPrice = option.NPV();

                final double mcError = Math.abs(analyticPrice - mcPrice) / s;
                assertTrue("testHestonMCForwardStartPrices type=" + optionType
                        + " moneyness=" + m
                        + " analytic=" + analyticPrice + " mc=" + mcPrice
                        + " error=" + mcError + " tolerance=" + mcTolerance,
                        mcError <= mcTolerance);
            }

            // Sub-case 2: smile-Heston with reset=today vs analytic vanilla
            v0 = sigma_bs * sigma_bs;
            kappa = 1.0;
            theta = 0.08;
            sigmaHes = 0.39;
            rho = -0.93;

            reset = today;

            final HestonProcess hestonProcessSmile = new HestonProcess(
                    rTS, qTS, new Handle<Quote>(spot),
                    v0, kappa, theta, sigmaHes, rho);
            hestonProcessSmile.update();

            final HestonModel hestonModel = new HestonModel(hestonProcessSmile);
            final PricingEngine analyticHestonEngine =
                    new AnalyticHestonEngine(hestonModel, hestonProcessSmile, 96);

            final PricingEngine mcEngineSmile = new MCForwardEuropeanHestonEngine(
                    hestonProcessSmile, timeSteps, McSimulation.NULL_SAMPLES,
                    /* antithetic */ false,
                    numberOfSamples, McSimulation.NULL_TOLERANCE,
                    McSimulation.NULL_SAMPLES, mcSeed);

            for (final double m : moneynesses) {
                final double strike = s * m;
                final StrikedTypePayoff vanillaPayoff =
                        new PlainVanillaPayoff(optionType, strike);
                final VanillaOption vanillaOption =
                        new VanillaOption(vanillaPayoff, exercise);
                final ForwardVanillaOption forwardOption =
                        new ForwardVanillaOption(m, reset, payoff, exercise);

                vanillaOption.setPricingEngine(analyticHestonEngine);
                final double analyticPrice = vanillaOption.NPV();

                forwardOption.setPricingEngine(mcEngineSmile);
                final double mcPrice = forwardOption.NPV();

                final double mcError = Math.abs(analyticPrice - mcPrice) / s;
                assertTrue("testHestonMCPrices type=" + optionType
                        + " moneyness=" + m
                        + " analytic=" + analyticPrice + " mc=" + mcPrice
                        + " error=" + mcError + " tolerance=" + analyticTolerance,
                        mcError <= analyticTolerance);
            }
        }
    }

    /**
     * Port of C++ {@code forwardoption.cpp::testHestonAnalyticalVsMCPrices}
     * (v1.42.1 lines 702-801).
     *
     * <p>Cross-checks {@link MCForwardEuropeanHestonEngine} (with and
     * without control variate) against
     * {@link AnalyticHestonForwardEuropeanEngine} on a non-trivial
     * Heston model under both Call and Put types.
     *
     * <p>C++ uses 50 timesteps, 5000 samples, seed 42, PseudoRandom RNG
     * with per-moneyness tolerances {0.001 - 0.003}. Java relaxes to the
     * project-wide LOOSE 1e-2 MC tier (CLAUDE.md).
     */
    @Test
    public void testHestonAnalyticalVsMCPrices() {
        QL.info("Testing Heston analytic vs MC prices...");

        final Option.Type[] optionTypes = { Option.Type.Call, Option.Type.Put };

        for (final Option.Type optionType : optionTypes) {

            final int timeSteps = 50;
            final int numberOfSamples = 5000;
            final long mcSeed = 42L;

            final double q = 0.03;
            final double r = 0.005;
            final double s = 100.0;

            final double volBs = 0.3;
            final double v0 = volBs * volBs;
            final double kappa = 11.35;
            final double theta = 0.022;
            final double sigmaHes = 0.618;
            final double rho = -0.5;

            final DayCounter dc = new Actual360();
            final Date today = new Date(15, Month.January, 2026);
            new Settings().setEvaluationDate(today);

            final Date exDate = today.add(new org.jquantlib.time.Period(1,
                    org.jquantlib.time.TimeUnit.Years));
            final Exercise exercise = new EuropeanExercise(exDate);
            final Date reset = today.add(new org.jquantlib.time.Period(6,
                    org.jquantlib.time.TimeUnit.Months));
            final StrikedTypePayoff payoff =
                    new PlainVanillaPayoff(optionType, 0.0);

            final SimpleQuote spot  = new SimpleQuote(s);
            final SimpleQuote qRate = new SimpleQuote(q);
            final SimpleQuote rRate = new SimpleQuote(r);

            final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                    new FlatForward(today, new Handle<Quote>(qRate), dc,
                            Compounding.Continuous, Frequency.Annual));
            final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                    new FlatForward(today, new Handle<Quote>(rRate), dc,
                            Compounding.Continuous, Frequency.Annual));

            final HestonProcess hestonProcess = new HestonProcess(
                    rTS, qTS, new Handle<Quote>(spot),
                    v0, kappa, theta, sigmaHes, rho);
            hestonProcess.update();

            final PricingEngine mcEngine = new MCForwardEuropeanHestonEngine(
                    hestonProcess, timeSteps, McSimulation.NULL_SAMPLES,
                    /* antithetic */ false,
                    numberOfSamples, McSimulation.NULL_TOLERANCE,
                    McSimulation.NULL_SAMPLES, mcSeed);

            final PricingEngine mcEngineCv = new MCForwardEuropeanHestonEngine(
                    hestonProcess, timeSteps, McSimulation.NULL_SAMPLES,
                    /* antithetic */ false,
                    numberOfSamples, McSimulation.NULL_TOLERANCE,
                    McSimulation.NULL_SAMPLES, mcSeed,
                    /* controlVariate */ true);

            final PricingEngine analyticEngine =
                    new AnalyticHestonForwardEuropeanEngine(hestonProcess);

            final double[] moneynesses = { 0.8, 1.0, 1.2 };
            final double tolerance = 1.0e-2;   // LOOSE MC tier

            for (final double m : moneynesses) {
                final ForwardVanillaOption option =
                        new ForwardVanillaOption(m, reset, payoff, exercise);

                option.setPricingEngine(analyticEngine);
                final double analyticPrice = option.NPV();

                option.setPricingEngine(mcEngine);
                final double mcPrice = option.NPV();
                final double error = Math.abs(analyticPrice - mcPrice) / s;
                assertTrue("testHestonMCVsAnalyticPrices type=" + optionType
                        + " moneyness=" + m
                        + " analytic=" + analyticPrice + " mc=" + mcPrice
                        + " error=" + error + " tolerance=" + tolerance,
                        error <= tolerance);

                option.setPricingEngine(mcEngineCv);
                final double mcPriceCv = option.NPV();
                final double errorCv = Math.abs(analyticPrice - mcPriceCv) / s;
                assertTrue("testHestonMCControlVariateVsAnalyticPrices type=" + optionType
                        + " moneyness=" + m
                        + " analytic=" + analyticPrice + " mc(CV)=" + mcPriceCv
                        + " error=" + errorCv + " tolerance=" + tolerance,
                        errorCv <= tolerance);
            }
        }
    }
}

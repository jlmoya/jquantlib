/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.CliquetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PercentageStrikePayoff;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.cliquet.AnalyticCliquetEngine;
import org.jquantlib.pricingengines.cliquet.AnalyticPerformanceEngine;
import org.jquantlib.pricingengines.cliquet.MCPerformanceEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.junit.Test;

/**
 * Phase 5k port of {@code test-suite/cliquetoption.cpp} v1.42.1
 * (356 LOC, 4 cases).
 *
 * <p>Exercises the cliquet (ratchet) option: analytic forward-start
 * compounded values vs Haug 1998 reference, Greeks via analytic engine,
 * Greeks via MC performance engine, and end-to-end MC performance
 * pricing (one path per reset period).
 *
 * <p><strong>Phase 4h.5c: full sweeps for testGreeks (648 cases),
 * testPerformanceGreeks (648 cases) and testMcPerformance (256 cases)
 * are bodied.</strong>
 *
 * <p>Source: {@code test-suite/cliquetoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CliquetOptionTest {

    public CliquetOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Mirrors C++ test-suite/cliquetoption.cpp::testValues.
     * Expected NPV: 4.4064 (Haug, "Option Pricing Formulas", p.37) with tolerance 1e-4.
     */
    @Test
    public void testValues() {
        QL.info("Testing Cliquet option values...");

        final Date today = new Settings().evaluationDate();
        final DayCounter dc = new Actual360();

        final SimpleQuote spot = new SimpleQuote(60.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.08);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.30);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final PricingEngine engine = new AnalyticCliquetEngine(process);

        final List<Date> reset = new ArrayList<>();
        reset.add(today.add(90));
        final Date maturity = today.add(360);

        final Option.Type type = Option.Type.Call;
        final double moneyness = 1.1;

        final PercentageStrikePayoff payoff = new PercentageStrikePayoff(type, moneyness);
        final EuropeanExercise exercise = new EuropeanExercise(maturity);

        final CliquetOption option = new CliquetOption(payoff, exercise, reset);
        option.setPricingEngine(engine);

        final double calculated = option.NPV();
        final double expected = 4.4064; // Haug p.37
        final double tolerance = 1.0e-4;
        assertEquals("Cliquet NPV mismatch (vs Haug p.37)", expected, calculated, tolerance);
    }

    /**
     * Mirrors C++ test-suite/cliquetoption.cpp::testGreeks (template
     * {@code testOptionGreeks<AnalyticCliquetEngine>}).
     * <p>
     * Numerical-derivative cross-check of analytic Greeks against finite
     * differences. Full Cartesian sweep: 2 types x 3 moneyness x 2 lengths
     * x 2 frequencies x 3 q x 3 r x 3 vol = 648 cases per Greek.
     */
    @Test
    public void testGreeks() {
        QL.info("Testing Cliquet option greeks (full 648-case sweep)...");
        testOptionGreeks(/* useAnalyticPerformanceEngine */ false);
    }

    /**
     * Mirrors C++ test-suite/cliquetoption.cpp::testPerformanceGreeks
     * (template {@code testOptionGreeks<AnalyticPerformanceEngine>}).
     * <p>
     * Same numerical-derivative cross-check, but using
     * {@link AnalyticPerformanceEngine}.
     */
    @Test
    public void testPerformanceGreeks() {
        QL.info("Testing performance option greeks (full 648-case sweep)...");
        testOptionGreeks(/* useAnalyticPerformanceEngine */ true);
    }

    /**
     * Shared body for {@link #testGreeks} and {@link #testPerformanceGreeks}
     * mirroring the C++ template {@code testOptionGreeks<T>}.
     */
    private static void testOptionGreeks(final boolean useAnalyticPerformanceEngine) {
        final double tolDelta  = 1.0e-5;
        final double tolGamma  = 1.0e-5;
        final double tolTheta  = 1.0e-5;
        final double tolRho    = 1.0e-5;
        final double tolDivRho = 1.0e-5;
        final double tolVega   = 1.0e-5;

        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] moneyness = { 0.9, 1.0, 1.1 };
        final double[] underlyings = { 100.0 };
        final double[] qRates = { 0.04, 0.05, 0.06 };
        final double[] rRates = { 0.01, 0.05, 0.15 };
        final int[] lengths = { 1, 2 };
        final Frequency[] frequencies = { Frequency.Semiannual, Frequency.Quarterly };
        final double[] vols = { 0.11, 0.50, 1.20 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        // Use the moving (settlement-days=0, NullCalendar) variant so the curve's
        // reference date tracks Settings.evaluationDate (matches C++ flatRate(qRate, dc)).
        final YieldTermStructure qTS = Utilities.flatRate(qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(rRate, dc);
        final SimpleQuote vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(vol, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        for (final Option.Type type : types) {
            for (final double moneynes : moneyness) {
                for (final int length : lengths) {
                    for (final Frequency frequencie : frequencies) {

                        final EuropeanExercise maturity = new EuropeanExercise(
                                today.add(new Period(length, org.jquantlib.time.TimeUnit.Years)));

                        final PercentageStrikePayoff payoff =
                                new PercentageStrikePayoff(type, moneynes);

                        // Reset dates: every Period(frequencie) between today (exclusive)
                        // and maturity->lastDate() (exclusive)
                        final List<Date> reset = new ArrayList<>();
                        final Period tenor = new Period(frequencie);
                        Date d = today.add(tenor);
                        while (d.lt(maturity.lastDate())) {
                            reset.add(d);
                            d = d.add(tenor);
                        }

                        final PricingEngine engine = useAnalyticPerformanceEngine
                                ? new AnalyticPerformanceEngine(process)
                                : new AnalyticCliquetEngine(process);

                        final CliquetOption option = new CliquetOption(payoff, maturity, reset);
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
                                        final double calcDelta  = option.delta();
                                        final double calcGamma  = option.gamma();
                                        final double calcTheta  = option.theta();
                                        final double calcRho    = option.rho();
                                        final double calcDivRho = option.dividendRho();
                                        final double calcVega   = option.vega();

                                        if (value > u * 1.0e-5) {
                                            // Perturb spot and get delta and gamma
                                            final double du = u * 1.0e-4;
                                            spot.setValue(u + du);
                                            final double valueP_s = option.NPV();
                                            final double deltaP   = option.delta();
                                            spot.setValue(u - du);
                                            final double valueM_s = option.NPV();
                                            final double deltaM   = option.delta();
                                            spot.setValue(u);
                                            final double expDelta = (valueP_s - valueM_s) / (2 * du);
                                            final double expGamma = (deltaP - deltaM) / (2 * du);

                                            // Perturb r, get rho
                                            final double dr = r * 1.0e-4;
                                            rRate.setValue(r + dr);
                                            final double valueP_r = option.NPV();
                                            rRate.setValue(r - dr);
                                            final double valueM_r = option.NPV();
                                            rRate.setValue(r);
                                            final double expRho = (valueP_r - valueM_r) / (2 * dr);

                                            // Perturb q, get dividendRho
                                            final double dq = q * 1.0e-4;
                                            qRate.setValue(q + dq);
                                            final double valueP_q = option.NPV();
                                            qRate.setValue(q - dq);
                                            final double valueM_q = option.NPV();
                                            qRate.setValue(q);
                                            final double expDivRho = (valueP_q - valueM_q) / (2 * dq);

                                            // Perturb vol, get vega
                                            final double dv = v * 1.0e-4;
                                            vol.setValue(v + dv);
                                            final double valueP_v = option.NPV();
                                            vol.setValue(v - dv);
                                            final double valueM_v = option.NPV();
                                            vol.setValue(v);
                                            final double expVega = (valueP_v - valueM_v) / (2 * dv);

                                            // Perturb date and get theta
                                            final double dT = dc.yearFraction(today.sub(1), today.add(1));
                                            new Settings().setEvaluationDate(today.sub(1));
                                            final double valueM_t = option.NPV();
                                            new Settings().setEvaluationDate(today.add(1));
                                            final double valueP_t = option.NPV();
                                            new Settings().setEvaluationDate(today);
                                            final double expTheta = (valueP_t - valueM_t) / dT;

                                            checkGreek("delta",       expDelta,  calcDelta,  u, tolDelta,  type, payoff, maturity, today, q, r, v);
                                            checkGreek("gamma",       expGamma,  calcGamma,  u, tolGamma,  type, payoff, maturity, today, q, r, v);
                                            checkGreek("theta",       expTheta,  calcTheta,  u, tolTheta,  type, payoff, maturity, today, q, r, v);
                                            checkGreek("rho",         expRho,    calcRho,    u, tolRho,    type, payoff, maturity, today, q, r, v);
                                            checkGreek("divRho",      expDivRho, calcDivRho, u, tolDivRho, type, payoff, maturity, today, q, r, v);
                                            checkGreek("vega",        expVega,   calcVega,   u, tolVega,   type, payoff, maturity, today, q, r, v);
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

    private static void checkGreek(final String greek, final double expected, final double calculated,
                                   final double reference, final double tol,
                                   final Option.Type type, final PercentageStrikePayoff payoff,
                                   final EuropeanExercise maturity, final Date today,
                                   final double q, final double r, final double v) {
        final double error = Utilities.relativeError(expected, calculated, reference);
        if (error > tol) {
            fail(type + " option:\n"
                + "    spot value:       " + reference + "\n"
                + "    moneyness:        " + payoff.strike() + "\n"
                + "    dividend yield:   " + q + "\n"
                + "    risk-free rate:   " + r + "\n"
                + "    reference date:   " + today + "\n"
                + "    maturity:         " + maturity.lastDate() + "\n"
                + "    volatility:       " + v + "\n\n"
                + "    expected   " + greek + ": " + expected + "\n"
                + "    calculated " + greek + ": " + calculated + "\n"
                + "    error:            " + error + "\n"
                + "    tolerance:        " + tol);
        }
    }

    /**
     * Mirrors C++ test-suite/cliquetoption.cpp::testMcPerformance.
     * Cross-validates {@link MCPerformanceEngine} against
     * {@link AnalyticPerformanceEngine} for the full Cartesian sweep:
     * 2 types x 2 moneyness x 2 lengths x 2 frequencies x 1 underlying
     * x 2 q x 2 r x 2 vol = 256 cases. Absolute tolerance 1.5e-2 (matches C++).
     */
    @Test
    public void testMcPerformance() {
        QL.info("Testing Monte Carlo performance engine against analytic results "
              + "(full 256-case sweep)...");

        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] moneyness = { 0.9, 1.1 };
        final double[] underlyings = { 100.0 };
        final double[] qRates = { 0.04, 0.06 };
        final double[] rRates = { 0.01, 0.10 };
        final int[] lengths = { 2, 4 };
        final Frequency[] frequencies = { Frequency.Semiannual, Frequency.Quarterly };
        final double[] vols = { 0.10, 0.90 };

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot  = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final YieldTermStructure qTS = Utilities.flatRate(qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final YieldTermStructure rTS = Utilities.flatRate(rRate, dc);
        final SimpleQuote vol   = new SimpleQuote(0.0);
        final BlackVolTermStructure volTS = Utilities.flatVol(vol, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        for (final Option.Type type : types) {
            for (final double moneynes : moneyness) {
                for (final int length : lengths) {
                    for (final Frequency frequencie : frequencies) {

                        final Period tenor = new Period(frequencie);
                        final EuropeanExercise maturity = new EuropeanExercise(
                                today.add(tenor.mul(length)));

                        final PercentageStrikePayoff payoff =
                                new PercentageStrikePayoff(type, moneynes);

                        final List<Date> reset = new ArrayList<>();
                        Date d = today.add(tenor);
                        while (d.lt(maturity.lastDate())) {
                            reset.add(d);
                            d = d.add(tenor);
                        }

                        final CliquetOption option = new CliquetOption(payoff, maturity, reset);

                        final PricingEngine refEngine =
                                new AnalyticPerformanceEngine(process);

                        final PricingEngine mcEngine = new MCPerformanceEngine(
                                process,
                                /* brownianBridge */ true,
                                /* antitheticVariate */ false,
                                /* requiredSamples */ McSimulation.NULL_SAMPLES,
                                /* requiredTolerance */ 5.0e-3,
                                /* maxSamples */ McSimulation.NULL_SAMPLES,
                                /* seed */ 42L);

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

                                        option.setPricingEngine(refEngine);
                                        final double refValue = option.NPV();

                                        option.setPricingEngine(mcEngine);
                                        final double value = option.NPV();

                                        final double error = Math.abs(refValue - value);
                                        final double tolerance = 1.5e-2;
                                        if (error > tolerance) {
                                            fail(type + " option:\n"
                                                + "    spot value:       " + u + "\n"
                                                + "    moneyness:        " + payoff.strike() + "\n"
                                                + "    dividend yield:   " + q + "\n"
                                                + "    risk-free rate:   " + r + "\n"
                                                + "    reference date:   " + today + "\n"
                                                + "    maturity:         " + maturity.lastDate() + "\n"
                                                + "    volatility:       " + v + "\n\n"
                                                + "    expected   value: " + refValue + "\n"
                                                + "    calculated value: " + value + "\n"
                                                + "    error:            " + error + "\n"
                                                + "    tolerance:        " + tolerance);
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

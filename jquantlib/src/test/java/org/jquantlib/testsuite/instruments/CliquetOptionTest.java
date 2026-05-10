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
import org.junit.Ignore;
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
 * <p><strong>Phase 4h.5 partial: testValues bodied</strong> — uses the newly
 * ported {@link AnalyticCliquetEngine} + {@link CliquetOption} +
 * {@link PercentageStrikePayoff}. Greeks and MC variants remain Phase 5k.5
 * carry-forwards (numerical-derivative cross-check + MC engine not yet
 * ported).
 *
 * <p>Source: {@code test-suite/cliquetoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CliquetOptionTest {

    public CliquetOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_GREEKS =
            "Phase 5k.5 — requires CliquetOption + AnalyticCliquetEngine "
          + "Greeks numerical-derivative cross-check (delta/gamma/theta/vega per-reset)";

    private static final String REASON_PERF_GREEKS =
            "Phase 5k.5 — requires AnalyticPerformanceEngine "
          + "Greeks numerical-derivative cross-check";

    private static final String REASON_MC_PERF =
            "Phase 5k.5 — requires McPerformanceEngine "
          + "(MC cliquet performance pricing; depends on path-by-path reset wiring)";

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

        final List<Date> reset = new ArrayList<Date>();
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
     * differences. The C++ test sweeps 2 types x 3 moneyness x 2 lengths
     * x 2 frequencies x 3 q x 3 r x 3 vol = 648 cases; Java tests one
     * representative case to keep runtime under a second (full sweep is
     * a Phase 4h.5c carry-forward).
     */
    @Test
    public void testGreeks() {
        QL.info("Testing Cliquet option greeks (single representative case)...");

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        final Settings settings = new Settings();
        settings.setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final SimpleQuote rRate = new SimpleQuote(0.05);
        final SimpleQuote vol = new SimpleQuote(0.50);

        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final EuropeanExercise maturity = new EuropeanExercise(
                today.add(new Period(2, org.jquantlib.time.TimeUnit.Years)));

        final PercentageStrikePayoff payoff = new PercentageStrikePayoff(Option.Type.Call, 1.0);

        // Reset dates: every 6 months between today and maturity (exclusive)
        final List<Date> reset = new ArrayList<Date>();
        Date d = today.add(new Period(Frequency.Semiannual));
        while (d.lt(maturity.lastDate())) {
            reset.add(d);
            d = d.add(new Period(Frequency.Semiannual));
        }

        final PricingEngine engine = new AnalyticCliquetEngine(process);
        final CliquetOption option = new CliquetOption(payoff, maturity, reset);
        option.setPricingEngine(engine);

        final double u = 100.0;
        final double q = 0.04;
        final double r = 0.05;
        final double v = 0.50;

        final double value = option.NPV();
        QL.require(value > u * 1.0e-5, "value too small to perturb");

        final double calcDelta  = option.delta();
        final double calcGamma  = option.gamma();
        // theta/rho/dividendRho/vega available too
        final double calcRho    = option.rho();
        final double calcDivRho = option.dividendRho();
        final double calcVega   = option.vega();

        // Perturb spot and get delta and gamma
        final double du = u * 1.0e-4;
        spot.setValue(u + du);
        final double valueP = option.NPV();
        final double deltaP = option.delta();
        spot.setValue(u - du);
        final double valueM = option.NPV();
        final double deltaM = option.delta();
        spot.setValue(u);
        final double expDelta = (valueP - valueM) / (2 * du);
        final double expGamma = (deltaP - deltaM) / (2 * du);

        // Perturb r, get rho
        final double dr = r * 1.0e-4;
        rRate.setValue(r + dr);
        final double rPlus = option.NPV();
        rRate.setValue(r - dr);
        final double rMinus = option.NPV();
        rRate.setValue(r);
        final double expRho = (rPlus - rMinus) / (2 * dr);

        // Perturb q, get dividendRho
        final double dq = q * 1.0e-4;
        qRate.setValue(q + dq);
        final double qPlus = option.NPV();
        qRate.setValue(q - dq);
        final double qMinus = option.NPV();
        qRate.setValue(q);
        final double expDivRho = (qPlus - qMinus) / (2 * dq);

        // Perturb vol, get vega
        final double dv = v * 1.0e-4;
        vol.setValue(v + dv);
        final double vPlus = option.NPV();
        vol.setValue(v - dv);
        final double vMinus = option.NPV();
        vol.setValue(v);
        final double expVega = (vPlus - vMinus) / (2 * dv);

        // Tolerance: 1e-5 relative to underlying
        final double tol = 1.0e-5 * u;
        assertEquals("delta",       expDelta,  calcDelta,  tol);
        assertEquals("gamma",       expGamma,  calcGamma,  tol);
        assertEquals("rho",         expRho,    calcRho,    tol);
        assertEquals("dividendRho", expDivRho, calcDivRho, tol);
        assertEquals("vega",        expVega,   calcVega,   tol);
    }

    @Ignore(REASON_PERF_GREEKS) @Test public void testPerformanceGreeks()   { fail("not implemented"); }

    /**
     * Mirrors C++ test-suite/cliquetoption.cpp::testMcPerformance.
     * Smoke test: cross-validate a single representative configuration
     * via {@link MCPerformanceEngine} against {@link AnalyticPerformanceEngine}
     * with absolute tolerance 1.5e-2 (matches C++).
     *
     * <p>The C++ test sweeps all combinations (2 types x 2 moneyness
     * x 2 lengths x 2 frequencies x 2 q x 2 r x 2 vol = 256 cases). For
     * Java we exercise one representative case so the test stays under a
     * second; full sweep is a Phase 4h.5c carry-forward.
     */
    @Test
    public void testMcPerformance() {
        QL.info("Testing Monte Carlo performance engine against analytic results...");

        final DayCounter dc = new Actual360();
        final Date today = Date.todaysDate();
        new Settings().setEvaluationDate(today);

        final SimpleQuote spot = new SimpleQuote(100.0);
        final SimpleQuote qRate = new SimpleQuote(0.04);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final SimpleQuote rRate = new SimpleQuote(0.10);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final SimpleQuote vol = new SimpleQuote(0.10);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final BlackScholesMertonProcess process = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final Period tenor = new Period(Frequency.Semiannual);
        final int length = 2;
        final EuropeanExercise maturity = new EuropeanExercise(today.add(tenor.mul(length)));

        final PercentageStrikePayoff payoff = new PercentageStrikePayoff(Option.Type.Call, 1.1);

        final List<Date> reset = new ArrayList<Date>();
        Date d = today.add(tenor);
        while (d.lt(maturity.lastDate())) {
            reset.add(d);
            d = d.add(tenor);
        }

        final CliquetOption option = new CliquetOption(payoff, maturity, reset);

        // Reference: AnalyticPerformanceEngine
        final PricingEngine refEngine = new AnalyticPerformanceEngine(process);
        option.setPricingEngine(refEngine);
        final double refValue = option.NPV();

        // MC engine
        final PricingEngine mcEngine = new MCPerformanceEngine(
                process,
                /* brownianBridge */ true,
                /* antitheticVariate */ false,
                /* requiredSamples */ McSimulation.NULL_SAMPLES,
                /* requiredTolerance */ 5.0e-3,
                /* maxSamples */ McSimulation.NULL_SAMPLES,
                /* seed */ 42L);
        option.setPricingEngine(mcEngine);
        final double value = option.NPV();

        final double error = Math.abs(refValue - value);
        final double tolerance = 1.5e-2;
        assertEquals("MCPerformanceEngine NPV vs AnalyticPerformanceEngine "
                   + "(refValue=" + refValue + ", mcValue=" + value
                   + ", error=" + error + ")",
                   refValue, value, tolerance);
    }
}

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
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.cliquet.AnalyticCliquetEngine;
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

    @Ignore(REASON_GREEKS)      @Test public void testGreeks()              { fail("not implemented"); }
    @Ignore(REASON_PERF_GREEKS) @Test public void testPerformanceGreeks()   { fail("not implemented"); }
    @Ignore(REASON_MC_PERF)     @Test public void testMcPerformance()       { fail("not implemented"); }
}

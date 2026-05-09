/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5k skeleton port of {@code test-suite/cliquetoption.cpp} v1.42.1
 * (356 LOC, 4 cases).
 *
 * <p>Exercises the cliquet (ratchet) option: analytic forward-start
 * compounded values vs Haug 1998 reference, Greeks via analytic engine,
 * Greeks via MC performance engine, and end-to-end MC performance
 * pricing (one path per reset period).
 *
 * <p><strong>All 4 cases deferred to Phase 5k.5</strong> — Java has no
 * cliquet option subsystem:
 * <ul>
 *   <li>No {@code CliquetOption} instrument class
 *       (a path of forward-start option resets);
 *   <li>No {@code PercentageStrikePayoff} (cliquet uses moneyness-based
 *       strikes per reset);
 *   <li>No {@code AnalyticCliquetEngine} (Black analytic forward-start);
 *   <li>No {@code AnalyticPerformanceEngine} (cliquet performance variant);
 *   <li>No {@code McPerformanceEngine} (MC cliquet performance pricing).
 * </ul>
 *
 * <p>The cliquet family is a moderately-sized production-code carry-forward;
 * Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/cliquetoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CliquetOptionTest {

    private static final String REASON_VALUES =
            "Phase 5k.5 — requires CliquetOption instrument + "
          + "AnalyticCliquetEngine + PercentageStrikePayoff "
          + "for the Haug 1998 reference table";

    private static final String REASON_GREEKS =
            "Phase 5k.5 — requires CliquetOption + AnalyticCliquetEngine "
          + "Greeks branch (delta/gamma/theta/vega per-reset)";

    private static final String REASON_PERF_GREEKS =
            "Phase 5k.5 — requires AnalyticPerformanceEngine "
          + "(cliquet performance variant Greeks)";

    private static final String REASON_MC_PERF =
            "Phase 5k.5 — requires McPerformanceEngine "
          + "(MC cliquet performance pricing; depends on path-by-path reset "
          + "wiring)";

    @Ignore(REASON_VALUES)      @Test public void testValues()              { fail("not implemented"); }
    @Ignore(REASON_GREEKS)      @Test public void testGreeks()              { fail("not implemented"); }
    @Ignore(REASON_PERF_GREEKS) @Test public void testPerformanceGreeks()   { fail("not implemented"); }
    @Ignore(REASON_MC_PERF)     @Test public void testMcPerformance()       { fail("not implemented"); }
}

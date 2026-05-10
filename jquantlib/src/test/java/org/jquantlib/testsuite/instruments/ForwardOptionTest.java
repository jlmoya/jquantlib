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
 * Phase 5i skeleton port of {@code test-suite/forwardoption.cpp} v1.42.1
 * (805 LOC, 7 cases).
 *
 * <p>Exercises forward-start vanilla options under both the standard
 * Black-Scholes-driven analytic engine ({@code ForwardEngine} /
 * {@code ForwardPerformanceEngine}) and the Heston-driven analytic and
 * MC engines.
 *
 * <p><strong>All 7 cases deferred to Phase 5i.5</strong>:
 * <ul>
 *   <li>Java has the {@link org.jquantlib.instruments.ForwardVanillaOption}
 *       instrument and {@link
 *       org.jquantlib.experimental.forward.AnalyticHestonForwardEuropeanEngine}
 *       (Heston analytic), but lacks:
 *       <ul>
 *         <li>{@code ForwardEngine} (BS analytic);
 *         <li>{@code ForwardPerformanceEngine};
 *         <li>{@code MCForwardEuropeanBSEngine} / {@code MCForwardEuropeanHestonEngine}.
 *       </ul></li>
 *   <li>The Heston-analytic engine sits under {@code experimental.forward}
 *       and has its own test {@code AnalyticHestonForwardEuropeanEngineTest};
 *       the in-instruments-package wrapper tests are deferred.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/forwardoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class ForwardOptionTest {

    private static final String REASON_BS_ANALYTIC =
            "Phase 5i.5: ForwardVanillaEngine now ported "
          + "(see org.jquantlib.pricingengines.forward.ForwardVanillaEngine); "
          + "test body is `fail(\"not implemented\")` — needs full port from C++ forwardoption.cpp";

    private static final String REASON_BS_PERFORMANCE =
            "Phase 5i.5: ForwardPerformanceVanillaEngine now ported "
          + "(see org.jquantlib.pricingengines.forward.ForwardPerformanceVanillaEngine); "
          + "test body is `fail(\"not implemented\")` — needs full port from C++ forwardoption.cpp";

    private static final String REASON_GREEKS =
            "Phase 5i.5: ForwardVanillaEngine + ForwardPerformanceVanillaEngine ported; "
          + "Greeks-initialization / numerical-derivative harness needs body fill from C++ "
          + "forwardoption.cpp";

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

    @Ignore(REASON_BS_ANALYTIC)
    @Test
    public void testValues() { fail("not implemented"); }

    @Ignore(REASON_BS_PERFORMANCE)
    @Test
    public void testPerformanceValues() { fail("not implemented"); }

    @Ignore(REASON_GREEKS)
    @Test
    public void testGreeks() { fail("not implemented"); }

    @Ignore(REASON_GREEKS + " — performance-style variant")
    @Test
    public void testPerformanceGreeks() { fail("not implemented"); }

    @Ignore(REASON_GREEKS + " — Greeks-initialization regression")
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

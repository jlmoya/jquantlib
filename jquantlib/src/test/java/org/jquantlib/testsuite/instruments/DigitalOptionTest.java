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
 * Phase 5i skeleton port of {@code test-suite/digitaloption.cpp} v1.42.1
 * (733 LOC, 8 cases).
 *
 * <p>Exercises European and American digital option pricing with
 * cash-or-nothing, asset-or-nothing, and gap payoffs, plus the MC
 * cash-at-hit American engine.
 *
 * <p><strong>All 8 cases deferred to Phase 5i.5</strong> — Java has the
 * payoff classes ({@link org.jquantlib.instruments.CashOrNothingPayoff},
 * {@link org.jquantlib.instruments.AssetOrNothingPayoff},
 * {@link org.jquantlib.instruments.GapPayoff}) and the calculator
 * helpers {@link org.jquantlib.pricingengines.AmericanPayoffAtExpiry} /
 * {@link org.jquantlib.pricingengines.AmericanPayoffAtHit}, but lacks:
 * <ul>
 *   <li><strong>Engine wiring</strong> — the
 *       {@code AnalyticDigitalAmericanEngine} / {@code AnalyticEuropeanEngine}
 *       digital-payoff branch is not exercised end-to-end against the
 *       reference values from C++ {@code digitaloption.cpp};
 *   <li>{@code MCDigitalEngine} (path-dependent cash-at-hit) is not yet
 *       ported;
 *   <li>The Greeks numerical-derivative cross-check requires bumping
 *       infrastructure that exists for vanilla but is not wired for the
 *       digital payoff hierarchy.
 * </ul>
 *
 * <p>Source: {@code test-suite/digitaloption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class DigitalOptionTest {

    private static final String REASON_EUROPEAN =
            "Phase 5i.5 — requires end-to-end wiring of digital payoffs "
          + "through AnalyticEuropeanEngine + reference-value cross-validation";

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

    @Ignore(REASON_EUROPEAN + " — cash-or-nothing payoff")
    @Test
    public void testCashOrNothingEuropeanValues() { fail("not implemented"); }

    @Ignore(REASON_EUROPEAN + " — asset-or-nothing payoff")
    @Test
    public void testAssetOrNothingEuropeanValues() { fail("not implemented"); }

    @Ignore(REASON_EUROPEAN + " — gap payoff")
    @Test
    public void testGapEuropeanValues() { fail("not implemented"); }

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

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
 * Phase 5i skeleton port of {@code test-suite/margrabeoption.cpp} v1.42.1
 * (553 LOC, 3 cases).
 *
 * <p>Exercises the Margrabe two-asset exchange option (option to exchange
 * one risky asset for another), in both European and American flavours.
 *
 * <p><strong>All 3 cases deferred to Phase 5i.5</strong> — Java has no
 * Margrabe option family at all:
 * <ul>
 *   <li>No {@code MargrabeOption} multi-asset instrument
 *       (the C++ class lives under {@code ql/experimental/exoticoptions/}
 *       in v1.42.1; Java {@code experimental.exoticoptions} package has not
 *       ported it);
 *   <li>No {@code AnalyticEuropeanMargrabeEngine} (Margrabe-Fischer
 *       1978 closed form);
 *   <li>No {@code AnalyticAmericanMargrabeEngine} (Margrabe approximation
 *       for early-exercise exchange option).
 * </ul>
 *
 * <p>Both the instrument and engines belong to a future
 * {@code experimental.exoticoptions} sub-phase; Phase 5i.5 is the
 * test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/margrabeoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class MargrabeOptionTest {

    private static final String REASON_EURO =
            "Phase 5i.5 — requires MargrabeOption multi-asset instrument + "
          + "AnalyticEuropeanMargrabeEngine port (no Java equivalent yet)";

    private static final String REASON_GREEKS =
            "Phase 5i.5 — requires Margrabe instrument + numerical-derivative "
          + "Greeks harness for two-asset payoffs";

    private static final String REASON_AMERICAN =
            "Phase 5i.5 — requires MargrabeOption + "
          + "AnalyticAmericanMargrabeEngine port (no Java equivalent yet)";

    @Ignore(REASON_EURO)
    @Test
    public void testEuroExchangeTwoAssets() { fail("not implemented"); }

    @Ignore(REASON_GREEKS)
    @Test
    public void testGreeks() { fail("not implemented"); }

    @Ignore(REASON_AMERICAN)
    @Test
    public void testAmericanExchangeTwoAssets() { fail("not implemented"); }
}

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
 * Phase 5d skeleton port of {@code test-suite/amortizingbond.cpp} v1.42.1
 * (285 LOC, 3 cases).
 *
 * <p>Exercises the {@code AmortizingFixedRateBond} instrument — fixed-rate
 * bonds with a notional schedule that decreases over time, including the
 * Brazilian convention (sinkable / amortizing schedule) and draw-down
 * support.
 *
 * <p><strong>All 3 cases deferred to Phase 5d.5</strong> — Java has no
 * {@code AmortizingFixedRateBond} class:
 * <ul>
 *   <li>No {@code AmortizingFixedRateBond} instrument
 *       (C++ {@code ql/instruments/bonds/amortizingfixedratebond.hpp});
 *   <li>No {@code AmortizingPayoff} / amortizing-leg builder helpers
 *       in {@code org.jquantlib.cashflow};
 *   <li>No Brazilian sinkable-bond convention helpers
 *       ({@code BrazilianAmortizingFixedRateBond} test variant);
 *   <li>No draw-down schedule wrapper for partial bond issuance.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the entire amortizing bond family
 * (instrument + leg builder + Brazilian helpers) belongs to a future
 * production-code phase.
 *
 * <p>Source: {@code test-suite/amortizingbond.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class AmortizingBondTest {

    private static final String REASON =
            "Phase 5d.5: AmortizingFixedRateBond now ported (commit d303b8bc); "
          + "test body is `fail(\"not implemented\")` — needs full port from "
          + "C++ amortizingbond.cpp::testAmortizingFixedRateBond.";

    private static final String REASON_BRAZIL =
            "Phase 5d.5: AmortizingFixedRateBond ported; Brazilian sinkable-bond "
          + "schedule helpers still needed; test body is `fail(\"not implemented\")` — "
          + "needs full port + Brazilian helpers.";

    private static final String REASON_DRAW_DOWN =
            "Phase 5d.5: AmortizingFixedRateBond ported; draw-down schedule wrapper "
          + "still needed; test body is `fail(\"not implemented\")` — needs full port + "
          + "draw-down helpers.";

    @Ignore(REASON)
    @Test
    public void testAmortizingFixedRateBond() { fail("not implemented"); }

    @Ignore(REASON_BRAZIL)
    @Test
    public void testBrazilianAmortizingFixedRateBond() { fail("not implemented"); }

    @Ignore(REASON_DRAW_DOWN)
    @Test
    public void testAmortizingFixedRateBondWithDrawDown() { fail("not implemented"); }
}

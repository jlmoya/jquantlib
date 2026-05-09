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
 * Phase 5d skeleton port of {@code test-suite/equitytotalreturnswap.cpp}
 * v1.42.1 (305 LOC, 5 cases).
 *
 * <p>Exercises the {@code EquityTotalReturnSwap} (TRS) instrument — a
 * swap that exchanges the total return on an equity index for a funding
 * leg (typically a floating IBOR-spread leg).
 *
 * <p><strong>All 5 cases deferred to Phase 5d.5</strong> — Java has no
 * equity TRS family:
 * <ul>
 *   <li>No {@code EquityTotalReturnSwap} instrument
 *       (C++ {@code ql/instruments/equitytotalreturnswap.hpp});
 *   <li>No {@code EquityIndex} (TRS underlying — see
 *       {@link org.jquantlib.testsuite.indexes.EquityIndexTest});
 *   <li>No equity-leg / equity-coupon builder needed for TRS construction
 *       (see {@link org.jquantlib.testsuite.cashflows.EquityCashFlowTest});
 *   <li>No payment-calendar negotiation between equity and floating legs.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the equity TRS family belongs to a future
 * production-code phase. Depends on {@code EquityIndex} +
 * {@code EquityCashFlow} which are themselves deferred.
 *
 * <p>Source: {@code test-suite/equitytotalreturnswap.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class EquityTotalReturnSwapTest {

    private static final String REASON_PRICING =
            "Phase 5d.5 — requires EquityTotalReturnSwap + EquityIndex + "
          + "EquityCashFlow port (no Java equivalent yet)";

    private static final String REASON_VALIDATION =
            "Phase 5d.5 — requires EquityTotalReturnSwap port + parameter-"
          + "validation harness (negative nominal / missing payment calendar)";

    @Ignore(REASON_PRICING)
    @Test
    public void testFairMargin() { fail("not implemented"); }

    @Ignore(REASON_VALIDATION)
    @Test
    public void testErrorWhenNegativeNominal() { fail("not implemented"); }

    @Ignore(REASON_VALIDATION)
    @Test
    public void testErrorWhenNoPaymentCalendar() { fail("not implemented"); }

    @Ignore(REASON_PRICING)
    @Test
    public void testEquityLegNPV() { fail("not implemented"); }

    @Ignore(REASON_PRICING)
    @Test
    public void testTRSNPV() { fail("not implemented"); }
}

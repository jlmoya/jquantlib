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
 * Phase 5d skeleton port of {@code test-suite/zerocouponswap.cpp}
 * v1.42.1 (311 LOC, 6 cases).
 *
 * <p>Exercises the {@code ZeroCouponSwap} instrument — a swap that
 * exchanges a single fixed payment at maturity for a single floating
 * payment computed from compounded floating-rate fixings over the
 * contract's life. Tests instrument valuation, fair fixed payment, fair
 * fixed rate, fixed-payment-derived-from-rate consistency, argument
 * validation, and expected cash-flow placement in each leg.
 *
 * <p><strong>All 6 cases deferred to Phase 5d.5</strong> — Java has no
 * {@code ZeroCouponSwap} class:
 * <ul>
 *   <li>No {@code ZeroCouponSwap} instrument
 *       (C++ {@code ql/instruments/zerocouponswap.hpp});
 *   <li>No compounded-floating-rate cashflow at maturity (similar to
 *       OIS but with single payment);
 *   <li>No {@code MakeZeroCouponSwap} convenience factory.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the zero-coupon swap belongs to a future
 * production-code phase. Shares some machinery with OIS bootstrapping
 * (compounded floating).
 *
 * <p>Source: {@code test-suite/zerocouponswap.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ZeroCouponSwapTest {

    private static final String REASON =
            "Phase 5d.5 — requires ZeroCouponSwap port (no Java equivalent yet)";

    @Ignore(REASON)
    @Test
    public void testInstrumentValuation() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFairFixedPayment() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFairFixedRate() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testFixedPaymentFromRate() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testArgumentsValidation() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testExpectedCashFlowsInLegs() { fail("not implemented"); }
}

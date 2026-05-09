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
 * Phase 5d skeleton port of {@code test-suite/multipleresetsswap.cpp}
 * v1.42.1 (159 LOC, 4 cases).
 *
 * <p>Exercises a swap whose floating leg is built from multiple-resets
 * coupons (compounded or averaged sub-period IBOR fixings) — tests fair
 * rate computation, consistency with a hand-built leg, averaging vs
 * compounding parity, and a rate-helper variant for curve bootstrapping.
 *
 * <p><strong>All 4 cases deferred to Phase 5d.5</strong> — Java has no
 * multiple-resets swap family:
 * <ul>
 *   <li>No multiple-resets swap construct
 *       (depends on {@link
 *       org.jquantlib.testsuite.cashflows.MultipleResetsCouponsTest});
 *   <li>No multiple-resets rate-helper for yield-curve bootstrapping.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the multiple-resets swap and rate-helper
 * belong to a future production-code phase. Depends on the multiple-
 * resets coupon family being ported first.
 *
 * <p>Source: {@code test-suite/multipleresetsswap.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class MultipleResetsSwapTest {

    private static final String REASON =
            "Phase 5d.5 — requires multiple-resets swap construct + "
          + "MultipleResetsCoupon + MultipleResetsRateHelper (no Java "
          + "equivalent yet)";

    @Ignore(REASON)
    @Test
    public void testFairRate() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testConsistencyWithLeg() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testAveragingVsCompounding() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testRateHelper() { fail("not implemented"); }
}

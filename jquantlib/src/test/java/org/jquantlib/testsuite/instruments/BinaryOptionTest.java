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
 * Phase 5k skeleton port of {@code test-suite/binaryoption.cpp} v1.42.1
 * (256 LOC, 2 cases).
 *
 * <p>Exercises the (single-) barrier-binary option: cash-or-nothing-at-touch
 * and asset-or-nothing-at-touch one-touch barriers cross-validated against
 * the Haug 2007 reference table.
 *
 * <p><strong>All 2 cases deferred to Phase 5k.5</strong> — Java has the
 * cash-or-nothing / asset-or-nothing payoff classes but lacks the
 * barrier-binary engines:
 * <ul>
 *   <li>No {@code BarrierBinaryOption} instrument class
 *       (one-touch / no-touch with cash-or-nothing or asset-or-nothing
 *       at the barrier);
 *   <li>No {@code AnalyticBinaryBarrierEngine} (Haug 1999 closed form).
 *   <li>{@link org.jquantlib.instruments.CashOrNothingPayoff} and
 *       {@link org.jquantlib.instruments.AssetOrNothingPayoff} exist but
 *       are not wired through an at-hit barrier engine.
 * </ul>
 *
 * <p>The barrier-binary engine is a production-code carry-forward to a
 * future binary-barrier extensions phase; Phase 5k.5 is the test-only
 * carry-forward tag.
 *
 * <p>Source: {@code test-suite/binaryoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class BinaryOptionTest {

    private static final String REASON_CASH =
            "Phase 5k.5 — requires BarrierBinaryOption instrument + "
          + "AnalyticBinaryBarrierEngine (cash-or-nothing-at-touch branch) "
          + "for the Haug 2007 reference table";

    private static final String REASON_ASSET =
            "Phase 5k.5 — requires BarrierBinaryOption instrument + "
          + "AnalyticBinaryBarrierEngine (asset-or-nothing-at-touch branch) "
          + "for the Haug 2007 reference table";

    @Ignore(REASON_CASH)  @Test public void testCashOrNothingHaugValues()   { fail("not implemented"); }
    @Ignore(REASON_ASSET) @Test public void testAssetOrNothingHaugValues()  { fail("not implemented"); }
}

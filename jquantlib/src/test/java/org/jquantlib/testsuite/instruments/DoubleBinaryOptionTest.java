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
 * Phase 5k skeleton port of {@code test-suite/doublebinaryoption.cpp}
 * v1.42.1 (330 LOC, 2 cases).
 *
 * <p>Exercises the double-binary (double-barrier touch / no-touch) option
 * across the analytic engine variants (Hui, Wulin-Suo-Wang, Vannilas-Skiadopoulos)
 * cross-validated against Haug 2007 reference values, plus a PDE
 * cross-check for the double-barrier discontinuous payoff.
 *
 * <p><strong>All 2 cases deferred to Phase 5k.5</strong> — Java's
 * {@code DoubleBarrierOption} (Phase 4e) covers continuous-payoff
 * double barriers but not the binary (cash-or-nothing at touch) variant:
 * <ul>
 *   <li>No {@code DoubleBarrierBinaryOption} instrument class;
 *   <li>No double-binary analytic engines
 *       ({@code AnalyticDoubleBarrierBinaryEngine} with method-selector
 *       {@code Hui} / {@code SuoWang} / {@code VannilasSkiadopoulos});
 *   <li>No {@code FdHestonDoubleBarrierEngine} extension for binary
 *       double barriers needed by {@code testPdeDoubleBarrierWithAnalytical}.
 * </ul>
 *
 * <p>Production-code carry-forward to a future binary-barrier
 * extensions phase; Phase 5k.5 is the test-only carry-forward tag.
 *
 * <p>Source: {@code test-suite/doublebinaryoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class DoubleBinaryOptionTest {

    private static final String REASON_HAUG =
            "Phase 5k.5 — requires DoubleBarrierBinaryOption instrument + "
          + "AnalyticDoubleBarrierBinaryEngine (Hui / Suo-Wang / Vannilas-Skiadopoulos "
          + "method selectors) for the Haug 2007 reference table";

    private static final String REASON_PDE =
            "Phase 5k.5 — requires PDE double-barrier binary engine "
          + "cross-validation against the analytic Hui method";

    @Ignore(REASON_HAUG) @Test public void testHaugValues()                       { fail("not implemented"); }
    @Ignore(REASON_PDE)  @Test public void testPdeDoubleBarrierWithAnalytical()   { fail("not implemented"); }
}

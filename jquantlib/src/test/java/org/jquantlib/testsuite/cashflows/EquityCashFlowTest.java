/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/equitycashflow.cpp} v1.42.1
 * (282 LOC, 8 cases).
 *
 * <p>Exercises the equity cash flow / equity coupon family — equity-linked
 * payment streams that pay the price-return on a referenced
 * {@link org.jquantlib.indexes.Index}, including quanto-corrected variants
 * (equity in foreign currency discounted in domestic currency).
 *
 * <p><strong>All 8 cases deferred to Phase 5d.5</strong> — Java has no
 * equity cashflow / equity index family:
 * <ul>
 *   <li>No {@code EquityCashFlow} class
 *       (C++ {@code ql/cashflows/equitycashflow.hpp});
 *   <li>No {@code EquityIndex} (used as the underlying observable);
 *   <li>No quanto-correction pricer / pricer-setter for equity coupons
 *       (requires equity vol, FX vol, equity-FX correlation surface);
 *   <li>No validation harness for missing / inconsistent market-data
 *       handles (the C++ tests exercise QL_FAIL behavior on empty
 *       quanto-curve / equity-vol / FX-vol / correlation handles).
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the equity-linked cashflow family belongs
 * to a future production-code phase (depends on {@code EquityIndex},
 * which is itself deferred — see {@link org.jquantlib.testsuite.indexes.EquityIndexTest}).
 *
 * <p>Source: {@code test-suite/equitycashflow.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class EquityCashFlowTest {

    private static final String REASON_SIMPLE =
            "Phase 5d.5 — requires EquityCashFlow + EquityIndex port "
          + "(no Java equivalent yet)";

    private static final String REASON_QUANTO =
            "Phase 5d.5 — requires EquityCashFlow + quanto-correction pricer "
          + "(equity vol + FX vol + correlation surface; no Java equivalent yet)";

    private static final String REASON_BASE_DATE =
            "Phase 5d.5 — requires EquityCashFlow + base-date validation "
          + "(no Java equivalent yet)";

    private static final String REASON_HANDLE =
            "Phase 5d.5 — requires EquityCashFlow + market-data handle "
          + "validation harness (no Java equivalent yet)";

    @Ignore(REASON_SIMPLE)
    @Test
    public void testSimpleEquityCashFlow() { fail("not implemented"); }

    @Ignore(REASON_QUANTO)
    @Test
    public void testQuantoCorrection() { fail("not implemented"); }

    @Ignore(REASON_BASE_DATE)
    @Test
    public void testErrorWhenBaseDateAfterFixingDate() { fail("not implemented"); }

    @Ignore(REASON_HANDLE)
    @Test
    public void testErrorWhenQuantoCurveHandleIsEmpty() { fail("not implemented"); }

    @Ignore(REASON_HANDLE)
    @Test
    public void testErrorWhenEquityVolHandleIsEmpty() { fail("not implemented"); }

    @Ignore(REASON_HANDLE)
    @Test
    public void testErrorWhenFXVolHandleIsEmpty() { fail("not implemented"); }

    @Ignore(REASON_HANDLE)
    @Test
    public void testErrorWhenCorrelationHandleIsEmpty() { fail("not implemented"); }

    @Ignore(REASON_HANDLE)
    @Test
    public void testErrorWhenInconsistentMarketDataReferenceDate() {
        fail("not implemented");
    }
}

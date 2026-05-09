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
 * Phase 5d skeleton port of {@code test-suite/cashflows.cpp} v1.42.1
 * (623 LOC, 11 cases).
 *
 * <p>Exercises the {@link org.jquantlib.cashflow.CashFlows} static
 * facade — settlement-date semantics, default-settlement-date logic,
 * NPV/duration/yield computations across legs, ex-coupon date treatment,
 * irregular first/last coupon reference dates (end-of-month and
 * end-of-calendar-month variants), partial-schedule leg construction,
 * fixed/IBOR coupon behavior without a forecast curve, and the
 * "IborCoupon knows when it has fixed" regression.
 *
 * <p><strong>All 11 cases deferred to Phase 5d.5</strong>:
 * <ul>
 *   <li>{@code testSettings}, {@code testAccessViolation},
 *       {@code testDefaultSettlementDate}, {@code testNullFixingDays},
 *       {@code testExCouponDates},
 *       {@code testIrregularFirstCouponReferenceDatesAtEndOfMonth},
 *       {@code testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth},
 *       {@code testIrregularLastCouponReferenceDatesAtEndOfMonth},
 *       {@code testPartialScheduleLegConstruction},
 *       {@code testFixedIborCouponWithoutForecastCurve},
 *       {@code testIborCouponKnowsWhenitHasFixed}.
 * </ul>
 *
 * <p>Java has the {@link org.jquantlib.cashflow.CashFlows} facade and
 * many leg / coupon classes, so several of these tests could be bodied
 * directly with cross-validated reference values from a probe. The
 * skeleton-pattern is used to keep Phase 5d as a uniform deferral; the
 * Phase 5d.5 carry-forward is to body these cases against
 * {@code migration-harness/cpp/probes/cashflows/} probes once authored.
 *
 * <p>Specific gaps that block immediate body-fill:
 * <ul>
 *   <li>{@code testNullFixingDays} requires the
 *       {@code IborCoupon::Settings::usingAtParCoupons()} precondition
 *       (Java has the accessor — Phase 2x — but the precondition guard
 *       on this case wasn't ported);
 *   <li>{@code testIrregular*ReferenceDatesAt*} expectations come from
 *       the C++ schedule generation rules; reference values must be
 *       captured via probe before body-fill;
 *   <li>{@code testIborCouponKnowsWhenitHasFixed} regression depends on
 *       fixing-history population helpers that are partially ported.
 * </ul>
 *
 * <p>Source: {@code test-suite/cashflows.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CashFlowsTest {

    private static final String REASON =
            "Phase 5d.5 — requires reference-value probes for CashFlows facade "
          + "behaviour (settlement / ex-coupon / irregular-reference-date logic); "
          + "Java production code is in place but probes not yet authored";

    private static final String REASON_NULL_FIXING =
            "Phase 5d.5 — requires precondition-guarded usingAtParCoupons() "
          + "test variant; Java has the accessor (Phase 2x) but the precondition "
          + "wrapper / parametric flag is not wired into the JUnit case yet";

    private static final String REASON_FIXING =
            "Phase 5d.5 — requires fixing-history harness to validate the "
          + "IborCoupon-knows-when-it-has-fixed regression";

    @Ignore(REASON) @Test public void testSettings() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testAccessViolation() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testDefaultSettlementDate() { fail("not implemented"); }
    @Ignore(REASON_NULL_FIXING) @Test public void testNullFixingDays() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testExCouponDates() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testIrregularFirstCouponReferenceDatesAtEndOfMonth() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testIrregularLastCouponReferenceDatesAtEndOfMonth() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testPartialScheduleLegConstruction() { fail("not implemented"); }
    @Ignore(REASON) @Test public void testFixedIborCouponWithoutForecastCurve() { fail("not implemented"); }
    @Ignore(REASON_FIXING) @Test public void testIborCouponKnowsWhenitHasFixed() { fail("not implemented"); }
}

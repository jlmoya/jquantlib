/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.MultipleResetsCoupon;
import org.jquantlib.cashflow.MultipleResetsLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.indexes.Euribor1M;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.calendars.Target;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/multipleresetscoupons.cpp}
 * v1.42.1 (288 LOC, 5 cases).
 *
 * <p>Exercises multiple-resets coupons — coupons whose payoff depends on
 * the compounded or arithmetic-averaged value of an IBOR fixing taken at
 * multiple sub-period reset dates within a single accrual period. Tests
 * compounded vs averaged variants, ex-coupon cashflow logic, leg
 * consistency checks, and a regression case.
 *
 * <p><strong>All 5 cases deferred to Phase 5d.5</strong> — Java has no
 * multiple-resets coupon family:
 * <ul>
 *   <li>No {@code MultipleResetsCoupon} class
 *       (C++ {@code ql/cashflows/multipleresetscoupon.hpp});
 *   <li>No compounded / averaged sub-period IBOR pricer for this coupon;
 *   <li>No {@code MultipleResetsLeg} builder;
 *   <li>No ex-coupon date treatment for the multiple-resets case.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the entire multiple-resets coupon family
 * belongs to a future production-code phase.
 *
 * <p>Source: {@code test-suite/multipleresetscoupons.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class MultipleResetsCouponsTest {

    private static final String REASON =
            "Phase 5d.5: MultipleResetsCoupon + MultipleResetsLeg now ported (commit 40bf78eb); "
          + "test bodies are `fail(\"not implemented\")` — needs full port from C++ "
          + "multipleresetscoupons.cpp. (Note: parallel MultipleResetsCouponSmokeTest already "
          + "exercises the freshly-ported family with smoke tests.)";

    @Ignore(REASON)
    @Test
    public void testCompoundedCouponWithMultipleResets() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testAveragedCouponWithMultipleResets() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testExCouponCashFlow() { fail("not implemented"); }

    @Ignore(REASON)
    @Test
    public void testMultipleResetsLegConsistencyChecks() { fail("not implemented"); }

    /**
     * Phase Body-Fill (2026-05-09) — port of C++
     * {@code multipleresetscoupons.cpp::testMultipleResetsLegRegression}.
     *
     * <p>Builds a 1Y monthly schedule with {@code resetsPerCoupon = 3}
     * (so each coupon spans 3 sub-periods) and asserts every coupon in
     * the resulting leg has exactly 3 fixing dates.
     *
     * <p>This is a pure structural test that does not depend on cached
     * reference values or pricing-engine integration.
     */
    @Test
    public void testMultipleResetsLegRegression() {
        QL.info("Testing number of fixing dates in multiple-resets coupons...");

        final Schedule schedule = new MakeSchedule(
                new Date(1, Month.August, 2024),
                new Date(1, Month.August, 2025),
                new Period(Frequency.Monthly),
                new Target(),
                org.jquantlib.time.BusinessDayConvention.Following)
                .schedule();

        final int resetsPerCoupon = 3;
        final Leg leg = new MultipleResetsLeg(
                schedule, new Euribor1M(), resetsPerCoupon)
                .withNotionals(100.0)
                .withAveragingMethod(RateAveraging.Type.Compound)
                .Leg();

        for (final CashFlow cf : leg) {
            if (!(cf instanceof MultipleResetsCoupon)) {
                fail("expected MultipleResetsCoupon, got " + cf.getClass());
            }
            final MultipleResetsCoupon c = (MultipleResetsCoupon) cf;
            final int n = c.fixingDates().size();
            if (n != resetsPerCoupon) {
                fail("Unexpected number of fixing dates (" + n
                        + ") in coupon paying on " + c.date());
            }
        }
    }
}

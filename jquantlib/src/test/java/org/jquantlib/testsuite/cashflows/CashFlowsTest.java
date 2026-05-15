/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
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
 * <p><strong>Phase 5e.5b-CFC-d-21</strong> bodied {@code testSettings}
 * after porting {@link org.jquantlib.Settings#includeReferenceDateEvents()}
 * and {@link org.jquantlib.Settings#includeTodaysCashFlows()} accessors and
 * the C++-aligned {@code CashFlow.hasOccurred(Date, Boolean)} override
 * (cashflow.cpp v1.42.1 lines 27-49).
 *
 * <p>Remaining Phase 5d.5 cases:
 * <ul>
 *   <li>{@code testAccessViolation}, {@code testDefaultSettlementDate},
 *       {@code testNullFixingDays}, {@code testExCouponDates},
 *       {@code testIrregularFirstCouponReferenceDatesAtEndOfMonth},
 *       {@code testIrregularFirstCouponReferenceDatesAtEndOfCalendarMonth},
 *       {@code testIrregularLastCouponReferenceDatesAtEndOfMonth},
 *       {@code testPartialScheduleLegConstruction},
 *       {@code testFixedIborCouponWithoutForecastCurve},
 *       {@code testIborCouponKnowsWhenitHasFixed}.
 * </ul>
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

    /**
     * Mirrors C++ {@code CashFlowTests::testSettings} (test-suite/cashflows.cpp
     * v1.42.1 lines 48-179). Validates the
     * {@link Settings#includeReferenceDateEvents()} and
     * {@link Settings#includeTodaysCashFlows()} flag interaction with
     * {@link CashFlow#hasOccurred(Date, Boolean)} and
     * {@link CashFlows#npv(Leg, org.jquantlib.termstructures.YieldTermStructure, boolean, Date, Date)}.
     */
    @Test
    public void testSettings() {
        // Save Settings state for restoration at the end (mirrors C++
        // SavedSettings RAII; Java has no destructor, so we restore manually).
        final Settings settings = new Settings();
        final Date savedEval = settings.evaluationDate();
        final boolean savedIncludeRef = settings.includeReferenceDateEvents();
        final Boolean savedIncludeToday = settings.includeTodaysCashFlows();

        try {
            final Date today = Date.todaysDate();
            settings.setEvaluationDate(today);

            // cash flows at T+0, T+1, T+2
            final Leg leg = new Leg();
            for (int i = 0; i < 3; ++i) {
                leg.add((CashFlow) new SimpleCashFlow(1.0, today.add(i)));
            }

            // case 1: don't include reference-date payments, no override at
            //         today's date
            settings.setIncludeReferenceDateEvents(false);
            settings.setIncludeTodaysCashFlows(null);

            checkInclusion(leg, 0, 0, false);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, false);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, false);
            checkInclusion(leg, 2, 3, false);

            // case 2: same, but with explicit setting at today's date
            settings.setIncludeReferenceDateEvents(false);
            settings.setIncludeTodaysCashFlows(Boolean.FALSE);

            checkInclusion(leg, 0, 0, false);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, false);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, false);
            checkInclusion(leg, 2, 3, false);

            // case 3: do include reference-date payments, no override at
            //         today's date
            settings.setIncludeReferenceDateEvents(true);
            settings.setIncludeTodaysCashFlows(null);

            checkInclusion(leg, 0, 0, true);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, true);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, true);
            checkInclusion(leg, 2, 3, false);

            // case 4: do include reference-date payments, explicit (and same)
            //         setting at today's date
            settings.setIncludeReferenceDateEvents(true);
            settings.setIncludeTodaysCashFlows(Boolean.TRUE);

            checkInclusion(leg, 0, 0, true);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, true);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, true);
            checkInclusion(leg, 2, 3, false);

            // case 5: do include reference-date payments, override at
            //         today's date
            settings.setIncludeReferenceDateEvents(true);
            settings.setIncludeTodaysCashFlows(Boolean.FALSE);

            checkInclusion(leg, 0, 0, false);
            checkInclusion(leg, 0, 1, false);

            checkInclusion(leg, 1, 0, true);
            checkInclusion(leg, 1, 1, true);
            checkInclusion(leg, 1, 2, false);

            checkInclusion(leg, 2, 1, true);
            checkInclusion(leg, 2, 2, true);
            checkInclusion(leg, 2, 3, false);

            // NPV section — no discount to make calculations easier.
            // C++ uses InterestRate(0.0, Actual365Fixed, Continuous, Annual);
            // Java equivalent is FlatForward at the same parameters wrapping
            // a 0.0 rate (discount factor = 1.0 at every date).
            final DayCounter dc = new Actual365Fixed();
            final FlatForward noDiscount = new FlatForward(today, 0.0, dc,
                    Compounding.Continuous, Frequency.Annual);

            // no override
            settings.setIncludeTodaysCashFlows(null);
            checkNpv(leg, noDiscount, false, today, 2.0);
            checkNpv(leg, noDiscount, true, today, 3.0);

            // override
            settings.setIncludeTodaysCashFlows(Boolean.FALSE);
            checkNpv(leg, noDiscount, false, today, 2.0);
            checkNpv(leg, noDiscount, true, today, 2.0);
        } finally {
            settings.setIncludeTodaysCashFlows(savedIncludeToday);
            settings.setIncludeReferenceDateEvents(savedIncludeRef);
            settings.setEvaluationDate(savedEval);
        }
    }

    /**
     * Mirrors C++ {@code CHECK_INCLUSION(n, days, expected)} macro: asserts
     * {@code !leg[n].hasOccurred(today + days) == expected} (i.e., the cash
     * flow is "included" when it has NOT yet occurred).
     */
    private static void checkInclusion(final Leg leg, final int n, final int days,
                                       final boolean expected) {
        final Date today = new Settings().evaluationDate();
        final boolean included = !leg.get(n).hasOccurred(today.add(days));
        if (included != expected) {
            fail("cashflow at T+" + n + " " + (expected ? "not " : "")
                    + "included at T+" + days);
        }
    }

    /**
     * Mirrors C++ {@code CHECK_NPV(includeRef, expected)} macro.
     */
    private static void checkNpv(final Leg leg, final FlatForward noDiscount,
                                 final boolean includeRef, final Date today,
                                 final double expected) {
        final double npv = CashFlows.npv(leg, noDiscount, includeRef, today, today);
        assertEquals("NPV mismatch (includeRef=" + includeRef + ")",
                expected, npv, 1e-6);
    }

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

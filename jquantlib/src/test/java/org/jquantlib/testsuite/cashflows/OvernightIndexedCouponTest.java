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
 * Phase 5d skeleton port of {@code test-suite/overnightindexedcoupon.cpp}
 * v1.42.1 (1,130 LOC, 35 cases).
 *
 * <p>Exercises the {@code OvernightIndexedCoupon} family — coupons whose
 * payoff is the compounded (geometric, classic-OIS) or arithmetic-averaged
 * value of an overnight index (SOFR/EONIA/SONIA/etc.) over the accrual
 * period. Covers all the post-Libor reform variants:
 * <ul>
 *   <li><strong>Past / current / future coupon rates</strong> — coupon
 *       rate computation when the period is fully realized, partially
 *       realized, or fully forecast;
 *   <li><strong>Lookback / observation-shift / lockout</strong> —
 *       conventions for SOFR-style overnight rates that are observed
 *       earlier than payment to allow operational lag;
 *   <li><strong>Black caplet/floorlet pricers</strong> — Black ON-coupon
 *       and Black averaging-ON pricers for capped/floored overnight
 *       coupons (with vol consistency checks);
 *   <li><strong>OvernightLeg builder</strong> — gearings/spreads,
 *       lookback, lockout, observation-shift, simple averaging,
 *       caps/floors, NPV, error-condition coverage;
 *   <li><strong>Telescopic value-dates</strong> — error when telescopic
 *       value-dates conflict with lookback;
 *   <li><strong>Payment-before-accrual-end</strong> — corner case of OIS
 *       payment scheduled before the accrual period ends.
 * </ul>
 *
 * <p><strong>All 35 cases deferred to Phase 5d.5</strong> — Java has no
 * {@code OvernightIndexedCoupon} family:
 * <ul>
 *   <li>No {@code OvernightIndex} hierarchy
 *       (Java has only IBOR-style indices in {@code org.jquantlib.indexes});
 *   <li>No {@code OvernightIndexedCoupon} class;
 *   <li>No averaging mode enum (compounded vs simple);
 *   <li>No lookback / observation-shift / lockout machinery;
 *   <li>No {@code OvernightLeg} builder;
 *   <li>No Black ON pricer / Black averaging-ON pricer for capped/floored
 *       overnight coupons.
 * </ul>
 *
 * <p>Phase 5d.5 carry-forward: the overnight-index family is a major
 * production-code subsystem (overnight indices + SOFR/EONIA/SONIA index
 * classes + coupon + leg + Black pricer hierarchy + telescopic-dates
 * helpers). It is the most material gap in the bond-instrument test suite.
 *
 * <p>Source: {@code test-suite/overnightindexedcoupon.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class OvernightIndexedCouponTest {

    private static final String REASON_PAST =
            "Phase 5d.5 — requires OvernightIndex + OvernightIndexedCoupon "
          + "(no Java equivalent for past-coupon rate computation yet)";

    private static final String REASON_CURRENT =
            "Phase 5d.5 — requires OvernightIndex + OvernightIndexedCoupon "
          + "(no Java equivalent for current-coupon rate computation yet)";

    private static final String REASON_FUTURE =
            "Phase 5d.5 — requires OvernightIndex + OvernightIndexedCoupon "
          + "(no Java equivalent for future-coupon rate forecasting yet)";

    private static final String REASON_ACCRUED =
            "Phase 5d.5 — requires OvernightIndex + OvernightIndexedCoupon "
          + "(no Java equivalent for accrued-amount computation yet)";

    private static final String REASON_LOOKBACK =
            "Phase 5d.5 — requires OvernightIndexedCoupon + lookback / "
          + "observation-shift / lockout machinery (no Java equivalent yet)";

    private static final String REASON_BLACK =
            "Phase 5d.5 — requires Black ON-coupon / Black averaging-ON "
          + "caplet/floorlet pricer (no Java equivalent yet)";

    private static final String REASON_LEG =
            "Phase 5d.5 — requires OvernightLeg builder + gearings / "
          + "spreads / caps / floors (no Java equivalent yet)";

    private static final String REASON_TELESCOPIC =
            "Phase 5d.5 — requires OvernightIndexedCoupon + telescopic "
          + "value-dates handling (no Java equivalent yet)";

    private static final String REASON_PAYMENT =
            "Phase 5d.5 — requires OvernightIndexedCoupon + payment-before-"
          + "accrual-end corner case (no Java equivalent yet)";

    @Ignore(REASON_PAST) @Test public void testPastCouponRate() { fail("not implemented"); }
    @Ignore(REASON_PAST) @Test public void testPastSpreadedCouponRate() { fail("not implemented"); }
    @Ignore(REASON_CURRENT) @Test public void testCurrentCouponRate() { fail("not implemented"); }
    @Ignore(REASON_FUTURE) @Test public void testFutureCouponRate() { fail("not implemented"); }
    @Ignore(REASON_CURRENT) @Test public void testRateWhenTodayIsHoliday() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountInThePast() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountSpanningToday() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountInTheFuture() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountOnPastHoliday() { fail("not implemented"); }
    @Ignore(REASON_ACCRUED) @Test public void testAccruedAmountOnFutureHoliday() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLookbackAndObservationShift() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLockout() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPastCouponRateWithLookbackObservationShiftAndLockout() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testIncorrectNumberOfLockoutDays() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testFutureCouponRateWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testFutureCouponRateWithLookbackAndObservationShift() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testFutureCouponRateWithLookout() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testPartiallyAccruedAmountOfFutureCouponWithLookout() { fail("not implemented"); }
    @Ignore(REASON_TELESCOPIC) @Test public void testTelescopicFormulaWhenLookbackWithObservationShiftAndNoIndexFixingDelay() { fail("not implemented"); }
    @Ignore(REASON_TELESCOPIC) @Test public void testErrorWhenTelescopicValueDatesEnforcedWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LOOKBACK) @Test public void testErrorWhenLookbackOrLockoutAppliedForSimpleAveraging() { fail("not implemented"); }
    @Ignore(REASON_BLACK) @Test public void testBlackOvernightIndexedCouponPricerCapletFloorlet() { fail("not implemented"); }
    @Ignore(REASON_BLACK) @Test public void testBlackAverageONIndexedCouponPricerCapletFloorlet() { fail("not implemented"); }
    @Ignore(REASON_BLACK) @Test public void testBlackONPricerConsistencyWithNoVol() { fail("not implemented"); }
    @Ignore(REASON_BLACK) @Test public void testBlackONAveragingPricerConsistencyWithNoVol() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegBasicFunctionality() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegWithLookback() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegWithLockout() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegWithObservationShift() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegWithGearingsAndSpreads() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegNPV() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegWithCapsAndFloors() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegSimpleAveraging() { fail("not implemented"); }
    @Ignore(REASON_LEG) @Test public void testOvernightLegErrorConditions() { fail("not implemented"); }
    @Ignore(REASON_PAYMENT) @Test public void testOvernightIndexedCouponPaymentBeforeAccrualEnd() { fail("not implemented"); }
}

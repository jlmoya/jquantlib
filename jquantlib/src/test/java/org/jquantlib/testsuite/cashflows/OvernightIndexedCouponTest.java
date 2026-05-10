/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.OvernightIndexedCoupon;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.math.Constants;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d skeleton port of {@code test-suite/overnightindexedcoupon.cpp}
 * v1.42.1 (1,130 LOC, 35 cases).
 *
 * <p>Phase Body-Fill (2026-05-09) — 1 case body-filled
 * (testFutureCouponRate, the simplest single-coupon all-future case).
 *
 * <p>Remaining 34 cases stay deferred to Phase 5d.5 — they need either:
 * <ul>
 *   <li>past-fixings setup (the C++ {@code CommonVars} fixture seeds 56
 *       SOFR fixings spanning 2019-06-21..2021-11-22 for past/spanning-today
 *       coupons);</li>
 *   <li>lookback / observation-shift / lockout machinery (Java
 *       OvernightIndexedCoupon ctor currently rejects non-default lookback /
 *       lockout / observationShift via QL.require — Phase 5d.5 MVP);</li>
 *   <li>OvernightLeg builder coverage (Phase 5d.5);</li>
 *   <li>Black ON / Black averaging-ON pricer integration with
 *       CappedFlooredOvernightIndexedCoupon (Phase 5d.5);</li>
 *   <li>telescopicValueDates handling and
 *       payment-before-accrual-end corner case.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/overnightindexedcoupon.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class OvernightIndexedCouponTest {

    private static final String REASON_PAST =
            "Phase 5d.5: OvernightIndex + OvernightIndexedCoupon now ported (commits fa38ff70, 41f7102a); "
          + "needs full port from C++ overnightindexedcoupon.cpp + 56-row CommonVars past-fixings table.";

    private static final String REASON_CURRENT =
            "Phase 5d.5: OvernightIndex + OvernightIndexedCoupon now ported (commits fa38ff70, 41f7102a); "
          + "needs full port from C++ overnightindexedcoupon.cpp + 56-row CommonVars past-fixings table "
          + "(coupon spans today, requires both historical and forecast fixings).";

    private static final String REASON_ACCRUED =
            "Phase 5d.5: OvernightIndex + OvernightIndexedCoupon now ported (commits fa38ff70, 41f7102a); "
          + "needs full port from C++ overnightindexedcoupon.cpp + accrued-amount cached references.";

    private static final String REASON_LOOKBACK =
            "Phase 5d.5: OvernightIndexedCoupon ported but lookback / observation-shift / lockout "
          + "machinery rejected by ctor (QL.require lookbackDays==0, lockoutDays==0, !observationShift). "
          + "Phase 5d.5 MVP carry-forward.";

    private static final String REASON_BLACK =
            "Phase 5d.5: OvernightIndexedCouponPricer now ported (commit 41f7102a); needs Black "
          + "ON-coupon / Black averaging-ON caplet/floorlet pricer integration.";

    private static final String REASON_LEG =
            "Phase 5d.5: OvernightLeg builder ported (commit 41f7102a); needs full port from C++ "
          + "overnightindexedcoupon.cpp.";

    private static final String REASON_TELESCOPIC =
            "Phase 5d.5: OvernightIndexedCoupon ported; telescopicValueDates handling deferred "
          + "(MVP ignores the flag and always builds the full schedule).";

    private static final String REASON_PAYMENT =
            "Phase 5d.5: OvernightIndexedCoupon ported; payment-before-accrual-end corner case "
          + "rejected by ctor's QL.require(paymentDate.ge(endDate)).";

    /** Mirror of C++ {@code CommonVars} struct minimum subset for the body-filled test. */
    private static final class CommonVars {
        final Date today;
        final double notional = 10000.0;
        final OvernightIndex sofr;
        final RelinkableHandle<YieldTermStructure> forecastCurve;

        CommonVars(final Date evaluationDate) {
            this.today = evaluationDate;
            new Settings().setEvaluationDate(today);
            this.forecastCurve = new RelinkableHandle<YieldTermStructure>();
            this.sofr = new Sofr(forecastCurve);
        }

        CommonVars() {
            this(new Date(23, Month.November, 2021));
        }

        OvernightIndexedCoupon makeCoupon(final Date startDate,
                                          final Date endDate) {
            // Match C++ defaults (cpp:52-62): paymentDate=endDate, gearing=1,
            // spread=0, no refperiod, default DayCounter (forces fall-back
            // to overnightIndex.dayCounter() via super ctor), no telescopic,
            // Compound averaging, default fixingDays/lookback/lockout, no
            // observation shift, compoundSpreadDaily=false.
            return new OvernightIndexedCoupon(
                    endDate, notional, startDate, endDate, sofr,
                    1.0, 0.0, new Date(), new Date(),
                    sofr.dayCounter(),
                    /* telescopicValueDates */ false,
                    RateAveraging.Type.Compound,
                    /* lookbackDays */ Constants.NULL_NATURAL,
                    /* lockoutDays */ 0,
                    /* applyObservationShift */ false,
                    /* compoundSpreadDaily */ false);
        }
    }

    @Ignore(REASON_PAST) @Test public void testPastCouponRate() { fail("not implemented"); }
    @Ignore(REASON_PAST) @Test public void testPastSpreadedCouponRate() { fail("not implemented"); }
    @Ignore(REASON_CURRENT) @Test public void testCurrentCouponRate() { fail("not implemented"); }

    @Test
    public void testFutureCouponRate() {
        QL.info("Testing rate for future overnight-indexed coupon...");

        final CommonVars vars = new CommonVars();

        // Flat 0.10% forecast curve via Actual360, eval date 2021-11-23.
        final DayCounter dc360 = new Actual360();
        vars.forecastCurve.linkTo(Utilities.flatRate(vars.today, 0.0010, dc360));

        // Coupon entirely in the future: 2021-12-10 to 2022-01-10.
        final OvernightIndexedCoupon futureCoupon = vars.makeCoupon(
                new Date(10, Month.December, 2021),
                new Date(10, Month.January, 2022));

        // C++ expected (overnightindexedcoupon.cpp:407-411):
        //   expectedRate = 0.001000043057
        //   expectedAmount = notional * expectedRate * 31/360
        //   tolerance: 1e-12 on rate, 1e-8 on amount.
        final double expectedRate = 0.001000043057;
        final double expectedAmount = vars.notional * expectedRate * 31.0 / 360.0;
        final double rateTol = 1e-12;
        final double amountTol = 1e-8;

        final double rate = futureCoupon.rate();
        if (Math.abs(rate - expectedRate) > rateTol) {
            fail("future coupon rate: expected=" + expectedRate
                    + " calculated=" + rate
                    + " diff=" + Math.abs(rate - expectedRate)
                    + " tolerance=" + rateTol);
        }
        final double amount = futureCoupon.amount();
        if (Math.abs(amount - expectedAmount) > amountTol) {
            fail("future coupon amount: expected=" + expectedAmount
                    + " calculated=" + amount
                    + " diff=" + Math.abs(amount - expectedAmount)
                    + " tolerance=" + amountTol);
        }
    }

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

/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CallabilitySchedule;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Schedule;

/**
 * Callable / puttable fixed-rate bond.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/experimental/callablebonds/callablebond.{hpp,cpp}}
 * (the {@code CallableFixedRateBond} portion).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>The C++ ctor takes optional {@code exCouponPeriod / exCouponCalendar /
 *     exCouponConvention / exCouponEndOfMonth} arguments and threads them
 *     through {@code FixedRateLeg.withExCouponPeriod}. {@link FixedRateLeg}
 *     does not yet expose that builder; the ex-coupon parameters are accepted
 *     for source compatibility but ignored. Tests exercising the ex-coupon
 *     window are deferred to Phase 4b.5 (see CallableBondTest).
 * </ul>
 */
public class CallableFixedRateBond extends CallableBond {

    public CallableFixedRateBond(final int settlementDays, final double faceAmount,
            final Schedule schedule, final double[] coupons, final DayCounter accrualDayCounter,
            final BusinessDayConvention paymentConvention, final double redemption,
            final Date issueDate, final CallabilitySchedule putCallSchedule) {
        super(settlementDays, schedule.dates().get(schedule.dates().size() - 1),
                schedule.calendar(), accrualDayCounter, faceAmount, issueDate, putCallSchedule);

        // Mirrors C++ ql/experimental/callablebonds/callablebond.cpp:
        //   frequency_ = schedule.hasTenor() ? schedule.tenor().frequency()
        //                                    : NoFrequency;
        // The hasTenor() guard is required for arbitrary-date schedules
        // (Schedule(List<Date>, Calendar, BDC)) where calling tenor() would
        // throw "full interface (tenor) not available". Phase 5e.5b-CFC-d-159.
        frequency_ = (schedule.hasTenor() && schedule.tenor().length() != 0)
                ? schedule.tenor().frequency()
                : Frequency.NoFrequency;

        cashflows_ = new FixedRateLeg(schedule, accrualDayCounter)
                .withNotionals(faceAmount)
                .withCouponRates(coupons)
                .withPaymentAdjustment(paymentConvention)
                .Leg();

        addRedemptionsToCashflows(new double[] { redemption });

        QL.ensure(!cashflows().isEmpty(), "bond with no cashflows!");
        QL.ensure(redemptions_.size() == 1, "multiple redemptions created");
    }

    /** Convenience overload: default {@code redemption=100}, no put/call schedule. */
    public CallableFixedRateBond(final int settlementDays, final double faceAmount,
            final Schedule schedule, final double[] coupons, final DayCounter accrualDayCounter,
            final BusinessDayConvention paymentConvention, final double redemption,
            final Date issueDate) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter, paymentConvention,
                redemption, issueDate, new CallabilitySchedule());
    }

    /** Convenience overload: default {@code paymentConvention=Following}. */
    public CallableFixedRateBond(final int settlementDays, final double faceAmount,
            final Schedule schedule, final double[] coupons, final DayCounter accrualDayCounter,
            final double redemption, final Date issueDate,
            final CallabilitySchedule putCallSchedule) {
        this(settlementDays, faceAmount, schedule, coupons, accrualDayCounter,
                BusinessDayConvention.Following, redemption, issueDate, putCallSchedule);
    }
}
